-- Hero generation hardening (RW-176 follow-up, 2026-11-17)
--
-- Schema support for generate-hero-image v39:
--   1. generation_jobs.reclaim_count — the stale-PROCESSING reclaim
--      budget (mirrors personal_generation_jobs.reclaim_count, RW-15).
--      A job whose invocation was terminated mid-processing is reclaimed
--      back to QUEUED at most 3 times; the next stale scan fails it with
--      the named stale_processing_timeout reason.
--   2. hero_generation_lock — a single-row compare-and-swap lock that
--      serializes generate-hero-image invocations (pg_cron drains,
--      manual drains, legacy single-job calls, and the per-row
--      "trigger-image-generation" webhook invocations). The daily
--      generation cap counter is only meaningful if exactly one
--      invocation reads it at a time.
--
-- Applied via the Supabase Management API SQL route on 2026-11-17
-- (the repo carries no service-role credential for a local psql).
-- The companion unique index on generated_images lands in
-- 20261118004500_generated_images_approved_url_unique.sql, after the
-- pre-existing duplicate approved rows are cleaned up.

alter table public.generation_jobs
  add column if not exists reclaim_count integer not null default 0;

create table if not exists public.hero_generation_lock (
  id integer primary key,
  owner text not null default '',
  acquired_at timestamptz,
  expires_at timestamptz not null default 'epoch'::timestamptz
);

insert into public.hero_generation_lock (id) values (1)
  on conflict (id) do nothing;

-- Only the service role (used by the edge function) may touch the lock:
-- RLS is enabled with no policies, so anon/authenticated are denied and
-- service_role bypasses RLS as usual.
alter table public.hero_generation_lock enable row level security;
