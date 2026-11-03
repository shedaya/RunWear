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

// RW-16.30 intake quality: run the shared intake policy (face
// detected, normalized face height >= ~50/768, |yaw| <= 25 deg) on
// the derivative via the identity-screen service and store the
// verdict on the reference row. The worker's anchor selection reads
// it (a failing photo is never the anchor while a passing one
// exists). When the service is not configured the verdict stays
// null — checks run when the service exists; go-live requires it.
async function checkIntakeQuality(
  sourcePath: string, derivative: Uint8Array
): Promise<Record<string, unknown> | null> {
  if (!IDENTITY_SCREEN_URL) return null
  try {
    let binary = ''
    for (let i = 0; i < derivative.length; i += 8192) {
      binary += String.fromCharCode(...derivative.subarray(i, i + 8192))
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
    const body = await resp.json() as Record<string, unknown>
    const quality = {
      face_detected: body.detected === true,
      face_height_norm: body.height_norm ?? null,
      yaw_deg: body.yaw_deg ?? null,
      pitch_deg: body.pitch_deg ?? null,
      verdict: typeof body.verdict === 'string' ? body.verdict : null,
      retake_key: body.retake_key ?? null,
      checked_at: new Date().toISOString()
    }
    const { error } = await supabase
      .from('personal_reference_photos')
      .update({ quality })
      .eq('storage_path', sourcePath)
    if (error) console.error('[personal-ref-prep] quality write failed:', error)
    return quality
  } catch (e) {
    console.error('[personal-ref-prep] intake check failed:', (e as Error).message)
    return null
  }
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
    const sourceBytes = new Uint8Array(await blob.arrayBuffer())

    const isJpeg = sourceBytes[0] === 0xFF && sourceBytes[1] === 0xD8
    if (!isJpeg) {
      // Non-JPEG source (e.g. PNG): fall back to the imagescript
      // decode the worker used to perform. Registered references are
      // JPEG in practice (the observed wedge class); this path keeps
      // the function correct for other formats.
      const img = await Image.decode(sourceBytes)
      const w = img.width, h = img.height
      const scale = Math.min(1, MAX_HEIGHT / h, MAX_WIDTH / w)
      const out = scale < 1
        ? img.resize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)))
        : img
      const derivative = await out.encodeJPEG(JPEG_QUALITY)
      const { error: upError } = await supabase.storage
        .from(BUCKET)
        .upload(derivativePath, derivative, { contentType: 'image/jpeg', upsert: true })
      if (upError) {
        return jsonResponse({ error: `Derivative upload failed: ${upError.message}` }, 502)
      }
      const quality = await checkIntakeQuality(path, derivative)
      return jsonResponse({
        ok: true, source_path: path, derivative_path: derivativePath,
        source_width: w, source_height: h,
        derivative_width: out.width, derivative_height: out.height,
        derivative_bytes: derivative.length, decoder: 'imagescript-fallback',
        quality
      })
    }

    const frame = await decodeJpeg(sourceBytes)
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
    const quality = await checkIntakeQuality(path, derivative)
    return jsonResponse({
      ok: true, source_path: path, derivative_path: derivativePath,
      source_width: w, source_height: h,
      derivative_width: outW, derivative_height: outH,
      derivative_bytes: derivative.length,
      quality
    })
  } catch (error) {
    console.error('[personal-ref-prep] error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})
