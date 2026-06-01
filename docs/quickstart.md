# Quickstart

Five minutes from "connected" to "playing."

## Your first steps

1. You spawn in a **read-only hub world** (`spawn`). You can't build here
   and you'll be in Adventure mode — that's expected.
2. Type `/wild` to teleport to a random surface point in the overworld.
   This is your base-building dimension; survival mode kicks in automatically.
3. Type `/sethome <name>` once you've picked a spot. Get back with
   `/home <name>` anytime.
4. The **starter kit** lands in your inventory on first join: stone tools,
   a Pokédex (red), and baked potatoes.

## Picking your starter

Talk to the **starter NPC** at spawn (follow the on-screen quest toast
chain). Picking your starter unlocks the mainline quest chain —
visible in the advancement menu under the "Server" tab.

## The progression loop

The mainline quest chain walks you through every system on the server.
Follow it; you don't need to memorize anything.

| Stage | Reward | Unlocks |
|-------|--------|---------|
| Pick starter | 10 Poké Balls | Catch your first wild |
| Heal at the healer | 1 Revive | Healer-block crafting |
| Beat your first trainer | **Exeggcute Egg** | Onboarding chain |
| Beat Gym 1 | Rare gacha key | [Gacha crates](economy-and-automation.md#gacha-crates) |
| Earn ¢250 | Leaf Stone | Evolve Exeggcute |
| Evolve Exeggutor | Pasture Block | [Cobbleworkers](economy-and-automation.md#cobbleworkers) |
| Place pasture + ranch carrots | 16 Bone Meal | Automated harvesting |
| First PvP win | 5 Great Balls | [Ranked PvP](#pvp) |
| Beat Gym 2 → … → 24 | Cash + keys | Elite Four → Champion |

## Core commands

```
/wild              Teleport to a random overworld point
/spawn             Return to the spawn hub
/sethome <name>    Save your current location
/home <name>       Teleport to a saved home
/market            Open the dynamic-pricing shop
/gacha odds <tier> Preview a crate's loot table
/ranked help       PvP rating + matchmaking
/feedback bug <…>  File a GitHub issue from in-game

Gym warps aren't a chat command — talk to the **gym-warp villager**
in spawn. Each gym unlocks after you've earned the prior gym's
advancement.
```

NeoEssentials provides `/tpa`, `/tpahere`, `/back`, `/balance`, `/pay`,
etc. — type any command's name with `?` for help.

## What's unique here

- **Dynamic-priced market** with per-item elasticity and slow restock —
  flipping is real. [More →](economy-and-automation.md#market)
- **Gacha crates** in three tiers + four egg pools, keys earned from
  quests and gym wins. [More →](economy-and-automation.md#gacha-crates)
- **Carrots heal Pokémon.** A baked potato won't. A regular potato won't.
  Only carrots. [More →](economy-and-automation.md#carrots--healing)
- **Cobbleworkers** turn idle Pokémon into farm hands — Exeggutor on a
  pasture block harvests every ripe crop in a 5×5 area.
  [More →](economy-and-automation.md#cobbleworkers)
- **Minecolonies citizens battle you** with profession-themed Pokémon
  teams that level up every time they lose to you.
  [More →](minecolonies.md)
- **No hostile vanilla mob spawns** outside the deep dark. Pokémon are
  the only night-time threat. [More →](pokemon-spawns.md)

## PvP

`/ranked queue` puts you in the matchmaking pool. Wins move your ELO up;
losses move it down. ELO milestones reward gear via the quest chain.

## When something breaks

`/feedback bug <description>` — opens a GitHub issue with your
coordinates, party, recent server log, and TPS automatically attached.
You don't need to know what's broken; just describe what you saw.
