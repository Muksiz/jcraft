#!/usr/bin/env bash
# Run a server command, e.g. `scripts/rcon.sh spark tps`. No arguments = interactive console.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

if (($#)); then
  compose exec -T mc rcon-cli "$@"
else
  compose exec mc rcon-cli
fi
