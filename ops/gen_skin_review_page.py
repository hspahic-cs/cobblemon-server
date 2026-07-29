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
OUT_PICKER = Path("/tmp/roguelite-skin-picker.html")
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


PICKER_TEMPLATE = """<!doctype html><meta charset=utf-8><title>Pick trainer skins</title>
<style>
body{background:#15171c;color:#e6e8ee;font:14px/1.5 system-ui,sans-serif;margin:0;padding:0 32px 48px}
header{position:sticky;top:0;background:#15171cf2;backdrop-filter:blur(8px);padding:24px 0 16px;
 border-bottom:1px solid #2a2e39;margin-bottom:24px;z-index:10}
h1{font-size:20px;margin:0 0 4px}p.sub{color:#8b93a7;margin:0 0 14px;max-width:78ch}
.bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
button{background:#252a35;color:#e6e8ee;border:1px solid #3a4150;border-radius:6px;
 padding:7px 13px;font:inherit;cursor:pointer}
button:hover{background:#2f3542}button.primary{background:#2c5cc5;border-color:#3f70da}
#count{color:#8b93a7;margin-left:auto;font-variant-numeric:tabular-nums}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(132px,1fr));gap:16px}
figure{margin:0;text-align:center;background:#1c1f27;border:2px solid #2a2e39;border-radius:8px;
 padding:12px 6px 10px;cursor:pointer;user-select:none;position:relative;transition:.08s}
figure:hover{border-color:#4a5265}
figure.off{opacity:.3;filter:grayscale(1)}
figure.on{border-color:#3f9c52;background:#1b2620}
figure .tick{position:absolute;top:6px;right:8px;font-size:15px;opacity:0}
figure.on .tick{opacity:1}
img{image-rendering:pixelated;height:168px;width:auto;display:block;margin:0 auto 8px}
figcaption b{display:block;text-transform:capitalize;font-size:13px}
figcaption span{display:block;color:#5a6376;font-size:10.5px;font-family:ui-monospace,monospace}
.veto{color:#d9843b;font-size:10.5px}
textarea{width:100%;height:90px;margin-top:16px;background:#0f1116;color:#c8cedb;border:1px solid #2a2e39;
 border-radius:6px;padding:10px;font:12px/1.5 ui-monospace,monospace}
</style>
<header>
<h1>Pick trainer skins &mdash; __TOTAL__ distinct looks</h1>
<p class=sub>Every distinct trainer texture RCT + Trainers+ has for ordinary trainers. Click a card
to keep or drop it. Trainers+ reuses one texture per class, so each card here is one class &mdash;
the number in brackets is how many trainer ids share that face. Orange cards are currently vetoed
(a famous character on a generic class, or a joke class). Your picks are remembered in this browser.</p>
<div class=bar>
<button class=primary onclick="dl()">Download picks</button>
<button onclick="copyFlag()">Copy class list</button>
<button onclick="setAll(true)">Select all</button>
<button onclick="setAll(false)">Select none</button>
<button onclick="reset()">Reset to defaults</button>
<span id=count></span>
</div>
<textarea id=out readonly></textarea>
</header>
<div class=grid id=grid>__CARDS__</div>
<script>
const DEFAULTS = __DEFAULTS__;
const LOOKCLASS = __LOOKCLASS__;
const KEY = 'roguelite-skin-picks';
let picked = new Set(JSON.parse(localStorage.getItem(KEY) || 'null') || DEFAULTS);

function render() {
  document.querySelectorAll('#grid figure').forEach(f => {
    const on = picked.has(f.dataset.cls);
    f.classList.toggle('on', on);
    f.classList.toggle('off', !on);
  });
  const list = [...picked].sort();
  document.getElementById('count').textContent = `${list.length} of __TOTAL__ kept`;
  const names = [...new Set(list.map(l => LOOKCLASS[l]))].sort();
  document.getElementById('out').value = list.length
    ? names.length + ' classes: ' + names.join(', ')
    : '(nothing selected)';
  localStorage.setItem(KEY, JSON.stringify(list));
}
document.getElementById('grid').addEventListener('click', e => {
  const f = e.target.closest('figure');
  if (!f) return;
  picked.has(f.dataset.cls) ? picked.delete(f.dataset.cls) : picked.add(f.dataset.cls);
  render();
});
function setAll(on) {
  picked = on ? new Set([...document.querySelectorAll('#grid figure')].map(f => f.dataset.cls)) : new Set();
  render();
}
function reset() { picked = new Set(DEFAULTS); render(); }
function copyFlag() {
  navigator.clipboard.writeText(document.getElementById('out').value);
}
function dl() {
  const body = JSON.stringify({
    _comment: 'Generated by the skin picker. Feed to gen_roguelite_generic_pool.py --picks FILE.',
    looks: [...picked].sort(),
    classes: [...new Set([...picked].map(l => LOOKCLASS[l]))].sort(),
  }, null, 2);
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([body], {type: 'application/json'}));
  a.download = 'roguelite-skin-picks.json';
  a.click();
  URL.revokeObjectURL(a.href);
}
render();
</script>
"""


