// Supabase Edge Function: reconcile-personal-entitlement (RW-16.12 Lane B, 2026-10-31)
//
// The entitlement backstop. The revenuecat-webhook function is the
// primary path that writes personal_profiles.entitled, but it only
// fires when RevenueCat delivers an event — and RW-16.12 proved that
// hop can be silently dark (a completed purchase, zero lifetime events
// project-wide, the flag never set, start-personal-fill refusing every
// client kick with not_entitled, the matrix never built). This
// function is the reconciliation path that cannot share that failure
// mode: it asks RevenueCat directly what state the user is in and
// brings the server flag to match.
//
// Trust model: the client (or an operator) merely ASSERTS "a purchase
// completed" by calling this function. The assertion is never trusted —
// the function verifies the user's subscriber record against the
// RevenueCat REST API (GET /v1/subscribers/{app_user_id}) and only then
// acts. RevenueCat's record is the system that validated the store
// receipt, so a forged assertion cannot manufacture an entitlement.
// Identity is never taken from the request body on the user path:
// the caller reconciles THEMSELF (uid from the verified JWT). The
// service-role path (same credential detection as start-personal-fill)
// takes { "user_id": "..." } for operator / scheduled use.
//
// RW-137 (2026-11-25): this function is also the bank resolver.
// After the entitlement is verified, an entitled caller is resolved
// to the uid holding their SUBSCRIPTION's bank (subscription-as-
// identity, Sol's ruling Nov 7, 2026) via public.resolve_
// subscription_bank, and the outcome rides the response as `bank`
// ({state: restored|own|new, looks, collision}) so the client can
// show a restored user what actually happened — the retired adoption
// mechanism failed SILENTLY (ok:true, adoption_available:null, bank
// stranded); this one reports its result on every call. See the
// key-derivation comment block below and fix report §21.
//
// RC API keys: the subscriber read uses the project's PUBLIC SDK keys
// (function secrets REVENUECAT_PUBLIC_KEY_IOS / _ANDROID), the same
// keys shipped inside the apps — the v1 subscriber GET is a public-key
// operation (it is how the SDK itself reads CustomerInfo). Every
// configured key is tried and the reads are merged, because a
// subscriber's purchases may sit under either store's app. Upgrade
// path, when a dashboard hand next touches RevenueCat: mint a
// read-only SECRET key and swap these secrets — no code change.
//
// Behavior (mirrors revenuecat-webhook's discipline):
// - Calls public.set_personal_entitlement ONLY on an actual
//   transition, with source 'entitlement-reconciliation', so the
//   personal_entitlement_events audit trail holds one row per real
//   change and the path that made it is unambiguous.
// - Entitlement active in RC := entitlements.make_it_me present with
//   expires_date null or in the future, and the underlying
//   subscription not refunded (RC also drops refunded entitlements;
//   checked anyway).
// - On a false->true grant, kicks start-personal-fill server-side
//   (service role + user_id) — the fill does not depend on the client
//   kicking again in a later session. A failed kick never fails the
//   reconciliation; it is reported in the response.
// - On true->false (RC positively shows no active entitlement), the
//   flag is cleared with the same source. An RC outage or malformed
//   response NEVER clears a flag: no usable record, no change.
// - Android caveat, inherited from the webhook: until the Android
//   client calls Purchases.logIn(supabaseUid), Android purchases live
//   under RC's anonymous id and cannot be found by Supabase uid here
//   either. iOS configures Purchases with the Supabase uid and maps
//   directly.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const ENTITLEMENT_ID = 'make_it_me'
const SOURCE = 'entitlement-reconciliation'
const RC_API = 'https://api.revenuecat.com/v1'

const RC_KEYS = [
  Deno.env.get('REVENUECAT_PUBLIC_KEY_IOS'),
  Deno.env.get('REVENUECAT_PUBLIC_KEY_ANDROID'),
].filter((k): k is string => typeof k === 'string' && k.length > 0)

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

