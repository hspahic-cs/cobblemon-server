#!/usr/bin/env python3
"""Generate the client resource pack that gives custom trainers their skins.

RCT resolves trainer skins CLIENT-side by filename:
    assets/rctmod/textures/trainers/single/<trainerId>.png
(then groups/<group>.png, then default.png). Custom datapack trainers
(server-gyms, aitest) have IDs with no matching texture, so everyone renders
as the default skin. The `textureResource` field in our trainer JSONs is the
intended skin — RCT ignores it, but we use it as the mapping table: copy each
referenced built-in texture out of the rctmod jar under our trainer's ID.

    python3 ops/gen_trainer_texture_pack.py [path/to/rctmod.jar] [--roster roster.json]

Re-run after adding trainers or changing textureResource values. Output:
modpack/resourcepacks/rct-server-trainers/ (folder resource pack).

ROGUELITE TRAINERS (ROGUELITE_SKINS, below) are handled differently, because their
trainer JSONs do not exist yet — the roguelite roster names ids (`rgl_brock`, …) that
nothing defines. Their skins are therefore driven by an explicit table here rather than
by a `textureResource` field. Pass `--roster <generated roster.json>` to cross-check the
table's ids against the roster: an id in one and not the other is reported, never guessed.
"""

import argparse
import json
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TRAINER_DIRS = [
    REPO / "modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers",
    REPO / "modpack/server-overrides/datapacks/server-gym-ai-test/data/rctmod/trainers",
    REPO / "modpack/server-overrides/datapacks/server-trainer-spawns/data/rctmod/trainers",
]

# The hl_* high-level wild trainers (gen_highlevel_trainers.py) declare a
# `textureResource` of type_<x>.png — a skin RCT does NOT ship (it only has
# per-character textures + default.png), so those trainers rendered as the default
# skin. Map each invented type_<x> skin to a representative real RCT texture of that
# trainer class so they get a class-appropriate skin instead of the default.
TYPE_FALLBACK = {
    "type_flying": "bird_keeper_alexandra_0326",
    "type_bug": "bug_catcher_anthony_0213",
    "type_rock": "hiker_alan_00b9",
    "type_water": "fisherman_andrew_00e9",
    "type_fighting": "black_belt_aaron_0140",
    "type_psychic": "psychic_abigail_0345",
    "type_ghost": "pokemaniac_ashton_00a8",
    "type_normal": "ace_trainer_abel_04a5",
}
# Ships inside the cobblemon-npc mod jar — that mod goes to BOTH sides
# (cobblemon-poke-ai is in CI's SERVER_ONLY list and never reaches clients).
# Mirrors how RCT's own built-ins work: the client needs the trainer DATA
# (data/rctmod/trainers/<id>.json) to bind a texture to a trainer id, plus
# the texture itself (assets/rctmod/textures/trainers/single/<id>.png).
# Server-side the world datapack overrides the jar's data copies.
PACK_DIR = REPO / "custom-mods/cobblemon-npc/src/main/resources"


