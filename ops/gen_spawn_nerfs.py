#!/usr/bin/env python3
"""Regenerate the server-spawn-nerfs datapack from AllTheMons.

The AllTheMons [R3.5] zip ships spawn pools for legendaries, mythicals,
paradoxes, and ultra beasts under
`data/special_spawns/spawn_pool_world/<category>/<species>.json`.

This script overrides every entry at the same path with:

  1. weight     ← effective_weight(species, category):
                    - LM species (covered by LegendaryMonuments mod) get
                      weight 0 — they should ONLY be obtainable via the
                      LM questline, never via random wild spawn.
                    - otherwise: base = TIER_WEIGHTS[SPECIES_TIERS[species]]
                    - mythicals additionally floored at the Ubers weight
                      (so OU/UU+ mythicals like Mew, Jirachi, Phione
                      can never be more common than a box legend)
                  (replaces upstream weight entirely — does NOT scale)
  2. bucket     ← always set to "ultra-rare" (legendaries/most mythicals
                  already are; paradoxes + most UBs get promoted from
                  "rare" — the whole point is to put them all in the
                  same shared ultra-rare bucket roll)

The flat per-tier weighting normalises rates within a tier — Suicune
(upstream weight 0.5) and Articuno (upstream 2.0) both become 1.0 under
the UU+ tier — so upstream's per-species inconsistencies don't leak
through. The curve is intentionally steep: top-tier (AG) species are
20× rarer than UU+ ones, mirroring the intent that game-defining
legendaries should feel like once-a-server events.

Re-run after bumping AllTheMons to pick up new species, or to retune:

    python3 ops/gen_spawn_nerfs.py

If a species in the AllTheMons zip isn't in SPECIES_TIERS, the script
emits a WARNING and skips that file — add a tier and re-run.
"""
from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ATM_ZIP_PATH = REPO / "modpack/server-overrides/datapacks/AllTheMons [R3.5].zip"
OUT_BASE = REPO / "modpack/server-overrides/datapacks/server-spawn-nerfs"

# Categories we process from ATM's data/special_spawns/spawn_pool_world/.
# Pseudo-legendaries intentionally NOT in scope — those spawn from
# cobblemon-base spawn pools as base forms (Dratini/Gible/Bagon/etc.)
# and are a separate balance question from "how rare should this
# legendary be".
CATEGORIES = ("legendary", "mythical", "paradox", "ultra_beast")

# Steep tier curve — AG (rarest) < Ubers < OU < UU+ (most common).
# 20× spread top to bottom: game-defining legendaries are once-per-server
# events; competitively-weak legendaries (Articuno trio, Beasts trio,
# Regis) are still rare but findable.
TIER_WEIGHTS: dict[str, float] = {
    "AG":    0.05,   # banned everywhere — vanishingly rare
    "Ubers": 0.10,   # banned from OU — very rare
    "OU":    0.30,   # competitive staples
    "UU+":   1.00,   # lower-tier / competitively weak
}

# Mythicals get an extra floor: regardless of competitive tier, a
# mythical is at most as common as an Ubers-tier legendary. So Mew (OU
# tier on paper) effectively spawns at the Ubers weight, Phione (UU+ on
# paper) likewise, etc. AG mythicals (Arceus, Deoxys) stay at the AG
# weight since AG < Ubers.
MYTHICAL_FLOOR_TIER = "Ubers"

# Species covered by the LegendaryMonuments mod (verified against
# LegendaryMonuments-7.8.jar pedestal/shrine assets + in-game UI). These
# have a dedicated questline — pedestal blocks, structure spawns, treat
# items, monument completion vouchers from the gacha — so allowing them
# to also spawn randomly in the wild would undermine the intended
# acquisition path. Override their weight to 0 to fully suppress wild
# spawns. Players acquire them ONLY via LM.
#
# 46 species. Re-verify if LegendaryMonuments adds new content.
LM_SPECIES: set[str] = {
    # Gen 1
    "articuno", "zapdos", "moltres", "mew",
    # Gen 2
    "raikou", "entei", "suicune", "lugia", "hooh", "celebi",
    # Gen 3
    "regirock", "regice", "registeel", "latias", "latios",
    # Gen 4
    "regigigas", "cresselia", "darkrai", "heatran", "mesprit",
    "azelf", "uxie", "dialga", "palkia", "giratina", "arceus",
    # Gen 5
    "cobalion", "terrakion", "virizion", "keldeo",
    "reshiram", "zekrom", "kyurem", "victini",
    # Gen 6
    "hoopa",
    # Gen 7
    "cosmog", "meltan",
    # Gen 8
    "zacian", "zamazenta", "eternatus", "regieleki", "regidrago",
    # Gen 9
    "chienpao", "chiyu", "tinglu", "wochien",
}

