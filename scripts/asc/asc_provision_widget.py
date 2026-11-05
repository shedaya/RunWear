"""GEN-5 (Addendum 7): portal provisioning for the RunWear widget.

The widget extension added in gen-5 needs portal objects the
build-25 pipeline never had:
  - a bundle ID for com.uptimeventures.runwear.widget
  - the App Group group.com.uptimeventures.runwear, assigned to
    BOTH the app and the widget bundle IDs (the app's profile must
    be re-issued after the capability lands, or the archive fails
    entitlement validation)
  - an App Store profile named "RunWear Widget App Store", and a
    re-issued "RunWear App Store" profile carrying the group

task=probe: read-only inventory (bundle IDs, app groups, the app
bundle ID's capabilities + group assignments, certificates,
profiles) — establishes what the API key may do before anything
is created.
task=apply: idempotent creation of the above, in dependency
order. Stops with a named message at the first capability the
API does not expose (app groups are the known risk).

Usage: python3 asc_provision_widget.py [probe|apply]
"""
import sys, urllib.parse
from asc_common import call

APP_BUNDLE = "com.uptimeventures.runwear"
WIDGET_BUNDLE = "com.uptimeventures.runwear.widget"
WATCH_BUNDLE = "com.uptimeventures.runwear.watchkitapp"
APP_GROUP = "group.com.uptimeventures.runwear"
WIDGET_PROFILE = "RunWear Widget App Store"
APP_PROFILE = "RunWear App Store"

def q(**kw):
    return "?" + urllib.parse.urlencode(kw)

def list_all(path):
    out, url = [], path
    while url:
        s, d = call("GET", url)
        out.extend(d.get("data", []))
        url = (d.get("links") or {}).get("next")
    return out

def bundle_ids():
    return {b["attributes"]["identifier"]: b
            for b in list_all("/v1/bundleIds" + q(limit=200))}

def find_profile(name):
    for p in list_all("/v1/profiles" + q(limit=200)):
        if p["attributes"].get("name") == name:
            return p
    return None

def distribution_cert():
    certs = list_all("/v1/certificates" + q(
        **{"filter[certificateType]": "DISTRIBUTION"}, limit=50))
    live = [c for c in certs
            if c["attributes"].get("expirationDate", "") > "2026-11-05"]
    if not live:
        raise SystemExit("NO_LIVE_DISTRIBUTION_CERTIFICATE")
    return live[0]

def probe():
    bids = bundle_ids()
    print(f"{len(bids)} bundle IDs:")
    for ident, b in sorted(bids.items()):
        print(" ", ident, b["id"])
    for ident in (APP_BUNDLE, WIDGET_BUNDLE):
        print(f"{ident}: {'PRESENT' if ident in bids else 'ABSENT'}")
    s, d = call("GET", "/v1/appGroups" + q(limit=200), expect=(200, 403, 404))
    if s == 200:
        groups = [(g["id"], g["attributes"].get("identifier"))
                  for g in d.get("data", [])]
        print("app groups:", groups)
    else:
        print(f"appGroups LIST unavailable: HTTP {s}")
    if APP_BUNDLE in bids:
        app_id = bids[APP_BUNDLE]["id"]
        caps = list_all(f"/v1/bundleIds/{app_id}/bundleIdCapabilities")
        print("app capabilities:",
              [c["attributes"].get("capabilityType") for c in caps])
        s, d = call("GET", f"/v1/bundleIds/{app_id}/appGroups" + q(limit=50),
                    expect=(200, 403, 404))
        if s == 200:
            print("app group assignments:",
                  [g["attributes"].get("identifier") for g in d.get("data", [])])
        else:
            print(f"app group assignments unreadable: HTTP {s}")
    cert = distribution_cert()
    print("distribution certificate:", cert["id"],
          cert["attributes"].get("name"),
          "expires", cert["attributes"].get("expirationDate"))
    for name in (APP_PROFILE, WIDGET_PROFILE):
        p = find_profile(name)
        print(f"profile '{name}':", "PRESENT " + p["id"] if p else "ABSENT")

