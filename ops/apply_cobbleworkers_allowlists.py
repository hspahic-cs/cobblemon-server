#!/usr/bin/env python3
"""
Generate the `server-cobbleworkers-allowlists` datapack: for each user-specified job,
write an override of `data/cobbleworkers/jobs/<id>.json` that swaps the upstream
type/move-based `requirements` block for a species allowlist.

Each override preserves the upstream `id`, `cooldown`, `cooldown_scope`, `components`,
and any upstream `conditions` (e.g. IN_WATER for fishing/extinguisher). Only the
`types`/`moves`/`abilities` parts of `requirements` are replaced; `conditions` carry over.

Regional-form notes: user spec uses "alolan_ninetales" / "galarian_darmanitan" etc.
Cobblemon models regional forms via the `aspects` mechanism, not as separate species ids,
and Cobbleworkers' Requirements AND-s species + aspects. We can't say "regular Ninetales
OR Alolan Ninetales" in one species list with form-specific rules, so we collapse to the
base species name and accept that BOTH forms qualify. If you later want strict form-only
matching, split the job into multiple definitions or add aspect-aware requirement logic.

Run from repo root:
    python3 ops/apply_cobbleworkers_allowlists.py
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
COBBLEWORKERS_JAR = "/tmp/cobbleworkers.jar"
DATAPACK_ROOT = REPO / "modpack/server-overrides/datapacks/server-cobbleworkers-allowlists/data/cobbleworkers/jobs"

# user_key → cobbleworkers job id(s) — most are 1:1; tumblestone applies to all 3 variants.
# Treasure-generator jobs disabled in 0.7.14 — these produce random loot from tables with
# no real-world block to harvest (vs. crops/berries/tumblestones which use BLOCK_BREAK on
# an actual block). Want to revive any of these later? Drop it from this set and re-run.
# `honey_collection` is intentionally NOT here: it's loot-table-driven but tied to real
# beehives in the world, so the activity is grounded.
DISABLED_JOBS: set[str] = {
    "archaeologist",
    "dive_looter",
    "fishing_looter",
    "pickup_looter",
}

JOB_ALIASES: dict[str, list[str]] = {
    "crop_harvester":        ["crops_harvester"],
    "berry_harvester":       ["berry_harvester"],
    "crop_irrigator":        ["irrigator"],
    "fire_extinguisher":     ["extinguisher"],
    "water_generator":       ["water_generator"],
    "fishing":               ["fishing_looter"],
    "lava_generator":        ["lava_generator"],
    "fuel_generator":        ["fuel_generator"],
    "snow_generator":        ["powder_snow_generator"],
    "mint_harvester":        ["mint_harvester"],
    "ground_item_gatherer":  ["item_gatherer"],
    "fletcher":              ["fletcher"],
    "netherwart_harvester":  ["netherwart_harvester"],
    "archeologist":          ["archaeologist"],  # user typo, mod uses correct spelling
    "tumblestone_harvester": ["tumblestone_harvester", "black_tumblestone_harvester", "sky_tumblestone_harvester"],
    "apricorn_harvester":    ["apricorn_harvester"],
    "amethyst_harvester":    ["amethyst_harvester"],
    "brewing_stand_fuel":    ["brewing_stand_fuel_generator"],
}

ALLOWLISTS: dict[str, list[str]] = {
    "crop_harvester":        ["leafeon", "bellossom", "sunflora", "lilligant", "vileplume", "victreebel", "exeggutor", "roserade", "simisage", "whimsicott"],
    "berry_harvester":       ["leafeon", "bellossom", "sunflora", "lilligant", "vileplume", "victreebel", "exeggutor", "roserade", "simisage", "whimsicott"],
    "crop_irrigator":        ["vaporeon", "starmie", "cloyster", "poliwrath", "ludicolo", "simipour", "politoed", "slowking", "kingdra"],
    "fire_extinguisher":     ["vaporeon", "starmie", "cloyster", "poliwrath", "ludicolo", "simipour", "politoed", "slowking", "kingdra"],
    "water_generator":       ["vaporeon", "starmie", "cloyster", "poliwrath", "ludicolo", "simipour", "politoed", "slowking", "kingdra"],
    "fishing":               ["vaporeon", "starmie", "cloyster", "poliwrath", "ludicolo", "simipour", "politoed", "slowking", "kingdra"],
    "lava_generator":        ["flareon", "arcanine", "ninetales", "simisear", "magmortar"],
    "fuel_generator":        ["flareon", "arcanine", "ninetales", "simisear", "magmortar"],
    "snow_generator":        ["glaceon", "cetitan", "froslass", "cloyster", "ninetales", "sandslash", "darmanitan"],  # alolan_*, galarian_* collapsed to base species
    "mint_harvester":        ["clefable", "wigglytuff", "togekiss", "florges", "whimsicott", "slurpuff", "aromatisse", "ninetales"],  # alolan_ninetales → ninetales
    "ground_item_gatherer":  ["starmie", "exeggutor", "gallade", "musharna", "slowking"],
    "fletcher":              ["vileplume", "victreebel", "roserade", "nidoking", "nidoqueen"],
    "netherwart_harvester":  ["mismagius", "chandelure", "aegislash", "froslass", "dusknoir", "polteageist"],
    "archeologist":          ["nidoking", "nidoqueen", "rhyperior", "steelix", "gliscor"],
    "tumblestone_harvester": ["aegislash", "sandslash", "scizor", "steelix"],  # alolan_sandslash → sandslash
    "apricorn_harvester":    ["vikavolt", "scizor"],
    "amethyst_harvester":    ["rhyperior"],
    "brewing_stand_fuel":    ["kingdra"],
}


def read_upstream(job_id: str) -> dict:
    """Read the upstream `data/cobbleworkers/jobs/<job_id>.json` from the mod jar."""
    raw = subprocess.check_output(
        ["unzip", "-p", COBBLEWORKERS_JAR, f"data/cobbleworkers/jobs/{job_id}.json"],
        stderr=subprocess.DEVNULL,
    )
    return json.loads(raw)


def build_override(upstream: dict, species: list[str]) -> dict:
    """Take the upstream job JSON and return a copy with `requirements` replaced by
    a species allowlist. Preserves `conditions` if present (e.g., IN_WATER)."""
    out = dict(upstream)  # shallow copy
    new_requirements: dict = {"species": list(species)}
    upstream_conditions = upstream.get("requirements", {}).get("conditions")
    if upstream_conditions:
        new_requirements["conditions"] = upstream_conditions
    out["requirements"] = new_requirements
    return out


def build_disabled(upstream: dict) -> dict:
    """Return a job override that effectively disables the job: an impossible species
    requirement plus an empty components list (belt-and-suspenders — even if the
    upstream Requirements logic treats an unknown species as "match-all", the empty
    components ensure nothing actually happens)."""
    return {
        "id": upstream["id"],
        "cooldown": upstream.get("cooldown", 0),
        "cooldown_scope": upstream.get("cooldown_scope", "PER_ENTITY"),
        "requirements": {"species": ["__disabled__"]},
        "components": [],
    }


def main() -> int:
    if not Path(COBBLEWORKERS_JAR).exists():
        print(f"ERROR: {COBBLEWORKERS_JAR} not found. Copy from /opt/cobblemon-dev/mods/ first:")
        print("  scp cobblemon:/opt/cobblemon-dev/mods/cobbleworkers-neoforge-*.jar /tmp/cobbleworkers.jar")
        return 1

    DATAPACK_ROOT.mkdir(parents=True, exist_ok=True)
    written = 0
    for user_key, species in ALLOWLISTS.items():
        job_ids = JOB_ALIASES[user_key]
        for jid in job_ids:
            upstream = read_upstream(jid)
            override = build_override(upstream, species)
            out_path = DATAPACK_ROOT / f"{jid}.json"
            out_path.write_text(json.dumps(override, indent=2) + "\n")
            written += 1
            print(f"  wrote {jid:38s} ({len(species)} species)")
    for jid in sorted(DISABLED_JOBS):
        upstream = read_upstream(jid)
        override = build_disabled(upstream)
        out_path = DATAPACK_ROOT / f"{jid}.json"
        out_path.write_text(json.dumps(override, indent=2) + "\n")
        written += 1
        print(f"  wrote {jid:38s} (DISABLED — treasure generator)")
    print(f"\nWrote {written} job overrides → {DATAPACK_ROOT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
