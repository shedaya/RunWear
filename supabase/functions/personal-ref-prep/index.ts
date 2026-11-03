// Supabase Edge Function: personal-ref-prep (RW-16.12 Lane A, 2026-11-01)
//
// Produces the composite-grade derivative of a personal reference
// photo: the original bounded to the identity gate's working size
// (height <= 768 px, width <= 2048 px, aspect preserved), JPEG q85,
// stored beside the original at <uid>/refs-small/<name>.jpg.
//
// Why this exists: the personal-hero-worker's identity gate builds a
// side-by-side composite at 768 px tall, so it never needs the
// reference at full resolution — but decoding a 3600x4800 (17.3 MP)
// original to get there is the gate path's dominant memory spike
// (measured locally against the exact composite code: the worker's
// imagescript decode of the original peaks a bare process at
// ~315 MB, over the platform's per-isolate cap once the function
// carries its normal resident state; attempts died mid-decode with
// nothing written — the RW-16 wedge class: jobs stuck in PROCESSING,
// attempts consumed, no gate output). Supabase Storage image
// transformation is not enabled on this project (probed live: 403
// FeatureNotEnabled), so the downscale happens here instead — once
// per reference, in an isolate that does nothing else, using
// MozJPEG (via @jsquash/jpeg) whose decode spike measured ~60 MB
// leaner than the imagescript path. The worker then only ever
// decodes the derivative (see loadCompositeRefBytes in
// personal-hero-worker).
//
// Decoder note: photon (Rust/WASM, leanest in local tests) cannot
// run on this platform — its wasm-bindgen web glue references DOM
// APIs and the function fails to boot (verified live, BOOT_ERROR),
// and runtime dynamic imports of remote/npm modules are not
// resolved by the edge runtime. All imports here are therefore
// static, matching how the worker itself is bundled.
//
// Invocation: POST { "path": "<uid>/refs/ref-1.jpg" }, service-role
// bearer. Idempotent: re-running overwrites the derivative.
//
// RW-27 F-1 (2026-11-03): before encoding, prep probes the decoded
// frame's face roll via the identity screen; a reference stored
// sideways (|roll| > 30 deg) is uprighted by quarter-turns in the
// DERIVATIVE only — the original object is never rewritten — and
// the correction is recorded in the stored quality verdict. A
// correction that cannot be verified upright fails the verdict
// with retake_key 'rotated' rather than passing.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import jpegDecode from "https://esm.sh/@jsquash/jpeg@1.6.0/decode"
import jpegEncode from "https://esm.sh/@jsquash/jpeg@1.6.0/encode"
import { Image } from "https://deno.land/x/imagescript@1.2.17/mod.ts"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
// RW-16.30: the identity-screen service also runs the intake quality
// checks (same deployment as the worker's IDENTITY_SCREEN_URL).
const IDENTITY_SCREEN_URL = (Deno.env.get('IDENTITY_SCREEN_URL') ?? '').replace(/\/$/, '')
const IDENTITY_SCREEN_KEY = Deno.env.get('IDENTITY_SCREEN_KEY') ?? ''

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

// jsquash's published types predate the ArrayBuffer-generic lib
// types and describe ImageData more narrowly than the runtime
// accepts; these shims state the actual runtime contract (verified
// live against 3600x4800 references) at the package boundary.
type DecodedFrame = {
  data: Uint8ClampedArray, width: number, height: number, colorSpace: string
}
const decodeJpeg = jpegDecode as unknown as
  (data: Uint8Array) => Promise<DecodedFrame>
const encodeJpeg = jpegEncode as unknown as
  (img: DecodedFrame, opts: { quality: number }) => Promise<ArrayBuffer>

const BUCKET = 'personal-heroes'
// The identity composite's working geometry (personal-hero-worker
// runGate composites at H = 768). A derivative at exactly that height
// is fidelity-identical to what the gate has always judged.
const MAX_HEIGHT = 768
const MAX_WIDTH = 2048
const JPEG_QUALITY = 85

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

function derivativePathFor(sourcePath: string): string {
  const slash = sourcePath.lastIndexOf('/')
  const dir = sourcePath.slice(0, slash)               // <uid>/refs
  const base = sourcePath.slice(slash + 1)             // ref-1.jpg
  const parent = dir.slice(0, dir.lastIndexOf('/'))    // <uid>
  const stem = base.replace(/\.[^.]+$/, '')
  return `${parent}/refs-small/${stem}.jpg`
}

