// RW-16.30 tests for delete-my-data — the full account-data sweep.
//
// deleteUserData runs here against an in-memory fake of the Supabase
// client surface it (and the shared deleteUserPhotos core it builds
// on) uses — seed a full account footprint → invoke → assert absence
// per store/table, the same shape as the delete-my-photos suite and
// the deployed smoke battery. The auth-account deletion is injected;
// the fake records every operation so the tests can prove the
// account goes LAST (after the webhook-log sweep whose user_id would
// otherwise be nulled by its ON DELETE SET NULL foreign key).
//
// The fake also simulates the one production behavior a naive fake
// would hide (deletion-completeness lane finding, verified live
// 2026-11-02): the personal_heroes_bump_version trigger inserts a
// personal_cells row on every hero DELETE, and the real GoTrue
// admin delete FAILS (SQL 23503 → HTTP 500) when hero rows remain
// for the departing user. The 'production' auth behavior below
// enforces exactly that, so the full-sweep test — whose seed HAS
// heroes — passes only if the composed path empties heroes, then
// cells (sweeping the trigger's own inserts), then the account.
//
// Run: deno test supabase/functions/delete-my-data/
// (Local assert shims instead of std/testing, matching the repo's
// function suites: this VM's egress CA blocks deno.land fetches.)
function assert(cond: unknown, msg = 'assertion failed'): asserts cond {
  if (!cond) throw new Error(msg)
}
function assertEquals(actual: unknown, expected: unknown, msg?: string) {
  const a = JSON.stringify(actual), e = JSON.stringify(expected)
  if (a !== e) throw new Error(msg ?? `assertEquals: ${a} !== ${e}`)
}

import { deleteUserData } from "./index.ts"

// ---------------------------------------------------------------------
// Fake Supabase client (same surface as the delete-my-photos suite,
// plus an ops log)
// ---------------------------------------------------------------------
type Row = Record<string, unknown>
type Pred = (r: Row) => boolean

class FakeQuery {
  mode: 'select' | 'delete' = 'select'
  preds: Pred[] = []
  constructor(private db: FakeDb, private table: string) {}
  select(_cols: string) { this.mode = 'select'; return this }
  delete(_opts?: unknown) { this.mode = 'delete'; return this }
  eq(col: string, val: unknown) { this.preds.push(r => r[col] === val); return this }
  neq(col: string, val: unknown) { this.preds.push(r => r[col] !== val); return this }
  in(col: string, vals: unknown[]) { this.preds.push(r => vals.includes(r[col])); return this }
  then<T>(resolve: (v: { data?: Row[]; count?: number; error: null }) => T): Promise<T> {
    const rows = (this.db.tables[this.table] ?? []).filter(r => this.preds.every(p => p(r)))
    if (this.mode === 'delete') {
      const before = (this.db.tables[this.table] ?? []).length
      const removed = (this.db.tables[this.table] ?? []).filter(r => this.preds.every(p => p(r)))
      this.db.tables[this.table] = (this.db.tables[this.table] ?? []).filter(r => !this.preds.every(p => p(r)))
      this.db.ops.push(`delete:${this.table}`)
      // personal_heroes_bump_version (production trigger): every
      // hero DELETE inserts a personal_cells row for the hero's
      // combination. Simulated verbatim — a sweep that deletes
      // cells BEFORE heroes leaves these rows behind, and an auth
      // deletion attempted while heroes remain dies on them.
      if (this.table === 'personal_heroes') {
        const cells = this.db.tables['personal_cells'] ?? []
        for (const hero of removed) {
          cells.push({ user_id: hero.user_id, combination_id: hero.combination_id ?? 'unknown', bumped_by_trigger: true })
        }
        this.db.tables['personal_cells'] = cells
      }
      return Promise.resolve(resolve({ count: before - this.db.tables[this.table].length, error: null }))
    }
    return Promise.resolve(resolve({ data: rows.map(r => ({ ...r })), error: null }))
  }
}

class FakeStorage {
  constructor(private db: FakeDb) {}
  from(_bucket: string) { return this }
  list(prefix: string, _opts?: unknown) {
    const head = prefix + '/'
    const seen = new Map<string, { name: string; id: string | null }>()
    for (const p of this.db.objects) {
      if (!p.startsWith(head)) continue
      const rest = p.slice(head.length)
      const seg = rest.split('/')[0]
      if (!seen.has(seg)) {
        seen.set(seg, { name: seg, id: rest.includes('/') ? null : 'obj-' + seg })
      }
    }
    return Promise.resolve({ data: [...seen.values()], error: null })
  }
  remove(paths: string[]) {
    const removed: { name: string }[] = []
    for (const p of paths) if (this.db.objects.delete(p)) removed.push({ name: p })
    if (removed.length > 0) this.db.ops.push('storage:remove')
    return Promise.resolve({ data: removed, error: null })
  }
}

