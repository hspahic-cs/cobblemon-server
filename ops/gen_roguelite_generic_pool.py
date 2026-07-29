#!/usr/bin/env python3
"""Build the roguelite's GENERIC trainer bands from RCT's own trainer library.

    python3 ops/gen_roguelite_generic_pool.py [path/to/rctmod.jar] [--out FILE]

Writes a `{"bands": [...]}` fragment to merge into a trainer roster (see
`trainer_rosters/example.json` for the full schema). Trainer bands only — boss bands are a
separate, hand-authored concern.

WHY THESE COST ALMOST NOTHING, unlike the named cast:

A 200-wave run at trainer-every-5 / boss-every-10 is 20 boss + 20 trainer + 160 wild waves
(plan §2.19). The 20 boss waves are the named characters, and giving those faces took a
hand-sourcing exercise across 87 characters because RCT ships no gym leaders. The 20 plain
trainer waves are the opposite case: RCT ships **1409 generic trainers**, and each one
already has

  - an authored team (`data/rctmod/trainers/<id>.json`),
  - a name, a bag, a loot table and an AI type, and
  - a skin, because RCT resolves its own ids client-side.

So a generic trainer needs no skin, no team and no trainer JSON from us. Listing its id in a
band is the entire job. The one rule that makes this work is in the roster schema: an id with
no entry in `generated` fights its **RCT-authored team**. We only add `generated` entries for
trainers whose teams we want to build from signature species — which is the boss roster.

WHAT THE BANDS ARE FOR, and why party size drives them:

Levels always come from the wave curve, so a band is not about levels. Scaling a team's level
does not scale its *composition* — and RCT's generic trainers run from one Pokémon to six.
A one-Pokémon Youngster levelled to 90 is not a wave-90 encounter, it is a free wave. Party
size is therefore the difficulty lever the bands exist to ramp, which is why the defaults
below gate on it and why sizes 1-2 are excluded from every band by default.

WHAT IS DELIBERATELY NOT DECIDED HERE:

Which classes suit the mode is a flavour call, and how hard a wave-135 trainer should be is a
balance call. Both are the server's, not this script's. The defaults are a starting point
chosen to be defensible, not authoritative — `--classes`, `--exclude-classes`, `--band` and
`--pool-size` are how you overrule them, and the report prints enough to argue with.
"""

import argparse
import json
import re
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_OUT = REPO / "ops/roguelite-generic-trainer-bands.json"

# RCT's named-character classes. Excluded from every band: these are the boss roster's cast,
# and a gym leader turning up as a filler trainer would read as a bug, not as variety.
#
# Matched as a WORD ANYWHERE IN THE ID, not as a prefix. A prefix test looks correct and is not:
# RCT files region-specific leaders as `sinnoh_leader_candice`, and admins as
# `rocket_admin_archer_ariana_m000` / `shadow_admin_marlon_3_05c0` / `light_of_ruin_admin_*`.
# All of those pass a `leader_`/`boss_` prefix check and land in the pool — Candice reached the
# late band before this was tightened.
NAMED_WORDS = frozenset({
    "leader", "elite", "four", "e4", "champion", "champ",
    "boss", "rival", "title", "defense", "admin",
})

# Joke and one-off classes RCT ships that would break the fiction of a trainer gauntlet. Dropped
# by default and listed here so the choice is visible; clear it with `--exclude-classes ''`.
DEFAULT_EXCLUDED_CLASSES = ("dumbass", "dumbass_jojo", "friendly", "game_freaks", "gatekeeper")


def is_named_character(stem: str) -> bool:
    """True if the id names one of RCT's story characters rather than a generic trainer."""
    return bool(NAMED_WORDS & set(stem.split("_")))

