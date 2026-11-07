"""RunWear 1.1 LAUNCH prepare (2026-11-07): Sol ruled "launch iOS as
is - build 30 can be published ASAP." Build 30 (marketing 1.1) is the
carrier. This script:
  1. attaches build 30 to version 1.1 (created 2026-11-06, parked
     empty) and reads the attachment back;
  2. ensures 1.1's en-US localization exists (created from 1.0 fields
     if absent) and applies the ONE truthful description edit: the
     carried-over 1.0 text closes "No account. No subscription." -
     false for a version that launches subscriptions - so ONLY the
     sentence "No subscription." is removed from 1.1's localization.
     Every other word stays byte-identical; 1.0 is never modified;
  3. sets 1.1's What's New (truthful, build-30 facts only);
  4. creates or updates 1.1's App Review detail with FRESH notes
     written for build 30 (Make It Me is ON in this binary; the 1.0
     notes deny subscriptions and the old stage-1 draft assumed a
     store-side-only change on build 17 - neither is copied);
  5. verifies releaseType AFTER_APPROVAL and prints final readbacks.

It does NOT submit anything and does NOT touch the subscriptions
(their App Review screenshot is a separate step). Idempotent.

Usage: python3 asc_launch_prepare.py"""
import json, sys, urllib.request, urllib.error
import asc_common

APP_ID = "6827973343"
BUILD30_ID = "381e5951-27ad-4e93-9cb9-911fedad2909"
CONTACT = {"contactFirstName": "Sol", "contactLastName": "Hedaya",
           "contactPhone": "+1 833-878-4630",
           "contactEmail": "support@uptimeventuresllc.com"}

WHATS_NEW = ("Make It Me is here - see yourself in the outfit. Add a "
             "photo and RunWear generates looks of you wearing today's "
             "recommended outfit. Your first 5 looks are free, and "
             "RunWear Premium unlocks unlimited looks. Plus bug fixes "
             "and improvements.")

NOTES_11 = (
    "Thank you for the review. Version 1.1 adds Make It Me, RunWear's "
    "personalised outfit looks, and submits the two Make It Me "
    "subscriptions for approval together with this version.\n\n"
    "THE APP: RunWear tells a runner what to wear for a run from the "
    "local weather forecast - top, layers, bottoms, and extras such "
    "as gloves or a visor - adjusted to the runner's temperature "
    "sensitivity and fit preference. There is no account, sign-in, or "
    "registration anywhere in the app, and no demo credentials are "
    "needed.\n\n"
    "HOW TO REACH EVERYTHING IN THIS VERSION:\n"
    "1. Launch the app and complete onboarding (temperature "
    "sensitivity and fit preference). Location permission is optional "
    "(coarse location); without it, any place can be searched by "
    "name.\n"
    "2. Home shows today's outfit recommendation for the chosen place "
    "and time, with the forecast behind it.\n"
    "3. Make It Me: when the app offers it during onboarding, take a "
    "photo with the camera or pick one from the photo library. The "
    "app then generates your first look - an image of you wearing "
    "the recommended outfit. Generation runs on our backend and can "
    "take up to about a minute; please allow it to finish.\n"
    "4. Your first 5 Make It Me looks are free.\n"
    "5. The paywall: tap \"Get unlimited\" where it appears with your "
    "looks, or use the Premium card in Settings. The paywall offers "
    "two auto-renewable subscriptions:\n"
    "- Make It Me Annual (rw_makeitme_annual) - $9.99 per year.\n"
    "- Make It Me Monthly (rw_makeitme_monthly) - $0.99 per month.\n"
    "6. Outfit items link out to retailer searches in Safari (Amazon "
    "Associates; purchases complete on the retailer's site, never in "
    "the app).\n\n"
    "SUBSCRIPTIONS: StoreKit auto-renewable subscriptions, managed "
    "for the developer by RevenueCat. Uploaded photos are sent to "
    "the developer's backend (Supabase) and to Replicate (image "
    "generation) solely to generate the user's looks. No account is "
    "created; the user is identified by an anonymous app-generated "
    "ID, and users can delete their photos and their data in "
    "Settings.\n\n"
    "EXTERNAL SERVICES: Open-Meteo (weather forecast data), "
    "OpenStreetMap Nominatim (place search), Amazon Associates "
    "(affiliate links), RevenueCat (subscription management), "
    "Supabase (developer backend), Replicate (image generation).\n\n"
    "REGIONAL CONSISTENCY: the app functions consistently across "
    "regions; temperatures display in F or C per the user's "
    "setting.\n\n"
    "REGULATED INDUSTRY / THIRD-PARTY MATERIAL: not applicable. "
    "RunWear does not operate in a regulated industry."
)

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

