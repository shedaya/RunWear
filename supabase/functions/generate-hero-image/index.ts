// Supabase Edge Function: generate-hero-image
// Processes generation_jobs and creates hero images via Replicate API
// Uses polling instead of webhooks for reliability
//
// Invocation modes:
//   * Legacy single-job (original behavior, used by scripts/*.js and
//     by the "trigger-image-generation" database webhook, which fires
//     one legacy invocation per inserted generation_jobs row):
//       POST with no drain parameter -> processes ONE queued job and
//       returns { success, combination_id, image_url } /
//       { message: 'No queued jobs' } / 500 { error, job_id }.
//   * Batch drain (revival scheduler, 2026-10-27):
//       POST ?drain=N  or  POST JSON body { "drain": N } / { "drain": true }
//       Processes up to N queued jobs (oldest first), each independent —
//       one failure does not sink the batch — and returns a per-job
//       summary. N defaults to DEFAULT_DRAIN_LIMIT and is clamped to
//       MAX_DRAIN_LIMIT, and the batch is further clamped by the daily
//       spend cap below. Triggered every 30 minutes by pg_cron (see
//       supabase/migrations/20261027130000_hero_generation_scheduler.sql)
//       or, as a fallback, by .github/workflows/hero-generation-drain.yml.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const REPLICATE_API_TOKEN = Deno.env.get('REPLICATE_API_TOKEN')!
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

// SPEND CAP: maximum number of images generated per UTC day across ALL
// invocations of this function (legacy single-job calls included — every
// completion counts). This is the one number that bounds daily Replicate
// spend; raise or lower it here. Enforcement (v39): invocations are
// serialized by the drain lock below, so the count is read by exactly
// one invocation at a time and each invocation admits at most the
// remaining capacity — the pre-v39 "concurrent invocations can overshoot
// by a few" race (61 completions against this 50 cap on 2026-11-17) is
// closed, and the legacy path checks the same cap.
//
// TEMPORARY RAISE to 500 (2026-11-17, Sol's directive): the pilot
// replacement batch (~100 census-FAIL combinations x 2 draws, on top of
// the ear-warmer refill queue) runs under this allowance today.
// Programme-temporary — reinstate 50 when the replacement programme
// ends or if Sol kills it after the pilot evaluation. Do not treat 500
// as the new standing value.
const DAILY_GENERATION_CAP = 500

// Batch drain sizing. Each job can take up to ~60s of polling, so the
// batch must stay small enough to finish inside the edge-function wall
// clock: default 5, hard max 10 per invocation.
const DEFAULT_DRAIN_LIMIT = 5
const MAX_DRAIN_LIMIT = 10

// Invocation hardening (v39, 2026-11-17; RW-176 follow-up). Three live
// incidents in 24h traced to this function's concurrency and recovery
// gaps; the full diagnosis is in
// release-prep/rw176-production-2026-11-17/REPORT.md. In brief:
//   * The "trigger-image-generation" database trigger (AFTER INSERT ON
//     generation_jobs FOR EACH ROW) POSTs this function with NO drain
//     parameter — legacy mode — once per inserted job. A batch insert
//     therefore launches that many simultaneous legacy invocations, and
//     the legacy claim used to be UNGUARDED: every invocation fetched
//     the same oldest QUEUED job and processed it, so one job was
//     generated (and inserted into generated_images) N times. The claim
//     is now guarded on QUEUED in both modes.
//   * Concurrent drains each read the daily-cap counter before the
//     others' completions landed and overshot the cap. Invocations are
//     now serialized with a single-row compare-and-swap lock
//     (hero_generation_lock), and the legacy path checks the cap too.
//   * A killed invocation orphaned its in-flight job in PROCESSING
//     forever, and FAILED jobs needed manual re-queue sweeps. Both
//     invocation modes now reclaim stale PROCESSING jobs, and drains
//     automatically re-queue eligible FAILED jobs (below).

// Stale-PROCESSING reclaim (mirrors personal-hero-worker's RW-15
// pattern; threshold adapted to this function's longer jobs). Every
// in-code failure path marks a claimed job FAILED, so a job still
// PROCESSING past the threshold means its invocation was terminated by
// the platform mid-processing. The worst legitimate job runtime is
// ~14 min (60s stage-1 poll + 3 x 240s Gemini draw polls + download /
// composite), so 15 min cannot reclaim a job that is still genuinely
// running — and platform wall-clock limits kill invocations far sooner
// than that anyway. Each reclaim spends one from the job's
// reclaim_count budget; at the cap the job is FAILED with the named
// stale_processing_timeout reason instead of requeuing forever.
const STALE_PROCESSING_MS = 900_000 // 15 minutes
const STALE_MAX_RECLAIMS = 3
const STALE_FAILURE_REASON = 'stale_processing_timeout'

// Drain lock TTL: comfortably longer than any live invocation (the
// platform kills invocations at its wall clock, minutes at most) and
// longer than the stale threshold, so a killed invocation's jobs are
// reclaimable before its lock can still block the next pg_cron drain
// (30-minute cadence — a stuck lock costs at most one skipped cycle).
const DRAIN_LOCK_TTL_MS = 1_200_000 // 20 minutes

// Automatic FAILED retry bounds (per combination_id, counted across
// all of its job rows — the finisher lane's field rules, automated):
// a combination whose latest job failed on the RW-176 gates is retried
// while its total gate failures are under 3; at 3 it stays FAILED and
// is named in the drain log/response. Transient failures (Replicate
// 429s, timeouts, network) retry more freely but still bounded: a
// combination with 10 total failures is left alone. At most 10
// re-queues per drain, so a failure wave re-enters the queue gradually
// (and always at the back — clones carry a fresh requested_at).
const RETRY_GATE_MAX_FAILURES = 3
const RETRY_TOTAL_MAX_FAILURES = 10
const RETRY_PER_DRAIN_MAX = 10

// Prompt hardening + review gate (2026-10-27), mirrors HeroPoolPolicy.kt.
// Every prompt is hardened HERE, server-side, so jobs queued by older
// clients (whose buildPrompt predates the anatomy block) are covered too.
// FLUX has no negative-prompt channel, so guidance is phrased as positive
// constraints plus one explicit Avoid clause.
const QUALITY_MARKER = 'ANATOMY & QUALITY:'
const QUALITY_SUFFIX =
  ` ${QUALITY_MARKER} anatomically correct human body with natural proportions; ` +
  `arms bent at the elbows and swinging naturally beside the torso, hands relaxed ` +
  `at the runner's sides — never reaching across the back or touching the shoulders; ` +
  `each hand with five correct fingers; legs in a natural mid-stride gait with knees ` +
  `and feet pointing in the direction of travel. ` +
  `Photorealistic, shot on a full-frame camera. Avoid: distorted or merged limbs, ` +
  `extra fingers, hands or limbs, warped joints, backwards-bending arms.`

function ensureQualitySuffix(prompt: string): string {
  return prompt.includes(QUALITY_MARKER) ? prompt : prompt + QUALITY_SUFFIX
}

// Facing-camera composition (2026-11-25, plan step 5 — step-back diagnosis
// §(d)). Until now the prompt stack never specified a facing direction, and
// the quality suffix above even licensed the opposite ("if the runner is
// seen from behind, arms, legs and hands must remain anatomically coherent
// from the back view" — removed in this change): the recipe drew runners
// from behind at a high rate, and the census rubric (v1) had no
// face-presence or orientation criterion to catch them, so back views were
// judged PASS and swapped into the approved pool (31 of 100 pilot wave-1
// draws; the canonical generic for MALE_CLOUDY_COOL_DUSK_84c10525 is one).
// Rubric v2 (census/RUBRIC-v2.md) now fails them; this clause stops making
// them. Hardened HERE, server-side, for the same reason as the clauses
// above: every queued job is covered regardless of which client or script
// queued it. PREPARED 2026-11-25 under the RW-176 freeze — deploys only
// with the replacement programme's resumption.
const FACING_MARKER = 'FACING:'
const FACING_CLAUSE =
  ` ${FACING_MARKER} the runner faces the camera — photographed from the ` +
  `front, running toward the camera, face fully visible and turned toward ` +
  `the camera with eyes, nose and mouth clearly seen; never photographed ` +
  `from behind, never a back view.`

function ensureFacingClause(prompt: string): string {
  return prompt.includes(FACING_MARKER) ? prompt : prompt + FACING_CLAUSE
}

// Ear-warmer placement (RW-176, 2026-11-16). Generic prompts are assembled
// by the clients (and the legacy queue script) from display labels, and
// 'ear warmer / headband' renders as a sweatband worn above the ears unless
// the prompt states the garment's function and placement. Hardened HERE,
// server-side, for the same reason as the quality suffix above: every
// queued job is covered regardless of which client or script queued it.
const EAR_WARMER_MARKER = 'EAR WARMER PLACEMENT:'
const EAR_WARMER_INLINE =
  'a fleece ear-warmer band worn low across the lower forehead, covering both ears completely'
const EAR_WARMER_CLAUSE =
  ` ${EAR_WARMER_MARKER} the ear warmer is ${EAR_WARMER_INLINE} — not a ` +
  `sweatband worn above the ears; the forehead keeps natural proportions.`

function ensureEarWarmerClause(prompt: string): string {
  if (prompt.includes(EAR_WARMER_MARKER)) return prompt
  if (!/ear warmer|headband/i.test(prompt)) return prompt
  // Round 1 of RW-176 validation (2026-11-16) showed an end-of-prompt
  // clause alone is too weak for flux-schnell: the garment was dropped or
  // still rendered above the ears. Substitute the descriptor inline, where
  // the outfit is introduced, and keep the closing clause as reinforcement.
  const inlined = prompt
    .replace(/ear warmer\s*\/\s*headband/gi, EAR_WARMER_INLINE)
    .replace(/ear warmer/gi, EAR_WARMER_INLINE)
    .replace(/headband/gi, EAR_WARMER_INLINE)
  return inlined + EAR_WARMER_CLAUSE
}

