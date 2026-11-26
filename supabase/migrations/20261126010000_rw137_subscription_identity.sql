-- RW-137 subscription-as-identity: the bank follows the SUBSCRIPTION,
-- not the device uid (Sol's ruling, Nov 7, 2026: subscription-as-
-- identity, no login, stores are identity escrow; durable identity is
-- a standing Premium property; control = delete-my-data; retention =
-- subscription + 12 months).
--
-- This migration replaces the MECHANISM of the Nov 10 RW-137 build
-- (uid + adoption via RevenueCat original_app_user_id / aliases). The
-- Nov 25, 2026 restore exercise proved that mechanism has never
-- executed: RevenueCat's v1 subscriber payload carries no `aliases`
-- field, a transfer is not a merge (the destination keeps its own
-- original_app_user_id), adoption_available never fires, and
-- personal_identity_adoptions holds zero rows. The stable identifiers
-- exist only in RevenueCat WEBHOOK events (original_transaction_id on
-- lifecycle events; transferred_from / transferred_to on TRANSFER).
--
-- Design (fix report §21):
-- * personal_subscription_banks — the registry: subscription_key ->
--   bank_user_id (the uid whose rows hold the bank). Many keys may
--   point at one bank (a store key and an rc key for the same
--   subscription). Written ONLY by resolve_subscription_bank /
--   backfill (service role), never by clients.
-- * personal_subscription_links — the evidence table: the identity
--   fields of every RevenueCat webhook event, kept verbatim (the
--   legacy personal_webhook_events.detail stores only a summary and
--   cannot answer identity questions). Written by revenuecat-webhook.
-- * personal_subscription_tombstones — severance: when a bank is
--   deleted (delete-my-data), its keys are tombstoned so a later
--   restore of the same subscription can never resurrect the deleted
--   bank (the RW-137 S4c severance rule, carried to the new keying).
-- * personal_profiles gains the per-uid RESOLUTION CACHE:
--   bank_user_id (whose bank this uid currently reads/writes),
--   subscription_key (the key that resolved it), bank_resolved_at.
--   reconcile-personal-entitlement is the only writer; every serving
--   function reads bank_user_id and falls back to the caller's uid.
--
-- RLS: enabled with NO policies on the three new tables — service
-- role only, the same posture as the jetguide backup schema. The new
-- profile columns inherit the table's existing policies.
--
-- Dependencies: personal_identity_tombstones (migration
-- 20261110120000, applied to production Nov 10, 2026 from
-- ship/rw137-server-2026-11-10; the file travels with this branch so
-- the chain replays whole). The delete-my-data control's server
-- half is the delete-my-data edge function (ported with this
-- branch; it is NOT deployed in production today, so the shipped
-- client's control currently has nothing to call — this branch
-- supplies it, and sever_subscription_bank below is its severance
-- step).

-- ----------------------------------------------------------------------
-- Key derivation: 'store:<store>:<original_transaction_id>'.
-- Google Play order ids increment per renewal as GPA.<base>..N; the
-- base (suffix stripped) identifies the subscription chain. Apple
-- original transaction ids carry no suffix and pass through unchanged.
-- ----------------------------------------------------------------------
create or replace function public.derive_subscription_key(
    p_store text,
    p_original_transaction_id text
) returns text
language sql
immutable
as $$
    select case
        when p_original_transaction_id is null
             or btrim(p_original_transaction_id) = ''
            then null
        else 'store:' || coalesce(nullif(btrim(p_store), ''), 'unknown')
             || ':' || split_part(p_original_transaction_id, '..', 1)
    end
$$;

comment on function public.derive_subscription_key(text, text) is
    'RW-137: subscription key from a store original transaction id (Google ''..N'' renewal suffix stripped). Null in, null out.';

