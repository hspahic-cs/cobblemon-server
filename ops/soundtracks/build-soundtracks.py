#!/usr/bin/env python3
"""Convert world-soundtrack source audio into the .ogg tracks the
cobblemon-soundtracks mod ships, and regenerate its sounds.json.

Workflow
--------
1. Drop your audio (mp3 / wav / flac / m4a / ogg ... anything ffmpeg reads)
   into the source folders. There are two layers:

   Exploration music (plays while walking the world; per-dimension):
       ops/soundtracks/source/spawn/         -> main hub   (multiworld:spawn)
       ops/soundtracks/source/elite4/        -> Elite Four (multiworld:elite4)
       ops/soundtracks/source/arena/         -> PvP arenas (multiworld:arena*)

   Battle themes (play during the fight; set by cobblemon-bridge):
       ops/soundtracks/source/elite4-battle/ -> Elite Four battles
       ops/soundtracks/source/arena-battle/  -> arena (PvP) battles

   Any number of files per folder. Exploration folders rotate through their
   tracks; a battle folder picks one track at random per battle and loops it.
   File names are slugified (lowercased, non-alnum -> '_'); keep them
   descriptive, the name is cosmetic.

2. Run this script (needs ffmpeg on PATH):

       python3 ops/soundtracks/build-soundtracks.py

   It re-encodes every source file to Ogg Vorbis into
   custom-mods/cobblemon-soundtracks/.../sounds/music/<world>/ and rewrites
   sounds.json so the mod knows about them. Stale .ogg from removed sources are
   pruned. Re-run any time you add/remove tracks.

That's it — commit the resulting .ogg files + sounds.json. The wilderness
(survival overworld) is deliberately not a world here; it keeps vanilla music.
"""

import json
import shutil
import subprocess
import sys
from pathlib import Path

# repo_root/ops/soundtracks/build-soundtracks.py -> repo_root
REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = REPO_ROOT / "ops" / "soundtracks" / "source"
NAMESPACE = "cobblemon_soundtracks"
ASSETS = (
    REPO_ROOT
    / "custom-mods"
    / "cobblemon-soundtracks"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / NAMESPACE
)
SOUNDS_DIR = ASSETS / "sounds"
SOUNDS_JSON = ASSETS / "sounds.json"

# One entry per sound event. Each is (event id, source subdir under source/,
# dest subdir under sounds/). Two layers:
#   music.*  -> exploration music, played per-dimension by SoundtrackManager.kt
#              (the "music.*" ids must match ModSounds.kt).
#   battle.* -> battle themes, set on the player actor by cobblemon-bridge's
#              BattleThemeHook during Elite Four / arena battles. These need NO
#              code registration — Cobblemon builds the SoundEvent client-side
#              from the id — only a sounds.json entry, which this script writes.
EVENTS = [
    # event id            source subdir             dest subdir
    ("music.spawn",       "spawn",                  "music/spawn"),
    ("music.elite4",      "elite4",                 "music/elite4"),
    # Regular gym battles (gym 1–19) — one shared rotating pool, random per battle.
    ("battle.gym",        "gym-battle",             "battle/gym"),
    # Elite Four battle themes — one per member (gym 20–24), see BattleThemeHook.
    ("battle.e4_alder",   "elite4-battle/alder",    "battle/e4/alder"),
    ("battle.e4_cynthia", "elite4-battle/cynthia",  "battle/e4/cynthia"),
    ("battle.e4_ash",     "elite4-battle/ash",      "battle/e4/ash"),
    ("battle.e4_lance",   "elite4-battle/lance",    "battle/e4/lance"),
    ("battle.e4_n",       "elite4-battle/n",        "battle/e4/n"),
    # Arena PvP battle theme (shared — no per-opponent split for PvP).
    ("battle.arena",      "arena-battle",           "battle/arena"),
]

