#!/usr/bin/env python3
"""Generate and validate cobblemon-roguelite trainer rosters.

A roster answers one question for the roguelite mode: given a wave number and an encounter kind,
WHICH authored trainer does the player fight. It holds trainer ids and nothing else — teams are
authored as RCT trainers in their own datapack, and levels come from the wave curve at battle start.
The file lives at:

    data/<namespace>/roguelite/trainer_rosters/<name>.json

Why this script exists, given the mod already validates on load:

  - A roster hole is only discoverable by reaching it. The mod reports one at load, but "at load"
    means after a deploy; this reports it before the commit.
  - The mod CANNOT check that a trainer id names a real trainer — it never sees RCTmod's registry
    (their licence is unverified, so they stay a soft dependency and are never compiled against).
    `--trainers-dir` closes that gap here, where an author can act on it: an id naming nothing loads
    perfectly and fails hours later at summon time.
  - Hand-writing 200 waves of band edges is how off-by-one gaps get in.

The Kotlin loader is authoritative; this mirrors its rules so the answer is the same in both places.
The unit tests in TrainerRosterParseTest pin the Kotlin side — if the two ever disagree, the mod is
right and this needs updating.

    # validate every roster shipped in the mod (the default)
    python3 ops/gen_roguelite_roster.py validate

    # validate a server datapack's rosters, cross-checking ids against its RCT trainers
    python3 ops/gen_roguelite_roster.py validate path/to/roster.json \\
        --trainers-dir modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers

    # generate one, then validate it
    python3 ops/gen_roguelite_roster.py generate --out path/to/classic.json \\
        --band early:trainer:1-60 --pool early=ns:rgl_a,ns:rgl_b \\
        --band earlyboss:boss:1-60 --pool earlyboss=ns:rgl_boss_a \\
        --band late:trainer:61- --pool late=ns:rgl_c \\
        --band lateboss:boss:61- --pool lateboss=ns:rgl_boss_b \\
        --fixed 182:boss=ns:rgl_e4_1

Deliberately NOT in here: any trainer content. Which trainers exist, which band they belong in and
where a ladder sits are the server owner's calls, and the mod ships no schedule for the same reason.
"""
from __future__ import annotations
import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_ROSTERS = os.path.join(
    ROOT, "custom-mods/cobblemon-roguelite/src/main/resources/data/cobblemon_roguelite/roguelite/trainer_rosters"
)

# The shipping schedule. Kept in step with WaveCompositionConfig's defaults: a roster that omits
# `authored_for` is checked against these, exactly as the mod checks it.
DEFAULT_RUN_LENGTH = 200
DEFAULT_TRAINER_INTERVAL = 5
DEFAULT_BOSS_INTERVAL = 10

KINDS = ("trainer", "boss")
# Minecraft's own id grammar. Anything else is rejected by ResourceLocation.tryParse in the mod, so
# rejecting it here keeps the two answers the same.
ID_RE = re.compile(r"^(?:[a-z0-9_.-]+:)?[a-z0-9_.\-/]+$")

BAND_FIELDS = {"id", "kind", "min_wave", "max_wave", "trainers"}
FIXED_FIELDS = {"wave", "kind", "trainer"}
SCHEDULE_FIELDS = {"run_length", "trainer_interval", "boss_interval"}
ROSTER_FIELDS = {"authored_for", "bands", "fixed", "generated", "generation", "rival"}
# §2.36's rival: one character met on a fixed schedule with a team that GROWS. A third mechanism beside
# bands and fixed encounters, because a rival is neither a pool nor a pin — see RivalLadder.kt. The
# checks below mirror the mod's, and the two worth having outside the game are the same two as for
# `generated`: the JOIN (a stage id that also sits in a band pool can be drawn out of order) and the
# ramp (a team one slot short means the rival silently stops growing at the last meeting).
RIVAL_FIELDS = {"meetings", "teams", "party_size"}
MEETING_FIELDS = {"wave", "trainer"}
RIVAL_TEAM_FIELDS = {"id", "slots"}
# The ramp §2.36 describes: two Pokemon at the first meeting, one more each time, capped by Cobblemon's
# party limit. Must stay in step with RivalLadder.FIRST_MEETING_PARTY / MAX_PARTY.
RIVAL_FIRST_MEETING_PARTY = 2
MAX_PARTY = 6
# §2.30's generated teams. The blocks are written by ops/gen_pokerogue_roster.py, not by hand, so what
# is checked here is the join and the shape — the things a hand edit breaks — rather than every value.
ENTRY_FIELDS = {"trainer", "signature", "filler"}
SLOT_FIELDS = {"alternatives"}
ALTERNATIVE_FIELDS = {"line", "weight"}
GENERATION_FIELDS = {"party_size", "evolution", "held_items", "boss_shields"}
BOSS_SHIELD_FIELDS = {"min_wave", "shields", "members"}
# §2.32: one held-item script ships per shield count, so this ceiling is not taste. A higher number
# is a Showdown item id that does not exist — the boss holds nothing, fights unshielded, and NOTHING
# logs, because a Pokemon holding an unknown item is ordinary from Cobblemon's side. Must stay in
# step with BossShields.MAX_SHIELDS and with the boss_shield_*.js files beside it.
MAX_BOSS_SHIELDS = 5


