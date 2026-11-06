"""Option 3 (2026-11-06) READ-ONLY probe for the monetization-only iOS
update: current App Store version/build state, the 1.0 localization +
review detail that a new version would carry, and the full metadata
state of the two Make It Me subscriptions (plus a read-only state line
for the two experiment arms, to prove later that they were untouched).
GET requests only; this script never writes to App Store Connect.

Usage: python3 asc_opt3_probe.py"""
import json, sys, urllib.request, urllib.error
import asc_common

APP_ID = "6827973343"
GROUP_ID = "22531902"
TARGETS = {"rw_makeitme_annual", "rw_makeitme_monthly"}

def get(path):
    url = path if path.startswith("http") else asc_common.BASE + path
    req = urllib.request.Request(url, method="GET")
    req.add_header("Authorization", "Bearer " + asc_common.token())
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:400].decode("utf-8", "replace")}
        return e.code, parsed

def show(label, path):
    s, d = get(path)
    print(f"--- {label}: GET {path} -> {s}")
    print(json.dumps(d, indent=1)[:6000])
    return s, d

# --- App + versions ------------------------------------------------------
show("app", f"/v1/apps/{APP_ID}")
s, d = get(f"/v1/apps/{APP_ID}/appStoreVersions?limit=50")
versions = d.get("data", [])
print(f"VERSIONS ({len(versions)}):")
v10 = None
for v in versions:
    va = v["attributes"]
    print("  ", v["id"], "version", va.get("versionString"),
          "state", va.get("appStoreState"), "releaseType",
          va.get("releaseType"), "created", va.get("createdDate"))
    if va.get("versionString") == "1.0":
        v10 = v["id"]
    s2, d2 = get(f"/v1/appStoreVersions/{v['id']}/build")
    rel = d2.get("data") or {}
    if rel:
        ra = rel.get("attributes", {})
        print("     attached build id:", rel["id"], "buildNumber",
              ra.get("version"), "processingState",
              ra.get("processingState"), "uploaded", ra.get("uploadedDate"))
    else:
        print("     attached build: NONE")

# --- Builds of interest ----------------------------------------------------
s, d = get(f"/v1/builds?filter[app]={APP_ID}&sort=-uploadedDate&limit=30"
           f"&include=preReleaseVersion")
prv = {x["id"]: x["attributes"].get("version")
       for x in d.get("included", []) if x["type"] == "preReleaseVersions"}
print("BUILDS:")
for b in d.get("data", []):
    ba = b["attributes"]
    ver = prv.get(b["relationships"]["preReleaseVersion"]["data"]["id"])
    print("  ", b["id"], "marketing", ver, "build", ba.get("version"),
          "state", ba.get("processingState"), "expired", ba.get("expired"),
          "uploaded", ba.get("uploadedDate"))

# --- 1.0 localization + review detail (what 1.1 would carry) ---------------
if v10:
    s, d = get(f"/v1/appStoreVersions/{v10}/appStoreVersionLocalizations"
               f"?limit=50")
    for loc in d.get("data", []):
        la = loc["attributes"]
        if la.get("locale") == "en-US":
            print("1.0 en-US localization id:", loc["id"])
            for k in ("name", "subtitle", "promotionalText", "keywords",
                      "supportUrl", "marketingUrl", "whatsNew"):
                print(f"   {k}: {la.get(k)!r}")
            print("   description:")
            print("   ", (la.get("description") or "").replace("\n", "\n    "))
            s2, d2 = get(f"/v1/appStoreVersionLocalizations/{loc['id']}"
                         f"/appScreenshotSets?limit=50")
            sets = d2.get("data", [])
            print(f"   screenshot sets on 1.0 en-US: {len(sets)}")
            for st in sets:
                print("     ", st["id"], st["attributes"].get("screenshotDisplayType"))
    show("1.0 review detail", f"/v1/appStoreVersions/{v10}/appStoreReviewDetail")

# --- Subscription group + subscriptions ------------------------------------
show("group localizations",
     f"/v1/subscriptionGroups/{GROUP_ID}/subscriptionGroupLocalizations?limit=50")
s, d = get(f"/v1/subscriptionGroups/{GROUP_ID}/subscriptions?limit=50")
subs = d.get("data", [])
print(f"SUBSCRIPTIONS IN GROUP ({len(subs)}):")
for sub in subs:
    sa = sub["attributes"]
    pid = sa.get("productId")
    tag = "TARGET" if pid in TARGETS else "ARM-DO-NOT-TOUCH"
    print("  ", sub["id"], pid, "| name:", sa.get("name"), "| state:",
          sa.get("state"), "| period:", sa.get("subscriptionPeriod"),
          "| groupLevel:", sa.get("groupLevel"), f"[{tag}]")
    if pid not in TARGETS:
        continue
    s2, d2 = get(f"/v1/subscriptions/{sub['id']}/subscriptionLocalizations"
                 f"?limit=50")
    for loc in d2.get("data", []):
        la = loc["attributes"]
        print("     loc:", la.get("locale"), "name:", repr(la.get("name")),
              "description:", repr(la.get("description")),
              "state:", la.get("state"))
    s2, d2 = get(f"/v1/subscriptions/{sub['id']}/prices?limit=50"
                 f"&include=subscriptionPricePoint,territory")
    inc = {x["id"]: x for x in d2.get("included", [])}
    prices = d2.get("data", [])
    print(f"     prices: {len(prices)} records")
    for p in prices[:8]:
        rel = p.get("relationships", {})
        pp = inc.get((rel.get("subscriptionPricePoint", {}).get("data")
                      or {}).get("id"), {})
        te = inc.get((rel.get("territory", {}).get("data") or {}).get("id"), {})
        ppa = pp.get("attributes", {})
        print("       territory:", te.get("attributes", {}).get("currency"),
              "| customerPrice:", ppa.get("customerPrice"),
              "| proceeds:", ppa.get("proceeds"),
              "| startDate:", p["attributes"].get("startDate"),
              "| preserved:", p["attributes"].get("preserved"))
    s2, d2 = get(f"/v1/subscriptions/{sub['id']}/subscriptionAvailability")
    av = (d2.get("data") or {}).get("attributes", {})
    print("     availability: availableInNewTerritories:",
          av.get("availableInNewTerritories"))
    s2, d2 = get(f"/v1/subscriptions/{sub['id']}/appStoreReviewScreenshot")
    shot = d2.get("data")
    if shot:
        sha = shot.get("attributes", {})
        ads = sha.get("assetDeliveryState", {}) or {}
        print("     review screenshot: PRESENT id:", shot["id"],
              "fileName:", sha.get("fileName"), "fileSize:",
              sha.get("fileSize"), "state:", ads.get("state"))
    else:
        print(f"     review screenshot: ABSENT (GET -> {s2})")
print("PROBE_DONE")
