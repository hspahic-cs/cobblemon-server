# Economy System

## Summary

Two separate, parallel economies govern the server. Coins drive the personal player economy — items, shops, wagers. BP drives the faction meta economy — upgrades, territory, attractor contributions. Neither currency crosses into the other's domain.

---

## Currency Overview

| Currency | Scope | Injected By | Sunk By |
|----------|-------|-------------|---------|
| Coins | Personal | Gym leader wins, selling items to the stock market | Server shop purchases, wagers, team switch |
| BP | Faction | Faction gym challenge wins, events | Faction upgrades, territory claiming, attractor contributions, raid losses |

**Key rules:**
- Raid outcomes redistribute BP between factions — they do not inject new BP
- Wager wins redistribute coins between players — they do not inject new coins
- Items sold in the server shop are NOT eligible for the stock market
- Faction cache items cost BP, not coins — reinforces that competitive activity funds faction perks

---

## Time Value Anchors

| Activity | Time | Earning |
|----------|------|---------|
| Challenge mode gym win | ~20 min | 250 coins |
| Tier 4 faction gym challenge | ~15 min | 30 BP |
| Rough coin rate | — | ~12 coins/min |
| Rough BP rate | — | ~2 BP/min |

---

## Coin Injection: Gym Leader Rewards

Gym leader battles are repeatable. Challenge mode is the primary farming loop for experienced players.

| Tier | Level Cap | Reward Per Win |
|------|-----------|---------------|
| 1 | 15 | 15 coins |
| 2 | 25 | 35 coins |
| 3 | 35 | 70 coins |
| 4 | 45 | 120 coins |
| Challenge | 100 | 250 coins |

**Ceiling:** ~3 challenge mode wins/hour = ~750 coins/hour for an experienced player.

---

## BP Injection: Faction Activity

| Action | BP Earned |
|--------|-----------|
| Faction gym challenge win (attacker) | +5–30 (scales with gym tier) |
| Faction gym defense win (NPC) | +3–18 (scales with gym tier) |
| Live defense win (player) | 1.5× gym defense reward |
| Team Showdown win | +3 |
| Future events | TBD |

Raids redistribute BP only — no net injection.

---

## Server Shop

Fixed prices. Coin sink. Items here are **not** available on the stock market.

### Medicine

| Item | Price |
|------|-------|
| Potion | 15 coins |
| Super Potion | 30 coins |
| Hyper Potion | 55 coins |
| Max Potion | 100 coins |
| Full Restore | 150 coins |
| Revive | 60 coins |
| Max Revive | 175 coins |
| Antidote | 10 coins |
| Burn Heal | 10 coins |
| Ice Heal | 10 coins |
| Paralyze Heal | 10 coins |
| Awakening | 10 coins |
| Full Heal | 35 coins |
| Ether | 30 coins |
| Max Ether | 75 coins |
| Elixir | 120 coins |
| Max Elixir | 300 coins |

### Poké Balls

| Item | Price |
|------|-------|
| Poké Ball | 8 coins |
| Great Ball | 20 coins |
| Ultra Ball | 60 coins |

### Battle Consumables (X Items)

| Item | Price |
|------|-------|
| X Attack | 30 coins |
| X Defense | 30 coins |
| X Special Attack | 30 coins |
| X Special Defense | 30 coins |
| X Speed | 30 coins |
| X Accuracy | 30 coins |
| Dire Hit | 30 coins |
| Guard Spec. | 35 coins |

### Move Shop (TM Equivalent)

Exclusive signature moves (Draco Meteor, Hydro Cannon, Blast Burn, etc.) are not sold here — faction Move Tutor only.

| Tier | Examples | Price |
|------|---------|-------|
| Utility moves | Status moves, weak coverage | 100 coins |
| Standard competitive moves | Surf, Earthquake, Ice Beam, Flamethrower | 400 coins |
| Strong competitive moves | Close Combat, Stone Edge, Nasty Plot | 800 coins |
| Elite moves | Spore, Knock Off, Stealth Rock, Scald | 1,500 coins |

*Full move list TBD — requires confirmed Cobblemon move tutor support.*

---

## Stock Market

Players buy and sell items through a server-run exchange — not player-to-player. Players can gift coins freely but cannot sell items directly to each other. Items in the server shop are excluded.

### Market Mechanics

Implemented in `mods/stock_market`. All parameters configurable in `config/cobblemon-market/config.json`.

