#!/usr/bin/env bash
# Build one of our mods (flyspeed, gamble) in a JDK container and copy the jar into mods/,
# which the server loads alongside the Modrinth mods.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

mod="${1:?usage: scripts/build-mod.sh <flyspeed|gamble>}"
[[ -f "$mod/build.gradle" ]] || { echo "No mod called $mod" >&2; exit 1; }

engine="$(command -v docker || command -v podman)"
"$engine" run --rm \
  -v "$PWD/$mod:/src:z" \
  -v jcraft-gradle-cache:/root/.gradle \
  -w /src \
  docker.io/library/eclipse-temurin:25.0.4.1_1-jdk \
  ./gradlew build --no-daemon -q

rm -f mods/"$mod"-*.jar
cp "$mod"/build/libs/"$mod"-*.jar mods/
ls -l mods/
