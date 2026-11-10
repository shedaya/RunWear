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
// - Android note (corrected RW-137, 2026-11-10; DESIGN §1.2): the
//   shipped Android client DOES call Purchases.logIn(supabaseUid) at
//   bootstrap/purchase sync, so Android purchases record under the
//   Supabase uid and reconcile directly. (The RW-14-era caveat that
//   Android never called logIn is stale.)
//
// RW-137 extension (DESIGN §4.4.2): the subscriber payload this
// function already fetches can expose a P2 adoption proof — the
// caller's previous identity named in the record's `aliases` and/or
// `original_app_user_id`. When the caller's entitlement is ACTIVE and
// such a source exists as an auth user, the response carries
// `adoption_available: <source_uid>` and `adoption_via` naming the
// shape that exposed it. The server does NOT adopt on this call: the
// client follows up with request-identity-adoption, which re-verifies
// the record server-side before executing. RC's v1 payload shape for
// aliases was an open verification (DESIGN §9.2), so extraction is
// coded defensively against BOTH shapes and logs which one was seen.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const ENTITLEMENT_ID = 'make_it_me'
const SOURCE = 'entitlement-reconciliation'
// REVENUECAT_API_BASE exists so tests can point the subscriber reads
// at a fixture server; production leaves it unset (real RC API).
const RC_API = Deno.env.get('REVENUECAT_API_BASE') ?? 'https://api.revenuecat.com/v1'
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

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
}

// Pure decision over one RC subscriber payload — extracted so the
// rule can be read (and tested) without the network around it.
export function entitlementFromSubscriber(subscriber: unknown): RcEntitlementState {
  const sub = (subscriber as Record<string, unknown> | null)?.subscriber as
    | Record<string, unknown> | undefined
  if (!sub || typeof sub !== 'object') {
    return { state: 'unknown', productId: null, expiresDate: null, isSandbox: null }
  }
  const entitlements = (sub.entitlements ?? {}) as Record<string, Record<string, unknown>>
  const ent = entitlements[ENTITLEMENT_ID]
  if (!ent) return { state: 'inactive', productId: null, expiresDate: null, isSandbox: null }

  const expires = typeof ent.expires_date === 'string' ? ent.expires_date : null
  if (expires !== null && Date.parse(expires) <= Date.now()) {
    return { state: 'inactive', productId: null, expiresDate: expires, isSandbox: null }
  }
  const productId = typeof ent.product_identifier === 'string' ? ent.product_identifier : null

  // Refund check on the underlying subscription record, when present.
  let isSandbox: boolean | null = null
  if (productId) {
    const subscriptions = (sub.subscriptions ?? {}) as Record<string, Record<string, unknown>>
    const record = subscriptions[productId]
    if (record) {
      if (typeof record.refunded_at === 'string') {
        return { state: 'inactive', productId: null, expiresDate: expires, isSandbox: null }
      }
      if (typeof record.is_sandbox === 'boolean') isSandbox = record.is_sandbox
    }
  }
  return { state: 'active', productId, expiresDate: expires, isSandbox }
}

// RW-137: the adoption source (P2 proof, DESIGN §4.1/§4.4.2) a
// subscriber record exposes for `callerUid`: the first UUID-shaped id
// other than the caller named by original_app_user_id, else by the
// aliases array. Coded against BOTH shapes because which one the v1
// payload carries was an open verification (DESIGN §9.2); the caller
// logs which shape was seen. Pure — unit-testable without a network.
export function adoptionSourceFromSubscriber(
  subscriber: unknown, callerUid: string,
): { source: string | null; via: 'original_app_user_id' | 'aliases' | null } {
  const sub = (subscriber as Record<string, unknown> | null)?.subscriber as
    | Record<string, unknown> | undefined
  if (!sub || typeof sub !== 'object') return { source: null, via: null }
  const original = sub.original_app_user_id
  if (typeof original === 'string' && UUID_RE.test(original) && original !== callerUid) {
    return { source: original, via: 'original_app_user_id' }
  }
  const aliases = Array.isArray(sub.aliases) ? sub.aliases : []
  for (const raw of aliases) {
    if (typeof raw === 'string' && UUID_RE.test(raw) && raw !== callerUid) {
      return { source: raw, via: 'aliases' }
    }
  }
  return { source: null, via: null }
}

async function fetchRcState(userId: string, platform: string): Promise<RcEntitlementState & { subscriber: unknown }> {
  let sawInactive = false
  let lastSubscriber: unknown = null
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
      const payload = await resp.json()
      lastSubscriber = payload
      const state = entitlementFromSubscriber(payload)
      if (state.state === 'active') return { ...state, subscriber: payload }
      if (state.state === 'inactive') sawInactive = true
    } catch { /* try the next key / fall through to unknown */ }
  }
  return sawInactive
    ? { state: 'inactive', productId: null, expiresDate: null, isSandbox: null, subscriber: lastSubscriber }
    : { state: 'unknown', productId: null, expiresDate: null, isSandbox: null, subscriber: null }
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

serve(async (req: Request) => {
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

  // RW-137 (DESIGN §4.4.2): surface a P2 adoption source from the
  // subscriber record already fetched — but only alongside an ACTIVE
  // entitlement, and only when the source is a real auth user. No
  // adoption runs here; the client calls request-identity-adoption,
  // which re-verifies this same record before executing.
  let adoptionAvailable: string | null = null
  let adoptionVia: string | null = null
  if (target && rc.subscriber) {
    const found = adoptionSourceFromSubscriber(rc.subscriber, userId)
    if (found.source) {
      const { data: srcUser } = await admin.auth.admin.getUserById(found.source)
      if (srcUser?.user) {
        adoptionAvailable = found.source
        adoptionVia = found.via
        console.log(`[reconcile-personal-entitlement] adoption source for user ${userId} seen via ${found.via}: ${found.source}`)
      }
    }
  }

  const { data: profile } = await admin
    .from('personal_profiles')
    .select('entitled')
    .eq('user_id', userId)
    .maybeSingle()
  const current = profile?.entitled === true

  if (current === target) {
    return jsonResponse({
      ok: true, applied: false, reason: 'state_unchanged',
      user_id: userId, entitled: current,
      rc_product_id: rc.productId, rc_expires_date: rc.expiresDate, rc_is_sandbox: rc.isSandbox,
      adoption_available: adoptionAvailable, adoption_via: adoptionVia,
    })
  }

  const { error: setError } = await admin.rpc('set_personal_entitlement', {
    p_user_id: userId, p_entitled: target, p_source: SOURCE,
  })
  if (setError) {
    return jsonResponse({ error: 'set_entitlement_failed', detail: setError.message }, 500)
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
        body: JSON.stringify({ user_id: userId }),
      })
      fill = { status: resp.status, body: await resp.json().catch(() => null) }
    } catch (e) {
      fill = { error: String(e) }
    }
  }

  return jsonResponse({
    ok: true, applied: true, user_id: userId, entitled: target,
    rc_product_id: rc.productId, rc_expires_date: rc.expiresDate, rc_is_sandbox: rc.isSandbox,
    adoption_available: adoptionAvailable, adoption_via: adoptionVia,
    fill,
  })
})
