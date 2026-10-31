// Supabase Edge Function: request-personal-generation (RW-14, 2026-10-31)
//
// User-facing entry point for "Make it me" personalization. All actions
// require a user JWT; the caller's user id comes ONLY from the verified
// token, never from the request body.
//
// Actions (POST JSON { "action": ... }):
//   record_consent      { consent_version, attested_own_likeness,
//                         attested_adult, gender? }
//                       Records the §4 consent + own-likeness/18+
//                       attestation. Both attestations must be true.
//   register_references { paths: ["<uid>/refs/ref-1.jpg", ...] } (1..3)
//                       Registers reference photos the client already
//                       uploaded to the private 'personal-heroes' bucket
//                       under its own prefix. Paths outside the caller's
//                       prefix, or objects that do not exist, are
//                       rejected.
//   request (default)   { combination_id, outfit_items: ["SHORTS", ...] }
//                       Validates consent + >=1 reference + allowance
//                       (entitled OR free_generations_used < 5), then
//                       enqueues a personal_generation_jobs row unless a
//                       verified hero or an active job already exists
//                       for the cell. Kicks the worker via waitUntil.
//
// Free-tier accounting: the counter counts VERIFIED heroes delivered
// (incremented by the worker on gate pass), so a generation that fails
// the gate twice never burns a free user's allowance.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const FREE_GENERATION_LIMIT = 5
const PRIORITY_FREE_ON_DEMAND = 20

const GENDERS = ['MALE', 'FEMALE', 'UNISEX']
const WEATHERS = ['CLEAR', 'CLOUDY', 'RAIN', 'SNOW']
const TEMP_BRACKETS = ['FREEZING', 'COLD', 'COOL', 'MILD', 'WARM', 'HOT']
const TIMES_OF_DAY = ['DAWN', 'MIDDAY', 'DUSK', 'NIGHT']
const KNOWN_ITEMS = new Set([
  'TANK_TOP', 'SHORT_SLEEVE', 'LONG_SLEEVE_LIGHT', 'LONG_SLEEVE_THERMAL',
  'QUARTER_ZIP', 'FLEECE_PULLOVER', 'HALF_ZIP_THERMAL',
  'LIGHT_VEST', 'WINDBREAKER', 'LIGHT_JACKET', 'RAIN_JACKET', 'INSULATED_JACKET',
  'SHORT_SHORTS', 'SHORTS', 'CAPRIS', 'LIGHT_TIGHTS', 'THERMAL_TIGHTS',
  'VISOR', 'BASEBALL_CAP', 'HEADBAND', 'LIGHT_BEANIE', 'THERMAL_BEANIE', 'BALACLAVA',
  'LIGHT_GLOVES', 'THERMAL_GLOVES', 'MITTENS',
  'SUNGLASSES', 'SUNSCREEN', 'REFLECTIVE_GEAR', 'NECK_GAITER'
])

// Outfit signature: the engine's hash — Java/Kotlin String.hashCode()
// (31-based, UTF-16 code units) of the sorted item keys joined with '-',
// rendered as unsigned lowercase hex, last 8 chars, zero-padded.
// Mirrors OutfitCombination.fromConditions in the shared engine.
export function outfitHash(itemKeys: string[]): string {
  const joined = [...itemKeys].sort().join('-')
  let h = 0
  for (let i = 0; i < joined.length; i++) {
    h = (Math.imul(h, 31) + joined.charCodeAt(i)) | 0
  }
  return (h >>> 0).toString(16).slice(-8).padStart(8, '0')
}

type Cell = {
  gender: string, weather: string, tempBracket: string,
  timeOfDay: string, signature: string, combinationId: string
}

function parseCombinationId(raw: unknown): Cell | null {
  if (typeof raw !== 'string') return null
  const parts = raw.split('_')
  if (parts.length !== 5) return null
  const [gender, weather, tempBracket, timeOfDay, signature] = parts
  if (!GENDERS.includes(gender)) return null
  if (!WEATHERS.includes(weather)) return null
  if (!TEMP_BRACKETS.includes(tempBracket)) return null
  if (!TIMES_OF_DAY.includes(timeOfDay)) return null
  if (!/^[0-9a-f]{8}$/.test(signature)) return null
  return { gender, weather, tempBracket, timeOfDay, signature, combinationId: raw }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

function serviceClient() {
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
}

async function getCaller(req: Request) {
  const authHeader = req.headers.get('Authorization') ?? ''
  const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: authHeader } }
  })
  const { data, error } = await userClient.auth.getUser()
  if (error || !data.user) return null
  return data.user
}

