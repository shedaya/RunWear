"""Probe: can the ASC API create or address an IPAD_13_PROFILE set?

Read-mostly investigation for the last RunWear 1.0 submit blocker.
The submit validator demands STATE_ERROR.SCREENSHOT_REQUIRED
.IPAD_13_PROFILE on the en-US localization, but the localization's
sets are APP_IPHONE_67 / APP_IPAD_PRO_129 / APP_WATCH_SERIES_7 and
the create-set enum attempt 'APP_IPAD_13' 409'd in run 41211488282
with an error body enumerating the valid screenshotDisplayType
values (no 13-inch iPad type among them).

This probe, in one run:
  1. lists the en-US localization's existing sets verbatim (id,
     screenshotDisplayType, screenshot count) - a fresh check for a
     possibly pre-existing (e.g. portal-created) 13-inch set;
  2. attempts POST /v1/appScreenshotSets for the two candidate
     display-type strings not yet ruled out by a live call:
     'APP_IPAD_13' (re-confirmation of run 41211488282) and
     'IPAD_13_PROFILE' (the validator's own profile name).
     A 409 creates nothing; a success IS the route we are looking
     for and is reported with the created set's id + display type.

Nothing else is written. Usage: python3 asc_ipad13_probe.py
"""
import json
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
VERSION_ID = "505f7bab-da80-49c0-9d29-a8d51987a26a"
TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)
CANDIDATES = ["APP_IPAD_13", "IPAD_13_PROFILE"]


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def do(label, method, path, body=None):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:2500])
    return s, d


def main():
    # 1. Resolve the en-US localization.
    s, d = do("LOCALIZATIONS", "GET",
              f"/v1/appStoreVersions/{VERSION_ID}/appStoreVersionLocalizations"
              + q(limit=50))
    loc = next((l for l in d.get("data", [])
                if l.get("attributes", {}).get("locale") == "en-US"), None)
    if loc is None:
        print("ABORT: en-US localization not found")
        return
    loc_id = loc["id"]
    print("  localization:", loc_id, "en-US")

    # 2. List existing sets verbatim.
    s, d = do("SETS", "GET",
              f"/v1/appStoreVersionLocalizations/{loc_id}/appScreenshotSets"
              + q(limit=50))
    sets = d.get("data", [])
    for x in sets:
        dt = x.get("attributes", {}).get("screenshotDisplayType")
        s2, d2 = do(f"  SHOTS {dt}", "GET",
                    f"/v1/appScreenshotSets/{x['id']}/appScreenshots"
                    + q(limit=50))
        print(f"  existing set: id={x['id']} displayType={dt} "
              f"screenshots={len(d2.get('data', []))}")
    print(f"SETS_TOTAL {len(sets)}")

    # 3. Creation attempts for the candidate display-type strings.
    created = None
    for cand in CANDIDATES:
        body = {"data": {"type": "appScreenshotSets",
                         "attributes": {"screenshotDisplayType": cand},
                         "relationships": {
                             "appStoreVersionLocalization": {
                                 "data": {
                                     "type": "appStoreVersionLocalizations",
                                     "id": loc_id}}}}}
        s, d = do(f"CREATE SET {cand}", "POST", "/v1/appScreenshotSets", body)
        data = d.get("data") or {}
        if data.get("id"):
            created = (data["id"],
                       data.get("attributes", {}).get("screenshotDisplayType"))
            print(f"  CREATED set: id={created[0]} "
                  f"displayType={created[1]} (requested {cand})")
            break
        print(f"  create {cand} refused (HTTP {s})")

    if created:
        print(f"IPAD13_ROUTE_FOUND set={created[0]} displayType={created[1]}")
    else:
        print("IPAD13_ROUTE_NOT_FOUND no candidate created a set")
    print("IPAD13_PROBE_DONE")


if __name__ == "__main__":
    main()
