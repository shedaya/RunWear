-- RW-108 (2026-11-06): claim_free_delivery's free path must mark
-- ever_delivered consistently with the hero it just delivered.
--
-- Defect: the free path's cell upsert sets ever_delivered = true
-- in the INSERT values, but its ON CONFLICT DO UPDATE sets only
-- free_counted. Every free delivery to a cell row that already
-- exists (the normal case — the request path creates the cell
-- before the job runs) therefore landed free_counted = true with
-- ever_delivered stuck at false. Four of Sol's five Nov 6 free
-- looks read ever_delivered = false while holding delivered
-- heroes (nine rows project-wide at fix time). Serving never
-- reads the field, but request-personal-generation's
-- reference-change pending-marking does — a latent hazard.
--
-- Fix (free path only): the conflict update now sets
-- ever_delivered = true alongside free_counted, and the
-- already-counted fast path heals a straggler row (sets
-- ever_delivered where it is still false) before returning, so
-- any pre-fix row that is ever claimed again self-repairs. The
-- RW-82 entitled exemption branch above it is byte-for-byte the
-- deployed body: same fast path, same advisory lock, same cap
-- of 5, same counter increment. Applied to production as one
-- CREATE OR REPLACE on 2026-11-06; the deployed definition was
-- read back and verified to differ from the RW-82 definition
-- only in the two free-path spots named here.

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
        -- RW-108: an already-counted cell holds a delivered hero
        -- by definition; heal ever_delivered on any straggler row
        -- left by the pre-fix conflict update before returning.
        update public.personal_cells
            set ever_delivered = true, updated_at = now()
        where user_id = p_user_id and combination_id = p_combination_id
          and ever_delivered = false;
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
    do update set free_counted = true, ever_delivered = true, updated_at = now();

    return true;
end $function$;
