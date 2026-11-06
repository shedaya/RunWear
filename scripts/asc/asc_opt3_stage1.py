"""Option 3 (2026-11-06) STAGE 1: create version 1.1, test-attaching the
LIVE 1.0 build (build 17) to it, then prepare the version for a
monetization-only submission (localization carry-over, What's New,
App Review detail). Writes are confined to the NEW 1.1 version; 1.0 is
read but never modified.

If build 17 cannot be attached, the script prints the API error
verbatim plus the marker ATTACH_BUILD17_FAILED and exits 3, having
changed nothing except the (empty) 1.1 version's existence.

Usage: python3 asc_opt3_stage1.py"""
import json, sys, urllib.request, urllib.error
import asc_common

APP_ID = "6827973343"
WHATS_NEW = ("Premium is here: Make It Me subscriptions are now "
             "available - unlock unlimited looks that show you in every "
             "outfit. Your first 5 looks are still free.")

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

# --- Resolve 1.0 + its attached build (build 17) ---------------------------
v10 = find_version("1.0")
if not v10:
    print("FATAL: version 1.0 not found"); sys.exit(2)
print("1.0 version id:", v10["id"], "state:",
      v10["attributes"].get("appStoreState"))
s, d = raw("GET", f"/v1/appStoreVersions/{v10['id']}/build")
b17 = (d.get("data") or {}).get("id")
ba = (d.get("data") or {}).get("attributes", {})
print("1.0 attached build id:", b17, "buildNumber:", ba.get("version"),
      "processingState:", ba.get("processingState"))
if not b17:
    print("FATAL: 1.0 has no attached build"); sys.exit(2)
copyright_ = v10["attributes"].get("copyright")

# --- Create 1.1 if absent ---------------------------------------------------
v11 = find_version("1.1")
if v11:
    print("1.1 already exists:", v11["id"], "state:",
          v11["attributes"].get("appStoreState"))
else:
    body = {"data": {"type": "appStoreVersions",
                     "attributes": {"platform": "IOS",
                                    "versionString": "1.1",
                                    "releaseType": "AFTER_APPROVAL",
                                    "copyright": copyright_},
                     "relationships": {"app": {"data": {"type": "apps",
                                                         "id": APP_ID}}}}}
    s, d = raw("POST", "/v1/appStoreVersions", body)
    print("CREATE 1.1 ->", s)
    print(json.dumps(d, indent=1)[:3000])
    if s not in (200, 201):
        print("CREATE_11_FAILED"); sys.exit(3)
    v11 = d["data"]
print("1.1 version id:", v11["id"])

# --- THE TEST: attach build 17 ----------------------------------------------
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/build")
cur = (d.get("data") or {}).get("id")
if cur == b17:
    print("build 17 ALREADY attached to 1.1")
else:
    body = {"data": {"type": "builds", "id": b17}}
    s, d = raw("PATCH", f"/v1/appStoreVersions/{v11['id']}/relationships/build",
               body)
    print("ATTACH build17 ->", s)
    if s not in (200, 204):
        print(json.dumps(d, indent=1)[:4000])
        print("ATTACH_BUILD17_FAILED")
        sys.exit(3)
    print("ATTACH_BUILD17_OK")
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/build")
rel = d.get("data") or {}
ra = rel.get("attributes", {})
print("1.1 attached build readback:", rel.get("id"), "buildNumber",
      ra.get("version"), "processingState", ra.get("processingState"))

# --- Localization carry-over --------------------------------------------------
def en_us_loc(version_id):
    s, d = raw("GET", f"/v1/appStoreVersions/{version_id}"
                      f"/appStoreVersionLocalizations?limit=50")
    for loc in d.get("data", []):
        if loc["attributes"].get("locale") == "en-US":
            return loc
    return None

loc10 = en_us_loc(v10["id"])
loc11 = en_us_loc(v11["id"])
if loc11:
    print("1.1 en-US localization exists:", loc11["id"])
else:
    print("1.1 has NO en-US localization; creating from 1.0 fields")
    a10 = loc10["attributes"] if loc10 else {}
    attrs = {"locale": "en-US"}
    for k in ("description", "keywords", "marketingUrl", "promotionalText",
              "subtitle", "supportUrl"):
        if a10.get(k):
            attrs[k] = a10[k]
    body = {"data": {"type": "appStoreVersionLocalizations",
                     "attributes": attrs,
                     "relationships": {
                         "appStoreVersion": {
                             "data": {"type": "appStoreVersions",
                                      "id": v11["id"]}}}}}
    s, d = raw("POST", "/v1/appStoreVersionLocalizations", body)
    print("CREATE 1.1 en-US loc ->", s)
    print(json.dumps(d, indent=1)[:2000])
    if s not in (200, 201):
        print("LOC_CREATE_FAILED"); sys.exit(4)
    loc11 = d["data"]

# What's New
cur_wn = loc11["attributes"].get("whatsNew")
print("1.1 whatsNew before:", repr(cur_wn))
if cur_wn != WHATS_NEW:
    body = {"data": {"type": "appStoreVersionLocalizations",
                     "id": loc11["id"],
                     "attributes": {"whatsNew": WHATS_NEW}}}
    s, d = raw("PATCH", f"/v1/appStoreVersionLocalizations/{loc11['id']}", body)
    print("PATCH whatsNew ->", s)
    if s != 200:
        print(json.dumps(d, indent=1)[:3000]); print("WHATSNEW_FAILED")
        sys.exit(4)
