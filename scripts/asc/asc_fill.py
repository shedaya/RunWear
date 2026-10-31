"""Fill RunWear 1.0's audited packet gaps (metadata only — never submits).

Reads first (privacy declarations, price schedule detail, availability),
then writes each gap with an immediate readback:
  1. en-US App Info localization: subtitle + privacy policy URL
  2. Primary category: HEALTH_AND_FITNESS
  3. Age rating declaration: truthful all-none answers for a
     weather/outfit app (healthOrWellnessTopics = true: the app's
     content serves a fitness activity throughout and it sits in
     Health & Fitness; flagged in the lane report)
  4. en-US version localization: description, keywords, support and
     marketing URLs (copy from release-prep/runwear.md, with the
     platform line corrected for iOS build (8): Apple Watch yes,
     no iOS home-screen widget yet)
  5. Version copyright
  6. Content rights declaration: USES_THIRD_PARTY_CONTENT (the app
     displays third-party weather data and retailer product content)
  7. App Review detail: contact + notes, no demo account

Usage: python3 asc_fill.py
"""
import json
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
VERSION_ID = "505f7bab-da80-49c0-9d29-a8d51987a26a"
APP_INFO_ID = "be88cd25-97e0-4a1f-917f-fde761ddf2a4"
TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)

SUBTITLE = "What to wear on your run"
PRIVACY_URL = "https://uptimeventuresllc.com/runwear/privacy"
SUPPORT_URL = "https://uptimeventuresllc.com/runwear/support"
MARKETING_URL = "https://uptimeventuresllc.com/runwear/"
COPYRIGHT = "© 2026 Uptime Ventures LLC"
KEYWORDS = "marathon,training,forecast,gear,apparel,clothes,race,layering,kit,10k"

DESCRIPTION = """RunWear tells you what to wear on your run, based on the weather where and
when you are running.

Tell it where — your current location, or a city you are travelling to — and
when. It reads the forecast for that place and hour, including the
feels-like temperature, wind, rain, and UV, and builds your outfit: top,
layers, bottoms, and the extras that matter at the edges, like gloves or a
visor.

Built for runners, it dresses you for the run rather than for standing
outside: what feels right at the start line is usually too much by mile two,
and the recommendations account for that.

PLAN AHEAD
Check tomorrow morning, or the city you land in on Friday. Pick any day and
time, or tap Now and the outfit follows the current conditions in the place
you selected — shown in that place's own time, not your phone's.

YOUR PREFERENCES
Say whether you run warm or cold and set your fit preference once. Every
outfit is adjusted to you, and you can change it any time in settings.

SHOP THE OUTFIT
Each item links to a search for that piece of gear, and Shop All gathers the
whole outfit in one place. As an Amazon Associate, RunWear earns from
qualifying purchases made through these links, at no extra cost to you.

On your phone and on Apple Watch — so the answer is there before you open
the door.

No account. No subscription. Your location is used only to get your weather."""

REVIEW_NOTES = (
    "RunWear gives running-outfit recommendations from the local weather "
    "forecast. No account or sign-in is needed; every feature is available "
    "immediately. The app asks for location permission to fetch weather for "
    "the runner's area (coarse location); the user can instead search for a "
    "place by name. Outfit items link out to retailer searches in Safari; "
    "as an Amazon Associate, RunWear earns from qualifying purchases. "
    "There are no in-app purchases, subscriptions, or accounts."
)


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def do(label, method, path, body=None):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:1500])
    return s, d