# ------------------------------------------------------------------ roguelite
#
# PokéRogue-mode's named characters, mapped onto skins RCT ALREADY SHIPS.
#
# THE MATCHING RULE, and why it is not a filename search:
#   1. RCT's own trainer definition is the evidence, not the texture filename. Every
#      texture `X.png` has a sibling `data/rctmod/trainers/X.json` whose `name` /
#      `identity` fields say who it is ("Brock" / "Leader Brock"). A match is only
#      accepted when that name equals the character AND the file's class prefix is one
#      of RCT's named-character classes: leader_, gym_leader_, elite_four_, champion_,
#      boss_, rival_, title_defense_.
#   2. Substring matches are rejected by construction. RCT ships plenty of ordinary
#      NPCs who happen to share a name with a leader — `fisher_juan`, `bird_keeper_marlon`,
#      `veteran_grant`, `picnicker_valerie`, `lass_iris`, `ruin_maniac_larry`,
#      `camper_flint`, `dragon_tamer_drake`. None of those is the character, so none is
#      used. (This is also why "bea" is not `beauty_*`.)
#   3. Where a character has several textures, the pick is:
#      (a) the one our own server already uses for that character (server-gyms), so a
#          player sees the same face in both modes; otherwise
#      (b) the variant belonging to RCT's strongest encounter for them (highest team
#          level, then party size) — the roguelite meets these people as bosses.
#   4. NO LOOKALIKES. A character RCT does not ship is simply absent from this table and
#      renders as RCT's default skin. Substituting a similar-looking NPC would read as a
#      deliberate (wrong) casting choice; a default skin reads as "not done yet".
#
# Ids follow gen_pokerogue_roster.trainer_id(): `rgl_` + the PokéRogue key lowercased.
# Those keys are not re-derived here (that script reads PokéRogue's file at run time),
# so `--roster` exists to check them: an id here that no roster entry uses is reported.
ROGUELITE_SKINS = {
    # --- Kanto gym leaders (7 of 8; see UNCOVERED below) -----------------------
    "rgl_brock": "leader_brock_0038",
    "rgl_misty": "leader_misty_0020",
    "rgl_lt_surge": "leader_lt_surge_0033",
    "rgl_surge": "leader_lt_surge_0033",  # key-spelling hedge, see --roster
    "rgl_erika": "leader_erika_0041",
    "rgl_sabrina": "leader_sabrina_01a4",
    "rgl_blaine": "leader_blaine_01a3",
    # Viridian-gym Giovanni. RCT also has boss_giovanni_* (Rocket boss, L46/56/80) if he
    # is cast as an evil-team boss rather than a leader.
    "rgl_giovanni": "leader_giovanni_015e",
    # --- Johto gym leaders (8 of 8) -------------------------------------------
    "rgl_falkner": "leader_falkner_002b",
    "rgl_bugsy": "leader_bugsy_004b",
    "rgl_whitney": "leader_whitney_004c",
    "rgl_morty": "leader_morty_001c",
    "rgl_chuck": "leader_chuck_013d",
    "rgl_jasmine": "leader_jasmine_001d",
    "rgl_pryce": "leader_pryce_004f",
    "rgl_clair": "leader_clair_0026",
    # --- Sinnoh gym leaders (8 of 8) ------------------------------------------
    "rgl_roark": "gym_leader_roark_058a",
    "rgl_gardenia": "gym_leader_gardenia_03d6",
    "rgl_maylene": "gym_leader_maylene_058d",
    "rgl_crasher_wake": "gym_leader_wake_03d7",
    "rgl_wake": "gym_leader_wake_03d7",  # key-spelling hedge, see --roster
    "rgl_fantina": "gym_leader_fantina_058e",
    "rgl_byron": "gym_leader_byron_0399",
    "rgl_candice": "gym_leader_candice_058f",
    "rgl_volkner": "gym_leader_volkner_03db",
    # --- Elite Four + champion (§2.36: fixed, waves 182/184/186/188 + 190) -----
    # SINNOH is the only region RCT ships end to end (four E4 AND their champion), which
    # is why the rgl_e4_* slots are cast from it. Aaron/Bertha/Flint/Lucian + Cynthia.
    "rgl_e4_1": "elite_four_aaron_05a3",
    "rgl_e4_2": "elite_four_bertha_05a4",
    "rgl_e4_3": "elite_four_flint_05a5",
    "rgl_e4_4": "elite_four_lucian_05a6",
    "rgl_champion": "champion_cynthia_05a7",
    # The Kanto/Johto set, wired under name-shaped ids in case the E4 is cast from there
    # instead. Kanto's champion (Blue) is NOT in RCT — Lance is the Johto champion, and
    # RCT ships him twice (as E4 and as champion), hence two ids.
    "rgl_lorelei": "elite_four_lorelei_004e",
    "rgl_bruno": "elite_four_bruno_0051",
    "rgl_agatha": "elite_four_agatha_0054",
    "rgl_lance": "elite_four_lance_0057",
    "rgl_champion_lance": "champion_lance_0027",
    "rgl_koga": "leader_koga_01a2",  # Johto E4 Koga; RCT files him under leader_
    # --- Rival (§2.36: waves 8/25/55/95/145/195, same character, growing team) --
    # RCT's own rival "Wayne" is the only character it ships as a progression: five
    # numbered stages plus a title-defense stage, six in all, which is exactly the six
    # rival waves. RCT has three visually DIFFERENT variants per stage (named for the
    # starter the player chose); the `gible` line is used throughout so the character
    # does not change appearance between meetings.
    "rgl_rival_1": "rival_1_player_chose_gible_0606",
    "rgl_rival_2": "rival_2_player_chose_gible_0609",
    "rgl_rival_3": "rival_3_player_chose_gible_05fa",
    "rgl_rival_4": "rival_4_player_chose_gible_05fd",
    "rgl_rival_5": "rival_5_player_chose_gible_0600",
    "rgl_rival_6": "title_defense_rival_player_chose_gible_05ef",
    "rgl_rival": "rival_5_player_chose_gible_0600",  # single-id form used in the docs
}

