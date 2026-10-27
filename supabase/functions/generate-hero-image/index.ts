// Supabase Edge Function: generate-hero-image
// Processes generation_jobs and creates hero images via Replicate API
// Uses polling instead of webhooks for reliability
//
// Invocation modes:
//   * Legacy single-job (original behavior, used by scripts/*.js):
//       POST with no drain parameter -> processes ONE queued job and
//       returns { success, combination_id, image_url } /
//       { message: 'No queued jobs' } / 500 { error, job_id }.
//   * Batch drain (revival scheduler, 2026-10-27):
//       POST ?drain=N  or  POST JSON body { "drain": N } / { "drain": true }
//       Processes up to N queued jobs (oldest first), each independent —
//       one failure does not sink the batch — and returns a per-job
//       summary. N defaults to DEFAULT_DRAIN_LIMIT and is clamped to
//       MAX_DRAIN_LIMIT, and the batch is further clamped by the daily
//       spend cap below. Triggered every 30 minutes by pg_cron (see
//       supabase/migrations/20261027130000_hero_generation_scheduler.sql)
//       or, as a fallback, by .github/workflows/hero-generation-drain.yml.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const REPLICATE_API_TOKEN = Deno.env.get('REPLICATE_API_TOKEN')!
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

// SPEND CAP: maximum number of images generated per UTC day across ALL
// invocations of this function (legacy single-job calls included — every
// completion counts). This is the one number that bounds daily Replicate
// spend; raise or lower it here. Enforcement is deliberately simple: the
// count is read once per invocation, so two invocations running at the
// same moment can overshoot by a few images. Acceptable at this scale.
const DAILY_GENERATION_CAP = 50

// Batch drain sizing. Each job can take up to ~60s of polling, so the
// batch must stay small enough to finish inside the edge-function wall
// clock: default 5, hard max 10 per invocation.
const DEFAULT_DRAIN_LIMIT = 5
const MAX_DRAIN_LIMIT = 10

// Prompt hardening + review gate (2026-10-27), mirrors HeroPoolPolicy.kt.
// Every prompt is hardened HERE, server-side, so jobs queued by older
// clients (whose buildPrompt predates the anatomy block) are covered too.
// FLUX has no negative-prompt channel, so guidance is phrased as positive
// constraints plus one explicit Avoid clause.
const QUALITY_MARKER = 'ANATOMY & QUALITY:'
const QUALITY_SUFFIX =
  ` ${QUALITY_MARKER} anatomically correct human body with natural proportions; ` +
  `arms bent at the elbows and swinging naturally beside the torso, hands relaxed ` +
  `at the runner's sides — never reaching across the back or touching the shoulders; ` +
  `each hand with five correct fingers; legs in a natural mid-stride gait with knees ` +
  `and feet pointing in the direction of travel; if the runner is seen from behind, ` +
  `arms, legs and hands must remain anatomically coherent from the back view. ` +
  `Photorealistic, shot on a full-frame camera. Avoid: distorted or merged limbs, ` +
  `extra fingers, hands or limbs, warped joints, backwards-bending arms.`

