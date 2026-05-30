#!/usr/bin/env python3
"""
Apply the 0.7.11 market-overhaul authored values to items.json.

Scope: default-vendor scope only (vendorTag == ""). Held-items and TM-vendor
entries are preserved untouched. Categories marked "ships after daily-cap mod"
in the source table are intentionally skipped here (Exp Candies, Hyper
Training Candies) so this PR doesn't ship items that don't have a per-player
spend cap yet.

Run from repo root:
    python3 ops/apply_market_overhaul_values.py
"""
from __future__ import annotations

import json
from pathlib import Path

ITEMS_JSON = Path("modpack/server-overrides/config/cobblemon-market/authored/items.json")

# Per-item entries for the default vendor.
# Schema mirrors ItemEntry.kt — only the fields we actually want set; missing
# fields use Kotlin defaults (maxStockMultiplier=10.0, vendorTag="", sellable=true).
#
# Corrections applied vs the source table:
#   - All baseStock values floored at 200 (was 100 in earlier draft — bumped after
#     wiring up the server-wide `buyStockImpact = 3.0` default, so shift-buy-16
#     stays clear of the stock floor: 16 * 3 = 48 << 200).
#   - "Super common" items (carrot, all three Poké Ball tiers) bumped to 1000.
#   - Carrot elasticity lowered to 0.7.
#
# `baseSellPrice = 0` + `sellable = false` is how we model "buy-only" items.
DEFAULT_VENDOR_ITEMS: dict[str, dict] = {
    # ─── Pokéballs ──────────────────────────────────────────────────────
    "cobblemon:poke_ball":  {"baseBuyPrice": 40,  "baseSellPrice": 8,  "baseStock": 1000, "elasticity": 0.3},
    "cobblemon:great_ball": {"baseBuyPrice": 75,  "baseSellPrice": 15, "baseStock": 1000, "elasticity": 0.5},
    "cobblemon:ultra_ball": {"baseBuyPrice": 125, "baseSellPrice": 25, "baseStock": 1000, "elasticity": 0.5},

    # ─── Vegetables ────────────────────────────────────────────────────
    # 0.7.12: baseSellPrice raised 0 → 2 (+ sellable=true) so excess carrots from
    # the farm have a sink and the carrot economy isn't one-way.
    "minecraft:carrot": {"baseBuyPrice": 8, "baseSellPrice": 2, "baseStock": 1000, "elasticity": 0.7},

    # ─── HP potions ────────────────────────────────────────────────────
    # Hyper Potion slotted at 180/36 between Super (135/27) and Max (225/45) so each tier is
    # strictly better-priced than the one below — original table had Hyper = Super.
    "cobblemon:potion":       {"baseBuyPrice": 90,  "baseSellPrice": 18, "baseStock": 200},
    "cobblemon:super_potion": {"baseBuyPrice": 135, "baseSellPrice": 27, "baseStock": 200},
    "cobblemon:hyper_potion": {"baseBuyPrice": 180, "baseSellPrice": 36, "baseStock": 200},
    "cobblemon:max_potion":   {"baseBuyPrice": 225, "baseSellPrice": 45, "baseStock": 200},
    "cobblemon:full_restore": {"baseBuyPrice": 300, "baseSellPrice": 60, "baseStock": 200},

    # ─── Revives ───────────────────────────────────────────────────────
    "cobblemon:revive":     {"baseBuyPrice": 90,  "baseSellPrice": 18, "baseStock": 200},
    "cobblemon:max_revive": {"baseBuyPrice": 225, "baseSellPrice": 45, "baseStock": 200},

    # ─── Status heals ──────────────────────────────────────────────────
    "cobblemon:antidote":       {"baseBuyPrice": 60, "baseSellPrice": 12, "baseStock": 200},
    "cobblemon:burn_heal":      {"baseBuyPrice": 60, "baseSellPrice": 12, "baseStock": 200},
    "cobblemon:paralyze_heal":  {"baseBuyPrice": 60, "baseSellPrice": 12, "baseStock": 200},
    "cobblemon:ice_heal":       {"baseBuyPrice": 60, "baseSellPrice": 12, "baseStock": 200},
    "cobblemon:awakening":      {"baseBuyPrice": 60, "baseSellPrice": 12, "baseStock": 200},
    "cobblemon:full_heal":      {"baseBuyPrice": 190, "baseSellPrice": 38, "baseStock": 200},

    # ─── PP restore ────────────────────────────────────────────────────
    # Elixir/Max Elixir are strictly better than Ether/Max Ether (restore PP for all moves vs one).
    # Original table priced them identically; rebalanced so the ordering is
    #   Ether < Max Ether < Elixir < Max Elixir
    # matching vanilla Pokémon's intent.
    "cobblemon:ether":      {"baseBuyPrice": 90,  "baseSellPrice": 18, "baseStock": 200},
    "cobblemon:max_ether":  {"baseBuyPrice": 190, "baseSellPrice": 38, "baseStock": 200},
    "cobblemon:elixir":     {"baseBuyPrice": 225, "baseSellPrice": 45, "baseStock": 200},
    "cobblemon:max_elixir": {"baseBuyPrice": 315, "baseSellPrice": 63, "baseStock": 200},

    # ─── Loot-only (still purchasable from market at premium) ──────────
    # No explicit buyStockImpact — picks up the server-wide default of 3.0
    # (see ItemConfig.kt's effectiveBuyStockImpact extension property).
    "cobblemon:rare_candy": {"baseBuyPrice": 4050, "baseSellPrice": 810, "baseStock": 200, "elasticity": 1.0},
}

