"""Stateless wrapper around foul-play's parser + poke-engine search.

Each /pick call rebuilds a fresh `Battle` from the request JSON + the full
Showdown event log so far, runs `find_best_move`, and returns the chosen move.

This makes the bridge embarrassingly parallel — N uvicorn workers can each
handle requests independently with no shared state. The cost: re-parsing the
full log every turn. Empirically negligible vs. the MCTS search itself
(microseconds vs. ~1000 ms).

See memory/reference_foul_play_api_mapping.md for the foul-play surface notes.
"""

from __future__ import annotations

import collections
import hashlib
import json
import logging
import os
import random
import re
import threading
import time
import traceback
from concurrent.futures import ProcessPoolExecutor
from concurrent.futures.process import BrokenProcessPool
from copy import deepcopy
from dataclasses import asdict, dataclass

import constants
from constants import BattleType
from data.pkmn_sets import SmogonSets, TeamDatasets
from fp.battle import Battle, Pokemon
from fp.battle_modifier import process_battle_updates
from fp.helpers import calculate_stats, normalize_name
from fp.search.main import find_best_move
from fp.search.poke_engine_helpers import battle_to_poke_engine_state
from poke_engine import (
    State as PokeEngineState,
    iterative_deepening_expectiminimax,
    monte_carlo_tree_search,
)

logger = logging.getLogger(__name__)

# foul-play's dataset cache (SmogonSets/TeamDatasets) is module-level and NOT
# thread-safe — the getters return cached lists by reference and _do_check /
# initialize mutate them in place. FastAPI serves the sync /pick endpoint from a
# threadpool, so concurrent gym battles race on that cache (sporadic IndexError /
# KeyError). This serializes the cache-touching battle build per worker PROCESS;
# the expensive MCTS search runs outside it, so parallelism across uvicorn
# workers (separate processes, separate caches) is unaffected.
_CACHE_LOCK = threading.Lock()

# Consolidated, replayable record of picks foul-play couldn't process. Every
# failure (whether we recovered with a degraded pick or it propagated) appends
# one JSON line — the verbatim request plus the error + traceback — to
# <dir>/pick_failures.jsonl. Each line is a complete replayable turn (same
# shape replay.py consumes), so the whole backlog can be reviewed and fixed in
# one place. Defaults to the battle-log dir so it's on wherever battle logging
# is; override with BRIDGE_FAILURE_LOG_DIR.
FAILURE_LOG_DIR = os.environ.get("BRIDGE_FAILURE_LOG_DIR") or os.environ.get(
    "BRIDGE_BATTLE_LOG_DIR", ""
)
FAILURE_LOG_NAME = "pick_failures.jsonl"

# Disk hygiene on the small per-pod PVC: the verbose per-battle <id>.jsonl logs
# are bulky and disposable; pick_failures.jsonl is small and the batch-fix
# backlog we must never lose. So battle logs are evicted oldest-first to stay
# under LOG_DIR_MAX_BYTES (pick_failures* is always exempt), and the failure log
# itself rolls over to .1 past FAILURE_LOG_MAX_BYTES — bounded, but never
# evictable by battle-log churn. 0 disables either guard.
LOG_DIR_MAX_BYTES = int(os.environ.get("BRIDGE_LOG_MAX_BYTES", str(768 * 1024 * 1024)))
FAILURE_LOG_MAX_BYTES = int(
    os.environ.get("BRIDGE_FAILURE_LOG_MAX_BYTES", str(64 * 1024 * 1024))
)


def _rollover_if_large(path: str, cap: int) -> None:
    """Roll `path` to `path.1` once it reaches `cap` bytes (keeps 2 generations).

    Bounds an append-only log without ever deleting it via battle-log eviction.
    """
    if cap <= 0:
        return
    try:
        if os.path.getsize(path) >= cap:
            os.replace(path, path + ".1")  # atomic; clobbers any prior .1
    except OSError:
        pass  # missing file (first write) or transient fs error — just append


def enforce_battle_log_budget(log_dir: str, max_bytes: int) -> None:
    """Evict oldest per-battle *.jsonl until the dir is under budget.

    pick_failures.jsonl (and its .1 rollover) are exempt — the failure backlog
    is the whole point of the logs and must survive a full disk.
    """
    if not log_dir or max_bytes <= 0:
        return
    try:
        entries = []
        total = 0
        with os.scandir(log_dir) as it:
            for e in it:
                if not e.name.endswith(".jsonl") or e.name.startswith("pick_failures"):
                    continue
                try:
                    st = e.stat()
                except OSError:
                    continue
                entries.append((st.st_mtime, st.st_size, e.path))
                total += st.st_size
        if total <= max_bytes:
            return
        for _mtime, size, fpath in sorted(entries):  # oldest first
            try:
                os.remove(fpath)
            except OSError:
                continue
            total -= size
            if total <= max_bytes:
                break
    except OSError as e:
        logger.warning("battle-log budget sweep failed: %s", e)



