// Supabase Edge Function: personal-hero-worker (RW-16.30, 2026-11-02)
//
// Fulfills personal_generation_jobs for Make It Me on the PRODUCTION
// Route 2 pipeline — the pipeline Sol ruled on (2026-11-02): full-head
// GENERATION with zsxkib/flux-pulid from the user's single reference
// photo, replacing the v23 FLUX Kontext editor + two-stage redress +
// LLaVA gate wholesale. The editor preserved the reference's street
// clothes (the dress-shirt defect family) and the 7B gate could not
// see (audit F3/F12); generation has neither failure mode, and it is
// the first route to pass Sol's eye (RW-16.30 comparison + full-body
// records).
//
// Pipeline order per draw (commission, fixed):
//   generate (flux-pulid, pinned Part-4 prompt, single reference)
//     -> IDENTITY SCREEN (InsightFace buffalo_l embedding cosine via
//        the identity-screen service; FREE — a draw that fails the
//        screen is discarded before any paid call)
//     -> PRESCRIPTION GATE (the recalibrated gate: claude-sonnet-5
//        enumeration + the five recalibration rulings in code, ported
//        verbatim from the lanes' adjudicate_compare.py; verdicts
//        cached by image sha256 in personal_gate_verdicts)
//     -> deliver, or fall back to stock for that cell.
// Draw counts by cell difficulty (stop at first usable): Warm 2,
// Cold 3, Rain 4, Snow 5. There is NO repair stage: a gate-failed
// draw is a counted failure and the loop redraws within budget.
// (An accessory inpaint repair stage existed until 2026-11-03; the
// persisting re-battery measured its rescue rate at 0/15 — the
// head-band mask repaints the face and fails the identity re-screen
// by construction — and Sol ruled it dropped, 2026-11-03 04:13 EST.)
//
// HEADWEAR EDIT PASS (added 2026-11-03, after the gate in the order
// above): a draw that passed the identity screen WITH HEADROOM
// (similarity >= 0.48) and failed the gate ONLY on headwear absence
// gets exactly ONE instruction-following edit
// (black-forest-labs/flux-kontext-pro) that adds the prescribed head
// item in its production prompt wording and changes nothing else.
// The edited image must re-pass the identity screen (>= 0.42) AND
// the full prescription gate; only then is it the delivered photo
// for that draw. Evidence: the headwear A/B proved the identity
// conditioning is what suppresses headwear (presence 40% with the
// reference vs 100% without), and the edit-pass bench test rescued
// 15/19 eligible head-only failures (79%) at a median identity tax
// of -0.093 — with every post-edit identity loss starting below
// 0.48, which is why the headroom line exists. The edit is a
// transformation OF the current draw: it consumes no extra draw
// from the budget, runs at most once per draw, and is never applied
// to an edited image. It is NOT the dropped inpaint stage above:
// that stage repainted a masked band across the face and failed the
// identity re-screen by construction (0/15); this is a whole-image
// instruction edit whose output is re-screened and re-gated like
// any other candidate. If either re-check fails, the draw counts as
// failed with its ORIGINAL gate verdict and the budget continues;
// the attempt and its outcome ride on the draw record (edit field).
//
// Epoch discipline (RW-16.30): jobs carry the photo-set epoch they
// were enqueued under. A job whose epoch no longer matches the
// profile's is superseded — failed closed, no spend, no delivery, no
// charge — so a photo change can never deliver the old face, and the
// bank is cached per epoch (a photo change starts a new epoch for
// that user only).
//
// Free-allowance cap (audit F6): a free_on_demand delivery claims the
// allowance through public.claim_free_delivery INSIDE the delivery
// path — atomically, only for a never-counted cell, only below the
// cap of 5. A refused claim stores nothing.
//
// Fallback accounting (RW-16.20): when an ENTITLED user's job
// exhausts its draw budget without a delivery, a
// personal_fallback_events row records the cell + reason. The
// fallback to stock exists only for gate-failed cells and is never
// silent.
//
// Fail-closed configuration: the identity screen
// (IDENTITY_SCREEN_URL / IDENTITY_SCREEN_KEY) and the gate
// (ANTHROPIC_API_KEY) are hard dependencies. If either is missing or
// errors, the job fails with the named stage error and NOTHING
// ungated or unscreened is ever stored. There is no fallback judge
// (AC-19: no uncalibrated substitute).
//
// Kept from v23 unchanged in behaviour: the guarded QUEUED claim,
// stale-claim recovery (STALE_PROCESSING_MS / reclaim budget /
// replicate_id resume), the drain + soft time budget, service-role
// auth, the regeneration swap retirement (Lane C), the reference
// derivative loading (personal-ref-prep), and the delivery
// bookkeeping shape (cell ever_delivered / free_counted).
//
// Gen-6 Wave A amendment (2026-11-05, RW-38/DA-02 — the queue fix):
// the claim ORDER and the drain SHAPE above are superseded. The
// worker now claims by effective band (claimBand below): the
// current/imminent cell first, explicit on-demand next, forecast
// fill below it, replenishment last, with jobs that already burned
// a draw demoted half a band. And the drain is processing-bound:
// passes repeat until the queue empties or CHAIN_TIME_BUDGET_MS is
// spent, then the worker self-chains; the 5-minute pg_cron is a
// backstop, not the metronome. Everything else in this header
// stands as written.
//
// RW-80 amendment (2026-11-06): two changes, both evidence-led.
// (1) claimed_at: the guarded claim now also stamps a dedicated
// claimed_at column, set ONLY at claim time. started_at cannot
// serve as the claim stamp — it is the draw-boundary heartbeat
// (RW-16.25 discipline, refreshed at every draw so the stale scan
// can tell a live job from a terminated invocation), so reading
// started_at - requested_at as "claim latency" systematically
// overstates it by the whole generation time of the earlier draws
// (this misreading is what filed RW-80: gate-verdict timestamps
// prove Sol's 2026-11-06 cold jobs were claimed within ~4 s and
// their first draws were already gated ~25 s after the request,
// while started_at showed 63-65 s — the LAST draw's start).
// The drain-on-enqueue kick itself already exists on every
// enqueue path (request-personal-generation, start-personal-fill,
// get-personal-hero) and is unchanged. (2) Rain fidelity: the
// ENVIRONMENT light prefix is now weather-aware for RAIN cells
// and SCENE.RAIN names visible rain. Diagnosis from production
// gate records: gated RAIN draws at DAWN failed the conditions
// read ('clear', ruling 3) 17 of 19 times while MIDDAY rain draws
// failed it 0 of 15 — the weather-blind LIGHT text for DAWN/DUSK
// ("soft golden sunrise light, long shadows") paints direct sun
// into the scene and overrides the rain clause for both the
// generator and the gate's read. Non-rain prompts are byte-
// identical to before this amendment.
//
// RW-90 amendment (2026-11-06): a submitted draw can no longer
// orphan its job. Field case (Sol's bank burst, 2026-11-06
// 23:42 UTC): three draws were submitted to Replicate and their
// invocations died mid-poll — no gate verdict, no error, no
// completion was ever recorded; one job sat in PROCESSING until
// the stale scan reclaimed it ~8 minutes later (it then delivered
// from the retained prediction), and a fourth job failed at
// delivery with "Hero upload failed: The resource already
// exists" because an interrupted earlier attempt had stored the
// object and died before the hero row landed. Three changes:
// (1) HEARTBEATS DURING POLLS — pollPrediction now refreshes the
// job's started_at every POLL_HEARTBEAT_MS while it waits, so a
// live job never looks stale and a dead invocation's job goes
// stale on schedule; the draw-boundary heartbeat alone left the
// whole poll window (up to 180 s, twice with the edit pass)
// unrefreshed against the 300 s stale threshold. (2) DRAW-LEVEL
// TERMINAL DISCIPLINE — an outcome of a SUBMITTED draw (poll
// timeout, prediction failed/canceled, poll HTTP error) is a
// counted failed draw inside the job's existing budget, recorded
// on the draw record ('draw_timeout' / 'draw_failed'), and the
// loop redraws; only budget exhaustion fails the job, with the
// reason recorded. Creating a prediction stays job-fatal (no
// draw was submitted), as do the fail-closed screen/gate
// dependencies. The per-draw poll bound is
// DRAW_POLL_TIMEOUT_MS = 180 s: observed draws complete in
// ~19-21 s (RW-80 gate timestamps; the RW-90 control job ran
// end-to-end in 23.6 s), so 180 s is ~9x the observed draw with
// headroom for provider slowness, and it stays below
// STALE_PROCESSING_MS (300 s = one draw poll plus screen/gate/
// edit headroom at a 30 s heartbeat cadence), which remains the
// system bound: a job whose invocation dies reaches a terminal
// state via the reclaim budget (3 reclaims, then FAILED
// stale_processing_timeout) instead of sitting in PROCESSING
// indefinitely. (3) IDEMPOTENT DELIVERY — if the hero object
// already exists at the deterministic delivery path (an
// interrupted attempt of this same job stored it and died
// before the row insert), deliver() overwrites it with this
// draw's verified bytes and completes the delivery instead of
// failing the job on a duplicate.
//
// RW-96 note (2026-11-06): in-view promotion is written by the
// view functions (request-personal-generation's already-queued
// branch and get-personal-hero's convergence block stamp a
// viewed cell's pending subscriber_fill job with priority 15,
// the convergence class in claimBand below). The band system
// itself is unchanged.
//
// RW-107 amendment (2026-11-06): a counted cell can no longer be
// left without a hero. Field case (Sol, 2026-11-06): Juneau job
// 9bba9eb8 delivered normally — hero row written, free counter
// claimed, job completed 'delivered' with its result_hero_id
// set. Sol, shown the stock fallback by the RW-106 client render
// defect, flagged the photo ("doesn't look like me"); flag_hero
// deleted the hero row, and the result_hero_id foreign key's
// ON DELETE SET NULL retroactively nulled the job's hero link —
// leaving a COMPLETED/'delivered' job with result_hero_id NULL,
// no hero row, and a counter that said the look was spent. Only
// the regeneration flag_hero itself queued rescued the cell;
// had the hero been lost by any route that does not queue its
// own regeneration (admin deletion, a regeneration cycle dying
// terminally after a flag, storage/row drift), the cell would
// have stayed counted-but-empty forever — a second live case
// (a Nov 1 cell, four gate-failed regenerations, pending ever
// since) sat in exactly that state for five days. Two changes:
// (1) CONDITIONAL COMPLETION — deliver()'s 'delivered' stage
// transition is now a checked write, guarded on the job still
// being PROCESSING and re-verifying the hero row exists at the
// moment of the transition; if the row has vanished mid-delivery
// (e.g. a racing flag), deliver() throws and the job fails
// visibly instead of persisting a 'delivered' with no hero
// behind it. (2) RECONCILIATION SWEEP — every drain runs
// reconcileOrphanedCells() alongside the stale scan: a
// personal_cells row that is counted (free_counted, or
// ever_delivered for an entitled claim) with NO hero row and NO
// live job is detected server-side and repaired by queueing a
// 'regeneration' job (no allowance charge — regeneration kind
// never claims) at the profile's current epoch, stamping the
// cell regen-pending. The sweep is the mechanism because drains
// run on the pg_cron backstop independent of any user action —
// the repair must not wait for the user to view the cell or
// complain. Loop safety: a cell whose regen-pending stamp is
// fresher than RECONCILE_RETRY_AFTER_MS (24 h) is skipped, so a
// cell whose repair cycle terminally fails is retried at most
// daily instead of on every drain. Candidate selection runs in
// the database (public.reconcile_candidates) so the per-drain
// scan limit bounds true candidates — counted, hero-less,
// job-less — rather than the oldest healthy counted cells.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { Image } from "https://deno.land/x/imagescript@1.2.17/mod.ts"

const REPLICATE_API_TOKEN = Deno.env.get('REPLICATE_API_TOKEN')!
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const ANTHROPIC_API_KEY = Deno.env.get('ANTHROPIC_API_KEY') ?? ''
const IDENTITY_SCREEN_URL = (Deno.env.get('IDENTITY_SCREEN_URL') ?? '').replace(/\/$/, '')
const IDENTITY_SCREEN_KEY = Deno.env.get('IDENTITY_SCREEN_KEY') ?? ''

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

// Route 2 generation: the exact model the comparison + full-body
// lanes validated (community model — called version-pinned via
// /v1/predictions with the latest version resolved per invocation,
// the lanes' predict pattern).
const GENERATION_MODEL_SLUG = 'zsxkib/flux-pulid'
// The gate instrument of record: same model string the lanes'
// adjudicate_compare.py sends (env-overridable for the workspace's
// deployment naming, never for a different judge).
const GATE_MODEL = Deno.env.get('MIM_GATE_MODEL') ?? 'claude-sonnet-5-5'
const GATE_MAX_TOKENS = 900

const VARIANT_CAP = 3
const DEFAULT_DRAIN_LIMIT = 1
// Gen-6 Wave A queue fix (2026-11-05): the drain is now
// processing-bound, not invocation-bound. MAX_DRAIN_LIMIT is the
// per-PASS batch; the handler keeps passing over the queue until
// CHAIN_TIME_BUDGET_MS is spent, then self-chains (re-invokes
// itself) while jobs remain, with the 5-minute pg_cron as backstop
// only. SOFT_TIME_BUDGET_MS remains the per-pass soft budget.
const MAX_DRAIN_LIMIT = 6
const SOFT_TIME_BUDGET_MS = 100_000
const CHAIN_TIME_BUDGET_MS = 200_000
const CLAIM_FETCH_LIMIT = 200
// Identity screen delivered line (instrument of record since the
// 2026-11-03 swap: YuNet+SFace cosine >= 0.42 = delivered — the
// licensing package's measured line for the SFace stack. Deployed
// as worker v33 together with the swapped screen; this constant
// must move with the screen's IDENTITY_THRESHOLD, or SFace scores
// would silently be judged on buffalo_l's 0.40 line).
const IDENTITY_DELIVERED_MIN = 0.42

