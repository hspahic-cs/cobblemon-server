#!/usr/bin/env python3
"""Generate the server-zygarde-power-construct datapack.

WHY: three packs ship data/cobblemon/species/generation6/zygarde.json — base
Cobblemon, Mega Showdown, and the AllTheMons datapack. World datapacks load
after mod resources, so AllTheMons wins, and its version:

  * rewrites the Complete form's abilities to ["aurabreak", "h:aurabreak"], and
  * drops the "10%-C" / "50%-C" forms (the ones carrying the "power-construct"
    aspect) entirely.

Net effect: no Zygarde form on the server grants Power Construct. Assembling a
Complete Zygarde with the Mega Showdown cube (5 cores + 95 cells) sets the
aspect fine, but Cobblemon re-resolves the ability from form.abilities on the
form change (Pokemon.attemptAbilityUpdate) and lands on Aura Break.

AllTheMons also overrides data/cobblemon/species_features/percent_cells.json,
adding its own "1" choice but dropping Mega Showdown's "core" choice — which
ZygardeCube still writes (percent_cells=core), so the cube's Core state breaks
too.

This script re-derives BOTH files from the AllTheMons copies (so ATM's stats,
scales, hitboxes, movesets and slime-block evolution chain are preserved
verbatim) and re-applies only the Power Construct pieces:

  1. Complete form abilities -> powerconstruct.
  2. Re-add "10%-C" and "50%-C", cloned from ATM's own 10% form and 50% root so
     they keep ATM's models/stats, with the "power-construct" aspect and the
     powerconstruct ability.
  3. Re-add Mega Showdown's battle-only "Core" form and the "core" choice.

Re-run after every AllTheMons bump (the zip is version-named, so it re-enables
and clobbers these paths again). Also re-run the datapack ordering fix — this
pack must load AFTER AllTheMons:

    /datapack enable "file/server-zygarde-power-construct" last

Usage: ops/gen_zygarde_power_construct.py [--check]
"""

from __future__ import annotations

import argparse
import copy
import json
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATAPACKS = REPO / "modpack" / "server-overrides" / "datapacks"
OUT = DATAPACKS / "server-zygarde-power-construct"

SPECIES_PATH = "data/cobblemon/species/generation6/zygarde.json"
FEATURE_PATH = "data/cobblemon/species_features/percent_cells.json"
ASSIGNMENT_PATH = "data/cobblemon/species_feature_assignments/zygarde_power_construct.json"

# Base Cobblemon and Mega Showdown BOTH ship ASSIGNMENT_PATH, and they disagree:
# Cobblemon assigns the feature "power_construct" (underscore, registered in its own
# species_features/power_construct.json), Mega Showdown assigns "power-construct"
# (hyphen, registered in species_features/power-construct.json). They're distinct
# registry keys, so only one of them ends up attached to Zygarde and which one is
# down to mod load order. The forms match the hyphen aspect and ZygardeCube writes
# the hyphen aspect, so pin the hyphen here — a datapack beats every mod, which
# takes load order out of the picture.
ASSIGNMENT = {"pokemon": ["zygarde"], "features": ["power-construct"]}

POWER_CONSTRUCT = ["powerconstruct", "h:powerconstruct"]

# Verbatim from mega_showdown's zygarde.json. Battle-only holding form the
# Zygarde Cube swaps a Pokemon into; AllTheMons has no equivalent.
CORE_FORM = {
    "name": "Core",
    "baseScale": 0.5,
    "hitbox": {"width": 1, "height": 1, "fixed": False},
    "aspects": ["core-percent"],
    "baseStats": {
        "hp": 1,
        "attack": 1,
        "defence": 1,
        "special_attack": 1,
        "special_defence": 1,
        "speed": 1,
    },
    "moves": ["1:bulldoze"],
    "evolutions": [],
    "battleOnly": True,
}

PACK_MCMETA = {
    "pack": {
        "pack_format": 48,
        "description": (
            "Restores Zygarde's Power Construct. The AllTheMons datapack overrides "
            "cobblemon:species/generation6/zygarde.json and wins over both base Cobblemon "
            "and Mega Showdown (world datapacks load after mod resources); its copy gives the "
            "Complete form Aura Break and deletes the 10%-C / 50%-C forms, so nothing on the "
            "server granted Power Construct and the Mega Showdown cube produced an Aura Break "
            "Zygarde. This pack re-derives AllTheMons' own file (keeping its stats, scales, "
            "movesets and slime-block evolution chain) and re-adds the Power Construct forms, "
            "plus Mega Showdown's Core form and the 'core' percent_cells choice the cube needs. "
            "Also pins the zygarde power-construct feature assignment, which Cobblemon and Mega "
            "Showdown ship conflicting copies of (power_construct vs power-construct). "
            "MUST load after AllTheMons. Regenerate with ops/gen_zygarde_power_construct.py."
        ),
    }
}


