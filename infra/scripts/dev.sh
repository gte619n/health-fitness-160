#!/usr/bin/env bash
set -euo pipefail

# Pulls the OAuth + Auth.js secrets out of Secret Manager and runs the backend
# and web app side-by-side for local end-to-end auth testing.
#
# Backend listens on http://localhost:8080 with CORS allowing localhost:3000.
# Web listens on http://localhost:3000 and forwards bearer tokens to backend.
# Ctrl-C tears both down.
#
# Flags:
#   --emulator   Boot the local Firestore emulator on :8085 first, then
#                point the backend at it via FIRESTORE_EMULATOR_HOST.
#                Without this flag the backend talks to real Firestore in
#                project health-fitness-160.

PROJECT_ID="health-fitness-160"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
USE_EMULATOR=0

for arg in "$@"; do
  case "$arg" in
    --emulator) USE_EMULATOR=1 ;;
    *) echo "Unknown flag: $arg" >&2; exit 2 ;;
  esac
done

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 1; }
}
require gcloud
require pnpm

secret() {
  gcloud secrets versions access latest --secret="$1" --project="$PROJECT_ID"
}

echo "==> Fetching secrets from Secret Manager"
OAUTH_ALLOWED_AUDIENCES="$(secret oauth-allowed-audiences)"
OAUTH_WEB_CLIENT_ID="$(secret oauth-web-client-id)"
OAUTH_WEB_CLIENT_SECRET="$(secret oauth-web-client-secret)"
AUTH_GOOGLE_ID="$OAUTH_WEB_CLIENT_ID"
AUTH_GOOGLE_SECRET="$OAUTH_WEB_CLIENT_SECRET"
AUTH_SECRET="$(secret authjs-secret)"
GOOGLE_HEALTH_WEBHOOK_SECRET="$(secret google-health-webhook-secret)"

# --- Backend env ---
# IMPL-02 audience checks + CORS allow-list for the local web origin.
export OAUTH_ALLOWED_AUDIENCES
export CORS_ALLOWED_ORIGINS="http://localhost:3000"
# IMPL-03 Firestore project (real cloud Firestore unless --emulator).
export GCP_PROJECT_ID="$PROJECT_ID"
# IMPL-04 Google Health: backend refreshes the per-user OAuth token via
# the web OAuth client, encrypts refresh tokens via the live KMS key
# (defaulted in application.yml), and validates webhook callbacks with
# the shared secret. KMS calls require ADC — make sure you've run
# `gcloud auth application-default login` before this script.
export OAUTH_WEB_CLIENT_ID
export OAUTH_WEB_CLIENT_SECRET
export GOOGLE_HEALTH_WEBHOOK_SECRET

# --- Web env (.env.local) ---
WEB_ENV="${REPO_ROOT}/web/.env.local"
cat > "$WEB_ENV" <<EOF
AUTH_SECRET=${AUTH_SECRET}
AUTH_GOOGLE_ID=${AUTH_GOOGLE_ID}
AUTH_GOOGLE_SECRET=${AUTH_GOOGLE_SECRET}
BACKEND_URL=http://localhost:8080
EOF
echo "    wrote $WEB_ENV"

# --- Run both ---

EMULATOR_PID=""

cleanup() {
  echo
  echo "==> Stopping"
  [[ -n "${BACKEND_PID:-}"  ]] && kill "$BACKEND_PID"  2>/dev/null || true
  [[ -n "${WEB_PID:-}"      ]] && kill "$WEB_PID"      2>/dev/null || true
  [[ -n "${EMULATOR_PID:-}" ]] && kill "$EMULATOR_PID" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

if [[ "$USE_EMULATOR" -eq 1 ]]; then
  echo "==> Starting Firestore emulator on :8085"
  gcloud emulators firestore start --host-port=localhost:8085 \
      --project="$PROJECT_ID" >/tmp/firestore-emulator.log 2>&1 &
  EMULATOR_PID=$!
  export FIRESTORE_EMULATOR_HOST="localhost:8085"
  # Give the emulator a moment to bind the port.
  until nc -z localhost 8085 2>/dev/null; do sleep 0.5; done
  echo "    emulator ready ($FIRESTORE_EMULATOR_HOST)"
fi

echo "==> Starting backend"
(cd "${REPO_ROOT}/backend" && ./gradlew :app:bootRun --console=plain) &
BACKEND_PID=$!

echo "==> Starting web"
(cd "${REPO_ROOT}/web" && pnpm dev) &
WEB_PID=$!

echo
echo "  Backend: http://localhost:8080  (PID $BACKEND_PID)"
echo "  Web:     http://localhost:3000  (PID $WEB_PID)"
echo "  Try:     open http://localhost:3000/me  in an incognito window"
echo
echo "  Ctrl-C to stop both."

wait
