"""READ-ONLY diagnostic (2026-11-07): the Option 3 submit script's
'subscription' relationship on reviewSubmissionItems was rejected by
the ASC API ("'subscription' is not a relationship on the resource
'reviewSubmissionItems'"). In the unified IAP model, an auto-renewable
subscription is also an inAppPurchases resource. This script checks
whether the two Make It Me subscription ids resolve as inAppPurchases
(and with what type/state), and prints the open review submission's
current items. GETs only; never writes.

Usage: python3 asc_launch_diag.py"""
import json, urllib.request, urllib.error
import asc_common

SUBS = ["6829225711", "6829225446"]
SUBMISSION_ID = "b49523a5-5832-449b-bda7-da075580d9f8"

def get(path):
    url = path if path.startswith("http") else asc_common.BASE + path
    req = urllib.request.Request(url, method="GET")
    req.add_header("Authorization", "Bearer " + asc_common.token())
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            b = resp.read()
            return resp.status, (json.loads(b) if b else {})
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            parsed = json.loads(b) if b else {}
        except json.JSONDecodeError:
            parsed = {"raw": b[:400].decode("utf-8", "replace")}
        return e.code, parsed

for sid in SUBS:
    s, d = get(f"/v1/inAppPurchases/{sid}")
    a = (d.get("data") or {}).get("attributes", {})
    print(f"inAppPurchases/{sid} -> {s} | productId:",
          a.get("productId"), "| type:", a.get("inAppPurchaseType"),
          "| state:", a.get("state"), "| name:", a.get("name"))
    if s != 200:
        print("   ", json.dumps(d)[:400])

s, d = get(f"/v1/reviewSubmissions/{SUBMISSION_ID}")
ra = (d.get("data") or {}).get("attributes", {})
print("reviewSubmission:", SUBMISSION_ID, "| state:", ra.get("state"),
      "| submitted:", ra.get("submitted"), "| platform:",
      ra.get("platform"))
s, d = get(f"/v1/reviewSubmissions/{SUBMISSION_ID}/items?limit=200")
for it in d.get("data", []):
    rels = it.get("relationships", {})
    keys = {k: (v.get("data") or {}).get("id") for k, v in rels.items()
            if v.get("data")}
    print("item:", it["id"], "| state:",
          it.get("attributes", {}).get("state"), "| relationships:",
          keys)
print("DIAG_DONE")
