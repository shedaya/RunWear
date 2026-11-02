"""App Store resubmission packet prep for RunWear iOS 1.0 (ASC app
6827973343) — the Make-It-Me-OFF resubmission answering the
Guideline 2.1 information request.

Modes:
  read  — print the full current packet state: version state,
          attached build, en-US localization (description
          verbatim), review detail (notes verbatim), screenshot
          set composition, the Make It Me subscription states, and
          every review submission on the app. Changes nothing.
  apply — attach the target build (argv[2], default 16) to the
          1.0 version, set the en-US description from
          packet_description.txt and the review notes from
          packet_notes.txt (both beside this script), then print
          the same readback as `read`. Creates NO review
          submission and submits NOTHING.

Idempotent: apply skips any field already at its target value.

Usage: python3 asc_packet.py read|apply [build]
"""
import os, sys, urllib.parse
from asc_common import call

APP_ID = "6827973343"
VERSION_ID = "505f7bab-da80-49c0-9d29-a8d51987a26a"  # iOS "1.0"
MIM_GROUP_ID = "22531902"
HERE = os.path.dirname(os.path.abspath(__file__))

def q(**kw):
    return "?" + urllib.parse.urlencode(kw)

def readback():
    s, d = call("GET", f"/v1/appStoreVersions/{VERSION_ID}")
    a = d["data"]["attributes"]
    print("VERSION:", d["data"]["id"], a.get("versionString"),
          "state:", a.get("appStoreState"), "releaseType:", a.get("releaseType"))

    s, d = call("GET", f"/v1/appStoreVersions/{VERSION_ID}/build")
    b = d.get("data")
    if b:
        ba = b["attributes"]
        print("ATTACHED BUILD:", b["id"], "version:", ba.get("version"),
              "processingState:", ba.get("processingState"),
              "expired:", ba.get("expired"),
              "usesNonExemptEncryption:", ba.get("usesNonExemptEncryption"))
    else:
        print("ATTACHED BUILD: none")

    s, d = call("GET", f"/v1/appStoreVersions/{VERSION_ID}/appStoreVersionLocalizations"
                + q(limit=50))
    locs = d.get("data", [])
    en = None
    for loc in locs:
        la = loc["attributes"]
        print("LOCALIZATION:", loc["id"], la.get("locale"))
        if la.get("locale") == "en-US":
            en = loc
    if en:
        ea = en["attributes"]
        print("--- en-US description (verbatim) ---")
        print(ea.get("description"))
        print("--- end description ---")
        print("keywords:", ea.get("keywords"))
        print("supportUrl:", ea.get("supportUrl"),
              "marketingUrl:", ea.get("marketingUrl"))
        print("promotionalText:", ea.get("promotionalText"))
        s, d = call("GET", f"/v1/appStoreVersionLocalizations/{en['id']}/appScreenshotSets"
                    + q(limit=50))
        for ss in d.get("data", []):
            sa = ss["attributes"]
            s2, d2 = call("GET", f"/v1/appScreenshotSets/{ss['id']}/appScreenshots"
                          + q(limit=100))
            print("SCREENSHOT SET:", sa.get("screenshotDisplayType"),
                  "count:", len(d2.get("data", [])))

    try:
        s, d = call("GET", f"/v1/appStoreVersions/{VERSION_ID}/appStoreReviewDetail")
        rd = d.get("data", {})
        ra = rd.get("attributes", {})
        print("REVIEW DETAIL:", rd.get("id"))
        print("contact:", ra.get("contactFirstName"), ra.get("contactLastName"),
              ra.get("contactPhone"), ra.get("contactEmail"))
        print("demoAccountRequired:", ra.get("demoAccountRequired"),
              "demoAccountName set:", bool(ra.get("demoAccountName")))
        print("--- review notes (verbatim) ---")
        print(ra.get("notes"))
        print("--- end notes ---")
        s, d = call("GET", f"/v1/appStoreReviewDetails/{rd['id']}/attachments"
                    + q(limit=50)) if rd.get("id") else (200, {"data": []})
        print("review detail attachments:",
              [(x["id"], x["attributes"].get("fileName")) for x in d.get("data", [])])
    except SystemExit:
        print("REVIEW DETAIL: none (GET failed)")

    s, d = call("GET", f"/v1/subscriptionGroups/{MIM_GROUP_ID}/subscriptions"
                + q(limit=50))
    for sub in d.get("data", []):
        sa = sub["attributes"]
        print("MIM SUBSCRIPTION:", sa.get("productId"), "state:", sa.get("state"))

    s, d = call("GET", "/v1/reviewSubmissions" + q(**{"filter[app]": APP_ID}, limit=50))
    for rs in d.get("data", []):
        ra = rs["attributes"]
        print("REVIEW SUBMISSION:", rs["id"], "state:", ra.get("state"),
              "submittedDate:", ra.get("submittedDate"))
    return en

def find_build(build_number):
    s, d = call("GET", "/v1/builds" + q(**{"filter[app]": APP_ID,
                                           "filter[version]": build_number}, limit=5))
    for b in d.get("data", []):
        if b["attributes"].get("version") == build_number:
            return b
    return None

def apply(build_number):
    target = find_build(build_number)
    if not target:
        print(f"TARGET BUILD {build_number} NOT FOUND — aborting apply")
        sys.exit(1)
    ta = target["attributes"]
    print("target build:", target["id"], "processingState:", ta.get("processingState"))
    if ta.get("processingState") != "VALID":
        print("TARGET BUILD NOT VALID — aborting apply")
        sys.exit(1)

    s, d = call("GET", f"/v1/appStoreVersions/{VERSION_ID}/build")
    current = (d.get("data") or {}).get("id")
    if current == target["id"]:
        print("build already attached — skipping build PATCH")
    else:
        s, d = call("PATCH", f"/v1/appStoreVersions/{VERSION_ID}/relationships/build",
                    {"data": {"type": "builds", "id": target["id"]}},
                    expect=(200, 204))
        print("build PATCH ->", s)

    en = None
    s, d = call("GET", f"/v1/appStoreVersions/{VERSION_ID}/appStoreVersionLocalizations"
                + q(limit=50))
    for loc in d.get("data", []):
        if loc["attributes"].get("locale") == "en-US":
            en = loc
    desc = open(os.path.join(HERE, "packet_description.txt")).read().strip()
    if en["attributes"].get("description", "").strip() == desc:
        print("description already at target — skipping localization PATCH")
    else:
        s, d = call("PATCH", f"/v1/appStoreVersionLocalizations/{en['id']}",
                    {"data": {"type": "appStoreVersionLocalizations",
                              "id": en["id"],
                              "attributes": {"description": desc}}})
        print("description PATCH ->", s)

    notes = open(os.path.join(HERE, "packet_notes.txt")).read().strip()
    s, d = call("GET", f"/v1/appStoreVersions/{VERSION_ID}/appStoreReviewDetail")
    rd = d["data"]
    if (rd["attributes"].get("notes") or "").strip() == notes:
        print("notes already at target — skipping review detail PATCH")
    else:
        s, d = call("PATCH", f"/v1/appStoreReviewDetails/{rd['id']}",
                    {"data": {"type": "appStoreReviewDetails",
                              "id": rd["id"],
                              "attributes": {"notes": notes}}})
        print("notes PATCH ->", s)

    print("=== FINAL READBACK ===")
    readback()

if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "read"
    build = sys.argv[2] if len(sys.argv) > 2 else "16"
    if mode == "read":
        readback()
    elif mode == "apply":
        apply(build)
    else:
        print("unknown mode", mode); sys.exit(2)
