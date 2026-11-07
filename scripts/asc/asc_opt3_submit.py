"""RunWear 1.1 SUBMIT (Option 3 lineage, launch lane 2026-11-07):
submit version 1.1 AND both Make It Me subscriptions (annual +
monthly ONLY - the experiment arms are never resolved, added, or
written) in one review submission, then read everything back.

Empirically established in the 2026-11-07 launch lane (runs 102-110):
- The review-submission machinery works via API: run 42373436516
  created reviewSubmission b49523a5-5832-449b-bda7-da075580d9f8 and
  added the version-1.1 item (READY_FOR_REVIEW).
- reviewSubmissionItems REJECTS both seemingly-obvious subscription
  relationships: 'subscription' (409 ENTITY_ERROR.RELATIONSHIP.
  UNKNOWN) and 'inAppPurchase' (same). The numeric subscription ids
  do not resolve as inAppPurchases resources (404); the v1
  inAppPurchases LIST does carry the products (UUIDs, type
  AUTOMATICALLY_RENEWABLE_SUBSCRIPTION, state CREATED) - recorded
  here for the next operator, but neither key adds an item today.
- A subscription in MISSING_METADATA cannot be submitted by any
  mechanism. As of the lane, BOTH products are MISSING_METADATA for
  exactly one reason: the App Review paywall screenshot has not been
  uploaded (every other metadata item is probe-verified present).

Behaviour:
- If either target subscription is still MISSING_METADATA, the
  script verifies state, prints SUBMIT_BLOCKED_MISSING_METADATA with
  the precise reason, and exits 3 having written NOTHING.
- Once both products leave MISSING_METADATA (screenshot uploaded),
  it reuses/creates the open review submission, ensures the version
  item is present (an 'already added' 409 counts as present), tries
  the 'subscription' then the 'inAppPurchase' item relationships for
  each product and reports Apple's answers verbatim, refuses to
  submit a version-only package, and only then PATCHes submitted.

Usage: python3 asc_opt3_submit.py"""
import json, sys, urllib.request, urllib.error
import asc_common

APP_ID = "6827973343"
SUBS = [("rw_makeitme_annual", "6829225711"),
        ("rw_makeitme_monthly", "6829225446")]
ARMS = ["6829993757", "6829993678"]  # state read-back only (as before)

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

def resolve_iap_uuids():
    targets = {pid for pid, _ in SUBS}
    s, d = raw("GET", f"/v1/apps/{APP_ID}/inAppPurchases?limit=200")
    found = {}
    for row in d.get("data", []):
        a = row.get("attributes", {})
        if a.get("productId") in targets:
            found[a["productId"]] = row["id"]
    return found

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

states = {}
for pid, sid in SUBS:
    states[pid] = sub_state(sid)
    print("sub:", states[pid])
    s2, d2 = raw("GET", f"/v1/subscriptions/{sid}/appStoreReviewScreenshot")
    print("   review screenshot present:", bool(d2.get("data")))

# --- THE GATE: no submission while metadata is incomplete ---------------------
blocked = [pid for pid, (_, st) in states.items()
           if st == "MISSING_METADATA"]
if blocked:
    print("SUBMIT_BLOCKED_MISSING_METADATA:", blocked)
    print("Reason (probe-verified): the sole missing metadata item on "
          "each product is its App Review screenshot - a genuine "
          "paywall capture from build 30 (both prices visible). "
          "Upload it to both subscriptions "
          "(subscriptionAppStoreReviewScreenshots), confirm both "
          "products leave MISSING_METADATA, then re-run this script.")
    sys.exit(3)

IAP_UUIDS = resolve_iap_uuids()
print("iap uuids:", IAP_UUIDS)

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

# --- Which items already exist? (include is REQUIRED for linkage) -------------
existing = set()
s, d = raw("GET", f"/v1/reviewSubmissions/{sub_id}/items?limit=200"
                  f"&include=appStoreVersion")
for it in d.get("data", []):
    rels = it.get("relationships", {})
    for key, rv in rels.items():
        rid = (rv.get("data") or {}).get("id")
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
    print(f"ADD item {label} via {rel_key} ->", s)
    if s in (200, 201):
        print("   item id:", d["data"]["id"], "state:",
              d["data"]["attributes"].get("state"))
        return True
    errs = json.dumps(d) 
    if "already added to this reviewSubmission" in errs:
        print(f"   (Apple: {label} was already added - counts as present)")
        return True
    print(json.dumps(d, indent=1)[:2500])
    print("ITEM_ADD_FAILED:", label, "via", rel_key)
    return False

ok = add_item("appStoreVersion", "appStoreVersions", v11["id"],
              "version 1.1")
for pid, sid in SUBS:
    good = add_item("subscription", "subscriptions", sid, pid)
    if not good:
        good = add_item("inAppPurchase", "inAppPurchases",
                        IAP_UUIDS.get(pid), pid)
    ok = good and ok
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
