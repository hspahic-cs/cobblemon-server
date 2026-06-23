#!/usr/bin/env python3
"""Generate the client resource pack that gives custom trainers their skins.

RCT resolves trainer skins CLIENT-side by filename:
    assets/rctmod/textures/trainers/single/<trainerId>.png
(then groups/<group>.png, then default.png). Custom datapack trainers
(server-gyms, aitest) have IDs with no matching texture, so everyone renders
as the default skin. The `textureResource` field in our trainer JSONs is the
intended skin — RCT ignores it, but we use it as the mapping table: copy each
referenced built-in texture out of the rctmod jar under our trainer's ID.

    python3 ops/gen_trainer_texture_pack.py [path/to/rctmod.jar]

Re-run after adding trainers or changing textureResource values. Output:
modpack/resourcepacks/rct-server-trainers/ (folder resource pack).
"""

import json
import sys
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


def main() -> None:
    jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/rctmod.jar")
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


if __name__ == "__main__":
    main()
