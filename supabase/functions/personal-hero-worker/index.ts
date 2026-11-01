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
// Stale-claim recovery (RW-15 remedy, 2026-10-31; threshold revised
// RW-16.12 Lane A, 2026-11-01): every in-code failure path marks a
// claimed job FAILED, so a job still PROCESSING long after its claim
// means the invocation itself was terminated by the platform
// mid-processing (observed live: a job claimed, its generation
// completed, the invocation killed during the gate — the job then
// wedged forever, because the drain only claimed QUEUED jobs and the
// request action answered already_queued against it). Each
// invocation therefore first scans for PROCESSING jobs whose claim
// is older than STALE_PROCESSING_MS and either requeues them
// (spending one reclaim from the job's reclaim_count budget) or, at
// the budget cap, fails them with the named stale_processing_timeout
// reason — every terminal state writes its reason. The scan runs at
// the top of every invocation regardless of job kind (free_on_demand,
// subscriber_fill, regeneration alike), and the personal-hero-drain
// pg_cron job invokes this function every 5 minutes, so recovery no
// longer depends on an unrelated client kick. A reclaimed job that
// carries a replicate_id resumes by gating the already-generated
// image before paying for a new one.
//
// Reference derivatives (RW-16.12 Lane A, 2026-11-01): the identity
// gate's composite works at 768 px tall, so it is fed a bounded
// derivative of the first reference (produced once per reference by
// the personal-ref-prep function and cached in storage), never the
// full-resolution original. Decoding a 3600x4800 original was the
// gate path's dominant memory spike and the wedge mechanism above:
// attempts died mid-decode and wrote nothing. Generation still uses
// the full-size signed reference URLs; only the composite input
// changed, so gate semantics are untouched.
//
// Two-stage redress (RW-16.28, 2026-11-01): the RW-16.28 diagnosis
// established that Kontext's minimal-edit prior preserves whatever
// the reference photos wear — every delivered hero prescribing a
// sleeved top wore the reference's dress shirt (8 of 8), because
// stage-2 generation starts from the street-clothes portrait.
// Stage 1 therefore runs ONE Kontext edit of the first reference
// that redresses the subject into plain athletic kit, full-body,
// and caches the result as a derived reference (in the refs folder,
// so delete-my-photos' prefix sweep removes it with the rest of
// the user's photos). Stage 2 then leads its input images with
// that derived reference, so preservation works for the outfit
// instead of against it. The derived reference is gated BEFORE
// caching by the same identity composite that gates heroes
// (derived vs the ORIGINAL first reference, same threshold): a
// derived reference that fails identity is never cached and never
// used, and any stage-1 failure falls back to the pre-RW-16.28
// single-stage inputs for that job — stage 1 can never block
// generation. The stage-2 identity gate is unchanged and stays
// pinned to the original first reference, never to the synthetic
// one, so the identity standard cannot drift. Cache freshness
// mirrors the reference-derivative rule: the derived object is
// fresh iff it was written no earlier than EVERY current reference
// source object (register_references replaces sources in place,
// bumping updated_at; an added reference carries a fresh
// updated_at of its own), so any photo change rebuilds it.
//
// Regeneration swap retirement (RW-16.12 Lane C spec, implemented by
// Lane A): under keep-serving, a kind='regeneration' job whose cell
// already holds VARIANT_CAP heroes retires the cell's
// prior-generation heroes (created before the cell's
// regen_pending_since) inside gateAndDeliver — after the gate
// passes, before the insert — so the replacement lands in the
// lowest freed slot. If the gate rejects, nothing is retired and
// the prior heroes keep serving.
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
// Stale-claim threshold: 5 minutes — ~7x the top of the
// field-measured healthy envelope (completed jobs run 17–43 s; the
// slowest observed legitimate job, a resume-gate followed by a
// fresh attempt, took ~78 s end to end, and two full attempts at
// typical timings — generation ~5–9 s, gate ~40–80 s — bound at
// ~3 minutes). 300 s therefore sits beyond any live invocation's
// plausible duration, so a reclaim cannot race a worker that is
// still legitimately processing (and the guarded claim plus the
// replicate_id resume bound the damage even in the pathological
// case: a reclaimed job gates its already-generated image before
// paying for a new one). The polls' hard caps (120 s generation,
// 180 s gate) are timeouts that throw, not extensions of legitimate
// processing. The previous 15-minute threshold left the RW-16.12
// COOL DUSK cell blocked for a quarter hour per wedge cycle; at
// 5 minutes plus the 5-minute drain cadence, worst-case recovery
// from a wedge is ~5–10 minutes from the claim. RW-16.25: the
// per-attempt markJob in the generation loop also refreshes
// started_at (a heartbeat), because a layered bracket's
// five-attempt job can now legitimately run past 300 s; a live job
// crosses an attempt boundary — and re-stamps its claim — at field
// timings of ~90 s or less per attempt, so the threshold still
// separates live processing from platform termination.
const STALE_PROCESSING_MS = 300_000
// Reclaim budget per job: a job may be reclaimed at most this many
// times; the next stale scan after the budget is spent fails it with
// STALE_FAILURE_REASON. Bounds the worst case at 1 + 3 invocations
// per job against repeated platform terminations.
const STALE_MAX_RECLAIMS = 3
const STALE_FAILURE_REASON = 'stale_processing_timeout'

