"""Prepare RunWear's 1.2 App Store version (build 34) and submit it.

Creates the 1.2 version if it does not exist yet, carries the 1.1
record's content across programmatically (en-US localization fields,
copyright, App Review detail), attaches build 34, sets the release
type to AFTER_APPROVAL (automatic release on approval — the pattern
1.1 used), sets the supplied What's New text, then submits via a
reviewSubmission and reads everything back.

Strict-2xx discipline, same as asc_submit.py: any non-2xx on the
submit path prints the error body and exits non-zero, so a failed
submit can never masquerade as a green run. (Read probes soft-fail:
ASC legitimately 404s an uncreated review detail / empty lists.)

Scope: app 6827973343 (RunWear) only. The live 1.1 version is read,
never written.

Usage: python3 asc_prepare12.py
"""
import json
import sys
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
BUILD_ID = "aa552745-3724-4e32-9b91-fc1921aa1f7f"  # 1.2 (34)
BUILD_VERSION = "34"
SOURCE_VERSION = "1.1"

WHATS_NEW = (
    "New first screen: your running photo now fills the screen, with "
    "your conditions over it. Fixes: the date and time controls can no "
    "longer overlap your feels-like temperature; the Wear I've Run bar "
    "no longer covers your outfit; the location sheet, your place and "
    "your conditions always agree; one free-looks counter everywhere; "
    "and the Wear I've Run line now names the place, date and time you "
    "picked."
)

TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def do(label, method, path, body=None, fatal=True):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:3000])
        if fatal:
            print(f"ABORT: {label} failed with HTTP {s} — submit path is "
                  "strict-2xx; exiting non-zero so this cannot read as success")
            sys.exit(1)
    return s, d


def find_version(version_string):
    s, d = do(f"FIND version {version_string}", "GET",
              f"/v1/apps/{APP_ID}/appStoreVersions"
              + q(**{"filter[versionString]": version_string, "limit": 10}))
    for v in d.get("data", []):
        if v.get("attributes", {}).get("versionString") == version_string:
            return v
    return None


