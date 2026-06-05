#!/usr/bin/env python3
"""Generate L50 hard-mode (_challenge) gym teams from ShepskyDad's type teams.

Source: https://arynlight.fandom.com/wiki/ShepskyDad%27s_Gym_Leaders
("Perfect Gym Leaders" — one curated L50 team per type: species, ability,
held item, 4 moves; no natures/EVs, so spreads are derived heuristically
from move categories — see _spread()).

Usage:
    # 1. (once / on wiki updates) parse the wiki API dump into vendored data:
    curl -s -A "Mozilla/5.0" "https://arynlight.fandom.com/api.php?action=parse&page=ShepskyDad%27s_Gym_Leaders&format=json&prop=wikitext" -o /tmp/gym_wiki.json
    python3 ops/gen_challenge_teams.py --parse /tmp/gym_wiki.json

    # 2. generate gym_NN_*_challenge.json for every type-identity gym:
    python3 ops/gen_challenge_teams.py

Validation against foul-play's pokedex/move data (catches typos and
Cobblemon-unknown content):
    PYTHONPATH=reference/foul-play reference/foul-play/.venv/bin/python \
        ops/gen_challenge_teams.py --validate
"""

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
GYMS = REPO / "modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers"
DATA = REPO / "ops/data/shepskydad_teams.json"

TYPE_SECTIONS = [
    "Normal", "Bug", "Ice", "Poison", "Grass", "Flying", "Ghost", "Electric",
    "Rock", "Fire", "Fighting", "Psychic", "Dark", "Ground", "Water", "Steel",
    "Fairy", "Dragon",
]

FORM_ASPECTS = {
    "hisuian": "hisuian",
    "galarian": "galarian",
    "alolan": "alolan",
    "paldean": "paldean",
}

# wiki typos -> real species ids
SPECIES_FIXES = {
    "amoongus": "amoonguss",
}


def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def item_id(s: str) -> str:
    return "cobblemon:" + re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


def parse_wiki(path: str) -> dict:
    w = json.load(open(path))["parse"]["wikitext"]["*"]
    teams = {}
    # exactly-h3 sections, sliced to the next h2/h3 heading
    for m in re.finditer(r"\n=== ([^=\n]+?) ===\n", w):
        name = m.group(1).strip()
        if name not in TYPE_SECTIONS:
            continue
        end = re.search(r"\n===? [^=\n]+? ===?\n", w[m.end():])
        body = w[m.end() : m.end() + end.start()] if end else w[m.end():]
        # Flying/Psychic carry h4 sub-teams (Team #1.., Battle #1/#2-doubles);
        # use only the first (singles) sub-team
        subs = list(re.finditer(r"\n==== [^=\n]+? ====\n", body))
        if subs:
            sub_end = subs[1].start() if len(subs) > 1 else len(body)
            body = body[subs[0].end() : sub_end]
        mons = []
        for pm in re.finditer(r"\{\{Pokémon(.*?)\n\}\}", body, re.S):
            fields = dict(
                re.findall(r"\| *([a-z0-9 ]+?) *= *([^|\n]*?) *(?=\||\n)", pm.group(1))
            )
            if not fields.get("pokemon"):
                continue
            moves, move_cats = [], []
            for i in range(1, 5):
                mv = fields.get(f"move{i}", "").strip()
                if mv:
                    moves.append(mv)
                    move_cats.append(fields.get(f"move{i}category", "").strip())
            mons.append(
                {
                    "species": fields["pokemon"].strip(),
                    "form": fields.get("form1", "").strip(),
                    "ability": fields.get("ability", "").strip(),
                    "held_item": fields.get("held item", "").strip(),
                    "moves": moves,
                    "move_categories": move_cats,
                }
            )
        assert 6 <= len(mons) <= 7, f"{name}: expected 6-7 mons, got {len(mons)}"
        if len(mons) > 6:
            print(f"note: {name} lists {len(mons)} mons — keeping the first 6 "
                  f"(dropping {mons[6]['species']})")
        teams[name] = mons[:6]
    missing = [t for t in TYPE_SECTIONS if t not in teams]
    assert not missing, f"missing type sections: {missing}"
    return teams


