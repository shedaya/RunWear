// Supabase Edge Function: request-identity-adoption (RW-137, 2026-11-10)
//
// The P2 adoption endpoint (DESIGN §4.4.2/§4.4.3 of the RW-137 design
// of record). After a purchase restore, the client calls this; the
// caller's previous identity is adopted into the caller's current one
// — automatically, with no opt-in (Sol's ruling, DESIGN §4.1) — but
// ONLY on RevenueCat-side proof.
//
// Trust model (mirrors reconcile-personal-entitlement's posture:
// assertions are never trusted):
//   * The caller is the destination; its uid comes from the verified
//     JWT, never the body.
//   * The body's claimed source_user_id is a LOOKUP HINT ONLY. The
//     function re-reads the caller's RevenueCat subscriber record
//     server-side (public SDK keys, both stores, merged) and treats
//     THAT record as the proof: a source is adoptable only if the
//     record names it in original_app_user_id / aliases (the P2 alias
//     chain) AND the record shows the make_it_me entitlement active.
//   * A claimed source RC does not corroborate is a forged assertion:
//     answered as a no-op, nothing is read from or written to the
//     claimed identity.
//   * Execution is delegated to the adopt_personal_identity RPC,
//     which re-checks every guard inside its own transaction
//     (destination entitled, source exists, no prior adoption from
//     the source, no deletion tombstone).
//
// Response: { ok, adopted, results: [{source, via, ...rpc outcome}] }.
// adopted=false with reason 'source_not_corroborated' |
// 'not_entitled' | 'no_adoption_source', or per-source RPC reasons
// such as 'source_already_adopted' (the double-adoption case) inside
// results.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const ENTITLEMENT_ID = 'make_it_me'
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

// The entitlement rule, identical to reconcile-personal-entitlement's
// entitlementFromSubscriber (duplicated per the single-file function
// convention): make_it_me present, unexpired, not refunded.
export function rcEntitlementActive(subscriber: unknown): boolean {
  const sub = (subscriber as Record<string, unknown> | null)?.subscriber as
    | Record<string, unknown> | undefined
  if (!sub || typeof sub !== 'object') return false
  const entitlements = (sub.entitlements ?? {}) as Record<string, Record<string, unknown>>
  const ent = entitlements[ENTITLEMENT_ID]
  if (!ent) return false
  const expires = typeof ent.expires_date === 'string' ? ent.expires_date : null
  if (expires !== null && Date.parse(expires) <= Date.now()) return false
  const productId = typeof ent.product_identifier === 'string' ? ent.product_identifier : null
  if (productId) {
    const subscriptions = (sub.subscriptions ?? {}) as Record<string, Record<string, unknown>>
    const record = subscriptions[productId]
    if (record && typeof record.refunded_at === 'string') return false
  }
  return true
}

// Every P2 candidate a subscriber record exposes for `callerUid`:
// UUID-shaped ids other than the caller, from original_app_user_id
// and from aliases (BOTH shapes, defensively — DESIGN §9.2), each
// tagged with the shape that named it. Pure; unit-testable.
export function adoptionCandidatesFromSubscriber(
  subscriber: unknown, callerUid: string,
): { source: string; via: 'original_app_user_id' | 'aliases' }[] {
  const sub = (subscriber as Record<string, unknown> | null)?.subscriber as
    | Record<string, unknown> | undefined
  if (!sub || typeof sub !== 'object') return []
  const out: { source: string; via: 'original_app_user_id' | 'aliases' }[] = []
  const original = sub.original_app_user_id
  if (typeof original === 'string' && UUID_RE.test(original) && original !== callerUid) {
    out.push({ source: original, via: 'original_app_user_id' })
  }
  const aliases = Array.isArray(sub.aliases) ? sub.aliases : []
  for (const raw of aliases) {
    if (typeof raw === 'string' && UUID_RE.test(raw) && raw !== callerUid
        && !out.some(c => c.source === raw)) {
      out.push({ source: raw, via: 'aliases' })
    }
  }
  return out
}

