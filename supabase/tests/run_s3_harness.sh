#!/bin/bash
# RW-137 S3 harness driver: mock on :54399, then each function in turn
# on :8000 (std serve's fixed port), running its phase of fixtures.
set -u
cd "$(dirname "$0")"
DENO="${DENO:-$HOME/workspace/.deno-bin/deno}"
export DENO_CERT=/run/hatch/egress-tls/ca-bundle.pem
export DENO_DIR="${DENO_DIR:-$HOME/workspace/.deno-cache}"

node mock_supabase.mjs &
MOCK_PID=$!
trap 'kill $MOCK_PID 2>/dev/null' EXIT
for i in $(seq 1 30); do curl -s -o /dev/null http://127.0.0.1:54399/__state && break; sleep 1; done

run_phase() {
  local fn_path="$1" phase="$2"
  SUPABASE_URL=http://127.0.0.1:54399 \
  SUPABASE_ANON_KEY=test-anon-key \
  SUPABASE_SERVICE_ROLE_KEY=test-service-key \
  REVENUECAT_PUBLIC_KEY_IOS=test-rc-key-ios \
  REVENUECAT_PUBLIC_KEY_ANDROID=test-rc-key-android \
  REVENUECAT_API_BASE=http://127.0.0.1:54399/rc/v1 \
  "$DENO" run --allow-net --allow-env "$fn_path" &
  local pid=$!
  for i in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8000/ -H 'Content-Type: application/json' -d '{}')
    [ "$code" != "000" ] && break
    sleep 1
  done
  node rw137_s3_harness.mjs "$phase"
  local rc=$?
  kill $pid 2>/dev/null; wait $pid 2>/dev/null
  return $rc
}

run_phase ../functions/reconcile-personal-entitlement/index.ts reconcile
R1=$?
run_phase ../functions/request-identity-adoption/index.ts request
R2=$?
echo "reconcile phase rc=$R1, request phase rc=$R2"
[ $R1 -eq 0 ] && [ $R2 -eq 0 ]
