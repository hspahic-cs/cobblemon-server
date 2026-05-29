# Economy design

> **Status:** rebalance v2 — 2026-05-29. Replaces the v1 design that
> assumed a parallel BP currency and a different gym reward model.
> See "Open questions" at the bottom for the things still TBD.

## The model in one sentence

The price of every item is anchored to **how long it takes a player to earn
the coins to buy it**. One coin per ~1.2 seconds of active play.

## Time anchor

**~50 coins per minute** of active gameplay is the canonical reference.
Every earn rate, every market base price, every cooldown that costs coins
ties back to it.

| Pace | Activity | Notes |
|---|---|---|
| Fast (~80–100 coins/min) | Gym farming, especially Challenge mode | Headline progression loop |
| Median (~40–60 coins/min) | Mixed: some gyms, some trainer NPCs, occasional market sells | What an "average session" looks like |
| Slow (~10–20 coins/min) | Just trainer NPCs, no gym wins | Possible at any point but slow |
| Zero | Wild Pokémon battles, AFK | Wild Pokémon are caught/farmed for their own sake, not coin grind |

This document assumes a single currency: **coins**. The earlier draft's
**BP / faction currency** is parked. See `team-faction-system.md` for the
future plan; nothing in this doc routes value through it.

---

## Coin sources

### Gym leader wins (the headline)

Beating a gym leader awards a **gacha key** AND a coin payout. The key
is the surprise reward; coins are the steady economic fuel.

| Tier | Level cap | Coins | Notes |
|---|---|---|---|
| 1 | 15 | 50 | Roughly 1 minute of play |
| 2 | 25 | 100 | |
| 3 | 35 | 200 | |
| 4 | 45 | 350 | |
| Challenge | 100 | 500 | The 10× tier-1 reward |

Repeatable on a per-fight basis. A practiced player clears a Challenge
gym in ~5–10 min, putting Challenge farming at ~50–100 coins/min.

**Implementation note:** the coin payout is a NEW reward path. Today the
`server-quests` datapack reward functions only grant gacha keys via tags
(`cq_reward_key_<tier>_1`); they need an additional payout step. See
"Implementation work" below.

### Trainer NPC wins (the steady income)

Beating any non-gym `rctmod:trainer` NPC pays **coins scaled by the
trainer's `cobblemon_bridge.adjust_level.<N>` tag** (`N` ∈ 1–100):

```
coins = N × 2
```

So a level-15 trainer pays 30 coins, a level-50 pays 100, a level-100
pays 200. The scaling is linear so ops can hand-tune individual NPC
difficulty without re-deriving the math each time.

If a trainer has no `adjust_level` tag, the payout defaults to **20 coins**
(treats them as a level-10 grunt). Ops who want a richer payout should
tag the trainer.

### Wild Pokémon battles

**Zero coins.** Wild encounters are valued for the catch, the EXP, and
the items dropped from `cobblemon_bridge.give_party_exp` loot balls —
not as a coin source. Removing the bounty flattens the wild grind so it
isn't competing with gym/trainer fights.

### Stock market sells (player → server)

Selling items at the `/market` is a coin source for items the player
collected, crafted, or grew themselves. Sell price is dynamic (see
"Stock market mechanics" below). Items the server flags as *unsellable*
return zero coins regardless of how the player obtained them — this is
the lever that prevents auto-farming exploits (Cobbleworkers, Minecolonies).

### Quest milestones (one-time)

`reach_income_*`, `beat_wild_trainer`, `farm_carrots`, `join_colony`,
ELO milestones, etc. — these grant **items**, not coins, by design.
They're nudges to push you to the next phase of the game; the recurring
coin economy comes from the three sources above.

### Sources NOT yet implemented

- **PvP wagers** (escrow on `/rankchallenge accept`, payout on
  `BATTLE_VICTORY`) — design exists in `pvp-elo.md`, mod work is in
  `cobblemon-ranked` but the wager flow isn't wired.
- **BP / faction currency** — parked. See `team-faction-system.md`.

---

## Stock market mechanics

The market is a **server-run exchange**. Players buy and sell from the
server, never from each other. Direct player-to-player gifting is allowed
through other channels (chat trade, dropped items, etc.); the market is
just for converting items ↔ coins.

