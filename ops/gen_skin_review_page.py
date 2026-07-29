#!/usr/bin/env python3
"""Render every installed roguelite trainer skin to a local HTML page for eyeball review.

    python3 ops/gen_skin_review_page.py && open /tmp/roguelite-skins.html

Writes a self-contained page (images inlined as base64) OUTSIDE the repo, because the
images are fan skins of Nintendo characters and are server-side content only — the page is
a review tool, not an artefact to commit or publish (plan §2.7, §1.2).

Each skin is drawn as a front-facing paper doll assembled from the texture's own face
regions, at 6x nearest-neighbour, with the overlay (hat/jacket) layer composited on top.
A flat texture dump would technically show the same pixels but is unreadable as a
likeness, and the whole point of the page is answering "is that actually Steven Stone".
"""

import argparse
import base64
import json
import sys
from io import BytesIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_trainer_texture_pack import PACK_DIR, REPO, ROGUELITE_FROM_SERVER_GYMS, ROGUELITE_SKINS  # noqa: E402

DEST = PACK_DIR / "assets/rctmod/textures/trainers/single"
OUT = Path("/tmp/roguelite-skins.html")
OUT_GENERIC = Path("/tmp/roguelite-generic-trainers.html")
SCALE = 6

SHELL = (
    "<!doctype html><meta charset=utf-8><title>{title}</title>"
    "<style>"
    "body{{background:#15171c;color:#e6e8ee;font:14px/1.5 system-ui,sans-serif;margin:0;padding:32px}}"
    "h1{{font-size:20px;margin:0 0 4px}}p.sub{{color:#8b93a7;margin:0 0 28px;max-width:70ch}}"
    "h2{{font-size:14px;text-transform:uppercase;letter-spacing:.08em;color:#8b93a7;"
    "border-bottom:1px solid #2a2e39;padding-bottom:8px;margin:36px 0 20px}}"
    "h2 .n{{color:#5a6376;margin-left:6px}}"
    ".grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:20px}}"
    "figure{{margin:0;text-align:center;background:#1c1f27;border:1px solid #2a2e39;"
    "border-radius:8px;padding:14px 8px}}"
    "img{{image-rendering:pixelated;height:192px;width:auto;display:block;margin:0 auto 10px}}"
    ".missing{{height:192px;display:flex;align-items:center;justify-content:center;"
    "font-size:48px;color:#3a4050;margin-bottom:10px}}"
    "figcaption b{{display:block;text-transform:capitalize}}"
    "figcaption span{{display:block;color:#5a6376;font-size:11px;font-family:ui-monospace,monospace}}"
    "</style>"
    "<h1>{heading}</h1><p class=sub>{sub}</p>{body}"
)

# (source box in the 64x64 texture, destination corner on the 16x32 doll).
# Second entry of each pair is the overlay layer for the same body part.
PARTS = [
    ((8, 8, 16, 16), (40, 8, 48, 16), (4, 0)),     # head
    ((20, 20, 28, 32), (20, 36, 28, 48), (4, 8)),  # torso
    ((44, 20, 48, 32), (44, 36, 48, 48), (0, 8)),  # right arm
    ((36, 52, 40, 64), (52, 52, 56, 64), (12, 8)), # left arm
    ((4, 20, 8, 32), (4, 36, 8, 48), (4, 20)),     # right leg
    ((20, 52, 24, 64), (4, 52, 8, 64), (8, 20)),   # left leg
]


def paper_doll(source) -> str:
    """Render one skin as a front-facing figure. Takes a path or raw PNG bytes.

    Bytes matter for the generic pool: those trainers' textures are never installed into the
    pack — they live inside the rctmod jar and RCT resolves them by its own id — so reviewing
    them means reading straight out of the archive.
    """
    from PIL import Image

    src = Image.open(BytesIO(source) if isinstance(source, bytes) else source).convert("RGBA")
    if src.size != (64, 64):
        src = src.resize((64, 64), Image.NEAREST)
    doll = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    for base, overlay, corner in PARTS:
        doll.alpha_composite(src.crop(base), corner)
        doll.alpha_composite(src.crop(overlay), corner)
    doll = doll.resize((16 * SCALE, 32 * SCALE), Image.NEAREST)
    buf = BytesIO()
    doll.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


