#!/usr/bin/env python3
"""Regenerate the `server-baby-legends` datapack from the pinned Baby Legends jar.

The [Baby Legends (Cobblemon)](https://modrinth.com/datapack/baby-legends-cobblemon)
mod adds baby pre-evolutions of legendaries. Upstream each one:
  - has a wild spawn (`data/cobblemon/spawn_pool_world/<id>.json`),
  - evolves into the real legendary at level 50,
  - ships full, often 450-480 BST stats.

On this server we keep them as crate-only collectibles, so we override all of
that with a datapack (`modpack/server-overrides/datapacks/server-baby-legends`).
Datapack data outranks mod-embedded data, so the overrides win WITHOUT editing
the mod jar — which keeps packwiz's modrinth auto-update (hash + version pin)
intact. Same philosophy as `ops/gen_spawn_nerfs.py` for AllTheMons.

The catch: those override files are a hand-derived SNAPSHOT of one mod version.
Nothing regenerates them automatically — `packwiz update` only bumps the jar,
not our datapack. So when the mod version-bumps, a newly-added baby legend would
spawn wild / evolve / ship full stats, and a renamed one would silently revert.
This script removes that footgun: it re-derives the entire datapack from
whatever jar `modpack/mods/baby-legends-cobblemon.pw.toml` currently points at.

What it does, for the jar named in the pw.toml's [download] url:
  1. SPAWNS  — for every `spawn_pool_world/<id>.json` in the jar, writes an
     override with `enabled:false` / empty `spawns` (baby legends never spawn
     in the wild; the gacha crate is the only source).
  2. SPECIES — for every `species/custom/<id>.json` in the jar, writes a FULL
     copy with only two changes: `evolutions: []` (no evolving into the
     legendary) and `baseStats` flattened to WEAK_STATS (BST 220, ~Cleffa).
     Everything else (types, abilities, movepool, model hitbox) is preserved
     verbatim from the jar, so upstream data changes flow through on re-run.
  3. EGG-POOL DRIFT CHECK — compares the jar's standalone species ids against
     the `baby_legend` tier in
     `config/cobblemon-gacha/authored/egg_pools.json` and WARNS (non-fatal) on
     any mismatch, since the egg pool is hand-authored balance this script does
     NOT edit. A bump that adds a species shows up here as "add to egg pool".

The `spawn_pool_world` and `species/custom` dirs are wiped of `*.json` before
regs so a renamed/removed species leaves no orphan override. `pack.mcmeta` and
`README.md` at the datapack root are left untouched.

A human-readable summary is written to `ops/baby-legends-overrides-manifest.txt`
so PRs stay reviewable. The script is deterministic: same jar -> same output.

USAGE / when baby-legends version-bumps:
    1. Bump the mod: `cd modpack && packwiz update baby-legends-cobblemon`
       (or edit modpack/mods/baby-legends-cobblemon.pw.toml's version/url).
    2. python3 ops/gen_baby_legends_overrides.py
    3. Resolve any egg-pool drift warnings by hand in egg_pools.json
       (and the crate odds in tables/pokemon.json if the lineup changed).
    4. cd modpack && packwiz refresh
    5. Commit the regenerated datapack + manifest.

NOTE: `royal_carbink` is a Carbink ASPECT (`carbink royal`), not a standalone
`species/custom` file — it ships via `species_additions`. So it gets a spawn
override (it has a spawn file) but NO species override and is NOT in the egg
pool. With its wild spawn disabled it is unobtainable, which is intended.
"""
from __future__ import annotations

import json
import re
import sys
import urllib.request
import zipfile
from io import BytesIO
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PW_TOML = REPO / "modpack/mods/baby-legends-cobblemon.pw.toml"
DATAPACK = REPO / "modpack/server-overrides/datapacks/server-baby-legends"
SPAWN_DIR = DATAPACK / "data/cobblemon/spawn_pool_world"
SPECIES_DIR = DATAPACK / "data/cobblemon/species/custom"
EGG_POOLS = REPO / "modpack/server-overrides/config/cobblemon-gacha/authored/egg_pools.json"
MANIFEST = REPO / "ops/baby-legends-overrides-manifest.txt"

# Baby-tier statline applied to EVERY baby legend so none are competitively
# viable. BST 220 (~Cleffa's 218). Tune here if the server wants them weaker /
# stronger — the value is intentionally uniform across all species.
WEAK_STATS: dict[str, int] = {
    "hp": 45,
    "attack": 35,
    "defence": 35,
    "special_attack": 35,
    "special_defence": 35,
    "speed": 35,
}
TARGET_BST = sum(WEAK_STATS.values())

SPAWN_PREFIX = "data/cobblemon/spawn_pool_world/"
SPECIES_PREFIX = "data/cobblemon/species/custom/"
EGG_POOL_TIER = "baby_legend"


