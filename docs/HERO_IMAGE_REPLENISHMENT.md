# Hero Image Replenishment System

## Overview

RunWear uses AI-generated hero images that match the current weather conditions and outfit recommendations. The system automatically replenishes images based on user demand, ensuring variety and relevance.

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  App/PWA    │────▶│  Supabase DB     │────▶│  Edge Function  │
│  (Client)   │     │  generation_jobs │     │  generate-hero  │
└─────────────┘     └──────────────────┘     └────────┬────────┘
                                                      │
                    ┌──────────────────┐              │
                    │  generated_images│◀─────────────┤
                    │  (tracking)      │              │
                    └──────────────────┘              ▼
                                              ┌───────────────┐
                    ┌──────────────────┐      │  Replicate    │
                    │  Supabase Storage│◀─────│  API (Flux)   │
                    │  hero-images/    │      └───────────────┘
                    └──────────────────┘
```

## Combination ID Format

Images are identified by a combination ID that encodes the conditions:

```
{GENDER}_{WEATHER}_{TEMP}_{TIME}_v{VARIANT}
```

**Components:**
- **Gender**: `MALE`, `FEMALE`
- **Weather**: `CLEAR`, `CLOUDY`, `RAINY`, `SNOWY`
- **Temperature**: `FREEZING`, `COLD`, `COOL`, `MILD`, `WARM`, `HOT`
- **Time of Day**: `DAWN`, `MIDDAY`, `DUSK`, `NIGHT`
- **Variant**: `v1`, `v2`, `v3`... (for variety)

**Example:** `FEMALE_CLEAR_WARM_NIGHT_v778`

## Database Schema

### `generation_jobs` Table
Tracks image generation requests:

| Column | Type | Description |
|--------|------|-------------|
| id | uuid | Primary key |
| combination_id | text | Full combination ID with variant |
| status | text | `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED` |
| prompt | text | AI generation prompt |
| requested_at | timestamp | When job was queued |
| started_at | timestamp | When processing began |
| completed_at | timestamp | When job finished |
| error_message | text | Error details if failed |
| replicate_id | text | Replicate prediction ID |

### `generated_images` Table
Tracks completed images:

| Column | Type | Description |
|--------|------|-------------|
| id | uuid | Primary key |
| combination_id | text | Full combination ID with variant |
| image_url | text | Public URL in Supabase Storage |
| prompt | text | Prompt used to generate |
| created_at | timestamp | When image was created |
| served_count | int | How many times served to users |

**Note:** The FK constraint on `combination_id` was removed to allow variant suffixes.

### `hero-images` Storage Bucket
Images stored at: `{combination_id}/{combination_id}.png`

Example: `hero-images/FEMALE_CLEAR_WARM_NIGHT_v778/FEMALE_CLEAR_WARM_NIGHT_v778.png`

## Client-Side Workflow

### 1. Query for Hero Image

The app queries `generated_images` using a cascade pattern:

```javascript
// Cascade query order (stops at first match):
1. FEMALE_CLEAR_WARM_NIGHT_%     // Exact match
2. FEMALE_CLEAR_WARM_%           // Any time of day
3. FEMALE_CLEAR_%                // Clear weather fallback
4. MALE_CLEAR_WARM_NIGHT_%       // Opposite gender
5. MALE_CLEAR_WARM_%             // Opposite gender, any time
```

### 2. Replenishment Logic

After finding an image, the app checks variant count:

```javascript
// If exact match found but < 5 variants exist:
if (variantCount < 5) {
    queueReplenishment(combination, currentVariantCount + 1);
}

// If found via fallback (different gender/weather):
queueReplenishment(originalCombination, 1);
```

### 3. Rate Limiting

To prevent abuse, replenishment is rate-limited:
- **1 queue per user per 5 minutes**
- Stored in localStorage with timestamp

```javascript
const RATE_LIMIT_KEY = 'hero_replenish_last';
const RATE_LIMIT_MS = 5 * 60 * 1000; // 5 minutes

const lastQueue = localStorage.getItem(RATE_LIMIT_KEY);
if (lastQueue && Date.now() - parseInt(lastQueue) < RATE_LIMIT_MS) {
    console.log('[Replenish] Rate limited, skipping');
    return;
}
```

## Edge Function: generate-hero-image

Located at: `supabase/functions/generate-hero-image/index.ts`

### Processing Flow

1. **Fetch queued job** - Gets one `QUEUED` job from `generation_jobs`
2. **Update status** - Marks job as `PROCESSING`
3. **Call Replicate API** - Submits prompt to Flux model
4. **Poll for completion** - Checks every 1s for up to 60s
5. **Download image** - Fetches generated image from Replicate
6. **Upload to Storage** - Saves to `hero-images` bucket
7. **Insert tracking record** - Adds to `generated_images` table
8. **Mark complete** - Updates job status to `COMPLETED`

### Key Configuration

```typescript
// Replicate model (Flux Schnell - fast generation)
version: "5599ed30703defd1d160a25a63321b4dec97101d98b4674bcc56e41f62f35637"

