# Team & Faction System

## Summary

A long-term engagement layer built on top of the gym and ranked battle systems. Players choose one of three teams (Valor, Mystic, Instinct), then optionally form sub-factions to claim wilderness territory, build faction gyms, and unlock upgrades using Battle Points earned through gameplay.

Designed for a small server (~15 players). Raid mechanic is deferred until player base grows.

---

## Two-Layer Structure

| Layer | What it is | Formed by |
|-------|-----------|-----------|
| Team | Valor, Mystic, or Instinct | Player choice on first login |
| Sub-faction | A named group of players who claim land together | Players via `/faction create` |

A sub-faction belongs to exactly one team. Players without a sub-faction can still join a team, challenge gyms for BP, and contribute to the Legendary Attractor.

---

## Teams

| Team | Color | Mascot |
|------|-------|--------|
| Valor | Red | Moltres |
| Mystic | Blue | Articuno |
| Instinct | Yellow | Zapdos |

No team balance enforcement — friend groups join together.

---

## Scoring

### BP (Battle Points)
Individual, persistent. Primary currency for upgrades and territory expansion.

| Action | BP |
|--------|-----|
| Ranked win | +2 |
| Friendly win | +1 |
| Gym challenge win (attacker) | +5–30 (scales with gym tier) |
| Gym defense win (NPC) | +3–18 (scales with gym tier) |
| Live defense win (player) | 1.5× gym defense reward |
| Team Showdown win | +3 |

### GCP (Gym Control Points)
Team score, resets bi-weekly.

| Action | GCP |
|--------|-----|
| Gym challenge win | +3 |
| Gym defended | +1 |
| Gym lost | -1 |


---

## Territory & Land Claiming

- Sub-factions claim chunks anywhere in the wilderness world
- Every faction gets 1 free chunk on creation (starter claim)
- Additional chunks purchased from the faction's shared BP pool

| Chunks owned | Cost per additional chunk |
|-------------|--------------------------|
| 1–3 | 100 BP |
| 4–6 | 200 BP |
| 7–10 | 400 BP |
| 11+ | 800 BP |

Claimed chunks are protected — rival players cannot place or break blocks. Faction members build freely inside.

---

## Faction Gyms

### How It Works
- Challenges are command-based (`/faction challenge <name>`)
- On challenge: both parties teleport to an available Pixeltown arena
- Faction leader registers up to 6 Pokémon as the gym team via `/faction gymset`
- NPC uses that team, level-capped by gym tier

### Defense
| Scenario | What Happens |
|----------|-------------|
| No members online | NPC defends automatically |
| Member online | Gets a ping, can choose to fight manually |
| Manual defense win | 1.5× BP reward |
| Attacker wins | Standard BP reward regardless of NPC or live defense |

### Gym Tiers

| Tier | NPC Level Cap | Attacker BP | Defender BP | Upgrade Cost |
|------|--------------|-------------|-------------|-------------|
| 1 | 30 | 5 | 3 | Free |
| 2 | 50 | 10 | 6 | 150 BP |
| 3 | 75 | 18 | 10 | 350 BP |
| 4 | 100 | 30 | 18 | 750 BP |

---

## Upgrades

Upgrades are purchased and managed via `/faction upgrade <type>`. No physical block placement required — an upgrade is active as long as it exists in the faction's upgrade list.

| Upgrade | Effect | BP Cost |
|---------|--------|---------|
| Rare Spawn Beacon | Elevated spawn rate for one rare/pseudo-legendary in your chunks | 250 |
| Type Lure | Chosen type spawns 3× more frequently in territory | 150 |
| Shiny Magnet | Shiny rate boosted 1.5× within claimed chunks | 500 |
| IV Tutor NPC | Boosts 1 random IV to 31, once per day per member | 300 |
| Nature Minter NPC | Applies a mint of choice, free, once per week per member | 200 |
| Breeding Nursery | Eggs hatch in 50% fewer steps for faction members | 200 |
| Training Ground | +25% EXP while in claimed territory | 150 |
| Move Tutor NPC | Exclusive tutor moves unavailable elsewhere | 400 |
| Held Item Cache | Faction-only shop: rare held items at cost | 350 |
| Outpost | +4 claimable chunks to faction cap (stackable) | 300 |
| Faction Warp | `/faction warp <name>` for team members | 100 |
| Vault | Shared item storage, team-only access | 100 |
| Team Relay | Passive GCP trickle to team per reset period | 400 |

---

## Legendary Availability

Some legendaries spawn naturally in the wild and can be caught by any player:

ARTICUNO, AZELF, ARCEUS, CELEBI, CHIEN-PAO, CHI-YU, COBALION, COSMOG, CRESSELIA, DARKRAI, DIALGA, ENTEI, ETERNATUS, GIRATINA, HEATRAN, HO-OH, HOOPA, KELDEO, KYUREM, LATIAS, LATIOS, LUGIA, LUNALA, MEW, MESPRIT, MOLTRES, PALKIA, RAIKOU, REGICE, REGIDRAGO, REGIELEKI, REGIGIGAS, REGIROCK, REGISTEEL, RESHIRAM, SOLGALEO, SUICUNE, TERRAKION, TING-LU, UXIE, VICTINI, VIRIZION, WO-CHIEN, ZACIAN, ZAMAZENTA, ZAPDOS, ZEKROM

