import { applySecondLook, buildPrompt, classifyProviderFailure, dependencyDrawFailure, dependencyFailure, evaluateGate, isUniqueViolation, providerFailureText, terminalFailureText, type GarmentClasses, type SecondLookResolution } from "./index.ts"

function assert(cond: boolean, msg: string) { if (!cond) throw new Error("ASSERT FAILED: " + msg) }

const base: GarmentClasses = {
  topBase: 'long', outer: false, mid: false, bottom: 'tights',
  head: 'headband', hands: 'gloves', sunglasses: false, gaiter: false
}
const ans: Record<string, string | null> = {
  person_count: '1', background_people: 'no', anatomy_ok: 'yes', face_ok: 'yes',
  pose_ok: 'yes', construction_ok: 'yes', logos_or_text: 'no', two_watches: 'no'
}

// 1. Baseline (no second look): null bottom + null hands fail as before.
{
  const seen: GarmentClasses = { ...base, bottom: null, hands: null }
  const r = evaluateGate('CLOUDY', 'DUSK', base, seen, ans, 'overcast', 'golden')
  assert(!r.pass, 'baseline should fail')
  assert(r.failures.some(f => f.cls === 'bottom'), 'baseline bottom failure')
  assert(r.failures.some(f => f.cls === 'hands'), 'baseline hands failure')
  assert(!r.failures.some(f => f.cls === 'gate_unparseable'), 'baseline no unparseable')
}

// 2. Joint null after second look -> single gate_unparseable, no bottom/hands failures.
{
  const seen: GarmentClasses = { ...base, bottom: null, hands: null }
  const sl: SecondLookResolution = { bottom: 'unseen', hands: 'unseen' }
  const r = evaluateGate('CLOUDY', 'DUSK', base, seen, ans, 'overcast', 'golden', sl)
  assert(!r.pass, 'joint-null should not pass')
  assert(r.failures.length === 1 && r.failures[0].cls === 'gate_unparseable',
    'joint-null single unparseable failure, got ' + JSON.stringify(r.failures))
}

// 3. 'absent' bottom defeats the joint rule: bottom fails as missing, no unparseable.
{
  const seen: GarmentClasses = { ...base, bottom: null, hands: null }
  const sl: SecondLookResolution = { bottom: 'absent', hands: 'unseen' }
  const r = evaluateGate('CLOUDY', 'DUSK', base, seen, ans, 'overcast', 'golden', sl)
  assert(r.failures.some(f => f.cls === 'bottom'), 'absent bottom still fails')
  assert(!r.failures.some(f => f.cls === 'gate_unparseable'), 'absent defeats joint rule')
}

// 4. applySecondLook resolves a named covering; 'not visible' stays unseen.
{
  const seen: GarmentClasses = { ...base, bottom: null, hands: null }
  const res = applySecondLook(seen,
    'LEGS_VISIBLE: yes\nLEG_COVERING: tights\nHANDS_VISIBLE: no\nHAND_COVERING: not visible')
  assert(res.bottom === 'resolved' && seen.bottom === 'tights', 'bottom resolved to tights')
  assert(res.hands === 'unseen' && seen.hands === null, 'hands unseen')
}

// 5. applySecondLook never overwrites a positive first-pass read.
{
  const seen: GarmentClasses = { ...base, bottom: 'shorts', hands: 'gloves' }
  const res = applySecondLook(seen,
    'LEGS_VISIBLE: yes\nLEG_COVERING: tights\nHANDS_VISIBLE: yes\nHAND_COVERING: mittens')
  assert(res.bottom === null && seen.bottom === 'shorts', 'positive bottom untouched')
  assert(res.hands === null && seen.hands === 'gloves', 'positive hands untouched')
}

