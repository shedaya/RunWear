-- RW-179b (2026-11-25): atomic, deduplicated enqueue for personal
-- LOOK jobs (every kind except 'replenishment', whose multi-candidate
-- top-ups are intentional and already net against active jobs).
--
-- The defect: every enqueue path (start-personal-fill,
-- request-personal-generation, get-personal-hero's convergence
-- insert) did check-then-insert against personal_generation_jobs.
-- Two calls landing inside each other's read window both saw "no
-- active job" and both inserted — on 2026-11-25 a fresh subscriber
-- fill produced 96 jobs for ~52 cells, most cells holding exactly
-- two active duplicates inserted ~20 ms apart by two racing fill
-- invocations.
--
-- This function is the single atomic enqueue path for look jobs.
-- It takes a per-account advisory transaction lock, so the
-- existence check and the insert cannot interleave with another
-- enqueue for the same account, and it returns which cells were
-- inserted and which were skipped because an active (QUEUED or
-- PROCESSING) look job already covers them. An account can
-- therefore never hold more than one active look job per cell.
--
-- requested_at is stamped with clock_timestamp() plus a
-- per-position millisecond offset: now() is constant inside a
-- transaction, and the fill's forecast order is carried by
-- requested_at (the worker claims by priority, then requested_at),
-- so a batch insert must not stamp every row identically.
--
-- Grants mirror the house convention for SECURITY DEFINER personal
-- RPCs (adopt_personal_identity, claim_free_delivery): execute for
-- postgres and service_role only. All callers are edge functions
-- running with the service role.

create or replace function public.enqueue_personal_look_jobs(
  p_user_id uuid,
  p_jobs jsonb
)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_job jsonb;
  v_idx int := 0;
  v_inserted int := 0;
  v_existing_id uuid;
  v_new_id uuid;
  v_inserted_ids jsonb := '[]'::jsonb;
  v_skipped jsonb := '[]'::jsonb;
begin
  if p_user_id is null then
    return jsonb_build_object('ok', false, 'reason', 'user_id_required');
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_user_id::text, 0));

  for v_job in
    select * from jsonb_array_elements(coalesce(p_jobs, '[]'::jsonb))
  loop
    v_idx := v_idx + 1;

    select id into v_existing_id
    from public.personal_generation_jobs
    where user_id = p_user_id
      and combination_id = v_job->>'combination_id'
      and status in ('QUEUED', 'PROCESSING')
      and kind <> 'replenishment'
    order by requested_at
    limit 1;

    if v_existing_id is not null then
      v_skipped := v_skipped || jsonb_build_object(
        'combination_id', v_job->>'combination_id',
        'job_id', v_existing_id);
    else
      insert into public.personal_generation_jobs (
        user_id, combination_id, gender, weather, temp_bracket,
        time_of_day, outfit_signature, outfit_items, kind, priority,
        epoch, status, requested_at
      ) values (
        p_user_id,
        v_job->>'combination_id',
        v_job->>'gender',
        v_job->>'weather',
        v_job->>'temp_bracket',
        v_job->>'time_of_day',
        v_job->>'outfit_signature',
        coalesce(v_job->'outfit_items', '[]'::jsonb),
        v_job->>'kind',
        coalesce((v_job->>'priority')::int, 20),
        coalesce((v_job->>'epoch')::int, 0),
        'QUEUED',
        clock_timestamp() + (v_idx * interval '1 millisecond')
      )
      returning id into v_new_id;
      v_inserted := v_inserted + 1;
      v_inserted_ids := v_inserted_ids || to_jsonb(v_new_id);
    end if;
  end loop;

  return jsonb_build_object(
    'ok', true,
    'inserted', v_inserted,
    'inserted_ids', v_inserted_ids,
    'skipped', v_skipped);
end
$function$;

revoke all on function public.enqueue_personal_look_jobs(uuid, jsonb) from public;
grant execute on function public.enqueue_personal_look_jobs(uuid, jsonb) to service_role;
