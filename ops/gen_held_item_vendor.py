#!/usr/bin/env python3
"""Append a held-item vendor's worth of entries to
modpack/server-overrides/config/cobblemon-market/authored/items.json.
Pulls the canonical held-item list from Cobblemon's `is_held_item` tag
on the dev VM so future Cobblemon updates can be reflected with a rerun.

Each entry is buy-only (sellable=false), flat $5,000, vendorTag=held_items —
mirrors the TM-vendor pattern from 0.7.4. Spawn the vendor with
`/market admin spawn held_items`.

Excluded from the tag's 106 entries:
  - Tag refs (`#cobblemon:held/terrain_seeds`, `#cobblemon:type_gems`) — those
    expand to other items, would double-count or miss specific ids
  - `cobblemon:medicinal_leek` — it's a crop (in `cobbleworkers:crops` tag) so
    it's already a farmable resource, not a held-item purchase
  - Vanilla `minecraft:bone`, `minecraft:snowball` — plentiful via gameplay
"""
import json
import subprocess
import sys
from pathlib import Path

ITEMS_JSON = Path("modpack/server-overrides/config/cobblemon-market/authored/items.json")
VENDOR_TAG = "held_items"
PRICE = 5000

EXCLUDE = {
    "cobblemon:medicinal_leek",
    "minecraft:bone",
    "minecraft:snowball",
}


def fetch_held_items() -> list[str]:
    """SSH into dev VM, read is_held_item tag from Cobblemon jar, return item ids."""
    cmd = [
        "ssh", "cobblemon",
        "python3 -c \""
        "import zipfile, json; "
        "z = zipfile.ZipFile('/opt/cobblemon-dev/mods/Cobblemon-neoforge-1.7.3+1.21.1.jar'); "
        "d = json.loads(z.read('data/cobblemon/tags/item/held/is_held_item.json').decode()); "
        "print('\\n'.join(v if isinstance(v, str) else v.get('id', '') for v in d['values']))"
        "\"",
    ]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True)
    raw = [line.strip() for line in out.stdout.splitlines() if line.strip()]
    # Drop tag refs (start with #) and the explicit excludes.
    return sorted(i for i in raw if not i.startswith("#") and i not in EXCLUDE)


def make_entry() -> dict:
    return {
        "baseBuyPrice": PRICE,
        "baseSellPrice": 0,
        "baseStock": 9999,
        "elasticity": 0.0,
        "maxStockMultiplier": 10.0,
        "vendorTag": VENDOR_TAG,
        "sellable": False,
    }


def main() -> int:
    items_data = json.loads(ITEMS_JSON.read_text(encoding="utf-8"))
    held = fetch_held_items()
    print(f"adding {len(held)} held-item entries (vendorTag={VENDOR_TAG}, price=${PRICE})")

    added = 0
    for item_id in held:
        if item_id in items_data:
            # already in items.json — leave existing entry alone (admin might have tuned it)
            continue
        items_data[item_id] = make_entry()
        added += 1

    # Sorted output keeps the file diff stable across reruns.
    sorted_items = dict(sorted(items_data.items()))
    ITEMS_JSON.write_text(
        json.dumps(sorted_items, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"added {added} new entries; items.json now has {len(sorted_items)} total")
    return 0


if __name__ == "__main__":
    sys.exit(main())
