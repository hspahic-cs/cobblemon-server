#!/usr/bin/env python3
"""Convert a gym-leader trainer JSON to Smogon paste format for foul-play.

Usage:
    python3 ops/gym_to_showdown_paste.py <input.json> [--output <path>] [--level N]

Default output goes to reference/foul-play/teams/teams/gen9/gym/<basename>.

If --level is given, every pokemon's level is overwritten with that value
(useful since Showdown's gen9customgame doesn't enforce a level cap, and we
may want to test at L100 instead of L50). Otherwise the source levels are kept.
"""

import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_OUT_DIR = REPO / "reference/foul-play/teams/teams/gen9/gym"

# Cobblemon `form` field → Smogon paste suffix
FORM_SUFFIX = {
    "alolan": "-Alola",
    "hisuian": "-Hisui",
    "galarian": "-Galar",
    "paldean": "-Paldea",
    "wash": "-Wash",
    "heat": "-Heat",
    "fan": "-Fan",
    "frost": "-Frost",
    "mow": "-Mow",
    "midday": "",          # Lycanroc-Midday is the default form on Showdown
    "midnight": "-Midnight",
    "dusk": "-Dusk",
    "pom-pom": "-Pom-Pom",
    "baile": "",
    "sensu": "-Sensu",
    "blue": "-Blue",       # Florges-Blue
    "mega": "-Mega",
}


def cap(s):
    """Title-case species/move/ability names: 'futuresight' -> 'Future Sight'."""
    # Showdown is permissive: it'll resolve 'futuresight' too, but human-readable
    # output reads better. Keep simple — split known compound moves only.
    return " ".join(w.capitalize() for w in _split_words(s))


def _split_words(s):
    # Heuristic: split camelCase / lowercase-runs at known word boundaries.
    # We won't perfect this — Showdown parser tolerates either form.
    return [s]


def species_label(species: str, form: str | None) -> str:
    """e.g. ('electrode', 'hisuian') -> 'Electrode-Hisui'."""
    base = species.capitalize()
    if not form:
        return base
    suffix = FORM_SUFFIX.get(form.lower())
    if suffix is None:
        # Unknown form — pass through as -Form (Showdown may or may not accept).
        return f"{base}-{form.capitalize()}"
    return f"{base}{suffix}"


def item_label(item: str | None) -> str:
    if not item:
        return ""
    # 'cobblemon:heavy_duty_boots' -> 'Heavy Duty Boots'
    raw = item.split(":")[-1].replace("_", " ")
    return " ".join(w.capitalize() for w in raw.split())


def ability_label(ability: str) -> str:
    # 'magicguard' -> 'Magic Guard' is hard without a dictionary; Showdown
    # parses lowercased forms fine, but we'll title-case for readability.
    return ability.capitalize()


def move_label(move: str) -> str:
    return move.capitalize()


def nature_label(nature: str) -> str:
    return nature.capitalize()


def emit_pokemon(mon: dict, level_override: int | None) -> str:
    species = species_label(mon["species"], mon.get("form"))
    item = item_label(mon.get("heldItem"))
    ability = ability_label(mon.get("ability", ""))
    nature = nature_label(mon.get("nature", "Hardy"))
    level = level_override if level_override is not None else mon.get("level", 100)

    evs = mon.get("evs", {})
    ev_parts = []
    for k, label in [("hp", "HP"), ("atk", "Atk"), ("def", "Def"),
                     ("spa", "SpA"), ("spd", "SpD"), ("spe", "Spe")]:
        v = evs.get(k, 0) or 0
        if v:
            ev_parts.append(f"{v} {label}")

    ivs = mon.get("ivs", {})
    iv_parts = []
    for k, label in [("hp", "HP"), ("atk", "Atk"), ("def", "Def"),
                     ("spa", "SpA"), ("spd", "SpD"), ("spe", "Spe")]:
        v = ivs.get(k, 31)
        if v != 31:
            iv_parts.append(f"{v} {label}")

    moves = mon.get("moveset", [])
    gender = mon.get("gender", "")

    line1 = species
    if gender in ("MALE", "FEMALE"):
        line1 += f" ({'M' if gender == 'MALE' else 'F'})"
    if item:
        line1 += f" @ {item}"

    out = [line1, f"Ability: {ability}"]
    if level != 100:
        out.append(f"Level: {level}")
    if ev_parts:
        out.append(f"EVs: {' / '.join(ev_parts)}")
    out.append(f"{nature} Nature")
    if iv_parts:
        out.append(f"IVs: {' / '.join(iv_parts)}")
    for m in moves:
        out.append(f"- {move_label(m)}")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="Path to gym trainer JSON")
    ap.add_argument("--output", help="Output path (default: reference/foul-play/teams/teams/gen9/gym/<basename>)")
    ap.add_argument("--level", type=int, help="Override every pokemon's level (e.g., 100)")
    args = ap.parse_args()

    src = Path(args.input)
    data = json.loads(src.read_text())
    team_text = "\n\n".join(emit_pokemon(m, args.level) for m in data.get("team", []))

    if args.output:
        out = Path(args.output)
    else:
        DEFAULT_OUT_DIR.mkdir(parents=True, exist_ok=True)
        out = DEFAULT_OUT_DIR / src.stem  # foul-play loader treats files without extension fine

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(team_text + "\n")
    print(f"wrote {out.relative_to(REPO) if out.is_relative_to(REPO) else out}")
    print(f"  {len(data.get('team', []))} pokemon")
    if args.level:
        print(f"  level override: {args.level}")


if __name__ == "__main__":
    main()