# Characters the roguelite roster wants that RCT DOES NOT SHIP. Kept as data, not prose,
# so the gap is greppable and so nothing quietly acquires a lookalike later. Everything
# here renders as RCT's default skin until a licensed skin source is decided.
#
# This is the mainline leader / E4 / champion cast by region, NOT a read of PokéRogue's
# own table (that needs their file; gen_pokerogue_roster.py fetches it at run time). It is
# therefore the shape of the gap, not proof of its exact membership — `--roster` is what
# proves that, and it is the reason both directions of the diff are printed.
ROGUELITE_UNCOVERED = {
    "kanto": ["Janine"],
    "hoenn": ["Roxanne", "Brawly", "Wattson", "Flannery", "Norman", "Winona",
              "Tate", "Liza", "Juan", "Wallace"],
    "unova": ["Cilan", "Chili", "Cress", "Lenora", "Burgh", "Elesa", "Clay",
              "Skyla", "Brycen", "Drayden", "Roxie", "Marlon", "Cheren", "Iris"],
    "kalos": ["Viola", "Grant", "Korrina", "Ramos", "Clemont", "Valerie",
              "Olympia", "Wulfric"],
    "alola": ["Ilima", "Lana", "Kiawe", "Mallow", "Sophocles", "Acerola", "Mina",
              "Hala", "Olivia", "Nanu", "Hapu"],
    "galar": ["Milo", "Nessa", "Kabu", "Bea", "Allister", "Opal", "Gordie",
              "Melony", "Piers", "Marnie", "Raihan", "Bede"],
    "paldea": ["Katy", "Brassius", "Iono", "Kofu", "Larry", "Ryme", "Tulip", "Grusha"],
    "elite_four": ["Will", "Karen", "Sidney", "Phoebe", "Glacia", "Drake",
                   "Shauntal", "Marshal", "Grimsley", "Caitlin", "Malva",
                   "Siebold", "Wikstrom", "Drasna", "Molayne", "Kahili"],
    "champions": ["Blue", "Steven", "Wallace", "Alder", "Diantha", "Kukui",
                  "Leon", "Geeta"],
}


def roster_trainer_ids(roster_path: Path) -> "set[str]":
    """Every trainer id a roster names, as bare RCT ids (the part after `namespace:`)."""
    roster = json.loads(roster_path.read_text())
    ids: set[str] = set()

    def add(value) -> None:
        if isinstance(value, str):
            ids.add(value.rpartition(":")[2])

    for band in roster.get("bands", []):
        for trainer in band.get("trainers", []):
            add(trainer)
    for fixed in roster.get("fixed", []):
        add(fixed.get("trainer"))
    for entry in (roster.get("generated") or {}).get("entries", roster.get("generated") or []):
        if isinstance(entry, dict):
            add(entry.get("trainer"))
    return ids


