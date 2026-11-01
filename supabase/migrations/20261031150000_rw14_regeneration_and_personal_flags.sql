-- RW-14 (b)/(c) — regeneration + personal hero flags (2026-10-31)
--
-- (b) FORCE-REGENERATE ON REFERENCE CHANGE. Until now, replacing
-- reference photos left every existing verified hero untouched:
-- request-personal-generation returned already_available for any cell
-- holding a hero, so the OLD likeness served indefinitely (iOS lane
-- gap (b)). This migration adds the bookkeeping the fix needs:
--
--   * personal_cells.ever_delivered — the cell has had at least one
--     verified hero delivered at some point. Survives hero retirement
--     (the cell row is never deleted by invalidation), which is what
--     lets the request path distinguish a REGENERATION of a look the
--     user already has from a genuinely new cell.
--   * personal_cells.free_counted — this cell already consumed one of
--     the 5 free generations. The plan's allowance counts GENUINELY
--     NEW cells ("today's cell plus the next four genuinely new cells
--     encountered", plan §2.2 as amended) and spec J4 rules that a
--     photo-replacement regeneration "does not consume free
--     allowance". The worker therefore charges the counter only for a
--     free_on_demand delivery to a cell that has never been counted.
--   * personal_reference_photos.object_stamp — a change-detection
--     token for the stored object (its etag when storage provides
--     one, else its updated_at), recorded at registration time.
--     register_references compares position-wise: a different path,
--     a different count, or a different stamp at the same path (the
--     photo was overwritten in place) all mean the reference SET
--     changed, which retires the user's verified heroes (rows deleted
--     -> the existing version trigger bumps every affected cell).
--   * personal_generation_jobs kind 'regeneration' — the job kind
--     both regeneration paths enqueue (priority 20, same as
--     free_on_demand: it is user-initiated). The worker never charges
--     the free counter for this kind.
--
-- (c) PERSONAL-HERO FLAG ("This doesn't look like me", spec §4). The
-- pool's hero_flags system targets generic-pool rows; personal heroes
-- had no flag path (iOS lane gap (c)). personal_hero_flags records a
-- flag against a personal hero the caller owns. The hero row itself
-- is retired by the flag action (deleted -> version bump -> stops
-- being served on the next lookup), so the flag row is a SNAPSHOT:
-- hero_id is stored as a plain uuid with NO foreign key, because the
-- hero row it describes no longer exists.
--
-- Privacy posture is unchanged: no anon policies, owner-only reads,
-- all writes service-role via the Edge Functions. The generic pool,
-- generation_jobs, and both drain crons are NOT touched.

-- ---------------------------------------------------------------------
-- 1. personal_cells delivery bookkeeping
-- ---------------------------------------------------------------------
alter table public.personal_cells
    add column if not exists ever_delivered boolean not null default false;
alter table public.personal_cells
    add column if not exists free_counted boolean not null default false;

comment on column public.personal_cells.ever_delivered is
    'RW-14 (b): true once any verified hero has been delivered for this cell. Drives regeneration treatment in request-personal-generation (no allowance gate, kind=regeneration) after the cell''s heroes are retired by a reference change or a flag.';
comment on column public.personal_cells.free_counted is
    'RW-14 (b): true once this cell has consumed one of the 5 free generations. The worker charges free_generations_used only for a free_on_demand delivery to a cell where this is false (plan: the allowance counts genuinely new cells; spec J4: replacement regenerations cost no allowance).';

-- Backfill: any cell currently holding a verified hero has been delivered.
update public.personal_cells c
   set ever_delivered = true
 where exists (select 1 from public.personal_heroes h
                where h.user_id = c.user_id
                  and h.combination_id = c.combination_id);

-- ---------------------------------------------------------------------
-- 2. 'regeneration' job kind
-- ---------------------------------------------------------------------
do $$
begin
    alter table public.personal_generation_jobs
        drop constraint if exists personal_generation_jobs_kind_check;
    alter table public.personal_generation_jobs
        add constraint personal_generation_jobs_kind_check
        check (kind in ('subscriber_fill', 'free_on_demand',
                        'replenishment', 'regeneration'));
end $$;

-- ---------------------------------------------------------------------
-- 3. Reference object stamps for change detection
-- ---------------------------------------------------------------------
alter table public.personal_reference_photos
    add column if not exists object_stamp text;

comment on column public.personal_reference_photos.object_stamp is
    'RW-14 (b): change-detection token for the stored object at registration time (''etag:<etag>'' when storage provides an etag, else ''ms:<updated_at epoch ms>''). Rows registered before this column existed carry NULL; register_references adopts the current stamp as their baseline instead of treating them as changed.';

-- ---------------------------------------------------------------------
-- 4. personal_hero_flags — flag records for personal heroes
-- ---------------------------------------------------------------------
create table if not exists public.personal_hero_flags (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null references auth.users (id) on delete cascade,
    hero_id        uuid not null,
    combination_id text not null,
    variant        integer not null,
    storage_path   text not null,
    reason         text not null default 'doesnt_look_like_me'
                   check (reason in ('doesnt_look_like_me', 'looks_broken',
                                     'inappropriate', 'outfit_mismatch')),
    created_at     timestamptz not null default now()
);
create index if not exists personal_hero_flags_user_idx
    on public.personal_hero_flags (user_id, created_at desc);

comment on table public.personal_hero_flags is
    'RW-14 (c): user flags on their OWN personal heroes ("This doesn''t look like me"). Written only by request-personal-generation''s flag_hero action (service role); the flagged hero row is retired in the same call, so hero_id/combination_id/variant/storage_path are snapshots of what was flagged. Reasons mirror the pool flag vocabulary plus the personal-specific default.';

alter table public.personal_hero_flags enable row level security;

do $$
begin
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_hero_flags' and policyname='personal_hero_flags_owner_select') then
        create policy personal_hero_flags_owner_select on public.personal_hero_flags
            for select to authenticated using (user_id = auth.uid());
    end if;
end $$;
