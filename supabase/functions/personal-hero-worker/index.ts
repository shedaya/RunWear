// Supabase Edge Function: personal-hero-worker (RW-14, 2026-10-31)
//
// Fulfills personal_generation_jobs: for each queued job, generate a
// personalized hero with FLUX Kontext Pro (Replicate) from the user's
// reference photos, run the VERIFICATION GATE, and store the image as a
// verified variant only if the gate passes.
//
// Gate (plan §3 — the load-bearing wall; a personalized hero must beat
// the generic pool hero or not appear at all):
//   1. Garment + anatomy check (vision model, generated image):
//      visible-surface items strict, occluded layers loose, nothing
//      extra, no brand logos/text, exactly one person, anatomy and
//      face intact, no duplicated accessories (the Kontext defect
//      classes observed in the Oct 31 bake-off).
//   2. Identity sanity (vision model, reference|generated composite):
//      the generated face must plausibly be the reference person.
//   Fail -> ONE regeneration with the failure classes fed back into
//   the prompt. Second fail -> job FAILED, nothing stored, the pool
//   hero keeps serving for that cell. A gate ERROR also fails closed:
//   an ungated image is never stored or served.
//
// Invocation: POST { "drain": N } (default 1, max 3), service-role
// bearer. Triggered by the personal-hero-drain pg_cron job, by
// request-personal-generation's waitUntil kick, and by
// start-personal-fill. Jobs are claimed with a QUEUED-status guard so
// concurrent invocations cannot double-process; a soft time budget
// stops claiming new jobs before the function wall clock.
//
// Stale-claim recovery (RW-15 remedy, 2026-10-31): every in-code
// failure path marks a claimed job FAILED, so a job still PROCESSING
// long after its claim means the invocation itself was terminated by
// the platform mid-processing (observed live: a job claimed, its
// generation completed, the invocation killed during the gate — the
// job then wedged forever, because the drain only claimed QUEUED
// jobs and the request action answered already_queued against it).
// Each invocation therefore first scans for PROCESSING jobs whose
// claim is older than STALE_PROCESSING_MS and either requeues them
// (spending one reclaim from the job's reclaim_count budget) or, at
// the budget cap, fails them with the named stale_processing_timeout
// reason. A reclaimed job that carries a replicate_id resumes by
// gating the already-generated image before paying for a new one.
//
// Spend: Kontext Pro is $0.04/image (published). Worst case per job is
// 2 generations + up to 4 vision calls.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { Image } from "https://deno.land/x/imagescript@1.2.17/mod.ts"

const REPLICATE_API_TOKEN = Deno.env.get('REPLICATE_API_TOKEN')!
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

const GENERATION_MODEL = 'black-forest-labs/flux-kontext-pro'
// Gate vision model: LLaVA v1.6 (Mistral 7B) — a community deployment,
// so it is called version-pinned via /v1/predictions (the /v1/models
// endpoint only serves official models). ~$0.04/run, ~40s typical.
const GATE_MODEL_VERSION = '19be067b589d0c46689ffa7cc3ff321447a441986a7694c01225973c2eafc874'
const VARIANT_CAP = 3
const IDENTITY_MIN_CONFIDENCE = 0.6
const DEFAULT_DRAIN_LIMIT = 1
const MAX_DRAIN_LIMIT = 3
const SOFT_TIME_BUDGET_MS = 100_000
// Stale-claim threshold: 15 minutes. Justification from this file's
// own timing bounds — one generation attempt is capped by the
// Kontext poll timeout (120 s) plus the gate (two vision calls in
// parallel, each poll-capped at 180 s), so two attempts plus the
// reference signing/download overhead cannot exceed ~11 minutes of
// legitimate work, and a typical job finishes in ~1–4 minutes
// (generation 5–9 s, gate ~40 s). The 100 s soft budget above only
// governs claiming NEW jobs, never an in-flight one. 15 minutes
// therefore sits strictly beyond any live invocation's possible
// duration — a reclaim can never race a worker that is still
// legitimately processing (no double generation, no double spend) —
// while the 5-minute drain cadence still recovers a wedged job
// within ~15–20 minutes of its claim.
const STALE_PROCESSING_MS = 900_000
// Reclaim budget per job: a job may be reclaimed at most this many
// times; the next stale scan after the budget is spent fails it with
// STALE_FAILURE_REASON. Bounds the worst case at 1 + 3 invocations
// per job against repeated platform terminations.
const STALE_MAX_RECLAIMS = 3
const STALE_FAILURE_REASON = 'stale_processing_timeout'