The `cobblemon-market` mod implements the dynamic-pricing engine. Each
market item has five knobs in `config/cobblemon-market/authored/items.json`:

| Knob | Purpose |
|---|---|
| `baseSellPrice` | Coins received per item at "healthy" market state. The number this whole document is anchored against. |
| `baseBuyPrice` | Coins charged when buying. Set to `baseSellPrice × 5` at equilibrium — that 5× spread is the server's tax on round-trip trading. See "Why a 5× spread" below. |
| `baseStock` | How many of this item the server holds at equilibrium. Buying/selling shifts current stock; passive recovery pulls it back to `baseStock` over time. |
| `elasticity` | How sharply price moves with stock changes. Higher = more volatile. |
| `maxStockMultiplier` | Hard cap on how high stock can climb (i.e. how many of this item the server is willing to absorb before refusing further sales). The "restock cap" we want for auto-farmable items. |

Plus two global knobs in `config.json`:

| Knob | Purpose |
|---|---|
| `restockRatePerHour` | Fraction of `baseStock` that's returned per hour. Currently 0.07 (7%/hr → ~14 hr from extreme to equilibrium). |
| `priceHistorySize` | How many price snapshots are kept for the leaderboard. |

### Pricing model: anchor on demand, derive everything else

We don't try to estimate "fair time-to-acquire" for every item from
scratch. Many items have a nominal time cost on paper but are
auto-farmable, RNG-gated, or only valuable in combination with other
items. Instead:

> **Anchor on the items players most want. Derive everything else.**

Playtesting showed two item families drive demand:

1. **Hyper Training Candies** (12 types — IV ±1)
2. **Exp. Candy family** (XS / S / M / L / XL — XP boosts)

Both chains converge on the same base material: **sculk**. Trace it back:

```
Exp. Candy XS = 1 sculk + 1 honeycomb         (the entry point)
1 S  = 6 XS  = 6 sculk + 6 honeycomb
1 M  = 3 S   = 18 sculk
1 L  = 3 M   = 54 sculk
1 XL = 3 L   = 162 sculk

Hyper Training Candy (any) = 2 L + berry + 2 honeycomb  = 108 sculk    ← cheaper path
                          OR 3 XL + berry + honeycomb  = 486 sculk     ← never used
```

Players will always pick the L-based recipe for Hyper Training, so the
real Hyper Candy cost is **~108 sculk**.

Honeycomb is trivial (vanilla bee farming), berries are trivial (farmed
from seeds). The bottleneck is **sculk acquisition**.

### Sculk as the time anchor

Sculk comes from two sources, both manual:

- **Ancient City raids.** Find one (long trip), bulk-mine. A typical
  Ancient City has ~10,000 sculk blocks scattered across its floor.
  Travel time dominates the first visit; subsequent visits (set-home)
  let players harvest much faster.
- **Sculk Catalyst farming.** Catalyst placed in a mob killbox; XP-bearing
  kills near it generate sculk. Steady but slower than raids.

Neither Cobbleworkers nor Minecolonies has a job type that produces
sculk — Cobbleworkers components are all peaceful (smelt/harvest/pickup,
no combat or sculk), Minecolonies' colonists don't kill mobs in a way
that triggers Catalysts. So sculk stays a real time investment, but
the *rate* varies wildly with player progression:

| Player tier | Sculk/min | Notes |
|---|---|---|
| New player (stone tools, no buffs) | ~5/min | Mostly Catalyst farming with weak weapons |
| Median player (iron, set-home, some XP) | ~20/min | Active Ancient City raids + Catalyst farming |
| **Established player in a known Ancient City** | **~100/min** | Bulk-mining a city they've already explored |
| Max-level (netherite, Beacon Haste, full setup) | ~200/min | Three stacks per minute |

We use the **established-player rate (~100 sculk/min)** as the design
reference for sculk extraction. This is the pace someone hits once
they've found and revisited a city — i.e. the pace that drives long-term
supply. Newer players take longer per sculk but they're not the supply
shapers.

### Pricing approach: direct, not derived

In earlier drafts we tried to derive every candy price from a "1 sculk
= X coins" anchor. That kept oscillating (2.5 vs 10 vs 0.5 coins/sculk)
because the sculk extraction rate varies wildly by player tier, so any
single multiplier prices things wrong for someone.

