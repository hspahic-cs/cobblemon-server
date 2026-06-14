# server-monument-villager-trades

Datapack override that makes **Zygarde non-buyable** from the Legendary Monuments
"Entrepreneur" villager.

## Background

The Entrepreneur (`legendarymonuments:entrepreneur`) is a custom villager profession from the
`LegendaryMonuments` mod. Its trades are **not** registered in mod code or any config — they're
baked into the villager entity's `Offers.Recipes` inside the structure template
`data/legendarymonuments/structure/outskirt_stand.nbt`. The mod places that structure through
**vanilla worldgen** (`worldgen/structure/structure_set/template_pool` → jigsaw), so the structure
template is loaded via Minecraft's datapack-respecting `StructureTemplateManager` — meaning a
datapack override of the `.nbt` is honored for newly-generated monuments.

The Entrepreneur originally had two trades:

1. `20 emerald + 40 relic_coin → 1 mega_showdown:zygarde_cell`  ← removed here
2. `1 emerald → 2 cobblemon:relic_coin`                          ← kept (relic coins aren't currency here)

## What it does

Ships an edited `outskirt_stand.nbt` with trade #1 (the `zygarde_cell` sale) deleted, so the
Entrepreneur only offers the emerald → relic-coin conversion.

## Scope / caveat

**Future monuments only.** This swaps the structure *template*, so it applies to monuments generated
after deploy. Entrepreneur villagers in already-generated monuments keep their baked Offers (their
trades live in the saved world entity, not the template). Fixing those would need a runtime hook in
cobblemon-bridge that rewrites the offers on load.

## Regenerating

Edited with `nbtlib`: load the base `outskirt_stand.nbt` from the LegendaryMonuments jar, drop the
recipe whose `sell.id` is `mega_showdown:zygarde_cell`, save gzipped. Re-do the same single edit if
the mod updates the base structure.
