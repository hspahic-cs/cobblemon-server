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