// Bilinear downscale in the raw RGBA buffer, sampling from the
// output side (bounded work: one pass over the OUTPUT pixels).
// Only ever shrinks: callers pass scale <= 1 targets.
function downscaleRGBA(
  src: Uint8ClampedArray, sw: number, sh: number, dw: number, dh: number
): Uint8ClampedArray {
  const dst = new Uint8ClampedArray(dw * dh * 4)
  const xRatio = sw / dw, yRatio = sh / dh
  for (let y = 0; y < dh; y++) {
    const sy = (y + 0.5) * yRatio - 0.5
    const y0 = Math.max(0, Math.floor(sy)), y1 = Math.min(sh - 1, y0 + 1)
    const fy = Math.min(1, Math.max(0, sy - y0))
    for (let x = 0; x < dw; x++) {
      const sx = (x + 0.5) * xRatio - 0.5
      const x0 = Math.max(0, Math.floor(sx)), x1 = Math.min(sw - 1, x0 + 1)
      const fx = Math.min(1, Math.max(0, sx - x0))
      const di = (y * dw + x) * 4
      const i00 = (y0 * sw + x0) * 4, i10 = (y0 * sw + x1) * 4
      const i01 = (y1 * sw + x0) * 4, i11 = (y1 * sw + x1) * 4
      for (let c = 0; c < 4; c++) {
        const top = src[i00 + c] * (1 - fx) + src[i10 + c] * fx
        const bot = src[i01 + c] * (1 - fx) + src[i11 + c] * fx
        dst[di + c] = top * (1 - fy) + bot * fy
      }
    }
  }
  return dst
}

// Rotate an RGBA frame by whole quarter-turns counter-clockwise
// (RW-27 F-1). Only quarter-turns are ever applied: the correction
// targets in-plane rotation of the stored pixels (a sideways or
// upside-down photo), which is a quarter-turn class of defect.
function rotateRGBAQuarterTurns(
  src: Uint8ClampedArray, w: number, h: number, turnsCCW: number
): { data: Uint8ClampedArray, width: number, height: number } {
  const t = ((turnsCCW % 4) + 4) % 4
  if (t === 0) return { data: src, width: w, height: h }
  const dw = t === 2 ? w : h, dh = t === 2 ? h : w
  const dst = new Uint8ClampedArray(dw * dh * 4)
  for (let y = 0; y < dh; y++) {
    for (let x = 0; x < dw; x++) {
      // Source pixel for destination (x, y), per turn count:
      // 1 (90 CCW): src(w-1-y, x) · 2 (180): src(w-1-x, h-1-y)
      // 3 (90 CW):  src(y, h-1-x)
      let sx = x, sy = y
      if (t === 1) { sx = w - 1 - y; sy = x }
      else if (t === 2) { sx = w - 1 - x; sy = h - 1 - y }
      else { sx = y; sy = h - 1 - x }
      const di = (y * dw + x) * 4, si = (sy * w + sx) * 4
      dst[di] = src[si]; dst[di + 1] = src[si + 1]
      dst[di + 2] = src[si + 2]; dst[di + 3] = src[si + 3]
    }
  }
  return { data: dst, width: dw, height: dh }
}

// F-1 orientation probe: read the face's in-plane rotation (roll)
// from the identity screen on a bounded-size JPEG rendering of the
// decoded source frame. Roll is scale-invariant, so the probe runs
// at derivative scale. Returns the correction to apply, or null
// when no correction is warranted / the screen is unavailable.
// Correction direction is empirical, from the F-1 diagnosis: a
// measured roll of +90 deg is uprighted by rotating the pixels
// 90 deg counter-clockwise (post-rotation roll read -0.3 deg, and
// the identity comparison against the same person went from
// 0.19 not-delivered to 0.73 delivered).
async function probeOrientation(
  frame: DecodedFrame
): Promise<OrientationInfo | null> {
  if (!IDENTITY_SCREEN_URL) return null
  const scale = Math.min(1, MAX_HEIGHT / frame.height, MAX_WIDTH / frame.width)
  let probeData = frame.data, pw = frame.width, ph = frame.height
  if (scale < 1) {
    pw = Math.max(1, Math.round(frame.width * scale))
    ph = Math.max(1, Math.round(frame.height * scale))
    probeData = downscaleRGBA(frame.data, frame.width, frame.height, pw, ph)
  }
  const probeJpeg = new Uint8Array(await encodeJpeg(
    { data: probeData, width: pw, height: ph, colorSpace: 'srgb' },
    { quality: JPEG_QUALITY }))
  const body = await screenIntake(probeJpeg)
  if (!body || body.detected !== true) return null
  const roll = body.roll_deg
  if (typeof roll !== 'number' || Math.abs(roll) <= 30) return null
  const turnsCCW = Math.round(roll / 90)
  if (turnsCCW === 0) return null
  return { rollBeforeDeg: roll, correctedDeg: turnsCCW * 90 }
}