def _failure_phase(tb: str) -> str:
    """Coarse stage the pick died in — for grouping the batch-fix backlog.

    "build" = parsing/replaying the log into a foul-play Battle; "search" = the
    poke-engine MCTS/expectiminimax; "other" = anything else (e.g. fallback).
    """
    if "process_battle_updates" in tb or "_build_battle" in tb:
        return "build"
    if "monte_carlo_tree_search" in tb or "_search_mcts" in tb or "find_best_move" in tb:
        return "search"
    return "other"


def _failure_fingerprint(error: str, tb: str) -> str:
    """Stable short id for "the same bug" so failures group for batch-fixing.

    Hash of the exception type plus the deepest frame's file:line — picks
    accumulated over weeks collapse to a handful of fingerprints
    (`jq -r .fingerprint pick_failures.jsonl | sort | uniq -c`). The full battle
    is still in the record for replay; this is just the grouping key.
    """
    exc_type = error.split(":", 1)[0].strip()
    frames = re.findall(r'File "([^"]+)", line (\d+)', tb)
    deepest = f"{frames[-1][0].split('/')[-1]}:{frames[-1][1]}" if frames else ""
    return hashlib.sha1(f"{exc_type}|{deepest}".encode()).hexdigest()[:12]


def record_pick_failure(
    battle_id: str,
    request: dict,
    error: str,
    degraded: bool,
    degraded_move: str | None = None,
) -> None:
    """Append one replayable JSONL line for a pick foul-play couldn't handle.

    `request` must be the serialized PickRequest/PickRequestBody (it carries
    `request_json` + `log_lines`, which replay.py reads). Call from inside an
    `except` block so `traceback.format_exc()` captures the live trace.

    The record self-bundles everything needed to triage and replay one failure
    in one place: the verbatim battle (request + full Showdown log), the live
    traceback, the fallback move the player actually got, a coarse `phase`, the
    engine build `meta`, and a `fingerprint` that groups identical bugs for a
    batched fix.
    """
    if not FAILURE_LOG_DIR:
        return
    try:
        os.makedirs(FAILURE_LOG_DIR, exist_ok=True)
        tb = traceback.format_exc()
        record = {
            "ts": time.time(),
            "battle_id": battle_id,
            "degraded": degraded,
            "degraded_move": degraded_move,
            "fingerprint": _failure_fingerprint(error, tb),
            "phase": _failure_phase(tb),
            "error": error,
            "traceback": tb,
            "meta": _ENGINE_META,
        }
        record.update(request)
        failures_path = os.path.join(FAILURE_LOG_DIR, FAILURE_LOG_NAME)
        _rollover_if_large(failures_path, FAILURE_LOG_MAX_BYTES)
        with open(failures_path, "a") as f:
            f.write(json.dumps(record) + "\n")
    except (OSError, TypeError) as e:
        logger.warning("pick-failure log write failed: %s", e)

# Perfect-info search knobs (env, read at import — restart the service to change):
#   BRIDGE_SEARCH_ALGORITHM  "mcts" (default) | "expectiminimax"
#   BRIDGE_MCTS_BATCHES      root-parallel MCTS: N independent searches of the
#                            same state in worker processes, visit counts merged.
#                            (installed poke-engine 0.0.46 predates the threads
#                            param, so parallelism has to live above the engine)
#   BRIDGE_LOG_STATE         "1" to log the serialized engine state every pick
#                            (audit tool — ~1-2KB per turn, dev only)
SEARCH_ALGORITHM = os.environ.get("BRIDGE_SEARCH_ALGORITHM", "mcts")
MCTS_BATCHES = int(os.environ.get("BRIDGE_MCTS_BATCHES", "1"))
LOG_STATE = os.environ.get("BRIDGE_LOG_STATE", "0").lower() not in ("0", "", "false")
# Terastallization is disabled on the server. The engine wheel is compiled
# with the tera feature, so "-tera" options still appear in search results;
# we drop them before selection. Proper fix: build poke-engine without the
# `terastallization` cargo feature (planned for the cluster image).
ALLOW_TERA = os.environ.get("BRIDGE_ALLOW_TERA", "0").lower() not in ("0", "", "false")

# Stamped onto every failure record so a weeks-old backlog can be replayed
# against the build that produced it. The engine/foul-play refs are baked at
# image-build time (Dockerfile ARGs); surface them at runtime via env when set.
_ENGINE_META = {
    "search_algorithm": SEARCH_ALGORITHM,
    "mcts_batches": MCTS_BATCHES,
    "allow_tera": ALLOW_TERA,
    "poke_engine_ref": os.environ.get("POKE_ENGINE_REF", ""),
    "foul_play_commit": os.environ.get("FOUL_PLAY_COMMIT", ""),
}

