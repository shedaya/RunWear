// Supabase Edge Function: generate-hero-image
// Processes generation_jobs and creates hero images via Replicate API

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const REPLICATE_API_TOKEN = Deno.env.get('REPLICATE_API_TOKEN')!
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

serve(async (req) => {
  try {
    const url = new URL(req.url)

    // Handle Replicate webhook callback
    if (req.method === 'POST' && url.searchParams.has('job_id')) {
      return await handleReplicateWebhook(req)
    }

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

  // Get queued jobs (limit to avoid timeout)
  const { data: jobs, error } = await supabase
    .from('generation_jobs')
    .select('*')
    .eq('status', 'QUEUED')
    .limit(5)

  if (error) {
    console.error('[generate-hero-image] Error fetching jobs:', error)
    throw error
  }

  if (!jobs || jobs.length === 0) {
    console.log('[generate-hero-image] No queued jobs found')
    return new Response(JSON.stringify({ message: 'No queued jobs' }), {
      headers: { 'Content-Type': 'application/json' }
    })
  }

  console.log(`[generate-hero-image] Found ${jobs.length} queued jobs`)

  const results = []

  for (const job of jobs) {
    try {
      // Update status to PROCESSING
      await supabase
        .from('generation_jobs')
        .update({
          status: 'PROCESSING',
          started_at: new Date().toISOString()
        })
        .eq('id', job.id)

      console.log(`[generate-hero-image] Processing job: ${job.combination_id}`)

      // Build webhook URL with job_id
      const webhookUrl = `${SUPABASE_URL}/functions/v1/generate-hero-image?job_id=${job.id}`

      // Call Replicate API
      const response = await fetch('https://api.replicate.com/v1/predictions', {
        method: 'POST',
        headers: {
          'Authorization': `Token ${REPLICATE_API_TOKEN}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          version: "black-forest-labs/flux-schnell",
          input: {
            prompt: job.prompt,
            num_outputs: 1,
            aspect_ratio: "2:3",  // Portrait for hero images
            output_format: "png",
            output_quality: 90
          },
          webhook: webhookUrl,
          webhook_events_filter: ["completed"]
        })
      })

      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`Replicate API error: ${response.status} ${errorText}`)
      }

      const prediction = await response.json()

      // Store replicate_id
      await supabase
        .from('generation_jobs')
        .update({ replicate_id: prediction.id })
        .eq('id', job.id)

      console.log(`[generate-hero-image] Started prediction: ${prediction.id} for ${job.combination_id}`)
      results.push({ job_id: job.id, replicate_id: prediction.id, status: 'started' })

    } catch (jobError) {
      console.error(`[generate-hero-image] Error processing job ${job.id}:`, jobError)

      // Mark job as failed
      await supabase
        .from('generation_jobs')
        .update({
          status: 'FAILED',
          error_message: jobError.message
        })
        .eq('id', job.id)

      results.push({ job_id: job.id, status: 'failed', error: jobError.message })
    }
  }

  return new Response(JSON.stringify({ processed: results }), {
    headers: { 'Content-Type': 'application/json' }
  })
}

async function handleReplicateWebhook(req: Request) {
  const url = new URL(req.url)
  const jobId = url.searchParams.get('job_id')

  console.log(`[generate-hero-image] Webhook received for job: ${jobId}`)

  const payload = await req.json()
  console.log(`[generate-hero-image] Webhook payload status: ${payload.status}`)

  if (payload.status !== 'succeeded') {
    console.error(`[generate-hero-image] Prediction failed:`, payload.error)

    // Mark job as failed
    await supabase
      .from('generation_jobs')
      .update({
        status: 'FAILED',
        error_message: payload.error || 'Prediction failed'
      })
      .eq('id', jobId)

    return new Response(JSON.stringify({ error: 'Prediction failed' }), {
      status: 200, // Return 200 to acknowledge webhook
      headers: { 'Content-Type': 'application/json' }
    })
  }

  // Get the job details
  const { data: job, error: jobError } = await supabase
    .from('generation_jobs')
    .select('*')
    .eq('id', jobId)
    .single()

  if (jobError || !job) {
    console.error(`[generate-hero-image] Job not found: ${jobId}`, jobError)
    return new Response(JSON.stringify({ error: 'Job not found' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }

  try {
    // Get the image URL from Replicate output
    const imageUrl = payload.output?.[0]
    if (!imageUrl) {
      throw new Error('No image URL in Replicate output')
    }

    console.log(`[generate-hero-image] Downloading image from: ${imageUrl}`)

    // Download the image
    const imageResponse = await fetch(imageUrl)
    if (!imageResponse.ok) {
      throw new Error(`Failed to download image: ${imageResponse.status}`)
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
      throw new Error(`Storage upload failed: ${uploadError.message}`)
    }

    // Get public URL
    const { data: publicUrlData } = supabase
      .storage
      .from('hero-images')
      .getPublicUrl(storagePath)

    const publicUrl = publicUrlData.publicUrl
    console.log(`[generate-hero-image] Uploaded to: ${publicUrl}`)

    // CRITICAL: Insert into generated_images table
    const { error: insertError } = await supabase
      .from('generated_images')
      .insert({
        combination_id: job.combination_id,
        image_url: publicUrl,
        prompt: job.prompt
      })

    if (insertError) {
      // Check if it's a duplicate (might have been inserted already)
      if (insertError.code === '23505') {
        console.log(`[generate-hero-image] Image already exists for ${job.combination_id}`)
      } else {
        throw new Error(`Insert failed: ${insertError.message}`)
      }
    } else {
      console.log(`[generate-hero-image] Inserted into generated_images: ${job.combination_id}`)
    }

    // CRITICAL: Update job status to COMPLETED
    const { error: updateError } = await supabase
      .from('generation_jobs')
      .update({
        status: 'COMPLETED',
        completed_at: new Date().toISOString()
      })
      .eq('id', jobId)

    if (updateError) {
      throw new Error(`Status update failed: ${updateError.message}`)
    }

    console.log(`[generate-hero-image] Job completed: ${job.combination_id}`)

    return new Response(JSON.stringify({
      success: true,
      combination_id: job.combination_id,
      image_url: publicUrl
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })

  } catch (error) {
    console.error(`[generate-hero-image] Error in webhook handler:`, error)

    // Mark job as failed
    await supabase
      .from('generation_jobs')
      .update({
        status: 'FAILED',
        error_message: error.message
      })
      .eq('id', jobId)

    return new Response(JSON.stringify({ error: error.message }), {
      status: 200, // Return 200 to acknowledge webhook
      headers: { 'Content-Type': 'application/json' }
    })
  }
}
