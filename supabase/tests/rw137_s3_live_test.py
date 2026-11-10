#!/usr/bin/env python3
"""RW-137 S3 live checks against the DEPLOYED functions, throwaway
accounts only:
- request-identity-adoption without a JWT -> 401 (gateway).
- ...with a throwaway JWT claiming a forged source: RevenueCat's
  public-key subscriber GET answers an EMPTY record for a never-seen
  user (verified live by this test — not a 404), so the claim dies
  at the entitlement gate as a not_entitled no-op and NOTHING moves.
  (The corroboration branch itself is proven against the mock RC in
  the S3 harness.)
- reconcile-personal-entitlement with a throwaway JWT: same empty
  record -> state_unchanged with adoption_available null, proving
  v10 boots and its new adoption code path is inert without an RC
  alias chain.
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


def main():
    created = []
    try:
        # No JWT at all.
        status, _ = t.api("POST", "/functions/v1/request-identity-adoption",
                          t.keys()["anon"], {})
        check("deployed request-identity-adoption: no JWT -> 401", status == 401, f"status={status}")

        import uuid as uuidlib
        suffix = uuidlib.uuid4().hex[:8]
        email = f"rw137-forge-{suffix}@example.com"
        status, user = t.api("POST", "/auth/v1/admin/users", t.svc(),
                             {"email": email, "password": "Throwaway!2345",
                              "email_confirm": True, "user_metadata": {"rw137_test": "forge"}})
        caller = user["id"]; created.append(caller)
        victim = t.create_user("forge-victim"); created.append(victim)
        t.seed_profile(victim, personalization_on=True, free_generations_used=4, entitled=True)
        hero = t.seed_hero(victim, "cellA", 1, "heroA1.jpg")
        t.seed_profile(caller, entitled=False)

        status, tok = t.api("POST", "/auth/v1/token?grant_type=password", t.keys()["anon"],
                            {"email": email, "password": "Throwaway!2345"})
        token = tok["access_token"]
        status, resp = t.api("POST", "/functions/v1/request-identity-adoption", t.keys()["anon"],
                             {"source_user_id": victim},
                             extra_headers={"Authorization": f"Bearer {token}"})
        # RC's public-key subscriber GET answers an EMPTY record for a
        # never-seen user (not a 404): usable payload, no entitlement,
        # no alias chain -> the forged claim dies at the entitlement
        # gate as a not_entitled no-op.
        check("forged claim against real RC: not_entitled no-op (empty RC record)",
              status == 200 and resp.get("adopted") is False and resp.get("reason") == "not_entitled",
              f"{status} {json.dumps(resp)[:200]}")
        rows = t.sql(f"select count(*) as n from public.personal_identity_adoptions "
                     f"where source_user_id='{victim}' or destination_user_id='{caller}'")
        check("forged claim created zero adoption rows", rows[0]["n"] == 0)
        vprof = t.select("personal_profiles", f"user_id=eq.{victim}")[0]
        check("victim profile untouched", vprof["free_generations_used"] == 4)
        check("victim hero untouched",
              len(t.select("personal_heroes", f"user_id=eq.{victim}")) == 1)

        status, resp = t.api("POST", "/functions/v1/reconcile-personal-entitlement", t.keys()["anon"],
                             {"platform": "ios"},
                             extra_headers={"Authorization": f"Bearer {token}"})
        check("deployed reconcile v10: empty RC record -> state_unchanged, adoption_available null",
              status == 200 and resp.get("reason") == "state_unchanged"
              and resp.get("adoption_available") is None,
              f"{status} {json.dumps(resp)[:260]}")
    finally:
        if created:
            idlist = ",".join(f"'{u}'" for u in created)
            t.sql("delete from public.personal_generation_jobs where user_id in (" + idlist + ");"
                  "delete from public.personal_heroes where user_id in (" + idlist + ");"
                  "delete from public.personal_cells where user_id in (" + idlist + ");"
                  "delete from public.personal_profiles where user_id in (" + idlist + ");")
            for u in created:
                try:
                    t.storage_remove(t.all_objects(u))
                except Exception as e:
                    print("cleanup storage", u, e)
                t.delete_user(u)
            print("cleanup residue:", t.sql(f"select count(*) as users from auth.users where id in ({idlist})"))
    failed = [r for r in RESULTS if not r[1]]
    print(f"\n{len(RESULTS) - len(failed)}/{len(RESULTS)} live S3 checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
