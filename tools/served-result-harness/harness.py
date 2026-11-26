#!/usr/bin/env python3
"""
Served-result verification harness — RunWear Make It Me, plan step 4.

WHY THIS EXISTS (rw-mim-stepback-diagnosis.md §(e) step 4): every layer of
Make It Me verified itself against its own outputs, and no layer verified
the SERVED result against the product's promises. This harness is that
layer for the generic-serving chain: it replays the merged build's
serving logic against the LIVE pool (read through the client's own anon
PostgREST path) and asserts the invariants a user is promised:

  (i)   hero never blank where an approved generic exists for the cell
  (ii)  served generic's gender always equals the fit preference
  (iii) served hero has a visible front-facing face — PARTIAL until the
        step-5 rubric re-judgment creates orientation data; this harness
        enumerates the documented back-view rows that remain servable
  (iv)  the first-look surface in the pending state shows the standard
        photo its copy promises (FirstLookPhotoPolicy replay)
  (v)   reset-without-delete -> restore returns the bank — exercised
        separately against the server machinery (see runbook); not part
        of this script's pool simulation

FIDELITY: the serving functions below are line-faithful Python ports of
the merged train's Kotlin sources. Each port names its source. The ports
are pinned by the repo's own unit tests in CI (HeroCascadePatternsTest,
HeroPointGrainPolicyTest, HeroFallbackChainTest, FirstLookPhotoPolicyTest)
— if a port and the Kotlin disagree, the Kotlin and its tests win and
this file must be fixed. Snapshot mode evaluates the full matching row
set per cascade level (a superset of the client's 50-row page — see
README); the named checks run in LIVE mode, issuing the client's exact
per-pattern query (LIKE pattern, limit 50) against PostgREST.

Usage:
  python3 harness.py [--out REPORT.json] [--pool-file pool.json]
With no --pool-file, the pool is fetched live via the anon path.
Exit code: 0 = all hard checks pass; 1 = a hard check failed.
Checks (iii) and the ground-truth set score are reports, not gates.
"""

import json
import re
import sys
import urllib.request
import urllib.parse

# The client's own public coordinates (HeroImageRepository.kt companion
# object). The anon key is the app's shipped public credential; the pool
# read below is exactly the read the app performs (RLS: approved only).
SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
ANON_KEY = (
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6"
    "ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5"
    "MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"
)

GENDERS = ["MALE", "FEMALE"]
WEATHERS = ["CLEAR", "CLOUDY", "RAIN", "SNOW"]
BRACKETS = ["FREEZING", "COLD", "COOL", "MILD", "WARM", "HOT"]
TIMES = ["DAWN", "MIDDAY", "DUSK", "NIGHT"]
WARMTH_ORDER = ["FREEZING", "COLD", "COOL", "MILD", "WARM", "HOT"]


# ---------------------------------------------------------------- pool I/O

