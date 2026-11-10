#!/bin/bash
# RW-137 webhook replay driver: mock Supabase on :54399, the real
# revenuecat-webhook under Deno on :8000, then the fixture runner.
set -u
cd "$(dirname "$0")"
DENO="${DENO:-$HOME/workspace/.deno-bin/deno}"
export DENO_CERT=/run/hatch/egress-tls/ca-bundle.pem
export DENO_DIR="${DENO_DIR:-$HOME/workspace/.deno-cache}"

node mock_supabase.mjs &
MOCK_PID=$!
SUPABASE_URL=http://127.0.0.1:54399 \
SUPABASE_ANON_KEY=test-anon-key \
SUPABASE_SERVICE_ROLE_KEY=test-service-key \
REVENUECAT_WEBHOOK_AUTH_KEY=test-hook-key \
"$DENO" run --allow-net --allow-env ../functions/revenuecat-webhook/index.ts &
HOOK_PID=$!
cleanup() { kill $MOCK_PID $HOOK_PID 2>/dev/null; }
trap cleanup EXIT

for i in $(seq 1 60); do
  curl -s -o /dev/null http://127.0.0.1:54399/__state && break
  sleep 1
done
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8000/ -H 'Content-Type: application/json' -d '{}')
  [ "$code" != "000" ] && break
  sleep 1
done
node rw137_webhook_replay.mjs