- Each item has a `baseSellPrice` — what a player receives when selling at a healthy market (factor = 1.0, balanced activity)
- **Buy price at equilibrium** = `baseSellPrice × 3` (`spreadBase` = 3.0)
- `priceFactor` starts at 1.0 (ceiling) and only goes **down** from sell pressure — buying recovers it back toward 1.0 but cannot exceed it
- **Dynamic spread** widens when activity is one-sided: min 3× (balanced), max 7× (fully one-sided)
- **Passive recovery:** 4% per hour back toward equilibrium (`recoveryRatePerHour` = 0.04)
- **Factor floor:** 0.10 — prices never drop below 10% of base

**Example — Lucky Egg (baseSellPrice = 500):**
| Market State | Sell Price | Buy Price |
|-------------|-----------|-----------|
| Healthy (factor 1.0, balanced) | 500 coins | 1,500 coins |
| Depressed (factor 0.5, sell-heavy) | ~250 coins | ~750 coins |
| Floor (factor 0.10) | ~28 coins | 150 coins |

### Item Price Reference

All sell prices are `baseSellPrice` (received at healthy market). Buy prices = sell × 3 at equilibrium.

| Item | Sell Price | Buy Price | Notes |
|------|-----------|-----------|-------|
| Berries (common) | 8 | 24 | |
| Apricorns | 12 | 36 | |
| Mint leaves | 10 | 30 | |
| Evolution stones | 80 | 240 | |
| Common evo items (Link Cable etc.) | 60 | 180 | |
| Type gems | 50 | 150 | |
| Quick Claw, Lagging Tail | 120 | 360 | |
| Razor Claw | 200 | 600 | |
| Exp. Candy XS | 15 | 45 | 1 sculk + 1 honeycomb |
| Exp. Candy S | 50 | 150 | 6 sculk + 6 honeycomb |
| Exp. Candy M | 175 | 525 | 18 sculk + 18 honeycomb |
| Exp. Candy L | 600 | 1,800 | 54 sculk + 54 honeycomb (~60–90 min) |
| Exp. Candy XL | 1,500 | 4,500 | 162 sculk + 162 honeycomb |
| Rare Candy | 300 | 900 | Ruins exploration only |
| Leftovers | 75 | 225 | ~40 apples (10–15 min) |
| Lucky Egg | 500 | 1,500 | 10% drop from Chansey/Blissey |
| Master Ball | 2,500 | 7,500 | Wither + End City required |

---

## Faction Cache

Unlocked as a faction upgrade (350 BP). Faction members purchase items using BP. Calibrated against tier-4 faction gym challenge rate (~30 BP / 15 min).

| Item | BP Cost | Tier-4 wins | Time | Notes |
|------|---------|-------------|------|-------|
| EV Feather (each stat, 6 types) | 30 BP | 1 | ~15 min | +1 EV, unobtainable in survival |
| Terrain Seed (each type, 4 types) | 80 BP | 2.5 | ~35 min | Electric/Psychic/Grassy/Misty, unobtainable in survival |
| Rare Candy | 150 BP | 5 | ~1.5 hrs | Also on stock market |
| Tart Apple | 175 BP | 6 | ~1.5 hrs | Evolves Applin → Flapple, unobtainable in survival |
| Sweet Apple | 175 BP | 6 | ~1.5 hrs | Evolves Applin → Appletun, unobtainable in survival |
| Syrupy Apple | 175 BP | 6 | ~1.5 hrs | Evolves Applin → Dipplin, unobtainable in survival |
| Metal Alloy | 200 BP | 7 | ~1.75 hrs | Evolves Duraludon → Archaludon, unobtainable in survival |
| Cherish Ball | 400 BP | 13 | ~3.5 hrs | Cosmetic prestige — Pokémon shows as caught in Cherish Ball |
| Lucky Egg | 500 BP | 17 | ~4 hrs | Also on stock market |
| Ability Capsule | 600 BP | 20 | ~5 hrs | Unobtainable in survival |
| Ability Patch | 1,000 BP | 33 | ~8 hrs | Unobtainable in survival |

---

## Open Questions

- Full move list for the server Move Shop — requires confirming which moves Cobblemon supports via tutor NPC
- Exclusive faction Move Tutor move list (signature/ultimate moves only)
- Stock market floor price — how low does value decay before stabilizing?
