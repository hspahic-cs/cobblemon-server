# PokéRogue's economy, extracted

Read out of PokéRogue's source on 2026-07-31 so nobody has to go and find it again. This is a
**reference for balancing ours against theirs**, not a shipping plan — see §2.7 on where their numbers
are allowed to live.

Sources, all `pagefaultgames/pokerogue`, `main`:

- `src/battle-scene.ts` — `getWaveMoneyAmount`
- `src/modifier/modifier-type.ts` — `getPlayerShopModifierTypeOptionsForWave`
- `src/phases/select-modifier-phase.ts` — the call site and the reroll cost

## The one number everything hangs off

```ts
getWaveMoneyAmount(moneyMultiplier: number): number {
  const waveIndex = this.currentBattle.waveIndex;
  const waveSetIndex = Math.ceil(waveIndex / 10) - 1;
  const moneyValue =
    Math.pow((waveSetIndex + 1 + (0.75 + (((waveIndex - 1) % 10) + 1) / 10)) * 100,
             1 + 0.005 * waveSetIndex)
    * moneyMultiplier;
  return Math.floor(moneyValue / 10) * 10;
}
```

Two things worth noticing before copying it:

- **It is per-wave, not per-tier.** The `(((wave-1) % 10) + 1) / 10` term ramps *within* each block of
  ten, so wave 19 is worth meaningfully more than wave 11. Prices ramp with it, since the shop's base
  cost is this same function at multiplier 1.
- **The exponent grows with depth** (`1 + 0.005 * waveSetIndex`), so it is superlinear — by wave 100 it
  is not "ten times wave 10".

Rounded down to the nearest 10, always.

## Shop prices

The shop's base cost is `getWaveMoneyAmount(1)` for the current wave, and every item is a multiple of
it:

| Item | × base | | Item | × base |
|---|---|---|---|---|
| Potion | 0.2 | | Max Revive | 2.75 |
| Ether | 0.4 | | Max Potion | 1.5 |
| Super Potion | 0.45 | | Max Elixir | 2.5 |
| Hyper Potion | 0.8 | | Full Restore | 2.25 |
| Full Heal | 1 | | Memory Mushroom | 4 |
| Elixir | 1 | | Sacred Ash | 10 |
| Max Ether | 1 | | Revive | 2 |

## What is stocked, and when

```ts
if (!(waveIndex % 10)) return [];   // no shop on every tenth wave

const options = [
  [potion, ether, revive],
  [super_potion, full_heal],
  [elixir, max_ether],
  [hyper_potion, max_revive, memory_mushroom],
  [max_potion, max_elixir],
  [full_restore],
  [sacred_ash],
];

options.slice(0, Math.ceil(Math.max(waveIndex + 10, 0) / 30))
```

So rows unlock on a 30-wave cadence, and the stock is *cumulative* rather than a rotating selection:

| Waves | Rows | Items stocked |
|---|---|---|
| 1–20 | 1 | 3 |
| 21–50 | 2 | 5 |
| 51–80 | 3 | 7 |
| 81–110 | 4 | 10 |
| 111–140 | 5 | 12 |
| 141–170 | 6 | 13 |
| 171+ | 7 | 14 |

**No shop at all on every tenth wave** — which is their boss cadence. Ours puts a boss on every tenth
too (§2.19), so that lines up for free.

## Reroll

```ts
Math.ceil(waveIndex / 10) * 250 * 2 ** rerollCount
```

Doubling per reroll within a wave, and the `ceil(wave/10)` factor means a reroll costs the same across
a block of ten and steps up between them. The rarity-lock variant sums tier values `[50, 125, 300,
750, 2000]` instead of the flat 250.

## The free reward roll — tiers and weights

This is the *other* half, and it is a different system to the shop: the three options offered after a
wave are rolled from a weighted pool, not bought. Source is `src/modifier/init-modifier-pools.ts` and
`src/modifier/modifier-type.ts`.

### Which tier an option comes from

```ts
const tierValue = randSeedInt(1024);
if (tierValue > 255)      tier = COMMON;
else if (tierValue > 60)  tier = GREAT;
else if (tierValue > 12)  tier = ULTRA;
else if (tierValue)       tier = ROGUE;
else                      tier = MASTER;
```

