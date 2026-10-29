-- Hero photo flags (2026-10-29)
--
-- The in-app counterpart to the review gate (20261027120000): users can
-- flag the hero photo on their screen when it looks broken, is
-- inappropriate, or doesn't match the outfit. Per Sol's 2026-10-27
-- auto-replenishment call, new generations still insert as 'approved'
-- and serve immediately — flags are the human quality signal that
-- replaces pre-publication review. A flag is reviewed manually and,
-- when upheld, the image is pulled with the existing retraction lever
-- (generated_images.review_status = 'rejected'). Nothing consumes
-- hero_flags automatically.
--
-- Applied to runwear-prod (ebicqznlcjbqcukjfzcf) via the Supabase
-- Management API on 2026-10-29. Additive only: no existing table,
-- column, or row is touched.
--
-- Access model: RLS is enabled and the ONLY grant is INSERT for the
-- anon role (the app ships with the anon key and has no accounts).
-- There is deliberately no SELECT/UPDATE/DELETE policy for anon or
-- authenticated — clients can report a photo but can never read the
-- reports back. Review reads happen through service-role tooling.

create table if not exists public.hero_flags (
    id uuid primary key default gen_random_uuid(),
    -- The exact photo flagged: the generated_images row when the hero
    -- came from the pool; NULL for curated/fallback photos, which are
    -- identified by image_url + image_source = 'fallback' instead.
    image_row_id uuid references public.generated_images (id) on delete set null,
    image_url text not null,
    -- The served row's own combination cell when known (the selection
    -- cascade can serve a neighbouring cell), else the requested one.
    combination_id text,
    image_source text not null default 'pool'
        check (image_source in ('pool', 'fallback')),
    reason text not null
        check (reason in ('looks_broken', 'inappropriate', 'outfit_mismatch')),
    platform text not null default 'Android',
    app_version text,
    -- Client clock at tap time; the column default covers inserts
    -- that omit it.
    created_at timestamptz not null default now()
);

create index if not exists hero_flags_created_at_idx
    on public.hero_flags (created_at desc);

create index if not exists hero_flags_image_row_id_idx
    on public.hero_flags (image_row_id);

comment on table public.hero_flags is
    'User reports against hero photos (in-app flag button, 2026-10-29). Reviewed manually; upheld flags are actioned by setting generated_images.review_status = ''rejected''. Anon clients may INSERT only.';

alter table public.hero_flags enable row level security;

-- This project grants broad default privileges on new public tables;
-- strip them back to the single operation the app needs.
revoke all on public.hero_flags from anon, authenticated;
grant insert on public.hero_flags to anon;

drop policy if exists "App insert hero flags" on public.hero_flags;
create policy "App insert hero flags"
    on public.hero_flags
    for insert
    to anon
    with check (true);

-- Review workflow (service-role SQL):
--   * Recent flags:
--       select * from public.hero_flags order by created_at desc limit 50;
--   * Most-flagged pool photos:
--       select image_row_id, count(*) from public.hero_flags
--       where image_row_id is not null
--       group by image_row_id order by count(*) desc;
--   * Uphold a flag (pull the photo from rotation):
--       update public.generated_images set review_status = 'rejected'
--       where id = '<image_row_id>';