print("1.1 whatsNew set:", repr(WHATS_NEW))

# --- App Review detail ---------------------------------------------------------
# NOTE (probe finding, 2026-11-06): the approved 1.0 review notes state
# that build 17 contains no purchase surface and that the subscriptions
# are "switched off in this version". Those notes describe the 1.0
# submission (products deliberately unattached) and must NOT be copied
# to a version whose entire purpose is submitting the subscriptions.
# 1.1 gets fresh notes confined to verifiable facts: same binary as
# 1.0; the change is store-side (the two products, submitted with this
# version); the products' settled terms; the unchanged core flow.
NOTES_11 = (
    "Thank you for the review. Version 1.1 carries the same build as "
    "the approved 1.0. The change in this submission is on the store "
    "side: the two Make It Me subscriptions are submitted for approval "
    "together with this version.\n\n"
    "THE APP (unchanged from 1.0): RunWear tells a runner what to wear "
    "for a run from the local weather forecast - top, layers, bottoms, "
    "and extras such as gloves or a visor - adjusted to the runner's "
    "temperature sensitivity and fit preference. There is no account, "
    "sign-in, or registration anywhere in the app. Launch asks for "
    "optional location permission (coarse location); without it, the "
    "user can search for any place by name. Outfit items link out to "
    "retailer searches in Safari (Amazon Associates; purchases "
    "complete on the retailer's site).\n\n"
    "MAKE IT ME AND THE TWO SUBSCRIPTIONS IN THIS SUBMISSION: Make It "
    "Me lets a user upload photos of themselves and receive generated "
    "looks - images of themselves wearing the recommended outfits. "
    "The first 5 looks are free. The two auto-renewable subscriptions "
    "attached to this version unlock unlimited Make It Me looks:\n"
    "- Make It Me Annual (rw_makeitme_annual), $9.99 per year.\n"
    "- Make It Me Monthly (rw_makeitme_monthly), $0.99 per month.\n"
    "Subscriptions are StoreKit auto-renewable subscriptions, managed "
    "for the developer by RevenueCat. Uploaded photos are sent to the "
    "developer's backend (Supabase) and to Replicate (image model) "
    "solely to generate the user's looks. No account is created; the "
    "user is identified by an anonymous app-generated ID, and users "
    "can delete their photos and their data in Settings.\n\n"
    "EXTERNAL SERVICES: Open-Meteo (weather forecast data), "
    "OpenStreetMap Nominatim (place search), Amazon Associates "
    "(affiliate links), RevenueCat (subscription management), "
    "Supabase (developer backend), Replicate (image generation).\n\n"
    "REGIONAL CONSISTENCY: the app functions consistently across "
    "regions; temperatures display in F or C per the user's setting.\n\n"
    "REGULATED INDUSTRY / THIRD-PARTY MATERIAL: not applicable. "
    "RunWear does not operate in a regulated industry."
)
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/appStoreReviewDetail")
if s == 200 and d.get("data"):
    da = d["data"]["attributes"]
    print("1.1 review detail EXISTS: contact",
          da.get("contactFirstName"), da.get("contactLastName"),
          da.get("contactEmail"), "| demoAccountRequired:",
          da.get("demoAccountRequired"))
    print("1.1 review notes:", repr((da.get("notes") or "")[:400]))
else:
    print("1.1 review detail absent (GET ->", s,
          "); creating with fresh truthful notes")
    s, d = raw("GET", f"/v1/appStoreVersions/{v10['id']}/appStoreReviewDetail")
    d10 = (d.get("data") or {}).get("attributes", {})
    print("1.0 review notes (NOT copied - they deny subscriptions):",
          repr((d10.get("notes") or "")[:200]))
    s2, d2 = raw("GET", f"/v1/appStoreReviewDetails/{d['data']['id']}"
                        f"/appStoreReviewAttachments?limit=50")
    n_att = len(d2.get("data", [])) if s2 == 200 else -1
    print("1.0 review attachments count:", n_att,
          "(attachments are files and are NOT copied by this script)")
    attrs = {"notes": NOTES_11, "demoAccountRequired": False}
    for k in ("contactFirstName", "contactLastName", "contactPhone",
              "contactEmail"):
        if d10.get(k) is not None:
            attrs[k] = d10[k]
    body = {"data": {"type": "appStoreReviewDetails",
                     "attributes": attrs,
                     "relationships": {
                         "appStoreVersion": {
                             "data": {"type": "appStoreVersions",
                                      "id": v11["id"]}}}}}
    s, d = raw("POST", "/v1/appStoreReviewDetails", body)
    print("CREATE 1.1 review detail ->", s)
    print(json.dumps(d, indent=1)[:2000])
    if s not in (200, 201):
        print("REVIEW_DETAIL_CREATE_FAILED"); sys.exit(5)

# --- Final 1.1 readback ---------------------------------------------------------
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}")
va = d["data"]["attributes"]
print("1.1 FINAL: state:", va.get("appStoreState"), "| releaseType:",
      va.get("releaseType"), "| versionString:", va.get("versionString"))
print("STAGE1_DONE")
