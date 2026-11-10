// RW-137 S3 harness runner. Phase "reconcile" runs against the real
// reconcile-personal-entitlement on :8000; phase "request" against
// the real request-identity-adoption on :8000 (the driver script
// starts one at a time). Both point at the mock Supabase (:54399) and
// the mock RevenueCat API (same process, /rc/v1).
const MOCK = 'http://127.0.0.1:54399'
const FN = 'http://127.0.0.1:8000'

const D = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
const S_ALIAS = 'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1'
const S_ORIG = 'b2b2b2b2-b2b2-4b2b-8b2b-b2b2b2b2b2b2'
const R = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
const L = 'f1f1f1f1-f1f1-4f1f-8f1f-f1f1f1f1f1f1'
const FORGED = 'c3c3c3c3-c3c3-4c3c-8c3c-c3c3c3c3c3c3'

const results = []
function check(name, cond, extra = '') {
  results.push([name, !!cond])
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}  ${extra}`)
}
async function post(url, body, headers = {}) {
  const resp = await fetch(url, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  })
  return { status: resp.status, body: await resp.json().catch(() => null) }
}
const mockState = async () => (await (await fetch(MOCK + '/__state')).json())
const rcPayload = (uid, extra) => ({
  request_date: '2026-11-10T00:00:00Z',
  subscriber: {
    original_app_user_id: uid,
    entitlements: {
      make_it_me: { expires_date: null, product_identifier: 'rw_makeitme_monthly', purchase_date: '2026-11-01T00:00:00Z' },
    },
    subscriptions: { rw_makeitme_monthly: { is_sandbox: true, refunded_at: null } },
    ...extra,
  },
})

async function reconcilePhase() {
  await post(MOCK + '/__reset', {})
  await post(MOCK + '/__seed', {
    users: [D, S_ALIAS, S_ORIG],
    profiles: { [D]: { entitled: false } },
    jwtUsers: { 'tok-D': D },
  })
  // Shape 1: source exposed via aliases only.
  await post(`${MOCK}/__rc/${D}`, rcPayload(D, { aliases: [S_ALIAS, '$RCAnonymousID:zzz'] }))
  let r = await post(FN + '/', { platform: 'ios' }, { Authorization: 'Bearer tok-D' })
  check('reconcile: grant applied from RC record', r.body.applied === true && r.body.entitled === true)
  check('reconcile: adoption_available via aliases shape',
    r.body.adoption_available === S_ALIAS && r.body.adoption_via === 'aliases', JSON.stringify(r.body))
  // Shape 2: source exposed via original_app_user_id only.
  await post(`${MOCK}/__rc/${D}`, rcPayload(D, { original_app_user_id: S_ORIG, aliases: [] }))
  r = await post(FN + '/', { platform: 'ios' }, { Authorization: 'Bearer tok-D' })
  check('reconcile: state_unchanged on second read', r.body.applied === false && r.body.reason === 'state_unchanged')
  check('reconcile: adoption_available via original_app_user_id shape',
    r.body.adoption_available === S_ORIG && r.body.adoption_via === 'original_app_user_id')
  // No source exposed.
  await post(`${MOCK}/__rc/${D}`, rcPayload(D, { aliases: [] }))
  r = await post(FN + '/', {}, { Authorization: 'Bearer tok-D' })
  check('reconcile: no source -> adoption_available null', r.body.adoption_available === null)
  // Inactive record: downgrade confirmed on second read, no adoption.
  await post(`${MOCK}/__rc/${D}`, { subscriber: { original_app_user_id: S_ORIG, entitlements: {}, subscriptions: {} } })
  r = await post(FN + '/', {}, { Authorization: 'Bearer tok-D' })
  check('reconcile: confirmed downgrade clears, adoption null',
    r.body.applied === true && r.body.entitled === false && r.body.adoption_available === null, JSON.stringify(r.body))
  const st = await mockState()
  check('reconcile: no adoption RPC ever invoked by reconcile',
    st.rpcCalls.filter(c => c.path.includes('adopt')).length === 0)
}

async function requestPhase() {
  await post(MOCK + '/__reset', {})
  await post(MOCK + '/__seed', {
    users: [R, L, S_ALIAS],
    profiles: { [R]: { entitled: true }, [L]: { entitled: false } },
    jwtUsers: { 'tok-R': R, 'tok-L': L },
    objects: [`${S_ALIAS}/heroes/cellX/heroX.jpg`, `${S_ALIAS}/tmp/junk.jpg`],
  })
  await post(`${MOCK}/__rc/${R}`, rcPayload(R, { aliases: [S_ALIAS] }))
  await post(`${MOCK}/__rc/${L}`, { subscriber: { original_app_user_id: L, entitlements: {}, subscriptions: {} } })

  let r = await post(FN + '/', {}, {})
  check('request: unauthenticated -> 401', r.status === 401)

  r = await post(FN + '/', {}, { Authorization: 'Bearer tok-R' })
  check('request: happy path adopts', r.body.adopted === true && r.body.results?.[0]?.state === 'done',
    JSON.stringify(r.body))
  let st = await mockState()
  check('request: happy path ran 2-phase RPC + copy + cleanup + fill kick',
    st.rpcCalls.filter(c => c.path.includes('adopt_personal_identity')).length === 2 &&
    st.copies.length === 1 && st.removes.length === 1 &&
    st.fillKicks.some(f => f.user_id === R))

  r = await post(FN + '/', { source_user_id: S_ALIAS }, { Authorization: 'Bearer tok-R' })
  check('request: corroborated claim adopts', r.body.adopted === true)

  await post(MOCK + '/__seed', { adoptScript: { [`${S_ALIAS}->${R}`]: 'already' } })
  r = await post(FN + '/', {}, { Authorization: 'Bearer tok-R' })
  check('request: double adoption -> no-op with RPC reason',
    r.body.adopted === false && r.body.results?.[0]?.reason === 'source_already_adopted', JSON.stringify(r.body))

  const before = (await mockState()).rpcCalls.filter(c => c.path.includes('adopt_personal_identity')).length
  r = await post(FN + '/', { source_user_id: FORGED }, { Authorization: 'Bearer tok-R' })
  check('request: FORGED claim -> source_not_corroborated, no-op',
    r.body.adopted === false && r.body.reason === 'source_not_corroborated')
  st = await mockState()
  check('request: forged claim produced zero adopt RPC calls',
    st.rpcCalls.filter(c => c.path.includes('adopt_personal_identity')).length === before)

  r = await post(FN + '/', {}, { Authorization: 'Bearer tok-L' })
  check('request: lapsed entitlement -> not_entitled no-op',
    r.body.adopted === false && r.body.reason === 'not_entitled')
  st = await mockState()
  check('request: lapsed case produced zero adopt RPC calls',
    st.rpcCalls.filter(c => c.path.includes('adopt_personal_identity')).length === before)
}

const phase = process.argv[2]
;(phase === 'reconcile' ? reconcilePhase() : requestPhase())
  .then(() => {
    const failed = results.filter(([, ok]) => !ok)
    console.log(`\n${results.length - failed.length}/${results.length} ${phase} checks passed`)
    process.exit(failed.length ? 1 : 0)
  })
  .catch((e) => { console.error(e); process.exit(2) })
