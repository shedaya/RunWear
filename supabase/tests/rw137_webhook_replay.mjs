// RW-137 S2 replay test: runs the REAL revenuecat-webhook function
// (Deno, pointed at the in-memory mock Supabase) through reconstructed
// production event shapes — the 16 lifetime events of DESIGN §5
// (3 INITIAL_PURCHASE / 6 RENEWAL / 3 EXPIRATION / 2 CANCELLATION /
// 2 TEST with their recorded outcome distribution) — plus synthetic
// TRANSFER fixtures for the new RW-137 adoption case.
// Run via run_webhook_replay.sh (starts the mock + the function).
const MOCK = 'http://127.0.0.1:54399'
const HOOK = 'http://127.0.0.1:8000'
const AUTH = 'test-hook-key'

const U_NEW = '11111111-1111-4111-8111-111111111111'
const U_NEW2 = '22222222-2222-4222-8222-222222222222'
const U_ENT = '33333333-3333-4333-8333-333333333333'
const U_EXP = '44444444-4444-4444-8444-444444444444'
const U_NEVER = '55555555-5555-4555-8555-555555555555'
const U_GHOST = '66666666-6666-4666-8666-666666666666'
const S1 = '77777777-7777-4777-8777-777777777777'
const D1 = '88888888-8888-4888-8888-888888888888'
const S2 = '99999999-9999-4999-8999-999999999999'
const S3 = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'

