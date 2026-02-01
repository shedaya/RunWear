// Check what's in Storage vs Database
const SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4";

async function main() {
    // Count jobs by status
    console.log("=== Job Status Counts ===");

    const statuses = ['QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'];
    for (const status of statuses) {
        const response = await fetch(
            `${SUPABASE_URL}/rest/v1/generation_jobs?status=eq.${status}&select=id`,
            { headers: { 'apikey': SUPABASE_ANON_KEY } }
        );
        const jobs = await response.json();
        console.log(`${status}: ${jobs.length}`);
    }

    // Count images in DB
    console.log("\n=== Images in Database ===");
    const imagesResponse = await fetch(
        `${SUPABASE_URL}/rest/v1/generated_images?select=id`,
        { headers: { 'apikey': SUPABASE_ANON_KEY } }
    );
    const images = await imagesResponse.json();
    console.log(`Total images in generated_images: ${images.length}`);

    // List storage folders (if accessible)
    console.log("\n=== Storage Check ===");
    try {
        const storageResponse = await fetch(
            `${SUPABASE_URL}/storage/v1/object/list/hero-images`,
            {
                headers: {
                    'apikey': SUPABASE_ANON_KEY,
                    'Authorization': `Bearer ${SUPABASE_ANON_KEY}`
                },
                method: 'POST',
                body: JSON.stringify({ prefix: '', limit: 100 })
            }
        );
        const folders = await storageResponse.json();
        console.log(`Storage folders/files: ${JSON.stringify(folders).slice(0, 500)}`);
    } catch (e) {
        console.log(`Storage check failed: ${e.message}`);
    }
}

main().catch(console.error);