// Headwear edit pass (2026-11-03): the edit model + the headroom
// line. A gate-failed draw is offered the edit only when its
// identity similarity clears EDIT_MIN_SIMILARITY — the edit test
// measured a median identity tax of -0.093 from editing, and every
// post-edit identity loss started below 0.48, so a draw under the
// line counts as failed without an edit attempt.
export const EDIT_MIN_SIMILARITY = 0.48
const EDIT_MODEL_SLUG = 'black-forest-labs/flux-kontext-pro'

// Stale-claim recovery — unchanged from v23 (see the v23 header for
// the full reasoning; thresholds and budgets carry over verbatim).
const STALE_PROCESSING_MS = 300_000
const STALE_MAX_RECLAIMS = 3
const STALE_FAILURE_REASON = 'stale_processing_timeout'

// RW-90 draw bounds (see the RW-90 header amendment for the full
// reasoning): the per-draw poll bound, ~9x the observed ~19-21 s
// draw; and the heartbeat cadence maintained DURING a poll, so a
// live job's started_at never ages more than ~30 s plus one
// screen/gate call while it waits on a prediction.
const DRAW_POLL_TIMEOUT_MS = 180_000
const POLL_HEARTBEAT_MS = 30_000

// RW-107 reconciliation bounds (see the RW-107 header amendment):
// candidates scanned per drain, oldest-stuck first; the repair
// priority is the regeneration class request-personal-generation
// uses for the same job kind; and the retry floor — a cell whose
// regen-pending stamp is younger than this is left alone, so a
// persistently failing repair retries at most once a day.
const RECONCILE_SCAN_LIMIT = 25
const RECONCILE_RETRY_AFTER_MS = 24 * 60 * 60 * 1000
const REPAIR_PRIORITY = 20

// ---------------------------------------------------------------------
// Draw budget by cell difficulty (commission §3): stop at first
// usable. Weather dominates (Rain 4, Snow 5); the layered cold
// brackets get 3; everything else 2 (the Warm case).
// ---------------------------------------------------------------------
export function drawBudgetFor(weather: string, bracket: string): number {
  let budget = 2
  if (bracket === 'FREEZING' || bracket === 'COLD') budget = Math.max(budget, 3)
  if (weather === 'RAIN') budget = Math.max(budget, 4)
  if (weather === 'SNOW') budget = Math.max(budget, 5)
  return budget
}

// ---------------------------------------------------------------------
// Prompt — design-spec Part 4 structure, ported from the refill
// lane's plan.py build_prompt (the pinned recipe of record), with the
// recalibration header ("OUTFIT:", no legibility-pressure line) the
// comparison/full-body drivers applied at send time. Bullets are the
// ENGINE's point-grain prescription (job.outfit_items), one per item.
// EMPHASIS (yield experiment arm B, adopted 2026-11-03): the
// prescribed head and hands bullets move to the FRONT of the outfit
// list (head first) and both items are named in the opening subject
// sentence ("…, wearing a light beanie and light gloves, running
// mid-stride"); the remaining bullets keep wearing order (base, mid,
// outer, bottom, then accessories). Measured on the production host:
// delivery 12.5% → 38.9%, cost per usable $0.221 → $0.072 (record:
// rw-mim-yield-experiment-2026-11-03). The construction is the
// experiment's own, generalized only in that a prescription carrying
// just one of head/hands names and fronts that one item; with both
// prescribed the output is byte-identical to arm B. Expected garment
// classes are unchanged (same items, different order/emphasis) —
// the gate derives them from the outfit segment, which is
// order-insensitive. Nothing follows the MOOD line.
// RAIN: the rain-jacket bullet pins the hood DOWN ("…with the hood
// down") — the bare bullet let FLUX render the jacket hood-up, and
// the raised hood occupied the head slot and ate the prescribed cap
// (yield experiment Part 1 addendum). "hood" maps to no garment
// class, so no gate expectation moves.
// DESCRIPTIVE EXPANSION (yield experiment ROUND TWO arm F, adopted
// 2026-11-03): every prescribed HEAD and HANDS item is described by
// its attributes instead of merely named — in the subject sentence
// AND in its fronted bullet (HEAD_DESC / HANDS_DESC below). Measured
// on the production host against the emphasis control: delivery
// 22.2% → 40.3% (z = 2.34; snow 7.1% → 40.9% throttle-adjusted),
// cost per usable $0.105 → $0.067, identity screen unharmed
// (record: rw-mim-yield-round2-2026-11-03). The wording is the
// experiment's verbatim (as adopted), including its one documented
// deviation: the headband was a "wide KNITTED headband", not fleece
// — "fleece" is the gate parser's mid-layer token and would have
// moved the expected classes. Every description was checked against
// classesFromText: each still parses to the item's own class, so
// expected garment classes are unchanged. VISOR was not exercised
// by the experiment; its wording extends the cap's in the identical
// spirit ("a running visor with the brim facing forward") and is
// the only non-experiment wording in the maps. Round two's other
// arms (G completeness close, H extended emphasis, I exclusion)
// MATCHED the control and are NOT adopted.
// RUNNING FORM (Sol's direction, 2026-11-03, after his bench
// review: the headwear rendered as knit/fashion beanies, not
// athletic gear): every prescribed garment is now named in its
// RUNNING form throughout the prompt — subject sentence, HEAD_DESC /
// HANDS_DESC, and outfit bullets ("running beanie", "running
// neck gaiter", "running rain jacket", …). The arm-F attribute
// descriptions survive around the qualified noun; the two "knitted"
// descriptors do NOT — knit is the fashion material Sol flagged, so
// the beanie is "a running beanie pulled down over the ears" and
// the headband "a wide running headband covering the ears" (the
// fleece trap is unchanged: "fleece" still never names a garment
// outside FLEECE_PULLOVER itself). Every new string was re-checked
// against classesFromText over the full canonical cell set plus a
// per-item sweep: expected garment classes are unchanged for every
// cell (build record §19).
// SUNSCREEN (Sol's ruling, 2026-11-03, later the same day —
// "Don't need to mention sunscreen in a prompt!"): SUNSCREEN is
// not named ANYWHERE in the prompt. It is advice for the runner,
// not something a picture can show. The v31 pass had left it as
// the one unqualified bullet; Sol overruled the exception. The
// PRESCRIPTION is untouched — the engine still prescribes
// sunscreen and the app's outfit list still shows it; only the
// image-generation prompt drops it (buildPrompt filters the item
// out, and the BULLET map carries no entry for it). The gate is
// unaffected: GarmentClasses has no sunscreen dimension and
// classesFromText maps no token from the word, so expected
// classes and verdicts are unchanged — verified by the
// invariance sweep + 97-tuple calibration (build record §20).
// ---------------------------------------------------------------------
const BULLET: Record<string, string> = {
  TANK_TOP: 'Sleeveless running tank top',
  SHORT_SLEEVE: 'Running short sleeve shirt',
  LONG_SLEEVE_LIGHT: 'Light running long sleeve shirt',
  LONG_SLEEVE_THERMAL: 'Thermal running long sleeve shirt',
  QUARTER_ZIP: 'Running quarter zip pullover',
  FLEECE_PULLOVER: 'Running fleece pullover',
  HALF_ZIP_THERMAL: 'Thermal running half zip pullover',
  LIGHT_VEST: 'Light running vest',
  WINDBREAKER: 'Running windbreaker jacket',
  LIGHT_JACKET: 'Light running jacket',
  RAIN_JACKET: 'Waterproof running rain jacket with the hood down',
  INSULATED_JACKET: 'Insulated running jacket',
  SHORTS: 'Running shorts',
  SHORT_SHORTS: 'Running short shorts',
  CAPRIS: 'Running capris / 3/4 tights',
  LIGHT_TIGHTS: 'Light running tights',
  THERMAL_TIGHTS: 'Thermal running tights',
  VISOR: 'Running visor',
  BASEBALL_CAP: 'Running cap',
  HEADBAND: 'Running headband',
  LIGHT_BEANIE: 'Light running beanie',
  THERMAL_BEANIE: 'Thermal running beanie',
  BALACLAVA: 'Running balaclava',
  LIGHT_GLOVES: 'Light running gloves',
  THERMAL_GLOVES: 'Thermal running gloves',
  MITTENS: 'Running mittens',
  NECK_GAITER: 'Running neck gaiter',
  SUNGLASSES: 'Running sunglasses',
  REFLECTIVE_GEAR: 'Reflective running gear'
}

const SLOT_ORDER: Record<string, number> = {
  TANK_TOP: 0, SHORT_SLEEVE: 0, LONG_SLEEVE_LIGHT: 0, LONG_SLEEVE_THERMAL: 0,
  QUARTER_ZIP: 1, FLEECE_PULLOVER: 1, HALF_ZIP_THERMAL: 1,
  LIGHT_VEST: 2, WINDBREAKER: 2, LIGHT_JACKET: 2, RAIN_JACKET: 2, INSULATED_JACKET: 2,
  SHORT_SHORTS: 3, SHORTS: 3, CAPRIS: 3, LIGHT_TIGHTS: 3, THERMAL_TIGHTS: 3,
  VISOR: 4, BASEBALL_CAP: 4, HEADBAND: 4, LIGHT_BEANIE: 4, THERMAL_BEANIE: 4, BALACLAVA: 4,
  LIGHT_GLOVES: 5, THERMAL_GLOVES: 5, MITTENS: 5,
  NECK_GAITER: 6, SUNGLASSES: 6, SUNSCREEN: 6, REFLECTIVE_GEAR: 6
}

const BRACKET_PHRASE: Record<string, string> = {
  FREEZING: 'freezing winter', COLD: 'cold winter', COOL: 'cool autumn',
  MILD: 'mild', WARM: 'warm', HOT: 'hot summer'
}
const TIME_WORD: Record<string, string> = {
  DAWN: 'early morning', MIDDAY: 'midday', DUSK: 'evening', NIGHT: 'night'
}
const WEATHER_WORD: Record<string, string> = {
  CLEAR: 'clear', CLOUDY: 'overcast cloudy', RAIN: 'rainy', SNOW: 'snowy'
}
const TEMP_ADJ: Record<string, string> = {
  FREEZING: 'freezing', COLD: 'cold', COOL: 'cool',
  MILD: 'mild', WARM: 'warm', HOT: 'hot'
}
const WX_ADJ: Record<string, string> = {
  CLEAR: 'clear', CLOUDY: 'overcast', RAIN: 'rainy', SNOW: 'snowy'
}
const MOOD: Record<string, string> = {
  FREEZING: 'Tough and determined, layered and ready',
  COLD: 'Strong and steady, winter grit',
  COOL: 'Crisp and focused, brisk run',
  MILD: 'Fresh and invigorating, energized',
  WARM: 'Light and free, easy summer rhythm',
  HOT: 'Fierce and relentless, summer heat'
}
const MOOD_RAIN = 'Undeterred and strong, splashing through the rain'
const MOOD_SNOW = 'Quiet and powerful, tracks in fresh snow'
const SCENE: Record<string, string> = {
  CLEAR: 'Clear sky, suburban neighborhood with sidewalks and trees',
  CLOUDY: 'Overcast gray sky, diffused lighting, suburban neighborhood with sidewalks and trees',
  RAIN: 'Rain falling in visible streaks, raindrops splashing in puddles, wet glistening pavement, soaked surroundings, gray overcast sky',
  SNOW: 'Snow falling, snow-covered streets and trees, soft winter light'
}
const SCENE_MILD_SNOW = 'Light spring snow shower, snowflakes melting on wet pavement, green lawns at the edges'
const LIGHT: Record<string, string> = {
  DAWN: 'Soft golden sunrise light, long shadows, quiet streets; ',
  MIDDAY: 'Bright daylight; ',
  DUSK: 'Golden evening light, long shadows; ',
  NIGHT: 'Dark night lit by streetlights; '
}
// RW-80 rain fidelity (2026-11-06): the LIGHT prefixes above are
// weather-blind — at DAWN/DUSK they describe direct golden sun and
// long shadows, which contradicts a rain scene outright. Production
// gate records show the cost: gated RAIN draws at DAWN read 'clear'
// (a ruling-3 hard failure) 17 of 19 times, vs 0 of 15 at MIDDAY.
// RAIN cells therefore take an overcast light prefix with no sun
// and no shadows; every other weather keeps the table above, so
// non-rain prompts are unchanged byte for byte.
const LIGHT_RAIN: Record<string, string> = {
  DAWN: 'Gray overcast dawn light, no direct sun, quiet streets; ',
  MIDDAY: 'Flat gray overcast light, no direct sun; ',
  DUSK: 'Gray overcast evening light, no direct sun; ',
  NIGHT: 'Dark rainy night lit by streetlights, reflections on wet pavement; '
}
const STYLE_BLOCK =
  'Professional athletic action photography. Full body in motion, natural running stride, ' +
  'captured mid-step. Sharp focus on runner with slight motion energy, soft bokeh background. ' +
  'Authentic and dynamic, like a Nike campaign shot. The scene should make viewers FEEL the '

// Emphasis phrase helpers (yield-experiment arm B construction):
// the subject sentence names the head item with its indefinite
// article and the hands item bare. The article rule reproduces the
// experiment's hand-written phrases exactly — "a light beanie"
// (LIGHT_BEANIE), "a running cap" (BASEBALL_CAP), "an ear warmer
// headband" (HEADBAND), "light gloves" (LIGHT_GLOVES). Since the
// round-two arm-F adoption, the HEAD_DESC / HANDS_DESC wording
// below supersedes this rule for every item it defines; the rule
// remains only as the fallback for an item with no description.
const HEAD_KEYS: ReadonlySet<string> = new Set([
  'VISOR', 'BASEBALL_CAP', 'HEADBAND', 'LIGHT_BEANIE', 'THERMAL_BEANIE', 'BALACLAVA'
])
const HANDS_KEYS: ReadonlySet<string> = new Set([
  'LIGHT_GLOVES', 'THERMAL_GLOVES', 'MITTENS'
])

