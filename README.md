# Cobblemon Server

## Platform

| Component     | Version     |
|---------------|-------------|
| Minecraft     | 1.21.1      |
| Mod Loader    | NeoForge    |
| NeoForge      | 21.1.227    |
| Java          | 21          |
| Cobblemon     | 1.7.3       |

## Mods

Grouped by purpose. Everything below is installed on the live server unless
marked otherwise.

### Core gameplay
| Mod | Purpose |
|-----|---------|
| Cobblemon | Core Pokemon mod |
| Cobblemon: Legendary Monuments | Structure-based legendary quests (45+ legendaries) — runs via Sinytra Connector |
| mega_showdown | Mega evolutions, Z-moves, Dynamax in Cobblemon battles |
| Cobblemon Ranked | PvP Elo ladder backbone |
| CobbleFurnies | Cobblemon-themed decoration blocks |
| Cobblemon Integrations | Bridges Cobblemon with Waystones, JEI, Jade, Serene Seasons, Enhanced Celestials |
| Serene Seasons | Seasonal spawns + regional form variants (Spring Espeon, Summer Glaceon, Fall Sylveon, Winter Jolteon) |
| Enhanced Celestials | Lunar events affect shiny rates / EVs + variants (Blood Moon Umbreon, Harvest Moon Leafeon, Blue Moon Flareon) |
| Terralith | Expanded biome set that Cobblemon already spawns Pokemon into |

### NPCs and towns
| Mod | Purpose |
|-----|---------|
| Minecolonies | Villager-citizen colony mechanic — provides the NPC substrate for gym leaders and generic trainers |
| Structurize, BlockUI, MultiPiston, Domum Ornamentum | Minecolonies' required libs (building, UI, custom blocks) |
| cobblemon-npc *(custom, in-repo)* | Gym-leader hut + job, profession-based team generation, battle handler, tier progression |
| Waystones | Player-placed teleport network (bridged into Pokemon interactions via Cobblemon Integrations) |

### World tools
| Mod | Purpose |
|-----|---------|
| WorldEdit | Build tooling, schematic import/export |
| Multiworld | Hosts separate dimensions for spawn, arenas, Elite Four |

### Cross-loader bridges (only needed for Connector-wrapped Fabric mods)
| Mod | Purpose |
|-----|---------|
| Sinytra Connector | Runs Fabric mods on NeoForge (specifically LegendaryMonuments) |
| Forgified Fabric API | Neo port of Fabric API — Connector dependency |
| accessories, owo-lib | Mega Showdown / LegendaryMonuments accessory system |
| TerraBlender, chipped, resourcefullib | LegendaryMonuments world-gen / block deps |

### Libraries
| Mod | Purpose |
|-----|---------|
| Kotlin for Forge | Kotlin runtime for Cobblemon + cobblemon-npc |
| Architectury | Cross-loader API used by several mods |
| balm | Shared lib (Waystones) |
| GlitchCore, CorgiLib, Data Anchor | Shared libs (Serene Seasons, Enhanced Celestials) |

### Performance and ops
| Mod | Purpose |
|-----|---------|
| Lithium (NeoForge) | Server-tick + chunk-ticking optimizations |
| FerriteCore | Memory dedup for blockstates/models |
| ModernFix | Startup + dynamic-resource + leak fixes |
| Spark | Profiler — `/spark profiler` for diagnosing lag spikes |

### Client-only (not installed on server; see `client-extras/`)
Sodium, ImmediatelyFast, EntityCulling, Fast IP Ping, Xaero's Minimap,
Xaero's World Map, JEI.

## Gym System

### The Town

The server hub town. Contains:
- A Pokemon Center
- A Pokemart
- **10-floor Gym Tower** — 10 permanent gym leaders, one per floor, part of normal progression
- **3-floor Challenge Tower** — standalone gauntlet; beat all 3 floors in one run (no leaving) for $3,000 cash; trainers are fixed at competitive level; no progression credit
- The Elite Four (separate map, teleport command)
- 2 singles arenas + 1 doubles/triples arena for PvP
- EV training area