# ---------------------------------------------------------------- validation


class Problems:
    """Every problem in one file, collected rather than raised.

    Same policy as the mod's DataProblems: a roster with four mistakes should be fixable in one
    sitting, not one deploy per mistake.
    """

    def __init__(self, path: str) -> None:
        self.path = path
        self.lines: list[str] = []

    def add(self, where: str, message: str) -> None:
        self.lines.append(f"{self.path} at {where}: {message}" if where else f"{self.path}: {message}")

    def report(self) -> bool:
        for line in self.lines:
            print(f"  ERROR {line}")
        return not self.lines


def unknown_fields(obj: dict, allowed: set[str], where: str, problems: Problems) -> None:
    # Underscore-prefixed keys are where comments go — JSON has none, and a format meant to be
    # hand-edited needs somewhere to explain itself. Everything else unknown is a typo, and a typo
    # that silently takes a default is the failure this whole format is shaped to avoid.
    for key in obj:
        if not key.startswith("_") and key not in allowed:
            problems.add(where, f"unknown field '{key}' (expected: {', '.join(sorted(allowed))})")


def kind_of(wave: int, trainer_interval: int, boss_interval: int) -> str:
    """Which kind the interval schedule makes `wave`. Boss beats trainer where they collide."""
    if wave % boss_interval == 0:
        return "boss"
    if wave % trainer_interval == 0:
        return "trainer"
    return "wild"


def range_text(start: int, end: int | None) -> str:
    if end is None:
        return f"waves {start}+"
    return f"wave {start}" if start == end else f"waves {start}-{end}"


