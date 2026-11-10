-- RW-137 S1 (2026-11-10): identity adoption core.
--
-- Design of record: release-prep/rw137-identity-exploration-2026-11-07/
-- DESIGN.md §4.1/§4.2/§4.3/§4.8. Adoption is AUTOMATIC on RevenueCat
-- proof (P1 TRANSFER event / P2 alias chain on the subscriber record),
-- Premium-only, with NO opt-in toggle anywhere in the schema — the
-- proof plus an entitled destination is the entire decision.
--
-- Surface (all service-role only; clients can neither read nor call):
--   * personal_identity_adoptions — audit + state machine:
--       started -> storage_copied -> rows_moved -> done   (or failed)
--   * personal_identity_tombstones — deletion severance (§4.8.4):
--     written by the erasure path (delete-my-data) BEFORE the auth
--     user goes; adoption refuses a tombstoned source forever. No FK
--     to auth.users on purpose: the tombstone must survive the
--     account it severs.
--   * begin_identity_adoption(p_source, p_destination, p_proof)
--       Guards + adoption row + the storage copy plan. Storage bytes
--       live outside the DB transaction (§4.2.3), so the caller (the
--       revenuecat-webhook TRANSFER case / request-identity-adoption)
--       performs the copies from the returned plan, then completes.
--   * complete_identity_adoption(p_adoption_id)
--       ONE transaction: guards re-checked (destination entitled
--       re-verified INSIDE the transaction — the Premium gate),
--       planned copies verified present in storage.objects, then the
--       §4.3 merge, counts, audit note, state -> done. Returns the
--       source cleanup list (every object under the source prefixes;
--       the caller deletes them only after this commits).
--   * adopt_personal_identity(p_source, p_destination, p_proof)
--       The named entry point (DESIGN §4.2). Runs begin; completes
--       immediately when no storage copy is outstanding, otherwise
--       returns state 'started' + the copy plan; a repeat call with
--       the same pair resumes and completes. Idempotent throughout.
--
-- Merge rules implemented (§4.3, unabridged):
--   profiles: free_generations_used SUM; personalization_on/gender
--     destination-wins-if-set (gender inherits the source's when the
--     destination never opted in); entitled is NEVER merged (it is
--     RC-verified truth about the destination). photo_epoch /
--     photo_set_hash follow the reference set when refs are adopted.
--   reference photos: destination set wins outright if it has any;
--     otherwise the source set is adopted (rows re-keyed, paths
--     rewritten, refs-small derivatives copied alongside).
--   heroes: union on (combination_id, variant); destination row kept
--     on collision; imported heroes keep their gate_result verdicts.
--   cells: union; version = max(source, destination) per cell.
--   consents: union; duplicates by (consent_version, created_at)
--     collapse.
--   generation jobs: terminal rows re-keyed to the destination (with
--     result_hero_id remapped onto the merged bank); non-terminal
--     source jobs are CANCELLED, never run into a husk.
--   hero_flags: re-keyed onto the destination's merged hero rows.
--   event/telemetry tables (personal_webhook_events,
--     personal_fallback_events, personal_gate_verdicts,
--     personal_scaffold_events, personal_entitlement_events history):
--     NEVER rewritten. The adoption row is the join key. (One NEW
--     entitlement-events note is written for the destination, per
--     §4.2.4, so the trail shows in existing tooling.)
--   Wear I've Run entries: OUT — device-local, no server table
--     exists and none is created here (DESIGN §4.6, W1).

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------
create table if not exists public.personal_identity_adoptions (
    id                  uuid primary key default gen_random_uuid(),
    source_user_id      uuid not null,
    destination_user_id uuid not null,
    proof_type          text not null check (proof_type in ('transfer_event', 'reconcile_alias')),
    proof               jsonb not null default '{}'::jsonb,
    rc_event_id         text,
    state               text not null default 'started'
                        check (state in ('started', 'storage_copied', 'rows_moved', 'done', 'failed')),
    counts              jsonb not null default '{}'::jsonb,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);
comment on table public.personal_identity_adoptions is
    'RW-137: one row per identity adoption (source husk -> entitled destination), with the resumable state machine of DESIGN §4.2. Service role only. Rows naming a user are deleted by the erasure path after the tombstone is written (§4.8).';