// Per-job generation-attempt budget (RW-16.25). Attempts within a
// job are NOT blind retries: attempt N+1 is prompted from the union
// of every prior gate verdict (the in-job loop below, plus cross-job
// seeding from the cell's latest FAILED record), so a larger budget
// compounds evidence instead of repeating samples. The layered
// brackets — FREEZING, COLD, COOL — prescribe multi-atom outfits
// whose joint event is rare per sample (RW-16.22/23: the two-atom
// COOL outfit landed once in ~28 cell attempts under the previous
// cap), so they get MAX_ATTEMPTS_LAYERED attempts per job; every
// other bracket keeps the established MAX_ATTEMPTS_DEFAULT. The
// budget lives only here, in the worker: neither enqueue path
// (request-personal-generation, start-personal-fill) carries an
// attempt policy, so on-demand and fill jobs share this rule by
// construction.
const MAX_ATTEMPTS_DEFAULT = 2
const MAX_ATTEMPTS_LAYERED = 5
const LAYERED_BRACKETS: ReadonlySet<string> = new Set(['FREEZING', 'COLD', 'COOL'])

function maxAttemptsFor(job: JobRow): number {
  return LAYERED_BRACKETS.has(job.temp_bracket)
    ? MAX_ATTEMPTS_LAYERED
    : MAX_ATTEMPTS_DEFAULT
}

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

// Body-anchored visual restatements of the outfit's discriminating
// garments (RW-16.13). The verification gate judges garment CLASSES
// (sleeve length, bottom coverage, outer layer), and the field
// evidence showed the model (a) preserving the reference photos'
// indoor clothing instead of redressing the runner and (b) returning
// torso-weighted frames in which below-waist garments never appear.
// The prompt therefore restates those classes in on-body visual
// terms. Generation-side only: nothing in the gate reads this.
function outfitAnchors(keys: string[]): string {
  const has = (k: string) => keys.includes(k)
  const parts: string[] = []
  if (has('TANK_TOP')) parts.push('the top is sleeveless, with bare shoulders and arms')
  else if (has('SHORT_SLEEVE')) parts.push("the top's short sleeves end above the elbow")
  else if (has('LONG_SLEEVE_LIGHT') || has('LONG_SLEEVE_THERMAL')) {
    parts.push("the top's long sleeves reach all the way to the wrists")
  }
  // The outer layer is restated by its specific construction: a
  // generic "jacket" anchor got the FREEZING cell's insulated jacket
  // rendered reliably, but the RAIN COLD cell's shell was repeatedly
  // rendered as (or read as) a plain shirt — a rain jacket's hood is
  // its discriminating visual feature.
  if (has('RAIN_JACKET')) {
    parts.push('a hooded waterproof rain jacket, zipped up, is worn as the outermost layer')
  } else if (has('INSULATED_JACKET')) {
    parts.push('a puffy insulated winter jacket, zipped up, is worn as the outermost layer')
  } else if (keys.some(k => ITEM_CATEGORY[k] === 'TOP_OUTER')) {
    parts.push('a zipped jacket is worn over the top as the outermost layer')
  }
  if (has('LIGHT_TIGHTS') || has('THERMAL_TIGHTS')) {
    parts.push('skin-tight athletic running tights (leggings) hug the legs completely, ' +
      'from the waist down to the ankles — the runner is not wearing shorts or loose ' +
      'trousers, and no part of the legs is bare')
  } else if (has('CAPRIS')) parts.push('three-quarter tights end just below the knee')
  else if (has('SHORTS') || has('SHORT_SHORTS')) parts.push('the shorts end above the knee')
  // Head and hands items were dropped wholesale in the RW-16.13
  // subset's first round (named in the item list, absent from the
  // images), so they get the same on-body restatement.
  if (has('BALACLAVA')) parts.push('a balaclava covers the whole head and face except the eyes')
  else if (has('LIGHT_BEANIE') || has('THERMAL_BEANIE')) {
    parts.push('a knit winter beanie hat covers the head and ears (a beanie, not a baseball cap)')
  }
  else if (has('HEADBAND')) parts.push('an ear-warmer headband covers the ears')
  else if (has('VISOR')) parts.push('a visor shades the eyes')
  else if (has('BASEBALL_CAP')) parts.push('a running cap is worn on the head')
  if (has('MITTENS')) parts.push('mittens cover both hands completely — no bare hands or fingers are visible')
  else if (has('LIGHT_GLOVES') || has('THERMAL_GLOVES')) {
    parts.push('gloves cover both hands completely — no bare hands or fingers are visible')
  }
  if (has('NECK_GAITER')) parts.push('a neck gaiter covers the neck and lower face')
  if (has('SUNGLASSES')) parts.push('sunglasses cover the eyes')
  // Dusk outfits pair reflective gear with the base top; the model's
  // canonical dusk-runner image is a tee under a hi-viz vest, which
  // silently replaces a long-sleeved base (COOL DUSK, 5 of 6
  // generations). State the layering explicitly for that pairing.
  if (has('REFLECTIVE_GEAR')) {
    parts.push(has('LONG_SLEEVE_LIGHT') || has('LONG_SLEEVE_THERMAL')
      ? 'a thin reflective sash is worn over the long-sleeved shirt, whose full-length ' +
        'sleeves stay visible on the arms down to the wrists'
      : 'reflective strips on the clothing catch the light')
  }
  return parts.length ? ` In the photo: ${parts.join('; ')}.` : ''
}

