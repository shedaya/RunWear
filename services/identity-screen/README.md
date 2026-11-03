# Identity-screen service (RW-16.30)

Hosts the Make It Me pipeline's identity screen and intake quality
checks. Since 2026-11-03 the instrument is **YuNet (detection +
landmarks, MIT) + SFace (recognition, Apache 2.0)** — the buffalo_l
stack it replaced carries non-commercial-research-only weights and
cannot ship in a paid app (licensing package:
`~/workspace/release-prep/rw-mim-licensing-2026-11-03.md`). The HTTP
contract is unchanged from the buffalo_l service; pose (yaw/pitch/
roll) is computed from YuNet's landmarks, calibrated against
buffalo_l's stored reads, and `roll_deg` still drives
personal-ref-prep's sideways-reference correction (RW-27 F-1). See
`app.py`'s header for the endpoint contracts, the intake policy
table, the calibration record, and the floor derivation.

Delivered line: `IDENTITY_THRESHOLD` env, default **0.42** (the
licensing package's measured recommendation; 0.45 is the stricter
fallback). The personal-hero-worker applies its own line to the
returned similarity, so the worker constant and this setting must
be deployed together.

## Models

Not committed (39 MB). The Dockerfiles download them at build time
from opencv_zoo with sha256 pins. For a local run, place:

- `models/face_detection_yunet_2023mar.onnx`
- `models/face_recognition_sface_2021dec.onnx`

(or point `YUNET_MODEL_PATH` / `SFACE_MODEL_PATH` elsewhere.)

## Run locally (calibration / battery)

```bash
~/workspace/.venvs/face/bin/python services/identity-screen/app.py
# PORT=8080, SCREEN_KEY optional
```

## Deploy (production)

Any container host reachable over HTTPS from Supabase Edge Functions.
Production uses the GHCR image built from `Dockerfile.provisioning`
by `.github/workflows/identity-screen-image.yml` (Fly.io machine;
see the provisioning package in
`~/workspace/release-prep/mim-provisioning-package-2026-11-03/`).

Then set the worker's edge-function secrets:

- `IDENTITY_SCREEN_URL` = the service base URL (no trailing slash)
- `IDENTITY_SCREEN_KEY` = the same bearer value
- `ANTHROPIC_API_KEY` = the gate's Anthropic key (funded workspace)

Until those secrets exist, the worker fails every personal job closed
with the named stage error (`identity_screen_unavailable` /
`gate_unavailable`) and stores nothing — by design (no uncalibrated
substitute judge, AC-19).
