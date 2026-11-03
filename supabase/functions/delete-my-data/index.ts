// Supabase Edge Function: delete-my-data (RW-16.30, 2026-11-02)
// Privacy package decision 5(a), adopted by Sol 2026-11-02 ~22:05 EST:
// build the full "Delete my data" control.
//
// "Delete my data" removes EVERYTHING RunWear holds server-side for
// the CALLER (user id from the verified JWT, never the body):
//   1. The complete photo sweep — delegated to delete-my-photos'
//      exported core (deleteUserPhotos), so the two controls can
//      never drift: reference originals + refs-small derivatives +
//      banked heroes + repair/tmp orphans (storage), generation jobs,
//      scaffold/fallback events, cells, consents, entitlement events,
//      hero flags, the profile row, and the gate-verdict cache rows
//      joined to the caller's hero hashes alone.
//   2. The RevenueCat webhook processing log rows keyed to the
//      caller (personal_webhook_events — the one user-keyed table
//      delete-my-photos does not own; it is an event log, and a full
//      account deletion takes it too).
//   3. The account itself (auth.users) — LAST, via the admin API.
//      After this the caller's session is dead; the client signs out
//      and any later use mints a fresh anonymous account.
//
// Ordering matters, twice over:
//   - personal_webhook_events.user_id is ON DELETE SET NULL, so the
//     log sweep must run before the account goes.
//   - Deleting an auth.users row FAILS for any user who still has
//     hero rows (verified live by the deletion-completeness lane,
//     2026-11-02): the personal_heroes_bump_version trigger inserts
//     a personal_cells row on hero DELETE, and mid-cascade that
//     insert references the departing user — FK violation
//     (SQL 23503; the GoTrue Admin API answers 500). Step 1 defuses
//     it by construction: heroes are emptied inside the sweep, and
//     the sweep deletes cells AFTER heroes, so the trigger's own
//     inserted rows are swept too; by step 3 no hero or cell row
//     remains for the cascade to trip over. Any future change to
//     this order must preserve content rows → cells → account, or
//     make the trigger cascade-safe first.
// Everything before step 3 is idempotent (count-based deletes), so a
// failure anywhere — including step 3 — is safe to retry: the retry
// re-runs the sweeps (all zeros) and re-attempts the account
// deletion. A "user not found" answer from the admin API is the
// desired end state, not an error.
//
// If step 3 fails for a real reason, the receipt says so
// (account_deleted: false) with HTTP 200: the data is gone, the
// account is not, and the client keeps the session so the user can
// retry — it must not claim a deletion that did not happen.
//
// NOT held / NOT deleted here, by construction (named so the copy
// never overclaims): purchase records live with Apple / Google /
// RevenueCat (deleting data does NOT cancel a subscription — the
// settings copy says so); hero_flags (the stock-pool flag table)
// carries no user column and cannot be joined to a caller; Wear
// I've Run collection entries have no server store in this build
// (they live on the user's phone; the client clears its local
// Make It Me state on this flow, and the collection's own deletion
// language governs the cards); platform backups cycle on the
// provider's schedule.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { deleteUserPhotos } from "../delete-my-photos/index.ts"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

export interface DeleteDataReceipt {
  storage_objects_removed: number
  rows_removed: Record<string, number>
  account_deleted: boolean
}

// The auth-account deletion is injected so tests can record it (and
// its position in the sequence) without an auth server; production
// passes the supabase-js admin call. Implementations signal an
// already-deleted account by throwing an error whose message matches
// /not found/i — treated as success (the end state is identical).
export type DeleteAuthUser = (userId: string) => Promise<void>

export async function deleteUserData(
  // deno-lint-ignore no-explicit-any
  sb: any,
  userId: string,
  deleteAuthUser: DeleteAuthUser
): Promise<DeleteDataReceipt> {
  // 1. Photos + every personal table + verdict cache (shared core).
  const photos = await deleteUserPhotos(sb, userId)
  const counts: Record<string, number> = { ...photos.rows_removed }

  // 2. Webhook processing log rows keyed to the caller.
  const { count: webhookCount, error: webhookError } = await sb
    .from('personal_webhook_events')
    .delete({ count: 'exact' })
    .eq('user_id', userId)
  if (webhookError) throw webhookError
  counts['personal_webhook_events'] = webhookCount ?? 0

  // 3. The account itself, last. A failure here is reported, not
  // thrown: the sweeps above already committed, and the receipt is
  // the client's only truthful signal to offer a retry.
  let accountDeleted = true
  try {
    await deleteAuthUser(userId)
  } catch (error) {
    const message = (error as Error).message ?? ''
    if (!/not found/i.test(message)) accountDeleted = false
  }

  return {
    storage_objects_removed: photos.storage_objects_removed,
    rows_removed: counts,
    account_deleted: accountDeleted
  }
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
      const result = await deleteUserData(sb, userId, async (id) => {
        const { error } = await sb.auth.admin.deleteUser(id)
        if (error) throw error
      })

      return jsonResponse({
        ok: true,
        user_id: userId,
        deleted_at: new Date().toISOString(),
        storage_objects_removed: result.storage_objects_removed,
        rows_removed: result.rows_removed,
        account_deleted: result.account_deleted,
        personalization_on: false
      })
    } catch (error) {
      console.error('[delete-my-data] error:', error)
      return jsonResponse({ error: (error as Error).message }, 500)
    }
  })
}
