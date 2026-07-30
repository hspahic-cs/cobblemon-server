#!/usr/bin/env python3
"""Turn PokéRogue's starter economy into the roguelite's server-side starter datapack.

Three things come out of PokéRogue's balance data, and the mod has a loader for two of them:

    starter costs      per species, 1-10 points     -> roguelite/starter_costs/<name>.json
    passives           per species, one ability     -> roguelite/hidden_abilities/<name>.json
    candy prices       per COST, not per species    -> no loader exists; see CANDY below

    python3 ops/gen_pokerogue_starters.py

WHERE THEIR STARTER TABLE ACTUALLY LIVES (this moved, and the old answer is still all over the web)
    There is no `speciesStarterCosts` map any more. `src/data/balance/starters.ts` now holds only the
    candy/friendship curves, and the per-species cost is a `starterCost:` field on the species entry
    in `src/data/balance/species/generation-0N.ts` — nine files, ~3 MB, all of which have to be read.
    The field appears only on the entry that is its own line's starter (Bulbasaur has it, Ivysaur does
    not), which is what makes "every priced species" and "PokéRogue's starter list" the same set. That
    is why this script emits no separate starter list: the `costs` array *is* the list.

THE STARTER LIST HAS NO POOL LOADER, AND THAT IS NOT WHAT THIS FILE DOES
    Pricing a species does not make it startable. Eligibility is `StarterPoolSource` (§2.15's baseline
    pool plus the player's Pokédex) and `LabelStarterExclusion`, neither of which reads a datapack
    today — the pool is still `PlaceholderStarterPoolSource`. So this table prices 500-odd species and
    a player can pick from whichever of them the dex lets them; the legendaries in it are priced and
    permanently unpickable. The run prints how many of those there are, because "I shipped their table
    and Mewtwo is startable" is the fear, and it is unfounded for a reason worth restating.

FORMS ARE THE TRAP, AND THEY ARE FATAL RATHER THAN MERELY WRONG
    PokéRogue gives a regional form its own SpeciesId and its own price: ALOLA_VULPIX is 3 and VULPIX
    is 2. Both of our tables key on a bare species id with no aspect, so both would land on
    `cobblemon:vulpix` — and `StarterCostTables.parse` treats a species priced twice in one file as
    FATAL and drops the entire file. One unhandled Alolan form is therefore not a wrong price, it is
    every price gone with one line in the log. They are dropped here and listed at the end, along with
    the three form-starters that are not regional at all (Eternal Floette, Battle Bond Greninja,
    Bloodmoon Ursaluna — Cobblemon has each of them as an aspect, and an aspect is exactly what these
    tables cannot say).

PASSIVES: OURS REPLACES, THEIRS ADDS (§2.27)
    Their passive is a SECOND ability stacking on the Pokémon's own; ours is the one ability Showdown
    can apply. So importing their assignments is not a port, it is a re-reading: Bulbasaur here gets
    Grassy Surge *instead of* Overgrow, where in PokéRogue it would have both. The list is still worth
    taking — it is a hand-made judgement about what each species should get, which is the thing
    §2.27's table exists to hold — but it is a balance decision to review, not a transcription to
    trust. It is OFF by default and enabled with
    `--hidden-abilities`: their passive is an *additional* ability while §2.27's unlock *replaces* the
    base one, so importing their assignments is a balance re-reading, not a port. With it off, §2.27
    falls back to each species' real Cobblemon hidden ability.

    Four of their passives are abilities PokéRogue invented (Dragonize, Fire Mane, Spicy Spray).
    Nothing in Showdown implements them, and `HiddenAbilityTables` deliberately does not validate
    ability names at load time, so emitting one would be a species whose unlock is bought, granted,
    and silently does nothing. They are checked against Cobblemon's own Showdown ability list here.

CANDY — READ THIS BEFORE LOOKING FOR THE FILE THAT LOADS IT
    `CandyRules` and `CandyPrices` are constructor values set by the host mod through
    `ProgressionSettings.set`. There is no datapack registry for them, so there is nowhere for this
    script to emit data that anything parses, and inventing a folder would produce a file that reloads
    beautifully and changes nothing. The numbers are written to `candy-prices.reference.json` at the
    datapack ROOT — outside `data/`, so Minecraft never reads it — for whoever wires the host mod up.

    Two of their four curves do not fit the current data classes at all, and the reference file says so
    per field: `costReductionCandy` is one flat list where theirs is per starter cost, and `eggCandy` is
    one integer where theirs is per starter cost and per hatch count. Reported, not reshaped.

LICENSING (plan §2.7) — same split as ops/gen_pokerogue_roster.py
    PokéRogue is AGPL-3.0-only and their docs are CC-BY-NC-SA-4.0. Their files are FETCHED at run time
    and never vendored: do not commit a copy under ops/data/, do not add one to the modpack. The OUTPUT
    is server-side datapack content and must not reach
    custom-mods/cobblemon-roguelite/src/main/resources, which is a published build — the mod ships the
    schema and falls through to `DerivedStarterCost` for every species. This script refuses an
    `--out-dir` under the mod's resources for that reason.
"""
from __future__ import annotations

