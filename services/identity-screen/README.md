# Identity-screen service (RW-16.30)

Hosts the Make It Me pipeline's identity screen and intake quality
checks — the exact instrument the calibration lanes used (InsightFace
buffalo_l, largest face, replicated-border pad fallback, cosine
≥ 0.40 = delivered). See `app.py`'s header for the endpoint contracts,
the intake policy table, and the buffalo_l licensing note.

## Run locally (calibration / battery)

```bash
~/workspace/.venvs/face/bin/python services/identity-screen/app.py
# PORT=8080, SCREEN_KEY optional
```

## Deploy (production)

Any container host reachable over HTTPS from Supabase Edge Functions:

```bash
docker build -t runwear-identity-screen services/identity-screen
# run with SCREEN_KEY set to a long random bearer value
```

Then set the worker's edge-function secrets:

- `IDENTITY_SCREEN_URL` = the service base URL (no trailing slash)
- `IDENTITY_SCREEN_KEY` = the same bearer value
- `ANTHROPIC_API_KEY` = the gate's Anthropic key (funded workspace)

Until those secrets exist, the worker fails every personal job closed
with the named stage error (`identity_screen_unavailable` /
`gate_unavailable`) and stores nothing — by design (no uncalibrated
substitute judge, AC-19).
