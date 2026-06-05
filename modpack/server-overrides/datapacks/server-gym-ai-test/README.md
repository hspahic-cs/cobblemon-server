# server-gym-ai-test

Dev-only datapack for beta-testing the **pe** AI (poke-engine MCTS via the
`poke-engine-bridge` service) against every gym leader's real roster.

## Setup

`ops/gen_aitest_gyms.py` clones all 24 `server-gyms` leaders into
`aitest_gym_NN_<name>_pe.json` variants: same team, name prefixed
`AI Test [pe]:`, AI forced to `{"type": "pe"}`. No terastallization — the
generator asserts no tera config survives (the bridge also filters `-tera`
picks at selection time).

> Requires the `cobblemon-poke-ai` mod **and** a reachable `poke-engine-bridge`
> (URL in `config/cobblemon-poke-ai.json`). If the bridge is down the mod
> falls back to `StrongBattleAI(skill=5)`.

## Usage

Stand where the row should start — leaders spawn 3 blocks apart along your
facing direction, Clay first, Champion last:

```
/function server:aitest/spawn_all_pe
/function server:aitest/cleanup
```

`spawn_all_pe` clears any previous row first; `cleanup` removes everything
tagged `aitest`.

Every AI turn is logged by the bridge (one JSONL per battle in
`BRIDGE_BATTLE_LOG_DIR`) — see `ops/poke-engine-bridge/replay.py` to reproduce
any logged turn offline.

## Regenerate (after roster changes)

```
python3 ops/gen_aitest_gyms.py
```