// Descriptive expansion (round-two arm F, adopted 2026-11-03):
// subject-sentence forms of the head/hands descriptions, from the
// experiment driver (HEAD_DESC / HANDS_DESC there), AS AMENDED by
// Sol's running-form direction later the same day (see the header
// block): the knit descriptors are replaced by the running form;
// the remaining descriptions are the driver's verbatim. The
// bullet form is the description with its article stripped and the
// first letter capitalized (the driver's bulletForm()). Items with
// no entry keep the plain arm-B phrasing.
const HEAD_DESC: Record<string, string> = {
  LIGHT_BEANIE: 'a running beanie pulled down over the ears',
  THERMAL_BEANIE: 'a running beanie pulled down over the ears',
  BASEBALL_CAP: 'a running cap with the brim facing forward',
  HEADBAND: 'a wide running headband covering the ears',
  BALACLAVA: 'a running balaclava covering the head and neck',
  VISOR: 'a running visor with the brim facing forward'
}
const HANDS_DESC: Record<string, string> = {
  LIGHT_GLOVES: 'fitted running gloves with separate fingers',
  THERMAL_GLOVES: 'fitted running gloves with separate fingers',
  MITTENS: 'thick insulated running mittens with no separate fingers'
}
function bulletForm(desc: string): string {
  const bare = desc.replace(/^(a|an) /, '')
  return bare.charAt(0).toUpperCase() + bare.slice(1)
}
function bulletText(key: string): string {
  const desc = HEAD_DESC[key] ?? HANDS_DESC[key]
  return desc ? bulletForm(desc) : (BULLET[key] ?? key)
}

function headPhrase(key: string): string {
  if (HEAD_DESC[key]) return HEAD_DESC[key]
  const b = (BULLET[key] ?? key).toLowerCase()
  return `${/^[aeiou]/.test(b) ? 'an' : 'a'} ${b}`
}
function handsPhrase(key: string): string {
  if (HANDS_DESC[key]) return HANDS_DESC[key]
  return (BULLET[key] ?? key).toLowerCase()
}

export function buildPrompt(job: {
  gender: string, weather: string, temp_bracket: string, time_of_day: string,
  outfit_items: unknown
}): string {
  // SUNSCREEN never reaches the prompt (Sol's ruling, 2026-11-03):
  // it stays in the prescription and in the app's outfit list, but
  // a picture cannot show it, so no prompt surface names it.
  const items = [...(job.outfit_items as string[])]
    .filter(k => k !== 'SUNSCREEN')
    .sort((a, b) => (SLOT_ORDER[a] ?? 9) - (SLOT_ORDER[b] ?? 9))
  // Emphasis (arm B): the prescribed head + hands bullets move to
  // the front of the outfit list and are named in the subject
  // sentence. `items` is slot-sorted, so the promoted items are
  // already head-before-hands; the rest keep wearing order.
  const headItem = items.find(k => HEAD_KEYS.has(k))
  const handsItem = items.find(k => HANDS_KEYS.has(k))
  const promoted = items.filter(k => HEAD_KEYS.has(k) || HANDS_KEYS.has(k))
  const ordered = promoted.length
    ? [...promoted, ...items.filter(k => !HEAD_KEYS.has(k) && !HANDS_KEYS.has(k))]
    : items
  const wearing = headItem && handsItem
    ? `, wearing ${headPhrase(headItem)} and ${handsPhrase(handsItem)},`
    : headItem ? `, wearing ${headPhrase(headItem)},`
    : handsItem ? `, wearing ${handsPhrase(handsItem)},`
    : ''
  const g = job.gender === 'FEMALE' ? 'female' : job.gender === 'MALE' ? 'male' : null
  const subject = g
    ? `A ${g} runner in their 30s${wearing} running mid-stride along a suburban sidewalk during a ` +
      `${BRACKET_PHRASE[job.temp_bracket] ?? ''} ${TIME_WORD[job.time_of_day] ?? ''} with ` +
      `${WEATHER_WORD[job.weather] ?? ''} conditions.`
    : `A runner in their 30s${wearing} running mid-stride along a suburban sidewalk during a ` +
      `${BRACKET_PHRASE[job.temp_bracket] ?? ''} ${TIME_WORD[job.time_of_day] ?? ''} with ` +
      `${WEATHER_WORD[job.weather] ?? ''} conditions.`
  const outfit = 'OUTFIT:\n' + ordered.map(k => `- ${bulletText(k)}`).join('\n')
  const scene = (job.weather === 'SNOW' && job.temp_bracket === 'MILD')
    ? SCENE_MILD_SNOW : (SCENE[job.weather] ?? SCENE.CLEAR)
  const light = job.weather === 'RAIN'
    ? (LIGHT_RAIN[job.time_of_day] ?? '')
    : (LIGHT[job.time_of_day] ?? '')
  const env = `ENVIRONMENT: ${light}${scene}`
  const style = `STYLE: ${STYLE_BLOCK}${TEMP_ADJ[job.temp_bracket] ?? ''} ${WX_ADJ[job.weather] ?? ''} conditions.`
  const moodTxt = job.weather === 'RAIN' ? MOOD_RAIN
    : job.weather === 'SNOW' && job.temp_bracket === 'MILD' ? 'Light and amazed, spring snow falling'
    : job.weather === 'SNOW' ? MOOD_SNOW
    : (MOOD[job.temp_bracket] ?? '')
  return [subject, outfit, env, style, `MOOD: ${moodTxt}`].join('\n\n')
}

// ---------------------------------------------------------------------
// Garment classes — verbatim port of the lanes' audit_harness.py
// classes_from_text (the calibrated parser; the worker's older TS
// parser differed and is retired with the LLaVA gate). Used BOTH for
// the gate's seen-parse and for deriving the expected classes from
// the prompt's OUTFIT segment, exactly as adjudicate_compare.py does.
// ---------------------------------------------------------------------
export type GarmentClasses = {
  topBase: 'sleeveless' | 'short' | 'long' | null,
  outer: boolean, mid: boolean,
  bottom: 'short_shorts' | 'shorts' | 'capris' | 'tights' | null,
  head: 'cap' | 'visor' | 'headband' | 'beanie' | 'balaclava' | null,
  hands: 'gloves' | 'mittens' | null,
  sunglasses: boolean, gaiter: boolean
}

export function classesFromText(raw: string): GarmentClasses {
  const t = (raw ?? '').toLowerCase()
  const has = (...words: string[]) => words.some(w => t.includes(w))
  let topBase: GarmentClasses['topBase'] = null
  if (has('tank top', 'singlet', 'sleeveless')) topBase = 'sleeveless'
  else if (has('long sleeve', 'long-sleeve')) topBase = 'long'
  else if (has('short sleeve', 't-shirt', 't shirt', 'short-sleeve')) topBase = 'short'
  let bottom: GarmentClasses['bottom'] = null
  if (has('short shorts')) bottom = 'short_shorts'
  else if (has('capris', 'capri')) bottom = 'capris'
  else if (has('tights', 'leggings')) bottom = 'tights'
  else if (t.includes('shorts')) bottom = 'shorts'
  let head: GarmentClasses['head'] = null
  if (t.includes('balaclava')) head = 'balaclava'
  else if (has('beanie', 'winter hat', 'stocking cap')) head = 'beanie'
  else if (has('headband', 'ear warmer', 'earwarmer')) head = 'headband'
  else if (t.includes('visor')) head = 'visor'
  else if (has('cap', 'hat')) head = 'cap'
  let hands: GarmentClasses['hands'] = null
  if (t.includes('mittens')) hands = 'mittens'
  else if (t.includes('gloves')) hands = 'gloves'
  return {
    topBase,
    outer: has('jacket', 'windbreaker', 'coat', 'shell jacket', 'raincoat') ||
      (t.includes('vest') && !t.includes('reflective')),
    mid: has('quarter zip', 'quarter-zip', 'fleece', 'pullover', 'half zip', 'half-zip'),
    bottom, head, hands,
    sunglasses: t.includes('sunglasses'),
    gaiter: has('neck gaiter', 'gaiter', 'buff')
  }
}

// Extract the outfit segment of a Part-4 prompt (port of
// audit_harness.prompt_outfit_segment) — expected classes are parsed
// from the exact text the generator was given.
export function promptOutfitSegment(prompt: string): string | null {
  const m = prompt.match(/OUTFIT(?:\s*\([^)]*\))?\s*:\s*/)
  if (m && m.index !== undefined) {
    let seg = prompt.slice(m.index + m[0].length)
    const cut = seg.search(/\n\s*(ENVIRONMENT|STYLE|SETTING|BACKGROUND|SCENE|LIGHTING)\s*:/)
    if (cut >= 0) seg = seg.slice(0, cut)
    return seg.trim() || null
  }
  return null
}

export function expectedClassesForPrompt(prompt: string): GarmentClasses {
  const seg = promptOutfitSegment(prompt)
  return classesFromText(seg ?? '')
}

// ---------------------------------------------------------------------
// Prescription gate — the recalibrated gate of record. The vision
// read is the lanes' instrument (GARMENT_PROMPT +
// COHERENCE_ADDENDUM, claude-sonnet-5, image bounded to 1024 px tall
// JPEG q85, max_tokens 900); the verdict logic is a line-for-line port
// of adjudicate_compare.py's gate() implementing Sol's five
// recalibration rulings (2026-11-02):
//   1. gaiter mismatch is a note, never a failure;
//   2. a base layer unperceived under a worn shell is never a
//      failure or a flag (and mid under a worn shell is unverifiable,
//      not recorded);
//   3. condition/light fail only on a CLEAR contradiction;
//   4. pose/gait naturalness is a defect class (POSE_OK);
//   5. garment-construction coherence is a defect class
//      (CONSTRUCTION_OK); positively-seen class mismatches still
//      fail; bottom is judged at family grain (tights vs shorts),
//      exact-class mismatch within the family is a note.
// Amended by Sol's gate rulings (2026-11-03 ~10:14 CST):
//   8. HANDS are verified as hand COVERAGE, not the named garment's
//      form ("even mittens instead of gloves etc is OK") — the
//      ruling-7 principle extended to hands: gloves seen for a
//      mittens prescription (or the reverse) satisfies, noted; a
//      BARE hand where handwear is prescribed still fails, and
//      handwear seen where none is prescribed still fails (the
//      pre-existing additions behavior, unchanged).
//   9. BACKGROUND PEOPLE: an extra figure who is background and
//      blurred is not a second subject ("if there's people in the
//      background very blurred, I'm fine with that"). The
//      enumeration read now counts only CLEAR SUBJECTS in
//      PERSON_COUNT and reports background-only figures separately
//      as BACKGROUND_PEOPLE; evaluateGate fails the person class
//      only on the clear-subject count and records background-only
//      figures as a note. Stored reads taken under the old prompt
//      carry no BACKGROUND_PEOPLE field and keep the old counting
//      (a stored count above 1 still fails) — the amendment is
//      prompt-forward, never retroactive.
// Amended by Sol's stringency ruling (2026-11-02 ~21:28 EST):
//   6. SUNGLASSES OMISSION is never a failure (V1) — prescribed but
//      not seen is recorded as a note. Sunglasses seen where NOT
//      prescribed still fail: the ruling is omission-only, and no
//      other eyewear read changes.
//   7. HEADWEAR is verified as head COVERAGE, not the named
//      garment's form (Sol's balaclava clarification): any seen
//      covering satisfies a headwear prescription and a form
//      difference is a note; a bare head still fails (V2 headwear
//      leniency was REJECTED — absence is not excused), and headwear
//      seen where none is prescribed still fails. Face coverage
//      itself is not a defect. GARMENT_PROMPT primes the read to
//      list a balaclava — or a gaiter/buff pulled up over the face —
//      as "balaclava": the one deliberate prompt divergence from the
//      lanes' instrument, recorded here; it cannot change verdicts
//      on stored enumeration reads.
// LOGOS_OR_TEXT and TWO_WATCHES are recorded in the verdict detail
// but are NOT failures — faithful to the instrument of record; the
// divergence from the retired LLaVA gate (which failed logos) is
// named in the RW-16.30 build record for the calibration phase.
// ---------------------------------------------------------------------
const GARMENT_PROMPT =
  'Look at this photo of a runner. List EVERY clothing item the runner is wearing, one per line, ' +
  'using simple names (for example: tank top, short sleeve shirt, long sleeve shirt, jacket, ' +
  'quarter zip, shorts, short shorts, tights, leggings, cap, visor, headband, beanie, balaclava, gloves, ' +
  'sunglasses). If the runner\'s head or face is covered by a balaclava, or by a neck gaiter or buff ' +
  'pulled up over the face, list balaclava as the head item. Then answer these lines in exactly this format:\n' +
  'PERSON_COUNT: <number of people who are clear subjects of the photo — in the foreground or in focus. Do NOT count people who appear only in the background, out of focus or blurred>\n' +
  'BACKGROUND_PEOPLE: <yes or no — yes if one or more additional people appear ONLY in the background, out of focus or blurred, and are not subjects of the photo>\n' +
  'ANATOMY_OK: <yes or no — no if the runner has extra, missing, or deformed limbs, hands, or fingers>\n' +
  'FACE_OK: <yes or no — no if the face is distorted, melted, or uncanny>\n' +
  'LOGOS_OR_TEXT: <yes or no — yes if any visible brand logo or text appears on the clothing>\n' +
  'TWO_WATCHES: <yes or no — yes if the runner wears a watch on BOTH wrists>'