def validate_roster(path: str, doc: dict, trainer_ids: set[str] | None) -> Problems:
    problems = Problems(path)
    unknown_fields(doc, ROSTER_FIELDS, "", problems)

    schedule = doc.get("authored_for") or {}
    if not isinstance(schedule, dict):
        problems.add("authored_for", "expected an object")
        schedule = {}
    else:
        unknown_fields(schedule, SCHEDULE_FIELDS, "authored_for", problems)
    run_length = schedule.get("run_length", DEFAULT_RUN_LENGTH)
    trainer_interval = schedule.get("trainer_interval", DEFAULT_TRAINER_INTERVAL)
    boss_interval = schedule.get("boss_interval", DEFAULT_BOSS_INTERVAL)
    for name, value in (
        ("run_length", run_length),
        ("trainer_interval", trainer_interval),
        ("boss_interval", boss_interval),
    ):
        if not isinstance(value, int) or value < 1:
            problems.add(f"authored_for.{name}", f"must be a whole number of at least 1, was {value!r}")
            return problems

    bands = doc.get("bands")
    if not isinstance(bands, list) or not bands:
        problems.add("bands", "a roster with no bands cannot serve any wave")
        return problems

    parsed: list[dict] = []
    seen_ids: set[str] = set()
    for index, band in enumerate(bands):
        where = f"bands[{index}]"
        if not isinstance(band, dict):
            problems.add(where, "expected an object")
            continue
        unknown_fields(band, BAND_FIELDS, where, problems)
        band_id = band.get("id")
        kind = band.get("kind")
        min_wave = band.get("min_wave", 1)
        max_wave = band.get("max_wave")
        trainers = band.get("trainers")

        if not isinstance(band_id, str) or not band_id.strip():
            problems.add(where, "'id' must be a non-blank string — it is how problems name this band")
            continue
        if band_id in seen_ids:
            problems.add(where, f"duplicate band id '{band_id}'")
            continue
        seen_ids.add(band_id)
        if kind == "rival":
            # The plausible wrong guess rather than a typo, since RIVAL is a real wave kind. A band's
            # whole job is "which of these turns up here", and §2.36's rival has no which.
            problems.add(
                f"{where}.kind",
                "'rival' is not a band kind — a rival is met on a schedule, not drawn from a pool. "
                "Put it in the roster's top-level 'rival' block instead",
            )
            continue
        if kind not in KINDS:
            problems.add(
                f"{where}.kind",
                f"unknown kind {kind!r} (expected {' or '.join(KINDS)}) — wild waves are generated, not authored",
            )
            continue
        if not isinstance(min_wave, int) or min_wave < 1:
            problems.add(f"{where}.min_wave", f"must be at least 1, was {min_wave!r}")
            continue
        if max_wave is not None and (not isinstance(max_wave, int) or max_wave < min_wave):
            problems.add(f"{where}.max_wave", f"{max_wave!r} is before min_wave {min_wave}, so this band never matches")
            continue
        if not isinstance(trainers, list) or not trainers:
            problems.add(f"{where}.trainers", "must name at least one trainer — an empty pool leaves its waves unfought")
            continue
        for i, tid in enumerate(trainers):
            check_id(tid, f"{where}.trainers[{i}]", trainer_ids, problems)

        parsed.append({"id": band_id, "kind": kind, "min": min_wave, "max": max_wave, "trainers": trainers})

    fixed = doc.get("fixed", [])
    if not isinstance(fixed, list):
        problems.add("fixed", "expected a list")
        fixed = []
    by_wave: dict[int, dict] = {}
    for index, entry in enumerate(fixed):
        where = f"fixed[{index}]"
        if not isinstance(entry, dict):
            problems.add(where, "expected an object")
            continue
        unknown_fields(entry, FIXED_FIELDS, where, problems)
        wave = entry.get("wave")
        kind = entry.get("kind")
        trainer = entry.get("trainer")
        if not isinstance(wave, int) or wave < 1:
            problems.add(f"{where}.wave", f"waves are 1-based, was {wave!r}")
            continue
        if kind == "rival":
            # A promotion carries no meeting number, so there would be no party size and no team. Only
            # the `rival` block can say which meeting a wave is.
            problems.add(
                f"{where}.kind",
                "'rival' is not a kind a fixed encounter can declare — a rival's team depends on WHICH "
                "meeting it is, which only the roster's 'rival' block knows",
            )
            continue
        if kind is not None and kind not in KINDS:
            problems.add(f"{where}.kind", f"unknown kind {kind!r} (expected {' or '.join(KINDS)}, or omit it)")
            continue
        check_id(trainer, f"{where}.trainer", trainer_ids, problems)
        if wave in by_wave:
            problems.add(f"{where}.wave", f"wave {wave} already has a fixed encounter ({by_wave[wave]['trainer']})")
            continue
        by_wave[wave] = {"wave": wave, "kind": kind, "trainer": trainer}

    meetings = check_rival(doc, parsed, by_wave, run_length, trainer_interval, boss_interval, trainer_ids, problems)

    check_overlaps(parsed, problems)
    # Rival waves count as coverage. Five of §2.36's six meetings land on SCHEDULED TRAINER waves, so
    # without this a complete roster is told it has five holes — and an author whose trainer band stops
    # at 190 is told to extend it over a wave the rival already owns.
    check_gaps(parsed, set(by_wave) | {m["wave"] for m in meetings}, run_length, trainer_interval, boss_interval, problems)
    check_fixed(by_wave, run_length, trainer_interval, boss_interval, problems)
    # Rival stage ids count as named, so `check_generated` does not also report a generated entry for one
    # as "never fought". It is a mistake, and `check_rival` says so more precisely — one mistake earning
    # two messages is how the sharper of the two gets skimmed past.
    named = (
        {tid for band in parsed for tid in band["trainers"]}
        | {e["trainer"] for e in by_wave.values()}
        | {m["trainer"] for m in meetings}
    )
    check_generated(doc, named, problems)
    return problems


