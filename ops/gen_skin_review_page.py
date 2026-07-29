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

import base64
import sys
from io import BytesIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_trainer_texture_pack import PACK_DIR, ROGUELITE_FROM_SERVER_GYMS, ROGUELITE_SKINS  # noqa: E402

DEST = PACK_DIR / "assets/rctmod/textures/trainers/single"
OUT = Path("/tmp/roguelite-skins.html")
SCALE = 6

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


def paper_doll(path: Path) -> str:
    from PIL import Image

    src = Image.open(path).convert("RGBA")
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


def main() -> None:
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

    OUT.write_text(
        "<!doctype html><meta charset=utf-8><title>Roguelite trainer skins</title>"
        "<style>"
        "body{background:#15171c;color:#e6e8ee;font:14px/1.5 system-ui,sans-serif;margin:0;padding:32px}"
        "h1{font-size:20px;margin:0 0 4px}p.sub{color:#8b93a7;margin:0 0 28px}"
        "h2{font-size:14px;text-transform:uppercase;letter-spacing:.08em;color:#8b93a7;"
        "border-bottom:1px solid #2a2e39;padding-bottom:8px;margin:36px 0 20px}"
        "h2 .n{color:#5a6376;margin-left:6px}"
        ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:20px}"
        "figure{margin:0;text-align:center;background:#1c1f27;border:1px solid #2a2e39;"
        "border-radius:8px;padding:14px 8px}"
        "img{image-rendering:pixelated;height:192px;width:auto;display:block;margin:0 auto 10px}"
        "figcaption b{display:block;text-transform:capitalize}"
        "figcaption span{display:block;color:#5a6376;font-size:11px;font-family:ui-monospace,monospace}"
        "</style>"
        f"<h1>Roguelite trainer skins — {len(files)} installed</h1>"
        "<p class=sub>Front view, overlay layer composited. Check each is the right character "
        "and the art is acceptable; note any to replace.</p>" + "".join(cards),
        encoding="utf-8",
    )
    print(f"wrote {OUT} — {len(files)} skins")
    for source, paths in groups.items():
        print(f"  {source}: {len(paths)}")


if __name__ == "__main__":
    main()
