// Supabase Edge Function: get-personal-hero (RW-14, 2026-10-31)
//
// The serving contract for personalized heroes. POST with a user JWT:
//   { "combination_id": "MALE_CLEAR_MILD_DAWN_<8-hex outfit signature>" }
// Response:
//   found:  { "found": true, "combination_id", "hero_id", "variant",
//             "image_url": <signed URL, 1h>, "version": <cell version>,
//             "look_state", "look_reason" }
//   miss:   { "found": false, "combination_id", "version": <n|null>,
//             "look_state", "look_reason" }
// A miss means: serve the generic pool hero (the client fallback). The
// version marker changes whenever the cell's verified hero set changes
// (variant added or removed), so clients can invalidate cached heroes.
//
// RW-16.16 — LOOK STATE. Both payloads additionally carry the cell's
// personal-look state for the CALLER (free users included — the state
// derives from the caller's jobs/cell/heroes, none of which requires
// entitlement):
//   look_state: "none"    — no look exists and none is in flight or
//                           terminally failed for this cell.
//               "making"  — a look job for this cell is QUEUED or
//                           PROCESSING (replenishment top-ups do not
//                           count: the served hero is the state then).
//               "ready"   — a verified hero serves (found: true) and
//                           no look job is in flight and the cell is
//                           not in a failed regeneration cycle.
//               "failed"  — the latest look attempt for this cell
//                           ended FAILED and no replacement is in
//                           flight — including a keep-serving cell
//                           whose regeneration cycle died (found may
//                           still be true: the prior hero keeps
//                           serving, and the failure is the
//                           REPLACEMENT's, surfaced so the user can
//                           retry it explicitly).
//   look_reason: for "failed", a coarse CLASS only — never the raw
//               gate/moderation string:
//               "quality_refused" (the verification gate refused the
//               generated outfit), "service_refused" (the generation
//               service refused the input/output, E005-class), or
//               "error" (anything else). Null in every other state.
// The state exists so a terminal outcome is never again invisible to
// the user (the G4-silence defect, RW-16.16 trace §4): the clients
// render it durably at the hero, driven by this field, not by
// session memory. Derivation is read-time, from rows that already
// exist — no schema change, and the keep-serving partition above is
// untouched: state is computed AFTER the partition and any swap
// materialization, from the final regen_status / serving set.
//
// Side effect (replenishment hook, entitled users only): when a hero is
// served and the cell holds fewer than VARIANT_CAP verified variants,
// enqueue up to 2 replenishment candidate jobs for the cell — deduped
// against active jobs — so the cell keeps a fresh rotation. Free users
// never trigger replenishment.
//
// RW-16.12 Lane C — KEEP-SERVING (Sol's ruling, 2026-10-31: "Keep my
// current looks serving until the replacements are ready"). A cell
// whose references changed after its heroes were generated carries
// regen_status 'pending' (or 'failed' once its regeneration cycle
// died terminally) with regen_pending_since stamping the change.
// This function then serves ONLY the prior heroes (created before
// the change) until a replacement hero exists (created at/after the
// change); the first serve that sees a replacement materializes the
// swap — prior rows retired, cell marked current — so the client
// never observes a window with neither hero. While a cycle is
// 'pending' with nothing in flight, the serve path also guarantees a
// regeneration job exists (lazy enqueue, the same serve-path idiom
// as the replenishment hook): a client that finds a serving prior
// hero may never call the request action for this cell. A 'failed'
// cell keeps serving its prior hero and records the failure in
// regen_error; it is NOT auto-reenqueued here (its retry is an
// explicit request or the next reference change), so a gate-rejected
// reference set cannot loop paid generations on every serve. The
// replenishment hook is suppressed while a cell is pending/failed —
// the regeneration takes precedence and replenishment resumes once
// the cell is current again.

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

// RW-16.16: map a job/cell terminal error string onto the coarse
// reason CLASS the client may see. The raw strings name garments,
// enumerator listings, and provider error bodies — diagnostics, not
// user copy — so they never cross this boundary.
type LookReasonClass = 'quality_refused' | 'service_refused' | 'error'
function lookReasonClass(errorMessage: string | null | undefined): LookReasonClass {
  const msg = (errorMessage ?? '').toLowerCase()
  if (msg.startsWith('gate_failed')) return 'quality_refused'
  if (msg.includes('e005') || msg.includes('flagged as sensitive')) {
    return 'service_refused'
  }
  return 'error'
}

type LookState = 'none' | 'making' | 'ready' | 'failed'

