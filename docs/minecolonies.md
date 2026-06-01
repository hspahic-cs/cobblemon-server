# Minecolonies

Building a town with NPC citizens who happen to also be Pokémon
trainers.

## Starting a colony

You unlock the **Supply Camp Deployer** at the `reach_income_1000`
quest (Founding Fortune). Place it down anywhere in the overworld, log
through the build sequence, and you've got a colony.

From there, follow [the standard Minecolonies tutorial](https://wiki.minecolonies.ldtteam.com/source/getting_started/index.html) —
nothing in the colony lifecycle is server-customized. Town Hall,
Builder, etc. are vanilla Minecolonies.

The server bundles **Domum Ornamentum**, **Structurize**, **MultiPiston**,
and **BlockUI** alongside Minecolonies — all required for building
schematics.

## Where it differs from vanilla Minecolonies

Two things this server adds:

### 1. Citizens are Pokémon trainers

The **Cobblemon NPC** custom mod turns every Minecolonies citizen into
a battleable trainer. Right-click any citizen to challenge them.

**How their team grows:**

- **Every citizen spawns with one Pokémon** drawn from the "unemployed"
  pool, regardless of their assigned job.
- **Every time you beat one of their Pokémon, that Pokémon levels up
  +4–6.** Wins don't level them; only losses do. The progression is
  weighted so weak Pokémon catch up to the team average.
- When **all** of a citizen's Pokémon hit a tier threshold (level 15 →
  30 → 45 → 55 → 65), they advance a tier: their team grows by one
  slot, and they unlock held items.

  | Tier | All-Pokémon level | Team size | Held items |
  |------|-------------------|-----------|------------|
  | 1 | spawn | 1 | none |
  | 2 | 15 | 2 | none |
  | 3 | 30 | 3 | basic |
  | 4 | 45 | 4 | full |
  | 5 | 55 | 5 | full |
  | 6 | 65 | 6 | full |

- **Profession drift:** every 3 battles, if a citizen has any Pokémon
  not from their job's pool, one mismatched Pokémon gets swapped for
  one from the profession pool. So a freshly-employed Miner gradually
  fills out with rock/ground/dark mons over time.

**Profession pool themes** (43 jobs total — sample):

| Job | Theme |
|-----|-------|
| Miner | Underground / Rock / Ground / Dark |
| Forester | Grass / Bug — forest dwellers |
| Fisher | Water types |
| Farmer | Grass / Normal — pastoral |
| Smelter | Fire types |
| Baker | Food / round species |
| Beekeeper | Bug + flower types |
| Florist | Grass + Fairy |

**No legendaries, mythicals, or megas** in any citizen pool. Those are
reserved for **gym leader** NPCs, which use a separate pool tied to
their `hut_gym_leader` building level.

Battle bounties pay out like any other trainer fight (small ¢ reward
per win, scaled to citizen tier). Defeats count toward your Pokédex
completion if the species is new.

### 2. The Poké Healer reward

The **join_colony** quest hands you a **Healing Machine block** —
place it inside your colony's centre so your citizen-trainers don't
deplete your party between fights.

## Quests in the chain

| Quest | Reward |
|-------|--------|
| `reach_income_1000` | Supply Camp Deployer |
| `join_colony` | Healing Machine (Poké Healer block) |

Beyond those two, Minecolonies progression is purely Minecolonies'
own quest book + research tree.

## Tips

- Citizens spawn naturally inside built structures; you don't summon
  them.
- Citizen battles fire from **right-click**, not from running into
  them. They won't aggro on you.
- The **Pasture Block** ([Cobbleworkers](economy-and-automation.md#cobbleworkers))
  pairs well with the Farmer building — keep your work pasture inside
  the colony so a Forester-citizen-trainer is always five blocks away
  when you need an XP grind.
- Minecolonies' raids still happen. Hostile mob spawns are otherwise
  disabled server-wide (see [Pokémon spawns](pokemon-spawns.md)), so
  raids are the main "vanilla combat" event.
