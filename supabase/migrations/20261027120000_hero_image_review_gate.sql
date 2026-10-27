-- Hero image review gate (2026-10-27)
--
-- Until now every AI-generated hero image went straight into the servable
-- pool in generated_images. That is how an image with a visible anatomy
-- artifact (a back-view runner whose arm bends across his back and
-- clutches his own shoulder blade, MALE_CLEAR_MILD_DUSK_v5) could be
-- served to users on repeat. From this migration on, the generate-hero-
-- image edge function inserts new generations as 'pending'; a human
-- approves them before any client will serve them.
--
-- APPLY IN THE SUPABASE DASHBOARD (SQL editor) for project
-- ebicqznlcjbqcukjfzcf — the repo carries no service-role credential.
-- Safe to apply before or after the client/edge-function changes ship:
--   * Existing rows default to 'approved', so the current pool
--     (1,902 images) serves exactly as before.
--   * Clients treat a missing/NULL review_status as approved, so older
--     app builds are unaffected either way.
--   * The edge function falls back to a legacy insert if this column
--     does not exist yet.

alter table public.generated_images
    add column if not exists review_status text not null default 'approved';

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'generated_images_review_status_check'
    ) then
        alter table public.generated_images
            add constraint generated_images_review_status_check
            check (review_status in ('approved', 'pending', 'rejected'));
    end if;
end $$;

create index if not exists generated_images_review_status_idx
    on public.generated_images (review_status);

comment on column public.generated_images.review_status is
    'Curation gate: approved = servable; pending = generated, awaiting human review; rejected = never served. Added 2026-10-27.';

-- Review workflow (dashboard Table editor or SQL):
--   * See what awaits review:
--       select id, combination_id, image_url, prompt
--       from public.generated_images where review_status = 'pending';
--   * Approve / reject:
--       update public.generated_images set review_status = 'approved' where id = '<id>';
--       update public.generated_images set review_status = 'rejected' where id = '<id>';
--
-- Known-bad image from the 2026-10-27 audit — Sol may reject it with:
--   update public.generated_images
--   set review_status = 'rejected'
--   where id = '7367eebd-47ff-4338-a7a0-0a5a64e3d9b2';  -- MALE_CLEAR_MILD_DUSK_v5, arm-across-back artifact