# Alias events reuse another event's already-converted tracks (no extra .ogg, no
# source folder). Used so the arena WORLD plays the same pool as arena battles:
# walking the arena uses `music.arena` (exploration) and a PvP fight uses
# `battle.arena`, both pointing at the same files.
#   alias event id -> source event id
ALIASES = {
    "music.arena": "battle.arena",
}

AUDIO_EXTS = {".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wma"}


def slugify(name: str) -> str:
    out = []
    for ch in name.lower():
        out.append(ch if ch.isalnum() else "_")
    slug = "".join(out).strip("_")
    while "__" in slug:
        slug = slug.replace("__", "_")
    return slug or "track"


def ensure_ffmpeg() -> None:
    if shutil.which("ffmpeg") is None:
        sys.exit(
            "error: ffmpeg not found on PATH.\n"
            "  macOS:  brew install ffmpeg\n"
            "  debian: sudo apt-get install ffmpeg"
        )


# Target ~128 kbps: transparent for streamed background music, keeps the
# shipped soundtrack a reasonable download.
BITRATE_K = 128

# Loudness normalization (EBU R128). Source tracks arrive at wildly different
# levels (measured -12 to -17 LUFS), so without this the soundtrack jumps in
# volume from song to song. Every track is normalized to a single integrated
# loudness via ffmpeg's two-pass `loudnorm` so they all sound equally loud.
#   TARGET_I   integrated loudness, LUFS. -16 (streaming level) read too hot in
#              game; -18 is a gentler background bed. Go to ~-20 for quieter still.
#   TARGET_TP  max true peak, dBTP (headroom so lossy encode can't clip).
#   TARGET_LRA target loudness range (dynamics); 11 keeps music lively.
TARGET_I = -18.0
TARGET_TP = -1.5
TARGET_LRA = 11.0

# Encoder strategy, resolved once per run. Minecraft only plays OGG Vorbis.
#   "libvorbis"    — ffmpeg -c:a libvorbis (precise bitrate, best). Rare on
#                    modern Homebrew, which dropped libvorbis from ffmpeg.
#   "oggenc"       — ffmpeg decodes to WAV, piped to oggenc -b (precise, good).
#   "experimental" — ffmpeg's built-in 'vorbis' encoder. Last resort: it ignores
#                    -b:a (won't hit the target size) and is lower quality.
_ENCODER: str = "experimental"


def pick_encoder() -> str:
    try:
        enc = subprocess.run(
            ["ffmpeg", "-hide_banner", "-encoders"],
            capture_output=True, text=True, check=True,
        ).stdout
    except Exception:
        enc = ""
    if "libvorbis" in enc:
        return "libvorbis"
    if shutil.which("oggenc"):
        return "oggenc"
    print("note: no libvorbis and no oggenc — falling back to ffmpeg's built-in "
          "'vorbis' encoder (won't hit the target bitrate; lower quality).\n"
          "      Install oggenc for proper sizing:  brew install vorbis-tools")
    return "experimental"


def loudnorm_filter(src: Path) -> str:
    """Build the ffmpeg `loudnorm` filter for [src], normalized to TARGET_I LUFS.

    Two-pass: an analysis pass measures the source's real loudness, then those
    measured values are fed back so the second (encoding) pass does an accurate,
    mostly-linear normalization (preserves dynamics, hits the target). Falls back
    to single-pass loudnorm if the measure pass can't be parsed.
    """
    base = f"loudnorm=I={TARGET_I}:TP={TARGET_TP}:LRA={TARGET_LRA}"
    try:
        proc = subprocess.run(
            ["ffmpeg", "-hide_banner", "-nostats", "-i", str(src),
             "-af", base + ":print_format=json", "-f", "null", "-"],
            capture_output=True, text=True, check=True,
        )
        blob = proc.stderr[proc.stderr.rindex("{"):proc.stderr.rindex("}") + 1]
        m = json.loads(blob)
        return (f"{base}:measured_I={m['input_i']}:measured_TP={m['input_tp']}"
                f":measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}"
                f":offset={m['target_offset']}:linear=true")
    except Exception as e:
        print(f"  note: loudnorm measure pass failed for {src.name} "
              f"({e}); using single-pass.")
        return base


