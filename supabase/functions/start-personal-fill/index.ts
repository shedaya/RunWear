// Supabase Edge Function: start-personal-fill (RW-14, 2026-10-31)
//
// GEN-5 CONTRACT NOTE (RW-28 Addendum 8, Defect 1, Nov 5, 2026):
// this repository copy predates the deployed convergence model.
// Production runs v13 (RW-16.30), which enqueues ONLY the cells
// the caller names in the request body's `priority_cells`
// (combination ids from the user's 7-day forecast, in forecast
// order), intersected with the canonical launch set. The shipped
// clients now send priority_cells (Android in gen-5; the gen-4
// empty-body call was the defect that left banks unconverged).
// This copy still implements the older fixed-matrix contract
// below; bringing the repository copy to the v13 contract is
// named server-side work in the gen-5 report — production
// behaviour is NOT changed by anything in this file today.
//
// Subscriber initial fill: enqueues the 20-cell core matrix for an
// ENTITLED user — every temperature bracket x CLEAR x DAWN, x CLEAR x
// DUSK, x RAIN x DAWN, plus WARM/HOT x CLEAR x MIDDAY — each cell at
// the bracket's canonical outfit (item lists below, in the engine's
// ClothingItem keys; the outfit signature is the engine's hash of the
// sorted item keys). Cells that already have a verified hero or an
// active job are skipped, so re-calling is harmless.
//
// Callers: the entitled user themself (user JWT, no body user_id), or
// the service role with { "user_id": "..." } (the billing lane calls
// it after set_personal_entitlement flips a user to entitled).
// Non-entitled users are refused — the fill is a subscriber benefit.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const PRIORITY_SUBSCRIBER_FILL = 10

// Canonical fill cells — DERIVED FROM ENGINE v4.3 (RW-16.23), never
// hand-mirrored. Each cell's items are the exact allItems output of
// GetOutfitRecommendationUseCase.recommendFrames at the cell's
// representative frame:
//   comfort NEUTRAL (adjusted == raw); CLEAR calm (no wind shell);
//   RAIN measured rain, precipitation 1.0mm; UV 2 at DAWN/DUSK,
//   4 at MIDDAY, 1 in RAIN; hour 8 DAWN / 18 DUSK / 13 MIDDAY;
//   adjusted temps FREEZING 12F, COLD 27F, COOL 42F, MILD 57F,
//   WARM 72F, HOT 85F.
// The pre-RW-16.23 table mirrored the legacy pool prompts instead and
// 10 of its 12 outfits were outfits the engine cannot produce at any
// frame (e.g. CLEAR COOL demanded LIGHT_JACKET, which selectTopOuter
// never returns), so fill heroes were unservable on client cells even
// when they passed the gate. Drift guard: the JVM test
// FillCanonicalOutfitsTest (RunWear-android/shared) replays every
// representative frame through the engine and parses the block below
// out of this file — if the engine tables or this block change
// without each other, that test fails. Update both together.
// BEGIN GENERATED CANONICAL CELLS
const CANONICAL_CELLS: Array<{ weather: string, bracket: string, time: string, items: string[] }> = [
  { weather: 'CLEAR', bracket: 'FREEZING', time: 'DAWN', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'FREEZING', time: 'DUSK', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'FREEZING', time: 'DAWN', items: ['BASEBALL_CAP', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'COLD', time: 'DAWN', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'COLD', time: 'DUSK', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'COLD', time: 'DAWN', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'COOL', time: 'DAWN', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'CLEAR', bracket: 'COOL', time: 'DUSK', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'RAIN', bracket: 'COOL', time: 'DAWN', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'LIGHT_TIGHTS'] },
  { weather: 'CLEAR', bracket: 'MILD', time: 'DAWN', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'MILD', time: 'DUSK', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'MILD', time: 'DAWN', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'WARM', time: 'DAWN', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'WARM', time: 'DUSK', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'WARM', time: 'DAWN', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'HOT', time: 'DAWN', items: ['TANK_TOP', 'SHORT_SHORTS'] },
  { weather: 'CLEAR', bracket: 'HOT', time: 'DUSK', items: ['TANK_TOP', 'SHORT_SHORTS'] },
  { weather: 'RAIN', bracket: 'HOT', time: 'DAWN', items: ['BASEBALL_CAP', 'TANK_TOP', 'RAIN_JACKET', 'SHORT_SHORTS'] },
  { weather: 'CLEAR', bracket: 'WARM', time: 'MIDDAY', items: ['VISOR', 'SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES'] },
  { weather: 'CLEAR', bracket: 'HOT', time: 'MIDDAY', items: ['VISOR', 'TANK_TOP', 'SHORT_SHORTS', 'SUNGLASSES'] },
]
// END GENERATED CANONICAL CELLS

