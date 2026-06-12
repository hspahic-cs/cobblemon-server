"""FastAPI bridge between the Cobblemon gym-AI mod and foul-play+poke-engine.

Stateless: every /pick rebuilds a fresh foul-play `Battle` from the full
Showdown event log the mod sends. Workers run independently; there is no
per-battle state on the bridge.

Run from this directory so foul-play's packages are reachable via PYTHONPATH:

    cd ops/poke-engine-bridge
    source ../../reference/foul-play/.venv/bin/activate
    pip install -r requirements.txt
    PYTHONPATH=../../reference/foul-play uvicorn app:app --host 127.0.0.1 --port 8642 --workers 4
"""

from __future__ import annotations

import json
import logging
import os
import time
from pathlib import Path
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from config import FoulPlayConfig

from bridge import PickRequest, pick_move, record_pick_failure, legal_fallback_move

logger = logging.getLogger("poke_engine_bridge")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

# foul-play reads parallelism from FoulPlayConfig at request time. We let
# uvicorn handle outer concurrency via worker processes; each worker stays
# at parallelism=1 so cores aren't oversubscribed.
FoulPlayConfig.parallelism = 1
# Default only — each /pick overrides this from the request's search_time_ms
# (the mod sends it; see cobblemon-poke-ai BridgeConfig). Kept in sync at 3000.
FoulPlayConfig.search_time_ms = 3000

# When set, every /pick appends one JSON line to <dir>/<battle_id>.jsonl:
# the verbatim request body plus the outcome (pick or error). Each line is a
# complete replayable turn — see replay.py. Meant for beta-test sweeps.
BATTLE_LOG_DIR = os.environ.get("BRIDGE_BATTLE_LOG_DIR", "")

app = FastAPI(title="poke-engine-bridge")


def _log_battle_turn(battle_id: str, body: PickRequestBody, outcome: dict) -> None:
    if not BATTLE_LOG_DIR:
        return
    try:
        path = Path(BATTLE_LOG_DIR)
        path.mkdir(parents=True, exist_ok=True)
        record = {"ts": time.time(), "battle_id": battle_id}
        record.update(body.model_dump())
        record.update(outcome)
        safe_id = "".join(c for c in battle_id if c.isalnum() or c == "-")
        with open(path / f"{safe_id}.jsonl", "a") as f:
            f.write(json.dumps(record) + "\n")
    except OSError as e:
        logger.warning("battle log write failed: %s", e)


class PickRequestBody(BaseModel):
    request_json: dict[str, Any]
    log_lines: list[str] = Field(default_factory=list)
    gym_side: str = Field(pattern="^p[12]$")
    pokemon_format: str = "gen9customgame"
    generation: str = "gen9"
    smogon_stats_format: str = "gen9nationaldex"
    search_time_ms: int = 1000
    # Cobblemon packed-team string for the player's side; enables the
    # perfect-information search path (see bridge.overlay_opponent_team).
    opponent_team_packed: str | None = None
    # BattleAI.choose() forceSwitch flag — authoritative pivot/faint signal
    # (the |request| JSON in the log can be stale on pivot turns).
    force_switch: bool = False
    # Per-gym opponent-fallibility temperature for the MCTS (0 = perfect
    # opponent). Higher = the AI assumes the player misplays and punishes greed.
    temperature: float = 0.0


class PickResponse(BaseModel):
    move_choice: str
    search_ms: int


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/battles/{battle_id}/pick", response_model=PickResponse)
def pick(battle_id: str, body: PickRequestBody) -> PickResponse:
    FoulPlayConfig.search_time_ms = body.search_time_ms
    req = PickRequest(
        request_json=body.request_json,
        log_lines=body.log_lines,
        gym_side=body.gym_side,
        pokemon_format=body.pokemon_format,
        generation=body.generation,
        smogon_stats_format=body.smogon_stats_format,
        search_time_ms=body.search_time_ms,
        opponent_team_packed=body.opponent_team_packed,
        force_switch=body.force_switch,
        temperature=body.temperature,
    )
    started = time.monotonic()
    try:
        choice = pick_move(battle_id, req)
    except Exception as e:
        # Never 500 a pick. A 500 drops Cobblemon into its StrongBattleAI
        # fallback, which mishandles switch choices and bugs the whole battle
        # out. Log the failure (replayable, in pick_failures.jsonl) and degrade
        # to a legal move parsed from the request so the battle continues with a
        # sane, parseable action instead.
        err = f"{type(e).__name__}: {e}"
        logger.warning(
            "pick failed for battle=%s (%s) — degrading to a legal request move",
            battle_id,
            err,
            exc_info=True,
        )
        record_pick_failure(battle_id, body.model_dump(), err, degraded=True)
        choice = legal_fallback_move(body.request_json, req.force_switch)
        elapsed_ms = int((time.monotonic() - started) * 1000)
        _log_battle_turn(
            battle_id,
            body,
            {"error": err, "degraded_pick": choice, "search_ms": elapsed_ms},
        )
        return PickResponse(move_choice=choice, search_ms=elapsed_ms)
    elapsed_ms = int((time.monotonic() - started) * 1000)
    logger.info("battle=%s pick=%s elapsed_ms=%d", battle_id, choice, elapsed_ms)
    _log_battle_turn(battle_id, body, {"pick": choice, "search_ms": elapsed_ms})
    return PickResponse(move_choice=choice, search_ms=elapsed_ms)
