# Hero Image Generation — Revival Runbook

**Status:** prepared in-repo 2026-10-27, **not yet turned on.** Everything
below is a dashboard step for a human (the repo carries no service-role
credential, by design). Work through it in order.

**Why this exists:** the `generate-hero-image` edge function (Replicate
flux-schnell) fulfills `generation_jobs`, but nothing invoked it after
Mar 2026 — no cron, no CI, only manual scripts — so the pool stopped
growing and 3 jobs sat QUEUED for ~8 months. This revival adds a batch
drain to the function, schedules it every 30 minutes, caps daily spend,
and serves every new image immediately (auto-replenishment — no review
queue; `review_status` stays available as a retraction lever).

**Parameters chosen (all in code, all one-line changes):**

| Parameter | Value | Where |
|---|---|---|
| Drain batch size (per invocation) | default **5**, hard max **10** | `DEFAULT_DRAIN_LIMIT` / `MAX_DRAIN_LIMIT` in `supabase/functions/generate-hero-image/index.ts` |
| Schedule | every **30 minutes**, `?drain=5` | migration `20261027130000_hero_generation_scheduler.sql` |
| Daily generation cap | **50 images/day** (UTC), counted from COMPLETED jobs | `DAILY_GENERATION_CAP` in the function |

The 3 stale QUEUED jobs are real combinations and fulfill normally on
the first drain — they count toward day one's 50 like any other jobs.

---

## Step 0 — Apply the review-gate migration FIRST

Dashboard → SQL Editor → run
`supabase/migrations/20261027120000_hero_image_review_gate.sql` in full.

This adds `generated_images.review_status`. Until it exists, the edge
function falls back to legacy inserts (new images immediately servable,
no status recorded) — so this migration must land before the revival is
considered "on": it is what makes retraction (and the v5 rejection at the
bottom of the file) possible. Existing rows default to `approved`; the
current pool serves unchanged.

## Step 1 — Verify the Replicate secret exists

The function reads its Replicate token from the environment variable
**`REPLICATE_API_TOKEN`**. Verify it is present (do not copy its value
anywhere):

- Dashboard → Edge Functions → `generate-hero-image` → Secrets
  (or Project Settings → Edge Functions → Secrets).
- Confirm a secret named `REPLICATE_API_TOKEN` exists. If it is missing,
  create it with a token from the Replicate account (replicate.com →
  Account → API tokens) that owns the flux-schnell usage.
- `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected into every
  edge function automatically — nothing to set for those.

Without this secret the function throws on boot and every drain fails;
check it before debugging anything else.

## Step 2 — Deploy the updated edge function

Deploy `supabase/functions/generate-hero-image/` from this branch, either
way:

- Dashboard → Edge Functions → `generate-hero-image` → Edit → replace the
  code with the repo's `index.ts` → Deploy, **or**
- CLI: `supabase functions deploy generate-hero-image`

The updated function is backward compatible: a POST with no `drain`
parameter still processes exactly one job with the original response
shapes, so `scripts/reset-and-process.js` and
`scripts/reset-failed-and-process.js` keep working unchanged.

## Step 3 — Put the real service-role key in Vault

The cron job authenticates to the function with the service-role key,
read from Supabase Vault at fire time. The scheduler migration creates
the Vault entries if missing — `project_url` with the real (public)
project URL, and `service_role_key` with a **placeholder** that must be
replaced.

1. Get the key: Dashboard → Project Settings → API → **service_role**
   (click Reveal). Treat it like a password: it bypasses Row Level
   Security. Never paste it into chat, a ticket, or this repo.
2. In the SQL Editor:

   ```sql
   -- See what exists (names only):
   select name from vault.secrets;

   -- If the migration already created the placeholder, replace its value:
   select vault.update_secret(
     (select id from vault.secrets where name = 'service_role_key'),
     '<PASTE THE REAL SERVICE-ROLE KEY HERE>'
   );

   -- If it does not exist yet, create it instead:
   -- select vault.create_secret('<PASTE THE REAL SERVICE-ROLE KEY HERE>',
   --   'service_role_key', 'Service-role key for the hero-generation drain cron job');
   ```

## Step 4 — Apply the scheduler migration

Dashboard → SQL Editor → run
`supabase/migrations/20261027130000_hero_generation_scheduler.sql` in
full. It enables `pg_cron` + `pg_net`, creates the Vault placeholders if
Step 3 hasn't already, and schedules the job `hero-generation-drain`
(`*/30 * * * *` → POST `.../functions/v1/generate-hero-image?drain=5`).

**If `create extension` fails** (plan/SQL-role restrictions): enable
**pg_cron** and **pg_net** via Dashboard → Database → Extensions, then
re-run the migration.

**If pg_cron is not available on this plan at all:** skip this migration
and use the fallback scheduler instead —
`.github/workflows/hero-generation-drain.yml`:

1. Add the repository secret `SUPABASE_SERVICE_ROLE_KEY` (GitHub →
   Settings → Secrets and variables → Actions) with the same key from
   Step 3.1.
2. Uncomment the `schedule:` block in the workflow file and commit.
3. Do not run both schedulers at once; the pg_cron job is the primary.

## Step 5 — Verify with one manual drain

Trigger one drain by hand and watch the 3 stale jobs land:

```bash
curl -sS -X POST \
  "https://ebicqznlcjbqcukjfzcf.supabase.co/functions/v1/generate-hero-image?drain=5" \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>" \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Expected response: `drained: true`, `attempted: 3`, `completed: 3`,
