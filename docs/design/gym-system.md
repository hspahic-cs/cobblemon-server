# Gym System

## The Town

The server hub town. Contains:
- A Pokemon Center
- A Pokemart
- **10-floor Gym Tower** — 10 permanent gym leaders, one per floor, part of normal progression
- **3-floor Challenge Tower** — standalone gauntlet; beat all 3 floors in one run (no leaving) for $3,000 cash; trainers are fixed at competitive level; no progression credit
- The Elite Four (separate map, teleport command)
- 2 singles arenas + 1 doubles/triples arena for PvP
- EV training area

## Gym Leader Progression

Each gym leader has their own persistent team that grows over time. They do
NOT scale to the challenging player — a gym leader is a character on the
server, not a difficulty slider.

### Level caps by hut tier

| Hut Tier | Team Level Cap |
|----------|----------------|
| 1        | 20             |
| 2        | 40             |
| 3        | 60             |
| 4        | 80             |
| 5        | 100            |

- **Fresh leader:** a newly-hired gym leader starts with 3 Pokemon (their
  theme's `startingThree`) at the hut tier's level cap.
- **Growth:** every battle the leader *loses* grants each Pokemon on their
  team 4–6 levels (weighted — stragglers catch up faster than flagships),
  clamped at the current cap.
- **Wins don't grow them.** If players keep winning, the leader stays put.
  Growth is earned by the community beating on them.
- **Hut upgrade:** raises the cap. Team stays at its current levels and
  continues to grow toward the new ceiling battle by battle.
- **Hut downgrade:** cap drops, but existing team levels are preserved
  (one-way ratchet — demoting a hut does not nerf its leader).

### Team escalation bands (within any cap)

| Team avg level | Roster                              |
|----------------|-------------------------------------|
| 1–15           | 3 Pokemon, no items                 |
| 16–30          | 4 Pokemon, basic items              |
| 31–50          | 5 Pokemon, full items               |
| 51–65          | 6 Pokemon, full items               |
| 66–80          | 6 Pokemon + mega stone active       |
| 81+            | 6 Pokemon + mega + legendary slot   |

New team slots are added as the leader levels into the next band, drawn from
the theme's `maxTeam` order. Legendary slots unlock only at hut tier 5.

## AI Difficulty

- **Gym leaders:** always use `StrongBattleAI(skill=5)` — fully optimal, no
  randomness in decision-making. Every gym leader battle is maximally
  challenging regardless of team level or tier.
- **Normal NPCs:** use `StrongBattleAI` with `skill` derived from the
  citizen's average Minecolonies skill stats (across all 11 skills):

  | Avg citizen stat | AI skill | Feel |
  |------------------|----------|------|
  | < 15             | 1        | Rookie — mostly random from own moveset |
  | 15–22            | 2        | Apprentice — 40% optimal |
  | 23–30            | 3        | Experienced — 60% optimal |
  | 31–40            | 4        | Veteran — 80% optimal |
  | 41+              | 5        | Master — fully optimal |

  This keeps fights with normal villagers readable and scales difficulty
  naturally with colony maturity.

## Player Flow

1. Walk up to any gym leader → dialogue offers "Battle" or "Cancel"
2. Gym leader uses their persistent team at its current levels
3. Win or lose, talk to the gym leader again for one-time rewards if you've
   never beaten them before; otherwise friendly rematch
4. Losses grow the leader; wins leave them unchanged

No gatekeeping — you can challenge any gym leader at any time.

## Rewards

Every gym win grants items + cash (recorded per player, spendable at the Sky Town Pokemart).

| Tier | Items                              | Themed Item                      | Cash  |
|------|------------------------------------|----------------------------------|-------|
| 1    | 5× Poké Ball + 3× Potion           | Type-themed Berry                | TBD   |
| 2    | 10× Great Ball                     | Type-themed TM (basic move)      | TBD   |
| 3    | 5× Ultra Ball                      | Type-matched evolution item (e.g. Dragon Scale, Metal Coat, Dusk Stone — matched to gym type's Pokemon) | TBD   |
| 4    | 5× Ultra Ball                      | Type-boosting Plate + Rare Candy | TBD   |
| E4   | 1× Master Ball                     | Type-themed TM (advanced move)   | TBD   |

**Pokemart** — located in Sky Town, accepts gym cash. Sells held items, evolution items, and other goods not given directly as rewards. NPC right-click opens a GUI shop. Implemented via **Impactor** (economy backbone, `main_currency: "impactor"`) + **Cobblemon Economy** (shop front-end only — quest features unused).

## Challenge Mode

Unlocked after beating the Elite Four. Each gym leader gains a level 100 battle option.

- Beating a gym leader in Challenge Mode earns their **themed Badge** (one per leader, permanent)
- No other reward — the badge is the prize
- Challenge Mode teams are fully competitive (level 100, optimized movesets/EVs/IVs)

## Implementation

**Datapack (no mod needed):**
- Gym leader NPCs with per-tier teams + a separate Challenge Mode team (full control: species, level, moves, abilities, IVs, EVs, held items)
- Dialogue trees with conditional branching via MoLang — challenge mode option gated behind E4 completion flag
- Per-player state tracking via `save_data` / `get_npc_variable`
- Reward granting via `run_command` / `give_item`
- Battle initiation via `q.npc.start_battle()`

**Implemented by `cobblemon-npc` (NeoForge, `custom-mods/cobblemon-npc/`):**
- Minecolonies `JobGymLeader` + gym-leader hut block — assigns citizens as gym leaders via normal colony hiring flow
- Per-leader persistent team state (species, levels, progression) stored as NBT attachment data
- Tier progression driven by battle losses (see `TeamProgressionManager`); hut tier caps team level
- AI difficulty selection: `skill=5` for gym leaders, avg-skill mapping for normal citizens
- Cash rewards on `BATTLE_VICTORY` (CobbleDollars bridge currently disabled — rewards logged only)

## Open Questions

- Pokemart stock and prices (cash amounts proposed: T1 $100 / T2 $250 / T3 $500 / T4 $1,000 / E4 $5,000)
- Confirm ShopGUI+ (or alternative) is compatible with NeoForge 1.21.1 + Impactor
- Gym leader team compositions per type per tier (reference YouTube series)
- Elite Four team compositions

