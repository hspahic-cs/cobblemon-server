# server-gyms datapack

RCT trainer definitions for the server's gym ladder, generated from
[ShepskyDad's Gym Leaders](https://arynlight.fandom.com/wiki/ShepskyDad%27s_Gym_Leaders)
wiki page.

## Layout

```
data/rctmod/
├── trainers/
│   ├── gym_NN_<leader>.json                # Full trainer spec: team, AI, battle rules, bag
│   └── gym_NN_<leader>_challenge.json      # Hard-mode variants (gyms 1–10)
└── mobs/trainers/single/
    ├── gym_NN_<leader>.json                # spawnWeightFactor: 0.0 → no wild spawn
    ├── gym_NN_<leader>_challenge.json      # ditto for hard-mode variants
    └── gym_leader_<name>_<hex>.json        # overrides rctmod's bundled BDSP gyms
                                            # (byron/candice/fantina/gardenia/maylene/
                                            # roark/volkner/wake, single + double-battle)
```

`spawnWeightFactor: 0.0` keeps the trainer fully registered (battle-able if op-summoned via
Trainer Spawner block / `/rctmod trainer summon_persistent`) but yields 0 weight in the
natural-spawn pool, so it will never spawn in the wild.

**Schema gotcha (read before editing).** rctmod's `TrainerMobData` uses plain Gson with no
`@SerializedName` — the actual fields are `spawnWeightFactor` (float, default `1.0f`),
`type` (`"leader"` | `"normal"`), `battleCooldownTicks`, `maxTrainerWins`, `maxTrainerDefeats`,
`optional`, `biomeTagWhitelist`, `biomeTagBlacklist`, `requiredDefeats`, `series`,
`signatureItem`. Earlier versions of these files used `{"type": "single", "trainerId": "...",
"weight": 0, ...}` — every one of those keys is silently dropped by Gson, leaving the trainer
to spawn at the default weight of `1.0`. The fix landed in PR #95.

The 16 `gym_leader_<name>_<hex>.json` files override the spawn configs that rctmod ships
inside its own jar. They preserve every field from the bundled JSON (series, requiredDefeats,
biome tags, signature items) and only flip `spawnWeightFactor` from `0.25` to `0.0`. The 24
`gym_NN_<leader>.json` + 10 `_challenge` files apply the same suppression to our own roster.

## Wired gyms (18 of 24 — wiki-sourced)

| Gym | Leader | Type | Source |
|---|---|---|---|
| 1 | Clay | Ground | wiki Ground team |
| 2 | Gardenia | Grass | wiki Grass team |
| 3 | Korrina | Fighting | wiki Fighting team |
| 4 | Byron | Steel | wiki Steel team |
| 5 | Blaine | Fire | wiki Fire team |
| 6 | Volkner | Electric | swapped from Roxie (Poison) — see 0.7.6 release notes |
| 7 | Crasher Wake | Water | wiki Water team |
| 8 | Sabrina | Psychic | wiki Psychic Battle #1 (singles) |
| 9 | Drayden | Dragon | wiki Dragon team |
| 10 | Morty | Ghost | wiki Ghost team |
| 11 | Viola | Bug | wiki Bug team |
| 12 | Cheren | Normal | wiki Normal team |
| 13 | Koga | Poison | wiki Poison team (restored Poison type; was Lt. Surge/Electric) |
| 14 | Grant | Rock | wiki Rock team |
| 15 | Skyla | Flying | wiki Flying Team #2 (Pom-Pom Oricorio variant) |
| 16 | Brycen | Ice | wiki Ice team |
| 17 | Valerie | Fairy | wiki Fairy team |
| 18 | Marnie | Dark | wiki Dark team (trimmed to 6 of 7) |

## Custom-designed trainers (5 — Elite Four + Champion)

These are not in the ShepskyDad wiki; teams were designed per user spec and slot in the four
"required from monuments" legendaries:

| Gym | Trainer | Theme | Required mon | Notes |
|---|---|---|---|---|
| 20 | Lorelei (E4 #1) | Weather Wars (Sand + Hail) | Ho-oh | Heavy-Duty Boots to skip Stealth Rock 4× weakness |
| 21 | Cynthia (E4 #2) | Hyper Offense | Lucario | Renamed from Bruno per user preference (kept the Hyper Offense template) |
| 22 | Agatha (E4 #3) | Full Stall | Greninja | Slots in for Ferrothorn — covers Spikes + Toxic Spikes |
| 23 | Lance (E4 #4) | Dragon Hyper Offense | Eternatus | Slots in for Goodra |
| 24 | Champion | Sky Conqueror | Mega Rayquaza ×2 (one shiny) | `canMega: true` in AI; both carry Dragon Ascent so they Mega-evolve mid-battle |

## Gym 19 — Professor Oak (Kanto-only, very difficult)

Re-enabled with a custom team built per user spec. All six Pokémon are dex #1–151 (Kanto), and
the team is built around competitive top-tier picks rather than the Cheren clone we'd
considered:

| Pokemon | Ability | Item | Role |
|---|---|---|---|
| Mewtwo (Mega Y when triggered) | Insomnia | Mewtwonite Y | Special wallbreaker — Psystrike + mixed coverage |
| Snorlax | Thick Fat | Leftovers | Status absorber / Rest pivot |
| Dragonite | Multiscale | Heavy-Duty Boots | Dragon Dance + Extreme Speed priority |
| Gengar | Cursed Body | Choice Specs | Special wallbreaker, 4 STAB/coverage |
| Chansey | Natural Cure | Eviolite | Ultimate special wall + Heal Bell support |
| Gyarados | Intimidate | Life Orb | Physical Dragon Dance sweeper |

Reward upgraded to **Ultra Key** (one tier above the standard rotating gym Rare Key) to match
the "very difficult" billing.

`ai.data.canMega: true` permits Mewtwo's Mega Evolution mid-battle (uses the `multipleMegas:
true` Mega Showdown config flag we set when wiring Champion).

## Defaults applied by the generator

The wiki specifies species, ability, held item, and moves but **not** EVs, IVs, or nature.
The generator applies these defaults to every imported Pokémon:

- `level: 50` (matches wiki's "eighth gym" benchmark — bridge `adjust_level` tag handles scaling)
- `nature: jolly` (speed-boosting; suits most attackers)
- `ivs: { hp/atk/def/spa/spd/spe: 31 }` (max across the board)
- `evs: { hp: 4, atk: 252, spe: 252 }` (fast + offensive default — tune per Pokémon role later)

To rebalance a specific Pokémon (e.g. give Skyla's Corviknight a defensive spread), edit its
entry in `trainers/gym_15_flying.json` directly. The generator (`/tmp/gen_trainers.py`) is a
one-shot bootstrap, not a recurring build step — once you've tuned a JSON, regenerating would
clobber the tuning.

## Bridge tags (gameplay wiring)

When you place a gym trainer in the world via a Trainer Spawner block (or `/spawnnpc` if that
works for RCT — check), tag the resulting entity:

```mcfunction
# Award the corresponding server:beat_gym_<N> advancement on defeat
tag @e[type=rctmod:trainer,limit=1,...] add cobblemon_bridge.gym_id.<N>

# Scale player team to N for the battle (set N to whatever gym level you want)
tag @e[type=rctmod:trainer,limit=1,...] add cobblemon_bridge.adjust_level.<N>
```

The bridge mod's `GymDefeatHook` reads `gym_id` on `BATTLE_VICTORY` and awards. The
`AdjustLevelHook` reads `adjust_level` on `EntityInteract` and applies to the format.

## Reload

`/reload` after editing — datapack hot-reloads. RCT trainer registry refreshes.
