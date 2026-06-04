# server-gym-ai-test

Dev-only datapack for head-to-head **rb vs pe** AI testing on real player battles.

## Setup

3 leaders × 2 AI variants = 6 trainer JSONs, all sharing each leader's hardmode
team (EVs/IVs/natures/items copied from `_challenge.json` in `server-gyms`)
with every Pokemon level clamped to 50 so the only variable is the AI.

## Variant → AI mapping

| Variant | `ai.type` | Class      | Notes                                                  |
|---------|-----------|------------|--------------------------------------------------------|
| **rb**  | `rb`      | RunBunAI   | Run & Bun (rbrctai add-on) — current prod AI on gyms   |
| **pe**  | `pe`      | PokeEngineAI | poke-engine MCTS via local FastAPI bridge ([phase 1](../../../../custom-mods/cobblemon-poke-ai)) |

Trainer NPCs are labeled in-game as `AI Test [rb]: Sabrina` and `AI Test [pe]: Sabrina`
so the play-tester sees which AI they're fighting.

> The pe variant requires `cobblemon-poke-ai` mod loaded **and** the
> `poke-engine-bridge` service running on 127.0.0.1:8642. If the bridge is
> down the mod falls back to `StrongBattleAI(skill=5)`.

## Usage

Stand where you want the leaders to spawn — the per-leader command places **both**
variants of that leader 3 blocks apart along your facing direction.

```
/function server:aitest/spawn_sabrina
/function server:aitest/spawn_surge
/function server:aitest/spawn_blaine
/function server:aitest/cleanup
```

`cleanup` removes all 6. Re-running `spawn_<leader>` clears that leader's pair
first, so it's safe to re-spawn after a battle.

## Regenerate

```
python3 ops/gen_gym_ai_test_datapack.py
```

Source teams: `server-gyms/data/rctmod/trainers/{gym_08_sabrina_challenge,gym_13_lt_surge,gym_05_blaine_challenge}.json`.