def main():
    assert len(SUBTITLE) <= 30, len(SUBTITLE)
    assert len(KEYWORDS) <= 100, len(KEYWORDS)
    assert len(DESCRIPTION) <= 4000, len(DESCRIPTION)

    # ── Read phase ────────────────────────────────────────────────────
    s, d = do("READ privacy declarations", "GET",
              f"/v1/apps/{APP_ID}/appDataUsages" + q(limit=200))
    if s == 200:
        for u in d.get("data", []):
            print("  dataUsage:", u["id"], "| published:",
                  u.get("attributes", {}).get("published"),
                  "| relationships:", list((u.get("relationships") or {}).keys()))
    else:
        print("  dataUsages: NOT READABLE via API")

    s, d = do("READ price manualPrices", "GET",
              f"/v1/appPriceSchedules/{APP_ID}/manualPrices" + q(limit=50))
    if s == 200:
        for p in d.get("data", []):
            print("  manualPrice:", p["id"], p.get("attributes", {}))
    s, d = do("READ price baseTerritory", "GET",
              f"/v1/appPriceSchedules/{APP_ID}/baseTerritory")
    if s == 200:
        print("  baseTerritory:", (d.get("data") or {}).get("id"))

    s, d = do("READ availability v2", "GET",
              "/v2/appAvailabilities"
              + q(**{"filter[app]": APP_ID}, include="territory", limit=200))
    if s == 200:
        terr = [i["id"] for i in d.get("included", []) if i["type"] == "territories"]
        for av in d.get("data", []):
            print("  availability:", av["id"], "| availableInNewTerritories:",
                  av.get("attributes", {}).get("availableInNewTerritories"),
                  "| territories included in response:", len(terr))
        eu = [t for t in terr if t in {
            "AUT", "BEL", "BGR", "HRV", "CYP", "CZE", "DNK", "EST", "FIN",
            "FRA", "DEU", "GRC", "HUN", "IRL", "ITA", "LVA", "LTU", "LUX",
            "MLT", "NLD", "POL", "PRT", "ROU", "SVK", "SVN", "ESP", "SWE"}]
        print("  EU territories in availability:", eu)

    # IDs for the writes
    s, d = do("READ appInfo localizations", "GET",
              f"/v1/appInfos/{APP_INFO_ID}/appInfoLocalizations" + q(limit=50))
    ai_loc = next((l for l in d.get("data", [])
                   if l["attributes"].get("locale") == "en-US"), None)
    s, d = do("READ version localizations", "GET",
              f"/v1/appStoreVersions/{VERSION_ID}/appStoreVersionLocalizations"
              + q(limit=50))
    v_loc = next((l for l in d.get("data", [])
                  if l["attributes"].get("locale") == "en-US"), None)
    s, d = do("READ age declaration", "GET",
              f"/v1/appInfos/{APP_INFO_ID}/ageRatingDeclaration")
    decl = d.get("data") or {}
    decl_id = decl.get("id")
    print("  ids: appInfoLoc:", ai_loc and ai_loc["id"],
          "| versionLoc:", v_loc and v_loc["id"], "| ageDecl:", decl_id)

    # ── 1. App Info localization: subtitle + privacy URL ─────────────
    if ai_loc:
        body = {"data": {"type": "appInfoLocalizations", "id": ai_loc["id"],
                         "attributes": {"subtitle": SUBTITLE,
                                        "privacyPolicyUrl": PRIVACY_URL}}}
        do("WRITE subtitle+privacyUrl", "PATCH",
           f"/v1/appInfoLocalizations/{ai_loc['id']}", body)
        s, d = do("READBACK appInfo localization", "GET",
                  f"/v1/appInfoLocalizations/{ai_loc['id']}")
        la = d.get("data", {}).get("attributes", {})
        print("  now: subtitle:", repr(la.get("subtitle")),
              "| privacyPolicyUrl:", repr(la.get("privacyPolicyUrl")),
              "| name:", repr(la.get("name")))

    # ── 2. Primary category ───────────────────────────────────────────
    body = {"data": {"type": "appInfos", "id": APP_INFO_ID,
                     "relationships": {"primaryCategory": {
                         "data": {"type": "appCategories",
                                  "id": "HEALTH_AND_FITNESS"}}}}}
    do("WRITE primaryCategory", "PATCH", f"/v1/appInfos/{APP_INFO_ID}", body)
    s, d = do("READBACK primaryCategory", "GET",
              f"/v1/appInfos/{APP_INFO_ID}/primaryCategory")
    print("  now primaryCategory:", (d.get("data") or {}).get("id"))

    # ── 3. Age rating declaration ─────────────────────────────────────
    if decl_id:
        current = decl.get("attributes", {})
        ENUM_NONE = [
            "alcoholTobaccoOrDrugUseOrReferences", "contests",
            "gamblingSimulated", "gunsOrOtherWeapons",
            "medicalOrTreatmentInformation", "profanityOrCrudeHumor",
            "sexualContentGraphicAndNudity", "sexualContentOrNudity",
            "horrorOrFearThemes", "matureOrSuggestiveThemes",
            "violenceCartoonOrFantasy",
            "violenceRealisticProlongedGraphicOrSadistic", "violenceRealistic",
        ]
        BOOL_FALSE = [
            "gambling", "unrestrictedWebAccess", "socialMediaAgeRestricted",
            "messagingAndChat", "parentalControls", "advertising",
            "socialMedia", "ageAssurance", "userGeneratedContent", "lootBox",
        ]
        attrs = {}
        for k in ENUM_NONE:
            if k in current:
                attrs[k] = "NONE"
        for k in BOOL_FALSE:
            if k in current:
                attrs[k] = False
        if "healthOrWellnessTopics" in current:
            attrs["healthOrWellnessTopics"] = True
        print("  age-rating patch attributes:", json.dumps(attrs))
        body = {"data": {"type": "ageRatingDeclarations", "id": decl_id,
                         "attributes": attrs}}
        do("WRITE ageRatingDeclaration", "PATCH",
           f"/v1/ageRatingDeclarations/{decl_id}", body)
        s, d = do("READBACK age declaration", "GET",
                  f"/v1/appInfos/{APP_INFO_ID}/ageRatingDeclaration")
        after = (d.get("data") or {}).get("attributes", {})
        nulls = [k for k, v in after.items()
                 if v is None and k not in ("kidsAgeBand",
                                            "gracRatingClassificationNumber",
                                            "developerAgeRatingInfoUrl")]
        print("  age declaration after: null fields remaining:", nulls)

    # ── 4. Version localization ───────────────────────────────────────
    if v_loc:
        body = {"data": {"type": "appStoreVersionLocalizations",
                         "id": v_loc["id"],
                         "attributes": {"description": DESCRIPTION,
                                        "keywords": KEYWORDS,
                                        "supportUrl": SUPPORT_URL,
                                        "marketingUrl": MARKETING_URL}}}
        do("WRITE version localization", "PATCH",
           f"/v1/appStoreVersionLocalizations/{v_loc['id']}", body)
        s, d = do("READBACK version localization", "GET",
                  f"/v1/appStoreVersionLocalizations/{v_loc['id']}")
        la = d.get("data", {}).get("attributes", {})
        print("  now: description chars:", len(la.get("description") or ""),
              "| keywords:", repr(la.get("keywords")),
              "| supportUrl:", repr(la.get("supportUrl")),
              "| marketingUrl:", repr(la.get("marketingUrl")))

    # ── 5. Copyright ──────────────────────────────────────────────────
    body = {"data": {"type": "appStoreVersions", "id": VERSION_ID,
                     "attributes": {"copyright": COPYRIGHT}}}
    do("WRITE copyright", "PATCH", f"/v1/appStoreVersions/{VERSION_ID}", body)
    s, d = do("READBACK version", "GET", f"/v1/appStoreVersions/{VERSION_ID}")
    va = d.get("data", {}).get("attributes", {})
    print("  now: copyright:", repr(va.get("copyright")),
          "| state:", va.get("appStoreState"))

    # ── 6. Content rights ─────────────────────────────────────────────
    body = {"data": {"type": "apps", "id": APP_ID,
                     "attributes": {
                         "contentRightsDeclaration": "USES_THIRD_PARTY_CONTENT"}}}
    do("WRITE contentRightsDeclaration", "PATCH", f"/v1/apps/{APP_ID}", body)
    s, d = do("READBACK app", "GET", f"/v1/apps/{APP_ID}")
    print("  now contentRightsDeclaration:",
          d.get("data", {}).get("attributes", {}).get("contentRightsDeclaration"))

    # ── 7. Review detail ──────────────────────────────────────────────
    body = {"data": {"type": "appStoreReviewDetails",
                     "attributes": {
                         "contactFirstName": "Sol",
                         "contactLastName": "Hedaya",
                         "contactPhone": "+1 833-878-4630",
                         "contactEmail": "support@uptimeventuresllc.com",
                         "demoAccountRequired": False,
                         "notes": REVIEW_NOTES},
                     "relationships": {"appStoreVersion": {
                         "data": {"type": "appStoreVersions",
                                  "id": VERSION_ID}}}}}
    s, d = do("WRITE review detail", "POST", "/v1/appStoreReviewDetails", body)
    if s in (200, 201):
        print("  review detail created:", d.get("data", {}).get("id"))
    s, d = do("READBACK review detail", "GET",
              f"/v1/appStoreVersions/{VERSION_ID}/appStoreReviewDetail")
    if d.get("data"):
        rd = d["data"].get("attributes", {})
        print("  now review detail: contact:", rd.get("contactFirstName"),
              rd.get("contactLastName"), "| email:", rd.get("contactEmail"),
              "| demoAccountRequired:", rd.get("demoAccountRequired"),
              "| notes chars:", len(rd.get("notes") or ""))
    else:
        print("  review detail still: NONE")
    print("FILL_DONE")


if __name__ == "__main__":
    main()