interface RcEntitlementState {
  // 'active' | 'inactive' — a usable RC record was read.
  // 'unknown' — no configured key produced a usable record (outage,
  // bad key, malformed payload). Callers must not mutate state on it.
  state: 'active' | 'inactive' | 'unknown'
  productId: string | null
  expiresDate: string | null
  isSandbox: boolean | null
  // RW-137 subscription identity (2026-11-25): the subscriber's
  // original_app_user_id and the subscription's store, read from the
  // same verified payload. These feed bank-key derivation below —
  // they are evidence, never client-asserted.
  originalAppUserId: string | null
  store: string | null
  // 'PURCHASED' | 'FAMILY_SHARED' when the subscription record
  // carries it. A family-shared entitlement confers Premium
  // features but NEVER bank identity: the bank belongs to the
  // purchaser's subscription, so resolution is skipped for it.
  ownershipType: string | null
}

const EMPTY_RC: Pick<RcEntitlementState, 'originalAppUserId' | 'store' | 'ownershipType'> = {
  originalAppUserId: null, store: null, ownershipType: null,
}

// Pure decision over one RC subscriber payload — extracted so the
// rule can be read (and tested) without the network around it.
export function entitlementFromSubscriber(subscriber: unknown): RcEntitlementState {
  const sub = (subscriber as Record<string, unknown> | null)?.subscriber as
    | Record<string, unknown> | undefined
  if (!sub || typeof sub !== 'object') {
    return { state: 'unknown', productId: null, expiresDate: null, isSandbox: null, ...EMPTY_RC }
  }
  const originalAppUserId = typeof sub.original_app_user_id === 'string'
    ? sub.original_app_user_id : null
  const entitlements = (sub.entitlements ?? {}) as Record<string, Record<string, unknown>>
  const ent = entitlements[ENTITLEMENT_ID]
  if (!ent) {
    return { state: 'inactive', productId: null, expiresDate: null, isSandbox: null, originalAppUserId, store: null, ownershipType: null }
  }

  const expires = typeof ent.expires_date === 'string' ? ent.expires_date : null
  if (expires !== null && Date.parse(expires) <= Date.now()) {
    return { state: 'inactive', productId: null, expiresDate: expires, isSandbox: null, originalAppUserId, store: null, ownershipType: null }
  }
  const productId = typeof ent.product_identifier === 'string' ? ent.product_identifier : null

  // Refund check on the underlying subscription record, when present.
  let isSandbox: boolean | null = null
  let store: string | null = null
  let ownershipType: string | null = null
  if (productId) {
    const subscriptions = (sub.subscriptions ?? {}) as Record<string, Record<string, unknown>>
    const record = subscriptions[productId]
    if (record) {
      if (typeof record.refunded_at === 'string') {
        return { state: 'inactive', productId: null, expiresDate: expires, isSandbox: null, originalAppUserId, store: null, ownershipType: null }
      }
      if (typeof record.is_sandbox === 'boolean') isSandbox = record.is_sandbox
      if (typeof record.store === 'string') store = record.store
      if (typeof record.ownership_type === 'string') ownershipType = record.ownership_type
    }
  }
  return { state: 'active', productId, expiresDate: expires, isSandbox, originalAppUserId, store, ownershipType }
}

// ---------------------------------------------------------------------
// RW-137 subscription-as-identity: bank-key derivation (pure).
//
// The bank is keyed to the SUBSCRIPTION (Sol's ruling, Nov 7, 2026),
// not to the device uid. The Nov 10 mechanism (adopt the bank when
// RC's original_app_user_id / aliases name a prior uid) never once
// executed — the Nov 25, 2026 restore exercise proved the v1 payload
// carries no aliases field and a restore TRANSFER does not merge
// customers, so the destination's original_app_user_id cannot be
// trusted to name the source. The identifiers that DO survive a
// delete-and-reinstall live in RevenueCat WEBHOOK events:
// original_transaction_id (constant for a subscription: Apple per
// subscription group + Apple ID; Google Play the first order id,
// whose per-renewal '..N' suffix we strip) and the TRANSFER event's
// transferred_from / transferred_to. revenuecat-webhook records them
// verbatim in personal_subscription_links; this function assembles
// the candidate keys from that evidence plus the verified RC record,
// strongest first, and public.resolve_subscription_bank (migration
// 20261126010000) does the registry work.
//
// This function intentionally does NOT emit the retired
// `adoption_available` field: deployed v11 emits it, shipped clients
// parse it optionally and treat its absence as "no adoption" — which
// is now literally true, because resolution replaces adoption.
// ---------------------------------------------------------------------