All other legendaries are unobtainable in the wild and can only be accessed through the Legendary Attractor.

---

## Legendary Attractor (Team-Wide)

The flagship feature — the only way to access legendaries that don't spawn in the wild.

- Each team has a shared BP pool
- Any team member can contribute BP: `/team attractor contribute <amount>`
- When the pool hits the threshold, the legendary spawns in the territory of the faction that contributed the most BP to that pool. If that faction has no claimed territory, the spawn falls to the next highest contributing faction with territory.
- All team members who contributed at least 200 BP receive a personal encounter with the legendary, regardless of which faction they belong to
- Pool resets after spawn; players choose the next target

| BP Threshold | Min Contribution to Qualify | Spawn Duration |
|-------------|----------------------------|----------------|
| 2,000 BP | 200 BP | 1 hour |

---

## Raid System (Deferred — enabled via admin toggle)

- `/faction raid <name>` — formal declaration, server-wide broadcast
- 24-hour notice window — defending faction knows in advance
- Attacker picks a 2-hour combat window within 48 hours of declaration
- Undefended: NPC defends; attacker win proceeds to standard outcome
- Defended: live battles, NPC fills empty defender slots
- Outcome: winner steals 300 BP from the losing faction's pool. If the pool has less than 300 BP, upgrades are sold at their original cost (random selection) until covered. Any shortfall after the last upgrade is forgiven.
- Raid cooldown: 48 hours per target

---

## Leaderboard & Resets

- GCP resets **bi-weekly** (gives small factions time to build)
- Season = 8 resets (~16 weeks)

**Bi-weekly reset rewards (per player on winning team):**

| Place | Reward |
|-------|--------|
| 1st | 2 Rare Candy + 300 coins |
| 2nd | 1 Rare Candy + 150 coins |
| 3rd | 50 coins |

**Season-end rewards (winning team):**
- 1 Master Ball per player
- 2,000 coins per player
- Highlighted title prefix in chat (exclusive to season winners)

---

## Commands

### Team Commands
| Command | Who | Description |
|---------|-----|-------------|
| `/team join <valor\|mystic\|instinct>` | Player | Join a team (first-time only) |
| `/team info` | Player | Your team, GCP rank, BP total |
| `/team leaderboard` | Player | GCP standings |
| `/team chat <message>` | Player | Team-only chat |
| `/team switch <team>` | Player | Switch team (2,000 coins, 30-day cooldown) |
| `/team attractor` | Player | View Legendary Attractor pool progress |
| `/team attractor contribute <amount>` | Player | Donate BP to team pool |

### Faction Commands
| Command | Who | Description |
|---------|-----|-------------|
| `/faction create <name>` | Player | Form a sub-faction (must be in a team) |
| `/faction invite <player>` | Leader | Invite a member |
| `/faction claim` | Leader | Claim current chunk (costs BP from pool) |
| `/faction unclaim` | Leader | Release current chunk |
| `/faction deposit <amount>` | Member | Donate BP to faction pool |
| `/faction challenge <name>` | Player | Challenge a faction's gym |
| `/faction gymset` | Leader | Register gym team (up to 6 Pokémon) |
| `/faction upgrade <type>` | Leader | Purchase an upgrade from faction pool |
| `/faction warp <name>` | Team member | Warp to faction (requires Faction Warp upgrade) |
| `/faction info <name>` | Anyone | View faction stats, upgrades, gym tier |
| `/faction raid <name>` | Leader | Declare a raid (deferred) |

### Admin Commands
| Command | Description |
|---------|-------------|
| `/team admin reset` | Trigger bi-weekly reset manually |
| `/team admin set <player> <team>` | Force-assign a player to a team |
| `/team admin attractor spawn <team>` | Manually trigger a legendary spawn for a team |

---

## Launch Scope

**v1 (launch):**
- Team join, chat, leaderboard
- Faction create, land claiming, BP pool
- Gym challenge flow (arena teleport)
- Gym tier upgrades
- BP from battles
- Legendary Attractor pool
- 3 upgrades: Rare Spawn Beacon, Training Ground, Shiny Magnet

**v2 (after feedback):**
- Remaining upgrades
- Season rewards
- Events (Raid Weekend, Team Showdown, Gym Blitz)

**v3 (admin-enabled when ready):**
- Raid system with 24-hour declaration window

---

## Acceptance Criteria

- [ ] Players can join a team on first login
- [ ] Sub-factions can be created, joined, and disbanded
- [ ] Land claiming protects chunks from rival players
- [ ] Gym challenges correctly route both parties to a Pixeltown arena
- [ ] BP awards correctly from ranked battles, friendly battles, and gym challenges
- [ ] Upgrades purchase from faction BP pool and take effect in claimed chunks
- [ ] Legendary Attractor pool accumulates across team members and triggers spawn at threshold
- [ ] GCP resets bi-weekly with coin rewards distributed via Impactor
- [ ] All values configurable (BP costs, thresholds, reward amounts)

---

## Open Questions

- Exclusive Move Tutor move list — what moves should be available only through this upgrade?
- Exclusive Move Tutor move list — what moves should be available only through this upgrade?
