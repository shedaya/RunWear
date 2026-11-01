// Supabase Edge Function: revenuecat-webhook (RW-14 follow-up, 2026-10-31)
//
// The entitlement path. RevenueCat (RunWear project, entitlement
// `make_it_me`) POSTs subscription lifecycle events here; this function
// is the ONLY production caller of public.set_personal_entitlement —
// the service-role-only setter the backend build left as the billing
// lane's integration point. Clients never write `entitled`.
//
// Deployed with verify_jwt = false: RevenueCat sends no Supabase JWT.
// The caller is authenticated by RevenueCat's own webhook authorization
// header, whose value is the function secret REVENUECAT_WEBHOOK_AUTH_KEY
// (the same value must be pasted into the RC dashboard's webhook
// Authorization field; RC sends it verbatim in the Authorization header,
// with or without a "Bearer " prefix — both forms are accepted).
//
// Identity mapping (verified against the client code, 2026-10-31):
// - iOS configures Purchases with appUserID = the Supabase user id, so
//   app_user_id IS the auth.users id.
// - Android currently configures Purchases with NO app user id and never
//   calls logIn, so its purchases arrive under RC's anonymous id
//   ($RCAnonymousID:...). Such events are mapped when any of the event's
//   aliases / original_app_user_id is a known Supabase user id (the
//   state after a future logIn merge); otherwise they are accepted with
//   200 and logged as unmapped so RC does not retry-storm, and the gap
//   stays visible in personal_webhook_events. The permanent fix is one
//   client line (Android: Purchases.logIn(supabaseUid) once its
//   anonymous Supabase session exists) — out of this function's scope,
//   named in the follow-up report.
//
// Event mapping (entitlement make_it_me; products rw_makeitme_monthly /
// rw_makeitme_annual on both stores, Play ids carry the :monthly /
// :annual base-plan suffix in RC):
//   INITIAL_PURCHASE, RENEWAL, PRODUCT_CHANGE, UNCANCELLATION -> grant
//   EXPIRATION                                                -> clear
//   CANCELLATION  -> no-op (access runs to expiration; EXPIRATION clears)
//   BILLING_ISSUE -> no-op (RC keeps the entitlement during grace)
//   SUBSCRIPTION_PAUSED, SUBSCRIPTION_EXTENDED, TEST, anything else
//                 -> no-op (TEST is RC's dashboard probe)
// A grant/clear applies only when the event concerns make_it_me:
// entitlement_ids / entitlement_id naming it, or — when the payload
// carries no entitlement fields — the product being one of the four
// attached product ids.
//
// Idempotency: every event id is recorded in personal_webhook_events;
// a redelivery is answered {duplicate: true} without touching state.
// The setter is also called only when the stored flag actually changes,
// so the personal_entitlement_events audit trail holds one row per real
// transition, each with source 'revenuecat-webhook'.
//
// On a false->true grant transition the function best-effort kicks
// start-personal-fill (service role + user_id), matching the billing
// hookup the backend build specified; the clients call it too, and the
// fill itself is idempotent. A failed kick never fails the webhook.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const WEBHOOK_AUTH_KEY = Deno.env.get('REVENUECAT_WEBHOOK_AUTH_KEY')!

const ENTITLEMENT_ID = 'make_it_me'
const SOURCE = 'revenuecat-webhook'
const ATTACHED_PRODUCTS = new Set([
  'rw_makeitme_monthly',
  'rw_makeitme_annual',
  'rw_makeitme_monthly:monthly',
  'rw_makeitme_annual:annual',
])
const GRANT_TYPES = new Set(['INITIAL_PURCHASE', 'RENEWAL', 'PRODUCT_CHANGE', 'UNCANCELLATION'])
const CLEAR_TYPES = new Set(['EXPIRATION'])
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function authorized(req: Request): boolean {
  const header = req.headers.get('authorization')
  if (!header || !WEBHOOK_AUTH_KEY) return false
  return header === WEBHOOK_AUTH_KEY || header === `Bearer ${WEBHOOK_AUTH_KEY}`
}

