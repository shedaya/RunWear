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
  isAccessoryOnlyFailure, type GarmentClasses
} from "./index.ts"

const baseJob = {
  gender: 'MALE', weather: 'CLEAR', temp_bracket: 'WARM', time_of_day: 'MIDDAY',
  outfit_items: ['SHORT_SLEEVE', 'SHORTS', 'SUNGLASSES']
}

Deno.test('prompt: warm clear midday matches the validated Part-4 shape', () => {
  const p = buildPrompt(baseJob)
  assertStringIncludes(p, 'A male runner in their 30s running mid-stride along a suburban sidewalk during a warm midday with clear conditions.')
  assertStringIncludes(p, 'OUTFIT:\n- Short sleeve shirt\n- Running shorts\n- Sunglasses')
  assert(!p.includes('show ALL items clearly'), 'legibility-pressure line stays removed (recalibration)')
  assert(p.trimEnd().endsWith('MOOD: Light and free, easy summer rhythm'), 'nothing follows the MOOD line')
})

Deno.test('prompt: cold clear midday items in wearing order', () => {
  const p = buildPrompt({
    gender: 'FEMALE', weather: 'CLEAR', temp_bracket: 'COLD', time_of_day: 'MIDDAY',
    outfit_items: ['NECK_GAITER', 'THERMAL_TIGHTS', 'LIGHT_GLOVES', 'QUARTER_ZIP', 'LONG_SLEEVE_LIGHT', 'LIGHT_BEANIE']
  })
  const seg = p.split('OUTFIT:\n')[1].split('\n\n')[0]
  assertEquals(seg, [
    '- Light long sleeve shirt', '- Quarter zip pullover', '- Thermal tights',
    '- Light beanie', '- Light gloves', '- Neck gaiter'
  ].join('\n'))
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

Deno.test('gate: accessory-only classification drives repair eligibility', () => {
  assert(isAccessoryOnlyFailure([{ cls: 'head', message: 'x' }, { cls: 'sunglasses', message: 'y' }]))
  assert(isAccessoryOnlyFailure([{ cls: 'hands', message: 'x' }]))
  assert(!isAccessoryOnlyFailure([{ cls: 'head', message: 'x' }, { cls: 'bottom', message: 'y' }]))
  assert(!isAccessoryOnlyFailure([]))
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
