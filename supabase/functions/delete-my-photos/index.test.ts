// RW-16.30 deletion-completeness tests for delete-my-photos.
//
// The function's core routine (deleteUserPhotos) runs here against an
// in-memory fake of the Supabase client surface it uses — the same
// seed → delete → assert-absence shape the deployed smoke battery
// uses against the real project. Per table and per storage prefix:
// everything the caller owns goes; everything another user owns
// stays; the gate-verdict sweep removes only the hashes that joined
// to the caller's heroes alone.
//
// Run: deno test supabase/functions/delete-my-photos/
// (Local assert shims instead of std/testing, matching the worker
// suite: this VM's egress CA blocks deno.land fetches.)
function assert(cond: unknown, msg = 'assertion failed'): asserts cond {
  if (!cond) throw new Error(msg)
}
function assertEquals(actual: unknown, expected: unknown, msg?: string) {
  const a = JSON.stringify(actual), e = JSON.stringify(expected)
  if (a !== e) throw new Error(msg ?? `assertEquals: ${a} !== ${e}`)
}

import { deleteUserPhotos, derivativePathFor } from "./index.ts"

// ---------------------------------------------------------------------
// Fake Supabase client
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
      const before = this.db.tables[this.table].length
      this.db.tables[this.table] = this.db.tables[this.table].filter(r => !this.preds.every(p => p(r)))
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
    return Promise.resolve({ data: removed, error: null })
  }
}

class FakeDb {
  tables: Record<string, Row[]> = {}
  objects = new Set<string>()
  storage = new FakeStorage(this)
  from(table: string) { return new FakeQuery(this, table) }
}

// ---------------------------------------------------------------------
// Seed: user A (the caller) + user B (must be untouched)
// ---------------------------------------------------------------------
const A = 'user-a', B = 'user-b'
const USER_TABLES = [
  'personal_generation_jobs', 'personal_scaffold_events', 'personal_fallback_events',
  'personal_heroes', 'personal_cells', 'personal_reference_photos', 'personal_consents',
  'personal_entitlement_events', 'personal_hero_flags', 'personal_profiles'
]

function seed(): FakeDb {
  const db = new FakeDb()
  const t = (name: string, rows: Row[]) => { db.tables[name] = rows }
  t('personal_profiles', [{ user_id: A }, { user_id: B }])
  t('personal_reference_photos', [
    { user_id: A, storage_path: `${A}/refs/ref-1.jpg` },
    { user_id: A, storage_path: `${A}/refs/ref-2.png` },
    { user_id: B, storage_path: `${B}/refs/ref-1.jpg` }
  ])
  t('personal_heroes', [
    { user_id: A, storage_path: `${A}/heroes/C1/v1.png`, image_hash: 'H1' },
    { user_id: A, storage_path: `${A}/heroes/C2/v1.png`, image_hash: 'H2' },  // H2 shared with B
    { user_id: A, storage_path: `${A}/heroes/C2/v2.png`, image_hash: null },
    { user_id: B, storage_path: `${B}/heroes/C2/v1.png`, image_hash: 'H2' }
  ])
  t('personal_generation_jobs', [{ user_id: A }, { user_id: A }, { user_id: B }])
  t('personal_cells', [{ user_id: A }, { user_id: A }, { user_id: B }])
  t('personal_consents', [{ user_id: A }, { user_id: B }])
  t('personal_entitlement_events', [{ user_id: A }, { user_id: B }])
  t('personal_hero_flags', [{ user_id: A }, { user_id: B }])
  t('personal_scaffold_events', [{ user_id: A }, { user_id: A }, { user_id: B }])
  t('personal_fallback_events', [{ user_id: A }, { user_id: B }])
  t('personal_gate_verdicts', [
    { image_hash: 'H1', pass: true },   // A's hero only  -> swept
    { image_hash: 'H2', pass: true },   // shared with B  -> kept
    { image_hash: 'H3', pass: false }   // no hero joins  -> kept
  ])
  for (const p of [
    `${A}/refs/ref-1.jpg`, `${A}/refs/ref-2.png`,
    `${A}/refs-small/ref-1.jpg`, `${A}/refs-small/ref-2.jpg`,   // derivatives (defect 1)
    `${A}/heroes/C1/v1.png`, `${A}/heroes/C2/v1.png`, `${A}/heroes/C2/v2.png`,
    `${A}/heroes/C3/v9.png`,                                    // orphan, no DB row
    `${A}/repair/job-x-src.png`, `${A}/repair/job-x-mask.png`,  // orphaned worker temps
    `${A}/tmp/scratch.png`,
    `${B}/refs/ref-1.jpg`, `${B}/refs-small/ref-1.jpg`, `${B}/heroes/C2/v1.png`
  ]) db.objects.add(p)
  return db
}

Deno.test('derivative path mirrors personal-ref-prep', () => {
  assertEquals(derivativePathFor('u/refs/ref-1.jpg'), 'u/refs-small/ref-1.jpg')
  assertEquals(derivativePathFor('u/refs/photo.final.png'), 'u/refs-small/photo.final.jpg')
})

Deno.test('delete sweeps every store for the caller, nothing of anyone else', async () => {
  const db = seed()
  const res = await deleteUserPhotos(db, A)

  // Rows: caller gone from every user-keyed table; B untouched.
  for (const table of USER_TABLES) {
    assertEquals((db.tables[table] ?? []).filter(r => r.user_id === A).length, 0,
      `${table}: caller rows remain`)
    assert((db.tables[table] ?? []).some(r => r.user_id === B), `${table}: B lost rows`)
  }

  // Storage: nothing under the caller's prefix survives — originals,
  // refs-small derivatives, banked + orphaned heroes, repair temps.
  assertEquals([...db.objects].filter(p => p.startsWith(A + '/')), [],
    'caller storage objects remain')
  for (const p of [`${B}/refs/ref-1.jpg`, `${B}/refs-small/ref-1.jpg`, `${B}/heroes/C2/v1.png`]) {
    assert(db.objects.has(p), `B object missing: ${p}`)
  }
  assertEquals(res.storage_objects_removed, 11)

  // Verdict cache: H1 (caller's hero alone) swept; H2 survives
  // because B's hero still joins to it; H3 was never the caller's.
  const verdicts = (db.tables['personal_gate_verdicts'] ?? []).map(r => r.image_hash).sort()
  assertEquals(verdicts, ['H2', 'H3'])
  assertEquals(res.rows_removed['personal_gate_verdicts'], 1)

  // Reported counts match the seed.
  assertEquals(res.rows_removed['personal_reference_photos'], 2)
  assertEquals(res.rows_removed['personal_heroes'], 3)
  assertEquals(res.rows_removed['personal_scaffold_events'], 2)
  assertEquals(res.rows_removed['personal_fallback_events'], 1)
  assertEquals(res.rows_removed['personal_profiles'], 1)
})

Deno.test('delete is idempotent — a second call removes nothing', async () => {
  const db = seed()
  await deleteUserPhotos(db, A)
  const res = await deleteUserPhotos(db, A)
  assertEquals(res.storage_objects_removed, 0)
  for (const [table, n] of Object.entries(res.rows_removed)) {
    assertEquals(n, 0, `${table}: second call removed ${n}`)
  }
})
