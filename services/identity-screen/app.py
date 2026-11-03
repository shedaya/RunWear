"""RunWear identity screen (RW-16.30) — YuNet + SFace stack.

Replaced the InsightFace buffalo_l stack on 2026-11-03 (Sol's ruling:
"Build the swap only — skip the inquiry"). buffalo_l's code is MIT but
its WEIGHTS are licensed for non-commercial research only, which a
paid app cannot ship on. The replacement models are commercially
clean, verified against their primary sources in the licensing
package (~/workspace/release-prep/rw-mim-licensing-2026-11-03.md):

  * YuNet face detection + 5 landmarks — MIT (opencv_zoo,
    models/face_detection_yunet).
    sha256(face_detection_yunet_2023mar.onnx)
      = 8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4
  * SFace recognition — Apache License 2.0 (opencv_zoo,
    models/face_recognition_sface; the model card's BSD note covers
    the separate training-data tooling, not the weights).
    sha256(face_recognition_sface_2021dec.onnx)
      = 0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79

The HTTP contract is IDENTICAL to the buffalo_l service it replaces:

    GET  /health  -> {"ok": true, "model": "yunet_sface"}
    POST /compare {reference_image_b64, candidate_image_b64}
                  -> {similarity, delivered, reference_face,
                      candidate_face}
    POST /intake  {image_b64}
                  -> face meta + {verdict, retake_key}

Face meta keys (unchanged): detected, height_norm (largest face's
bbox height over frame height in the ORIGINAL frame), yaw_deg,
pitch_deg, roll_deg. Consumers: personal-ref-prep (intake + the
roll-driven orientation correction — RW-27 F-1: ref-prep reads
roll_deg to upright sideways references before encoding their
derivatives) and personal-hero-worker (compare, BEFORE any paid gate
call; the worker applies its own delivered line to the returned
similarity, so this service's IDENTITY_THRESHOLD and the worker's
line must be deployed together). Both consumers fail closed when
this service is unreachable — there is no substitute identity read.

Pose from YuNet's 5 landmarks, calibrated against buffalo_l's stored
reads on the bench reference corpus (17 refs + the F-1 evidence
files; full parity table in ~/workspace/release-prep/rw-mim-swap-work/):
  * roll  — solvePnP (SQPNP, canonical camera-frame 3D model)
    z-angle. Corpus MAE 1.8 deg. This is the reading ref-prep's F-1
    correction consumes; its hard anchors reproduce (sideways
    derivative +90.4 vs buffalo +90.5; corrected derivative -2.5
    vs -0.2 in-frame).
  * yaw   — linear calibration on the nose-vs-mouth-midline offset:
    yaw = 1.82 + 67.31 * (nose_x - mouth_mid_x) / inter_eye_dist.
    Corpus MAE 6.1 deg; the 25 deg intake gate reproduces 17/18
    stored verdicts (the miss is N4, a marginal buffalo -26.0 read
    whose YuNet geometry reads -13.7; the clear fail, N6 at +47.2,
    reads +35.7 and still fails).
  * pitch — linear calibration on the nose height below the eye
    line: pitch = -17.59 + 21.64 * (nose_y - eye_mid_y) /
    inter_eye_dist. Corpus MAE 5.6 deg. Informational only —
    nothing gates on pitch.

Rotated photos: YuNet does not detect a face in a 90-degree-rotated
frame, so when the original frame yields no face the screen retries
detection on quarter-turn rotations (CCW, CW, 180) and reports meta
from the best-scoring rotated find — roll = quarter-turn offset +
in-frame roll, exactly the value ref-prep's correction consumes
(roll_deg MUST keep working; the F-1 fix depends on it). A rotated
find carries NO embedding: similarity stays null and delivered
stays false, matching buffalo_l's effective behavior on rolled
references (its sideways-vs-selfie cosine collapsed to ~0.19, a
fail). The correction belongs to ref-prep, not to this screen.

Intake floor (MIN_FACE_HEIGHT_NORM) re-derived on THIS stack, not
carried over by box-convention accident: downscale series on three
subjects show SFace self-cosines hold >= ~0.84 down to a ~50 px
face (at the 768-normalized derivative scale intake measures),
degrade through 40-30 px, and collapse below ~25-30 px; the
full-body bracket's working face (N6, 59 px on buffalo) reads
58 px on YuNet and its selfie<->body SFace cosine (0.664) matches
buffalo's (0.675). Floor = 50/768 ~= 0.0651 — the same normalized
value as the buffalo floor, now standing on SFace's own
measurements. Override with INTAKE_MIN_FACE_HEIGHT_NORM.

The intake policy (unchanged): face detected, normalized face
height >= floor, |yaw| <= 25 deg. retake_key names the client copy
(one shared table on both platforms):
    no_face       -> "We couldn't find a face — try again facing the camera."
    face_too_small-> "A closer selfie works best — hold the phone at arm's length, or use a mirror selfie."
    turned_away   -> "Try facing a window or light, looking straight at the camera."

Settings (environment):
  SCREEN_KEY                    Bearer token required on POSTs when set
  PORT                          listen port (default 8080)
  IDENTITY_THRESHOLD            delivered line for /compare
                                (default 0.42; 0.45 is the documented
                                stricter fallback. The FINAL line is
                                set by the battery + eye-spread re-run
                                on this stack — hence env, not a
                                buried constant.)
  INTAKE_MIN_FACE_HEIGHT_NORM   intake size floor (default 50/768)
  YUNET_MODEL_PATH / SFACE_MODEL_PATH
                                model file locations (default:
                                ./models/<opencv_zoo file names>)
"""