# Per-worker-process health, read by /healthz. The distinction that matters:
# a *content* bug (foul-play can't parse some set) is deterministic — restarting
# the pod won't fix it and would needlessly drop every other live battle on it,
# so it only degrades+logs. *Pod-local corruption* (the MCTS ProcessPool dying
# and not recovering) IS restart-fixable, so a run of unrecovered pool breaks is
# the one thing that fails liveness and lets k8s recycle just this pod. A single
# transient break (e.g. a deploy killed the children mid-search) recovers on the
# next successful pick and never trips it. Counters are exposed for observability
# only — they never change the liveness verdict.
_HEALTH_POOL_BREAK_LIMIT = int(os.environ.get("BRIDGE_HEALTH_POOL_BREAK_LIMIT", "3"))


class _Health:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.picks = 0
        self.degrades = 0
        self.consecutive_pool_breaks = 0
        self.last_error = ""
        # Rolling window of recent outcomes (True=ok) for a degrade-rate gauge.
        self._window: collections.deque[bool] = collections.deque(maxlen=100)

    def record_ok(self) -> None:
        with self._lock:
            self.picks += 1
            self.consecutive_pool_breaks = 0  # a good pick clears the streak
            self._window.append(True)

    def record_degrade(self, error: str = "") -> None:
        # A degraded pick (content bug or recovered-from failure). Counts toward
        # the degrade gauge but is neutral to liveness — only pool breaks decide
        # that, so a recurring data bug can't take the pod (or the fleet) down.
        with self._lock:
            self.picks += 1
            self.degrades += 1
            self.last_error = error
            self._window.append(False)

    def record_pool_break(self) -> None:
        # The MCTS pool broke AND the one-shot recreate+retry broke again:
        # genuine pod-local corruption a restart clears.
        with self._lock:
            self.consecutive_pool_breaks += 1

    def is_live(self) -> bool:
        return self.consecutive_pool_breaks < _HEALTH_POOL_BREAK_LIMIT

    def snapshot(self) -> dict:
        with self._lock:
            n = len(self._window)
            degrade_rate = (sum(1 for ok in self._window if not ok) / n) if n else 0.0
            return {
                "live": self.consecutive_pool_breaks < _HEALTH_POOL_BREAK_LIMIT,
                "picks": self.picks,
                "degrades": self.degrades,
                "consecutive_pool_breaks": self.consecutive_pool_breaks,
                "recent_degrade_rate": round(degrade_rate, 3),
                "recent_window": n,
                "last_error": self.last_error,
            }


health = _Health()


@dataclass
class PickRequest:
    request_json: dict
    log_lines: list[str]
    gym_side: str
    pokemon_format: str
    generation: str
    smogon_stats_format: str
    search_time_ms: int
    # Cobblemon packed-team string for the opposing (player) side. When present,
    # the bridge plays with perfect information instead of sampling Smogon sets.
    opponent_team_packed: str | None = None
    # Cobblemon's BattleAI.choose() forceSwitch parameter. The |request| JSON in
    # showdownMessages can be stale on pivot turns (Volt Switch/U-turn/Teleport
    # KOs), so this is the authoritative "you must pick a switch now" signal —
    # without it the engine returns "No Move" and the battle softlocks.
    force_switch: bool = False
    # Opponent-fallibility temperature for the perfect-info MCTS. 0 = perfect
    # opponent (original behaviour); higher values make the search assume the
    # player sometimes misplays, so it punishes greedy lines (e.g. free setup).
    # Intended as a per-gym difficulty dial. Only the perfect-info MCTS path
    # honours it.
    temperature: float = 0.0


def pick_move(battle_id: str, req: PickRequest) -> str:
    battle = _build_battle(battle_id, req)
    if req.opponent_team_packed:
        overlay_opponent_team(battle, parse_packed_team(req.opponent_team_packed))
        return _find_best_move_perfect_info(
            battle, req.search_time_ms, req.temperature
        )
    return find_best_move(battle)