# Per-species tier, classified by peak Gen 6+ competitive tier of the
# species' FINAL evolved form (Cosmog → Solgaleo's tier, Poipole →
# Naganadel's tier, etc.). Borderline calls noted in CHANGELOG.
SPECIES_TIERS: dict[str, str] = {
    # ─── Legendaries (62) ─────────────────────────────────────────────
    # AG (3)
    "miraidon": "AG", "koraidon": "AG", "eternatus": "AG",
    # Ubers (24)
    "lugia": "Ubers", "hooh": "Ubers", "kyogre": "Ubers", "groudon": "Ubers",
    "rayquaza": "Ubers", "dialga": "Ubers", "palkia": "Ubers", "giratina": "Ubers",
    "reshiram": "Ubers", "zekrom": "Ubers", "kyurem": "Ubers", "xerneas": "Ubers",
    "yveltal": "Ubers", "zygarde": "Ubers", "necrozma": "Ubers", "zacian": "Ubers",
    "zamazenta": "Ubers", "spectrier": "Ubers", "chiyu": "Ubers", "terapagos": "Ubers",
    "calyrex": "Ubers", "cosmog": "Ubers", "kubfu": "Ubers", "chienpao": "Ubers",
    # OU (21)
    "tapukoko": "OU", "tapulele": "OU", "tapubulu": "OU", "tapufini": "OU",
    "heatran": "OU", "latios": "OU", "latias": "OU", "cresselia": "OU",
    "tornadus": "OU", "thundurus": "OU", "landorus": "OU", "regieleki": "OU",
    "regidrago": "OU", "wochien": "OU", "fezandipiti": "OU", "okidogi": "OU",
    "munkidori": "OU", "ogerpon": "OU", "enamorus": "OU", "terrakion": "OU",
    "cobalion": "OU",
    # UU+ (14)
    "articuno": "UU+", "zapdos": "UU+", "moltres": "UU+", "entei": "UU+",
    "raikou": "UU+", "suicune": "UU+", "regice": "UU+", "regirock": "UU+",
    "registeel": "UU+", "regigigas": "UU+", "mesprit": "UU+", "uxie": "UU+",
    "azelf": "UU+", "glastrier": "UU+",

    # ─── Mythicals (20) ──────────────────────────────────────────────
    # AG (2): banned-everywhere mythicals
    "arceus": "AG", "deoxys": "AG",
    # Ubers (6)
    "darkrai": "Ubers", "hoopa": "Ubers", "magearna": "Ubers",
    "marshadow": "Ubers", "shaymin": "Ubers", "victini": "Ubers",
    # OU (9)
    "celebi": "OU", "diancie": "OU", "jirachi": "OU", "keldeo": "OU",
    "manaphy": "OU", "mew": "OU", "meltan": "OU", "pecharunt": "OU",
    "volcanion": "OU",
    # UU+ (3)
    "phione": "UU+", "zarude": "UU+", "zeraora": "UU+",

    # ─── Paradoxes (17) ──────────────────────────────────────────────
    # Ubers (7)
    "fluttermane": "Ubers", "ironbundle": "Ubers", "roaringmoon": "Ubers",
    "ironvaliant": "Ubers", "ironcrown": "Ubers", "gougingfire": "Ubers",
    "ragingbolt": "Ubers",
    # OU (7)
    "greattusk": "OU", "irontreads": "OU", "slitherwing": "OU",
    "ironmoth": "OU", "sandyshocks": "OU", "ironthorns": "OU",
    "walkingwake": "OU",
    # UU+ (3)
    "brutebonnet": "UU+", "screamtail": "UU+", "ironleaves": "UU+",

    # ─── Ultra Beasts (9) ────────────────────────────────────────────
    # Ubers (2): Pheromosa banned Gen 7; Poipole → Naganadel Ubers Gen 7
    "pheromosa": "Ubers", "poipole": "Ubers",
    # OU (6)
    "kartana": "OU", "blacephalon": "OU", "xurkitree": "OU",
    "nihilego": "OU", "stakataka": "OU", "buzzwole": "OU",
    # UU+ (1)
    "guzzlord": "UU+",
}

