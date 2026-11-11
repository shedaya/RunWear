"""RunWear listing copy application (REVIEW-v2 English, Nov 11 2026).

Modes:
  dump  — read-only: print the full listing state (app infos + their
          en-US localizations; every iOS App Store version + its en-US
          localization) as one JSON document between LISTING_JSON markers.
  apply — write the v2 English copy of record:
          * App Info (resolved from the editable/next version, or the
            current app info when no editable version exists): name,
            subtitle.
          * Editable/next version en-US localization: description,
            keywords, promotionalText. If no editable version exists,
            version 1.3 is created to hold the copy (metadata only —
            no build attached, nothing submitted).
          * Live (READY_FOR_SALE) version en-US localization:
            promotionalText only (the one version field Apple lets you
            change on a live version without a new release).
          Every write is followed by a read-back of that resource.
          Each field's outcome is reported in an APPLY_SUMMARY JSON:
          APPLIED / REFUSED (with Apple's error body) — a refusal is
          data, not a crash; the run's exit code is 0 unless the
          initial state read itself fails.

Copy source: S5 source pack (REVIEW-v2) as amended by the Nov 10 sales
audit — numeral-less free-looks form, ASC widget bullet added, and
"Morning Poster" -> "Postcard" (Sol's Nov 10 rename). whatsNew is NOT
written anywhere (the pack's whats_new is launch copy, not 1.3 notes).

Scope: app 6827973343 (RunWear) only. Usage: asc_listing.py dump|apply
"""
import json
import sys
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
NEXT_VERSION = "1.3"
NAME = 'RunWear — Running Outfits'
SUBTITLE = 'What to wear for your run'
DESCRIPTION = "Run, weather or not.\n\nYou check the temperature, dress for the doorway, and regret it by mile 2. RunWear ends the guesswork: tell it when and where you're running, and it shows you exactly what to wear.\n\nRunWear reads the weather you'll actually feel — feels-like temperature, wind, humidity, and sun — and adjusts for the fact that your body warms up once you're moving. What comes back isn't a forecast to interpret. It's the outfit: top, bottom, and the extras that make the difference, like gloves, a hat, or a vest — shown in a photo, not just listed.\n\nSee the whole day\nA chart of the day runs right under your outfit: the cold dawn, the midday peak, the evening drop. Open it and drag to any hour — tomorrow's 6 a.m., the weekend long run, race morning — and watch where the outfit itself changes. The hour you pick dresses the whole screen.\n\nMake It Me — see yourself in the outfit\nAdd your photo, and the runner in the picture is you. Your first looks are free — no card, no account. Each look is made for your place and your hour, so the outfit you see is the outfit for the weather you'll actually run in.\n\nPremium — unlimited Make It Me\nPremium unlocks unlimited Make It Me looks, and RunWear keeps a full set of your outfits being made across every running condition, so yours is ready when you need it — no waiting at the door. The outfit recommendation itself is always free: no sign-up to start, no account to manage.\n\nWear I've Run\nEvery run leaves a card: what you wore, where you ran, the weather you ran in. They collect into a scrapbook grouped by place — your New York mornings, your Miami heat — and the Postcard and Kit Card turn any day into something worth sharing. #RunAnyWear\n\nWhy runners use it\n• See your outfit, don't just read it — a photographic hero for every recommendation\n• Dress for feels-like, not the thermometer — wind chill, humidity, and UV factored in\n• Plan by the hour with the day chart — dawn and noon are different outfits, and now you can see both\n• Works in °F and °C, for your location or a place you're traveling to\n• Check it on your wrist with the Apple Watch app\n• Put it on your home screen with the Running Outfit widget\n\nHow it works\nOpen RunWear, confirm where and when you're running, and get one clear recommendation. Change the day or the hour and the outfit changes with the conditions. Each item can link you to shop the look if you need to fill a gap in your drawer (affiliate links, disclosed in the app).\n\nRunWear doesn't track your runs — it gets you out the door dressed right for them. Works alongside the trackers you already run with.\n\nNo sign-up to start. Your location is used only to fetch your weather and to name the place you are running.\n\nMade by runners who got tired of standing in front of the closet doing weather math at 6 a.m."
KEYWORDS = 'marathon,5k,10k,half marathon,tights,gloves,weather,clothes,race day,kit,see yourself,photo'
PROMO = "New: see the whole day's weather arc and pick your hour — and with Make It Me, see yourself in the outfit before you run. Your first looks are free."

TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)
EDITABLE_STATES = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED",
                   "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def get(label, path):
    s, d = call("GET", path, expect=TOLERANT)
    print(f"--- {label}: GET -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:1500])
    return s, d


def write(label, method, path, body):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:2500])
    return s, d


def enus_loc(version_id):
    s, d = get(f"localizations of version {version_id}",
               f"/v1/appStoreVersions/{version_id}/appStoreVersionLocalizations"
               + q(limit=50))
    for loc in d.get("data", []):
        if loc.get("attributes", {}).get("locale") == "en-US":
            return loc
    return None


def dump_state():
    out = {"app_id": APP_ID, "app_infos": [], "versions": []}
    s, d = get("APP", f"/v1/apps/{APP_ID}")
    out["app"] = (d.get("data") or {}).get("attributes", {})
    s, d = get("APP INFOS", f"/v1/apps/{APP_ID}/appInfos" + q(limit=10))
    for ai in d.get("data", []):
        entry = {"id": ai["id"],
                 "state": ai.get("attributes", {}).get("appStoreState"),
                 "localizations": []}
        s2, d2 = get(f"APP INFO LOCALIZATIONS {ai['id']}",
                     f"/v1/appInfos/{ai['id']}/appInfoLocalizations" + q(limit=50))
        for loc in d2.get("data", []):
            la = loc.get("attributes", {})
            if la.get("locale") == "en-US":
                entry["localizations"].append(
                    {"id": loc["id"], "locale": la.get("locale"),
                     "name": la.get("name"), "subtitle": la.get("subtitle"),
                     "privacyPolicyUrl": la.get("privacyPolicyUrl"),
                     "privacyChoicesUrl": la.get("privacyChoicesUrl")})
        out["app_infos"].append(entry)
    s, d = get("APP STORE VERSIONS",
               f"/v1/apps/{APP_ID}/appStoreVersions"
               + q(**{"filter[platform]": "IOS"}, limit=50))
    for v in d.get("data", []):
        va = v.get("attributes", {})
        entry = {"id": v["id"], "versionString": va.get("versionString"),
                 "state": va.get("appStoreState"),
                 "releaseType": va.get("releaseType"),
                 "copyright": va.get("copyright"), "localization": None}
        loc = enus_loc(v["id"])
        if loc:
            la = loc.get("attributes", {})
            entry["localization"] = {
                "id": loc["id"], "description": la.get("description"),
                "keywords": la.get("keywords"),
                "promotionalText": la.get("promotionalText"),
                "whatsNew": la.get("whatsNew"),
                "supportUrl": la.get("supportUrl"),
                "marketingUrl": la.get("marketingUrl")}
        out["versions"].append(entry)
    print("LISTING_JSON_BEGIN")
    print(json.dumps(out, indent=1, ensure_ascii=False))
    print("LISTING_JSON_END")
    return out