const COHERENCE_ADDENDUM = `

Also report the scene conditions you can see, each on its own line, EXACTLY in this format:
CONDITIONS_SEEN: <one word: clear, overcast, raining, snowing, mixed>
LIGHT_SEEN: <one word: daylight, golden, night, flat> — 'daylight' for bright sun, 'golden' for low warm sunrise/sunset light, 'flat' for dull overcast daylight, 'night' for darkness lit artificially.
POSE_OK: <yes or no> — 'yes' if the runner's legs are in a plausible running stride; 'no' if the legs form a locked scissor or mannequin-like configuration or the joints bend impossibly.
CONSTRUCTION_OK: <yes or no> — 'yes' if every sleeve attaches to a garment body and every layer is worn as a garment; 'no' for sleeves attached to no garment body (phantom sleeves), or a shell/vest worn as an open display frame over a tank top instead of as a garment.`

export type GateFailure = { cls: string, message: string }
export type GateVerdict = {
  pass: boolean, failures: GateFailure[], notes: string[],
  seen: GarmentClasses, answers: Record<string, string | null>,
  conditionsSeen: string | null, lightSeen: string | null,
  garmentText: string
}

// Line-for-line port of adjudicate_compare.gate(). `cellWeather` and
// `cellTod` are the cell's weather / time-of-day tokens.
export function evaluateGate(
  cellWeather: string, cellTod: string,
  exp: GarmentClasses, seen: GarmentClasses,
  ans: Record<string, string | null>,
  conditionsSeen: string | null, lightSeen: string | null
): { pass: boolean, failures: GateFailure[], notes: string[] } {
  const fails: GateFailure[] = []
  const notes: string[] = []
  const pcRaw = ans.person_count ?? null
  const pcMatch = pcRaw ? pcRaw.match(/\d+/) : null
  const pcN = pcMatch ? parseInt(pcMatch[0], 10) : null
  if (pcN !== 1) fails.push({ cls: 'person', message: `person_count=${JSON.stringify(pcRaw)}` })
  // Background people (ruling 9 — Sol, 2026-11-03 ~10:14 CST): the
  // enumeration counts clear subjects in PERSON_COUNT; additional
  // figures who are background-only and blurred are reported as
  // BACKGROUND_PEOPLE and are a note, never a failure. A second
  // CLEAR subject still fails on the count above.
  if (pcN === 1 && ans.background_people === 'yes') {
    notes.push('additional people visible only as background figures, out of focus or blurred (ruling 9: not a failure)')
  }
  if (ans.anatomy_ok !== 'yes') fails.push({ cls: 'anatomy', message: `anatomy_ok=${JSON.stringify(ans.anatomy_ok ?? null)}` })
  if (ans.face_ok !== 'yes') fails.push({ cls: 'face', message: `face_ok=${JSON.stringify(ans.face_ok ?? null)}` })
  if (ans.pose_ok !== 'yes') fails.push({ cls: 'pose', message: `pose_ok=${JSON.stringify(ans.pose_ok ?? null)} (ruling 4)` })
  if (ans.construction_ok !== 'yes') fails.push({ cls: 'construction', message: `construction_ok=${JSON.stringify(ans.construction_ok ?? null)} (ruling 5b)` })
  // base layer (rulings 2 + 5): unperceived is never a failure or flag
  if (seen.topBase !== null && seen.topBase !== exp.topBase) {
    fails.push({ cls: 'top', message: `topBase seen ${seen.topBase} != prompt ${exp.topBase}` })
  }
  if (seen.outer !== exp.outer) {
    fails.push({ cls: 'outer', message: `outer seen ${seen.outer} != prompt ${exp.outer}` })
  }
  if (exp.outer && seen.outer) {
    // mid under a worn shell: unverifiable, not recorded (ruling 2)
  } else if (seen.mid !== exp.mid) {
    fails.push({ cls: 'mid', message: `mid seen ${seen.mid} != prompt ${exp.mid}` })
  }
  const fam = (b: string | null) => b === 'tights' ? 'tights' : b ? 'shorts' : null
  if (fam(seen.bottom) !== fam(exp.bottom)) {
    fails.push({ cls: 'bottom', message: `bottom seen ${seen.bottom} != prompt ${exp.bottom}` })
  } else if (seen.bottom !== exp.bottom) {
    notes.push(`bottom exact class seen ${seen.bottom} vs prompt ${exp.bottom} (same family)`)
  }
  // Headwear (ruling 7 — Sol's balaclava clarification, 2026-11-02
  // ~21:28 EST): coverage is what is verified, not the covering's
  // form. Both non-null with different forms = satisfied, noted.
  // Absence (seen null vs prescribed) and unprescribed headwear
  // (seen vs prompt null) keep the exact-match failure below.
  if (exp.head !== null && seen.head !== null && seen.head !== exp.head) {
    notes.push(`head seen ${seen.head} vs prompt ${exp.head} — a covered head satisfies the headwear prescription (balaclava clarification 2026-11-02)`)
  } else if (seen.head !== exp.head) {
    fails.push({ cls: 'head', message: `head seen ${seen.head} != prompt ${exp.head}` })
  }
  // Handwear (ruling 8 — Sol, 2026-11-03 ~10:14 CST): coverage is
  // what is verified, not the covering's form — the ruling-7
  // principle extended to hands. Both non-null with different forms
  // (gloves for mittens, mittens for gloves) = satisfied, noted.
  // Absence (seen null vs prescribed) and unprescribed handwear
  // (seen vs prompt null) keep the exact-match failure below.
  if (exp.hands !== null && seen.hands !== null && seen.hands !== exp.hands) {
    notes.push(`hands seen ${seen.hands} vs prompt ${exp.hands} — covered hands satisfy the handwear prescription (coverage ruling 2026-11-03)`)
  } else if (seen.hands !== exp.hands) {
    fails.push({ cls: 'hands', message: `hands seen ${seen.hands} != prompt ${exp.hands}` })
  }
  // Sunglasses (ruling 6 — V1, Sol 2026-11-02 ~21:28 EST): omission
  // (prescribed, not seen) is a note, never a failure. The reverse —
  // seen where not prescribed — still fails, message unchanged.
  if (seen.sunglasses !== exp.sunglasses) {
    if (exp.sunglasses && !seen.sunglasses) {
      notes.push('sunglasses prescribed but not seen (V1 ruling 2026-11-02: omission is never a failure)')
    } else {
      fails.push({ cls: 'sunglasses', message: `sunglasses seen ${seen.sunglasses} != prompt ${exp.sunglasses}` })
    }
  }
  if (seen.gaiter !== exp.gaiter) {
    notes.push(`gaiter seen ${seen.gaiter} vs prompt ${exp.gaiter} (ruling 1: not a failure)`)
  }
  // conditions (ruling 3: clear contradiction only)
  const cs = conditionsSeen
  const contra: Record<string, string[]> = {
    CLEAR: ['raining', 'snowing'], CLOUDY: ['raining', 'snowing'],
    RAIN: ['clear'], SNOW: ['raining']
  }
  if (cs && (contra[cellWeather] ?? []).includes(cs)) {
    fails.push({ cls: 'conditions', message: `conditions seen '${cs}' clearly contradicts cell ${cellWeather} (ruling 3)` })
  } else if (cellWeather === 'CLEAR' && cs !== 'clear') {
    notes.push(`conditions seen '${cs}' in CLEAR cell — marginal (ruling 3)`)
  } else if (cellWeather === 'CLOUDY' && cs !== 'overcast') {
    notes.push(`conditions seen '${cs}' in CLOUDY cell — marginal (ruling 3)`)
  } else if (cellWeather === 'RAIN' && cs !== 'raining') {
    notes.push(`conditions seen '${cs}' in RAIN cell — marginal, no visible rain (ruling 3)`)
  } else if (cellWeather === 'SNOW' && !(cs === 'snowing' || cs === 'mixed')) {
    notes.push(`conditions seen '${cs}' in SNOW cell — marginal, no visible snow (ruling 3)`)
  }
  const ls = lightSeen
  if (cellTod === 'MIDDAY' && ls === 'night') {
    fails.push({ cls: 'light', message: `light seen 'night' clearly contradicts MIDDAY (ruling 3)` })
  } else if ((cellTod === 'DUSK' || cellTod === 'DAWN') && ls === 'daylight') {
    fails.push({ cls: 'light', message: `light seen 'daylight' clearly contradicts ${cellTod} (ruling 3)` })
  } else if (cellTod === 'NIGHT' && ls === 'daylight') {
    fails.push({ cls: 'light', message: `light seen 'daylight' clearly contradicts NIGHT (ruling 3)` })
  } else if (cellTod === 'MIDDAY' && ls !== 'daylight') {
    notes.push(`light seen '${ls}' in MIDDAY cell — marginal (ruling 3)`)
  }
  return { pass: fails.length === 0, failures: fails, notes }
}

// Parse the enumeration text into the verdict inputs (port of
// adjudicate_compare.enumerate_photo's extraction).
export function parseGateText(text: string): {
  seen: GarmentClasses, answers: Record<string, string | null>,
  conditionsSeen: string | null, lightSeen: string | null
} {
  const answers: Record<string, string | null> = {}
  const fields: Array<[string, string]> = [
    ['PERSON_COUNT', 'person_count'], ['BACKGROUND_PEOPLE', 'background_people'],
    ['ANATOMY_OK', 'anatomy_ok'],
    ['FACE_OK', 'face_ok'], ['LOGOS_OR_TEXT', 'logos_or_text'],
    ['TWO_WATCHES', 'two_watches'], ['POSE_OK', 'pose_ok'],
    ['CONSTRUCTION_OK', 'construction_ok']
  ]
  for (const [key, field] of fields) {
    const m = text.match(new RegExp(`${key}\\s*:\\s*([^\\n]+)`, 'i'))
    answers[field] = m ? m[1].trim().toLowerCase() : null
  }
  const cond = text.match(/CONDITIONS_SEEN\s*:\s*([a-z]+)/i)
  const light = text.match(/LIGHT_SEEN\s*:\s*([a-z]+)/i)
  return {
    seen: classesFromText(text),
    answers,
    conditionsSeen: cond ? cond[1].toLowerCase() : null,
    lightSeen: light ? light[1].toLowerCase() : null
  }
}

// ---------------------------------------------------------------------
// Headwear edit pass — trigger classification + instruction.
//
// The trigger mirrors the edit test's head-only classification
// (rw-mim-headwear-edit-test-2026-11-03, whose ledger classified a
// draw head-only when its gate failure list was exactly ["head"] and
// the seen head was null): the verdict's failure list is exactly one
// failure, of class 'head', and that failure is an ABSENCE —
// headwear was prescribed (expected head non-null) and none was seen
// (seen head null). Two neighbouring cases are deliberately NOT
// eligible, the strictest reasonable reading: a form difference
// never reaches here (ruling 7 makes it a note, so the verdict
// passes), and headwear seen where none was prescribed also fails
// with class 'head' but has seen non-null — that is an addition to
// remove, not an absence to fill, and this pass only adds.
// On top of the classification, the identity conditions: the draw
// passed the screen (similarity >= IDENTITY_DELIVERED_MIN — implied
// in the pipeline, where the gate only runs on delivered draws, and
// stated here so the pure check is complete) AND clears the
// headroom line EDIT_MIN_SIMILARITY (0.48 — below it the edit's
// measured identity tax usually ends the draw, so it counts as
// failed without an attempt).
// ---------------------------------------------------------------------
export type EditEligibility = { eligible: boolean, reason: string }

export function headwearEditEligibility(args: {
  similarity: number | null,
  failures: GateFailure[],
  seen: GarmentClasses,
  expected: GarmentClasses
}): EditEligibility {
  const { similarity, failures, seen, expected } = args
  if (similarity === null) return { eligible: false, reason: 'no_identity_similarity' }
  if (similarity < IDENTITY_DELIVERED_MIN) {
    return { eligible: false, reason: 'identity_below_delivered_line' }
  }
  if (similarity < EDIT_MIN_SIMILARITY) {
    return { eligible: false, reason: 'identity_below_edit_headroom' }
  }
  if (failures.length !== 1) return { eligible: false, reason: 'not_head_only_failure' }
  if (failures[0].cls !== 'head') return { eligible: false, reason: 'failure_not_headwear' }
  if (expected.head === null) return { eligible: false, reason: 'no_headwear_prescribed' }
  if (seen.head !== null) return { eligible: false, reason: 'headwear_present_not_absent' }
  return { eligible: true, reason: 'head_only_absence_with_headroom' }
}

// The outfit key of the prescribed head item — the same selection
// buildPrompt's emphasis makes — or null when none is prescribed.
export function prescribedHeadKey(outfitItems: unknown): string | null {
  if (!Array.isArray(outfitItems)) return null
  const key = (outfitItems as unknown[]).find(
    k => typeof k === 'string' && HEAD_KEYS.has(k))
  return (key as string | undefined) ?? null
}

// The edit instruction, byte-identical in construction to the edit
// test's: the prescribed head item in its exact production prompt
// wording (the HEAD_DESC subject-sentence form buildPrompt uses),
// then the change-nothing-else clause verbatim.
export function buildHeadwearEditInstruction(headKey: string): string {
  return `Add ${headPhrase(headKey)} onto the runner's head. ` +
    'Change nothing else: same person, same face, same hair below the headwear, ' +
    'same outfit, same pose, same background.'
}

class GateUnavailable extends Error {}

