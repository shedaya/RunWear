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
//                       rejected. (b) If the registered SET changed —
//                       different paths, different count, or a stored
//                       object whose stamp (etag / updated_at) differs
//                       from what was recorded, i.e. the photo was
//                       overwritten in place — every cell holding a
//                       look is marked regen_status='pending' and its
//                       verified heroes KEEP SERVING until a
//                       replacement generated from the new references
//                       has been delivered; the swap then materializes
//                       in get-personal-hero / the request action and
//                       the prior heroes are retired at that point,
//                       never before (RW-16.12 Lane C — Sol's ruling
//                       2026-10-31: "Keep my current looks serving
//                       until the replacements are ready"). Immediate
//                       wipe remains exclusive to the delete-my-photos
//                       function. The response reports
//                       references_changed + heroes_invalidated (kept
//                       for response compatibility, now always 0) +
//                       heroes_kept_serving +
//                       cells_pending_regeneration.
//   request (default)   { combination_id, outfit_items: ["SHORTS", ...] }
//                       Validates consent + >=1 reference + allowance
//                       (entitled OR free_generations_used < 5), then
//                       enqueues a personal_generation_jobs row unless a
//                       verified hero or an active job already exists
//                       for the cell. Kicks the worker via waitUntil.
//                       (b) A cell that was delivered before
//                       (personal_cells.ever_delivered) is a
//                       REGENERATION of a look the user already has,
//                       not a genuinely new cell: no allowance gate,
//                       and the job is enqueued as kind='regeneration',
//                       which the worker never charges (see below).
//   flag_hero           { hero_id, reason? } — (c) "This doesn't look
//                       like me" (spec §4) for PERSONAL heroes (the
//                       pool's hero_flags system covers pool rows
//                       only). The hero must belong to the caller; a
//                       missing hero and someone else's hero are the
//                       same 404. The hero is retired in the same call
//                       (row deleted -> version bump -> it stops being
//                       served on the very next get-personal-hero), a
//                       snapshot row lands in personal_hero_flags, and
//                       a kind='regeneration' job is queued for the
//                       cell unless an active job already covers it.
//
// Free-tier accounting: the counter counts VERIFIED heroes delivered
// for GENUINELY NEW cells (incremented by the worker on gate pass for
// a free_on_demand job whose cell was never counted), so a generation
// that fails the gate twice never burns a free user's allowance, and
// regenerating an already-delivered cell never charges twice — the
// plan's allowance is "today's cell plus the next four genuinely new
// cells encountered", and spec J4 rules replacement regenerations
// consume no allowance.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const FREE_GENERATION_LIMIT = 5
const PRIORITY_FREE_ON_DEMAND = 20
const PRIORITY_REGENERATION = 20

// (c) Flag reasons: the personal-specific default plus the pool flag
// vocabulary (hero_flags: looks_broken / inappropriate /
// outfit_mismatch), so both systems speak the same language.
const FLAG_REASONS = new Set([
  'doesnt_look_like_me', 'looks_broken', 'inappropriate', 'outfit_mismatch'
])

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