import argparse
import glob
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_RESOURCES = os.path.join(ROOT, "custom-mods/cobblemon-roguelite/src/main/resources")

DEFAULT_OUT_DIR = os.path.join(
    ROOT, "modpack/server-overrides/datapacks/server-roguelite-starters"
)

DEFAULT_SOURCE = "https://raw.githubusercontent.com/pagefaultgames/pokerogue/beta/src/data/balance"

# Their species data is split one file per generation, and the cost we want is in all nine of them.
# Spelled out rather than globbed because a missing file has to be an error: nine files that parse and
# one that 404s would look exactly like "generation 5 has no starters".
GENERATION_FILES = [f"species/generation-{index:02d}.ts" for index in range(1, 10)]
CANDY_FILE = "starters.ts"

# Where a Gradle build leaves the Cobblemon jar. Searched, not required: --cobblemon-jar wins, and a
# path that does not exist is a clear error rather than a silent fallback to "no validation".
JAR_GLOB = os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1/com.cobblemon/neoforge/*/*/neoforge-*.jar"
)

# ops/gen_pokerogue_roster.py's mapping, for its reason: PokéRogue spells a regional form as a prefix
# on the species name, Cobblemon spells it as an aspect on the base species, and neither is guessable
# from the other. Here the mapping is used only to RECOGNISE such a name so it can be reported —
# see resolve_species() and the FORMS note above.
REGIONAL_PREFIXES = {
    "GALAR": "galarian",
    "ALOLA": "alolan",
    "HISUI": "hisuian",
    "PALDEA": "paldean",
}

# §2.13's ban, as LabelStarterExclusion.EXCLUDED_LABELS spells it. Only used to count how many of the
# prices we emit are inert. Kept in step with the Kotlin by hand; being out of step misreports a
# number in a log line and breaks nothing.
BANNED_LABELS = {"legendary", "mythical", "paradox", "ultra_beast", "ultrabeast"}

PACK_FORMAT = 48


# ---------------------------------------------------------------- their files