def main():
    # 0. Preconditions: target build VALID.
    s, d = do("CHECK build 34", "GET", f"/v1/builds/{BUILD_ID}")
    ba = d.get("data", {}).get("attributes", {})
    print("  build:", ba.get("version"), ba.get("processingState"),
          "expired:", ba.get("expired"))
    if ba.get("version") != BUILD_VERSION or ba.get("processingState") != "VALID":
        print("ABORT: target build is not build 34 / VALID")
        sys.exit(1)

    # 1. Create the 1.2 version if absent.
    ver = find_version("1.2")
    if ver is None:
        body = {"data": {"type": "appStoreVersions",
                         "attributes": {"platform": "IOS",
                                        "versionString": "1.2"},
                         "relationships": {"app": {
                             "data": {"type": "apps", "id": APP_ID}}}}}
        s, d = do("CREATE version 1.2", "POST", "/v1/appStoreVersions", body)
        ver = d.get("data") or {}
        print("  created version:", ver.get("id"))
    version_id = ver.get("id")
    va = ver.get("attributes", {})
    print("  version 1.2:", version_id, "| state:", va.get("appStoreState"),
          "| releaseType:", va.get("releaseType"))
    if not version_id:
        print("ABORT: no 1.2 version id")
        sys.exit(1)
    if va.get("appStoreState") in ("WAITING_FOR_REVIEW", "IN_REVIEW",
                                   "READY_FOR_SALE"):
        print("ABORT: 1.2 is already", va.get("appStoreState"),
              "— nothing to prepare")
        sys.exit(1)

    # 2. Source record: 1.1 (read-only reference for the carry-over).
    src = find_version(SOURCE_VERSION)
    if src is None:
        print("ABORT: source version", SOURCE_VERSION, "not found")
        sys.exit(1)
    src_id = src["id"]
    src_va = src.get("attributes", {})
    print("  source 1.1:", src_id, "| state:", src_va.get("appStoreState"),
          "| releaseType:", src_va.get("releaseType"),
          "| copyright:", repr(src_va.get("copyright")))

    # 3. Carry the en-US localization across; set What's New on 1.2.
    s, d = do("READ 1.1 localizations", "GET",
              f"/v1/appStoreVersions/{src_id}/appStoreVersionLocalizations"
              + q(limit=50))
    src_loc = next((l for l in d.get("data", [])
                    if l["attributes"].get("locale") == "en-US"), None)
    s, d = do("READ 1.2 localizations", "GET",
              f"/v1/appStoreVersions/{version_id}/appStoreVersionLocalizations"
              + q(limit=50))
    dst_loc = next((l for l in d.get("data", [])
                    if l["attributes"].get("locale") == "en-US"), None)
    sl = (src_loc or {}).get("attributes", {})
    attrs = {"whatsNew": WHATS_NEW}
    for f in ("description", "keywords", "supportUrl", "marketingUrl",
              "promotionalText"):
        if sl.get(f):
            attrs[f] = sl[f]
    print("  carrying fields:", sorted(attrs.keys()))
    if dst_loc is None:
        body = {"data": {"type": "appStoreVersionLocalizations",
                         "attributes": dict(attrs, locale="en-US"),
                         "relationships": {"appStoreVersion": {
                             "data": {"type": "appStoreVersions",
                                      "id": version_id}}}}}
        s, d = do("CREATE 1.2 en-US localization", "POST",
                  "/v1/appStoreVersionLocalizations", body)
        dst_loc = d.get("data") or {}
    else:
        body = {"data": {"type": "appStoreVersionLocalizations",
                         "id": dst_loc["id"], "attributes": attrs}}
        do("WRITE 1.2 en-US localization", "PATCH",
           f"/v1/appStoreVersionLocalizations/{dst_loc['id']}", body)
    s, d = do("READBACK 1.2 localization", "GET",
              f"/v1/appStoreVersionLocalizations/{dst_loc.get('id')}")
    la = (d.get("data") or {}).get("attributes", {})
    print("  now: whatsNew chars:", len(la.get("whatsNew") or ""),
          "| description chars:", len(la.get("description") or ""),
          "| keywords:", repr(la.get("keywords")))
    if la.get("whatsNew") != WHATS_NEW:
        print("ABORT: What's New readback does not match the supplied text")
        sys.exit(1)

    # 4. Copyright (carried from 1.1 when present).
    if src_va.get("copyright"):
        body = {"data": {"type": "appStoreVersions", "id": version_id,
                         "attributes": {"copyright": src_va["copyright"]}}}
        do("WRITE copyright", "PATCH",
           f"/v1/appStoreVersions/{version_id}", body)

    # 5. App Review detail: copy 1.1's to 1.2 (contact changes with Sol).
    s, d = do("READ 1.1 review detail", "GET",
              f"/v1/appStoreVersions/{src_id}/appStoreReviewDetail",
              fatal=False)
    src_rd = (d.get("data") or {}).get("attributes", {})
    s, d = do("READ 1.2 review detail", "GET",
              f"/v1/appStoreVersions/{version_id}/appStoreReviewDetail",
              fatal=False)
    if d.get("data"):
        print("  1.2 review detail already present:",
              (d["data"].get("attributes") or {}).get("contactEmail"))
    elif src_rd:
        keep = {k: src_rd[k] for k in (
            "contactFirstName", "contactLastName", "contactPhone",
            "contactEmail", "demoAccountRequired", "demoAccountName",
            "demoAccountPassword", "notes") if src_rd.get(k) is not None}
        body = {"data": {"type": "appStoreReviewDetails",
                         "attributes": keep,
                         "relationships": {"appStoreVersion": {
                             "data": {"type": "appStoreVersions",
                                      "id": version_id}}}}}
        do("CREATE 1.2 review detail", "POST", "/v1/appStoreReviewDetails",
           body)
    else:
        print("ABORT: 1.1 has no review detail to carry and 1.2 has none")
        sys.exit(1)

    # 6. Attach build 34 (before releaseType: a version whose build
    #    selection is still open can read MANUAL).
    body = {"data": {"type": "appStoreVersions", "id": version_id,
                     "relationships": {"build": {
                         "data": {"type": "builds", "id": BUILD_ID}}}}}
    do("ATTACH build 34", "PATCH", f"/v1/appStoreVersions/{version_id}", body)
    s, d = do("READBACK version build", "GET",
              f"/v1/appStoreVersions/{version_id}/build")
    rel = d.get("data") or {}
    print("  attached now:", rel.get("id"))
    if rel.get("id") != BUILD_ID:
        print("ABORT: readback does not show build 34 attached")
        sys.exit(1)

    # 7. Automatic release on approval.
    body = {"data": {"type": "appStoreVersions", "id": version_id,
                     "attributes": {"releaseType": "AFTER_APPROVAL"}}}
    do("SET releaseType AFTER_APPROVAL", "PATCH",
       f"/v1/appStoreVersions/{version_id}", body)
    s, d = do("READBACK version", "GET", f"/v1/appStoreVersions/{version_id}")
    va = (d.get("data") or {}).get("attributes", {})
    print("  now: state:", va.get("appStoreState"),
          "| releaseType:", va.get("releaseType"))
    if va.get("releaseType") != "AFTER_APPROVAL":
        print("ABORT: releaseType readback is not AFTER_APPROVAL")
        sys.exit(1)

    # 8. Screenshots precondition (>= 1 across the 1.2 version's sets).
    s, d = do("READ 1.2 localizations (sets)", "GET",
              f"/v1/appStoreVersions/{version_id}/appStoreVersionLocalizations"
              + q(limit=50))
    total_shots = 0
    for loc in d.get("data", []):
        s2, d2 = do("  sets", "GET",
                    f"/v1/appStoreVersionLocalizations/{loc['id']}/appScreenshotSets"
                    + q(limit=50), fatal=False)
        for sset in (d2 or {}).get("data", []):
            s3, d3 = do("  shots", "GET",
                        f"/v1/appScreenshotSets/{sset['id']}/appScreenshots"
                        + q(limit=50), fatal=False)
            total_shots += len((d3 or {}).get("data", []))
    print("  total screenshots across 1.2 sets:", total_shots)
    if total_shots == 0:
        print("SCREENSHOTS_MISSING: the 1.2 version carries no screenshots; "
              "Apple will reject the submission as-is. Aborting BEFORE "
              "submit so the parent can fill the sets (see the portal "
              "fallback in the task brief).")
        sys.exit(2)

    # 9. Review submission (reuse an open one, else create).
    s, d = do("LIST review submissions", "GET",
              "/v1/reviewSubmissions" + q(**{"filter[app]": APP_ID}, limit=20))
    sub_id = None
    for rs in d.get("data", []):
        ra = rs.get("attributes", {})
        if ra.get("platform") == "IOS" \
                and ra.get("state") not in ("WAITING_FOR_REVIEW", "IN_REVIEW",
                                            "COMPLETE") \
                and not ra.get("submittedDate"):
            sub_id = rs["id"]
            print("  reusing open reviewSubmission:", sub_id)
    if not sub_id:
        body = {"data": {"type": "reviewSubmissions",
                         "attributes": {"platform": "IOS"},
                         "relationships": {"app": {
                             "data": {"type": "apps", "id": APP_ID}}}}}
        s, d = do("CREATE reviewSubmission", "POST", "/v1/reviewSubmissions",
                  body)
        sub_id = (d.get("data") or {}).get("id")
        print("  created reviewSubmission:", sub_id)
    if not sub_id:
        print("ABORT: no reviewSubmission available")
        sys.exit(1)

    s, d = do("LIST submission items", "GET",
              f"/v1/reviewSubmissions/{sub_id}/items"
              + q(include="appStoreVersion", limit=50))
    have_item = False
    for item in d.get("data", []):
        rel = ((item.get("relationships") or {}).get("appStoreVersion")
               or {}).get("data") or {}
        if rel.get("id") == version_id:
            have_item = True
            print("  version item already present:", item["id"])
    if not have_item:
        body = {"data": {"type": "reviewSubmissionItems",
                         "relationships": {
                             "reviewSubmission": {
                                 "data": {"type": "reviewSubmissions",
                                          "id": sub_id}},
                             "appStoreVersion": {
                                 "data": {"type": "appStoreVersions",
                                          "id": version_id}}}}}
        do("ADD version item", "POST", "/v1/reviewSubmissionItems", body)

    body = {"data": {"type": "reviewSubmissions", "id": sub_id,
                     "attributes": {"submitted": True}}}
    do("SUBMIT", "PATCH", f"/v1/reviewSubmissions/{sub_id}", body)

    # 10. Readback.
    s, d = do("READBACK reviewSubmission", "GET",
              f"/v1/reviewSubmissions/{sub_id}")
    ra = (d.get("data") or {}).get("attributes", {})
    print("  reviewSubmission: state:", ra.get("state"),
          "| submittedDate:", ra.get("submittedDate"))
    s, d = do("READBACK version (final)", "GET",
              f"/v1/appStoreVersions/{version_id}")
    va = (d.get("data") or {}).get("attributes", {})
    print("  version 1.2 final: state:", va.get("appStoreState"),
          "| releaseType:", va.get("releaseType"))
    # Success keys on state/submittedDate, never the `submitted` boolean
    # (Apple returns it null even on success — asc_submit.py's note).
    if ra.get("state") not in ("WAITING_FOR_REVIEW", "IN_REVIEW") \
            and not ra.get("submittedDate"):
        print("ABORT: readback does not show the submission as submitted "
              f"(state: {ra.get('state')}, "
              f"submittedDate: {ra.get('submittedDate')})")
        sys.exit(1)
    print("SUBMIT12_DONE")


if __name__ == "__main__":
    main()
