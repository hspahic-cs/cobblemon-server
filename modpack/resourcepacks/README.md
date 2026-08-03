# Resource packs

Client-side packs shipped with the modpack. Each is a committed zip, listed in
`modpack/index.toml` with its sha256 and enabled in `modpack/options.txt`.

Load order in `options.txt` is significant — later entries win. Trainers+ is last so its
trainer art takes precedence over anything below it.

## Third-party packs and their licences

### AllTheMons [R3.5.1].zip

Ships with the AllTheMons content. See that project for terms.

### RCT Trainers+ [1.6] v2.1.zip

**Radical Cobblemon Trainer Textures Plus** by **forestfire5129**
<https://modrinth.com/resourcepack/rct-trainer-textures-plus>
Licence: **LGPL-3.0-or-later**

Redistributed **unmodified**. Retextures all 1559 of RCT's trainer skins.

Adopted because RCT's own trainer art is plain — colour-swapped default skins with no class
identity. A Bug Catcher, a Black Belt and a Burglar were three Steves in different shirts.
Trainers+ gives each class its actual look (gi and belt, striped shirt and mask, cap and
shorts), which matters for the roguelite: 20 of a 200-wave run's encounters are generic
trainers, and they were the least interesting thing in the mode.

It also fixes the named cast. `rgl_misty` was a generic brown-haired figure and is now
orange-haired with red suspenders; Cynthia, Roark and Volkner are likewise recognisable.

**Derivative files.** `ops/gen_trainer_texture_pack.py --textures-from` copies 43 textures out
of this pack into `custom-mods/cobblemon-npc` under our own `rgl_*` trainer ids, because RCT
resolves skins by trainer id and our roguelite ids do not exist in the pack. Those 43 files are
derived from LGPL-3.0-or-later art and carry its terms, with credit as above.

They are in `cobblemon-npc`, which is modpack-side. They must **not** be moved into
`cobblemon-roguelite`, which is intended for publication and ships no fan art of any kind
(plan §2.7, §1.2).

To re-derive after a pack update:

    python3 ops/gen_trainer_texture_pack.py /tmp/rctmod.jar \
        --textures-from "modpack/resourcepacks/RCT Trainers+ [1.6] v2.1.zip"

A texture the pack lacks falls back to the rctmod jar per id, so an incomplete pack degrades
to RCT's own art rather than to a missing file.
