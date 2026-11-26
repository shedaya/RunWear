// RW-137: pure-logic tests for reconcile-personal-entitlement —
// entitlement decision + subscription key derivation. Run:
//   deno run --allow-env supabase/functions/reconcile-personal-entitlement/index.test.ts
// (plain asserts, the personal-hero-worker test idiom; importing
// index.ts does not start the server — serve is import.meta.main-
// guarded.)
import {
  assembleCandidateKeys,
  entitlementFromSubscriber,
  normalizeOriginalTransactionId,
  rcSubscriptionKey,
  storeSubscriptionKey,
  type SubscriptionLinkRow,
} from "./index.ts"

function assert(cond: boolean, msg: string) { if (!cond) throw new Error("ASSERT FAILED: " + msg) }
function eq(a: unknown, b: unknown, msg: string) {
  if (JSON.stringify(a) !== JSON.stringify(b)) {
    throw new Error(`ASSERT FAILED: ${msg} — got ${JSON.stringify(a)}, want ${JSON.stringify(b)}`)
  }
}

// 1. Google Play renewal suffix is stripped; Apple ids pass through.
eq(normalizeOriginalTransactionId("GPA.1234-5678-9012-34567..3"), "GPA.1234-5678-9012-34567", "play suffix strip")
eq(normalizeOriginalTransactionId("1000000123456789"), "1000000123456789", "apple passthrough")

// 2. Key shapes.
eq(storeSubscriptionKey("play_store", "GPA.1..2"), "store:play_store:GPA.1", "store key from play otid")
eq(storeSubscriptionKey("app_store", "1000000123456789"), "store:app_store:1000000123456789", "store key from apple otid")
eq(storeSubscriptionKey(null, "1000000123456789"), "store:unknown:1000000123456789", "store key without store")
eq(storeSubscriptionKey("app_store", null), null, "no otid, no key")
eq(storeSubscriptionKey("app_store", "  "), null, "blank otid, no key")
eq(rcSubscriptionKey("63e82e02-0000-4000-8000-000000000000"), "rc:63e82e02-0000-4000-8000-000000000000", "rc key")
eq(rcSubscriptionKey(null), null, "no original, no rc key")
eq(rcSubscriptionKey(""), null, "blank original, no rc key")

// 3. Candidate assembly: evidence keys first in order, deduped,
// transfer-sourced keys keep their provenance, rc key last.
{
  const links: SubscriptionLinkRow[] = [
    { subscription_key: "store:app_store:1000000123456789", event_type: "INITIAL_PURCHASE", transferred_from: null },
    { subscription_key: "store:app_store:1000000123456789", event_type: "RENEWAL", transferred_from: null },
    { subscription_key: "store:app_store:1000000999999999", event_type: "TRANSFER", transferred_from: "63e82e02-0000-4000-8000-000000000000" },
    { subscription_key: null, event_type: "TRANSFER", transferred_from: "63e82e02-0000-4000-8000-000000000000" },
  ]
  const got = assembleCandidateKeys(links, "6a6592d4-0000-4000-8000-000000000000")
  eq(got, [
    { key: "store:app_store:1000000123456789", source: "store_otid" },
    { key: "store:app_store:1000000999999999", source: "transfer" },
    { key: "rc:6a6592d4-0000-4000-8000-000000000000", source: "rc_original" },
  ], "candidate assembly order + dedupe + sources")
}

// 4. Candidate assembly with no evidence and no original: empty.
eq(assembleCandidateKeys([], null), [], "no evidence, no keys")

// 5. entitlementFromSubscriber carries the identity fields.
{
  const payload = {
    subscriber: {
      original_app_user_id: "63e82e02-0000-4000-8000-000000000000",
      entitlements: {
        make_it_me: { product_identifier: "rw_makeitme_annual", expires_date: "2099-01-01T00:00:00Z" },
      },
      subscriptions: {
        rw_makeitme_annual: { store: "app_store", is_sandbox: false },
      },
    },
  }
  const state = entitlementFromSubscriber(payload)
  eq(state.state, "active", "active entitlement")
  eq(state.originalAppUserId, "63e82e02-0000-4000-8000-000000000000", "original carried")
  eq(state.store, "app_store", "store carried")
}

// 6. Inactive / unknown payloads keep identity null-safe.
{
  const state = entitlementFromSubscriber({ subscriber: { entitlements: {}, subscriptions: {} } })
  eq(state.state, "inactive", "empty subscriber is inactive")
  eq(state.originalAppUserId, null, "missing original is null")
  eq(entitlementFromSubscriber(null).state, "unknown", "null payload is unknown")
  eq(entitlementFromSubscriber({}).state, "unknown", "shapeless payload is unknown")
}

// 6b. ownership_type is carried from the subscription record (the
// FAMILY_SHARED resolution guard reads it).
{
  const payload = {
    subscriber: {
      original_app_user_id: "63e82e02-0000-4000-8000-000000000000",
      entitlements: {
        make_it_me: { product_identifier: "rw_makeitme_annual", expires_date: "2099-01-01T00:00:00Z" },
      },
      subscriptions: {
        rw_makeitme_annual: { store: "app_store", ownership_type: "FAMILY_SHARED" },
      },
    },
  }
  eq(entitlementFromSubscriber(payload).ownershipType, "FAMILY_SHARED", "ownership carried")
}

// 7. A refunded subscription is inactive even with a future expiry.
{
  const payload = {
    subscriber: {
      original_app_user_id: "63e82e02-0000-4000-8000-000000000000",
      entitlements: {
        make_it_me: { product_identifier: "rw_makeitme_annual", expires_date: "2099-01-01T00:00:00Z" },
      },
      subscriptions: {
        rw_makeitme_annual: { store: "app_store", refunded_at: "2026-11-01T00:00:00Z" },
      },
    },
  }
  eq(entitlementFromSubscriber(payload).state, "inactive", "refunded is inactive")
}

console.log("reconcile-personal-entitlement: ALL TESTS PASSED")
