# poke-engine-bridge

Local HTTP service that picks Cobblemon gym-leader moves via foul-play + poke-engine MCTS.

The Kotlin mod (`custom-mods/cobblemon-poke-ai`) POSTs each turn's `|request|` JSON
plus new lines from `battle.showdownMessages`; this service replays the log into
foul-play's `Battle` model and runs MCTS, returning the chosen move.

See `~/.claude/projects/.../memory/project_gym_ai_tier1.md` and
`reference_foul_play_api_mapping.md` for design context.

## Layout

- `app.py` — FastAPI app, in-memory `Battle` registry keyed by Cobblemon battle id.
- `bridge.py` — wraps foul-play's `Battle` / `process_battle_updates` / `find_best_move`.
- `requirements.txt` — only the bridge's own deps; foul-play's deps are reused from `reference/foul-play/.venv`.

## Running locally

The service piggy-backs on foul-play's existing venv (which already has
`poke-engine` built and the foul-play package importable).

```bash
cd ops/poke-engine-bridge
source ../../reference/foul-play/.venv/bin/activate
pip install -r requirements.txt

# foul-play's data/, fp/, constants modules live there — surface them via PYTHONPATH.
PYTHONPATH=../../reference/foul-play uvicorn app:app --host 127.0.0.1 --port 8642
```

## API

### `POST /battles/{battle_id}/pick`

Stateless. Mod sends the FULL battle log so far each turn — bridge rebuilds
the foul-play `Battle` from scratch every call. `battle_id` is informational
(used for log lines); the bridge keeps no state keyed by it.

Request:
```json
{
  "request_json": { ... },
  "log_lines": ["|switch|p1a: ...", "|switch|p2a: ...", "|move|p2a: ...", "|-damage|..."],
  "gym_side": "p1",
  "pokemon_format": "gen9customgame",
  "generation": "gen9",
  "smogon_stats_format": "gen9nationaldex",
  "search_time_ms": 1000
}
```

Response:
```json
{ "move_choice": "thunderbolt", "search_ms": 1142 }
```

### `GET /healthz`

Liveness/readiness target for the k8s probes. Returns the per-worker health
snapshot; HTTP **503** (status `"degraded"`) only when the worker is in a
restart-fixable poisoned state — specifically `BRIDGE_HEALTH_POOL_BREAK_LIMIT`
(default 3) *consecutive unrecovered* MCTS `ProcessPool` breaks. Content/data
bugs degrade individual picks and are counted (`degrades`, `recent_degrade_rate`)
but never flip the status: they're deterministic, so recycling pods wouldn't help
and marking both replicas unhealthy would cause an outage.

```json
{ "status": "ok", "live": true, "picks": 1234, "degrades": 3,
  "consecutive_pool_breaks": 0, "recent_degrade_rate": 0.0,
  "recent_window": 100, "last_error": "" }
```

## Failure logs & batch-fixing

Every pick foul-play can't process appends one replayable JSON line to
`pick_failures.jsonl` (in `BRIDGE_BATTLE_LOG_DIR`): the verbatim battle (request +
full Showdown log), the live traceback, the `degraded_move` the player got, a
coarse `phase` (build/search), the engine `meta`, and a **`fingerprint`** (exc
type + deepest frame) that groups identical bugs.

This file is never evicted by battle-log rotation and rolls over to
`pick_failures.jsonl.1` past `BRIDGE_FAILURE_LOG_MAX_BYTES` (64Mi); bulky
per-battle `<id>.jsonl` logs are evicted oldest-first to keep the PVC under
`BRIDGE_LOG_MAX_BYTES` (768Mi).

Each pod has its own PVC, so collect the backlog across replicas and triage by
fingerprint with:

```sh
./collect_failures.sh            # merges both pods -> pick_failures.merged.jsonl + histogram
# replay a single failing turn (foul-play side) to reproduce:
python replay.py pick_failures.merged.jsonl
```