// The pool's anatomy hardening, reused verbatim (server-side, so every
// prompt carries it regardless of who queued the job).
const QUALITY_SUFFIX =
  ` ANATOMY & QUALITY: anatomically correct human body with natural proportions; ` +
  `arms bent at the elbows and swinging naturally beside the torso, hands relaxed ` +
  `at the runner's sides — never reaching across the back or touching the shoulders; ` +
  `each hand with five correct fingers; legs in a natural mid-stride gait with knees ` +
  `and feet pointing in the direction of travel; if the runner is seen from behind, ` +
  `arms, legs and hands must remain anatomically coherent from the back view. ` +
  `Photorealistic, shot on a full-frame camera. Avoid: distorted or merged limbs, ` +
  `extra fingers, hands or limbs, warped joints, backwards-bending arms.`

const ITEM_DISPLAY: Record<string, string> = {
  TANK_TOP: 'Tank Top / Singlet', SHORT_SLEEVE: 'Short Sleeve Shirt',
  LONG_SLEEVE_LIGHT: 'Light Long Sleeve', LONG_SLEEVE_THERMAL: 'Thermal Long Sleeve',
  QUARTER_ZIP: 'Quarter-Zip Pullover', FLEECE_PULLOVER: 'Fleece Pullover',
  HALF_ZIP_THERMAL: 'Thermal Half-Zip', LIGHT_VEST: 'Light Vest',
  WINDBREAKER: 'Windbreaker', LIGHT_JACKET: 'Light Running Jacket',
  RAIN_JACKET: 'Rain Jacket', INSULATED_JACKET: 'Insulated Jacket',
  SHORT_SHORTS: 'Short Shorts (3 inch)', SHORTS: 'Running Shorts (5-7 inch)',
  CAPRIS: 'Capris / 3/4 Tights', LIGHT_TIGHTS: 'Light Tights',
  THERMAL_TIGHTS: 'Thermal Tights', VISOR: 'Visor', BASEBALL_CAP: 'Running Cap',
  HEADBAND: 'Ear Warmer / Headband', LIGHT_BEANIE: 'Light Beanie',
  THERMAL_BEANIE: 'Thermal Beanie', BALACLAVA: 'Balaclava / Face Mask',
  LIGHT_GLOVES: 'Light Gloves', THERMAL_GLOVES: 'Thermal Gloves',
  MITTENS: 'Mittens', SUNGLASSES: 'Sunglasses', SUNSCREEN: 'Sunscreen',
  REFLECTIVE_GEAR: 'Reflective Gear', NECK_GAITER: 'Neck Gaiter / Buff'
}

const ITEM_CATEGORY: Record<string, string> = {
  TANK_TOP: 'TOP_BASE', SHORT_SLEEVE: 'TOP_BASE', LONG_SLEEVE_LIGHT: 'TOP_BASE',
  LONG_SLEEVE_THERMAL: 'TOP_BASE', QUARTER_ZIP: 'TOP_MID', FLEECE_PULLOVER: 'TOP_MID',
  HALF_ZIP_THERMAL: 'TOP_MID', LIGHT_VEST: 'TOP_OUTER', WINDBREAKER: 'TOP_OUTER',
  LIGHT_JACKET: 'TOP_OUTER', RAIN_JACKET: 'TOP_OUTER', INSULATED_JACKET: 'TOP_OUTER',
  SHORT_SHORTS: 'BOTTOM', SHORTS: 'BOTTOM', CAPRIS: 'BOTTOM', LIGHT_TIGHTS: 'BOTTOM',
  THERMAL_TIGHTS: 'BOTTOM', VISOR: 'HEAD', BASEBALL_CAP: 'HEAD', HEADBAND: 'HEAD',
  LIGHT_BEANIE: 'HEAD', THERMAL_BEANIE: 'HEAD', BALACLAVA: 'HEAD',
  LIGHT_GLOVES: 'HANDS', THERMAL_GLOVES: 'HANDS', MITTENS: 'HANDS',
  SUNGLASSES: 'ACCESSORIES', SUNSCREEN: 'ACCESSORIES',
  REFLECTIVE_GEAR: 'ACCESSORIES', NECK_GAITER: 'ACCESSORIES'
}

const BACKGROUNDS = [
  'a city street with buildings in the background', 'an urban park with trees',
  'a waterfront boardwalk', 'a scenic trail with nature', 'a downtown area with shops',
  'a bridge with a city skyline', 'a tree-lined avenue'
]

