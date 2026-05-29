#!/usr/bin/env python3
"""
Generate the SimpleTMs vendor entries for cobblemon-market.

Reads the SimpleTMs jar to get the list of TM items, and Pokémon Showdown's moves data to
derive each move's type, then emits a `items.json` block partitioned by `vendorTag: "tm_<type>"`.

Usage:
    ops/gen-tm-items.py --simpletms <path-to-SimpleTMs.jar>
        [--showdown-cache /tmp/moves.json]
        [--price 5000]
        [--out modpack/server-overrides/config/cobblemon-market/authored/items.json.tm_block]
        [--merge modpack/server-overrides/config/cobblemon-market/authored/items.json]

Without --merge the script writes the TM-only block to a stand-alone file. With --merge it
loads the existing items.json, drops any pre-existing `simpletms:tm_*` entries, adds the new
ones, and writes the merged file back. Either mode is idempotent — re-runnable when SimpleTMs
updates or you change the default price.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

SHOWDOWN_MOVES_URL = (
    "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/moves.ts"
)

# Pokemon-Showdown's TS data uses each move's lowercase id as the dict key, with a `type:
# "Foo"` field nested inside each block. We track the current top-level key while scanning
# for the matching type. Nested `flags: { ... }` blocks defeat simple regex spans, so a
# line-oriented walk is more reliable than a single multi-line regex.
KEY_LINE = re.compile(r"^\s*([0-9a-z_]+):\s*\{")
TYPE_LINE = re.compile(r"^\s+type:\s+\"([A-Z][a-zA-Z]+)\",")


def fetch_showdown_moves(cache: Path | None) -> dict[str, str]:
    """Return {move_id_lowercase_alphanumeric: Type}. Populates `cache` for reuse."""
    if cache and cache.exists():
        text = cache.read_text()
    else:
        print(f"Fetching {SHOWDOWN_MOVES_URL} …", file=sys.stderr)
        with urllib.request.urlopen(SHOWDOWN_MOVES_URL) as resp:
            text = resp.read().decode("utf-8")
        if cache:
            cache.write_text(text)

    # Showdown's top-level dict keys already use the same lowercase-alphanumeric id the
    # SimpleTMs item ids use (e.g. `absorb`, `hiddenpower`, `10000000voltthunderbolt`).
    # Walk line by line, latching the latest top-level move id and recording its type when
    # we see the `type:` line for the same block.
    move_types: dict[str, str] = {}
    current_key: str | None = None
    nest_depth = 0
    for line in text.splitlines():
        if nest_depth == 0:
            m = KEY_LINE.match(line)
            if m:
                current_key = m.group(1)
                nest_depth = 1
                continue
        else:
            nest_depth += line.count("{") - line.count("}")
            if current_key and TYPE_LINE.match(line):
                move_types[current_key] = TYPE_LINE.match(line).group(1)
            if nest_depth <= 0:
                current_key = None
                nest_depth = 0
    return move_types


def list_simpletms_tm_ids(jar_path: Path) -> list[str]:
    """Pull every TM move id from the SimpleTMs jar's `assets/.../models/item/tm_*.json`."""
    with zipfile.ZipFile(jar_path) as z:
        names = [
            n for n in z.namelist()
            if n.startswith("assets/simpletms/models/item/tm_") and n.endswith(".json")
        ]
    ids = sorted(Path(n).stem.removeprefix("tm_") for n in names)
    return ids


def build_entries(
    tm_ids: list[str],
    move_types: dict[str, str],
    price: int,
) -> tuple[dict[str, dict], list[str]]:
    """Return (item_entries_map, unmapped_move_ids). Skips moves whose type we can't resolve."""
    entries: dict[str, dict] = {}
    unmapped: list[str] = []
    for move_id in tm_ids:
        ptype = move_types.get(move_id)
        if not ptype:
            unmapped.append(move_id)
            continue
        item_id = f"simpletms:tm_{move_id}"
        entries[item_id] = {
            "baseBuyPrice": price,
            "baseSellPrice": 0,
            "baseStock": 9999,
            "elasticity": 0.0,
            "maxStockMultiplier": 10.0,
            "vendorTag": f"tm_{ptype.lower()}",
            "sellable": False,
        }
    return entries, unmapped


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--simpletms", required=True, type=Path,
                    help="Path to SimpleTMs-neoforge-X.Y.Z.jar")
    ap.add_argument("--showdown-cache", type=Path, default=Path("/tmp/showdown-moves.ts"))
    ap.add_argument("--price", type=int, default=5000)
    ap.add_argument("--out", type=Path)
    ap.add_argument("--merge", type=Path,
                    help="If given, merge into this existing items.json instead of writing standalone")
    args = ap.parse_args()

    move_types = fetch_showdown_moves(args.showdown_cache)
    print(f"Loaded {len(move_types)} moves from Showdown", file=sys.stderr)

    tm_ids = list_simpletms_tm_ids(args.simpletms)
    print(f"Found {len(tm_ids)} TM items in {args.simpletms.name}", file=sys.stderr)

    entries, unmapped = build_entries(tm_ids, move_types, args.price)
    by_type: dict[str, int] = {}
    for e in entries.values():
        by_type[e["vendorTag"]] = by_type.get(e["vendorTag"], 0) + 1
    print(f"Mapped {len(entries)} TMs across {len(by_type)} type vendors:", file=sys.stderr)
    for tag in sorted(by_type):
        print(f"  {tag}: {by_type[tag]}", file=sys.stderr)
    if unmapped:
        print(f"WARNING: {len(unmapped)} moves had no Showdown type — skipped:", file=sys.stderr)
        print("  " + ", ".join(unmapped), file=sys.stderr)

    if args.merge:
        try:
            existing = json.loads(args.merge.read_text())
        except FileNotFoundError:
            existing = {}
        # Drop prior TM rows so a re-run doesn't accumulate stale ids.
        existing = {k: v for k, v in existing.items() if not k.startswith("simpletms:tm_")}
        existing.update(entries)
        args.merge.write_text(json.dumps(existing, indent=2) + "\n")
        print(f"Merged into {args.merge}", file=sys.stderr)
    else:
        out = args.out or Path("tm-items.json")
        out.write_text(json.dumps(entries, indent=2) + "\n")
        print(f"Wrote {out}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
