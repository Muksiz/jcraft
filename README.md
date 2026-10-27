# jcraft

A self-hosted Fabric Minecraft server for a Raspberry Pi 5 (8 GB, NVMe), built on
[itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server). Players join
with a **vanilla Java client**: every mod is server-side only.

The heavy work (chunk generation, map rendering) runs on a desktop. The Pi then serves a
world that is already generated.

| | |
|---|---|
| Minecraft | 26.3, Fabric loader 0.20.1, Java 25 |
| Server | `mc.janix.fi` (port 25565) |
| Map | `https://map.janix.fi` (BlueMap via Cloudflare Tunnel) |

## What's installed

All versions are pinned in [`compose.yaml`](compose.yaml).

- **Performance:** Lithium, FerriteCore, ScalableLux, C2ME
- **Tools:** Chunky (pre-generation), spark (profiling), BlueMap (web map)
- **Gameplay:** Universal Graves (with Polymer), Flyspeed (our own, see below)
- **World generation:** Terralith, Tectonic, Incendium, Nullscape, Dungeons and Taverns, Structory
- **Vanilla Tweaks:** player head drops, AFK display
- **Resource pack:** Sparkles is offered to players for Incendium's item textures. It's optional.

Containers on the Pi (`COMPOSE_PROFILES=pi`):

| Service | Purpose |
|---|---|
| `mc` | the server |
| `backup` | tar backup every 6 h into `./backups`, kept 7 days, skipped while nobody is online |
| `ddns` | keeps the DNS-only A record `mc.janix.fi` set to the current public IP |
| `tunnel` | Cloudflare Tunnel that serves `map.janix.fi` |

## One-time setup

### Cloudflare

1. **API token:** go to My Profile → API Tokens → *Edit zone DNS* template and limit it to
   `janix.fi`. Put it in `CLOUDFLARE_API_TOKEN`. The `ddns` container creates
   `mc.janix.fi` as DNS-only (grey cloud). Don't proxy it: Cloudflare can't proxy Minecraft
   traffic.
2. **Tunnel:** go to Zero Trust → Networks → Tunnels → Create (cloudflared). Add the
   public hostname `map.janix.fi` → `http://mc:8100`. Put the tunnel token in
   `CLOUDFLARE_TUNNEL_TOKEN`.

### Router

Forward **TCP 25565** to the Pi. Don't forward RCON (25575) or SSH.

If the WAN IP on the router's status page differs from your public IP, you're behind CGNAT
and port forwarding won't work.

### Pi

```sh
git clone https://github.com/Muksiz/jcraft.git && cd jcraft
cp .env.example .env    # fill in RCON_PASSWORD, WHITELIST, OPS, Cloudflare tokens
mkdir -p data backups
```

Don't start the server until the world has been pushed from the desktop (next section).
Otherwise the Pi generates its own world.

SSH: use key-based login only (`PasswordAuthentication no`, `PermitRootLogin no`). Reach the
Pi over the LAN, or over Tailscale when you're away. Never port-forward SSH.

### Desktop

Same clone. In `.env`, leave `COMPOSE_PROFILES` empty and set `PI_HOST` (an SSH host alias
for the Pi) and `PI_DIR`. Use the **same `SEED`** as the Pi.

The scripts use `docker compose` where Docker is installed and `podman-compose` (1.6+)
otherwise, so you don't need Docker on the desktop. The commands below use `docker compose`.
On the desktop, type `podman-compose` instead.

## Pre-generating the world on the desktop

```sh
scripts/pregen.sh                   # generates, then stops
scripts/pregen.sh --keep-running    # same, but leaves the server up so BlueMap can render
scripts/sync-world.sh push          # copy world (and map render) to the Pi
ssh pi 'cd jcraft && docker compose up -d'
```

Radii are set in `.env` (`PREGEN_RADIUS_*`, in blocks). The defaults are 5000 for the
Overworld, 1000 for the Nether and 3000 for the End. The world has no border: past the
pre-generated area the Pi generates new chunks live, which is slower.

**Extending the world later:** stop the Pi server, run `scripts/sync-world.sh pull`,
raise the radii, run `pregen.sh`, then `sync-world.sh push`. `push` replaces the Pi's world,
so always pull first once people have played.

## Day-to-day

```sh
docker compose up -d                 # start everything
docker compose logs -f mc            # logs
scripts/rcon.sh                      # interactive console
scripts/rcon.sh spark tps            # TPS / MSPT
scripts/rcon.sh spark profiler start --timeout 60
scripts/rcon.sh whitelist add Name
docker compose restart mc
scripts/backup-pull.sh               # (desktop) copy Pi backups locally
```

## Flyspeed mod

A small server-side mod in [`flyspeed/`](flyspeed). It works with vanilla clients.

| Command | Who | Effect |
|---|---|---|
| `/flyspeed` | anyone | show your current multiplier |
| `/flyspeed <0.1–5>` | anyone | set your own flight speed (`1` = vanilla) |
| `/flyspeed <0.1–10>` | ops | ops can go up to 10× for themselves |
| `/flyspeed <0.1–10> <players>` | ops | set it for other players |

The speed is saved with the player, so it survives relogs and restarts.

The built jar is committed in `mods/`. The server copies everything in that folder into its
mods directory at startup, so the Pi doesn't need to build anything.

### Building with the JDK container

The desktop only has a Java *runtime*, so the mod is compiled inside a JDK container
(`eclipse-temurin:25.0.4.1_1-jdk`, pinned in the script). Nothing needs to be installed and
no sudo is required:

```sh
scripts/build-flyspeed.sh    # builds, then replaces mods/flyspeed-*.jar
```

- The script uses `docker` if it exists, otherwise `podman`.
- Gradle's downloads (Minecraft, Fabric, Gradle itself) are cached in the `jcraft-gradle-cache`
  volume. The first build takes about a minute, later ones a few seconds. Remove the cache with
  `podman volume rm jcraft-gradle-cache`.
- To run other Gradle tasks, use the same container by hand:
  ```sh
  podman run --rm -it -v "$PWD/flyspeed:/src:z" -v jcraft-gradle-cache:/root/.gradle -w /src \
    docker.io/library/eclipse-temurin:25.0.4.1_1-jdk ./gradlew <task> --no-daemon
  ```
- To inspect a Minecraft class (e.g. after an API change breaks the build), run `javap` from
  the same image against the cached jar:
  ```sh
  podman run --rm -v jcraft-gradle-cache:/root/.gradle docker.io/library/eclipse-temurin:25.0.4.1_1-jdk \
    javap -cp /root/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar \
    net.minecraft.commands.Commands
  ```

After building, restart the server (`podman-compose down && podman-compose up -d mc`) and
commit the new jar.

## Updating

1. Pick the new Minecraft version. **Every** pack in `MODRINTH_PROJECTS` must support it.
   Check Modrinth.
2. Update `VERSION`, `FABRIC_LOADER_VERSION`, the Modrinth version IDs (and their comments),
   the Vanilla Tweaks version in `packs/vanillatweaks.json`, and the Sparkles URL and SHA-1.
3. Update `flyspeed/gradle.properties` and the `minecraft` range in
   `flyspeed/src/main/resources/fabric.mod.json`, then run `scripts/build-flyspeed.sh`.
   Check the latest Loom and Fabric API versions on <https://fabricmc.net/develop>.
4. Commit, then `git pull && docker compose up -d` on the Pi. The most recent backup is your
   rollback.

Already-generated chunks keep their old terrain. New biomes and structures from an update
only appear in new chunks.