`failed: 0`, with one result per job. (A drain can take a few minutes —
each job polls Replicate for up to ~60s.) Then confirm in the SQL
Editor:

```sql
-- The 3 stale jobs are done:
select combination_id, status, completed_at
from public.generation_jobs
where combination_id in (
  'MALE_SNOW_FREEZING_DAWN_v1185',
  'FEMALE_CLEAR_COLD_DUSK_v936',
  'MALE_RAIN_COLD_NIGHT_v1'
);
-- Expect: all COMPLETED.

-- Their images exist and are SERVED immediately (auto-replenishment):
select combination_id, review_status, image_url
from public.generated_images
where combination_id in (
  'MALE_SNOW_FREEZING_DAWN_v1185',
  'FEMALE_CLEAR_COLD_DUSK_v936',
  'MALE_RAIN_COLD_NIGHT_v1'
);
-- Expect: 3 rows, review_status = 'approved'.
```

After that, confirm the schedule is armed:

```sql
select jobid, jobname, schedule, active from cron.job;
-- Expect: hero-generation-drain, */30 * * * *, active = true.
```

---

## Spot-checks and retraction (no review queue)

Auto-replenishment (Sol's call, 2026-10-27): new generations are inserted
as `approved` and **served immediately** — nothing waits for review.
`review_status` remains as a retraction lever: setting a row to `rejected`
pulls it from rotation (clients only serve `approved` rows and legacy rows
with no status).

**List recent generations for a spot-check:**

```sql
select id, combination_id, image_url, created_at
from public.generated_images
order by created_at desc
limit 50;
```

**Judge an image against the anatomy criteria** (the same rules the
generation prompt now enforces). Open the `image_url` and check:

- Natural human proportions; photorealistic.
- Arms bent at the elbows, swinging beside the torso — **never reaching
  across the back, never clutching a shoulder** (the exact artifact that
  triggered the gate).
- Each visible hand has five correct fingers, relaxed.
- Legs mid-stride, knees and feet pointing in the direction of travel.
- From a back view, arms/legs/hands still anatomically coherent.
- **Reject** on: distorted or merged limbs, extra fingers/hands/limbs,
  warped or backwards-bending joints, hands fused to the body.

**Approve / reject** (single, list, or whole-combination forms):

```sql
update public.generated_images
set review_status = 'approved'
where id in ('<id>', '<id>');

update public.generated_images
set review_status = 'rejected'
where id in ('<id>');
```

New images are in the servable rotation from the moment they are
generated; setting a row to `rejected` pulls it (the row stays for audit).

## Operations

- **Pause the revival:** `select cron.unschedule('hero-generation-drain');`
  (or re-comment the GitHub schedule in fallback mode). Queued jobs just
  wait; nothing breaks.
- **Daily cap:** 50 completed generations per UTC day, enforced in the
  function (`DAILY_GENERATION_CAP`). At the cap, drains return
  `cap_reached: true` and process nothing. Each completion is one
  flux-schnell prediction billed to the Replicate account behind
  `REPLICATE_API_TOKEN` — check the current per-image price in the
  Replicate dashboard; the cap is the spend bound.
- **A job stuck at PROCESSING** means an invocation died mid-job (wall
  clock). Recover with the existing script, which resets PROCESSING →
  QUEUED so the next drain picks it up: `scripts/reset-and-process.js`.
- **Drain timeouts:** if a 5-job drain regularly hits the edge-function
  wall clock, lower the cron URL to `?drain=3` (update the job in
  `cron.job`, or re-run the scheduler migration after editing it).
- **Cron firing history:** `select * from cron.job_run_details order by start_time desc limit 10;`
