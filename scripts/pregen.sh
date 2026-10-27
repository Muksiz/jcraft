#!/usr/bin/env bash
# Pre-generate chunks on the desktop with Chunky, then stop.
# Copy the result to the Pi afterwards with `scripts/sync-world.sh push`.
#
# Usage: scripts/pregen.sh [--keep-running]
#   --keep-running  leave the server up afterwards so BlueMap can finish rendering
#                   (check with `scripts/rcon.sh bluemap`, then `docker compose stop mc`)
set -euo pipefail
source "$(dirname "$0")/lib.sh"

keep_running=false
[[ "${1:-}" == --keep-running ]] && keep_running=true

declare -A radius=(
  [minecraft:overworld]="$(setting PREGEN_RADIUS_OVERWORLD 5000)"
  [minecraft:the_nether]="$(setting PREGEN_RADIUS_NETHER 1000)"
  [minecraft:the_end]="$(setting PREGEN_RADIUS_END 3000)"
)

# Never start the Pi-only services (backup, ddns, tunnel) here.
export COMPOSE_PROFILES=
dc() { docker compose -f compose.yaml -f compose.pregen.yaml "$@"; }
rcon() { dc exec -T mc rcon-cli "$@"; }

mkdir -p data
since="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
dc up -d mc

echo "Waiting for the server to come up (first start downloads everything and creates the world)..."
for _ in $(seq 180); do
  dc exec -T mc mc-health >/dev/null 2>&1 && break
  sleep 5
done
dc exec -T mc mc-health >/dev/null || { echo "Server did not become healthy; see: docker compose logs mc"; exit 1; }

rcon "chunky quiet 60"
for dim in "${!radius[@]}"; do
  r="${radius[$dim]}"
  rcon "chunky start $dim square 0 0 $r"
  rcon "chunky confirm"  # replaces a finished task from an earlier run
done

pending=("${!radius[@]}")
while ((${#pending[@]})); do
  sleep 60
  logs="$(dc logs --no-log-prefix --since "$since" mc)"
  still=()
  for dim in "${pending[@]}"; do
    grep -q "Task finished for $dim" <<<"$logs" || still+=("$dim")
  done
  pending=("${still[@]}")
  rcon "chunky progress" || true
done

echo "All dimensions generated."
rcon "save-all flush"
if $keep_running; then
  echo "Server left running for BlueMap. Check: scripts/rcon.sh bluemap"
  echo "Then stop with: docker compose stop mc"
else
  dc stop mc
fi
