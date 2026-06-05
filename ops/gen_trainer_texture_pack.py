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
]
PACK_DIR = REPO / "modpack/resourcepacks/rct-server-trainers"
PACK_FORMAT = 34  # 1.21–1.21.1


def main() -> None:
    jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/rctmod.jar")
    jar = zipfile.ZipFile(jar_path)
    jar_names = set(jar.namelist())

    out_textures = PACK_DIR / "assets/rctmod/textures/trainers/single"
    out_textures.mkdir(parents=True, exist_ok=True)

    written, missing = 0, []
    for trainer_dir in TRAINER_DIRS:
        for path in sorted(trainer_dir.glob("*.json")):
            trainer = json.loads(path.read_text())
            ref = trainer.get("textureResource")
            if not ref:
                missing.append(f"{path.stem} (no textureResource)")
                continue
            # "rctmod:textures/trainers/single/x.png" -> assets/rctmod/textures/...
            ns, _, rel = ref.partition(":")
            jar_entry = f"assets/{ns}/{rel}"
            if jar_entry not in jar_names:
                missing.append(f"{path.stem} -> {jar_entry} (not in jar)")
                continue
            (out_textures / f"{path.stem}.png").write_bytes(jar.read(jar_entry))
            written += 1

    (PACK_DIR / "pack.mcmeta").write_text(
        json.dumps(
            {
                "pack": {
                    "pack_format": PACK_FORMAT,
                    "description": "Skins for this server's custom RCT trainers (gyms + AI test)",
                }
            },
            indent=2,
        )
        + "\n"
    )

    print(f"wrote {written} textures to {out_textures}")
    if missing:
        print("UNRESOLVED (will render default skin):")
        for m in missing:
            print(f"  - {m}")


if __name__ == "__main__":
    main()
