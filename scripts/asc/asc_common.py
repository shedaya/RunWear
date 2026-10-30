"""Shared ASC API client for the AccliMate ship tooling (stdlib only).
Auth: ES256 JWT signed via the openssl CLI (the API key .p8), so no
third-party Python packages are needed on the runner."""
import base64, json, os, subprocess, sys, time, urllib.request, urllib.error

BASE = "https://api.appstoreconnect.apple.com"
KEY_ID = os.environ["ASC_KEY_ID"]
ISSUER = os.environ["ASC_ISSUER_ID"]
KEY_PATH = os.environ["ASC_KEY_PATH"]

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

def der_to_raw(der: bytes) -> bytes:
    assert der[0] == 0x30
    idx = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)
    assert der[idx] == 0x02
    ln = der[idx + 1]; r = der[idx + 2: idx + 2 + ln]; idx += 2 + ln
    assert der[idx] == 0x02
    ln = der[idx + 1]; s = der[idx + 2: idx + 2 + ln]
    r = r.lstrip(b"\x00").rjust(32, b"\x00")
    s = s.lstrip(b"\x00").rjust(32, b"\x00")
    return r + s

def token() -> str:
    now = int(time.time())
    head = b64url(json.dumps({"alg": "ES256", "kid": KEY_ID, "typ": "JWT"},
                             separators=(",", ":")).encode())
    body = b64url(json.dumps({"iss": ISSUER, "iat": now, "exp": now + 1100,
                              "aud": "appstoreconnect-v1"},
                             separators=(",", ":")).encode())
    signing = f"{head}.{body}".encode()
    p = subprocess.run(["openssl", "dgst", "-sha256", "-sign", KEY_PATH],
                       input=signing, capture_output=True)
    if p.returncode != 0:
        raise SystemExit("openssl signing failed: " + p.stderr.decode()[:300])
    return f"{head}.{body}.{b64url(der_to_raw(p.stdout))}"

def call(method, path, body=None, expect=(200, 201, 204), tries=4):
    url = path if path.startswith("http") else BASE + path
    data = json.dumps(body).encode() if body is not None else None
    last = None
    for a in range(tries):
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Authorization", "Bearer " + token())
        if data: req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=90) as resp:
                raw = resp.read()
                return resp.status, (json.loads(raw) if raw else {})
        except urllib.error.HTTPError as e:
            raw = e.read()
            try: parsed = json.loads(raw) if raw else {}
            except json.JSONDecodeError: parsed = {"raw": raw[:400].decode("utf-8", "replace")}
            if e.code in expect:
                return e.code, parsed
            print(f"!! {method} {path} -> {e.code}", file=sys.stderr)
            print(json.dumps(parsed, indent=1)[:3000], file=sys.stderr)
            raise SystemExit(f"ASC API error {e.code} on {method} {path}")
        except Exception as e:
            last = e
            print(f".. retry {a+1}: {e}", file=sys.stderr)
            time.sleep(5 * (a + 1))
    raise SystemExit(f"network failure on {method} {path}: {last}")
