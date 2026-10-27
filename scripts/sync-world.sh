#!/usr/bin/env bash
# Copy the world (and BlueMap render) between this machine and the Pi.
#
# Usage: scripts/sync-world.sh push   desktop -> Pi (after pregen.sh)
#        scripts/sync-world.sh pull   Pi -> desktop (before extending the pre-generated area)
#
# Both servers must be stopped. The destination's world is replaced, so pull
# before pre-generating again once people have played on the Pi.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

pi_host="$(setting PI_HOST pi)"
pi_dir="$(setting PI_DIR jcraft)"
direction="${1:-}"
[[ "$direction" == push || "$direction" == pull ]] || { echo "Usage: $0 push|pull" >&2; exit 2; }

if compose exec -T mc true >/dev/null 2>&1; then
  echo "The local server is running. Stop it first: $compose_cmd stop mc" >&2
  exit 1
fi
if ssh "$pi_host" "cd $pi_dir && docker compose exec -T mc true" >/dev/null 2>&1; then
  echo "The Pi server is running. Stop it first: ssh $pi_host 'cd $pi_dir && docker compose stop mc'" >&2
  exit 1
fi

if [[ "$direction" == push ]]; then
  confirm "This replaces the world on $pi_host with the local one."
else
  confirm "This replaces the local world with the one on $pi_host."
fi

mkdir -p data
ssh "$pi_host" "mkdir -p $pi_dir/data"
for dir in world bluemap; do
  if [[ "$direction" == push ]]; then
    [[ -d "data/$dir" ]] || continue
    rsync -aH --delete --exclude session.lock --info=progress2 "data/$dir/" "$pi_host:$pi_dir/data/$dir/"
  else
    ssh "$pi_host" "test -d $pi_dir/data/$dir" || continue
    rsync -aH --delete --exclude session.lock --info=progress2 "$pi_host:$pi_dir/data/$dir/" "data/$dir/"
  fi
done
if [[ "$direction" == push ]]; then
  echo "Done. Start the Pi server with: ssh $pi_host 'cd $pi_dir && docker compose up -d'"
else
  echo "Done."
fi