// Two-stage ear-warmer route (RW-176). Text-to-image alone could not
// serve this garment: across the RW-176 validation draws flux-schnell
// omitted the ear warmer in 5 of 6 (and placed it above the ears in the
// sixth, pre-hardening). Stage 1 therefore generates the runner WITHOUT
// the head item (the garment is stripped from the prompt).
//
// Stage 2 is the HYBRID (v37, 2026-11-17; Sol accepted the method
// 2026-11-17 after the hybrid lane proved it on all three validation
// bases — record: release-prep/rw176-hybrid-2026-11-17/REPORT.md,
// production record: release-prep/rw176-production-2026-11-17/REPORT.md):
// Gemini 3 Pro Image (google/nano-banana-pro) edits the band onto the
// stage-1 base with the research lane's tuned prompt, then ONLY the
// band strip is transplanted onto the original base with the
// deterministic extractor in the RW176_HYBRID_CORE section below —
// everything outside the strip is bit-identical to the stage-1 image,
// so the runner's face, hair and body are never repainted. (The v36
// Kontext edit route was rejected by Sol on sight: it rendered a thick
// plush band, and on Kontext-class routes band height and ear coverage
// are coupled — a shorter band always lifts off the ears.)
//
// Every draw is gated BEFORE transplant (see RW176_* constants in the
// core): band front height at pixel level and an ear-coverage dip
// statistic. A draw over the hard height limit fails the job at once;
// a draw in the retry band or short on coverage is retried up to
// RW176_MAX_DRAWS draws total; if no draw passes, the job FAILS — a
// bad band, a missing garment, or the unedited base is never served.
// allow_fallback_model is pinned false: at capacity Replicate would
// otherwise silently substitute Seedream, whose band renders failed
// every RW-176 validation (0 of 6).
const EAR_WARMER_GEMINI_PROMPT =
  'Add a slim black microfiber running ear band to the runner in this photo. ' +
  'The band is a NARROW STRIP: at the front it is only about 1 inch (2.5 cm) ' +
  'tall - roughly one third of the forehead\'s height - with its top edge ' +
  'exactly at the hairline, so most of the forehead and all of the hair ' +
  'above it stay bare and visible. The strip wraps around the head and on ' +
  'each side dips just low enough to cover the ear, ending AT the earlobe: ' +
  'the fabric must not hang below the earlobes and must not reach the jaw. ' +
  'Smooth matte black microfiber, no pile, no fuzz, no logo, flat and snug ' +
  'against the head and hair. Photorealistic. Change nothing else: same ' +
  'person, same face, same hair, same outfit, same pose, same background, ' +
  'same lighting.'

// Remove the ear warmer from a hardened prompt for the stage-1 base
// generation: drop the placement clause and the inline descriptor, then
// tidy the separators the removal leaves behind.
function stripEarWarmer(prompt: string): string {
  let p = prompt.replace(EAR_WARMER_CLAUSE, '')
  p = p.split(EAR_WARMER_INLINE).join('')
  p = p
    .replace(/:\s*,/g, ': ')
    .replace(/\s+,/g, ',')
    .replace(/,\s*,/g, ',')
    .replace(/,\s*\./g, '.')
    .replace(/\s{2,}/g, ' ')
  return p
}

// Beanie proportion (RW-176 class extension, 2026-11-17). The beanie
// renders oversized and tall — a high watch-cap silhouette with excess
// crown height — from the same root cause as the ear warmer: the prompt
// names the garment with no proportion anchor. Sol approved the hybrid
// treatment for beanies on 2026-11-17 ("productionise it with the
// hair-tuft guard") after the phase-1C validation
// (release-prep/rw176-production-2026-11-17/REPORT.md §1C): tuned-prompt
// Gemini beanies transplanted with this function's strip extractor read
// beanie:face 0.93x / 1.10x against 1.38x for the flagged render.
//
// The route mirrors the ear warmer exactly: stage 1 generates the
// runner WITHOUT the beanie, stage 2 draws it on with Gemini and
// transplants only the beanie strip. The marker clause below is the
// server-side detection hook (queued jobs are covered regardless of
// which client or script queued them); the clause's wording never
// reaches a served image — stage 1 strips it with the garment.
const BEANIE_MARKER = 'BEANIE PLACEMENT:'
const BEANIE_CLAUSE =
  ` ${BEANIE_MARKER} the beanie is a thin light running beanie, snug and ` +
  `low-profile, sitting low on the crown with no excess height above the head.`

function ensureBeanieClause(prompt: string): string {
  if (prompt.includes(BEANIE_MARKER)) return prompt
  if (!/beanie/i.test(prompt)) return prompt
  return prompt + BEANIE_CLAUSE
}

// The phase-1C proportion-tuned beanie prompt, verbatim (the exact text
// the validated draws were made with).
const BEANIE_GEMINI_PROMPT =
  'Add a thin light running beanie to the runner in this photo. ' +
  'The beanie is SNUG and LOW-PROFILE: it follows the shape of the skull ' +
  'closely and sits low on the crown, with NO excess fabric, NO slouch and ' +
  'NO extra height above the head - the top of the beanie is no more than ' +
  'about 1 inch (2.5 cm) above the top of the head. Its bottom edge sits ' +
  'at the ears, covering the top half of each ear, and just above the ' +
  'eyebrows at the front. Thin smooth matte knit, dark colour, no ' +
  'pom-pom, no folded cuff, no logo. Photorealistic. Change nothing ' +
  'else: same person, same face, same hair below the beanie line, same ' +
  'outfit, same pose, same background, same lighting.'

// Remove the beanie from a hardened prompt for the stage-1 base
// generation: drop the placement clause and every beanie mention, then
// tidy the separators the removal leaves behind.
function stripBeanie(prompt: string): string {
  let p = prompt.replace(BEANIE_CLAUSE, '')
  p = p.replace(/thermal beanie/gi, '').replace(/light beanie/gi, '').replace(/beanie/gi, '')
  p = p
    .replace(/:\s*,/g, ': ')
    .replace(/\s+,/g, ',')
    .replace(/,\s*,/g, ',')
    .replace(/,\s*\./g, '.')
    .replace(/\s{2,}/g, ' ')
  return p
}

