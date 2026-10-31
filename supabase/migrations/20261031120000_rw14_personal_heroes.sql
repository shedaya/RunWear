-- RW-14 "Make it me" — personalized hero backend (2026-10-31)
--
-- Per-user personalization storage for RunWear: reference photos,
-- personalized heroes keyed user x outfit signature x condition cell,
-- a personal generation job queue (distinct from the generic
-- generation_jobs queue), consent/attestation records, and entitlement.
--
-- PRIVACY POSTURE (plan: runwear-personal-hero-plan.md §4):
--   * Everything here is PRIVATE. The 'personal-heroes' storage bucket is
--     not public, and there are NO anon policies on any personal_* table.
--     This is the deliberate opposite of the generic pool in
--     generated_images, whose approved rows are publicly readable.
--   * A personalized hero is keyed to exactly one user and is never
--     listed, served, or reused for any other user.
--   * Reference photos live under {user_id}/refs/ in the private bucket;
--     heroes under {user_id}/heroes/. Storage RLS restricts every object
--     to the owner (first path segment == auth.uid()).
--
-- The generic pool, generation_jobs, and the (disabled) generic drain
-- cron job are NOT touched by this migration.

-- ---------------------------------------------------------------------
-- Storage bucket (private)
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('personal-heroes', 'personal-heroes', false, 10485760,
        array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

-- Storage RLS: owner-only access, keyed on the first path segment.
-- (No policy for anon -> anon can do nothing with this bucket.)
do $$
begin
    if not exists (select 1 from pg_policies where schemaname = 'storage'
                   and tablename = 'objects' and policyname = 'personal_heroes_owner_select') then
        create policy personal_heroes_owner_select on storage.objects
            for select to authenticated
            using (bucket_id = 'personal-heroes'
                   and (storage.foldername(name))[1] = auth.uid()::text);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'storage'
                   and tablename = 'objects' and policyname = 'personal_heroes_owner_insert') then
        create policy personal_heroes_owner_insert on storage.objects
            for insert to authenticated
            with check (bucket_id = 'personal-heroes'
                   and (storage.foldername(name))[1] = auth.uid()::text);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'storage'
                   and tablename = 'objects' and policyname = 'personal_heroes_owner_update') then
        create policy personal_heroes_owner_update on storage.objects
            for update to authenticated
            using (bucket_id = 'personal-heroes'
                   and (storage.foldername(name))[1] = auth.uid()::text);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'storage'
                   and tablename = 'objects' and policyname = 'personal_heroes_owner_delete') then
        create policy personal_heroes_owner_delete on storage.objects
            for delete to authenticated
            using (bucket_id = 'personal-heroes'
                   and (storage.foldername(name))[1] = auth.uid()::text);
    end if;
end $$;

-- ---------------------------------------------------------------------
-- personal_profiles — one row per opted-in user
-- ---------------------------------------------------------------------
create table if not exists public.personal_profiles (
    user_id               uuid primary key references auth.users (id) on delete cascade,
    personalization_on    boolean not null default false,
    gender                text not null default 'MALE'
                          check (gender in ('MALE', 'FEMALE', 'UNISEX')),
    free_generations_used integer not null default 0 check (free_generations_used >= 0),
    entitled              boolean not null default false,
    entitlement_source    text,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);
comment on table public.personal_profiles is
    'RW-14: per-user personalization state. free_generations_used counts VERIFIED free-tier heroes delivered (lifetime allowance 5). entitled is set ONLY via public.set_personal_entitlement (service role) — the billing lane''s webhook will call it; tests use source=''test-toggle'' so the state is auditable, never faked.';

-- ---------------------------------------------------------------------
-- personal_consents — consent + attestation history
-- ---------------------------------------------------------------------
create table if not exists public.personal_consents (
    id                     uuid primary key default gen_random_uuid(),
    user_id                uuid not null references auth.users (id) on delete cascade,
    consent_version        text not null,
    attested_own_likeness  boolean not null,
    attested_adult         boolean not null,
    created_at             timestamptz not null default now()
);
create index if not exists personal_consents_user_idx
    on public.personal_consents (user_id, created_at desc);