def legal_fallback_move(request_json: dict, force_switch: bool = False) -> str:
    """A guaranteed-legal action parsed straight from the Showdown |request|.

    Used when the engine search raises (unknown weather, empty set weights, …) so
    the pick degrades to a sane move instead of 500ing — a 500 drops Cobblemon
    into its StrongBattleAI fallback, which mishandles switch choices and bugs
    the whole battle out. Returns the bridge's normal move-choice shape: a bare
    move id, "switch <id>", or "pass". Prefers a usable move; on a forced switch
    (or when no move is usable) sends the first healthy reserve; passes only if
    nothing legal remains.
    """
    rj = request_json or {}
    must_switch = force_switch or bool(rj.get("forceSwitch"))

    if not must_switch:
        active = rj.get("active") or []
        if active and isinstance(active[0], dict):
            for mv in active[0].get("moves", []):
                if mv.get("disabled"):
                    continue
                pp = mv.get("pp")
                if pp is not None and pp <= 0:
                    continue
                move_id = mv.get("id")
                if move_id:
                    return move_id

    for mon in (rj.get("side") or {}).get("pokemon", []):
        if mon.get("active"):
            continue
        if str(mon.get("condition", "")).endswith("fnt"):
            continue
        ident = str(mon.get("ident", ""))
        species = ident.split(":", 1)[1] if ":" in ident else ident
        if not species.strip():
            species = str(mon.get("details", "")).split(",", 1)[0]
        species_id = re.sub(r"[^a-z0-9]", "", species.lower())
        if species_id:
            return f"switch {species_id}"

    return "pass"


def _build_battle(battle_id: str, req: PickRequest) -> Battle:
    # Serialize the foul-play cache-touching build (see _CACHE_LOCK above).
    with _CACHE_LOCK:
        return _build_battle_unlocked(battle_id, req)


def _build_battle_unlocked(battle_id: str, req: PickRequest) -> Battle:
    battle = Battle(battle_tag=battle_id)
    battle.battle_type = BattleType.STANDARD_BATTLE
    battle.pokemon_format = req.pokemon_format
    battle.generation = req.generation
    battle.user.name = req.gym_side
    battle.opponent.name = constants.ID_LOOKUP[req.gym_side]

    req = strip_cobblemon_uuids(req)
    battle.request_json = req.request_json
    # Cobblemon's |request| JSON omits `rqid` — only the real Showdown server
    # uses it to ack choices, which we never do. find_best_move doesn't read it.
    battle.rqid = req.request_json.get(constants.RQID)
    # Mirror foul-play's request handler (battle_modifier.request), which our
    # out-of-band request_json bypasses; OR with the mod's authoritative flag.
    battle.force_switch = req.force_switch or bool(
        req.request_json.get(constants.FORCE_SWITCH)
    )
    battle.wait = bool(req.request_json.get(constants.WAIT))
    battle.user.initialize_first_turn_user_from_json(req.request_json)

    # Smogon priors. SmogonSets/TeamDatasets cache themselves at module level
    # in foul-play, so this is a no-op after the first call per process.
    unique_names = {battle.user.active.name}
    unique_names.update(p.name for p in battle.user.reserve)
    SmogonSets.initialize(req.smogon_stats_format, unique_names)
    TeamDatasets.initialize(req.smogon_stats_format, unique_names)

    battle.started = True

    if req.log_lines:
        battle.msg_list = _normalize_log_lines(req.log_lines, req.gym_side)
        # Stateless-rebuild quirk: the latest request marks the CURRENT active,
        # but the replay starts at turn 0 with the lead's |switch| line.
        # foul-play's switch_or_drag only searches the reserve, so if the
        # request-active mon is the one "switching in", it isn't found and a
        # blank phantom is fabricated in its place (a mon that led and is
        # active again later spends the whole pick as a moveless ghost).
        # Demote the request-active into the reserve and let the replay
        # promote the right mon at the right time.
        gym_switch_prefixes = (
            f"|switch|{req.gym_side}a",
            f"|drag|{req.gym_side}a",
        )
        demoted_active = None
        if battle.user.active is not None and any(
            line.startswith(gym_switch_prefixes) for line in battle.msg_list
        ):
            demoted_active = battle.user.active
            battle.user.reserve.append(battle.user.active)
            battle.user.active = None
        if LOG_STATE:
            # Audit aids: what the request claims the gym side is, and the
            # switch lines the replay will apply on top of it.
            logger.info(
                "audit request side: %s",
                json.dumps(req.request_json.get("side", {})),
            )
            logger.info(
                "audit switch lines: %s",
                [l for l in battle.msg_list if "switch|" in l][:12],
            )
        try:
            process_battle_updates(battle)
        except Exception as exc:
            # foul-play occasionally raises mid-replay (observed: IndexError in
            # update_dataset_possibilities/_do_check on certain turns). That must
            # NOT 500 the pick: a 500 sends Cobblemon into its StrongBattleAI
            # fallback, which can't parse switch choices and degenerates into
            # perma-switching (the dragon-gym symptom). Degrade to the current
            # request snapshot — no historical replay/enrichment — so we still
            # return a legal, reasonable move. The full request + trace is also
            # appended to pick_failures.jsonl so the underlying foul-play bug
            # can be reproduced and fixed upstream.
            logger.warning(
                "process_battle_updates failed for battle=%s; "
                "using request snapshot without log replay",
                battle_id,
                exc_info=True,
            )
            record_pick_failure(
                battle_id, asdict(req), f"{type(exc).__name__}: {exc}", degraded=True
            )
            # The demote trick above set active=None expecting the replay to
            # re-promote it. If the replay died first, restore the request's
            # active so the battle isn't left headless.
            if battle.user.active is None and demoted_active is not None:
                if demoted_active in battle.user.reserve:
                    battle.user.reserve.remove(demoted_active)
                battle.user.active = demoted_active

    return battle


