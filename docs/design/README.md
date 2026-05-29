# Design rationale

The "why" behind the systems players interact with. Not how-to guides — those live in
[`docs/player/`](../player/) for players and [`docs/dev/`](../dev/) for operators.

## Systems

- **[Economy design](economy-design.md)** — two-currency model (coins + BP), time→value anchors, stock market mechanics
- **[Gym system](gym-system.md)** — tower structure, tier progression, challenge mode, rewards
- **[PvP Elo](pvp-elo.md)** — ranked ladder, K-factor, betting math
- **[Team & faction system](team-faction-system.md)** — Valor/Mystic/Instinct, sub-factions, territory, raids
- **[Ranked battle system](ranked-battle-system.md)** — implementation spec for the `cobblemon-pvp` mod
- **[Player feedback](player-feedback.md)** — `/feedback` design (phase 1)
- **[Player feedback phase 2](player-feedback-phase2.md)** — screenshot capture + R2 upload + PII anonymization

## Reference data

In [`reference/`](reference/):

- **[Cobblemon item value guide](reference/cobblemon-item-value-guide.md)** — 300+ items audited by survival obtainability. Drives stock market base prices.
- **[Gym leader teams](reference/gym-leader-teams.md)** — full rosters per tier
- **[Gym leader pool](reference/gym-leader-pool.json)** — themed templates
- **[Confirmed legendaries](reference/confirmed-legendaries.txt)** — legendary spawn list
