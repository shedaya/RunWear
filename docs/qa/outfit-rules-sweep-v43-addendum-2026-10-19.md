# Addendum — inv7 resolved: sun gear keys to RAW temperature (engine v4.3)

**Date:** 2026-10-19 · **Engine:** `GetOutfitRecommendationUseCase`, `ENGINE_VERSION = "4.3"` ·
**Supersedes:** the open inv7 finding in
[outfit-rules-sweep-2026-10-17.md](outfit-rules-sweep-2026-10-17.md) ·
**Machine report:** [outfit-rules-sweep-2026-10-19-full-report.txt](outfit-rules-sweep-2026-10-19-full-report.txt)

## The decision

Owner, 2026-10-19: **sun gear is not layering.** The Running Cap (and sun gear
generally) keys to the **raw temperature** — feels-like before the comfort
preference is applied — not the comfort-adjusted frame. The 640 inv7 inversions
the Oct 17 sweep found were by design pending this call; the call is now made.

## The change (engine)

In `GetOutfitRecommendationUseCase` (shared module):

- `selectHead()` now receives both frames. Its two sun gates — VISOR at ≥ 65 °F
  and BASEBALL_CAP at ≥ 50 °F, both with UV > 3 — read the **raw** temperature
  (`baseTemp`). Everything else in the function is untouched: the warmth
  brackets (headband / beanies / balaclava) still read the comfort-**adjusted**
  temperature, and layering logic elsewhere is unchanged.
- `ENGINE_VERSION` bumped 4.2 → 4.3 (per the constant's own rule: bump whenever
  the selection tables change). The version pins that read it were updated:
  the harness's inv0 and `:app`'s `TelemetryContractTest` bundle assertion.

### Classification of everything that shares the code path

| Item / branch | Role | Frame after v4.3 | Changed? |
|---|---|---|---|
| VISOR (≥ 65 °F, UV > 3) | Sun protection | **Raw** | Yes — was adjusted |
| BASEBALL_CAP via sun gate (≥ 50 °F, UV > 3) | Sun protection | **Raw** | Yes — was adjusted |
| BASEBALL_CAP via rain branch (`isRaining`) | Rain protection — keyed to rain itself, not a temperature decision | n/a | No |
| SUNGLASSES (UV > 3), SUNSCREEN (UV > 5) | Sun protection | UV only — never read a temperature in any frame, so comfort could never move them | No (audited, comment added) |
| HEADBAND / LIGHT_BEANIE / THERMAL_BEANIE / BALACLAVA | Insulation | Adjusted | No |
| NECK_GAITER (adjusted < 30 °F) | Insulation | Adjusted | No |
| REFLECTIVE_GEAR | Visibility (hour-keyed) | n/a | No |

The one shared-slot subtlety: the cap serves two jobs. Only its *sun* job
changed frame; when it rains it is still rain gear on the rain branch.

## The harness (inv7 rewritten)

`inv7_profileOrdering` no longer counts inversions and explains them away by
excluding sun gear. It now asserts both halves of the decision over the full
grid:

- **(a) Ordering:** runs-cold ≥ neutral ≥ runs-warm total item count at
  identical weather — 167,680 adjacent comfort comparisons, **0 inversions**
  (Oct 17, adjusted-frame sun gates: 640).
- **(b) Raw keying, exactly:** at identical weather the sun-protection outcome
  (sun headgear + sunglasses + sunscreen) is identical across all five comfort
  profiles, and in dry/snow conditions the sun headgear equals the pure
  raw-temperature function (VISOR iff raw ≥ 65 and UV > 3; BASEBALL_CAP iff
  50 ≤ raw < 65 and UV > 3; none below 50 raw or without UV) at *every*
  profile — 146,720 keying checks, **0 violations**. Rain/drizzle are excluded
  from the threshold half (there the cap is rain gear).

## Re-run results (2026-10-19)

`gradle :shared:testDebugUnitTest` (JDK 17, Gradle 8.10) — **13 tests, 0
failures**, full 209,600-scenario grid plus the divergence and Celsius slices:

| Invariant | Oct 17 (v4.2) | Oct 19 (v4.3) |
|---|---|---|
| inv0 version pin | 4.2 | 4.3 ✓ |
| inv1 band shell/mid exclusion | 0 | 0 |
| inv2 monotonic warmth | 0 | 0 |
| inv3 cliffs (> 2) | 0 (800 jumps of exactly 2) | 0 (720 jumps of exactly 2 — all the same headband+gloves adjusted-40 °F pattern; fewer because the raw-keyed cap now lands *in* some of those steps instead of crossing on the adjusted frame) |
| inv4 structure | 0 | 0 |
| inv5 contradiction pairs | 0 | 0 |
| inv6 wind sanity | 0 | 0 |
| **inv7 ordering / keying** | **640 inversions** | **0 / 0** |
| inv8 field cases | both exact | both exact |
| inv9 rain coverage | 0 | 0 |
| inv10 actual-temp independence | 0 | 0 |
| inv11 Celsius equivalence | 0 | 0 |

Also run: `:app:testDebugUnitTest --tests TelemetryContractTest` — 12/12 pass
(the feedback bundle now reports engine 4.3).

`:shared:testDebugUnitTest` is green end-to-end for the first time since the
harness was written — the by-design red is closed.

## Not done / follow-ups

- **PWA mirror:** the PWA duplicates these tables and still keys its sun gates
  to the adjusted frame, as it did for v4.2. It needs the same change,
  tracked the same way v4.2's mirror was.
- The Oct 17 observations stand unchanged: snow is still invisible to the
  engine (`isSnowing` unread), and drizzle (precip ≤ 0.1) still earns no rain
  jacket. Neither was in scope here.
