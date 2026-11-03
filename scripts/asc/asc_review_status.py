"""READ-ONLY App Store Connect review-status readback for the three
Uptime Ventures iOS submissions (JetGuide, RunWear, AccliMate).

GET requests only. For each app: identity, its App Store versions with
appStoreState, its review submissions with state + submittedDate, and
the items of the newest submission. Printed as JSON to stdout so the
workflow log is the evidence.
"""
import json, sys
from urllib.parse import quote

from asc_common import call

APPS = [
    ("JetGuide", "6826923693"),
    ("RunWear", "6827973343"),
    ("AccliMate", "6828337210"),
]

def get(path):
    status, data = call("GET", quote(path, safe="/?&=[]%,-._~:"))
    return status, data

def attrs(resource):
    return resource.get("attributes", {})

out = {}
for name, app_id in APPS:
    entry = {}
    try:
        _, app = get(f"/v1/apps/{app_id}")
        a = attrs(app.get("data", {}))
        entry["app"] = {k: a.get(k) for k in ("name", "bundleId", "sku")}

        _, versions = get(f"/v1/apps/{app_id}/appStoreVersions?limit=10")
        entry["appStoreVersions"] = [
            {k: attrs(v).get(k) for k in
             ("versionString", "platform", "appStoreState", "createdDate")}
            | {"id": v.get("id")}
            for v in versions.get("data", [])
        ]

        _, subs = get(
            f"/v1/reviewSubmissions?filter[app]={app_id}&limit=10")
        sub_list = []
        for s in subs.get("data", []):
            sa = attrs(s)
            sub_list.append(
                {k: sa.get(k) for k in ("platform", "state", "submittedDate")}
                | {"id": s.get("id")})
        entry["reviewSubmissions"] = sub_list

        if sub_list:
            newest = sub_list[0]
            _, items = get(
                f"/v1/reviewSubmissions/{newest['id']}/items?limit=50")
            entry["newestSubmissionItems"] = [
                {"id": i.get("id"),
                 "state": attrs(i).get("state"),
                 "relationships": sorted((i.get("relationships") or {}).keys())}
                for i in items.get("data", [])
            ]
    except SystemExit as e:
        entry["error"] = str(e)
    out[name] = entry

print(json.dumps(out, indent=2))
sys.exit(0)