# (band id, min_wave, max_wave or None, minimum party size).
# The wave edges match example.json's trainer bands so the fragment drops straight in. The
# size floors are the difficulty ramp: a mid-run trainer brings at least four, and the last
# band is full teams only, because from about wave 138 the level curve has flattened at 100
# and composition is the only dial still turning.
DEFAULT_BANDS = [
    ("generic_trainer_early", 1, 60, 3),
    ("generic_trainer_mid", 61, 130, 4),
    ("generic_trainer_late", 131, None, 6),
]

DEFAULT_POOL_SIZE = 24


def load_generic_trainers(jar_path: Path) -> "list[dict]":
    """Every RCT trainer that is not a named character, with the facts a band needs."""
    if not jar_path.is_file():
        sys.exit(
            f"no rctmod jar at {jar_path}\n"
            "Download it with the url in modpack/mods/rctmod.pw.toml, or pass a path."
        )
    out = []
    with zipfile.ZipFile(jar_path) as jar:
        textures = {
            name.rsplit("/", 1)[1][:-4]
            for name in jar.namelist()
            if name.startswith("assets/rctmod/textures/trainers/single/") and name.endswith(".png")
        }
        for name in jar.namelist():
            if not (name.startswith("data/rctmod/trainers/") and name.endswith(".json")):
                continue
            stem = name.rsplit("/", 1)[1][:-5]
            if is_named_character(stem):
                continue
            try:
                data = json.loads(jar.read(name))
            except json.JSONDecodeError:
                continue
            team = data.get("team") or []
            if not team:
                continue
            levels = [member.get("level", 0) for member in team]
            out.append({
                "id": stem,
                "name": data.get("name", stem),
                "klass": trainer_class(stem),
                "size": len(team),
                "max_level": max(levels),
                # Recorded so a missing face is reported rather than discovered in game. RCT
                # falls back to groups/<group>.png then default.png, so this is not fatal.
                "has_skin": stem in textures,
            })
    return out


def trainer_class(stem: str) -> str:
    """`ace_trainer_abel_04a5` -> `ace_trainer`. Falls back to the whole stem."""
    match = re.match(r"^([a-z_]+?)_[a-z]+_[0-9a-f]{3,4}$", stem)
    return match.group(1) if match else stem


def pick_pool(candidates: "list[dict]", size: int) -> "list[dict]":
    """Take `size` trainers, spread across classes.

    Round-robin by class rather than a plain sort or a random sample. A sort by id would make
    a band 24 ace_trainers (they are the largest class by a wide margin); a random sample
    would make the file churn on every run for no reason. Round-robin gives class variety and
    is deterministic, so re-running with the same jar produces an identical file — which
    matters because editing a pool re-points runs already in progress.
    """
    by_class: "dict[str, list[dict]]" = defaultdict(list)
    for candidate in sorted(candidates, key=lambda c: (-c["max_level"], c["id"])):
        by_class[candidate["klass"]].append(candidate)

    picked, exhausted = [], False
    while len(picked) < size and not exhausted:
        exhausted = True
        for klass in sorted(by_class):
            if not by_class[klass]:
                continue
            picked.append(by_class[klass].pop(0))
            exhausted = False
            if len(picked) == size:
                break
    return picked


