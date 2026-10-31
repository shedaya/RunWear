"""TestFlight tester administration for Alexandra Hedaya
(amelloul.ajc@gmail.com) across all three Uptime apps — she is being
added for Apple Watch testing; the watch companions ship inside the
iPhone builds, so internal-group membership per app is the whole job.

Modes:
  probe  read-only: her betaTester record, team user, pending
         invitation; every app's beta groups, their builds, and
         whether she is already in each.
  add    if she is neither a team user nor an invited one, send the
         Users and Access invitation (minimal role, all apps
         visible). Then, if a betaTester record exists, add her to
         each app's internal group that serves the current build
         (JetGuide 36 — never a diagnostics-only 37 group; RunWear 13
         in "RunWear Internal"; AccliMate 15) and read membership back.

Usage: python3 asc_testers.py [probe|add]"""
import sys, urllib.parse
from asc_common import call

EMAIL = "amelloul.ajc@gmail.com"
FIRST = "Alexandra"
LAST = "Hedaya"
APPS = [("JetGuide", "6826923693", "36"),
        ("RunWear", "6827973343", "13"),
        ("AccliMate", "6828337210", "15")]
RUNWEAR_INTERNAL_GROUP = "c384f9db-61ac-4644-bbc8-da89ad471617"


def q(**kw):
    return "?" + urllib.parse.urlencode(kw)


def get_all(path):
    """Follow paging links, merging data arrays."""
    out, url = [], path
    for _ in range(6):
        s, d = call("GET", url)
        out.extend(d.get("data", []))
        nxt = (d.get("links") or {}).get("next")
        if not nxt:
            break
        url = nxt
    return out


def find_tester():
    s, d = call("GET", "/v1/betaTesters" + q(**{"filter[email]": EMAIL}))
    rows = d.get("data", [])
    return rows[0] if rows else None


def find_user():
    for u in get_all("/v1/users" + q(limit=200)):
        if (u["attributes"].get("username") or "").lower() == EMAIL:
            return u
    return None


def find_invitation():
    for inv in get_all("/v1/userInvitations" + q(limit=200)):
        if (inv["attributes"].get("email") or "").lower() == EMAIL:
            return inv
    return None


def group_builds(gid):
    # NOTE: this endpoint rejects `include` (400 PARAMETER_ERROR.ILLEGAL);
    # the build number is attributes.version on the build itself, which
    # is all the target matching needs.
    s, d = call("GET", f"/v1/betaGroups/{gid}/builds" + q(limit=200))
    return [(b["attributes"].get("version"),
             b["attributes"].get("processingState"))
            for b in d.get("data", [])]


def group_tester_ids(gid):
    rows = get_all(f"/v1/betaGroups/{gid}/betaTesters" + q(limit=200))
    return {t["id"] for t in rows}


def app_groups(app_id):
    return get_all(f"/v1/apps/{app_id}/betaGroups" + q(limit=200))


def probe():
    t = find_tester()
    print("betaTester:", None if not t else
          (t["id"], t["attributes"].get("inviteType"),
           t["attributes"].get("state"),
           t["attributes"].get("email")))
    u = find_user()
    print("team user:", None if not u else
          (u["id"], u["attributes"].get("username"),
           u["attributes"].get("roles")))
    inv = find_invitation()
    print("pending invitation:", None if not inv else
          (inv["id"], inv["attributes"].get("email"),
           inv["attributes"].get("expirationDate"),
           inv["attributes"].get("roles")))
    for name, app_id, want_build in APPS:
        print(f"== {name} ({app_id}), target build {want_build}")
        for g in app_groups(app_id):
            ga = g["attributes"]
            builds = group_builds(g["id"])
            tids = group_tester_ids(g["id"])
            member = bool(t) and t["id"] in tids
            print(f"  group {g['id']} name={ga.get('name')!r} "
                  f"internal={ga.get('isInternalGroup')} "
                  f"testers={len(tids)} alexandra_member={member}")
            print(f"    builds: {builds}")


def target_groups(app_id, want_build):
    """Internal groups serving the target build. RunWear: pin the
    known internal group. JetGuide: any internal group containing
    build 36 (a group serving only the diagnostics build 37 is
    excluded by construction)."""
    out = []
    for g in app_groups(app_id):
        if not g["attributes"].get("isInternalGroup"):
            continue
        if app_id == "6827973343" and g["id"] != RUNWEAR_INTERNAL_GROUP:
            continue
        builds = group_builds(g["id"])
        if any(bn == want_build for (bn, _s) in builds):
            out.append(g)
    return out


def add():
    u = find_user()
    inv = find_invitation()
    if u:
        print("team user already present:", u["id"],
              u["attributes"].get("roles"))
    elif inv:
        print("invitation already pending:", inv["id"],
              "expires", inv["attributes"].get("expirationDate"))
    else:
        body = {"data": {"type": "userInvitations",
                         "attributes": {"email": EMAIL,
                                        "firstName": FIRST,
                                        "lastName": LAST,
                                        "roles": ["MARKETING"],
                                        "allAppsVisible": True}}}
        s, d = call("POST", "/v1/userInvitations", body)
        print("INVITATION_CREATED status", s,
              "id", d.get("data", {}).get("id"),
              "expires",
              d.get("data", {}).get("attributes", {}).get("expirationDate"))
    t = find_tester()
    if not t:
        print("TESTER_RECORD_PENDING_ACCEPTANCE: no betaTester record "
              "exists yet — group adds must wait until Alexandra "
              "accepts the team invitation.")
        return
    print("betaTester:", t["id"], t["attributes"].get("inviteType"),
          t["attributes"].get("state"))
    for name, app_id, want_build in APPS:
        groups = target_groups(app_id, want_build)
        if not groups:
            print(f"{name}: NO_TARGET_GROUP_FOUND for build {want_build}")
            continue
        for g in groups:
            tids = group_tester_ids(g["id"])
            if t["id"] in tids:
                print(f"{name}: already a member of "
                      f"{g['attributes'].get('name')!r} ({g['id']})")
                continue
            body = {"data": [{"type": "betaTesters", "id": t["id"]}]}
            try:
                s, d = call(
                    "POST",
                    f"/v1/betaGroups/{g['id']}/relationships/betaTesters",
                    body, expect=(204,))
                print(f"{name}: ADDED to {g['attributes'].get('name')!r} "
                      f"({g['id']}) status {s}")
            except SystemExit:
                print(f"{name}: ADD_FAILED for group {g['id']} "
                      f"(API error printed above)")
            tids = group_tester_ids(g["id"])
            print(f"{name}: readback member={t['id'] in tids} "
                  f"in group {g['id']}")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "probe"
    (add if mode == "add" else probe)()