// detected, normalized face height >= ~50/768, |yaw| <= 25 deg) on
// the derivative via the identity-screen service and store the
// verdict on the reference row. The worker's anchor selection reads
// it (a failing photo is never the anchor while a passing one
// exists). When the service is not configured the verdict stays
// null — checks run when the service exists; go-live requires it.
//
// RW-27 F-1 (2026-11-03): the verdict record also carries the
// screen's roll read (roll_deg) and, when prep uprighted a sideways
// reference, orientation_corrected_deg + roll_before_deg (see
// probeOrientation / the serve handler). A correction that cannot
// be verified upright fails with retake_key 'rotated' instead of
// passing — a sideways reference silently poisoned every identity
// comparison against it (Sol's field failure: draws at 0.20-0.25
// against the 0.40 line while intake said PASS).
type OrientationInfo = {
  rollBeforeDeg: number, correctedDeg: number
}

async function screenIntake(
  jpegBytes: Uint8Array
): Promise<Record<string, unknown> | null> {
  if (!IDENTITY_SCREEN_URL) return null
  try {
    let binary = ''
    for (let i = 0; i < jpegBytes.length; i += 8192) {
      binary += String.fromCharCode(...jpegBytes.subarray(i, i + 8192))
    }
    const resp = await fetch(`${IDENTITY_SCREEN_URL}/intake`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        ...(IDENTITY_SCREEN_KEY ? { 'Authorization': `Bearer ${IDENTITY_SCREEN_KEY}` } : {})
      },
      body: JSON.stringify({ image_b64: btoa(binary) })
    })
    if (!resp.ok) {
      console.error(`[personal-ref-prep] intake check returned ${resp.status}`)
      return null
    }
    return await resp.json() as Record<string, unknown>
  } catch (e) {
    console.error('[personal-ref-prep] intake check failed:', (e as Error).message)
    return null
  }
}

async function checkIntakeQuality(
  sourcePath: string, derivative: Uint8Array,
  orientation: OrientationInfo | null = null
): Promise<Record<string, unknown> | null> {
  if (!IDENTITY_SCREEN_URL) return null
  const body = await screenIntake(derivative)
  if (!body) return null
  let verdict = typeof body.verdict === 'string' ? body.verdict : null
  let retakeKey = (body.retake_key as string | null) ?? null
  if (orientation) {
    // The derivative was rotated to upright this reference. If the
    // corrected pixels still do not read upright — face lost in the
    // rotation, or roll still beyond the threshold — the photo
    // could not be uprighted: fail it with the 'rotated' retake
    // key instead of letting it pass as an anchor.
    const rollAfter = typeof body.roll_deg === 'number' ? body.roll_deg : null
    if (body.detected !== true || rollAfter === null || Math.abs(rollAfter) > 30) {
      verdict = 'fail'
      retakeKey = 'rotated'
    }
  }
  const quality = {
    face_detected: body.detected === true,
    face_height_norm: body.height_norm ?? null,
    yaw_deg: body.yaw_deg ?? null,
    pitch_deg: body.pitch_deg ?? null,
    roll_deg: body.roll_deg ?? null,
    verdict,
    retake_key: retakeKey,
    ...(orientation ? {
      orientation_corrected_deg: orientation.correctedDeg,
      roll_before_deg: orientation.rollBeforeDeg
    } : {}),
    checked_at: new Date().toISOString()
  }
  const { error } = await supabase
    .from('personal_reference_photos')
    .update({ quality })
    .eq('storage_path', sourcePath)
  if (error) console.error('[personal-ref-prep] quality write failed:', error)
  return quality
}

serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'Method not allowed' }, 405)

    // Service-role only, same acceptance pattern as the worker: the
    // gateway has already validated the credential; accept the raw
    // env key, any key in SUPABASE_SECRET_KEYS, or a JWT whose role
    // claim is service_role (the Vault-held key pg_cron sends).
    const token = (req.headers.get('Authorization') ?? '').replace(/^Bearer\s+/i, '')
    let serviceCall = token === SUPABASE_SERVICE_ROLE_KEY
    if (!serviceCall) {
      const secretKeys = Deno.env.get('SUPABASE_SECRET_KEYS')
      if (secretKeys && token && secretKeys.includes(token)) serviceCall = true
    }
    if (!serviceCall && token) {
      try {
        const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        const payload = JSON.parse(atob(part + '='.repeat((4 - part.length % 4) % 4)))
        serviceCall = payload?.role === 'service_role'
      } catch { /* not a JWT */ }
    }
    if (!serviceCall) return jsonResponse({ error: 'Unauthorized' }, 401)

    const body = await req.json().catch(() => null)
    const path = body?.path
    // Only reference objects are preppable: <uid>/refs/<file>. This
    // keeps the function from being pointed at arbitrary bucket paths.
    if (typeof path !== 'string' ||
        !/^[^/]+\/refs\/[^/]+\.(jpe?g|png)$/i.test(path)) {
      return jsonResponse({ error: 'Invalid reference path' }, 400)
    }
    const derivativePath = derivativePathFor(path)

    const { data: blob, error: dlError } = await supabase.storage
      .from(BUCKET)
      .download(path)
    if (dlError || !blob) {
      return jsonResponse({ error: `Reference download failed: ${dlError?.message}` }, 502)
    }
    let sourceBytes = new Uint8Array(await blob.arrayBuffer())

    const isJpeg = sourceBytes[0] === 0xFF && sourceBytes[1] === 0xD8
    let decoder = 'jsquash'
    if (!isJpeg) {
      // Non-JPEG source (e.g. PNG): decode once with imagescript
      // (the fallback the worker used to perform) and re-encode to
      // JPEG, then run the uniform JPEG path below — orientation
      // probing/correction included. Registered references are
      // JPEG in practice (the observed wedge class).
      const img = await Image.decode(sourceBytes)
      sourceBytes = await img.encodeJPEG(JPEG_QUALITY)
      decoder = 'imagescript-fallback'
    }

    let frame = await decodeJpeg(sourceBytes)

    // RW-27 F-1: upright a sideways reference BEFORE the derivative
    // is encoded. Only the derivative is corrected — the stored
    // original is never rewritten (retention ruling: originals are
    // kept as uploaded; derivatives are working copies).
    const orientation = await probeOrientation(frame)
    if (orientation) {
      const turnsCCW = orientation.correctedDeg / 90
      const rotated = rotateRGBAQuarterTurns(frame.data, frame.width, frame.height, turnsCCW)
      frame = {
        data: rotated.data, width: rotated.width, height: rotated.height,
        colorSpace: frame.colorSpace
      }
    }

    const w = frame.width, h = frame.height
    const scale = Math.min(1, MAX_HEIGHT / h, MAX_WIDTH / w)
    let derivative: Uint8Array
    let outW = w, outH = h
    if (scale < 1) {
      outW = Math.max(1, Math.round(w * scale))
      outH = Math.max(1, Math.round(h * scale))
      const small = downscaleRGBA(frame.data, w, h, outW, outH)
      derivative = new Uint8Array(await encodeJpeg(
        { data: small, width: outW, height: outH, colorSpace: 'srgb' },
        { quality: JPEG_QUALITY }))
    } else {
      // Already within bounds: still normalize to a JPEG derivative
      // so the worker's composite input is uniform.
      derivative = new Uint8Array(await encodeJpeg(
        { data: frame.data, width: w, height: h, colorSpace: 'srgb' },
        { quality: JPEG_QUALITY }))
    }

    const { error: upError } = await supabase.storage
      .from(BUCKET)
      .upload(derivativePath, derivative, { contentType: 'image/jpeg', upsert: true })
    if (upError) {
      return jsonResponse({ error: `Derivative upload failed: ${upError.message}` }, 502)
    }
    const quality = await checkIntakeQuality(path, derivative, orientation)
    return jsonResponse({
      ok: true, source_path: path, derivative_path: derivativePath,
      source_width: w, source_height: h,
      derivative_width: outW, derivative_height: outH,
      derivative_bytes: derivative.length, decoder,
      orientation_corrected_deg: orientation?.correctedDeg ?? null,
      quality
    })
  } catch (error) {
    console.error('[personal-ref-prep] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