// 6. Second-look resolved WRONG garment still fails (anti-slippage).
{
  const seen: GarmentClasses = { ...base, bottom: null, hands: 'gloves' }
  const res = applySecondLook(seen,
    'LEGS_VISIBLE: yes\nLEG_COVERING: shorts\nHANDS_VISIBLE: yes\nHAND_COVERING: gloves')
  assert(res.bottom === 'resolved' && seen.bottom === 'shorts', 'resolved to shorts')
  const r = evaluateGate('CLOUDY', 'DUSK', base, seen, ans, 'overcast', 'golden', res)
  assert(r.failures.some(f => f.cls === 'bottom'), 'wrong garment via second look still fails')
}

// 7. Bare skin reads as a judged absence: fails, no unparseable.
{
  const seen: GarmentClasses = { ...base, bottom: null, hands: null }
  const res = applySecondLook(seen,
    'LEGS_VISIBLE: yes\nLEG_COVERING: bare skin\nHANDS_VISIBLE: yes\nHAND_COVERING: bare skin')
  assert(res.bottom === 'absent' && res.hands === 'absent', 'bare skin = absent')
  const r = evaluateGate('CLOUDY', 'DUSK', base, seen, ans, 'overcast', 'golden', res)
  assert(r.failures.some(f => f.cls === 'bottom') && r.failures.some(f => f.cls === 'hands'),
    'judged absences fail')
  assert(!r.failures.some(f => f.cls === 'gate_unparseable'), 'absent is not unparseable')
}

