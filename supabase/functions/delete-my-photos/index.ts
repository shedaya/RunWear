// Supabase Edge Function: delete-my-photos (RW-14, 2026-10-31)
//
// The §4 delete control, as an API: "Delete my photos" removes, for the
// CALLER only (user id from the verified JWT, never the body):
//   * all reference photos (DB rows + storage objects)
//   * all generated personalized heroes (DB rows + storage objects)
//   * all cell version markers, queued/processing personal jobs,
//     consent records, and entitlement events
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

    // Collect every storage object this user owns in the bucket:
    // DB rows first (authoritative), then a prefix listing sweep so
    // orphaned objects (e.g. a hero whose row insert failed) go too.
    const paths = new Set<string>()
    const { data: refs } = await sb
      .from('personal_reference_photos').select('storage_path').eq('user_id', userId)
    for (const r of refs ?? []) paths.add(r.storage_path as string)
    const { data: heroes } = await sb
      .from('personal_heroes').select('storage_path').eq('user_id', userId)
    for (const h of heroes ?? []) paths.add(h.storage_path as string)

    for (const folder of ['refs', 'heroes', 'tmp']) {
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

    // Rows. Jobs first (FK to heroes), then heroes (bumps cell versions
    // via trigger — the cell rows are deleted right after anyway).
    const counts: Record<string, number> = {}
    const tables = [
      'personal_generation_jobs', 'personal_heroes', 'personal_cells',
      'personal_reference_photos', 'personal_consents',
      'personal_entitlement_events', 'personal_profiles'
    ]
    for (const table of tables) {
      const { count, error } = await sb
        .from(table)
        .delete({ count: 'exact' })
        .eq('user_id', userId)
      if (error) throw error
      counts[table] = count ?? 0
    }

    return jsonResponse({
      ok: true,
      user_id: userId,
      storage_objects_removed: objectsRemoved,
      rows_removed: counts,
      personalization_on: false
    })
  } catch (error) {
    console.error('[delete-my-photos] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
