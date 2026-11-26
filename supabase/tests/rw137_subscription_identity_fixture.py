#!/usr/bin/env python3
"""RW-137 subscription-identity fixture test.

Applies migration 20261126010000 (registry / links / tombstones /
resolver / severance / backfill) plus a seeded scenario suite inside
ONE transaction against the production schema and ROLLS BACK — the
test validates the migration against the real schema (real FKs, the
Nov 10 identity tombstones, the real profile shape) while persisting
nothing. Scenarios: key derivation (Google '..N' suffix strip),
first-purchase registration, restore resolution via a registered key
(collision flagged, profile cache written), the original-owner rule,
backfill, severance (deleted bank never resurrects), and the
tombstoned-residue guard.

Run: python3 supabase/tests/rw137_subscription_identity_fixture.py
Exit 0 = all scenarios pass. The Management API credential flows
through the connector surrogate; nothing is printed but results.
"""
import json
import pathlib
import sys
import urllib.request
import urllib.error

sys.path.insert(0, "/opt/muse/skills/custom-connector-setup/bin")
from dynamic_credentials import add_surrogate_to_request

REF = "ebicqznlcjbqcukjfzcf"
ROOT = pathlib.Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "supabase/migrations/20261126010000_rw137_subscription_identity.sql"

FIXTURE = r'''
create temp table fixture_results(scenario text, pass boolean, detail text) on commit drop;

insert into auth.users (id) values
  ('aaaaaaaa-0000-4000-8000-0000000000a1'),
  ('bbbbbbbb-0000-4000-8000-0000000000b2'),
  ('cccccccc-0000-4000-8000-0000000000c3'),
  ('dddddddd-0000-4000-8000-0000000000d4'),
  ('eeeeeeee-0000-4000-8000-0000000000e5'),
  ('ffffffff-0000-4000-8000-0000000000f6');
insert into public.personal_profiles (user_id, entitled, personalization_on) values
  ('aaaaaaaa-0000-4000-8000-0000000000a1', true, true),
  ('bbbbbbbb-0000-4000-8000-0000000000b2', true, false),
  ('cccccccc-0000-4000-8000-0000000000c3', true, false),
  ('dddddddd-0000-4000-8000-0000000000d4', true, true),
  ('eeeeeeee-0000-4000-8000-0000000000e5', true, false),
  ('ffffffff-0000-4000-8000-0000000000f6', true, false);
insert into public.personal_heroes (user_id, combination_id, gender, weather, temp_bracket, time_of_day, outfit_signature, storage_path, variant) values
  ('aaaaaaaa-0000-4000-8000-0000000000a1','MALE_CLEAR_COOL_MIDDAY_aaaa0001','MALE','CLEAR','COOL','MIDDAY','aaaa0001','aaaaaaaa-0000-4000-8000-0000000000a1/heroes/x/1.jpg',1),
  ('aaaaaaaa-0000-4000-8000-0000000000a1','MALE_CLOUDY_COOL_DUSK_aaaa0002','MALE','CLOUDY','COOL','DUSK','aaaa0002','aaaaaaaa-0000-4000-8000-0000000000a1/heroes/y/1.jpg',1),
  ('bbbbbbbb-0000-4000-8000-0000000000b2','MALE_CLEAR_COOL_MIDDAY_bbbb0001','MALE','CLEAR','COOL','MIDDAY','bbbb0001','bbbbbbbb-0000-4000-8000-0000000000b2/heroes/x/1.jpg',1),
  ('dddddddd-0000-4000-8000-0000000000d4','FEMALE_CLEAR_COOL_MIDDAY_dddd0001','FEMALE','CLEAR','COOL','MIDDAY','dddd0001','dddddddd-0000-4000-8000-0000000000d4/heroes/x/1.jpg',1);

do $$
declare r record; n int;
begin
  insert into fixture_results select 'S6 derive play suffix',
    public.derive_subscription_key('play_store','GPA.1234..4') = 'store:play_store:GPA.1234',
    public.derive_subscription_key('play_store','GPA.1234..4');
  insert into fixture_results select 'S6 derive apple',
    public.derive_subscription_key('app_store','1000000123') = 'store:app_store:1000000123',
    public.derive_subscription_key('app_store','1000000123');
  insert into fixture_results select 'S6 derive null',
    public.derive_subscription_key('app_store', null) is null, 'null in, null out';

  select * into r from public.resolve_subscription_bank(
    'aaaaaaaa-0000-4000-8000-0000000000a1',
    array['store:app_store:1000000123','rc:aaaaaaaa-0000-4000-8000-0000000000a1'],
    array['store_otid','rc_original'], array[]::uuid[], 'ios', 'app_store');
  insert into fixture_results select 'S1 first purchase own bank',
    r.bank_user_id = 'aaaaaaaa-0000-4000-8000-0000000000a1' and r.state = 'own' and not r.collision,
    r.bank_user_id::text || '/' || r.state;
  select count(*) into n from public.personal_subscription_banks
    where bank_user_id = 'aaaaaaaa-0000-4000-8000-0000000000a1';
  insert into fixture_results select 'S1 registry holds both keys', n = 2, n::text;

  select * into r from public.resolve_subscription_bank(
    'bbbbbbbb-0000-4000-8000-0000000000b2',
    array['rc:aaaaaaaa-0000-4000-8000-0000000000a1'],
    array['rc_original'],
    array['aaaaaaaa-0000-4000-8000-0000000000a1']::uuid[], 'ios', 'app_store');
  insert into fixture_results select 'S2 restore resolves to A bank',
    r.bank_user_id = 'aaaaaaaa-0000-4000-8000-0000000000a1' and r.state = 'restored',
    r.bank_user_id::text || '/' || r.state;
  insert into fixture_results select 'S2 collision flagged',
    r.collision, r.collision::text;
  insert into fixture_results select 'S2 B profile points at A',
    (select bank_user_id from public.personal_profiles where user_id='bbbbbbbb-0000-4000-8000-0000000000b2')
      = 'aaaaaaaa-0000-4000-8000-0000000000a1', 'profile cache';

  select * into r from public.resolve_subscription_bank(
    'eeeeeeee-0000-4000-8000-0000000000e5',
    array['store:play_store:GPA.9999'],
    array['store_otid'],
    array['dddddddd-0000-4000-8000-0000000000d4']::uuid[], 'android', 'play_store');
  insert into fixture_results select 'S3 original-owner rule',
    r.bank_user_id = 'dddddddd-0000-4000-8000-0000000000d4' and r.state = 'restored',
    r.bank_user_id::text || '/' || r.state;

  perform public.backfill_subscription_bank_for(
    'dddddddd-0000-4000-8000-0000000000d4', 'rc:dddddddd-0000-4000-8000-0000000000d4');
  insert into fixture_results select 'S5 backfill registers rc key',
    exists (select 1 from public.personal_subscription_banks
      where subscription_key = 'rc:dddddddd-0000-4000-8000-0000000000d4'
        and bank_user_id = 'dddddddd-0000-4000-8000-0000000000d4'), 'registry row';

  select public.sever_subscription_bank('bbbbbbbb-0000-4000-8000-0000000000b2') into n;
  insert into fixture_results select 'S4 sever removes A keys', n = 2, n::text;
  insert into public.personal_identity_tombstones (user_id, source)
    values ('aaaaaaaa-0000-4000-8000-0000000000a1', 'delete-my-data');
  select * into r from public.resolve_subscription_bank(
    'cccccccc-0000-4000-8000-0000000000c3',
    array['rc:aaaaaaaa-0000-4000-8000-0000000000a1'],
    array['rc_original'],
    array['aaaaaaaa-0000-4000-8000-0000000000a1']::uuid[], 'ios', 'app_store');
  insert into fixture_results select 'S4 deleted bank not resurrected',
    r.bank_user_id = 'cccccccc-0000-4000-8000-0000000000c3' and r.state = 'new',
    r.bank_user_id::text || '/' || r.state;

  insert into public.personal_subscription_tombstones (subscription_key, bank_user_id, source)
    values ('rc:dddddddd-0000-4000-8000-0000000000d4', 'dddddddd-0000-4000-8000-0000000000d4', 'delete-my-data');
  insert into public.personal_identity_tombstones (user_id, source)
    values ('dddddddd-0000-4000-8000-0000000000d4', 'delete-my-data');
  select * into r from public.resolve_subscription_bank(
    'ffffffff-0000-4000-8000-0000000000f6',
    array['rc:dddddddd-0000-4000-8000-0000000000d4'],
    array['rc_original'],
    array['dddddddd-0000-4000-8000-0000000000d4']::uuid[], 'ios', 'app_store');
  insert into fixture_results select 'S7 tombstoned residue never resolves',
    r.bank_user_id = 'ffffffff-0000-4000-8000-0000000000f6',
    r.bank_user_id::text || '/' || r.state;
end $$;

select scenario, pass, detail from fixture_results order by scenario;
'''


def main() -> int:
    script = "begin;\n" + MIGRATION.read_text() + "\n" + FIXTURE + "\nrollback;\n"
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{REF}/database/query",
        data=json.dumps({"query": script}).encode(), method="POST",
        headers={"Content-Type": "application/json"})
    add_surrogate_to_request(req, "custom.supabase", entry_name="access_token",
                             allowed_hosts=["api.supabase.com"])
    try:
        with urllib.request.build_opener().open(req, timeout=120) as resp:
            rows = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode()[:2000])
        return 1
    failed = [r for r in rows if not r["pass"]]
    for r in rows:
        print(("PASS " if r["pass"] else "FAIL ") + r["scenario"] + "  " + str(r["detail"]))
    print(f"{len(rows) - len(failed)}/{len(rows)} scenarios passed (transaction rolled back)")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