// RW-16.30: the photo-set hash — sha256 hex over the sorted
// "path@stamp" entries of a reference set. Stored on the profile as
// the epoch's cache key when a changed set opens a new epoch.
async function photoSetHash(entries: string[]): Promise<string> {
  const text = [...entries].sort().join('\n')
  const digest = await crypto.subtle.digest(
    'SHA-256', new TextEncoder().encode(text))
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('')
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

// RW-16.12 Lane C: retire a cell's prior-generation heroes once a
// replacement is serving. Rows are deleted first (serving reads
// rows, so the prior hero stops being served in the same instant
// the fresh one takes over — never a window with neither); storage
// objects are removed right after, best-effort (an orphaned object
// is unreachable: serving signs URLs from rows, never from
// listings, and the delete-my-photos prefix sweep collects
// orphans). The cell is then marked current. Mirrored in
// get-personal-hero's serve path.
async function retirePriorHeroes(
  sb: ReturnType<typeof serviceClient>, userId: string, combinationId: string,
  priorHeroes: Array<{ id: string, storage_path: string }>
) {
  if (priorHeroes.length > 0) {
    const { error: deleteError } = await sb
      .from('personal_heroes')
      .delete()
      .in('id', priorHeroes.map(h => h.id))
      .eq('user_id', userId)
    if (deleteError) throw deleteError
    const { error: removeError } = await sb.storage
      .from('personal-heroes')
      .remove(priorHeroes.map(h => h.storage_path))
    if (removeError) {
      console.error('[request-personal-generation] prior hero object removal failed:', removeError)
    }
  }
  const { error: cellError } = await sb
    .from('personal_cells')
    .update({
      regen_status: 'current', regen_pending_since: null, regen_error: null,
      updated_at: new Date().toISOString()
    })
    .eq('user_id', userId)
    .eq('combination_id', combinationId)
  if (cellError) throw cellError
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
      // Canonical field is `paths`. `storage_paths` is accepted as a
      // legacy alias because the shipped iOS build (9) sends it — the
      // name came from an error in the backend build report and caused
      // RW-15 (every registration 400'd). If both are present, the
      // canonical `paths` wins.
      const paths = body.paths ?? body.storage_paths
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
      // Objects must actually exist in the private bucket; capture a
      // change-detection stamp for each (etag when storage provides
      // one, else the object's updated_at as epoch ms).
      const { data: listing, error: listError } = await sb.storage
        .from('personal-heroes')
        .list(`${user.id}/refs`, { limit: 10 })
      if (listError) throw listError
      const stampOf = (o: Record<string, unknown>): string => {
        // Composite token: identity of the object (etag when storage
        // provides one, else updated_at) PLUS its size — an in-place
        // overwrite changes at least one of them.
        const meta = o.metadata as Record<string, unknown> | undefined
        const etag = meta?.etag
        const size = typeof meta?.size === 'number' ? meta.size : -1
        if (typeof etag === 'string' && etag.length > 0) return `etag:${etag}|size:${size}`
        const updated = typeof o.updated_at === 'string' ? Date.parse(o.updated_at) : NaN
        return Number.isNaN(updated) ? `unknown|size:${size}` : `ms:${updated}|size:${size}`
      }
      const currentStamps = new Map<string, string>()
      for (const o of listing ?? []) {
        currentStamps.set(`${user.id}/refs/${o.name}`, stampOf(o as Record<string, unknown>))
      }
      for (const p of paths as string[]) {
        if (!currentStamps.has(p)) {
          return jsonResponse({ error: `Reference object not found in storage: ${p}` }, 400)
        }
      }

      // (b) Change detection against the previously registered set,
      // position by position: a different path at a position, a
      // different count, or a different stamp at the same path (the
      // photo was overwritten in place) all mean the reference SET
      // changed. Rows registered before object_stamp existed carry
      // NULL; adopt the current stamp as their baseline rather than
      // treating a pre-existing set as changed.
      const { data: oldRefs } = await sb
        .from('personal_reference_photos')
        .select('storage_path, object_stamp')
        .eq('user_id', user.id)
        .order('position', { ascending: true })
      let referencesChanged = false
      if (oldRefs && oldRefs.length > 0) {
        if (oldRefs.length !== (paths as string[]).length) {
          referencesChanged = true
        } else {
          for (let i = 0; i < (paths as string[]).length; i++) {
            const old = oldRefs[i]
            if (old.storage_path !== (paths as string[])[i]) { referencesChanged = true; break }
            if (old.object_stamp !== null &&
                old.object_stamp !== currentStamps.get((paths as string[])[i])) {
              referencesChanged = true; break
            }
          }
        }
      }

      // RW-16.12 Lane C — KEEP-SERVING on reference change (Sol's
      // ruling, 2026-10-31). The pre-ruling behavior retired every
      // verified hero the instant a changed set registered, which
      // left the user with zero servable looks until regenerations
      // landed (field case: two delivered MILD looks gone in the
      // same second as the photo replace, RW-16.12 trace §3). Now
      // NOTHING is deleted here: every cell that holds a look — a
      // hero row, or ever_delivered from an earlier delivery — is
      // marked regen_status='pending' with regen_pending_since
      // stamped at this change. get-personal-hero keeps serving
      // heroes created before that instant until a hero created
      // at/after it (one generated from the NEW references) exists;
      // the swap then materializes there / in the request action
      // and the prior heroes are retired at that point, never
      // before. A cell whose regeneration fails terminally records
      // regen_status='failed' + regen_error and keeps its prior
      // hero (see the request action). Delete-my-photos is
      // untouched by this ruling and remains an immediate, total
      // wipe, invoked explicitly through its own function.
      let heroesKeptServing = 0
      let cellsPendingRegeneration = 0
      if (referencesChanged) {
        const pendingSince = new Date().toISOString()
        const { data: heroRows } = await sb
          .from('personal_heroes')
          .select('combination_id')
          .eq('user_id', user.id)
        heroesKeptServing = heroRows?.length ?? 0
        const { data: deliveredCells } = await sb
          .from('personal_cells')
          .select('combination_id')
          .eq('user_id', user.id)
          .eq('ever_delivered', true)
        const cellIds = new Set<string>()
        for (const c of deliveredCells ?? []) cellIds.add(c.combination_id as string)
        for (const h of heroRows ?? []) cellIds.add(h.combination_id as string)
        if (cellIds.size > 0) {
          const { data: marked, error: markError } = await sb
            .from('personal_cells')
            .update({
              regen_status: 'pending',
              regen_pending_since: pendingSince,
              regen_error: null,
              updated_at: pendingSince
            })
            .eq('user_id', user.id)
            .in('combination_id', [...cellIds])
            .select('combination_id')
          if (markError) throw markError
          cellsPendingRegeneration = marked?.length ?? 0
        }
      }

      // Replace the reference set wholesale (positions 1..3),
      // recording the stamps for the next change detection.
      await sb.from('personal_reference_photos').delete().eq('user_id', user.id)
      const rows = (paths as string[]).map((p, i) => ({
        user_id: user.id, storage_path: p, position: i + 1,
        object_stamp: currentStamps.get(p) ?? null
      }))
      const { error: insertError } = await sb.from('personal_reference_photos').insert(rows)
      if (insertError) throw insertError

      // RW-16.30 epoch: a changed set opens a new photo-set epoch
      // for THIS user only. Jobs enqueued from now on carry the new
      // epoch; the worker supersedes unprocessed prior-epoch jobs
      // (no spend, no delivery from the old face), and keep-serving
      // (Lane C above) still governs the heroes already banked.
      let photoEpoch = (profile.photo_epoch as number | undefined) ?? 0
      if (referencesChanged) {
        photoEpoch += 1
        const setHash = await photoSetHash(
          (paths as string[]).map(p => `${p}@${currentStamps.get(p) ?? ''}`))
        const { error: epochError } = await sb
          .from('personal_profiles')
          .update({
            photo_epoch: photoEpoch, photo_set_hash: setHash,
            updated_at: new Date().toISOString()
          })
          .eq('user_id', user.id)
        if (epochError) throw epochError
      }
      return jsonResponse({
        ok: true, references: rows.length,
        references_changed: referencesChanged,
        photo_epoch: photoEpoch,
        // Kept for response compatibility with shipped clients; the
        // keep-serving ruling means a reference change now retires
        // nothing at registration time.
        heroes_invalidated: 0,
        heroes_kept_serving: heroesKeptServing,
        cells_pending_regeneration: cellsPendingRegeneration
      })
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

      // --- RW-16.12 Lane C: keep-serving state for this cell ---
      // Load the cell's regeneration state and its heroes. A cell in
      // 'pending'/'failed' (references changed after its heroes were
      // generated) can hold a PRIOR hero — created before
      // regen_pending_since, made from the previous references —
      // which keeps serving under the ruling, and/or a FRESH hero —
      // created at/after regen_pending_since, generated from the
      // current references — which completes the swap.
      const { data: cellRow } = await sb
        .from('personal_cells')
        .select('ever_delivered, regen_status, regen_pending_since, regen_error')
        .eq('user_id', user.id)
        .eq('combination_id', cell.combinationId)
        .maybeSingle()
      const regenState = (cellRow?.regen_status as string | undefined) ?? 'current'
      const pendingSince = typeof cellRow?.regen_pending_since === 'string'
        ? cellRow.regen_pending_since : null
      const regenPending = (regenState === 'pending' || regenState === 'failed') &&
        pendingSince !== null
      const pendingSinceMs = pendingSince ? Date.parse(pendingSince) : NaN

      const { data: heroRows } = await sb
        .from('personal_heroes')
        .select('id, variant, storage_path, created_at')
        .eq('user_id', user.id)
        .eq('combination_id', cell.combinationId)
      const allHeroes = heroRows ?? []
      const freshHeroes = regenPending
        ? allHeroes.filter(h => Date.parse(h.created_at as string) >= pendingSinceMs)
        : allHeroes
      const priorHeroes = regenPending
        ? allHeroes.filter(h => Date.parse(h.created_at as string) < pendingSinceMs)
        : []

      // A replacement has been delivered since the reference change:
      // materialize the swap — retire the prior heroes now (never
      // before this moment) and mark the cell current — then the
      // cell is simply available.
      if (regenPending && freshHeroes.length > 0) {
        await retirePriorHeroes(sb, user.id, cell.combinationId, priorHeroes)
        return jsonResponse({ status: 'already_available', combination_id: cell.combinationId })
      }

      // Already have a verified hero for this cell and no reference
      // change outstanding? Nothing to do. (A pending cell holding
      // only a prior hero falls through: this request is what
      // (re)enqueues its regeneration, while the prior hero keeps
      // serving in the meantime.)
      if (!regenPending && allHeroes.length > 0) {
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

      // (b) Was this cell already delivered once? Then this request is
      // a REGENERATION of a look the user already has — their heroes
      // for it were retired by a reference change (register_references)
      // or a flag (flag_hero), or (RW-16.12 Lane C) are still serving
      // as prior heroes while the replacement is outstanding. It is
      // not a genuinely new cell, so the allowance gate does not
      // apply and the job kind is 'regeneration', which the worker
      // never charges against free_generations_used (plan §2.2: the
      // allowance counts genuinely new cells; spec J4: replacement
      // regenerations cost no allowance).
      const isRegeneration = cellRow?.ever_delivered === true || regenPending

      // RW-16.12 Lane C: if this cell's outstanding regeneration
      // cycle already died — the latest job since the change is
      // FAILED, nothing is in flight, and no replacement has been
      // delivered — record the failure on the cell before this
      // request retries the cycle below. The prior hero keeps
      // serving throughout, and regen_error stays on the record
      // until a replacement actually lands, so the staleness is
      // never silent or permanent: this request (or the next
      // reference change) is the retry.
      if (regenPending && regenState === 'pending') {
        const { data: lastJob } = await sb
          .from('personal_generation_jobs')
          .select('status, error_message')
          .eq('user_id', user.id)
          .eq('combination_id', cell.combinationId)
          .gte('requested_at', pendingSince)
          .order('requested_at', { ascending: false })
          .limit(1)
        if (lastJob && lastJob.length > 0 && lastJob[0].status === 'FAILED') {
          await sb
            .from('personal_cells')
            .update({
              regen_status: 'failed',
              regen_error: (lastJob[0].error_message as string | null) ?? 'generation_failed',
              updated_at: new Date().toISOString()
            })
            .eq('user_id', user.id)
            .eq('combination_id', cell.combinationId)
        }
      }

      // Allowance: entitled users always; free users under the
      // lifetime cap — for genuinely new cells only.
      const entitled = profile.entitled === true
      if (!isRegeneration && !entitled &&
          (profile.free_generations_used ?? 0) >= FREE_GENERATION_LIMIT) {
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
          kind: isRegeneration ? 'regeneration' : 'free_on_demand',
          priority: isRegeneration ? PRIORITY_REGENERATION : PRIORITY_FREE_ON_DEMAND,
          epoch: (profile.photo_epoch as number | undefined) ?? 0,
          status: 'QUEUED'
        })
        .select('id')
        .single()
      if (jobError) throw jobError

      // RW-16.12 Lane C: a request that retries a 'failed' cell puts
      // its cycle back in flight ('pending'); regen_error is kept
      // until a replacement lands and clears it at the swap.
      if (regenPending && regenState === 'failed') {
        await sb
          .from('personal_cells')
          .update({ regen_status: 'pending', updated_at: new Date().toISOString() })
          .eq('user_id', user.id)
          .eq('combination_id', cell.combinationId)
      }

      kickWorker()
      return jsonResponse({
        status: 'queued', job_id: job.id, combination_id: cell.combinationId,
        entitled, free_generations_used: profile.free_generations_used,
        regeneration: isRegeneration,
        regen_status: regenPending ? 'pending' : 'current',
        kept_serving: regenPending && priorHeroes.length > 0
      })
    }

    // ---------------- flag_hero (c) ----------------
    if (action === 'flag_hero') {
      const heroId = body.hero_id
      if (typeof heroId !== 'string' ||
          !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(heroId)) {
        return jsonResponse({ error: 'hero_id must be a hero UUID' }, 400)
      }
      const reason = typeof body.reason === 'string' ? body.reason : 'doesnt_look_like_me'
      if (!FLAG_REASONS.has(reason)) {
        return jsonResponse({ error: `Unknown flag reason: ${reason}` }, 400)
      }

      // The hero must belong to the CALLER (user id from the JWT
      // only). A hero that does not exist and a hero that belongs to
      // someone else are deliberately the same answer: 404.
      const { data: hero } = await sb
        .from('personal_heroes')
        .select('id, combination_id, variant, storage_path, outfit_items')
        .eq('id', heroId)
        .eq('user_id', user.id)
        .maybeSingle()
      if (!hero) return jsonResponse({ error: 'hero_not_found' }, 404)

      // Retire the hero FIRST: deleting the row stops it being served
      // on the very next get-personal-hero call and bumps the cell
      // version via the existing trigger. The storage object goes
      // right after; if that removal failed, the object is orphaned
      // but unreachable (serving signs URLs from rows, never from
      // listings) and the delete-my-photos prefix sweep collects it.
      const { error: heroDeleteError } = await sb
        .from('personal_heroes')
        .delete()
        .eq('id', hero.id)
        .eq('user_id', user.id)
      if (heroDeleteError) throw heroDeleteError
      const { error: removeError } = await sb.storage
        .from('personal-heroes')
        .remove([hero.storage_path as string])
      if (removeError) {
        console.error('[request-personal-generation] flag_hero object removal failed:', removeError)
      }

      // The flag record (snapshot — the hero row no longer exists).
      const { error: flagError } = await sb
        .from('personal_hero_flags')
        .insert({
          user_id: user.id,
          hero_id: hero.id,
          combination_id: hero.combination_id,
          variant: hero.variant,
          storage_path: hero.storage_path,
          reason
        })
      if (flagError) throw flagError

      // Queue the cell's regeneration with the same treatment as (b),
      // unless an active job already covers the cell — that job will
      // refill a variant through the same gate, from the current
      // references.
      const { data: active } = await sb
        .from('personal_generation_jobs')
        .select('id')
        .eq('user_id', user.id)
        .eq('combination_id', hero.combination_id)
        .in('status', ['QUEUED', 'PROCESSING'])
        .limit(1)
      let jobId: string | null = null
      if (!active || active.length === 0) {
        const parts = (hero.combination_id as string).split('_')
        const { data: job, error: jobError } = await sb
          .from('personal_generation_jobs')
          .insert({
            user_id: user.id,
            combination_id: hero.combination_id,
            gender: parts[0], weather: parts[1],
            temp_bracket: parts[2], time_of_day: parts[3],
            outfit_signature: parts[4],
            outfit_items: hero.outfit_items,
            kind: 'regeneration',
            priority: PRIORITY_REGENERATION,
            epoch: (profile.photo_epoch as number | undefined) ?? 0,
            status: 'QUEUED'
          })
          .select('id')
          .single()
        if (jobError) throw jobError
        jobId = job.id
        kickWorker()
      }

      return jsonResponse({
        ok: true,
        hero_id: hero.id,
        combination_id: hero.combination_id,
        regeneration_queued: jobId !== null,
        job_id: jobId
      })
    }

    return jsonResponse({ error: `Unknown action: ${action}` }, 400)
  } catch (error) {
    console.error('[request-personal-generation] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