// Imperative corrections derived from gate failure classes (RW-16.22).
// The v16 feedback appended the gate's raw class strings ("missing_
// required: bottom (expected tights, saw none)") and the model kept
// failing on the same classes — the classes are written for the gate's
// log, not as garment instructions. Translate each recurring class
// into a mandatory/forbidden clause naming the exact garment. This
// only ever fires on a retry (in-job attempt 2, or a re-queued job
// seeded with the cell's prior verdict), so first attempts — the ones
// the delivering families pass on — are untouched.
function correctionClauses(failures: string[], keys: string[]): string[] {
  const has = (k: string) => keys.includes(k)
  const outerDesc = has('RAIN_JACKET') ? 'a hooded waterproof rain jacket'
    : has('INSULATED_JACKET') ? 'a puffy insulated winter jacket'
    : has('LIGHT_JACKET') ? 'a lightweight zip-up running jacket'
    : has('WINDBREAKER') ? 'a lightweight windbreaker jacket'
    : has('LIGHT_VEST') ? 'a light running vest' : 'the jacket'
  const clauses: string[] = []
  for (const f of failures) {
    if (f.startsWith('missing_required: bottom')) {
      if (f.includes('expected tights')) {
        clauses.push('LEGS: the runner MUST wear skin-tight full-length running tights ' +
          '(leggings) covering both legs completely from the waist to the ankles — ' +
          'shorts are FORBIDDEN, capris are FORBIDDEN, and no bare skin may show on the legs')
      } else if (f.includes('expected capris')) {
        clauses.push('LEGS: the runner MUST wear three-quarter tights ending just below ' +
          'the knee — shorts and full-length trousers are FORBIDDEN')
      } else if (f.includes('expected shorts') || f.includes('expected short_shorts')) {
        clauses.push('LEGS: the runner MUST wear running shorts ending above the knee — ' +
          'full-length tights are FORBIDDEN')
      }
    } else if (f.startsWith('missing_required: outer layer') &&
               keys.some(k => ITEM_CATEGORY[k] === 'TOP_OUTER')) {
      clauses.push(`OUTER LAYER: the runner MUST wear ${outerDesc}, zipped up, as the ` +
        'outermost layer over the shirt — the previous attempt left the jacket out; ' +
        'it is mandatory, not optional')
    } else if (f.startsWith('missing_required: top')) {
      if (f.includes('expected long sleeve')) {
        clauses.push('SLEEVES: the shirt MUST have full-length sleeves reaching all the ' +
          'way to the wrists — a short-sleeve shirt is FORBIDDEN and a sleeveless top is FORBIDDEN')
      } else if (f.includes('expected short sleeve')) {
        clauses.push('SLEEVES: the shirt MUST have short sleeves ending above the elbow — ' +
          'long sleeves are FORBIDDEN')
      } else if (f.includes('expected sleeveless')) {
        clauses.push('TOP: the runner MUST wear a sleeveless tank top with bare shoulders ' +
          'and arms — sleeves of any length are FORBIDDEN')
      }
    } else if (f.startsWith('missing_required: head')) {
      if (f.includes('expected beanie')) {
        clauses.push('HEAD: the runner MUST wear a knit winter beanie covering the head ' +
          'and ears — a baseball cap is FORBIDDEN')
      } else if (f.includes('expected headband')) {
        clauses.push('HEAD: the runner MUST wear a wide ear-warmer headband covering the ears')
      } else if (f.includes('expected cap')) {
        clauses.push('HEAD: the runner MUST wear a running cap')
      } else if (f.includes('expected visor')) {
        clauses.push('HEAD: the runner MUST wear a visor')
      } else if (f.includes('expected balaclava')) {
        clauses.push('HEAD: the runner MUST wear a balaclava covering the whole head and ' +
          'face except the eyes')
      }
    } else if (f.startsWith('missing_required: hands')) {
      clauses.push('HANDS: gloves or mittens MUST cover both hands completely — bare ' +
        'hands and visible fingers are FORBIDDEN')
    } else if (f.startsWith('missing_required: sunglasses')) {
      clauses.push('EYES: the runner MUST wear sunglasses')
    } else if (f.startsWith('extra_items: jacket')) {
      clauses.push('OUTER LAYER: the runner must NOT wear any jacket, coat or vest in this photo')
    } else if (f === 'brand_logos_or_text') {
      clauses.push('LOGOS: every garment MUST be completely plain and unbranded — no ' +
        'swoosh, no stripe logo, no emblem, no printed word anywhere on the clothing')
    }
  }
  return clauses
}

