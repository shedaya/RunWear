// Supabase Edge Function: start-personal-fill (RW-14, 2026-10-31;
// RW-16.30 launch-set extension + convergence amendment, 2026-11-02)
// RW-151 amendment (v15): the fill no longer intersects the
// caller-named cells with this function's canonical launch table.
// The engine's outfit for a real forecast frame legitimately
// differs from the table's representative-frame outfit for the
// same (weather, bracket, daypart) — frame inputs the table's
// frames fix (wind, comfort adjustment, measured rain) move the
// prescription — so the intersection silently dropped exactly the
// cells the client most needed filled (RW-151 diagnosis,
// 2026-11-09: 12 of the owner's 44 banked cells exist only because
// the on-demand path accepted them as named). Named cells now
// enqueue AS NAMED, subject to the same validation
// request-personal-generation applies: the combination id must
// parse, and outfit_items must hash to the id's signature. The
// canonical table below is RETAINED for one purpose only: the
// scaffold guarantee (see SCAFFOLD CELLS).
//
// Subscriber fill under the CONVERGENCE model (Sol's architecture
// amendment, 2026-11-02 ~19:31 EST, restoring his original
// scaffolding design): the per-user bank is NOT pre-generated in
// full. This function enqueues the forecast-imminent cells —
// the combination_ids the client names from its 7-day forecast in
// the request body ("priority_cells") — AS NAMED (RW-151: the
// pre-v15 canonical-88 intersection silently dropped named cells
// whose engine outfit differs from the table's representative
// outfit; see the version note above), in the client's forecast
// order.
// Every other cell converges on first open: get-personal-hero
// serves the user's own same-prescription photo as a scaffold and
// queues the exact photo in the background; once generated it is
// banked for the selfie epoch and served forever after. A scaffold
// is never a stock photo for an entitled user (RW-16.20).
// Cells that already have a verified hero or an active job are
// skipped, so re-calling is harmless (e.g. a forecast refresh).
// Jobs are stamped with the user's current photo-set epoch
// (personal_profiles.photo_epoch); a photo change supersedes
// unprocessed prior-epoch jobs in the worker, never another user's.
//
// Callers: the entitled user themself (user JWT, no body user_id), or
// the service role with { "user_id": "..." } (the billing lane calls
// it after set_personal_entitlement flips a user to entitled).
// Non-entitled users are refused — the fill is a subscriber benefit.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

// RW-151: caller-named cell validation — the same vocabulary and
// rules request-personal-generation applies to its request action,
// so the fill path accepts exactly the cells the on-demand path
// accepts, and no others. outfitHash is that function's exported
// hash verbatim (the engine's String.hashCode mirror).
const GENDERS = ['MALE', 'FEMALE', 'UNISEX']
const WEATHERS = ['CLEAR', 'CLOUDY', 'RAIN', 'SNOW']
const TEMP_BRACKETS = ['FREEZING', 'COLD', 'COOL', 'MILD', 'WARM', 'HOT']
const TIMES_OF_DAY = ['DAWN', 'MIDDAY', 'DUSK', 'NIGHT']

function outfitHash(itemKeys: string[]): string {
  const joined = [...itemKeys].sort().join('-')
  let h = 0
  for (let i = 0; i < joined.length; i++) {
    h = (Math.imul(h, 31) + joined.charCodeAt(i)) | 0
  }
  return (h >>> 0).toString(16).slice(-8).padStart(8, '0')
}

type NamedCell = {
  gender: string, weather: string, tempBracket: string,
  timeOfDay: string, signature: string, combinationId: string
}