-- A source is adoptable once: at most one non-failed adoption per source.
create unique index if not exists personal_identity_adoptions_source_live_idx
    on public.personal_identity_adoptions (source_user_id)
    where state <> 'failed';
create index if not exists personal_identity_adoptions_dest_idx
    on public.personal_identity_adoptions (destination_user_id, created_at desc);

create table if not exists public.personal_identity_tombstones (
    user_id    uuid primary key,
    deleted_at timestamptz not null default now(),
    source     text not null default 'delete-my-data'
);
comment on table public.personal_identity_tombstones is
    'RW-137 / DESIGN §4.8.4: deletion tombstones. Written by the erasure path before the auth user is deleted; adopt_* refuses any tombstoned source. No FK to auth.users — the tombstone must outlive the account. Service role only.';

alter table public.personal_identity_adoptions  enable row level security;
alter table public.personal_identity_tombstones enable row level security;
-- No client policies: service role only, like personal_webhook_events.

-- ---------------------------------------------------------------------
-- Storage plan helper: the copy list for a (source, destination) pair,
-- computed from the same rules complete_ verifies against. Copies:
--   * heroes — every object under {S}/heroes/ whose (combination_id,
--     variant) the destination lacks (row-derived), plus any orphan
--     object under the prefix not backed by a row (listing-derived).
--   * refs + refs-small — ONLY when the destination has no ref rows
--     (its set would win outright; the source's is then cleanup-only).
-- Paths are rewritten {S}/... -> {D}/... so storage RLS (first path
-- segment == auth.uid()) keeps working untouched.
-- ---------------------------------------------------------------------
create or replace function public.identity_adoption_storage_plan(
    p_source uuid, p_destination uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_refs_adopted boolean;
    v_copies jsonb;
begin
    v_refs_adopted := not exists (
        select 1 from public.personal_reference_photos where user_id = p_destination);

    with wanted as (
        -- heroes the destination lacks, by bank key
        select h.storage_path as src
        from public.personal_heroes h
        where h.user_id = p_source
          and not exists (
              select 1 from public.personal_heroes d
              where d.user_id = p_destination
                and d.combination_id = h.combination_id
                and d.variant = h.variant)
        union
        -- orphan hero objects (no backing row) still move with the bank;
        -- row-backed objects are governed by the branch above alone, so
        -- a collision hero's bytes are never copied over the winner's.
        select o.name
        from storage.objects o
        where o.bucket_id = 'personal-heroes'
          and o.name like p_source::text || '/heroes/%'
          and not exists (
              select 1 from public.personal_heroes h
              where h.user_id = p_source and h.storage_path = o.name)
        union
        -- reference originals + derivatives, only when adopted
        select r.storage_path
        from public.personal_reference_photos r
        where v_refs_adopted and r.user_id = p_source
        union
        select o.name
        from storage.objects o
        where v_refs_adopted
          and o.bucket_id = 'personal-heroes'
          and (o.name like p_source::text || '/refs/%'
               or o.name like p_source::text || '/refs-small/%')
    )
    select coalesce(jsonb_agg(jsonb_build_object(
               'from', src,
               'to', p_destination::text || substr(src, length(p_source::text) + 1)
           ) order by src), '[]'::jsonb)
    into v_copies
    from wanted
    -- A copy is a byte operation: only objects that actually exist in
    -- storage can be copied. (A row whose object is missing is broken
    -- under the source already; its ROW still merges in complete_.)
    where exists (
        select 1 from storage.objects o
        where o.bucket_id = 'personal-heroes' and o.name = wanted.src);

    return jsonb_build_object(
        'refs_adopted', v_refs_adopted,
        'copies', v_copies);
end $$;

-- ---------------------------------------------------------------------
-- begin_identity_adoption: guards + row + plan.
-- ---------------------------------------------------------------------
create or replace function public.begin_identity_adoption(
    p_source uuid, p_destination uuid, p_proof jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_existing public.personal_identity_adoptions%rowtype;
    v_plan jsonb;
    v_id uuid;
    v_proof_type text := coalesce(p_proof->>'type', '');
begin
    if p_source is null or p_destination is null or p_source = p_destination then
        return jsonb_build_object('ok', false, 'reason', 'source_equals_destination');
    end if;
    if v_proof_type not in ('transfer_event', 'reconcile_alias') then
        return jsonb_build_object('ok', false, 'reason', 'unrecognised_proof_type');
    end if;
    -- Severance first: a deleted identity can never be a source again.
    if exists (select 1 from public.personal_identity_tombstones where user_id = p_source) then
        return jsonb_build_object('ok', false, 'reason', 'source_tombstoned');
    end if;
    if not exists (select 1 from auth.users where id = p_source) then
        return jsonb_build_object('ok', false, 'reason', 'source_missing');
    end if;
    if not exists (select 1 from auth.users where id = p_destination) then
        return jsonb_build_object('ok', false, 'reason', 'destination_missing');
    end if;

    select * into v_existing
    from public.personal_identity_adoptions
    where source_user_id = p_source and state <> 'failed'
    order by created_at desc limit 1;
    if found then
        if v_existing.state = 'done' then
            return jsonb_build_object('ok', false, 'reason', 'source_already_adopted',
                'adoption_id', v_existing.id, 'counts', v_existing.counts);
        end if;
        if v_existing.destination_user_id <> p_destination then
            return jsonb_build_object('ok', false, 'reason', 'source_pending_other_destination',
                'adoption_id', v_existing.id, 'state', v_existing.state);
        end if;
        -- Resume the in-flight adoption for this source.
        v_plan := public.identity_adoption_storage_plan(p_source, p_destination);
        return jsonb_build_object('ok', true, 'resumed', true,
            'adoption_id', v_existing.id, 'state', v_existing.state,
            'refs_adopted', v_plan->'refs_adopted', 'copies', v_plan->'copies');
    end if;

    -- Adoptions into one destination run sequentially (§4.2.1).
    if exists (select 1 from public.personal_identity_adoptions
               where destination_user_id = p_destination
                 and state in ('started', 'storage_copied', 'rows_moved')) then
        return jsonb_build_object('ok', false, 'reason', 'destination_adoption_in_progress');
    end if;

    -- Early entitlement read (the authoritative re-check happens
    -- inside complete_'s transaction; this just fails fast).
    if not exists (select 1 from public.personal_profiles
                   where user_id = p_destination and entitled) then
        return jsonb_build_object('ok', false, 'reason', 'destination_not_entitled');
    end if;

    insert into public.personal_identity_adoptions
        (source_user_id, destination_user_id, proof_type, proof, rc_event_id, state)
    values
        (p_source, p_destination, v_proof_type, coalesce(p_proof, '{}'::jsonb),
         p_proof->>'rc_event_id', 'started')
    returning id into v_id;

    v_plan := public.identity_adoption_storage_plan(p_source, p_destination);
    return jsonb_build_object('ok', true, 'resumed', false,
        'adoption_id', v_id, 'state', 'started',
        'refs_adopted', v_plan->'refs_adopted', 'copies', v_plan->'copies');
end $$;

-- ---------------------------------------------------------------------
-- complete_identity_adoption: the merge, in one transaction.
-- ---------------------------------------------------------------------
create or replace function public.complete_identity_adoption(p_adoption_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_row public.personal_identity_adoptions%rowtype;
    v_s uuid; v_d uuid;
    v_plan jsonb;
    v_missing jsonb;
    v_s_prof public.personal_profiles%rowtype;
    v_d_prof public.personal_profiles%rowtype;
    v_refs_adopted boolean;
    v_counts jsonb := '{}'::jsonb;
    v_n integer;
    v_s_cells jsonb;
    v_d_cells jsonb;
    v_cleanup jsonb;
begin
    select * into v_row from public.personal_identity_adoptions
    where id = p_adoption_id for update;
    if not found then
        return jsonb_build_object('ok', false, 'reason', 'adoption_not_found');
    end if;
    if v_row.state = 'done' then
        return jsonb_build_object('ok', true, 'already_done', true,
            'adoption_id', v_row.id, 'state', 'done', 'counts', v_row.counts);
    end if;
    v_s := v_row.source_user_id; v_d := v_row.destination_user_id;

    -- Guards, re-checked inside the transaction (§4.2.1). The
    -- destination entitlement check HERE is the Premium gate.
    if v_s = v_d then
        return jsonb_build_object('ok', false, 'reason', 'source_equals_destination');
    end if;
    if exists (select 1 from public.personal_identity_tombstones where user_id = v_s) then
        return jsonb_build_object('ok', false, 'reason', 'source_tombstoned');
    end if;
    if not exists (select 1 from auth.users where id = v_s) then
        return jsonb_build_object('ok', false, 'reason', 'source_missing');
    end if;
    if not exists (select 1 from auth.users where id = v_d) then
        return jsonb_build_object('ok', false, 'reason', 'destination_missing');
    end if;
    select * into v_d_prof from public.personal_profiles
    where user_id = v_d for update;
    if not found or not v_d_prof.entitled then
        return jsonb_build_object('ok', false, 'reason', 'destination_not_entitled');
    end if;
    select * into v_s_prof from public.personal_profiles where user_id = v_s;

    -- Storage verification: every planned copy must exist at its
    -- destination path before any row moves (§4.2.3).
    v_plan := public.identity_adoption_storage_plan(v_s, v_d);
    v_refs_adopted := (v_plan->>'refs_adopted')::boolean;
    select coalesce(jsonb_agg(c), '[]'::jsonb) into v_missing
    from jsonb_array_elements(v_plan->'copies') c
    where not exists (
        select 1 from storage.objects o
        where o.bucket_id = 'personal-heroes' and o.name = c->>'to');
    if jsonb_array_length(v_missing) > 0 then
        return jsonb_build_object('ok', false, 'reason', 'storage_copy_incomplete',
            'adoption_id', v_row.id, 'state', 'started',
            'missing', v_missing, 'copies', v_plan->'copies');
    end if;
    update public.personal_identity_adoptions
    set state = 'storage_copied', updated_at = now() where id = v_row.id;

    -- Snapshot cell state BEFORE hero writes: the bump trigger rewrites
    -- cells as heroes move, and §4.3 wants version = max(S, D) of the
    -- PRE-merge markers.
    select coalesce(jsonb_object_agg(combination_id, jsonb_build_object(
               'version', version, 'ever_delivered', ever_delivered,
               'free_counted', free_counted, 'regen_status', regen_status)),
           '{}'::jsonb)
    into v_s_cells
    from public.personal_cells where user_id = v_s;
    select coalesce(jsonb_object_agg(combination_id, jsonb_build_object(
               'version', version, 'ever_delivered', ever_delivered,
               'free_counted', free_counted, 'regen_status', regen_status)),
           '{}'::jsonb)
    into v_d_cells
    from public.personal_cells where user_id = v_d;

    -- ---- profiles (§4.3) -------------------------------------------
    if v_s_prof.user_id is not null then
        update public.personal_profiles d
        set free_generations_used = d.free_generations_used + v_s_prof.free_generations_used,
            personalization_on = d.personalization_on or v_s_prof.personalization_on,
            gender = case when d.personalization_on then d.gender else v_s_prof.gender end,
            photo_epoch = case when v_refs_adopted then v_s_prof.photo_epoch else d.photo_epoch end,
            photo_set_hash = case when v_refs_adopted then v_s_prof.photo_set_hash else d.photo_set_hash end,
            updated_at = now()
        where d.user_id = v_d;
        v_counts := v_counts || jsonb_build_object(
            'free_generations_used_sum', v_d_prof.free_generations_used + v_s_prof.free_generations_used);
        delete from public.personal_profiles where user_id = v_s;
    end if;
    v_counts := v_counts || jsonb_build_object('refs_adopted', v_refs_adopted);

    -- ---- reference photos (§4.3) ------------------------------------
    if v_refs_adopted then
        update public.personal_reference_photos
        set user_id = v_d,
            storage_path = v_d::text || substr(storage_path, length(v_s::text) + 1)
        where user_id = v_s;
        get diagnostics v_n = row_count;
        v_counts := v_counts || jsonb_build_object('refs_imported', v_n);
    else
        -- Destination set wins outright; the source's rows go with the
        -- husk (their objects are in the cleanup list).
        delete from public.personal_reference_photos where user_id = v_s;
        get diagnostics v_n = row_count;
        v_counts := v_counts || jsonb_build_object('refs_superseded_deleted', v_n);
    end if;

    -- ---- heroes: union on (combination_id, variant) (§4.3) ----------
    -- Collision count BEFORE the import insert: afterwards every
    -- source hero has a destination counterpart by construction.
    select count(*) into v_n from public.personal_heroes s
    where s.user_id = v_s
      and exists (select 1 from public.personal_heroes d
                  where d.user_id = v_d
                    and d.combination_id = s.combination_id
                    and d.variant = s.variant);
    v_counts := v_counts || jsonb_build_object('heroes_collisions_kept_destination', v_n);
    insert into public.personal_heroes
        (user_id, combination_id, gender, weather, temp_bracket, time_of_day,
         outfit_signature, outfit_items, storage_path, variant, prompt,
         gate_result, created_at, epoch, image_hash)
    select v_d, h.combination_id, h.gender, h.weather, h.temp_bracket,
           h.time_of_day, h.outfit_signature, h.outfit_items,
           v_d::text || substr(h.storage_path, length(v_s::text) + 1),
           h.variant, h.prompt, h.gate_result, h.created_at, h.epoch, h.image_hash
    from public.personal_heroes h
    where h.user_id = v_s
      and not exists (select 1 from public.personal_heroes d
                      where d.user_id = v_d
                        and d.combination_id = h.combination_id
                        and d.variant = h.variant);
    get diagnostics v_n = row_count;
    v_counts := v_counts || jsonb_build_object('heroes_imported', v_n);

    -- ---- hero flags: re-key onto the merged bank (§4.3) -------------
    insert into public.personal_hero_flags
        (user_id, hero_id, combination_id, variant, storage_path, reason, created_at)
    select v_d, dh.id, f.combination_id, f.variant,
           v_d::text || substr(f.storage_path, length(v_s::text) + 1),
           f.reason, f.created_at
    from public.personal_hero_flags f
    join public.personal_heroes dh
      on dh.user_id = v_d
     and dh.combination_id = f.combination_id
     and dh.variant = f.variant
    where f.user_id = v_s
      and not exists (select 1 from public.personal_hero_flags x
                      where x.user_id = v_d and x.hero_id = dh.id
                        and x.reason = f.reason);
    get diagnostics v_n = row_count;
    v_counts := v_counts || jsonb_build_object('hero_flags_rekeyed', v_n);
    delete from public.personal_hero_flags where user_id = v_s;

    -- ---- generation jobs (§4.3) --------------------------------------
    -- Terminal rows re-key, result_hero_id remapped onto the merged
    -- bank by (combination_id, variant); non-terminal source jobs are
    -- cancelled in place — never run into a husk.
    update public.personal_generation_jobs j
    set user_id = v_d,
        result_hero_id = (
            select dh.id from public.personal_heroes sh
            join public.personal_heroes dh
              on dh.user_id = v_d
             and dh.combination_id = sh.combination_id
             and dh.variant = sh.variant
            where sh.id = j.result_hero_id)
    where j.user_id = v_s and j.status in ('COMPLETED', 'FAILED', 'CANCELLED');
    get diagnostics v_n = row_count;
    v_counts := v_counts || jsonb_build_object('jobs_rekeyed', v_n);
    update public.personal_generation_jobs
    set status = 'CANCELLED'
    where user_id = v_s and status in ('QUEUED', 'PROCESSING');
    get diagnostics v_n = row_count;
    v_counts := v_counts || jsonb_build_object('jobs_cancelled', v_n);

    -- ---- consents: union, (version, timestamp) duplicates collapse ---
    insert into public.personal_consents
        (user_id, consent_version, attested_own_likeness, attested_adult, created_at)
    select v_d, c.consent_version, c.attested_own_likeness, c.attested_adult, c.created_at
    from public.personal_consents c
    where c.user_id = v_s
      and not exists (select 1 from public.personal_consents d
                      where d.user_id = v_d
                        and d.consent_version = c.consent_version
                        and d.created_at = c.created_at);
    get diagnostics v_n = row_count;
    v_counts := v_counts || jsonb_build_object('consents_imported', v_n);
    delete from public.personal_consents where user_id = v_s;

    -- ---- source heroes out (imported copies + collision losers) -----
    delete from public.personal_heroes where user_id = v_s;

    -- ---- cells: union, version = max (§4.3) --------------------------
    -- Written from the PRE-merge snapshots with exact values, so the
    -- bump trigger's writes during the hero move cannot inflate a
    -- version past max(S, D). regen_status: destination's when it has
    -- the cell, else the source's.
    with keys as (
        select key from jsonb_object_keys(v_s_cells) as key
        union
        select key from jsonb_object_keys(v_d_cells) as key
    )
    insert into public.personal_cells
        (user_id, combination_id, version, ever_delivered, free_counted, regen_status, updated_at)
    select v_d, k.key,
           greatest(coalesce((v_s_cells->k.key->>'version')::integer, 0),
                    coalesce((v_d_cells->k.key->>'version')::integer, 0)),
           coalesce((v_s_cells->k.key->>'ever_delivered')::boolean, false)
               or coalesce((v_d_cells->k.key->>'ever_delivered')::boolean, false),
           coalesce((v_s_cells->k.key->>'free_counted')::boolean, false)
               or coalesce((v_d_cells->k.key->>'free_counted')::boolean, false),
           coalesce(v_d_cells->k.key->>'regen_status', v_s_cells->k.key->>'regen_status'),
           now()
    from keys k
    on conflict (user_id, combination_id) do update
        set version = excluded.version,
            ever_delivered = excluded.ever_delivered,
            free_counted = excluded.free_counted,
            regen_status = excluded.regen_status,
            updated_at = now();
    select count(*) into v_n from (
        select key from jsonb_object_keys(v_s_cells) as key
        union select key from jsonb_object_keys(v_d_cells) as key) u;
    v_counts := v_counts || jsonb_build_object('cells_merged', v_n);
    delete from public.personal_cells where user_id = v_s;

    update public.personal_identity_adoptions
    set state = 'rows_moved', updated_at = now() where id = v_row.id;

    -- Audit note in existing tooling (§4.2.4): one entitlement-events
    -- row for the destination carrying its current (RC-verified) flag.
    insert into public.personal_entitlement_events (user_id, entitled, source)
    values (v_d, true, 'identity-adoption');

    -- Cleanup list: EVERY object under the source's prefixes. Copied
    -- objects now live at destination paths; the rest was superseded.
    -- The caller deletes these only after this transaction commits.
    select coalesce(jsonb_agg(o.name order by o.name), '[]'::jsonb) into v_cleanup
    from storage.objects o
    where o.bucket_id = 'personal-heroes'
      and o.name like v_s::text || '/%';
    v_counts := v_counts || jsonb_build_object(
        'storage_copies', jsonb_array_length(v_plan->'copies'));

    update public.personal_identity_adoptions
    set state = 'done', counts = v_counts, updated_at = now()
    where id = v_row.id;

    return jsonb_build_object('ok', true, 'adoption_id', v_row.id,
        'state', 'done', 'counts', v_counts, 'source_cleanup_paths', v_cleanup);
end $$;

-- ---------------------------------------------------------------------
-- adopt_personal_identity: the §4.2 entry point. Storage copy happens
-- between begin and complete (outside the DB transaction, §4.2.3);
-- when copies are outstanding this returns the plan and the caller
-- performs them, then calls again to resume + complete.
-- ---------------------------------------------------------------------
create or replace function public.adopt_personal_identity(
    p_source uuid, p_destination uuid, p_proof jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_begin jsonb;
    v_done jsonb;
begin
    v_begin := public.begin_identity_adoption(p_source, p_destination, p_proof);
    if not (v_begin->>'ok')::boolean then
        return v_begin;
    end if;
    v_done := public.complete_identity_adoption((v_begin->>'adoption_id')::uuid);
    if (v_done->>'ok')::boolean then
        return v_done;
    end if;
    if v_done->>'reason' = 'storage_copy_incomplete' then
        return jsonb_build_object('ok', true, 'state', 'started',
            'adoption_id', v_begin->'adoption_id',
            'refs_adopted', v_begin->'refs_adopted',
            'copies', v_done->'copies',
            'note', 'perform the storage copies, then call again to complete');
    end if;
    return v_done;
end $$;

revoke execute on function public.identity_adoption_storage_plan(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.begin_identity_adoption(uuid, uuid, jsonb) from public, anon, authenticated;
revoke execute on function public.complete_identity_adoption(uuid) from public, anon, authenticated;
revoke execute on function public.adopt_personal_identity(uuid, uuid, jsonb) from public, anon, authenticated;
grant  execute on function public.identity_adoption_storage_plan(uuid, uuid) to service_role;
grant  execute on function public.begin_identity_adoption(uuid, uuid, jsonb) to service_role;
grant  execute on function public.complete_identity_adoption(uuid) to service_role;
grant  execute on function public.adopt_personal_identity(uuid, uuid, jsonb) to service_role;