def _normalize_log_lines(log_entries: list[str], gym_side: str) -> list[str]:
    """Flatten Cobblemon's showdownMessages into the per-line protocol stream
    foul-play expects.

    Two adjustments:
    - Each entry can contain multiple newline-separated protocol lines (Cobblemon
      stores whole rawMessage blocks). Split them out.
    - `|split|<side>` is followed by a private line (real HPs, for that side)
      and a public line (percent HPs, for everyone else). Showdown's websocket
      pre-filters this for clients; Cobblemon doesn't, so we see both. Pick the
      private line iff <side> is our gym side, otherwise the public one. Drop
      the `|split|` marker itself in either case.
    """
    flat: list[str] = []
    for entry in log_entries:
        for line in entry.split("\n"):
            if line.strip():
                flat.append(line)

    out: list[str] = []
    i = 0
    while i < len(flat):
        line = flat[i]
        # Cobblemon's Showdown fork uses the pre-gen9 weather name; foul-play
        # only knows the modern one (constants.SNOW == "snowscape").
        if line.startswith("|-weather|Snow") and not line.startswith(
            "|-weather|Snowscape"
        ):
            line = line.replace("|-weather|Snow", "|-weather|Snowscape", 1)
        if line.startswith("|split|") and i + 2 < len(flat):
            split_side = line[len("|split|"):].strip()
            private_line = flat[i + 1]
            public_line = flat[i + 2]
            out.append(private_line if split_side == gym_side else public_line)
            i += 3
            continue
        out.append(line)
        i += 1
    return out


# --- Cobblemon protocol normalization ---------------------------------------
#
# Cobblemon's Showdown fork injects a Pokemon UUID as an extra token in the
# details string ("Slowbro, <uuid>, L50, M" — vanilla is "Slowbro, L50, M")
# and uses the UUID as the nickname in protocol idents ("p2a: <uuid>").
# foul-play parses the level from details[1] (the UUID -> ValueError -> level
# silently defaults to 100) and matches request mons to protocol mons by
# nickname (UUID != "Slowbro" -> no match -> a blank phantom is fabricated
# on the side, orphaning the real mon's moves/ability/item).
#
# Fix: map UUID -> species from every details occurrence, drop the UUID token
# from details, and rewrite UUID nicknames to species names everywhere.
# Limitation: two same-species mons on one team collapse to one identity.

UUID_RE = re.compile(r"[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")
DETAILS_UUID_RE = re.compile(
    r"(?P<species>[^,|]+), (?P<uuid>[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12})"
)


def strip_cobblemon_uuids(req: PickRequest) -> PickRequest:
    uuid_to_species: dict[str, str] = {}

    def collect_and_strip(text: str) -> str:
        def repl(m):
            # the species group can carry junk prefix when matching inside
            # serialized JSON ('"details": "Slowbro') — keep the full text in
            # the substitution (drops only ", <uuid>") but clean the mapping
            species = m.group("species").split('"')[-1].split(":")[-1].strip()
            uuid_to_species[m.group("uuid")] = species
            return m.group("species")

        return DETAILS_UUID_RE.sub(repl, text)

    request_json = json.loads(collect_and_strip(json.dumps(req.request_json)))
    log_lines = [collect_and_strip(line) for line in req.log_lines]

    # second pass: any remaining UUID occurrences (idents like "p2a: <uuid>")
    def replace_known(text: str) -> str:
        return UUID_RE.sub(
            lambda m: uuid_to_species.get(m.group(0), m.group(0)), text
        )

    request_json = json.loads(replace_known(json.dumps(request_json)))
    log_lines = [replace_known(line) for line in log_lines]
    return PickRequest(
        request_json=request_json,
        log_lines=log_lines,
        gym_side=req.gym_side,
        pokemon_format=req.pokemon_format,
        generation=req.generation,
        smogon_stats_format=req.smogon_stats_format,
        search_time_ms=req.search_time_ms,
        opponent_team_packed=req.opponent_team_packed,
        force_switch=req.force_switch,
    )


