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
| `tiers.json` | Generated. Machine-readable source of truth. |
| `../../docs/loot-tiers.md` | Generated. Human-readable reference. |

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
