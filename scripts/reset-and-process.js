// Reset stuck PROCESSING jobs and trigger parallel processing
const SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4";
const EDGE_FUNCTION_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co/functions/v1/generate-hero-image";

async function resetStuckJobs() {
    console.log("Resetting stuck PROCESSING jobs to QUEUED...");

    const response = await fetch(`${SUPABASE_URL}/rest/v1/generation_jobs?status=eq.PROCESSING`, {
        method: 'PATCH',
        headers: {
            'apikey': SUPABASE_ANON_KEY,
            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
            'Content-Type': 'application/json',
            'Prefer': 'return=representation'
        },
        body: JSON.stringify({
            status: 'QUEUED',
            started_at: null,
            replicate_id: null
        })
    });

    if (!response.ok) {
        const text = await response.text();
        console.error(`Reset failed: ${response.status} ${text}`);
        return 0;
    }

    const data = await response.json();
    console.log(`Reset ${data.length} jobs back to QUEUED`);
    return data.length;
}

async function triggerWorker(workerId) {
    try {
        const response = await fetch(EDGE_FUNCTION_URL, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
                'Content-Type': 'application/json'
            }
        });

        const result = await response.json();
        if (result.success) {
            return { workerId, success: true, combination: result.combination_id };
        } else if (result.message === 'No queued jobs') {
            return { workerId, success: false, done: true };
        } else {
            return { workerId, success: false, error: result.error };
        }
    } catch (e) {
        return { workerId, success: false, error: e.message };
    }
}

async function processWithParallelWorkers(numWorkers = 5) {
    console.log(`\nStarting ${numWorkers} parallel workers...`);

    let completed = 0;
    let failed = 0;
    let running = true;
    let allDone = false;

    while (running && !allDone) {
        // Launch workers in parallel
        const promises = [];
        for (let i = 0; i < numWorkers; i++) {
            promises.push(triggerWorker(i));
        }

        const results = await Promise.all(promises);

        let doneCount = 0;
        for (const result of results) {
            if (result.success) {
                completed++;
                console.log(`Worker ${result.workerId}: Completed ${result.combination}`);
            } else if (result.done) {
                doneCount++;
            } else if (result.error) {
                failed++;
                console.log(`Worker ${result.workerId}: Error - ${result.error}`);
            }
        }

        // If all workers returned "no jobs", we're done
        if (doneCount === numWorkers) {
            allDone = true;
        }

        // Progress update
        if (completed > 0 && completed % 10 === 0) {
            console.log(`\n--- Progress: ${completed} completed, ${failed} failed ---\n`);
        }

        // Small delay to avoid overwhelming the edge function
        if (!allDone) {
            await new Promise(r => setTimeout(r, 500));
        }
    }

    return { completed, failed };
}

async function main() {
    console.log("=== RunWear Image Generation (Polling Mode) ===\n");

    // Reset stuck jobs first
    await resetStuckJobs();

    // Check initial status
    const statusCheck = await fetch(`${SUPABASE_URL}/rest/v1/generation_jobs?status=eq.QUEUED&select=id`, {
        headers: { 'apikey': SUPABASE_ANON_KEY }
    });
    const queuedJobs = await statusCheck.json();
    console.log(`\nQueued jobs to process: ${queuedJobs.length}`);

    if (queuedJobs.length === 0) {
        console.log("No jobs to process!");
        return;
    }

    // Start parallel processing
    const startTime = Date.now();
    const { completed, failed } = await processWithParallelWorkers(5);
    const elapsed = Math.round((Date.now() - startTime) / 1000);

    console.log(`\n=== Complete ===`);
    console.log(`Completed: ${completed}`);
    console.log(`Failed: ${failed}`);
    console.log(`Time: ${elapsed} seconds`);
}

main().catch(console.error);
