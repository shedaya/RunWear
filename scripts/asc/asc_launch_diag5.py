"""READ-ONLY diagnostic 5 (2026-11-07): the subscription resource
serializes a 'versions' relationship and so does its group. Print
what those endpoints return for the never-yet-submitted Make It Me
products - the semantics decide whether linking a subscription to
version 1.1 is the portal's 'select products for this version' step.
GETs only.

Usage: python3 asc_launch_diag5.py"""
import json, urllib.request, urllib.error
import asc_common

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
    ("annual versions", "/v1/subscriptions/6829225711/versions?limit=50"),
    ("monthly versions", "/v1/subscriptions/6829225446/versions?limit=50"),
    ("group versions",
     "/v1/subscriptionGroups/22531902/versions?limit=50"),
):
    s, d = get(path)
    print(f"== {label} -> {s}")
    for row in d.get("data", []):
        a = row.get("attributes", {})
        print("   ", row["id"], "| version:", a.get("versionString"),
              "| state:", a.get("appStoreState"))
    if s != 200 or not d.get("data"):
        print("   ", json.dumps(d)[:500])
print("DIAG5_DONE")
