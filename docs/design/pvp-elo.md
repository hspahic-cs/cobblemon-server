# PvP Elo System

Ranked PvP ladder using Cobblemon's built-in challenge system. Battles happen at the 2 arenas in Sky Town.

Cobblemon already has player-to-player challenges (singles, doubles, triples, multi, royal) with a built-in `adjustLevel` option that can force all Pokemon to a flat level for fairness.

## Rules

- All players start at 1000 Elo
- Ranked battles are level-scaled (all Pokemon set to 50) so progression doesn't determine outcome
- Format is player's choice: singles or doubles (doubles arena supports triples if requested)
- A lower Elo player can issue a ranked challenge to a higher Elo player, once per day
- Being challenged starts a 24-hour activity timer — if the target doesn't complete any ranked battle within that window, they lose Elo as a forfeit
- Battles can happen anywhere; both players must be at spawn to start a match at an available arena with the 60-second betting window
- Any player can send a friendly challenge to any other player — if accepted, the battle happens but no Elo changes
- Elo only updates on the first ranked match between a pair per day

## Betting

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

## Commands

- `/leaderboard` — top 5 players + their Elo, and your own Elo/rank
- `/rankchallenge <player> [amount]` — issue a ranked challenge (only if your Elo < theirs, once per day); starts a 24-hour activity window for the target
- `/bet <player> <amount>` — bet on a player in an active ranked match during the betting window
- Friendly battles use Cobblemon's built-in challenge system (no Elo)

## Implementation

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

## Open Questions

- *(no open questions)*

