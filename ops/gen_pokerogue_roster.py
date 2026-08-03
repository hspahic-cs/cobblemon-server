#!/usr/bin/env python3
"""Turn PokéRogue's signature-species table into cobblemon-roguelite roster entries.

PokéRogue gives each gym leader FOUR slots, where a slot is one species or a set of alternatives:

    BROCK:  [ONIX, GEODUDE, [OMANYTE, KABUTO], AERODACTYL]
    MELONY: [LAPRAS, SNOM, EISCUE, [GALAR_MR_MIME, GALAR_DARUMAKA]]

That is the whole input. The team itself is generated at the encounter from (run seed, wave) — see
plan §2.30 — so what this script emits per trainer is not a team but the *material* for one: each
slot's alternatives, and for each alternative the species' evolution line, base form first. The run
picks an alternative by seed and a stage by wave, which is why a line and not a species.

    python3 ops/gen_pokerogue_roster.py --out /tmp/pokerogue.json --roster

WHY THE LINES ARE COMPUTED HERE AND NOT AT RUNTIME
    The mod could read Cobblemon's evolution graph at the encounter — it compiles against Cobblemon.
    It deliberately does not. A Cobblemon version bump that re-points one evolution would then change
    which Pokémon a *checkpointed* run meets at a wave it has not reached yet, and the run has no way
    to notice. Baking the line into the datapack makes that an operator action (re-run this script,
    read the diff) instead of a silent side effect of updating a mod.

LICENSING — READ BEFORE WIRING THIS INTO A BUILD (plan §2.7)
    PokéRogue's code is AGPL-3.0-only and their docs/assets are CC-BY-NC-SA-4.0. The split we rely on
    is that this reads their source at RUN time and writes OUR data format:

      * their file is fetched, never vendored into this repo — do not commit it, do not check in a
        copy under ops/data/, and do not add it to the modpack;
      * the OUTPUT is server-side datapack content. It belongs in a server datapack, never in
        custom-mods/cobblemon-roguelite/src/main/resources, which is a *published build*.

    The script refuses to write into the mod's resources for that second reason.

SPECIES MAPPING — GAPS ARE REPORTED, NEVER DROPPED
    A slot that quietly vanishes is a weaker trainer nobody notices; a slot that turns into the wrong
    Pokémon is worse (base Corsola is Water, Galarian Corsola is Ghost). So every name is resolved
    against a real Cobblemon species list read out of the Cobblemon jar, and anything unresolved is
    printed and fails the run unless --allow-missing is passed. See resolve_species().

WHAT THIS DOES NOT DECIDE
    Held items and which leader appears at which wave. Item choices are content (§2.30 says make them
    data), and band edges are a design call; --roster emits a mechanical split of PokéRogue's own
    ordering, clearly labelled as a starting point, with an empty held-item block to fill in.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_RESOURCES = os.path.join(ROOT, "custom-mods/cobblemon-roguelite/src/main/resources")

DEFAULT_SOURCE = (
    "https://raw.githubusercontent.com/pagefaultgames/pokerogue/beta/src/data/balance/signature-species.ts"
)

# Where a Gradle build leaves the Cobblemon jar. Searched, not required: --cobblemon-jar wins, and a
# path that does not exist is a clear error rather than a silent fallback to "no validation".
JAR_GLOB = os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1/com.cobblemon/neoforge/*/*/neoforge-*.jar"
)

# PokéRogue spells a regional form as a prefix on the species name; Cobblemon spells it as an ASPECT
# on the base species, and the aspect is what a PokemonProperties string carries. The two are not
# guessable from each other, so the mapping is written out and then CHECKED against the species file:
# a prefix whose aspect the species does not actually have is reported, never assumed.
REGIONAL_PREFIXES = {
    "GALAR": "galarian",
    "ALOLA": "alolan",
    "HISUI": "hisuian",
    "PALDEA": "paldean",
}

# A run's own default schedule (WaveCompositionConfig). Only used by --roster, to cut band edges.
RUN_LENGTH = 200

# Kept out of filler pools. See filler_slots().
FILLER_EXCLUDED_LABELS = {"legendary", "mythical", "restricted", "paradox", "ultra_beast"}


# ---------------------------------------------------------------- their file


def fetch_source(source: str) -> str:
    """Their signature-species.ts, from a URL or a local path.

    A local path exists for working offline and for pinning a known revision; it is NOT an invitation
    to keep a copy in the repo (see the licensing note above).
    """
    if os.path.exists(source):
        with open(source, encoding="utf-8") as handle:
            return handle.read()
    request = urllib.request.Request(
        source,
        # raw.githubusercontent is fine with anything, but our LAN's WAF is not — the same
        # browser-UA workaround the R2 fetches need.
        headers={"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8")


ENTRY_RE = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\s*:\s*\[(.*)\],?\s*(?://.*)?$")
SPECIES_RE = re.compile(r"SpeciesId\.([A-Z0-9_]+)")


def parse_signature_species(text: str) -> "list[tuple[str, list[list[str]]]]":
    """Their table as [(TRAINER_KEY, [slot, ...])], a slot being a list of SpeciesId names.

    Parsed line-wise with a regex rather than by evaluating the TypeScript. The file is one entry per
    line and has been for its whole history, and a real TS parse would drag a toolchain in for a
    format whose only variation is `X` vs `[X, Y]`. If they ever reformat it, this returns fewer
    trainers than expected and --expect catches it — which is why --expect exists.
    """
    trainers: list[tuple[str, list[list[str]]]] = []
    for line in text.splitlines():
        match = ENTRY_RE.match(line)
        if not match:
            continue
        key, body = match.group(1), match.group(2)
        slots: list[list[str]] = []
        # Split the slot list on top-level commas: a bracketed group is one slot with alternatives.
        depth = 0
        current = ""
        for char in body:
            if char == "[":
                depth += 1
            elif char == "]":
                depth -= 1
            if char == "," and depth == 0:
                slots.append(SPECIES_RE.findall(current))
                current = ""
            else:
                current += char
        slots.append(SPECIES_RE.findall(current))
        slots = [slot for slot in slots if slot]
        if slots:
            trainers.append((key, slots))
    return trainers


# ---------------------------------------------------------------- our species


class Cobblemon:
    """Cobblemon's species data, read straight out of the jar.

    Everything this script has to be right about lives in here: which species exist, which regional
    aspects they actually have, and what evolves into what. Reading the jar rather than a checked-in
    list is deliberate — a hand-maintained species list goes stale silently, and the failure it
    produces (a slot mapped to a species this server does not have) only shows up mid-run.
    """

    def __init__(self, jar_path: str) -> None:
        self.by_id: dict[str, dict] = {}
        with zipfile.ZipFile(jar_path) as jar:
            for name in jar.namelist():
                if not name.startswith("data/cobblemon/species/") or not name.endswith(".json"):
                    continue
                species_id = os.path.basename(name)[:-len(".json")]
                self.by_id[species_id] = json.loads(jar.read(name).decode("utf-8"))
        if not self.by_id:
            raise SystemExit(f"{jar_path} contains no data/cobblemon/species — is that a Cobblemon jar?")

    def form_with_aspect(self, species_id: str, aspect: str) -> "dict | None":
        for form in self.by_id[species_id].get("forms", []):
            if aspect in (form.get("aspects") or []):
                return form
        return None

    def types_of(self, species_id: str) -> "set[str]":
        species = self.by_id.get(species_id) or {}
        return {t for t in (species.get("primaryType"), species.get("secondaryType")) if t}

    def labels_of(self, species_id: str) -> "set[str]":
        return set((self.by_id.get(species_id) or {}).get("labels") or [])


def normalise(pokerogue_name: str) -> str:
    """Their SPECIES_NAME to a Cobblemon species id.

    Their names are the English name in upper snake case; Cobblemon's ids are the same name lowercased
    with every separator removed — MR_MIME/mrmime, HO_OH/hooh, NIDORAN_F/nidoranf, JANGMO_O/jangmoo.
    So the rule is "lowercase and drop the underscores", and every result is then checked against the
    real species list, because a transliteration rule that is right 98% of the time is a rule that
    ships two wrong Pokémon.
    """
    return pokerogue_name.lower().replace("_", "")


def resolve_species(name: str, cobblemon: Cobblemon) -> "tuple[str, str | None, str | None]":
    """(species_id, aspect, problem) for one PokéRogue name.

    `problem` non-None means DO NOT USE THIS: either Cobblemon has no such species, or it has the
    species but not the regional form asked for. The second case is the one worth the extra code —
    falling back to the base species would put a Water Corsola in a Ghost leader's team and look like
    a balance mistake rather than a data one.
    """
    prefix, _, rest = name.partition("_")
    aspect = REGIONAL_PREFIXES.get(prefix)
    if aspect:
        species_id = normalise(rest)
        if species_id not in cobblemon.by_id:
            return species_id, aspect, f"no Cobblemon species '{species_id}' (from {name})"
        if cobblemon.form_with_aspect(species_id, aspect) is None:
            return species_id, aspect, (
                f"Cobblemon's '{species_id}' has no '{aspect}' form, so {name} would silently become "
                f"the base form — a different Pokémon with different types"
            )
        return species_id, aspect, None

    species_id = normalise(name)
    if species_id not in cobblemon.by_id:
        return species_id, None, f"no Cobblemon species '{species_id}' (from {name})"
    return species_id, None, None


# ---------------------------------------------------------------- evolution lines


def stage_text(namespace_id: str, properties: str) -> str:
    """One stage as a PokemonProperties fragment — `cobblemon:geodude alolan`.

    Cobblemon's own species files write evolution results in exactly this form, so the fragments here
    are theirs, carried through rather than re-derived. That matters for the awkward ones: Toxtricity
    is `toxtricity punk_form=amped`, and no naming rule would have produced that.
    """
    return f"cobblemon:{namespace_id}{' ' + properties if properties else ''}".strip()


def split_result(result: str) -> "tuple[str, str]":
    """A Cobblemon evolution result string into (species id, properties fragment)."""
    parts = result.split()
    return parts[0], " ".join(parts[1:])


def evolution_lines(species_id: str, aspect: "str | None", cobblemon: Cobblemon) -> "list[list[str]]":
    """Every full line through this species, base form first, as stage fragments.

    Walks DOWN to the base first and only then UP, and the direction matters: walking up from the
    base would branch into every eeveelution for a leader whose signature species is Sylveon. The
    signature species pins the branch; the down-walk only ever adds the stages below it.

    Branching upward (Tyrogue, Wooper, Sneasel) returns one line per branch. Those become weighted
    alternatives so a branchy species does not out-draw the plain one beside it in the same slot.
    """
    source = cobblemon.by_id[species_id]
    form = cobblemon.form_with_aspect(species_id, aspect) if aspect else None
    # A regional form's evolutions and pre-evolution are read from the FORM only. Falling back to the
    # base species' would evolve Galarian Meowth into Persian.
    node = form if form is not None else source

    aspect_properties = " ".join(form.get("aspects", [])) if form is not None else ""
    head = stage_text(species_id, aspect_properties)

    below: list[str] = []
    pre = node.get("preEvolution")
    seen = {species_id}
    while pre:
        pre_id, pre_properties = split_result(pre)
        if pre_id in seen or pre_id not in cobblemon.by_id:
            break
        seen.add(pre_id)
        below.insert(0, stage_text(pre_id, pre_properties))
        pre_form = None
        if pre_properties:
            for candidate in cobblemon.by_id[pre_id].get("forms", []):
                if set(candidate.get("aspects") or []) & set(pre_properties.split()):
                    pre_form = candidate
                    break
        pre = (pre_form or cobblemon.by_id[pre_id]).get("preEvolution")

    def upward(current_id: str, current_node: dict, prefix: "list[str]") -> "list[list[str]]":
        results = []
        for result in dict.fromkeys(
            evolution.get("result") for evolution in current_node.get("evolutions", []) if evolution.get("result")
        ):
            next_id, next_properties = split_result(result)
            if next_id not in cobblemon.by_id or next_id in prefix_ids(prefix):
                continue
            stage = stage_text(next_id, next_properties)
            next_form = None
            for candidate in cobblemon.by_id[next_id].get("forms", []):
                if set(candidate.get("aspects") or []) & set(next_properties.split()):
                    next_form = candidate
                    break
            results.extend(
                upward(next_id, next_form or cobblemon.by_id[next_id], prefix + [stage])
            )
        return results or [prefix]

    def prefix_ids(prefix: "list[str]") -> "set[str]":
        return {stage.split()[0].split(":")[1] for stage in prefix}

    return [below + line for line in upward(species_id, node, [head])]


# ---------------------------------------------------------------- our format


def slot_json(names: "list[str]", cobblemon: Cobblemon, problems: "list[str]", who: str) -> "dict | None":
    """One party slot: every alternative, each with its line and a weight.

    The weight is what keeps PokéRogue's intent when a species branches. Their `[OMANYTE, KABUTO]` is
    a coin flip; expanding Tyrogue into three lines and dropping them in flat would make a slot that
    was meant to be 50/50 into 25/75. So each PokéRogue alternative gets one unit of weight, split
    evenly across the branches it expands into.
    """
    alternatives = []
    for name in names:
        species_id, aspect, problem = resolve_species(name, cobblemon)
        if problem:
            problems.append(f"{who}: {problem}")
            continue
        lines = evolution_lines(species_id, aspect, cobblemon)
        weight = round(1.0 / len(lines), 4)
        for line in lines:
            alternatives.append({"line": line, "weight": weight})
    if not alternatives:
        return None
    return {"alternatives": alternatives}


def filler_slots(
    signature: "list[dict]", cobblemon: Cobblemon, count: int, exclude: "set[str]"
) -> "list[dict]":
    """Extra slots for the bands whose party size is 5 or 6, type-matched to the leader.

    A signature table has four slots and §2.30 wants six-Pokémon parties late, so the two extra ones
    have to come from somewhere. PokéRogue fills them from a type-filtered pool, and this is that
    idea at the resolution we can afford: species sharing a type with the leader's own, sampled at a
    stride across the dex so the filler is not four Kanto Pokémon every time.

    Not a hand-written pool, and not one either: with --filler 0 there is none, and a band that asks
    for six then gets four. That is the honest failure — a smaller party, not an invented one.
    """
    if count <= 0:
        return []
    types: set[str] = set()
    for slot in signature:
        for alternative in slot["alternatives"]:
            final = alternative["line"][-1].split()[0].split(":")[1]
            types |= cobblemon.types_of(final)
    if not types:
        return []

    candidates = sorted(
        species_id
        for species_id, species in cobblemon.by_id.items()
        if species_id not in exclude
        and not species.get("preEvolution")
        and cobblemon.types_of(species_id) & types
        and species.get("nationalPokedexNumber")
        # A type filter alone hands Morty an Entei and Valerie a Jirachi. Legendaries are the
        # server's monument content (they are not gym-leader filler), and paradox/ultra beasts read
        # as a bug in a Kanto gym. Cobblemon labels all four, so the exclusion is data, not a list.
        and not cobblemon.labels_of(species_id) & FILLER_EXCLUDED_LABELS
    )
    if not candidates:
        return []
    stride = max(1, len(candidates) // count)
    picked = candidates[::stride][:count]

    slots = []
    for species_id in picked:
        lines = evolution_lines(species_id, None, cobblemon)
        weight = round(1.0 / len(lines), 4)
        slots.append({"alternatives": [{"line": line, "weight": weight} for line in lines]})
    return slots


def trainer_id(key: str, namespace: str, prefix: str) -> str:
    return f"{namespace}:{prefix}{key.lower()}"


def build_entries(
    trainers: "list[tuple[str, list[list[str]]]]",
    cobblemon: Cobblemon,
    namespace: str,
    prefix: str,
    filler: int,
    problems: "list[str]",
) -> "list[dict]":
    entries = []
    for key, slots in trainers:
        signature = []
        for index, names in enumerate(slots):
            slot = slot_json(names, cobblemon, problems, f"{key} slot {index + 1}")
            if slot:
                signature.append(slot)
        if not signature:
            problems.append(f"{key}: every slot failed to resolve — the trainer would have no team")
            continue
        used = {
            alternative["line"][stage].split()[0].split(":")[1]
            for slot in signature
            for alternative in slot["alternatives"]
            for stage in range(len(alternative["line"]))
        }
        entry = {
            "_pokerogue": key,
            "trainer": trainer_id(key, namespace, prefix),
            "signature": signature,
        }
        extra = filler_slots(signature, cobblemon, filler, used)
        if extra:
            entry["filler"] = extra
        entries.append(entry)
    return entries


def build_roster(entries: "list[dict]", boss_share: int) -> dict:
    """The entries wrapped in a loadable roster, band edges cut mechanically.

    Bands name trainer IDS, exactly as they always have — the signature data sits in its own
    `generated` block and is joined to a band by id. That split is the schema's, not this script's:
    a leader that appears in both a trainer band and a boss band must not have two copies of its
    signature to keep in step, and a band whose entries are all authored stays byte-identical to what
    ops/gen_roguelite_roster.py writes.

    The cut is by thirds of PokéRogue's own ordering — which is region order, and only roughly a
    difficulty order. It is a starting point that loads, not a schedule: the `_comment` says so, and
    §2.19's twenty boss waves against a small late band is exactly the kind of thing that has to be
    re-cut by hand once somebody has played it.
    """
    bosses = entries[-boss_share:] if boss_share else []
    regulars = entries[: len(entries) - len(bosses)] or entries

    def cut(items: "list[dict]", parts: int) -> "list[list[dict]]":
        size = max(1, (len(items) + parts - 1) // parts)
        return [items[i : i + size] for i in range(0, len(items), size)] or [[]]

    edges = [(1, 59), (60, 119), (120, None)]
    bands = []
    for kind, pool in (("trainer", regulars), ("boss", bosses or regulars)):
        for (minimum, maximum), chunk in zip(edges, cut(pool, len(edges))):
            band = {
                "id": f"{kind}_{minimum}",
                "kind": kind,
                "min_wave": minimum,
                "trainers": [entry["trainer"] for entry in chunk],
            }
            if maximum is not None:
                band["max_wave"] = maximum
            bands.append(band)

    return {
        "_comment": [
            "GENERATED by ops/gen_pokerogue_roster.py from PokeRogue's signature-species table.",
            "Server-side datapack content: do NOT commit this into the mod's resources (plan 2.7).",
            "",
            "The band edges below are a MECHANICAL split of PokeRogue's own trainer ordering, not a",
            "designed schedule - re-cut them once the run has been played. Which leader appears when",
            "is a content decision this script deliberately does not make.",
            "",
            "generation.held_items is EMPTY. Held items are generated per member and scaled by band",
            "and boss status, but WHICH items is content - fill it in, or trainers carry nothing.",
        ],
        "authored_for": {"run_length": RUN_LENGTH, "trainer_interval": 5, "boss_interval": 10},
        "generation": {
            "party_size": [
                {"min_wave": 1, "size": 4},
                {"min_wave": 60, "size": 5},
                {"min_wave": 120, "size": 6},
            ],
            "evolution": {"stage_waves": [20], "fully_evolved_from": 80},
            "held_items": [],
        },
        "generated": entries,
        "bands": bands,
        "fixed": [],
    }


# ---------------------------------------------------------------- entry point


def find_jar(explicit: "str | None") -> str:
    if explicit:
        if not os.path.exists(explicit):
            raise SystemExit(f"--cobblemon-jar {explicit} does not exist")
        return explicit
    matches = sorted(glob.glob(JAR_GLOB))
    if not matches:
        raise SystemExit(
            "no Cobblemon jar found in the Gradle cache — build a module once, or pass "
            "--cobblemon-jar. Species names cannot be checked without it, and an unchecked "
            "mapping is how a slot silently becomes the wrong Pokémon."
        )
    return matches[-1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", default=DEFAULT_SOURCE, help="URL or local path to signature-species.ts")
    parser.add_argument("--cobblemon-jar", help="Cobblemon jar to read species data from")
    parser.add_argument("--out", required=True, help="where to write the JSON")
    parser.add_argument("--namespace", default="cobblemon_roguelite", help="namespace for generated trainer ids")
    parser.add_argument("--trainer-prefix", default="rgl_", help="prefix for generated trainer ids")
    parser.add_argument("--filler", type=int, default=6, help="type-matched filler slots per trainer (0 = none)")
    parser.add_argument("--roster", action="store_true", help="emit a loadable roster, not just entries")
    parser.add_argument("--boss-share", type=int, default=12, help="--roster: trainers reserved for boss bands")
    parser.add_argument("--expect", type=int, default=60, help="fail if fewer trainers than this were parsed")
    parser.add_argument("--allow-missing", action="store_true", help="write the file even with unresolved species")
    args = parser.parse_args()

    out = os.path.abspath(args.out)
    if out.startswith(MOD_RESOURCES):
        # §2.7: the mod is a published build and the transcription is private server content. Blocked
        # here rather than left to review, because the mistake is one --out away and invisible after.
        raise SystemExit(
            "refusing to write into the mod's resources — this output is PokeRogue-derived data and "
            "must stay in a server-side datapack (plan §2.7)"
        )

    cobblemon = Cobblemon(find_jar(args.cobblemon_jar))
    trainers = parse_signature_species(fetch_source(args.source))
    print(f"parsed {len(trainers)} trainers from {args.source}", file=sys.stderr)
    if len(trainers) < args.expect:
        raise SystemExit(
            f"only {len(trainers)} trainers parsed, expected at least {args.expect} — their file has "
            f"probably been reformatted and parse_signature_species needs updating"
        )

    problems: list[str] = []
    entries = build_entries(
        trainers, cobblemon, args.namespace, args.trainer_prefix, args.filler, problems
    )

    if problems:
        print(f"\n{len(problems)} species could not be mapped to Cobblemon:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        print(
            "\nEach of these is a MISSING SLOT — the trainer is a Pokemon short and nobody will "
            "notice in play. Fix the mapping, or pass --allow-missing to accept it.",
            file=sys.stderr,
        )
        if not args.allow_missing:
            return 2

    # Without --roster the output is just the `generated` block, keyed as it is in a roster file so it
    # can be pasted into one that already has its bands the way somebody wants them.
    document = build_roster(entries, args.boss_share) if args.roster else {"generated": entries}
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(document, handle, indent=2)
        handle.write("\n")

    slots = sum(len(entry["signature"]) for entry in entries)
    print(
        f"wrote {out}: {len(entries)} trainers, {slots} signature slots"
        + (f", {args.filler} filler slots each" if args.filler else ""),
        file=sys.stderr,
    )
    print(
        "The trainer ids above must exist as RCT trainers (skin, name, AI) — the generated team "
        "replaces their authored one, but the NPC is still RCT's. Cross-check with "
        "ops/gen_roguelite_roster.py validate --trainers-dir ...",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
