const SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4";

async function main() {
    const statuses = ['QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'];

    console.log("=== Generation Job Status ===");
    for (const status of statuses) {
        const r = await fetch(`${SUPABASE_URL}/rest/v1/generation_jobs?status=eq.${status}&select=id`, {
            headers: { 'apikey': SUPABASE_ANON_KEY }
        });
        const data = await r.json();
        console.log(`${status}: ${data.length}`);
    }

    console.log("\n=== Images in Database ===");
    const imgR = await fetch(`${SUPABASE_URL}/rest/v1/generated_images?select=id`, {
        headers: { 'apikey': SUPABASE_ANON_KEY }
    });
    const imgs = await imgR.json();
    console.log(`Total: ${imgs.length}`);
}

main();
