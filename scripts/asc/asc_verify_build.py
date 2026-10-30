"""Readback for the rebuilt app's TestFlight builds: list the app's most
recent builds and, for a target version+build (argv), print its
processing state and the beta groups it is attached to.

Usage: python3 asc_verify_build.py [version build]"""
import sys, urllib.parse
from asc_common import call

APP_ID = "6827973343"

def q(**kw):
    return "?" + urllib.parse.urlencode(kw)

s, d = call("GET", "/v1/builds" + q(**{"filter[app]": APP_ID},
                                    sort="-uploadedDate", limit=10,
                                    include="preReleaseVersion"))
versions = {v["id"]: v["attributes"].get("version")
            for v in d.get("included", []) if v["type"] == "preReleaseVersions"}
builds = d.get("data", [])
print(f"{len(builds)} most recent builds for app {APP_ID}:")
target = None
want = (sys.argv[1], sys.argv[2]) if len(sys.argv) > 2 else None
for b in builds:
    a = b["attributes"]
    ver = versions.get(b["relationships"]["preReleaseVersion"]["data"]["id"])
    print(" ", b["id"], "version", ver, "build", a.get("version"),
          "state", a.get("processingState"), "expired", a.get("expired"),
          "uploaded", a.get("uploadedDate"))
    if want and ver == want[0] and a.get("version") == want[1]:
        target = b

if want:
    if not target:
        print(f"TARGET_BUILD_NOT_FOUND version={want[0]} build={want[1]}")
        sys.exit(0)
    print("target build:", target["id"])
    # builds->betaGroups does not allow GET_RELATED (403 FORBIDDEN_ERROR:
    # "Allowed operations are: CREATE, DELETE"), so read membership from
    # the group side instead: list the app's beta groups, then each
    # group's builds, and keep the groups that contain the target.
    s, d = call("GET", f"/v1/apps/{APP_ID}/betaGroups" + q(limit=200))
    groups = []
    for g in d.get("data", []):
        s2, d2 = call("GET", f"/v1/betaGroups/{g['id']}/builds" + q(limit=200))
        if any(b["id"] == target["id"] for b in d2.get("data", [])):
            ga = g["attributes"]
            groups.append((g["id"], ga.get("name"), ga.get("isInternalGroup")))
    print("attached beta groups:", groups)
    internal = [g for g in groups if g[2]]
    if internal:
        print("ATTACHED_TO_INTERNAL_GROUP:", internal[0][1])
    else:
        print("NOT_ATTACHED_TO_ANY_INTERNAL_GROUP")
    print("processingState:", target["attributes"].get("processingState"),
          "expired:", target["attributes"].get("expired"))
    print("usesNonExemptEncryption:",
          target["attributes"].get("usesNonExemptEncryption"))
    try:
        s, d = call("GET", f"/v1/builds/{target['id']}/buildBetaDetail")
        bd = d.get("data", {}).get("attributes", {})
        print("betaDetail: internalReviewState=", bd.get("internalReviewState"),
              " externalBuildState=", bd.get("externalBuildState"))
    except SystemExit:
        print("betaDetail: unavailable (buildBetaDetail request failed)")