| Tier | Window | Chance |
|---|---|---|
| Common | 256–1023 | **75%** |
| Great | 61–255 | **19.0%** |
| Ultra | 13–60 | **4.7%** |
| Rogue | 1–12 | **1.2%** |
| Master | 0 | **0.1%** |

Then **luck upgrades**, which is the part worth stealing:

```ts
const upgradeOdds = Math.floor(128 / ((partyLuckValue + 4) / 4));
do { upgraded = randSeedInt(upgradeOdds) < 4; if (upgraded) upgradeCount++; } while (upgraded);
tier += upgradeCount;
```

It loops, so a lucky roll can climb more than one tier. At luck 0 that is `4/128` = 3.1% per step. If
the resulting tier's pool is empty the tier walks back *down* until it finds one, which is what keeps
a narrow pool from producing nothing.

### What is in each tier

Entry counts: **Common 10, Great 24, Ultra 26, Rogue 19, Master 7.**

Static weights, the ones that are plain numbers:

| Tier | Entries |
|---|---|
| Common | Poké Ball 6, Temp Stat Booster 4, Rare Candy 2, Berry 2, TM 2 |
| Great | Great Ball 6, Dire Hit 4, TM 3, Base Stat Booster 3, PP Up 2, Species Stat Booster 2, Soothe Bell 2 |
| Ultra | Ultra Ball 15, Rare Species Stat Booster 12, TM 11, Attack Type Booster 9, Wide Lens 7, Mint 4, Reviver Seed 4, Rarer Candy 4, PP Max 3, Quick Claw 3 |
| Rogue | Rogue Ball 16, Soul Dew 7, Grip Claw 5, Focus Band 5, Berry Pouch 4, Scope Lens 4, Leftovers 3, Shell Bell 3, King's Rock 3, Baton 2 |
| Master | Master Ball 24, Healing Charm 18, Multi Lens 18, Shiny Charm 14 |

### The thing to actually copy

**Most healing items have weight *functions*, not numbers.** Potion, Super Potion, Hyper Potion, Max
Potion, Full Restore, Revive, Max Revive, Sacred Ash, Ether, Elixir and Full Heal all take
`(party: Pokemon[]) => …` and weight themselves by how hurt, fainted or PP-drained the party actually
is. A full-health party is not offered potions; a party with two faints sees revives.

That is the single best idea in their reward design and it costs us nothing structurally — our
`RewardEntry` already resolves per wave, and the weight would just need the party in scope. Without
it, a flat table hands out revives to a full party and reads as the mode wasting your pick.

Others are conditional in ways worth knowing: evolution items and form-change items scale their weight
with `waveIndex` (deeper runs offer them more), several are `skipInLastClassicWave`, and ball entries
drop to weight 0 once you are at the cap — so "you already have plenty" removes an option rather than
letting it clog the roll.

## What this means for ours

Ours already has the shape — `ShopSettings.shopSlotsAt`, `rerollPrice`, a per-entry `price` — so this
is a re-numbering rather than a rebuild. The pieces that do not exist yet:

1. **A wave-money curve.** We have no equivalent of `getWaveMoneyAmount`; the smoke table has flat
   prices. This is the one new mechanism.
2. **Prices as multipliers of it**, rather than absolute numbers per entry. A shop table entry would
   carry `× base` instead of a flat `price`, or carry both and prefer the multiplier.
3. **Money only on trainer waves**, which is a deliberate divergence from PokéRogue — they pay out on
   every wave. Decided for ours because it makes trainer waves matter beyond difficulty.
4. **No shop on boss waves**, which we would get by matching their `wave % 10` rule.
5. **Tier odds on the free roll.** Ours has tiers with per-wave weight curves; theirs is a flat
   75/19/4.7/1.2/0.1 with a *looping* luck upgrade on top. The loop is what makes a good-luck run feel
   different rather than slightly better, and we have no luck stat to hang it on yet.
6. **Party-aware reward weights**, which is the one to take first — see above. It is the difference
   between a reward screen that reads the run and one that offers revives to a full party.

**§2.7 applies to all of it.** PokéRogue's numbers are their data: the *mechanism* (a configurable
curve, price multipliers) belongs in the mod, and these constants belong in a server-side datapack
that is never shipped — the same split the starter costs already use.
