-- Hero generation revival: drain scheduler (2026-10-27)
--
-- The generate-hero-image edge function went dormant in Mar 2026 because
-- NOTHING invoked it: fulfillment only ever happened when a human ran
-- the manual scripts. This migration schedules a batch drain every
-- 30 minutes: pg_cron fires net.http_post at the function with ?drain=5,
-- and the function processes up to 5 QUEUED jobs (bounded further by its
-- DAILY_GENERATION_CAP of 50/day). Queued jobs therefore fulfill
-- themselves again, and every new image lands as review_status='pending'
-- per the review-gate migration (20261027120000), which must be applied
-- BEFORE this one.
--
-- APPLY IN THE SUPABASE DASHBOARD (SQL editor) for project
-- ebicqznlcjbqcukjfzcf — the repo carries no service-role credential.
-- Full ordered walkthrough: docs/hero-generation-revival.md.
--
-- SECRETS: the cron job reads the project URL and the service-role key
-- from Supabase Vault at fire time. This file creates the Vault entries
-- ONLY if they do not already exist, and the service-role entry is
-- created with a PLACEHOLDER value on purpose: a human must replace it
-- with the real key (Dashboard -> Project Settings -> API -> service_role)
-- before the schedule can authenticate. The drain fails loudly (401 from
-- the function gateway) until that is done. NEVER commit a real key here.

create extension if not exists pg_cron;
create extension if not exists pg_net;

-- Vault placeholders (no-ops if the secrets already exist).
do $$
begin
    if not exists (select 1 from vault.secrets where name = 'project_url') then
        perform vault.create_secret(
            'https://ebicqznlcjbqcukjfzcf.supabase.co',
            'project_url',
            'RunWear Supabase project URL, used by the hero-generation drain cron job'
        );
    end if;

    if not exists (select 1 from vault.secrets where name = 'service_role_key') then
        perform vault.create_secret(
            'REPLACE_WITH_THE_SERVICE_ROLE_KEY',
            'service_role_key',
            'Service-role key for the hero-generation drain cron job. REPLACE the placeholder value before relying on the schedule (see docs/hero-generation-revival.md).'
        );
        raise warning 'service_role_key Vault secret created with a PLACEHOLDER value — replace it with the real service-role key or the drain will fail with 401.';
    end if;
end $$;

-- (Re)schedule the drain: every 30 minutes, 5 jobs per invocation.
do $$
begin
    if exists (select 1 from cron.job where jobname = 'hero-generation-drain') then
        perform cron.unschedule('hero-generation-drain');
    end if;
end $$;

select cron.schedule(
    'hero-generation-drain',
    '*/30 * * * *',
    $$
    select net.http_post(
        url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url')
               || '/functions/v1/generate-hero-image?drain=5',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key')
        ),
        body := '{}'::jsonb,
        timeout_milliseconds := 300000
    );
    $$
);

-- Verify after applying (and after replacing the placeholder key):
--   * The job exists:        select jobid, jobname, schedule, active from cron.job;
--   * Vault entries exist:   select name from vault.secrets;
--   * Recent firings:        select * from cron.job_run_details order by start_time desc limit 5;
--   * HTTP responses:        select status_code, created from net.http_request_queue / net._http_response (pg_net tables)
--
-- Pause the revival without deleting anything:
--   select cron.unschedule('hero-generation-drain');