-- ----------------------------------------------------------------------
-- The registry
-- ----------------------------------------------------------------------
create table if not exists public.personal_subscription_banks (
    subscription_key text primary key,
    bank_user_id     uuid not null,
    key_source       text not null
                     check (key_source in ('store_otid', 'rc_original',
                                           'transfer', 'backfill', 'operator')),
    platform         text,
    store            text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
comment on table public.personal_subscription_banks is
    'RW-137 subscription-as-identity registry: subscription_key -> the uid whose rows hold that subscription''s personal bank. Service-role only.';
alter table public.personal_subscription_banks enable row level security;

create index if not exists personal_subscription_banks_bank_idx
    on public.personal_subscription_banks (bank_user_id);

-- ----------------------------------------------------------------------
-- The evidence table (webhook identity fields, verbatim)
-- ----------------------------------------------------------------------
create table if not exists public.personal_subscription_links (
    id                      uuid primary key default gen_random_uuid(),
    event_id                text unique,
    event_type              text not null,
    app_user_id             text,
    original_app_user_id    text,
    transferred_from        text,
    transferred_to          text,
    subscription_key        text,
    original_transaction_id text,
    transaction_id          text,
    store                   text,
    received_at             timestamptz not null default now()
);
comment on table public.personal_subscription_links is
    'RW-137: identity fields of RevenueCat webhook events, kept verbatim so bank resolution can be derived from evidence (original_transaction_id exists ONLY here and in RC webhooks — never in the v1 subscriber GET). Service-role only.';
alter table public.personal_subscription_links enable row level security;

create index if not exists personal_subscription_links_app_user_idx
    on public.personal_subscription_links (app_user_id);
create index if not exists personal_subscription_links_original_idx
    on public.personal_subscription_links (original_app_user_id);
create index if not exists personal_subscription_links_key_idx
    on public.personal_subscription_links (subscription_key);
create index if not exists personal_subscription_links_transfer_to_idx
    on public.personal_subscription_links (transferred_to);

-- ----------------------------------------------------------------------
-- Severance tombstones
-- ----------------------------------------------------------------------
create table if not exists public.personal_subscription_tombstones (
    subscription_key text primary key,
    bank_user_id     uuid not null,
    severed_at       timestamptz not null default now(),
    source           text not null default 'delete-my-data'
);
comment on table public.personal_subscription_tombstones is
    'RW-137 severance: keys whose bank was deleted. resolve_subscription_bank never resolves a caller to a tombstoned key''s bank; the key may later re-register to a NEW bank (a genuinely fresh start after deletion). Service-role only.';
alter table public.personal_subscription_tombstones enable row level security;

-- ----------------------------------------------------------------------
-- Resolution cache on the profile
-- ----------------------------------------------------------------------
alter table public.personal_profiles
    add column if not exists bank_user_id uuid,
    add column if not exists subscription_key text,
    add column if not exists bank_resolved_at timestamptz;

comment on column public.personal_profiles.bank_user_id is
    'RW-137: the uid whose personal bank this user currently reads/writes, resolved from the subscription by reconcile-personal-entitlement. Null = self (unresolved / free / lapsed).';

-- ----------------------------------------------------------------------
-- resolve_subscription_bank — the resolver. Called by
-- reconcile-personal-entitlement AFTER it has verified the caller's
-- entitlement against RevenueCat. p_keys is ordered strongest-first
-- (store keys from webhook evidence, then the rc original key).
-- p_original_candidates are uids the subscription may have been
-- purchased under (RC original_app_user_id; TRANSFER sources).
--
-- Rules, in order:
--  1. A registered, unsevered key wins. A key tombstoned against a
--     DIFFERENT bank never resolves to that bank (severance).
--  2. Original-owner rule: the first original candidate (other than
--     the caller) that holds a bank and is not deletion-tombstoned
--     (personal_identity_tombstones) owns the subscription; all
--     candidate keys register to it.
--  3. Otherwise the caller owns it; all candidate keys register to
--     the caller.
-- Collision (the resolved bank is not the caller AND the caller holds
-- heroes of their own) is REPORTED, never merged: the subscription's
-- bank is served; the caller's stray rows stay keyed to the caller.
-- ----------------------------------------------------------------------
create or replace function public.resolve_subscription_bank(
    p_caller uuid,
    p_keys text[],
    p_key_sources text[],
    p_original_candidates uuid[],
    p_platform text,
    p_store text
) returns table (
    bank_user_id uuid,
    subscription_key text,
    state text,
    collision boolean
)
language plpgsql
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_key text;
    v_bank uuid;
    v_state text;
    v_candidate uuid;
    v_i integer;
    v_collision boolean := false;
begin
    -- Rule 1: registered keys, strongest first.
    foreach v_key in array coalesce(p_keys, array[]::text[])
    loop
        select b.bank_user_id into v_bank
          from public.personal_subscription_banks b
         where b.subscription_key = v_key;
        if found then
            if exists (
                select 1 from public.personal_subscription_tombstones t
                 where t.subscription_key = v_key
                   and t.bank_user_id = v_bank
            ) then
                -- Severed against this bank: the registry row is the
                -- pre-deletion residue of a delete that did not run
                -- the severance sweep; never resolve to it.
                v_bank := null;
            else
                v_state := case when v_bank = p_caller then 'own' else 'restored' end;
                v_collision := v_bank <> p_caller and exists (
                    select 1 from public.personal_heroes h where h.user_id = p_caller
                );
                update public.personal_profiles
                   set bank_user_id = v_bank,
                       subscription_key = v_key,
                       bank_resolved_at = now(),
                       updated_at = now()
                 where user_id = p_caller;
                return query select v_bank, v_key, v_state, v_collision;
                return;
            end if;
        end if;
    end loop;

    -- Rule 2: original-owner rule.
    foreach v_candidate in array coalesce(p_original_candidates, array[]::uuid[])
    loop
        if v_candidate is null or v_candidate = p_caller then
            continue;
        end if;
        if exists (
            select 1 from public.personal_identity_tombstones t
             where t.user_id = v_candidate
        ) then
            continue;  -- a deleted identity owns nothing, ever again
        end if;
        if exists (select 1 from public.personal_heroes h where h.user_id = v_candidate)
           or exists (select 1 from public.personal_profiles p
                       where p.user_id = v_candidate and p.personalization_on) then
            v_bank := v_candidate;
            v_state := 'restored';
            v_collision := exists (
                select 1 from public.personal_heroes h where h.user_id = p_caller
            );
            for v_i in 1 .. coalesce(array_length(p_keys, 1), 0)
            loop
                insert into public.personal_subscription_banks
                    (subscription_key, bank_user_id, key_source, platform, store)
                values (p_keys[v_i], v_bank,
                        coalesce(p_key_sources[v_i], 'rc_original'),
                        p_platform, p_store)
                on conflict (subscription_key) do nothing;
            end loop;
            update public.personal_profiles
               set bank_user_id = v_bank,
                   subscription_key = p_keys[1],
                   bank_resolved_at = now(),
                   updated_at = now()
             where user_id = p_caller;
            return query select v_bank, p_keys[1], v_state, v_collision;
            return;
        end if;
    end loop;

    -- Rule 3: the caller owns the subscription.
    v_bank := p_caller;
    v_state := case
        when exists (select 1 from public.personal_heroes h where h.user_id = p_caller)
            then 'own'
        else 'new'
    end;
    for v_i in 1 .. coalesce(array_length(p_keys, 1), 0)
    loop
        insert into public.personal_subscription_banks
            (subscription_key, bank_user_id, key_source, platform, store)
        values (p_keys[v_i], v_bank,
                coalesce(p_key_sources[v_i], 'rc_original'),
                p_platform, p_store)
        on conflict (subscription_key) do nothing;
    end loop;
    update public.personal_profiles
       set bank_user_id = v_bank,
           subscription_key = p_keys[1],
           bank_resolved_at = now(),
           updated_at = now()
     where user_id = p_caller;
    return query select v_bank, p_keys[1], v_state, v_collision;
end;
$$;

comment on function public.resolve_subscription_bank(uuid, text[], text[], uuid[], text, text) is
    'RW-137 resolver: maps a verified-entitled caller to the uid holding their subscription''s bank, registering candidate keys. See migration header for the rules. Service-role only.';

-- ----------------------------------------------------------------------
-- sever_subscription_bank — the delete-my-data severance. Removes
-- every registry row that could resolve anyone to p_user_id's bank
-- (rows naming the bank, plus the row for the key on p_user_id's own
-- profile if it points elsewhere) and tombstones those keys against
-- the bank they named, so the deleted bank can never be resurrected
-- by a later restore. Clears the resolution cache on every profile
-- that pointed at a severed bank.
-- ----------------------------------------------------------------------
create or replace function public.sever_subscription_bank(
    p_user_id uuid
) returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_severed integer := 0;
    v_bank uuid;
    r record;
begin
    -- The bank the caller resolves to (their own uid when never
    -- resolved): every key naming THAT bank goes, not just keys
    -- naming the caller — after a restore the keys name the bank's
    -- uid, and the caller is a different uid entirely.
    select coalesce(p.bank_user_id, p.user_id) into v_bank
      from public.personal_profiles p
     where p.user_id = p_user_id;
    v_bank := coalesce(v_bank, p_user_id);
    for r in
        select distinct b.subscription_key, b.bank_user_id
          from public.personal_subscription_banks b
         where b.bank_user_id in (p_user_id, v_bank)
            or b.subscription_key = (
                select p.subscription_key from public.personal_profiles p
                 where p.user_id = p_user_id
            )
    loop
        insert into public.personal_subscription_tombstones
            (subscription_key, bank_user_id, source)
        values (r.subscription_key, r.bank_user_id, 'delete-my-data')
        on conflict (subscription_key) do update
            set bank_user_id = excluded.bank_user_id,
                severed_at = now(),
                source = excluded.source;
        delete from public.personal_subscription_banks b
         where b.subscription_key = r.subscription_key;
        update public.personal_profiles p
           set bank_user_id = null,
               subscription_key = null,
               bank_resolved_at = now(),
               updated_at = now()
         where p.bank_user_id = r.bank_user_id
           and p.subscription_key = r.subscription_key;
        v_severed := v_severed + 1;
    end loop;
    return v_severed;
end;
$$;

comment on function public.sever_subscription_bank(uuid) is
    'RW-137 severance for delete-my-data: unregisters + tombstones every subscription key that resolves to the user''s bank. Returns the number of keys severed. Service-role only.';

-- ----------------------------------------------------------------------
-- Backfill (migration path for banks created under device uids).
-- backfill_subscription_bank_for is the per-user unit: it registers
-- the given key to the user (when unregistered) and points their
-- profile at their own bank. The sweep form registers the rc key
-- ('rc:' || user_id) for every entitled profile — correct for every
-- subscriber who purchased under their own uid (the shipped clients
-- configure/log in with the uid before purchase). Anyone who restored
-- pre-fix is re-resolved by reconcile's original-owner rule on their
-- next contact; the sweep never guesses a cross-uid link.
-- EXECUTED BY THE OPERATOR AT DEPLOY TIME, per user or as a sweep —
-- writing this function executes nothing.
-- ----------------------------------------------------------------------
create or replace function public.backfill_subscription_bank_for(
    p_user_id uuid,
    p_key text,
    p_key_source text default 'backfill'
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_bank uuid;
begin
    if p_key is not null then
        insert into public.personal_subscription_banks
            (subscription_key, bank_user_id, key_source)
        values (p_key, p_user_id, p_key_source)
        on conflict (subscription_key) do nothing;
        select b.bank_user_id into v_bank
          from public.personal_subscription_banks b
         where b.subscription_key = p_key;
    end if;
    v_bank := coalesce(v_bank, p_user_id);
    update public.personal_profiles
       set bank_user_id = v_bank,
           subscription_key = coalesce(subscription_key, p_key),
           bank_resolved_at = now(),
           updated_at = now()
     where user_id = p_user_id;
    return v_bank;
end;
$$;

create or replace function public.backfill_subscription_banks()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    r record;
    v_count integer := 0;
begin
    for r in
        select p.user_id from public.personal_profiles p where p.entitled
    loop
        perform public.backfill_subscription_bank_for(
            r.user_id, 'rc:' || r.user_id::text, 'backfill');
        v_count := v_count + 1;
    end loop;
    return v_count;
end;
$$;

comment on function public.backfill_subscription_banks() is
    'RW-137 deploy-time sweep: registers rc:self keys for all entitled profiles. NOT run by the migration; the operator runs it (or the per-user form) at deploy.';

-- Service-role only: nobody else may resolve, sever, or backfill.
revoke all on function public.resolve_subscription_bank(uuid, text[], text[], uuid[], text, text)
    from public, anon, authenticated;
revoke all on function public.sever_subscription_bank(uuid)
    from public, anon, authenticated;
revoke all on function public.backfill_subscription_bank_for(uuid, text, text)
    from public, anon, authenticated;
revoke all on function public.backfill_subscription_banks()
    from public, anon, authenticated;
grant execute on function public.resolve_subscription_bank(uuid, text[], text[], uuid[], text, text)
    to service_role;
grant execute on function public.sever_subscription_bank(uuid) to service_role;
grant execute on function public.backfill_subscription_bank_for(uuid, text, text) to service_role;
grant execute on function public.backfill_subscription_banks() to service_role;
