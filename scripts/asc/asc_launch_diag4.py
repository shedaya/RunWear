"""READ-ONLY diagnostic 4 (2026-11-07): ground truth for how a
subscription joins a review submission.
  1. Print every relationship key on the subscription resource and
     on its subscription group (the API's own serialization).
  2. Fetch Apple's OpenAPI spec (following the HTTP 300 choices page
     if needed) and print the ReviewSubmissionItemCreateRequest
     relationships, plus any schema whose name joins Subscription
     with Review/Submission. GETs only; the only writes are none.

Usage: python3 asc_launch_diag4.py"""
import io, json, re, sys, urllib.request, urllib.error, zipfile
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
    ("subscription annual", "/v1/subscriptions/6829225711"),
    ("subscription group", "/v1/subscriptionGroups/22531902"),
):
    s, d = get(path)
    rels = (d.get("data") or {}).get("relationships", {})
    print(f"== {label} -> {s}; relationships:", sorted(rels.keys()))

# --- OpenAPI spec -------------------------------------------------------------
SPEC = ("https://developer.apple.com/sample-code/app-store-connect/"
        "app-store-connect-openapi-specification.json.zip")

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, resp.read(), resp.headers.get("Location")
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers.get("Location")

code, body, loc = fetch(SPEC)
print("spec fetch ->", code, "bytes:", len(body or b""))
spec = None
if code == 300 and body:
    text = body.decode("utf-8", "replace")
    links = re.findall(r'href="([^"]+)"', text)
    print("300 choices links:", links[:10])
    for link in links:
        if ".json" in link:
            url = link if link.startswith("http") else \
                "https://developer.apple.com" + link
            code, body, loc = fetch(url)
            print("choice fetch", url, "->", code, len(body or b""))
            if code == 200:
                break
if code == 200 and body:
    data = body
    if data[:2] == b"PK":
        z = zipfile.ZipFile(io.BytesIO(data))
        name = [n for n in z.namelist() if n.endswith(".json")][0]
        data = z.read(name)
    spec = json.loads(data)

if spec is None:
    print("SPEC_STILL_UNAVAILABLE")
else:
    schemas = spec.get("components", {}).get("schemas", {})
    for name, sch in schemas.items():
        low = name.lower()
        if "reviewsubmissionitem" in low or \
           ("subscription" in low and ("review" in low or
                                       "submission" in low)):
            rels = (sch.get("properties", {}).get("relationships", {})
                    .get("properties", {}))
            print(f"SCHEMA {name}: relationships ->",
                  sorted(rels.keys()) if rels else "(none)")
    # also: any path mentioning both subscription and review
    for p in spec.get("paths", {}):
        if "subscription" in p and "review" in p:
            print("PATH:", p)
print("DIAG4_DONE")
