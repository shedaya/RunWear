-- RW-16.30 — Make It Me production pipeline on Route 2 generation
-- (2026-11-02). Additive only: new columns carry defaults, new tables
-- are new. Nothing here changes the behaviour of the deployed v23
-- worker; the new worker (same branch) is what reads them.
--
-- What this adds and why:
--  * Photo-set EPOCH. personal_profiles.photo_epoch increments every
--    time register_references observes a changed reference set, and
--    photo_set_hash stores the set's content hash (sha256 over the
--    sorted "path@stamp" list — the same stamps register_references
--    already compares). Jobs and heroes are stamped with the epoch
--    they were produced under; the worker refuses to deliver a job
--    whose epoch no longer matches the profile's, so a photo change
--    can never be served a prior epoch's face, and one user's epoch
--    never touches another user's bank (the epoch is per-profile).
--  * Verdict cache. personal_gate_verdicts caches prescription-gate
--    verdicts by image sha256 (the epoch cache is the business model —
--    cost rework §3): the same bytes never pay for the gate twice,
--    across draws, jobs, repairs and epochs.
--  * Draw accounting. personal_generation_jobs gains the Route 2
--    pipeline's bookkeeping: draws_used against the cell-difficulty
--    draw budget, the best identity similarity seen, whether a repair
--    was attempted, and the pipeline stage the job ended in — the
--    diagnostics the RW-16.20 fallback accounting reads from.
--  * Fallback accounting. personal_fallback_events records every
--    entitled/trial cell whose bank missed and fell back to stock
--    (RW-16.20: the fallback must be counted and visible, never
--    silent). Written by the worker when a fill/on-demand job for an
--    entitled user exhausts its draw budget without a delivery.
--  * Intake quality. personal_reference_photos.quality stores the
--    intake-policy verdict for the photo (face detected, normalized
--    face height, yaw, verdict, retake key) written by
--    personal-ref-prep when the identity service is configured.
--  * claim_free_delivery(): the delivery-time free-allowance claim.
--    The worker calls it inside the delivery path for a
--    free_on_demand job; it atomically charges the counter ONLY when
--    the cell has never been counted AND the counter is below the
--    lifetime cap of 5. This pins the audit F6 cap edge in the one
--    place it cannot race: two concurrent deliveries cannot both
--    take the last slot, and a delivery that would take the counter
--    to 6 is refused (the hero is not stored) instead of silently
--    over-delivering.

-- ---------------------------------------------------------------------
-- Epoch + set hash on profiles
-- ---------------------------------------------------------------------
alter table public.personal_profiles
    add column if not exists photo_epoch integer not null default 0,
    add column if not exists photo_set_hash text;

comment on column public.personal_profiles.photo_epoch is
    'RW-16.30: increments when register_references observes a changed reference-photo set. Jobs/heroes carry the epoch they were made under; the worker delivers only current-epoch jobs.';
comment on column public.personal_profiles.photo_set_hash is
    'RW-16.30: sha256 hex of the reference set (sorted path@stamp entries) the epoch was opened for. Epoch-cache key for the user''s bank.';

-- ---------------------------------------------------------------------
-- Epoch + image hash on heroes
-- ---------------------------------------------------------------------
alter table public.personal_heroes
    add column if not exists epoch integer not null default 0,
    add column if not exists image_hash text;

create index if not exists personal_heroes_image_hash_idx
    on public.personal_heroes (image_hash);

comment on column public.personal_heroes.epoch is
    'RW-16.30: the photo-set epoch this hero was generated under. Serving stays epoch-agnostic under keep-serving (Lane C): prior-epoch heroes serve until a current-epoch replacement lands.';
comment on column public.personal_heroes.image_hash is
    'RW-16.30: sha256 hex of the stored image bytes; joins to personal_gate_verdicts.';

-- ---------------------------------------------------------------------
-- Pipeline bookkeeping on jobs
-- ---------------------------------------------------------------------
alter table public.personal_generation_jobs
    add column if not exists epoch integer not null default 0,
    add column if not exists draws_used integer not null default 0,
    add column if not exists identity_best double precision,
    add column if not exists repair_attempted boolean not null default false,
    add column if not exists stage text;

comment on column public.personal_generation_jobs.epoch is
    'RW-16.30: the photo-set epoch this job was enqueued under. The worker supersedes (fails closed, no delivery, no charge) a job whose epoch is stale.';
