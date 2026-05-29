# Ranked Battle System

## Summary

Implement the full ranked PvP ladder using Cobblemon's built-in challenge system. Requires a custom Fabric mod (`cobblemon-pvp`) and arena setup in Pixeltown.

## Scope

### Custom Mod (`cobblemon-pvp`)

- [ ] Elo tracking per player (persistent storage, all start at 1000)
- [ ] Standard Elo formula with K-factor 32
- [ ] `/leaderboard` — top 5 players + Elo, and caller's own rank
- [ ] `/rankchallenge <player> [amount]` — issue ranked challenge (only if caller Elo < target, once per day)
- [ ] `/bet <player> <amount>` — bet on a player during active ranked match betting window
- [ ] Once-per-day ranked challenge enforcement
- [ ] 24-hour activity window — if target completes no ranked battle, they forfeit Elo to challenger
- [ ] On accept: teleport both players to an available arena at Pixeltown spawn
- [ ] 60-second betting window opens — server-wide broadcast with player names + Elo
- [ ] Battle starts when betting window closes
- [ ] Wager escrow — hold funds from both players on accept, release to winner on `BATTLE_VICTORY`
- [ ] Spectator bet registry — track bets per match, distribute winnings proportionally on `BATTLE_VICTORY`
- [ ] Disconnect handler — 30-second reconnect timer (once per match); second disconnect = forfeit, bets settle immediately
- [ ] Config file: betting window duration, reconnect timeout, bet cap percentage (default 50%)

### Cobblemon Integration

- [ ] Use `ChallengeManager` for challenge send/accept/decline/expire flow
- [ ] Use `BattleBuilder.pvp1v1()` with `adjustLevel` set to 50 for ranked matches
- [ ] Use `BattleFormat` for singles/doubles with level scaling
- [ ] Listen to `BATTLE_VICTORY` event for Elo updates and bet settlement

### Economy Integration

- [ ] Wagers via Impactor `EconomyService` — escrow on accept, release on `BATTLE_VICTORY`
- [ ] Spectator bets held in escrow, distributed to winning side proportionally
- [ ] Wagers capped at 50% of player balance (configurable)
- [ ] Spectator bets capped at 50% of bettor balance (configurable)

### World Setup

- [ ] 2 singles arenas + 1 doubles/triples arena in Pixeltown
- [ ] Arenas must be bookable (only one match per arena at a time)
- [ ] Players teleport to available arena on ranked challenge accept

## Rules Summary

- All players start at 1000 Elo
- Ranked battles are level-scaled (all Pokemon forced to level 50)
- Challenger must have lower Elo than target
- One ranked challenge per pair per day
- Friendly battles use Cobblemon's built-in system — no Elo changes
- Elo only updates on first ranked match between a pair per day

## Commands

| Command | Description |
|---------|-------------|
| `/leaderboard` | Top 5 players + Elo and caller's rank |
| `/rankchallenge <player> [wager]` | Issue ranked challenge |
| `/bet <player> <amount>` | Bet on a player during betting window |

## Acceptance Criteria

- [ ] Players can issue and accept ranked challenges
- [ ] Elo updates correctly on win/loss using standard formula
- [ ] Betting window opens on match start, closes after 60 seconds
- [ ] Wagers held in escrow and released correctly
- [ ] Spectator bets distributed proportionally to winning side
- [ ] Disconnect handled — forfeit on second disconnect
- [ ] Leaderboard shows correct top 5 + caller rank
- [ ] All values configurable via config file
- [ ] Friendly battles have no Elo impact

## Open Questions

- Which persistent storage backend for Elo? (SQLite via existing `sqlite-jdbc`, flat file, or NBT player data)
- Arena booking system — how to mark an arena as occupied and release it after match ends
