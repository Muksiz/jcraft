#!/usr/bin/env bash
# Copy the Pi's backups to this machine. Old local copies are kept.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

pi_host="$(setting PI_HOST pi)"
pi_dir="$(setting PI_DIR jcraft)"

mkdir -p backups
rsync -a --info=progress2 "$pi_host:$pi_dir/backups/" backups/
