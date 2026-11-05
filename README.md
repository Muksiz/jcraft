# jcraft

A Fabric Minecraft server on [itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server).
Every mod is server-side, so players join with a **vanilla Java client**.

| | |
|---|---|
| Minecraft | 26.3, Fabric loader 0.20.1, Java 25 |
| Server | `mc.janix.fi` |
| Map | <https://map.janix.fi> (BlueMap) |

## Mods

All versions are pinned in [`compose.yaml`](compose.yaml).

- **Performance:** Lithium, FerriteCore, ScalableLux, C2ME
- **Tools:** Chunky, spark, BlueMap
- **Gameplay:** Universal Graves (items only; XP drops as in vanilla, set in
  [`patches/`](patches)), Tree Vein Miner (crouch to fell trees), Flyspeed and Gamble (ours, in [`flyspeed/`](flyspeed) and [`gamble/`](gamble))
- **World generation:** vanilla terrain; structures from Dungeons and Taverns, Towns and Towers,
  Trek and Structory in chunks generated from 2026-11-04 on (the older area stays vanilla)
- **Vanilla Tweaks:** player head drops, AFK display
- **Game rules:** set by our datapack in [`packs/jcraft`](packs/jcraft) on every start;
  one sleeping player skips the night

## Pre-generation

Generate chunks and render the map on a strong computer, then copy the world to the
server. Chunks the server has to generate live are slow.

```sh
scripts/pregen.sh [--keep-running]   # Chunky; --keep-running lets BlueMap finish
scripts/sync-world.sh pull|push      # push replaces the server's world: pull first
```

## Flyspeed

| Command | Who | Effect |
|---|---|---|
| `/flyspeed` | anyone | show your multiplier |
| `/flyspeed <0.1–5>` | anyone | set your own flight speed (`1` = vanilla) |
| `/flyspeed <0.1–10> [players]` | ops | up to 10×, for yourself or others |

Rebuild with `scripts/build-mod.sh flyspeed` and commit the jar in `mods/`.

## Gamble

`/gamble <1–64>` bets that many of your own golden carrots on a coin flip. The bet is taken at
once and announced in chat; 3 seconds later the result is announced. A win pays back double the
bet, a loss keeps it. One gamble at a time per player.

Rebuild with `scripts/build-mod.sh gamble` and commit the jar in `mods/`.