Instead we **set candy prices directly** based on what they're worth as
gameplay outcomes, then sanity-check against the recipe chain. If a
craft recipe ever costs more in buy-side market materials than buying
the finished item directly, that's a buy-and-disassemble exploit — we
fix the price, not the recipe.

| Item | sellPrice | buyPrice (5×) | Notes |
|---|---|---|---|
| Exp. Candy XS | 5 | 25 | Volume item, cheap |
| Exp. Candy S | 30 | 150 | 6 × XS — matches recipe ratio |
| Exp. Candy M | 90 | 450 | 3 × S |
| Exp. Candy L | 270 | 1,350 | 3 × M |
| Exp. Candy XL | 810 | 4,050 | 3 × L |
| **Hyper Training Candy** (any of 12) | **900** | **4,500** | Priced ABOVE its inputs — see below |

**Why Hyper Training is priced above its inputs (the anti-arbitrage rule):**

The cheaper Hyper Training recipe is `2 × Exp. Candy L + 1 berry + 2
honeycomb`. At our prices, the input materials cost:

- 2 × Exp. Candy L buy = 2 × 1,350 = **2,700 coins**
- Berry: free (farmable)
- Honeycomb: free (auto-farmable, not on market)

So crafting a Hyper Training costs ~2,700 in buy-side materials. We
price the finished Hyper Training at **4,500 buy** — a 40% premium
over the input cost. This makes crafting the obviously-cheaper path
and the market the convenience option.

If we priced Hyper Training at, say, 2,500 buy, players could buy
Hyper Training, dismantle it for L candies, and sell those at floor
prices for arbitrage. The 40% premium prevents that loop.

This is a general rule the doc commits to: **for any craftable endgame
item on the market, buy-direct must cost more than buy-the-inputs-to-craft**,
ideally by enough margin (~30-50%) that the crafting incentive is
strong, not marginal.

**Hyper Training Candy at 4,500 buy ≈ 1.5 hours of median play.** The
grind isn't to afford ONE Hyper Training; it's to afford the dozens
needed to max all six IVs across a competitive team. A player
IV-perfecting 6 mons (6 stats × 6 mons = 36 candies) at full buy price
needs ~162,000 coins — about 54 hours of median play. With
chronic-saturation floor prices the buy cost drops to ~33% (~54,000
coins, ~18 hours), still a real long-tail commitment. A player who
crafts their own pays input cost (~2,700/HT × 36 = 97,200 coins, but
much of that is sculk grind they enjoy) — typically faster than
buying.

### Why a 5× spread, not 3×

The default `cobblemon-market` spread is 3× (buyPrice = 3 × sellPrice),
but for this server we use **5×**. Three reasons:

1. **Round-trip arbitrage hurts more.** Buying then reselling costs
   80% of the input value, not 67%. Practically eliminates speculation.
2. **Crafting becomes much more attractive than buying.** A player who
   grinds their own sculk pays zero spread. A player who buys direct
   pays 5× the underlying time-value. The market is for emergencies
   and convenience, not a substitute for play.
3. **Floor compression is healthier.** With chronic oversupply, sell
   prices collapse to ~33% of base (see floor section). At 5× spread,
   buy prices at floor still feel "real" — a Hyper Training Candy at
   90 sell / 450 buy is sensible. At 3× spread the buy price collapses
   too far at floor and undercuts the sell price's signaling.

### Rare Candy: same outcome, same price as Exp. Candy XL

In Cobblemon, **Rare Candy and Exp. Candy XL deliver the same outcome:
one level for one item used.** Players don't choose between them on a
coin-per-XP basis — they grab whichever is in inventory and click.
Because the outcome is identical, the **price is identical**.

The two items differ only in *how* you acquire them:

- **Exp. Candy XL** comes from the sculk chain (162 sculk equivalent ≈
  8 minutes at 20 sculk/min).
- **Rare Candy** comes from Ruins exploration (loot-only, no crafting
  recipe). Roughly equivalent player-time per item.

Listed prices:

| Item | sellPrice | buyPrice (5×) | Source |
|---|---|---|---|
| Exp. Candy XL | 810 | 4,050 | Sculk chain (craftable) |
| Rare Candy | 810 | 4,050 | Ruins loot only |