def find_version(state, version_string):
    for v in state["versions"]:
        if v["versionString"] == version_string:
            return v
    return None


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "dump"
    if mode == "fix-appinfo":
        fix_appinfo()
        return
    state = dump_state()
    if mode == "dump":
        return
    if mode != "apply":
        print("unknown mode:", mode)
        sys.exit(2)

    summary = {}
    live = next((v for v in state["versions"]
                 if v["state"] == "READY_FOR_SALE"), None)
    editable = next((v for v in state["versions"]
                     if v["state"] in EDITABLE_STATES), None)
    print("live version:", live and (live["versionString"], live["state"]))
    print("editable version:", editable and (editable["versionString"], editable["state"]))

    # --- Target version for description/keywords: editable, else create 1.3.
    target = editable
    if target is None:
        existing = find_version(state, NEXT_VERSION)
        if existing is not None:
            print(f"version {NEXT_VERSION} exists in non-editable state",
                  existing["state"], "— cannot hold copy; skipping version writes")
            summary["version_copy"] = f"SKIPPED: {NEXT_VERSION} in state {existing['state']}"
            target = None
        else:
            body = {"data": {"type": "appStoreVersions",
                             "attributes": {"platform": "IOS",
                                            "versionString": NEXT_VERSION},
                             "relationships": {"app": {"data": {"type": "apps",
                                                                "id": APP_ID}}}}}
            s, d = write(f"CREATE version {NEXT_VERSION}", "POST",
                         "/v1/appStoreVersions", body)
            if s in (200, 201) and d.get("data"):
                nv = d["data"]
                target = {"id": nv["id"], "versionString": NEXT_VERSION,
                          "state": nv.get("attributes", {}).get("appStoreState"),
                          "localization": None}
                summary["version_created"] = NEXT_VERSION
            else:
                summary["version_copy"] = "SKIPPED: create version failed"

    # --- App Info: resolve from the target version when there is one,
    # else fall back to the app's (single) current app info.
    appinfo_id = None
    if target is not None:
        s, d = get(f"appInfo of version {target['versionString']}",
                   f"/v1/appStoreVersions/{target['id']}/appInfo")
        if d.get("data"):
            appinfo_id = d["data"]["id"]
    if appinfo_id is None and state["app_infos"]:
        appinfo_id = state["app_infos"][0]["id"]
    print("target appInfo:", appinfo_id)
    if appinfo_id:
        ai_entry = next((a for a in state["app_infos"] if a["id"] == appinfo_id), None)
        locs = (ai_entry or {}).get("localizations", [])
        ai_loc = locs[0] if locs else None
        if ai_loc:
            body = {"data": {"type": "appInfoLocalizations", "id": ai_loc["id"],
                             "attributes": {"name": NAME, "subtitle": SUBTITLE}}}
            s, d = write("PATCH appInfo en-US (name/subtitle)", "PATCH",
                         f"/v1/appInfoLocalizations/{ai_loc['id']}", body)
            summary["appinfo_name_subtitle"] = (
                "APPLIED" if s in (200, 201) else f"REFUSED HTTP {s}")
            s, d = get("READBACK appInfo localization",
                       f"/v1/appInfoLocalizations/{ai_loc['id']}")
            la = (d.get("data") or {}).get("attributes", {})
            print("  now name:", repr(la.get("name")), "| subtitle:", repr(la.get("subtitle")))
            summary["appinfo_readback"] = {"name": la.get("name"),
                                           "subtitle": la.get("subtitle")}
        else:
            summary["appinfo_name_subtitle"] = "SKIPPED: no en-US appInfo localization in dump"
    else:
        summary["appinfo_name_subtitle"] = "SKIPPED: no appInfo resolved"

    # --- Version localization writes.
    if target is not None:
        loc = enus_loc(target["id"])
        attrs = {"description": DESCRIPTION, "keywords": KEYWORDS,
                 "promotionalText": PROMO}
        if loc is None:
            carry = {}
            if live and live.get("localization"):
                for f in ("supportUrl", "marketingUrl"):
                    if live["localization"].get(f):
                        carry[f] = live["localization"][f]
            body = {"data": {"type": "appStoreVersionLocalizations",
                             "attributes": dict(attrs, locale="en-US", **carry),
                             "relationships": {"appStoreVersion": {
                                 "data": {"type": "appStoreVersions",
                                          "id": target["id"]}}}}}
            s, d = write("CREATE target en-US localization", "POST",
                         "/v1/appStoreVersionLocalizations", body)
            loc = d.get("data") or None
            summary["version_copy"] = ("APPLIED (created localization)"
                                       if s in (200, 201) else f"REFUSED HTTP {s}")
        else:
            body = {"data": {"type": "appStoreVersionLocalizations",
                             "id": loc["id"], "attributes": attrs}}
            s, d = write("PATCH target en-US localization", "PATCH",
                         f"/v1/appStoreVersionLocalizations/{loc['id']}", body)
            summary["version_copy"] = ("APPLIED" if s in (200, 201)
                                       else f"REFUSED HTTP {s}")
        if loc:
            s, d = get("READBACK target localization",
                       f"/v1/appStoreVersionLocalizations/{loc['id']}")
            la = (d.get("data") or {}).get("attributes", {})
            rb = {"description": la.get("description"),
                  "keywords": la.get("keywords"),
                  "promotionalText": la.get("promotionalText")}
            summary["target_readback"] = rb
            summary["target_match"] = {
                "description": rb["description"] == DESCRIPTION,
                "keywords": rb["keywords"] == KEYWORDS,
                "promotionalText": rb["promotionalText"] == PROMO}

    # --- Live version: promotional text only.
    if live is not None:
        loc = (live.get("localization") or {})
        if loc.get("id"):
            if loc.get("promotionalText") == PROMO:
                summary["live_promo"] = "ALREADY SET"
            else:
                body = {"data": {"type": "appStoreVersionLocalizations",
                                 "id": loc["id"],
                                 "attributes": {"promotionalText": PROMO}}}
                s, d = write("PATCH live promotionalText", "PATCH",
                             f"/v1/appStoreVersionLocalizations/{loc['id']}", body)
                summary["live_promo"] = ("APPLIED" if s in (200, 201)
                                         else f"REFUSED HTTP {s}")
                s, d = get("READBACK live localization",
                           f"/v1/appStoreVersionLocalizations/{loc['id']}")
                la = (d.get("data") or {}).get("attributes", {})
                print("  live promotionalText now:", repr(la.get("promotionalText")))
                summary["live_promo_readback_match"] = la.get("promotionalText") == PROMO
        else:
            summary["live_promo"] = "SKIPPED: live localization not in dump"
    print("APPLY_SUMMARY_BEGIN")
    print(json.dumps(summary, indent=1, ensure_ascii=False))
    print("APPLY_SUMMARY_END")


