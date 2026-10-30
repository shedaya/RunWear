"""Attach a TestFlight build to the app's internal beta group(s).

Usage: python3 asc_attach_build.py [version build]

Finds the build by marketing version + build number, then for each
internal beta group of the app that does not already contain it, POSTs
the builds relationship. Idempotent: already-attached groups are
skipped. Mirrors the attachment the sibling apps' upload workflows
perform inline.
"""
import sys, urllib.parse
from asc_common import call

APP_ID = "6827973343"

def q(**kw):
    return "?" + urllib.parse.urlencode(kw)

want = (sys.argv[1], sys.argv[2])
s, d = call("GET", "/v1/builds" + q(**{"filter[app]": APP_ID},
                                    sort="-uploadedDate", limit=25,
                                    include="preReleaseVersion"))
versions = {v["id"]: v["attributes"].get("version")
            for v in d.get("included", []) if v["type"] == "preReleaseVersions"}
target = None
for b in d.get("data", []):
    ver = versions.get(b["relationships"]["preReleaseVersion"]["data"]["id"])
    if ver == want[0] and b["attributes"].get("version") == want[1]:
        target = b
        break
if not target:
    print(f"TARGET_BUILD_NOT_FOUND version={want[0]} build={want[1]}")
    sys.exit(1)
print("target build:", target["id"], "state:",
      target["attributes"].get("processingState"))

s, d = call("GET", f"/v1/apps/{APP_ID}/betaGroups" + q(limit=200))
attached_any = False
for g in d.get("data", []):
    ga = g["attributes"]
    if not ga.get("isInternalGroup"):
        continue
    s2, d2 = call("GET", f"/v1/betaGroups/{g['id']}/builds" + q(limit=200))
    if any(b["id"] == target["id"] for b in d2.get("data", [])):
        print("already attached to internal group:", ga.get("name"))
        attached_any = True
        continue
    body = {"data": [{"type": "builds", "id": target["id"]}]}
    s3, _ = call("POST", f"/v1/betaGroups/{g['id']}/relationships/builds",
                 body=body, expect=(200, 201, 204))
    print(f"attach POST to internal group {ga.get('name')} -> {s3}")
    attached_any = True
print("ATTACHED_TO_INTERNAL_GROUP" if attached_any else "NO_INTERNAL_GROUP_FOUND")