// Itemized mandatory outfit block for the COOL bracket (RW-16.22).
// The COOL family is the generator's wall: 22 of 22 COOL jobs on the
// reference account failed the gate, always on the same classes —
// tights read as shorts/none, the light jacket dropped. The v16 flat
// item list plus anchor paragraph did not move it. For COOL jobs the
// outfit is therefore restated as one mandatory clause per garment in
// wearing order (outermost first, the visible state the gate judges),
// preceded by the cold-weather frame the model's shorts prior lacks,
// and closed with the outfit's explicit negatives. Non-COOL jobs keep
// the v16 segment byte-for-byte.
function coolOutfitBlock(keys: string[], poolComposition = false, derivedLead = false): string {
  const has = (k: string) => keys.includes(k)
  const hasOuter = keys.some(k => ITEM_CATEGORY[k] === 'TOP_OUTER')
  const lines: string[] = []
  if (has('RAIN_JACKET')) lines.push('a hooded waterproof rain jacket, zipped all the way up, worn as the outermost layer')
  else if (has('INSULATED_JACKET')) lines.push('a puffy insulated winter jacket, zipped all the way up, worn as the outermost layer')
  else if (has('LIGHT_JACKET')) lines.push('a lightweight zip-up running jacket with long sleeves and a stand-up collar, zipped all the way up, worn as the outermost layer over the shirt — the jacket is the most visible garment in the photo')
  else if (has('WINDBREAKER')) lines.push('a lightweight windbreaker jacket, zipped all the way up, worn as the outermost layer over the shirt')
  else if (has('LIGHT_VEST')) lines.push('a light running vest worn as the outermost layer over the shirt')
  if (has('QUARTER_ZIP')) lines.push('a quarter-zip pullover worn under the jacket')
  else if (has('FLEECE_PULLOVER')) lines.push('a fleece pullover worn under the jacket')
  else if (has('HALF_ZIP_THERMAL')) lines.push('a thermal half-zip worn under the jacket')
  if (has('LONG_SLEEVE_LIGHT') || has('LONG_SLEEVE_THERMAL')) {
    lines.push(hasOuter
      ? 'a long-sleeve running shirt underneath the jacket'
      : "a long-sleeve running shirt whose sleeves reach all the way to the wrists")
  } else if (has('SHORT_SLEEVE')) lines.push('a short-sleeve running shirt, its sleeves ending above the elbow')
  else if (has('TANK_TOP')) lines.push('a sleeveless tank top with bare shoulders and arms')
  if (has('LIGHT_TIGHTS') || has('THERMAL_TIGHTS')) {
    lines.push('skin-tight full-length running tights (leggings) covering both legs completely from the waist down to the ankles')
  } else if (has('CAPRIS')) lines.push('three-quarter tights ending just below the knee')
  else if (has('SHORTS')) lines.push('running shorts ending above the knee')
  else if (has('SHORT_SHORTS')) lines.push('short running shorts ending high on the thigh')
  if (has('BALACLAVA')) lines.push('a balaclava covering the whole head and face except the eyes')
  else if (has('LIGHT_BEANIE') || has('THERMAL_BEANIE')) lines.push('a knit winter beanie hat covering the head and ears (a beanie, not a baseball cap)')
  else if (has('HEADBAND')) lines.push('a wide ear-warmer headband covering the ears')
  else if (has('VISOR')) lines.push('a visor shading the eyes')
  else if (has('BASEBALL_CAP')) lines.push('a running cap on the head')
  if (has('MITTENS')) lines.push('mittens covering both hands completely, no bare fingers visible')
  else if (has('LIGHT_GLOVES') || has('THERMAL_GLOVES')) lines.push('gloves covering both hands completely, no bare fingers visible')
  if (has('NECK_GAITER')) lines.push('a neck gaiter covering the neck and lower face')
  if (has('SUNGLASSES')) lines.push('sunglasses covering the eyes')
  if (has('REFLECTIVE_GEAR')) {
    lines.push((has('LONG_SLEEVE_LIGHT') || has('LONG_SLEEVE_THERMAL'))
      ? 'a thin reflective sash worn over the top, the full-length sleeves staying visible down to the wrists'
      : 'reflective strips on the clothing catching the light')
  }
  const negatives: string[] = []
  if (has('LIGHT_TIGHTS') || has('THERMAL_TIGHTS')) negatives.push('shorts of any kind')
  if ((has('LONG_SLEEVE_LIGHT') || has('LONG_SLEEVE_THERMAL')) && !hasOuter) {
    negatives.push('a short-sleeve shirt', 'a sleeveless top')
  }
  let block = ''
  // Composition reference (RW-16.22 round 3): when the worker passes
  // a pool hero of this cell as the second input image, name the two
  // images' roles — the stock frame carries the outfit and the
  // full-body composition the model's edit prior otherwise drops.
  if (poolComposition && derivedLead) {
    // RW-16.28: with the derived athletic reference leading, the
    // pool hero is the THIRD input; the role sentence names all
    // three so the model is never told a person photo is stock.
    block += ` Three input images are given: the FIRST and SECOND both show the real ` +
      `person to depict — the first already wears plain athletic running kit — and ` +
      `the THIRD is a stock running photo: copy its outfit and its full-body ` +
      `composition exactly: the same garments, the same full-length framing with ` +
      `the whole body visible from head to shoes. The runner in the photo is the ` +
      `person from the FIRST image, not the stock runner.`
  } else if (poolComposition) {
    block += ` Two input images are given: the FIRST is the real person to depict; ` +
      `the SECOND is a stock running photo — copy its outfit and its full-body ` +
      `composition exactly: the same garments, the same full-length framing with ` +
      `the whole body visible from head to shoes. The runner in the photo is the ` +
      `person from the FIRST image, not the stock runner.`
  }
  block +=
    ` It is a chilly morning and the runner is dressed warmly for cold weather. ` +
    `The outfit — every item in this list is mandatory and must be clearly visible: ` +
    `${lines.join('; ')}. `
  if (negatives.length) block += `The runner is NOT wearing ${negatives.join(' or ')}. `
  // Composition (RW-16.22 round 2): the walled COOL generations came
  // back as waist-up portraits — the enumerator listed only a shirt
  // and a headband, no legs at all — so the frame itself is restated
  // here, against the v16 opening's "large in the frame" pull.
  block += `Composition: a wide full-length shot — the ENTIRE body is in the frame from ` +
    `the top of the head to the shoes, both full legs visible mid-stride; not a portrait, ` +
    `not a close-up, not cropped at the waist. `
  block += `Every item in that outfit list is visibly worn in the photo — nothing from the list is missing or substituted. `
  return block
}

