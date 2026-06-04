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

import logging
from dataclasses import dataclass

import constants
from constants import BattleType
from data.pkmn_sets import SmogonSets, TeamDatasets
from fp.battle import Battle
from fp.battle_modifier import process_battle_updates
from fp.search.main import find_best_move

logger = logging.getLogger(__name__)


@dataclass
class PickRequest:
    request_json: dict
    log_lines: list[str]
    gym_side: str
    pokemon_format: str
    generation: str
    smogon_stats_format: str
    search_time_ms: int


def pick_move(battle_id: str, req: PickRequest) -> str:
    battle = _build_battle(battle_id, req)
    return find_best_move(battle)


def _build_battle(battle_id: str, req: PickRequest) -> Battle:
    battle = Battle(battle_tag=battle_id)
    battle.battle_type = BattleType.STANDARD_BATTLE
    battle.pokemon_format = req.pokemon_format
    battle.generation = req.generation
    battle.user.name = req.gym_side
    battle.opponent.name = constants.ID_LOOKUP[req.gym_side]

    battle.request_json = req.request_json
    battle.rqid = req.request_json[constants.RQID]
    battle.user.initialize_first_turn_user_from_json(req.request_json)

    # Smogon priors. SmogonSets/TeamDatasets cache themselves at module level
    # in foul-play, so this is a no-op after the first call per process.
    unique_names = {battle.user.active.name}
    unique_names.update(p.name for p in battle.user.reserve)
    SmogonSets.initialize(req.smogon_stats_format, unique_names)
    TeamDatasets.initialize(req.smogon_stats_format, unique_names)

    battle.started = True

    if req.log_lines:
        battle.msg_list = list(req.log_lines)
        process_battle_updates(battle)

    return battle