def check_rival(
    doc: dict,
    bands: list[dict],
    fixed: dict[int, dict],
    run_length: int,
    trainer_interval: int,
    boss_interval: int,
    trainer_ids: set[str] | None,
    problems: Problems,
) -> list[dict]:
    """The `rival` block — §2.36's ladder. Returns the parsed meetings, empty when there is none.

    An absent block is NOT a hole, unlike an absent band: §2.14's mode is trainers, bosses and wild
    waves, and the rival is an addition. So nothing is reported for a roster without one.
    """
    rival = doc.get("rival")
    if rival is None:
        return []
    if not isinstance(rival, dict):
        problems.add("rival", "expected an object")
        return []
    unknown_fields(rival, RIVAL_FIELDS, "rival", problems)

    raw_meetings = rival.get("meetings")
    if not isinstance(raw_meetings, list) or not raw_meetings:
        problems.add("rival.meetings", "a rival with no meetings is never met — delete the whole block instead")
        raw_meetings = []

    meetings: list[dict] = []
    for index, meeting in enumerate(raw_meetings):
        where = f"rival.meetings[{index}]"
        if not isinstance(meeting, dict):
            problems.add(where, "expected an object")
            continue
        unknown_fields(meeting, MEETING_FIELDS, where, problems)
        wave = meeting.get("wave")
        if not isinstance(wave, int) or wave < 1:
            problems.add(f"{where}.wave", f"waves are 1-based, was {wave!r}")
            continue
        check_id(meeting.get("trainer"), f"{where}.trainer", trainer_ids, problems)
        meetings.append({"wave": wave, "trainer": meeting.get("trainer")})

    # Ascending and distinct, not sorted for the author: the POSITION in this list is the meeting
    # number, which is what decides how many Pokemon turn up. Sorting would move the party sizes onto
    # different waves, silently and differently from what was written.
    waves = [m["wave"] for m in meetings]
    if waves != sorted(waves):
        problems.add(
            "rival.meetings",
            "must be written in ascending wave order — the position in this list is the meeting number, "
            "which is what decides how many Pokemon the rival brings",
        )
    for wave in sorted({w for w in waves if waves.count(w) > 1}):
        problems.add("rival.meetings", f"two meetings on wave {wave} — the meeting number would be ambiguous")

    party_size = rival.get("party_size") or []
    if not isinstance(party_size, list):
        problems.add("rival.party_size", "expected a list of party sizes, one per meeting")
        party_size = []
    for index, size in enumerate(party_size):
        if not isinstance(size, int) or not 1 <= size <= MAX_PARTY:
            problems.add(f"rival.party_size[{index}]", f"must be 1..{MAX_PARTY}, was {size!r}")

    raw_teams = rival.get("teams")
    if not isinstance(raw_teams, list) or not raw_teams:
        problems.add("rival.teams", "a rival needs at least one team, or it arrives with no Pokemon")
        raw_teams = []
    teams: list[dict] = []
    seen_ids: set = set()
    for index, team in enumerate(raw_teams):
        where = f"rival.teams[{index}]"
        if not isinstance(team, dict):
            problems.add(where, "expected an object")
            continue
        unknown_fields(team, RIVAL_TEAM_FIELDS, where, problems)
        team_id = team.get("id")
        if not isinstance(team_id, str) or not team_id.strip():
            problems.add(f"{where}.id", "must be a non-empty name — it is how these messages identify the team")
            continue
        if team_id in seen_ids:
            problems.add(f"{where}.id", f"duplicate rival team id '{team_id}'")
            continue
        seen_ids.add(team_id)
        slots = team.get("slots")
        if not isinstance(slots, list) or not slots:
            problems.add(
                f"{where}.slots",
                "must name at least one slot — slot one is the rival's starter, the only Pokemon every "
                "meeting has in common",
            )
            continue
        for slot_index, slot in enumerate(slots):
            check_slot(slot, f"{where}.slots[{slot_index}]", problems)
        teams.append({"id": team_id, "slots": len(slots)})

    pooled = {tid for band in bands for tid in band["trainers"]}
    generated_ids = {
        e.get("trainer") for e in (doc.get("generated") or []) if isinstance(e, dict)
    }
    for index, meeting in enumerate(meetings):
        wave = meeting["wave"]
        number = index + 1
        if wave > run_length:
            problems.add(
                "rival",
                f"meeting {number} is at wave {wave}, past the end of the run (run_length={run_length}), "
                f"so the rival's team stops growing at meeting {index}",
            )
            continue
        if wave in fixed:
            problems.add(
                "rival",
                f"wave {wave} is both meeting {number} and a fixed encounter ({fixed[wave]['trainer']}) — "
                f"the fixed encounter wins, so the meeting would silently not happen. Move one of them",
            )
        if kind_of(wave, trainer_interval, boss_interval) == "boss":
            problems.add(
                "rival",
                f"meeting {number} is at wave {wave}, which this schedule makes a boss wave (boss every "
                f"{boss_interval}) — a rival is not a boss, so this removes a boss from the run and the "
                f"meeting takes no boss multiplier and no shields",
            )
        if meeting["trainer"] in pooled:
            problems.add(
                "rival",
                f"stage '{meeting['trainer']}' (meeting {number}) is also in a band pool, so an ordinary "
                f"trainer wave can draw it — the same character out of order, with the wrong meeting's team",
            )
        if meeting["trainer"] in generated_ids:
            problems.add(
                "rival",
                f"stage '{meeting['trainer']}' (meeting {number}) also has a generated entry, which its own "
                f"meeting will never use — a rival's team comes from rival.teams. Delete the generated entry",
            )

    # The ramp, measured against the deepest REACHABLE meeting so that shortening a run does not also
    # demand longer teams for meetings that were just deleted. A team one slot short does not fail: the
    # rival plateaus, which is the mechanic quietly not happening at the point it was meant to pay off.
    reachable = [i for i, m in enumerate(meetings) if m["wave"] <= run_length]
    if reachable and teams:
        deepest = max(reachable)
        if party_size:
            wanted = party_size[min(deepest, len(party_size) - 1)]
        else:
            wanted = min(RIVAL_FIRST_MEETING_PARTY + deepest, MAX_PARTY)
        for team in teams:
            if team["slots"] < wanted:
                problems.add(
                    "rival",
                    f"team '{team['id']}' has {team['slots']} slots but meeting {deepest + 1} "
                    f"(wave {meetings[deepest]['wave']}) asks for {wanted} — the rival would arrive with "
                    f"{team['slots']} Pokemon and stop growing, which is the one thing a rival is for",
                )

    return meetings


