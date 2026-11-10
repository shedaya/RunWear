#!/usr/bin/env python3
"""RW-137 S4c live erasure test against the DEPLOYED delete-my-data,
on a THROWAWAY account only.

Creates a throwaway user, populates every store §4.8 names (storage
refs / refs-small / heroes / tmp / repair; all personal tables incl.
personal_webhook_events; an adoption row naming the user; a second
throwaway as the adoption counterparty), signs in as the user, calls
the deployed function, then asserts: zero rows and zero objects
remain, the auth user is gone, the tombstone is present, the RC
disposition is reported verbatim, and a subsequent adoption attempt
FROM the erased uid is refused by the tombstone.

Cleanup removes the counterparty user and the test tombstone.
"""
import importlib.util
import json
import sys

spec = importlib.util.spec_from_file_location(
    "s1", "/home/hatch/workspace/runwear-rw137-server-wt/supabase/tests/rw137_s1_adoption_test.py")
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)

RESULTS = []


def check(name, cond, extra=""):
    RESULTS.append((name, bool(cond)))
    print(f"{'PASS' if cond else 'FAIL'}  {name}  {extra}")


def sign_in(email, password):
    status, out = t.api("POST", "/auth/v1/token?grant_type=password", t.keys()["anon"],
                        {"email": email, "password": password})
    assert status == 200, (status, out)
    return out["access_token"]