Same numbers. Rare Candy is priced at 810 because it delivers the same
outcome as XL, **not** because we measured Ruins exploration time. If
both items diverge in difficulty later (e.g. Cobblemon nerfs Ruins loot
rates), we re-balance Rare Candy to match XL's price, not the other
way around.

### Pokéballs and consumables

These are the high-volume, low-friction items players need to keep their
team battle-ready. Priced against actual recipe craft time at 50 coins/min
labor anchor, with a **+50% markup** so that supplies are a real strategic
consideration during gym farming (not free).

A typical Challenge gym fight (500 coin payout) burns roughly:
- 2-3 Super Potions
- 1 Revive (if a mon faints)
- 1 Ether or status heal
≈ **~520 coins of supplies**, leaving the fight roughly break-even.
Skilled players profit; sloppy players go net-negative and need other
income paths to cover.

| Item | sellPrice | buyPrice (5×) | Recipe / source |
|---|---|---|---|
| **Pokéballs** | | | |
| Poké Ball | 8 | 40 | Apricorn + iron (output 4 per craft) |
| Great Ball | 15 | 75 | Apricorn + iron, blue accents |
| Ultra Ball | 25 | 125 | Apricorn + gold |
| **Vegetables** | | | |
| Carrot | — (no sell) | 8 | Buy-only listing for early players. Auto-farmable so sellPrice = 0 prevents the auto-farm-to-coins exploit. |
| **HP potions** | | | |
| Potion | 18 | 90 | Medicinal Brew + Oran Berry |
| Super Potion | 27 | 135 | Medicinal Brew + super-potion ingredient tag |
| Hyper Potion | 27 | 135 | Medicinal Brew + Sitrus Berry |
| Max Potion | 45 | 225 | Hyper Potion + Vivichoke (vivichoke gates this) |
| Full Restore | 60 | 300 | Max Potion + Lum Berry (top-tier) |
| **Revives** | | | |
| Revive | 18 | 90 | Heal Powder + Honey Bottle |
| Max Revive | 45 | 225 | 2 Revives + Vivichoke |
| **Single-status heals** | | | |
| Antidote | 12 | 60 | Glass Bottle + Pecha Berry |
| Burn Heal | 12 | 60 | Glass Bottle + Rawst Berry |
| Paralyze Heal | 12 | 60 | Glass Bottle + Cheri Berry |
| Ice Heal | 12 | 60 | Glass Bottle + Aspear Berry |
| Awakening | 12 | 60 | Glass Bottle + Chesto Berry |
| **Universal status** | | | |
| Full Heal | 38 | 190 | Cures all status one-shot |
| **PP restore** | | | |
| Ether | 18 | 90 | Medicinal Brew + Leppa Berry |
| Max Ether | 38 | 190 | Ether + Pep-Up Flower (flower-gated) |
| Elixir | 18 | 90 | Medicinal Brew + Hopo Berry |
| Max Elixir | 38 | 190 | Elixir + Pep-Up Flower (flower-gated) |

**Carrot pricing detail:** the `cobblemon-market` engine uses the same
scale formula for both buy and sell. Setting `baseSellPrice = 0` makes
sell pay zero at any stock level (zero × anything = zero), while
`baseBuyPrice = 8` lets the buy side function normally. Players can
buy carrots from the market but never sell them for coins — the
auto-farm exploit is closed without needing to omit the listing
entirely.

### Adding new items later

When new items come up for inclusion:

1. **Is it craftable from a base material we already price?** Don't list
   it on the market. Players craft it. (They can sell raw if they don't
   want to craft, at ~1/3 the value of the cooked item — fine.)
2. **Is it auto-farmable via Cobbleworkers / Minecolonies?** Don't list
   it. See policy below.
3. **Is it loot-only / unobtainable otherwise?** Add it as a new base
   material with `sellPrice = (estimated time to acquire) × 50`.

---

## Auto-farmable items policy

Two mods generate items without active player time:

- **Cobbleworkers** — Pokémon assigned to bee/farm/etc. infrastructure
  produce drops periodically. Job definitions live in the mod jar at
  `data/cobbleworkers/jobs/*.json`.
