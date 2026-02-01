// Audit script for hero image replenishment workflow
// Usage: node audit-replenishment.js [COMBINATION_ID_PREFIX]
// Example: node audit-replenishment.js MALE_CLEAR_MILD

const SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4";

async function query(endpoint) {
    const r = await fetch(`${SUPABASE_URL}/rest/v1/${endpoint}`, {
        headers: { 'apikey': SUPABASE_ANON_KEY }
    });
    return r.json();
}

async function auditCombination(prefix) {
    console.log(`\n=== Auditing: ${prefix}* ===\n`);

    // Get all jobs for this combination
    const jobs = await query(`generation_jobs?combination_id=like.${prefix}*&order=requested_at.desc&limit=20`);

    console.log(`Generation Jobs (${Array.isArray(jobs) ? jobs.length : 0}):`);
    if (!Array.isArray(jobs) || jobs.length === 0) {
        console.log("  No jobs found for this combination");
    } else {
        jobs.forEach(j => {
            const time = new Date(j.requested_at).toLocaleString();
            console.log(`  ${j.status.padEnd(12)} ${j.combination_id.padEnd(35)} ${time}`);
            if (j.error_message) {
                console.log(`               ERROR: ${j.error_message}`);
            }
        });
    }

    // Get all images for this combination
    const images = await query(`generated_images?combination_id=like.${prefix}*&limit=20`);

    console.log(`\nGenerated Images (${Array.isArray(images) ? images.length : 0}):`);
    if (!Array.isArray(images) || images.length === 0) {
        console.log("  No images found for this combination");
    } else {
        images.forEach(img => {
            console.log(`  ${img.combination_id.padEnd(35)} ${img.image_url ? '✓' : ''}`);
        });
    }

    // Summary
    const jobList = Array.isArray(jobs) ? jobs : [];
    const imageList = Array.isArray(images) ? images : [];

    console.log(`\n--- Summary ---`);
    console.log(`Total jobs: ${jobList.length}`);
    console.log(`  QUEUED: ${jobList.filter(j => j.status === 'QUEUED').length}`);
    console.log(`  PROCESSING: ${jobList.filter(j => j.status === 'PROCESSING').length}`);
    console.log(`  COMPLETED: ${jobList.filter(j => j.status === 'COMPLETED').length}`);
    console.log(`  FAILED: ${jobList.filter(j => j.status === 'FAILED').length}`);
    console.log(`Total images: ${imageList.length}`);

    if (imageList.length < 5) {
        console.log(`\n[!] Less than 5 variants - replenishment should trigger on next app load`);
    } else {
        console.log(`\n[OK] 5+ variants available - no replenishment needed`);
    }
}

async function showRecentActivity() {
    console.log("=== Recent Job Activity (last 10) ===\n");

    const recentJobs = await query(`generation_jobs?order=requested_at.desc&limit=10`);

    if (Array.isArray(recentJobs)) {
        recentJobs.forEach(j => {
            const time = new Date(j.requested_at).toLocaleString();
            console.log(`${j.status.padEnd(12)} ${j.combination_id.padEnd(40)} ${time}`);
        });
    } else {
        console.log("  Could not fetch recent jobs");
    }
}

async function main() {
    const prefix = process.argv[2];

    if (prefix) {
        await auditCombination(prefix);
    } else {
        // Show overall status and recent activity
        console.log("=== Overall Status ===\n");

        const statuses = ['QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'];
        for (const status of statuses) {
            const jobs = await query(`generation_jobs?status=eq.${status}&select=id`);
            console.log(`${status}: ${jobs.length}`);
        }

        const images = await query(`generated_images?select=id`);
        console.log(`\nTotal images in database: ${images.length}`);

        console.log("\n");
        await showRecentActivity();

        console.log("\n---");
        console.log("Usage: node audit-replenishment.js [COMBINATION_PREFIX]");
        console.log("Example: node audit-replenishment.js MALE_CLEAR_MILD");
        console.log("         node audit-replenishment.js FEMALE_RAINY_COOL_DUSK");
    }
}

main().catch(console.error);