def _spread(mon: dict) -> tuple[str, dict]:
    """Heuristic competitive spread from move categories.

    - mostly-status mons: bulky support — fast leads (Focus Sash) stay speedy
    - physical attackers: jolly 252 Atk / 252 Spe
    - special attackers: timid 252 SpA / 252 Spe
    - mixed: naive, split between the dominant category and speed
    """
    cats = mon["move_categories"]
    phys = sum(c == "physical" for c in cats)
    spec = sum(c == "special" for c in cats)
    status = sum(c == "status" for c in cats)

    if status >= 2 and phys + spec <= 2:
        if norm(mon["held_item"]) == "focussash":
            nature = "jolly" if phys >= spec else "timid"
            evs = {"hp": 4, "atk": 0, "def": 0, "spa": 0, "spd": 0, "spe": 252}
            evs["atk" if phys >= spec else "spa"] = 252
            return nature, evs
        return "bold" if phys >= spec else "calm", {
            "hp": 252, "atk": 0, "def": 128, "spa": 0, "spd": 128, "spe": 0,
        }
    if phys > spec:
        return "jolly", {"hp": 0, "atk": 252, "def": 0, "spa": 0, "spd": 4, "spe": 252}
    if spec > phys:
        return "timid", {"hp": 0, "atk": 0, "def": 0, "spa": 252, "spd": 4, "spe": 252}
    return "naive", {"hp": 0, "atk": 128, "def": 0, "spa": 128, "spd": 0, "spe": 252}


def to_rct_mon(mon: dict) -> dict:
    nature, evs = _spread(mon)
    # form can come as a field (form1=hisuian) or a species prefix ("Alolan Muk")
    species, form = mon["species"].strip(), mon.get("form", "").lower()
    prefix_match = re.match(r"(alolan|galarian|hisuian|paldean)\s+(.+)", species, re.I)
    if prefix_match:
        form, species = prefix_match.group(1).lower(), prefix_match.group(2)
    out = {
        "species": SPECIES_FIXES.get(norm(species), norm(species)),
        "level": 50,
        "nature": nature,
        "ivs": {k: 31 for k in ("hp", "atk", "def", "spa", "spd", "spe")},
        "evs": evs,
        "ability": norm(mon["ability"]),
        "moveset": [norm(m) for m in mon["moves"]],
    }
    if mon["held_item"]:
        out["heldItem"] = item_id(mon["held_item"])
    if form in FORM_ASPECTS:
        out["aspects"] = [FORM_ASPECTS[form]]
    return out


def generate() -> None:
    teams = json.loads(DATA.read_text())
    written = 0
    for base_path in sorted(GYMS.glob("gym_*.json")):
        if base_path.stem.endswith("_challenge"):
            continue
        base = json.loads(base_path.read_text())
        identity = base.get("identity")
        if identity not in teams:
            continue  # custom-identity gyms (E4, champion) keep their teams
        challenge = dict(base)
        challenge["team"] = [to_rct_mon(m) for m in teams[identity]]
        out = base_path.with_name(f"{base_path.stem}_challenge.json")
        out.write_text(json.dumps(challenge, indent=2, ensure_ascii=False) + "\n")
        written += 1
    print(f"wrote {written} challenge teams")


def validate() -> None:
    from data import all_move_json, pokedex  # foul-play, via PYTHONPATH

    teams = json.loads(DATA.read_text())
    problems = []
    for type_name, mons in teams.items():
        for mon in mons:
            rct = to_rct_mon(mon)
            species = rct["species"] + (
                rct["aspects"][0].replace("ian", "") if "aspects" in rct else ""
            )
            if rct["species"] not in pokedex and species not in pokedex:
                problems.append(f"{type_name}: unknown species {rct['species']}")
            for mv in rct["moveset"]:
                if mv not in all_move_json:
                    problems.append(f"{type_name}/{rct['species']}: unknown move {mv}")
    print("\n".join(problems) if problems else "all species and moves known")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--parse", metavar="WIKI_JSON")
    ap.add_argument("--validate", action="store_true")
    args = ap.parse_args()
    if args.parse:
        DATA.parent.mkdir(parents=True, exist_ok=True)
        DATA.write_text(json.dumps(parse_wiki(args.parse), indent=1) + "\n")
        print(f"vendored {DATA}")
    elif args.validate:
        validate()
    else:
        generate()
