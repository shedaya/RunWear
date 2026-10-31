"""Attach build 1.0 (8) to RunWear's 1.0 version and submit for review.

Strict by design: EVERY call in this path must return 2xx. Any
non-2xx prints the error body and exits non-zero immediately, so a
failed submit can never masquerade as a green run (the 2026-10-31
first attempt returned 409s on the item-add and submit calls yet
exited 0 — this rewrite closes that hole).

Steps, each read back:
  1. Preconditions: target build VALID; >= 1 screenshot across sets.
  2. Attach build fa972eeb (1.0 (8)) via PATCH on the version.
  3. Reuse the open reviewSubmission (or create one), ensure the
     version is present as a reviewSubmissionItem (listed first —
     an already-present item is skipped, not re-POSTed), then
     PATCH submitted=true.
  4. Read back: reviewSubmission state/submittedDate and the version's
     appStoreState; abort unless they show the submission landed (the
     read-back `submitted` boolean is null even on success — see the
     note at the guard).

Usage: python3 asc_submit.py
"""
import json
import sys
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
VERSION_ID = "505f7bab-da80-49c0-9d29-a8d51987a26a"
BUILD_ID = "fa972eeb-fa7f-4544-939f-adbabccbff50"
# Errors are still RETURNED by call() (so the body can be printed in
# full), but do() below treats any of them as fatal.
TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def do(label, method, path, body=None):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:3000])
        print(f"ABORT: {label} failed with HTTP {s} — submit path is "
              "strict-2xx; exiting non-zero so this cannot read as success")
        sys.exit(1)
    return s, d


def main():
    # 1. Preconditions
    s, d = do("CHECK build", "GET", f"/v1/builds/{BUILD_ID}")
    ba = d.get("data", {}).get("attributes", {})
    print("  build:", ba.get("version"), ba.get("processingState"),
          "expired:", ba.get("expired"))
    if ba.get("version") != "8" or ba.get("processingState") != "VALID":
        print("ABORT: target build is not build 8 / VALID")
        sys.exit(1)

    s, d = do("CHECK screenshot sets", "GET",
              f"/v1/appStoreVersions/{VERSION_ID}/appStoreVersionLocalizations"
              + q(limit=50))
    total_shots = 0
    for loc in d.get("data", []):
        s2, d2 = do("  sets", "GET",
                    f"/v1/appStoreVersionLocalizations/{loc['id']}/appScreenshotSets"
                    + q(limit=50))
        for sset in d2.get("data", []):
            s3, d3 = do("  shots", "GET",
                        f"/v1/appScreenshotSets/{sset['id']}/appScreenshots"
                        + q(limit=50))
            total_shots += len(d3.get("data", []))
    print("  total screenshots across sets:", total_shots)
    if total_shots == 0:
        print("ABORT: zero screenshots — required assets missing")
        sys.exit(1)

    # 2. Attach build
    body = {"data": {"type": "appStoreVersions", "id": VERSION_ID,
                     "relationships": {"build": {
                         "data": {"type": "builds", "id": BUILD_ID}}}}}
    do("ATTACH build", "PATCH", f"/v1/appStoreVersions/{VERSION_ID}", body)
    s, d = do("READBACK version build", "GET",
              f"/v1/appStoreVersions/{VERSION_ID}/build")
    rel = d.get("data") or {}
    print("  attached now:", rel.get("id"))
    if rel.get("id") != BUILD_ID:
        print("ABORT: readback does not show build (8) attached")
        sys.exit(1)

    # 3. Review submission
    s, d = do("LIST review submissions", "GET",
              "/v1/reviewSubmissions" + q(**{"filter[app]": APP_ID}, limit=20))
    sub_id = None
    for rs in d.get("data", []):
        ra = rs.get("attributes", {})
        # "Open" = not yet submitted. Key on state/submittedDate, not
        # the `submitted` boolean — Apple returns it null even after a
        # successful submit (see the note at the final guard), so a
        # completed submission would otherwise be mistaken for open.
        if ra.get("platform") == "IOS" \
                and ra.get("state") not in ("WAITING_FOR_REVIEW", "IN_REVIEW") \
                and not ra.get("submittedDate"):
            sub_id = rs["id"]
            print("  reusing open reviewSubmission:", sub_id)
    if not sub_id:
        body = {"data": {"type": "reviewSubmissions",
                         "attributes": {"platform": "IOS"},
                         "relationships": {"app": {
                             "data": {"type": "apps", "id": APP_ID}}}}}
        s, d = do("CREATE reviewSubmission", "POST", "/v1/reviewSubmissions", body)
        sub_id = (d.get("data") or {}).get("id")
        print("  created reviewSubmission:", sub_id)
    if not sub_id:
        print("ABORT: no reviewSubmission available")
        sys.exit(1)

    # Ensure the version item is present (list first; POST only if absent).
    s, d = do("LIST submission items", "GET",
              f"/v1/reviewSubmissions/{sub_id}/items"
              + q(include="appStoreVersion", limit=50))
    have_item = False
    for item in d.get("data", []):
        rel = ((item.get("relationships") or {}).get("appStoreVersion")
               or {}).get("data") or {}
        if rel.get("id") == VERSION_ID:
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
                                          "id": VERSION_ID}}}}}
        do("ADD version item", "POST", "/v1/reviewSubmissionItems", body)

    body = {"data": {"type": "reviewSubmissions", "id": sub_id,
                     "attributes": {"submitted": True}}}
    do("SUBMIT", "PATCH", f"/v1/reviewSubmissions/{sub_id}", body)

    # 4. Readback
    s, d = do("READBACK reviewSubmission", "GET",
              f"/v1/reviewSubmissions/{sub_id}")
    ra = (d.get("data") or {}).get("attributes", {})
    print("  reviewSubmission: submitted:", ra.get("submitted"),
          "| state:", ra.get("state"), "| submittedDate:", ra.get("submittedDate"))
    s, d = do("READBACK version", "GET", f"/v1/appStoreVersions/{VERSION_ID}")
    va = (d.get("data") or {}).get("attributes", {})
    print("  version state now:", va.get("appStoreState"))
    # Do NOT key success on the `submitted` boolean: Apple returns it
    # as null even on a fully successful submission. Evidence: the
    # 2026-10-31 RunWear submit (asc-verify run 41217290133) landed —
    # the version and the reviewSubmission both read WAITING_FOR_REVIEW
    # with a non-null submittedDate, independently confirmed by audit
    # run 41217360276 — yet `submitted` read null, and a guard keyed on
    # it aborted a run whose submit had already succeeded. The
    # truth-carrying fields are the submission state and submittedDate.
    if ra.get("state") not in ("WAITING_FOR_REVIEW", "IN_REVIEW") \
            and not ra.get("submittedDate"):
        print("ABORT: readback does not show the submission as submitted "
              f"(state: {ra.get('state')}, "
              f"submittedDate: {ra.get('submittedDate')})")
        sys.exit(1)
    print("SUBMIT_DONE")


if __name__ == "__main__":
    main()
