# Cobblemon Server

## Platform

| Component  | Version |
|------------|---------|
| Minecraft  | 1.21.1  |
| Mod Loader | Fabric  |
| Cobblemon  | 1.7.3   |

## Mods

| Mod | Purpose |
|-----|---------|
| Cobblemon | Core Pokemon mod |
| Cobblemon: Legendary Monuments | Structure-based legendary quests (45+ legendaries) |
| falcraft | AI-powered in-world structure generation *(build-time only, not on live server)* |
| Impactor | Economy backbone — currency storage and API |
| Cobblemon Economy | Pokemart NPC shop front-end |

## Gym System

### The Town

The server hub town. Contains:
- A Pokemon Center
- A Pokemart
- **10-floor Gym Tower** — 10 permanent gym leaders, one per floor, part of normal progression
- **3-floor Challenge Tower** — standalone gauntlet; beat all 3 floors in one run (no leaving) for $3,000 cash; all trainers are max tier (level 45); no progression credit
- The Elite Four (separate map, teleport command)
- 2 singles arenas + 1 doubles/triples arena for PvP
- EV training area

### Progression

Gyms use a tiered level cap system. All gyms scale to the player's current tier — not a fixed order.

| Tier | Level Cap | Requirement to Advance    |
|------|-----------|---------------------------|
| 1    | 15        | Beat any 3 of 10 gyms     |
| 2    | 25        | Beat any 3 of 10 gyms     |
| 3    | 35        | Beat any 3 of 10 gyms     |
| 4    | 45        | Beat any 3 of 10 gyms     |
| E4   | 50        | Beat the Elite Four       |

- Gym leaders use a team matching YOUR current level tier
- Beating 3 gyms at your tier raises the level cap — your Pokemon can now level past it
- ALL gym leaders advance to the new tier for you (per-player scaling)
- Once you beat all gyms at tier 4, you challenge the Elite Four at level 50 (competitive standard)
- Any gym rematches after E4 use a competitive level 50 team

### Player Flow

1. Walk up to any gym leader → dialogue offers "Battle" or "Cancel"
2. Gym leader uses a team scaled to your current tier
3. Win the battle
4. Talk to the gym leader again → they give gym-leader-specific rewards and mark the gym as beaten for your current tier
5. Already beaten them this tier → friendly rematch, no reward
6. Beat 3 gyms at your tier → tier advances, level cap raises, all gym leaders scale to new tier

No gatekeeping — you can challenge any gym leader at any time. Victories only count toward tier progression if you haven't already beaten that leader at your current tier. Rewards are one-time per gym per tier and scale with tier difficulty. After claiming, you can still rematch for fun but get nothing.

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

**Requires a custom Fabric mod (`cobblemon-gym`):**
- Level cap enforcement — intercept `EXPERIENCE_GAINED_EVENT_PRE` and block XP if Pokemon would exceed current tier cap
- Block Rare Candy and Exp Candy from pushing Pokemon past the current tier cap
- Cash granted on gym win via Impactor Economy API (`EconomyService.deposit()`)

### Open Questions

- Pokemart stock and prices (cash amounts proposed: T1 $100 / T2 $250 / T3 $500 / T4 $1,000 / E4 $5,000)
- Confirm ShopGUI+ (or alternative) is compatible with Fabric 1.21.1 + Impactor
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

**Custom Fabric mod (`cobblemon-pvp`) adds:**
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

## Setup

<!-- TBD -->