def write_roguelite_skins(jar: zipfile.ZipFile, jar_names: "set[str]",
                          out_textures: Path, roster_path: "Path | None") -> None:
    """Copy each ROGUELITE_SKINS texture out of the jar under our roguelite trainer id."""
    written, broken = 0, []
    for trainer_id, stem in sorted(ROGUELITE_SKINS.items()):
        entry = f"assets/rctmod/textures/trainers/single/{stem}.png"
        if entry not in jar_names:
            broken.append(f"{trainer_id} -> {stem} (not in this rctmod jar)")
            continue
        (out_textures / f"{trainer_id}.png").write_bytes(jar.read(entry))
        written += 1
    print(f"wrote {written} roguelite textures ({len(ROGUELITE_SKINS)} mapped)")
    for problem in broken:
        print(f"  - BROKEN MAPPING: {problem}")

    uncovered = sum(len(names) for names in ROGUELITE_UNCOVERED.values())
    print(f"roguelite characters with NO RCT skin: {uncovered} (see ROGUELITE_UNCOVERED)")

    if roster_path is None:
        print("no --roster given: the ids above were NOT checked against a real roster")
        return
    roster_ids = roster_trainer_ids(roster_path)
    unused = sorted(set(ROGUELITE_SKINS) - roster_ids)
    skinless = sorted(roster_ids - set(ROGUELITE_SKINS))
    print(f"cross-checked against {len(roster_ids)} roster id(s) in {roster_path}")
    if unused:
        print("  skins for ids no roster entry uses (dead files, or a wrong key guess):")
        for trainer_id in unused:
            print(f"    - {trainer_id}")
    if skinless:
        print("  roster trainers with NO skin (they render as RCT's default):")
        for trainer_id in skinless:
            print(f"    - {trainer_id}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("jar", nargs="?", default="/tmp/rctmod.jar",
                        help="rctmod jar to copy textures out of (prefer the DEPLOYED version)")
    parser.add_argument("--roster", type=Path,
                        help="generated roguelite roster JSON, to cross-check ROGUELITE_SKINS ids")
    args = parser.parse_args()

    jar_path = Path(args.jar)
    jar = zipfile.ZipFile(jar_path)
    jar_names = set(jar.namelist())

    out_textures = PACK_DIR / "assets/rctmod/textures/trainers/single"
    out_textures.mkdir(parents=True, exist_ok=True)
    out_data = PACK_DIR / "data/rctmod/trainers"
    out_data.mkdir(parents=True, exist_ok=True)

    written, missing = 0, []
    for trainer_dir in TRAINER_DIRS:
        for path in sorted(trainer_dir.glob("*.json")):
            trainer = json.loads(path.read_text())
            # client-side copy of the trainer definition (texture binding
            # needs the trainer data present on the client)
            (out_data / path.name).write_text(path.read_text())
            ref = trainer.get("textureResource")
            if not ref:
                missing.append(f"{path.stem} (no textureResource)")
                continue
            # "rctmod:textures/trainers/single/x.png" -> assets/rctmod/textures/...
            ns, _, rel = ref.partition(":")
            jar_entry = f"assets/{ns}/{rel}"
            if jar_entry not in jar_names:
                # Invented type_<x>.png skins -> representative real class texture.
                fb = TYPE_FALLBACK.get(Path(rel).stem)
                if fb:
                    jar_entry = f"assets/rctmod/textures/trainers/single/{fb}.png"
                if jar_entry not in jar_names:
                    missing.append(f"{path.stem} -> {ref} (not in jar)")
                    continue
            (out_textures / f"{path.stem}.png").write_bytes(jar.read(jar_entry))
            written += 1

    print(f"wrote {written} textures to {out_textures}")
    if missing:
        print("UNRESOLVED (will render default skin):")
        for m in missing:
            print(f"  - {m}")

    # Roguelite trainers have no JSONs yet, so they come from the table, not the walk.
    write_roguelite_skins(jar, jar_names, out_textures, args.roster)


if __name__ == "__main__":
    main()