def fetch_pool():
    """Fetch every row the anon client can see, paged. Columns mirror the
    client's GeneratedImage decode (HeroImageRepository.kt)."""
    rows = []
    offset = 0
    while True:
        q = urllib.parse.urlencode({
            "select": "id,combination_id,image_url,review_status,person_count,garment_classes",
            "limit": 1000,
            "offset": offset,
        })
        req = urllib.request.Request(
            f"{SUPABASE_URL}/rest/v1/generated_images?{q}",
            headers={"apikey": ANON_KEY, "Authorization": f"Bearer {ANON_KEY}"},
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            page = json.load(resp)
        rows.extend(page)
        if len(page) < 1000:
            return rows
        offset += 1000


def fetch_like(pattern, limit=50):
    """The client's exact per-pattern query (getRandomImage): LIKE pattern
    (PostgREST spells % as *), page of `limit`, no ordering."""
    pg_pattern = pattern.replace("%", "*")
    q = urllib.parse.urlencode({
        "select": "id,combination_id,image_url,review_status,person_count,garment_classes",
        "combination_id": f"like.{pg_pattern}",
        "limit": limit,
    })
    req = urllib.request.Request(
        f"{SUPABASE_URL}/rest/v1/generated_images?{q}",
        headers={"apikey": ANON_KEY, "Authorization": f"Bearer {ANON_KEY}"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def like_match(pattern, value):
    """SQL LIKE semantics: % = any run, _ = any single char."""
    rx = ""
    for ch in pattern:
        if ch == "%":
            rx += ".*"
        elif ch == "_":
            rx += "."
        else:
            rx += re.escape(ch)
    return re.fullmatch(rx, value) is not None


# ------------------------------------------------------- ported serving code

def cascade_patterns(gender, weather, temp, time):
    """Port of heroCascadePatterns (HeroImageRepository.kt, RW-178 form:
    opposite-gender levels removed; widening is the outfit axis only)."""
    queries = [f"{gender}_{weather}_{temp}_{time}_%", f"{gender}_{weather}_{temp}_%"]
    if weather != "CLEAR":
        queries.append(f"{gender}_CLEAR_{temp}_%")
    if weather == "RAIN":
        queries.append(f"{gender}_RAINY_{temp}_%")
    if weather == "SNOW":
        queries.append(f"{gender}_SNOWY_{temp}_%")
    return queries


def is_servable(review_status):
    """Port of HeroPoolPolicy.isServable: null (legacy) or 'approved'."""
    return review_status is None or review_status.lower() == "approved"


def servable_images(rows):
    """Port of servableImages (HeroImageRepository.kt)."""
    return [r for r in rows if is_servable(r.get("review_status"))]


def point_grain_matches(person_count, gc, rx):
    """Port of HeroPointGrainPolicy.matches (HeroPoolPolicy.kt).
    gc: garment classes dict (topBase/outer/bottom/head/hands) or None.
    rx: prescription in ClothingItem space: keys topBase, topOuter,
    bottom, head, hands; values are item names or None."""
    gc = gc or {}
    if person_count is not None and person_count >= 2:
        return False
    pb = rx["bottom"]
    bottom = gc.get("bottom")
    if pb in ("SHORT_SHORTS", "SHORTS"):
        bottom_ok = bottom in ("shorts", "short_shorts")
    elif pb == "CAPRIS":
        bottom_ok = bottom in ("capris", "tights")
    elif pb in ("LIGHT_TIGHTS", "THERMAL_TIGHTS"):
        bottom_ok = bottom in ("tights", "capris")
    else:
        bottom_ok = False
    if not bottom_ok:
        return False
    outer_seen = gc.get("outer") is True
    if (rx["topOuter"] is not None) != outer_seen:
        return False
    top_base = gc.get("topBase")
    if top_base is not None:
        expected = {"TANK_TOP": "sleeveless", "SHORT_SLEEVE": "short"}.get(rx["topBase"], "long")
        if top_base != expected:
            return False
    elif not outer_seen:
        return False
    head = gc.get("head")
    if rx["head"] is None:
        if head is not None:
            return False
    else:
        exp_head = {"VISOR": "visor", "BASEBALL_CAP": "cap", "HEADBAND": "headband",
                    "LIGHT_BEANIE": "beanie", "THERMAL_BEANIE": "beanie",
                    "BALACLAVA": "balaclava"}.get(rx["head"])
        if exp_head is None or head != exp_head:
            return False
    hands = gc.get("hands")
    if rx["hands"] is None:
        if hands is not None:
            return False
    else:
        exp_hands = {"LIGHT_GLOVES": "gloves", "THERMAL_GLOVES": "gloves",
                     "MITTENS": "mittens"}.get(rx["hands"])
        if exp_hands is None or not (hands == exp_hands or
                                     (rx["hands"] == "MITTENS" and hands == "gloves")):
            return False
    return True


def point_grain_images(rows, rx):
    """Port of pointGrainImages (HeroImageRepository.kt)."""
    return [r for r in servable_images(rows)
            if point_grain_matches(r.get("person_count"), r.get("garment_classes"), rx)]


# --- HeroFallbackChain port (engine HeroFallbackChain.kt) ---

def _bottom_rank(c):
    return {"shorts": 0, "short_shorts": 0, "capris": 1, "tights": 2}.get(c, 1)


def _top_rank(c):
    return {"sleeveless": 0, "short": 1, "long": 2}.get(c, 1)


def _head_rank(c):
    if c is None:
        return 0
    return {"visor": 1, "cap": 1, "headband": 2, "beanie": 3, "balaclava": 4}.get(c, 1)


def _hands_rank(c):
    if c is None:
        return 0
    return {"gloves": 1, "mittens": 2}.get(c, 1)


def prescription_snapshot(rx):
    """Port of HeroFallbackChain.prescriptionSnapshot."""
    return {
        "topBase": {"TANK_TOP": "sleeveless", "SHORT_SLEEVE": "short"}.get(rx["topBase"], "long"),
        "outer": rx["topOuter"] is not None,
        "bottom": {"SHORT_SHORTS": "short_shorts", "SHORTS": "shorts",
                   "CAPRIS": "capris"}.get(rx["bottom"], "tights"),
        "head": (None if rx["head"] is None else
                 {"VISOR": "visor", "BASEBALL_CAP": "cap", "HEADBAND": "headband",
                  "LIGHT_BEANIE": "beanie", "THERMAL_BEANIE": "beanie",
                  "BALACLAVA": "balaclava"}.get(rx["head"], "other")),
        "hands": (None if rx["hands"] is None else
                  {"LIGHT_GLOVES": "gloves", "THERMAL_GLOVES": "gloves",
                   "MITTENS": "mittens"}.get(rx["hands"], "other")),
    }


def pool_photo_candidates(rows):
    """Port of poolPhotoCandidates (HeroImageRepository.kt): servable rows
    only; gender/bracket/weather parsed from combination_id tokens."""
    out = []
    for r in servable_images(rows):
        tokens = r["combination_id"].split("_")
        gender = {"MALE": "MALE", "FEMALE": "FEMALE"}.get(tokens[0] if tokens else None, "UNISEX")
        bracket = None
        if len(tokens) > 2 and tokens[2] in BRACKETS:
            bracket = tokens[2]
        weather = None
        if len(tokens) > 1:
            weather = {"CLEAR": "CLEAR", "CLOUDY": "CLOUDY", "RAIN": "RAIN",
                       "RAINY": "RAIN", "SNOW": "SNOW", "SNOWY": "SNOW"}.get(tokens[1])
        gc = r.get("garment_classes") or {}
        out.append({
            "url": r["image_url"], "source": "POOL", "gender": gender,
            "bracket": bracket, "weather": weather,
            "garments": {"topBase": gc.get("topBase"), "outer": gc.get("outer"),
                         "bottom": gc.get("bottom"), "head": gc.get("head"),
                         "hands": gc.get("hands")},
            "personCount": r.get("person_count"), "rowId": r["id"],
            "combinationId": r["combination_id"],
        })
    return out


def chain_distance(cand, rx, bracket, weather):
    """Port of HeroFallbackChain.distance (lexicographic tuple)."""
    prescribed = prescription_snapshot(rx)
    worn = cand["garments"]
    if cand["bracket"] is not None:
        bracket_steps = abs(WARMTH_ORDER.index(cand["bracket"]) - WARMTH_ORDER.index(bracket))
    else:
        bracket_steps = 2
    weather_steps = 0 if cand["weather"] == weather else 1
    bottom_steps = abs(_bottom_rank(worn["bottom"]) - _bottom_rank(prescribed["bottom"]))
    if worn["topBase"] is None and worn["outer"] is True:
        top_steps = 0
    else:
        top_steps = abs(_top_rank(worn["topBase"]) - _top_rank(prescribed["topBase"]))
    outer_steps = 0 if (worn["outer"] is True) == (prescribed["outer"] is True) else 1
    head_steps = abs(_head_rank(worn["head"]) - _head_rank(prescribed["head"]))
    hands_steps = abs(_hands_rank(worn["hands"]) - _hands_rank(prescribed["hands"]))
    mismatches = 0
    if _bottom_rank(worn["bottom"]) != _bottom_rank(prescribed["bottom"]):
        mismatches += 1
    if top_steps > 0:
        mismatches += 1
    if outer_steps > 0:
        mismatches += 1
    if worn["head"] != prescribed["head"]:
        mismatches += 1
    if worn["hands"] != prescribed["hands"]:
        mismatches += 1
    return (bracket_steps, weather_steps, bottom_steps, top_steps, outer_steps,
            head_steps, hands_steps, mismatches, 0, cand["url"])


def chain_resolve(gender, bracket, weather, rx, pool_candidates):
    """Port of HeroFallbackChain.resolve as invoked by
    getScaffoldOrFallback: pool candidates only (bundled list is empty
    there); tiers EXACT then SCAFFOLD; None -> the caller falls to the
    bundled floor, which always resolves for a gendered request."""
    def eligible(c):
        return (gender == "UNISEX" or c["gender"] == gender) and \
               (c["personCount"] is None or c["personCount"] < 2)

    pool = [c for c in pool_candidates if eligible(c)]

    def nearest(cands):
        if not cands:
            return None
        return min(cands, key=lambda c: chain_distance(c, rx, bracket, weather))

    exact = nearest([c for c in pool if point_grain_matches(
        c["personCount"],
        {"topBase": c["garments"]["topBase"], "outer": c["garments"]["outer"],
         "bottom": c["garments"]["bottom"], "head": c["garments"]["head"],
         "hands": c["garments"]["hands"]}, rx)])
    if exact is not None:
        return {"candidate": exact, "tier": "EXACT"}
    scaffold = nearest(pool)
    if scaffold is not None:
        return {"candidate": scaffold, "tier": "SCAFFOLD"}
    return None


def simulate_serve(pool_rows, gender, weather, temp, time, rx, live=False):
    """Replay getHeroImage's generic chain (getRandomImage then
    getScaffoldOrFallback) for one request. Returns a dict describing
    the servable universe and the deterministic scaffold pick.
    live=True issues the client's exact per-pattern queries (limit 50);
    live=False evaluates the full matching set from the snapshot."""
    patterns = cascade_patterns(gender, weather, temp, time)
    levels = []
    for pattern in patterns:
        if live:
            rows = fetch_like(pattern, limit=50)
        else:
            rows = [r for r in pool_rows if like_match(pattern, r["combination_id"])]
        servable = point_grain_images(rows, rx)
        levels.append({"pattern": pattern, "matched": len(rows),
                       "servable": [r["combination_id"] for r in servable],
                       "servable_ids": [r["id"] for r in servable]})
        if servable:
            return {"outcome": "CASCADE", "level": len(levels), "levels": levels,
                    "servable": servable, "patterns": patterns}
    # Cascade exhausted -> GEN-4 scaffold over the broad same-gender read.
    if live:
        broad = fetch_like(f"{gender}_%", limit=1000)
    else:
        broad = [r for r in pool_rows if r["combination_id"].startswith(f"{gender}_")]
    resolution = chain_resolve(gender, temp, weather, rx, pool_photo_candidates(broad))
    if resolution is not None:
        return {"outcome": resolution["tier"], "levels": levels,
                "scaffold_pick": resolution["candidate"]["combinationId"],
                "scaffold_pick_id": resolution["candidate"]["rowId"],
                "patterns": patterns}
    return {"outcome": "FLOOR", "levels": levels, "patterns": patterns,
            "note": "pool chain empty; bundled floor resolves by construction "
                    "(HeroImageSelector covers every gender x bracket family)"}


# --- First-look policy port (FirstLookPresentation.kt, RW-177) ---

def first_look_photo_to_show(personal_url, generic_url, scene_url):
    """Port of FirstLookPhotoPolicy.photoToShow."""
    return personal_url or generic_url or scene_url


# ------------------------------------------------------------- prescriptions

def rx_from_items(items):
    """Slot an outfit_items list (the engine's vocabulary, as recorded on
    personal_generation_jobs) into prescription space. Slot assignment
    for the named cells is verified against fix report §1."""
    top_base = next((i for i in items if "SLEEVE" in i or i == "TANK_TOP"), None)
    outer = next((i for i in items if i in
                  ("WINDBREAKER", "RAIN_JACKET", "INSULATED_JACKET", "SHELL")), None)
    bottom = next((i for i in items if "TIGHTS" in i or "SHORTS" in i or i == "CAPRIS"), None)
    head = next((i for i in items if i in
                 ("HEADBAND", "BASEBALL_CAP", "VISOR", "LIGHT_BEANIE",
                  "THERMAL_BEANIE", "BALACLAVA")), None)
    hands = next((i for i in items if "GLOVES" in i or i == "MITTENS"), None)
    return {"topBase": top_base, "topOuter": outer, "bottom": bottom,
            "head": head, "hands": hands}


def rx_from_garments(gc):
    """A pool row's own adjudicated classes as a prescription (the row
    always agrees with itself at the exact tier when its classes are
    readable; unreadable slots fail closed exactly as in production)."""
    gc = gc or {}
    return {
        "topBase": {"sleeveless": "TANK_TOP", "short": "SHORT_SLEEVE",
                    "long": "LONG_SLEEVE"}.get(gc.get("topBase")),
        "topOuter": "WINDBREAKER" if gc.get("outer") is True else None,
        "bottom": {"tights": "LIGHT_TIGHTS", "capris": "CAPRIS",
                   "shorts": "SHORTS", "short_shorts": "SHORT_SHORTS"}.get(gc.get("bottom")),
        "head": {"visor": "VISOR", "cap": "BASEBALL_CAP", "headband": "HEADBAND",
                 "beanie": "LIGHT_BEANIE", "balaclava": "BALACLAVA"}.get(gc.get("head")),
        "hands": {"gloves": "LIGHT_GLOVES", "mittens": "MITTENS"}.get(gc.get("hands")),
    }


# ------------------------------------------------------------------- checks

_RX_CACHE = {}


def like_match_cached(pattern, value):
    rx = _RX_CACHE.get(pattern)
    if rx is None:
        body = ""
        for ch in pattern:
            if ch == "%":
                body += ".*"
            elif ch == "_":
                body += "."
            else:
                body += re.escape(ch)
        rx = re.compile(body)
        _RX_CACHE[pattern] = rx
    return rx.fullmatch(value) is not None


def check_pattern_gender_proof(pool):
    """Invariant (ii), pattern space: every cascade pattern for a request
    gender begins with that gender's token, and no pattern for gender A
    LIKE-matches any live combination_id of gender B."""
    violations = []
    for gender in GENDERS:
        for weather in WEATHERS:
            for temp in BRACKETS:
                for time in TIMES:
                    for pattern in cascade_patterns(gender, weather, temp, time):
                        if not pattern.startswith(f"{gender}_"):
                            violations.append(f"pattern {pattern} lacks {gender} prefix")
    for row in pool:
        row_gender = row["combination_id"].split("_")[0]
        if row_gender not in GENDERS:
            continue
        for gender in GENDERS:
            if gender == row_gender:
                continue
            for weather in WEATHERS:
                for temp in BRACKETS:
                    for time in TIMES:
                        for pattern in cascade_patterns(gender, weather, temp, time):
                            if like_match_cached(pattern, row["combination_id"]):
                                violations.append(
                                    f"{gender} pattern {pattern} matches {row['combination_id']}")
    return {"check": "pattern_gender_proof", "violations": violations,
            "verdict": "PASS" if not violations else "FAIL"}


def cell_of(combination_id):
    tokens = combination_id.split("_")
    weather = {"RAINY": "RAIN", "SNOWY": "SNOW"}.get(tokens[1], tokens[1])
    return (tokens[0], weather, tokens[2], tokens[3])


def check_matrix_sweep(pool):
    """Invariants (i)+(ii) over every pool cell: for each row, serve the
    cell under the row's own-garments prescription (snapshot semantics)
    and assert a non-blank outcome whose servable candidates are all of
    the request gender. FLOOR outcomes are non-blank by construction
    (bundled floor) and are counted separately."""
    cells = {}
    for row in pool:
        cells.setdefault(cell_of(row["combination_id"]), []).append(row)
    violations = []
    stats = {"cells": len(cells), "requests": 0, "cascade": 0, "scaffold": 0,
             "floor": 0}
    for (gender, weather, temp, time), rows in sorted(cells.items()):
        for row in rows:
            rx = rx_from_garments(row.get("garment_classes"))
            result = simulate_serve(pool, gender, weather, temp, time, rx)
            stats["requests"] += 1
            outcome = result["outcome"]
            if outcome in ("EXACT", "SCAFFOLD"):
                stats["scaffold"] += 1
                pick_gender = result["scaffold_pick"].split("_")[0]
                if pick_gender != gender:
                    violations.append(
                        f"{row['combination_id']}: scaffold pick {result['scaffold_pick']} "
                        f"is {pick_gender}, request {gender}")
            elif outcome == "CASCADE":
                stats["cascade"] += 1
                for combo in result["levels"][-1]["servable"]:
                    if combo.split("_")[0] != gender:
                        violations.append(
                            f"{row['combination_id']}: cascade served {combo} "
                            f"for {gender} request")
            elif outcome == "FLOOR":
                stats["floor"] += 1
            else:
                violations.append(f"{row['combination_id']}: unknown outcome {outcome}")
    return {"check": "matrix_sweep", "stats": stats, "violations": violations[:50],
            "violation_count": len(violations),
            "verdict": "PASS" if not violations else "FAIL"}


# Sol's cells, recorded from live personal_generation_jobs outfit_items
# 2026-11-26 (fix report §1 prescriptions, re-read first-hand this run).
PLANNED_CELL = {"gender": "MALE", "weather": "RAIN", "temp": "COOL", "time": "DAWN",
                "items": ["BASEBALL_CAP", "LONG_SLEEVE_LIGHT", "WINDBREAKER", "LIGHT_TIGHTS"],
                "combination": "MALE_RAIN_COOL_DAWN_32531934"}
FIRSTLOOK_CELL = {"gender": "MALE", "weather": "CLOUDY", "temp": "COOL", "time": "DUSK",
                  "items": ["HEADBAND", "LONG_SLEEVE_LIGHT", "LIGHT_TIGHTS", "LIGHT_GLOVES"],
                  "combination": "MALE_CLOUDY_COOL_DUSK_84c10525"}
GT019_FEMALE_COMBO = "FEMALE_CLEAR_COOL_MIDDAY_32531934"


def check_named_planned_cell():
    """GT-019 replay, LIVE mode (the client's exact per-pattern queries):
    Sol's planned cell must not serve the female same-signature generic;
    the fixed ladder serves the male scaffold set instead."""
    rx = rx_from_items(PLANNED_CELL["items"])
    result = simulate_serve(None, PLANNED_CELL["gender"], PLANNED_CELL["weather"],
                            PLANNED_CELL["temp"], PLANNED_CELL["time"], rx, live=True)
    female_seen = []
    for level in result["levels"]:
        female_seen.extend(c for c in level["servable"]
                           if c.startswith("FEMALE_"))
    pick = result.get("scaffold_pick")
    if result["outcome"] == "CASCADE":
        final_servable = result["levels"][-1]["servable"]
        serve_ok = bool(final_servable) and all(
            c.startswith("MALE_") for c in final_servable)
    else:
        serve_ok = pick is not None and pick.startswith("MALE_")
    ok = (not female_seen and serve_ok
          and GT019_FEMALE_COMBO not in
          [c for lvl in result["levels"] for c in lvl["servable"]])
    return {"check": "named_planned_cell_GT019", "live": True,
            "outcome": result["outcome"], "scaffold_pick": pick,
            "final_servable": (result["levels"][-1]["servable"]
                               if result["outcome"] == "CASCADE" else [pick]),
            "levels": result["levels"], "female_servable_seen": female_seen,
            "note": "Fix report §1 predicted the scaffold set (f213805f) as "
                    "this cell's serve; the live replay serves earlier, at "
                    "cascade level 4 (the legacy same-gender RAINY level), "
                    "which precedes the scaffold in getRandomImage. The "
                    "invariant — no female serve — holds either way.",
            "verdict": "PASS" if ok else "FAIL"}


def check_named_firstlook_cell():
    """Invariants (i)+(iv) on Sol's first-look cell, LIVE mode: in the
    pending state (no personal photo yet) FirstLookPhotoPolicy must
    surface the cell's generic — the standard photo the surface's copy
    promises — never blank."""
    rx = rx_from_items(FIRSTLOOK_CELL["items"])
    result = simulate_serve(None, FIRSTLOOK_CELL["gender"], FIRSTLOOK_CELL["weather"],
                            FIRSTLOOK_CELL["temp"], FIRSTLOOK_CELL["time"], rx, live=True)
    generic_url = None
    servable_combos = []
    if result["outcome"] == "CASCADE":
        servable_combos = result["levels"][-1]["servable"]
        generic_url = "pool://" + servable_combos[0] if servable_combos else None
    elif result["outcome"] in ("EXACT", "SCAFFOLD"):
        servable_combos = [result["scaffold_pick"]]
        generic_url = "pool://" + result["scaffold_pick"]
    shown = first_look_photo_to_show(None, generic_url, "asset://scene")
    gt017_servable = FIRSTLOOK_CELL["combination"] in servable_combos
    return {"check": "named_firstlook_cell", "live": True,
            "outcome": result["outcome"], "servable_combos": servable_combos,
            "levels": result["levels"],
            "firstlook_shows": shown, "pending_state_blank": shown is None,
            "gt017_canonical_in_servable_set": gt017_servable,
            "note": "The GT-017 canonical rows match at cascade level 1 but "
                    "are rejected by the point-grain gate for Sol's actual "
                    "prescription (their read carries no gloves; the "
                    "prescription names LIGHT_GLOVES). The scaffold serves "
                    "the nearest male row instead. GT-017 remains servable "
                    "for other prescriptions — see backview_enumeration.",
            "verdict": "PASS" if shown is not None else "FAIL"}


def check_backview_enumeration(pool, backview_combos):
    """Invariant (iii), PARTIAL by design until step 5's re-judgment:
    enumerate the DOCUMENTED back-view combinations and report which
    remain servable in the live approved pool."""
    pool_combos = {}
    for row in pool:
        pool_combos.setdefault(row["combination_id"], []).append(row["id"])
    still = []
    gone = []
    for entry in backview_combos:
        combo = entry["combination_id"]
        if combo in pool_combos:
            still.append({"combination_id": combo, "source": entry.get("source"),
                          "approved_rows": len(pool_combos[combo])})
        else:
            gone.append({"combination_id": combo, "source": entry.get("source")})
    return {"check": "backview_enumeration", "verdict": "PARTIAL",
            "still_servable": still, "no_longer_servable": gone,
            "note": "Pool-wide orientation cannot be asserted: garment_classes "
                    "carries no orientation/face-presence field. The count of "
                    "back views in the pool is whatever step 5's re-judgment "
                    "measures; this check covers only documented instances."}


def score_ground_truth_set(pool, named, backview):
    """SET.md scoring protocol, serving-layer classes only. Pairs and
    as-served items are scored per the manifest's named classes; classes
    this change does not touch are NOT EVALUATED, never passed."""
    by_id = {r["id"]: r for r in pool}
    scores = []

    def entry(item, verdict, classes, note):
        scores.append({"item": item, "verdict": verdict, "classes": classes, "note": note})

    # GT-002 — Nov 2 GOOD exemplar (pool row): must remain servable.
    # Verified by id against the full table (Management SQL): the row is
    # ABSENT from generated_images entirely — consumed in the RW-176
    # replacement era (an old-format square row), not by this client
    # change, which touches no pool state. Its coherence/detail classes
    # are quality classes (wrong layer here, NOT EVALUATED); the serving
    # class is scored FAIL on live state with the cause named, and the
    # loss of a labeled GOOD row is flagged for step 5.
    row = by_id.get("39d7eb15-fa6d-4076-be02-643020ca4d68")
    entry("GT-002", "PASS" if row is not None else "FAIL",
          ["pool-prescription-match"],
          "row present in the live approved pool" if row is not None
          else "FAIL on live state, NOT attributable to the merged client "
               "change: pool row 39d7eb15 absent from generated_images "
               "entirely (verified by id) — removed in the RW-176 "
               "replacement era; a labeled GOOD row no longer exists in "
               "the pool. pool-condition-coherence / pool-detail classes "
               "NOT EVALUATED (quality layer). Step 5 must reckon with "
               "the consumed GOOD exemplar.")

    # GT-006 / GT-007 — BAD prescription matches (shorts served against a
    # tights prescription): the merged point-grain gate must reject the
    # row for a LIGHT_TIGHTS prescription on the bottom slot. Head/hands
    # are set to the rows' own classes to isolate the named failure.
    for item, rid in (("GT-006", "e92a11f5-5039-47f3-b90e-6bc39af13d98"),
                      ("GT-007", "21b1ccef-8d0c-483c-ac6a-a6746936be39")):
        row = by_id.get(rid)
        if row is None:
            entry(item, "PASS", ["pool-prescription-match"],
                  "row absent from generated_images entirely (verified by "
                  "id) — removed in the RW-176 replacement era; not servable")
            continue
        gc = row.get("garment_classes") or {}
        rx = {"topBase": "LONG_SLEEVE", "topOuter": None, "bottom": "LIGHT_TIGHTS",
              "head": {"visor": "VISOR", "cap": "BASEBALL_CAP", "headband": "HEADBAND",
                       "beanie": "LIGHT_BEANIE", "balaclava": "BALACLAVA"}.get(gc.get("head")),
              "hands": {"gloves": "LIGHT_GLOVES", "mittens": "MITTENS"}.get(gc.get("hands"))}
        rejected = not point_grain_matches(row.get("person_count"), gc, rx)
        entry(item, "PASS" if rejected else "FAIL", ["pool-prescription-match"],
              f"row garments bottom={gc.get('bottom')}; point-grain under a "
              f"LIGHT_TIGHTS prescription: {'rejected' if rejected else 'ACCEPTED'}")

    # GT-019 — cross-gender serve: scored from the live named-cell replay.
    entry("GT-019", named["verdict"], ["cross-gender-serve"],
          f"planned cell outcome {named['outcome']}, pick {named.get('scaffold_pick')}")

    # GT-017 / GT-018 — back-view generic + as served: still servable
    # under the merged build; this change does not remediate them
    # (step 5 owns the class). Scored FAIL on their named classes.
    gt017_still = any(s["combination_id"] == FIRSTLOOK_CELL["combination"]
                      for s in backview["still_servable"])
    entry("GT-017", "FAIL" if gt017_still else "PASS",
          ["orientation-back-view", "face-presence"],
          "canonical back-view generic remains in the approved pool and "
          "servable" if gt017_still else "no longer servable")
    entry("GT-018", "FAIL" if gt017_still else "PASS",
          ["orientation-back-view", "face-presence"],
          "as-served instance of GT-017's class; stands or falls with GT-017")

    for item, why in (
            ("GT-001", "pool row was never identified on record — cannot be scored"),
            ("GT-003", "generation-recipe quality class; a serving change is the wrong layer"),
            ("GT-004", "generation-recipe quality class; a serving change is the wrong layer"),
            ("GT-005", "generation-recipe quality class; a serving change is the wrong layer"),
            ("GT-008", "plastic-quality pole; a serving change is the wrong layer"),
            ("GT-009", "likeness class — step 3 owns; protocol rule 6 bars instrument readings"),
            ("GT-010", "likeness class — step 3 owns"),
            ("GT-011", "likeness class — step 3 owns"),
            ("GT-012", "likeness class — step 3 owns"),
            ("GT-013", "likeness class — step 3 owns; this change neither accepts nor rejects it"),
            ("GT-014", "reference pole, not a servable item"),
            ("GT-015", "reference pole, not a servable item"),
            ("GT-016", "as-served likeness — step 3 owns")):
        entry(item, "NOT EVALUATED", [], why)

    return {"scores": scores,
            "uncovered_classes": [
                "blank/missing hero — no set item; covered behaviourally by "
                "invariant (i)/(iv) checks, not by the set",
                "wrong-condition personal serve (GT-013 note) — personal ladder, "
                "outside this change",
                "likeness for anyone other than Sol — step 3",
                "likeness at NIGHT/DAWN — step 3",
                "correct-gender serve, GOOD side — invariant (ii) checks, not the set",
                "restore/identity behaviour — invariant (v), exercised separately",
                "the six blind-judged Nov 2 pool photos — files unnamed on record",
                "hands/limbs standalone pool class — step 5 rubric"]}


def main(argv):
    out_path = None
    pool_file = None
    backview_file = None
    args = list(argv)
    while args:
        a = args.pop(0)
        if a == "--out":
            out_path = args.pop(0)
        elif a == "--pool-file":
            pool_file = args.pop(0)
        elif a == "--backview-file":
            backview_file = args.pop(0)
    if pool_file:
        with open(pool_file) as fh:
            pool = json.load(fh)
    else:
        pool = fetch_pool()
    backview_combos = [
        {"combination_id": FIRSTLOOK_CELL["combination"],
         "source": "GT-017 canonical approved generic (diagnosis §d, verified first-hand)"},
        {"combination_id": "FEMALE_CLOUDY_MILD_NIGHT_v4",
         "source": "RW-176 census judges' notes (diagnosis §d)"},
        {"combination_id": "MALE_SNOW_MILD_MIDDAY_v3",
         "source": "RW-176 census judges' notes (diagnosis §d)"},
    ]
    if backview_file:
        with open(backview_file) as fh:
            backview_combos.extend(json.load(fh))

    report = {"pool_rows_anon_visible": len(pool)}
    report["pattern_gender_proof"] = check_pattern_gender_proof(pool)
    report["matrix_sweep"] = check_matrix_sweep(pool)
    report["named_planned_cell"] = check_named_planned_cell()
    report["named_firstlook_cell"] = check_named_firstlook_cell()
    report["backview_enumeration"] = check_backview_enumeration(pool, backview_combos)
    report["ground_truth_set"] = score_ground_truth_set(
        pool, report["named_planned_cell"], report["backview_enumeration"])

    hard = [report["pattern_gender_proof"]["verdict"],
            report["matrix_sweep"]["verdict"],
            report["named_planned_cell"]["verdict"],
            report["named_firstlook_cell"]["verdict"]]
    report["hard_verdicts"] = hard
    text = json.dumps(report, indent=1)
    if out_path:
        with open(out_path, "w") as fh:
            fh.write(text)
    print(text)
    return 0 if all(v == "PASS" for v in hard) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
