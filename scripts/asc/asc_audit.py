"""Read-only full-packet audit of RunWear's App Store version 1.0.

GET requests only (plus tolerant probes that expect 4xx). Prints one
section per submission requirement so the log is the audit report:
app record, App Info (name/subtitle/privacy URL/categories/age rating),
version state + attached build, version localizations, screenshot sets,
review detail, price schedule, availability, existing review
submissions, and the target TestFlight build.

Usage: python3 asc_audit.py
"""
import json
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
VERSION_STRING = "1.0"
TARGET_BUILD_ID = "fa972eeb-fa7f-4544-939f-adbabccbff50"
TOLERANT = (200, 201, 204, 400, 403, 404)


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def probe(label, path, expect=TOLERANT):
    s, d = call("GET", path, expect=expect)
    print(f"--- {label}: HTTP {s}")
    return s, d


def main():
    # --- App record ------------------------------------------------------
    s, d = probe("APP", f"/v1/apps/{APP_ID}")
    a = d.get("data", {}).get("attributes", {})
    print("  name:", a.get("name"), "| bundleId:", a.get("bundleId"),
          "| sku:", a.get("sku"), "| primaryLocale:", a.get("primaryLocale"),
          "| contentRightsDeclaration:", a.get("contentRightsDeclaration"))

    # --- Target build ----------------------------------------------------
    s, d = probe("TARGET BUILD", f"/v1/builds/{TARGET_BUILD_ID}")
    ba = d.get("data", {}).get("attributes", {})
    print("  buildNumber:", ba.get("version"), "| processingState:",
          ba.get("processingState"), "| expired:", ba.get("expired"),
          "| uploaded:", ba.get("uploadedDate"),
          "| usesNonExemptEncryption:", ba.get("usesNonExemptEncryption"))

    # --- App Infos (name/subtitle/privacy URL/categories/age rating) -----
    s, d = probe("APP INFOS", f"/v1/apps/{APP_ID}/appInfos" + q(limit=10))
    app_info_id = None
    for ai in d.get("data", []):
        print("  appInfo:", ai["id"], "| state:", ai.get("attributes", {}).get("appStoreState"))
        if app_info_id is None:
            app_info_id = ai["id"]
    if app_info_id:
        s, d = probe("APP INFO DETAIL",
                     f"/v1/appInfos/{app_info_id}"
                     + q(include="primaryCategory,secondaryCategory,ageRatingDeclaration"))
        inc = {i["id"]: i for i in d.get("included", [])}
        rels = d.get("data", {}).get("relationships", {})
        for rel_name in ("primaryCategory", "secondaryCategory"):
            rel = (rels.get(rel_name) or {}).get("data") or {}
            cat = inc.get(rel.get("id"), {})
            print(f"  {rel_name}:", rel.get("id"),
                  (cat.get("attributes") or {}).get("platforms"))
        ard = (rels.get("ageRatingDeclaration") or {}).get("data") or {}
        if ard.get("id") and ard["id"] in inc:
            print("  ageRatingDeclaration:",
                  json.dumps(inc[ard["id"]].get("attributes", {}))[:1200])
        else:
            print("  ageRatingDeclaration: NOT LINKED / not returned")
        s, d = probe("APP INFO LOCALIZATIONS",
                     f"/v1/appInfos/{app_info_id}/appInfoLocalizations" + q(limit=50))
        for loc in d.get("data", []):
            la = loc.get("attributes", {})
            print("  loc:", la.get("locale"), "| name:", repr(la.get("name")),
                  "| subtitle:", repr(la.get("subtitle")),
                  "| privacyPolicyUrl:", repr(la.get("privacyPolicyUrl")),
                  "| privacyChoicesUrl:", repr(la.get("privacyChoicesUrl")))

    # --- App Store version 1.0 ------------------------------------------
    s, d = probe("APP STORE VERSIONS",
                 f"/v1/apps/{APP_ID}/appStoreVersions"
                 + q(**{"filter[platform]": "IOS"}, limit=50))
    version = None
    for v in d.get("data", []):
        va = v.get("attributes", {})
        print("  version:", va.get("versionString"), "| id:", v["id"],
              "| state:", va.get("appStoreState"), "| releaseType:",
              va.get("releaseType"), "| copyright:", repr(va.get("copyright")),
              "| created:", va.get("createdDate"))
        if va.get("versionString") == VERSION_STRING:
            version = v
    if not version:
        print("VERSION_1_0_NOT_FOUND")
        return
    vid = version["id"]

    s, d = probe("VERSION BUILD", f"/v1/appStoreVersions/{vid}/build",
                 expect=TOLERANT)
    rel = d.get("data") or {}
    if rel:
        ra = rel.get("attributes", {})
        print("  attached build:", rel["id"], "| buildNumber:", ra.get("version"),
              "| processingState:", ra.get("processingState"))
    else:
        print("  attached build: NONE")

    s, d = probe("REVIEW DETAIL",
                 f"/v1/appStoreVersions/{vid}/appStoreReviewDetail",
                 expect=TOLERANT)
    if s == 200 and d.get("data"):
        rd = d["data"].get("attributes", {})
        print("  review detail:", json.dumps(rd)[:1200])
    else:
        print("  review detail: NONE")

    # --- Version localizations + screenshots -----------------------------
    s, d = probe("VERSION LOCALIZATIONS",
                 f"/v1/appStoreVersions/{vid}/appStoreVersionLocalizations"
                 + q(limit=50))
    for loc in d.get("data", []):
        la = loc.get("attributes", {})
        print("  loc:", la.get("locale"),
              "| description chars:", len(la.get("description") or ""),
              "| keywords:", repr(la.get("keywords")),
              "| supportUrl:", repr(la.get("supportUrl")),
              "| marketingUrl:", repr(la.get("marketingUrl")),
              "| promotionalText chars:", len(la.get("promotionalText") or ""),
              "| whatsNew:", repr(la.get("whatsNew")))
        s2, d2 = probe("  SCREENSHOT SETS",
                       f"/v1/appStoreVersionLocalizations/{loc['id']}/appScreenshotSets"
                       + q(limit=50), expect=TOLERANT)
        for sset in d2.get("data", []):
            sa = sset.get("attributes", {})
            s3, d3 = probe("    SCREENSHOTS",
                           f"/v1/appScreenshotSets/{sset['id']}/appScreenshots"
                           + q(limit=50), expect=TOLERANT)
            shots = d3.get("data", [])
            dims = []
            for shot in shots:
                sh = shot.get("attributes", {})
                asset = sh.get("imageAsset") or {}
                dims.append(f"{sh.get('fileName')}:{asset.get('width')}x{asset.get('height')}"
                            f":{(sh.get('assetDeliveryState') or {}).get('state')}")
            print("    set:", sa.get("screenshotDisplayType"),
                  "| count:", len(shots), "|", dims)

    # --- Pricing + availability ------------------------------------------
    s, d = probe("PRICE SCHEDULE", f"/v1/apps/{APP_ID}/appPriceSchedule",
                 expect=TOLERANT)
    if s == 200:
        print("  price schedule:", json.dumps(d.get("data", {}))[:600])
    else:
        print("  price schedule: NONE / not readable")
    s, d = probe("AVAILABILITY v1", f"/v1/apps/{APP_ID}/appAvailabilities",
                 expect=TOLERANT)
    if s == 200:
        for av in d.get("data", []):
            print("  availability:", av["id"],
                  av.get("attributes", {}).get("availableInNewTerritories"))
    s, d = probe("AVAILABILITY v2",
                 "/v2/appAvailabilities" + q(**{"filter[app]": APP_ID}, limit=5),
                 expect=TOLERANT)
    if s == 200:
        for av in d.get("data", []):
            print("  availability v2:", av["id"],
                  av.get("attributes", {}).get("availableInNewTerritories"))

    # --- Existing review submissions --------------------------------------
    s, d = probe("REVIEW SUBMISSIONS",
                 "/v1/reviewSubmissions" + q(**{"filter[app]": APP_ID}, limit=20),
                 expect=TOLERANT)
    if s == 200:
        for rs in d.get("data", []):
            ra = rs.get("attributes", {})
            print("  reviewSubmission:", rs["id"], "| platform:", ra.get("platform"),
                  "| submitted:", ra.get("submitted"), "| state:", ra.get("state"),
                  "| submittedDate:", ra.get("submittedDate"))
    print("AUDIT_DONE")


if __name__ == "__main__":
    main()
