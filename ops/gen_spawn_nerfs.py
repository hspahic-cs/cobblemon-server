#!/usr/bin/env python3
"""Bake tier-based spawn rates directly into the AllTheMons datapack zip.

AllTheMons ships spawn pools for legendaries, mythicals, paradoxes, and
ultra beasts under
`data/special_spawns/spawn_pool_world/<category>/<species>.json`.

Historically we shipped a SEPARATE `server-spawn-nerfs` datapack that
re-declared each of these files at the same resource path with nerfed
weights. That only worked if our pack out-prioritised AllTheMons in
`level.dat`'s enabled list — and it silently DIDN'T, because every
AllTheMons version bump produces a new zip filename that Minecraft
auto-enables LAST (= highest priority), clobbering all our overrides.
Net effect: ultra-rares spawned ~100x too often. See
memory/reference_datapack_priority_allthemons.md.

This script removes that whole class of bug by editing AllTheMons'
OWN files in place instead of shipping a competing copy. There is then
exactly one `xurkitree.json`, so load order is irrelevant.

For each in-scope spawn file it rewrites every spawn entry with:

  1. weight  ← effective_weight(species, category):
                 - LM species (covered by the LegendaryMonuments mod) get
                   weight 0 — obtainable ONLY via the LM questline, never
                   a random wild spawn.
                 - otherwise: base = TIER_WEIGHTS[SPECIES_TIERS[species]]
                 - mythicals additionally floored at the Ubers weight (so
                   OU/UU+ mythicals like Mew, Jirachi, Phione can never be
                   more common than a box legend)
               (replaces upstream weight entirely — does NOT scale)
  2. bucket  ← always "ultra-rare" (legendaries/most mythicals already
               are; paradoxes + most UBs get promoted from "rare" — the
               whole point is to put them all in the shared ultra-rare
               bucket roll, which best-spawner-config.json weights at
               0.001).

The flat per-tier weighting normalises rates within a tier — Suicune
(upstream 0.5) and Articuno (upstream 2.0) both become 1.0 under UU+ — so
upstream's per-species inconsistencies don't leak through. The curve is
steep (20x AG->UU+) so game-defining legendaries feel like once-a-server
events.

USAGE / when AllTheMons version-bumps:
    1. Drop the fresh upstream AllTheMons*.zip into
       modpack/server-overrides/datapacks/ (replacing the old one).
    2. python3 ops/gen_spawn_nerfs.py
    3. Commit the patched zip + the regenerated manifest.

The script is idempotent: weights are derived from SPECIES_TIERS (by
filename), not from the current weight, so re-running on an
already-patched zip yields identical output.

A human-readable summary of every change is written to
`ops/spawn-nerf-manifest.txt` so PRs stay reviewable even though the zip
diff itself is an opaque binary blob.

If a species in the zip isn't in SPECIES_TIERS, the script emits a
WARNING and leaves that file UNTOUCHED — add a tier and re-run.
"""
from __future__ import annotations

import json
import os
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATAPACK_DIR = REPO / "modpack/server-overrides/datapacks"
MANIFEST_PATH = REPO / "ops/spawn-nerf-manifest.txt"

# Categories we process from ATM's data/special_spawns/spawn_pool_world/.
# Pseudo-legendaries intentionally NOT in scope — those spawn from
# cobblemon-base spawn pools as base forms (Dratini/Gible/Bagon/etc.)
# and are a separate balance question from "how rare should this
# legendary be".
CATEGORIES = ("legendary", "mythical", "paradox", "ultra_beast")
PATH_PREFIX = "data/special_spawns/spawn_pool_world/"

# Steep tier curve — AG (rarest) < Ubers < OU < UU+ (most common).
# 20x spread top to bottom: game-defining legendaries are once-per-server
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
    # ─── Legendaries (63) ─────────────────────────────────────────────
    # AG (3)
    "miraidon": "AG", "koraidon": "AG", "eternatus": "AG",
    # Ubers (24)
    "lugia": "Ubers", "hooh": "Ubers", "kyogre": "Ubers", "groudon": "Ubers",
    "rayquaza": "Ubers", "dialga": "Ubers", "palkia": "Ubers", "giratina": "Ubers",
    "reshiram": "Ubers", "zekrom": "Ubers", "kyurem": "Ubers", "xerneas": "Ubers",
    "yveltal": "Ubers", "zygarde": "Ubers", "necrozma": "Ubers", "zacian": "Ubers",
    "zamazenta": "Ubers", "spectrier": "Ubers", "chiyu": "Ubers", "terapagos": "Ubers",
    "calyrex": "Ubers", "cosmog": "Ubers", "kubfu": "Ubers", "chienpao": "Ubers",
    # OU (22)
    "tapukoko": "OU", "tapulele": "OU", "tapubulu": "OU", "tapufini": "OU",
    "heatran": "OU", "latios": "OU", "latias": "OU", "cresselia": "OU",
    "tornadus": "OU", "thundurus": "OU", "landorus": "OU", "regieleki": "OU",
    "regidrago": "OU", "wochien": "OU", "fezandipiti": "OU", "okidogi": "OU",
    "munkidori": "OU", "ogerpon": "OU", "enamorus": "OU", "terrakion": "OU",
    "cobalion": "OU", "virizion": "OU",
    # UU+ (14)
    "articuno": "UU+", "zapdos": "UU+", "moltres": "UU+", "entei": "UU+",
    "raikou": "UU+", "suicune": "UU+", "regice": "UU+", "regirock": "UU+",
    "registeel": "UU+", "regigigas": "UU+", "mesprit": "UU+", "uxie": "UU+",
    "azelf": "UU+", "glastrier": "UU+",

    # ─── Mythicals (21) ──────────────────────────────────────────────
    # AG (2): banned-everywhere mythicals
    "arceus": "AG", "deoxys": "AG",
    # Ubers (6)
    "darkrai": "Ubers", "hoopa": "Ubers", "magearna": "Ubers",
    "marshadow": "Ubers", "shaymin": "Ubers", "victini": "Ubers",
    # OU (9)
    "celebi": "OU", "diancie": "OU", "jirachi": "OU", "keldeo": "OU",
    "manaphy": "OU", "mew": "OU", "meltan": "OU", "pecharunt": "OU",
    "volcanion": "OU",
    # UU+ (4)
    "phione": "UU+", "zarude": "UU+", "zeraora": "UU+", "meloetta": "UU+",

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


