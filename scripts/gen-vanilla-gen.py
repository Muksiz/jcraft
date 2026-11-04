#!/usr/bin/env python3
"""Regenerate packs/vanilla-gen: keeps the structure datapacks loaded but stops them
from placing anything in new chunks, so new chunks generate as vanilla.

Usage: python3 -I scripts/gen-vanilla-gen.py
Reads the installed packs from data/world/datapacks and the vanilla data from the server
jar in data/versions/<VERSION>/ (both read-only). Rerun after updating any structure pack.

What the generated pack does:
  1. Every non-vanilla structure set gets an empty structure list. The structures themselves
     stay registered, so ones that started in existing chunks still finish generating and
     their loot, spawners and enchantments keep working.
  2. Vanilla worldgen files the packs override (Trek's villages, strongholds, outposts, end
     cities, dungeons) are restored from the server jar.
  3. Vanilla tags the packs replace or extend in ways that affect new chunks or trades
     (Towns and Towers' biome tags, the Dungeons and Taverns map trades) are restored with
     "replace": true.
It must load after the structure packs (check with `/datapack list`).
"""
import json, re, shutil, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKS = ["Dungeons and Taverns", "t_and_t-datapack", "Trek", "Structory"]
OUT = ROOT / "packs" / "vanilla-gen"
# Vanilla tags to restore when a structure pack touches them.
RESTORE_TAGS = re.compile(r"^data/minecraft/tags/(worldgen/biome/|villager_trade/(cartographer|wandering_trader)/)")
RESTORE_FILES = re.compile(r"^data/minecraft/(worldgen|structure)/")

match = re.search(r'VERSION: "([^"]+)"', (ROOT / "compose.yaml").read_text())
if not match:
    sys.exit("VERSION not found in compose.yaml")
version = match.group(1)
vanilla = zipfile.ZipFile(ROOT / "data" / "versions" / version / f"server-{version}.jar")
vanilla_names = set(vanilla.namelist())

zips = [z for z in sorted((ROOT / "data/world/datapacks").glob("*.zip")) if any(z.name.startswith(p) for p in PACKS)]
if len(zips) != len(PACKS):
    sys.exit(f"Expected {len(PACKS)} structure packs, found: {[z.name for z in zips]}")

files: dict[str, bytes] = {}  # output path -> contents
for z in zips:
    pack = zipfile.ZipFile(z)
    for name in pack.namelist():
        if name.endswith("/"):
            continue
        if re.match(r"^data/(?!minecraft/)[^/]+/worldgen/structure_set/.+\.json$", name):
            data = json.loads(pack.read(name))
            files[name] = json.dumps({"structures": [], "placement": data["placement"]}, indent=2).encode() + b"\n"
        elif (RESTORE_FILES.match(name) or RESTORE_TAGS.match(name)) and name in vanilla_names:
            data = vanilla.read(name)
            if name.startswith("data/minecraft/tags/"):
                tag = json.loads(data)
                tag["replace"] = True
                data = json.dumps(tag, indent=2).encode() + b"\n"
            files[name] = data

if OUT.exists():
    shutil.rmtree(OUT)
for name, data in sorted(files.items()):
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
mcmeta = json.loads((ROOT / "packs/jcraft/pack.mcmeta").read_text())
mcmeta["pack"]["description"] = "jcraft: no structure-pack structures in new chunks"
(OUT / "pack.mcmeta").write_text(json.dumps(mcmeta, indent=2) + "\n")

print(f"Wrote {len(files)} files to {OUT.relative_to(ROOT)}")
for kind in ("structure_set/", "data/minecraft/worldgen/", "data/minecraft/structure/", "data/minecraft/tags/"):
    print(f"  {kind:28} {sum(kind in n for n in files)}")