function parseCombinationId(raw: unknown): NamedCell | null {
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

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const PRIORITY_FORECAST = 5
// Gen-6 Wave A queue fix (2026-11-05, RW-38/DA-02): the FIRST cell
// the caller names in priority_cells is the current/imminent cell —
// the one on the user's screen. It is stamped with the top
// current-cell priority class (1), which the worker's effective
// claim band ranks strictly ahead of on-demand (20) and of the
// forecast fill below (5). The kind stays 'subscriber_fill' (the
// jobs kind CHECK admits no new kind); the class rides on
// priority, exactly as convergence (15) already does.
const PRIORITY_CURRENT_CELL = 1

// The pre-RW-16.30 core matrix (20 cells) keeps the established
// priority; the launch-set extension drains behind it.
function isCoreCell(c: { weather: string, bracket: string, time: string }): boolean {
  if (c.weather === 'CLEAR' && (c.time === 'DAWN' || c.time === 'DUSK')) return true
  if (c.weather === 'RAIN' && c.time === 'DAWN') return true
  if (c.weather === 'CLEAR' && c.time === 'MIDDAY' &&
      (c.bracket === 'WARM' || c.bracket === 'HOT')) return true
  return false
}

// Canonical SCAFFOLD cells — DERIVED FROM ENGINE v4.3 (RW-16.23;
// launch set extended RW-16.30), never hand-mirrored. RW-151:
// since v15 this table no longer gates the caller-named fill. Its
// one remaining job is the scaffold guarantee the client composes
// into every fill request: the 20 core-matrix cells are a subset
// of this table at these exact outfits, so they resolve here and
// keep filling at the core's established priority (isCoreCell)
// behind the forecast cells. Each cell's items are
// the exact allItems output of
// GetOutfitRecommendationUseCase.recommendFrames at the cell's
// representative frame:
//   comfort NEUTRAL (adjusted == raw); calm (no wind shell);
//   RAIN measured rain, precipitation 1.0mm; CLOUDY and SNOW take
//   the dry frame (the engine reads measured rain only — snow cells
//   use the dry rules, the refill census discipline);
//   UV 2 at DAWN/DUSK, 4 at MIDDAY, 1 in RAIN (0 at NIGHT);
//   hour 8 DAWN / 13 MIDDAY / 18 DUSK / 22 NIGHT;
//   adjusted temps FREEZING 12F, COLD 27F, COOL 42F, MILD 57F,
//   WARM 72F, HOT 85F.
// WARM/HOT x SNOW cells are omitted: verified unreachable (no
// forecast state pairs a snow code with a >=65F feels-like; the
// pool census scope note, RW-24b refill).
// The pre-RW-16.23 table mirrored the legacy pool prompts instead and
// 10 of its 12 outfits were outfits the engine cannot produce at any
// frame, so fill heroes were unservable on client cells even when
// they passed the gate. Drift guard: the JVM test
// FillCanonicalOutfitsTest (RunWear-android/shared) replays every
// representative frame through the engine and parses the block below
// out of this file — if the engine tables or this block change
// without each other, that test fails. Update both together. The
// 88-cell extension was generated by an engine mirror verified to
// reproduce the established 20 cells exactly before extending
// (gen_canonical_cells.py, RW-16.30 build record).
// BEGIN GENERATED CANONICAL CELLS
const CANONICAL_CELLS: Array<{ weather: string, bracket: string, time: string, items: string[] }> = [
  { weather: 'CLEAR', bracket: 'FREEZING', time: 'DAWN', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'FREEZING', time: 'MIDDAY', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'SUNGLASSES', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'FREEZING', time: 'DUSK', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'FREEZING', time: 'NIGHT', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'CLEAR', bracket: 'COLD', time: 'DAWN', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'COLD', time: 'MIDDAY', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'SUNGLASSES', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'COLD', time: 'DUSK', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'CLEAR', bracket: 'COLD', time: 'NIGHT', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'CLEAR', bracket: 'COOL', time: 'DAWN', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'CLEAR', bracket: 'COOL', time: 'MIDDAY', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'SUNGLASSES'] },
  { weather: 'CLEAR', bracket: 'COOL', time: 'DUSK', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'CLEAR', bracket: 'COOL', time: 'NIGHT', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'REFLECTIVE_GEAR'] },
  { weather: 'CLEAR', bracket: 'MILD', time: 'DAWN', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'MILD', time: 'MIDDAY', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES'] },
  { weather: 'CLEAR', bracket: 'MILD', time: 'DUSK', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'MILD', time: 'NIGHT', items: ['SHORT_SLEEVE', 'SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'CLEAR', bracket: 'WARM', time: 'DAWN', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'WARM', time: 'MIDDAY', items: ['VISOR', 'SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES'] },
  { weather: 'CLEAR', bracket: 'WARM', time: 'DUSK', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLEAR', bracket: 'WARM', time: 'NIGHT', items: ['SHORT_SLEEVE', 'SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'CLEAR', bracket: 'HOT', time: 'DAWN', items: ['TANK_TOP', 'SHORT_SHORTS'] },
  { weather: 'CLEAR', bracket: 'HOT', time: 'MIDDAY', items: ['VISOR', 'TANK_TOP', 'SHORT_SHORTS', 'SUNGLASSES'] },
  { weather: 'CLEAR', bracket: 'HOT', time: 'DUSK', items: ['TANK_TOP', 'SHORT_SHORTS'] },
  { weather: 'CLEAR', bracket: 'HOT', time: 'NIGHT', items: ['TANK_TOP', 'SHORT_SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'CLOUDY', bracket: 'FREEZING', time: 'DAWN', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'CLOUDY', bracket: 'FREEZING', time: 'MIDDAY', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'SUNGLASSES', 'NECK_GAITER'] },
  { weather: 'CLOUDY', bracket: 'FREEZING', time: 'DUSK', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'CLOUDY', bracket: 'FREEZING', time: 'NIGHT', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'CLOUDY', bracket: 'COLD', time: 'DAWN', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'CLOUDY', bracket: 'COLD', time: 'MIDDAY', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'SUNGLASSES', 'NECK_GAITER'] },
  { weather: 'CLOUDY', bracket: 'COLD', time: 'DUSK', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'CLOUDY', bracket: 'COLD', time: 'NIGHT', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'CLOUDY', bracket: 'COOL', time: 'DAWN', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'CLOUDY', bracket: 'COOL', time: 'MIDDAY', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'SUNGLASSES'] },
  { weather: 'CLOUDY', bracket: 'COOL', time: 'DUSK', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'CLOUDY', bracket: 'COOL', time: 'NIGHT', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'REFLECTIVE_GEAR'] },
  { weather: 'CLOUDY', bracket: 'MILD', time: 'DAWN', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLOUDY', bracket: 'MILD', time: 'MIDDAY', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES'] },
  { weather: 'CLOUDY', bracket: 'MILD', time: 'DUSK', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLOUDY', bracket: 'MILD', time: 'NIGHT', items: ['SHORT_SLEEVE', 'SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'CLOUDY', bracket: 'WARM', time: 'DAWN', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLOUDY', bracket: 'WARM', time: 'MIDDAY', items: ['VISOR', 'SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES'] },
  { weather: 'CLOUDY', bracket: 'WARM', time: 'DUSK', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'CLOUDY', bracket: 'WARM', time: 'NIGHT', items: ['SHORT_SLEEVE', 'SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'CLOUDY', bracket: 'HOT', time: 'DAWN', items: ['TANK_TOP', 'SHORT_SHORTS'] },
  { weather: 'CLOUDY', bracket: 'HOT', time: 'MIDDAY', items: ['VISOR', 'TANK_TOP', 'SHORT_SHORTS', 'SUNGLASSES'] },
  { weather: 'CLOUDY', bracket: 'HOT', time: 'DUSK', items: ['TANK_TOP', 'SHORT_SHORTS'] },
  { weather: 'CLOUDY', bracket: 'HOT', time: 'NIGHT', items: ['TANK_TOP', 'SHORT_SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'RAIN', bracket: 'FREEZING', time: 'DAWN', items: ['BASEBALL_CAP', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'FREEZING', time: 'MIDDAY', items: ['BASEBALL_CAP', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'FREEZING', time: 'DUSK', items: ['BASEBALL_CAP', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'FREEZING', time: 'NIGHT', items: ['BASEBALL_CAP', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'RAIN', bracket: 'COLD', time: 'DAWN', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'COLD', time: 'MIDDAY', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'COLD', time: 'DUSK', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'RAIN', bracket: 'COLD', time: 'NIGHT', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'RAIN_JACKET', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'RAIN', bracket: 'COOL', time: 'DAWN', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'LIGHT_TIGHTS'] },
  { weather: 'RAIN', bracket: 'COOL', time: 'MIDDAY', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'LIGHT_TIGHTS'] },
  { weather: 'RAIN', bracket: 'COOL', time: 'DUSK', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'LIGHT_TIGHTS'] },
  { weather: 'RAIN', bracket: 'COOL', time: 'NIGHT', items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'LIGHT_TIGHTS', 'REFLECTIVE_GEAR'] },
  { weather: 'RAIN', bracket: 'MILD', time: 'DAWN', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'MILD', time: 'MIDDAY', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'MILD', time: 'DUSK', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'MILD', time: 'NIGHT', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'RAIN', bracket: 'WARM', time: 'DAWN', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'WARM', time: 'MIDDAY', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'WARM', time: 'DUSK', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS'] },
  { weather: 'RAIN', bracket: 'WARM', time: 'NIGHT', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'RAIN_JACKET', 'SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'RAIN', bracket: 'HOT', time: 'DAWN', items: ['BASEBALL_CAP', 'TANK_TOP', 'RAIN_JACKET', 'SHORT_SHORTS'] },
  { weather: 'RAIN', bracket: 'HOT', time: 'MIDDAY', items: ['BASEBALL_CAP', 'TANK_TOP', 'RAIN_JACKET', 'SHORT_SHORTS'] },
  { weather: 'RAIN', bracket: 'HOT', time: 'DUSK', items: ['BASEBALL_CAP', 'TANK_TOP', 'RAIN_JACKET', 'SHORT_SHORTS'] },
  { weather: 'RAIN', bracket: 'HOT', time: 'NIGHT', items: ['BASEBALL_CAP', 'TANK_TOP', 'RAIN_JACKET', 'SHORT_SHORTS', 'REFLECTIVE_GEAR'] },
  { weather: 'SNOW', bracket: 'FREEZING', time: 'DAWN', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'SNOW', bracket: 'FREEZING', time: 'MIDDAY', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'SUNGLASSES', 'NECK_GAITER'] },
  { weather: 'SNOW', bracket: 'FREEZING', time: 'DUSK', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER'] },
  { weather: 'SNOW', bracket: 'FREEZING', time: 'NIGHT', items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'FLEECE_PULLOVER', 'WINDBREAKER', 'THERMAL_TIGHTS', 'MITTENS', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'SNOW', bracket: 'COLD', time: 'DAWN', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'SNOW', bracket: 'COLD', time: 'MIDDAY', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'SUNGLASSES', 'NECK_GAITER'] },
  { weather: 'SNOW', bracket: 'COLD', time: 'DUSK', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER'] },
  { weather: 'SNOW', bracket: 'COLD', time: 'NIGHT', items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'NECK_GAITER', 'REFLECTIVE_GEAR'] },
  { weather: 'SNOW', bracket: 'COOL', time: 'DAWN', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'SNOW', bracket: 'COOL', time: 'MIDDAY', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'SUNGLASSES'] },
  { weather: 'SNOW', bracket: 'COOL', time: 'DUSK', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] },
  { weather: 'SNOW', bracket: 'COOL', time: 'NIGHT', items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'REFLECTIVE_GEAR'] },
  { weather: 'SNOW', bracket: 'MILD', time: 'DAWN', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'SNOW', bracket: 'MILD', time: 'MIDDAY', items: ['BASEBALL_CAP', 'SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES'] },
  { weather: 'SNOW', bracket: 'MILD', time: 'DUSK', items: ['SHORT_SLEEVE', 'SHORTS'] },
  { weather: 'SNOW', bracket: 'MILD', time: 'NIGHT', items: ['SHORT_SLEEVE', 'SHORTS', 'REFLECTIVE_GEAR'] },
]
// END GENERATED CANONICAL CELLS

