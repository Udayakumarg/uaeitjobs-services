#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# uaeitjobs — one-shot job ingest runner
#
# Logs in as the admin user, triggers POST /admin/ingest/run, polls
# /admin/ingest/status until the run completes (or times out), then
# prints a per-source count of rows now in the jobs table.
#
# Usage:
#   ./scripts/run-ingest.sh                          # uses defaults below
#   API=https://www.uaeitjobs.com ./scripts/run-ingest.sh
#   EMAIL=admin@... PASSWORD=... ./scripts/run-ingest.sh
#
# Deps on host: bash, curl, jq, docker.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

API="${API:-https://www.uaeitjobs.com}"
EMAIL="${EMAIL:-admin@uaeitjobs.com}"
PASSWORD="${PASSWORD:-***REMOVED***}"
DB_CONTAINER="${DB_CONTAINER:-deploy-db-1}"
DB_USER="${DB_USER:-uaeitjobs_user}"
DB_NAME="${DB_NAME:-uaeitjobs_db}"
POLL_INTERVAL="${POLL_INTERVAL:-5}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"

bold()  { printf "\033[1m%s\033[0m\n" "$*"; }
green() { printf "\033[32m%s\033[0m\n" "$*"; }
red()   { printf "\033[31m%s\033[0m\n" "$*"; }
gray()  { printf "\033[90m%s\033[0m\n" "$*"; }
need() { command -v "$1" >/dev/null || { red "missing dep: $1"; exit 1; }; }
need curl; need jq; need docker

bold "→ Logging in as $EMAIL"
LOGIN_RESPONSE=$(curl -sS -X POST "$API/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken // empty')
[[ -z "$TOKEN" ]] && { red "login failed:"; echo "$LOGIN_RESPONSE" | jq .; exit 1; }
green "  ✓ got token"

bold "→ Job counts before ingest"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c \
    "SELECT source, COUNT(*) FROM jobs GROUP BY source ORDER BY 2 DESC;" \
    | awk -F'|' '{ printf "  %-12s %s\n", $1, $2 }'
BEFORE=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "SELECT COUNT(*) FROM jobs;")
gray "  total: $BEFORE"

bold "→ Triggering ingest"
curl -sS -X POST "$API/api/v1/admin/ingest/run" -H "Authorization: Bearer $TOKEN" | jq -C .

bold "→ Polling every ${POLL_INTERVAL}s (timeout ${TIMEOUT_SECONDS}s)"
START=$(date +%s)
while true; do
    STATUS=$(curl -sS -H "Authorization: Bearer $TOKEN" "$API/api/v1/admin/ingest/status")
    RUNNING=$(echo "$STATUS" | jq -r '.running')
    ELAPSED=$(( $(date +%s) - START ))
    printf "  %3ds  running=%s\n" "$ELAPSED" "$RUNNING"
    [[ "$RUNNING" == "false" ]] && { green "  ✓ finished in ${ELAPSED}s"; break; }
    [[ "$ELAPSED" -ge "$TIMEOUT_SECONDS" ]] && { red "  ✗ timed out"; exit 2; }
    sleep "$POLL_INTERVAL"
done

bold "→ Recent runs"
echo "$STATUS" | jq -C '.recent[] | {source, startedAt, finishedAt, inserted, updated, rejected, errors}'

bold "→ Job counts after"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c \
    "SELECT source, COUNT(*) FROM jobs GROUP BY source ORDER BY 2 DESC;" \
    | awk -F'|' '{ printf "  %-12s %s\n", $1, $2 }'
AFTER=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "SELECT COUNT(*) FROM jobs;")
DELTA=$((AFTER - BEFORE))
[[ "$DELTA" -gt 0 ]] && green "  +$DELTA new jobs" || gray "  no new jobs (all dedup-matched)"