PACK_MCMETA = {
    "pack": {
        "pack_format": 48,
        "description": (
            "Tier-based spawn rates for legendaries, mythicals, paradoxes, and "
            "ultra beasts (overrides AllTheMons spawn pools)."
        ),
    }
}


def main() -> None:
    if not ATM_ZIP_PATH.exists():
        raise SystemExit(f"AllTheMons zip not found: {ATM_ZIP_PATH}")

    data_dir = OUT_BASE / "data/special_spawns/spawn_pool_world"
    if data_dir.exists():
        shutil.rmtree(data_dir)
    for cat in CATEGORIES:
        (data_dir / cat).mkdir(parents=True, exist_ok=True)

    (OUT_BASE / "pack.mcmeta").write_text(json.dumps(PACK_MCMETA, indent=2) + "\n")

    file_count = 0
    entry_count = 0
    unmapped: list[str] = []
    by_tier: dict[str, int] = {t: 0 for t in TIER_WEIGHTS}
    suppressed_count = 0

    with zipfile.ZipFile(ATM_ZIP_PATH) as z:
        for cat in CATEGORIES:
            prefix = f"data/special_spawns/spawn_pool_world/{cat}/"
            for name in z.namelist():
                if not name.startswith(prefix) or not name.endswith(".json"):
                    continue
                species = Path(name).stem
                tier = SPECIES_TIERS.get(species)
                if tier is None:
                    unmapped.append(f"{cat}/{species}")
                    continue
                is_lm = species in LM_SPECIES
                if is_lm:
                    weight = 0.0
                    tier_note = f"{tier}→LM-suppressed"
                else:
                    base_weight = TIER_WEIGHTS[tier]
                    floored = (
                        cat == "mythical"
                        and base_weight > TIER_WEIGHTS[MYTHICAL_FLOOR_TIER]
                    )
                    weight = TIER_WEIGHTS[MYTHICAL_FLOOR_TIER] if floored else base_weight
                    tier_note = f"{tier}→{MYTHICAL_FLOOR_TIER}-floor" if floored else tier
                d = json.loads(z.read(name).decode("utf-8"))
                for entry in d.get("spawns", []):
                    entry["weight"] = weight
                    entry["bucket"] = "ultra-rare"
                d["_comment"] = (
                    f"Override of AllTheMons {cat} spawn pool — "
                    f"tier={tier_note} weight={weight} bucket=ultra-rare"
                )
                out_name = Path(name).name
                (data_dir / cat / out_name).write_text(json.dumps(d, indent=2) + "\n")
                file_count += 1
                entry_count += len(d.get("spawns", []))
                by_tier[tier] += 1
                if is_lm:
                    suppressed_count += 1

    print(f"Wrote {file_count} files / {entry_count} spawn entries to {OUT_BASE}")
    print(f"By tier (all entries): {by_tier}")
    print(f"LM-suppressed (weight=0): {suppressed_count} species "
          f"({file_count - suppressed_count} species spawn at tier weights)")
    # Sanity check: any LM species we declared but don't see in ATM?
    seen_species = {Path(n).stem for n in z.namelist() if n.endswith(".json")}
    lm_unused = sorted(LM_SPECIES - seen_species)
    if lm_unused:
        print(f"\nNote: {len(lm_unused)} LM species have no ATM spawn pool "
              f"(no override needed): {lm_unused}")
    if unmapped:
        print(f"\nWARNING: {len(unmapped)} species in ATM not in SPECIES_TIERS — add and re-run:")
        for u in unmapped:
            print(f"  {u}")


if __name__ == "__main__":
    main()