// 8. Full-body framing is in the generation prompt by construction.
{
  const p = buildPrompt({ gender: 'MALE', weather: 'CLOUDY', temp_bracket: 'COOL',
    time_of_day: 'DUSK', outfit_items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS', 'HEADBAND', 'LIGHT_GLOVES'] })
  assert(p.includes('Full body in motion'), 'STYLE_BLOCK frames full body')
  assert(p.includes('running mid-stride along a suburban sidewalk'), 'subject frames full body')
}


// ---------------------------------------------------------------------
// Reconciliation tests (2026-11-25, plan step 6): the restored v46
// machinery — failure classification, hold-don't-fail predicates,
// terminal-text constructors, RW-126 violation detection, RW-80
// rain prompt fidelity.
// ---------------------------------------------------------------------

// 9. classifyProviderFailure: prediction refusals and 4xx/5xx
// create refusals are provider rulings; other shapes are not.
{
  const pf = classifyProviderFailure('Prediction abc123 failed: flagged as sensitive (E005)')
  assert(pf !== null && pf.code === 'E005', 'prediction refusal classified with E005')
  const pc = classifyProviderFailure('PuLID create failed: 400 {"detail":"bad input"}')
  assert(pc !== null && pc.code === null, 'create refusal classified, no code')
  assert(classifyProviderFailure('Replicate poll failed: 503') === null, 'poll 5xx is not a provider refusal')
  assert(classifyProviderFailure('Prediction timed out') === null, 'timeout is not a provider refusal')
  assert(classifyProviderFailure('Prediction x9 canceled: user canceled') !== null, 'canceled prediction classified')
}

// 10. providerFailureText: provider_error prefix (never
// gate_failed), code preserved — the serve path maps this prefix
// to service_refused.
{
  const t = providerFailureText({ code: 'E005', message: 'Prediction abc failed: flagged (E005)' })
  assert(t.startsWith('provider_error: E005:'), 'provider text carries prefix + code, got ' + t)
  assert(!t.startsWith('gate_failed'), 'provider text is not a gate failure')
  assert(providerFailureText(null).startsWith('provider_error:'), 'null pf still provider_error')
}

// 11. dependencyFailure (RW-172 job level): the three named
// prefixes + 5xx creates hold; 4xx creates and job faults do not.
{
  assert(dependencyFailure('gate_unavailable: anthropic 529'), 'gate down holds')
  assert(dependencyFailure('identity_screen_unavailable: screen returned 503'), 'screen down holds')
  assert(dependencyFailure('replicate_unavailable: poll request failed: reset'), 'transport holds')
  assert(dependencyFailure('PuLID create failed: 503 slow'), '5xx create holds')
  assert(dependencyFailure('PuLID model lookup failed: 502'), '5xx lookup holds')
  assert(!dependencyFailure('PuLID create failed: 400 bad'), '4xx create is a ruling, not a hold')
  assert(!dependencyFailure('No reference photos registered'), 'job fault does not hold')
  assert(!dependencyFailure('Hero insert failed: boom'), 'insert fault does not hold')
}

// 12. dependencyDrawFailure (RW-172 draw level): poll/download
// 5xx + transport hold; timeouts and refusals do not.
{
  assert(dependencyDrawFailure('replicate_unavailable: output download request failed: reset'), 'download transport holds')
  assert(dependencyDrawFailure('Replicate poll failed: 503'), 'poll 5xx holds')
  assert(dependencyDrawFailure('Output download failed: 500'), 'download 5xx holds')
  assert(!dependencyDrawFailure('Replicate poll failed: 404'), 'poll 404 does not hold')
  assert(!dependencyDrawFailure('Prediction timed out'), 'timeout keeps counted-draw discipline')
  assert(!dependencyDrawFailure('Prediction abc failed: nope'), 'refusal keeps provider class')
}

// 13. terminalFailureText: v46 constructors for the shared
// stages + the Nov 25 ports' exact live texts.
{
  const base = { lastFailures: [{ cls: 'bottom', message: 'bottom wrong' }], identityBest: null, lastProviderFailure: null, drawsUsedTotal: 2, budget: 2 }
  assert(terminalFailureText({ ...base, lastStage: 'prescription_gate' }) === 'gate_failed: bottom wrong', 'gate terminal text')
  assert(terminalFailureText({ ...base, lastStage: 'identity_screen', identityBest: 0.31 }) === 'gate_failed: identity_screen (best similarity 0.31)', 'screen terminal text')
  const pt = terminalFailureText({ ...base, lastStage: 'provider_error', lastProviderFailure: { code: 'E005', message: 'Prediction abc failed: flagged (E005)' } })
  assert(pt === 'provider_error: E005: Prediction abc failed: flagged (E005)', 'provider terminal text, got ' + pt)
  assert(terminalFailureText({ ...base, lastStage: 'sensitive_flag' }) === 'sensitive_flag: generation flagged as sensitive (E005) on every draw in budget', 'sensitive terminal text is the live v52 text')
  assert(terminalFailureText({ ...base, lastStage: 'gate_unparseable' }) === 'gate_unparseable: bottom wrong', 'unparseable terminal text')
  assert(terminalFailureText({ ...base, lastStage: 'draw_timeout' }) === 'draw_timeout: draw never returned a usable image within the job\'s draw budget (2/2 draws used)', 'generic terminal text')
}

// 14. isUniqueViolation (RW-126): pg code, message shapes, null.
{
  assert(isUniqueViolation({ code: '23505' }), 'pg unique code')
  assert(isUniqueViolation({ message: 'duplicate key value violates unique constraint' }), 'duplicate key message')
  assert(isUniqueViolation({ message: 'Unique constraint failed' }), 'unique constraint message')
  assert(!isUniqueViolation({ message: 'some other error' }), 'other error is not a violation')
  assert(!isUniqueViolation(null), 'null is not a violation')
}

// 15. RW-80 rain fidelity: RAIN cells take the overcast light
// table + the v46 scene text; non-rain prompts are unchanged.
{
  const rain = buildPrompt({ gender: 'MALE', weather: 'RAIN', temp_bracket: 'COOL',
    time_of_day: 'DAWN', outfit_items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] })
  assert(rain.includes('Gray overcast dawn light, no direct sun'), 'rain dawn uses LIGHT_RAIN')
  assert(!rain.includes('Soft golden sunrise'), 'rain dawn has no golden sun')
  assert(rain.includes('Rain falling in visible streaks'), 'rain scene is the v46 text')
  const clear = buildPrompt({ gender: 'MALE', weather: 'CLEAR', temp_bracket: 'COOL',
    time_of_day: 'DAWN', outfit_items: ['LONG_SLEEVE_LIGHT', 'LIGHT_TIGHTS'] })
  assert(clear.includes('Soft golden sunrise light'), 'clear dawn keeps LIGHT')
}

console.log('ALL CANDIDATE TESTS PASSED')