### Gym Leader Progression

Each gym leader has their own persistent team that grows over time. They do
NOT scale to the challenging player — a gym leader is a character on the
server, not a difficulty slider.

#### Level caps by hut tier

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

#### Team escalation bands (within any cap)

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

### AI Difficulty

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

### Player Flow

1. Walk up to any gym leader → dialogue offers "Battle" or "Cancel"
2. Gym leader uses their persistent team at its current levels
3. Win or lose, talk to the gym leader again for one-time rewards if you've
   never beaten them before; otherwise friendly rematch
4. Losses grow the leader; wins leave them unchanged

No gatekeeping — you can challenge any gym leader at any time.

### Rewards

Every gym win grants items + cash (recorded per player, spendable at the Sky Town Pokemart).

| Tier | Items                              | Themed Item                      | Cash  |
|------|------------------------------------|----------------------------------|-------|
| 1    | 5× Poké Ball + 3× Potion           | Type-themed Berry                | TBD   |
| 2    | 10× Great Ball                     | Type-themed TM (basic move)      | TBD   |
| 3    | 5× Ultra Ball                      | Type-matched evolution item (e.g. Dragon Scale, Metal Coat, Dusk Stone — matched to gym type's Pokemon) | TBD   |
| 4    | 5× Ultra Ball                      | Type-boosting Plate + Rare Candy | TBD   |
| E4   | 1× Master Ball                     | Type-themed TM (advanced move)   | TBD   |

**Pokemart** — located in Sky Town, accepts gym cash. Sells held items, evolution items, and other goods not given directly as rewards. NPC right-click opens a GUI shop. Implemented via **Impactor** (economy backbone, `main_currency: "impactor"`) + **Cobblemon Economy** (shop front-end only — quest features unused).

### Challenge Mode

Unlocked after beating the Elite Four. Each gym leader gains a level 100 battle option.

- Beating a gym leader in Challenge Mode earns their **themed Badge** (one per leader, permanent)
- No other reward — the badge is the prize
- Challenge Mode teams are fully competitive (level 100, optimized movesets/EVs/IVs)

### Implementation

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

### Open Questions

- Pokemart stock and prices (cash amounts proposed: T1 $100 / T2 $250 / T3 $500 / T4 $1,000 / E4 $5,000)
- Confirm ShopGUI+ (or alternative) is compatible with NeoForge 1.21.1 + Impactor
- Gym leader team compositions per type per tier (reference YouTube series)
- Elite Four team compositions

## PvP Elo System

Ranked PvP ladder using Cobblemon's built-in challenge system. Battles happen at the 2 arenas in Sky Town.

Cobblemon already has player-to-player challenges (singles, doubles, triples, multi, royal) with a built-in `adjustLevel` option that can force all Pokemon to a flat level for fairness.

### Rules

- All players start at 1000 Elo
- Ranked battles are level-scaled (all Pokemon set to 50) so progression doesn't determine outcome
- Format is player's choice: singles or doubles (doubles arena supports triples if requested)
- A lower Elo player can issue a ranked challenge to a higher Elo player, once per day
- Being challenged starts a 24-hour activity timer — if the target doesn't complete any ranked battle within that window, they lose Elo as a forfeit
- Battles can happen anywhere; both players must be at spawn to start a match at an available arena with the 60-second betting window
- Any player can send a friendly challenge to any other player — if accepted, the battle happens but no Elo changes
- Elo only updates on the first ranked match between a pair per day

### Betting

**Player vs Player wagers:**
- Either player can attach a cash wager when issuing or accepting a challenge
- Both players must agree to the wager amount before the battle starts
- Wagers capped at 50% of the wagering player's current balance (configurable)
- Winner takes the full pot; funds are held in escrow during the battle
- Applies to both ranked and friendly matches

**Spectator betting:**
- When a ranked match is declared official, a 60-second betting window opens (configurable)
- Any player can bet on either participant during the window; the battle does not start until it closes
- Bets capped at 50% of the bettor's current balance (configurable) — prevents all-in desperation bets
- Winner's backers split the losing side's pot proportionally to their stake
- If a combatant disconnects mid-battle, they have 30 seconds to reconnect (configurable); this grace period can only trigger once per match — a second disconnect counts as a forfeit, bets settle as normal

### Commands

- `/leaderboard` — top 5 players + their Elo, and your own Elo/rank
- `/rankchallenge <player> [amount]` — issue a ranked challenge (only if your Elo < theirs, once per day); starts a 24-hour activity window for the target
- `/bet <player> <amount>` — bet on a player in an active ranked match during the betting window
- Friendly battles use Cobblemon's built-in challenge system (no Elo)

### Implementation

**Cobblemon provides:**
- `ChallengeManager` — full challenge send/accept/decline/expire flow
- `BattleBuilder.pvp1v1()` — starts a PvP battle with optional `adjustLevel`
- `BattleFormat` — singles/doubles/triples with level scaling
- `BATTLE_VICTORY` event — winner/loser callbacks

**Custom NeoForge mod (`cobblemon-pvp`, not yet built) adds:**
- Elo tracking per player (persistent storage)
- Standard Elo formula (K-factor: 32)
- `/leaderboard`, `/rankchallenge`, and `/bet` commands
- Once-per-day ranked challenge enforcement (challenger must have lower Elo)
- 24-hour activity window on challenge: if target completes no ranked battle, Elo is deducted as a forfeit win for challenger
- On accept: both players teleport to an available arena at spawn; 60-second betting window opens
- Wager escrow: hold funds from both players on challenge accept, release to winner on `BATTLE_VICTORY`
- Spectator bet registry: track bets per active ranked match, distribute winnings on `BATTLE_VICTORY`
- 60-second pre-match countdown announced via server-wide chat broadcast (player names + Elo); spectator bets accepted during this window, battle starts when timer expires
- Disconnect handler: on player disconnect, start a 30-second reconnect timer (once per match); second disconnect = forfeit, bets settle immediately
- Listen to `BATTLE_VICTORY` to update Elo and settle all bets
- Config file exposes: betting window duration, reconnect timeout, bet cap percentage

### Open Questions

- *(no open questions)*

## Repo layout

```
cobblemon-server/
├── custom-mods/             # mods this repo builds (CI ships them on tag)
│   └── cobblemon-npc/
├── modpack/                 # packwiz source for the client .mrpack
├── client-extras/           # client-only jars not in the modpack
├── docs/                    # design notes, install guides, upstream links
└── mods/                    # gitignored — local clones of upstream mod
                             # source for reference / compileOnly jars.
                             # See docs/upstream-sources.md
```

## Working with mods

Full E2E guide: **[docs/working-with-mods.md](docs/working-with-mods.md)** —
edit a mod, ship to dev, promote to prod, troubleshoot.

TL;DR: edit code on a branch → PR → merge → bump `CHANGELOG.md` →
push → dev auto-deploys. Tag `vX.Y.Z` for a GitHub Release. Manually run
"Deploy prod" to promote.

## Releasing

One version covers the entire repo. Bump it like this:

```
scripts/bump-version.sh 0.5.0
# update CHANGELOG.md — move [Unreleased] entries into a new [0.5.0] section
git add CHANGELOG.md modpack/pack.toml custom-mods/cobblemon-npc/gradle.properties
git commit -m "release: 0.5.0"
git push    # CHANGELOG bump on main triggers Deploy dev
git tag v0.5.0 && git push --tags    # tag triggers Release (drafts GitHub Release)
gh workflow run deploy-prod.yml -f tag=v0.5.0    # promote when ready
```

See [docs/working-with-mods.md](docs/working-with-mods.md) for details.

## Setup

<!-- TBD -->
