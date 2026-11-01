-- RW-14 personal hero drain scheduler (2026-10-31)
--
-- Schedules fulfillment of the PERSONAL generation queue
-- (personal_generation_jobs) every 5 minutes via the
-- personal-hero-worker edge function. This is a NEW job for the
-- personal queue only.
--
-- It does NOT touch 'hero-generation-drain' (the generic pool drain),
-- which stays INACTIVE per Sol's Oct 31, 2026 pool-freeze ruling.
--
-- Same Vault pattern as the generic drain: the job reads project_url
-- and service_role_key from Vault at fire time; both entries already
-- exist (created for the generic drain, real service key stored
-- 2026-10-28).

create extension if not exists pg_cron;
create extension if not exists pg_net;

do $$
begin
    if exists (select 1 from cron.job where jobname = 'personal-hero-drain') then
        perform cron.unschedule('personal-hero-drain');
    end if;
end $$;

select cron.schedule(
    'personal-hero-drain',
    '*/5 * * * *',
    $$
    select net.http_post(
        url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url')
               || '/functions/v1/personal-hero-worker',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key')
        ),
        body := '{"drain": 2}'::jsonb,
        timeout_milliseconds := 300000
    );
    $$
);

-- Pause without deleting:  select cron.unschedule('personal-hero-drain');
-- Verify: select jobid, jobname, schedule, active from cron.job
--         where jobname in ('personal-hero-drain', 'hero-generation-drain');