- **Minecolonies** — colonists run farms, mines, fisheries, beehives, etc.

If these items appeared on the stock market, players could leave their
server running overnight and wake up to a coin pile, breaking the
time-anchor.

**Policy:** if an item is auto-farmable through either mod, **don't add
it to `items.json`.** Items not listed in the market config are
implicitly unsellable. Players can still craft, use, and gift them; they
just can't be converted to coins through `/market`.

This is the simpler alternative to maintaining an explicit
`sellPrice = 0` list. The market only sells what's listed; that's the
contract.

### Confirmed auto-farmable (do NOT list on market)

Verified by inspecting the mod jars on the dev VM:

- **Carrots** (`minecraft:carrot`) — Minecolonies Farmer + Cobbleworkers harvest jobs
- **Honeycomb** (`minecraft:honeycomb`) — Cobbleworkers `honey_collection`
  job (Combee/Vespiquen) + Minecolonies Beekeeper
- **Honey Bottle** (`minecraft:honey_bottle`) — same auto-farm route via
  Cobbleworkers' baked-in `honey_bottle_from_honeycombs` recipe

The list grows as we audit more Cobbleworkers job definitions and
Minecolonies worker types. Authoritative source for Cobbleworkers is the
`data/cobbleworkers/jobs/*.json` files inside the jar; for Minecolonies,
the `JobBeekeeper` / `JobFarmer` / `JobFisher` / `JobMiner` etc. classes.

### Confirmed NOT auto-farmable

For the avoidance of doubt — checked to confirm these stay manual:

- **Sculk** — neither mod has a `KillEntity` or sculk-aware job
  component. Cobbleworkers' job system is peaceful-only; Minecolonies'
  colonists don't kill mobs in a way that triggers Sculk Catalysts.
  Sculk is the time anchor for the demand chain (see "Pricing model"
  above), so this matters.

---

## Anti-macro: server-wide daily sell cap

The dynamic-pricing model in `cobblemon-market` (factor decay + passive
recovery) is designed for *organic* trading. It doesn't defend against:

- A player using a macro to drip-sell stockpiled inventory across the
  recovery cycle.
- A first-finder of an Ancient City extracting ~3 stacks of sculk
  (~200 sculk/min for max-level), converting to ~500 candies, and
  flooding the market.

**Mitigation: server-wide daily sell cap = `baseStock / 2` per UTC day.**

For each market item, players collectively can only sell up to half of
the item's `baseStock` per UTC day. Once that pool is exhausted, all
players see zero-coin sales of that item until the next day rollover.

This is a deliberate "race to the pump" dynamic. On a small server
(~15 players) it generates social play: people show up around midnight
UTC to compete for the day's sell allocation. It also means a stockpile
sitting in chests doesn't grow more valuable — it just takes more days
to liquidate.

### Why server-wide instead of per-player

We considered per-player daily caps. For a larger server they'd be
fairer (each grinder gets equal access regardless of timezone). For
this server's scale, server-wide is better because:

- Simpler implementation: one counter per item, not one per
  `(player, item)` pair.
- Bounded windfall is fixed and predictable (`baseStock / 2 × sellPrice`),
  not "(player count) × (per-player cap)" which scales with playerbase.
- Race dynamic creates moments of social play rather than independent
  grinding.

### Reasonable `baseStock` values

The cap is derived from `baseStock`, so picking the right `baseStock`
matters. Rules of thumb:

- **Item that's expected to sell ~10/day across the server:**
  set `baseStock = 20` so the daily cap is 10. Half-saturates the
  market on the average day.
- **Item that sees windfall extraction (sculk-derived):** set
  `baseStock` low (e.g. 50–100) so the daily cap is meaningful. A
  stronghold-finder still benefits but can't dump everything at once.
- **Bulk consumables (Pokéballs):** set `baseStock` high (the current
  100 is fine) so casual selling isn't constantly hitting the cap.

Proposed `baseStock` values for the demand-anchor items:

| Item | baseStock | Daily cap (half) | Notes |
|---|---|---|---|
| Exp. Candy XS | 200 | 100 | Volume item, larger cap |
| Exp. Candy S | 100 | 50 | |
| Exp. Candy M | 50 | 25 | |
| Exp. Candy L | 30 | 15 | |
| Exp. Candy XL | 20 | 10 | |
| Hyper Training Candy (each variant) | 10 | 5 | Tightest cap; rate-limit hyper crafting deliberately |

