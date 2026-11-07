-- RW-107 companion (2026-11-06): candidate selection for the
-- worker's reconciliation sweep.
--
-- The sweep's first cut selected counted cells through
-- PostgREST ordered by updated_at with a small limit and
-- filtered hero/job presence in code — but the limit applied
-- BEFORE the filters, so with 104 counted cells project-wide
-- the oldest healthy cells filled every scan window and a
-- recently orphaned cell might never be reached. Candidate
-- selection belongs in the database: this function returns
-- only true candidates (counted, no hero row, no live job),
-- oldest-stuck first, so the worker's per-drain limit applies
-- to cells that actually need repair. The worker re-checks
-- hero and live-job presence per candidate before queueing,
-- so a race between selection and repair cannot double-queue.

CREATE OR REPLACE FUNCTION public.reconcile_candidates(p_limit integer)
 RETURNS TABLE(user_id uuid, combination_id text, regen_status text, regen_pending_since timestamp with time zone)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
    select c.user_id, c.combination_id, c.regen_status, c.regen_pending_since
    from public.personal_cells c
    where (c.free_counted or c.ever_delivered)
      and not exists (
          select 1 from public.personal_heroes h
          where h.user_id = c.user_id and h.combination_id = c.combination_id)
      and not exists (
          select 1 from public.personal_generation_jobs j
          where j.user_id = c.user_id and j.combination_id = c.combination_id
            and j.status in ('QUEUED', 'PROCESSING'))
    order by c.updated_at asc
    limit p_limit;
$function$;