async function ensureProfile(sb: ReturnType<typeof serviceClient>, userId: string) {
  const { data } = await sb
    .from('personal_profiles')
    .select('*')
    .eq('user_id', userId)
    .maybeSingle()
  if (data) return data
  const { data: created, error } = await sb
    .from('personal_profiles')
    .insert({ user_id: userId })
    .select('*')
    .single()
  if (error) throw error
  return created
}

function kickWorker() {
  // Fire-and-forget: process queued personal jobs promptly instead of
  // waiting for the 5-minute drain cron. waitUntil keeps the fetch
  // alive after this response returns.
  const kick = fetch(`${SUPABASE_URL}/functions/v1/personal-hero-worker`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ drain: 2 })
  }).then(r => r.text()).then(t => console.log('[request-personal-generation] worker kick:', t.slice(0, 200)))
    .catch(e => console.error('[request-personal-generation] worker kick failed:', e))
  // @ts-ignore EdgeRuntime is a Supabase runtime global
  if (typeof EdgeRuntime !== 'undefined') { EdgeRuntime.waitUntil(kick) }
}

serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'Method not allowed' }, 405)

    const user = await getCaller(req)
    if (!user) return jsonResponse({ error: 'Unauthorized' }, 401)

    const body = await req.json().catch(() => ({})) as Record<string, unknown>
    const action = typeof body.action === 'string' ? body.action : 'request'
    const sb = serviceClient()
    const profile = await ensureProfile(sb, user.id)

    // ---------------- record_consent ----------------
    if (action === 'record_consent') {
      const consentVersion = body.consent_version
      const ownLikeness = body.attested_own_likeness === true
      const adult = body.attested_adult === true
      if (typeof consentVersion !== 'string' || consentVersion.length === 0) {
        return jsonResponse({ error: 'consent_version is required' }, 400)
      }
      if (!ownLikeness || !adult) {
        return jsonResponse({
          error: 'Attestation required: the photos must be of you, and you must be 18 or older.'
        }, 400)
      }
      const gender = typeof body.gender === 'string' && GENDERS.includes(body.gender)
        ? body.gender : profile.gender
      const { error: consentError } = await sb
        .from('personal_consents')
        .insert({
          user_id: user.id,
          consent_version: consentVersion,
          attested_own_likeness: true,
          attested_adult: true
        })
      if (consentError) throw consentError
      const { error: profileError } = await sb
        .from('personal_profiles')
        .update({ personalization_on: true, gender, updated_at: new Date().toISOString() })
        .eq('user_id', user.id)
      if (profileError) throw profileError
      return jsonResponse({ ok: true, personalization_on: true, gender })
    }

    // ---------------- register_references ----------------
    if (action === 'register_references') {
      const paths = body.paths
      if (!Array.isArray(paths) || paths.length < 1 || paths.length > 3 ||
          !paths.every(p => typeof p === 'string')) {
        return jsonResponse({ error: 'paths must be an array of 1 to 3 storage paths' }, 400)
      }
      const prefix = `${user.id}/refs/`
      for (const p of paths as string[]) {
        if (!p.startsWith(prefix)) {
          return jsonResponse({ error: `Reference path is outside your own folder: ${p}` }, 400)
        }
      }
      // Objects must actually exist in the private bucket.
      const { data: listing, error: listError } = await sb.storage
        .from('personal-heroes')
        .list(`${user.id}/refs`, { limit: 10 })
      if (listError) throw listError
      const existing = new Set((listing ?? []).map(o => `${user.id}/refs/${o.name}`))
      for (const p of paths as string[]) {
        if (!existing.has(p)) {
          return jsonResponse({ error: `Reference object not found in storage: ${p}` }, 400)
        }
      }
      // Replace the reference set wholesale (positions 1..3).
      await sb.from('personal_reference_photos').delete().eq('user_id', user.id)
      const rows = (paths as string[]).map((p, i) => ({
        user_id: user.id, storage_path: p, position: i + 1
      }))
      const { error: insertError } = await sb.from('personal_reference_photos').insert(rows)
      if (insertError) throw insertError
      return jsonResponse({ ok: true, references: rows.length })
    }

    // ---------------- request (default) ----------------
    if (action === 'request') {
      const cell = parseCombinationId(body.combination_id)
      if (!cell) return jsonResponse({ error: 'Invalid combination_id' }, 400)

      const items = body.outfit_items
      if (!Array.isArray(items) || items.length === 0 ||
          !items.every(i => typeof i === 'string' && KNOWN_ITEMS.has(i))) {
        return jsonResponse({ error: 'outfit_items must be a non-empty array of known item keys' }, 400)
      }
      const itemKeys = items as string[]
      if (outfitHash(itemKeys) !== cell.signature) {
        return jsonResponse({
          error: 'outfit_items do not hash to the combination_id outfit signature'
        }, 400)
      }

      // Consent gate: a current consent record with both attestations.
      const { data: consent } = await sb
        .from('personal_consents')
        .select('id')
        .eq('user_id', user.id)
        .eq('attested_own_likeness', true)
        .eq('attested_adult', true)
        .limit(1)
      if (!consent || consent.length === 0) {
        return jsonResponse({ error: 'consent_required', message: 'Consent and attestation must be recorded before generation.' }, 403)
      }

      // At least one registered reference photo.
      const { count: refCount } = await sb
        .from('personal_reference_photos')
        .select('id', { count: 'exact', head: true })
        .eq('user_id', user.id)
      if (!refCount || refCount < 1) {
        return jsonResponse({ error: 'references_required', message: 'Upload and register at least one reference photo first.' }, 403)
      }

      // Already have a verified hero for this cell? Nothing to do.
      const { data: hero } = await sb
        .from('personal_heroes')
        .select('id, variant')
        .eq('user_id', user.id)
        .eq('combination_id', cell.combinationId)
        .limit(1)
      if (hero && hero.length > 0) {
        return jsonResponse({ status: 'already_available', combination_id: cell.combinationId })
      }

      // An active job already covers this cell? Report it, don't dup.
      const { data: active } = await sb
        .from('personal_generation_jobs')
        .select('id, status')
        .eq('user_id', user.id)
        .eq('combination_id', cell.combinationId)
        .in('status', ['QUEUED', 'PROCESSING'])
        .limit(1)
      if (active && active.length > 0) {
        return jsonResponse({ status: 'already_queued', job_id: active[0].id, combination_id: cell.combinationId })
      }

      // Allowance: entitled users always; free users under the lifetime cap.
      const entitled = profile.entitled === true
      if (!entitled && (profile.free_generations_used ?? 0) >= FREE_GENERATION_LIMIT) {
        return jsonResponse({
          error: 'allowance_exhausted',
          message: `Free personalized generations used up (${FREE_GENERATION_LIMIT}).`,
          free_generations_used: profile.free_generations_used
        }, 403)
      }

      const { data: job, error: jobError } = await sb
        .from('personal_generation_jobs')
        .insert({
          user_id: user.id,
          combination_id: cell.combinationId,
          gender: cell.gender,
          weather: cell.weather,
          temp_bracket: cell.tempBracket,
          time_of_day: cell.timeOfDay,
          outfit_signature: cell.signature,
          outfit_items: itemKeys,
          // prompt is built by the worker from the canonical template
          kind: 'free_on_demand',
          priority: PRIORITY_FREE_ON_DEMAND,
          status: 'QUEUED'
        })
        .select('id')
        .single()
      if (jobError) throw jobError

      kickWorker()
      return jsonResponse({
        status: 'queued', job_id: job.id, combination_id: cell.combinationId,
        entitled, free_generations_used: profile.free_generations_used
      })
    }

    return jsonResponse({ error: `Unknown action: ${action}` }, 400)
  } catch (error) {
    console.error('[request-personal-generation] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