comment on column public.personal_generation_jobs.stage is
    'RW-16.30: terminal pipeline stage for diagnostics: delivered | identity_screen | prescription_gate | repair_gate | superseded_epoch | allowance_exhausted | variant_cap | infrastructure.';

-- ---------------------------------------------------------------------
-- Gate verdict cache (by image hash)
-- ---------------------------------------------------------------------
create table if not exists public.personal_gate_verdicts (
    image_hash  text primary key,
    pass        boolean not null,
    failures    jsonb not null default '[]'::jsonb,
    notes       jsonb not null default '[]'::jsonb,
    detail      jsonb not null default '{}'::jsonb,
    gate_model  text not null,
    created_at  timestamptz not null default now()
);
comment on table public.personal_gate_verdicts is
    'RW-16.30: prescription-gate verdicts cached by image sha256. Service-role only; the worker reads before paying for a gate call and writes after. Verbatim gate detail is kept for calibration audits.';

-- ---------------------------------------------------------------------
-- Fallback accounting (RW-16.20 visibility)
-- ---------------------------------------------------------------------
create table if not exists public.personal_fallback_events (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null references auth.users (id) on delete cascade,
    combination_id text not null,
    epoch          integer not null default 0,
    job_id         uuid references public.personal_generation_jobs (id) on delete set null,
    reason         text not null,
    created_at     timestamptz not null default now()
);
create index if not exists personal_fallback_events_user_idx
    on public.personal_fallback_events (user_id, created_at desc);
comment on table public.personal_fallback_events is
    'RW-16.30: one row per entitled-user cell whose personal bank missed and fell back to stock serving, with the pipeline reason. The RW-16.20 defect counter — it must never be silent.';

-- ---------------------------------------------------------------------
-- Intake quality on reference photos
-- ---------------------------------------------------------------------
alter table public.personal_reference_photos
    add column if not exists quality jsonb;

comment on column public.personal_reference_photos.quality is
    'RW-16.30: intake-policy verdict for this photo: {face_detected, face_height_norm, yaw_deg, verdict: pass|fail, retake_key, checked_at}. Written by personal-ref-prep via the identity service; null until checked.';

-- ---------------------------------------------------------------------
-- RLS: the two new tables are service-role only (no client policies,
-- matching the jobs/heroes write discipline).
-- ---------------------------------------------------------------------
alter table public.personal_gate_verdicts   enable row level security;
alter table public.personal_fallback_events enable row level security;

-- ---------------------------------------------------------------------
-- claim_free_delivery — atomic delivery-time free-allowance claim.
-- Returns true when the caller may deliver (and the counter has been
-- charged); false when the allowance is exhausted. A cell that was
-- already counted never charges again (returns true without
-- incrementing), so regeneration/repair deliveries keep working.
-- ---------------------------------------------------------------------
create or replace function public.claim_free_delivery(p_user_id uuid, p_combination_id text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_counted boolean;
    v_used integer;
begin
    select free_counted into v_counted
    from public.personal_cells
    where user_id = p_user_id and combination_id = p_combination_id;
    if coalesce(v_counted, false) then
        return true;
    end if;

    -- Serialize claims per user so the cap cannot be raced.
    perform pg_advisory_xact_lock(hashtext('free_delivery:' || p_user_id::text));

    select free_generations_used into v_used
    from public.personal_profiles
    where user_id = p_user_id
    for update;
    if v_used is null then
        return false;
    end if;
    if v_used >= 5 then
        return false;
    end if;

    update public.personal_profiles
        set free_generations_used = free_generations_used + 1,
            updated_at = now()
    where user_id = p_user_id;

    insert into public.personal_cells (user_id, combination_id, ever_delivered, free_counted, updated_at)
    values (p_user_id, p_combination_id, true, true, now())
    on conflict (user_id, combination_id)
    do update set free_counted = true, updated_at = now();

    return true;
end $$;

revoke execute on function public.claim_free_delivery(uuid, text) from public;
revoke execute on function public.claim_free_delivery(uuid, text) from anon;
revoke execute on function public.claim_free_delivery(uuid, text) from authenticated;
grant  execute on function public.claim_free_delivery(uuid, text) to service_role;

comment on function public.claim_free_delivery(uuid, text) is
    'RW-16.30: atomic free-allowance claim at delivery time (audit F6 cap pin). Charges free_generations_used only for a never-counted cell and only below the cap of 5; returns false when exhausted.';