# Schema defaults — match ItemEntry.kt. The optional overhaul fields
# (buyPriceClamp / sellPriceClamp / buyStockImpact / sellStockImpact /
# minBuyPrice) are omitted from output unless set on the entry — keeps the
# JSON minimal and lets Kotlin's nullable-default behavior take over.
REQUIRED_OR_DEFAULT = {
    "baseBuyPrice": None,         # required
    "baseSellPrice": None,        # required
    "baseStock": 200,
    "elasticity": 1.0,
    "maxStockMultiplier": 10.0,
    "vendorTag": "",
    "sellable": True,
}
OPTIONAL_OVERRIDES = (
    "buyPriceClamp", "sellPriceClamp",
    "buyStockImpact", "sellStockImpact",
    "minBuyPrice",
)


def normalize_entry(partial: dict) -> dict:
    """Fill in schema defaults; carry through any optional overhaul overrides."""
    out = {}
    for key, default in REQUIRED_OR_DEFAULT.items():
        out[key] = partial.get(key, default)
        if out[key] is None:
            raise ValueError(f"Missing required field {key} in {partial}")
    for key in OPTIONAL_OVERRIDES:
        if key in partial:
            out[key] = partial[key]
    return out


def main() -> None:
    data = json.loads(ITEMS_JSON.read_text())

    # Wipe ALL default-vendor entries (vendorTag == "") so the script is idempotent:
    # re-running it produces the same JSON regardless of whether prior runs already
    # planted the overhaul items. Other-vendor entries (held_items, tm_*) are kept.
    default_ids = [k for k, v in data.items() if v.get("vendorTag", "") == ""]
    for k in default_ids:
        del data[k]

    for k, partial in DEFAULT_VENDOR_ITEMS.items():
        data[k] = normalize_entry(partial)

    # Re-sort alphabetically so the resulting file diff is minimal and predictable.
    sorted_data = dict(sorted(data.items()))

    ITEMS_JSON.write_text(json.dumps(sorted_data, indent=2) + "\n")
    print(f"Wrote {len(sorted_data)} entries to {ITEMS_JSON}")
    print(f"  default-vendor: {len(DEFAULT_VENDOR_ITEMS)}")
    print(f"  preserved (held_items / tm_*): {len(sorted_data) - len(DEFAULT_VENDOR_ITEMS)}")


if __name__ == "__main__":
    main()
