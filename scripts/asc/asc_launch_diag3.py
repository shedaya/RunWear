"""READ-ONLY diagnostic 3 (2026-11-07): do the Make It Me
subscriptions exist under ANY in-app-purchase listing (v1 or v2) with
a different id, and how does the existing review-submission item
serialize its relationships when includes are requested? GETs only.

Usage: python3 asc_launch_diag3.py"""
import json, urllib.request, urllib.error
import asc_common

APP_ID = "6827973343"
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

for label, path in (
    ("inAppPurchasesV2", f"/v1/apps/{APP_ID}/inAppPurchasesV2?limit=50"),
    ("inAppPurchases(v1)", f"/v1/apps/{APP_ID}/inAppPurchases?limit=50"),
):
    s, d = get(path)
    print(f"== {label} -> {s} ({len(d.get('data', []))} rows)")
    for row in d.get("data", []):
        a = row.get("attributes", {})
        print("   ", row["id"], "| productId:", a.get("productId"),
              "| type:", a.get("inAppPurchaseType"), "| state:",
              a.get("state"), "| name:", a.get("name"))
    if s != 200:
        print("   ", json.dumps(d)[:300])

s, d = get(f"/v1/reviewSubmissions/{SUBMISSION_ID}/items?limit=200"
           f"&include=appStoreVersion")
print("== submission items with include ->", s)
print(json.dumps(d, indent=1)[:3500])
print("DIAG3_DONE")
