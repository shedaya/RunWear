"""Create RunWear's "Make It Me" subscription products in App Store Connect.

RW-14 billing setup (Sol's tiered model, Oct 31 2026): personalization
("Make it me") is a subscription at $0.99/month and $9.99/year (US base
price; Apple's standard price matrix equalizes the other territories).
Free users keep 5 on-demand generations, enforced server-side — no
product needed for the free tier.

What this creates (idempotently — safe to re-run; existing pieces are
found by reference name / product id and reused):
  1. Subscription group, reference name "Make It Me"
  2. Group localization (en-US): name "Make It Me"
  3. Two auto-renewable subscriptions in the group, level 1:
       rw_makeitme_monthly  ONE_MONTH  $0.99
       rw_makeitme_annual   ONE_YEAR   $9.99
  4. Subscription localization (en-US) for each
  5. US (USA) price for each, from the subscription's own price points

It NEVER submits anything for review and never touches the app's
version or review submission. Products are expected to sit in
MISSING_METADATA until review screenshots are supplied at submission
time (that is normal — see the JetGuide product setup).

Usage: python3 asc_products.py
"""
import json
import sys
import urllib.parse
import urllib.request
import urllib.error

from asc_common import token, BASE

APP_ID = "6827973343"
GROUP_REFERENCE_NAME = "Make It Me"
GROUP_DISPLAY_NAME = "Make It Me"
SUB_LOCALE = "en-US"
SUB_DISPLAY_NAME = "Make It Me"
# ASC caps the subscription localization description at 55 characters.
SUB_DESCRIPTION = "See yourself in the outfit. Your heroes, instantly."
BASE_TERRITORY = "USA"

PRODUCTS = [
    {"key": "monthly", "name": "Make It Me Monthly",
     "productId": "rw_makeitme_monthly", "period": "ONE_MONTH", "price": 0.99},
    {"key": "annual", "name": "Make It Me Annual",
     "productId": "rw_makeitme_annual", "period": "ONE_YEAR", "price": 9.99},
]


def q(**params):
    return "?" + urllib.parse.urlencode(params)


def call_tolerant(method, path, body=None):
    """Like asc_common.call but returns (status, parsed) instead of exiting."""
    url = path if path.startswith("http") else BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token())
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:400].decode("utf-8", "replace")}
        return e.code, parsed


def must(method, path, body=None, expect=(200, 201, 204)):
    status, parsed = call_tolerant(method, path, body)
    if status not in expect:
        print(f"!! {method} {path} -> {status}", file=sys.stderr)
        print(json.dumps(parsed, indent=1)[:3000], file=sys.stderr)
        raise SystemExit(f"ASC API error {status} on {method} {path}")
    return parsed


def items(parsed):
    return parsed.get("data", []) if isinstance(parsed, dict) else []


# ---------------------------------------------------------------- group
def find_or_create_group():
    _, d = call_tolerant("GET", f"/v1/apps/{APP_ID}/subscriptionGroups" + q(limit=200))
    for g in items(d):
        if g.get("attributes", {}).get("referenceName") == GROUP_REFERENCE_NAME:
            print(f"group: exists id={g['id']}")
            return g["id"]
    body = {"data": {"type": "subscriptionGroups",
                     "attributes": {"referenceName": GROUP_REFERENCE_NAME},
                     "relationships": {"app": {"data": {"type": "apps", "id": APP_ID}}}}}
    d = must("POST", "/v1/subscriptionGroups", body)
    gid = d["data"]["id"]
    print(f"group: CREATED id={gid}")
    return gid


def ensure_group_localization(gid):
    _, d = call_tolerant("GET", f"/v1/subscriptionGroups/{gid}/subscriptionGroupLocalizations" + q(limit=50))
    for loc in items(d):
        if loc.get("attributes", {}).get("locale") == SUB_LOCALE:
            print(f"group localization {SUB_LOCALE}: exists id={loc['id']}")
            return loc["id"]
    body = {"data": {"type": "subscriptionGroupLocalizations",
                     "attributes": {"locale": SUB_LOCALE, "name": GROUP_DISPLAY_NAME},
                     "relationships": {"subscriptionGroup": {"data": {"type": "subscriptionGroups", "id": gid}}}}}
    d = must("POST", "/v1/subscriptionGroupLocalizations", body)
    print(f"group localization {SUB_LOCALE}: CREATED id={d['data']['id']}")
    return d["data"]["id"]


# ---------------------------------------------------------- subscriptions
def find_or_create_subscription(gid, spec):
    _, d = call_tolerant("GET", f"/v1/subscriptionGroups/{gid}/subscriptions" + q(limit=200))
    for s in items(d):
        if s.get("attributes", {}).get("productId") == spec["productId"]:
            print(f"subscription {spec['productId']}: exists id={s['id']} "
                  f"state={s.get('attributes', {}).get('state')}")
            return s["id"]
    body = {"data": {"type": "subscriptions",
                     "attributes": {"name": spec["name"],
                                    "productId": spec["productId"],
                                    "subscriptionPeriod": spec["period"],
                                    "groupLevel": 1},
                     "relationships": {"group": {"data": {"type": "subscriptionGroups", "id": gid}}}}}
    d = must("POST", "/v1/subscriptions", body)
    sid = d["data"]["id"]
    print(f"subscription {spec['productId']}: CREATED id={sid} "
          f"state={d['data'].get('attributes', {}).get('state')}")
    return sid


