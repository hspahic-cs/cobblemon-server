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

WHAT THE BANDS ARE FOR, and the mistake to avoid:

Levels always come from the wave curve, so a band is not about levels. It is about composition,
and the tempting move — ramp the minimum party size, 3 then 4 then 6 — is wrong. RCT's authored
party sizes follow the games: a Youngster caps at three Pokémon, a Bug Catcher, Hiker, Swimmer
and Black Belt at four, and essentially only `ace_trainer` fields six. So a `>= 6` floor does
not select hard trainers, it selects ace trainers, and the pool stops showing the breadth of
trainer classes that is the entire reason to have generic waves. The floors default low (2/3/4)
for that reason.

Late-run difficulty belongs to the levers built for it: the roster's `generated` block builds a
team at the encounter with `party_size` from the wave (4/5/6), and boss waves carry shields.
Those scale a Bug Catcher without requiring that RCT authored him six Pokémon.

WHAT IS DELIBERATELY NOT DECIDED HERE:

Which classes suit the mode is a flavour call, and how hard a wave-135 trainer should be is a
balance call. Both are the server's, not this script's. The defaults are a starting point
chosen to be defensible, not authoritative — `--classes`, `--exclude-classes`, `--size-floors`
and `--pool-size` are how you overrule them, and the report prints enough to argue with.
"""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from collections import Counter as collections_Counter, Counter, defaultdict
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

# Classes dropped by default. Listed here so the choice is visible rather than buried in a
# filter; override with `--exclude-classes ''`.
#
# The first three are where the PROTAGONISTS AND PROFESSORS HIDE, and they are the reason
# neither filter above is enough on its own. RCT types Red, Brendan, May, Dawn and Oak as
# `normal`, and their ids carry no word NAMED_WORDS could match, so `trainer_red_0003` and
# `professor_oak_00c8` both reached the late band as ordinary filler. What gives them away is
# the class: a generic trainer in this mode should have a JOB — hiker, biker, lass, ace trainer
# — and a class of literally "trainer", "pokemon_trainer" or "professor" is the catch-all where
# story characters live. Excluding those three costs only `trainer_77`, which is no loss.
#
# `dragon_tamer` and `gentleman` are excluded for a THIRD reason, and it is worth spelling out
# because it is invisible to every check above. Trainers+ draws one texture per class and reuses
# it across every member, and for these two the artist drew a recognisable character:
# dragon_tamer is unmistakably Drake of the Hoenn Elite Four, gentleman is another known face.
# Our boss roster already casts Drake (`rgl_drake`), so leaving the class in means a player fights
# him as filler at wave 45 and again as an Elite Four member.
#
# The veto has to be the CLASS, not the id. All 16 dragon_tamer_* ids share that one texture and
# all 14 gentleman_* ids share theirs, so excluding `dragon_tamer_ramiro_0134` would simply pick
# another dragon tamer with the same face. Use --exclude-classes to revisit, and note that no
# automated rule finds these: the id is generic, RCT's type is `normal`, and the art is bespoke
# rather than a byte-identical copy of the named character's texture. Somebody has to look.
#
# The rest are RCT's joke and utility classes, which would break the fiction of a gauntlet.
DEFAULT_EXCLUDED_CLASSES = (
    "trainer", "trainer_77_05f6", "pokemon_trainer", "professor",
    "dragon_tamer", "gentleman",
    "dumbass", "dumbass_jojo", "friendly", "game_freaks", "gatekeeper",
)


def is_named_character(stem: str) -> bool:
    """Backstop only — the real filter is RCT's own `type`, see [resolve_types].

    Kept because it costs nothing and catches an id whose group is missing, but it must not be
    the primary test: guessing character-hood from a filename does not work. `trainer_red_0003`
    is Red, `professor_oak_00c8` is Oak, and `commander_jupiter_041e` is a Team Galactic
    commander — none contains a word this could match, and all three reached the pool.
    """
    return bool(NAMED_WORDS & set(stem.split("_")))


# The only RCT trainer type that is an ordinary trainer. Everything else is story cast:
# `leader` / `e4` / `champ` are the boss roster's, `rival` is Red, Brendan, May and Dawn,
# `team_rocket` / `team_galactic` / `team_shadow` are villain organisations, `battleground` is
# RCT's endgame gauntlet, and `ligh_of_ruin` (sic — RCT's spelling) is romhack-series cast.
GENERIC_TYPE = "normal"


def resolve_types(jar: zipfile.ZipFile) -> "tuple[dict[str, str], list[str]]":
    """Map every trainer id to RCT's own type, plus the group names, longest first.

    RCT declares the type in `data/rctmod/mobs/trainers/`, as an individual file per trainer
    where one exists and otherwise on the trainer's GROUP (`groups/<name>.json`) — 116 groups
    covering 1559 trainers. So a lookup is: the individual entry, else the longest group name
    that prefixes the id.

    Longest-prefix and not the class regex, because the two disagree exactly where it matters:
    `professor_oak_00c8` has no individual entry, and a shortest-match would resolve it against
    a `trainer`-ish group and call Oak an ordinary trainer.
    """
    individual, groups = {}, {}
    prefix = "data/rctmod/mobs/trainers/"
    for name in jar.namelist():
        if not (name.startswith(prefix) and name.endswith(".json")):
            continue
        stem = name[len(prefix):-5]
        try:
            declared = json.loads(jar.read(name)).get("type")
        except json.JSONDecodeError:
            continue
        if not declared:
            continue
        if stem.startswith("groups/"):
            groups[stem[len("groups/"):]] = declared
        else:
            individual[stem] = declared
    return {**groups, **individual}, sorted(groups, key=len, reverse=True)


def type_of(stem: str, types: "dict[str, str]", group_names: "list[str]") -> "str | None":
    if stem in types:
        return types[stem]
    for group in group_names:
        if stem.startswith(group):
            return types[group]
    return None

# (band id, min_wave, max_wave or None, minimum party size).
# The wave edges match example.json's trainer bands so the fragment drops straight in.
#
# THE SIZE FLOORS ARE LOW ON PURPOSE, and an earlier version got this wrong. Treating party
# size as the difficulty ramp (3 / 4 / 6) looked principled and quietly destroyed the thing
# generic trainers are FOR. RCT's authored party sizes follow the games: a Youngster caps at
# three Pokémon, a Bug Catcher, Hiker, Swimmer and Black Belt at four. Only `ace_trainer` has
# a deep bench of six. So a `>= 6` floor does not select "hard trainers", it selects
# ace_trainers — and the pool stops looking like the breadth of trainer classes the games have,
# which was the whole point of adding it.
#
# Difficulty at late waves has to come from somewhere else, and the roster already has the
# levers: the `generated` block builds a team at the encounter with `party_size` from the wave
# (4/5/6), and boss waves carry the shields. Those scale a Bug Catcher without demanding RCT
# had authored him six Pokémon. Keeping a mild ramp here (2/3/4) still trends bigger over a run
# without narrowing the cast to one class.
#
# Override with --size-floors.
DEFAULT_BANDS = [
    ("generic_trainer_early", 1, 60, 2),
    ("generic_trainer_mid", 61, 130, 3),
    ("generic_trainer_late", 131, None, 4),
]

DEFAULT_POOL_SIZE = 24

# The retexture pack the modpack ships. Read here for ONE reason: to deduplicate on ART.
#
# Trainers+ gives a texture per trainer CLASS and reuses it across every member — 1559 ids share
# just 173 distinct images. Picking one trainer per class *per band* therefore put the same Bug
# Catcher picture in all three bands and four identical Burglars in the pool: 72 ids, 39 looks.
# Deduplicating by id cannot see that, because the ids genuinely differ. Only the bytes do not.
DEFAULT_PACK = REPO / "modpack/resourcepacks/RCT Trainers+ [1.6] v2.1.zip"


def art_hashes(pack_path: "Path | None", jar: zipfile.ZipFile) -> "dict[str, str]":
    """trainer id -> hash of the texture the game will actually render for it."""
    out = {}
    entries = {
        n.rsplit("/", 1)[1][:-4]: (jar, n)
        for n in jar.namelist()
        if "/trainers/single/" in n and n.endswith(".png")
    }
    if pack_path is not None and pack_path.is_file():
        pack = zipfile.ZipFile(pack_path)
        # Pack wins, exactly as it does in game.
        for n in pack.namelist():
            if "/trainers/single/" in n and n.endswith(".png"):
                entries[n.rsplit("/", 1)[1][:-4]] = (pack, n)
    for stem, (archive, name) in entries.items():
        out[stem] = hashlib.sha256(archive.read(name)).hexdigest()
    return out


def load_generic_trainers(jar_path: Path) -> "list[dict]":
    """Every RCT trainer that is not a named character, with the facts a band needs."""
    if not jar_path.is_file():
        sys.exit(
            f"no rctmod jar at {jar_path}\n"
            "Download it with the url in modpack/mods/rctmod.pw.toml, or pass a path."
        )
    out = []
    skipped = collections_Counter()
    with zipfile.ZipFile(jar_path) as jar:
        types, group_names = resolve_types(jar)
        textures = {
            name.rsplit("/", 1)[1][:-4]
            for name in jar.namelist()
            if name.startswith("assets/rctmod/textures/trainers/single/") and name.endswith(".png")
        }
        for name in jar.namelist():
            if not (name.startswith("data/rctmod/trainers/") and name.endswith(".json")):
                continue
            stem = name.rsplit("/", 1)[1][:-5]
            # RCT's own classification first; the filename heuristic is only a backstop for a
            # trainer whose group is missing, and an unresolved type is treated as story cast
            # rather than waved through — a mystery id is not something to put in a filler wave.
            declared = type_of(stem, types, group_names)
            if declared != GENERIC_TYPE or is_named_character(stem):
                skipped[declared or "<unclassified>"] += 1
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
    for kind, n in sorted(skipped.items(), key=lambda kv: -kv[1]):
        print(f"  excluded {n:>4} of type {kind}")
    return out


def trainer_class(stem: str) -> str:
    """`ace_trainer_abel_04a5` -> `ace_trainer`. Falls back to the whole stem."""
    match = re.match(r"^([a-z_]+?)_[a-z]+_[0-9a-f]{3,4}$", stem)
    return match.group(1) if match else stem


def pick_pool(candidates: "list[dict]", size: int, used_art: "set[str]") -> "list[dict]":
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
            while by_class[klass]:
                candidate = by_class[klass].pop(0)
                art = candidate.get("art")
                # Skip a trainer whose picture is already in the pool. Not an id check: the ids
                # differ, the art does not, and a player cannot tell two Burglars apart.
                if art is not None and art in used_art:
                    continue
                if art is not None:
                    used_art.add(art)
                picked.append(candidate)
                exhausted = False
                break
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
    parser.add_argument("--pack", type=Path, default=DEFAULT_PACK,
                        help="retexture pack the modpack ships. Read to deduplicate on ART, since "
                             "it reuses one texture per class across every member.")
    parser.add_argument("--exclude-ids", default="",
                        help="comma-separated trainer ids to veto, for art that depicts a famous "
                             "character on a generic id (e.g. dragon_tamer_ramiro_0134 is Drake)")
    parser.add_argument("--size-floors", default="",
                        help="comma-separated minimum party size per band, e.g. 2,3,4. RCT's "
                             "authored sizes follow the games, so a high floor narrows the pool "
                             "to ace_trainers rather than making it harder.")
    parser.add_argument("--run-length", type=int, default=200)
    parser.add_argument("--trainer-interval", type=int, default=5)
    parser.add_argument("--boss-interval", type=int, default=10)
    args = parser.parse_args()

    bands_spec = DEFAULT_BANDS
    if args.size_floors:
        floors = [int(x) for x in args.size_floors.split(",")]
        if len(floors) != len(DEFAULT_BANDS):
            sys.exit(f"--size-floors needs {len(DEFAULT_BANDS)} values, got {len(floors)}")
        bands_spec = [(b[0], b[1], b[2], f) for b, f in zip(DEFAULT_BANDS, floors)]

    trainers = load_generic_trainers(args.jar)
    allow = {c.strip() for c in args.classes.split(",") if c.strip()}
    deny = {c.strip() for c in args.exclude_classes.split(",") if c.strip()}
    if allow:
        trainers = [t for t in trainers if t["klass"] in allow]
    if deny:
        trainers = [t for t in trainers if t["klass"] not in deny]
    if not trainers:
        sys.exit("no trainers left after --classes/--exclude-classes")

    veto = {i.strip() for i in args.exclude_ids.split(",") if i.strip()}
    if veto:
        before = len(trainers)
        trainers = [t for t in trainers if t["id"] not in veto]
        print(f"vetoed {before - len(trainers)} id(s) by --exclude-ids")

    # Attach the hash of the texture the game will render, so selection can dedupe on art.
    with zipfile.ZipFile(args.jar) as jar:
        hashes = art_hashes(args.pack if args.pack and args.pack.is_file() else None, jar)
    for t in trainers:
        t["art"] = hashes.get(t["id"])

    distinct = len({t["art"] for t in trainers if t["art"]})
    print(f"{len(trainers)} generic trainers available in {args.jar.name}, "
          f"but only {distinct} DISTINCT LOOKS — that is the real ceiling on variety")

    waves = trainer_waves(args.run_length, args.trainer_interval, args.boss_interval)
    bands, used, report = [], set(), []
    # Shared across bands: the same Bug Catcher picture in early AND late is the duplication
    # this exists to prevent, so the set is not reset per band.
    used_art = set()

    # MOST CONSTRAINED BAND FIRST, then restore the declared order for output.
    #
    # With art deduplicated globally there are only ~87 distinct looks to go round, so whichever
    # band draws first takes the pick of them. Drawing in declared order made the bands
    # alphabetical slices — early got ace_trainer..dragon_tamer, late got whatever survived — and
    # left the late band with 9 trainers because the high party sizes it needs had already gone
    # to a band that had no such requirement. Filling by descending size floor gives the scarce
    # big-party art to the only band that cannot substitute for it; the early band is choosing
    # from 847 candidates and can afford to go last.
    for band_id, min_wave, max_wave, min_size in sorted(bands_spec, key=lambda b: -b[3]):
        # Each id is used by at most one band. A trainer in two bands is not broken, but it
        # makes "have I seen this one already" depend on where you are, which is the opposite
        # of what a ramp is for.
        candidates = [
            t for t in trainers
            if t["size"] >= min_size and t["id"] not in used
        ]
        pool = pick_pool(candidates, args.pool_size, used_art)
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

    order = [b[0] for b in bands_spec]
    bands.sort(key=lambda b: order.index(b["id"]))
    report.sort(key=lambda r: order.index(r[0]))

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
        # Two different complaints, and only the first is a bug. A pool smaller than its waves
        # forces a repeat inside one run. A pool merely CLOSE to its wave count is fine for one
        # run but means every run draws from nearly the same handful, so the mode stops feeling
        # different on a replay — which for a roguelite is the point of the mode.
        if len(pool) < len(covered):
            print(f"  WARNING: pool of {len(pool)} is smaller than the {len(covered)} waves it "
                  "covers, so a single run will meet repeats")
        elif len(pool) < 2 * len(covered):
            print(f"  NOTE: {len(pool)} trainers over {len(covered)} waves is thin for replay "
                  "variety — lower this band's party-size floor to widen it")
        if skinless:
            print(f"  NO RCT SKIN ({len(skinless)}), will fall back to the group/default face: "
                  f"{', '.join(skinless)}")
        print()


if __name__ == "__main__":
    main()
