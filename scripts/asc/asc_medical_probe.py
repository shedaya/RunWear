"""Probe round 2 for the Regulated Medical Device declaration.

asc_medical.py proved the declaration is NOT an attribute of the
appInfo resource (GET exposes only age-rating/state attributes; four
candidate PATCHes all returned ENTITY_ERROR.ATTRIBUTE.UNKNOWN). This
probe checks the remaining API surfaces before concluding the
declaration is portal-only:

  1. appInfo relationships (full key list + any declaration-like link)
  2. the apps resource attributes (full dump)
  3. candidate declaration sub-resources under the appInfo

Usage: python3 asc_medical_probe.py
"""
import json
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
APP_INFO_ID = "be88cd25-97e0-4a1f-917f-fde761ddf2a4"
TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def probe(label, path):
    s, d = call("GET", path, expect=TOLERANT)
    print(f"--- {label}: GET -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:600])
    return s, d


def main():
    s, d = probe("APP INFO FULL", f"/v1/appInfos/{APP_INFO_ID}")
    data = d.get("data") or {}
    rels = data.get("relationships") or {}
    print("  relationship keys:", sorted(rels.keys()))
    for k, v in rels.items():
        link = ((v or {}).get("links") or {}).get("related")
        print(f"    {k}: {link}")
    decl = [k for k in rels if "regulat" in k.lower()
            or "medical" in k.lower() or "declar" in k.lower()]
    print("  declaration-like relationships:", decl)

    s, d = probe("APP FULL", f"/v1/apps/{APP_ID}")
    attrs = (d.get("data") or {}).get("attributes", {}) or {}
    print("  app attributes (full):")
    print(json.dumps(attrs, indent=1, sort_keys=True)[:3000])
    hits = [k for k in attrs if "regulat" in k.lower()
            or "medical" in k.lower()]
    print("  declaration-like app attributes:", hits)

    for sub in ("regulatedMedicalDeviceDeclaration",
                "medicalDeviceDeclaration",
                "appRegulatedMedicalDeviceDeclaration"):
        s, d = probe(f"SUB {sub}", f"/v1/appInfos/{APP_INFO_ID}/{sub}")
        if s == 200:
            print("  FOUND:", sub, json.dumps(d)[:1500])
    print("PROBE_DONE")


if __name__ == "__main__":
    main()