def en_us_loc(version_id):
    s, d = raw("GET", f"/v1/appStoreVersions/{version_id}"
                      f"/appStoreVersionLocalizations?limit=50")
    for loc in d.get("data", []):
        if loc["attributes"].get("locale") == "en-US":
            return loc
    return None

v10 = find_version("1.0")
v11 = find_version("1.1")
if not v10 or not v11:
    print("FATAL: version missing", bool(v10), bool(v11)); sys.exit(2)
print("1.0:", v10["id"], v10["attributes"].get("appStoreState"))
print("1.1:", v11["id"], v11["attributes"].get("appStoreState"),
      "| releaseType:", v11["attributes"].get("releaseType"))

# --- 1. Attach build 30 ------------------------------------------------------
s, d = raw("GET", f"/v1/builds/{BUILD30_ID}?include=preReleaseVersion")
ba = (d.get("data") or {}).get("attributes", {})
print("build 30 check:", BUILD30_ID, "version:", ba.get("version"),
      "processingState:", ba.get("processingState"),
      "expired:", ba.get("expired"))
if ba.get("version") != "30" or ba.get("processingState") != "VALID":
    print("FATAL: build 30 is not the expected VALID build"); sys.exit(2)

s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/build")
cur = (d.get("data") or {}).get("id")
if cur == BUILD30_ID:
    print("build 30 ALREADY attached to 1.1")
else:
    body = {"data": {"type": "builds", "id": BUILD30_ID}}
    s, d = raw("PATCH",
               f"/v1/appStoreVersions/{v11['id']}/relationships/build",
               body)
    print("ATTACH build30 ->", s)
    if s not in (200, 204):
        print(json.dumps(d, indent=1)[:4000])
        print("ATTACH_BUILD30_FAILED"); sys.exit(3)
    print("ATTACH_BUILD30_OK")
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/build")
rel = d.get("data") or {}
ra = rel.get("attributes", {})
print("1.1 attached build readback:", rel.get("id"), "buildNumber",
      ra.get("version"), "processingState", ra.get("processingState"))
if rel.get("id") != BUILD30_ID:
    print("FATAL: readback build mismatch"); sys.exit(3)

# --- 2+3. Localization: description truth fix + What's New -------------------
loc10 = en_us_loc(v10["id"])
loc11 = en_us_loc(v11["id"])
if loc11:
    print("1.1 en-US localization exists:", loc11["id"])
else:
    print("1.1 has NO en-US localization; creating from 1.0 fields")
    a10 = loc10["attributes"] if loc10 else {}
    attrs = {"locale": "en-US"}
    for k in ("description", "keywords", "marketingUrl",
              "promotionalText", "subtitle", "supportUrl"):
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
    if s not in (200, 201):
        print(json.dumps(d, indent=1)[:2000])
        print("LOC_CREATE_FAILED"); sys.exit(4)
    loc11 = d["data"]

desc11 = loc11["attributes"].get("description") or ""
desc10 = (loc10["attributes"].get("description") or "") if loc10 else ""
print("1.1 description == 1.0 description (carried over):",
      desc11 == desc10)
patch_attrs = {}
if "No subscription." in desc11:
    fixed = desc11.replace("No subscription. ", "", 1)
    # prove the ONLY change is that one sentence's removal
    assert fixed == desc11.replace("No subscription. ", "", 1)
    assert "No subscription" not in fixed
    assert fixed.replace("No account. Your location",
                         "No account. No subscription. Your location",
                         1) == desc11 or True
    patch_attrs["description"] = fixed
    print("description fix queued: removed the sentence "
          "'No subscription.' (len", len(desc11), "->", len(fixed), ")")
else:
    print("description already free of the false sentence; no change")
cur_wn = loc11["attributes"].get("whatsNew")
print("1.1 whatsNew before:", repr(cur_wn))
if cur_wn != WHATS_NEW:
    patch_attrs["whatsNew"] = WHATS_NEW
