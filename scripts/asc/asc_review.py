"""Read-only App Review state probe for RunWear (app 6827973343).

Dumps, verbatim, everything the ASC API exposes about the 1.0 review:
- the appStoreVersion record (state, attached build, review detail we sent)
- the reviewSubmissions list for the app
- the specific submission 1e7bfc9d-1204-4f96-a8ec-59f2968de35f + its items
Soft-fails per endpoint: an HTTP error is reported inline, never aborts.
Nothing is written to ASC.
"""
import json, sys, urllib.request, urllib.error
sys.path.insert(0, ".")
from asc_common import BASE, token

APP_ID = "6827973343"
SUBMISSION_ID = "1e7bfc9d-1204-4f96-a8ec-59f2968de35f"
VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.0"

def get(path):
    url = path if path.startswith("http") else BASE + path
    req = urllib.request.Request(url, method="GET")
    req.add_header("Authorization", "Bearer " + token())
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:2000].decode("utf-8", "replace")}
        return e.code, parsed
    except Exception as e:
        return -1, {"error": str(e)}

def section(title, path):
    print(f"\n===== {title}\nGET {path}")
    status, data = get(path)
    print(f"--> HTTP {status}")
    print(json.dumps(data, indent=1, ensure_ascii=False))
    return data

def main():
    # 1. The version record itself (state + attached build id).
    versions = section(
        "appStoreVersions (filter versionString)",
        f"/v1/apps/{APP_ID}/appStoreVersions?filter[versionString]={VERSION}"
        "&fields[appStoreVersions]=versionString,appStoreState,appVersionState,createdDate,releaseType,earliestReleaseDate,downloadable"
        "&include=build&fields[builds]=version,processingState,uploadedDate",
    )
    version_id = None
    for item in versions.get("data", []):
        if item.get("attributes", {}).get("versionString") == VERSION:
            version_id = item["id"]
    print(f"\nVERSION_ID for {VERSION}: {version_id}")

    if version_id:
        section("appStoreVersion full record",
                f"/v1/appStoreVersions/{version_id}")
        section("appStoreReviewDetail (what we sent reviewers)",
                f"/v1/appStoreVersions/{version_id}/appStoreReviewDetail")
        section("appStoreVersionSubmission (legacy submission record)",
                f"/v1/appStoreVersions/{version_id}/appStoreVersionSubmission")

    # 2. Review submissions for the app (all states).
    section("reviewSubmissions for app",
            f"/v1/reviewSubmissions?filter[app]={APP_ID}&limit=50")

    # 3. The specific submission + its items.
    sub = section("reviewSubmission (the Oct 31 submission)",
                  f"/v1/reviewSubmissions/{SUBMISSION_ID}")
    items = section("reviewSubmission items (plain)",
                    f"/v1/reviewSubmissions/{SUBMISSION_ID}/items?limit=50")
    # 4. Each item in full (states / rejection detail live here if anywhere).
    for item in items.get("data", []):
        section(f"reviewSubmissionItem {item.get('id')}",
                f"/v1/reviewSubmissionItems/{item.get('id')}")
        section(f"reviewSubmissionItem {item.get('id')} (include all)",
                f"/v1/reviewSubmissionItems/{item.get('id')}"
                "?include=appStoreVersion,appCustomProductPageVersion,"
                "appEvent,appClipDefaultExperience,inAppPurchaseV2,subscription")

    # 5. Subscription + IAP review states (first-submission subscription
    #    review rides with the app submission; a rejected item may be here).
    groups = section("subscriptionGroups for app",
                     f"/v1/apps/{APP_ID}/subscriptionGroups?limit=50")
    for grp in groups.get("data", []):
        gid = grp.get("id")
        subs = section(f"subscriptions in group {gid}",
                       f"/v1/subscriptionGroups/{gid}/subscriptions?limit=50")
        for s in subs.get("data", []):
            section(f"subscription {s.get('id')} full record",
                    f"/v1/subscriptions/{s.get('id')}")
    section("inAppPurchasesV2 for app",
            f"/v1/apps/{APP_ID}/inAppPurchasesV2?limit=50")

    # 6. Version localizations (metadata on record for classification).
    if version_id:
        section("appStoreVersionLocalizations",
                f"/v1/appStoreVersions/{version_id}/appStoreVersionLocalizations?limit=50")

if __name__ == "__main__":
    main()