if __name__ == "__main__":
    main()


def fix_appinfo():
    """PATCH name/subtitle onto the EDITABLE (non-READY_FOR_SALE) app
    info's en-US localization — the record Apple created alongside the
    new version. The live app info is never touched."""
    s, d = get("APP INFOS", f"/v1/apps/{APP_ID}/appInfos" + q(limit=10))
    targets = []
    for ai in d.get("data", []):
        st = ai.get("attributes", {}).get("appStoreState")
        print("  appInfo:", ai["id"], "| state:", st)
        if st != "READY_FOR_SALE":
            targets.append(ai["id"])
    if len(targets) != 1:
        print("ABORT: expected exactly 1 editable appInfo, found", len(targets))
        sys.exit(1)
    ai_id = targets[0]
    s, d = get("editable appInfo localizations",
               f"/v1/appInfos/{ai_id}/appInfoLocalizations" + q(limit=50))
    loc = next((l for l in d.get("data", [])
                if l.get("attributes", {}).get("locale") == "en-US"), None)
    if loc is None:
        print("ABORT: no en-US localization on the editable appInfo")
        sys.exit(1)
    body = {"data": {"type": "appInfoLocalizations", "id": loc["id"],
                     "attributes": {"name": NAME, "subtitle": SUBTITLE}}}
    s, d = write("PATCH editable appInfo en-US (name/subtitle)", "PATCH",
                 f"/v1/appInfoLocalizations/{loc['id']}", body)
    ok = s in (200, 201)
    s, d = get("READBACK editable appInfo localization",
               f"/v1/appInfoLocalizations/{loc['id']}")
    la = (d.get("data") or {}).get("attributes", {})
    print("  now name:", repr(la.get("name")), "| subtitle:", repr(la.get("subtitle")))
    print("APPINFO_FIX_SUMMARY_BEGIN")
    print(json.dumps({"appinfo_id": ai_id, "applied": ok,
                      "name": la.get("name"), "subtitle": la.get("subtitle"),
                      "name_match": la.get("name") == NAME,
                      "subtitle_match": la.get("subtitle") == SUBTITLE},
                     indent=1, ensure_ascii=False))
    print("APPINFO_FIX_SUMMARY_END")
