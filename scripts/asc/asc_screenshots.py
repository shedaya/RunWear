"""Upload the reviewed App Store screenshot sets for RunWear 1.0.

Three families, each with its own committed PNG directory and its own
screenshot set on the en-US localization of version 1.0:
  iphone  scripts/asc/screenshots/        APP_IPHONE_69/67/65 (largest
                                          the create enum accepts)
  ipad    scripts/asc/screenshots-ipad/   APP_IPAD_PRO_129 (2064x2752;
          the create-set enum has no APP_IPAD_13 - the 13-inch frames
          live under the 12.9-inch Pro display type, proven by run
          41211488282's 409 error body enumerating the valid values)
  watch   scripts/asc/screenshots-watch/  display type chosen from the
                                          PNG's real pixel size

Per family: find-or-create the set, reserve -> upload -> commit each
new file in filename order, then read the set back and print exactly
what App Store Connect holds. Idempotent per file name: a screenshot
already in the set is skipped, not duplicated. A family with no local
PNGs is skipped with a loud log line (never silently).

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
TOLERANT = (200, 201, 204, 400, 403, 404, 409, 422)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

WATCH_BY_SIZE = {
    (312, 390): ["APP_WATCH_SERIES_3"],
    (324, 394): ["APP_WATCH_SERIES_4", "APP_WATCH_SERIES_3"],
    (368, 448): ["APP_WATCH_SERIES_4"],
    (352, 430): ["APP_WATCH_SERIES_7", "APP_WATCH_SERIES_4"],
    (396, 484): ["APP_WATCH_SERIES_7"],
    (410, 502): ["APP_WATCH_ULTRA", "APP_WATCH_SERIES_7"],
    (374, 446): ["APP_WATCH_SERIES_10", "APP_WATCH_SERIES_7"],
    (416, 496): ["APP_WATCH_SERIES_10", "APP_WATCH_SERIES_7"],
    (422, 514): ["APP_WATCH_SERIES_11", "APP_WATCH_SERIES_10",
                 "APP_WATCH_SERIES_7"],
}
WATCH_FALLBACK = ["APP_WATCH_SERIES_3", "APP_WATCH_SERIES_4",
                  "APP_WATCH_SERIES_7", "APP_WATCH_ULTRA",
                  "APP_WATCH_SERIES_10"]

FAMILIES = [
    {"name": "iphone", "dir": "screenshots",
     "candidates": ["APP_IPHONE_69", "APP_IPHONE_67", "APP_IPHONE_65"]},
    {"name": "ipad", "dir": "screenshots-ipad",
     "candidates": ["APP_IPAD_PRO_129", "APP_IPAD_PRO_3GEN_129",
                    "APP_IPAD_13"]},
    {"name": "watch", "dir": "screenshots-watch",
     "candidates": None},  # resolved from PNG size at runtime
]


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def do(label, method, path, body=None):
    s, d = call(method, path, body, expect=TOLERANT)
    print(f"--- {label}: {method} -> HTTP {s}")
    if s >= 400:
        print("    error body:", json.dumps(d)[:2000])
    return s, d


def png_size(path):
    with open(path, "rb") as fh:
        head = fh.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return (int.from_bytes(head[16:20], "big"),
            int.from_bytes(head[20:24], "big"))


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


def find_or_create_set(loc_id, candidates, existing_sets):
    for cand in candidates:
        sset = next((x for x in existing_sets
                     if x.get("attributes", {}).get("screenshotDisplayType")
                     == cand), None)
        if sset is not None:
            return sset, cand
    for cand in candidates:
        body = {"data": {"type": "appScreenshotSets",
                         "attributes": {"screenshotDisplayType": cand},
                         "relationships": {
                             "appStoreVersionLocalization": {
                                 "data": {"type": "appStoreVersionLocalizations",
                                          "id": loc_id}}}}}
        s, d = do(f"CREATE SET {cand}", "POST", "/v1/appScreenshotSets", body)
        if d.get("data", {}).get("id"):
            return d["data"], cand
        print(f"  create {cand} refused; trying next candidate")
    return None, None


def process_family(loc_id, existing_sets, fam, files):
    name = fam["name"]
    candidates = fam["candidates"]
    if candidates is None:  # watch: resolve from the first PNG's size
        size = png_size(files[0])
        print(f"  watch PNG size: {size}")
        candidates = WATCH_BY_SIZE.get(size, WATCH_FALLBACK)
        print(f"  watch display-type candidates: {candidates}")
    sset, display_type = find_or_create_set(loc_id, candidates, existing_sets)
    set_id = (sset or {}).get("id")
    if not set_id:
        print(f"ABORT: {name} screenshot set id unavailable "
              f"(candidates tried: {candidates})")
        sys.exit(1)
    print(f"  {name} set:", set_id, display_type)

    def list_shots():
        s_, d_ = do("  SHOTS", "GET",
                    f"/v1/appScreenshotSets/{set_id}/appScreenshots" + q(limit=50))
        return d_.get("data", [])

    existing = {}
    for sh in list_shots():
        a = sh.get("attributes", {})
        asset = a.get("imageAsset") or {}
        existing[a.get("fileName")] = (
            sh.get("id"),
            (a.get("assetDeliveryState") or {}).get("state"),
            asset.get("width") or 0)

    for path in files:
        fname = os.path.basename(path)
        if fname in existing:
            shot_id, state, asset_w = existing[fname]
            # Replace shots that can never complete on their own:
            # FAILED (terminal rejection, e.g. a pixel size the set type
            # does not accept) and UPLOAD_COMPLETE with no image asset
            # at all - at the START of a run such a shot was uploaded by
            # a previous run and its processing is wedged (observed:
            # 02-tomorrow-forecast sat at 0x0 UPLOAD_COMPLETE across
            # three readbacks over ~10 minutes while its siblings all
            # completed). The replacement file is the local master, so
            # nothing is lost. COMPLETE shots are never touched.
            stuck = state == "FAILED" or (
                state == "UPLOAD_COMPLETE" and asset_w == 0)
            if stuck:
                s, d = do(f"DELETE STUCK {fname} (state={state})", "DELETE",
                          f"/v1/appScreenshots/{shot_id}")
                if s >= 400:
                    print(f"ABORT: could not delete stuck shot {fname}")
                    sys.exit(1)
            else:
                print(f"  skip {fname}: already in set (state={state})")
                continue
        blob = open(path, "rb").read()
        checksum = hashlib.md5(blob).hexdigest()
        body = {"data": {"type": "appScreenshots",
                         "attributes": {"fileName": fname,
                                        "fileSize": len(blob)},
                         "relationships": {
                             "appScreenshotSet": {
                                 "data": {"type": "appScreenshotSets",
                                          "id": set_id}}}}}
        s, d = do(f"RESERVE {fname}", "POST", "/v1/appScreenshots", body)
        data = d.get("data") or {}
        shot_id = data.get("id")
        ops = data.get("attributes", {}).get("uploadOperations") or []
        if not shot_id or not ops:
            print(f"ABORT: reservation for {fname} returned no upload operations")
            sys.exit(1)
        for op in ops:
            put_bytes(op, blob)
        body = {"data": {"type": "appScreenshots", "id": shot_id,
                         "attributes": {"uploaded": True,
                                        "sourceFileChecksum": checksum}}}
        s, d = do(f"COMMIT {fname}", "PATCH", f"/v1/appScreenshots/{shot_id}", body)
        if s >= 400:
            print(f"ABORT: commit for {fname} failed (HTTP {s}) — "
                  "image likely rejected (size/type mismatch); see error body")
            sys.exit(1)

    shots = list_shots()
    print(f"READBACK {name} screenshot count:", len(shots))
    for sh in shots:
        a = sh.get("attributes", {})
        asset = a.get("imageAsset") or {}
        state = (a.get("assetDeliveryState") or {}).get("state")
        print(f"  - {a.get('fileName')}: {asset.get('width')}x{asset.get('height')}"
              f" state={state}")
    complete = sum(1 for sh in shots
                   if (sh.get("attributes", {}).get("assetDeliveryState") or {})
                   .get("state") == "COMPLETE")
    print(f"READBACK {name} complete: {complete}/{len(shots)}")
    return display_type, len(shots), complete


def main():
    planned = {}
    for fam in FAMILIES:
        files = sorted(glob.glob(os.path.join(BASE_DIR, fam["dir"], "*.png")))
        planned[fam["name"]] = (fam, files)
        print(f"family {fam['name']}: {len(files)} local PNG(s)",
              [os.path.basename(f) for f in files])
    if not any(files for _, files in planned.values()):
        print("ABORT: no PNGs in any family directory")
        sys.exit(1)

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

    # 2. Existing sets (printed so every choice is visible in the log).
    s, d = do("SETS", "GET",
              f"/v1/appStoreVersionLocalizations/{loc_id}/appScreenshotSets"
              + q(limit=50))
    existing_sets = d.get("data", [])
    for x in existing_sets:
        print("  existing set:", x.get("id"),
              x.get("attributes", {}).get("screenshotDisplayType"))

    summary = {}
    for name, (fam, files) in planned.items():
        if not files:
            print(f"SKIP family {name}: no local PNGs committed")
            summary[name] = (None, 0, 0)
            continue
        summary[name] = process_family(loc_id, existing_sets, fam, files)

    print("SETS_READBACK " + " ".join(
        f"{k}={v[1]}({v[0]})" for k, v in summary.items()))


if __name__ == "__main__":
    main()
