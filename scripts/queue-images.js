// Node.js script to queue hero image generation jobs
// Run with: node queue-images.js

const SUPABASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4";

const GENDERS = ["MALE", "FEMALE"];
const WEATHERS = ["CLEAR", "CLOUDY", "RAINY", "SNOWY"];
const TEMPS = ["HOT", "WARM", "MILD", "COOL", "COLD", "FREEZING"];
const TIMES = ["DAWN", "MIDDAY", "DUSK", "NIGHT"];

// Outfit descriptions - these are representative; actual app uses dynamic from outfit recommendation
const OUTFITS = {
    HOT: "tank top / singlet, short shorts (3\"), sunglasses",
    WARM: "short sleeve shirt, running shorts (5-7\"), running cap",
    MILD: "light long sleeve, running shorts (5-7\")",
    COOL: "light long sleeve, light running jacket, light tights, ear warmer / headband",
    COLD: "thermal long sleeve, insulated jacket, thermal tights, light beanie, thermal gloves, neck gaiter / buff",
    FREEZING: "thermal long sleeve, insulated jacket, thermal tights, thermal beanie, mittens, neck gaiter / buff"
};

// Rain/wind variations
const RAIN_OUTFITS = {
    HOT: "short sleeve shirt, rain jacket, short shorts (3\"), running cap",
    WARM: "short sleeve shirt, rain jacket, running shorts (5-7\"), running cap",
    MILD: "light long sleeve, rain jacket, running shorts (5-7\"), running cap",
    COOL: "light long sleeve, rain jacket, light tights, running cap, light gloves",
    COLD: "thermal long sleeve, rain jacket, thermal tights, light beanie, thermal gloves",
    FREEZING: "thermal long sleeve, rain jacket, thermal tights, thermal beanie, thermal gloves, neck gaiter / buff"
};

function isValidCombination(weather, temp) {
    // Snow not possible when hot or warm
    if (weather === "SNOWY" && (temp === "HOT" || temp === "WARM")) {
        return false;
    }
    return true;
}

function getOutfit(weather, temp) {
    if (weather === "RAINY") {
        return RAIN_OUTFITS[temp] || OUTFITS[temp];
    }
    return OUTFITS[temp];
}

function buildPrompt(gender, weather, temp, time) {
    const genderDesc = gender === "MALE" ? "male" : "female";
    const weatherDesc = weather.toLowerCase();
    const tempDesc = temp.toLowerCase();
    const timeDesc = {
        DAWN: "early morning golden hour",
        MIDDAY: "bright midday",
        DUSK: "evening golden hour",
        NIGHT: "night with street lights"
    }[time];

    // Random backgrounds for variety
    const backgrounds = [
        'city street with buildings in background',
        'urban park with trees',
        'waterfront boardwalk',
        'scenic trail with nature',
        'downtown area with shops',
        'bridge with city skyline',
        'tree-lined avenue'
    ];
    const background = backgrounds[Math.floor(Math.random() * backgrounds.length)];

    // Mood based on conditions
    let mood;
    if (weather === 'RAIN') mood = 'determined, pushing through the rain';
    else if (weather === 'SNOW') mood = 'resilient, winter warrior';
    else if (temp === 'HOT') mood = 'energetic, summer vibes';
    else if (temp === 'FREEZING') mood = 'tough, braving the cold';
    else mood = 'focused, confident stride';

    const outfit = getOutfit(weather, temp);

    return `A ${genderDesc} runner in their 30s running mid-stride along a ${background}. They are wearing ${outfit} appropriate for ${weatherDesc} ${tempDesc} weather. Time of day: ${timeDesc}. Professional running photography, dynamic action shot, high quality, sharp focus. MOOD: ${mood}`;
}

async function queueJob(combinationId, prompt) {
    const response = await fetch(`${SUPABASE_URL}/rest/v1/generation_jobs`, {
        method: 'POST',
        headers: {
            'apikey': SUPABASE_ANON_KEY,
            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
            'Content-Type': 'application/json',
            'Prefer': 'return=minimal'
        },
        body: JSON.stringify({
            combination_id: combinationId,
            prompt: prompt,
            status: 'QUEUED'
        })
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Failed to queue ${combinationId}: ${response.status} ${text}`);
    }
}

async function main() {
    console.log("=== RunWear Hero Image Library Rebuild ===\n");

    const TARGET = 1000;
    const VARIANTS_PER_COMBO = 6;
    let queued = 0;
    const errors = [];

    // Generate all valid combinations
    const jobs = [];

    for (const gender of GENDERS) {
        for (const weather of WEATHERS) {
            for (const temp of TEMPS) {
                if (!isValidCombination(weather, temp)) {
                    continue;
                }

                for (const time of TIMES) {
                    for (let variant = 1; variant <= VARIANTS_PER_COMBO; variant++) {
                        if (jobs.length >= TARGET) break;

                        const combinationId = `${gender}_${weather}_${temp}_${time}_v${variant}`;
                        const prompt = buildPrompt(gender, weather, temp, time);

                        jobs.push({ combinationId, prompt });
                    }
                    if (jobs.length >= TARGET) break;
                }
                if (jobs.length >= TARGET) break;
            }
            if (jobs.length >= TARGET) break;
        }
        if (jobs.length >= TARGET) break;
    }

    console.log(`Queueing ${jobs.length} image generation jobs...\n`);

    // Queue in batches of 10 with delay to avoid rate limiting
    const BATCH_SIZE = 10;
    for (let i = 0; i < jobs.length; i += BATCH_SIZE) {
        const batch = jobs.slice(i, i + BATCH_SIZE);

        await Promise.all(batch.map(async (job) => {
            try {
                await queueJob(job.combinationId, job.prompt);
                queued++;
            } catch (e) {
                errors.push(e.message);
            }
        }));

        // Progress update every 100
        if ((i + BATCH_SIZE) % 100 === 0 || i + BATCH_SIZE >= jobs.length) {
            console.log(`  Progress: ${Math.min(i + BATCH_SIZE, jobs.length)} / ${jobs.length}`);
        }

        // Small delay between batches
        await new Promise(r => setTimeout(r, 100));
    }

    console.log(`\n=== Complete ===`);
    console.log(`Successfully queued: ${queued}`);
    if (errors.length > 0) {
        console.log(`Errors: ${errors.length}`);
        errors.slice(0, 5).forEach(e => console.log(`  - ${e}`));
    }

    console.log(`\nNext: Run your image generation worker to process the queue.`);
}

main().catch(console.error);
