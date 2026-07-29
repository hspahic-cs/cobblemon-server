# Loot tiers

Canonical rarity ladder (T0–T5) for every item the server hands out. Exists so
that a new game, quest, crate, or reward can be priced against what already
exists instead of by feel.

```sh
python3 ops/loot-tiers/build_tiers.py            # regenerate
python3 ops/loot-tiers/build_tiers.py --check    # fail if stale (CI)
```

| File | Role |
|---|---|
| `build_tiers.py` | Generator. Holds the category rules. |
| `overrides.json` | **Edit this.** Explicit per-item tier pins. |
| `item-registry.json` | Harvested. Every real item id + display name + tooltip. |
| `mod-loot.json` | Harvested. Loot tables shipped by the mods. |
| `mod-recipes.json` | Harvested. Recipes shipped by the mods. |
| `refresh_registry.sh` | Re-harvests the three files above after a modpack bump. |
| `tiers.json` | Generated. Machine-readable source of truth. |
| `../../docs/loot-tiers.md` | Generated. Human-readable reference. |

## TX is two different things

`TX` means **never award**, but for two distinct reasons, and the per-item
`status` field says which:

- **`not-obtainable`** — recipe banned and/or stripped from loot. If one of these
  is still dropping, that's a bug to fix, not a tier to change.
- **`banned-to-use`** — freely obtainable (usually craftable), but the mechanic
  is disabled, so the item is inert. Tera and Dynamax items are here: players can
  make a Tera Orb or a Dynamax Band, they just can't use the mechanic, and doing
  so is a bannable offence. Worthless as a reward, but not a bug.

The split is *derived*, not declared — an item is `banned-to-use` iff something
actually grants it. That means it self-corrects: ban the last recipe and it flips
to `not-obtainable` on the next build.

## Why evidence must cover the mods, not just us

Three separate times, "this item has no source" turned out to mean "nothing **we
override** grants it":

- Items from `cobblemon/ruins/common/*`, archaeology and dive treasure looked
  sourceless — that's how 9 type gems were wrongly reported as unobtainable.
- Every craftable item looked sourceless, which made all the Dynamax and Tera
  gear look disabled when it is in fact freely craftable.

So the harvest reads the mods' own loot tables **and** recipes. Tables we
override are skipped (ours is live), and recipes we ban with a
`neoforge:false` condition are skipped (the ban is real).

`tiers.json` and `docs/loot-tiers.md` are build outputs — don't hand-edit them,
the next run overwrites your change.

## Changing a tier

- **One item** → add it to `overrides.json`. Overrides always win.
- **A whole class** (all gems, all specialty balls) → edit `CATEGORY_RULES` in
  `build_tiers.py`. First match wins, so put specific patterns above general ones.

Then re-run the generator and commit both outputs.

## Why purchasability doesn't set the tier either

An earlier version capped any purchasable item at T1 — "if you can buy it, it
isn't a chase item." That rule was removed, because it is wrong in a way that
matters: **heavy consumption creates scarcity even when an item is stocked.**

- **Exp candies** are purchasable *and* among the few sellable items *and*
  feedstock for the EV candies. Constant demand keeps them genuinely valuable,
  so T2 — not T1.
- **Arceus plates** are stocked at $5,000 and still Rare.

Shelf price is recorded as evidence and left for a human to weigh. Note also
that price barely discriminates anyway: 751 of 816 catalogue entries are a flat
$5,000. The exp candies are one of the few real price ladders in the game
($15 → $90 → $270 → $810 → $2,430).

## Why drop rates don't set the tier

Every row records its evidence — which crate, chest, or trainer table grants the
item and at what rate. That evidence is deliberately **not** used to assign the
tier.

The tier is what an item *should* be worth. The drop rate is what we *currently*
pay out. Deriving one from the other would make the list unable to tell us
anything: a mispriced item would justify its own mispricing, and the whole point
is to catch exactly that. Keeping them separate is what let this list surface
Mew at 12.3%/chest sitting at T4.

## Item universe

Ids are pulled only from keys that actually hold an item (`item`,
`signatureItem`, `heldItem`, loot-table `name` on `minecraft:item` entries,
gacha `items[].id`, recipe `result.id`, market keys), and `worldgen/`,
`structure/`, `tags/` paths are skipped entirely.

This matters: a blanket text regex also scoops up structure ids, biome tags and
sound events — the first draft confidently tiered `legendarymonuments:spear_pillar`
as an Epic item. It's a structure.

SimpleTMs (631 near-identical ids) are collapsed to two rules rather than
enumerated. The premium split is **curated by competitive relevance** — TM item
ids carry no move-power data to derive it from, so that list is a judgment call
and is expected to drift as the metagame moves.

## Coverage

The list covers what the server references today. An item no mod loot table,
crate, or market entry mentions won't appear — add it to `overrides.json` when
you first need it.