-- ---------------------------------------------------------------------
-- personal_reference_photos — the user's uploaded references
-- ---------------------------------------------------------------------
create table if not exists public.personal_reference_photos (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references auth.users (id) on delete cascade,
    storage_path text not null,
    position     integer not null check (position between 1 and 3),
    created_at   timestamptz not null default now(),
    unique (user_id, position),
    unique (user_id, storage_path)
);

-- ---------------------------------------------------------------------
-- personal_cells — per-cell version marker
-- The version changes whenever the cell's verified hero set changes
-- (insert or delete of a variant). Clients compare versions to know a
-- cached hero is stale.
-- ---------------------------------------------------------------------
create table if not exists public.personal_cells (
    user_id        uuid not null references auth.users (id) on delete cascade,
    combination_id text not null,
    version        integer not null default 1,
    updated_at     timestamptz not null default now(),
    primary key (user_id, combination_id)
);

-- ---------------------------------------------------------------------
-- personal_heroes — VERIFIED personalized heroes only.
-- A hero row exists only after the image passed the verification gate
-- (see the personal-hero-worker edge function). Unverified generations
-- are never stored here and never served.
-- ---------------------------------------------------------------------
create table if not exists public.personal_heroes (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references auth.users (id) on delete cascade,
    combination_id   text not null,
    gender           text not null,
    weather          text not null,
    temp_bracket     text not null,
    time_of_day      text not null,
    outfit_signature text not null,
    outfit_items     jsonb not null default '[]'::jsonb,
    storage_path     text not null,
    variant          integer not null check (variant between 1 and 3),
    prompt           text,
    gate_result      jsonb,
    created_at       timestamptz not null default now(),
    unique (user_id, combination_id, variant)
);
create index if not exists personal_heroes_cell_idx
    on public.personal_heroes (user_id, combination_id);

-- Bump the cell version whenever its hero set changes.
create or replace function public.bump_personal_cell_version()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        insert into public.personal_cells (user_id, combination_id, version, updated_at)
        values (new.user_id, new.combination_id, 1, now())
        on conflict (user_id, combination_id)
        do update set version = public.personal_cells.version + 1, updated_at = now();
        return new;
    else
        insert into public.personal_cells (user_id, combination_id, version, updated_at)
        values (old.user_id, old.combination_id, 2, now())
        on conflict (user_id, combination_id)
        do update set version = public.personal_cells.version + 1, updated_at = now();
        return old;
    end if;
end $$;

drop trigger if exists personal_heroes_bump_version on public.personal_heroes;
create trigger personal_heroes_bump_version
    after insert or delete on public.personal_heroes
    for each row execute function public.bump_personal_cell_version();

