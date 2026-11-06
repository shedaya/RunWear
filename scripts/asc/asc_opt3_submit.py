"""Option 3 (2026-11-06) SUBMIT: put version 1.1 AND both Make It Me
subscriptions (annual + monthly ONLY - never the experiment arms) into
one review submission and submit it, then read everything back.

The script refuses to submit a version-only package: if either
subscription item cannot be added (e.g. the product is still
MISSING_METADATA), it prints ITEM_ADD_FAILED, leaves the submission
unsubmitted, and exits 3.

Usage: python3 asc_opt3_submit.py"""
import json, sys, urllib.request, urllib.error
import asc_common

APP_ID = "6827973343"
SUBS = [("rw_makeitme_annual", "6829225711"),
        ("rw_makeitme_monthly", "6829225446")]
ARMS = ["6829993757", "6829993678"]  # read-back only: prove untouched

def raw(method, path, body=None):
    url = path if path.startswith("http") else asc_common.BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + asc_common.token())
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            b = resp.read()
            return resp.status, (json.loads(b) if b else {})
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            parsed = json.loads(b) if b else {}
        except json.JSONDecodeError:
            parsed = {"raw": b[:600].decode("utf-8", "replace")}
        return e.code, parsed

def find_version(vs):
    s, d = raw("GET", f"/v1/apps/{APP_ID}/appStoreVersions?limit=50")
    for v in d.get("data", []):
        if v["attributes"].get("versionString") == vs:
            return v
    return None

def sub_state(sid):
    s, d = raw("GET", f"/v1/subscriptions/{sid}")
    a = (d.get("data") or {}).get("attributes", {})
    return a.get("productId"), a.get("state")

v11 = find_version("1.1")
if not v11:
    print("FATAL: version 1.1 not found (run stage 1 first)"); sys.exit(2)
print("1.1:", v11["id"], "state:", v11["attributes"].get("appStoreState"),
      "releaseType:", v11["attributes"].get("releaseType"))
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/build")
rel = d.get("data") or {}
print("1.1 attached build:", rel.get("id"), "buildNumber",
      (rel.get("attributes") or {}).get("version"))
if not rel.get("id"):
    print("FATAL: 1.1 has no build attached"); sys.exit(2)
for pid, sid in SUBS:
    print("pre-submit sub:", sub_state(sid))

# --- Find or create the open review submission ------------------------------
sub_id = None
s, d = raw("GET", f"/v1/reviewSubmissions?filter[app]={APP_ID}"
                  f"&filter[state]=READY_FOR_REVIEW&limit=50")
for rs in d.get("data", []):
    if rs["attributes"].get("platform") == "IOS":
        sub_id = rs["id"]
        print("reusing open reviewSubmission:", sub_id)
        break
if not sub_id:
    body = {"data": {"type": "reviewSubmissions",
                     "attributes": {"platform": "IOS"},
                     "relationships": {"app": {"data": {"type": "apps",
                                                         "id": APP_ID}}}}}
    s, d = raw("POST", "/v1/reviewSubmissions", body)
    print("CREATE reviewSubmission ->", s)
    if s not in (200, 201):
        print(json.dumps(d, indent=1)[:3000]); print("SUBMISSION_CREATE_FAILED")
        sys.exit(3)
    sub_id = d["data"]["id"]
print("reviewSubmission id:", sub_id)

# --- Which items already exist? ----------------------------------------------
existing = set()
s, d = raw("GET", f"/v1/reviewSubmissions/{sub_id}/items?limit=200"
                  f"&include=appStoreVersion,subscription")
inc = {x["id"]: x for x in d.get("included", [])}
for it in d.get("data", []):
    rels = it.get("relationships", {})
    for key in ("appStoreVersion", "subscription"):
        rid = (rels.get(key, {}).get("data") or {}).get("id")
        if rid:
            existing.add(rid)
print("items already on submission:", existing)

def add_item(rel_key, rel_type, rid, label):
    if rid in existing:
        print(f"item {label}: already present")
        return True
    body = {"data": {"type": "reviewSubmissionItems",
                     "relationships": {
                         "reviewSubmission": {
                             "data": {"type": "reviewSubmissions",
                                      "id": sub_id}},
                         rel_key: {"data": {"type": rel_type, "id": rid}}}}}
    s, d = raw("POST", "/v1/reviewSubmissionItems", body)
    print(f"ADD item {label} ->", s)
    if s not in (200, 201):
        print(json.dumps(d, indent=1)[:4000])
        print("ITEM_ADD_FAILED:", label)
        return False
    print("   item id:", d["data"]["id"], "state:",
          d["data"]["attributes"].get("state"))
    return True

ok = add_item("appStoreVersion", "appStoreVersions", v11["id"], "version 1.1")
for pid, sid in SUBS:
    ok = add_item("subscription", "subscriptions", sid, pid) and ok
if not ok:
    print("SUBMIT_ABORTED: not all items could be added; "
          "submission left UNSUBMITTED")
    sys.exit(3)

# --- Submit ---------------------------------------------------------------------
body = {"data": {"type": "reviewSubmissions", "id": sub_id,
                 "attributes": {"submitted": True}}}
s, d = raw("PATCH", f"/v1/reviewSubmissions/{sub_id}", body)
print("SUBMIT PATCH ->", s)
print(json.dumps(d, indent=1)[:2500])
if s != 200:
    print("SUBMIT_FAILED"); sys.exit(4)

# --- Read back everything ---------------------------------------------------------
s, d = raw("GET", f"/v1/reviewSubmissions/{sub_id}")
ra = (d.get("data") or {}).get("attributes", {})
print("reviewSubmission state:", ra.get("state"), "| submittedDate:",
      ra.get("submittedDate"))
v11 = find_version("1.1")
print("1.1 state:", v11["attributes"].get("appStoreState"),
      "| releaseType:", v11["attributes"].get("releaseType"))
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/build")
rel = d.get("data") or {}
print("1.1 attached build:", rel.get("id"), "buildNumber",
      (rel.get("attributes") or {}).get("version"))
for pid, sid in SUBS:
    print("post-submit sub:", sub_state(sid))
for sid in ARMS:
    print("arm (untouched check):", sub_state(sid))
v10 = find_version("1.0")
print("1.0 state (must be unchanged):",
      v10["attributes"].get("appStoreState"))
print("SUBMIT_DONE")