def generic_pool_groups(jar_path: Path, bands_path: Path,
                        pack_path: "Path | None") -> "dict[str, list[tuple[str, bytes]]]":
    """The generated generic-trainer bands, as {band id: [(label, png bytes)]}.

    Reads through the SAME source the game will: the shipped retexture pack first, the rctmod
    jar per missing id. That is not a detail — an earlier version read only the jar, so the page
    showed RCT's plain colour-swapped skins while the game was about to render Trainers+ art.
    A review tool that does not show what players see is worse than no review tool, because it
    invites a decision about art nobody is going to look at.

    Reports a missing texture rather than skipping it: RCT falls back to a group face and then
    to default.png, so a gap is a trainer who looks like nobody in particular, not a crash.
    """
    import zipfile

    bands = json.loads(bands_path.read_text(encoding="utf-8"))["bands"]
    groups = {}
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        pack, by_name = None, {}
        if pack_path is not None and pack_path.is_file():
            pack = zipfile.ZipFile(pack_path)
            by_name = {
                n.rsplit("/", 1)[1][:-4]: n
                for n in pack.namelist()
                if "/trainers/single/" in n and n.endswith(".png")
            }
        for band in bands:
            entries = []
            for trainer in band["trainers"]:
                stem = trainer.split(":", 1)[-1]
                if stem in by_name:
                    entries.append((stem, pack.read(by_name[stem])))
                    continue
                entry = f"assets/rctmod/textures/trainers/single/{stem}.png"
                if entry not in names:
                    entries.append((f"{stem} (NO TEXTURE)", None))
                    continue
                entries.append((stem, jar.read(entry)))
            waves = f"waves {band['min_wave']}-{band.get('max_wave', '200')}"
            groups[f"{band['id']} — {waves}, {len(entries)} trainers"] = entries
    return groups


def render_generic(jar_path: Path, bands_path: Path, pack_path: "Path | None") -> None:
    groups = generic_pool_groups(jar_path, bands_path, pack_path)
    cards, total = [], 0
    for title, entries in groups.items():
        cards.append(f'<h2>{title}</h2><div class="grid">')
        for label, data in entries:
            total += 1
            if data is None:
                cards.append(f'<figure><div class="missing">?</div><figcaption><b>{label}</b></figcaption></figure>')
                continue
            name = label.rsplit("_", 1)[0].replace("_", " ")
            cards.append(
                f'<figure><img src="data:image/png;base64,{paper_doll(data)}" alt="{name}">'
                f"<figcaption><b>{name}</b><span>{label}</span></figcaption></figure>"
            )
        cards.append("</div>")
    OUT_GENERIC.write_text(SHELL.format(
        title="Roguelite generic trainers",
        heading=f"Generic trainer pool — {total} from RCT's library",
        sub="These fight their RCT-authored teams at levels from the wave curve. Skins are resolved "
            "by RCT's own trainer id and read through the shipped RCT Trainers+ pack, falling back "
            "to the rctmod jar per id — the same order the game uses.",
        body="".join(cards),
    ), encoding="utf-8")
    print(f"wrote {OUT_GENERIC} — {total} trainers across {len(groups)} bands")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--generic", action="store_true",
                        help="review the generated generic trainer bands instead of the installed skins")
    parser.add_argument("--jar", default=Path("/tmp/rctmod.jar"), type=Path)
    parser.add_argument("--bands", default=REPO / "ops/roguelite-generic-trainer-bands.json", type=Path)
    parser.add_argument("--pack", type=Path,
                        default=REPO / "modpack/resourcepacks/RCT Trainers+ [1.6] v2.1.zip",
                        help="retexture pack the modpack ships; read FIRST so the page shows what "
                             "players actually see. Pass a nonexistent path to see raw RCT art.")
    args = parser.parse_args()

    if args.generic:
        if not args.jar.is_file():
            sys.exit(f"no rctmod jar at {args.jar} — see modpack/mods/rctmod.pw.toml for the url")
        if not args.bands.is_file():
            sys.exit(f"no bands file at {args.bands} — run gen_roguelite_generic_pool.py first")
        render_generic(args.jar, args.bands, args.pack)
        return

    files = sorted(DEST.glob("rgl_*.png"))
    if not files:
        sys.exit(f"no rgl_*.png in {DEST}")

    def origin(stem: str) -> str:
        if stem in ROGUELITE_SKINS:
            return "RCT library"
        if stem in ROGUELITE_FROM_SERVER_GYMS:
            return "our server-gym cast"
        return "hand-sourced"

    groups: "dict[str, list[Path]]" = {}
    for path in files:
        groups.setdefault(origin(path.stem), []).append(path)

    cards = []
    for source in ("hand-sourced", "our server-gym cast", "RCT library"):
        paths = groups.get(source, [])
        if not paths:
            continue
        cards.append(f'<h2>{source} <span class="n">{len(paths)}</span></h2><div class="grid">')
        for path in paths:
            name = path.stem.removeprefix("rgl_").replace("_", " ")
            cards.append(
                f'<figure><img src="data:image/png;base64,{paper_doll(path)}" alt="{name}">'
                f"<figcaption><b>{name}</b><span>{path.stem}</span></figcaption></figure>"
            )
        cards.append("</div>")

    OUT.write_text(SHELL.format(
        title="Roguelite trainer skins",
        heading=f"Roguelite trainer skins — {len(files)} installed",
        sub="Front view, overlay layer composited. Check each is the right character and the art "
            "is acceptable; note any to replace.",
        body="".join(cards),
    ), encoding="utf-8")
    print(f"wrote {OUT} — {len(files)} skins")
    for source, paths in groups.items():
        print(f"  {source}: {len(paths)}")


if __name__ == "__main__":
    main()
