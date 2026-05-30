#!/usr/bin/env python3
"""Generate a datapack that strips `cobblemon:exp_candy_*` items from worldgen
chest loot tables. Two strategies depending on shape:

  - Cobblemon sets/any_exp_candy.json — overridden with an empty pool.
    Every cobblemon table that pulls from this sub-loot-table will roll
    nothing for the EXP candy slot.
  - mega_showdown tables that INLINE `cobblemon:exp_candy_*` entries — read
    each, filter the offending entries out of every pool, drop pools that
    become empty, write the result to the override.

Reads source JSON from JARs on the dev VM via SSH (we don't have them locally).
"""
import json
import subprocess
import sys
from pathlib import Path

OUT = Path("modpack/server-overrides/datapacks/server-no-exp-candy-chests/data")

# (jar-on-vm, in-jar-path, output-relative-path)
TARGETS = [
    ("Cobblemon-neoforge-1.7.3+1.21.1.jar",
     "data/cobblemon/loot_table/sets/any_exp_candy.json",
     "cobblemon/loot_table/sets/any_exp_candy.json"),
    ("mega_showdown-neoforge-1.8.2+1.7.3+1.21.1-hotfix.jar",
     "data/mega_showdown/loot_table/chests/observatory_chest.json",
     "mega_showdown/loot_table/chests/observatory_chest.json"),
    ("mega_showdown-neoforge-1.8.2+1.7.3+1.21.1-hotfix.jar",
     "data/mega_showdown/loot_table/chests/observatory_barrel_2.json",
     "mega_showdown/loot_table/chests/observatory_barrel_2.json"),
    ("mega_showdown-neoforge-1.8.2+1.7.3+1.21.1-hotfix.jar",
     "data/mega_showdown/loot_table/archaeology/ruins.json",
     "mega_showdown/loot_table/archaeology/ruins.json"),
]

def is_exp_candy_entry(entry: dict) -> bool:
    t = entry.get("type", "")
    name = entry.get("name", "")
    value = entry.get("value", "")
    if t == "minecraft:item" and name.startswith("cobblemon:exp_candy"):
        return True
    # Nested loot_table refs to the set are also redundant once we override the set,
    # but we strip them defensively in case the override mechanism doesn't fire for
    # some referencer.
    if t == "minecraft:loot_table" and value == "cobblemon:sets/any_exp_candy":
        return True
    return False


def strip_table(table: dict) -> dict:
    pools = table.get("pools", [])
    new_pools = []
    for p in pools:
        entries = p.get("entries", [])
        # Recursive: an `entries` entry can itself be a group with `children` etc.
        # For our targets every entry is a leaf, so a flat filter suffices.
        kept = [e for e in entries if not is_exp_candy_entry(e)]
        if kept:
            p2 = dict(p); p2["entries"] = kept
            new_pools.append(p2)
    out = dict(table); out["pools"] = new_pools
    return out


def fetch_jar_entry(jar: str, path: str) -> bytes:
    # SSH-based fetch: print the file content to stdout as base64, decode locally.
    # Plain stdout streaming would mangle binary, but loot tables are utf-8 JSON.
    cmd = [
        "ssh", "cobblemon",
        f"python3 -c \"import zipfile; print(zipfile.ZipFile('/opt/cobblemon-dev/mods/{jar}').read('{path}').decode())\"",
    ]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return out.stdout


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for jar, in_path, out_rel in TARGETS:
        src_text = fetch_jar_entry(jar, in_path)
        src = json.loads(src_text)
        if in_path.endswith("sets/any_exp_candy.json"):
            stripped = {"type": "minecraft:chest", "pools": []}
        else:
            stripped = strip_table(src)
        dest = OUT / out_rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(json.dumps(stripped, indent=2) + "\n", encoding="utf-8")
        print(f"  wrote {out_rel} (pools: {len(src.get('pools', []))} -> "
              f"{len(stripped['pools'])})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