# --- Perfect information ("open team sheet") -------------------------------
#
# The mod sends the player's full team in Cobblemon's packed-team format
# (BattleRegistry.packTeam()). This is NOT the standard Showdown packed format:
# Cobblemon's fork inserts uuid/currentHp/status/statusDuration after the
# species fields ("REQUIRES OUR SHOWDOWN" in their source). Field layout:
#
#   0 name (showdownId)   1 species (blank)     2 uuid
#   3 currentHp           4 status              5 statusDuration
#   6 item                7 ability             8 moves (csv)
#   9 movePp (csv)       10 nature             11 evs (csv)
#  12 gender             13 ivs (csv)          14 shiny
#  15 level              16 misc (happiness,ball,hpType,gmax,dmax,teraType)
#
# EV/IV csv order is Stats.PERMANENT = hp,atk,def,spa,spd,spe — the same order
# foul-play's calculate_stats expects.


@dataclass
class PackedPokemon:
    name: str
    hp: int
    status: str | None
    item: str | None
    ability: str
    moves: list[str]
    nature: str
    evs: tuple[int, ...]
    ivs: tuple[int, ...]
    level: int
    tera_type: str | None


def parse_packed_team(packed: str) -> list[PackedPokemon]:
    mons = []
    for entry in packed.split("]"):
        if not entry.strip():
            continue
        f = entry.split("|")
        misc = f[16].split(",") if len(f) > 16 else []
        tera_type = normalize_name(misc[5]) if len(misc) > 5 and misc[5] else None
        mons.append(
            PackedPokemon(
                name=normalize_name(f[0]),
                hp=int(f[3]),
                status=f[4] or None,
                item=normalize_name(f[6]) if f[6] else None,
                ability=normalize_name(f[7]),
                moves=[normalize_name(m) for m in f[8].split(",") if m],
                nature=normalize_name(f[10]),
                evs=tuple(int(x) for x in f[11].split(",")),
                ivs=tuple(int(x) for x in f[13].split(",")),
                level=int(f[15]),
                tera_type=tera_type,
            )
        )
    return mons


def overlay_opponent_team(battle: Battle, team: list[PackedPokemon]) -> None:
    """Overlay ground truth from the team sheet onto battle.opponent.

    The log replay already produced Pokemon objects for everything revealed,
    with correct *live* state (HP%, status, boosts, consumed/knocked-off
    items). We fill in what the log can't know — unrevealed moves, ability,
    spread, held item — and create reserve entries for mons never sent out.

    Merge rules err toward the log for live state and toward the sheet for
    static build info: an item is only set if foul-play still considers it
    unknown (None means "tracked as removed", which must survive).
    """
    revealed = [p for p in [battle.opponent.active, *battle.opponent.reserve] if p]
    matched: set[int] = set()
    for pm in team:
        pkmn = next(
            (
                p
                for p in revealed
                if id(p) not in matched
                and (p.name == pm.name or p.base_name == pm.name)
            ),
            None,
        )
        if pkmn is None:
            pkmn = Pokemon(pm.name, pm.level)
            _apply_truth(pkmn, pm)
            # Never revealed: the sheet's raw HP/status are current state.
            # Our max_hp comes from the same stat formula Cobblemon uses, so
            # raw HP is comparable; clamp for safety.
            pkmn.hp = min(pm.hp, pkmn.max_hp)
            pkmn.status = pm.status
            battle.opponent.reserve.append(pkmn)
        else:
            matched.add(id(pkmn))
            _apply_truth(pkmn, pm)


def _apply_truth(pkmn: Pokemon, pm: PackedPokemon) -> None:
    # Level first: revealed mons are created via from_switch_string, which
    # defaults to 100 when the protocol line omits the level token. Stats
    # below are computed from it. (Attacker level scales the damage formula
    # directly, so a wrong level ~doubles/halves simulated damage.)
    pkmn.level = pm.level
    # Spread: recalc stats from real nature/EVs/IVs, preserving HP fraction
    # (same approach as Pokemon.set_spread, which doesn't accept IVs).
    hp_fraction = pkmn.hp / pkmn.max_hp
    stats = calculate_stats(
        pkmn.base_stats, pkmn.level, ivs=pm.ivs, evs=pm.evs, nature=pm.nature
    )
    pkmn.nature = pm.nature
    pkmn.evs = pm.evs
    pkmn.stats = stats
    pkmn.max_hp = pkmn.stats.pop(constants.HITPOINTS)
    pkmn.hp = round(pkmn.max_hp * hp_fraction)

    if pkmn.ability is None:
        pkmn.ability = pm.ability
    # item None means the log saw it removed (knock off, consumed berry, ...)
    if pkmn.item == constants.UNKNOWN_ITEM:
        pkmn.item = pm.item
    if pkmn.tera_type is None:
        pkmn.tera_type = pm.tera_type

    known_moves = {m.name for m in pkmn.moves}
    for mv in pm.moves:
        if mv not in known_moves:
            pkmn.add_move(mv)