def render_picker(jar_path: Path, pack_path: "Path | None") -> None:
    """One card per DISTINCT texture, click to keep, export the selection.

    Distinct texture and not distinct trainer id, because the pack reuses one image per class:
    1559 ids resolve to about 173 pictures, so a page per id would be the same face over and
    over and there would be nothing to choose between. Choosing a class IS choosing a look.
    """
    import zipfile

    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from gen_roguelite_generic_pool import (  # noqa: PLC0415
        DEFAULT_EXCLUDED_CLASSES, art_hashes, load_generic_trainers,
    )

    trainers = load_generic_trainers(jar_path)
    with zipfile.ZipFile(jar_path) as jar:
        hashes = art_hashes(pack_path if pack_path and pack_path.is_file() else None, jar)

    # Group by art. The representative is the alphabetically-first id, so the page is stable
    # across runs and a re-render does not shuffle the grid under a half-finished selection.
    looks: "dict[str, list[dict]]" = {}
    for trainer in trainers:
        art = hashes.get(trainer["id"])
        if art:
            looks.setdefault(art, []).append(trainer)

    rows = []
    for art, members in looks.items():
        members.sort(key=lambda m: m["id"])
        rows.append({
            "look": art[:12],
            "cls": members[0]["klass"],
            "rep": members[0]["id"],
            "n": len(members),
            "max_size": max(m["size"] for m in members),
            "art": art,
            "vetoed": members[0]["klass"] in DEFAULT_EXCLUDED_CLASSES,
        })
    rows.sort(key=lambda r: r["cls"])

    with zipfile.ZipFile(jar_path) as jar:
        pack = zipfile.ZipFile(pack_path) if pack_path and pack_path.is_file() else None
        pack_by_name = {
            n.rsplit("/", 1)[1][:-4]: n
            for n in (pack.namelist() if pack else [])
            if "/trainers/single/" in n and n.endswith(".png")
        }
        cards = []
        for row in rows:
            stem = row["rep"]
            if stem in pack_by_name:
                data = pack.read(pack_by_name[stem])
            else:
                data = jar.read(f"assets/rctmod/textures/trainers/single/{stem}.png")
            label = row["cls"].replace("_", " ")
            veto = '<span class="veto">vetoed</span>' if row["vetoed"] else ""
            cards.append(
                f'<figure data-cls="{row["look"]}"><span class=tick>&#10003;</span>'
                f'<img src="data:image/png;base64,{paper_doll(data)}" alt="{label}">'
                f"<figcaption><b>{label}</b>"
                f'<span>[{row["n"]}] max party {row["max_size"]}</span>{veto}</figcaption></figure>'
            )
    look_class = {r["look"]: r["cls"] for r in rows}

    defaults = [r["look"] for r in rows if not r["vetoed"]]
    OUT_PICKER.write_text(
        PICKER_TEMPLATE
        .replace("__CARDS__", "".join(cards))
        .replace("__DEFAULTS__", json.dumps(defaults))
        .replace("__LOOKCLASS__", json.dumps(look_class))
        .replace("__TOTAL__", str(len(rows))),
        encoding="utf-8",
    )
    print(f"wrote {OUT_PICKER} — {len(rows)} distinct looks, {len(defaults)} selected by default")
    print("  click to keep/drop, then 'Download picks' -> ops/roguelite-skin-picks.json")
    print("  then: python3 ops/gen_roguelite_generic_pool.py --picks ops/roguelite-skin-picks.json")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--picker", action="store_true",
                        help="render EVERY distinct trainer look with click-to-keep selection")
    parser.add_argument("--generic", action="store_true",
                        help="review the generated generic trainer bands instead of the installed skins")
    parser.add_argument("--jar", default=Path("/tmp/rctmod.jar"), type=Path)
    parser.add_argument("--bands", default=REPO / "ops/roguelite-generic-trainer-bands.json", type=Path)
    parser.add_argument("--pack", type=Path,
                        default=REPO / "modpack/resourcepacks/RCT Trainers+ [1.6] v2.1.zip",
                        help="retexture pack the modpack ships; read FIRST so the page shows what "
                             "players actually see. Pass a nonexistent path to see raw RCT art.")
    args = parser.parse_args()

    if args.picker:
        if not args.jar.is_file():
            sys.exit(f"no rctmod jar at {args.jar} — see modpack/mods/rctmod.pw.toml for the url")
        render_picker(args.jar, args.pack)
        return

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