// Image settings
input: {
    prompt: job.prompt,
    num_outputs: 1,
    aspect_ratio: "2:3",    // Portrait for mobile
    output_format: "png",
    output_quality: 90
}
```

### Error Handling

- **Replicate API errors** → Job marked `FAILED` with error message
- **Timeout (60s)** → Job marked `FAILED` with timeout error
- **Upload errors** → Job marked `FAILED` with upload error
- **Duplicate key (23505)** → Silently skipped (image exists)

## Prompt Structure

Prompts are built dynamically from the actual outfit recommendation:

```
A {gender} runner in their 30s running mid-stride along a {background}.
They are wearing {outfit items} appropriate for {weather} {temp} weather.
Time of day: {time description}.
Professional running photography, dynamic action shot, high quality, sharp focus.
MOOD: {mood based on conditions}
```

**Backgrounds** (randomly selected):
- city street with buildings
- urban park with trees
- waterfront boardwalk
- scenic trail with nature
- downtown area with shops
- bridge with city skyline
- tree-lined avenue

**Mood examples:**
- Rainy → "determined, pushing through the rain"
- Snowy → "resilient, winter warrior"
- Hot → "energetic, summer vibes"
- Freezing → "tough, braving the cold"
- Default → "focused, confident stride"

## Monitoring & Debugging

### Check Overall Status

```bash
node scripts/check-status.js
```

Output:
```
=== Generation Job Status ===
QUEUED: 587
PROCESSING: 1
COMPLETED: 1000
FAILED: 0

=== Images in Database ===
Total: 1001
```

### Audit Specific Combination

```bash
node scripts/audit-replenishment.js FEMALE_CLEAR_WARM_NIGHT
```

Output:
```
=== Auditing: FEMALE_CLEAR_WARM_NIGHT* ===

Generation Jobs (14):
  COMPLETED    FEMALE_CLEAR_WARM_NIGHT_v778        1/31/2026, 10:04:27 PM
  QUEUED       FEMALE_CLEAR_WARM_NIGHT_v1          1/31/2026, 7:41:44 PM
  ...

Generated Images (2):
  FEMALE_CLEAR_WARM_NIGHT_v1          ✓
  FEMALE_CLEAR_WARM_NIGHT_v778        ✓

--- Summary ---
Total jobs: 14
  QUEUED: 7
  COMPLETED: 7
Total images: 2

[!] Less than 5 variants - replenishment should trigger on next app load
```

### View Recent Activity

```bash
node scripts/audit-replenishment.js
```

Shows last 10 jobs across all combinations.

## Batch Processing

For bulk image generation (e.g., rebuilding library):

```bash
# Queue 1000 jobs
node scripts/queue-images.js

# Process with parallel workers
node scripts/reset-failed-and-process.js
```

The processing script runs 5 parallel workers, achieving ~25 images/minute.

## Troubleshooting

### Jobs Stuck in PROCESSING

Reset and reprocess:
```bash
node scripts/reset-failed-and-process.js
```

### FK Constraint Errors

If you see `violates foreign key constraint "generated_images_combination_id_fkey"`:

```sql
-- Run in Supabase SQL Editor
ALTER TABLE generated_images DROP CONSTRAINT generated_images_combination_id_fkey;
```

### Images Not Appearing in App

1. Check if image exists in storage:
   ```
   https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/public/hero-images/{combination_id}/{combination_id}.png
   ```

2. Check if tracked in database:
   ```bash
   node scripts/audit-replenishment.js {COMBINATION_PREFIX}
   ```

3. Verify app query pattern matches combination_id format

## Environment Variables

Required for Edge Function:
- `REPLICATE_API_TOKEN` - Replicate API key
- `SUPABASE_URL` - Auto-provided
- `SUPABASE_SERVICE_ROLE_KEY` - Auto-provided

## Files Reference

| File | Purpose |
|------|---------|
| `supabase/functions/generate-hero-image/index.ts` | Edge function |
| `scripts/check-status.js` | Quick status check |
| `scripts/audit-replenishment.js` | Detailed combination audit |
| `scripts/queue-images.js` | Bulk queue jobs |
| `scripts/reset-failed-and-process.js` | Batch processing with parallel workers |
| `runwearpwa22/index.php` | PWA client implementation |
| `RunWear-android/shared/.../HeroImageRepository.kt` | Android client |
| `RunWear-iOS/RunWear/Services/HeroImageService.swift` | iOS client |