def ensure_bundle_id(identifier, name):
    bids = bundle_ids()
    if identifier in bids:
        print(f"bundle ID {identifier}: already present")
        return bids[identifier]
    s, d = call("POST", "/v1/bundleIds", body={
        "data": {"type": "bundleIds",
                 "attributes": {"identifier": identifier,
                                "name": name, "platform": "IOS"}}})
    print(f"bundle ID {identifier}: CREATED {d['data']['id']}")
    return d["data"]

def ensure_app_group():
    s, d = call("GET", "/v1/appGroups" + q(limit=200), expect=(200, 403, 404))
    if s != 200:
        raise SystemExit(
            f"APP_GROUPS_NOT_API_MANAGEABLE: listing app groups returned "
            f"HTTP {s}; the group must be created in the developer portal")
    for g in d.get("data", []):
        if g["attributes"].get("identifier") == APP_GROUP:
            print(f"app group {APP_GROUP}: already present")
            return g
    s, d = call("POST", "/v1/appGroups", body={
        "data": {"type": "appGroups",
                 "attributes": {"identifier": APP_GROUP,
                                "name": "RunWear"}}},
        expect=(200, 201, 403, 404, 409))
    if s not in (200, 201):
        raise SystemExit(
            f"APP_GROUPS_NOT_API_MANAGEABLE: creating the app group "
            f"returned HTTP {s}; the group must be created in the "
            f"developer portal")
    print(f"app group {APP_GROUP}: CREATED {d['data']['id']}")
    return d["data"]

def ensure_capability(bundle, group):
    bid = bundle["id"]
    caps = list_all(f"/v1/bundleIds/{bid}/bundleIdCapabilities")
    if any(c["attributes"].get("capabilityType") == "APP_GROUPS"
           for c in caps):
        print(f"{bundle['attributes']['identifier']}: APP_GROUPS already on")
    else:
        call("POST", "/v1/bundleIdCapabilities", body={
            "data": {"type": "bundleIdCapabilities",
                     "attributes": {"capabilityType": "APP_GROUPS"},
                     "relationships": {
                         "bundleId": {"data": {"type": "bundleIds",
                                               "id": bid}}}}})
        print(f"{bundle['attributes']['identifier']}: APP_GROUPS enabled")
    s, d = call("GET", f"/v1/bundleIds/{bid}/appGroups" + q(limit=50))
    assigned = {g["id"] for g in d.get("data", [])}
    if group["id"] in assigned:
        print(f"{bundle['attributes']['identifier']}: group already assigned")
    else:
        call("POST", f"/v1/bundleIds/{bid}/relationships/appGroups", body={
            "data": [{"type": "appGroups", "id": group["id"]}]})
        print(f"{bundle['attributes']['identifier']}: group assigned")

def ensure_profile(name, bundle, cert, recreate=False):
    existing = find_profile(name)
    if existing and not recreate:
        print(f"profile '{name}': already present")
        return existing
    if existing and recreate:
        call("DELETE", f"/v1/profiles/{existing['id']}", expect=(204,))
        print(f"profile '{name}': deleted for re-issue")
    s, d = call("POST", "/v1/profiles", body={
        "data": {"type": "profiles",
                 "attributes": {"name": name,
                                "profileType": "IOS_APP_STORE"},
                 "relationships": {
                     "bundleId": {"data": {"type": "bundleIds",
                                           "id": bundle["id"]}},
                     "certificates": {"data": [{"type": "certificates",
                                                "id": cert["id"]}]}}}})
    print(f"profile '{name}': CREATED {d['data']['id']}")
    return d["data"]

def apply():
    widget = ensure_bundle_id(WIDGET_BUNDLE, "RunWear Widget")
    bids = bundle_ids()
    app = bids[APP_BUNDLE]
    group = ensure_app_group()
    ensure_capability(app, group)
    ensure_capability(widget, group)
    cert = distribution_cert()
    ensure_profile(WIDGET_PROFILE, widget, cert)
    # The app profile predates the group assignment: re-issue it
    # so the archived app's declared application-groups
    # entitlement is inside its profile.
    ensure_profile(APP_PROFILE, app, cert, recreate=True)
    print("PROVISIONING_COMPLETE")

if __name__ == "__main__":
    task = sys.argv[1] if len(sys.argv) > 1 else "probe"
    {"probe": probe, "apply": apply}[task]()
