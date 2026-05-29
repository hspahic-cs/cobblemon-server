# Ranked PvP

> **Status:** stub — full guide pending. Some commands may not be implemented yet.

Compete on a server-wide Elo ladder. Everyone starts at 1000.

## Commands

- `/leaderboard` — top 5 players + your own rank
- `/rankchallenge <player> [amount]` — issue a ranked challenge (you can
  only challenge players with higher Elo than you, once per day)
- `/bet <player> <amount>` — bet on someone during the 60-second pre-match
  window once a ranked match is announced

## How it works

- Both players ante up coins on accept; winner takes the pot
- A 60-second betting window opens for spectators before the match starts
- Spectator bets are pooled and paid out proportionally to winners
- Standard Elo formula, K-factor 32

## Read more

- **[PvP Elo design](../design/pvp-elo.md)** — the math and rationale
- **[Ranked battle system spec](../design/ranked-battle-system.md)** — implementation details