// Adoption orchestrator — same shape as revenuecat-webhook's (the
// single-file convention duplicates it deliberately). Two RPC phases
// with the storage copies in between; source objects are deleted
// only after the row merge commits; failures stay resumable.
// deno-lint-ignore no-explicit-any
async function runIdentityAdoption(admin: any, source: string, destination: string,
    proof: Record<string, unknown>): Promise<Record<string, unknown>> {
  try {
    const call = async () => {
      const { data, error } = await admin.rpc('adopt_personal_identity', {
        p_source: source, p_destination: destination, p_proof: proof,
      })
      if (error) throw new Error(error.message)
      return data as Record<string, unknown>
    }
    let result = await call()
    if (result.state === 'started' && Array.isArray(result.copies)) {
      for (const c of result.copies as { from: string; to: string }[]) {
        const { error } = await admin.storage.from('personal-heroes').copy(c.from, c.to)
        if (error) {
          const dir = c.to.slice(0, c.to.lastIndexOf('/'))
          const name = c.to.slice(c.to.lastIndexOf('/') + 1)
          const { data: listing } = await admin.storage.from('personal-heroes').list(dir)
          const present = (listing ?? []).some((o: { name: string }) => o.name === name)
          if (!present) {
            return { ok: false, reason: 'storage_copy_failed', detail: error.message, from: c.from }
          }
        }
      }
      result = await call()
    }
    if (result.state === 'done' && Array.isArray(result.source_cleanup_paths)) {
      const paths = result.source_cleanup_paths as string[]
      for (let i = 0; i < paths.length; i += 100) {
        await admin.storage.from('personal-heroes').remove(paths.slice(i, i + 100))
      }
      try {
        await fetch(`${SUPABASE_URL}/functions/v1/start-personal-fill`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            'apikey': SUPABASE_SERVICE_ROLE_KEY,
          },
          body: JSON.stringify({ user_id: destination }),
        })
      } catch { /* the client's own kick is the trigger of record */ }
    }
    return result
  } catch (e) {
    return { ok: false, reason: 'adoption_error', detail: String(e) }
  }
}

serve(async (req: Request) => {
  if (req.method !== 'POST') return jsonResponse({ error: 'method_not_allowed' }, 405)
  if (RC_KEYS.length === 0) {
    return jsonResponse({ error: 'rc_keys_not_configured' }, 500)
  }

  const body = await req.json().catch(() => ({})) as Record<string, unknown>
  const authHeader = req.headers.get('Authorization') ?? ''
  const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
  })
  const { data: userData, error: userError } = await userClient.auth.getUser()
  if (userError || !userData.user) return jsonResponse({ error: 'unauthorized' }, 401)
  const userId = userData.user.id

  const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
  const platform = typeof body.platform === 'string' ? body.platform : 'ios'

  // Re-verify against RevenueCat server-side: every configured key is
  // read and the records merged (a subscriber's history may sit under
  // either store's app). No usable record -> no action (mirrors
  // reconcile's rc_state_unknown discipline).
  const payloads: unknown[] = []
  for (const key of RC_KEYS) {
    try {
      const resp = await fetch(`${RC_API}/subscribers/${encodeURIComponent(userId)}`, {
        headers: { 'Authorization': `Bearer ${key}`, 'X-Platform': platform },
      })
      if (resp.ok) payloads.push(await resp.json())
    } catch { /* try the next key */ }
  }
  if (payloads.length === 0) {
    return jsonResponse({ ok: false, error: 'rc_state_unknown', user_id: userId }, 502)
  }

  const active = payloads.some(p => rcEntitlementActive(p))
  if (!active) {
    return jsonResponse({
      ok: true, adopted: false, reason: 'not_entitled', user_id: userId, results: [],
    })
  }

  const candidates: { source: string; via: string }[] = []
  for (const p of payloads) {
    for (const c of adoptionCandidatesFromSubscriber(p, userId)) {
      if (!candidates.some(x => x.source === c.source)) candidates.push(c)
    }
  }

  const claimed = typeof body.source_user_id === 'string' ? body.source_user_id : null
  let targets = candidates
  if (claimed !== null) {
    // The claim is a hint, never proof: it narrows the RC-corroborated
    // candidate set, and corroboration failure is a flat no-op.
    targets = candidates.filter(c => c.source === claimed)
    if (targets.length === 0) {
      return jsonResponse({
        ok: true, adopted: false, reason: 'source_not_corroborated',
        user_id: userId, results: [],
      })
    }
  }
  if (targets.length === 0) {
    return jsonResponse({
      ok: true, adopted: false, reason: 'no_adoption_source', user_id: userId, results: [],
    })
  }

  const results: Record<string, unknown>[] = []
  for (const t of targets) {
    console.log(`[request-identity-adoption] corroborated source for user ${userId} seen via ${t.via}: ${t.source}`)
    const outcome = await runIdentityAdoption(admin, t.source, userId, {
      type: 'reconcile_alias', seen_via: t.via, reconcile_at: new Date().toISOString(),
    })
    results.push({ source: t.source, via: t.via, ...outcome })
  }
  const adopted = results.some(r => r.state === 'done')
  return jsonResponse({ ok: true, adopted, user_id: userId, results })
})