def find_atm_zip() -> Path:
    """Locate the AllTheMons zip without hardcoding a version string."""
    matches = sorted(DATAPACK_DIR.glob("AllTheMons*.zip"))
    if not matches:
        raise SystemExit(f"No AllTheMons*.zip found in {DATAPACK_DIR}")
    if len(matches) > 1:
        raise SystemExit(
            f"Multiple AllTheMons*.zip found (expected one): "
            f"{[m.name for m in matches]}"
        )
    return matches[0]


def effective(species: str, category: str) -> tuple[float, str]:
    """Return (weight, tier_note) for an in-scope species."""
    tier = SPECIES_TIERS[species]
    if species in LM_SPECIES:
        return 0.0, f"{tier}->LM-suppressed"
    base = TIER_WEIGHTS[tier]
    if category == "mythical" and base > TIER_WEIGHTS[MYTHICAL_FLOOR_TIER]:
        return TIER_WEIGHTS[MYTHICAL_FLOOR_TIER], f"{tier}->{MYTHICAL_FLOOR_TIER}-floor"
    return base, tier


def category_of(name: str) -> str | None:
    """Return the in-scope category for an archive path, or None."""
    if not name.startswith(PATH_PREFIX) or not name.endswith(".json"):
        return None
    rel = name[len(PATH_PREFIX):]
    cat = rel.split("/", 1)[0]
    return cat if cat in CATEGORIES else None


def main() -> None:
    atm = find_atm_zip()
    tmp = atm.with_suffix(".zip.tmp")

    manifest: list[tuple[str, str, str, float, str]] = []  # cat, species, tier_note, weight, lm
    unmapped: list[str] = []
    seen_species: set[str] = set()
    by_tier: dict[str, int] = {t: 0 for t in TIER_WEIGHTS}
    suppressed = 0
    patched = 0

    with zipfile.ZipFile(atm) as zin, \
         zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith(".json"):
                seen_species.add(Path(item.filename).stem)
            cat = category_of(item.filename)
            if cat is not None:
                species = Path(item.filename).stem
                if species not in SPECIES_TIERS:
                    unmapped.append(f"{cat}/{species}")
                    # leave the file byte-identical — do NOT silently nerf
                    zout.writestr(item, data)
                    continue
                weight, tier_note = effective(species, cat)
                doc = json.loads(data.decode("utf-8"))
                for entry in doc.get("spawns", []):
                    entry["weight"] = weight
                    entry["bucket"] = "ultra-rare"
                doc["_comment"] = (
                    f"Spawn rate baked by ops/gen_spawn_nerfs.py — "
                    f"tier={tier_note} weight={weight} bucket=ultra-rare"
                )
                out = (json.dumps(doc, indent=2) + "\n").encode("utf-8")
                zout.writestr(item, out)
                patched += 1
                by_tier[SPECIES_TIERS[species]] += 1
                is_lm = species in LM_SPECIES
                suppressed += is_lm
                manifest.append((cat, species, tier_note, weight, "LM" if is_lm else ""))
            else:
                zout.writestr(item, data)

    os.replace(tmp, atm)

    # ── Manifest (reviewable diff surface for the opaque zip) ──────────
    lines = [
        f"# Spawn-nerf manifest — generated by ops/gen_spawn_nerfs.py",
        f"# Source zip: {atm.name}",
        f"# {patched} files patched, bucket=ultra-rare, {suppressed} LM-suppressed (weight=0)",
        f"# Tier weights: {TIER_WEIGHTS}",
        "",
        f"{'CATEGORY':<12}{'SPECIES':<16}{'TIER':<22}{'WEIGHT':>8}  LM",
    ]
    for cat, species, tier_note, weight, lm in sorted(manifest):
        lines.append(f"{cat:<12}{species:<16}{tier_note:<22}{weight:>8}  {lm}")
    MANIFEST_PATH.write_text("\n".join(lines) + "\n")

    print(f"Patched {patched} files in {atm.name}; manifest -> {MANIFEST_PATH.name}")
    print(f"By tier: {by_tier}  |  LM-suppressed: {suppressed}")

    lm_unused = sorted(LM_SPECIES - seen_species)
    if lm_unused:
        print(f"\nNote: {len(lm_unused)} LM species have no ATM spawn pool "
              f"(no patch needed): {lm_unused}")
    if unmapped:
        print(f"\nWARNING: {len(unmapped)} species in ATM not in SPECIES_TIERS "
              f"— left UNTOUCHED (still upstream weight). Add a tier and re-run:")
        for u in sorted(unmapped):
            print(f"  {u}")


if __name__ == "__main__":
    main()
