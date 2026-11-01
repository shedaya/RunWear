// Supabase Edge Function: start-personal-fill (RW-14, 2026-10-31)
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
const TEMP_BRACKETS = ['FREEZING', 'COLD', 'COOL', 'MILD', 'WARM', 'HOT']

// Canonical outfits per bracket x weather (engine ClothingItem keys),
// mirroring the canonical recommendations the generic pool's prompts
// were built from (scripts/queue-images.js OUTFITS / RAIN_OUTFITS,
// mapped to item keys).
const CANONICAL_OUTFITS: Record<string, Record<string, string[]>> = {
  CLEAR: {
    FREEZING: ['LONG_SLEEVE_THERMAL', 'INSULATED_JACKET', 'THERMAL_TIGHTS', 'THERMAL_BEANIE', 'MITTENS', 'NECK_GAITER'],
    COLD: ['LONG_SLEEVE_THERMAL', 'INSULATED_JACKET', 'THERMAL_TIGHTS', 'LIGHT_BEANIE', 'THERMAL_GLOVES', 'NECK_GAITER'],
    COOL: ['LONG_SLEEVE_LIGHT', 'LIGHT_JACKET', 'LIGHT_TIGHTS', 'HEADBAND'],
    MILD: ['LONG_SLEEVE_LIGHT', 'SHORTS'],
    WARM: ['SHORT_SLEEVE', 'SHORTS', 'BASEBALL_CAP'],
    HOT: ['TANK_TOP', 'SHORT_SHORTS', 'SUNGLASSES']
  },
  RAIN: {
    FREEZING: ['LONG_SLEEVE_THERMAL', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'THERMAL_BEANIE', 'THERMAL_GLOVES', 'NECK_GAITER'],
    COLD: ['LONG_SLEEVE_THERMAL', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'LIGHT_BEANIE', 'THERMAL_GLOVES'],
    COOL: ['LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'LIGHT_TIGHTS', 'BASEBALL_CAP', 'LIGHT_GLOVES'],
    MILD: ['LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'SHORTS', 'BASEBALL_CAP'],
    WARM: ['SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS', 'BASEBALL_CAP'],
    HOT: ['SHORT_SLEEVE', 'RAIN_JACKET', 'SHORT_SHORTS', 'BASEBALL_CAP']
  }
}

function outfitHash(itemKeys: string[]): string {
  const joined = [...itemKeys].sort().join('-')
  let h = 0
  for (let i = 0; i < joined.length; i++) {
    h = (Math.imul(h, 31) + joined.charCodeAt(i)) | 0
  }
  return (h >>> 0).toString(16).slice(-8).padStart(8, '0')
}

function matrixCells(gender: string): Array<Record<string, unknown>> {
  const cells: Array<Record<string, unknown>> = []
  const push = (weather: string, bracket: string, time: string) => {
    const items = CANONICAL_OUTFITS[weather][bracket]
    const signature = outfitHash(items)
    cells.push({
      combination_id: `${gender}_${weather}_${bracket}_${time}_${signature}`,
      gender, weather, temp_bracket: bracket, time_of_day: time,
      outfit_signature: signature, outfit_items: items
    })
  }
  for (const bracket of TEMP_BRACKETS) {
    push('CLEAR', bracket, 'DAWN')
    push('CLEAR', bracket, 'DUSK')
    push('RAIN', bracket, 'DAWN')
  }
  push('CLEAR', 'WARM', 'MIDDAY')
  push('CLEAR', 'HOT', 'MIDDAY')
  return cells
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
