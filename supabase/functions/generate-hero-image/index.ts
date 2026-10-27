// Supabase Edge Function: generate-hero-image
// Processes generation_jobs and creates hero images via Replicate API
// Uses polling instead of webhooks for reliability

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const REPLICATE_API_TOKEN = Deno.env.get('REPLICATE_API_TOKEN')!
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

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

serve(async (req) => {
  try {
    // Handle trigger to process queued jobs
    if (req.method === 'POST') {
      return await processQueuedJobs()
    }

    return new Response(JSON.stringify({ error: 'Method not allowed' }), {
      status: 405,
      headers: { 'Content-Type': 'application/json' }
    })
  } catch (error) {
    console.error('Edge function error:', error)
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    })
  }
})

async function processQueuedJobs() {
  console.log('[generate-hero-image] Processing queued jobs...')

  // Get ONE queued job at a time (polling approach)
  const { data: jobs, error } = await supabase
    .from('generation_jobs')
    .select('*')
    .eq('status', 'QUEUED')
    .limit(1)

  if (error) {
    console.error('[generate-hero-image] Error fetching jobs:', error)
    throw error
  }

  if (!jobs || jobs.length === 0) {
    return new Response(JSON.stringify({ message: 'No queued jobs' }), {
      headers: { 'Content-Type': 'application/json' }
    })
  }

  const job = jobs[0]
  console.log(`[generate-hero-image] Processing: ${job.combination_id}`)

  try {
    // Update status to PROCESSING
    await supabase
      .from('generation_jobs')
      .update({
        status: 'PROCESSING',
        started_at: new Date().toISOString()
      })
      .eq('id', job.id)

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

    if (result.status === 'succeeded') {
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
      // variant). Review gate: new generations land as 'pending' and are
      // NOT served until a human approves them (clients only serve
      // 'approved'/legacy rows). If the review-gate migration has not been
      // applied yet (column missing: PG 42703 / PostgREST PGRST204), fall
      // back to the legacy insert so generation never breaks — the row is
      // then immediately servable, exactly as before the gate existed.
      let { error: insertError } = await supabase
        .from('generated_images')
        .insert({
          combination_id: job.combination_id,
          image_url: publicUrl,
          prompt: prompt,
          review_status: 'pending'
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

      return new Response(JSON.stringify({
        success: true,
        combination_id: job.combination_id,
        image_url: publicUrl
      }), {
        headers: { 'Content-Type': 'application/json' }
      })

    } else {
      throw new Error(`Prediction failed: ${result.error || result.status}`)
    }

  } catch (jobError) {
    console.error(`[generate-hero-image] Error:`, jobError)

    await supabase
      .from('generation_jobs')
      .update({
        status: 'FAILED',
        error_message: jobError.message
      })
      .eq('id', job.id)

    return new Response(JSON.stringify({
      error: jobError.message,
      job_id: job.id
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    })
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