def check_generated(doc: dict, named: set, problems: Problems) -> None:
    """The `generated` and `generation` blocks — §2.30's teams-are-generated half of the format.

    Mirrors the mod's own rules, and the one worth having outside the game is the JOIN: a generated
    entry is tied to a fight by trainer id, so `rgl_brock` in the entries and `rgl_borck` in a band
    is a roster that loads, runs, and fights the trainer's authored placeholder team forever. Nothing
    downstream can see that — the id resolves and the battle starts — so only this comparison can.
    """
    entries = doc.get("generated", [])
    if not isinstance(entries, list):
        problems.add("generated", "expected a list")
        entries = []

    seen: set = set()
    for index, entry in enumerate(entries):
        where = f"generated[{index}]"
        if not isinstance(entry, dict):
            problems.add(where, "expected an object")
            continue
        unknown_fields(entry, ENTRY_FIELDS, where, problems)
        trainer = entry.get("trainer")
        check_id(trainer, f"{where}.trainer", None, problems)
        if isinstance(trainer, str):
            if trainer in seen:
                problems.add(f"{where}.trainer", f"trainer '{trainer}' already has a generated entry")
            seen.add(trainer)
            if trainer not in named:
                problems.add(
                    f"{where}.trainer",
                    f"'{trainer}' is never fought: no band lists it and no fixed encounter names it, "
                    f"so its signature species do nothing — check the spelling against the band pools",
                )
        signature = entry.get("signature")
        if not isinstance(signature, list) or not signature:
            problems.add(
                f"{where}.signature",
                "must name at least one slot — for an AUTHORED fight, delete this entry and leave "
                "the id in its band",
            )
            continue
        for kind in ("signature", "filler"):
            for slot_index, slot in enumerate(entry.get(kind) or []):
                check_slot(slot, f"{where}.{kind}[{slot_index}]", problems)

    generation = doc.get("generation")
    if generation is None:
        return
    if not isinstance(generation, dict):
        problems.add("generation", "expected an object")
        return
    unknown_fields(generation, GENERATION_FIELDS, "generation", problems)
    for index, tier in enumerate(generation.get("party_size") or []):
        size = tier.get("size") if isinstance(tier, dict) else None
        if not isinstance(size, int) or not 1 <= size <= 6:
            # Cobblemon's own party limit. A 7 loads and is silently truncated wherever the team is
            # built, which is a difficulty change nobody wrote down.
            problems.add(f"generation.party_size[{index}].size", f"must be 1..6, was {size!r}")
    for index, tier in enumerate(generation.get("held_items") or []):
        chance = tier.get("chance") if isinstance(tier, dict) else None
        if not isinstance(chance, (int, float)) or not 0.0 <= chance <= 1.0:
            problems.add(f"generation.held_items[{index}].chance", f"must be between 0 and 1, was {chance!r}")
        if not tier.get("items"):
            problems.add(f"generation.held_items[{index}].items", "a tier with no items never places anything")
    for index, tier in enumerate(generation.get("boss_shields") or []):
        at = f"generation.boss_shields[{index}]"
        if not isinstance(tier, dict):
            problems.add(at, "expected an object")
            continue
        unknown_fields(tier, BOSS_SHIELD_FIELDS, at, problems)
        shields = tier.get("shields")
        if not isinstance(shields, int) or not 1 <= shields <= MAX_BOSS_SHIELDS:
            problems.add(
                f"{at}.shields",
                f"must be 1..{MAX_BOSS_SHIELDS}, was {shields!r} — there is one held-item script per "
                "shield count, so a higher number is an item Showdown does not have and the boss "
                "would fight with no shields at all",
            )
        members = tier.get("members", 1)
        if not isinstance(members, int) or members < 1:
            problems.add(f"{at}.members", f"must be at least 1, was {members!r} — omit the tier for an unshielded boss")


