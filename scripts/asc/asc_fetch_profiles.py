"""Download provisioning profiles by name from App Store Connect.

Used by the iOS TestFlight workflow (GEN-5, Addendum 7): once the
widget's portal objects exist, the portal — not a stored secret —
is the source of truth for profiles, because the app profile had
to be re-issued when the App Group capability landed (a stale
secret copy would fail archive entitlement validation). Writes
each profile's .mobileprovision to the directory named by argv[1]
under its profile NAME, and prints name + UUID lines for the
install step.

Usage: python3 asc_fetch_profiles.py <outdir> "Name One" "Name Two" ...
"""
import base64, os, sys
import plistlib
from asc_common import call

outdir = sys.argv[1]
wanted = sys.argv[2:]
os.makedirs(outdir, exist_ok=True)

s, d = call("GET", "/v1/profiles?limit=200")
by_name = {p["attributes"]["name"]: p for p in d.get("data", [])}
missing = [n for n in wanted if n not in by_name]
if missing:
    raise SystemExit(f"PROFILES_NOT_FOUND_IN_ASC: {missing}")

for name in wanted:
    p = by_name[name]
    raw = base64.b64decode(p["attributes"]["profileContent"])
    # The profile is a CMS-signed plist; the plist payload follows
    # the signature header — plistlib cannot parse the wrapper, so
    # extract the UUID via the embedded plist slice.
    start = raw.find(b"<?xml")
    end = raw.rfind(b"</plist>") + len(b"</plist>")
    info = plistlib.loads(raw[start:end])
    uuid = info["UUID"]
    path = os.path.join(outdir, name + ".mobileprovision")
    with open(path, "wb") as f:
        f.write(raw)
    print(f"PROFILE {name} uuid={uuid} bytes={len(raw)} -> {path}")
print("FETCH_COMPLETE")
