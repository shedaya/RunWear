"""Set RunWear's Regulated Medical Device declaration to NO (appInfo).

RunWear is a weather-based clothing recommendation app — it is NOT a
regulated medical device. The first submit attempt failed with
STATE_ERROR.CANNOT_SUBMIT_MISSING_REGULATED_MEDICAL_DEVICE_APP_DECLARATION
pointing at /v1/appInfos/{id}, so the declaration lives on the appInfo.

Route: GET the appInfo and dump every attribute (ground truth for what
the API exposes). If an attribute mentioning 'regulat' or 'medical'
exists, PATCH it to false under its real name. Otherwise try the
candidate attribute names in order and log Apple's exact responses.
Either way, finish with a GET readback and print the final value.
If no API route sets it, print API_DOES_NOT_EXPOSE so the caller
knows the portal route is required.

Usage: python3 asc_medical.py
"""
import json
import sys
import urllib.parse

from asc_common import call

APP_ID = "6827973343"
APP_INFO_ID = "be88cd25-97e0-4a1f-917f-fde761ddf2a4"
TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)
CANDIDATES = ["isRegulatedMedicalDevice", "regulatedMedicalDevice",
              "isMedicalDevice", "medicalDevice"]


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def do(label, method, path, body=None):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:2000])
    return s, d


def get_attrs():
    s, d = do("GET appInfo", "GET", f"/v1/appInfos/{APP_INFO_ID}")
    return (d.get("data") or {}).get("attributes", {}) or {}


def try_patch(name):
    body = {"data": {"type": "appInfos", "id": APP_INFO_ID,
                     "attributes": {name: False}}}
    s, d = do(f"PATCH appInfo {name}=false", "PATCH",
              f"/v1/appInfos/{APP_INFO_ID}", body)
    return s


def main():
    attrs = get_attrs()
    print("  appInfo attributes (full):")
    print(json.dumps(attrs, indent=1, sort_keys=True)[:4000])

    hits = [k for k in attrs
            if "regulat" in k.lower() or "medical" in k.lower()]
    print("  declaration-like attributes present:", hits)

    target = None
    if hits:
        target = hits[0]
        if attrs.get(target) is not False:
            try_patch(target)
    else:
        print("  no declaration attribute in GET payload; trying candidates")
        for cand in CANDIDATES:
            s = try_patch(cand)
            if 200 <= s < 300:
                target = cand
                break
            print(f"  candidate {cand} refused (HTTP {s}); trying next")

    attrs = get_attrs()
    if target is not None:
        print(f"  READBACK {target}:", attrs.get(target))
        if attrs.get(target) is False:
            print("MEDICAL_DECLARATION_SET_NO")
            return
        print("  WARNING: readback does not show false under", target)
    print("API_DOES_NOT_EXPOSE — declaration not set via API; portal route required")
    sys.exit(2)


if __name__ == "__main__":
    main()