function ensureQualitySuffix(prompt: string): string {
  return prompt.includes(QUALITY_MARKER) ? prompt : prompt + QUALITY_SUFFIX
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

serve(async (req) => {
  try {
    if (req.method === 'POST') {
      const drainLimit = await parseDrainLimit(req)
      if (drainLimit !== null) {
        return await drainQueuedJobs(drainLimit)
      }
      return await processQueuedJobs()
    }

    return jsonResponse({ error: 'Method not allowed' }, 405)
  } catch (error) {
    console.error('Edge function error:', error)
    return jsonResponse({ error: (error as Error).message }, 500)
  }
})

// Returns the requested drain batch size, or null when the caller did not
// ask for a drain (legacy single-job mode). The drain is requested either
// as a query parameter (?drain=5, bare ?drain means "default size") or as
// a JSON body field ({ "drain": 5 } / { "drain": true }).
async function parseDrainLimit(req: Request): Promise<number | null> {
  let raw: unknown = null

  const fromQuery = new URL(req.url).searchParams.get('drain')
  if (fromQuery !== null) {
    raw = fromQuery === '' ? true : fromQuery
  } else {
    try {
      const body = await req.json()
      if (body && typeof body === 'object' && 'drain' in body) {
        raw = (body as Record<string, unknown>).drain
      }
    } catch {
      // No/invalid JSON body — the legacy scripts POST with no body at all.
      raw = null
    }
  }

  if (raw === null || raw === undefined || raw === false) return null

  const parsed = raw === true ? DEFAULT_DRAIN_LIMIT : parseInt(String(raw), 10)
  const limit = Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_DRAIN_LIMIT
  return Math.min(limit, MAX_DRAIN_LIMIT)
}

// How many generations have completed today (UTC)? One COMPLETED job ==
// one generated image == one unit of Replicate spend.
async function countTodaysGenerations(): Promise<number> {
  const startOfUtcDay = new Date()
  startOfUtcDay.setUTCHours(0, 0, 0, 0)

  const { count, error } = await supabase
    .from('generation_jobs')
    .select('id', { count: 'exact', head: true })
    .eq('status', 'COMPLETED')
    .gte('completed_at', startOfUtcDay.toISOString())

  if (error) {
    console.error('[generate-hero-image] Error counting today\'s generations:', error)
    throw error
  }

  return count ?? 0
}

// Legacy single-job mode: process ONE queued job (the oldest). Response
// shapes are unchanged from the original implementation — the manual
// scripts branch on { success } / { message: 'No queued jobs' } / 500.
async function processQueuedJobs() {
  console.log('[generate-hero-image] Processing queued jobs...')

  const { data: jobs, error } = await supabase
    .from('generation_jobs')
    .select('*')
    .eq('status', 'QUEUED')
    .order('created_at', { ascending: true })
    .limit(1)

  if (error) {
    console.error('[generate-hero-image] Error fetching jobs:', error)
    throw error
  }

  if (!jobs || jobs.length === 0) {
    return jsonResponse({ message: 'No queued jobs' })
  }

  const job = jobs[0]
  const result = await processJob(job, { guardedClaim: false })

  if (result.ok) {
    return jsonResponse({
      success: true,
      combination_id: result.combination_id,
      image_url: result.image_url
    })
  }

  return jsonResponse({
    error: result.error,
    job_id: job.id
  }, 500)
}

// Batch drain mode: process up to `limit` queued jobs, oldest first. The
// batch is clamped by whatever is left of today's spend cap. Each job is
// claimed with a QUEUED-status guard so a concurrent invocation cannot
// process the same job twice, and each job succeeds or fails on its own.
async function drainQueuedJobs(limit: number) {
  console.log(`[generate-hero-image] Draining up to ${limit} queued jobs...`)

  const generatedTodayBefore = await countTodaysGenerations()
  const remainingByCap = Math.max(0, DAILY_GENERATION_CAP - generatedTodayBefore)
  const batchSize = Math.min(limit, remainingByCap)

  const baseSummary = {
    drained: true,
    daily_cap: DAILY_GENERATION_CAP,
    generated_today_before: generatedTodayBefore,
    cap_reached: remainingByCap <= 0
  }

  if (batchSize === 0) {
    return jsonResponse({
      ...baseSummary,
      message: 'Daily generation cap reached',
      attempted: 0,
      completed: 0,
      failed: 0,
      results: []
    })
  }

  const { data: jobs, error } = await supabase
    .from('generation_jobs')
    .select('*')
    .eq('status', 'QUEUED')
    .order('created_at', { ascending: true })
    .limit(batchSize)

  if (error) {
    console.error('[generate-hero-image] Error fetching jobs:', error)
    throw error
  }

  if (!jobs || jobs.length === 0) {
    return jsonResponse({
      ...baseSummary,
      message: 'No queued jobs',
      attempted: 0,
      completed: 0,
      failed: 0,
      results: []
    })
  }

  const results: Array<Record<string, unknown>> = []
  let completed = 0
  let failed = 0

  for (const job of jobs) {
    const result = await processJob(job, { guardedClaim: true })
    if (!result.ok && result.skipped) {
      // Another invocation claimed this job first; not a failure.
      results.push({
        job_id: job.id,
        combination_id: job.combination_id,
        status: 'SKIPPED',
        error: result.error
      })
    } else if (result.ok) {
      completed++
      results.push({
        job_id: job.id,
        combination_id: result.combination_id,
        status: 'COMPLETED',
        image_url: result.image_url
      })
    } else {
      failed++
      results.push({
        job_id: job.id,
        combination_id: job.combination_id,
        status: 'FAILED',
        error: result.error
      })
    }
  }

  return jsonResponse({
    ...baseSummary,
    attempted: results.length,
    completed,
    failed,
    results
  })
}

// Flat result shape (no discriminated union) so the file type-checks
// under any compiler strictness setting.
type ProcessJobResult = {
  ok: boolean,
  skipped?: boolean,
  combination_id: string,
  image_url?: string,
  error?: string
}

// Process a single generation job end to end: claim it, generate via
// Replicate, store the image, insert it into the pool as 'approved'
// (auto-replenishment), and mark the job COMPLETED — or mark it FAILED and
// report the error. Never throws; the caller decides how to present the
// outcome. With guardedClaim, the claim only lands if the job is still
// QUEUED, so concurrent drains cannot double-process it.
async function processJob(job: Record<string, unknown> & { id: unknown, combination_id: string, prompt: string },
                          options: { guardedClaim: boolean }): Promise<ProcessJobResult> {
  console.log(`[generate-hero-image] Processing: ${job.combination_id}`)

  try {
    // Claim the job (status -> PROCESSING)
    let claim = supabase
      .from('generation_jobs')
      .update({
        status: 'PROCESSING',
        started_at: new Date().toISOString()
      })
      .eq('id', job.id)

    if (options.guardedClaim) {
      const { data: claimed, error: claimError } = await claim
        .eq('status', 'QUEUED')
        .select('id')

      if (claimError) {
        throw new Error(`Claim failed: ${claimError.message}`)
      }
      if (!claimed || claimed.length === 0) {
        return {
          ok: false,
          skipped: true,
          combination_id: job.combination_id,
          error: 'Job was already claimed by another invocation'
        }
      }
    } else {
      // Legacy path: the original implementation updated unconditionally
      // and ignored the result. Kept exactly that way.
      await claim
    }

    const prompt = ensureQualitySuffix(job.prompt)

    // Call Replicate API (synchronous mode - no webhook)
    const response = await fetch('https://api.replicate.com/v1/predictions', {
      method: 'POST',
      headers: {
        'Authorization': `Token ${REPLICATE_API_TOKEN}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        version: "5599ed30703defd1d160a25a63321b4dec97101d98b4674bcc56e41f62f35637", // flux-schnell
        input: {
          prompt: prompt,
          num_outputs: 1,
          aspect_ratio: "2:3",
          output_format: "png",
          output_quality: 90
        }
      })
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Replicate API error: ${response.status} ${errorText}`)
    }

    const prediction = await response.json()
    console.log(`[generate-hero-image] Prediction created: ${prediction.id}`)

    // Update job with replicate_id
    await supabase
      .from('generation_jobs')
      .update({ replicate_id: prediction.id })
      .eq('id', job.id)

    // Poll for completion (max 60 seconds)
    const result = await pollForCompletion(prediction.id)

    if (result.status !== 'succeeded') {
      throw new Error(`Prediction failed: ${result.error || result.status}`)
    }

    // Get the image URL
    const imageUrl = result.output?.[0]
    if (!imageUrl) {
      throw new Error('No image URL in output')
    }

    console.log(`[generate-hero-image] Downloading from: ${imageUrl}`)

    // Download the image
    const imageResponse = await fetch(imageUrl)
    if (!imageResponse.ok) {
      throw new Error(`Failed to download: ${imageResponse.status}`)
    }
    const imageBlob = await imageResponse.blob()

    // Upload to Supabase Storage
    const storagePath = `${job.combination_id}/${job.combination_id}.png`
    const { error: uploadError } = await supabase
      .storage
      .from('hero-images')
      .upload(storagePath, imageBlob, {
        contentType: 'image/png',
        upsert: true
      })

    if (uploadError) {
      throw new Error(`Upload failed: ${uploadError.message}`)
    }

    // Get public URL
    const { data: publicUrlData } = supabase
      .storage
      .from('hero-images')
      .getPublicUrl(storagePath)

    const publicUrl = publicUrlData.publicUrl
    console.log(`[generate-hero-image] Uploaded to: ${publicUrl}`)

    // Insert into generated_images with full combination_id (including
    // variant). Auto-replenishment (Sol's call, 2026-10-27): new generations
    // are inserted as 'approved' and served immediately — there is no
    // pending-review queue. review_status remains as a retraction lever:
    // setting a row to 'rejected' pulls it from rotation (clients only
    // serve 'approved'/legacy rows). If the review-gate migration has not
    // been applied yet (column missing: PG 42703 / PostgREST PGRST204),
    // fall back to the legacy insert so generation never breaks — the row
    // is then immediately servable, exactly as before the column existed.
    let { error: insertError } = await supabase
      .from('generated_images')
      .insert({
        combination_id: job.combination_id,
        image_url: publicUrl,
        prompt: prompt,
        review_status: 'approved'
      })

    if (insertError && (insertError.code === '42703' || insertError.code === 'PGRST204')) {
      console.warn('[generate-hero-image] review_status column missing — legacy insert (apply the 2026-10-27 migration)')
      const retry = await supabase
        .from('generated_images')
        .insert({
          combination_id: job.combination_id,
          image_url: publicUrl,
          prompt: prompt
        })
      insertError = retry.error
    }

    // Skip duplicate key errors (23505) - image already exists
    if (insertError && insertError.code !== '23505') {
      throw new Error(`Insert failed: ${insertError.message}`)
    }

    // Update job to COMPLETED
    await supabase
      .from('generation_jobs')
      .update({
        status: 'COMPLETED',
        completed_at: new Date().toISOString()
      })
      .eq('id', job.id)

    console.log(`[generate-hero-image] COMPLETED: ${job.combination_id}`)

    return {
      ok: true,
      combination_id: job.combination_id,
      image_url: publicUrl
    }

  } catch (jobError) {
    console.error(`[generate-hero-image] Error:`, jobError)

    await supabase
      .from('generation_jobs')
      .update({
        status: 'FAILED',
        error_message: (jobError as Error).message
      })
      .eq('id', job.id)

    return {
      ok: false,
      skipped: false,
      combination_id: job.combination_id,
      error: (jobError as Error).message
    }
  }
}

async function pollForCompletion(predictionId: string, maxWaitMs = 60000) {
  const startTime = Date.now()
  const pollInterval = 1000 // 1 second

  while (Date.now() - startTime < maxWaitMs) {
    const response = await fetch(
      `https://api.replicate.com/v1/predictions/${predictionId}`,
      {
        headers: {
          'Authorization': `Token ${REPLICATE_API_TOKEN}`
        }
      }
    )

    if (!response.ok) {
      throw new Error(`Poll failed: ${response.status}`)
    }

    const prediction = await response.json()

    if (prediction.status === 'succeeded' || prediction.status === 'failed') {
      return prediction
    }

    // Wait before next poll
    await new Promise(resolve => setTimeout(resolve, pollInterval))
  }

  throw new Error('Prediction timed out')
}