export function normalizeOriginalTransactionId(otid: string): string {
  // Google Play order ids: 'GPA.<base>..<n>' per renewal — the base
  // identifies the subscription chain. Apple ids carry no suffix.
  return otid.split('..')[0]
}

export function storeSubscriptionKey(store: string | null, otid: string | null): string | null {
  if (!otid || otid.trim() === '') return null
  return `store:${store && store.trim() !== '' ? store : 'unknown'}:${normalizeOriginalTransactionId(otid)}`
}

export function rcSubscriptionKey(originalAppUserId: string | null): string | null {
  if (!originalAppUserId || originalAppUserId.trim() === '') return null
  return `rc:${originalAppUserId}`
}

export interface CandidateKey { key: string; source: string }

export interface SubscriptionLinkRow {
  subscription_key: string | null
  event_type: string
  transferred_from: string | null
}

// Assemble ordered candidate keys: webhook-evidence keys first (in
// evidence order, deduped), then the rc original key from the
// verified subscriber record.
export function assembleCandidateKeys(
  linkRows: SubscriptionLinkRow[],
  originalAppUserId: string | null,
): CandidateKey[] {
  const out: CandidateKey[] = []
  const seen = new Set<string>()
  for (const row of linkRows) {
    const key = row.subscription_key
    if (!key || seen.has(key)) continue
    seen.add(key)
    out.push({ key, source: row.event_type === 'TRANSFER' ? 'transfer' : 'store_otid' })
  }
  const rcKey = rcSubscriptionKey(originalAppUserId)
  if (rcKey && !seen.has(rcKey)) out.push({ key: rcKey, source: 'rc_original' })
  return out
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export interface BankOutcome {
  state: string          // 'restored' | 'own' | 'new'
  looks: number          // heroes held by the resolved bank
  collision: boolean
  bankUserId: string
}

// Resolve the caller's bank from their VERIFIED entitlement record.
// Returns null when resolution is unavailable (registry not yet
// deployed, RPC error) — reconcile then behaves exactly as it did
// before RW-137, because a resolution outage must never break the
// entitlement backstop.
async function resolveBank(
  // Loosely typed on purpose: the registry tables/RPC are new and
  // not in any generated Database type; runtime shape is asserted
  // by the fixture tests, not the checker.
  admin: SupabaseClient<any, any, any>,
  userId: string,
  rc: RcEntitlementState,
  platform: string,
): Promise<BankOutcome | null> {
  // A family-shared entitlement is not the caller's subscription:
  // never resolve it to the purchaser's bank (and, downstream,
  // never let a delete touch that bank through this caller).
  if (rc.ownershipType === 'FAMILY_SHARED') return null
  try {
    const original = rc.originalAppUserId
    const orParts = [
      `app_user_id.eq.${userId}`,
      `original_app_user_id.eq.${userId}`,
      `transferred_to.eq.${userId}`,
    ]
    if (original) {
      orParts.push(`app_user_id.eq.${original}`, `original_app_user_id.eq.${original}`)
    }
    const { data: linkRows, error: linkError } = await admin
      .from('personal_subscription_links')
      .select('subscription_key, event_type, transferred_from')
      .or(orParts.join(','))
      .order('received_at', { ascending: true })
      .limit(200)
    if (linkError) return null
    const rows = (linkRows ?? []) as SubscriptionLinkRow[]
    const candidates = assembleCandidateKeys(rows, original)

    const originals: string[] = []
    if (original && UUID_RE.test(original) && original !== userId) originals.push(original)
    for (const row of rows) {
      const from = row.transferred_from
      if (from && UUID_RE.test(from) && from !== userId && !originals.includes(from)) {
        originals.push(from)
      }
    }

    const { data: resolved, error: rpcError } = await admin.rpc('resolve_subscription_bank', {
      p_caller: userId,
      p_keys: candidates.map(c => c.key),
      p_key_sources: candidates.map(c => c.source),
      p_original_candidates: originals,
      p_platform: platform,
      p_store: rc.store,
    })
    if (rpcError) return null
    const row = Array.isArray(resolved) ? resolved[0] : resolved
    if (!row?.bank_user_id) return null

    const { count } = await admin
      .from('personal_heroes')
      .select('id', { count: 'exact', head: true })
      .eq('user_id', row.bank_user_id)
    return {
      state: row.state as string,
      looks: count ?? 0,
      collision: row.collision === true,
      bankUserId: row.bank_user_id as string,
    }
  } catch {
    return null
  }
}

// The client-facing slice of a bank outcome: state + looks +
// collision. The bank uid itself never crosses the wire.
function bankForResponse(bank: BankOutcome | null): Record<string, unknown> | null {
  if (!bank) return null
  return { state: bank.state, looks: bank.looks, collision: bank.collision }
}

async function fetchRcState(userId: string, platform: string): Promise<RcEntitlementState> {
  let sawInactive = false
  for (const key of RC_KEYS) {
    try {
      const resp = await fetch(`${RC_API}/subscribers/${encodeURIComponent(userId)}`, {
        // GEN-5 (RW-28 Addendum 8, Item 4a): the platform comes
        // from the caller's body (the Android client sends
        // "android"); the gen-4 hard-coded 'ios' made every
        // lookup an iOS-storefront view regardless of the caller.
        headers: { 'Authorization': `Bearer ${key}`, 'X-Platform': platform },
      })
      if (!resp.ok) continue
      const state = entitlementFromSubscriber(await resp.json())
      if (state.state === 'active') return state
      if (state.state === 'inactive') sawInactive = true
    } catch { /* try the next key / fall through to unknown */ }
  }
  return sawInactive
    ? { state: 'inactive', productId: null, expiresDate: null, isSandbox: null, ...EMPTY_RC }
    : { state: 'unknown', productId: null, expiresDate: null, isSandbox: null, ...EMPTY_RC }
}

// Same service-credential detection as start-personal-fill: exact
// env-key match, a new-format secret key, or a JWT whose role claim
// is service_role (the gateway has already validated the credential).
function isServiceCall(authHeader: string): boolean {
  const bearerToken = authHeader.replace(/^Bearer\s+/i, '')
  if (!bearerToken) return false
  if (bearerToken === SUPABASE_SERVICE_ROLE_KEY) return true
  const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
  if (secretKeys && secretKeys.includes(bearerToken)) return true
  try {
    const part = bearerToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(part + '='.repeat((4 - part.length % 4) % 4)))
    return payload?.role === 'service_role'
  } catch { return false }
}

