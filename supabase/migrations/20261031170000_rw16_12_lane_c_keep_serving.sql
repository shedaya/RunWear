-- RW-16.12 Lane C — keep-serving on reference replacement (2026-10-31)
--
-- Sol's ruling (2026-10-31, verbatim): "Keep my current looks serving
-- until the replacements are ready."
--
-- Until now, register_references' change detection (RW-14 (b)) retired
-- every verified hero the instant a changed photo set registered:
-- storage objects removed, hero rows deleted, cell versions bumped.
-- The RW-16.12 field trace showed the cost: Sol replaced his photos
-- at 23:45:38 UTC and both of his delivered looks vanished in the
-- same second — personal_heroes empty, zero servable heroes anywhere,
-- and no surface said so.
--
-- This migration adds the explicit per-cell regeneration state the
-- keep-serving behavior needs. Serving (get-personal-hero) and the
-- request path (request-personal-generation) now distinguish:
--
--   current — the cell's heroes were generated from the registered
--             reference set; nothing outstanding.
--   pending — the reference set changed after this cell's heroes
--             were generated. Heroes created BEFORE
--             regen_pending_since are the prior generation and KEEP
--             SERVING; heroes created at/after it are replacements.
--             The swap materializes on the first serve/request that
--             sees a replacement: prior rows are retired then, never
--             before, so there is no window with neither hero.
--   failed  — the outstanding regeneration cycle ended in a terminal
--             failure (the latest job since the change is FAILED with
--             nothing in flight and no replacement delivered). The
--             prior hero keeps serving; regen_error records why. A
--             fresh request or the next reference change retries the
--             cycle — the failure is recorded, never silent, and the
--             cell is not stuck: 'failed' flips back to 'pending'
--             when a retry is enqueued.
--
-- regen_pending_since is the change instant that partitions prior
-- heroes from replacements by created_at. The partition is sound
-- because the worker reads the registered references when it
-- processes a job: a hero delivered after the change was generated
-- from the new set.
--
-- Free-tier accounting is NOT touched: the counter lives on
-- personal_profiles and is charged only by the worker on a
-- free_on_demand delivery to a never-counted cell; regeneration
-- deliveries (kind='regeneration') never charge, exactly as before.
-- Delete-my-photos is NOT touched: it remains an immediate, total
-- wipe in its own function. This state machine governs photo
-- REPLACEMENT only.

alter table public.personal_cells
    add column if not exists regen_status text not null default 'current';
alter table public.personal_cells
    add column if not exists regen_pending_since timestamptz;
alter table public.personal_cells
    add column if not exists regen_error text;

do $$
begin
    if not exists (
        select 1 from pg_constraint
         where conname = 'personal_cells_regen_status_check'
           and conrelid = 'public.personal_cells'::regclass
    ) then
        alter table public.personal_cells
            add constraint personal_cells_regen_status_check
            check (regen_status in ('current', 'pending', 'failed'));
    end if;
end $$;

comment on column public.personal_cells.regen_status is
    'RW-16.12 Lane C: keep-serving regeneration state for this cell — current (heroes match the registered references), pending (references changed; prior heroes keep serving until a replacement lands), failed (the outstanding regeneration cycle failed terminally; prior hero still serving, see regen_error). Set by register_references on a set change; cleared when the swap materializes in get-personal-hero / request-personal-generation.';
comment on column public.personal_cells.regen_pending_since is
    'RW-16.12 Lane C: instant of the reference change that opened the current pending/failed cycle. Heroes with created_at before it are prior-generation; at/after it are replacements.';
comment on column public.personal_cells.regen_error is
    'RW-16.12 Lane C: error_message of the terminally failed job that put this cell into regen_status=failed. Retained while a retry is in flight; cleared when a replacement lands or a new reference change opens a new cycle.';

-- Backfill: a cell that was delivered before but currently holds no
-- hero rows is, by definition, awaiting a regeneration under the new
-- semantics — its heroes were retired by the pre-ruling behavior
-- (the RW-16.12 field case: both MILD cells, version 2, heroes
-- empty). Mark those cells pending with the cycle opened at their
-- last update, so the first post-ruling serve/request treats any
-- hero delivered from now on as the replacement and the lazy
-- regeneration enqueue can fire. Cells holding heroes are current:
-- under the pre-ruling code a change deleted the heroes, so any
-- surviving hero was necessarily generated from the registered set.
update public.personal_cells c
   set regen_status = 'pending',
       regen_pending_since = c.updated_at
 where c.ever_delivered = true
   and c.regen_status = 'current'
   and not exists (select 1 from public.personal_heroes h
                    where h.user_id = c.user_id
                      and h.combination_id = c.combination_id);