def ensure_subscription_localization(sid, product_id):
    _, d = call_tolerant("GET", f"/v1/subscriptions/{sid}/subscriptionLocalizations" + q(limit=50))
    for loc in items(d):
        if loc.get("attributes", {}).get("locale") == SUB_LOCALE:
            print(f"  localization {SUB_LOCALE} ({product_id}): exists id={loc['id']}")
            return loc["id"]
    body = {"data": {"type": "subscriptionLocalizations",
                     "attributes": {"locale": SUB_LOCALE,
                                    "name": SUB_DISPLAY_NAME,
                                    "description": SUB_DESCRIPTION},
                     "relationships": {"subscription": {"data": {"type": "subscriptions", "id": sid}}}}}
    d = must("POST", "/v1/subscriptionLocalizations", body)
    print(f"  localization {SUB_LOCALE} ({product_id}): CREATED id={d['data']['id']}")
    return d["data"]["id"]


def find_price_point(sid, target):
    """Walk the subscription's USA price points until the target price."""
    path = f"/v1/subscriptions/{sid}/pricePoints" + q(**{"filter[territory]": BASE_TERRITORY}, limit=200)
    seen = 0
    while path:
        _, d = call_tolerant("GET", path)
        for pp in items(d):
            seen += 1
            cp = pp.get("attributes", {}).get("customerPrice")
            try:
                if cp is not None and abs(float(cp) - target) < 0.001:
                    return pp["id"], cp
            except (TypeError, ValueError):
                pass
        path = d.get("links", {}).get("next") if isinstance(d, dict) else None
    raise SystemExit(f"price point {target} not found for subscription {sid} "
                     f"(walked {seen} USA price points)")


def existing_price_territories(sid):
    status, d = call_tolerant("GET", f"/v1/subscriptions/{sid}/prices" + q(limit=200))
    if status != 200:
        return None  # endpoint shape unknown; caller falls back to POST-tolerant
    terrs = []
    for p in items(d):
        rel = p.get("relationships", {}).get("territory", {}).get("data", {})
        terrs.append(rel.get("id"))
    return terrs


def ensure_us_price(sid, spec):
    terrs = existing_price_territories(sid)
    if terrs and BASE_TERRITORY in terrs:
        print(f"  price ({spec['productId']}): USA price already set")
        return
    ppid, cp = find_price_point(sid, spec["price"])
    body = {"data": {"type": "subscriptionPrices",
                     "relationships": {
                         "subscription": {"data": {"type": "subscriptions", "id": sid}},
                         "subscriptionPricePoint": {"data": {"type": "subscriptionPricePoints", "id": ppid}},
                         "territory": {"data": {"type": "territories", "id": BASE_TERRITORY}}}}}
    status, d = call_tolerant("POST", "/v1/subscriptionPrices", body)
    if status in (200, 201):
        print(f"  price ({spec['productId']}): CREATED USA ${cp} pricepoint={ppid} price_id={d['data']['id']}")
    elif status == 409:
        print(f"  price ({spec['productId']}): already exists (409 on create)")
    else:
        print(f"!! price create ({spec['productId']}) -> {status}", file=sys.stderr)
        print(json.dumps(d, indent=1)[:2000], file=sys.stderr)
        raise SystemExit(f"price create failed for {spec['productId']}: {status}")


# ------------------------------------------------------------------ main
def main():
    print("== Make It Me subscription products: create/verify ==")
    gid = find_or_create_group()
    ensure_group_localization(gid)
    results = {}
    for spec in PRODUCTS:
        sid = find_or_create_subscription(gid, spec)
        ensure_subscription_localization(sid, spec["productId"])
        ensure_us_price(sid, spec)
        results[spec["key"]] = (sid, spec)

    print("\n== READBACK ==")
    _, d = call_tolerant("GET", f"/v1/subscriptionGroups/{gid}")
    attrs = d.get("data", {}).get("attributes", {})
    print(f"GROUP id={gid} referenceName={attrs.get('referenceName')}")
    for key, (sid, spec) in results.items():
        _, sd = call_tolerant("GET", f"/v1/subscriptions/{sid}")
        a = sd.get("data", {}).get("attributes", {})
        terrs = existing_price_territories(sid)
        print(f"SUB {key}: id={sid} productId={a.get('productId')} "
              f"name={a.get('name')} period={a.get('subscriptionPeriod')} "
              f"groupLevel={a.get('groupLevel')} state={a.get('state')} "
              f"priceTerritories={terrs}")
    print("== done ==")


if __name__ == "__main__":
    main()
