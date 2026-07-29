#!/usr/bin/env python3
"""Install hand-sourced trainer skins from ops/skin-staging/ under their roguelite ids.

`gen_trainer_texture_pack.py` covers the roguelite characters RCT already ships, by
copying textures out of the rctmod jar (ROGUELITE_SKINS). This script covers the other
half: the characters RCT does NOT ship (ROGUELITE_UNCOVERED), whose skins have to be
sourced by hand from the community skin sites.

    python3 ops/install_hand_sourced_skins.py [--dry-run]

Drop files in ops/skin-staging/ named after the character ("Brawly.png"). The staging
folder is gitignored; only the installed result is committed. Re-runnable — installing
the same file twice is a no-op, so the remaining characters can be dropped in batches.

WHAT THIS CHECKS, and why each check exists rather than trusting the file:

  1. It is really a PNG. Saving a skin *page* instead of the skin gives you a 110 KB
     HTML document called `Blue.png`, which every tool downstream accepts and renders as
     a missing texture. This is the single most common failure and it is silent, so it is
     checked first and reported as "re-download", not "corrupt".

  2. It is 64x64, or 64x32 and converted. 64x32 is the pre-1.8 layout with no second
     limb pair and no overlays; Minecraft still loads it, but the model samples limbs
     that are not there. Converting is exact (the missing limbs are mirrors of the
     present ones), so it is done here rather than pushed back to whoever sourced it.

  3. The filename names a character we actually want. A skin called `Wallace.png` when
     the roster wants `wallace` is fine; `Sydney.png` when the character is Sidney is a
     misspelling that would install a file no trainer id ever reads. Spelling variants
     that are known and unambiguous are aliased below; anything else is REPORTED AND NOT
     INSTALLED, because a silently-ignored file looks identical to a job well done.

  4. It is not already covered from the jar. An id in both ROGUELITE_SKINS and here would
     have two sources and the winner would depend on which script ran last.
"""

import argparse
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_trainer_texture_pack import (  # noqa: E402
    PACK_DIR,
    REPO,
    ROGUELITE_FROM_SERVER_GYMS,
    ROGUELITE_SKINS,
    ROGUELITE_UNCOVERED,
)

STAGING = REPO / "ops/skin-staging"
DEST = PACK_DIR / "assets/rctmod/textures/trainers/single"

# Housekeeping files that live in the staging folder and are not attempts at a skin.
SKIP = {"README.md", ".DS_Store", ".gitignore"}

# Spelling variants seen in the wild on the skin sites, mapped to the roster's spelling.
# Only unambiguous ones belong here: each is a different rendering of the SAME character,
# never a guess at which character was meant.
ALIASES = {
    "clement": "clemont",   # Kalos Lumiose leader, commonly misspelled on skin sites
    "sydney": "sidney",     # Hoenn Elite Four
    "valarie": "valerie",   # Kalos Laverre leader
}


def norm(name: str) -> str:
    return "".join(c for c in name.lower() if c.isalnum())


def wanted_ids() -> "dict[str, str]":
    """normalised character name -> roguelite trainer id, per gen_pokerogue_roster.trainer_id."""
    out = {}
    for names in ROGUELITE_UNCOVERED.values():
        for name in names:
            out[norm(name)] = "rgl_" + name.lower().replace(" ", "_").replace(".", "")
    return out


def png_size(data: bytes) -> "tuple[int, int] | None":
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def mirror_limb(img):
    """Mirror one 16x16 arm/leg block, face by face.

    A limb block is laid out, left to right: side(4) front(4) side(4) back(4), with
    top(4x4) and bottom(4x4) stacked above the middle two. Mirroring the limb flips each
    face horizontally AND swaps the two side faces — which end of the limb you see
    changes. Flipping the 16x16 block as a whole is the tempting shortcut and is wrong:
    it lands the sides in the front/back columns and swaps top with bottom.
    """
    from PIL import Image

    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    faces = [
        (4, 0, 8, 4),    # top      -> in place
        (8, 0, 12, 4),   # bottom   -> in place
        (4, 4, 8, 16),   # front    -> in place
        (12, 4, 16, 16), # back     -> in place
    ]
    for box in faces:
        out.paste(img.crop(box).transpose(Image.FLIP_LEFT_RIGHT), box)
    left = (0, 4, 4, 16)
    right = (8, 4, 12, 16)
    out.paste(img.crop(left).transpose(Image.FLIP_LEFT_RIGHT), right)
    out.paste(img.crop(right).transpose(Image.FLIP_LEFT_RIGHT), left)
    return out