// Official-model predictions live on the per-model endpoint (the same
// route personal-hero-worker uses for flux-kontext-pro).
async function createModelPrediction(modelPath: string, input: Record<string, unknown>): Promise<Record<string, any>> {
  const response = await fetch(`https://api.replicate.com/v1/models/${modelPath}/predictions`, {
    method: 'POST',
    headers: {
      'Authorization': `Token ${REPLICATE_API_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ input })
  })
  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Replicate API error: ${response.status} ${errorText}`)
  }
  return await response.json()
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

serve(async (req) => {
  try {
    if (req.method === 'POST') {
      const drainLimit = await parseDrainLimit(req)
      if (drainLimit !== null) {
        return await drainQueuedJobs(drainLimit)
      }
      return await processQueuedJobs()
    }

    return jsonResponse({ error: 'Method not allowed' }, 405)
  } catch (error) {
    console.error('Edge function error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})

// Returns the requested drain batch size, or null when the caller did not
// ask for a drain (legacy single-job mode). The drain is requested either
// as a query parameter (?drain=5, bare ?drain means "default size") or as
// a JSON body field ({ "drain": 5 } / { "drain": true }).
async function parseDrainLimit(req: Request): Promise<number | null> {
  let raw: unknown = null

  const fromQuery = new URL(req.url).searchParams.get('drain')
  if (fromQuery !== null) {
    raw = fromQuery === '' ? true : fromQuery
  } else {
    try {
      const body = await req.json()
      if (body && typeof body === 'object' && 'drain' in body) {
        raw = (body as Record<string, unknown>).drain
      }
    } catch {
      // No/invalid JSON body — the legacy scripts POST with no body at all.
      raw = null
    }
  }

  if (raw === null || raw === undefined || raw === false) return null

  const parsed = raw === true ? DEFAULT_DRAIN_LIMIT : parseInt(String(raw), 10)
  const limit = Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_DRAIN_LIMIT
  return Math.min(limit, MAX_DRAIN_LIMIT)
}

// How many generations have completed today (UTC)? One COMPLETED job ==
// one generated image == one unit of Replicate spend.
async function countTodaysGenerations(): Promise<number> {
  const startOfUtcDay = new Date()
  startOfUtcDay.setUTCHours(0, 0, 0, 0)

  const { count, error } = await supabase
    .from('generation_jobs')
    .select('id', { count: 'exact', head: true })
    .eq('status', 'COMPLETED')
    .gte('completed_at', startOfUtcDay.toISOString())

  if (error) {
    console.error('[generate-hero-image] Error counting today\'s generations:', error)
    throw error
  }

  return count ?? 0
}

// Drain lock: a single-row compare-and-swap on hero_generation_lock.
// The acquire is ONE atomic UPDATE that only lands when the previous
// lock has expired (expires_at < now), so exactly one concurrent
// invocation can hold it; the rest see zero rows and stand down. The
// holder releases by stamping expires_at back to the epoch, guarded on
// its owner token so it can never release someone else's lock. If the
// lock table is unreachable (e.g. the migration has not been applied),
// acquisition returns null and the caller proceeds WITHOUT the lock
// rather than halting generation entirely — the guarded claim still
// prevents double-processing — matching this function's existing
// fallback posture (see the review_status insert fallback). The error
// is logged loudly either way.
type DrainLockHandle = { token: string }

async function acquireDrainLock(): Promise<DrainLockHandle | 'busy' | null> {
  const now = new Date()
  const token = crypto.randomUUID()
  const { data, error } = await supabase
    .from('hero_generation_lock')
    .update({
      owner: token,
      acquired_at: now.toISOString(),
      expires_at: new Date(now.getTime() + DRAIN_LOCK_TTL_MS).toISOString()
    })
    .eq('id', 1)
    .lt('expires_at', now.toISOString())
    .select('id')

  if (error) {
    console.error('[generate-hero-image] Drain lock unavailable, proceeding unlocked:', error)
    return null
  }
  if (!data || data.length === 0) return 'busy'
  return { token }
}

async function releaseDrainLock(lock: DrainLockHandle | 'busy' | null): Promise<void> {
  if (!lock || lock === 'busy') return
  const { error } = await supabase
    .from('hero_generation_lock')
    .update({ owner: '', expires_at: new Date(0).toISOString() })
    .eq('id', 1)
    .eq('owner', lock.token)

  if (error) {
    console.error('[generate-hero-image] Drain lock release failed (the lock will expire on its TTL):', error)
  }
}

// Stale-PROCESSING reclaim: find jobs whose PROCESSING claim is older
// than STALE_PROCESSING_MS and return them to QUEUED, spending one
// reclaim from each job's reclaim_count budget; a job whose budget is
// spent is marked FAILED with the named stale_processing_timeout
// reason. All transitions are guarded on the exact state read (status
// + reclaim_count + claim age) so concurrent invocations cannot
// double-reclaim the same job. Unlike personal-hero-worker there is no
// resume path here — a reclaimed job restarts from stage 1, so its
// replicate_id is cleared (processJob overwrites it before polling
// anyway).
async function reclaimStaleJobs(): Promise<{ reclaimed: string[], staleFailed: string[] }> {
  const cutoff = new Date(Date.now() - STALE_PROCESSING_MS).toISOString()
  const { data: stale, error } = await supabase
    .from('generation_jobs')
    .select('id, combination_id, reclaim_count')
    .eq('status', 'PROCESSING')
    .lt('started_at', cutoff)

  if (error) {
    console.error('[generate-hero-image] Stale scan failed:', error)
    return { reclaimed: [], staleFailed: [] }
  }

  const reclaimed: string[] = []
  const staleFailed: string[] = []

  for (const row of (stale ?? []) as Array<{ id: string, combination_id: string, reclaim_count: number | null }>) {
    const reclaims = row.reclaim_count ?? 0
    if (reclaims >= STALE_MAX_RECLAIMS) {
      const { data: marked } = await supabase
        .from('generation_jobs')
        .update({
          status: 'FAILED',
          error_message: `${STALE_FAILURE_REASON}: worker invocation terminated mid-processing; job reclaimed ${reclaims} time(s) without completing`,
          completed_at: new Date().toISOString()
        })
        .eq('id', row.id)
        .eq('status', 'PROCESSING')
        .eq('reclaim_count', reclaims)
        .lt('started_at', cutoff)
        .select('id')
      if (marked && marked.length > 0) {
        staleFailed.push(row.combination_id)
        console.log(`[generate-hero-image] Stale job ${row.combination_id} FAILED: ${STALE_FAILURE_REASON} after ${reclaims} reclaim(s)`)
      }
    } else {
      const { data: reset } = await supabase
        .from('generation_jobs')
        .update({ status: 'QUEUED', reclaim_count: reclaims + 1, started_at: null, replicate_id: null })
        .eq('id', row.id)
        .eq('status', 'PROCESSING')
        .eq('reclaim_count', reclaims)
        .lt('started_at', cutoff)
        .select('id')
      if (reset && reset.length > 0) {
        reclaimed.push(row.combination_id)
        console.log(`[generate-hero-image] Reclaimed stale job ${row.combination_id} (reclaim ${reclaims + 1}/${STALE_MAX_RECLAIMS})`)
      }
    }
  }

  return { reclaimed, staleFailed }
}

// Failure classes for the automatic retry. Gate failures are the
// RW-176 gate verdicts (their job-level messages all start 'RW-176');
// stale_processing_timeout is terminal by design (its reclaim budget
// was already spent); everything else — Replicate 429 throttling,
// prediction timeouts, network and transfer errors — is transient.
function classifyJobError(errorMessage: string | null): 'gate' | 'stale' | 'transient' {
  const msg = errorMessage ?? ''
  if (msg.startsWith(STALE_FAILURE_REASON)) return 'stale'
  if (msg.startsWith('RW-176')) return 'gate'
  return 'transient'
}

// Automatic FAILED retry: for each combination whose LATEST job is
// FAILED (and which has no QUEUED/PROCESSING successor), insert a
// fresh QUEUED clone of the failed job — the same fresh-row pattern
// the manual re-queue sweeps used, so the FAILED row itself is never
// mutated and its error_message remains as the failure history.
// Bounds are the RETRY_* constants above; combinations that hit the
// gate-failure cap are returned (and logged) by name.
async function requeueFailedJobs(): Promise<{ requeued: string[], terminalGate: string[] }> {
  const { data: rows, error } = await supabase
    .from('generation_jobs')
    .select('combination_id, status, error_message, requested_at')
    .order('requested_at', { ascending: false })
    .limit(10000)

  if (error) {
    console.error('[generate-hero-image] Retry scan failed:', error)
    return { requeued: [], terminalGate: [] }
  }

  type JobRow = { combination_id: string, status: string, error_message: string | null, requested_at: string }
  const byCombo = new Map<string, JobRow[]>()
  for (const row of (rows ?? []) as JobRow[]) {
    const list = byCombo.get(row.combination_id)
    if (list) list.push(row)
    else byCombo.set(row.combination_id, [row])
  }

  const requeued: string[] = []
  const terminalGate: string[] = []

  for (const [combo, list] of byCombo) {
    // Rows arrive newest-first per combination, so list[0] is its
    // latest job. Only a combination whose latest job is FAILED is a
    // retry candidate at all.
    const latest = list[0]
    if (latest.status !== 'FAILED') continue
    // A QUEUED/PROCESSING row anywhere in the list (e.g. a backdated
    // successor) means the cell is already covered — never stack
    // another attempt on top of it.
    if (list.some(r => r.status === 'QUEUED' || r.status === 'PROCESSING')) continue

    const latestClass = classifyJobError(latest.error_message)
    if (latestClass === 'stale') continue

    const failedRows = list.filter(r => r.status === 'FAILED')
    const gateFailures = failedRows.filter(r => classifyJobError(r.error_message) === 'gate').length

    if (latestClass === 'gate' && gateFailures >= RETRY_GATE_MAX_FAILURES) {
      // Gate-failure cap reached: the combination stays FAILED and is
      // named in the log and the drain response.
      terminalGate.push(combo)
      continue
    }
    if (failedRows.length >= RETRY_TOTAL_MAX_FAILURES) continue
    if (requeued.length >= RETRY_PER_DRAIN_MAX) continue

    const { data: src } = await supabase
      .from('generation_jobs')
      .select('prompt')
      .eq('combination_id', combo)
      .eq('status', 'FAILED')
      .order('requested_at', { ascending: false })
      .limit(1)

    if (!src || src.length === 0) continue

    const { error: insertError } = await supabase
      .from('generation_jobs')
      .insert({ combination_id: combo, prompt: src[0].prompt })

    if (insertError) {
      console.error(`[generate-hero-image] Auto-requeue failed for ${combo}:`, insertError)
      continue
    }

    requeued.push(combo)
    console.log(`[generate-hero-image] Auto-requeued FAILED job for ${combo} (${latestClass}; gate failures ${gateFailures}, total failures ${failedRows.length})`)
  }

  if (terminalGate.length > 0) {
    console.log(`[generate-hero-image] Combinations at the gate-failure cap, staying FAILED: ${terminalGate.join(', ')}`)
  }

  return { requeued, terminalGate }
}

// Legacy single-job mode: process ONE queued job (the oldest). Response
// shapes are unchanged from the original implementation — the manual
// scripts branch on { success } / { message: 'No queued jobs' } / 500.
// The { message } shape also carries the two v39 stand-downs (another
// invocation holds the drain lock; the daily cap is reached) — both
// mean "no work done by this call", which is what the shape is for.
async function processQueuedJobs() {
  console.log('[generate-hero-image] Processing queued jobs...')

  const lock = await acquireDrainLock()
  if (lock === 'busy') {
    return jsonResponse({ message: 'Generation already in progress' })
  }

  try {
    await reclaimStaleJobs()

    // The daily cap binds legacy calls too (v39). Before this, the
    // legacy path never read the counter, so the per-row insert
    // webhook's invocations could spend straight through the cap.
    const generatedToday = await countTodaysGenerations()
    if (generatedToday >= DAILY_GENERATION_CAP) {
      return jsonResponse({ message: 'Daily generation cap reached' })
    }

    // Fetch-claim loop: if the oldest QUEUED job was claimed by a
    // concurrent invocation between fetch and claim (processJob
    // reports it SKIPPED), move on to the next one instead of failing.
    for (let attempt = 0; attempt < 3; attempt++) {
      const { data: jobs, error } = await supabase
        .from('generation_jobs')
        .select('*')
        .eq('status', 'QUEUED')
        .order('requested_at', { ascending: true })
        .limit(1)

      if (error) {
        console.error('[generate-hero-image] Error fetching jobs:', error)
        throw error
      }

      if (!jobs || jobs.length === 0) {
        return jsonResponse({ message: 'No queued jobs' })
      }

      const job = jobs[0]
      const result = await processJob(job)

      if (!result.ok && result.skipped) continue

      if (result.ok) {
        return jsonResponse({
          success: true,
          combination_id: result.combination_id,
          image_url: result.image_url
        })
      }

      return jsonResponse({
        error: result.error,
        job_id: job.id
      }, 500)
    }

    return jsonResponse({ message: 'No queued jobs' })
  } finally {
    await releaseDrainLock(lock)
  }
}

// Batch drain mode: process up to `limit` queued jobs, oldest first. The
// batch is clamped by whatever is left of today's spend cap. Each job is
// claimed with a QUEUED-status guard so a concurrent invocation cannot
// process the same job twice, and each job succeeds or fails on its own.
//
// The wrapper serializes drains on the drain lock: a concurrent drain
// (pg_cron overlapping a manual drain, a webhook burst) stands down
// with a lock_busy summary instead of racing the cap counter. Queue
// hygiene — the stale-PROCESSING reclaim and the automatic FAILED
// retry — runs first, inside the lock, on every drain, including
// cap-exhausted ones: recovery must not wait for spend headroom.
async function drainQueuedJobs(limit: number) {
  const lock = await acquireDrainLock()
  if (lock === 'busy') {
    return jsonResponse({
      drained: true,
      skipped: 'lock_busy',
      attempted: 0,
      completed: 0,
      failed: 0,
      results: []
    })
  }

  try {
    const { reclaimed, staleFailed } = await reclaimStaleJobs()
    const { requeued, terminalGate } = await requeueFailedJobs()
    const hygiene = {
      reclaimed,
      stale_failed: staleFailed,
      retried: requeued,
      retry_terminal_gate: terminalGate
    }
    return await drainQueuedJobsInner(limit, hygiene)
  } finally {
    await releaseDrainLock(lock)
  }
}

async function drainQueuedJobsInner(limit: number, hygiene: Record<string, unknown>) {
  console.log(`[generate-hero-image] Draining up to ${limit} queued jobs...`)

  const generatedTodayBefore = await countTodaysGenerations()
  const remainingByCap = Math.max(0, DAILY_GENERATION_CAP - generatedTodayBefore)
  const batchSize = Math.min(limit, remainingByCap)

  const baseSummary = {
    drained: true,
    daily_cap: DAILY_GENERATION_CAP,
    generated_today_before: generatedTodayBefore,
    cap_reached: remainingByCap <= 0,
    ...hygiene
  }

  if (batchSize === 0) {
    return jsonResponse({
      ...baseSummary,
      message: 'Daily generation cap reached',
      attempted: 0,
      completed: 0,
      failed: 0,
      results: []
    })
  }

  const { data: jobs, error } = await supabase
    .from('generation_jobs')
    .select('*')
    .eq('status', 'QUEUED')
    .order('requested_at', { ascending: true })
    .limit(batchSize)

  if (error) {
    console.error('[generate-hero-image] Error fetching jobs:', error)
    throw error
  }

  if (!jobs || jobs.length === 0) {
    return jsonResponse({
      ...baseSummary,
      message: 'No queued jobs',
      attempted: 0,
      completed: 0,
      failed: 0,
      results: []
    })
  }

  const results: Array<Record<string, unknown>> = []
  let completed = 0
  let failed = 0

  for (const job of jobs) {
    const result = await processJob(job)
    if (!result.ok && result.skipped) {
      // Another invocation claimed this job first; not a failure.
      results.push({
        job_id: job.id,
        combination_id: job.combination_id,
        status: 'SKIPPED',
        error: result.error
      })
    } else if (result.ok) {
      completed++
      results.push({
        job_id: job.id,
        combination_id: result.combination_id,
        status: 'COMPLETED',
        image_url: result.image_url
      })
    } else {
      failed++
      results.push({
        job_id: job.id,
        combination_id: job.combination_id,
        status: 'FAILED',
        error: result.error
      })
    }
  }

  return jsonResponse({
    ...baseSummary,
    attempted: results.length,
    completed,
    failed,
    results
  })
}

// Flat result shape (no discriminated union) so the file type-checks
// under any compiler strictness setting.
type ProcessJobResult = {
  ok: boolean,
  skipped?: boolean,
  combination_id: string,
  image_url?: string,
  error?: string
}

// Process a single generation job end to end: claim it, generate via
// Replicate, store the image, insert it into the pool as 'approved'
// (auto-replenishment), and mark the job COMPLETED — or mark it FAILED and
// report the error. Never throws; the caller decides how to present the
// outcome.
async function processJob(job: Record<string, unknown> & { id: unknown, combination_id: string, prompt: string }): Promise<ProcessJobResult> {
  console.log(`[generate-hero-image] Processing: ${job.combination_id}`)

  try {
    // Claim the job (status -> PROCESSING). The claim is guarded on
    // QUEUED in BOTH invocation modes (v39): it is a single atomic
    // UPDATE, so exactly one concurrent invocation can win it and the
    // rest report the job SKIPPED. The pre-v39 legacy path claimed
    // unconditionally — and because the insert webhook fires one
    // legacy invocation per inserted job row, a batch insert launched
    // N simultaneous invocations that all fetched the same oldest
    // QUEUED job and all processed it, generating (and inserting) the
    // same job N times. That was the RW-176 duplicate-rows mechanism
    // for single-job combinations; see the REPORT's diagnosis.
    const { data: claimed, error: claimError } = await supabase
      .from('generation_jobs')
      .update({
        status: 'PROCESSING',
        started_at: new Date().toISOString()
      })
      .eq('id', job.id)
      .eq('status', 'QUEUED')
      .select('id')

    if (claimError) {
      throw new Error(`Claim failed: ${claimError.message}`)
    }
    if (!claimed || claimed.length === 0) {
      return {
        ok: false,
        skipped: true,
        combination_id: job.combination_id,
        error: 'Job was already claimed by another invocation'
      }
    }

    const prompt = ensureFacingClause(ensureBeanieClause(ensureEarWarmerClause(ensureQualitySuffix(job.prompt))))

    // Ear-warmer and beanie jobs take the two-stage hybrid route (see
    // stripEarWarmer / stripBeanie): stage 1 generates the runner
    // without the head item. The two classes are mutually exclusive —
    // an outfit carries one head item — and the ear warmer wins the
    // check so its behaviour is unchanged.
    const isEarWarmerJob = prompt.includes(EAR_WARMER_MARKER)
    const isBeanieJob = !isEarWarmerJob && prompt.includes(BEANIE_MARKER)
    const stage1Prompt = isEarWarmerJob ? stripEarWarmer(prompt)
      : isBeanieJob ? stripBeanie(prompt) : prompt

    // Stage-1 generation — the RW-24b pool recipe (2026-11-02), adopted
    // 2026-11-17: flux-schnell through the OFFICIAL-MODEL endpoint with
    // aspect_ratio "2:3" and output_quality 90. The previous call form —
    // a pinned version hash through the generic predictions endpoint —
    // silently ignored aspect_ratio and returned 1024x1024 SQUARE
    // images, which the app then cropped into the portrait hero frame
    // (the soft, "old way" look on every function-generated hero).
    // Verified empirically before this change (three test draws, same
    // prompt, 2026-11-17): pinned-version call -> 1024x1024 PNG;
    // official-model call -> 832x1216 PNG (and 832x1216 with
    // output_format webp). PNG is kept deliberately: the hybrid routes
    // decode the stage-1 base with this function's hand-rolled PNG
    // codec, and stored heroes live at deterministic .png paths. The
    // stored-format deviation from the RW-24b webp stock is recorded
    // in release-prep/rw176-production-2026-11-17/REPORT.md.
    const prediction = await createModelPrediction('black-forest-labs/flux-schnell', {
      prompt: stage1Prompt,
      num_outputs: 1,
      aspect_ratio: "2:3",
      output_format: "png",
      output_quality: 90
    })
    console.log(`[generate-hero-image] Prediction created: ${prediction.id}`)

    // Update job with replicate_id
    await supabase
      .from('generation_jobs')
      .update({ replicate_id: prediction.id })
      .eq('id', job.id)

    // Poll for completion (max 60 seconds)
    const result = await pollForCompletion(prediction.id)

    if (result.status !== 'succeeded') {
      throw new Error(`Prediction failed: ${result.error || result.status}`)
    }

    // Get the image URL
    let imageUrl = result.output?.[0]
    if (!imageUrl) {
      throw new Error('No image URL in output')
    }

    // Final image bytes for hybrid-route jobs (ear warmer, beanie)
    // come from the strip composite, not from a download (see the
    // stage-2 blocks below).
    let finalBytes: Uint8Array | null = null

    if (isEarWarmerJob) {
      // Stage 2 (hybrid): Gemini draws the band on the stage-1 base,
      // the gates judge the draw, and only the band strip is
      // transplanted onto the original base. Any failure after the
      // retries fails the job — the garment-missing base is never
      // what gets stored or served.
      const baseResponse = await fetch(imageUrl)
      if (!baseResponse.ok) {
        throw new Error(`Failed to download stage-1 base: ${baseResponse.status}`)
      }
      const baseImg = await decodePng(new Uint8Array(await baseResponse.arrayBuffer()))

      let acceptedPng: Uint8Array | null = null
      for (let draw = 1; draw <= RW176_MAX_DRAWS; draw++) {
        let result: HybridResult | null = null
        try {
          const edit = await createModelPrediction('google/nano-banana-pro', {
            prompt: EAR_WARMER_GEMINI_PROMPT,
            image_input: [imageUrl],
            aspect_ratio: 'match_input_image',
            resolution: '1K',
            output_format: 'png',
            allow_fallback_model: false
          })
          console.log(`[generate-hero-image] Ear-warmer Gemini draw ${draw} created: ${edit.id}`)

          await supabase
            .from('generation_jobs')
            .update({ replicate_id: edit.id })
            .eq('id', job.id)

          const editResult = await pollForCompletion(edit.id, 240000)
          if (editResult.status !== 'succeeded') {
            throw new Error(`prediction ${editResult.error || editResult.status}`)
          }
          const drawUrl = Array.isArray(editResult.output) ? editResult.output[0] : editResult.output
          if (!drawUrl) {
            throw new Error('prediction returned no image URL')
          }
          const drawResponse = await fetch(drawUrl)
          if (!drawResponse.ok) {
            throw new Error(`draw download failed: ${drawResponse.status}`)
          }
          const drawImg = await decodePng(new Uint8Array(await drawResponse.arrayBuffer()))
          // nano-banana-pro returns its 2:3 edit at 848x1264, not the
          // base's 832x1216 — resample onto the base grid before the
          // gates measure it (v42; see resampleToGrid in the core).
          result = analyseHybrid(baseImg, resampleToGrid(drawImg, baseImg.width, baseImg.height))
        } catch (drawError) {
          // A failed, timed-out, undecodable or band-less draw consumes
          // one of the retries; it never reaches the pool.
          console.warn(`[generate-hero-image] Ear-warmer draw ${draw} unusable: ${(drawError as Error).message}`)
          continue
        }
        if (!result) continue

        const m = result.meas
        console.log(`[generate-hero-image] Ear-warmer draw ${draw} gates: front=${m.frontHNorm} dipP90=${m.dipP90Norm} outsideDiff=${m.outsideMaxDiff} strip=${m.stripXNorm}`)

        // The composite must be bit-identical to the stage-1 base
        // outside the feathered strip (true by construction — the
        // feather kernel is finite — and asserted here on every job).
        if (m.outsideMaxDiff !== 0) {
          throw new Error(`RW-176 assertion failed: outside-strip diff ${m.outsideMaxDiff} != 0`)
        }
        if (m.frontHNorm !== null && m.frontHNorm > RW176_HARD_FRONT_NORM) {
          throw new Error(`RW-176 hard reject: band front ${m.frontHNorm} norm over hard limit ${RW176_HARD_FRONT_NORM}`)
        }
        if (m.frontHNorm !== null && m.frontHNorm <= RW176_ACCEPT_FRONT_MAX_NORM &&
            m.dipP90Norm !== null && m.dipP90Norm >= RW176_MIN_DIP_P90_NORM) {
          acceptedPng = await encodePng(result.composite)
          break
        }
        console.warn(`[generate-hero-image] Ear-warmer draw ${draw} in retry band or short on coverage`)
      }

      if (!acceptedPng) {
        throw new Error(`RW-176: no Gemini draw passed the band gates in ${RW176_MAX_DRAWS} draws`)
      }
      finalBytes = acceptedPng
    }

    if (isBeanieJob) {
      // Stage 2 (hybrid, v38): Gemini draws the beanie on the stage-1
      // base with the phase-1C tuned prompt, the beanie gates judge the
      // draw (presence, crown proportion, hair-tuft guard), and only
      // the beanie strip is transplanted onto the original base. Any
      // failure after the retries fails the job — a tall beanie, a
      // tufted transplant, a missing garment, or the unedited base is
      // never what gets stored or served.
      const baseResponse = await fetch(imageUrl)
      if (!baseResponse.ok) {
        throw new Error(`Failed to download stage-1 base: ${baseResponse.status}`)
      }
      const baseImg = await decodePng(new Uint8Array(await baseResponse.arrayBuffer()))

      let acceptedPng: Uint8Array | null = null
      for (let draw = 1; draw <= RW176_MAX_DRAWS; draw++) {
        let result: HybridResult | null = null
        let drawImg: RgbImage | null = null
        try {
          const edit = await createModelPrediction('google/nano-banana-pro', {
            prompt: BEANIE_GEMINI_PROMPT,
            image_input: [imageUrl],
            aspect_ratio: 'match_input_image',
            resolution: '1K',
            output_format: 'png',
            allow_fallback_model: false
          })
          console.log(`[generate-hero-image] Beanie Gemini draw ${draw} created: ${edit.id}`)

          await supabase
            .from('generation_jobs')
            .update({ replicate_id: edit.id })
            .eq('id', job.id)

          const editResult = await pollForCompletion(edit.id, 240000)
          if (editResult.status !== 'succeeded') {
            throw new Error(`prediction ${editResult.error || editResult.status}`)
          }
          const drawUrl = Array.isArray(editResult.output) ? editResult.output[0] : editResult.output
          if (!drawUrl) {
            throw new Error('prediction returned no image URL')
          }
          const drawResponse = await fetch(drawUrl)
          if (!drawResponse.ok) {
            throw new Error(`draw download failed: ${drawResponse.status}`)
          }
          drawImg = await decodePng(new Uint8Array(await drawResponse.arrayBuffer()))
          // Same resample as the ear-warmer route (v42): the draw is
          // measured (analyseHybrid + beanieMeasurements) on the
          // base's grid, never on nano-banana-pro's 848x1264 grid.
          drawImg = resampleToGrid(drawImg, baseImg.width, baseImg.height)
          result = analyseHybrid(baseImg, drawImg)
        } catch (drawError) {
          // A failed, timed-out, undecodable or beanie-less draw
          // consumes one of the retries; it never reaches the pool.
          console.warn(`[generate-hero-image] Beanie draw ${draw} unusable: ${(drawError as Error).message}`)
          continue
        }
        if (!result || !drawImg) continue

        const m = result.meas
        const b = beanieMeasurements(baseImg, drawImg, result.strip)
        console.log(`[generate-hero-image] Beanie draw ${draw} gates: crown=${b.crownHMedianNorm} width=${b.stripWidthNorm} tuft=${b.tuftMaxNorm}/${b.tuftColsOver12} outsideDiff=${m.outsideMaxDiff}`)

        // The composite must be bit-identical to the stage-1 base
        // outside the feathered strip (true by construction — the
        // feather kernel is finite — and asserted here on every job).
        if (m.outsideMaxDiff !== 0) {
          throw new Error(`RW-176 assertion failed: outside-strip diff ${m.outsideMaxDiff} != 0`)
        }
        if (b.crownHMedianNorm !== null && b.crownHMedianNorm > RW176_BEANIE_HARD_CROWN_NORM) {
          throw new Error(`RW-176 hard reject: beanie crown ${b.crownHMedianNorm} norm over hard limit ${RW176_BEANIE_HARD_CROWN_NORM}`)
        }
        const present = b.crownHMedianNorm !== null &&
          b.crownHMedianNorm >= RW176_BEANIE_MIN_CROWN_NORM &&
          b.stripWidthNorm >= RW176_BEANIE_MIN_STRIP_WIDTH_NORM
        if (present && b.crownHMedianNorm !== null &&
            b.crownHMedianNorm <= RW176_BEANIE_ACCEPT_CROWN_MAX_NORM &&
            !b.tuftFires) {
          acceptedPng = await encodePng(result.composite)
          break
        }
        console.warn(`[generate-hero-image] Beanie draw ${draw} rejected by gates (present=${present}, tuftFires=${b.tuftFires})`)
      }

      if (!acceptedPng) {
        throw new Error(`RW-176: no Gemini draw passed the beanie gates in ${RW176_MAX_DRAWS} draws`)
      }
      finalBytes = acceptedPng
    }

    let imageBlob: Blob
    if (finalBytes) {
      console.log('[generate-hero-image] Storing RW-176 hybrid composite')
      imageBlob = new Blob([finalBytes as BlobPart], { type: 'image/png' })
    } else {
      console.log(`[generate-hero-image] Downloading from: ${imageUrl}`)

      // Download the image
      const imageResponse = await fetch(imageUrl)
      if (!imageResponse.ok) {
        throw new Error(`Failed to download: ${imageResponse.status}`)
      }
      imageBlob = await imageResponse.blob()
    }

    // Upload to Supabase Storage
    const storagePath = `${job.combination_id}/${job.combination_id}.png`
    const { error: uploadError } = await supabase
      .storage
      .from('hero-images')
      .upload(storagePath, imageBlob, {
        contentType: 'image/png',
        upsert: true
      })

    if (uploadError) {
      throw new Error(`Upload failed: ${uploadError.message}`)
    }

    // Get public URL
    const { data: publicUrlData } = supabase
      .storage
      .from('hero-images')
      .getPublicUrl(storagePath)

    const publicUrl = publicUrlData.publicUrl
    console.log(`[generate-hero-image] Uploaded to: ${publicUrl}`)

    // Insert into generated_images with full combination_id (including
    // variant). Auto-replenishment (Sol's call, 2026-10-27): new generations
    // are inserted as 'approved' and served immediately — there is no
    // pending-review queue. review_status remains as a retraction lever:
    // setting a row to 'rejected' pulls it from rotation (clients only
    // serve 'approved'/legacy rows). If the review-gate migration has not
    // been applied yet (column missing: PG 42703 / PostgREST PGRST204),
    // fall back to the legacy insert so generation never breaks — the row
    // is then immediately servable, exactly as before the column existed.
    let { error: insertError } = await supabase
      .from('generated_images')
      .insert({
        combination_id: job.combination_id,
        image_url: publicUrl,
        prompt: prompt,
        review_status: 'approved'
      })

    if (insertError && (insertError.code === '42703' || insertError.code === 'PGRST204')) {
      console.warn('[generate-hero-image] review_status column missing — legacy insert (apply the 2026-10-27 migration)')
      const retry = await supabase
        .from('generated_images')
        .insert({
          combination_id: job.combination_id,
          image_url: publicUrl,
          prompt: prompt
        })
      insertError = retry.error
    }

    // Skip duplicate key errors (23505) - image already exists
    if (insertError && insertError.code !== '23505') {
      throw new Error(`Insert failed: ${insertError.message}`)
    }

    // Update job to COMPLETED
    await supabase
      .from('generation_jobs')
      .update({
        status: 'COMPLETED',
        completed_at: new Date().toISOString()
      })
      .eq('id', job.id)

    console.log(`[generate-hero-image] COMPLETED: ${job.combination_id}`)

    return {
      ok: true,
      combination_id: job.combination_id,
      image_url: publicUrl
    }

  } catch (jobError) {
    console.error(`[generate-hero-image] Error:`, jobError)

    await supabase
      .from('generation_jobs')
      .update({
        status: 'FAILED',
        error_message: (jobError as Error).message
      })
      .eq('id', job.id)

    return {
      ok: false,
      skipped: false,
      combination_id: job.combination_id,
      error: (jobError as Error).message
    }
  }
}

async function pollForCompletion(predictionId: string, maxWaitMs = 60000) {
  const startTime = Date.now()
  const pollInterval = 1000 // 1 second

  while (Date.now() - startTime < maxWaitMs) {
    const response = await fetch(
      `https://api.replicate.com/v1/predictions/${predictionId}`,
      {
        headers: {
          'Authorization': `Token ${REPLICATE_API_TOKEN}`
        }
      }
    )

    if (!response.ok) {
      throw new Error(`Poll failed: ${response.status}`)
    }

    const prediction = await response.json()

    if (prediction.status === 'succeeded' || prediction.status === 'failed') {
      return prediction
    }

    // Wait before next poll
    await new Promise(resolve => setTimeout(resolve, pollInterval))
  }

  throw new Error('Prediction timed out')
}

// === RW176_HYBRID_CORE_BEGIN ===
// RW-176 hybrid core: deterministic ear-band strip extraction + composite.
// Ported 1:1 from the validated Python reference implementation
// (release-prep/rw176-production-2026-11-17/scripts/proto_auto.py, itself a
// port of the hybrid lane's composite2/composite3). Pure per-pixel math +
// connected components; no external dependencies. PNG decode/encode is a
// minimal hand-rolled codec (8-bit, non-interlaced; colour types 0/2/3/4/6)
// using the platform Deflate streams.
//
// The gate thresholds below are the production calibration of the research
// lane's ratio gates (accept <= 0.65x, hard-reject > 0.85x of the look-2
// reference) on THIS instrument; see the production REPORT for the
// calibration table and the known FEMALE template limitation.

export const RW176_ACCEPT_FRONT_MAX_NORM = 27.0;
export const RW176_HARD_FRONT_NORM = 34.0;
export const RW176_MIN_DIP_P90_NORM = 22.0;
export const RW176_MAX_DRAWS = 3;

export interface RgbImage {
  width: number;
  height: number;
  data: Uint8Array; // RGB, 3 bytes per pixel, row-major
}

export interface HybridMeasurements {
  frontHNorm: number | null;
  frontBotNorm: number;
  dipP90Norm: number | null;
  dipMaxNorm: number;
  outsideMaxDiff: number;
  stripXNorm: [number, number];
  seedCentreNorm: number;
}

export interface HybridResult {
  composite: RgbImage;
  meas: HybridMeasurements;
  // The final per-column strip [top, bot] the composite was built from.
  // Additive in v38: the ear-warmer gates ignore it; the beanie gates
  // (crown proportion + hair-tuft guard) consume it via
  // beanieMeasurements below.
  strip: Map<number, [number, number]>;
}

// ---------------------------------------------------------------- PNG codec

const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();

function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

async function zlibInflate(data: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([data as BlobPart]).stream()
    .pipeThrough(new DecompressionStream("deflate"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

async function zlibDeflate(data: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([data as BlobPart]).stream()
    .pipeThrough(new CompressionStream("deflate"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

export async function decodePng(bytes: Uint8Array): Promise<RgbImage> {
  if (bytes.length < 8 || bytes[0] !== 0x89 || bytes[1] !== 0x50) {
    throw new Error("RW176: not a PNG");
  }
  let pos = 8;
  let width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
  let palette: Uint8Array | null = null;
  const idat: Uint8Array[] = [];
  while (pos + 8 <= bytes.length) {
    const len = ((bytes[pos] << 24) | (bytes[pos + 1] << 16) | (bytes[pos + 2] << 8) | bytes[pos + 3]) >>> 0;
    const type = String.fromCharCode(bytes[pos + 4], bytes[pos + 5], bytes[pos + 6], bytes[pos + 7]);
    const chunk = bytes.subarray(pos + 8, pos + 8 + len);
    if (type === "IHDR") {
      width = ((chunk[0] << 24) | (chunk[1] << 16) | (chunk[2] << 8) | chunk[3]) >>> 0;
      height = ((chunk[4] << 24) | (chunk[5] << 16) | (chunk[6] << 8) | chunk[7]) >>> 0;
      bitDepth = chunk[8]; colorType = chunk[9]; interlace = chunk[12];
    } else if (type === "PLTE") {
      palette = chunk.slice();
    } else if (type === "IDAT") {
      idat.push(chunk.slice());
    } else if (type === "IEND") {
      break;
    }
    pos += 12 + len;
  }
  if (!width || bitDepth !== 8 || interlace !== 0 ||
      ![0, 2, 3, 4, 6].includes(colorType)) {
    throw new Error(`RW176: unsupported PNG (bitDepth=${bitDepth} colorType=${colorType} interlace=${interlace})`);
  }
  const bpp = colorType === 2 ? 3 : colorType === 6 ? 4 : colorType === 4 ? 2 : 1;
  const raw = await zlibInflate(concatBytes(idat));
  const stride = width * bpp;
  const px = new Uint8Array(height * stride);
  let rp = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[rp++];
    const rowOff = y * stride;
    const upOff = rowOff - stride;
    for (let i = 0; i < stride; i++) {
      const v = raw[rp++];
      const a = i >= bpp ? px[rowOff + i - bpp] : 0;
      const b = y > 0 ? px[upOff + i] : 0;
      const c = y > 0 && i >= bpp ? px[upOff + i - bpp] : 0;
      let out: number;
      switch (filter) {
        case 0: out = v; break;
        case 1: out = v + a; break;
        case 2: out = v + b; break;
        case 3: out = v + ((a + b) >> 1); break;
        case 4: {
          const p = a + b - c;
          const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
          out = v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
          break;
        }
        default: throw new Error(`RW176: bad PNG filter ${filter}`);
      }
      px[rowOff + i] = out & 0xff;
    }
  }
  const rgb = new Uint8Array(width * height * 3);
  for (let i = 0, n = width * height; i < n; i++) {
    const s = i * bpp, d = i * 3;
    if (colorType === 2 || colorType === 6) {
      rgb[d] = px[s]; rgb[d + 1] = px[s + 1]; rgb[d + 2] = px[s + 2];
    } else if (colorType === 0 || colorType === 4) {
      rgb[d] = rgb[d + 1] = rgb[d + 2] = px[s];
    } else { // palette
      const p = px[s] * 3;
      if (!palette || p + 2 >= palette.length) throw new Error("RW176: bad palette index");
      rgb[d] = palette[p]; rgb[d + 1] = palette[p + 1]; rgb[d + 2] = palette[p + 2];
    }
  }
  return { width, height, data: rgb };
}

function concatBytes(parts: Uint8Array[]): Uint8Array {
  let total = 0;
  for (const p of parts) total += p.length;
  const out = new Uint8Array(total);
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}

function pngChunk(type: string, data: Uint8Array): Uint8Array {
  const out = new Uint8Array(12 + data.length);
  const dv = new DataView(out.buffer);
  dv.setUint32(0, data.length);
  for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
  out.set(data, 8);
  dv.setUint32(8 + data.length, crc32(out.subarray(4, 8 + data.length)));
  return out;
}

export async function encodePng(img: RgbImage): Promise<Uint8Array> {
  const { width, height, data } = img;
  const stride = width * 3;
  const raw = new Uint8Array(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    const rowOff = y * stride, rawOff = y * (stride + 1);
    // Pick the filter (None/Sub/Up/Avg/Paeth) minimising sum |signed byte|.
    let bestFilter = 0, bestScore = Infinity, bestRow: Uint8Array | null = null;
    for (let f = 0; f <= 4; f++) {
      const cand = new Uint8Array(stride);
      let score = 0;
      for (let i = 0; i < stride; i++) {
        const v = data[rowOff + i];
        const a = i >= 3 ? data[rowOff + i - 3] : 0;
        const b = y > 0 ? data[rowOff - stride + i] : 0;
        const c = y > 0 && i >= 3 ? data[rowOff - stride + i - 3] : 0;
        let pred = 0;
        if (f === 1) pred = a;
        else if (f === 2) pred = b;
        else if (f === 3) pred = (a + b) >> 1;
        else if (f === 4) {
          const p = a + b - c;
          const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
          pred = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
        }
        const d = (v - pred) & 0xff;
        cand[i] = d;
        score += d < 128 ? d : 256 - d;
      }
      if (score < bestScore) { bestScore = score; bestFilter = f; bestRow = cand; }
    }
    raw[rawOff] = bestFilter;
    raw.set(bestRow!, rawOff + 1);
  }
  const idat = await zlibDeflate(raw);
  const ihdr = new Uint8Array(13);
  const dv = new DataView(ihdr.buffer);
  dv.setUint32(0, width); dv.setUint32(4, height);
  ihdr[8] = 8; ihdr[9] = 2; // 8-bit RGB
  return concatBytes([
    new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk("IHDR", ihdr),
    pngChunk("IDAT", idat),
    pngChunk("IEND", new Uint8Array(0)),
  ]);
}

// ------------------------------------------------------- draw resampling
//
// RW-176 follow-up (2026-11-17, v42): google/nano-banana-pro does NOT
// return its edit at the input's pixel size. In the square era it
// returned exactly 1024x1024 for a 1024x1024 input, so base and draw
// grids coincided and analyseHybrid could compare them pixel for
// pixel. On the v40 portrait recipe (stage-1 base 832x1216) the same
// call returns 848x1264, and analyseHybrid threw "base/draw size
// mismatch" on every draw — every hybrid job burned its 3 draws and
// failed without a single gate measurement ever running. Neither
// model's 2:3 output size can be forced to the other's, so the draw
// is resampled onto the base's grid before any analysis: bilinear
// interpolation on pixel centres (the standard align_corners=false
// mapping), which is an exact identity when the grids already match,
// so the square-era calibration is untouched. All gate measurements
// then run on the base grid with S = base height / 1000, exactly as
// calibrated.
export function resampleToGrid(img: RgbImage, width: number, height: number): RgbImage {
  if (img.width === width && img.height === height) return img;
  const out = new Uint8Array(width * height * 3);
  const xScale = img.width / width;
  const yScale = img.height / height;
  for (let y = 0; y < height; y++) {
    let sy = (y + 0.5) * yScale - 0.5;
    if (sy < 0) sy = 0;
    if (sy > img.height - 1) sy = img.height - 1;
    const y0 = Math.floor(sy);
    const y1 = Math.min(y0 + 1, img.height - 1);
    const fy = sy - y0;
    for (let x = 0; x < width; x++) {
      let sx = (x + 0.5) * xScale - 0.5;
      if (sx < 0) sx = 0;
      if (sx > img.width - 1) sx = img.width - 1;
      const x0 = Math.floor(sx);
      const x1 = Math.min(x0 + 1, img.width - 1);
      const fx = sx - x0;
      const d = (y * width + x) * 3;
      const row0 = y0 * img.width, row1 = y1 * img.width;
      for (let c = 0; c < 3; c++) {
        const top = img.data[(row0 + x0) * 3 + c] * (1 - fx) + img.data[(row0 + x1) * 3 + c] * fx;
        const bot = img.data[(row1 + x0) * 3 + c] * (1 - fx) + img.data[(row1 + x1) * 3 + c] * fx;
        out[d + c] = Math.round(top * (1 - fy) + bot * fy);
      }
    }
  }
  return { width, height, data: out };
}

// ------------------------------------------------------- mask / array utils
// Masks are Uint8Array of 0/1, row-major, length W*H.

function dilate(mask: Uint8Array, W: number, H: number, offsets: Array<[number, number]>): Uint8Array {
  const out = new Uint8Array(W * H);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (!mask[y * W + x]) continue;
      for (const [dy, dx] of offsets) {
        const yy = y + dy, xx = x + dx;
        if (yy >= 0 && yy < H && xx >= 0 && xx < W) out[yy * W + xx] = 1;
      }
    }
  }
  return out;
}

function erode(mask: Uint8Array, W: number, H: number, offsets: Array<[number, number]>): Uint8Array {
  const out = new Uint8Array(W * H);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      let ok = 1;
      for (const [dy, dx] of offsets) {
        const yy = y + dy, xx = x + dx;
        if (yy < 0 || yy >= H || xx < 0 || xx >= W || !mask[yy * W + xx]) { ok = 0; break; }
      }
      out[y * W + x] = ok;
    }
  }
  return out;
}

const OFF_3X3: Array<[number, number]> = [
  [-1, -1], [-1, 0], [-1, 1], [0, -1], [0, 0], [0, 1], [1, -1], [1, 0], [1, 1],
];
function offVert(n: number): Array<[number, number]> {
  const o: Array<[number, number]> = [];
  const r = (n - 1) / 2;
  for (let dy = -r; dy <= r; dy++) o.push([dy, 0]);
  return o;
}
function closing(mask: Uint8Array, W: number, H: number, offsets: Array<[number, number]>): Uint8Array {
  return erode(dilate(mask, W, H, offsets), W, H, offsets);
}

// 8-connected component labelling (two-pass union-find). Returns labels
// (Int32Array, 0 = background, 1..n components) and the component count.
function label8(mask: Uint8Array, W: number, H: number): { lab: Int32Array; n: number } {
  const lab = new Int32Array(W * H);
  const parent: number[] = [0];
  const find = (a: number): number => {
    let r = a;
    while (parent[r] !== r) r = parent[r];
    while (parent[a] !== r) { const nxt = parent[a]; parent[a] = r; a = nxt; }
    return r;
  };
  let next = 1;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const idx = y * W + x;
      if (!mask[idx]) continue;
      const neigh: number[] = [];
      if (x > 0 && y > 0 && lab[idx - W - 1]) neigh.push(lab[idx - W - 1]);
      if (y > 0 && lab[idx - W]) neigh.push(lab[idx - W]);
      if (x < W - 1 && y > 0 && lab[idx - W + 1]) neigh.push(lab[idx - W + 1]);
      if (x > 0 && lab[idx - 1]) neigh.push(lab[idx - 1]);
      if (neigh.length === 0) {
        lab[idx] = next;
        parent.push(next);
        next++;
      } else {
        let m = neigh[0];
        for (const v of neigh) if (v < m) m = v;
        lab[idx] = m;
        for (const v of neigh) {
          const rv = find(v), rm = find(m);
          if (rv !== rm) parent[Math.max(rv, rm)] = Math.min(rv, rm);
        }
      }
    }
  }
  // Compact labels to 1..n via root mapping.
  const remap = new Map<number, number>();
  let n = 0;
  for (let i = 0; i < lab.length; i++) {
    if (!lab[i]) continue;
    const r = find(lab[i]);
    let c = remap.get(r);
    if (c === undefined) { c = ++n; remap.set(r, c); }
    lab[i] = c;
  }
  return { lab, n };
}

function runsOfColumn(mask: Uint8Array, W: number, H: number, x: number,
                       y0 = 0, y1 = -1): Array<[number, number]> {
  if (y1 < 0) y1 = H - 1;
  const out: Array<[number, number]> = [];
  let i = y0;
  while (i <= y1) {
    if (mask[i * W + x]) {
      let j = i;
      while (j + 1 <= y1 && mask[(j + 1) * W + x]) j++;
      out.push([i, j]);
      i = j + 1;
    } else i++;
  }
  return out;
}

function interp(x: number, xs: ArrayLike<number>, ys: ArrayLike<number>): number {
  const n = xs.length;
  if (x <= xs[0]) return ys[0];
  if (x >= xs[n - 1]) return ys[n - 1];
  let lo = 0, hi = n - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (xs[mid] <= x) lo = mid; else hi = mid;
  }
  const t = (x - xs[lo]) / (xs[hi] - xs[lo]);
  return ys[lo] + t * (ys[hi] - ys[lo]);
}

function median(vals: number[]): number {
  const s = [...vals].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

function percentile(vals: number[], q: number): number {
  const s = [...vals].sort((a, b) => a - b);
  const pos = (q / 100) * (s.length - 1);
  const lo = Math.floor(pos), hi = Math.ceil(pos);
  return lo === hi ? s[lo] : s[lo] + (pos - lo) * (s[hi] - s[lo]);
}

// scipy median_filter(size=5, mode='nearest') then gaussian_filter1d
// (sigma=1, mode='reflect', truncate=4) — the prototype's curve smoothing.
function smoothCurve(vals: number[]): number[] {
  const n = vals.length;
  const med = new Array<number>(n);
  for (let i = 0; i < n; i++) {
    const w: number[] = [];
    for (let k = -2; k <= 2; k++) w.push(vals[Math.min(n - 1, Math.max(0, i + k))]);
    med[i] = median(w);
  }
  const radius = 4;
  const kernel: number[] = [];
  let ksum = 0;
  for (let k = -radius; k <= radius; k++) {
    const v = Math.exp(-(k * k) / 2);
    kernel.push(v); ksum += v;
  }
  const out = new Array<number>(n);
  for (let i = 0; i < n; i++) {
    let acc = 0;
    for (let k = -radius; k <= radius; k++) {
      let j = i + k;
      // reflect (half-sample symmetric), scipy 'reflect'
      while (j < 0 || j >= n) j = j < 0 ? -j - 1 : 2 * n - 1 - j;
      acc += med[j] * kernel[k + radius];
    }
    out[i] = acc / ksum;
  }
  return out;
}

function fillSmooth(strip: Map<number, [number, number]>): Map<number, [number, number]> {
  const xs = [...strip.keys()].sort((a, b) => a - b);
  const tops = xs.map((x) => strip.get(x)![0]);
  const bots = xs.map((x) => strip.get(x)![1]);
  const filled = new Map<number, [number, number]>();
  const allX: number[] = [];
  for (let x = xs[0]; x <= xs[xs.length - 1]; x++) {
    filled.set(x, [Math.round(interp(x, xs, tops)), Math.round(interp(x, xs, bots))]);
    allX.push(x);
  }
  const smT = smoothCurve(allX.map((x) => filled.get(x)![0]));
  const smB = smoothCurve(allX.map((x) => filled.get(x)![1]));
  allX.forEach((x, i) => filled.set(x, [Math.round(smT[i]), Math.round(smB[i])]));
  return filled;
}

// ------------------------------------------------------------- the analysis

function strictFab(img: RgbImage): { fab: Uint8Array; lum: Float32Array } {
  const { width: W, height: H, data } = img;
  const fab = new Uint8Array(W * H);
  const lum = new Float32Array(W * H);
  for (let i = 0, n = W * H; i < n; i++) {
    const r = data[i * 3], g = data[i * 3 + 1], b = data[i * 3 + 2];
    const l = 0.299 * r + 0.587 * g + 0.114 * b;
    lum[i] = l;
    fab[i] = l < 72 && b - r >= -2 ? 1 : 0;
  }
  return { fab, lum };
}

// Port of proto_auto.analyse(): derive the band strip from (base, draw),
// feather it, composite the draw's strip onto the base, and measure the
// band (front height + flap dip) on the draw. Throws when no band can be
// found — the caller treats that as a failed draw.
export function analyseHybrid(base: RgbImage, gem: RgbImage): HybridResult {
  if (base.width !== gem.width || base.height !== gem.height) {
    throw new Error("RW176: base/draw size mismatch");
  }
  const W = base.width, H = base.height;
  const S = H / 1000;
  const { fab: fabG, lum: lumG } = strictFab(gem);
  const { fab: fabB } = strictFab(base);
  const cand = new Uint8Array(W * H);
  const loose = new Uint8Array(W * H);
  for (let i = 0, n = W * H; i < n; i++) {
    const d = (Math.abs(gem.data[i * 3] - base.data[i * 3]) +
               Math.abs(gem.data[i * 3 + 1] - base.data[i * 3 + 1]) +
               Math.abs(gem.data[i * 3 + 2] - base.data[i * 3 + 2])) / 3;
    if (d > 20 && lumG[i] < 140) cand[i] = 1;
    if (d > 10 && lumG[i] < 160) loose[i] = 1;
  }

  // Seed: changed fabric in the head rows, largest 8-connected component.
  const fabBDil = dilate(fabB, W, H, OFF_3X3);
  const changed = new Uint8Array(W * H);
  for (let i = 0; i < W * H; i++) changed[i] = cand[i] && fabG[i] && !fabBDil[i] ? 1 : 0;
  const seedMask = closing(changed, W, H, OFF_3X3);
  const headRows = Math.floor(0.45 * H);
  for (let y = headRows; y < H; y++) seedMask.fill(0, y * W, (y + 1) * W);
  const { lab, n } = label8(seedMask, W, H);
  if (n === 0) throw new Error("RW176: no band seed (no changed fabric in head rows)");
  const sizes = new Float64Array(n + 1);
  for (let i = 0; i < W * H; i++) if (lab[i]) sizes[lab[i]]++;
  let seedId = 1;
  for (let i = 2; i <= n; i++) if (sizes[i] > sizes[seedId]) seedId = i;
  if (sizes[seedId] < 120) throw new Error(`RW176: band seed too small (${sizes[seedId]}px)`);
  let sxMin = W, sxMax = -1, syMin = H, syMax = -1;
  const seedYs: number[] = [];
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (lab[y * W + x] === seedId) {
        if (x < sxMin) sxMin = x;
        if (x > sxMax) sxMax = x;
        if (y < syMin) syMin = y;
        if (y > syMax) syMax = y;
        seedYs.push(y);
      }
    }
  }
  const centre = median(seedYs);
  const zx0 = Math.max(0, Math.trunc(sxMin - 60 * S));
  const zx1 = Math.min(W - 1, Math.trunc(sxMax + 60 * S));
  const zy0 = Math.max(0, Math.trunc(syMin - 30 * S));
  const zy1 = Math.min(H - 1, Math.trunc(syMax + 70 * S));

  const fabzFull = new Uint8Array(W * H);
  for (let y = zy0; y <= zy1; y++) {
    for (let x = zx0; x <= zx1; x++) fabzFull[y * W + x] = fabG[y * W + x];
  }
  const fabz = closing(fabzFull, W, H, offVert(5));

  // v3 strip: per-column core/candp run picking around the seed centre.
  const core = new Map<number, [number, number]>();
  const candp = new Map<number, [number, number]>();
  for (let x = zx0; x <= zx1; x++) {
    const runs = runsOfColumn(fabz, W, H, x).filter(([a, b]) => b - a + 1 >= 8);
    if (runs.length) {
      let best = runs[0];
      for (const r of runs) {
        if (Math.abs((r[0] + r[1]) / 2 - centre) < Math.abs((best[0] + best[1]) / 2 - centre)) best = r;
      }
      core.set(x, [best[0], best[1]]);
    }
    const cruns = runsOfColumn(cand, W, H, x, zy0, zy1).filter(([a, b]) => b - a + 1 >= 6);
    if (cruns.length) {
      let best = cruns[0];
      for (const r of cruns) {
        if (Math.abs((r[0] + r[1]) / 2 - centre) < Math.abs((best[0] + best[1]) / 2 - centre)) best = r;
      }
      let a2 = best[0];
      while (a2 - 1 >= 0 && best[0] - a2 < 10 && loose[(a2 - 1) * W + x]) a2--;
      let b2 = best[1];
      while (b2 + 1 < H && b2 - best[1] < 10 && loose[(b2 + 1) * W + x]) b2++;
      candp.set(x, [a2, b2]);
    }
  }
  if (core.size === 0 && candp.size === 0) throw new Error("RW176: v3 found no core/cand columns");
  const xsRef = [...new Set([...core.keys(), ...candp.keys()])].sort((a, b) => a - b);
  const topRef = xsRef.map((x) => Math.min((core.get(x) ?? candp.get(x)!)[0], (candp.get(x) ?? core.get(x)!)[0]));
  const botRef = xsRef.map((x) => Math.max((core.get(x) ?? candp.get(x)!)[1], (candp.get(x) ?? core.get(x)!)[1]));
  const strip3Raw = new Map<number, [number, number]>();
  for (let x = xsRef[0] - 55; x <= xsRef[xsRef.length - 1] + 55; x++) {
    if (x < 0 || x >= W) continue;
    const ti = interp(x, xsRef, topRef);
    const bi = interp(x, xsRef, botRef);
    const parts = [core.get(x), candp.get(x)].filter((p) => p !== undefined) as Array<[number, number]>;
    if (parts.length) {
      const a = Math.max(Math.min(...parts.map((p) => p[0])), Math.trunc(ti) - 20);
      const bb = Math.min(Math.max(...parts.map((p) => p[1])), Math.trunc(bi) + 26);
      if (bb - a + 1 >= 8) strip3Raw.set(x, [a, bb]);
    }
  }
  const strip3 = fillSmooth(strip3Raw);

  // v4 completion: connected component of (fabz | cand) inside a corridor
  // around the v3 strip.
  const xs3 = [...strip3.keys()].sort((a, b) => a - b);
  const top3 = xs3.map((x) => strip3.get(x)![0]);
  const bot3 = xs3.map((x) => strip3.get(x)![1]);
  const corr = new Uint8Array(W * H);
  for (let x = Math.max(0, xs3[0] - 30); x < Math.min(W, xs3[xs3.length - 1] + 31); x++) {
    const t = Math.trunc(Math.max(0, interp(x, xs3, top3) - 12));
    const b = Math.trunc(Math.min(H, interp(x, xs3, bot3) + 50));
    for (let y = t; y <= Math.min(H - 1, b); y++) corr[y * W + x] = 1;
  }
  let P: Uint8Array = new Uint8Array(W * H);
  for (let i = 0; i < W * H; i++) P[i] = (fabz[i] || cand[i]) && corr[i] ? 1 : 0;
  P = closing(P, W, H, OFF_3X3);
  P = closing(P, W, H, offVert(9));
  const v3mask = new Uint8Array(W * H);
  for (const [x, [a, bb]] of strip3) {
    for (let y = a; y <= bb; y++) v3mask[y * W + x] = 1;
  }
  const labP = label8(P, W, H);
  let strip = strip3;
  if (labP.n > 0) {
    const overlap = new Float64Array(labP.n + 1);
    for (let i = 0; i < W * H; i++) if (v3mask[i] && labP.lab[i]) overlap[labP.lab[i]]++;
    let compId = 1;
    for (let i = 2; i <= labP.n; i++) if (overlap[i] > overlap[compId]) compId = i;
    const filled = new Map<number, [number, number]>();
    for (let x = 0; x < W; x++) {
      let mn = -1, mx = -1;
      for (let y = 0; y < H; y++) {
        const i = y * W + x;
        if ((labP.lab[i] === compId || v3mask[i]) && (corr[i] || v3mask[i])) {
          if (mn < 0) mn = y;
          mx = y;
        }
      }
      if (mn >= 0 && mx - mn + 1 >= 8) filled.set(x, [mn, mx]);
    }
    if (filled.size) strip = fillSmooth(filled);
  }

  // Alpha (separable Gaussian, sigma 1.7, finite radius ceil(4*sigma) = 7,
  // so alpha is exactly 0 beyond the feather and the composite there is
  // bit-identical to the base) + composite.
  const mask = new Float32Array(W * H);
  for (const [x, [a, bb]] of strip) {
    for (let y = a; y <= bb; y++) mask[y * W + x] = 1;
  }
  const sigma = 1.7, radius = Math.ceil(4 * sigma);
  const kernel: number[] = [];
  let ksum = 0;
  for (let k = -radius; k <= radius; k++) {
    const v = Math.exp(-(k * k) / (2 * sigma * sigma));
    kernel.push(v); ksum += v;
  }
  const tmp = new Float32Array(W * H);
  const alpha = new Float32Array(W * H);
  const reflect = (j: number, n: number) => {
    while (j < 0 || j >= n) j = j < 0 ? -j - 1 : 2 * n - 1 - j;
    return j;
  };
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      let acc = 0;
      for (let k = -radius; k <= radius; k++) acc += mask[y * W + reflect(x + k, W)] * kernel[k + radius];
      tmp[y * W + x] = acc / ksum;
    }
  }
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      let acc = 0;
      for (let k = -radius; k <= radius; k++) acc += tmp[reflect(y + k, H) * W + x] * kernel[k + radius];
      const a = (acc / ksum) * 1.15;
      alpha[y * W + x] = a < 0 ? 0 : a > 1 ? 1 : a;
    }
  }
  const compData = new Uint8Array(W * H * 3);
  let outsideMaxDiff = 0;
  for (let i = 0, n = W * H; i < n; i++) {
    const a = alpha[i];
    for (let c = 0; c < 3; c++) {
      const v = Math.round(base.data[i * 3 + c] * (1 - a) + gem.data[i * 3 + c] * a);
      compData[i * 3 + c] = v < 0 ? 0 : v > 255 ? 255 : v;
    }
    if (a === 0) {
      const d = Math.abs(compData[i * 3] - base.data[i * 3]);
      if (d > outsideMaxDiff) outsideMaxDiff = d;
    }
  }

  // Measurements (the prototype's shallow-bottom front rule).
  const xs = [...strip.keys()].sort((a, b) => a - b);
  const botsAll = xs.map((x) => strip.get(x)![1]);
  const shallowCut = percentile(botsAll, 25) + 12 * S;
  const candCols: Array<[number, number]> = [];
  for (const x of xs) {
    const [sa, sb] = strip.get(x)!;
    const cen = (sa + sb) / 2;
    const runs = runsOfColumn(fabz, W, H, x).filter(([a, b]) => b - a + 1 >= 8);
    if (!runs.length) continue;
    let best = runs[0];
    for (const r of runs) {
      if (Math.abs((r[0] + r[1]) / 2 - cen) < Math.abs((best[0] + best[1]) / 2 - cen)) best = r;
    }
    const ov = Math.min(best[1], sb) - Math.max(best[0], sa) + 1;
    if (ov >= 0.5 * (best[1] - best[0] + 1)) candCols.push([x, best[1] - best[0] + 1]);
  }
  let frontCols = candCols.filter(([x]) => strip.get(x)![1] <= shallowCut).map(([x]) => x);
  if (frontCols.length < 5) {
    const byBot = [...candCols].sort((p, q) => strip.get(p[0])![1] - strip.get(q[0])![1]);
    frontCols = byBot.slice(0, Math.max(5, Math.floor(byBot.length / 4))).map(([x]) => x);
  }
  const hmap = new Map(candCols);
  let heights = frontCols.map((x) => hmap.get(x)!);
  let frontH: number | null = null;
  if (heights.length) {
    const med = median(heights);
    heights = heights.filter((h) => h <= 1.5 * med);
    if (!heights.length) heights = frontCols.map((x) => hmap.get(x)!);
    frontH = median(heights) / S;
  }
  const frontBot = median(frontCols.map((x) => strip.get(x)![1])) / S;
  const frontSet = new Set(frontCols);
  const nonfront = xs.filter((x) => !frontSet.has(x)).map((x) => strip.get(x)![1] / S - frontBot);
  const allDips = xs.map((x) => strip.get(x)![1] / S - frontBot);
  const meas: HybridMeasurements = {
    frontHNorm: frontH !== null ? Math.round(frontH * 10) / 10 : null,
    frontBotNorm: Math.round(frontBot * 10) / 10,
    dipP90Norm: nonfront.length ? Math.round(percentile(nonfront, 90) * 10) / 10 : null,
    dipMaxNorm: Math.round(Math.max(...allDips) * 10) / 10,
    outsideMaxDiff,
    stripXNorm: [Math.round(xs[0] / S), Math.round(xs[xs.length - 1] / S)],
    seedCentreNorm: Math.round((centre / S) * 10) / 10,
  };
  return { composite: { width: W, height: H, data: compData }, meas, strip };
}