// One paid gate read. Throws GateUnavailable when the stage cannot
// run (no key configured, credit/quota refusal, API error) — the
// caller fails the job closed with the named stage error. A malformed
// model response is a gate ERROR in the lanes' sense (verdict
// 'error'), also thrown: nothing ungated is ever stored.
export async function callGateModel(imageJpegB64: string): Promise<string> {
  if (!ANTHROPIC_API_KEY) {
    throw new GateUnavailable('gate_unavailable: ANTHROPIC_API_KEY is not configured')
  }
  let resp: Response
  try {
    resp = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-api-key': ANTHROPIC_API_KEY,
        'anthropic-version': '2023-06-01'
      },
      body: JSON.stringify({
        model: GATE_MODEL,
        max_tokens: GATE_MAX_TOKENS,
        messages: [{
          role: 'user',
          content: [
            { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: imageJpegB64 } },
            { type: 'text', text: GARMENT_PROMPT + COHERENCE_ADDENDUM }
          ]
        }]
      })
    })
  } catch (e) {
    throw new GateUnavailable(`gate_unavailable: Anthropic request failed: ${(e as Error).message}`)
  }
  if (!resp.ok) {
    const body = (await resp.text()).slice(0, 300)
    // Credit/quota refusals (400 "credit balance is too low", 402,
    // 429) and 5xx are stage-unavailable, not verdicts.
    throw new GateUnavailable(`gate_unavailable: Anthropic ${resp.status}: ${body}`)
  }
  const payload = await resp.json() as Record<string, unknown>
  const content = (payload.content ?? []) as Array<Record<string, unknown>>
  const text = content.filter(b => b.type === 'text')
    .map(b => String(b.text ?? '')).join('')
  if (!text) throw new GateUnavailable('gate_unavailable: Anthropic returned no text')
  return text
}

// Gate one image, honouring the verdict cache (personal_gate_verdicts
// keyed by image sha256). Returns the verdict + whether it was cached.
async function runGate(
  job: { weather: string, time_of_day: string },
  prompt: string, imageBytes: Uint8Array, imageHash: string
): Promise<{ verdict: GateVerdict, cached: boolean }> {
  const { data: hit } = await supabase
    .from('personal_gate_verdicts')
    .select('pass, failures, notes, detail')
    .eq('image_hash', imageHash)
    .maybeSingle()
  if (hit) {
    const d = (hit.detail ?? {}) as Record<string, unknown>
    return {
      cached: true,
      verdict: {
        pass: hit.pass as boolean,
        failures: (hit.failures ?? []) as GateFailure[],
        notes: (hit.notes ?? []) as string[],
        seen: d.seen as GarmentClasses,
        answers: (d.answers ?? {}) as Record<string, string | null>,
        conditionsSeen: (d.conditionsSeen as string | null) ?? null,
        lightSeen: (d.lightSeen as string | null) ?? null,
        garmentText: String(d.garmentText ?? '')
      }
    }
  }
  const jpegB64 = await gateImageB64(imageBytes)
  const text = await callGateModel(jpegB64)
  const parsed = parseGateText(text)
  const expected = expectedClassesForPrompt(prompt)
  const { pass, failures, notes } = evaluateGate(
    job.weather, job.time_of_day, expected, parsed.seen, parsed.answers,
    parsed.conditionsSeen, parsed.lightSeen)
  const verdict: GateVerdict = {
    pass, failures, notes,
    seen: parsed.seen, answers: parsed.answers,
    conditionsSeen: parsed.conditionsSeen, lightSeen: parsed.lightSeen,
    garmentText: text.slice(0, 2000)
  }
  await supabase.from('personal_gate_verdicts').upsert({
    image_hash: imageHash, pass, failures, notes,
    detail: {
      seen: parsed.seen, answers: parsed.answers,
      conditionsSeen: parsed.conditionsSeen, lightSeen: parsed.lightSeen,
      garmentText: verdict.garmentText, expected
    },
    gate_model: GATE_MODEL
  })
  return { verdict, cached: false }
}

// ---------------------------------------------------------------------
// Identity screen — the identity-screen service (services/
// identity-screen in this repo): InsightFace buffalo_l, det_size
// (640,640), largest face, replicated-border pad fallback, cosine vs
// the reference embedding. delivered := similarity >= 0.40. This is
// the lanes' instrument (fb_score.py / score_compare.py) hosted; the
// worker never substitutes another identity read.
// ---------------------------------------------------------------------
export type FaceMeta = {
  detected: boolean, height_norm: number | null,
  yaw_deg: number | null, pitch_deg: number | null
}
export type ScreenResult = {
  similarity: number | null, delivered: boolean,
  referenceFace: FaceMeta, candidateFace: FaceMeta
}

class IdentityScreenUnavailable extends Error {}

async function callIdentityScreen(refBytes: Uint8Array, candBytes: Uint8Array): Promise<ScreenResult> {
  if (!IDENTITY_SCREEN_URL) {
    throw new IdentityScreenUnavailable(
      'identity_screen_unavailable: IDENTITY_SCREEN_URL is not configured')
  }
  let resp: Response
  try {
    resp = await fetch(`${IDENTITY_SCREEN_URL}/compare`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        ...(IDENTITY_SCREEN_KEY ? { 'Authorization': `Bearer ${IDENTITY_SCREEN_KEY}` } : {})
      },
      body: JSON.stringify({
        reference_image_b64: bytesToB64(refBytes),
        candidate_image_b64: bytesToB64(candBytes)
      })
    })
  } catch (e) {
    throw new IdentityScreenUnavailable(
      `identity_screen_unavailable: request failed: ${(e as Error).message}`)
  }
  if (!resp.ok) {
    throw new IdentityScreenUnavailable(
      `identity_screen_unavailable: screen returned ${resp.status}: ${(await resp.text()).slice(0, 200)}`)
  }
  const body = await resp.json() as Record<string, unknown>
  const sim = typeof body.similarity === 'number' ? body.similarity as number : null
  return {
    similarity: sim,
    delivered: sim !== null && sim >= IDENTITY_DELIVERED_MIN,
    referenceFace: body.reference_face as FaceMeta,
    candidateFace: body.candidate_face as FaceMeta
  }
}

// ---------------------------------------------------------------------
// Image helpers
// ---------------------------------------------------------------------
function bytesToB64(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i += 8192) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 8192))
  }
  return btoa(binary)
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes as BufferSource)
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('')
}

// The gate's working image: <=1024 px tall JPEG q85, exactly the
// lanes' enumerate_photo preprocessing.
export async function gateImageB64(imageBytes: Uint8Array): Promise<string> {
  const img = await Image.decode(imageBytes)
  const bounded = img.height > 1024
    ? img.resize(Math.max(1, Math.round(img.width * 1024 / img.height)), 1024)
    : img
  const jpeg = await bounded.encodeJPEG(85)
  return bytesToB64(jpeg)
}

// ---------------------------------------------------------------------
// Replicate helpers
// ---------------------------------------------------------------------
async function pollPrediction(predictionId: string, maxWaitMs: number, onHeartbeat?: () => Promise<void>): Promise<Record<string, any>> {
  const start = Date.now()
  let lastHeartbeat = start
  while (Date.now() - start < maxWaitMs) {
    // RW-90: heartbeat during the wait (see the header amendment).
    // Without it a long poll is indistinguishable from a dead
    // invocation to the stale scan — and a second invocation could
    // reclaim a job whose first invocation is still legitimately
    // polling. The callback refreshes started_at; it never throws.
    if (onHeartbeat && Date.now() - lastHeartbeat >= POLL_HEARTBEAT_MS) {
      lastHeartbeat = Date.now()
      await onHeartbeat()
    }
    const response = await fetch(`https://api.replicate.com/v1/predictions/${predictionId}`, {
      headers: { 'Authorization': `Token ${REPLICATE_API_TOKEN}` }
    })
    if (!response.ok) throw new Error(`Replicate poll failed: ${response.status}`)
    const prediction = await response.json()
    if (prediction.status === 'succeeded' || prediction.status === 'failed' ||
        prediction.status === 'canceled') {
      return prediction
    }
    await new Promise(r => setTimeout(r, 1500))
  }
  throw new Error('Prediction timed out')
}

let pulidVersionCache: string | null = null
async function pulidVersion(): Promise<string> {
  if (pulidVersionCache) return pulidVersionCache
  const resp = await fetch(`https://api.replicate.com/v1/models/${GENERATION_MODEL_SLUG}`, {
    headers: { 'Authorization': `Token ${REPLICATE_API_TOKEN}` }
  })
  if (!resp.ok) throw new Error(`PuLID model lookup failed: ${resp.status}`)
  const model = await resp.json()
  const version = model.latest_version?.id as string | undefined
  if (!version) throw new Error(`Model ${GENERATION_MODEL_SLUG} has no latest version`)
  pulidVersionCache = version
  return version
}

// One Route 2 draw. Input shape is the lanes' validated one:
// main_face_image = the single reference (the <=768px derivative as a
// data URI), 832x1216, one output. The auxiliary slot is NEVER used —
// references are never blended (full-body record §6).
async function createPulidDraw(prompt: string, refDataUri: string): Promise<string> {
  const version = await pulidVersion()
  const resp = await fetch('https://api.replicate.com/v1/predictions', {
    method: 'POST',
    headers: {
      'Authorization': `Token ${REPLICATE_API_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      version,
      input: {
        prompt,
        main_face_image: refDataUri,
        width: 832, height: 1216, num_outputs: 1,
        // PuLID's default output is WebP, which imagescript (the
        // gate's decoder) cannot read — every draw would fail at the
        // gate stage. PNG is decodable and matches the .png storage
        // convention. Caught by the RW-16.30 battery, 2026-11-02.
        output_format: 'png'
      }
    })
  })
  if (!resp.ok) {
    throw new Error(`PuLID create failed: ${resp.status} ${await resp.text()}`)
  }
  const created = await resp.json()
  return created.id as string
}

// One headwear edit: black-forest-labs/flux-kontext-pro on the
// official-model endpoint (the route the hero refill uses), input =
// the draw image itself as a data URI. Returns the prediction id;
// the output bytes come from predictionOutputBytes, unchanged. The
// input shape is the edit test's exactly (aspect_ratio
// 'match_input_image', output_format 'png' so the gate's decoder can
// read the result).
async function createHeadwearEdit(instruction: string, imageBytes: Uint8Array): Promise<string> {
  const resp = await fetch(`https://api.replicate.com/v1/models/${EDIT_MODEL_SLUG}/predictions`, {
    method: 'POST',
    headers: {
      'Authorization': `Token ${REPLICATE_API_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      input: {
        prompt: instruction,
        input_image: `data:image/png;base64,${bytesToB64(imageBytes)}`,
        aspect_ratio: 'match_input_image',
        output_format: 'png'
      }
    })
  })
  if (!resp.ok) {
    throw new Error(`Kontext edit create failed: ${resp.status} ${await resp.text()}`)
  }
  const created = await resp.json()
  return created.id as string
}

async function predictionOutputBytes(predictionId: string, maxWaitMs: number, onHeartbeat?: () => Promise<void>): Promise<Uint8Array> {
  const result = await pollPrediction(predictionId, maxWaitMs, onHeartbeat)
  if (result.status !== 'succeeded') {
    throw new Error(`Prediction ${predictionId} ${result.status}: ${result.error ?? ''}`)
  }
  const url = Array.isArray(result.output) ? result.output[0] : result.output
  if (!url) throw new Error(`Prediction ${predictionId} returned no output`)
  const resp = await fetch(url as string)
  if (!resp.ok) throw new Error(`Output download failed: ${resp.status}`)
  return new Uint8Array(await resp.arrayBuffer())
}

// Resume support (stale-claim recovery): fetch a completed
// prediction's output bytes; null when no longer retrievable
// (Replicate retains prediction outputs ~1h).
async function fetchPredictionOutputBytes(predictionId: string): Promise<Uint8Array | null> {
  try {
    const response = await fetch(`https://api.replicate.com/v1/predictions/${predictionId}`, {
      headers: { 'Authorization': `Token ${REPLICATE_API_TOKEN}` }
    })
    if (!response.ok) return null
    const prediction = await response.json()
    if (prediction.status !== 'succeeded') return null
    const url = Array.isArray(prediction.output) ? prediction.output[0] : prediction.output
    if (typeof url !== 'string') return null
    const resp = await fetch(url)
    if (!resp.ok) return null
    return new Uint8Array(await resp.arrayBuffer())
  } catch {
    return null
  }
}

// ---------------------------------------------------------------------
// Reference loading — anchor selection + derivative bytes (kept from
// v23: the derivative is produced by personal-ref-prep and bounded to
// the identity working size; decoding a full-resolution original in
// this isolate was the RW-16 wedge mechanism).
// ---------------------------------------------------------------------
function derivativePathFor(sourcePath: string): string {
  const slash = sourcePath.lastIndexOf('/')
  const dir = sourcePath.slice(0, slash)               // <uid>/refs
  const base = sourcePath.slice(slash + 1)             // ref-1.jpg
  const parent = dir.slice(0, dir.lastIndexOf('/'))    // <uid>
  const stem = base.replace(/\.[^.]+$/, '')
  return `${parent}/refs-small/${stem}.jpg`
}

async function derivativeIsFresh(sourcePath: string, derivativePath: string): Promise<boolean> {
  try {
    const srcDir = sourcePath.slice(0, sourcePath.lastIndexOf('/'))
    const srcName = sourcePath.slice(sourcePath.lastIndexOf('/') + 1)
    const derDir = derivativePath.slice(0, derivativePath.lastIndexOf('/'))
    const derName = derivativePath.slice(derivativePath.lastIndexOf('/') + 1)
    const [{ data: srcList }, { data: derList }] = await Promise.all([
      supabase.storage.from('personal-heroes').list(srcDir, { search: srcName }),
      supabase.storage.from('personal-heroes').list(derDir, { search: derName })
    ])
    const src = (srcList ?? []).find(e => e.name === srcName)
    const der = (derList ?? []).find(e => e.name === derName)
    if (!src || !der || !src.updated_at || !der.updated_at) return false
    return new Date(der.updated_at).getTime() >= new Date(src.updated_at).getTime()
  } catch {
    return false
  }
}

async function loadDerivativeBytes(sourcePath: string): Promise<Uint8Array> {
  const derivativePath = derivativePathFor(sourcePath)
  if (!(await derivativeIsFresh(sourcePath, derivativePath))) {
    const resp = await fetch(`${SUPABASE_URL}/functions/v1/personal-ref-prep`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ path: sourcePath })
    })
    if (!resp.ok) {
      throw new Error(`reference_prep_failed: prep returned ${resp.status} ${(await resp.text()).slice(0, 200)}`)
    }
  }
  const { data, error } = await supabase.storage
    .from('personal-heroes')
    .download(derivativePath)
  if (error || !data) {
    throw new Error(`reference_prep_failed: derivative download failed: ${error?.message}`)
  }
  return new Uint8Array(await data.arrayBuffer())
}