def convert(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    # -vn drop cover art, loudness-normalize to TARGET_I, force 44.1k stereo.
    decode = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
              "-i", str(src), "-vn", "-map_metadata", "-1",
              "-af", loudnorm_filter(src), "-ac", "2", "-ar", "44100"]
    if _ENCODER == "libvorbis":
        subprocess.run([*decode, "-c:a", "libvorbis", "-b:a", f"{BITRATE_K}k", str(dst)], check=True)
    elif _ENCODER == "oggenc":
        # ffmpeg -> WAV on stdout -> oggenc -b <kbps>
        p = subprocess.Popen([*decode, "-f", "wav", "-"], stdout=subprocess.PIPE)
        try:
            subprocess.run(["oggenc", "-Q", "-b", str(BITRATE_K), "-o", str(dst), "-"],
                           stdin=p.stdout, check=True)
        finally:
            if p.stdout:
                p.stdout.close()
            p.wait()
        if p.returncode not in (0, None):
            raise subprocess.CalledProcessError(p.returncode, "ffmpeg(decode)")
    else:
        subprocess.run([*decode, "-c:a", "vorbis", "-strict", "experimental",
                        "-q:a", "3", str(dst)], check=True)


def main() -> None:
    ensure_ffmpeg()
    global _ENCODER
    _ENCODER = pick_encoder()

    if not SOURCE_ROOT.exists():
        sys.exit(f"error: source folder missing: {SOURCE_ROOT}")

    sounds_json: dict[str, dict] = {}
    grand_total = 0

    for event, src_subdir, dst_subdir in EVENTS:
        src_dir = SOURCE_ROOT / src_subdir
        dst_dir = SOUNDS_DIR / dst_subdir
        dst_dir.mkdir(parents=True, exist_ok=True)

        # Prune stale generated .ogg (keep README.md / .gitkeep).
        for old in dst_dir.glob("*.ogg"):
            old.unlink()

        sources = []
        if src_dir.exists():
            sources = sorted(
                p for p in src_dir.iterdir()
                if p.is_file() and p.suffix.lower() in AUDIO_EXTS
            )

        entries = []
        used_slugs: set[str] = set()
        for src in sources:
            slug = slugify(src.stem)
            # de-dupe slug collisions deterministically
            base, n = slug, 2
            while slug in used_slugs:
                slug = f"{base}_{n}"
                n += 1
            used_slugs.add(slug)

            dst = dst_dir / f"{slug}.ogg"
            print(f"  [{event}] {src.name}  ->  {dst_subdir}/{slug}.ogg")
            convert(src, dst)
            # "stream": true so long music tracks stream from disk instead of
            # being fully decoded into memory.
            entries.append({"name": f"{NAMESPACE}:{dst_subdir}/{slug}", "stream": True})

        if not entries:
            print(f"  [{event}] (no source audio yet — empty)")
        grand_total += len(entries)
        sounds_json[event] = {"category": "music", "sounds": entries}

    # Alias events reuse a built event's track list (no extra .ogg).
    for alias_event, src_event in ALIASES.items():
        src = sounds_json.get(src_event, {}).get("sounds", [])
        sounds_json[alias_event] = {"category": "music", "sounds": [dict(s) for s in src]}
        print(f"  [{alias_event}] -> alias of [{src_event}] ({len(src)} track(s))")

    SOUNDS_JSON.write_text(json.dumps(sounds_json, indent=2) + "\n")
    print(f"\nWrote {SOUNDS_JSON.relative_to(REPO_ROOT)} ({grand_total} track(s) total).")
    if grand_total == 0:
        print("No audio converted yet — drop files into ops/soundtracks/source/<subdir>/ and re-run.")


if __name__ == "__main__":
    main()
