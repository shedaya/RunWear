# Served-result verification harness

Plan step 4 (Make It Me step-back plan, approved by Sol Nov 25, 2026).
Diagnosis: `rw-mim-stepback-diagnosis.md` §(e) step 4 — every layer of
Make It Me verified itself against its own outputs; this harness is the
layer that verifies the **served result** instead.

## What it checks

Against the **live** generic pool (read through the client's own anon
PostgREST path — the exact rows and columns the app can see) and a
line-faithful port of the merged build's serving logic:

| # | Invariant | How |
|---|---|---|
| (i) | Hero never blank where an approved generic exists for the cell | Matrix sweep: every pool cell × every row's own-garments prescription, replaying cascade → scaffold → floor |
| (ii) | Served generic's gender always equals the fit preference | Pattern-space proof (every cascade pattern carries the request gender; no pattern LIKE-matches the other gender's rows) + sweep assertion on every servable candidate |
| (iii) | Served hero has a visible front-facing face | **PARTIAL** until step 5's rubric re-judgment creates orientation data: enumerates the documented back-view combinations and reports which remain servable. The named negative is GT-017 |
| (iv) | First-look surface in the pending state shows the standard photo its copy promises | Live replay of Sol's first-look cell + `FirstLookPhotoPolicy` port (personal → generic → scene) |
| (v) | Reset-without-delete → Restore Purchases returns the bank | Not in this script — exercised against the deployed server machinery on fixture accounts; see the step-4 restore exercise record in the audit folder |

Named live checks replay the RW-178 incident cell (GT-019) and Sol's
first-look cell with the client's **exact** per-pattern queries
(LIKE pattern, limit 50), and the script also scores the merged build
against the ground-truth regression set per `SET.md`'s protocol
(serving-layer classes only; untouched classes are NOT EVALUATED).

## Run it

```sh
python3 tools/served-result-harness/harness.py \
  --out step4-harness-report.json \
  [--pool-file pool.json] [--backview-file backviews.json]
```

- No `--pool-file`: fetches the pool live (anon key is the app's shipped
  public credential, copied from `HeroImageRepository.kt`).
- `--backview-file`: JSON list of `{"combination_id", "source"}` for
  additional documented back-view combinations (e.g. the RW-176 pilot
  wave-1 verdicts).
- Exit code 0 = all hard checks (pattern proof, sweep, GT-019 replay,
  first-look replay) pass. (iii) and the set score are reports.

## Fidelity and limits

- Ports: `cascade_patterns` ← `HeroImageRepository.kt`;
  `is_servable` / `point_grain_matches` ← `HeroPoolPolicy.kt`
  (`HeroPointGrainPolicy`); `chain_resolve` / `chain_distance` /
  `prescription_snapshot` ← engine `HeroFallbackChain.kt`;
  `pool_photo_candidates` ← `HeroImageRepository.kt`;
  `first_look_photo_to_show` ← `FirstLookPresentation.kt`.
  The repo's CI suites pin the Kotlin behaviour; if a port and the
  Kotlin disagree, the Kotlin wins and this file must be fixed.
- Snapshot sweep semantics: per cascade level the harness evaluates the
  full matching row set; the client pages at 50 rows per pattern in
  database order. The named checks run in live mode with the client's
  exact page, so the incident cells are verified at full fidelity.
- The scaffold simulation mirrors `getScaffoldOrFallback` (bundled
  candidates are an empty list there). A FLOOR outcome means the pool
  chain was empty and the bundled floor resolved — non-blank by
  construction (`HeroImageSelector` covers every gender × bracket).
- The harness models the Android/shared serving chain. The iOS cascade
  (`HeroImageService.fetchWithCascade`) is verified by source (levels
  1–3 + legacy same-gender level 4; cross-gender levels removed) and by
  its CI suites, not by this simulation.
- Orientation/face-presence has **no column** in `garment_classes`; a
  pool-wide (iii) assertion is impossible until step 5 lands. Do not
  report (iii) as PASS from this harness.