class FakeDb {
  tables: Record<string, Row[]> = {}
  objects = new Set<string>()
  ops: string[] = []
  storage = new FakeStorage(this)
  from(table: string) { return new FakeQuery(this, table) }
}

// ---------------------------------------------------------------------
// Seed: a FULL account footprint for caller A; user B must survive
// ---------------------------------------------------------------------
const A = 'user-a', B = 'user-b'
const USER_TABLES = [
  'personal_generation_jobs', 'personal_scaffold_events', 'personal_fallback_events',
  'personal_heroes', 'personal_cells', 'personal_reference_photos', 'personal_consents',
  'personal_entitlement_events', 'personal_hero_flags', 'personal_profiles',
  'personal_webhook_events'
]

function seed(): FakeDb {
  const db = new FakeDb()
  const t = (name: string, rows: Row[]) => { db.tables[name] = rows }
  t('personal_profiles', [{ user_id: A }, { user_id: B }])
  t('personal_reference_photos', [
    { user_id: A, storage_path: `${A}/refs/ref-1.jpg` },
    { user_id: B, storage_path: `${B}/refs/ref-1.jpg` }
  ])
  t('personal_heroes', [
    { user_id: A, combination_id: 'C1', storage_path: `${A}/heroes/C1/v1.png`, image_hash: 'H1' },
    { user_id: A, combination_id: 'C2', storage_path: `${A}/heroes/C2/v1.png`, image_hash: 'H2' },  // H2 shared with B
    { user_id: B, combination_id: 'C2', storage_path: `${B}/heroes/C2/v1.png`, image_hash: 'H2' }
  ])
  t('personal_generation_jobs', [{ user_id: A }, { user_id: B }])
  t('personal_cells', [{ user_id: A }, { user_id: B }])
  t('personal_consents', [{ user_id: A }, { user_id: B }])
  t('personal_entitlement_events', [{ user_id: A }, { user_id: B }])
  t('personal_hero_flags', [{ user_id: A }, { user_id: B }])
  t('personal_scaffold_events', [{ user_id: A }, { user_id: B }])
  t('personal_fallback_events', [{ user_id: A }, { user_id: B }])
  t('personal_webhook_events', [
    { event_id: 'evt-a1', user_id: A }, { event_id: 'evt-a2', user_id: A },
    { event_id: 'evt-b1', user_id: B },
    { event_id: 'evt-unmapped', user_id: null }
  ])
  t('personal_gate_verdicts', [
    { image_hash: 'H1', pass: true },   // A's hero only  -> swept by the shared core
    { image_hash: 'H2', pass: true }    // shared with B  -> kept
  ])
  for (const p of [
    `${A}/refs/ref-1.jpg`, `${A}/refs-small/ref-1.jpg`,
    `${A}/heroes/C1/v1.png`, `${A}/heroes/C2/v1.png`,
    // The completeness lane's full prefix inventory: worker repair
    // temps (best-effort cleanup in production, so orphans happen)
    // and the legacy tmp prefix — both must go too.
    `${A}/repair/job-x-src.png`, `${A}/tmp/scratch.png`,
    `${B}/refs/ref-1.jpg`, `${B}/refs-small/ref-1.jpg`, `${B}/heroes/C2/v1.png`
  ]) db.objects.add(p)
  return db
}

function recordingAuth(db: FakeDb, behavior: 'ok' | 'fail' | 'not-found' | 'production' = 'ok') {
  return (userId: string): Promise<void> => {
    db.ops.push(`auth:delete:${userId}`)
    if (behavior === 'fail') return Promise.reject(new Error('admin api unavailable'))
    if (behavior === 'not-found') return Promise.reject(new Error('User not found'))
    if (behavior === 'production') {
      // The real GoTrue behavior (verified live by the completeness
      // lane): deleting auth.users fails while hero rows remain —
      // the bump_version trigger's mid-cascade personal_cells
      // insert violates personal_cells_user_id_fkey (SQL 23503).
      const heroesRemain = (db.tables['personal_heroes'] ?? []).some(r => r.user_id === userId)
      if (heroesRemain) {
        return Promise.reject(new Error(
          '23503: insert into personal_cells violates foreign key constraint personal_cells_user_id_fkey (simulated GoTrue 500)'))
      }
    }
    return Promise.resolve()
  }
}