def main():
    created = []
    U = None
    try:
        import uuid as uuidlib
        suffix = uuidlib.uuid4().hex[:8]
        email = f"rw137-erase-{suffix}@example.com"
        status, user = t.api("POST", "/auth/v1/admin/users", t.svc(),
                             {"email": email, "password": "Throwaway!2345",
                              "email_confirm": True, "user_metadata": {"rw137_test": "erase"}})
        U = user["id"]; created.append(U)
        V = t.create_user("erase-counterparty"); created.append(V)

        t.seed_profile(U, personalization_on=True, gender="FEMALE",
                       free_generations_used=2, entitled=True, photo_set_hash="hashU")
        t.seed_profile(V, entitled=True)
        t.insert("personal_reference_photos",
                 {"user_id": U, "storage_path": f"{U}/refs/ref-1.jpg", "position": 1})
        t.upload(f"{U}/refs/ref-1.jpg", b"ref")
        t.upload(f"{U}/refs-small/ref-1.jpg", b"refsmall")
        hero_id = t.seed_hero(U, "cellA", 1, "heroA1.jpg", gate={"verdict": "pass"})
        t.upload(f"{U}/tmp/scratch.jpg", b"tmp")
        t.upload(f"{U}/repair/src.jpg", b"repair")
        t.seed_cell(U, "cellA", 3)
        t.insert("personal_consents",
                 {"user_id": U, "consent_version": "v1", "attested_own_likeness": True, "attested_adult": True})
        t.insert("personal_generation_jobs",
                 {"user_id": U, "combination_id": "cellA", "gender": "FEMALE", "weather": "clear",
                  "temp_bracket": "mild", "time_of_day": "day", "outfit_signature": "sig",
                  "kind": "subscriber_fill", "priority": 10, "status": "COMPLETED", "result_hero_id": hero_id})
        t.insert("personal_hero_flags",
                 {"user_id": U, "hero_id": hero_id, "combination_id": "cellA", "variant": 1,
                  "storage_path": f"{U}/heroes/cellA/heroA1.jpg", "reason": "looks_broken"})
        t.insert("personal_entitlement_events", {"user_id": U, "entitled": True, "source": "test-toggle"})
        t.insert("personal_webhook_events",
                 {"event_id": f"RW137-ERASE-TEST-{suffix}", "event_type": "TEST",
                  "user_id": U, "applied": False, "detail": "test"})
        t.insert("personal_scaffold_events",
                 {"user_id": U, "requested_combination_id": "cellA", "served_combination_id": "cellA", "hero_id": hero_id})
        t.insert("personal_fallback_events",
                 {"user_id": U, "combination_id": "cellA", "reason": "test"})
        # An adoption row naming U as destination (from counterparty V).
        t.insert("personal_identity_adoptions",
                 {"source_user_id": V, "destination_user_id": U,
                  "proof_type": "reconcile_alias", "proof": {"type": "reconcile_alias"},
                  "state": "done", "counts": {}})

        token = sign_in(email, "Throwaway!2345")
        status, receipt = t.api("POST", "/functions/v1/delete-my-data", t.keys()["anon"],
                                {}, extra_headers={"Authorization": f"Bearer {token}"})
        check("erasure call 200", status == 200, json.dumps(receipt)[:400])
        check("receipt account_deleted", receipt.get("account_deleted") is True)
        check("receipt tombstone_written", receipt.get("tombstone_written") is True)
        check("receipt rc disposition = skipped_no_secret_key",
              receipt.get("rc_customer_deletion") == "skipped_no_secret_key",
              str(receipt.get("rc_customer_deletion")))
        rr = receipt.get("rows_removed", {})
        check("receipt counts webhook events removed", rr.get("personal_webhook_events") == 1)
        check("receipt counts adoption rows removed", rr.get("personal_identity_adoptions") == 1)
        check("receipt counts storage objects removed >= 5",
              (receipt.get("storage_objects_removed") or 0) >= 5,
              str(receipt.get("storage_objects_removed")))

        for table in ["personal_profiles", "personal_reference_photos", "personal_heroes",
                      "personal_cells", "personal_consents", "personal_generation_jobs",
                      "personal_hero_flags", "personal_entitlement_events",
                      "personal_webhook_events", "personal_scaffold_events",
                      "personal_fallback_events"]:
            n = len(t.select(table, f"user_id=eq.{U}"))
            check(f"zero rows left: {table}", n == 0, f"n={n}")
        adoptions = t.sql(f"select count(*) as n from public.personal_identity_adoptions "
                          f"where source_user_id='{U}' or destination_user_id='{U}'")
        check("zero adoption rows name the user", adoptions[0]["n"] == 0)
        check("zero storage objects left (all prefixes)", t.all_objects(U) == [], str(t.all_objects(U)))
        status, _ = t.api("GET", f"/auth/v1/admin/users/{U}", t.svc())
        check("auth user gone", status == 404, f"status={status}")
        tombs = t.select("personal_identity_tombstones", f"user_id=eq.{U}")
        check("tombstone present", len(tombs) == 1 and tombs[0]["source"] == "delete-my-data")

        # A subsequent adoption FROM the erased uid must be refused.
        res = t.rpc("adopt_personal_identity",
                    {"p_source": U, "p_destination": V, "p_proof": {"type": "transfer_event"}})
        check("adoption FROM erased uid refused by tombstone",
              res.get("reason") == "source_tombstoned", json.dumps(res))
    finally:
        if created:
            idlist = ",".join(f"'{u}'" for u in created)
            t.sql("delete from public.personal_generation_jobs where user_id in (" + idlist + ");"
                  "delete from public.personal_heroes where user_id in (" + idlist + ");"
                  "delete from public.personal_cells where user_id in (" + idlist + ");"
                  "delete from public.personal_reference_photos where user_id in (" + idlist + ");"
                  "delete from public.personal_consents where user_id in (" + idlist + ");"
                  "delete from public.personal_entitlement_events where user_id in (" + idlist + ");"
                  "delete from public.personal_hero_flags where user_id in (" + idlist + ");"
                  "delete from public.personal_webhook_events where user_id in (" + idlist + ");"
                  "delete from public.personal_scaffold_events where user_id in (" + idlist + ");"
                  "delete from public.personal_fallback_events where user_id in (" + idlist + ");"
                  "delete from public.personal_identity_adoptions where source_user_id in (" + idlist + ") or destination_user_id in (" + idlist + ");"
                  "delete from public.personal_identity_tombstones where user_id in (" + idlist + ");"
                  "delete from public.personal_profiles where user_id in (" + idlist + ");")
            for u in created:
                try:
                    t.storage_remove(t.all_objects(u))
                except Exception as e:
                    print("cleanup storage", u, e)
                t.delete_user(u)
            print("cleanup residue:", t.sql(
                f"select (select count(*) from auth.users where id in ({idlist})) as users,"
                f"(select count(*) from public.personal_identity_tombstones where user_id in ({idlist})) as tombstones"))
    failed = [r for r in RESULTS if not r[1]]
    print(f"\n{len(RESULTS) - len(failed)}/{len(RESULTS)} erasure checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