function buildPrompt(job: JobRow, failureClasses: string[]): string {
  // Layering order matters: Kontext drops outer layers when they are
  // listed flatly after the base layer (observed live: RAIN_JACKET
  // omitted twice when listed after LONG_SLEEVE). Name the outer layer
  // first and state the layering explicitly.
  const keys = job.outfit_items as string[]
  const order = ['TOP_OUTER', 'TOP_MID', 'TOP_BASE', 'BOTTOM', 'HEAD', 'HANDS', 'ACCESSORIES']
  const ordered = [...keys].sort(
    (a, b) => order.indexOf(ITEM_CATEGORY[a] ?? '') - order.indexOf(ITEM_CATEGORY[b] ?? ''))
  const items = ordered.map(k => ITEM_DISPLAY[k] ?? k)
  const outerKey = keys.find(k => ITEM_CATEGORY[k] === 'TOP_OUTER')
  const baseKey = keys.find(k => ITEM_CATEGORY[k] === 'TOP_BASE')
  const layering = outerKey && baseKey
    ? ` The ${ITEM_DISPLAY[outerKey]} is the outermost layer, worn over the ${ITEM_DISPLAY[baseKey]}.`
    : ''
  const weatherPhrase: Record<string, string> = {
    CLEAR: 'clear skies', CLOUDY: 'overcast skies',
    RAIN: 'steady rain, wet pavement', SNOW: 'falling snow'
  }
  const timePhrase: Record<string, string> = {
    DAWN: 'early morning golden-hour light', MIDDAY: 'bright midday light',
    DUSK: 'evening golden-hour light', NIGHT: 'night, lit by street lights'
  }
  let seed = 0
  for (let i = 0; i < job.combination_id.length; i++) seed += job.combination_id.charCodeAt(i)
  const background = BACKGROUNDS[seed % BACKGROUNDS.length]
  let prompt =
    `Photorealistic photo of the same person shown in the reference photos, ` +
    `now running mid-stride along ${background}, ${weatherPhrase[job.weather] ?? 'clear skies'}, ` +
    `${timePhrase[job.time_of_day] ?? 'daylight'}. They are wearing exactly this outfit and nothing extra: ` +
    `${items.join(', ')}.${layering} The runner is looking ahead, not at the camera. ` +
    `No visible brand logos, text, or trademarks anywhere in the image.` +
    QUALITY_SUFFIX
  if (failureClasses.length > 0) {
    prompt += ` IMPORTANT: a previous attempt at this photo failed verification because of: ` +
      `${failureClasses.join('; ')}. Fix exactly these problems.`
  }
  return prompt
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

type JobRow = {
  id: string, user_id: string, combination_id: string, gender: string,
  weather: string, temp_bracket: string, time_of_day: string,
  outfit_signature: string, outfit_items: unknown, kind: string,
  attempts: number, replicate_id: string | null, prompt: string | null,
  reclaim_count: number
}

async function replicatePredict(modelPath: string, input: Record<string, unknown>): Promise<string> {
  const response = await fetch(`https://api.replicate.com/v1/models/${modelPath}/predictions`, {
    method: 'POST',
    headers: {
      'Authorization': `Token ${REPLICATE_API_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ input })
  })
  if (!response.ok) {
    throw new Error(`Replicate create failed: ${response.status} ${await response.text()}`)
  }
  const prediction = await response.json()
  return prediction.id as string
}

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

async function generateImage(prompt: string, refUrls: string[]): Promise<{ url: string, predictionId: string }> {
  const input: Record<string, unknown> = {
    prompt,
    aspect_ratio: '2:3',
    output_format: 'png'
  }
  if (refUrls[0]) input.input_image = refUrls[0]
  if (refUrls[1]) input.input_image_2 = refUrls[1]
  if (refUrls[2]) input.input_image_3 = refUrls[2]
  const id = await replicatePredict(GENERATION_MODEL, input)
  const result = await pollPrediction(id, 120_000)
  if (result.status !== 'succeeded') {
    throw new Error(`Generation failed: ${result.error ?? result.status}`)
  }
  const url = Array.isArray(result.output) ? result.output[0] : result.output
  if (!url) throw new Error('Generation returned no output')
  return { url: url as string, predictionId: id }
}

// Resume support: fetch a completed prediction's output URL. The
// prediction record outlives the invocation that created it, but its
// output file URL expires (Replicate retains prediction file outputs
// for ~1 hour), so callers must treat a null here — or a failed
// download of the returned URL — as "restart the generation".
async function fetchPredictionOutputUrl(predictionId: string): Promise<string | null> {
  try {
    const response = await fetch(`https://api.replicate.com/v1/predictions/${predictionId}`, {
      headers: { 'Authorization': `Token ${REPLICATE_API_TOKEN}` }
    })
    if (!response.ok) return null
    const prediction = await response.json()
    if (prediction.status !== 'succeeded') return null
    const url = Array.isArray(prediction.output) ? prediction.output[0] : prediction.output
    return typeof url === 'string' ? url : null
  } catch {
    return null
  }
}

async function callVision(image: string, prompt: string): Promise<string> {
  const response = await fetch('https://api.replicate.com/v1/predictions', {
    method: 'POST',
    headers: {
      'Authorization': `Token ${REPLICATE_API_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      version: GATE_MODEL_VERSION,
      input: { image, prompt, max_tokens: 800, temperature: 0.1 }
    })
  })
  if (!response.ok) {
    throw new Error(`Gate create failed: ${response.status} ${await response.text()}`)
  }
  const created = await response.json()
  const result = await pollPrediction(created.id as string, 180_000)
  if (result.status !== 'succeeded') {
    throw new Error(`Gate model failed: ${result.error ?? result.status}`)
  }
  const out = result.output
  return Array.isArray(out) ? out.join('') : String(out ?? '')
}

function parseJsonObject(text: string): Record<string, any> | null {
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  try { return JSON.parse(text.slice(start, end + 1)) } catch { return null }
}

type GateOutcome = { pass: boolean, failures: string[], raw: Record<string, unknown> }

// --- garment perception -------------------------------------------------
// A 7B-class VLM is unreliable at emitting a strict JSON checklist (it
// echoes schema placeholders back as values — observed live), but it is
// good at PERCEPTION: listing what it sees. So the VLM only enumerates
// the visible clothing and answers five fixed-form questions; the
// checklist comparison happens here, in code, at garment-CLASS
// granularity (sleeve length, outer layer, bottom class, head class,
// hands class) — the granularity a photo can honestly be judged at.
type GarmentClasses = {
  topBase: 'sleeveless' | 'short' | 'long' | null,
  outer: boolean, mid: boolean,
  bottom: 'short_shorts' | 'shorts' | 'capris' | 'tights' | null,
  head: 'cap' | 'visor' | 'headband' | 'beanie' | 'balaclava' | null,
  hands: 'gloves' | 'mittens' | null,
  sunglasses: boolean, gaiter: boolean
}

function classesFromItems(items: string[]): GarmentClasses {
  const has = (k: string) => items.includes(k)
  return {
    topBase: has('TANK_TOP') ? 'sleeveless'
      : has('SHORT_SLEEVE') ? 'short'
      : (has('LONG_SLEEVE_LIGHT') || has('LONG_SLEEVE_THERMAL')) ? 'long' : null,
    outer: items.some(i => ITEM_CATEGORY[i] === 'TOP_OUTER'),
    mid: items.some(i => ITEM_CATEGORY[i] === 'TOP_MID'),
    bottom: has('SHORT_SHORTS') ? 'short_shorts'
      : has('SHORTS') ? 'shorts'
      : has('CAPRIS') ? 'capris'
      : (has('LIGHT_TIGHTS') || has('THERMAL_TIGHTS')) ? 'tights' : null,
    head: has('BALACLAVA') ? 'balaclava'
      : (has('LIGHT_BEANIE') || has('THERMAL_BEANIE')) ? 'beanie'
      : has('HEADBAND') ? 'headband'
      : has('VISOR') ? 'visor'
      : has('BASEBALL_CAP') ? 'cap' : null,
    hands: has('MITTENS') ? 'mittens'
      : (has('LIGHT_GLOVES') || has('THERMAL_GLOVES')) ? 'gloves' : null,
    sunglasses: has('SUNGLASSES'),
    gaiter: has('NECK_GAITER')
  }
}

function classesFromText(text: string): GarmentClasses {
  const t = text.toLowerCase()
  const hasAny = (...words: string[]) => words.some(w => t.includes(w))
  return {
    topBase: hasAny('tank top', 'singlet', 'sleeveless') ? 'sleeveless'
      : hasAny('long sleeve', 'long-sleeve', 'long sleeved') ? 'long'
      : hasAny('short sleeve', 'short-sleeve', 't-shirt', 'tee shirt') ? 'short' : null,
    outer: hasAny('jacket', 'windbreaker', 'coat', 'shell jacket', 'raincoat') ||
           (hasAny('vest') && !hasAny('reflective')),
    mid: hasAny('quarter zip', 'quarter-zip', 'fleece', 'pullover', 'half zip'),
    bottom: hasAny('short shorts') ? 'short_shorts'
      : hasAny('capris', '3/4 tights', 'three-quarter') ? 'capris'
      : hasAny('tights', 'leggings') ? 'tights'
      : hasAny('shorts') ? 'shorts' : null,
    head: hasAny('balaclava') ? 'balaclava'
      : hasAny('beanie', 'winter hat', 'knit hat') ? 'beanie'
      : hasAny('headband', 'ear warmer') ? 'headband'
      : hasAny('visor') ? 'visor'
      : hasAny('cap', 'hat') ? 'cap' : null,
    hands: hasAny('mittens') ? 'mittens' : hasAny('gloves') ? 'gloves' : null,
    sunglasses: hasAny('sunglasses', 'shades'),
    gaiter: hasAny('neck gaiter', 'buff', 'neck warmer', 'scarf')
  }
}

function compareGarments(expected: GarmentClasses, seen: GarmentClasses): string[] {
  const failures: string[] = []
  if (expected.bottom && seen.bottom !== expected.bottom &&
      !((expected.bottom === 'shorts' && seen.bottom === 'short_shorts') ||
        (expected.bottom === 'short_shorts' && seen.bottom === 'shorts'))) {
    failures.push(`missing_required: bottom (expected ${expected.bottom}, saw ${seen.bottom ?? 'none'})`)
  }
  if (expected.outer && !seen.outer) failures.push('missing_required: outer layer (jacket)')
  // A top base under an outer layer is honestly hidden -> not judged.
  if (expected.topBase && !expected.outer && seen.topBase !== expected.topBase) {
    failures.push(`missing_required: top (expected ${expected.topBase} sleeve, saw ${seen.topBase ?? 'none'})`)
  }
  if (expected.head && seen.head !== expected.head) {
    failures.push(`missing_required: head (expected ${expected.head}, saw ${seen.head ?? 'none'})`)
  }
  if (expected.hands && seen.hands !== expected.hands) {
    failures.push(`missing_required: hands (expected ${expected.hands}, saw ${seen.hands ?? 'none'})`)
  }
  if (expected.sunglasses && !seen.sunglasses) failures.push('missing_required: sunglasses')
  if (!expected.outer && seen.outer) failures.push('extra_items: jacket/outer layer')
  if (seen.head && !expected.head) failures.push(`extra_items: head (${seen.head})`)
  if (seen.hands && !expected.hands) failures.push(`extra_items: hands (${seen.hands})`)
  if (seen.sunglasses && !expected.sunglasses) failures.push('extra_items: sunglasses')
  return failures
}

async function runGate(job: JobRow, imageUrl: string, imageBytes: Uint8Array,
                       refBytes: Uint8Array): Promise<GateOutcome> {
  const items = job.outfit_items as string[]
  const failures: string[] = []
  const raw: Record<string, unknown> = {}

  // --- 1. garment + anatomy: the VLM enumerates; code compares ---
  const garmentPrompt =
    `Look at this photo of a runner. First, list every clothing item and accessory the runner is wearing, one per line, using simple names like: tank top, short sleeve shirt, long sleeve shirt, jacket, vest, shorts, short shorts, capris, tights, cap, visor, headband, beanie, gloves, mittens, sunglasses, neck gaiter, watch.\n` +
    `Then answer these questions, each on its own line, in exactly this form:\n` +
    `PERSON_COUNT: <number of people in the photo>\n` +
    `ANATOMY_OK: <yes if the body, limbs, hands and joints look anatomically correct; no if anything is distorted, merged, duplicated or missing>\n` +
    `FACE_OK: <yes if the face is intact, unobscured and clearly one person's face; no otherwise>\n` +
    `LOGOS_OR_TEXT: <yes if any brand logo or text is visible on the clothing; no otherwise>\n` +
    `TWO_WATCHES: <yes if the runner wears more than one watch; no otherwise>`
  // Build the identity composite first, then run both vision calls in
  // parallel (each takes ~40s on the gate model).
  let compositeUri: string | null = null
  let compositeError: string | null = null
  try {
    const refImg = await Image.decode(refBytes)
    const genImg = await Image.decode(imageBytes)
    const H = 768
    const refBit = refImg.resize(Image.RESIZE_AUTO, H)
    const genBit = genImg.resize(Image.RESIZE_AUTO, H)
    const composite = new Image(refBit.width + genBit.width, H)
    composite.composite(refBit, 0, 0)
    composite.composite(genBit, refBit.width, 0)
    const jpeg = await composite.encodeJPEG(80)
    let binary = ''
    for (let i = 0; i < jpeg.length; i += 8192) {
      binary += String.fromCharCode(...jpeg.subarray(i, i + 8192))
    }
    compositeUri = `data:image/jpeg;base64,${btoa(binary)}`
  } catch (e) {
    compositeError = (e as Error).message
  }
  const identityPrompt =
    `The LEFT half of this image is a reference photo of a real person. ` +
    `The RIGHT half is an AI-generated photo that should depict the SAME person. ` +
    `Compare facial structure, hair, eyebrows, eyes, nose and mouth. ` +
    `Answer with ONLY a JSON object: {"same_person": true or false, "confidence": a number from 0.0 to 1.0}`

  const [garmentText, identityText] = await Promise.all([
    callVision(imageUrl, garmentPrompt),
    compositeUri ? callVision(compositeUri, identityPrompt)
                 : Promise.resolve(null)
  ])
  const seen = classesFromText(garmentText)
  const expected = classesFromItems(items)
  raw.garment_text = garmentText.slice(0, 1000)
  raw.garment_seen = seen
  raw.garment_expected = expected
  failures.push(...compareGarments(expected, seen))
  const answer = (key: string): string | null => {
    const m = garmentText.match(new RegExp(`${key}\\s*:\\s*([^\\n]+)`, 'i'))
    return m ? m[1].trim().toLowerCase() : null
  }
  const personCount = answer('PERSON_COUNT')
  if (personCount === null) failures.push('gate_unparseable_garment')
  else if (!personCount.startsWith('1')) failures.push(`person_count: ${personCount}`)
  if (answer('ANATOMY_OK') !== 'yes') failures.push('anatomy')
  if (answer('FACE_OK') !== 'yes') failures.push('face_integrity')
  if (answer('LOGOS_OR_TEXT') === 'yes') failures.push('brand_logos_or_text')
  if (answer('TWO_WATCHES') === 'yes') failures.push('duplicated_accessories')

  // --- 2. identity sanity (result of the parallel call above) ---
  if (compositeError !== null || identityText === null) {
    failures.push(`identity_check_error: ${compositeError ?? 'no composite'}`)
  } else {
    const identity = parseJsonObject(identityText)
    raw.identity = identity ?? identityText.slice(0, 500)
    if (!identity) failures.push('gate_unparseable_identity')
    else if (identity.same_person !== true ||
             typeof identity.confidence !== 'number' ||
             identity.confidence < IDENTITY_MIN_CONFIDENCE) {
      failures.push(`identity_mismatch (confidence ${identity.confidence})`)
    }
  }

  return { pass: failures.length === 0, failures, raw }
}

async function markJob(id: string, patch: Record<string, unknown>) {
  const { error } = await supabase.from('personal_generation_jobs').update(patch).eq('id', id)
  if (error) console.error('[personal-hero-worker] markJob failed:', error)
}

// Gate one generated image and, iff it passes, store it as a
// verified hero and run the delivery accounting (free-tier charge on
// first verified delivery of a genuinely new cell only — see the
// accounting comment inside). Shared by the fresh generation loop
// and the stale-claim resume path so both honour the identical gate
// and the identical accounting. Throws on infrastructure errors
// (callers mark the job FAILED); returns the gate outcome otherwise.
async function gateAndDeliver(
  job: JobRow, attempt: number, imageUrl: string, imageBytes: Uint8Array,
  refBytes: Uint8Array, variant: number, prompt: string
): Promise<{ delivered: boolean, heroId?: string, failures: string[], gateRaw: Record<string, unknown> }> {
  const gate = await runGate(job, imageUrl, imageBytes, refBytes)
  const gateRaw = { attempt, ...gate.raw }
  if (!gate.pass) return { delivered: false, failures: gate.failures, gateRaw }

  const storagePath = `${job.user_id}/heroes/${job.combination_id}/v${variant}.png`
  const { error: uploadError } = await supabase.storage
    .from('personal-heroes')
    .upload(storagePath, imageBytes, { contentType: 'image/png', upsert: false })
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
      storage_path: storagePath, variant,
      prompt, gate_result: gateRaw
    })
    .select('id')
    .single()
  if (heroError) throw new Error(`Hero insert failed: ${heroError.message}`)

  // Delivery bookkeeping + free-tier accounting. The hero INSERT
  // above fired the version trigger, so the personal_cells row
  // for this cell exists. Mark the cell delivered, and charge
  // the free counter ONLY for a free_on_demand delivery to a
  // cell that has never been counted: the plan's allowance
  // counts genuinely new cells ("today's cell plus the next
  // four genuinely new cells encountered"), and spec J4 rules
  // replacement regenerations consume no allowance — so
  // kind='regeneration' deliveries (photo replacement (b),
  // flagged hero (c)) never charge, and neither do subscriber
  // fill or replenishment deliveries (as before).
  const { data: cellRow } = await supabase
    .from('personal_cells')
    .select('free_counted')
    .eq('user_id', job.user_id)
    .eq('combination_id', job.combination_id)
    .maybeSingle()
  const alreadyCounted = cellRow?.free_counted === true
  const chargeFree = job.kind === 'free_on_demand' && !alreadyCounted
  if (chargeFree) {
    const { data: profile } = await supabase
      .from('personal_profiles')
      .select('free_generations_used')
      .eq('user_id', job.user_id)
      .single()
    await supabase
      .from('personal_profiles')
      .update({
        free_generations_used: (profile?.free_generations_used ?? 0) + 1,
        updated_at: new Date().toISOString()
      })
      .eq('user_id', job.user_id)
  }
  await supabase
    .from('personal_cells')
    .update({
      ever_delivered: true,
      free_counted: alreadyCounted || chargeFree,
      updated_at: new Date().toISOString()
    })
    .eq('user_id', job.user_id)
    .eq('combination_id', job.combination_id)

  await markJob(job.id, {
    status: 'COMPLETED', gate_result: gateRaw,
    result_hero_id: hero.id, completed_at: new Date().toISOString()
  })
  return { delivered: true, heroId: hero.id, failures: [], gateRaw }
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
    // Variant slot: cap enforced at write time.
    const { data: existing } = await supabase
      .from('personal_heroes')
      .select('variant')
      .eq('user_id', job.user_id)
      .eq('combination_id', job.combination_id)
    const used = new Set((existing ?? []).map(r => r.variant as number))
    if (used.size >= VARIANT_CAP) {
      await markJob(job.id, {
        status: 'FAILED', error_message: 'variant_cap_reached',
        completed_at: new Date().toISOString()
      })
      return { job_id: job.id, status: 'FAILED', error: 'variant_cap_reached' }
    }
    let variant = 1
    while (used.has(variant)) variant++

    // Reference photos -> signed URLs (private bucket).
    const { data: refs, error: refError } = await supabase
      .from('personal_reference_photos')
      .select('storage_path')
      .eq('user_id', job.user_id)
      .order('position', { ascending: true })
    if (refError) throw refError
    if (!refs || refs.length === 0) throw new Error('No reference photos registered')
    const refPaths = refs.map(r => r.storage_path as string)
    const { data: signed, error: signError } = await supabase.storage
      .from('personal-heroes')
      .createSignedUrls(refPaths, 3600)
    if (signError) throw signError
    const refUrls = (signed ?? []).map(s => s.signedUrl).filter(Boolean) as string[]
    if (refUrls.length === 0) throw new Error('Could not sign reference photo URLs')

    // First reference bytes for the identity composite.
    const { data: refBlob, error: dlError } = await supabase.storage
      .from('personal-heroes')
      .download(refPaths[0])
    if (dlError || !refBlob) throw new Error(`Reference download failed: ${dlError?.message}`)
    const refBytes = new Uint8Array(await refBlob.arrayBuffer())

    let failures: string[] = []
    let gateRaw: Record<string, unknown> = {}
    let lastPrompt = ''
    let startAttempt = 1

    // --- Resume path (stale-claim recovery) ---
    // A reclaimed job carries the replicate_id of the generation its
    // terminated invocation had already completed and paid for (the
    // invocation died during that attempt's gate). Gate THAT image
    // before paying for a fresh generation. If the prediction's
    // output can no longer be retrieved (Replicate expires output
    // files ~1h after creation), fall through to a fresh restart.
    // The resumed gate counts as the attempt that produced the
    // image: if it fails and that was attempt 1, the loop below
    // still gets its attempt 2 with the failure classes fed back.
    if (job.replicate_id) {
      const resumeAttempt = Math.min(Math.max(job.attempts || 1, 1), 2)
      const resumedUrl = await fetchPredictionOutputUrl(job.replicate_id)
      let resumedBytes: Uint8Array | null = null
      if (resumedUrl) {
        try {
          const r = await fetch(resumedUrl)
          if (r.ok) resumedBytes = new Uint8Array(await r.arrayBuffer())
        } catch { /* output expired/unreachable -> fresh restart */ }
      }
      if (resumedUrl && resumedBytes) {
        console.log(`[personal-hero-worker] job ${job.id}: resuming gate on interrupted attempt ${resumeAttempt} (prediction ${job.replicate_id})`)
        const outcome = await gateAndDeliver(
          job, resumeAttempt, resumedUrl, resumedBytes, refBytes, variant, job.prompt ?? '')
        gateRaw = outcome.gateRaw
        if (outcome.delivered) {
          return {
            job_id: job.id, status: 'COMPLETED', hero_id: outcome.heroId,
            combination_id: job.combination_id, attempts: resumeAttempt, resumed: true
          }
        }
        failures = outcome.failures
        console.log(`[personal-hero-worker] job ${job.id} resumed attempt ${resumeAttempt} gate FAILED: ${failures.join('; ')}`)
        startAttempt = resumeAttempt + 1
      } else {
        console.log(`[personal-hero-worker] job ${job.id}: interrupted prediction ${job.replicate_id} no longer retrievable; restarting generation`)
      }
    }

    for (let attempt = startAttempt; attempt <= 2; attempt++) {
      lastPrompt = buildPrompt(job, failures)
      const { url, predictionId } = await generateImage(lastPrompt, refUrls)
      await markJob(job.id, { replicate_id: predictionId, attempts: attempt, prompt: lastPrompt })

      const imageResponse = await fetch(url)
      if (!imageResponse.ok) throw new Error(`Generated image download failed: ${imageResponse.status}`)
      const imageBytes = new Uint8Array(await imageResponse.arrayBuffer())

      const outcome = await gateAndDeliver(job, attempt, url, imageBytes, refBytes, variant, lastPrompt)
      gateRaw = outcome.gateRaw
      if (outcome.delivered) {
        return {
          job_id: job.id, status: 'COMPLETED', hero_id: outcome.heroId,
          combination_id: job.combination_id, attempts: attempt
        }
      }
      failures = outcome.failures
      console.log(`[personal-hero-worker] job ${job.id} attempt ${attempt} gate FAILED: ${failures.join('; ')}`)
    }

    // Both attempts failed the gate: store nothing, pool keeps serving.
    await markJob(job.id, {
      status: 'FAILED', gate_result: gateRaw,
      error_message: `gate_failed: ${failures.join('; ')}`,
      completed_at: new Date().toISOString()
    })
    return { job_id: job.id, status: 'FAILED', error: `gate_failed: ${failures.join('; ')}` }
  } catch (e) {
    await markJob(job.id, {
      status: 'FAILED', error_message: (e as Error).message,
      completed_at: new Date().toISOString()
    })
    return { job_id: job.id, status: 'FAILED', error: (e as Error).message }
  }
}

// Stale-claim recovery: find jobs whose PROCESSING claim is older
// than STALE_PROCESSING_MS. Every in-code failure path marks a
// claimed job FAILED, so a job still PROCESSING past the threshold
// means its invocation was terminated by the platform mid-processing
// — without this scan it would wedge its cell forever (the request
// action answers already_queued against a PROCESSING job).
//
// Each stale job spends one reclaim from its reclaim_count budget
// and returns to QUEUED (it keeps its replicate_id/attempts so the
// resume path in processJob can gate the already-generated image
// first). At the budget cap the job is marked FAILED with the named
// stale_processing_timeout reason instead of requeuing forever.
// reclaim_count is the budget counter — NOT the attempts column,
// whose established meaning is the generation attempt (1..2) within
// the current processing, written on every generation and used by
// the resume path to identify which completed generation
// replicate_id belongs to; spending from it would corrupt that
// bookkeeping (a job killed during attempt 1 would resume as though
// on attempt 2). All transitions are guarded on the exact state
// read (status + claim age + budget) so concurrent invocations
// cannot double-reclaim the same job.
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

serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'Method not allowed' }, 405)

    // Service-role only: this function spends money and writes heroes.
    // Accept any legitimate service credential, not just a raw string
    // match on the env key: the gateway (verify_jwt) has already
    // validated the credential, and the project's other service keys
    // (e.g. the Vault-held key pg_cron sends, the Management API key)
    // are legacy JWTs whose role claim is service_role.
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

    // Stale-claim recovery first: reclaimed jobs re-enter through
    // the normal QUEUED selection below, in the same invocation.
    const reclaimed = await reclaimStaleJobs()

    const { data: jobs, error } = await supabase
      .from('personal_generation_jobs')
      .select('id, user_id, combination_id, gender, weather, temp_bracket, time_of_day, outfit_signature, outfit_items, kind, attempts, replicate_id, prompt, reclaim_count')
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
