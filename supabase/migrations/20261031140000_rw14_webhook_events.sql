-- RW-14 follow-up (2026-10-31): RevenueCat webhook processing log.
--
-- The revenuecat-webhook Edge Function records every RevenueCat event id
-- it has processed here BEFORE answering, so RevenueCat's retries of the
-- same event are answered as duplicates instead of re-applying the state
-- change (and re-writing the entitlement audit trail). Rows are a
-- processing log only; the entitlement state of record stays
-- personal_profiles.entitled, written solely by set_personal_entitlement.

create table if not exists public.personal_webhook_events (
    event_id    text primary key,
    event_type  text not null,
    user_id     uuid references auth.users (id) on delete set null,
    applied     boolean not null default false,
    detail      text,
    received_at timestamptz not null default now()
);

comment on table public.personal_webhook_events is
    'RW-14: RevenueCat webhook idempotency + processing log. One row per RC event id; written by the revenuecat-webhook Edge Function (service role) only.';

alter table public.personal_webhook_events enable row level security;
-- No client policies: service role only, like the rest of the personal
-- schema's write paths. Clients can neither read nor forge the log.
