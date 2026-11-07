"""READ-ONLY diagnostic 2 (2026-11-07): fetch Apple's official App
Store Connect OpenAPI specification on the runner and print the
ReviewSubmissionItem relationship schema - ground truth for how a
subscription is added to a review submission. No ASC API calls, no
writes. Usage: python3 asc_launch_diag2.py"""
import io, json, sys, urllib.request, zipfile

URLS = [
    "https://developer.apple.com/sample-code/app-store-connect/"
    "app-store-connect-openapi-specification.json.zip",
    "https://developer.apple.com/tutorials/data/documentation/"
    "technologies/appstoreconnectapi/openapi.json",
]

spec = None
for u in URLS:
    try:
        req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = resp.read()
        print("fetched", u, len(data), "bytes")
        if u.endswith(".zip"):
            z = zipfile.ZipFile(io.BytesIO(data))
            name = [n for n in z.namelist() if n.endswith(".json")][0]
            spec = json.loads(z.read(name))
        else:
            spec = json.loads(data)
        break
    except Exception as e:
        print("FAILED", u, "->", repr(e)[:200])

if spec is None:
    print("SPEC_UNAVAILABLE"); sys.exit(3)

schemas = spec.get("components", {}).get("schemas", {})
for name, sch in schemas.items():
    if "ReviewSubmissionItem" in name:
        rels = (sch.get("properties", {}).get("relationships", {})
                .get("properties", {}))
        if rels:
            print(f"== {name}: relationships ->", sorted(rels.keys()))
        else:
            print(f"== {name}: (no relationships properties)")
        if "Create" in name:
            print(json.dumps(sch, indent=1)[:3000])
print("DIAG2_DONE")
