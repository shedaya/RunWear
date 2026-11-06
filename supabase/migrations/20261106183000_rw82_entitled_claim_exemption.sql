-- RW-82 (2026-11-06): entitled (Premium) users are exempt from the
-- free-delivery allowance in claim_free_delivery.
--
-- Defect: the worker's delivery path calls this RPC for EVERY
-- free_on_demand job, and the RPC enforced the 5-distinct-cell free
-- cap with no entitlement exemption — while the enqueue gate in
-- request-personal-generation admits entitled users unconditionally
-- (its jobs are still kind 'free_on_demand'; the kind does not encode
-- entitlement). Every gate-passing delivery to an entitled user from
-- the 6th distinct cell onward was refused (allowance_exhausted) and
-- the just-generated hero deleted, breaking subscriber convergence
-- beyond five on-demand cells. Premium (Make It Me) is unlimited
-- looks by the settled product definition.
--
-- Fix: an entitled profile's delivery always claims. It records the
-- delivery (ever_delivered = true) WITHOUT charging the free counter
-- and WITHOUT marking the cell free-counted — the same bookkeeping
-- the worker applies to non-free job kinds. Free users' behaviour is
-- byte-for-byte the pre-existing body below the exemption branch:
-- same already-counted fast path, same per-user advisory lock, same
-- cap of 5, same counter increment, same cell upsert.
--
-- Repo note: this function previously existed ONLY in the production
-- database (no migration captured it). This file is its first repo
-- capture AND the RW-82 amendment, applied to production as one
-- CREATE OR REPLACE on 2026-11-06; the deployed definition was read
-- back and verified identical to this file.

CREATE OR REPLACE FUNCTION public.claim_free_delivery(p_user_id uuid, p_combination_id text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    v_counted boolean;
    v_used integer;
    v_entitled boolean;
begin
    -- RW-82: entitled users are exempt from the free allowance —
    -- their delivery always claims, uncharged and uncounted.
    select entitled into v_entitled
    from public.personal_profiles
    where user_id = p_user_id;
    if coalesce(v_entitled, false) then
        insert into public.personal_cells (user_id, combination_id, ever_delivered, free_counted, updated_at)
        values (p_user_id, p_combination_id, true, false, now())
        on conflict (user_id, combination_id)
        do update set ever_delivered = true, updated_at = now();
        return true;
    end if;

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
end $function$;
