// RW-16.30 unit tests for the worker's pure logic: the Part-4 prompt
// builder, the recalibrated-gate port, the draw-budget policy, and
// anchor selection. Run: deno test supabase/functions/personal-hero-worker/
// (The gate port's fidelity against the Python instrument of record
// is additionally verified by the calibration harness in
// ~/workspace/release-prep/mim-production-work/ — these tests pin the
// rules, that harness pins the cross-language agreement.)
// (Local assert shims instead of std/testing: this VM's egress CA
// blocks deno.land fetches; the shims keep the suite dependency-free
// beyond the module under test.)
function assert(cond: unknown, msg = 'assertion failed'): asserts cond {
  if (!cond) throw new Error(msg)
}
function assertEquals(actual: unknown, expected: unknown, msg?: string) {
  const a = JSON.stringify(actual), e = JSON.stringify(expected)
  if (a !== e) throw new Error(msg ?? `assertEquals: ${a} !== ${e}`)
}
function assertStringIncludes(s: string, sub: string, msg?: string) {
  if (!s.includes(sub)) throw new Error(msg ?? `assertStringIncludes: missing ${JSON.stringify(sub)}`)
}
import {
  buildPrompt, classesFromText, expectedClassesForPrompt,
  evaluateGate, parseGateText, drawBudgetFor, selectAnchor,
  type GarmentClasses
} from "./index.ts"

const baseJob = {
  gender: 'MALE', weather: 'CLEAR', temp_bracket: 'WARM', time_of_day: 'MIDDAY',
  outfit_items: ['SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES']
}

Deno.test('prompt: warm clear midday matches the validated Part-4 shape', () => {
  const p = buildPrompt(baseJob)
  assertStringIncludes(p, 'A male runner in their 30s running mid-stride along a suburban sidewalk during a warm midday with clear conditions.')
  assertStringIncludes(p, 'OUTFIT:\n- Running short sleeve shirt\n- Running shorts\n- Running sunglasses')
  assert(!p.includes('show ALL items clearly'), 'legibility-pressure line stays removed (recalibration)')
  assert(p.trimEnd().endsWith('MOOD: Light and free, easy summer rhythm'), 'nothing follows the MOOD line')
})

Deno.test('prompt: cold clear midday — emphasis fronts head + hands, described (arm F) in subject and bullets', () => {
  const p = buildPrompt({
    gender: 'FEMALE', weather: 'CLEAR', temp_bracket: 'COLD', time_of_day: 'MIDDAY',
    outfit_items: ['NECK_GAITER', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'QUARTER_ZIP', 'LONG_SLEEVE_LIGHT', 'LIGHT_BEANIE']
  })
  assertStringIncludes(p, 'A female runner in their 30s, wearing a running beanie pulled down over the ears and fitted running gloves with separate fingers, running mid-stride')
  const seg = p.split('OUTFIT:\n')[1].split('\n\n')[0]
  assertEquals(seg, [
    '- Running beanie pulled down over the ears', '- Fitted running gloves with separate fingers',
    '- Light running long sleeve shirt', '- Running quarter zip pullover', '- Thermal running tights',
    '- Running neck gaiter'
  ].join('\n'))
})

