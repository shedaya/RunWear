-- RW-121 (2026-11-07): the RW-107 reconciliation sweep can now see
-- terminally failed FILL cells.
--
-- The sweep's candidates live in public.personal_cells, but a cell
-- row is only ever created by the personal_heroes insert/delete
-- trigger — so a subscriber_fill job that failed terminally before
-- any hero existed (field case: MALE_CLEAR_COLD_DAWN in the
-- 2026-11-06 burst, gate-failed in 3 draws) left NO cell row, and
-- the cell was invisible to the sweep forever: no hero, no retry,
-- and the bank could never complete.
--
-- The worker now records a fill job's terminal failure on its
-- cell (personal-hero-worker RW-121: regen_status 'failed' +
-- regen_error, the shape a failed regeneration cycle leaves).
-- This migration extends candidate selection to return those
-- cells alongside the RW-107 counted cells: uncounted (neither
-- flag set — the counted branch above already covers counted
-- cells), regen_status 'failed', backed by a terminally FAILED
-- subscriber_fill job (so the class is exactly "cells the fill
-- abandoned", not any cell that happens to carry a failed stamp),
-- still with no hero row and no live job. Everything downstream
-- is the unchanged RW-107 machinery: the worker re-checks hero
-- and live-job presence per candidate, applies the 24 h retry
-- floor via the regen-pending stamp it writes when queueing, and
-- repairs with a 'regeneration' job that never charges the free
-- allowance.

CREATE OR REPLACE FUNCTION public.reconcile_candidates(p_limit integer)
 RETURNS TABLE(user_id uuid, combination_id text, regen_status text, regen_pending_since timestamp with time zone)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
    select c.user_id, c.combination_id, c.regen_status, c.regen_pending_since
    from public.personal_cells c
    where (
        (c.free_counted or c.ever_delivered)
        or (
          c.regen_status = 'failed'
          and exists (
              select 1 from public.personal_generation_jobs j
              where j.user_id = c.user_id
                and j.combination_id = c.combination_id
                and j.kind = 'subscriber_fill'
                and j.status = 'FAILED')
        )
      )
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
