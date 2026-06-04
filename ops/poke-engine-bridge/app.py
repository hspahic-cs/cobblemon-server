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

import logging
import time
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from config import FoulPlayConfig

from bridge import PickRequest, pick_move

logger = logging.getLogger("poke_engine_bridge")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

# foul-play reads parallelism from FoulPlayConfig at request time. We let
# uvicorn handle outer concurrency via worker processes; each worker stays
# at parallelism=1 so cores aren't oversubscribed.
FoulPlayConfig.parallelism = 1
FoulPlayConfig.search_time_ms = 1000

app = FastAPI(title="poke-engine-bridge")


class PickRequestBody(BaseModel):
    request_json: dict[str, Any]
    log_lines: list[str] = Field(default_factory=list)
    gym_side: str = Field(pattern="^p[12]$")
    pokemon_format: str = "gen9customgame"
    generation: str = "gen9"
    smogon_stats_format: str = "gen9nationaldex"
    search_time_ms: int = 1000


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
    )
    started = time.monotonic()
    choice = pick_move(battle_id, req)
    elapsed_ms = int((time.monotonic() - started) * 1000)
    logger.info("battle=%s pick=%s elapsed_ms=%d", battle_id, choice, elapsed_ms)
    return PickResponse(move_choice=choice, search_ms=elapsed_ms)