# Selection knobs (see select_choice). Tuned for "gym leader" feel: fight for
# mistakes instead of wall-cycling when the engine reads the position as lost.
NEAR_BEST_VISITS_RATIO = 0.75  # foul-play's own near-best rule
DEFEATISM_SCORE = 0.35  # below this eval, MCTS "all lines lose" defeatism kicks in
AGGRESSION_VISITS_RATIO = 0.40  # min visit share (vs top pick) for an attack override


def select_choice(options: list[tuple[str, int, float]]) -> str:
    """Pick a move from MCTS (move_choice, visits, avg_score) tuples.

    Baseline is foul-play's rule: weighted draw among choices within 75% of
    the most-visited one (strict max-visits loops deterministically).

    On top: an anti-defeatism override. MCTS scores positions against a
    perfect opponent, so when every line "loses" (eval < DEFEATISM_SCORE) it
    drifts into loss-delaying switch cycles — stalling and fighting score the
    same against perfection. Against a human they don't: take the best attack
    instead when one carries a credible visit share. (Forced switches are
    unaffected — there is no attack option on those turns.)
    """
    options = sorted(options, key=lambda x: x[1], reverse=True)
    best_choice, best_visits, best_score = options[0]

    if best_choice.startswith("switch ") and best_score < DEFEATISM_SCORE:
        attacks = [o for o in options if not o[0].startswith("switch ")]
        if attacks and attacks[0][1] >= AGGRESSION_VISITS_RATIO * best_visits:
            logger.info(
                "anti-defeatism override: %s (visits %d) over %s (visits %d, score=%.3f)",
                attacks[0][0],
                attacks[0][1],
                best_choice,
                best_visits,
                best_score,
            )
            return attacks[0][0]

    near_best = [o for o in options if o[1] >= NEAR_BEST_VISITS_RATIO * best_visits]
    weights = [o[1] for o in near_best]
    if sum(weights) <= 0:
        # No visits recorded (degenerate or too-short search) — weighting by visit
        # count would raise "Total of weights must be greater than zero". Fall back
        # to the top-sorted option instead of crashing the whole pick.
        return best_choice
    return random.choices(near_best, weights=weights)[0][0]


def _find_best_move_perfect_info(
    battle: Battle, search_time_ms: int, temperature: float = 0.0
) -> str:
    """Search the one true battle state.

    With the opponent's team fully known there is no hidden information to
    marginalize over, so foul-play's sample-N-worlds loop (find_best_move)
    collapses to one search using the entire time budget.
    """
    battle = deepcopy(battle)
    battle.opponent.lock_moves()
    state_string = battle_to_poke_engine_state(battle).to_string()
    if LOG_STATE:
        logger.info("perfect-info state: %s", state_string)
    if SEARCH_ALGORITHM == "expectiminimax":
        # expectiminimax is maximin (worst-case opponent) and does not model
        # opponent fallibility, so temperature is ignored on this path.
        choice = _search_expectiminimax(state_string, search_time_ms)
    else:
        choice = _search_mcts(state_string, search_time_ms, temperature)
    # poke-engine emits "No Move" when the side has no legal action this
    # request (e.g. waiting on the opponent's faint replacement).
    if choice.lower() in ("no move", "none"):
        return "pass"
    return choice


# Warm worker pool for root-parallel MCTS, created lazily per uvicorn worker
# and reused across picks — spawning a pool per call cost ~1.4s/turn. Workers
# inherit the loaded foul-play modules via fork. Replaced if a deploy restart
# ever kills the children mid-search (BrokenProcessPool).
_mcts_pool: ProcessPoolExecutor | None = None


def _get_mcts_pool() -> ProcessPoolExecutor:
    global _mcts_pool
    if _mcts_pool is None:
        _mcts_pool = ProcessPoolExecutor(max_workers=MCTS_BATCHES)
    return _mcts_pool


def _run_mcts(state_string: str, search_time_ms: int, temperature: float):
    """Top-level (picklable) MCTS call used directly and by the worker pool.

    Calls poke-engine's single-threaded MCTS with the opponent-fallibility
    temperature. threads=1 keeps the search on the temperature-aware path.

    Backward-compatible: the `temperature` arg only exists on our patched
    poke-engine. When temperature is 0 (the default until gyms are calibrated)
    we call the stock 2-arg signature, so this bridge runs unchanged on an
    unpatched image. A non-zero temperature requires the patched engine — and
    if that engine ISN'T installed, the 4-arg call raises TypeError; rather than
    failing the whole pick (→ 500 → the mod falls back to a move-spamming AI) we
    degrade to the stock 2-arg MCTS (still a strong perfect-info search, just
    without the opponent-fallibility dial). Install the patched poke-engine to
    actually honour temperature.
    """
    state = PokeEngineState.from_string(state_string)
    if temperature:
        try:
            return monte_carlo_tree_search(state, search_time_ms, 1, temperature)
        except TypeError:
            logging.getLogger("poke-engine-bridge").warning(
                "poke-engine has no temperature arg — ignoring temperature=%s; "
                "install the patched engine to enable the difficulty dial",
                temperature,
            )
            return monte_carlo_tree_search(state, search_time_ms)
    return monte_carlo_tree_search(state, search_time_ms)