// ------------------------------------------------- beanie gates (v38)
//
// The beanie route reuses the same strip extraction + transplant (the
// phase-1C validation ran this exact algorithm on beanie draws). Its
// gates are beanie-specific and were calibrated on (base, draw) pairs —
// see release-prep/rw176-production-2026-11-17/beanie/
// beanie_gate_calibration.json:
//
//   tuned-prompt draws (6):  crown median 48.8 – 95.7 norm
//   tall-prompt draws (2):   crown median 108.4 / 131.8 norm (visually
//                            absurd towers — the reject ground truth)
//   Sol's flagged render:    beanie:face grounding ratio 1.38x, vs
//                            0.93x / 1.10x for the tuned hybrids.
//
// Accept at or below the accept line (every tuned draw passes); a draw
// in the retry band is never served — it is retried, and the job fails
// after RW176_MAX_DRAWS; a draw over the hard line fails the job at
// once, mirroring the ear-band hard reject.
export const RW176_BEANIE_ACCEPT_CROWN_MAX_NORM = 100.0;
export const RW176_BEANIE_HARD_CROWN_NORM = 125.0;
// Presence: a real beanie over the crown reads far above these floors
// on both instruments (tuned minima: crown 48.8, width 96.7). A draw
// under either floor is treated as a garment-missing draw.
export const RW176_BEANIE_MIN_CROWN_NORM = 25.0;
export const RW176_BEANIE_MIN_STRIP_WIDTH_NORM = 60.0;
// Hair-tuft guard: fires when a run of base head content the draw
// removed stands at least RW176_TUFT_FIRE_NORM tall above the strip's
// top edge in at least RW176_TUFT_MIN_COLS columns (a column counts at
// RW176_TUFT_COL_MIN_NORM). Calibration: the phase-1C DAWN artifact
// reads 24.4 norm / 6 columns (fires); the clean DUSK and FEMALE
// hybrids read at most 7.8 norm / 0 columns (never fires); a narrow
// 2-column spike on a tall DUSK draw (20.5 norm) does not fire — the
// column-count term is what separates a silhouette crescent from an
// edge artefact.
export const RW176_TUFT_FIRE_NORM = 15.0;
export const RW176_TUFT_COL_MIN_NORM = 12.0;
export const RW176_TUFT_MIN_COLS = 4;