Deno.test('full sweep: every store emptied for the caller, account deleted last, B untouched', async () => {
  const db = seed()
  // 'production' auth: the account deletion is attempted for an
  // account WITH heroes — the exact case that breaks in production
  // (23503) if the ordering discipline is ever lost.
  const res = await deleteUserData(db, A, recordingAuth(db, 'production'))

  // Rows: caller gone from every user-keyed table INCLUDING the
  // webhook log; B and the unmapped webhook row untouched.
  for (const table of USER_TABLES) {
    assertEquals((db.tables[table] ?? []).filter(r => r.user_id === A).length, 0,
      `${table}: caller rows remain`)
    assert((db.tables[table] ?? []).some(r => r.user_id === B), `${table}: B lost rows`)
  }
  assert((db.tables['personal_webhook_events'] ?? []).some(r => r.event_id === 'evt-unmapped'),
    'unmapped webhook row was swept')
  assertEquals(res.rows_removed['personal_webhook_events'], 2)

  // Storage: nothing under the caller's prefix; B intact.
  assertEquals([...db.objects].filter(p => p.startsWith(A + '/')), [])
  assertEquals(res.storage_objects_removed, 6)

  // Verdict cache via the shared core: H1 swept, shared H2 kept.
  assertEquals((db.tables['personal_gate_verdicts'] ?? []).map(r => r.image_hash), ['H2'])

  // The trigger guard, counted: the caller's cells delete removed
  // THREE rows — the seeded one plus the two rows the
  // bump_version trigger inserted while the caller's two heroes
  // were being deleted. If cells were ever deleted before heroes,
  // the trigger's rows would survive (final-state assertion above
  // fails too) and this count reads 1.
  assertEquals(res.rows_removed['personal_cells'], 3)

  // Account: deleted, and deleted LAST — after heroes, after cells
  // (the trigger-ordering discipline), after the webhook sweep
  // (its user_id FK is ON DELETE SET NULL), after every table.
  assertEquals(res.account_deleted, true)
  const authIdx = db.ops.indexOf(`auth:delete:${A}`)
  assert(authIdx === db.ops.length - 1, `auth delete not last: ${JSON.stringify(db.ops)}`)
  assert(db.ops.indexOf('delete:personal_heroes') < db.ops.indexOf('delete:personal_cells'),
    'cells deleted before heroes — trigger rows would survive')
  assert(db.ops.indexOf('delete:personal_cells') < authIdx, 'account deleted before cells were swept')
  assert(db.ops.indexOf('delete:personal_webhook_events') < authIdx, 'webhook sweep after auth delete')
})

Deno.test('idempotent: a second invocation removes nothing and still reports the account deleted', async () => {
  const db = seed()
  await deleteUserData(db, A, recordingAuth(db))
  // Second call: the account is already gone, so the admin API
  // answers "not found" — the desired end state, not a failure.
  const res = await deleteUserData(db, A, recordingAuth(db, 'not-found'))
  assertEquals(res.storage_objects_removed, 0)
  for (const [table, n] of Object.entries(res.rows_removed)) {
    assertEquals(n, 0, `${table}: second call removed ${n}`)
  }
  assertEquals(res.account_deleted, true)
})

Deno.test('account-deletion failure is reported, not hidden: data gone, account_deleted false, retry completes', async () => {
  const db = seed()
  const res = await deleteUserData(db, A, recordingAuth(db, 'fail'))
  assertEquals(res.account_deleted, false)
  assertEquals((db.tables['personal_profiles'] ?? []).filter(r => r.user_id === A).length, 0,
    'data sweep should still have committed')

  const retry = await deleteUserData(db, A, recordingAuth(db))
  assertEquals(retry.account_deleted, true)
  for (const n of Object.values(retry.rows_removed)) assertEquals(n, 0)
})

Deno.test('production auth simulation bites: account deletion attempted with heroes present fails (23503)', async () => {
  const db = seed()
  let threw = false
  try {
    await recordingAuth(db, 'production')(A)
  } catch {
    threw = true
  }
  assert(threw, 'production auth accepted an account that still has heroes — the simulation is not enforcing the finding')
})

Deno.test('empty account: a caller with no personal data still gets the account deletion', async () => {
  const db = new FakeDb()
  const res = await deleteUserData(db, 'user-empty', recordingAuth(db))
  assertEquals(res.storage_objects_removed, 0)
  assertEquals(res.account_deleted, true)
  assert(db.ops.includes('auth:delete:user-empty'))
})