def check_slot(slot, where: str, problems: Problems) -> None:
    """One party slot: its alternatives, and each alternative's evolution line."""
    if not isinstance(slot, dict):
        problems.add(where, "expected an object")
        return
    unknown_fields(slot, SLOT_FIELDS, where, problems)
    alternatives = slot.get("alternatives")
    if not isinstance(alternatives, list) or not alternatives:
        problems.add(f"{where}.alternatives", "a slot with no alternatives can never be filled")
        return
    for index, alternative in enumerate(alternatives):
        at = f"{where}.alternatives[{index}]"
        if not isinstance(alternative, dict):
            problems.add(at, "expected an object")
            continue
        unknown_fields(alternative, ALTERNATIVE_FIELDS, at, problems)
        line = alternative.get("line")
        if not isinstance(line, list) or not line:
            problems.add(f"{at}.line", "must name at least one species, base form first")
            continue
        for stage_index, stage in enumerate(line):
            # A stage is a properties fragment — `cobblemon:corsola galarian` — and only the first
            # token is an id. The rest is Cobblemon's grammar and is deliberately not parsed here.
            head = stage.split()[0] if isinstance(stage, str) and stage.strip() else stage
            if not isinstance(head, str) or not ID_RE.match(head):
                problems.add(f"{at}.line[{stage_index}]", f"{stage!r} is not a valid species id")
        weight = alternative.get("weight", 1.0)
        if not isinstance(weight, (int, float)) or weight < 0:
            problems.add(f"{at}.weight", f"must not be negative, was {weight!r}")


def check_id(value, where: str, trainer_ids: set[str] | None, problems: Problems) -> None:
    if not isinstance(value, str) or not ID_RE.match(value):
        problems.add(where, f"{value!r} is not a valid id (expected namespace:path)")
        return
    # The check the mod cannot make. Opt-in because a roster legitimately names trainers from a
    # datapack this script was not pointed at.
    if trainer_ids is not None and value not in trainer_ids:
        problems.add(where, f"'{value}' names no trainer in --trainers-dir (it would fail at summon time)")


def check_overlaps(bands: list[dict], problems: Problems) -> None:
    """Two bands of one kind covering a common wave.

    Per pair, not per wave: `1-60` plus `50-120` is one mistake, and sixty lines about it would bury
    every other problem in the file.
    """
    for kind in KINDS:
        of_kind = [b for b in bands if b["kind"] == kind]
        for i, a in enumerate(of_kind):
            for b in of_kind[i + 1:]:
                start = max(a["min"], b["min"])
                ends = [x["max"] for x in (a, b) if x["max"] is not None]
                end = min(ends) if ends else None
                if end is not None and end < start:
                    continue
                problems.add(
                    "bands",
                    f"{kind} bands '{a['id']}' and '{b['id']}' both cover {range_text(start, end)}, "
                    f"where '{b['id']}' can never be drawn — the first matching band wins",
                )