// Anchor selection (intake policy): ONE reference is used — the
// strongest, never blended. Strength order is capture priority: the
// first registered photo (the guided-capture selfie slot) whose
// intake verdict is not a fail; when no photo carries a failing
// verdict distinction, the first registered photo.
type RefRow = { storage_path: string, position: number, quality: unknown }
export function selectAnchor(refs: RefRow[]): RefRow | null {
  if (!refs || refs.length === 0) return null
  const sorted = [...refs].sort((a, b) => a.position - b.position)
  const passing = sorted.find(r => {
    const q = r.quality as { verdict?: string } | null
    return !q || q.verdict !== 'fail'
  })
  return passing ?? sorted[0]
}

// ---------------------------------------------------------------------
// Job plumbing
// ---------------------------------------------------------------------
type JobRow = {
  id: string, user_id: string, combination_id: string, gender: string,
  weather: string, temp_bracket: string, time_of_day: string,
  outfit_signature: string, outfit_items: unknown, kind: string,
  attempts: number, replicate_id: string | null, prompt: string | null,
  reclaim_count: number, epoch: number, draws_used: number,
  priority: number, requested_at: string
}

// ---------------------------------------------------------------------
// Claim ordering — Gen-6 Wave A queue fix (2026-11-05, RW-38/DA-02).
//
// The deployed priority numbers are INVERTED for the case that
// matters: a forecast fill stamps priority 5 while the photo the
// user is actually waiting for (an explicit on-demand request)
// stamps 20, so raw (priority, requested_at) ordering drained the
// bank fill ahead of the user's current cell — a current-cell
// request measured QUEUED >= 22 minutes behind 11 fill jobs. The
// enqueue-side numbers cannot all be renumbered from here (four
// functions stamp them, and the jobs kind CHECK admits no new
// kind), so the worker claims by an EFFECTIVE band derived from
// the deployed (kind, priority) pairs:
//
//   band 0  current / imminent cell — the cell on the user's
//           screen: priority <= 2 (the explicit current-cell class
//           start-personal-fill stamps on the head of
//           priority_cells), or the convergence enqueue
//           (kind 'subscriber_fill' at priority 15, written by
//           get-personal-hero for the exact cell being viewed).
//   band 1  explicit on-demand: kind 'free_on_demand' or
//           'regeneration' (priority 20).
//   band 2  subscriber forecast fill (kind 'subscriber_fill' at
//           priority 5; legacy 10 included).
//   band 3  replenishment (priority 30) and anything else.
//
// Within the effective order, a job that has already burned at
// least one draw (draws_used >= 1) or has been reclaimed once is
// demoted by half a band: gate-failure retries and resumed jobs
// yield to fresh work instead of re-taking the head of the queue
// on every pass. Ties break by requested_at (FIFO), as before.
// ---------------------------------------------------------------------
export function claimBand(job: { kind: string, priority: number }): number {
  if ((job.priority ?? 99) <= 2) return 0
  if (job.kind === 'subscriber_fill' && job.priority === 15) return 0
  if (job.kind === 'free_on_demand' || job.kind === 'regeneration') return 1
  if (job.kind === 'subscriber_fill') return 2
  return 3
}

export function claimKey(job: {
  kind: string, priority: number, draws_used?: number,
  reclaim_count?: number, requested_at?: string
}): [number, number, string] {
  const band = claimBand(job)
  const retried = (job.draws_used ?? 0) >= 1 || (job.reclaim_count ?? 0) >= 1
  return [band * 2 + (retried ? 1 : 0), band, job.requested_at ?? '']
}

export function compareJobsForClaim(
  a: { kind: string, priority: number, draws_used?: number, reclaim_count?: number, requested_at?: string },
  b: { kind: string, priority: number, draws_used?: number, reclaim_count?: number, requested_at?: string }
): number {
  const ka = claimKey(a)
  const kb = claimKey(b)
  if (ka[0] !== kb[0]) return ka[0] - kb[0]
  if (ka[2] < kb[2]) return -1
  if (ka[2] > kb[2]) return 1
  return 0
}

async function markJob(id: string, patch: Record<string, unknown>) {
  const { error } = await supabase.from('personal_generation_jobs').update(patch).eq('id', id)
  if (error) console.error('[personal-hero-worker] markJob failed:', error)
}

// Headwear edit pass bookkeeping, recorded on the draw record (and
// so in the job's gate_result and a delivered hero's gate detail)
// the way scaffold serves are recorded on theirs: whether an edit
// was attempted, the pre/post identity similarities, and the
// post-edit gate verdict.
export type EditRecord = {
  model: string, prediction_id: string | null,
  head_item: string, instruction: string,
  pre_similarity: number | null, post_similarity: number | null,
  trigger_failures: GateFailure[],
  post_gate_pass: boolean | null, post_gate_cached?: boolean,
  post_failures?: GateFailure[],
  outcome: 'delivered' | 'identity_screen' | 'prescription_gate' | 'edit_error',
  error?: string
}

type DrawRecord = {
  draw: number, prediction_id: string | null, image_hash: string | null,
  identity_similarity: number | null, stage: string,
  gate_pass?: boolean, gate_cached?: boolean,
  failures?: GateFailure[], notes?: string[],
  edited?: boolean, edit?: EditRecord
}

type ScreenedDraw = {
  bytes: Uint8Array, imageHash: string, screen: ScreenResult,
  verdict: GateVerdict, gateCached: boolean, prompt: string
}

// The headwear edit pass for one gate-failed draw. Returns null
// when the trigger is not met (the draw simply counts as failed).
// When the trigger IS met, exactly one edit is attempted: its
// outcome is recorded either way, and a deliverable is returned
// only when the edited image re-passes BOTH the identity screen and
// the full gate. A Replicate edit failure is recorded as outcome
// 'edit_error' and the draw counts as failed — the edit is a rescue
// attempt, not a pipeline stage the job depends on. The
// re-verification's hard-dependency failures (identity screen /
// gate unavailable) are NOT caught here: they throw and fail the
// job closed with the named stage error, exactly as for an original
// draw.
async function attemptHeadwearEdit(
  job: JobRow, prompt: string, bytes: Uint8Array, refBytes: Uint8Array,
  screen: ScreenResult, verdict: GateVerdict
): Promise<{ edit: EditRecord, deliverable?: ScreenedDraw } | null> {
  const check = headwearEditEligibility({
    similarity: screen.similarity, failures: verdict.failures,
    seen: verdict.seen, expected: expectedClassesForPrompt(prompt)
  })
  if (!check.eligible) return null
  const headKey = prescribedHeadKey(job.outfit_items)
  if (!headKey) return null
  const instruction = buildHeadwearEditInstruction(headKey)
  const edit: EditRecord = {
    model: EDIT_MODEL_SLUG, prediction_id: null,
    head_item: headKey, instruction,
    pre_similarity: screen.similarity, post_similarity: null,
    trigger_failures: verdict.failures, post_gate_pass: null,
    outcome: 'edit_error'
  }
  let editedBytes: Uint8Array
  try {
    edit.prediction_id = await createHeadwearEdit(instruction, bytes)
    editedBytes = await predictionOutputBytes(
      edit.prediction_id, DRAW_POLL_TIMEOUT_MS,
      () => markJob(job.id, { started_at: new Date().toISOString() })
    )
  } catch (e) {
    edit.error = (e as Error).message
    console.log(`[personal-hero-worker] job ${job.id}: headwear edit failed: ${edit.error}`)
    return { edit }
  }
  console.log(`[personal-hero-worker] job ${job.id}: headwear edit applied (${headKey}, prediction ${edit.prediction_id}); re-verifying`)
  // Re-verification: the edited image is a new candidate — identity
  // screen first (free, discards before the paid read), then the
  // full prescription gate (its verdict is cached by the edited
  // image's own hash, like any candidate's).
  const screen2 = await callIdentityScreen(refBytes, editedBytes)
  edit.post_similarity = screen2.similarity
  if (!screen2.delivered) {
    edit.outcome = 'identity_screen'
    return { edit }
  }
  const editedHash = await sha256Hex(editedBytes)
  const gate2 = await runGate(job, prompt, editedBytes, editedHash)
  edit.post_gate_pass = gate2.verdict.pass
  edit.post_gate_cached = gate2.cached
  if (!gate2.verdict.pass) {
    edit.outcome = 'prescription_gate'
    edit.post_failures = gate2.verdict.failures
    return { edit }
  }
  edit.outcome = 'delivered'
  return {
    edit,
    deliverable: {
      bytes: editedBytes, imageHash: editedHash, screen: screen2,
      verdict: gate2.verdict, gateCached: gate2.cached, prompt
    }
  }
}

// Run one candidate through screen + gate (in that order — the screen
// is free and discards before any paid call). Returns the terminal
// stage for the draw and, when both stages pass, the deliverable.
// A gate failure that meets the headwear-edit trigger gets its one
// edit inside this call (see attemptHeadwearEdit): on a rescued draw
// the returned verdict/screen/deliverable are the EDITED image's;
// on an unrescued one the original verdict stands and the attempt
// rides along in the edit field.
async function screenAndGate(
  job: JobRow, prompt: string, bytes: Uint8Array, refBytes: Uint8Array
): Promise<{ stage: string, screen: ScreenResult, deliverable?: ScreenedDraw, verdict?: GateVerdict, gateCached?: boolean, edit?: EditRecord }> {
  const screen = await callIdentityScreen(refBytes, bytes)
  if (!screen.delivered) return { stage: 'identity_screen', screen }
  const imageHash = await sha256Hex(bytes)
  const { verdict, cached } = await runGate(job, prompt, bytes, imageHash)
  if (!verdict.pass) {
    const edited = await attemptHeadwearEdit(job, prompt, bytes, refBytes, screen, verdict)
    if (edited?.deliverable) {
      return {
        stage: 'delivered', screen: edited.deliverable.screen,
        gateCached: edited.deliverable.gateCached,
        verdict: edited.deliverable.verdict,
        deliverable: edited.deliverable, edit: edited.edit
      }
    }
    return {
      stage: 'prescription_gate', screen, verdict, gateCached: cached,
      ...(edited ? { edit: edited.edit } : {})
    }
  }
  return {
    stage: 'delivered', screen, gateCached: cached, verdict,
    deliverable: { bytes, imageHash, screen, verdict, gateCached: cached, prompt }
  }
}