-- ---------------------------------------------------------------------
-- personal_generation_jobs — the personal queue.
-- Distinct from public.generation_jobs (generic pool). Priorities:
-- lower number = processed first.
--   10 subscriber initial fill · 20 free on-demand · 30 replenishment
-- Status set includes CANCELLED for admin/test control of queued work.
-- ---------------------------------------------------------------------
create table if not exists public.personal_generation_jobs (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references auth.users (id) on delete cascade,
    combination_id   text not null,
    gender           text not null,
    weather          text not null,
    temp_bracket     text not null,
    time_of_day      text not null,
    outfit_signature text not null,
    outfit_items     jsonb not null default '[]'::jsonb,
    prompt           text,
    kind             text not null
                     check (kind in ('subscriber_fill', 'free_on_demand', 'replenishment')),
    priority         integer not null,
    status           text not null default 'QUEUED'
                     check (status in ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    attempts         integer not null default 0,
    replicate_id     text,
    gate_result      jsonb,
    error_message    text,
    result_hero_id   uuid references public.personal_heroes (id) on delete set null,
    requested_at     timestamptz not null default now(),
    started_at       timestamptz,
    completed_at     timestamptz
);
create index if not exists personal_jobs_queue_idx
    on public.personal_generation_jobs (status, priority, requested_at);
create index if not exists personal_jobs_active_cell_idx
    on public.personal_generation_jobs (user_id, combination_id, status);

-- ---------------------------------------------------------------------
-- personal_entitlement_events — audit trail for entitlement changes
-- ---------------------------------------------------------------------
create table if not exists public.personal_entitlement_events (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references auth.users (id) on delete cascade,
    entitled   boolean not null,
    source     text not null,
    created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- set_personal_entitlement — the ONLY writer of the entitled flag.
-- Service role only. The billing lane's RevenueCat webhook integration
-- will call this; tests call it with source='test-toggle' so a test
-- entitlement is always distinguishable from a real one in the audit log.
-- ---------------------------------------------------------------------
create or replace function public.set_personal_entitlement(
    p_user_id uuid, p_entitled boolean, p_source text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.personal_profiles (user_id, entitled, entitlement_source)
    values (p_user_id, p_entitled, p_source)
    on conflict (user_id) do update
        set entitled = excluded.entitled,
            entitlement_source = excluded.entitlement_source,
            updated_at = now();
    insert into public.personal_entitlement_events (user_id, entitled, source)
    values (p_user_id, p_entitled, p_source);
end $$;

revoke execute on function public.set_personal_entitlement(uuid, boolean, text) from public;
revoke execute on function public.set_personal_entitlement(uuid, boolean, text) from anon;
revoke execute on function public.set_personal_entitlement(uuid, boolean, text) from authenticated;
grant  execute on function public.set_personal_entitlement(uuid, boolean, text) to service_role;

-- ---------------------------------------------------------------------
-- Row Level Security — owner-only reads; writes for jobs/heroes are
-- service-role only (no insert/update/delete policies for clients, so
-- the API can never be used to mint a hero or a job directly).
-- NO anon policies anywhere in this migration.
-- ---------------------------------------------------------------------
alter table public.personal_profiles          enable row level security;
alter table public.personal_consents          enable row level security;
alter table public.personal_reference_photos  enable row level security;
alter table public.personal_cells             enable row level security;
alter table public.personal_heroes            enable row level security;
alter table public.personal_generation_jobs   enable row level security;
alter table public.personal_entitlement_events enable row level security;

do $$
begin
    -- profiles: owner reads own row (writes go through edge functions /
    -- service role, except gender + personalization_on which the client
    -- may set on its own row).
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_profiles' and policyname='personal_profiles_owner_select') then
        create policy personal_profiles_owner_select on public.personal_profiles
            for select to authenticated using (user_id = auth.uid());
    end if;
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_profiles' and policyname='personal_profiles_owner_insert') then
        create policy personal_profiles_owner_insert on public.personal_profiles
            for insert to authenticated with check (user_id = auth.uid());
    end if;
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_profiles' and policyname='personal_profiles_owner_update') then
        create policy personal_profiles_owner_update on public.personal_profiles
            for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
    end if;

    -- consents: owner reads own; inserts happen via the edge function.
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_consents' and policyname='personal_consents_owner_select') then
        create policy personal_consents_owner_select on public.personal_consents
            for select to authenticated using (user_id = auth.uid());
    end if;

    -- reference photos: owner reads own rows.
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_reference_photos' and policyname='personal_refs_owner_select') then
        create policy personal_refs_owner_select on public.personal_reference_photos
            for select to authenticated using (user_id = auth.uid());
    end if;

    -- cells: owner reads own.
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_cells' and policyname='personal_cells_owner_select') then
        create policy personal_cells_owner_select on public.personal_cells
            for select to authenticated using (user_id = auth.uid());
    end if;

    -- heroes: owner reads own. Nothing else — no other user, no anon,
    -- no public listing. This is the hard rule from the plan.
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_heroes' and policyname='personal_heroes_owner_select') then
        create policy personal_heroes_owner_select on public.personal_heroes
            for select to authenticated using (user_id = auth.uid());
    end if;

    -- jobs: owner reads own job states (for progress UI); cannot write.
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_generation_jobs' and policyname='personal_jobs_owner_select') then
        create policy personal_jobs_owner_select on public.personal_generation_jobs
            for select to authenticated using (user_id = auth.uid());
    end if;

    -- entitlement events: owner reads own.
    if not exists (select 1 from pg_policies where schemaname='public' and tablename='personal_entitlement_events' and policyname='personal_entitlement_events_owner_select') then
        create policy personal_entitlement_events_owner_select on public.personal_entitlement_events
            for select to authenticated using (user_id = auth.uid());
    end if;
end $$;