import base64
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import cv2
import numpy as np

_HERE = os.path.dirname(os.path.abspath(__file__))
YUNET_PATH = os.environ.get(
    "YUNET_MODEL_PATH",
    os.path.join(_HERE, "models", "face_detection_yunet_2023mar.onnx"))
SFACE_PATH = os.environ.get(
    "SFACE_MODEL_PATH",
    os.path.join(_HERE, "models", "face_recognition_sface_2021dec.onnx"))

DELIVERED_MIN = float(os.environ.get("IDENTITY_THRESHOLD", "0.42"))
MIN_FACE_HEIGHT_NORM = float(
    os.environ.get("INTAKE_MIN_FACE_HEIGHT_NORM", str(50.0 / 768.0)))
MAX_YAW_DEG = 25.0
SCREEN_KEY = os.environ.get("SCREEN_KEY", "")
PORT = int(os.environ.get("PORT", "8080"))

# Canonical 3D face model in CAMERA frame (x right, y down, z into
# the scene), ordered [nose tip, image-left eye, image-right eye,
# image-left mouth corner, image-right mouth corner] to match the
# geometry-sorted YuNet landmarks. A frontal face solves near
# identity, which keeps the RQDecomp3x3 angles stable.
_MODEL_3D = np.array([
    (0.0, 0.0, 0.0),
    (-32.0, -45.0, 42.0),
    (32.0, -45.0, 42.0),
    (-26.0, 35.0, 30.0),
    (26.0, 35.0, 30.0),
], dtype=np.float64)

# Pose calibration constants (fit on the bench corpus vs buffalo_l
# stored reads — see the module docstring).
_YAW_INTERCEPT, _YAW_SLOPE = 1.816, 67.306
_PITCH_INTERCEPT, _PITCH_SLOPE = -17.588, 21.641

# Quarter-turn retries when the original frame has no detectable
# face: (cv2 rotate code, roll offset in degrees). The offset is
# the CCW pixel rotation that uprights the photo — the convention
# ref-prep's correction consumes (buffalo_l read +90.5 on Sol's
# sideways derivative; rotating that frame 90 deg CCW uprights it).
_ROTATIONS = [
    (cv2.ROTATE_90_COUNTERCLOCKWISE, 90.0),
    (cv2.ROTATE_90_CLOCKWISE, -90.0),
    (cv2.ROTATE_180, 180.0),
]

_detector = cv2.FaceDetectorYN.create(YUNET_PATH, "", (640, 640), 0.6, 0.3, 5000)
_recognizer = cv2.FaceRecognizerSF.create(SFACE_PATH, "")
# The detector's input size is mutable shared state; serialize all
# analysis so concurrent requests cannot interleave setInputSize.
_lock = threading.Lock()


def _detect(img):
    """All YuNet rows for an image (largest-face choice is the caller's)."""
    _detector.setInputSize((img.shape[1], img.shape[0]))
    out = _detector.detect(img)
    faces = out[1] if isinstance(out, tuple) else out
    if faces is None:
        return []
    return [row for row in faces]


def _largest(rows):
    return max(rows, key=lambda r: float(r[2]) * float(r[3]))


