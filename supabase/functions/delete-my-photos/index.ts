// Supabase Edge Function: delete-my-photos (RW-14, 2026-10-31;
// RW-16.30 deletion-completeness fix, 2026-11-02)
//
// The §4 delete control, as an API: "Delete my photos" removes, for the
// CALLER only (user id from the verified JWT, never the body):
//   * all reference photos (DB rows + storage objects), INCLUDING the
//     refs-small/ downscaled working copies personal-ref-prep derives
//     from them — every stored derivative of the reference photo
//   * all generated personalized heroes (DB rows + storage objects)
//   * any orphaned repair/ temp objects the worker's best-effort
//     cleanup left behind (the <uid>/repair/ prefix)
//   * all cell version markers, queued/processing personal jobs,
//     consent records, entitlement events, and personal hero flags
//   * the RW-16.30 scaffold + fallback event rows (this function
//     predates them; they are user-keyed usage rows and go too)
//   * the gate-verdict cache rows for THIS user's hero image hashes
//     (personal_gate_verdicts is keyed by image sha256 with no user
//     column; a hash another user's hero still carries is KEPT, so
//     the shared cache keeps working for everyone else)
//   * the profile row itself (personalization fully off)
// Idempotent: calling it with nothing to delete returns ok with zero
// counts. Storage objects are removed first (collected from DB rows),
// then rows; a storage failure aborts before rows are touched so the
// operation can simply be retried.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

// The refs-small derivative path for a reference object, mirroring
// personal-ref-prep / the worker: <uid>/refs/<stem>.<ext> is worked
// from <uid>/refs-small/<stem>.jpg. The derivative is recorded in no
// DB row, so the path is derived here as a belt-and-braces addition
// to the prefix-listing sweep below.
export function derivativePathFor(sourcePath: string): string {
  const slash = sourcePath.lastIndexOf('/')
  const dir = sourcePath.slice(0, slash)               // <uid>/refs
  const base = sourcePath.slice(slash + 1)             // ref-1.jpg
  const parent = dir.slice(0, dir.lastIndexOf('/'))    // <uid>
  const stem = base.replace(/\.[^.]+$/, '')
  return `${parent}/refs-small/${stem}.jpg`
}

const CHUNK = 100
function chunks<T>(items: T[]): T[][] {
  const out: T[][] = []
  for (let i = 0; i < items.length; i += CHUNK) out.push(items.slice(i, i + CHUNK))
  return out
}

export async function deleteUserPhotos(
  // deno-lint-ignore no-explicit-any
  sb: any,
  userId: string
): Promise<{ storage_objects_removed: number, rows_removed: Record<string, number> }> {
  // Collect every storage object this user owns in the bucket:
  // DB rows first (authoritative), then a prefix listing sweep so
  // orphaned objects (e.g. a hero whose row insert failed) go too.
  const paths = new Set<string>()
  const { data: refs } = await sb
    .from('personal_reference_photos').select('storage_path').eq('user_id', userId)
  for (const r of refs ?? []) {
    const p = r.storage_path as string
    paths.add(p)
    paths.add(derivativePathFor(p))
  }
  const { data: heroes } = await sb
    .from('personal_heroes').select('storage_path, image_hash').eq('user_id', userId)
  const heroHashes = new Set<string>()
  for (const h of heroes ?? []) {
    paths.add(h.storage_path as string)
    if (h.image_hash) heroHashes.add(h.image_hash as string)
  }

  // Every folder a user's objects can live under: refs (originals),
  // refs-small (prep derivatives), heroes/<combination_id>/ (banked
  // pictures), repair (worker temp src/mask pairs, removed best-effort
  // by the worker — this sweep is the guarantee), tmp (legacy).
  for (const folder of ['refs', 'refs-small', 'heroes', 'repair', 'tmp']) {
    const { data: listing } = await sb.storage
      .from('personal-heroes')
      .list(`${userId}/${folder}`, { limit: 1000 })
    for (const o of listing ?? []) {
      // One more level for heroes/<combination_id>/<file>
      if (o.id === null && folder === 'heroes') {
        const { data: inner } = await sb.storage
          .from('personal-heroes')
          .list(`${userId}/heroes/${o.name}`, { limit: 1000 })
        for (const f of inner ?? []) paths.add(`${userId}/heroes/${o.name}/${f.name}`)
      } else {
        paths.add(`${userId}/${folder}/${o.name}`)
      }
    }
  }

  let objectsRemoved = 0
  const pathList = [...paths]
  if (pathList.length > 0) {
    const { data: removed, error: removeError } = await sb.storage
      .from('personal-heroes')
      .remove(pathList)
    if (removeError) throw removeError
    objectsRemoved = removed?.length ?? 0
  }

  const counts: Record<string, number> = {}

  // Gate-verdict cache sweep (RW-16.30 privacy fix 3): verdict rows
  // carry no user id, so the join is the hero rows collected above.
  // A hash another user's surviving hero still references is kept —
  // deleting it would only make that user pay for a re-gate; a hash
  // that joins to nothing after this deletion goes with the photos.
  if (heroHashes.size > 0) {
    const shared = new Set<string>()
    for (const chunk of chunks([...heroHashes])) {
      const { data: others } = await sb
        .from('personal_heroes')
        .select('image_hash')
        .in('image_hash', chunk)
        .neq('user_id', userId)
      for (const o of others ?? []) shared.add(o.image_hash as string)
    }
    const sweep = [...heroHashes].filter(h => !shared.has(h))
    let verdictsRemoved = 0
    for (const chunk of chunks(sweep)) {
      const { count, error } = await sb
        .from('personal_gate_verdicts')
        .delete({ count: 'exact' })
        .in('image_hash', chunk)
      if (error) throw error
      verdictsRemoved += count ?? 0
    }
    counts['personal_gate_verdicts'] = verdictsRemoved
  }

  // Rows. Jobs first (FK to heroes), then the RW-16.30 event rows
  // (fallback events point at jobs with ON DELETE SET NULL; scaffold
  // events carry a bare hero id), then heroes (bumps cell versions
  // via trigger — the cell rows are deleted right after anyway).
  const tables = [
    'personal_generation_jobs', 'personal_scaffold_events',
    'personal_fallback_events', 'personal_heroes', 'personal_cells',
    'personal_reference_photos', 'personal_consents',
    'personal_entitlement_events', 'personal_hero_flags',
    'personal_profiles'
  ]
  for (const table of tables) {
    const { count, error } = await sb
      .from(table)
      .delete({ count: 'exact' })
      .eq('user_id', userId)
    if (error) throw error
    counts[table] = count ?? 0
  }

  return { storage_objects_removed: objectsRemoved, rows_removed: counts }
}

if (import.meta.main) {
  serve(async (req) => {
    try {
      if (req.method !== 'POST') return jsonResponse({ error: 'Method not allowed' }, 405)

      const authHeader = req.headers.get('Authorization') ?? ''
      const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
        global: { headers: { Authorization: authHeader } }
      })
      const { data: userData, error: userError } = await userClient.auth.getUser()
      if (userError || !userData.user) return jsonResponse({ error: 'Unauthorized' }, 401)
      const userId = userData.user.id

      const sb = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
      const result = await deleteUserPhotos(sb, userId)

      return jsonResponse({
        ok: true,
        user_id: userId,
        storage_objects_removed: result.storage_objects_removed,
        rows_removed: result.rows_removed,
        personalization_on: false
      })
    } catch (error) {
      console.error('[delete-my-photos] error:', error)
      return jsonResponse({ error: (error as Error).message }, 500)
    }
  })
}
