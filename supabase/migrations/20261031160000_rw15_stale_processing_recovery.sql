-- RW-15 remedy (2026-10-31): stale-PROCESSING recovery for
-- personal_generation_jobs.
--
-- A personal-hero-worker invocation terminated by the platform
-- mid-processing (observed live: Sol's first-look job, claimed
-- 2026-10-31 17:45:37 UTC, generation completed, invocation killed
-- during the verification gate) leaves its claimed job in PROCESSING
-- forever: the drain only claims QUEUED jobs, and the request action
-- answers already_queued against a PROCESSING job, so the cell can
-- never be retried.
--
-- reclaim_count is the per-job reclaim budget the worker's stale
-- scan spends. A job whose claim (started_at) is older than the
-- worker's stale threshold is reset to QUEUED with reclaim_count
-- incremented; once reclaim_count reaches the worker's cap the job
-- is marked FAILED with the named reason stale_processing_timeout
-- instead of being requeued again. The pre-existing attempts column
-- is deliberately NOT the budget counter: it records the generation
-- attempt (1..2) within the current processing and identifies which
-- completed generation replicate_id belongs to for the resume path.

alter table public.personal_generation_jobs
    add column if not exists reclaim_count integer not null default 0;

create index if not exists personal_jobs_stale_idx
    on public.personal_generation_jobs (status, started_at)
    where status = 'PROCESSING';
