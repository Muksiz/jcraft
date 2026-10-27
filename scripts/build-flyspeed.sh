#!/usr/bin/env bash
# Build the flyspeed mod in a JDK container and copy the jar into mods/,
# which the server loads alongside the Modrinth mods.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

engine="$(command -v docker || command -v podman)"
"$engine" run --rm \
  -v "$PWD/flyspeed:/src:z" \
  -v jcraft-gradle-cache:/root/.gradle \
  -w /src \
  docker.io/library/eclipse-temurin:25.0.4.1_1-jdk \
  ./gradlew build --no-daemon -q

rm -f mods/flyspeed-*.jar
cp flyspeed/build/libs/flyspeed-*.jar mods/
ls -l mods/