def read_jar_url() -> str:
    """Pull the [download] url out of the mod's packwiz metafile."""
    if not PW_TOML.exists():
        raise SystemExit(f"Mod metafile not found: {PW_TOML}")
    text = PW_TOML.read_text(encoding="utf-8")
    m = re.search(r'^\s*url\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not m:
        raise SystemExit(f"No [download] url in {PW_TOML}")
    return m.group(1)


def fetch_jar(url: str) -> bytes:
    """Download the jar bytes (browser UA — some CDNs 403 default UAs)."""
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp:  # noqa: S310 (pinned modrinth url)
        return resp.read()


def dump_json(doc: dict) -> bytes:
    """Match the committed files byte-for-byte: 2-space indent, unicode kept,
    trailing newline. Re-running on the pinned jar yields a zero diff."""
    return (json.dumps(doc, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def wipe_json(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for f in directory.glob("*.json"):
        f.unlink()


def main() -> None:
    url = read_jar_url()
    jar_name = url.rsplit("/", 1)[-1]
    print(f"Baby Legends jar (from pw.toml): {jar_name}")
    raw = fetch_jar(url)

    spawn_lines: list[str] = []
    species_lines: list[str] = []
    species_ids: set[str] = set()

    wipe_json(SPAWN_DIR)
    wipe_json(SPECIES_DIR)

    with zipfile.ZipFile(BytesIO(raw)) as z:
        names = z.namelist()

        # 1. Spawn overrides — disable every wild spawn the jar ships.
        spawn_files = sorted(
            n for n in names
            if n.startswith(SPAWN_PREFIX) and n.endswith(".json")
        )
        for name in spawn_files:
            sid = Path(name).stem
            doc = {
                "enabled": False,
                "neededInstalledMods": [],
                "neededUninstalledMods": [],
                "spawns": [],
                "_comment": (
                    f"server-baby-legends: baby legends are crate-only (poke-egg). "
                    f"Overrides baby-legends-cobblemon jar's wild spawn for {sid}."
                ),
            }
            (SPAWN_DIR / f"{sid}.json").write_bytes(dump_json(doc))
            spawn_lines.append(f"  {sid:14} wild spawn disabled")

        # 2. Species overrides — strip evolution, flatten stats.
        species_files = sorted(
            n for n in names
            if n.startswith(SPECIES_PREFIX) and n.endswith(".json")
        )
        for name in species_files:
            sid = Path(name).stem
            doc = json.loads(z.read(name).decode("utf-8"))
            old_bst = sum(doc.get("baseStats", {}).values())
            had_evo = bool(doc.get("evolutions"))
            doc["baseStats"] = dict(WEAK_STATS)
            doc["evolutions"] = []
            (SPECIES_DIR / f"{sid}.json").write_bytes(dump_json(doc))
            species_ids.add(sid)
            species_lines.append(
                f"  {sid:14} BST {old_bst:>3} -> {TARGET_BST}"
                f"   evo_removed={'yes' if had_evo else 'no'}"
            )

    # 3. Egg-pool drift check (warn-only — egg_pools.json is hand-authored).
    pool_ids: set[str] = set()
    if EGG_POOLS.exists():
        pools = json.loads(EGG_POOLS.read_text(encoding="utf-8"))
        pool_ids = {e["id"] for e in pools.get(EGG_POOL_TIER, [])}
    missing_from_pool = sorted(species_ids - pool_ids)  # in jar, not in crate
    stale_in_pool = sorted(pool_ids - species_ids)       # in crate, not in jar

    # Manifest.
    lines = [
        "# Baby Legends override manifest — generated by ops/gen_baby_legends_overrides.py",
        f"# Source jar: {jar_name}",
        f"# {len(spawn_files)} wild spawns disabled, "
        f"{len(species_files)} species nerfed to BST {TARGET_BST} + evolutions stripped.",
        "",
        "SPAWNS (crate-only):",
        *spawn_lines,
        "",
        "SPECIES (no evolution, baby-tier stats):",
        *species_lines,
        "",
        f"EGG POOL ({EGG_POOL_TIER}) sync:",
    ]
    if not missing_from_pool and not stale_in_pool:
        lines.append("  OK — egg pool matches the jar's standalone species.")
    else:
        for sid in missing_from_pool:
            lines.append(f"  WARN: {sid} is in the jar but NOT in the egg pool — add it (or intentionally skip).")
        for sid in stale_in_pool:
            lines.append(f"  WARN: {sid} is in the egg pool but NOT a jar species — stale, remove it.")
    lines.append("")
    MANIFEST.write_text("\n".join(lines), encoding="utf-8")

    print(f"Wrote {len(spawn_files)} spawn overrides, {len(species_files)} species overrides.")
    print(f"Manifest: {MANIFEST.relative_to(REPO)}")
    if missing_from_pool or stale_in_pool:
        print("\n*** EGG-POOL DRIFT — fix egg_pools.json (and tables/pokemon.json odds) by hand: ***")
        for sid in missing_from_pool:
            print(f"  + add '{sid}' to the '{EGG_POOL_TIER}' tier")
        for sid in stale_in_pool:
            print(f"  - remove stale '{sid}' from the '{EGG_POOL_TIER}' tier")
        print("Then: cd modpack && packwiz refresh")
        sys.exit(1)
    print("Egg pool in sync. Next: cd modpack && packwiz refresh")


if __name__ == "__main__":
    main()