def find_atm_zip() -> Path:
    zips = sorted(DATAPACKS.glob("AllTheMons*.zip"))
    if not zips:
        sys.exit(f"gen_zygarde_power_construct: no AllTheMons*.zip in {DATAPACKS}")
    if len(zips) > 1:
        sys.exit(
            "gen_zygarde_power_construct: multiple AllTheMons zips, "
            f"prune stale ones first: {[z.name for z in zips]}"
        )
    return zips[0]


def build(atm_zip: Path) -> dict[str, dict]:
    with zipfile.ZipFile(atm_zip) as z:
        species = json.loads(z.read(SPECIES_PATH))
        feature = json.loads(z.read(FEATURE_PATH))

    forms = species.get("forms", [])
    by_name = {f.get("name"): f for f in forms}

    complete = by_name.get("Complete")
    if complete is None:
        sys.exit("gen_zygarde_power_construct: AllTheMons zygarde.json has no Complete form")

    # 1. Complete gets its real ability back.
    complete["abilities"] = list(POWER_CONSTRUCT)

    # 2. Rebuild the power-construct variants from ATM's own forms so they keep
    #    ATM's models/stats rather than reintroducing Mega Showdown's geometry.
    #    Their evolutions are cleared: a Power Construct Zygarde came from the
    #    cube, and letting the slime-block chain re-roll it would strip the
    #    ability again.
    ten = by_name.get("10")
    if ten is None:
        sys.exit("gen_zygarde_power_construct: AllTheMons zygarde.json has no 10% form")

    ten_c = copy.deepcopy(ten)
    ten_c["name"] = "10%-C"
    ten_c["aspects"] = ["power-construct", "10-percent"]
    ten_c["abilities"] = list(POWER_CONSTRUCT)
    ten_c["evolutions"] = []
    ten_c["battleOnly"] = False

    # The 50% form is the species root, so clone the root's form-shaped fields.
    root_form_keys = (
        "primaryType",
        "secondaryType",
        "maleRatio",
        "height",
        "weight",
        "baseScale",
        "hitbox",
        "pokedex",
        "labels",
        "baseStats",
        "behaviour",
        "baseExperienceYield",
        "moves",
    )
    fifty_c = {k: copy.deepcopy(species[k]) for k in root_form_keys if k in species}
    fifty_c["name"] = "50%-C"
    fifty_c["aspects"] = ["50-percent", "power-construct"]
    fifty_c["abilities"] = list(POWER_CONSTRUCT)
    fifty_c["evolutions"] = []
    fifty_c["battleOnly"] = False

    # 3. Mega Showdown's Core form, which ATM dropped.
    core = copy.deepcopy(CORE_FORM)

    species["forms"] = forms + [ten_c, fifty_c, core]

    # percent_cells: keep ATM's choices, re-add the "core" state ZygardeCube writes.
    choices = list(feature.get("choices", []))
    if "core" not in choices:
        choices.append("core")
    feature["choices"] = choices

    return {
        SPECIES_PATH: species,
        FEATURE_PATH: feature,
        ASSIGNMENT_PATH: copy.deepcopy(ASSIGNMENT),
    }


def render(files: dict[str, dict]) -> dict[Path, str]:
    out = {OUT / "pack.mcmeta": json.dumps(PACK_MCMETA, indent=2) + "\n"}
    for rel, data in files.items():
        out[OUT / rel] = json.dumps(data, indent=2) + "\n"
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--check",
        action="store_true",
        help="exit non-zero if the committed datapack differs from what would be generated",
    )
    args = ap.parse_args()

    rendered = render(build(find_atm_zip()))

    if args.check:
        stale = [
            p for p, text in rendered.items()
            if not p.exists() or p.read_text() != text
        ]
        if stale:
            for p in stale:
                print(f"stale: {p.relative_to(REPO)}", file=sys.stderr)
            return 1
        print("server-zygarde-power-construct is up to date")
        return 0

    for path, text in rendered.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        print(f"wrote {path.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