def trainer_waves(run_length: int, trainer_interval: int, boss_interval: int) -> "list[int]":
    """The waves that are plain trainer waves — bosses win the collision (plan §2.19)."""
    return [
        wave for wave in range(1, run_length + 1)
        if wave % trainer_interval == 0 and wave % boss_interval != 0
    ]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("jar", nargs="?", default="/tmp/rctmod.jar", type=Path)
    parser.add_argument("--out", default=DEFAULT_OUT, type=Path)
    parser.add_argument("--pool-size", type=int, default=DEFAULT_POOL_SIZE,
                        help=f"trainers per band (default {DEFAULT_POOL_SIZE})")
    parser.add_argument("--classes", default="",
                        help="comma-separated allowlist of trainer classes, e.g. youngster,hiker")
    parser.add_argument("--exclude-classes", default=",".join(DEFAULT_EXCLUDED_CLASSES),
                        help=f"comma-separated classes to drop (default: {','.join(DEFAULT_EXCLUDED_CLASSES)})")
    parser.add_argument("--run-length", type=int, default=200)
    parser.add_argument("--trainer-interval", type=int, default=5)
    parser.add_argument("--boss-interval", type=int, default=10)
    args = parser.parse_args()

    trainers = load_generic_trainers(args.jar)
    allow = {c.strip() for c in args.classes.split(",") if c.strip()}
    deny = {c.strip() for c in args.exclude_classes.split(",") if c.strip()}
    if allow:
        trainers = [t for t in trainers if t["klass"] in allow]
    if deny:
        trainers = [t for t in trainers if t["klass"] not in deny]
    if not trainers:
        sys.exit("no trainers left after --classes/--exclude-classes")

    print(f"{len(trainers)} generic trainers available in {args.jar.name}")

    waves = trainer_waves(args.run_length, args.trainer_interval, args.boss_interval)
    bands, used, report = [], set(), []
    for band_id, min_wave, max_wave, min_size in DEFAULT_BANDS:
        # Each id is used by at most one band. A trainer in two bands is not broken, but it
        # makes "have I seen this one already" depend on where you are, which is the opposite
        # of what a ramp is for.
        candidates = [
            t for t in trainers
            if t["size"] >= min_size and t["id"] not in used
        ]
        pool = pick_pool(candidates, args.pool_size)
        used.update(t["id"] for t in pool)

        band = {
            "id": band_id,
            "kind": "trainer",
            "min_wave": min_wave,
            "trainers": [f"rctmod:{t['id']}" for t in pool],
        }
        if max_wave is not None:
            band["max_wave"] = max_wave
        # Insert max_wave before trainers for readability without relying on dict ordering games.
        bands.append({k: band[k] for k in ("id", "kind", "min_wave", "max_wave", "trainers") if k in band})

        covered = [w for w in waves if w >= min_wave and (max_wave is None or w <= max_wave)]
        report.append((band_id, min_size, len(candidates), pool, covered))

    if not args.out.parent.is_dir():
        sys.exit(f"no directory {args.out.parent}")
    args.out.write_text(json.dumps({
        "_comment": [
            "GENERATED by ops/gen_roguelite_generic_pool.py — do not hand-edit; re-run instead.",
            "",
            "A fragment, not a roster: merge these entries into the 'bands' array of a trainer",
            "roster that also has boss bands, or the loader reports uncovered boss waves.",
            "",
            "These are RCT's own generic trainers. None has a 'generated' entry, which is what",
            "makes each fight its RCT-AUTHORED team — name, skin, bag, loot and AI all come from",
            "RCT too. Levels still come from the wave curve.",
            "",
            "Bands ramp on PARTY SIZE, because the curve already handles levels and a",
            "one-Pokemon trainer levelled to 90 is a free wave rather than a wave-90 encounter.",
        ],
        "bands": bands,
    }, indent=2) + "\n", encoding="utf-8")

    print(f"wrote {args.out.relative_to(REPO)}\n")
    for band_id, min_size, available, pool, covered in report:
        classes = Counter(t["klass"] for t in pool)
        sizes = Counter(t["size"] for t in pool)
        skinless = [t["id"] for t in pool if not t["has_skin"]]
        print(f"{band_id}: {len(pool)} trainers (party size >= {min_size}, {available} available)")
        print(f"  covers {len(covered)} trainer wave(s): {covered}")
        print(f"  party sizes: {dict(sorted(sizes.items()))}")
        print(f"  classes: {', '.join(f'{k}x{v}' for k, v in classes.most_common())}")
        if len(pool) < len(covered):
            print(f"  WARNING: pool of {len(pool)} is smaller than the {len(covered)} waves it "
                  "covers, so a single run will meet repeats")
        if skinless:
            print(f"  NO RCT SKIN ({len(skinless)}), will fall back to the group/default face: "
                  f"{', '.join(skinless)}")
        print()


if __name__ == "__main__":
    main()