export interface BeanieMeasurements {
  crownHMedianNorm: number | null;
  crownHMaxNorm: number | null;
  stripWidthNorm: number;
  tuftMaxNorm: number;
  tuftColsOver12: number;
  tuftFires: boolean;
}

// Beanie measurements from a completed hybrid analysis: crown height
// over the central third of strip columns (the phase-1C instrument),
// strip width, and the hair-tuft scan. The tuft scan walks upward from
// just above the strip's top edge (skipping the ~9px feather/edge zone)
// and measures the longest run of pixels where the base still holds
// content the draw removed — mean-abs channel diff > 25 with base
// luminance < 170, gap tolerance 2px, scan depth 80 norm. That is the
// DAWN signature exactly: base hair left standing above the
// transplanted beanie because the draw's beanie sat inside the base's
// hair silhouette.
export function beanieMeasurements(base: RgbImage, gem: RgbImage,
                                   strip: Map<number, [number, number]>): BeanieMeasurements {
  const W = base.width, H = base.height;
  const S = H / 1000;
  const xs = [...strip.keys()].sort((a, b) => a - b);
  const heights = xs.map((x) => (strip.get(x)![1] - strip.get(x)![0] + 1) / S);
  const n = heights.length;
  const mid = heights.slice(Math.floor(n / 3), Math.floor((2 * n) / 3));
  let tuftMax = 0;
  let tuftCols = 0;
  for (const x of xs) {
    const top = strip.get(x)![0];
    const yMin = Math.max(0, top - Math.trunc(80 * S));
    let best = 0, run = 0, gap = 0;
    for (let y = top - 9; y >= yMin; y--) {
      const i = (y * W + x) * 3;
      const d = (Math.abs(gem.data[i] - base.data[i]) +
                 Math.abs(gem.data[i + 1] - base.data[i + 1]) +
                 Math.abs(gem.data[i + 2] - base.data[i + 2])) / 3;
      const lumB = 0.299 * base.data[i] + 0.587 * base.data[i + 1] + 0.114 * base.data[i + 2];
      if (d > 25 && lumB < 170) {
        run++; gap = 0;
        if (run > best) best = run;
      } else {
        gap++;
        if (gap > 2) break;
      }
    }
    const v = best / S;
    if (v > tuftMax) tuftMax = v;
    if (v >= RW176_TUFT_COL_MIN_NORM) tuftCols++;
  }
  const tuftMaxNorm = Math.round(tuftMax * 10) / 10;
  return {
    crownHMedianNorm: mid.length ? Math.round(median(mid) * 10) / 10 : null,
    crownHMaxNorm: mid.length ? Math.round(Math.max(...mid) * 10) / 10 : null,
    stripWidthNorm: Math.round(((xs[xs.length - 1] - xs[0]) / S) * 10) / 10,
    tuftMaxNorm,
    tuftColsOver12: tuftCols,
    tuftFires: tuftMaxNorm >= RW176_TUFT_FIRE_NORM && tuftCols >= RW176_TUFT_MIN_COLS,
  };
}
// === RW176_HYBRID_CORE_END ===