// Store a gate-passed image as a verified hero and run the delivery
// accounting. Returns 'delivered' or 'allowance_exhausted' (the free
// claim refused — nothing remains stored). Throws on infrastructure
// errors. The regeneration swap retirement is v23's Lane C logic,
// unchanged: gate first, retire prior-generation heroes after the
// pass, replacement takes the lowest freed slot.
async function deliver(
  job: JobRow, d: ScreenedDraw, variant: number, drawRec: DrawRecord
): Promise<'delivered' | 'allowance_exhausted'> {
  let finalVariant = variant
  if (job.kind === 'regeneration') {
    const { data: cellState } = await supabase
      .from('personal_cells')
      .select('regen_pending_since')
      .eq('user_id', job.user_id)
      .eq('combination_id', job.combination_id)
      .maybeSingle()
    const since = cellState?.regen_pending_since
      ? new Date(cellState.regen_pending_since as string).getTime()
      : null
    if (since !== null) {
      const { data: heroesNow } = await supabase
        .from('personal_heroes')
        .select('id, variant, storage_path, created_at')
        .eq('user_id', job.user_id)
        .eq('combination_id', job.combination_id)
      const rows = heroesNow ?? []
      if (rows.length >= VARIANT_CAP) {
        const prior = rows.filter(
          r => new Date(r.created_at as string).getTime() < since)
        if (prior.length > 0) {
          const { error: removeError } = await supabase.storage
            .from('personal-heroes')
            .remove(prior.map(r => r.storage_path as string))
          if (removeError) {
            throw new Error(`Regeneration swap retirement failed (storage): ${removeError.message}`)
          }
          const { error: deleteError } = await supabase
            .from('personal_heroes')
            .delete()
            .in('id', prior.map(r => r.id as string))
          if (deleteError) {
            throw new Error(`Regeneration swap retirement failed: ${deleteError.message}`)
          }
          finalVariant = Math.min(...prior.map(r => r.variant as number))
          console.log(`[personal-hero-worker] job ${job.id}: regeneration swap retired ${prior.length} prior-generation hero(es) for ${job.combination_id}; replacement takes variant ${finalVariant}`)
        }
      }
    }
  }
  if (finalVariant > VARIANT_CAP) throw new Error('variant_cap_reached')

  const gateDetail = {
    pipeline: 'rw16.30-route2',
    draw: drawRec,
    identity: {
      similarity: d.screen.similarity,
      reference_face: d.screen.referenceFace,
      candidate_face: d.screen.candidateFace
    },
    gate: {
      model: GATE_MODEL, cached: d.gateCached, pass: true,
      notes: d.verdict.notes, seen: d.verdict.seen,
      answers: d.verdict.answers,
      conditionsSeen: d.verdict.conditionsSeen, lightSeen: d.verdict.lightSeen
    }
  }

  const storagePath = `${job.user_id}/heroes/${job.combination_id}/v${finalVariant}.png`
  const { error: uploadError } = await supabase.storage
    .from('personal-heroes')
    .upload(storagePath, d.bytes, { contentType: 'image/png', upsert: false })
  if (uploadError) {
    // RW-90 idempotent delivery: the object already present at this
    // deterministic path can only be an interrupted earlier attempt
    // of THIS job (or a raced reclaim of it) that stored the bytes
    // and died before the hero row landed — the RAIN_WARM_DAWN scar
    // ("Hero upload failed: The resource already exists"). The path
    // is unique to this user+cell+variant, so overwriting it with
    // this draw's verified bytes completes the delivery instead of
    // failing a job whose draw already passed every gate.
    if (!/already exists/i.test(uploadError.message)) {
      throw new Error(`Hero upload failed: ${uploadError.message}`)
    }
    const { error: overwriteError } = await supabase.storage
      .from('personal-heroes')
      .upload(storagePath, d.bytes, { contentType: 'image/png', upsert: true })
    if (overwriteError) {
      throw new Error(`Hero upload failed: ${overwriteError.message}`)
    }
    console.log(`[personal-hero-worker] job ${job.id}: hero object already present at ${storagePath}; ` +
      `overwrote with the verified draw (RW-90 idempotent delivery)`)
  }

  const { data: hero, error: heroError } = await supabase
    .from('personal_heroes')
    .insert({
      user_id: job.user_id,
      combination_id: job.combination_id,
      gender: job.gender, weather: job.weather,
      temp_bracket: job.temp_bracket, time_of_day: job.time_of_day,
      outfit_signature: job.outfit_signature,
      outfit_items: job.outfit_items,
      storage_path: storagePath, variant: finalVariant,
      prompt: d.prompt, gate_result: gateDetail,
      epoch: job.epoch, image_hash: d.imageHash
    })
    .select('id')
    .single()
  if (heroError) {
    await supabase.storage.from('personal-heroes').remove([storagePath]).catch(() => {})
    throw new Error(`Hero insert failed: ${heroError.message}`)
  }

  // Free-allowance claim INSIDE the delivery path (audit F6): the
  // atomic claim charges the counter only for a never-counted cell
  // below the cap. A refused claim removes the just-stored hero —
  // an over-cap delivery never stands.
  if (job.kind === 'free_on_demand') {
    const { data: claimed, error: claimError } = await supabase
      .rpc('claim_free_delivery', {
        p_user_id: job.user_id, p_combination_id: job.combination_id
      })
    if (claimError) {
      await supabase.from('personal_heroes').delete().eq('id', hero.id)
      await supabase.storage.from('personal-heroes').remove([storagePath]).catch(() => {})
      throw new Error(`Free delivery claim failed: ${claimError.message}`)
    }
    if (claimed !== true) {
      await supabase.from('personal_heroes').delete().eq('id', hero.id)
      await supabase.storage.from('personal-heroes').remove([storagePath]).catch(() => {})
      return 'allowance_exhausted'
    }
  } else {
    // Non-free kinds: mark the cell delivered without charging
    // (regeneration of a counted cell keeps its counted state via
    // claim semantics in the RPC; other kinds simply record delivery).
    const { data: cellRow } = await supabase
      .from('personal_cells')
      .select('free_counted')
      .eq('user_id', job.user_id)
      .eq('combination_id', job.combination_id)
      .maybeSingle()
    await supabase
      .from('personal_cells')
      .update({
        ever_delivered: true,
        free_counted: cellRow?.free_counted === true,
        updated_at: new Date().toISOString()
      })
      .eq('user_id', job.user_id)
      .eq('combination_id', job.combination_id)
  }

  // RW-107 conditional completion: the 'delivered' stage
  // transition is persisted ONLY against the hero row this
  // delivery just wrote. The row's existence is re-verified at
  // the moment of the transition and the write itself is a
  // checked update guarded on the job still being PROCESSING —
  // if the hero row has vanished mid-delivery (a racing
  // flag_hero delete is the known route) or the completion write
  // does not land, deliver() throws and the job fails visibly,
  // so a 'delivered' stage can never be recorded without a hero
  // row behind it. (markJob's log-and-continue contract is
  // unchanged for heartbeat/progress writes; this one write is
  // the delivery of record and must not be silent.)
  const { data: heroRow } = await supabase
    .from('personal_heroes')
    .select('id')
    .eq('id', hero.id)
    .maybeSingle()
  if (!heroRow) {
    throw new Error(`Delivery integrity: hero row ${hero.id} missing at completion; refusing to record 'delivered'`)
  }
  const { data: completedRows, error: completionError } = await supabase
    .from('personal_generation_jobs')
    .update({
      status: 'COMPLETED', gate_result: gateDetail,
      result_hero_id: hero.id, stage: 'delivered',
      completed_at: new Date().toISOString()
    })
    .eq('id', job.id)
    .eq('status', 'PROCESSING')
    .select('id')
  if (completionError) {
    throw new Error(`Delivery completion write failed: ${completionError.message}`)
  }
  if (!completedRows || completedRows.length === 0) {
    throw new Error('Delivery completion write failed: job no longer PROCESSING')
  }
  return 'delivered'
}

// ---------------------------------------------------------------------
// processJob — the Route 2 pipeline for one job.
// ---------------------------------------------------------------------
async function recordFallback(job: JobRow, reason: string, entitled: boolean) {
  if (!entitled) return
  const { error } = await supabase.from('personal_fallback_events').insert({
    user_id: job.user_id, combination_id: job.combination_id,
    epoch: job.epoch, job_id: job.id, reason
  })
  if (error) console.error('[personal-hero-worker] fallback event failed:', error)
}

async function processJob(job: JobRow): Promise<Record<string, unknown>> {
  // Guarded claim.
  const { data: claimed, error: claimError } = await supabase
    .from('personal_generation_jobs')
    .update({
      status: 'PROCESSING', started_at: new Date().toISOString(),
      // RW-80: the claim stamp proper — written ONLY here, at claim
      // time (including re-claims after stale recovery, which pass
      // through this same guarded update). started_at keeps its
      // heartbeat role and must never be read as the claim time.
      claimed_at: new Date().toISOString()
    })
    .eq('id', job.id)
    .eq('status', 'QUEUED')
    .select('id')
  if (claimError) throw claimError
  if (!claimed || claimed.length === 0) {
    return { job_id: job.id, status: 'SKIPPED', error: 'already claimed' }
  }

  try {
    // Profile + epoch discipline: a job from a superseded photo set
    // fails closed before any spend.
    const { data: profile, error: profileError } = await supabase
      .from('personal_profiles')
      .select('photo_epoch, entitled')
      .eq('user_id', job.user_id)
      .maybeSingle()
    if (profileError) throw profileError
    const currentEpoch = (profile?.photo_epoch as number | undefined) ?? 0
    const entitled = profile?.entitled === true
    if ((job.epoch ?? 0) !== currentEpoch) {
      await markJob(job.id, {
        status: 'FAILED', stage: 'superseded_epoch',
        error_message: `superseded_epoch: job epoch ${job.epoch ?? 0} != current ${currentEpoch}`,
        completed_at: new Date().toISOString()
      })
      return { job_id: job.id, status: 'FAILED', error: 'superseded_epoch' }
    }

    // Variant slot: cap enforced at write time (v23 semantics).
    const { data: existing } = await supabase
      .from('personal_heroes')
      .select('variant')
      .eq('user_id', job.user_id)
      .eq('combination_id', job.combination_id)
    const used = new Set((existing ?? []).map(r => r.variant as number))
    if (used.size >= VARIANT_CAP && job.kind !== 'regeneration') {
      await markJob(job.id, {
        status: 'FAILED', error_message: 'variant_cap_reached', stage: 'variant_cap',
        completed_at: new Date().toISOString()
      })
      return { job_id: job.id, status: 'FAILED', error: 'variant_cap_reached' }
    }
    let variant = 1
    while (used.has(variant)) variant++

    // References: anchor = the single strongest (never blended).
    const { data: refs, error: refError } = await supabase
      .from('personal_reference_photos')
      .select('storage_path, position, quality')
      .eq('user_id', job.user_id)
      .order('position', { ascending: true })
    if (refError) throw refError
    const anchor = selectAnchor((refs ?? []) as RefRow[])
    if (!anchor) throw new Error('No reference photos registered')
    const refBytes = await loadDerivativeBytes(anchor.storage_path)
    const refDataUri = `data:image/jpeg;base64,${bytesToB64(refBytes)}`

    const prompt = buildPrompt(job)
    const budget = drawBudgetFor(job.weather, job.temp_bracket)
    const draws: DrawRecord[] = []
    let drawsUsedTotal = job.draws_used ?? 0
    let identityBest: number | null = null
    let lastStage = 'identity_screen'
    let lastFailures: GateFailure[] = []

    const noteScreen = (rec: DrawRecord, screen: ScreenResult) => {
      rec.identity_similarity = screen.similarity
      if (screen.similarity !== null) {
        identityBest = identityBest === null
          ? screen.similarity : Math.max(identityBest, screen.similarity)
      }
    }

    // Try one candidate end-to-end (screen -> gate -> deliver).
    // Returns true when the job completed.
    const tryCandidate = async (
      bytes: Uint8Array, rec: DrawRecord, candPrompt: string
    ): Promise<boolean> => {
      const outcome = await screenAndGate(job, candPrompt, bytes, refBytes)
      noteScreen(rec, outcome.screen)
      rec.stage = outcome.stage
      if (outcome.edit) {
        rec.edited = true
        rec.edit = outcome.edit
      }
      if (outcome.verdict) {
        rec.gate_pass = outcome.verdict.pass
        rec.gate_cached = outcome.gateCached
        rec.failures = outcome.verdict.failures
        rec.notes = outcome.verdict.notes
      }
      lastStage = outcome.stage
      if (outcome.verdict) lastFailures = outcome.verdict.failures
      if (!outcome.deliverable) return false
      const delivered = await deliver(job, outcome.deliverable, variant, rec)
      if (delivered === 'allowance_exhausted') {
        await markJob(job.id, {
          status: 'FAILED', stage: 'allowance_exhausted',
          error_message: 'allowance_exhausted: free delivery claim refused at the cap',
          draws_used: Math.max(job.draws_used ?? 0, rec.draw),
          completed_at: new Date().toISOString()
        })
        throw new AllowanceExhausted()
      }
      return true
    }

    // --- Resume path (stale-claim recovery): screen + gate the
    // interrupted invocation's already-paid draw before generating.
    if (job.replicate_id) {
      const resumedBytes = await fetchPredictionOutputBytes(job.replicate_id)
      if (resumedBytes) {
        const rec: DrawRecord = {
          draw: job.attempts || 1, prediction_id: job.replicate_id,
          image_hash: null, identity_similarity: null, stage: 'resumed'
        }
        console.log(`[personal-hero-worker] job ${job.id}: resuming screen+gate on interrupted draw (prediction ${job.replicate_id})`)
        // The gate's expected classes derive from buildPrompt(job) —
        // deterministic from the job's own fields — never from a
        // stored prompt (a pre-RW-16.30 job's stored prompt is a
        // Kontext editor prompt and would mis-derive them).
        if (await tryCandidate(resumedBytes, rec, prompt)) {
          return {
            job_id: job.id, status: 'COMPLETED',
            combination_id: job.combination_id, resumed: true, draws
          }
        }
        rec.image_hash = await sha256Hex(resumedBytes)
        draws.push(rec)
      } else {
        console.log(`[personal-hero-worker] job ${job.id}: interrupted prediction ${job.replicate_id} no longer retrievable; restarting generation`)
      }
    }

    // --- Draw loop: stop at first usable.
    const startDraw = (job.draws_used ?? 0) + 1
    for (let drawNo = startDraw; drawNo <= budget; drawNo++) {
      const predictionId = await createPulidDraw(prompt, refDataUri)
      // Heartbeat (RW-16.25 discipline): refresh the claim stamp at
      // every draw boundary so the stale scan can tell a live job
      // from a terminated invocation.
      await markJob(job.id, {
        replicate_id: predictionId, attempts: drawNo, draws_used: drawNo,
        prompt, started_at: new Date().toISOString()
      })
      drawsUsedTotal = drawNo
      // RW-90 draw-level terminal discipline: the outcome of a
      // SUBMITTED draw (poll timeout, prediction failed/canceled,
      // poll/download error) is a counted failed draw inside this
      // job's own budget — recorded on the draw record and the
      // loop redraws — never a job-level death that can strand
      // the job in PROCESSING. Only budget exhaustion (below)
      // fails the job, with this stage as the recorded reason.
      // The poll carries the RW-90 heartbeat so the stale scan
      // can tell this live job from a terminated invocation.
      let bytes: Uint8Array
      try {
        bytes = await predictionOutputBytes(
          predictionId, DRAW_POLL_TIMEOUT_MS,
          () => markJob(job.id, { started_at: new Date().toISOString() })
        )
      } catch (e) {
        const message = (e as Error).message
        const stage = message.startsWith('Prediction timed out')
          ? 'draw_timeout' : 'draw_failed'
        lastStage = stage
        const rec: DrawRecord = {
          draw: drawNo, prediction_id: predictionId,
          image_hash: null, identity_similarity: null, stage,
          failures: [{ rule: stage, message }]
        }
        draws.push(rec)
        console.log(`[personal-hero-worker] job ${job.id} draw ${drawNo}/${budget} ended at ${stage}: ${message}`)
        continue
      }
      const rec: DrawRecord = {
        draw: drawNo, prediction_id: predictionId,
        image_hash: await sha256Hex(bytes),
        identity_similarity: null, stage: 'generated'
      }
      if (await tryCandidate(bytes, rec, prompt)) {
        draws.push(rec)
        return {
          job_id: job.id, status: 'COMPLETED',
          combination_id: job.combination_id, draws
        }
      }
      draws.push(rec)
      console.log(`[personal-hero-worker] job ${job.id} draw ${drawNo}/${budget} ended at ${rec.stage}` +
        (rec.failures ? `: ${rec.failures.map(f => f.message).join('; ')}` : ''))
    }

    // Budget exhausted: nothing stored; stock keeps serving. For an
    // entitled user this is the RW-16.20 fallback — counted, never
    // silent.
    const failureText = lastStage === 'prescription_gate'
      ? `gate_failed: ${lastFailures.map(f => f.message).join('; ')}`
      : lastStage === 'identity_screen'
        ? `gate_failed: identity_screen (best similarity ${identityBest ?? 'none'})`
        : `${lastStage}: draw never returned a usable image within the job's draw budget (${drawsUsedTotal}/${budget} draws used)`
    await markJob(job.id, {
      status: 'FAILED', stage: lastStage,
      error_message: failureText,
      draws_used: drawsUsedTotal,
      identity_best: identityBest,
      gate_result: { pipeline: 'rw16.30-route2', draws },
      completed_at: new Date().toISOString()
    })
    await recordFallback(job, lastStage, entitled)
    return { job_id: job.id, status: 'FAILED', error: failureText, draws }
  } catch (e) {
    if (e instanceof AllowanceExhausted) {
      return { job_id: job.id, status: 'FAILED', error: 'allowance_exhausted' }
    }
    const message = (e as Error).message
    const stage = message.startsWith('gate_unavailable') ||
      message.startsWith('identity_screen_unavailable')
      ? 'infrastructure' : undefined
    await markJob(job.id, {
      status: 'FAILED', error_message: message,
      ...(stage ? { stage } : {}),
      completed_at: new Date().toISOString()
    })
    if (stage === 'infrastructure') {
      const { data: profile } = await supabase
        .from('personal_profiles')
        .select('entitled')
        .eq('user_id', job.user_id)
        .maybeSingle()
      await recordFallback(job, message.split(':')[0], profile?.entitled === true)
    }
    return { job_id: job.id, status: 'FAILED', error: message }
  }
}

