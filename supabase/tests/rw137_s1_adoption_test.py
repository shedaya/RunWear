#!/usr/bin/env python3
"""RW-137 S1 live test: adopt_personal_identity against THROWAWAY accounts.

Creates sandbox auth users, seeds both sides with data exercising every
DESIGN §4.3 merge rule, runs the two-phase adoption (plan -> storage
copy -> complete) exactly as the edge orchestrators do, and asserts
each rule. Also exercises the guards (S==D, unentitled destination,
double adoption, tombstoned source) and an S2->D2 pair where the
destination already has reference photos (destination set wins).

Credentials: the service_role / anon keys are fetched in-process via
the Supabase Management API (same surrogate flow as supabase_api.py)
and are never printed or written anywhere.

Cleanup: every throwaway user, storage object, adoption row and
tombstone created here is removed at the end, pass or fail.
"""
import json
import sys
import urllib.request
import urllib.error
import uuid

sys.path.insert(0, "/opt/muse/skills/custom-connector-setup/bin")
from dynamic_credentials import add_surrogate_to_request, read_json_response

REF = "ebicqznlcjbqcukjfzcf"
MGMT = "https://api.supabase.com/v1"
BASE = f"https://{REF}.supabase.co"
ALLOWED = ["api.supabase.com", f"{REF}.supabase.co"]
CONN = "custom.supabase"

RESULTS = []


def check(name, cond, extra=""):
    RESULTS.append((name, bool(cond), extra))
    print(f"{'PASS' if cond else 'FAIL'}  {name}  {extra}")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        raise RuntimeError("redirect refused")