function kickWorker() {
  // Fire-and-forget worker kick (same pattern as the replenishment
  // hook below and request-personal-generation): process queued
  // personal jobs promptly instead of waiting for the drain cron.
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

// RW-16.12 Lane C: retire a cell's prior-generation heroes once a
// replacement is serving, and mark the cell current. Best-effort by
// design: the caller's created_at partition already guarantees only
// fresh heroes serve even if this cleanup fails, and it retries on
// the next serve while the cell remains pending. Rows are deleted
// before storage objects (serving reads rows; an orphaned object is
// unreachable and is collected by the delete-my-photos sweep).
// Mirrors retirePriorHeroes in request-personal-generation.
async function retirePriorHeroes(
  sb: ReturnType<typeof createClient>, userId: string, combinationId: string,
  priorHeroes: Array<{ id: string, storage_path: string }>
): Promise<boolean> {
  try {
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
        console.error('[get-personal-hero] prior hero object removal failed:', removeError)
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
    return true
  } catch (e) {
    console.error('[get-personal-hero] swap materialization failed:', e)
    return false
  }
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
      .select('version, regen_status, regen_pending_since, regen_error')
      .eq('user_id', userId)
      .eq('combination_id', combinationId)
      .maybeSingle()
    let version = cell?.version ?? null
    let regenStatus = (cell?.regen_status as string | undefined) ?? 'current'
    let regenError = (cell?.regen_error as string | null | undefined) ?? null
    const pendingSince = typeof cell?.regen_pending_since === 'string'
      ? cell.regen_pending_since : null
    const regenPending = (regenStatus === 'pending' || regenStatus === 'failed') &&
      pendingSince !== null
    const pendingSinceMs = pendingSince ? Date.parse(pendingSince) : NaN

    const { data: heroes, error: heroError } = await sb
      .from('personal_heroes')
      .select('id, storage_path, variant, outfit_items, created_at')
      .eq('user_id', userId)
      .eq('combination_id', combinationId)
      .order('variant', { ascending: true })
    if (heroError) throw heroError
    const allHeroes = heroes ?? []

    // RW-16.16: the cell's recent jobs, fetched once — the
    // keep-serving branch below reasons over the regeneration
    // cycle's jobs, and the look-state derivation at the end reads
    // the same rows (active jobs, latest terminal outcome).
    const { data: recentJobs } = await sb
      .from('personal_generation_jobs')
      .select('status, error_message, requested_at, outfit_items, kind')
      .eq('user_id', userId)
      .eq('combination_id', combinationId)
      .order('requested_at', { ascending: false })
      .limit(10)
    const cellJobs = recentJobs ?? []

    // --- RW-16.12 Lane C: keep-serving partition ---
    // `serving` is the hero set this response may draw from. For a
    // current cell it is every verified hero, as before. For a
    // pending/failed cell it is the fresh set once a replacement
    // exists (prior heroes are retired at that moment), and the
    // prior set until then.
    let serving = allHeroes
    let regenerationQueued = 0
    if (regenPending) {
      const fresh = allHeroes.filter(
        h => Date.parse(h.created_at as string) >= pendingSinceMs)
      const prior = allHeroes.filter(
        h => Date.parse(h.created_at as string) < pendingSinceMs)
      if (fresh.length > 0) {
        serving = fresh
        if (await retirePriorHeroes(sb, userId, combinationId, prior)) {
          regenStatus = 'current'
          regenError = null
          const { data: cellAfter } = await sb
            .from('personal_cells')
            .select('version')
            .eq('user_id', userId)
            .eq('combination_id', combinationId)
            .maybeSingle()
          version = cellAfter?.version ?? version
        }
      } else {
        serving = prior
        // No replacement yet. Look at this cycle's jobs: if one is
        // in flight, it owns the outcome. If none is in flight:
        //  * the cycle's latest job FAILED -> record the failure on
        //    the cell (regen_status='failed' + regen_error) and keep
        //    serving the prior hero; retry is an explicit request or
        //    the next reference change, never a serve-path loop.
        //  * no job exists for this cycle at all (photos replaced
        //    while the app was closed, or the cell was backfilled)
        //    and the cycle is still 'pending' -> lazily enqueue the
        //    regeneration now, so the replacement actually gets made
        //    even though the client found a serving hero and never
        //    calls the request action for this cell.
        const jobs = cellJobs
        const hasActiveJob = jobs.some(
          j => j.status === 'QUEUED' || j.status === 'PROCESSING')
        const cycleJobs = jobs.filter(
          j => Date.parse(j.requested_at as string) >= pendingSinceMs)
        const latestCycleJob = cycleJobs[0]
        if (!hasActiveJob && latestCycleJob?.status === 'FAILED') {
          if (regenStatus === 'pending') {
            const { error: failMarkError } = await sb
              .from('personal_cells')
              .update({
                regen_status: 'failed',
                regen_error: (latestCycleJob.error_message as string | null) ??
                  'generation_failed',
                updated_at: new Date().toISOString()
              })
              .eq('user_id', userId)
              .eq('combination_id', combinationId)
            if (!failMarkError) {
              regenStatus = 'failed'
              regenError = (latestCycleJob.error_message as string | null) ??
                'generation_failed'
            }
            else console.error('[get-personal-hero] regen failure marking failed:', failMarkError)
          }
        } else if (!hasActiveJob && regenStatus === 'pending' && !latestCycleJob) {
          const outfitItems =
            (prior[0]?.outfit_items as string[] | undefined) ??
            (jobs[0]?.outfit_items as string[] | undefined)
          if (outfitItems && outfitItems.length > 0) {
            const parts = combinationId.split('_')
            const { error: enqueueError } = await sb
              .from('personal_generation_jobs')
              .insert({
                user_id: userId,
                combination_id: combinationId,
                gender: parts[0], weather: parts[1],
                temp_bracket: parts[2], time_of_day: parts[3],
                outfit_signature: parts[4],
                outfit_items: outfitItems,
                kind: 'regeneration',
                priority: 20,
                status: 'QUEUED'
              })
            if (!enqueueError) {
              regenerationQueued = 1
              kickWorker()
            } else {
              console.error('[get-personal-hero] regeneration enqueue failed:', enqueueError)
            }
          }
        }
      }
    }

    // --- RW-16.16: look-state derivation ---
    // Computed after the keep-serving partition (and any swap
    // materialization above), from the final regen_status, the
    // serving set, and the cell's jobs. Precedence:
    //   1. a look job in flight (any kind except replenishment —
    //      a rotation top-up never masks the served hero's state)
    //      -> making;
    //   2. a failed regeneration cycle on the cell record (prior
    //      hero may still serve) -> failed, classed from regen_error;
    //   3. a hero serves -> ready;
    //   4. nothing serves and the latest job FAILED -> failed,
    //      classed from its error (the free on-demand case: no cell
    //      row exists until a delivery, so the job IS the record);
    //   5. otherwise -> none.
    const hasActiveLookJob = regenerationQueued > 0 || cellJobs.some(j =>
      (j.status === 'QUEUED' || j.status === 'PROCESSING') &&
      j.kind !== 'replenishment')
    let lookState: LookState
    let lookReason: LookReasonClass | null = null
    if (hasActiveLookJob) {
      lookState = 'making'
    } else if (regenStatus === 'failed') {
      lookState = 'failed'
      lookReason = lookReasonClass(regenError)
    } else if (serving.length > 0) {
      lookState = 'ready'
    } else if (cellJobs[0]?.status === 'FAILED') {
      lookState = 'failed'
      lookReason = lookReasonClass(cellJobs[0].error_message as string | null)
    } else {
      lookState = 'none'
    }

    if (serving.length === 0) {
      return jsonResponse({
        found: false, combination_id: combinationId, version,
        regen_status: regenStatus, regeneration_queued: regenerationQueued,
        look_state: lookState, look_reason: lookReason
      })
    }

    // Rotate variants deterministically by day so a cell with several
    // verified variants does not show the same photo every day.
    const dayIndex = Math.floor(Date.now() / 86_400_000)
    const hero = serving[dayIndex % serving.length]

    const { data: signed, error: signError } = await sb.storage
      .from('personal-heroes')
      .createSignedUrl(hero.storage_path, SIGNED_URL_TTL_SECONDS)
    if (signError || !signed) throw signError ?? new Error('Could not sign hero URL')

    // --- replenishment hook (entitled only) ---
    // Counts the SERVING set (post-partition). Suppressed while a
    // regeneration is outstanding (RW-16.12 Lane C): the pending
    // regeneration takes precedence, and replenishment resumes once
    // the cell is current again.
    let replenishmentQueued = 0
    const { data: profile } = await sb
      .from('personal_profiles')
      .select('entitled')
      .eq('user_id', userId)
      .maybeSingle()
    if (profile?.entitled === true && regenStatus === 'current' && serving.length < VARIANT_CAP) {
      const { data: active } = await sb
        .from('personal_generation_jobs')
        .select('id')
        .eq('user_id', userId)
        .eq('combination_id', combinationId)
        .in('status', ['QUEUED', 'PROCESSING'])
      const activeCount = active?.length ?? 0
      const wanted = Math.min(
        REPLENISH_CANDIDATES,
        VARIANT_CAP - serving.length - activeCount
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
          kickWorker()
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
      regen_status: regenStatus,
      regeneration_queued: regenerationQueued,
      replenishment_queued: replenishmentQueued,
      look_state: lookState, look_reason: lookReason
    })
  } catch (error) {
    console.error('[get-personal-hero] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