def fetch_source(base: str, relative: str) -> str:
    """One of their files, from a URL base or a local directory.

    A local directory exists for working offline and for pinning a known revision; it is NOT an
    invitation to keep a copy in the repo (see the licensing note above).
    """
    if os.path.isdir(base):
        with open(os.path.join(base, relative), encoding="utf-8") as handle:
            return handle.read()
    request = urllib.request.Request(
        f"{base.rstrip('/')}/{relative}",
        # raw.githubusercontent is fine with anything, but our LAN's WAF is not — the same
        # browser-UA workaround the R2 fetches need.
        headers={"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


# `  generationOneSpeciesData[SpeciesId.BULBASAUR] = {` — the local is named per generation, so the
# middle is a wildcard. Anchored at two spaces of indent because that is the only depth an entry key
# appears at; a match anywhere else would be inside a form or an evolution.
ENTRY_RE = re.compile(r"^ {2}generation[A-Za-z]+SpeciesData\[SpeciesId\.([A-Z0-9_]+)\] = \{")
FIELD_INDENT = " " * 4
ABILITY_RE = re.compile(r"AbilityId\.([A-Z0-9_]+)")
# Inside `passives: { 0: AbilityId.X, ... }` — the key is the form index.
PASSIVE_FORM_RE = re.compile(r"^ {6}(\d+): AbilityId\.([A-Z0-9_]+),?\s*$")
# `    },` — the close of a field-level block. Used to end the passive object rather than ending it on
# the first line that is not a form entry: Scatterbug's block opens with a `// TODO` comment, and a
# parser that bailed there would silently drop its passive and report the species as having none.
BLOCK_END_RE = re.compile(r"^ {4}\}")


class Starter:
    """One priced entry from their species data: what it is called, what it costs, what it gets."""

    def __init__(self, name: str, source: str) -> None:
        self.name = name
        self.source = source
        self.cost: "int | None" = None
        self.passive: "str | None" = None
        self.declared_starter: "str | None" = None
        # True while the passive block is an object we are still reading form indices out of.
        self.in_passive_object = False


def parse_species_files(texts: "dict[str, str]", problems: "list[str]") -> "list[Starter]":
    """Their nine generation files as the priced entries in source order.

    Parsed line-wise with regexes rather than by evaluating the TypeScript, for
    ops/gen_pokerogue_roster.py's reason: a real TS parse drags a toolchain in, and these files are
    machine-formatted by their own lint step, so the fields we want are at a known indent on their own
    line. That assumption is CHECKED rather than trusted — every `starterCost:` and `passives:` in the
    file is counted, and a count that does not match what the line-wise walk picked up means they have
    reformatted and this function needs rewriting. Silently reading 400 of 570 starters would look like
    a Cobblemon gap, which is the one failure that would take a day to find.

    Source order is kept, and kept deliberately: it is generation-then-dex order, so a diff of the
    emitted table reads as "PokéRogue repriced these" instead of as a reshuffle.
    """
    starters: list[Starter] = []
    for relative in GENERATION_FILES:
        text = texts[relative]
        entries = 0
        current: "Starter | None" = None
        picked_costs = 0
        picked_passives = 0
        for line in text.splitlines():
            match = ENTRY_RE.match(line)
            if match:
                entries += 1
                current = Starter(match.group(1), relative)
                starters.append(current)
                continue
            if current is None:
                continue

            if current.in_passive_object:
                if BLOCK_END_RE.match(line):
                    current.in_passive_object = False
                else:
                    form = PASSIVE_FORM_RE.match(line)
                    # Form 0 is the default form and the only one either table can name — see FORMS.
                    if form and form.group(1) == "0":
                        current.passive = form.group(2)
                    continue

            if line.startswith(f"{FIELD_INDENT}starterCost:"):
                number = re.search(r"(\d+)", line)
                if number:
                    current.cost = int(number.group(1))
                    picked_costs += 1
            elif line.startswith(f"{FIELD_INDENT}starter:"):
                named = re.search(r"SpeciesId\.([A-Z0-9_]+)", line)
                if named:
                    current.declared_starter = named.group(1)
            elif line.startswith(f"{FIELD_INDENT}passives:"):
                picked_passives += 1
                ability = ABILITY_RE.search(line)
                if ability:
                    current.passive = ability.group(1)
                else:
                    current.in_passive_object = True

        # The reformat tripwire. Compares against every occurrence anywhere in the file, so a field
        # that moved to a different indent is caught rather than skipped.
        for field, picked in (("starterCost", picked_costs), ("passives", picked_passives)):
            total = len(re.findall(rf"\b{field}:", text))
            if picked != total:
                problems.append(
                    f"{relative}: read {picked} of {total} '{field}:' fields — their formatting has "
                    f"changed and parse_species_files needs updating"
                )
        if entries == 0:
            problems.append(f"{relative}: no species entries matched — ENTRY_RE needs updating")

    return [starter for starter in starters if starter.cost is not None]


CANDY_ROW_RE = re.compile(
    r"\{\s*passive:\s*(\d+),\s*costReduction:\s*\[([\d,\s]+)\],\s*eggCosts:\s*\[([\d,\s]+)\],\s*"
    r"eggCostReductionThresholds:\s*\[([\d,\s]+)\]\s*\}"
)


def parse_candy(text: str, problems: "list[str]") -> dict:
    """Their candy curves out of starters.ts, keyed by starter cost.

    Two shapes in one file, both switched on the starter cost and neither indexed by species:

      * `allStarterCandyCosts` — an array whose INDEX IS COST MINUS ONE (their own getters do
        `[starterCost - 1]`, and the trailing `// 1 Cost` comments are the only label). An off-by-one
        here would silently move every price one band, so the row count is asserted at exactly 10 —
        their `StarterCost` type is `IntClosedRange<1, 10>`.
      * `getStarterValueFriendshipCap` — a switch, with fallthrough cases (8 and 9 share a return) and
        a `default` that doubles as the 10 case. Parsed by collecting pending labels until a return,
        which is the only reading that gets the fallthrough right.
    """
    rows = CANDY_ROW_RE.findall(text)
    if len(rows) != 10:
        problems.append(
            f"starters.ts: found {len(rows)} candy rows, expected exactly 10 (one per starter cost) — "
            f"allStarterCandyCosts has been reshaped and parse_candy needs updating"
        )

    def numbers(text: str) -> "list[int]":
        return [int(part) for part in text.replace(" ", "").split(",") if part]

    candy: dict = {}
    for index, (passive, reduction, egg_costs, egg_thresholds) in enumerate(rows):
        candy[index + 1] = {
            "passive": int(passive),
            "cost_reduction": numbers(reduction),
            "egg_costs": numbers(egg_costs),
            "egg_cost_reduction_thresholds": numbers(egg_thresholds),
        }

    # The friendship switch. `case N:` labels accumulate; the next `return M;` claims all of them.
    caps: dict[int, int] = {}
    body = text.split("getStarterValueFriendshipCap")[-1]
    pending: list[int] = []
    saw_default = False
    for line in body.splitlines():
        stripped = line.strip()
        case = re.match(r"^case (\d+):$", stripped)
        if case:
            pending.append(int(case.group(1)))
            continue
        if stripped.startswith("default:"):
            saw_default = True
            continue
        returned = re.match(r"^return (\d+);$", stripped)
        if returned:
            for cost in pending:
                caps[cost] = int(returned.group(1))
            if saw_default and not pending:
                # A `default` with no case of its own still has to land somewhere, and their own
                # comment says it is the 10 branch.
                caps[10] = int(returned.group(1))
            pending, saw_default = [], False
            if len(caps) >= 10:
                break

    missing = [cost for cost in range(1, 11) if cost not in caps]
    if missing:
        problems.append(
            f"starters.ts: no friendship cap parsed for starter cost(s) {missing} — "
            f"getStarterValueFriendshipCap has been reshaped and parse_candy needs updating"
        )
    for cost, cap in caps.items():
        candy.setdefault(cost, {})["friendship_cap"] = cap
    return candy


# ---------------------------------------------------------------- our species


class Cobblemon:
    """Cobblemon's own data, read straight out of the jar.

    Everything this script has to be right about lives in here: which species exist, which aspects
    they have, which labels §2.13's ban reads, and which abilities Showdown can actually apply.
    Reading the jar rather than a checked-in list is deliberate — a hand-maintained list goes stale
    silently, and the failure it produces only shows up when a player is owed a price.
    """

    def __init__(self, jar_path: str) -> None:
        self.by_id: dict[str, dict] = {}
        self.abilities: set[str] = set()
        with zipfile.ZipFile(jar_path) as jar:
            for name in jar.namelist():
                if name.startswith("data/cobblemon/species/") and name.endswith(".json"):
                    species_id = os.path.basename(name)[: -len(".json")]
                    self.by_id[species_id] = json.loads(jar.read(name).decode("utf-8"))
            # The ability registry is Showdown's, shipped as a zip inside the jar. Species files would
            # have been the easy source, but the union of "abilities somebody has" is not the registry
            # — it would reject a legitimate hand-assignment of an ability no species carries natively,
            # which is precisely what §2.27's table is for.
            self.abilities = self._showdown_abilities(jar)
        if not self.by_id:
            raise SystemExit(f"{jar_path} contains no data/cobblemon/species — is that a Cobblemon jar?")
        if not self.abilities:
            raise SystemExit(f"{jar_path} has no readable Showdown ability list — is that a Cobblemon jar?")

    @staticmethod
    def _showdown_abilities(jar: zipfile.ZipFile) -> "set[str]":
        try:
            payload = jar.read("data/cobblemon/showdown.zip")
        except KeyError:
            return set()
        with zipfile.ZipFile(io.BytesIO(payload)) as showdown:
            text = showdown.read("data/abilities.js").decode("utf-8", "replace")
        # Their bundle is minified-ish JS, so the display names are the only stable handle. Normalised
        # the same way HiddenAbilityUnlock.normalise does, which is what makes the comparison valid.
        return {normalise_ability(name) for name in re.findall(r'name:\s*"([^"]+)"', text)}

    def has_aspect(self, species_id: str, aspect: str) -> bool:
        for form in self.by_id.get(species_id, {}).get("forms", []) or []:
            if aspect in (form.get("aspects") or []):
                return True
        return False

    def aspects_of(self, species_id: str) -> "list[str]":
        found: list[str] = []
        for form in self.by_id.get(species_id, {}).get("forms", []) or []:
            found.extend(form.get("aspects") or [])
        return found

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


def normalise_ability(name: str) -> str:
    """An ability name to the id Cobblemon matches on — `Speed Boost` and `SPEED_BOOST` to speedboost.

    Same normalisation HiddenAbilityUnlock.normalise applies, so an id that survives the check here is
    an id the mod will resolve. Written out rather than reusing normalise() because the inputs differ:
    ability names arrive from their enum (`GRASSY_SURGE`) and from Showdown's display strings
    (`Grassy Surge`), and only one of those is underscore-separated.
    """
    return re.sub(r"[^a-z0-9]", "", name.lower())


# The two ways a starter can fail to be emittable, kept apart because only one of them is a bug.
# UNREPRESENTABLE is permanent and structural: Cobblemon *has* the Pokémon, as an aspect, and this
# schema has nowhere to put an aspect. Nobody can fix it by editing the script, so failing the run
# over it would mean the table could never be generated at all. UNKNOWN is a genuine gap — a species
# this server does not have — and that one stops the run unless it is accepted explicitly.
UNREPRESENTABLE = "unrepresentable"
UNKNOWN = "unknown"


def resolve_species(name: str, cobblemon: Cobblemon) -> "tuple[str | None, str | None, str | None]":
    """(species_id, kind, problem) for one PokéRogue starter name.

    `species_id` None means DO NOT EMIT THIS, and `kind` says which of the two failures it is:

      * [UNREPRESENTABLE] a regional-prefixed name, or another form PokéRogue starters separately —
        Cobblemon has the Pokémon as an aspect on a base species, our tables key on a bare species id,
        and emitting the base id would collide with the base species' own entry and take the whole file
        down with it (FORMS);
      * [UNKNOWN] a name Cobblemon has no species for at all.

    The aspect NAME is reported but never relied on, because it is not reliably derivable: PokéRogue's
    HISUI_BASCULIN is Cobblemon's `whitestriped` (not `hisuian`) and their BATTLE_BOND_GRENINJA is
    `bond` (not `battlebond`). Nothing here is emitted for these, so a wrong guess costs a clearer log
    line and nothing else — which is the only reason the guessing is allowed to be loose at all.
    """
    prefix, _, rest = name.partition("_")
    aspect = REGIONAL_PREFIXES.get(prefix)
    if aspect:
        base = normalise(rest)
        if base not in cobblemon.by_id:
            return None, UNKNOWN, f"{name}: no Cobblemon species '{base}' to carry a regional form"
        if not cobblemon.has_aspect(base, aspect):
            spelt = ", ".join(sorted(cobblemon.aspects_of(base))) or "none"
            return None, UNREPRESENTABLE, (
                f"{name}: '{base}' has no '{aspect}' aspect — Cobblemon spells its forms [{spelt}]. "
                f"Unpriceable either way (species-keyed schema), but the aspect name is not the "
                f"regional one, so a form-aware table could not guess it"
            )
        return None, UNREPRESENTABLE, (
            f"{name}: aspect '{aspect}' on '{base}' — pricing it would price '{base}' twice, and the "
            f"loader treats a species priced twice as fatal for the whole file"
        )

    species_id = normalise(name)
    if species_id not in cobblemon.by_id:
        # Their non-regional form-starters (ETERNAL_FLOETTE, BATTLE_BOND_GRENINJA, BLOODMOON_URSALUNA)
        # land here. Naming the aspect they correspond to matters: it is the difference between "we are
        # missing a Pokémon" and "the schema cannot say this", and only the first is worth chasing.
        for candidate in sorted(cobblemon.by_id, key=len, reverse=True):
            if species_id.endswith(candidate):
                qualifier = species_id[: -len(candidate)]
                aspects = {normalise_ability(a) for a in cobblemon.aspects_of(candidate)}
                # Either direction of containment: Cobblemon writes `flower-eternal` where their name
                # says `ETERNAL`, and `bond` where theirs says `BATTLE_BOND`.
                matched = next(
                    (a for a in aspects if qualifier and (a.endswith(qualifier) or qualifier.endswith(a))),
                    None,
                )
                if matched:
                    return None, UNREPRESENTABLE, (
                        f"{name}: aspect '{matched}' on '{candidate}' — no Cobblemon species "
                        f"'{species_id}' exists, and these species-keyed tables cannot express an aspect"
                    )
                break
        return None, UNKNOWN, f"{name}: no Cobblemon species '{species_id}'"
    return species_id, None, None


# ---------------------------------------------------------------- our format


def species_ref(species_id: str) -> str:
    return f"cobblemon:{species_id}"


def cost_table(
    starters: "list[Starter]", cobblemon: Cobblemon, dropped: "dict[str, list[str]]"
) -> "tuple[dict, list[str]]":
    """The `starter_costs` document, plus the species ids it priced.

    One entry per starter that resolved, in their order. Duplicates cannot happen once forms are out
    (nothing else in their table shares a Cobblemon species id) but are checked anyway, since the
    consequence of one getting through is the whole file being rejected at load.
    """
    costs = []
    emitted: dict[str, int] = {}
    for starter in starters:
        species_id, kind, problem = resolve_species(starter.name, cobblemon)
        if species_id is None:
            dropped[kind].append(problem)
            continue
        if species_id in emitted:
            dropped[UNKNOWN].append(
                f"{starter.name}: '{species_id}' is already priced at {emitted[species_id]} — two of "
                f"their starters map to one Cobblemon species, which the loader treats as fatal"
            )
            continue
        emitted[species_id] = starter.cost
        costs.append({"species": species_ref(species_id), "cost": starter.cost})

    banned = sorted(
        species for species in emitted if cobblemon.labels_of(species) & BANNED_LABELS
    )
    document = {
        "_comment": [
            "GENERATED by ops/gen_pokerogue_starters.py from PokeRogue's per-species starterCost.",
            "Server-side datapack content: do NOT commit this into the mod's resources (plan 2.7).",
            "",
            "This entry list IS PokeRogue's starter list - they price exactly the species that are",
            "startable, one per evolution line. Pricing is NOT eligibility, though: which species a",
            "player may pick is StarterPoolSource (the baseline pool plus their Pokedex, 2.15) and",
            f"LabelStarterExclusion, and {len(banned)} of the species priced here are banned outright by",
            "the latter (legendary/mythical/paradox/ultra beast, 2.13) so their price is inert.",
            "",
            "Costs run 1-10, WIDER than the mod's own DerivedStarterCost fallback (3-7). A 10-point",
            "budget therefore buys more Pokemon here than the derived defaults imply - see the note in",
            "the script and 2.13's 'a team, but rarely six'.",
            "",
            "Regional forms and other alternate-form starters are ABSENT on purpose: they are aspects",
            "in Cobblemon, this schema keys on a bare species id, and pricing the base species twice",
            "makes the loader reject the entire file. Re-run the script to see the list.",
        ],
        "costs": costs,
    }
    return document, sorted(emitted)


def hidden_ability_table(
    starters: "list[Starter]", cobblemon: Cobblemon, priced: "set[str]", problems: "list[str]"
) -> dict:
    """The `hidden_abilities` document: their hand-assigned passive as our unlock ability (§2.27).

    Only for species the cost table priced, so the two files agree about which species exist — an
    assignment for a species nothing prices would be a grant nobody can buy.
    """
    abilities = []
    unimplemented: list[str] = []
    for starter in starters:
        species_id, _, _ = resolve_species(starter.name, cobblemon)
        if species_id is None or species_id not in priced:
            continue
        if not starter.passive:
            problems.append(f"{starter.name}: priced but has no passive — its unlock would grant nothing")
            continue
        ability = normalise_ability(starter.passive)
        if ability not in cobblemon.abilities:
            # Not a problems[] entry: this is their content being wider than Showdown's, not a mapping
            # mistake of ours, and failing the run over it would make the table unshippable forever.
            unimplemented.append(f"{starter.name} -> {starter.passive}")
            continue
        abilities.append({"species": species_ref(species_id), "ability": ability})

    if unimplemented:
        print(
            f"\n{len(unimplemented)} passive(s) name abilities Cobblemon's Showdown does not "
            f"implement, so they are NOT assigned (the unlock would be bought and do nothing):",
            file=sys.stderr,
        )
        for line in unimplemented:
            print(f"  {line}", file=sys.stderr)

    return {
        "_comment": [
            "GENERATED by ops/gen_pokerogue_starters.py from PokeRogue's per-species `passives`.",
            "Server-side datapack content: do NOT commit this into the mod's resources (plan 2.7).",
            "",
            "NOT A STRAIGHT PORT. PokeRogue's passive is a SECOND ability stacking on the Pokemon's",
            "own; this table REPLACES the ability entirely (2.27), because one ability is what",
            "Showdown can apply. So Bulbasaur here gets Grassy Surge INSTEAD OF Overgrow, where in",
            "PokeRogue it would have both. Every line is a balance decision to review, not a",
            "transcription to trust - delete the ones that read as a downgrade.",
            "",
            "Abilities PokeRogue invented (Dragonize, Fire Mane, Spicy Spray) are omitted: nothing",
            "implements them, and the loader does not validate ability names, so the unlock would be",
            "bought and silently do nothing.",
            "",
            "Only species priced by the starter_costs file appear here.",
        ],
        "abilities": abilities,
    }


def candy_reference(candy: dict) -> dict:
    """The candy curves, as a reference file that nothing loads. See CANDY in the module docstring.

    Written per Kotlin field so it can be typed straight into `ProgressionSettings.set`, and with the
    two fields that CANNOT hold their data called out beside the data they cannot hold. The point of
    emitting the unrepresentable half at all is that "their cost reduction varies by starter cost" is
    a fact somebody has to decide about; leaving it out would make the current flat list look adequate.
    """
    costs = sorted(candy)
    return {
        "_comment": [
            "GENERATED by ops/gen_pokerogue_starters.py from PokeRogue's src/data/balance/starters.ts.",
            "",
            "NOTHING LOADS THIS FILE. It sits outside data/ so Minecraft ignores it. CandyRules and",
            "CandyPrices are constructor values set through ProgressionSettings.set, and there is no",
            "datapack registry for them - these numbers are here for whoever wires the host mod up.",
            "",
            "Every key below is indexed by STARTER COST (1-10), which is how PokeRogue indexes all of",
            "it: none of these prices is per species.",
        ],
        "candy_rules": {
            "_comment": [
                "Kotlin: com.cobblemonroguelite.progression.CandyRules.",
                "friendship_threshold_by_cost maps 1:1 onto friendshipThresholdByCost.",
                "NOTE the KDoc there says their curve runs '20 for a 1-cost up to 450 for a 9-cost';",
                "it is 25 at the bottom and there is a 10-cost row at 600 that the doc does not have.",
            ],
            "friendship_threshold_by_cost": {str(c): candy[c]["friendship_cap"] for c in costs},
        },
        "candy_prices": {
            "_comment": [
                "Kotlin: com.cobblemonroguelite.progression.CandyPrices.",
                "hidden_ability_candy_by_cost maps 1:1 onto hiddenAbilityCandyByCost.",
                "NOTE the direction: their passive gets CHEAPER as the starter gets more expensive",
                "(40 candy at 1 cost, 10 at 10 cost). The KDoc predicts the opposite - it expects the",
                "species a cost table marks expensive to have the dearest unlocks.",
            ],
            "hidden_ability_candy_by_cost": {str(c): candy[c]["passive"] for c in costs},
        },
        "_not_representable": {
            "_comment": [
                "Their data, and the field that cannot hold it. Reported rather than reshaped.",
                "",
                "cost_reduction_candy_by_cost: CandyPrices.costReductionCandy is ONE flat List<Int>",
                "  whose length is also the cap on how many reductions a species may buy. Theirs is",
                "  two prices PER STARTER COST. The current default listOf(20, 50) is exactly their",
                "  3-cost row, so a flat list is their table for 3-cost species and wrong elsewhere.",
                "",
                "egg_candy_by_cost: CandyPrices.eggCandy is one nullable Int. Theirs is four prices",
                "  per starter cost, chosen by how many eggs of the species have already hatched",
                "  (egg_cost_reduction_thresholds), so it needs a hatch counter that does not exist -",
                "  SpeciesProgress deliberately keeps no egg count.",
            ],
            "cost_reduction_candy_by_cost": {str(c): candy[c]["cost_reduction"] for c in costs},
            "egg_candy_by_cost": {
                str(c): {
                    "costs": candy[c]["egg_costs"],
                    "hatch_thresholds": candy[c]["egg_cost_reduction_thresholds"],
                }
                for c in costs
            },
        },
    }


def pack_mcmeta() -> dict:
    return {
        "pack": {
            "description": (
                "PokeRogue-derived starter costs and unlock abilities for cobblemon-roguelite "
                "(plan 2.13/2.27). Server-side only. See ops/gen_pokerogue_starters.py."
            ),
            "pack_format": PACK_FORMAT,
        }
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
            "mapping is how a species we do not have ends up priced."
        )
    return matches[-1]


def write_json(path: str, document: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(document, handle, indent=2)
        handle.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--source", default=DEFAULT_SOURCE, help="URL or local dir of their src/data/balance")
    parser.add_argument("--cobblemon-jar", help="Cobblemon jar to read species and abilities from")
    parser.add_argument("--out-dir", default=DEFAULT_OUT_DIR, help="datapack directory to write")
    parser.add_argument("--namespace", default="cobblemon_roguelite", help="datapack namespace to write under")
    parser.add_argument("--file-name", default="pokerogue", help="table file name (its id, minus namespace)")
    # OPT-IN, decided 2026-07-29. PokéRogue's passive is an ADDITIONAL second ability; §2.27's unlock
    # REPLACES the base one. Under our semantics their table hands Bulbasaur Grassy Surge *instead of*
    # Overgrow and Pikachu Transistor instead of Static — abilities balanced as a bonus, imported as a
    # substitution. §2.27 already falls back to each species' own Cobblemon hidden ability when no
    # override exists, which is the slot Game Freak balanced, so the default is to emit nothing.
    parser.add_argument("--hidden-abilities", action="store_true",
                        help="ALSO emit the §2.27 ability table from their passives. Off by default: "
                             "their passive is additive, ours replaces, so this is a balance "
                             "re-reading rather than a port. See the module docstring.")
    parser.add_argument("--expect", type=int, default=500, help="fail if fewer starters than this were parsed")
    parser.add_argument("--allow-missing", action="store_true", help="write the files even with unmapped species")
    args = parser.parse_args()

    out_dir = os.path.abspath(args.out_dir)
    if out_dir.startswith(MOD_RESOURCES):
        # §2.7: the mod is a published build and this is private server content. Blocked here rather
        # than left to review, because the mistake is one --out-dir away and invisible after.
        raise SystemExit(
            "refusing to write into the mod's resources — this output is PokeRogue-derived data and "
            "must stay in a server-side datapack (plan §2.7)"
        )

    cobblemon = Cobblemon(find_jar(args.cobblemon_jar))
    print(
        f"cobblemon: {len(cobblemon.by_id)} species, {len(cobblemon.abilities)} abilities",
        file=sys.stderr,
    )

    fatal: list[str] = []
    texts = {relative: fetch_source(args.source, relative) for relative in GENERATION_FILES}
    starters = parse_species_files(texts, fatal)
    candy = parse_candy(fetch_source(args.source, CANDY_FILE), fatal)
    print(f"parsed {len(starters)} starters and {len(candy)} candy tiers from {args.source}", file=sys.stderr)
    if fatal:
        for line in fatal:
            print(f"  {line}", file=sys.stderr)
        raise SystemExit("their source did not parse as expected — see above; nothing was written")
    if len(starters) < args.expect:
        raise SystemExit(
            f"only {len(starters)} starters parsed, expected at least {args.expect} — their files have "
            f"probably been reformatted and parse_species_files needs updating"
        )
    # Their near-invariant: a priced entry heads its own line. Reported rather than enforced, because
    # the exception is real and deliberate — Pikachu is priced at 4 while its line's `starter` is Pichu,
    # i.e. they let you begin mid-line. The price still means "what starting with THIS species costs",
    # which is all this script needs; the count is printed so a NEW exception is visible.
    midline = [s.name for s in starters if s.declared_starter not in (None, s.name)]
    if midline:
        print(
            f"note: {len(midline)} priced entr(ies) do not head their own evolution line "
            f"({', '.join(sorted(midline))}) — priced anyway, which is their intent",
            file=sys.stderr,
        )

    dropped: dict[str, list[str]] = {UNREPRESENTABLE: [], UNKNOWN: []}
    costs, priced = cost_table(starters, cobblemon, dropped)

    if dropped[UNREPRESENTABLE]:
        print(
            f"\n{len(dropped[UNREPRESENTABLE])} starter(s) the SCHEMA cannot express — Cobblemon has "
            f"each of them as an aspect, and these tables key on a bare species id:",
            file=sys.stderr,
        )
        for problem in dropped[UNREPRESENTABLE]:
            print(f"  {problem}", file=sys.stderr)
        print(
            "  Not an error and not fixable here: the base species keeps its own price and each of "
            "these forms falls through to DerivedStarterCost. Pricing a form needs an aspect field in "
            "StarterCostTable, which is a Kotlin change.",
            file=sys.stderr,
        )

    if dropped[UNKNOWN]:
        print(
            f"\n{len(dropped[UNKNOWN])} starter(s) could not be mapped to a species this server has:",
            file=sys.stderr,
        )
        for problem in dropped[UNKNOWN]:
            print(f"  {problem}", file=sys.stderr)
        print(
            "\nEach of these is a species nobody can be priced for — it falls through to "
            "DerivedStarterCost, which is a placeholder rather than a balance statement. Fix the "
            "mapping, or pass --allow-missing to accept it.",
            file=sys.stderr,
        )
        if not args.allow_missing:
            return 2

    problems: list[str] = []

    data_root = os.path.join(out_dir, "data", args.namespace, "roguelite")
    write_json(os.path.join(out_dir, "pack.mcmeta"), pack_mcmeta())
    write_json(os.path.join(data_root, "starter_costs", f"{args.file_name}.json"), costs)
    print(
        f"wrote {len(costs['costs'])} prices to "
        f"{os.path.relpath(os.path.join(data_root, 'starter_costs', args.file_name + '.json'), ROOT)} "
        f"({args.namespace}:{args.file_name})",
        file=sys.stderr,
    )

    if args.hidden_abilities:
        table = hidden_ability_table(starters, cobblemon, set(priced), problems)
        write_json(os.path.join(data_root, "hidden_abilities", f"{args.file_name}.json"), table)
        print(
            f"wrote {len(table['abilities'])} unlock abilities to "
            f"{os.path.relpath(os.path.join(data_root, 'hidden_abilities', args.file_name + '.json'), ROOT)}",
            file=sys.stderr,
        )

    reference = os.path.join(out_dir, "candy-prices.reference.json")
    write_json(reference, candy_reference(candy))
    print(
        f"wrote {os.path.relpath(reference, ROOT)} — NOT loaded by anything; CandyRules/CandyPrices "
        f"are set in code through ProgressionSettings.set",
        file=sys.stderr,
    )

    if problems:
        print(f"\n{len(problems)} unlock ability problem(s):", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)

    tiers = sorted({entry["cost"] for entry in costs["costs"]})
    print(
        f"\ncost tiers in use: {tiers} — the mod's own DerivedStarterCost fallback only spans "
        f"3-7, so a 10-point budget goes further on this table than on the fallback (§2.13).",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