def mgmt(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(MGMT + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"} if data else {})
    add_surrogate_to_request(req, CONN, entry_name="access_token", allowed_hosts=ALLOWED)
    with urllib.request.build_opener(NoRedirect()).open(req, timeout=120) as resp:
        return read_json_response(resp)


KEYS = {}


def keys():
    if not KEYS:
        for k in mgmt("GET", f"/projects/{REF}/api-keys"):
            KEYS[k["name"]] = k["api_key"]
    return KEYS


def api(method, path, key, payload=None, raw=None, ctype="application/json", extra_headers=None):
    url = BASE + path
    data = raw if raw is not None else (json.dumps(payload).encode() if payload is not None else None)
    headers = {"Authorization": f"Bearer {key}", "apikey": key}
    if data is not None:
        headers["Content-Type"] = ctype
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect()).open(req, timeout=120) as resp:
            body = resp.read()
            return resp.status, json.loads(body) if body and ctype == "application/json" else body
    except urllib.error.HTTPError as e:
        body = e.read()
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, body.decode("utf-8", "replace")


def svc():
    return keys()["service_role"]


def sql(query):
    return mgmt("POST", f"/projects/{REF}/database/query", {"query": query})


def create_user(tag):
    email = f"rw137-{tag}-{uuid.uuid4().hex[:8]}@example.com"
    status, user = api("POST", "/auth/v1/admin/users", svc(),
                       {"email": email, "password": "Throwaway!2345",
                        "email_confirm": True, "user_metadata": {"rw137_test": tag}})
    assert status in (200, 201), (status, user)
    return user["id"]


def delete_user(uid):
    api("DELETE", f"/auth/v1/admin/users/{uid}", svc())


def insert(table, rows):
    status, out = api("POST", f"/rest/v1/{table}", svc(), rows,
                      extra_headers={"Prefer": "return=representation"})
    assert status in (200, 201), (table, status, out)
    return out


def select(table, query):
    status, out = api("GET", f"/rest/v1/{table}?{query}", svc())
    assert status == 200, (table, status, out)
    return out


def rpc(fn, args):
    status, out = api("POST", f"/rest/v1/rpc/{fn}", svc(), args)
    assert status == 200, (fn, status, out)
    return out


def upload(path, content):
    status, out = api("POST", f"/storage/v1/object/personal-heroes/{path}", svc(),
                      raw=content, ctype="image/jpeg")
    assert status == 200, (path, status, out)


def storage_list(prefix):
    status, out = api("POST", "/storage/v1/object/list/personal-heroes", svc(),
                      {"prefix": prefix, "limit": 1000})
    assert status == 200, (prefix, status, out)
    return out


def storage_copy(src, dst):
    status, out = api("POST", "/storage/v1/object/copy", svc(),
                      {"bucketId": "personal-heroes", "sourceKey": src, "destinationKey": dst})
    assert status == 200, (src, dst, status, out)


def storage_remove(paths):
    if not paths:
        return
    status, out = api("DELETE", "/storage/v1/object/personal-heroes", svc(), {"prefixes": paths})
    assert status == 200, (status, out)


def all_objects(uid):
    """Every object name under {uid}/ via recursive listing."""
    found = []

    def walk(prefix):
        for o in storage_list(prefix):
            if o.get("id") is None:
                walk(f"{prefix}/{o['name']}")
            else:
                found.append(f"{prefix}/{o['name']}")

    for folder in ["refs", "refs-small", "heroes", "tmp", "repair"]:
        walk(f"{uid}/{folder}")
    return sorted(found)


def run_adoption(source, dest, proof):
    """The orchestrator loop the edge functions run: adopt -> copy ->
    adopt again -> cleanup deletes after done."""
    res = rpc("adopt_personal_identity",
              {"p_source": source, "p_destination": dest, "p_proof": proof})
    if res.get("state") == "started":
        for c in res["copies"]:
            storage_copy(c["from"], c["to"])
        res = rpc("adopt_personal_identity",
                  {"p_source": source, "p_destination": dest, "p_proof": proof})
    if res.get("state") == "done":
        storage_remove(res.get("source_cleanup_paths", []))
    return res


def seed_profile(uid, **kw):
    row = {"user_id": uid, "personalization_on": False, "gender": "MALE",
           "free_generations_used": 0, "entitled": False}
    row.update(kw)
    insert("personal_profiles", row)


def seed_hero(uid, combo, variant, fname, gate=None):
    rows = insert("personal_heroes", {
        "user_id": uid, "combination_id": combo, "gender": "FEMALE",
        "weather": "clear", "temp_bracket": "mild", "time_of_day": "day",
        "outfit_signature": f"sig-{combo}", "outfit_items": [],
        "storage_path": f"{uid}/heroes/{combo}/{fname}", "variant": variant,
        "gate_result": gate})
    upload(f"{uid}/heroes/{combo}/{fname}", f"img-{uid[:6]}-{combo}-{variant}".encode())
    return rows[0]["id"]


def seed_cell(uid, combo, version, ever=True, free=False):
    status, out = api("POST", "/rest/v1/personal_cells", svc(),
                      {"user_id": uid, "combination_id": combo, "version": version,
                       "ever_delivered": ever, "free_counted": free},
                      extra_headers={"Prefer": "resolution=merge-duplicates,return=representation"})
    assert status in (200, 201), (status, out)


def main():
    created_users = []
    try:
        # ---------------- Case A: destination has NO refs -------------
        S = create_user("srcA"); created_users.append(S)
        D = create_user("dstA"); created_users.append(D)
        seed_profile(S, personalization_on=True, gender="FEMALE",
                     free_generations_used=3, entitled=True,
                     entitlement_source="test-toggle", photo_epoch=2,
                     photo_set_hash="hashS")
        seed_profile(D, personalization_on=False, gender="MALE",
                     free_generations_used=2, entitled=True,
                     entitlement_source="test-toggle")
        # refs on S only
        for pos in (1, 2):
            insert("personal_reference_photos",
                   {"user_id": S, "storage_path": f"{S}/refs/ref-{pos}.jpg", "position": pos})
            upload(f"{S}/refs/ref-{pos}.jpg", f"ref{pos}".encode())
            upload(f"{S}/refs-small/ref-{pos}.jpg", f"refsmall{pos}".encode())
        upload(f"{S}/tmp/junk.jpg", b"junk")
        # heroes: S cellA v1+v2, cellB v1 ; D cellA v1 (collision), cellC v1
        sA1 = seed_hero(S, "cellA", 1, "heroA1.jpg", gate={"verdict": "pass", "origin": "S"})
        sA2 = seed_hero(S, "cellA", 2, "heroA2.jpg", gate={"verdict": "pass", "origin": "S"})
        sB1 = seed_hero(S, "cellB", 1, "heroB1.jpg", gate={"verdict": "pass", "origin": "S"})
        dA1 = seed_hero(D, "cellA", 1, "heroA1-D.jpg", gate={"verdict": "pass", "origin": "D"})
        dC1 = seed_hero(D, "cellC", 1, "heroC1.jpg")
        seed_cell(S, "cellA", 5); seed_cell(S, "cellB", 2)
        seed_cell(D, "cellA", 3); seed_cell(D, "cellC", 7)
        # consents: S (v1,t1) (v2,t2); D (v1,t1) duplicate
        t1 = "2026-11-01T10:00:00Z"; t2 = "2026-11-02T10:00:00Z"
        insert("personal_consents", [
            {"user_id": S, "consent_version": "v1", "attested_own_likeness": True, "attested_adult": True, "created_at": t1},
            {"user_id": S, "consent_version": "v2", "attested_own_likeness": True, "attested_adult": True, "created_at": t2},
            {"user_id": D, "consent_version": "v1", "attested_own_likeness": True, "attested_adult": True, "created_at": t1},
        ])
        # jobs: S terminal w/ hero, S terminal no hero, S queued
        jobs = insert("personal_generation_jobs", [
            {"user_id": S, "combination_id": "cellB", "gender": "FEMALE", "weather": "clear",
             "temp_bracket": "mild", "time_of_day": "day", "outfit_signature": "sig-cellB",
             "kind": "subscriber_fill", "priority": 10, "status": "COMPLETED", "result_hero_id": sB1},
            {"user_id": S, "combination_id": "cellA", "gender": "FEMALE", "weather": "clear",
             "temp_bracket": "mild", "time_of_day": "day", "outfit_signature": "sig-cellA",
             "kind": "subscriber_fill", "priority": 10, "status": "FAILED", "result_hero_id": None},
            {"user_id": S, "combination_id": "cellC", "gender": "FEMALE", "weather": "clear",
             "temp_bracket": "mild", "time_of_day": "day", "outfit_signature": "sig-cellC",
             "kind": "subscriber_fill", "priority": 10, "status": "QUEUED", "result_hero_id": None},
        ])
        # flags: on imported hero (cellA v2) and on collision hero (cellA v1)
        insert("personal_hero_flags", [
            {"user_id": S, "hero_id": sA2, "combination_id": "cellA", "variant": 2,
             "storage_path": f"{S}/heroes/cellA/heroA2.jpg", "reason": "looks_broken"},
            {"user_id": S, "hero_id": sA1, "combination_id": "cellA", "variant": 1,
             "storage_path": f"{S}/heroes/cellA/heroA1.jpg", "reason": "outfit_mismatch"},
        ])
        insert("personal_entitlement_events",
               {"user_id": S, "entitled": True, "source": "test-toggle"})

        res = run_adoption(S, D, {"type": "transfer_event", "rc_event_id": "TEST-EVT-A"})
        check("A: adoption done", res.get("state") == "done", json.dumps(res.get("counts", {})))
        counts = res.get("counts", {})
        check("A: counts heroes_imported=2", counts.get("heroes_imported") == 2)
        check("A: counts collisions=1", counts.get("heroes_collisions_kept_destination") == 1)
        check("A: counts refs_imported=2", counts.get("refs_imported") == 2)
        check("A: counts jobs_rekeyed=2", counts.get("jobs_rekeyed") == 2)
        check("A: counts jobs_cancelled=1", counts.get("jobs_cancelled") == 1)
        check("A: counts flags=2", counts.get("hero_flags_rekeyed") == 2)
        check("A: counts consents_imported=1", counts.get("consents_imported") == 1)
        check("A: counts free sum=5", counts.get("free_generations_used_sum") == 5)

        prof = select("personal_profiles", f"user_id=eq.{D}")[0]
        check("A: profile free=5", prof["free_generations_used"] == 5)
        check("A: profile personalization_on=true (inherited)", prof["personalization_on"] is True)
        check("A: profile gender inherited FEMALE", prof["gender"] == "FEMALE")
        check("A: profile photo hash follows refs", prof["photo_set_hash"] == "hashS")
        check("A: source profile gone (husk)",
              len(select("personal_profiles", f"user_id=eq.{S}")) == 0)

        dheroes = {(h["combination_id"], h["variant"]): h
                   for h in select("personal_heroes", f"user_id=eq.{D}")}
        check("A: D bank has 4 heroes", len(dheroes) == 4, str(sorted(dheroes)))
        check("A: collision hero is D's original row", dheroes[("cellA", 1)]["id"] == dA1)
        check("A: imported hero path rewritten",
              dheroes[("cellA", 2)]["storage_path"] == f"{D}/heroes/cellA/heroA2.jpg")
        check("A: imported hero kept gate verdict",
              (dheroes[("cellB", 1)]["gate_result"] or {}).get("origin") == "S")
        check("A: S heroes gone", len(select("personal_heroes", f"user_id=eq.{S}")) == 0)

        dcells = {c["combination_id"]: c for c in select("personal_cells", f"user_id=eq.{D}")}
        check("A: cell versions max", (dcells["cellA"]["version"], dcells["cellB"]["version"], dcells["cellC"]["version"]) == (5, 2, 7),
              str({k: v["version"] for k, v in dcells.items()}))
        check("A: S cells gone", len(select("personal_cells", f"user_id=eq.{S}")) == 0)

        drefs = select("personal_reference_photos", f"user_id=eq.{D}")
        check("A: D refs=2 adopted, paths rewritten",
              len(drefs) == 2 and all(r["storage_path"].startswith(f"{D}/refs/") for r in drefs))

        dcons = select("personal_consents", f"user_id=eq.{D}")
        check("A: D consents union=2 (dupe collapsed)", len(dcons) == 2)

        djobs = select("personal_generation_jobs", f"user_id=eq.{D}")
        term = [j for j in djobs if j["status"] == "COMPLETED"]
        check("A: terminal jobs re-keyed=2", len(djobs) == 2)
        check("A: job result_hero_id remapped to D hero",
              term and term[0]["result_hero_id"] == dheroes[("cellB", 1)]["id"])
        sjobs = select("personal_generation_jobs", f"user_id=eq.{S}")
        check("A: queued source job CANCELLED in place",
              len(sjobs) == 1 and sjobs[0]["status"] == "CANCELLED")

        dflags = select("personal_hero_flags", f"user_id=eq.{D}")
        check("A: flags re-keyed=2 with D hero ids",
              len(dflags) == 2 and all(f["hero_id"] in (dheroes[("cellA", 1)]["id"], dheroes[("cellA", 2)]["id"]) for f in dflags))

        sev = select("personal_entitlement_events", f"user_id=eq.{S}")
        dev = select("personal_entitlement_events", f"user_id=eq.{D}&source=eq.identity-adoption")
        check("A: S event history untouched (never rewritten)", len(sev) == 1)
        check("A: D gained identity-adoption audit note", len(dev) == 1)

        sobj = all_objects(S)
        dobj = all_objects(D)
        check("A: S storage empty after cleanup", sobj == [], str(sobj))
        check("A: D storage = own 2 + copied 6", len(dobj) == 8, str(dobj))
        check("A: collision bytes NOT overwritten (no S heroA1 under D)",
              f"{D}/heroes/cellA/heroA1.jpg" not in dobj and f"{D}/heroes/cellA/heroA1-D.jpg" in dobj)

        # double adoption -> refused
        res2 = rpc("adopt_personal_identity",
                   {"p_source": S, "p_destination": D, "p_proof": {"type": "transfer_event"}})
        check("guard: double adoption refused", res2.get("reason") == "source_already_adopted", json.dumps(res2))

        # --------------- Case B: destination HAS refs -----------------
        S2 = create_user("srcB"); created_users.append(S2)
        D2 = create_user("dstB"); created_users.append(D2)
        seed_profile(S2, personalization_on=True, gender="FEMALE",
                     free_generations_used=1, entitled=True, photo_set_hash="hashS2")
        seed_profile(D2, personalization_on=True, gender="MALE",
                     free_generations_used=0, entitled=True, photo_set_hash="hashD2")
        insert("personal_reference_photos",
               {"user_id": S2, "storage_path": f"{S2}/refs/ref-1.jpg", "position": 1})
        upload(f"{S2}/refs/ref-1.jpg", b"s2ref")
        upload(f"{S2}/refs-small/ref-1.jpg", b"s2refsmall")
        insert("personal_reference_photos",
               {"user_id": D2, "storage_path": f"{D2}/refs/ref-1.jpg", "position": 1})
        upload(f"{D2}/refs/ref-1.jpg", b"d2ref")
        seed_hero(S2, "cellZ", 1, "heroZ1.jpg")

        resB = run_adoption(S2, D2, {"type": "reconcile_alias", "seen_via": "aliases"})
        check("B: adoption done", resB.get("state") == "done", json.dumps(resB.get("counts", {})))
        check("B: refs NOT adopted (destination wins)",
              resB.get("counts", {}).get("refs_adopted") is False)
        d2refs = select("personal_reference_photos", f"user_id=eq.{D2}")
        check("B: D2 refs still its own", len(d2refs) == 1 and d2refs[0]["storage_path"] == f"{D2}/refs/ref-1.jpg")
        check("B: S2 ref rows deleted", len(select("personal_reference_photos", f"user_id=eq.{S2}")) == 0)
        check("B: hero still imported", len(select("personal_heroes", f"user_id=eq.{D2}")) == 1)
        prof2 = select("personal_profiles", f"user_id=eq.{D2}")[0]
        check("B: gender destination wins (opted in)", prof2["gender"] == "MALE")
        check("B: photo hash stays destination's", prof2["photo_set_hash"] == "hashD2")
        check("B: S2 storage cleaned incl. un-adopted refs", all_objects(S2) == [], str(all_objects(S2)))

        # ------------------------- Guards ------------------------------
        r = rpc("adopt_personal_identity",
                {"p_source": D, "p_destination": D, "p_proof": {"type": "transfer_event"}})
        check("guard: S==D refused", r.get("reason") == "source_equals_destination")
        U = create_user("unent"); created_users.append(U)
        seed_profile(U, entitled=False)
        S3 = create_user("srcC"); created_users.append(S3)
        seed_profile(S3, entitled=True)
        r = rpc("adopt_personal_identity",
                {"p_source": S3, "p_destination": U, "p_proof": {"type": "transfer_event"}})
        check("guard: unentitled destination refused", r.get("reason") == "destination_not_entitled")
        insert("personal_identity_tombstones", {"user_id": S3, "source": "rw137-test"})
        r = rpc("adopt_personal_identity",
                {"p_source": S3, "p_destination": D, "p_proof": {"type": "transfer_event"}})
        check("guard: tombstoned source refused", r.get("reason") == "source_tombstoned")
        r = rpc("adopt_personal_identity",
                {"p_source": str(uuid.uuid4()), "p_destination": D, "p_proof": {"type": "transfer_event"}})
        check("guard: missing source refused", r.get("reason") == "source_missing")
    finally:
        # Cleanup: sweep rows in the cascade-safe order (content rows ->
        # cells -> profile), then adoption rows + tombstones, storage,
        # and the auth users themselves. (Deleting an auth user that
        # still has hero rows fails on the bump trigger's mid-cascade
        # cell insert — the RW-16.30 lesson.)
        if created_users:
            idlist = ",".join(f"'{u}'" for u in created_users)
            sql("delete from public.personal_generation_jobs where user_id in (" + idlist + ");"
                "delete from public.personal_scaffold_events where user_id in (" + idlist + ");"
                "delete from public.personal_fallback_events where user_id in (" + idlist + ");"
                "delete from public.personal_heroes where user_id in (" + idlist + ");"
                "delete from public.personal_cells where user_id in (" + idlist + ");"
                "delete from public.personal_reference_photos where user_id in (" + idlist + ");"
                "delete from public.personal_consents where user_id in (" + idlist + ");"
                "delete from public.personal_entitlement_events where user_id in (" + idlist + ");"
                "delete from public.personal_hero_flags where user_id in (" + idlist + ");"
                "delete from public.personal_webhook_events where user_id in (" + idlist + ");"
                "delete from public.personal_identity_adoptions where source_user_id in (" + idlist + ") or destination_user_id in (" + idlist + ");"
                "delete from public.personal_identity_tombstones where user_id in (" + idlist + ");"
                "delete from public.personal_profiles where user_id in (" + idlist + ");")
            for u in created_users:
                try:
                    storage_remove(all_objects(u))
                except Exception as e:
                    print("cleanup storage", u, e)
                delete_user(u)
            residue = sql(f"select (select count(*) from public.personal_identity_adoptions where source_user_id in ({idlist}) or destination_user_id in ({idlist})) as adoptions,"
                          f"(select count(*) from auth.users where id in ({idlist})) as users,"
                          f"(select count(*) from public.personal_profiles where user_id in ({idlist})) as profiles")
            print("cleanup residue:", residue)

    failed = [r for r in RESULTS if not r[1]]
    print(f"\n{len(RESULTS) - len(failed)}/{len(RESULTS)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
