# server-gym-ai-test

Dev-only datapack for A/B/C testing RCT AI types against a real player.

## Setup

3 leaders × 3 AI variants = 9 trainer JSONs, all sharing the same hardmode team
(EVs/IVs/natures/items copied from each leader's `_challenge.json` in `server-gyms`)
with every Pokemon level clamped to 50 so the only variable is the AI.

## Variant → AI mapping (single-blind — keep hidden from play-tester)

| Variant | `ai.type` | Class                | Notes                                                  |
|---------|-----------|----------------------|--------------------------------------------------------|
| **A**   | `rb`      | RunBunAI             | Run & Bun (rbrctai add-on) — current prod AI on gyms   |
| **B**   | `cbl`     | StrongBattleAI       | Cobblemon's built-in StrongBattleAI (default skill=5)  |
| **C**   | `sd5`     | SelfdotGen5AI        | Gen-5-style heuristic AI (rctapi experimental)         |

Type strings verified against rctapi-0.15.2 + rbrctai-0.15.3 (decompiled
`*Config.register()` → `JTO.registerParser`). A 4th type `rct` (RCTBattleAI,
the unconfigured default) is also registered but not used here.

Trainer NPCs are labeled in-game as `AI Test [A]: Sabrina` etc., so the play-tester
sees only the letter, not which AI is which.

## Usage

Stand where you want the leaders to spawn (they line up 3 blocks apart along your
facing direction), then run **one** of:

```
/function server:aitest/spawn_a
/function server:aitest/spawn_b
/function server:aitest/spawn_c
/function server:aitest/cleanup
```

`cleanup` removes all 9 (any variant). Re-running `spawn_x` clears that variant's
3 trainers first, so it's safe to re-spawn after a battle.

## Regenerate

```
python3 ops/gen_gym_ai_test_datapack.py
```

Source teams: `server-gyms/data/rctmod/trainers/{gym_08_sabrina_challenge,gym_13_lt_surge,gym_05_blaine_challenge}.json`.
