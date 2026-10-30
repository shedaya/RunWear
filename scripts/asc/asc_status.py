"""Read-only ASC publishing-status readback: App Store version state +
attached build, the most recent TestFlight builds, and the newest
build's beta-group attachment + beta detail. GET requests only; this
script never writes to App Store Connect.

Usage: python3 asc_status.py"""
import urllib.parse
from asc_common import call

APP_ID = "6827973343"

def q(**kw):
    return "?" + urllib.parse.urlencode(kw)

s, d = call("GET", f"/v1/apps/{APP_ID}")
a = d.get("data", {}).get("attributes", {})
print("APP:", a.get("name"), "| bundleId:", a.get("bundleId"),
      "| sku:", a.get("sku"))

# --- App Store versions + attached build -------------------------------
s, d = call("GET", f"/v1/apps/{APP_ID}/appStoreVersions" + q(limit=50))
versions = d.get("data", [])
print(f"APP STORE VERSIONS ({len(versions)}):")
if not versions:
    print("  NONE_CREATED")
for v in versions:
    va = v["attributes"]
    print("  ", v["id"], "version", va.get("versionString"),
          "platform", va.get("platform"), "state", va.get("appStoreState"),
          "releaseType", va.get("releaseType"), "created", va.get("createdDate"))
    s2, d2 = call("GET", f"/v1/appStoreVersions/{v['id']}/build")
    rel = d2.get("data") or {}
    if rel:
        ra = rel.get("attributes", {})
        print("     attached build:", rel["id"], "buildNumber", ra.get("version"),
              "processingState", ra.get("processingState"),
              "uploaded", ra.get("uploadedDate"))
    else:
        print("     attached build: NONE")

# --- Recent TestFlight builds -------------------------------------------
s, d = call("GET", "/v1/builds" + q(**{"filter[app]": APP_ID},
                                    sort="-uploadedDate", limit=15,
                                    include="preReleaseVersion"))
prv = {v["id"]: v["attributes"].get("version")
       for v in d.get("included", []) if v["type"] == "preReleaseVersions"}
builds = d.get("data", [])
print(f"BUILDS ({len(builds)} most recent):")
for b in builds:
    ba = b["attributes"]
    ver = prv.get(b["relationships"]["preReleaseVersion"]["data"]["id"])
    print("  ", b["id"], "marketing", ver, "build", ba.get("version"),
          "state", ba.get("processingState"), "expired", ba.get("expired"),
          "uploaded", ba.get("uploadedDate"))

# --- Newest build: beta-group attachment + beta detail ------------------
if builds:
    newest = builds[0]
    na = newest["attributes"]
    nver = prv.get(newest["relationships"]["preReleaseVersion"]["data"]["id"])
    print("NEWEST BUILD:", newest["id"], "marketing", nver,
          "build", na.get("version"), "state", na.get("processingState"),
          "expired", na.get("expired"))
    s, d = call("GET", f"/v1/apps/{APP_ID}/betaGroups" + q(limit=200))
    attached = []
    for g in d.get("data", []):
        s2, d2 = call("GET", f"/v1/betaGroups/{g['id']}/builds" + q(limit=200))
        if any(b["id"] == newest["id"] for b in d2.get("data", [])):
            ga = g["attributes"]
            attached.append((g["id"], ga.get("name"), ga.get("isInternalGroup")))
    print("NEWEST BUILD attached beta groups:", attached)
    internal = [g for g in attached if g[2]]
    print("ATTACHED_TO_INTERNAL_GROUP:", internal[0][1] if internal else "NONE")
    try:
        s, d = call("GET", f"/v1/builds/{newest['id']}/buildBetaDetail")
        bd = d.get("data", {}).get("attributes", {})
        print("betaDetail: internalReviewState=", bd.get("internalReviewState"),
              " externalBuildState=", bd.get("externalBuildState"))
    except SystemExit:
        print("betaDetail: unavailable (buildBetaDetail request failed)")
print("STATUS_DONE")