function buildPrompt(job: JobRow, failureClasses: string[], poolComposition = false, derivedLead = false): string {
  // Layering order matters: Kontext drops outer layers when they are
  // listed flatly after the base layer (observed live: RAIN_JACKET
  // omitted twice when listed after LONG_SLEEVE). Name the outer layer
  // first and state the layering explicitly.
  const keys = job.outfit_items as string[]
  const order = ['TOP_OUTER', 'TOP_MID', 'TOP_BASE', 'BOTTOM', 'HEAD', 'HANDS', 'ACCESSORIES']
  const ordered = [...keys].sort(
    (a, b) => order.indexOf(ITEM_CATEGORY[a] ?? '') - order.indexOf(ITEM_CATEGORY[b] ?? ''))
  // Prompt-side display overrides (RW-16.13): the shared display
  // names are UI labels; two of them mislead the generator. "Light
  // Long Sleeve" was repeatedly rendered as a short-sleeve tee
  // (4 of 5 generations on the COOL DUSK cell), so the long-sleeve
  // tops are named in the prompt by their construction instead.
  const PROMPT_DISPLAY: Record<string, string> = {
    LONG_SLEEVE_LIGHT: 'Long-Sleeve Running Shirt (thin, full-length sleeves)',
    LONG_SLEEVE_THERMAL: 'Thermal Long-Sleeve Running Shirt (full-length sleeves)'
  }
  const items = ordered.map(k => PROMPT_DISPLAY[k] ?? ITEM_DISPLAY[k] ?? k)
  const outerKey = keys.find(k => ITEM_CATEGORY[k] === 'TOP_OUTER')
  const baseKey = keys.find(k => ITEM_CATEGORY[k] === 'TOP_BASE')
  const layering = outerKey && baseKey
    ? ` The ${ITEM_DISPLAY[outerKey]} is the outermost layer, worn over the ${PROMPT_DISPLAY[baseKey] ?? ITEM_DISPLAY[baseKey]}.`
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
  // RW-16.22: COOL-bracket jobs use the itemized mandatory outfit
  // block (coolOutfitBlock); every other bracket keeps the v16
  // segment below byte-for-byte.
  const outfitSegment = job.temp_bracket === 'COOL'
    ? coolOutfitBlock(keys, poolComposition, derivedLead)
    : ` They are wearing exactly this running outfit and nothing extra: ` +
      `${items.join(', ')}.${layering}${outfitAnchors(keys)} Every item in that outfit list is ` +
      `visibly worn in the photo — nothing from the list is missing or substituted. `
  let prompt =
    `Photorealistic full-body photo of the same person shown in the reference photos, ` +
    `now running mid-stride along ${background}, ${weatherPhrase[job.weather] ?? 'clear skies'}, ` +
    `${timePhrase[job.time_of_day] ?? 'daylight'}. The runner's whole body is in the frame, ` +
    `from the top of the head to the shoes, legs and feet included, seen from the front ` +
    `and large in the frame. ` +
    `The reference photos show this person in indoor clothes: replace that clothing completely — ` +
    `do not keep any garment from the reference photos.` +
    outfitSegment +
    `The runner is looking ahead, not at the camera. ` +
    `No visible brand logos, text, or trademarks anywhere in the image: every garment is plain, ` +
    `solid and unbranded — no swooshes, emblems, printed words or brand marks.` +
    QUALITY_SUFFIX
  if (failureClasses.length > 0) {
    prompt += ` IMPORTANT: a previous attempt at this photo failed verification because of: ` +
      `${failureClasses.join('; ')}. Fix exactly these problems.`
    // RW-16.22: the same verdicts restated as garment imperatives —
    // the raw class strings alone did not move the model (22 of 22
    // COOL jobs failed on the classes they name).
    const clauses = correctionClauses(failureClasses, keys)
    if (clauses.length) prompt += ` ${clauses.join('. ')}.`
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

// --- identity composite (shared by the gate and RW-16.28 stage 1) ---
// LEFT: the reference derivative; RIGHT: the candidate image, both at
// 768 px tall, JPEG 80. The stage-1 redress gate runs this exact
// composite — same instrument, same prompt, same threshold
// (IDENTITY_MIN_CONFIDENCE) — against the stage-1 output before the
// derived reference may be cached.
const IDENTITY_PROMPT =
  `The LEFT half of this image is a reference photo of a real person. ` +
  `The RIGHT half is an AI-generated photo that should depict the SAME person. ` +
  `Compare facial structure, hair, eyebrows, eyes, nose and mouth. ` +
  `Answer with ONLY a JSON object: {"same_person": true or false, "confidence": a number from 0.0 to 1.0}`

async function buildIdentityCompositeUri(refBytes: Uint8Array, imageBytes: Uint8Array): Promise<string> {
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
  return `data:image/jpeg;base64,${btoa(binary)}`
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
    compositeUri = await buildIdentityCompositeUri(refBytes, imageBytes)
  } catch (e) {
    compositeError = (e as Error).message
  }

  const [garmentText, identityText] = await Promise.all([
    callVision(imageUrl, garmentPrompt),
    compositeUri ? callVision(compositeUri, IDENTITY_PROMPT)
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

// --- Reference derivatives (RW-16.12 Lane A) -------------------------
// The identity composite only ever needs the first reference at
// 768 px tall (runGate's H). personal-ref-prep produces that
// derivative once per reference and caches it in storage at
// <uid>/refs-small/<name>.jpg; the worker downloads the derivative
// for the composite instead of decoding the 17.3 MP original in this
// isolate (the wedge mechanism — see the header). A derivative is
// fresh iff it exists and was written no earlier than its source
// object (register_references replaces sources in place, which
// bumps their updated_at and so invalidates the cache naturally).

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

// Returns the composite-grade bytes for the reference at sourcePath,
// producing the derivative via personal-ref-prep when it is missing
// or stale. Throws a named reference_prep_failed error when the
// derivative cannot be produced — the job then fails with that
// reason recorded (and the stale-claim reclaim gives later retries
// further chances) instead of silently falling back to decoding the
// full-resolution original in this isolate.
async function loadCompositeRefBytes(sourcePath: string): Promise<Uint8Array> {
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

// --- Two-stage redress: stage 1 (RW-16.28) ---------------------------
// One Kontext edit of the first reference: the same person, full-body,
// redressed into plain athletic kit. The output is identity-gated
// against the ORIGINAL first reference (the composite above) before
// it is cached; on any failure — gate, generation, storage — this
// returns null and the job proceeds with the pre-RW-16.28 inputs.
// The stage-1 prompt is deliberately a portrait instruction, not a
// running scene: the derived image is a REFERENCE (the stage-2
// canvas), so it must read like the full-body athletic photo the
// RW-16.14 intake guidance asks for — head to shoes, legs and head
// visible, face fully visible, no headwear/sunglasses/gloves the
// stage-2 gate could read as extra items.
const STAGE1_PROMPT =
  `Edit this photo into a full-body reference portrait of the same person. ` +
  `Show the person's entire body in the frame, from the top of the head to the shoes, ` +
  `standing upright and facing the camera, arms relaxed at their sides, against a ` +
  `plain neutral background. Replace the person's clothing completely with a plain ` +
  `athletic running kit: a plain solid-color short-sleeve athletic t-shirt, plain ` +
  `solid-color running shorts ending above the knee, bare legs below the shorts, and ` +
  `plain running shoes. The person's face is fully visible: no sunglasses, no hat, ` +
  `no headwear of any kind. Keep the same face, the same hair and the same body — ` +
  `this must remain recognizably the same person, with natural anatomy and ` +
  `proportions. Photorealistic, sharp focus. No visible brand logos, text or ` +
  `trademarks anywhere in the image: every garment is plain, solid and unbranded.`
const DERIVED_REF_FILENAME = 'derived-athletic.png'

function derivedRefPathFor(firstRefPath: string): string {
  const dir = firstRefPath.slice(0, firstRefPath.lastIndexOf('/'))
  return `${dir}/${DERIVED_REF_FILENAME}`
}

// Fresh iff the derived object exists and was written no earlier
// than EVERY current reference source object — the same rule the
// composite derivatives use (derivativeIsFresh), widened from the
// first source to the whole set, since the derived reference stands
// for the set. All sources share the derived object's folder
// (<uid>/refs), so one listing answers it.
async function derivedRefIsFresh(refPaths: string[], derivedPath: string): Promise<boolean> {
  try {
    const dir = derivedPath.slice(0, derivedPath.lastIndexOf('/'))
    const { data: listing } = await supabase.storage
      .from('personal-heroes')
      .list(dir, { limit: 100 })
    if (!listing) return false
    const byName = new Map((listing as Array<{ name: string, updated_at?: string }>)
      .map(e => [e.name, e]))
    const derName = derivedPath.slice(derivedPath.lastIndexOf('/') + 1)
    const der = byName.get(derName)
    if (!der || !der.updated_at) return false
    const derTime = new Date(der.updated_at).getTime()
    for (const p of refPaths) {
      const src = byName.get(p.slice(p.lastIndexOf('/') + 1))
      if (!src || !src.updated_at) return false
      if (derTime < new Date(src.updated_at).getTime()) return false
    }
    return true
  } catch {
    return false
  }
}

async function signStoragePath(path: string): Promise<string | null> {
  const { data, error } = await supabase.storage
    .from('personal-heroes')
    .createSignedUrl(path, 3600)
  if (error || !data) return null
  return data.signedUrl
}

// Returns a signed URL for the user's cached derived reference,
// building (and identity-gating) it first when the cache is missing
// or stale. Returns null when stage 1 is unavailable for this job
// for ANY reason; the caller then uses the pre-RW-16.28 inputs.
async function ensureDerivedReference(
  job: JobRow, refPaths: string[], refUrls: string[], refBytes: Uint8Array
): Promise<string | null> {
  const derivedPath = derivedRefPathFor(refPaths[0])
  try {
    if (await derivedRefIsFresh(refPaths, derivedPath)) {
      const url = await signStoragePath(derivedPath)
      if (url) {
        console.log(`[personal-hero-worker] job ${job.id}: RW-16.28 stage 1 cache hit (${derivedPath})`)
        return url
      }
    }
    console.log(`[personal-hero-worker] job ${job.id}: RW-16.28 stage 1 building derived reference from ${refPaths[0]}`)
    const { url } = await generateImage(STAGE1_PROMPT, [refUrls[0]])
    const resp = await fetch(url)
    if (!resp.ok) throw new Error(`stage-1 output download failed: ${resp.status}`)
    const derivedBytes = new Uint8Array(await resp.arrayBuffer())
    // Stage-1 gate: the identity composite against the ORIGINAL
    // first reference's derivative (refBytes), same instrument and
    // threshold as the stage-2 identity gate. Fail-closed for the
    // cache (an unverified synthetic is never stored or used),
    // fail-open for the job (single-stage fallback below).
    const compositeUri = await buildIdentityCompositeUri(refBytes, derivedBytes)
    const identityText = await callVision(compositeUri, IDENTITY_PROMPT)
    const identity = parseJsonObject(identityText)
    const conf = identity && typeof identity.confidence === 'number'
      ? identity.confidence as number : null
    const pass = identity !== null && identity.same_person === true &&
      conf !== null && conf >= IDENTITY_MIN_CONFIDENCE
    if (!pass) {
      console.log(`[personal-hero-worker] job ${job.id}: RW-16.28 stage 1 identity gate FAILED (same_person=${identity?.same_person}, confidence=${conf}); falling back to single-stage inputs`)
      return null
    }
    const { error: uploadError } = await supabase.storage
      .from('personal-heroes')
      .upload(derivedPath, derivedBytes, { contentType: 'image/png', upsert: true })
    if (uploadError) throw new Error(`derived reference upload failed: ${uploadError.message}`)
    console.log(`[personal-hero-worker] job ${job.id}: RW-16.28 stage 1 derived reference cached at ${derivedPath} (identity confidence ${conf})`)
    return await signStoragePath(derivedPath)
  } catch (e) {
    console.log(`[personal-hero-worker] job ${job.id}: RW-16.28 stage 1 unavailable (${(e as Error).message}); falling back to single-stage inputs`)
    return null
  }
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

  // Regeneration swap retirement (RW-16.12 Lane C spec): the gate
  // has passed, so a regeneration whose cell is at the variant cap
  // retires the cell's prior-generation heroes — rows created before
  // the cell's regen_pending_since — together with their storage
  // objects, and the replacement takes the lowest freed slot.
  // Gate-first ordering is the ruling: had the gate rejected,
  // nothing would be retired and the prior heroes would keep
  // serving. If the cell is at cap with NO prior-generation heroes
  // to retire, the delivery fails as variant_cap_reached, exactly
  // as before.
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

  const storagePath = `${job.user_id}/heroes/${job.combination_id}/v${finalVariant}.png`
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
      storage_path: storagePath, variant: finalVariant,
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
    const maxAttempts = maxAttemptsFor(job)

    // Variant slot: cap enforced at write time.
    const { data: existing } = await supabase
      .from('personal_heroes')
      .select('variant')
      .eq('user_id', job.user_id)
      .eq('combination_id', job.combination_id)
    const used = new Set((existing ?? []).map(r => r.variant as number))
    // At-cap regeneration jobs are exempt from this early fail:
    // gateAndDeliver retires the cell's prior-generation heroes
    // after the gate passes and finalizes the variant slot there
    // (RW-16.12 Lane C). Every other kind fails fast, before any
    // generation spend.
    if (used.size >= VARIANT_CAP && job.kind !== 'regeneration') {
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

    // Composite-grade bytes of the first reference: the bounded
    // derivative produced by personal-ref-prep (RW-16.12 Lane A),
    // NOT the full-resolution original — decoding the original in
    // this isolate was the wedge mechanism. Generation below still
    // uses the original signed URLs, and the gate's composite
    // geometry is unchanged (it always worked at 768 px tall).
    const refBytes = await loadCompositeRefBytes(refPaths[0])

    let failures: string[] = []
    // The prompt's correction set (RW-16.22 round 3): the UNION of
    // every failure class this job has seen — the seeded prior verdict
    // plus each attempt's own — rather than only the latest sample's.
    // Field evidence: an attempt prompted with only the jacket clause
    // fixed nothing and re-lost the tights a sibling attempt had
    // landed; each attempt is an independent sample, so a class drops
    // out of the prompt only when a sample has actually passed it —
    // which ends the job. `failures` keeps its established meaning
    // (the LAST verdict, written to the job record); promptFailures
    // is prompt-side only.
    let promptFailures: string[] = []
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
    // image: if it fails, the loop below still gets the attempts
    // remaining under this job's budget with the failure classes
    // fed back.
    if (job.replicate_id) {
      const resumeAttempt = Math.min(Math.max(job.attempts || 1, 1), maxAttempts)
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
        promptFailures = [...failures]
        console.log(`[personal-hero-worker] job ${job.id} resumed attempt ${resumeAttempt} gate FAILED: ${failures.join('; ')}`)
        startAttempt = resumeAttempt + 1
      } else {
        console.log(`[personal-hero-worker] job ${job.id}: interrupted prediction ${job.replicate_id} no longer retrievable; restarting generation`)
      }
    }

    // --- Cross-job closed loop (RW-16.22) ---
    // A re-queued cell previously started every job blind: the only
    // failure feedback lived inside a single job's attempt 2, so the
    // subscriber fill re-ran this account's COOL DAWN cell four times
    // with four identical blind first attempts. When this job has no
    // fresher verdict of its own (the resume path above supplies one
    // when it gates an interrupted image), seed the loop with the
    // most recent gate verdict recorded for the same user +
    // combination, so attempt N+1 of the CELL — not just of the job —
    // is prompted from attempt N's gate record. Prompt-side only:
    // the gate and its thresholds are unchanged.
    if (failures.length === 0) {
      const { data: prior } = await supabase
        .from('personal_generation_jobs')
        .select('error_message')
        .eq('user_id', job.user_id)
        .eq('combination_id', job.combination_id)
        .eq('status', 'FAILED')
        .like('error_message', 'gate_failed:%')
        .neq('id', job.id)
        .order('completed_at', { ascending: false })
        .limit(1)
        .maybeSingle()
      const priorMsg = prior?.error_message as string | undefined
      if (priorMsg) {
        const seeded = priorMsg.replace(/^gate_failed:\s*/, '')
          .split('; ').map(s => s.trim()).filter(Boolean)
        if (seeded.length > 0) {
          failures = seeded
          promptFailures = [...seeded]
          console.log(`[personal-hero-worker] job ${job.id}: seeded attempt ${startAttempt} with the prior gate verdict for ${job.combination_id}: ${failures.join('; ')}`)
        }
      }
    }

    // Reference weighting for the walled bracket (RW-16.22 rounds
    // 2–3): the reference set is three upper-body indoor portraits,
    // and the COOL generations kept collapsing to the inputs'
    // portrait composition (waist-up frames with no legs for the
    // gate to see). For COOL jobs the generation inputs are the
    // user's first reference (the waist-up shot, the least tight
    // crop) plus — when the pool holds an approved hero for this
    // cell — that hero as a composition reference: it already
    // renders this cell's layered outfit in a full-body frame, the
    // two things the model's edit prior drops. The pool image is a
    // generation INPUT only; the output still has to pass the
    // unchanged gate, identity included (a frame that keeps the
    // stock runner's face fails closed). The identity gate's own
    // composite is unaffected — it uses the first reference either
    // way, and its field confidence runs 0.9+ against a 0.6 floor.
    // Two-stage redress (RW-16.28): build or reuse the derived
    // athletic reference and LEAD the generation inputs with it, so
    // the model's preservation prior starts from athletic kit. The
    // original references stay in the input set behind it (the
    // identity anchor in pixel form); the identity GATE is
    // unaffected — it composites against the original first
    // reference's derivative (refBytes), never the synthetic. When
    // stage 1 is unavailable (ensureDerivedReference returns null)
    // the input sets below are exactly the pre-RW-16.28 ones.
    const derivedUrl = await ensureDerivedReference(job, refPaths, refUrls, refBytes)

    let genRefUrls = refUrls
    let poolRefUrl: string | null = null
    if (job.temp_bracket === 'COOL') {
      genRefUrls = refUrls.slice(0, 1)
      const genders = job.gender === 'UNISEX' ? ['MALE', 'FEMALE'] : [job.gender]
      for (const g of genders) {
        const { data: poolRows } = await supabase
          .from('generated_images')
          .select('image_url')
          .like('combination_id', `${g}\_${job.weather}\_${job.temp_bracket}\_${job.time_of_day}\_%`)
          .eq('review_status', 'approved')
          .limit(1)
        if (poolRows && poolRows.length > 0 && poolRows[0].image_url) {
          poolRefUrl = poolRows[0].image_url as string
          break
        }
      }
      if (poolRefUrl) genRefUrls = [refUrls[0], poolRefUrl]
      if (derivedUrl) {
        genRefUrls = poolRefUrl
          ? [derivedUrl, refUrls[0], poolRefUrl]
          : [derivedUrl, refUrls[0]]
      }
    } else if (derivedUrl) {
      // Kontext takes three input images; the derived reference
      // leads and the first two original references follow (a
      // three-reference set's third portrait — a second street-
      // clothes anchor — yields its slot to the derived image).
      genRefUrls = [derivedUrl, ...refUrls].slice(0, 3)
    }

    for (let attempt = startAttempt; attempt <= maxAttempts; attempt++) {
      lastPrompt = buildPrompt(job, promptFailures, poolRefUrl !== null, derivedUrl !== null)
      const { url, predictionId } = await generateImage(lastPrompt, genRefUrls)
      // The started_at refresh is the claim heartbeat (RW-16.25,
      // see STALE_PROCESSING_MS): a five-attempt layered job can
      // legitimately outlive the stale threshold, and this stamp
      // is what proves the claim is still being worked.
      await markJob(job.id, {
        replicate_id: predictionId, attempts: attempt, prompt: lastPrompt,
        started_at: new Date().toISOString()
      })

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
      promptFailures = [...new Set([...promptFailures, ...outcome.failures])]
      console.log(`[personal-hero-worker] job ${job.id} attempt ${attempt} gate FAILED: ${failures.join('; ')}`)
    }

    // All attempts failed the gate: store nothing, pool keeps serving.
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
// whose established meaning is the generation attempt (1..5 on the
// layered brackets, else 1..2 — RW-16.25) within
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
