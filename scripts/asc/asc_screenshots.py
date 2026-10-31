"""Upload the reviewed App Store screenshot set for RunWear 1.0.

Reads PNGs from scripts/asc/screenshots/ (committed after the
parent's frame-by-frame review of the capture run), attaches them —
in filename order — to the en-US localization's 6.9" iPhone
screenshot set (APP_IPHONE_69) of version 1.0, then reads the set
back and prints exactly what App Store Connect holds.

Idempotent per file name: a screenshot whose fileName is already in
the set at a COMPLETE delivery state is skipped, not duplicated.

Usage: python3 asc_screenshots.py
"""
import glob
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request

from asc_common import call

APP_ID = "6827973343"
VERSION_ID = "505f7bab-da80-49c0-9d29-a8d51987a26a"
DISPLAY_TYPE = "APP_IPHONE_69"
TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)
SHOTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def do(label, method, path, body=None):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:2000])
    return s, d


def put_bytes(op, blob):
    """Execute one ASC upload operation (pre-signed URL, no auth)."""
    start = op.get("offset", 0)
    length = op.get("length", len(blob) - start)
    chunk = blob[start:start + length]
    req = urllib.request.Request(op["url"], data=chunk,
                                 method=op.get("method", "PUT"))
    for h in op.get("requestHeaders", []):
        req.add_header(h["name"], h["value"])
    with urllib.request.urlopen(req, timeout=300) as resp:
        print(f"    upload op -> HTTP {resp.status} ({length} bytes)")


def main():
    files = sorted(glob.glob(os.path.join(SHOTS_DIR, "*.png")))
    if not files:
        print("ABORT: no PNGs in", SHOTS_DIR)
        sys.exit(1)
    print("files to upload:", [os.path.basename(f) for f in files])

    # 1. The en-US localization of version 1.0.
    s, d = do("LOCALIZATIONS", "GET",
              f"/v1/appStoreVersions/{VERSION_ID}/appStoreVersionLocalizations"
              + q(limit=50))
    locs = d.get("data", [])
    loc = next((l for l in locs
                if l.get("attributes", {}).get("locale") == "en-US"), None)
    if loc is None and len(locs) == 1:
        loc = locs[0]
    if loc is None:
        print("ABORT: en-US localization not found among",
              [l.get("attributes", {}).get("locale") for l in locs])
        sys.exit(1)
    loc_id = loc["id"]
    print("  localization:", loc_id, loc.get("attributes", {}).get("locale"))

    # 2. The 6.9" screenshot set (find or create).
    s, d = do("SETS", "GET",
              f"/v1/appStoreVersionLocalizations/{loc_id}/appScreenshotSets"
              + q(limit=50))
    sset = next((x for x in d.get("data", [])
                 if x.get("attributes", {}).get("screenshotDisplayType")
                 == DISPLAY_TYPE), None)
    if sset is None:
        body = {"data": {"type": "appScreenshotSets",
                         "attributes": {"screenshotDisplayType": DISPLAY_TYPE},
                         "relationships": {
                             "appStoreVersionLocalization": {
                                 "data": {"type": "appStoreVersionLocalizations",
                                          "id": loc_id}}}}}
        s, d = do("CREATE SET", "POST", "/v1/appScreenshotSets", body)
        sset = d.get("data") or {}
    set_id = sset.get("id")
    if not set_id:
        print("ABORT: screenshot set id unavailable")
        sys.exit(1)
    print("  set:", set_id, DISPLAY_TYPE)

    # 3. Existing screenshots (for idempotency + final readback).
    def list_shots():
        s_, d_ = do("  SHOTS", "GET",
                    f"/v1/appScreenshotSets/{set_id}/appScreenshots" + q(limit=50))
        return d_.get("data", [])

    existing = {sh.get("attributes", {}).get("fileName") for sh in list_shots()}

    # 4. Reserve -> upload -> commit, per file, in order.
    for path in files:
        name = os.path.basename(path)
        if name in existing:
            print(f"  skip {name}: already in set")
            continue
        blob = open(path, "rb").read()
        checksum = hashlib.md5(blob).hexdigest()
        body = {"data": {"type": "appScreenshots",
                         "attributes": {"fileName": name,
                                        "fileSize": len(blob)},
                         "relationships": {
                             "appScreenshotSet": {
                                 "data": {"type": "appScreenshotSets",
                                          "id": set_id}}}}}
        s, d = do(f"RESERVE {name}", "POST", "/v1/appScreenshots", body)
        data = d.get("data") or {}
        shot_id = data.get("id")
        ops = data.get("attributes", {}).get("uploadOperations") or []
        if not shot_id or not ops:
            print(f"ABORT: reservation for {name} returned no upload operations")
            sys.exit(1)
        for op in ops:
            put_bytes(op, blob)
        body = {"data": {"type": "appScreenshots", "id": shot_id,
                         "attributes": {"uploaded": True,
                                        "sourceFileChecksum": checksum}}}
        do(f"COMMIT {name}", "PATCH", f"/v1/appScreenshots/{shot_id}", body)

    # 5. Readback: what ASC actually holds now.
    shots = list_shots()
    print("READBACK screenshot count:", len(shots))
    for sh in shots:
        a = sh.get("attributes", {})
        asset = a.get("imageAsset") or {}
        state = (a.get("assetDeliveryState") or {}).get("state")
        print(f"  - {a.get('fileName')}: {asset.get('width')}x{asset.get('height')}"
              f" state={state}")
    complete = sum(1 for sh in shots
                   if (sh.get("attributes", {}).get("assetDeliveryState") or {})
                   .get("state") == "COMPLETE")
    print(f"READBACK complete: {complete}/{len(shots)}")


if __name__ == "__main__":
    main()