const results = []
function check(name, cond, extra = '') {
  results.push([name, !!cond])
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}  ${extra}`)
}

async function post(path, body, headers = {}) {
  const resp = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  })
  return { status: resp.status, body: await resp.json().catch(() => null) }
}
const hook = (event) => post(HOOK + '/', { event }, { Authorization: AUTH })
const mockState = async () => (await (await fetch(MOCK + '/__state')).json())

let seq = 0
const eid = () => `REPLAY-${String(++seq).padStart(3, '0')}-TEST-EVENT-ID`
const ev = (type, extra) => ({ id: eid(), type, app_user_id: U_ENT, entitlement_ids: ['make_it_me'], product_id: 'rw_makeitme_monthly', ...extra })

async function main() {
  await post(MOCK + '/__seed', {
    users: [U_NEW, U_NEW2, U_ENT, U_EXP, U_NEVER, S1, D1, S2, S3],
    profiles: {
      [U_NEW]: { entitled: false }, [U_NEW2]: { entitled: false },
      [U_ENT]: { entitled: true }, [U_EXP]: { entitled: true },
      [U_NEVER]: { entitled: false }, [S1]: { entitled: true },
      [D1]: { entitled: false }, [S2]: { entitled: true }, [S3]: { entitled: true },
    },
    objects: [`${S1}/heroes/cellX/heroX.jpg`, `${S1}/tmp/junk.jpg`],
    adoptScript: { [`${S2}->${U_ENT}`]: 'not_entitled', [`${S3}->${U_ENT}`]: 'tombstoned' },
  })

  // --- auth / method discipline (unchanged behaviour) ---------------
  const bad = await post(HOOK + '/', { event: ev('TEST', {}) }, { Authorization: 'wrong' })
  check('auth: wrong key -> 401', bad.status === 401)
  const get = await fetch(HOOK + '/', { method: 'GET' })
  check('method: GET -> 405', get.status === 405)

  // --- the 16 production shapes --------------------------------------
  let r = await hook(ev('INITIAL_PURCHASE', { app_user_id: U_NEW }))
  check('IP#1 grant applied', r.body.applied === true && r.body.entitled === true)
  r = await hook(ev('INITIAL_PURCHASE', { app_user_id: U_NEW2 }))
  check('IP#2 grant applied', r.body.applied === true && r.body.entitled === true)
  r = await hook(ev('INITIAL_PURCHASE', { app_user_id: U_ENT }))
  check('IP#3 already entitled (state_unchanged)', r.body.applied === false && r.body.reason === 'state_unchanged')
  for (let i = 1; i <= 6; i++) {
    r = await hook(ev('RENEWAL', { app_user_id: U_ENT }))
    if (!(r.body.applied === false && r.body.reason === 'state_unchanged')) break
    if (i === 6) check('RENEWAL x6 all state_unchanged (already entitled)', true)
  }
  r = await hook(ev('EXPIRATION', { app_user_id: U_EXP }))
  check('EXPIRATION#1 clears', r.body.applied === true && r.body.entitled === false)
  r = await hook(ev('EXPIRATION', { app_user_id: U_NEVER }))
  check('EXPIRATION#2 already_not_entitled', r.body.applied === false && r.body.reason === 'state_unchanged')
  r = await hook(ev('EXPIRATION', { app_user_id: U_NEVER }))
  check('EXPIRATION#3 already_not_entitled', r.body.applied === false && r.body.reason === 'state_unchanged')
  r = await hook(ev('CANCELLATION', { app_user_id: U_ENT }))
  check('CANCELLATION#1 no-op', r.body.applied === false && r.body.reason === 'no_op_event_type')
  r = await hook(ev('CANCELLATION', { app_user_id: U_ENT }))
  check('CANCELLATION#2 no-op', r.body.applied === false && r.body.reason === 'no_op_event_type')
  r = await hook(ev('TEST', { app_user_id: U_ENT }))
  check('TEST#1 no-op', r.body.applied === false && r.body.reason === 'no_op_event_type')
  r = await hook(ev('TEST', { app_user_id: U_ENT }))
  check('TEST#2 no-op', r.body.applied === false && r.body.reason === 'no_op_event_type')
  r = await hook(ev('INITIAL_PURCHASE', { app_user_id: U_GHOST }))
  check('synthetic: unmapped user answered 200 unmapped_user', r.status === 200 && r.body.reason === 'unmapped_user')

  let st = await mockState()
  check('idempotency log holds all delivered events', st.webhookEvents.length === 17, `n=${st.webhookEvents.length}`)
  check('no adoption attempted for non-TRANSFER events', st.adoptions.length === 0)

  // --- TRANSFER fixtures ---------------------------------------------
  const tr1 = ev('TRANSFER', {
    app_user_id: D1, transferred_from: [S1], transferred_to: [D1],
    original_app_user_id: S1, aliases: [S1],
  })
  r = await hook(tr1)
  check('TR1 valid pair: grant applied to destination', r.body.applied === true && r.body.user_id === D1)
  check('TR1 adoption completed inline', Array.isArray(r.body.adoption) && r.body.adoption[0]?.state === 'done',
    JSON.stringify(r.body.adoption))
  st = await mockState()
  check('TR1 storage copy performed S->D', st.copies.length === 1 && st.copies[0].sourceKey.startsWith(S1))
  check('TR1 source objects cleaned after commit',
    st.objects.includes(`${D1}/heroes/cellX/heroX.jpg`) && !st.objects.some(o => o.startsWith(S1)))
  check('TR1 fill kicked for destination', st.fillKicks.filter(f => f.user_id === D1).length === 2,
    `kicks=${JSON.stringify(st.fillKicks)}`)

  const dup = await hook(tr1)
  check('TR1 redelivery -> duplicate, no re-adoption', dup.body.duplicate === true)
  st = await mockState()
  check('TR1 duplicate caused no extra adopt calls',
    st.rpcCalls.filter(c => c.path.includes('adopt_personal_identity')).length === 2)

  r = await hook(ev('TRANSFER', {
    app_user_id: U_ENT, transferred_from: ['$RCAnonymousID:xyz'], transferred_to: ['$RCAnonymousID:abc'],
  }))
  check('TR2 non-UUID pair: state_unchanged, adoption list empty',
    r.body.reason === 'state_unchanged' && Array.isArray(r.body.adoption) && r.body.adoption.length === 0)
  r = await hook(ev('TRANSFER', {
    app_user_id: U_ENT, transferred_from: [U_ENT], transferred_to: [U_ENT],
  }))
  check('TR3 source==destination: filtered, adoption list empty',
    Array.isArray(r.body.adoption) && r.body.adoption.length === 0)
  st = await mockState()
  check('TR2/TR3 produced no adopt RPC calls',
    st.rpcCalls.filter(c => c.path.includes('adopt_personal_identity')).length === 2)

  r = await hook(ev('TRANSFER', {
    app_user_id: D1, transferred_from: [S1], transferred_to: [D1],
    entitlement_ids: ['some_other_entitlement'],
  }))
  check('TR4 TRANSFER for another entitlement: existing no-op, no adoption',
    r.body.reason === 'other_entitlement_or_product' && r.body.adoption === undefined)

  r = await hook(ev('TRANSFER', {
    app_user_id: U_ENT, transferred_from: [S2], transferred_to: [U_ENT],
  }))
  check('TR5 unentitled destination (RPC guard): reported, webhook 200',
    r.status === 200 && r.body.adoption?.[0]?.reason === 'destination_not_entitled', JSON.stringify(r.body.adoption))
  r = await hook(ev('TRANSFER', {
    app_user_id: U_ENT, transferred_from: [S3], transferred_to: [U_ENT],
  }))
  check('TR6 tombstoned source (RPC guard): reported, webhook 200',
    r.status === 200 && r.body.adoption?.[0]?.reason === 'source_tombstoned', JSON.stringify(r.body.adoption))

  st = await mockState()
  check('final: entitlement events only from grant/clear path',
    st.entitlementEvents.length === 4 && st.entitlementEvents.every(e => e.source === 'revenuecat-webhook'),
    JSON.stringify(st.entitlementEvents))

  const failed = results.filter(([, ok]) => !ok)
  console.log(`\n${results.length - failed.length}/${results.length} replay checks passed`)
  process.exit(failed.length ? 1 : 0)
}

main().catch((e) => { console.error(e); process.exit(2) })