def _detect_with_pad(img):
    """(row, pad_dx, pad_dy, img_used) for the largest face by bbox
    area, retrying on a replicated-border padded frame when the
    plain frame finds none (faces flush with an edge — N3's
    edge-to-edge close-up; fb_score.py discipline, same as the
    buffalo service)."""
    rows = _detect(img)
    if rows:
        return _largest(rows), 0, 0, img
    h, w = img.shape[:2]
    padded = cv2.copyMakeBorder(img, h // 3, h // 3, w // 3, w // 3,
                                cv2.BORDER_REPLICATE)
    rows = _detect(padded)
    if not rows:
        return None, 0, 0, img
    return _largest(rows), w // 3, h // 3, padded


def _landmarks(row, dx, dy):
    """YuNet row -> landmarks in the analyzed frame's coordinates
    (pad offsets removed), ordered [nose, image-left eye,
    image-right eye, image-left mouth, image-right mouth] with
    eyes and mouth corners sorted by image x."""
    pts = np.array([[float(row[4]) - dx, float(row[5]) - dy],
                    [float(row[6]) - dx, float(row[7]) - dy],
                    [float(row[8]) - dx, float(row[9]) - dy],
                    [float(row[10]) - dx, float(row[11]) - dy],
                    [float(row[12]) - dx, float(row[13]) - dy]],
                   dtype=np.float64)
    eyes = sorted([pts[0], pts[1]], key=lambda p: p[0])
    mouth = sorted([pts[3], pts[4]], key=lambda p: p[0])
    return np.array([pts[2], eyes[0], eyes[1], mouth[0], mouth[1]])


def _pose(img_shape, lm):
    """(yaw_deg, pitch_deg, roll_deg) from ordered landmarks."""
    h, w = img_shape[:2]
    nose, eye_l, eye_r, mouth_l, mouth_r = lm
    eye_mid = (eye_l + eye_r) / 2.0
    mouth_mid = (mouth_l + mouth_r) / 2.0
    inter_eye = float(np.linalg.norm(eye_r - eye_l)) or 1.0
    yaw = _YAW_INTERCEPT + _YAW_SLOPE * float(nose[0] - mouth_mid[0]) / inter_eye
    pitch = _PITCH_INTERCEPT + _PITCH_SLOPE * float(nose[1] - eye_mid[1]) / inter_eye
    # Sanity clamps: the linear calibrations extrapolate without
    # bound when the inter-eye distance collapses (profile faces);
    # buffalo_l's pose never reports angles beyond these ranges.
    yaw = max(-65.0, min(65.0, yaw))
    pitch = max(-50.0, min(50.0, pitch))
    cam = np.array([[w, 0, w / 2.0], [0, w, h / 2.0], [0, 0, 1]],
                   dtype=np.float64)
    ok, rvec, _tvec = cv2.solvePnP(_MODEL_3D, lm, cam, np.zeros((4, 1)),
                                   flags=cv2.SOLVEPNP_SQPNP)
    roll = float("nan")
    if ok:
        rot, _ = cv2.Rodrigues(rvec)
        angles, *_ = cv2.RQDecomp3x3(rot)
        roll = float(angles[2])
    return yaw, pitch, roll


def _wrap_deg(deg):
    """Wrap an angle to (-180, 180]."""
    return 180.0 - ((180.0 - deg) % 360.0)


def _rotated_meta(rotated_img, row, dx, dy, code, offset):
    """Face meta for a face found in a quarter-turn-rotated frame.
    The bbox is mapped back to the original frame for height_norm;
    roll = quarter-turn offset + in-frame PnP roll (both are
    in-plane rotations about the same axis, so they add). Yaw and
    pitch are not measurable in the original frame's orientation
    and are reported as null. No embedding is produced (see the
    module docstring)."""
    frame_h, frame_w = rotated_img.shape[:2]
    x, y = float(row[0]) - dx, float(row[1]) - dy
    bw, bh = float(row[2]), float(row[3])
    corners = [(x, y), (x + bw, y), (x, y + bh), (x + bw, y + bh)]
    if code == cv2.ROTATE_180:
        orig_h, orig_w = frame_h, frame_w
        mapped = [(orig_w - 1 - cx, orig_h - 1 - cy) for cx, cy in corners]
    elif code == cv2.ROTATE_90_COUNTERCLOCKWISE:
        orig_h, orig_w = frame_w, frame_h
        mapped = [(cy, orig_w - 1 - cx) for cx, cy in corners]
    else:  # ROTATE_90_CLOCKWISE
        orig_h, orig_w = frame_w, frame_h
        mapped = [(orig_h - 1 - cy, cx) for cx, cy in corners]
    ys = [p[1] for p in mapped]
    height_norm = (max(ys) - min(ys)) / float(orig_h)
    lm = _landmarks(row, dx, dy)
    _yaw, _pitch, roll_in_frame = _pose(rotated_img.shape, lm)
    roll = _wrap_deg(offset + (0.0 if roll_in_frame != roll_in_frame
                               else roll_in_frame))
    return {
        "detected": True,
        "height_norm": round(height_norm, 4),
        "yaw_deg": None,
        "pitch_deg": None,
        "roll_deg": round(roll, 1),
    }


