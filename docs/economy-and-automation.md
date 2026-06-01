# Economy & Automation

How money, items, and labor flow on this server.

## Currency

Money is `¢` (NeoEssentials economy). Check your balance with
`/balance`. Send to another player with `/pay <name> <amount>`. Toggle
incoming payments with `/paytoggle`.

Income sources:

- **Trainer defeats** pay bounties scaled to trainer tier
- **Gym wins** pay `150 × gym_id` (so Gym 1 = ¢150, Gym 10 = ¢1500)
- **Elite Four** = ¢5,000
- **Quest rewards** for milestones (¢250, ¢1000, …)
- **Selling to the market** (limited subset of items — see below)

## Market

`/market` opens the dynamic-pricing shopkeeper. Pricing follows a
classic supply/demand curve, per item:

- Every item has a `baseBuyPrice` and a `baseStock`
- Buying drains stock → price climbs
- Selling adds stock → price drops (only for items flagged `sellable`)
- Stock restocks at **~7%/hour** toward the base level
- An item's **elasticity** (0.0 – 1.0+) controls how sharply the price
  reacts to stock swings. Held items + TMs are pegged at elasticity 0
  (flat price, unlimited supply); consumables sit at 1.0 (real markets)

**Vendors** (categories you'll see in `/market`): `held_items`,
`tm_<type>` (per-type TMs — 18 types, 759 total items), plus an
"everything else" pool for potions, balls, vitamins, evo stones.

Only 27 of the 759 items are `sellable`. The rest are buy-only — you
can't farm gacha rewards into a money tap.

`/market trade` opens player-to-player trading (item-for-item).

## Gacha crates

`/gacha` issues three crate tiers and four egg pools, redeemed at
physical crate blocks (placed by ops at spawn). Keys come from quest
rewards, gym wins, and milestone advancements.

| Tier | Floor odds | Notable rewards |
|------|-----------|-----------------|
| **Common** | 13% Poké Balls × 20 | Mostly resources + low-tier vitamins |
| **Rare** | — | Evolution stones, mid-tier TMs, items |
| **Ultra** | — | Master Balls, rare candies, ability shields |

Preview any crate's full loot table with `/gacha odds <tier>`. The
distribution rolls per pull — no pity, no streak protection.

### Egg pools

Egg crate pulls produce a **Cobreeding egg of a random species from a
rarity-tiered pool**: `common`, `uncommon`, `rare`, `ultra_rare`. Eggs
hatch on a **non-AFK playtime timer**:

| Pool | Hatch time |
|------|-----------|
| common | 1 hour |
| uncommon | 2 hours |
| rare | 4 hours |
| ultra | 8 hours |
| beginner (Exeggcute quest reward) | 10 minutes |

AFK time doesn't count — sit in spawn for an hour and your egg won't
budge. The timer pauses cleanly; no progress is lost.

## Cobbleworkers

Place a **Pasture Block**, send a Pokémon to it, and they'll do work on
nearby blocks while you're away. The Pasture Block range is **5×5
horizontal** from the block.

You get your first Pasture Block from the **Evolve Exeggutor** quest.

Active job classes on this server:

| Job | Compatible species (examples) | What they do |
|-----|-------------------------------|--------------|
| **crops_harvester** | Exeggutor, Leafeon, Bellossom, Sunflora, Lilligant, Vileplume, Victreebel, Roserade, Simisage, Whimsicott | Break + auto-replant ripe vanilla crops |
| **apricorn_harvester** | (grass/bug species) | Harvest mature apricorns |
| **berry_harvester** | (grass species) | Harvest Cobblemon berries |
| **mint_harvester** | (grass species) | Harvest nature mints |
| **netherwart_harvester** | (fire/dark species) | Auto-farm nether wart |
| **amethyst_harvester** | (rock species) | Mine ripe amethyst clusters |
| **black_tumblestone_harvester** | (rock species) | Mine tumblestone |
| **irrigator** | (water species) | Bonemeal-water adjacent farmland |
| **fuel_generator** | (fire species) | Generate fuel ticks for nearby blocks |
| **brewing_stand_fuel_generator** | (fire species) | Fuel adjacent brewing stands |
| **lava_generator** | (fire species) | Fill lava cauldrons |
| **powder_snow_generator** | (ice species) | Fill powder-snow cauldrons |
| **extinguisher** | (water species) | Put out fires in range |
| **fletcher** | (bug species) | Produce arrows from sticks + feathers |
| **mint_harvester** | (grass species) | Harvest mints |
| **item_gatherer** | (any) | Sweep dropped items into a hopper |

Disabled here (too easy to AFK-farm): `archaeologist`, `dive_looter`,
`fishing_looter`, `pickup_looter`.

Each job has its own cooldown (most: 30–210 seconds per entity) and
species allowlist. Full per-job definitions live in
`datapacks/server-cobbleworkers-allowlists/data/cobbleworkers/jobs/`.

### Carrot farming

The **Evolve Exeggutor** + **Ranch Carrot Farm** quest chain hands
you everything to set up an automated carrot patch:

1. Pasture Block (from `evolve_exeggutor`)
2. 16 Bone Meal (from `ranch_carrot_farm`)
3. An Exeggutor on the pasture

Carrots aren't just food here — see [Carrots & healing](#carrots--healing).

## Carrots & healing

`cobblemon-carrots` replaces the vanilla healer-block flow:

- **Right-click a Pokémon with a carrot** → heal 30 HP, consumes 1 carrot
- **Use a Poké Healer block** → confirmation prompt, then full-party
  heal-and-revive. Charges at least 4 carrots; more if your party is
  hurt. Revives also cost 3 carrots each. **Cost is paid in carrots +
  market-priced ¢ per carrot.**

The healer pulls carrots from the player's inventory; price per carrot
is the live `/market` price (default ¢5 if market is unavailable).

This is why the **ranch_carrot_farm** quest is mandatory before
serious battling — you'll burn through carrots fast.

## Minecolonies farming

Minecolonies citizens (Farmer, Forester, Fisher, Miner, etc.) handle
the high-throughput resource pipeline that Cobbleworkers can't. The two
systems complement each other: Cobbleworkers automate **Pokémon-themed
edge cases**; Minecolonies handles **bulk vanilla resources**.

See [Minecolonies](minecolonies.md) for setting up a colony.

## Carrot/economy summary

The dependency graph isn't obvious until you've played a while:

```
crops_harvester (Exeggutor + Pasture)  →  carrots  →  Poké Healer
                                                   →  /market sales
                                                   →  ¢ for crates/TMs/breeding
```

Setting up a single self-sustaining carrot farm is the unlock for
basically everything downstream. Do it early.