def check_gaps(
    bands: list[dict],
    served: set[int],
    run_length: int,
    trainer_interval: int,
    boss_interval: int,
    problems: Problems,
) -> None:
    """Waves of a kind the roster cannot answer for.

    Only waves the schedule actually produces are required: demanding a boss band over waves 1-9
    under a boss interval of 10 would force a pool that can never be drawn, and content written to
    satisfy a validator is content nobody checks.

    `served` is every wave already answered by something other than a band — fixed encounters, which is
    the entire point of the override, and §2.36's rival meetings, five of which land on scheduled
    trainer waves and would otherwise each be reported as a hole in a complete roster.
    """
    for kind in KINDS:
        of_kind = [b for b in bands if b["kind"] == kind]
        qualifying = [w for w in range(1, run_length + 1) if kind_of(w, trainer_interval, boss_interval) == kind]
        # Adjacency is measured in this list, not in wave numbers. The spacing between waves of one
        # kind is not the interval that produced them — under 5/10 the trainer waves are 5, 15, 25,
        # ten apart, because every other multiple of five is taken by a boss. Grouping by a fixed
        # step reports one missing band as fifteen separate gaps.
        uncovered = [
            i
            for i, wave in enumerate(qualifying)
            if wave not in served
            and not any(b["min"] <= wave and (b["max"] is None or wave <= b["max"]) for b in of_kind)
        ]
        start = 0
        while start < len(uncovered):
            end = start
            while end + 1 < len(uncovered) and uncovered[end + 1] == uncovered[end] + 1:
                end += 1
            first = qualifying[uncovered[start]]
            problems.add(
                "bands",
                f"no {kind} band covers {range_text(first, qualifying[uncovered[end]])} — "
                f"a run reaching wave {first} would have no opponent",
            )
            start = end + 1


def check_fixed(
    fixed: dict[int, dict], run_length: int, trainer_interval: int, boss_interval: int, problems: Problems
) -> None:
    """Fixed encounters that can never fire.

    The undeclared-kind-on-a-wild-wave case is the one that earns this: an author transcribing a
    ladder writes 182, 184, 186, 188, 190, and a typo'd 183 looks exactly like the four correct
    entries either side of it while doing nothing at all.
    """
    for wave in sorted(fixed):
        entry = fixed[wave]
        if wave > run_length:
            problems.add("fixed", f"wave {wave} is past the end of the run (run_length={run_length}) and never fires")
            continue
        scheduled = kind_of(wave, trainer_interval, boss_interval)
        if entry["kind"] is None and scheduled == "wild":
            below = next((w for w in range(wave - 1, 0, -1) if kind_of(w, trainer_interval, boss_interval) != "wild"), None)
            above = next(
                (w for w in range(wave + 1, run_length + 1) if kind_of(w, trainer_interval, boss_interval) != "wild"),
                None,
            )
            near = " or ".join(f"wave {w}" for w in (below, above) if w is not None) or "a trainer or boss wave"
            problems.add(
                "fixed",
                f"wave {wave} is a wild wave under this schedule (trainer every {trainer_interval}, "
                f"boss every {boss_interval}), so this entry never fires. Give it \"kind\" to promote "
                f"the wave, or move it to {near}",
            )
        elif entry["kind"] == "trainer" and scheduled == "boss":
            problems.add(
                "fixed",
                f"wave {wave} overrides a boss wave down to a trainer wave, which removes a boss from "
                f"the run — omit \"kind\" to keep it a boss battle",
            )


# ---------------------------------------------------------------- generation


def parse_band_arg(spec: str) -> dict:
    """`id:kind:min-max`, with `min-` for open-ended."""
    try:
        band_id, kind, waves = spec.split(":", 2)
        start, _, end = waves.partition("-")
        band = {"id": band_id, "kind": kind, "min_wave": int(start), "trainers": []}
        if end:
            band["max_wave"] = int(end)
        return band
    except ValueError:
        raise SystemExit(f"--band expects id:kind:min-max (e.g. early:trainer:1-60), got {spec!r}")


def parse_fixed_arg(spec: str) -> dict:
    """`wave=trainer` to replace, `wave:kind=trainer` to promote a wave the schedule skips."""
    left, _, trainer = spec.partition("=")
    if not trainer:
        raise SystemExit(f"--fixed expects wave[:kind]=trainer, got {spec!r}")
    wave, _, kind = left.partition(":")
    entry = {"wave": int(wave), "trainer": trainer}
    if kind:
        entry["kind"] = kind
    return entry


def trainer_ids_in(directory: str, namespace: str | None) -> set[str]:
    """Trainer ids from an RCT trainer datapack folder, as `<namespace>:<filename>`.

    The namespace defaults to the datapack's own (`data/<ns>/...`), which is what makes pointing at
    `.../data/rctmod/trainers` produce `rctmod:gym_01_rock` without being told.
    """
    ids: set[str] = set()
    resolved = namespace
    if resolved is None:
        parts = os.path.abspath(directory).split(os.sep)
        resolved = parts[parts.index("data") + 1] if "data" in parts else "minecraft"
    for path in glob.glob(os.path.join(directory, "**", "*.json"), recursive=True):
        rel = os.path.relpath(path, directory)[: -len(".json")]
        ids.add(f"{resolved}:{rel.replace(os.sep, '/')}")
    return ids


