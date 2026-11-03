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
const MAX_DRAIN_LIMIT = 3
const SOFT_TIME_BUDGET_MS = 100_000
// Identity screen delivered line (lanes' instrument of record:
// buffalo_l cosine >= 0.40 = delivered).
const IDENTITY_DELIVERED_MIN = 0.40

// Stale-claim recovery — unchanged from v23 (see the v23 header for
// the full reasoning; thresholds and budgets carry over verbatim).
const STALE_PROCESSING_MS = 300_000
const STALE_MAX_RECLAIMS = 3
const STALE_FAILURE_REASON = 'stale_processing_timeout'

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
  RAIN: 'Rain falling, wet glistening pavement, puddles, gray sky',
  SNOW: 'Snow falling, snow-covered streets and trees, soft winter light'
}
const SCENE_MILD_SNOW = 'Light spring snow shower, snowflakes melting on wet pavement, green lawns at the edges'
const LIGHT: Record<string, string> = {
  DAWN: 'Soft golden sunrise light, long shadows, quiet streets; ',
  MIDDAY: 'Bright daylight; ',
  DUSK: 'Golden evening light, long shadows; ',
  NIGHT: 'Dark night lit by streetlights; '
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
  const env = `ENVIRONMENT: ${LIGHT[job.time_of_day] ?? ''}${scene}`
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
  'PERSON_COUNT: <number of people in the photo>\n' +
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
  if (seen.hands !== exp.hands) {
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
    ['PERSON_COUNT', 'person_count'], ['ANATOMY_OK', 'anatomy_ok'],
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
async function pollPrediction(predictionId: string, maxWaitMs: number): Promise<Record<string, any>> {
  const start = Date.now()
  while (Date.now() - start < maxWaitMs) {
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

async function predictionOutputBytes(predictionId: string, maxWaitMs: number): Promise<Uint8Array> {
  const result = await pollPrediction(predictionId, maxWaitMs)
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
  reclaim_count: number, epoch: number, draws_used: number
}

async function markJob(id: string, patch: Record<string, unknown>) {
  const { error } = await supabase.from('personal_generation_jobs').update(patch).eq('id', id)
  if (error) console.error('[personal-hero-worker] markJob failed:', error)
}

type DrawRecord = {
  draw: number, prediction_id: string | null, image_hash: string | null,
  identity_similarity: number | null, stage: string,
  gate_pass?: boolean, gate_cached?: boolean,
  failures?: GateFailure[], notes?: string[]
}

type ScreenedDraw = {
  bytes: Uint8Array, imageHash: string, screen: ScreenResult,
  verdict: GateVerdict, gateCached: boolean, prompt: string
}

// Run one candidate through screen + gate (in that order — the screen
// is free and discards before any paid call). Returns the terminal
// stage for the draw and, when both stages pass, the deliverable.
async function screenAndGate(
  job: JobRow, prompt: string, bytes: Uint8Array, refBytes: Uint8Array
): Promise<{ stage: string, screen: ScreenResult, deliverable?: ScreenedDraw, verdict?: GateVerdict, gateCached?: boolean }> {
  const screen = await callIdentityScreen(refBytes, bytes)
  if (!screen.delivered) return { stage: 'identity_screen', screen }
  const imageHash = await sha256Hex(bytes)
  const { verdict, cached } = await runGate(job, prompt, bytes, imageHash)
  if (!verdict.pass) {
    return { stage: 'prescription_gate', screen, verdict, gateCached: cached }
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
  if (uploadError) throw new Error(`Hero upload failed: ${uploadError.message}`)

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

  await markJob(job.id, {
    status: 'COMPLETED', gate_result: gateDetail,
    result_hero_id: hero.id, stage: 'delivered',
    completed_at: new Date().toISOString()
  })
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
    .update({ status: 'PROCESSING', started_at: new Date().toISOString() })
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
      const bytes = await predictionOutputBytes(predictionId, 180_000)
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
      : `gate_failed: identity_screen (best similarity ${identityBest ?? 'none'})`
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

    const { data: jobs, error } = await supabase
      .from('personal_generation_jobs')
      .select('id, user_id, combination_id, gender, weather, temp_bracket, time_of_day, outfit_signature, outfit_items, kind, attempts, replicate_id, prompt, reclaim_count, epoch, draws_used')
      .eq('status', 'QUEUED')
      .order('priority', { ascending: true })
      .order('requested_at', { ascending: true })
      .limit(drain * 2)
    if (error) throw error

    const results: Array<Record<string, unknown>> = []
    const startedAt = Date.now()
    for (const job of (jobs ?? []) as JobRow[]) {
      if (results.filter(r => r.status !== 'SKIPPED').length >= drain) break
      if (Date.now() - startedAt > SOFT_TIME_BUDGET_MS && results.length > 0) break
      results.push(await processJob(job))
    }

    return jsonResponse({ drained: true, attempted: results.length, reclaimed, results })
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