def _embed(img_used, row):
    aligned = _recognizer.alignCrop(img_used, row)
    feat = _recognizer.feature(aligned).flatten().astype(np.float64)
    norm = float(np.linalg.norm(feat))
    return feat / norm if norm > 0 else feat


def decode(b64):
    raw = base64.b64decode(b64)
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("image decode failed")
    return img


def analyze(img):
    """One decoded image -> (meta, embedding). Meta has the wire
    shape of a face object; embedding is a unit-norm SFace vector
    or None (no face, or face found only in a rotated frame)."""
    with _lock:
        row, dx, dy, img_used = _detect_with_pad(img)
        if row is not None:
            lm = _landmarks(row, dx, dy)
            yaw, pitch, roll = _pose(img.shape, lm)
            meta = {
                "detected": True,
                "height_norm": round(float(row[3]) / img.shape[0], 4),
                "yaw_deg": None if yaw != yaw else round(yaw, 1),
                "pitch_deg": None if pitch != pitch else round(pitch, 1),
                "roll_deg": None if roll != roll else round(roll, 1),
            }
            return meta, _embed(img_used, row)
        # No face in the original frame: quarter-turn retries, meta
        # only. Pick the best-scoring rotated find.
        best = None
        for code, offset in _ROTATIONS:
            rotated = cv2.rotate(img, code)
            rrow, rdx, rdy, _rimg = _detect_with_pad(rotated)
            if rrow is None:
                continue
            score = float(rrow[14])
            if best is None or score > best[0]:
                best = (score, rotated, rrow, rdx, rdy, code, offset)
        if best is not None:
            _score, rotated, rrow, rdx, rdy, code, offset = best
            return _rotated_meta(rotated, rrow, rdx, rdy, code, offset), None
        return {"detected": False, "height_norm": None, "yaw_deg": None,
                "pitch_deg": None, "roll_deg": None}, None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):  # quiet
        pass

    def _json(self, code, body):
        raw = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _authorized(self):
        if not SCREEN_KEY:
            return True
        return self.headers.get("authorization") == f"Bearer {SCREEN_KEY}"

    def _body(self):
        n = int(self.headers.get("content-length") or 0)
        return json.loads(self.rfile.read(n) or b"{}")

    def do_GET(self):
        if self.path == "/health":
            return self._json(200, {"ok": True, "model": "yunet_sface"})
        return self._json(404, {"error": "not_found"})

    def do_POST(self):
        if not self._authorized():
            return self._json(401, {"error": "unauthorized"})
        try:
            body = self._body()
            if self.path == "/compare":
                ref_meta, ref_emb = analyze(decode(body["reference_image_b64"]))
                cand_meta, cand_emb = analyze(decode(body["candidate_image_b64"]))
                sim = None
                if ref_emb is not None and cand_emb is not None:
                    sim = round(float(ref_emb @ cand_emb), 4)
                return self._json(200, {
                    "similarity": sim,
                    "delivered": sim is not None and sim >= DELIVERED_MIN,
                    "reference_face": ref_meta,
                    "candidate_face": cand_meta,
                })
            if self.path == "/intake":
                meta, _emb = analyze(decode(body["image_b64"]))
                verdict, retake_key = "pass", None
                if not meta["detected"]:
                    verdict, retake_key = "fail", "no_face"
                elif meta["height_norm"] is not None and \
                        meta["height_norm"] < MIN_FACE_HEIGHT_NORM:
                    verdict, retake_key = "fail", "face_too_small"
                elif meta["yaw_deg"] is not None and \
                        abs(meta["yaw_deg"]) > MAX_YAW_DEG:
                    verdict, retake_key = "fail", "turned_away"
                return self._json(200, {**meta, "verdict": verdict,
                                        "retake_key": retake_key})
            return self._json(404, {"error": "not_found"})
        except Exception as e:  # fail closed, named
            return self._json(500, {"error": f"screen_error: {e}"})


if __name__ == "__main__":
    print(f"identity-screen (yunet_sface) listening on :{PORT} "
          f"(threshold {DELIVERED_MIN}, intake floor "
          f"{MIN_FACE_HEIGHT_NORM:.4f})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