def to_modern(path: Path) -> bytes:
    """Convert a 64x32 legacy skin to 64x64 by mirroring the single limb pair."""
    from PIL import Image

    src = Image.open(path).convert("RGBA")
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out.paste(src, (0, 0))
    # right leg (0,16) -> left leg (16,48); right arm (40,16) -> left arm (32,48)
    out.paste(mirror_limb(src.crop((0, 16, 16, 32))), (16, 48))
    out.paste(mirror_limb(src.crop((40, 16, 56, 32))), (32, 48))
    from io import BytesIO

    buf = BytesIO()
    out.save(buf, format="PNG")
    return buf.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="report only, write nothing")
    args = parser.parse_args()

    if not STAGING.is_dir():
        sys.exit(f"no staging folder at {STAGING}")
    DEST.mkdir(parents=True, exist_ok=True)

    want = wanted_ids()
    installed, converted, redownload, unmatched, clashes = [], [], [], [], []
    supersedes = []

    # Every file, not just *.png. A browser that saves the skin *page* also tends to drop the
    # extension entirely ("Raihan"), and a glob for *.png would not merely skip that file — it
    # would never mention it, which is indistinguishable from the character not being staged.
    for path in sorted(p for p in STAGING.iterdir() if p.is_file() and p.name not in SKIP):
        key = norm(path.stem)
        key = ALIASES.get(key, key)
        data = path.read_bytes()
        size = png_size(data)
        if size is None:
            redownload.append(path.name)
            continue
        trainer_id = want.get(key)
        if trainer_id is None:
            unmatched.append(path.name)
            continue
        if trainer_id in ROGUELITE_SKINS:
            clashes.append((path.name, trainer_id))
            continue
        if size == (64, 32):
            data = to_modern(path)
            converted.append(trainer_id)
        elif size != (64, 64):
            unmatched.append(f"{path.name} (bad size {size[0]}x{size[1]})")
            continue
        # A hand-sourced skin for a character we currently cast from our own server-gyms is
        # the intended upgrade path, so it installs — but both sources now write the same id
        # and gen_trainer_texture_pack.py, which runs later, would put the old face back. The
        # stale mapping has to be deleted, so say so rather than letting the next regeneration
        # silently revert the upgrade.
        if trainer_id in ROGUELITE_FROM_SERVER_GYMS:
            supersedes.append((trainer_id, ROGUELITE_FROM_SERVER_GYMS[trainer_id]))

        target = DEST / f"{trainer_id}.png"
        unchanged = target.exists() and target.read_bytes() == data
        if not args.dry_run and not unchanged:
            target.write_bytes(data)
        installed.append(trainer_id)

    print(f"installed {len(installed)} hand-sourced skins into {DEST.relative_to(REPO)}")
    if converted:
        print(f"  converted from legacy 64x32: {', '.join(sorted(converted))}")
    if redownload:
        print(f"\nNOT A PNG — an HTML page saved with a .png name ({len(redownload)}).")
        print("  Use the site's Download button, not File > Save Page As:")
        for name in redownload:
            print(f"    {name}")
    if unmatched:
        print(f"\nFILENAME MATCHES NO ROGUELITE CHARACTER ({len(unmatched)}) — not installed:")
        for name in unmatched:
            print(f"    {name}")
    if clashes:
        print(f"\nALREADY COVERED FROM THE RCT JAR ({len(clashes)}) — not installed:")
        for name, trainer_id in clashes:
            print(f"    {name} -> {trainer_id} is in ROGUELITE_SKINS")

    if supersedes:
        print(f"\nACTION REQUIRED ({len(supersedes)}) — installed, but two sources now claim the id.")
        print("  Delete these from ROGUELITE_FROM_SERVER_GYMS in gen_trainer_texture_pack.py,")
        print("  or the next regeneration will overwrite the skin just installed:")
        for trainer_id, stem in supersedes:
            print(f"    {trainer_id} (was cast from {stem})")

    have = {p.stem for p in DEST.glob("rgl_*.png")}
    missing = sorted(t for t in want.values() if t not in have)
    print(f"\nroguelite characters still skinless: {len(missing)} of {len(want)}")
    if missing:
        print("  " + ", ".join(m.removeprefix("rgl_") for m in missing))


if __name__ == "__main__":
    main()