def _search_mcts(state_string: str, search_time_ms: int, temperature: float = 0.0) -> str:
    if MCTS_BATCHES > 1:
        # Root parallelization: independent searches of the SAME state merged
        # by visit count. Diversifies tree exploration and uses spare cores.
        global _mcts_pool
        try:
            futures = [
                _get_mcts_pool().submit(
                    _run_mcts, state_string, search_time_ms, temperature
                )
                for i in range(MCTS_BATCHES)
            ]
            results = [f.result() for f in futures]
        except BrokenProcessPool:
            logger.warning("mcts pool broken — recreating and retrying once")
            _mcts_pool = None
            futures = [
                _get_mcts_pool().submit(
                    _run_mcts, state_string, search_time_ms, temperature
                )
                for i in range(MCTS_BATCHES)
            ]
            try:
                results = [f.result() for f in futures]
            except BrokenProcessPool:
                # Recreate+retry also died — pod-local corruption a restart
                # clears. Flag it for /healthz (liveness recycles this pod after
                # a few in a row) and let the pick degrade like any other failure.
                health.record_pool_break()
                raise
    else:
        results = [_run_mcts(state_string, search_time_ms, temperature)]

    merged: dict[str, list[float]] = {}  # move -> [visits, total_score]
    total_visits = 0
    for result in results:
        total_visits += result.total_visits
        for o in result.side_one:
            entry = merged.setdefault(o.move_choice, [0, 0.0])
            entry[0] += o.visits
            entry[1] += o.total_score
    options = [
        (mv, int(visits), total_score / visits if visits else 0.0)
        for mv, (visits, total_score) in merged.items()
        if ALLOW_TERA or not mv.endswith("-tera")
    ]
    # Full option set per pick — lets us see *why* a move won (visit share vs
    # avg_score) and spot the engine undervaluing setup/aggression. Shows up in
    # replay.py output too. Format: move=<visits>v/<avg_score>.
    logger.info(
        "perfect-info options (n=%d): %s",
        len(options),
        ", ".join(
            f"{mv}={v}v/{sc:.3f}"
            for mv, v, sc in sorted(options, key=lambda o: o[1], reverse=True)
        ),
    )
    choice = select_choice(options)
    chosen = next(o for o in options if o[0] == choice)
    logger.info(
        "perfect-info choice: %s (visits %d/%d across %d batch(es), avg_score=%.3f)",
        choice,
        chosen[1],
        total_visits,
        len(results),
        chosen[2],
    )
    return choice


def safest_move(side_one: list[str], side_two: list[str], matrix: list) -> str:
    """Maximin over an expectiminimax payoff matrix (row-major s1 x s2).

    Reimplemented rather than using IterativeDeepeningResult.get_safest_move:
    the upstream helper never advances its matrix index (0.0.46), so it
    compares every row by cell [0]. Pruned branches are None — a pruned cell
    can't be this row's realized worst case, so it's skipped; a fully-pruned
    row can never be chosen.
    """
    best_move, best_worst = side_one[0], float("-inf")
    n2 = len(side_two)
    for i, mv in enumerate(side_one):
        row = [s for s in matrix[i * n2 : (i + 1) * n2] if s is not None]
        worst = min(row) if row else float("-inf")
        if worst > best_worst:
            best_move, best_worst = mv, worst
    return best_move


def _search_expectiminimax(state_string: str, search_time_ms: int) -> str:
    result = iterative_deepening_expectiminimax(
        PokeEngineState.from_string(state_string), search_time_ms
    )
    side_one, matrix = result.side_one, result.matrix
    if not ALLOW_TERA:
        n2 = len(result.side_two)
        kept = [i for i, mv in enumerate(side_one) if not mv.endswith("-tera")]
        side_one = [side_one[i] for i in kept]
        matrix = [c for i in kept for c in result.matrix[i * n2 : (i + 1) * n2]]
    choice = safest_move(side_one, result.side_two, matrix)
    logger.info(
        "expectiminimax choice: %s (depth=%d, options=%d)",
        choice,
        result.depth_searched,
        len(result.side_one),
    )
    return choice
