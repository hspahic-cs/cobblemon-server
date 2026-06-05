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

import json
import logging
import os
import random
from concurrent.futures import ProcessPoolExecutor
from copy import deepcopy
from dataclasses import dataclass

import constants
from constants import BattleType
from data.pkmn_sets import SmogonSets, TeamDatasets
from fp.battle import Battle, Pokemon
from fp.battle_modifier import process_battle_updates
from fp.helpers import calculate_stats, normalize_name
from fp.search.main import find_best_move, get_result_from_mcts
from fp.search.poke_engine_helpers import battle_to_poke_engine_state
from poke_engine import (
    State as PokeEngineState,
    iterative_deepening_expectiminimax,
)

logger = logging.getLogger(__name__)

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


def pick_move(battle_id: str, req: PickRequest) -> str:
    battle = _build_battle(battle_id, req)
    if req.opponent_team_packed:
        overlay_opponent_team(battle, parse_packed_team(req.opponent_team_packed))
        return _find_best_move_perfect_info(battle, req.search_time_ms)
    return find_best_move(battle)


def _build_battle(battle_id: str, req: PickRequest) -> Battle:
    battle = Battle(battle_tag=battle_id)
    battle.battle_type = BattleType.STANDARD_BATTLE
    battle.pokemon_format = req.pokemon_format
    battle.generation = req.generation
    battle.user.name = req.gym_side
    battle.opponent.name = constants.ID_LOOKUP[req.gym_side]

    battle.request_json = req.request_json
    # Cobblemon's |request| JSON omits `rqid` — only the real Showdown server
    # uses it to ack choices, which we never do. find_best_move doesn't read it.
    battle.rqid = req.request_json.get(constants.RQID)
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
        process_battle_updates(battle)

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
    return random.choices(near_best, weights=[o[1] for o in near_best])[0][0]


def _find_best_move_perfect_info(battle: Battle, search_time_ms: int) -> str:
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
        choice = _search_expectiminimax(state_string, search_time_ms)
    else:
        choice = _search_mcts(state_string, search_time_ms)
    # poke-engine emits "No Move" when the side has no legal action this
    # request (e.g. waiting on the opponent's faint replacement).
    if choice.lower() in ("no move", "none"):
        return "pass"
    return choice


def _search_mcts(state_string: str, search_time_ms: int) -> str:
    if MCTS_BATCHES > 1:
        # Root parallelization: independent searches of the SAME state merged
        # by visit count. Diversifies tree exploration and uses spare cores.
        with ProcessPoolExecutor(max_workers=MCTS_BATCHES) as executor:
            futures = [
                executor.submit(get_result_from_mcts, state_string, search_time_ms, i)
                for i in range(MCTS_BATCHES)
            ]
        results = [f.result() for f in futures]
    else:
        results = [get_result_from_mcts(state_string, search_time_ms, 0)]

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
