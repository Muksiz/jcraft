# jcraft: notes for Claude

Fabric Minecraft server (itzg/minecraft-server) on a Raspberry Pi 5 (8 GB, NVMe). See
README.md for the full setup.

## Hosts
- **Pi**: runs `mc`, `backup`, `ddns`, `tunnel` (`COMPOSE_PROFILES=pi`). Reach it over SSH as
  `$PI_HOST` (default alias `pi`), with the repo in `~/jcraft`.
- **Desktop**: Fedora with rootless Podman and podman-compose. Keep the scripts compatible with both
  Docker Compose v2 (Pi) and podman-compose (desktop) via `compose` in `scripts/lib.sh`; there is no `docker`
  command on the desktop. Only used for `scripts/pregen.sh`
  (Chunky pre-generation and BlueMap pre-rendering) and `scripts/backup-pull.sh`.

## Rules
- Never edit `data/` or commit `.env`, `data/` or `backups/`.
- Anything that affects world generation (MC version, mods, datapacks, seed) must stay
  identical on desktop and Pi. Change it only in `compose.yaml` and commit it.
- Keep every Modrinth entry pinned to a version ID, with the readable version in the comment
  block. All 16 entries must support the chosen `VERSION`.
- `scripts/sync-world.sh push` overwrites the Pi world. Always `pull` first if players have
  been on the server, and ask before pushing.
- Don't port-forward or expose RCON (25575) or SSH.

## Monitoring (run on the Pi, or via `ssh pi 'cd jcraft && ...'`)
```sh
docker compose ps                         # health of all services
docker compose logs --tail 200 mc         # recent server log
scripts/rcon.sh spark tps                 # TPS/MSPT; healthy is 20 TPS, MSPT < 50
scripts/rcon.sh spark health              # memory, CPU, disk
scripts/rcon.sh list                      # online players
ls -lh backups/                           # latest backups
vcgencmd measure_temp; vcgencmd get_throttled   # Pi thermals (0x0 = not throttled)
```

## Flyspeed mod (`flyspeed/`)
- Our own server-side mod (`/flyspeed`). The built jar is committed in `mods/` and copied into
  the server at startup.
- The desktop has only a JRE. Build with `scripts/build-flyspeed.sh`, which runs Gradle in the
  pinned `eclipse-temurin` JDK container. Use `javap` in that container against the cached
  Minecraft jar to look up API changes (see README).
- Minecraft is unobfuscated since 26.1, so use Mojang names. Commands use
  `PermissionCheck` (e.g. `Commands.LEVEL_GAMEMASTERS.check(source.permissions())`).