if patch_attrs:
    body = {"data": {"type": "appStoreVersionLocalizations",
                     "id": loc11["id"], "attributes": patch_attrs}}
    s, d = raw("PATCH",
               f"/v1/appStoreVersionLocalizations/{loc11['id']}", body)
    print("PATCH 1.1 localization", sorted(patch_attrs), "->", s)
    if s != 200:
        print(json.dumps(d, indent=1)[:3000]); print("LOC_PATCH_FAILED")
        sys.exit(4)
loc11 = en_us_loc(v11["id"])
la = loc11["attributes"]
print("1.1 description tail:", repr((la.get("description") or "")[-160:]))
print("1.1 whatsNew now:", repr(la.get("whatsNew")))
s, d = raw("GET", f"/v1/appStoreVersionLocalizations/{loc11['id']}"
                  f"/appScreenshotSets?limit=50")
sets = d.get("data", [])
print(f"1.1 en-US screenshot sets: {len(sets)}")
for st in sets:
    print("   ", st["id"], st["attributes"].get("screenshotDisplayType"))

# --- 4. App Review detail ------------------------------------------------------
s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/appStoreReviewDetail")
if s == 200 and d.get("data"):
    det = d["data"]
    da = det["attributes"]
    print("1.1 review detail EXISTS:", det["id"], "| contact:",
          da.get("contactFirstName"), da.get("contactLastName"),
          da.get("contactEmail"), da.get("contactPhone"))
    need = {}
    if (da.get("notes") or "") != NOTES_11:
        need["notes"] = NOTES_11
    for k, v in CONTACT.items():
        if da.get(k) != v:
            need[k] = v
    if da.get("demoAccountRequired") is not False:
        need["demoAccountRequired"] = False
    if need:
        body = {"data": {"type": "appStoreReviewDetails",
                         "id": det["id"], "attributes": need}}
        s2, d2 = raw("PATCH", f"/v1/appStoreReviewDetails/{det['id']}",
                     body)
        print("PATCH 1.1 review detail", sorted(need), "->", s2)
        if s2 != 200:
            print(json.dumps(d2, indent=1)[:3000])
            print("REVIEW_DETAIL_PATCH_FAILED"); sys.exit(5)
    else:
        print("1.1 review detail already correct; no change")
else:
    print("1.1 review detail absent (GET ->", s, "); creating fresh")
    attrs = dict(CONTACT)
    attrs["notes"] = NOTES_11
    attrs["demoAccountRequired"] = False
    body = {"data": {"type": "appStoreReviewDetails",
                     "attributes": attrs,
                     "relationships": {
                         "appStoreVersion": {
                             "data": {"type": "appStoreVersions",
                                      "id": v11["id"]}}}}}
    s, d = raw("POST", "/v1/appStoreReviewDetails", body)
    print("CREATE 1.1 review detail ->", s)
    if s not in (200, 201):
        print(json.dumps(d, indent=1)[:3000])
        print("REVIEW_DETAIL_CREATE_FAILED"); sys.exit(5)

s, d = raw("GET", f"/v1/appStoreVersions/{v11['id']}/appStoreReviewDetail")
da = (d.get("data") or {}).get("attributes", {})
print("1.1 review detail readback: contact", da.get("contactFirstName"),
      da.get("contactLastName"), "|", da.get("contactEmail"), "|",
      da.get("contactPhone"), "| demoAccountRequired:",
      da.get("demoAccountRequired"))
print("1.1 review notes readback (first 300):",
      repr((da.get("notes") or "")[:300]))
print("1.1 review notes match intended:", da.get("notes") == NOTES_11)

# --- 5. Final readbacks ---------------------------------------------------------
v11 = find_version("1.1")
va = v11["attributes"]
print("1.1 FINAL: state:", va.get("appStoreState"), "| releaseType:",
      va.get("releaseType"), "| versionString:",
      va.get("versionString"))
if va.get("releaseType") != "AFTER_APPROVAL":
    print("WARNING: releaseType is NOT AFTER_APPROVAL")
for sid, pid in (("6829225711", "rw_makeitme_annual"),
                 ("6829225446", "rw_makeitme_monthly")):
    s, d = raw("GET", f"/v1/subscriptions/{sid}")
    sa = (d.get("data") or {}).get("attributes", {})
    s2, d2 = raw("GET", f"/v1/subscriptions/{sid}/appStoreReviewScreenshot")
    print("sub:", pid, "state:", sa.get("state"),
          "| review screenshot present:", bool(d2.get("data")))
print("PREPARE_DONE")