A max-level player with a fresh Ancient City haul would see something
like: ~5 hyper candies, ~10 XL, ~15 L, ~25 M, ~50 S, ~100 XS sold on
day one (assuming they're first to the market that day) → roughly
4,000 coins. Day two they sell another batch. Stockpile depletes over
1–2 weeks of daily selling.

### Implementation: shipping in `cobblemon-market` mod work

This feature is being implemented in the `cobblemon-market` mod (work
in progress, not yet shipped). The doc above describes the *target*
state. Until the cap ships, the candy items SHOULD NOT be added to
`items.json` — listing them without the cap exposes the macro exploit
described at the top of this section.

### Price floor at saturation

The pricing engine in `cobblemon-market` uses:

```
sellPrice = baseSellPrice × ((stock + 1) / (baseStock + 1))^(-elasticity)
```

When the market is fully saturated (stock = `baseStock × maxStockMultiplier`),
the price hits its floor:

```
floorPrice ≈ baseSellPrice × maxStockMultiplier^(-elasticity)
```

### Why this matters for our economy

We expect chronic oversupply. Players will sell as much as their daily
cap allows, every day. The market will sit at or near saturation most of
the time, which means:

- **Sellers** receive the floor price, not the base price.
- **Buyers** also get a discount (the same `scale` factor reduces buy
  prices proportionally), so the 5× spread stays intact in ratio terms
  but the absolute numbers shrink.

The base price is what the market would offer if stock were exactly at
`baseStock`. In practice that almost never happens — saturation is the
default state.

### Tuning: `maxStockMultiplier = 3` (was 10)

The current config has `maxStockMultiplier = 10` for all items. With
elasticity 1.0, that gives a floor of ~10% of base — too brutal:
saturated XS Exp. Candies would sell for 0–1 coin.

Lowering `maxStockMultiplier` to **3** brings the floor up to a more
livable ~33% of base for elasticity-1.0 items:

| Item | base sellPrice | elasticity | Floor (max=3) | % of base |
|---|---|---|---|---|
| **Pokéballs (low elasticity for stable consumables)** | | | | |
| Poké Ball | 8 | 0.3 | 6 | 72% |
| Great Ball | 15 | 0.5 | 9 | 58% |
| Ultra Ball | 25 | 0.5 | 14 | 58% |
| **Potions / Revives** | | | | |
| Potion | 18 | 1.0 | 6 | 33% |
| Super Potion | 27 | 1.0 | 9 | 33% |
| Hyper Potion | 27 | 1.0 | 9 | 33% |
| Max Potion | 45 | 1.0 | 15 | 33% |
| Full Restore | 60 | 1.0 | 20 | 33% |
| Revive | 18 | 1.0 | 6 | 33% |
| Max Revive | 45 | 1.0 | 15 | 33% |
| **Status heals** | | | | |
| Antidote / Burn Heal / Paralyze Heal / Ice Heal / Awakening | 12 | 1.0 | 4 | 33% |
| Full Heal | 38 | 1.0 | 13 | 33% |
| **PP restore** | | | | |
| Ether | 18 | 1.0 | 6 | 33% |
| Max Ether | 38 | 1.0 | 13 | 33% |
| Elixir | 18 | 1.0 | 6 | 33% |
| Max Elixir | 38 | 1.0 | 13 | 33% |
| **Candies (the demand-anchor chain)** | | | | |
| Exp. Candy XS | 5 | 1.0 | 2 | 33% |
| Exp. Candy S | 30 | 1.0 | 10 | 33% |
| Exp. Candy M | 90 | 1.0 | 30 | 33% |
| Exp. Candy L | 270 | 1.0 | 90 | 33% |
| Exp. Candy XL | 810 | 1.0 | 270 | 33% |
| Hyper Training Candy | 900 | 1.0 | 300 | 33% |
| Rare Candy | 810 | 1.0 (lowered from 2.0) | 270 | 33% |

The "% of base" column is the answer to the lower-bound question:
**at chronic saturation, sellers get roughly 33% of base for ordinary
items, more for stable consumables (Pokéballs hold 60-70% because of
low elasticity).** Items at floor are still worth selling — a saturated
Hyper Training Candy at 90 coins is still ~2 minutes of median play
returned per candy — they're just not the gold mine they would be in
an undersupplied market.

### Why lower Rare Candy elasticity from 2.0 to 1.0

The current 2.0 elasticity makes Rare Candy floor at 1% of base — a
99% collapse. That's appropriate for an item players might flood the
market with, but Rare Candy is loot-only (Gilded Chests in Ruins) and
not stockpilable at scale. Players don't have farms producing 1000
Rare Candies. Lowering to 1.0 keeps the floor at a sensible 33%.

### Other anti-exploit knobs

- `maxStockMultiplier` (per-item) — described above. Lower = higher
  floor; less stockpile flooding capacity.
- `elasticity` (per-item) — how fast price moves with stock. <1 for
  stable consumables (predictable pricing); 1.0 for ordinary items
  (linear-ish); >1 for items where we want sharp diminishing returns.

These two work in concert with the daily cap, not as substitutes:

- **Daily cap** bounds the *flow* of sales per day.
- **maxStockMultiplier** bounds the absolute *stock* held in the market.
- **Elasticity** shapes the price *curve* between empty and full.

---

## Coin sinks

What players actually buy with coins. (No fixed-price "server shop"
currently exists — everything goes through `/market`.)

| Sink | Cost | Notes |
|---|---|---|
| `/market` buy | 5× sell price | Default sink. Most players spend most coins here. |
| `/rankchallenge` wager | Player-set, capped at 50% balance | Redistributes to winner; doesn't sink coins from the economy. Kept here for completeness. |
| Future: gacha key purchase | TBD | If/when keys become buyable instead of strictly gym-earned. |
| Future: cosmetics, nameplates, custom Pokémon nicknames | TBD | Pure cosmetic sinks have no effect on gameplay balance. |

---

## What changed from v1 of this doc

- **Single currency, no BP.** Faction system parked.
- **Gym tiers retuned.** Was 15/35/70/120/250; now 50/100/200/350/500. The
  v1 numbers anchored to a 12 coins/min rate; v2 anchors to 50 coins/min
  to match the actual gym pace and account for trainer NPCs as a second
  income stream.
- **Wild Pokémon pay nothing.** Was implicit in v1 (no entry); v2 makes
  the design explicit. The currently-shipped +2 bounty in
  `WildBattleRewardHook` will be removed.
- **Wild trainer NPCs pay coins.** v1 didn't address them at all. They're
  now the steady income lane between gym fights.
- **Stock market prices reset.** v1 set Lucky Egg @ 500, Rare Candy @ 300,
  etc. v2 prices the 6 actually-configured market items against the
  50 coins/min anchor. Future items added with the same method.
- **Server shop removed.** v1 described a fixed-price shop for medicine /
  Pokéballs / X-items. None of that is implemented. The market handles
  all buying and selling today.
- **Auto-farmable policy.** Items auto-farmable through Cobbleworkers
  or Minecolonies are NOT added to `items.json` for sell. They MAY be
  listed buy-only (sellPrice = 0, buyPrice > 0) so early players can
  acquire starters. Currently confirmed auto-farmable: carrots,
  honeycomb, honey bottle. Carrots ship as buy-only; honeycomb and
  honey bottle don't appear at all.
- **Medical items + Pokéball ladder added.** `items.json` expands from
  the original 6 items to ~20+ covering Pokéballs (3 tiers), HP
  potions (5 tiers), revives (2), single-status heals (5 + Full
  Heal), and PP restore (4). Priced against recipe craft time + 50%
  markup so supplies are a real strategic consideration during gym
  farming.
- **Spread bumped from 3× to 5×.** Round-trip arbitrage now costs 80%
  of input value instead of 67%. Crafting your own materials is the
  default path; buying is a real penalty.
- **Rare Candy retuned to match Exp. Candy XL.** Both deliver +1 level
  for one item used; same outcome → same price. Rare Candy at 810 sell
  / 4,050 buy, identical to XL.
- **Direct gameplay-value pricing for candies.** Earlier drafts oscillated
  trying to derive prices from a sculk-rate anchor (2.5 / 10 / 0.5
  coins/sculk depending on which player tier you measure). Switched to
  setting candy prices directly based on gameplay value, with the
  recipe chain as a sanity check.
- **Hyper Training priced ABOVE its inputs.** 4,500 buy vs. 2,700 in
  input-buy materials → 40% premium for buying direct, ~zero premium
  for crafting. Prevents buy-and-disassemble arbitrage and rewards
  players who do their own grind.
- **Daily sell cap = `baseStock / 2`.** Server-wide cap, per UTC day.
  Prevents drip-sell macros + flooding.
- **`maxStockMultiplier` lowered from 10 → 3.** Floor prices now ~33%
  of base for ordinary items, not 10%. More livable for sellers at
  saturation.

---

## Implementation work

The doc above describes the target state. Pieces that need code/config changes:

### Code changes

1. **Remove `WildBattleRewardHook` bounty** in `cobblemon-bridge`. Either delete
   the hook entirely or zero `BOUNTY` to 0.
2. **Add a `WildTrainerRewardHook`** that pays coins on
   `BATTLE_VICTORY` when the loser is a `TrainerBattleActor` AND the loser
   is NOT a gym (no `cobblemon_bridge.gym_id.*` tag). Read `adjust_level`
   tag from the trainer entity (which `GymDefeatHook` already stashes on
   `EntityInteract` — same pattern), pay `level × 2` coins or 20 default.
3. **Extend gym reward `.mcfunction` files** to also grant coins. Two
   options:
   a. Have the function call a new `cobblemon-bridge` command like
      `/economy admin grant @s <amount>`. Cleaner.
   b. Use existing `neoeconomy` `/economy give` command directly. Faster
      to ship but couples the datapack to the upstream economy mod's
      command schema.

### Config changes

4. **Rewrite `modpack/server-overrides/config/cobblemon-market/authored/items.json`** to match the
   pricing tables in this doc. The current file has 6 items at uncalibrated
   prices; the rewrite expands to ~25+ items across Pokéballs, medical, PP
   restore, candies, and Rare Candy. Specific changes:
   - **Pokéballs**: Poké 25→8, Great 50→15, Ultra 150→25 (sells). All elasticity to 0.3-0.5 (stable).
   - **Revive**: 250→18 sell.
   - **Carrot**: keep, but `sellPrice = 0`, `buyPrice = 8` (buy-only listing).
   - **Rare Candy**: 2000→810 sell, 6000→4,050 buy, elasticity 2.0→1.0.
   - **All retained items**: set `buyPrice = 5 × sellPrice` (was 3×).
   - **All items**: set `maxStockMultiplier = 3` (was 10).
   - **Add medical lineup**: Potion (18), Super Potion (27), Hyper Potion (27), Max Potion (45), Full Restore (60), Max Revive (45), Antidote/Burn Heal/Paralyze Heal/Ice Heal/Awakening (12 each), Full Heal (38), Ether (18), Max Ether (38), Elixir (18), Max Elixir (38). All elasticity 1.0.
   - **Add Exp. Candy chain** (when daily-cap mod work ships): XS=5, S=30, M=90, L=270, XL=810 sells.
   - **Add Hyper Training Candy** (when daily-cap mod work ships): all 12 variants at 900 sell / 4,500 buy each.

### Future / TBD

- Continue auditing Cobbleworkers job JSONs + Minecolonies worker
  classes to confirm what's auto-farmable before adding new items to
  the market.
- Decide if/when to ship BP / faction currency.

---

## Open questions

- **Should the gym coin payout differ between first-clear and repeat?**
  Current proposal pays the same on every win. An alternative: first
  clear pays 2× (still gives the gacha key), repeats pay base. Adds
  state to track but rewards exploration.
- **Trainer NPC coin formula.** `level × 2` is a reasonable starting point;
  may need tuning once we see actual trainer fight pace in dev.
- **Restock cap mechanic.** `maxStockMultiplier` already exists in the
  config. We haven't picked a value or tested it; needs a real audit
  once auto-farmable items are listed.
- **Item value reference doc.** `cobblemon-item-value-guide.md` is 707
  lines based on April 2026 wiki data. Once we re-price the market, we
  should also update that doc and pin it to Cobblemon 1.7.3.