// Guarded so the pure logic (entitlement decision, key derivation)
// is unit-testable via index.test.ts; the edge runtime runs this
// file as main.
if (import.meta.main) serve(async (req: Request) => {
  if (req.method !== 'POST') return jsonResponse({ error: 'method_not_allowed' }, 405)
  if (RC_KEYS.length === 0) {
    return jsonResponse({ error: 'rc_keys_not_configured' }, 500)
  }

  const body = await req.json().catch(() => ({})) as Record<string, unknown>
  const authHeader = req.headers.get('Authorization') ?? ''
  const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  })

  let userId: string | null = null
  if (isServiceCall(authHeader)) {
    userId = typeof body.user_id === 'string' ? body.user_id : null
    if (!userId) return jsonResponse({ error: 'user_id is required for service-role calls' }, 400)
  } else {
    const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      global: { headers: { Authorization: authHeader } },
    })
    const { data, error } = await userClient.auth.getUser()
    if (error || !data.user) return jsonResponse({ error: 'unauthorized' }, 401)
    userId = data.user.id
  }

  const platform = typeof body.platform === 'string' ? body.platform : 'ios'
  let rc = await fetchRcState(userId, platform)
  if (rc.state === 'unknown') {
    // No usable answer from RevenueCat — change nothing, say so.
    return jsonResponse({ ok: false, error: 'rc_state_unknown', user_id: userId }, 502)
  }

  // GEN-5 (RW-28 Addendum 8, Item 4a): a DOWNGRADE is never
  // applied on a single read. On Nov 5, 2026 the flag flapped
  // true -> false (05:56 CST) -> true (06:31 CST) under this
  // function: one reconcile observed a transiently inactive
  // RevenueCat record and revoked a paying subscriber for 35
  // minutes. When the stored flag is true and the fresh read says
  // inactive, confirm with a second read after a pause; only a
  // confirmed inactive applies the downgrade. A still-unconfirmed
  // downgrade changes nothing and says so.
  {
    const { data: stored } = await admin
      .from('personal_profiles')
      .select('entitled')
      .eq('user_id', userId)
      .maybeSingle()
    if (stored?.entitled === true && rc.state === 'inactive') {
      await new Promise(resolve => setTimeout(resolve, 2000))
      const confirm = await fetchRcState(userId, platform)
      if (confirm.state !== 'inactive') {
        return jsonResponse({
          ok: true, applied: false, reason: 'downgrade_unconfirmed',
          user_id: userId, entitled: true,
        })
      }
      rc = confirm
    }
  }
  const target = rc.state === 'active'

  const { data: profile } = await admin
    .from('personal_profiles')
    .select('entitled')
    .eq('user_id', userId)
    .maybeSingle()
  const current = profile?.entitled === true

  if (current === target) {
    // RW-137: an entitled caller resolves their bank on EVERY
    // reconcile, transition or not — resolution is how a restored
    // subscription's bank reaches a fresh install's uid, and it must
    // also heal a resolution that was unavailable on an earlier call.
    const bank = target ? await resolveBank(admin, userId, rc, platform) : null
    return jsonResponse({
      ok: true, applied: false, reason: 'state_unchanged',
      user_id: userId, entitled: current,
      rc_product_id: rc.productId, rc_expires_date: rc.expiresDate, rc_is_sandbox: rc.isSandbox,
      bank: bankForResponse(bank),
    })
  }

  const { error: setError } = await admin.rpc('set_personal_entitlement', {
    p_user_id: userId, p_entitled: target, p_source: SOURCE,
  })
  if (setError) {
    return jsonResponse({ error: 'set_entitlement_failed', detail: setError.message }, 500)
  }

  // RW-137: on a grant, resolve the bank BEFORE the fill kick so
  // the kick targets the bank's uid (the fill writes under the data
  // key). On a confirmed downgrade, the durable identity lapses with
  // the subscription: the resolution cache reverts to self (the
  // registry entry survives for a resubscription inside the
  // retention window — subscription + 12 months, per the ruling).
  const bank = target ? await resolveBank(admin, userId, rc, platform) : null
  if (!target) {
    await admin
      .from('personal_profiles')
      .update({ bank_user_id: userId, bank_resolved_at: new Date().toISOString() })
      .eq('user_id', userId)
  }

  let fill: unknown = null
  if (target) {
    // GEN-5 (RW-28 Addendum 8, Defect 1): this kick names no
    // forecast cells, and under the deployed convergence contract
    // (start-personal-fill v13) it therefore enqueues nothing —
    // server-side kicks cannot name forecast cells and are NOT the
    // fill trigger. The trigger of record is the client's own
    // start-personal-fill call carrying priority_cells derived
    // from its 7-day forecast. The kick stays for the repo
    // contract's sake and is harmless in both worlds.
    try {
      const resp = await fetch(`${SUPABASE_URL}/functions/v1/start-personal-fill`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          'apikey': SUPABASE_SERVICE_ROLE_KEY,
        },
        body: JSON.stringify({ user_id: bank?.bankUserId ?? userId }),
      })
      fill = { status: resp.status, body: await resp.json().catch(() => null) }
    } catch (e) {
      fill = { error: String(e) }
    }
  }

  return jsonResponse({
    ok: true, applied: true, user_id: userId, entitled: target,
    rc_product_id: rc.productId, rc_expires_date: rc.expiresDate, rc_is_sandbox: rc.isSandbox,
    bank: bankForResponse(bank),
    fill,
  })
})
