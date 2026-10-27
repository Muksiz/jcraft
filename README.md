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
- **Gameplay:** Universal Graves (with Polymer)
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

The scripts call `docker compose`. With rootless Podman, `podman-compose` (1.6+) handles that
through the `docker` shim, so you don't need Docker on the desktop.

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

## Updating

1. Pick the new Minecraft version. **Every** pack in `MODRINTH_PROJECTS` must support it.
   Check Modrinth.
2. Update `VERSION`, `FABRIC_LOADER_VERSION`, the Modrinth version IDs (and their comments),
   the Vanilla Tweaks version in `packs/vanillatweaks.json`, and the Sparkles URL and SHA-1.
3. Commit, then `git pull && docker compose up -d` on the Pi. The most recent backup is your
   rollback.

Already-generated chunks keep their old terrain. New biomes and structures from an update
only appear in new chunks.