function matrixCells(gender: string): Array<Record<string, unknown>> {
  return CANONICAL_CELLS.map(c => {
    const signature = outfitHash(c.items)
    return {
      combination_id: `${gender}_${c.weather}_${c.bracket}_${c.time}_${signature}`,
      gender, weather: c.weather, temp_bracket: c.bracket, time_of_day: c.time,
      outfit_signature: signature, outfit_items: c.items,
      core: isCoreCell(c)
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

    // CONVERGENCE (Sol's architecture amendment, 2026-11-02 ~19:31
    // EST): the bank is NOT pre-generated in full. This fill enqueues
    // the forecast-imminent cells the client names from its 7-day
    // forecast, in the client's order; every other cell converges
    // on first open via get-personal-hero's scaffold + background
    // exact generation.
    //
    // RW-151 (v15): named cells resolve AS NAMED — there is no
    // canonical-table intersection. A named cell arriving WITH its
    // outfit items (the "id|ITEM,ITEM,..." form) is validated the
    // way request-personal-generation validates its request action:
    // the id must parse, and the items must hash to the id's
    // signature; only then can the worker fulfil it (the garment
    // gate judges the generated photo against the JOB's item list,
    // so a fill job must carry the outfit its id names). A named
    // cell arriving BARE (no items) resolves against the canonical
    // table exactly as v14 did — the client's composed core-matrix
    // cells arrive bare and are table cells at the table's outfits
    // by construction — and a bare id the table does not know is
    // ignored: its outfit cannot be known, and the cell still
    // converges on first open via the on-demand path, which carries
    // its items with it. Ids that fail to parse, and accompanied
    // ids whose items do not hash to the signature, are ignored —
    // a stale or hostile client still cannot inject cells.
    const cells = matrixCells(profile.gender as string)
    const cellByCombination = new Map(
      cells.filter((c, idx) => cells.findIndex(x => x.combination_id === c.combination_id) === idx)
        .map(c => [c.combination_id as string, c]))
    const epoch = (profile.photo_epoch as number | undefined) ?? 0

    type WantedCell = {
      combination_id: string, gender: string, weather: string,
      temp_bracket: string, time_of_day: string,
      outfit_signature: string, outfit_items: string[], core: boolean
    }
    const wanted: WantedCell[] = []
    const seenNamed = new Set<string>()
    const rawNamed = Array.isArray(body.priority_cells)
      ? (body.priority_cells as unknown[])
      : []
    for (const rawEntry of rawNamed) {
      if (typeof rawEntry !== 'string') continue
      const sep = rawEntry.indexOf('|')
      const idPart = sep >= 0 ? rawEntry.slice(0, sep) : rawEntry
      if (seenNamed.has(idPart)) continue
      const parsed = parseCombinationId(idPart)
      if (!parsed) continue
      if (parsed.gender !== (profile.gender as string)) continue
      seenNamed.add(idPart)
      if (sep >= 0) {
        const items = rawEntry.slice(sep + 1).split(',')
          .map(s => s.trim()).filter(s => s.length > 0)
        if (items.length === 0) continue
        if (outfitHash(items) !== parsed.signature) continue
        wanted.push({
          combination_id: parsed.combinationId,
          gender: parsed.gender, weather: parsed.weather,
          temp_bracket: parsed.tempBracket,
          time_of_day: parsed.timeOfDay,
          outfit_signature: parsed.signature,
          outfit_items: items,
          core: isCoreCell({
            weather: parsed.weather, bracket: parsed.tempBracket,
            time: parsed.timeOfDay
          })
        })
      } else {
        const tableCell = cellByCombination.get(idPart)
        if (!tableCell) continue
        wanted.push(tableCell as unknown as WantedCell)
      }
    }
    const wantedIds = wanted.map(c => c.combination_id)

    const { data: existingHeroes } = await sb
      .from('personal_heroes')
      .select('combination_id')
      .eq('user_id', userId)
      .in('combination_id', wantedIds)
    const haveHero = new Set((existingHeroes ?? []).map(r => r.combination_id as string))

    const { data: activeJobs } = await sb
      .from('personal_generation_jobs')
      .select('combination_id, id, status, priority')
      .eq('user_id', userId)
      .in('combination_id', wantedIds)
      .in('status', ['QUEUED', 'PROCESSING'])
    const haveJob = new Set((activeJobs ?? []).map(r => r.combination_id as string))
    const activeJobByCell = new Map<string, { id: string, status: string, priority: number }>(
      (activeJobs ?? []).map(r => [
        r.combination_id as string,
        r as unknown as { id: string, status: string, priority: number }
      ])
    )

    // Gen-6 Wave A: the caller-named head of priority_cells is the
    // current cell. If its job is already QUEUED (e.g. an earlier
    // fill enqueued it at forecast priority), promote it in place
    // to the current-cell class instead of leaving it in line.
    let currentCellPromoted = false
    const headId = wanted.length > 0 ? wanted[0].combination_id : null
    if (headId && !haveHero.has(headId)) {
      const existing = activeJobByCell.get(headId)
      if (
        existing &&
        existing.status === 'QUEUED' &&
        existing.priority > PRIORITY_CURRENT_CELL
      ) {
        const { error: promoteError } = await sb
          .from('personal_generation_jobs')
          .update({ priority: PRIORITY_CURRENT_CELL })
          .eq('id', existing.id)
          .eq('status', 'QUEUED')
        if (promoteError) throw promoteError
        currentCellPromoted = true
      }
    }

    // Sequential single-row inserts, in forecast order: the worker
    // claims by (priority, requested_at) and a multi-row insert
    // stamps one transaction timestamp on every row, which would
    // scramble the forecast order.
    let enqueued = 0
    let enqueuedCurrentCell = false
    for (let idx = 0; idx < wanted.length; idx++) {
      const c = wanted[idx]
      const combinationId = c.combination_id
      if (haveHero.has(combinationId) || haveJob.has(combinationId)) continue
      const { core: _core, ...cell } = c
      const isCurrentCell = idx === 0
      const { error: insertError } = await sb
        .from('personal_generation_jobs')
        .insert({
          ...cell,
          user_id: userId,
          kind: 'subscriber_fill',
          priority: isCurrentCell ? PRIORITY_CURRENT_CELL : PRIORITY_FORECAST,
          epoch,
          status: 'QUEUED'
        })
      if (insertError) throw insertError
      enqueued++
      if (isCurrentCell) enqueuedCurrentCell = true
    }

    if (enqueued > 0 || currentCellPromoted) {
      const kick = fetch(`${SUPABASE_URL}/functions/v1/personal-hero-worker`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ drain: 3 })
      }).catch(e => console.error('[start-personal-fill] worker kick failed:', e))
      // @ts-ignore EdgeRuntime is a Supabase runtime global
      if (typeof EdgeRuntime !== 'undefined') { EdgeRuntime.waitUntil(kick) }
    }

    return jsonResponse({
      ok: true,
      user_id: userId,
      model: 'convergence',
      matrix_cells: cells.length,
      epoch,
      enqueued,
      enqueued_forecast: enqueued,
      current_cell: headId,
      current_cell_enqueued: enqueuedCurrentCell,
      current_cell_promoted: currentCellPromoted,
      skipped_existing_hero: haveHero.size,
      skipped_active_job: haveJob.size
    })
  } catch (error) {
    console.error('[start-personal-fill] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
