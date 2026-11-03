#!/usr/bin/env python3
"""RunWear identity-screen service (RW-16.30).

The production host for the Make It Me pipeline's identity screen and
intake quality checks — the lanes' instrument, unchanged:

  * InsightFace buffalo_l (ArcFace w600k_r50), CPU, det_size (640,640)
  * largest face by bounding-box area
  * replicated-border pad fallback when no face is detected
    (border = h//3, w//3, BORDER_REPLICATE — N3's edge-to-edge
    close-up defeats SCRFD unpadded; fb_score.py discipline)
  * cosine = dot of normed embeddings; delivered := cosine >= 0.40

Endpoints (JSON; bearer auth when SCREEN_KEY is set):
  GET  /health  -> {"ok": true, "model": "buffalo_l"}
  POST /compare {reference_image_b64, candidate_image_b64}
       -> {similarity, delivered, reference_face, candidate_face}
          face = {detected, height_norm, yaw_deg, pitch_deg}
          (height_norm = face bbox height / image height, in the
          ORIGINAL image's frame; yaw/pitch from the detector pose)
  POST /intake  {image_b64}
       -> {face_detected, face_height_norm, yaw_deg, pitch_deg,
           verdict: "pass"|"fail", retake_key}
          The shared intake policy: face detected, normalized face
          height >= 50/768 (~0.065 — bracketed by the full-body
          record: 43px failed, 59px+ delivered), |yaw| <= 25 deg.
          retake_key names the client copy (one shared table on both
          platforms):
            no_face       -> "We couldn't find a face — try again facing the camera."
            face_too_small-> "A closer selfie works best — hold the phone at arm's length, or use a mirror selfie."
            turned_away   -> "Try facing a window or light, looking straight at the camera."
          (The too-close close-up case from the record — N3's
          edge-to-edge frame defeating detection — surfaces as
          no_face or face_too_small depending on the detector; the
          arm's-length copy is attached to face_too_small and the
          guided-capture overlay prevents the close-up upstream.)

The worker (personal-hero-worker) calls /compare for every draw
BEFORE any paid gate call; personal-ref-prep calls /intake when a
reference is registered. Both fail closed when this service is
unreachable — there is no substitute identity read.

LICENSING NOTE: buffalo_l weights are distributed by InsightFace for
non-commercial research use. Production go-live requires either a
commercial license from InsightFace or a threshold re-derivation on
a commercially-licensed embedding model (the 0.40 line is calibrated
to THIS instrument). Tracked in the RW-16.30 build record.
"""
import base64
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import cv2
import numpy as np
from insightface.app import FaceAnalysis

DELIVERED_MIN = 0.40
MIN_FACE_HEIGHT_NORM = 50.0 / 768.0
MAX_YAW_DEG = 25.0
SCREEN_KEY = os.environ.get("SCREEN_KEY", "")
PORT = int(os.environ.get("PORT", "8080"))

app = FaceAnalysis(name="buffalo_l", providers=["CPUExecutionProvider"])
app.prepare(ctx_id=-1, det_size=(640, 640))


def decode(b64):
    raw = base64.b64decode(b64)
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("image decode failed")
    return img


def largest_face(img):
    """(face, pad_dx, pad_dy, img_w, img_h) in the original frame.

    Mirrors fb_score.py: detect; if none, retry on a replicated
    border pad of (h//3, w//3); take the largest face by bbox area.
    """
    h, w = img.shape[:2]
    faces = app.get(img)
    if faces:
        face = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))
        return face, 0, 0, w, h
    img2 = cv2.copyMakeBorder(img, h // 3, h // 3, w // 3, w // 3,
                              cv2.BORDER_REPLICATE)
    faces = app.get(img2)
    if not faces:
        return None, 0, 0, w, h
    face = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))
    return face, w // 3, h // 3, w, h


def face_meta(img):
    face, dx, dy, w, h = largest_face(img)
    if face is None:
        return None, {"detected": False, "height_norm": None,
                      "yaw_deg": None, "pitch_deg": None}
    x1, y1, x2, y2 = [float(v) for v in face.bbox]
    height_norm = (y2 - y1) / float(h)
    pose = getattr(face, "pose", None)
    pitch = float(pose[0]) if pose is not None else None
    yaw = float(pose[1]) if pose is not None else None
    return face, {"detected": True, "height_norm": round(height_norm, 4),
                  "yaw_deg": round(yaw, 1) if yaw is not None else None,
                  "pitch_deg": round(pitch, 1) if pitch is not None else None}


def embedding(img):
    face, meta = face_meta(img)
    if face is None:
        return None, meta
    return np.asarray(face.normed_embedding, dtype=np.float64), meta


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
            return self._json(200, {"ok": True, "model": "buffalo_l"})
        return self._json(404, {"error": "not_found"})

    def do_POST(self):
        if not self._authorized():
            return self._json(401, {"error": "unauthorized"})
        try:
            body = self._body()
            if self.path == "/compare":
                ref = decode(body["reference_image_b64"])
                cand = decode(body["candidate_image_b64"])
                ref_emb, ref_meta = embedding(ref)
                cand_emb, cand_meta = embedding(cand)
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
                img = decode(body["image_b64"])
                _, meta = face_meta(img)
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
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
