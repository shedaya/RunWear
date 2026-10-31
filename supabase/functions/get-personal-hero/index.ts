// Supabase Edge Function: get-personal-hero (RW-14, 2026-10-31)
//
// The serving contract for personalized heroes. POST with a user JWT:
//   { "combination_id": "MALE_CLEAR_MILD_DAWN_<8-hex outfit signature>" }
// Response:
//   found:  { "found": true, "combination_id", "hero_id", "variant",
//             "image_url": <signed URL, 1h>, "version": <cell version> }
//   miss:   { "found": false, "combination_id", "version": <n|null> }
// A miss means: serve the generic pool hero (the client fallback). The
// version marker changes whenever the cell's verified hero set changes
// (variant added or removed), so clients can invalidate cached heroes.
//
// Side effect (replenishment hook, entitled users only): when a hero is
// served and the cell holds fewer than VARIANT_CAP verified variants,
// enqueue up to 2 replenishment candidate jobs for the cell — deduped
// against active jobs — so the cell keeps a fresh rotation. Free users
// never trigger replenishment.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const VARIANT_CAP = 3
const REPLENISH_CANDIDATES = 2
const PRIORITY_REPLENISHMENT = 30
const SIGNED_URL_TTL_SECONDS = 3600

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'Method not allowed' }, 405)

    const authHeader = req.headers.get('Authorization') ?? ''
    const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      global: { headers: { Authorization: authHeader } }
    })
    const { data: userData, error: userError } = await userClient.auth.getUser()
    if (userError || !userData.user) return jsonResponse({ error: 'Unauthorized' }, 401)
    const userId = userData.user.id

    const body = await req.json().catch(() => ({})) as Record<string, unknown>
    const combinationId = body.combination_id
    if (typeof combinationId !== 'string' || combinationId.length === 0) {
      return jsonResponse({ error: 'combination_id is required' }, 400)
    }

    const sb = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    const { data: cell } = await sb
      .from('personal_cells')
      .select('version')
      .eq('user_id', userId)
      .eq('combination_id', combinationId)
      .maybeSingle()
    const version = cell?.version ?? null

    const { data: heroes, error: heroError } = await sb
      .from('personal_heroes')
      .select('id, storage_path, variant, outfit_items')
      .eq('user_id', userId)
      .eq('combination_id', combinationId)
      .order('variant', { ascending: true })
    if (heroError) throw heroError

    if (!heroes || heroes.length === 0) {
      return jsonResponse({ found: false, combination_id: combinationId, version })
    }

    // Rotate variants deterministically by day so a cell with several
    // verified variants does not show the same photo every day.
    const dayIndex = Math.floor(Date.now() / 86_400_000)
    const hero = heroes[dayIndex % heroes.length]

    const { data: signed, error: signError } = await sb.storage
      .from('personal-heroes')
      .createSignedUrl(hero.storage_path, SIGNED_URL_TTL_SECONDS)
    if (signError || !signed) throw signError ?? new Error('Could not sign hero URL')

    // --- replenishment hook (entitled only) ---
    let replenishmentQueued = 0
    const { data: profile } = await sb
      .from('personal_profiles')
      .select('entitled')
      .eq('user_id', userId)
      .maybeSingle()
    if (profile?.entitled === true && heroes.length < VARIANT_CAP) {
      const { data: active } = await sb
        .from('personal_generation_jobs')
        .select('id')
        .eq('user_id', userId)
        .eq('combination_id', combinationId)
        .in('status', ['QUEUED', 'PROCESSING'])
      const activeCount = active?.length ?? 0
      const wanted = Math.min(
        REPLENISH_CANDIDATES,
        VARIANT_CAP - heroes.length - activeCount
      )
      if (wanted > 0) {
        const parts = combinationId.split('_')
        const rows = Array.from({ length: wanted }, () => ({
          user_id: userId,
          combination_id: combinationId,
          gender: parts[0], weather: parts[1],
          temp_bracket: parts[2], time_of_day: parts[3],
          outfit_signature: parts[4],
          outfit_items: hero.outfit_items,
          kind: 'replenishment',
          priority: PRIORITY_REPLENISHMENT,
          status: 'QUEUED'
        }))
        const { error: enqueueError } = await sb.from('personal_generation_jobs').insert(rows)
        if (!enqueueError) {
          replenishmentQueued = wanted
          // Prompt fulfillment instead of waiting for the drain cron.
          const kick = fetch(`${SUPABASE_URL}/functions/v1/personal-hero-worker`, {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ drain: 1 })
          }).catch(e => console.error('[get-personal-hero] worker kick failed:', e))
          // @ts-ignore EdgeRuntime is a Supabase runtime global
          if (typeof EdgeRuntime !== 'undefined') { EdgeRuntime.waitUntil(kick) }
        }
      }
    }

    return jsonResponse({
      found: true,
      combination_id: combinationId,
      hero_id: hero.id,
      variant: hero.variant,
      image_url: signed.signedUrl,
      version,
      replenishment_queued: replenishmentQueued
    })
  } catch (error) {
    console.error('[get-personal-hero] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