def build_roster(args) -> dict:
    bands = [parse_band_arg(spec) for spec in args.band]
    by_id = {band["id"]: band for band in bands}

    for spec in args.pool:
        band_id, _, ids = spec.partition("=")
        if band_id not in by_id:
            raise SystemExit(f"--pool names band {band_id!r}, which has no --band")
        by_id[band_id]["trainers"].extend(i for i in ids.split(",") if i)

    for spec in args.pool_from:
        band_id, _, directory = spec.partition("=")
        if band_id not in by_id:
            raise SystemExit(f"--pool-from names band {band_id!r}, which has no --band")
        directory, _, namespace = directory.partition(":")
        by_id[band_id]["trainers"].extend(sorted(trainer_ids_in(directory, namespace or None)))

    doc: dict = {
        "_generated": "ops/gen_roguelite_roster.py — band edges and pools are content, edit freely",
        "authored_for": {
            "run_length": args.run_length,
            "trainer_interval": args.trainer_interval,
            "boss_interval": args.boss_interval,
        },
        "bands": bands,
    }
    fixed = [parse_fixed_arg(spec) for spec in args.fixed]
    if fixed:
        doc["fixed"] = sorted(fixed, key=lambda e: e["wave"])
    return doc


# ---------------------------------------------------------------- entry point


def roster_paths(paths: list[str]) -> list[str]:
    if not paths:
        return sorted(glob.glob(os.path.join(MOD_ROSTERS, "**", "*.json"), recursive=True))
    found: list[str] = []
    for path in paths:
        if os.path.isdir(path):
            found.extend(sorted(glob.glob(os.path.join(path, "**", "*.json"), recursive=True)))
        else:
            found.append(path)
    return found


def run_validate(paths: list[str], trainers_dir: str | None, namespace: str | None) -> int:
    trainer_ids = trainer_ids_in(trainers_dir, namespace) if trainers_dir else None
    if trainer_ids is not None:
        print(f"cross-checking against {len(trainer_ids)} trainer(s) in {trainers_dir}")

    targets = roster_paths(paths)
    if not targets:
        print("no roster files found")
        return 0

    failed = 0
    for path in targets:
        print(f"{path}")
        try:
            doc = json.load(open(path))
        except json.JSONDecodeError as exc:
            print(f"  ERROR {path}: is not valid JSON: {exc}")
            failed += 1
            continue
        if not validate_roster(path, doc, trainer_ids).report():
            failed += 1
        else:
            print("  ok")
    print(f"{len(targets) - failed}/{len(targets)} roster(s) valid")
    return 1 if failed else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    check = sub.add_parser("validate", help="validate roster JSONs (default: the ones shipped in the mod)")
    check.add_argument("paths", nargs="*", help="roster files or directories")
    check.add_argument("--trainers-dir", help="RCT trainer datapack folder to cross-check ids against")
    check.add_argument("--namespace", help="override the namespace derived from --trainers-dir")

    make = sub.add_parser("generate", help="write a roster JSON, then validate it")
    make.add_argument("--out", required=True)
    make.add_argument("--band", action="append", default=[], metavar="ID:KIND:MIN-MAX")
    make.add_argument("--pool", action="append", default=[], metavar="ID=ns:a,ns:b")
    make.add_argument("--pool-from", action="append", default=[], metavar="ID=DIR[:NAMESPACE]")
    make.add_argument("--fixed", action="append", default=[], metavar="WAVE[:KIND]=ns:trainer")
    make.add_argument("--run-length", type=int, default=DEFAULT_RUN_LENGTH)
    make.add_argument("--trainer-interval", type=int, default=DEFAULT_TRAINER_INTERVAL)
    make.add_argument("--boss-interval", type=int, default=DEFAULT_BOSS_INTERVAL)
    make.add_argument("--trainers-dir", help="RCT trainer datapack folder to cross-check ids against")
    make.add_argument("--namespace", help="override the namespace derived from --trainers-dir")

    args = parser.parse_args()
    if args.command == "validate":
        return run_validate(args.paths, args.trainers_dir, args.namespace)

    doc = build_roster(args)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w") as handle:
        json.dump(doc, handle, indent=2)
        handle.write("\n")
    print(f"wrote {args.out}")
    # Always validated on the way out: a generator that can emit a file the loader rejects is a
    # generator that moves the error to a worse place to find it.
    return run_validate([args.out], args.trainers_dir, args.namespace)


if __name__ == "__main__":
    sys.exit(main())