Deno.test('prompt: byte-identical to yield round-two arm F AS AMENDED by the running-form ruling on its measured cells', () => {
  // The reference strings began as the round-two experiment driver's
  // own arm-F output, verbatim from its pre-spend prompt dump
  // (rw-mim-yield-round2-2026-11-03/work/dump_out.txt): the control
  // prompt (arm-B emphasis + hood-down rain bullet) run through
  // round2_driver.ts's armF(). Sol's running-form direction
  // (2026-11-03, after his bench review) then amended the garment
  // wording — every garment named in its running form, the two
  // "knitted" descriptors dropped — so the pins below are the arm-F
  // strings with exactly those qualifications applied (build record
  // §19 itemizes the per-item deltas). The production builder must
  // emit these strings directly.
  const cold = buildPrompt({
    gender: 'MALE', weather: 'CLEAR', temp_bracket: 'COLD', time_of_day: 'MIDDAY',
    outfit_items: ['LIGHT_BEANIE', 'LONG_SLEEVE_LIGHT', 'QUARTER_ZIP', 'THERMAL_TIGHTS',
      'LIGHT_GLOVES', 'SUNGLASSES', 'NECK_GAITER']
  })
  assertEquals(cold, `A male runner in their 30s, wearing a running beanie pulled down over the ears and fitted running gloves with separate fingers, running mid-stride along a suburban sidewalk during a cold winter midday with clear conditions.

OUTFIT:
- Running beanie pulled down over the ears
- Fitted running gloves with separate fingers
- Light running long sleeve shirt
- Running quarter zip pullover
- Thermal running tights
- Running sunglasses
- Running neck gaiter

ENVIRONMENT: Bright daylight; Clear sky, suburban neighborhood with sidewalks and trees

STYLE: Professional athletic action photography. Full body in motion, natural running stride, captured mid-step. Sharp focus on runner with slight motion energy, soft bokeh background. Authentic and dynamic, like a Nike campaign shot. The scene should make viewers FEEL the cold clear conditions.

MOOD: Strong and steady, winter grit`)
  const rain = buildPrompt({
    gender: 'MALE', weather: 'RAIN', temp_bracket: 'COOL', time_of_day: 'MIDDAY',
    outfit_items: ['BASEBALL_CAP', 'LONG_SLEEVE_LIGHT', 'RAIN_JACKET', 'LIGHT_TIGHTS', 'LIGHT_GLOVES']
  })
  assertEquals(rain, `A male runner in their 30s, wearing a running cap with the brim facing forward and fitted running gloves with separate fingers, running mid-stride along a suburban sidewalk during a cool autumn midday with rainy conditions.

OUTFIT:
- Running cap with the brim facing forward
- Fitted running gloves with separate fingers
- Light running long sleeve shirt
- Waterproof running rain jacket with the hood down
- Light running tights

ENVIRONMENT: Bright daylight; Rain falling, wet glistening pavement, puddles, gray sky

STYLE: Professional athletic action photography. Full body in motion, natural running stride, captured mid-step. Sharp focus on runner with slight motion energy, soft bokeh background. Authentic and dynamic, like a Nike campaign shot. The scene should make viewers FEEL the cool rainy conditions.

MOOD: Undeterred and strong, splashing through the rain`)
  const snow = buildPrompt({
    gender: 'MALE', weather: 'SNOW', temp_bracket: 'COOL', time_of_day: 'DUSK',
    outfit_items: ['HEADBAND', 'LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'LIGHT_GLOVES']
  })
  assertEquals(snow, `A male runner in their 30s, wearing a wide running headband covering the ears and fitted running gloves with separate fingers, running mid-stride along a suburban sidewalk during a cool autumn evening with snowy conditions.

OUTFIT:
- Wide running headband covering the ears
- Fitted running gloves with separate fingers
- Light running long sleeve shirt
- Light running tights

ENVIRONMENT: Golden evening light, long shadows; Snow falling, snow-covered streets and trees, soft winter light

STYLE: Professional athletic action photography. Full body in motion, natural running stride, captured mid-step. Sharp focus on runner with slight motion energy, soft bokeh background. Authentic and dynamic, like a Nike campaign shot. The scene should make viewers FEEL the cool snowy conditions.

MOOD: Quiet and powerful, tracks in fresh snow`)
  // Expected classes are emphasis-invariant: the gate derives the
  // same classes from the emphasized prompts as from the control
  // prompts (the experiment's own pre-spend sanity property).
  assertEquals(expectedClassesForPrompt(cold), {
    topBase: 'long', outer: false, mid: true, bottom: 'tights',
    head: 'beanie', hands: 'gloves', sunglasses: true, gaiter: true
  })
  assertEquals(expectedClassesForPrompt(rain), {
    topBase: 'long', outer: true, mid: false, bottom: 'tights',
    head: 'cap', hands: 'gloves', sunglasses: false, gaiter: false
  })
  assertEquals(expectedClassesForPrompt(snow), {
    topBase: 'long', outer: false, mid: false, bottom: 'tights',
    head: 'headband', hands: 'gloves', sunglasses: false, gaiter: false
  })
})

Deno.test('prompt: emphasis with only one of head/hands names and fronts that item (described, arm F)', () => {
  const headOnly = buildPrompt({
    gender: 'MALE', weather: 'CLEAR', temp_bracket: 'MILD', time_of_day: 'MIDDAY',
    outfit_items: ['SHORT_SLEEVE', 'SHORTS', 'BASEBALL_CAP']
  })
  assertStringIncludes(headOnly, 'A male runner in their 30s, wearing a running cap with the brim facing forward, running mid-stride')
  assertStringIncludes(headOnly, 'OUTFIT:\n- Running cap with the brim facing forward\n- Running short sleeve shirt\n- Running shorts')
  const handsOnly = buildPrompt({
    gender: 'FEMALE', weather: 'CLEAR', temp_bracket: 'FREEZING', time_of_day: 'DAWN',
    outfit_items: ['LONG_SLEEVE_THERMAL', 'THERMAL_TIGHTS', 'MITTENS']
  })
  assertStringIncludes(handsOnly, 'A female runner in their 30s, wearing thick insulated running mittens with no separate fingers, running mid-stride')
  assertStringIncludes(handsOnly, 'OUTFIT:\n- Thick insulated running mittens with no separate fingers\n- Thermal running long sleeve shirt\n- Thermal running tights')
})

Deno.test('prompt: arm F descriptions cover every head/hands item and keep expected classes unchanged', () => {
  // The experiment exercised LIGHT_BEANIE / BASEBALL_CAP / HEADBAND /
  // LIGHT_GLOVES. The remaining descriptions (experiment-brief
  // verbatim for THERMAL_BEANIE / THERMAL_GLOVES / BALACLAVA /
  // MITTENS; VISOR extended in the identical spirit) must render in
  // both places and parse to the same expected classes as the plain
  // bullets did.
  const thermal = buildPrompt({
    gender: 'MALE', weather: 'SNOW', temp_bracket: 'FREEZING', time_of_day: 'MIDDAY',
    outfit_items: ['THERMAL_BEANIE', 'LONG_SLEEVE_THERMAL', 'THERMAL_TIGHTS', 'THERMAL_GLOVES']
  })
  assertStringIncludes(thermal, 'wearing a running beanie pulled down over the ears and fitted running gloves with separate fingers,')
  assertStringIncludes(thermal, '- Running beanie pulled down over the ears\n- Fitted running gloves with separate fingers')
  assertEquals(expectedClassesForPrompt(thermal).head, 'beanie')
  assertEquals(expectedClassesForPrompt(thermal).hands, 'gloves')
  const balaclava = buildPrompt({
    gender: 'FEMALE', weather: 'CLEAR', temp_bracket: 'FREEZING', time_of_day: 'NIGHT',
    outfit_items: ['BALACLAVA', 'LONG_SLEEVE_THERMAL', 'THERMAL_TIGHTS', 'MITTENS']
  })
  assertStringIncludes(balaclava, 'wearing a running balaclava covering the head and neck and thick insulated running mittens with no separate fingers,')
  assertStringIncludes(balaclava, '- Running balaclava covering the head and neck\n- Thick insulated running mittens with no separate fingers')
  assertEquals(expectedClassesForPrompt(balaclava).head, 'balaclava')
  assertEquals(expectedClassesForPrompt(balaclava).hands, 'mittens')
  assertEquals(expectedClassesForPrompt(balaclava).gaiter, false)
  const visor = buildPrompt({
    gender: 'FEMALE', weather: 'CLEAR', temp_bracket: 'HOT', time_of_day: 'MIDDAY',
    outfit_items: ['TANK_TOP', 'SHORT_SHORTS', 'VISOR']
  })
  assertStringIncludes(visor, 'wearing a running visor with the brim facing forward,')
  assertStringIncludes(visor, '- Running visor with the brim facing forward')
  assertEquals(expectedClassesForPrompt(visor).head, 'visor')
})

Deno.test('prompt: running form — every garment is named as running gear; no knit wording (Sol ruling 2026-11-03)', () => {
  // Sol's bench review: the headwear rendered as knit/fashion
  // beanies, not athletic gear. Every prescribable garment must be
  // named in its running form somewhere in its outfit bullet, and
  // the word "knitted" must never appear. SUNSCREEN is no longer an
  // exception of any kind: it is not named in the prompt at all
  // (Sol's later ruling the same day — see the dedicated pin below).
  const ALL_ITEMS = [
    'TANK_TOP', 'SHORT_SLEEVE', 'LONG_SLEEVE_LIGHT', 'LONG_SLEEVE_THERMAL',
    'QUARTER_ZIP', 'FLEECE_PULLOVER', 'HALF_ZIP_THERMAL', 'LIGHT_VEST',
    'WINDBREAKER', 'LIGHT_JACKET', 'RAIN_JACKET', 'INSULATED_JACKET',
    'SHORTS', 'SHORT_SHORTS', 'CAPRIS', 'LIGHT_TIGHTS', 'THERMAL_TIGHTS',
    'VISOR', 'BASEBALL_CAP', 'HEADBAND', 'LIGHT_BEANIE', 'THERMAL_BEANIE',
    'BALACLAVA', 'LIGHT_GLOVES', 'THERMAL_GLOVES', 'MITTENS',
    'NECK_GAITER', 'SUNGLASSES', 'SUNSCREEN', 'REFLECTIVE_GEAR'
  ]
  for (const k of ALL_ITEMS) {
    const p = buildPrompt({
      gender: 'MALE', weather: 'CLEAR', temp_bracket: 'COLD',
      time_of_day: 'MIDDAY', outfit_items: [k]
    })
    const seg = p.split('OUTFIT:\n')[1].split('\n\n')[0].toLowerCase()
    assert(!p.toLowerCase().includes('knitted'), `${k}: knit wording is gone`)
    if (k === 'SUNSCREEN') {
      assert(!p.toLowerCase().includes('sunscreen'), 'sunscreen is never named in a prompt (Sol ruling 2026-11-03)')
    } else {
      assert(seg.includes('running'), `${k}: bullet names the running form (${seg})`)
    }
  }
})

Deno.test('prompt: sunscreen appears in NO prompt for any cell that prescribes it (Sol ruling 2026-11-03)', () => {
  // "Don't need to mention sunscreen in a prompt!" — sunscreen is
  // advice for the runner, not something a picture can show. The
  // prescription still carries it (the app's outfit list shows
  // it); buildPrompt must drop it from every surface. The prompt
  // for a job WITH sunscreen is byte-identical to the same job
  // without it, and the gate's expected classes do not move.
  const jobs: Array<{ items: string[], weather: string, bracket: string }> = [
    { items: ['SUNSCREEN'], weather: 'CLEAR', bracket: 'WARM' },
    { items: ['SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES', 'SUNSCREEN'], weather: 'CLEAR', bracket: 'WARM' },
    { items: ['TANK_TOP', 'SHORT_SHORTS', 'VISOR', 'SUNSCREEN'], weather: 'CLEAR', bracket: 'HOT' },
    { items: ['SHORT_SLEEVE', 'SHORTS', 'BASEBALL_CAP', 'SUNSCREEN', 'REFLECTIVE_GEAR'], weather: 'CLOUDY', bracket: 'MILD' },
    {
      items: ['TANK_TOP', 'SHORT_SLEEVE', 'LONG_SLEEVE_LIGHT', 'LONG_SLEEVE_THERMAL',
        'QUARTER_ZIP', 'FLEECE_PULLOVER', 'HALF_ZIP_THERMAL', 'LIGHT_VEST',
        'WINDBREAKER', 'LIGHT_JACKET', 'RAIN_JACKET', 'INSULATED_JACKET',
        'SHORTS', 'SHORT_SHORTS', 'CAPRIS', 'LIGHT_TIGHTS', 'THERMAL_TIGHTS',
        'VISOR', 'BASEBALL_CAP', 'HEADBAND', 'LIGHT_BEANIE', 'THERMAL_BEANIE',
        'BALACLAVA', 'LIGHT_GLOVES', 'THERMAL_GLOVES', 'MITTENS',
        'NECK_GAITER', 'SUNGLASSES', 'SUNSCREEN', 'REFLECTIVE_GEAR'],
      weather: 'CLEAR', bracket: 'HOT'
    }
  ]
  for (const j of jobs) {
    for (const gender of ['MALE', 'FEMALE']) {
      const withS = buildPrompt({
        gender, weather: j.weather, temp_bracket: j.bracket,
        time_of_day: 'MIDDAY', outfit_items: j.items
      })
      const without = buildPrompt({
        gender, weather: j.weather, temp_bracket: j.bracket,
        time_of_day: 'MIDDAY', outfit_items: j.items.filter(k => k !== 'SUNSCREEN')
      })
      assert(!withS.toLowerCase().includes('sunscreen'),
        `sunscreen named in prompt for [${j.items.join(',')}] (${gender})`)
      assertEquals(withS, without, `prompt changes beyond sunscreen removal for [${j.items.join(',')}] (${gender})`)
      assertEquals(expectedClassesForPrompt(withS), expectedClassesForPrompt(without),
        `expected classes move for [${j.items.join(',')}] (${gender})`)
    }
  }
})

Deno.test('expected classes derive from the prompt segment (lane method)', () => {
  const exp = expectedClassesForPrompt(buildPrompt(baseJob))
  assertEquals(exp.topBase, 'short')
  assertEquals(exp.bottom, 'shorts')
  assertEquals(exp.sunglasses, true)
  assertEquals(exp.outer, false)
})

Deno.test('budget: Warm 2 / Cold 3 / Rain 4 / Snow 5', () => {
  assertEquals(drawBudgetFor('CLEAR', 'WARM'), 2)
  assertEquals(drawBudgetFor('CLEAR', 'MILD'), 2)
  assertEquals(drawBudgetFor('CLOUDY', 'COOL'), 2)
  assertEquals(drawBudgetFor('CLEAR', 'COLD'), 3)
  assertEquals(drawBudgetFor('CLEAR', 'FREEZING'), 3)
  assertEquals(drawBudgetFor('RAIN', 'WARM'), 4)
  assertEquals(drawBudgetFor('RAIN', 'COOL'), 4)
  assertEquals(drawBudgetFor('SNOW', 'COOL'), 5)
  assertEquals(drawBudgetFor('SNOW', 'FREEZING'), 5)
})

function classes(partial: Partial<GarmentClasses>): GarmentClasses {
  return {
    topBase: null, outer: false, mid: false, bottom: null,
    head: null, hands: null, sunglasses: false, gaiter: false, ...partial
  }
}
const okAnswers = {
  person_count: '1', anatomy_ok: 'yes', face_ok: 'yes', pose_ok: 'yes',
  construction_ok: 'yes', logos_or_text: 'no', two_watches: 'no'
}

Deno.test('gate: clean warm draw passes', () => {
  const exp = classes({ topBase: 'short', bottom: 'shorts', sunglasses: true })
  const seen = classes({ topBase: 'short', bottom: 'shorts', sunglasses: true })
  const v = evaluateGate('CLEAR', 'MIDDAY', exp, seen, okAnswers, 'clear', 'daylight')
  assertEquals(v.pass, true)
})

Deno.test('gate: shorts in a tights cell fails at family grain', () => {
  const exp = classes({ topBase: 'long', bottom: 'tights', head: 'beanie', hands: 'gloves' })
  const seen = classes({ topBase: 'long', bottom: 'shorts', head: 'beanie', hands: 'gloves' })
  const v = evaluateGate('CLEAR', 'MIDDAY', exp, seen, okAnswers, 'clear', 'daylight')
  assertEquals(v.pass, false)
  assert(v.failures.some(f => f.cls === 'bottom'))
})

Deno.test('gate: ruling 1 — gaiter mismatch is a note, never a failure', () => {
  const exp = classes({ topBase: 'long', bottom: 'tights', gaiter: true })
  const seen = classes({ topBase: 'long', bottom: 'tights', gaiter: false })
  const v = evaluateGate('CLEAR', 'DAWN', exp, seen, okAnswers, 'clear', 'golden')
  assertEquals(v.pass, true)
  assertEquals(v.notes.length, 1)
})

Deno.test('gate: ruling 2 — base unperceived under a worn shell never fails', () => {
  const exp = classes({ topBase: 'long', outer: true, mid: true, bottom: 'tights' })
  const seen = classes({ topBase: null, outer: true, mid: false, bottom: 'tights' })
  const v = evaluateGate('RAIN', 'DAWN', exp, seen, okAnswers, 'raining', 'flat')
  assertEquals(v.pass, true)
})

Deno.test('gate: ruling 3 — marginal condition is a note, clear contradiction fails', () => {
  const exp = classes({ topBase: 'short', bottom: 'shorts' })
  const seen = classes({ topBase: 'short', bottom: 'shorts' })
  const marginal = evaluateGate('CLEAR', 'MIDDAY', exp, seen, okAnswers, 'overcast', 'daylight')
  assertEquals(marginal.pass, true)
  const contra = evaluateGate('CLEAR', 'MIDDAY', exp, seen, okAnswers, 'raining', 'daylight')
  assertEquals(contra.pass, false)
  const nightMidday = evaluateGate('CLEAR', 'MIDDAY', exp, seen, okAnswers, 'clear', 'night')
  assertEquals(nightMidday.pass, false)
})

Deno.test('gate: rulings 4+5 — pose and construction are defect classes', () => {
  const exp = classes({ topBase: 'short', bottom: 'shorts' })
  const seen = classes({ topBase: 'short', bottom: 'shorts' })
  const pose = evaluateGate('CLEAR', 'MIDDAY', exp, seen, { ...okAnswers, pose_ok: 'no' }, 'clear', 'daylight')
  assertEquals(pose.pass, false)
  const cons = evaluateGate('CLEAR', 'MIDDAY', exp, seen, { ...okAnswers, construction_ok: 'no' }, 'clear', 'daylight')
  assertEquals(cons.pass, false)
})

Deno.test('gate: ruling 6 (V1) — sunglasses omission is a note; unprescribed sunglasses still fail', () => {
  const exp = classes({ topBase: 'short', bottom: 'shorts', sunglasses: true })
  const omitted = classes({ topBase: 'short', bottom: 'shorts', sunglasses: false })
  const v = evaluateGate('CLEAR', 'MIDDAY', exp, omitted, okAnswers, 'clear', 'daylight')
  assertEquals(v.pass, true)
  assert(v.notes.some(n => n.includes('V1 ruling')), 'omission is recorded as a note')
  assertEquals(v.failures.length, 0)
  // Omission-only scope: sunglasses seen where NOT prescribed still fail.
  const expNone = classes({ topBase: 'short', bottom: 'shorts', sunglasses: false })
  const worn = classes({ topBase: 'short', bottom: 'shorts', sunglasses: true })
  const v2 = evaluateGate('CLEAR', 'MIDDAY', expNone, worn, okAnswers, 'clear', 'daylight')
  assertEquals(v2.pass, false)
  assert(v2.failures.some(f => f.cls === 'sunglasses'))
})

Deno.test('gate: ruling 7 (balaclava clarification) — a covered head satisfies headwear in any form; absence and unprescribed headwear still fail', () => {
  const exp = classes({ topBase: 'long', bottom: 'tights', head: 'beanie', hands: 'gloves' })
  const balaclava = classes({ topBase: 'long', bottom: 'tights', head: 'balaclava', hands: 'gloves' })
  const v = evaluateGate('CLEAR', 'MIDDAY', exp, balaclava, okAnswers, 'clear', 'daylight')
  assertEquals(v.pass, true)
  assert(v.notes.some(n => n.includes('balaclava clarification')), 'form difference is recorded as a note')
  // Any covering form satisfies — headband for a prescribed beanie.
  const headband = classes({ topBase: 'long', bottom: 'tights', head: 'headband', hands: 'gloves' })
  assertEquals(evaluateGate('CLEAR', 'MIDDAY', exp, headband, okAnswers, 'clear', 'daylight').pass, true)
  // V2 was rejected: a bare head still fails where headwear is prescribed.
  const bare = classes({ topBase: 'long', bottom: 'tights', head: null, hands: 'gloves' })
  const vBare = evaluateGate('CLEAR', 'MIDDAY', exp, bare, okAnswers, 'clear', 'daylight')
  assertEquals(vBare.pass, false)
  assert(vBare.failures.some(f => f.cls === 'head'))
  // Headwear seen where none is prescribed still fails — the
  // clarification satisfies a requirement; it does not erase one.
  const expNone = classes({ topBase: 'short', bottom: 'shorts' })
  const covered = classes({ topBase: 'short', bottom: 'shorts', head: 'balaclava' })
  const vUn = evaluateGate('CLEAR', 'MIDDAY', expNone, covered, okAnswers, 'clear', 'daylight')
  assertEquals(vUn.pass, false)
  assert(vUn.failures.some(f => f.cls === 'head'))
  // The enumeration read encodes a face covering as the balaclava class.
  const parsed = parseGateText(['Thermal tights', 'Balaclava', 'PERSON_COUNT: 1'].join('\n'))
  assertEquals(parsed.seen.head, 'balaclava')
})

Deno.test('gate: ruling 8 — covered hands satisfy handwear in either form; absence and unprescribed handwear still fail', () => {
  const exp = classes({ topBase: 'long', bottom: 'tights', head: 'beanie', hands: 'mittens' })
  const gloves = classes({ topBase: 'long', bottom: 'tights', head: 'beanie', hands: 'gloves' })
  const v = evaluateGate('CLEAR', 'MIDDAY', exp, gloves, okAnswers, 'clear', 'daylight')
  assertEquals(v.pass, true)
  assert(v.notes.some(n => n.includes('coverage ruling 2026-11-03')), 'form difference is recorded as a note')
  // The reverse direction satisfies too — mittens for gloves.
  const expGloves = classes({ topBase: 'long', bottom: 'tights', head: 'beanie', hands: 'gloves' })
  const mittens = classes({ topBase: 'long', bottom: 'tights', head: 'beanie', hands: 'mittens' })
  assertEquals(evaluateGate('CLEAR', 'MIDDAY', expGloves, mittens, okAnswers, 'clear', 'daylight').pass, true)
  // Absence is not excused: bare hands where handwear is prescribed fail.
  const bare = classes({ topBase: 'long', bottom: 'tights', head: 'beanie', hands: null })
  const vBare = evaluateGate('CLEAR', 'MIDDAY', exp, bare, okAnswers, 'clear', 'daylight')
  assertEquals(vBare.pass, false)
  assert(vBare.failures.some(f => f.cls === 'hands'))
  // Handwear seen where none is prescribed still fails (additions
  // behavior unchanged by the coverage ruling).
  const expNone = classes({ topBase: 'short', bottom: 'shorts' })
  const covered = classes({ topBase: 'short', bottom: 'shorts', hands: 'gloves' })
  const vUn = evaluateGate('CLEAR', 'MIDDAY', expNone, covered, okAnswers, 'clear', 'daylight')
  assertEquals(vUn.pass, false)
  assert(vUn.failures.some(f => f.cls === 'hands'))
})

Deno.test('gate: ruling 9 — blurred background people are a note; a second clear subject still fails', () => {
  const exp = classes({ topBase: 'short', bottom: 'shorts', sunglasses: true })
  const seen = classes({ topBase: 'short', bottom: 'shorts', sunglasses: true })
  const bg = evaluateGate('CLEAR', 'MIDDAY', exp, seen,
    { ...okAnswers, background_people: 'yes' }, 'clear', 'daylight')
  assertEquals(bg.pass, true)
  assert(bg.notes.some(n => n.includes('ruling 9')), 'background figures are recorded as a note')
  assertEquals(bg.failures.length, 0)
  // A second CLEAR subject still fails on the count, background flag or not.
  const two = evaluateGate('CLEAR', 'MIDDAY', exp, seen,
    { ...okAnswers, person_count: '2', background_people: 'yes' }, 'clear', 'daylight')
  assertEquals(two.pass, false)
  assert(two.failures.some(f => f.cls === 'person'))
  // Stored reads under the old prompt carry no background field:
  // an old count above 1 keeps failing (no retroactive change).
  const legacy = evaluateGate('CLEAR', 'MIDDAY', exp, seen,
    { ...okAnswers, person_count: '3' }, 'clear', 'daylight')
  assertEquals(legacy.pass, false)
  assert(legacy.failures.some(f => f.cls === 'person'))
  // The enumeration read parses the new field.
  const parsed = parseGateText(['PERSON_COUNT: 1', 'BACKGROUND_PEOPLE: yes'].join('\n'))
  assertEquals(parsed.answers.background_people, 'yes')
  assertEquals(parsed.answers.person_count, '1')
})

Deno.test('no repair stage: a gate-failed draw performs no fill call and no repair storage write (Sol ruling 2026-11-03)', () => {
  // The repair stage was dropped by Sol's ruling (2026-11-03 04:13
  // EST) after the persisting re-battery measured rescue at 0/15.
  // processJob is not unit-runnable (it is bound to the live
  // database), so the pin is structural: the worker source must
  // contain no fill model, no repair invocation, no mask builder,
  // and no repair storage path — a gate-failed draw can only fall
  // back or consume another draw from the budget.
  const source = Deno.readTextFileSync(new URL('./index.ts', import.meta.url))
  assert(!source.includes('flux-fill-pro'), 'no fill model reference remains')
  assert(!source.includes('attemptRepair'), 'no repair invocation remains')
  assert(!source.includes('buildRepairMask'), 'no repair mask builder remains')
  assert(!source.includes('isAccessoryOnlyFailure'), 'no repair eligibility classifier remains')
  assert(!source.includes('/repair/'), 'no repair storage path remains')
  assert(!source.includes('repair_attempted'), 'no repair bookkeeping is read or written')
})

Deno.test('parse: enumeration text extracts answers + conditions', () => {
  const text = [
    'Long sleeve shirt', 'Thermal tights', 'Light beanie',
    'PERSON_COUNT: 1', 'ANATOMY_OK: yes', 'FACE_OK: yes',
    'LOGOS_OR_TEXT: no', 'TWO_WATCHES: no',
    'CONDITIONS_SEEN: clear', 'LIGHT_SEEN: daylight',
    'POSE_OK: yes', 'CONSTRUCTION_OK: yes'
  ].join('\n')
  const parsed = parseGateText(text)
  assertEquals(parsed.answers.person_count, '1')
  assertEquals(parsed.conditionsSeen, 'clear')
  assertEquals(parsed.seen.topBase, 'long')
  assertEquals(parsed.seen.bottom, 'tights')
  assertEquals(parsed.seen.head, 'beanie')
})

Deno.test('anchor: first passing reference wins; failures are skipped, never blended', () => {
  const refs = [
    { storage_path: 'u/refs/ref-1.jpg', position: 1, quality: { verdict: 'fail' } },
    { storage_path: 'u/refs/ref-2.jpg', position: 2, quality: { verdict: 'pass' } },
    { storage_path: 'u/refs/ref-3.jpg', position: 3, quality: null }
  ]
  assertEquals(selectAnchor(refs)?.storage_path, 'u/refs/ref-2.jpg')
  const unchecked = [
    { storage_path: 'u/refs/ref-1.jpg', position: 1, quality: null },
    { storage_path: 'u/refs/ref-2.jpg', position: 2, quality: null }
  ]
  assertEquals(selectAnchor(unchecked)?.storage_path, 'u/refs/ref-1.jpg')
  assertEquals(selectAnchor([]), null)
})