function concernsEntitlement(event: Record<string, unknown>): boolean {
  const ids = event.entitlement_ids
  if (Array.isArray(ids)) return ids.includes(ENTITLEMENT_ID)
  if (typeof event.entitlement_id === 'string') return event.entitlement_id === ENTITLEMENT_ID
  return typeof event.product_id === 'string' && ATTACHED_PRODUCTS.has(event.product_id)
}

serve(async (req: Request) => {
  if (req.method !== 'POST') return jsonResponse({ error: 'method_not_allowed' }, 405)
  if (!authorized(req)) return jsonResponse({ error: 'unauthorized' }, 401)

  let payload: Record<string, unknown>
  try {
    payload = await req.json()
  } catch {
    return jsonResponse({ error: 'invalid_json' }, 400)
  }
  const event = (payload.event ?? payload) as Record<string, unknown>
  const type = typeof event.type === 'string' ? event.type : ''
  const eventId = typeof event.id === 'string' ? event.id : null
  if (!type) return jsonResponse({ error: 'missing_event_type' }, 400)

  const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  })

  // Idempotency: a redelivered event id is a duplicate, full stop.
  if (eventId) {
    const { data: seen } = await admin
      .from('personal_webhook_events')
      .select('event_id')
      .eq('event_id', eventId)
      .maybeSingle()
    if (seen) return jsonResponse({ ok: true, duplicate: true, event_id: eventId })
  }

  const record = async (userId: string | null, applied: boolean, detail: string) => {
    if (!eventId) return
    await admin.from('personal_webhook_events').insert({
      event_id: eventId, event_type: type, user_id: userId, applied, detail,
    })
  }

  // RC's dashboard probe (and every non-entitlement event kind not in
  // the grant/clear sets): acknowledge, change nothing.
  const isGrant = GRANT_TYPES.has(type)
  const isClear = CLEAR_TYPES.has(type)
  if (!isGrant && !isClear) {
    await record(null, false, 'no_op_event_type')
    return jsonResponse({ ok: true, applied: false, reason: 'no_op_event_type', type })
  }
  if (!concernsEntitlement(event)) {
    await record(null, false, 'other_entitlement_or_product')
    return jsonResponse({ ok: true, applied: false, reason: 'other_entitlement_or_product' })
  }

  // Identity mapping: first candidate id that is a real Supabase user.
  const candidates: string[] = []
  for (const raw of [event.app_user_id, event.original_app_user_id, ...(Array.isArray(event.aliases) ? event.aliases : [])]) {
    if (typeof raw === 'string' && UUID_RE.test(raw) && !candidates.includes(raw)) candidates.push(raw)
  }
  let userId: string | null = null
  for (const candidate of candidates) {
    const { data, error } = await admin.auth.admin.getUserById(candidate)
    if (!error && data?.user) { userId = candidate; break }
  }
  if (!userId) {
    await record(null, false, 'unmapped_user')
    return jsonResponse({ ok: true, applied: false, reason: 'unmapped_user' })
  }

  const target = isGrant
  const { data: profile } = await admin
    .from('personal_profiles')
    .select('entitled')
    .eq('user_id', userId)
    .maybeSingle()
  const current = profile?.entitled === true

  if (current === target) {
    await record(userId, false, target ? 'already_entitled' : 'already_not_entitled')
    return jsonResponse({ ok: true, applied: false, reason: 'state_unchanged', user_id: userId, entitled: current })
  }

  const { error: setError } = await admin.rpc('set_personal_entitlement', {
    p_user_id: userId, p_entitled: target, p_source: SOURCE,
  })
  if (setError) {
    // No idempotency row is written, so RC's retry can apply cleanly.
    return jsonResponse({ error: 'set_entitlement_failed', detail: setError.message }, 500)
  }
  await record(userId, true, target ? 'granted' : 'cleared')

  let fill: unknown = null
  if (target) {
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

  return jsonResponse({ ok: true, applied: true, user_id: userId, entitled: target, fill })
})