function outfitHash(itemKeys: string[]): string {
  const joined = [...itemKeys].sort().join('-')
  let h = 0
  for (let i = 0; i < joined.length; i++) {
    h = (Math.imul(h, 31) + joined.charCodeAt(i)) | 0
  }
  return (h >>> 0).toString(16).slice(-8).padStart(8, '0')
}

function matrixCells(gender: string): Array<Record<string, unknown>> {
  return CANONICAL_CELLS.map(c => {
    const signature = outfitHash(c.items)
    return {
      combination_id: `${gender}_${c.weather}_${c.bracket}_${c.time}_${signature}`,
      gender, weather: c.weather, temp_bracket: c.bracket, time_of_day: c.time,
      outfit_signature: signature, outfit_items: c.items
    }
  })
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'Method not allowed' }, 405)

    const body = await req.json().catch(() => ({})) as Record<string, unknown>
    const authHeader = req.headers.get('Authorization') ?? ''
    const sb = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    let userId: string | null = null
    // Service credential check: exact env-key match, a new-format
    // secret key, or a legacy JWT whose role claim is service_role
    // (the gateway has already validated the credential itself).
    const bearerToken = authHeader.replace(/^Bearer\s+/i, '')
    let isService = bearerToken === SUPABASE_SERVICE_ROLE_KEY
    if (!isService) {
      const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
      if (secretKeys && bearerToken && secretKeys.includes(bearerToken)) isService = true
    }
    if (!isService && bearerToken) {
      try {
        const part = bearerToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        const payload = JSON.parse(atob(part + '='.repeat((4 - part.length % 4) % 4)))
        isService = payload?.role === 'service_role'
      } catch { /* not a JWT */ }
    }
    if (isService) {
      userId = typeof body.user_id === 'string' ? body.user_id : null
      if (!userId) return jsonResponse({ error: 'user_id is required for service-role calls' }, 400)
    } else {
      const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
        global: { headers: { Authorization: authHeader } }
      })
      const { data, error } = await userClient.auth.getUser()
      if (error || !data.user) return jsonResponse({ error: 'Unauthorized' }, 401)
      userId = data.user.id
    }

    const { data: profile } = await sb
      .from('personal_profiles')
      .select('*')
      .eq('user_id', userId)
      .maybeSingle()
    if (!profile) return jsonResponse({ error: 'No personalization profile for this user' }, 404)
    if (profile.entitled !== true) {
      return jsonResponse({ error: 'not_entitled', message: 'The initial fill is a subscriber benefit.' }, 403)
    }

    const { count: refCount } = await sb
      .from('personal_reference_photos')
      .select('id', { count: 'exact', head: true })
      .eq('user_id', userId)
    if (!refCount || refCount < 1) {
      return jsonResponse({ error: 'references_required' }, 403)
    }

    const cells = matrixCells(profile.gender as string)
    const combinationIds = cells.map(c => c.combination_id as string)

    const { data: existingHeroes } = await sb
      .from('personal_heroes')
      .select('combination_id')
      .eq('user_id', userId)
      .in('combination_id', combinationIds)
    const haveHero = new Set((existingHeroes ?? []).map(r => r.combination_id as string))

    const { data: activeJobs } = await sb
      .from('personal_generation_jobs')
      .select('combination_id')
      .eq('user_id', userId)
      .in('combination_id', combinationIds)
      .in('status', ['QUEUED', 'PROCESSING'])
    const haveJob = new Set((activeJobs ?? []).map(r => r.combination_id as string))

    const rows = cells
      .filter(c => !haveHero.has(c.combination_id as string) && !haveJob.has(c.combination_id as string))
      .map(c => ({
        ...c,
        user_id: userId,
        kind: 'subscriber_fill',
        priority: PRIORITY_SUBSCRIBER_FILL,
        status: 'QUEUED'
      }))

    if (rows.length > 0) {
      const { error: insertError } = await sb.from('personal_generation_jobs').insert(rows)
      if (insertError) throw insertError
      const kick = fetch(`${SUPABASE_URL}/functions/v1/personal-hero-worker`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ drain: 2 })
      }).catch(e => console.error('[start-personal-fill] worker kick failed:', e))
      // @ts-ignore EdgeRuntime is a Supabase runtime global
      if (typeof EdgeRuntime !== 'undefined') { EdgeRuntime.waitUntil(kick) }
    }

    return jsonResponse({
      ok: true,
      user_id: userId,
      matrix_cells: cells.length,
      enqueued: rows.length,
      skipped_existing_hero: haveHero.size,
      skipped_active_job: haveJob.size
    })
  } catch (error) {
    console.error('[start-personal-fill] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