class AllowanceExhausted extends Error {
  constructor() { super('allowance_exhausted') }
}

// Stale-claim recovery — v23 logic unchanged (see the v23 header for
// the full reasoning): a job still PROCESSING past the threshold was
// terminated mid-processing by the platform; requeue it within its
// reclaim budget (keeping replicate_id/attempts so the resume path
// can screen + gate the already-generated draw first), or fail it
// with the named stale_processing_timeout reason at the cap.
async function reclaimStaleJobs(): Promise<Array<Record<string, unknown>>> {
  const cutoff = new Date(Date.now() - STALE_PROCESSING_MS).toISOString()
  const { data: stale, error } = await supabase
    .from('personal_generation_jobs')
    .select('id, reclaim_count')
    .eq('status', 'PROCESSING')
    .lt('started_at', cutoff)
  if (error) {
    console.error('[personal-hero-worker] stale scan failed:', error)
    return []
  }
  const outcomes: Array<Record<string, unknown>> = []
  for (const row of (stale ?? []) as Array<{ id: string, reclaim_count: number | null }>) {
    const reclaims = row.reclaim_count ?? 0
    if (reclaims >= STALE_MAX_RECLAIMS) {
      const { data: marked } = await supabase
        .from('personal_generation_jobs')
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
        outcomes.push({ job_id: row.id, status: 'FAILED', error: STALE_FAILURE_REASON, reclaims })
        console.log(`[personal-hero-worker] stale job ${row.id} FAILED: ${STALE_FAILURE_REASON} after ${reclaims} reclaim(s)`)
      }
    } else {
      const { data: reset } = await supabase
        .from('personal_generation_jobs')
        .update({ status: 'QUEUED', reclaim_count: reclaims + 1 })
        .eq('id', row.id)
        .eq('status', 'PROCESSING')
        .eq('reclaim_count', reclaims)
        .lt('started_at', cutoff)
        .select('id')
      if (reset && reset.length > 0) {
        outcomes.push({ job_id: row.id, status: 'RECLAIMED', reclaims: reclaims + 1 })
        console.log(`[personal-hero-worker] reclaimed stale job ${row.id} (reclaim ${reclaims + 1}/${STALE_MAX_RECLAIMS})`)
      }
    }
  }
  return outcomes
}

// RW-107 reconciliation sweep — see the RW-107 header amendment
// for the full reasoning. A personal_cells row that is counted
// (free_counted, or ever_delivered for an entitled claim) with
// no hero row and no live job is a look the user has spent (or
// been granted) that serves nothing. However the hero was lost
// — a flag whose regeneration later died terminally, an admin
// deletion, row drift — the repair is the same: queue a
// 'regeneration' job for the cell (the kind that never charges
// the free allowance and preserves the cell's counted state on
// delivery) at the profile's current epoch, and stamp the cell
// regen-pending. Runs on every drain, so the repair happens
// whether or not the user ever views the cell again.
async function reconcileOrphanedCells(): Promise<Array<Record<string, unknown>>> {
  // Candidate selection runs in the database
  // (public.reconcile_candidates): counted, no hero row, no live
  // job, oldest-stuck first — so RECONCILE_SCAN_LIMIT bounds
  // true candidates, not the oldest healthy cells (a code-side
  // filter over a limited scan window would starve recent
  // orphans behind every older healthy counted cell). The
  // per-cell hero/live-job checks below still run before any
  // repair is queued, closing the race between selection and
  // another path landing a hero or a job for the same cell.
  const { data: cells, error } = await supabase
    .rpc('reconcile_candidates', { p_limit: RECONCILE_SCAN_LIMIT })
  if (error) {
    console.error('[personal-hero-worker] reconcile scan failed:', error)
    return []
  }
  const outcomes: Array<Record<string, unknown>> = []
  for (const cell of (cells ?? []) as Array<Record<string, unknown>>) {
    const userId = cell.user_id as string
    const combinationId = cell.combination_id as string
    try {
      const { data: heroes } = await supabase
        .from('personal_heroes')
        .select('id')
        .eq('user_id', userId)
        .eq('combination_id', combinationId)
        .limit(1)
      if (heroes && heroes.length > 0) continue

      const { data: active } = await supabase
        .from('personal_generation_jobs')
        .select('id')
        .eq('user_id', userId)
        .eq('combination_id', combinationId)
        .in('status', ['QUEUED', 'PROCESSING'])
        .limit(1)
      if (active && active.length > 0) continue

      // Loop safety: a cell already stamped regen-pending within
      // the retry window has a repair cycle outstanding or
      // recently dead; leave it until the stamp ages out.
      const pendingSince = typeof cell.regen_pending_since === 'string'
        ? new Date(cell.regen_pending_since as string).getTime() : null
      if (cell.regen_status === 'pending' && pendingSince !== null &&
          Date.now() - pendingSince < RECONCILE_RETRY_AFTER_MS) {
        continue
      }

      // The repair job's shape comes from the cell's most recent
      // job (every counted cell was counted by a delivery running
      // under a job, so one exists; a cell without one cannot be
      // reconstructed faithfully and is reported, not guessed).
      const { data: priorJobs } = await supabase
        .from('personal_generation_jobs')
        .select('outfit_items')
        .eq('user_id', userId)
        .eq('combination_id', combinationId)
        .order('requested_at', { ascending: false })
        .limit(1)
      if (!priorJobs || priorJobs.length === 0) {
        outcomes.push({ user_id: userId, combination_id: combinationId, status: 'SKIPPED', error: 'no_source_job' })
        console.error(`[personal-hero-worker] reconcile: counted cell ${combinationId} for ${userId} has no source job; cannot reconstruct`)
        continue
      }
      const { data: profile } = await supabase
        .from('personal_profiles')
        .select('photo_epoch')
        .eq('user_id', userId)
        .maybeSingle()
      if (!profile) {
        outcomes.push({ user_id: userId, combination_id: combinationId, status: 'SKIPPED', error: 'no_profile' })
        continue
      }

      const parts = combinationId.split('_')
      const { data: repairJob, error: insertError } = await supabase
        .from('personal_generation_jobs')
        .insert({
          user_id: userId,
          combination_id: combinationId,
          gender: parts[0], weather: parts[1],
          temp_bracket: parts[2], time_of_day: parts[3],
          outfit_signature: parts[4],
          outfit_items: priorJobs[0].outfit_items,
          kind: 'regeneration',
          priority: REPAIR_PRIORITY,
          epoch: (profile.photo_epoch as number | undefined) ?? 0,
          status: 'QUEUED'
        })
        .select('id')
        .single()
      if (insertError) throw insertError

      await supabase
        .from('personal_cells')
        .update({
          regen_status: 'pending',
          regen_pending_since: new Date().toISOString(),
          regen_error: null,
          updated_at: new Date().toISOString()
        })
        .eq('user_id', userId)
        .eq('combination_id', combinationId)

      outcomes.push({ user_id: userId, combination_id: combinationId, status: 'REPAIR_QUEUED', job_id: repairJob.id })
      console.log(`[personal-hero-worker] reconcile: counted cell ${combinationId} for ${userId} has no hero and no live job; queued repair regeneration ${repairJob.id}`)
    } catch (e) {
      outcomes.push({ user_id: userId, combination_id: combinationId, status: 'ERROR', error: (e as Error).message })
      console.error(`[personal-hero-worker] reconcile failed for cell ${combinationId} (${userId}):`, e)
    }
  }
  return outcomes
}

// Guarded so the module's pure logic (prompt builder, gate port,
// budget policy, anchor selection) is unit-testable via
// index.test.ts; the edge runtime executes this file as main.
if (import.meta.main) {
  serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'Method not allowed' }, 405)

    // Service-role only: this function spends money and writes heroes.
    // Accept any legitimate service credential (v23 discipline): the
    // env key, a new-format secret key, or a legacy JWT whose role
    // claim is service_role (the gateway has already validated the
    // credential itself).
    const token = (req.headers.get('Authorization') ?? '').replace(/^Bearer\s+/i, '')
    let serviceCall = token === SUPABASE_SERVICE_ROLE_KEY
    if (!serviceCall) {
      const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
      if (secretKeys && token && secretKeys.includes(token)) serviceCall = true
    }
    if (!serviceCall && token) {
      try {
        const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        const payload = JSON.parse(atob(part + '='.repeat((4 - part.length % 4) % 4)))
        serviceCall = payload?.role === 'service_role'
      } catch { /* not a JWT */ }
    }
    if (!serviceCall) {
      return jsonResponse({ error: 'Unauthorized' }, 401)
    }

    let drain = DEFAULT_DRAIN_LIMIT
    try {
      const body = await req.json()
      if (body && typeof body.drain === 'number' && body.drain > 0) {
        drain = Math.min(Math.floor(body.drain), MAX_DRAIN_LIMIT)
      }
    } catch { /* no body -> default drain */ }

    const reclaimed = await reclaimStaleJobs()
    const reconciled = await reconcileOrphanedCells()

    // Gen-6 Wave A: claim in EFFECTIVE band order (see claimBand),
    // and keep draining while work remains and the chain budget
    // lasts — throughput is bounded by generation time, not by
    // how often something invokes this function.
    const JOB_SELECT = 'id, user_id, combination_id, gender, weather, temp_bracket, time_of_day, outfit_signature, outfit_items, kind, attempts, replicate_id, prompt, reclaim_count, epoch, draws_used, priority, requested_at'
    const results: Array<Record<string, unknown>> = []
    const startedAt = Date.now()
    let chained = false
    let remainingQueued = 0

    while (true) {
      const { data: jobs, error } = await supabase
        .from('personal_generation_jobs')
        .select(JOB_SELECT)
        .eq('status', 'QUEUED')
        .order('requested_at', { ascending: true })
        .limit(CLAIM_FETCH_LIMIT)
      if (error) throw error

      const ordered = ((jobs ?? []) as JobRow[]).slice().sort(compareJobsForClaim)
      remainingQueued = ordered.length

      const passStartedAt = Date.now()
      let passProcessed = 0
      for (const job of ordered) {
        if (passProcessed >= drain) break
        if (Date.now() - passStartedAt > SOFT_TIME_BUDGET_MS && passProcessed > 0) break
        if (Date.now() - startedAt > CHAIN_TIME_BUDGET_MS && results.length > 0) break
        const result = await processJob(job)
        results.push(result)
        if (result.status !== 'SKIPPED') passProcessed++
      }
      remainingQueued -= passProcessed

      if (passProcessed === 0) break
      if (remainingQueued <= 0) break
      if (Date.now() - startedAt > CHAIN_TIME_BUDGET_MS) break
    }

    // Self-chain: jobs remain but this invocation's chain budget is
    // spent — hand off to a fresh invocation immediately instead of
    // waiting for the 5-minute cron backstop.
    if (remainingQueued > 0) {
      const selfKick = fetch(`${SUPABASE_URL}/functions/v1/personal-hero-worker`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ drain })
      }).catch(e => console.error('[personal-hero-worker] self-chain kick failed:', e))
      // @ts-ignore EdgeRuntime is a Supabase runtime global
      if (typeof EdgeRuntime !== 'undefined') { EdgeRuntime.waitUntil(selfKick); chained = true }
    }

    return jsonResponse({
      drained: true,
      attempted: results.length,
      reclaimed,
      reconciled,
      chained,
      remaining_queued: Math.max(remainingQueued, 0),
      results
    })
  } catch (error) {
    console.error('[personal-hero-worker] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
  })
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}
