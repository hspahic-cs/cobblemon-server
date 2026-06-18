# server-monument-frequency

Makes the **rare Legendary Monuments ~2× more common** in worldgen.

The Arc Phone structure locator is banned on this server, so the high-`spacing` legendary monuments
(Reshiram/Zekrom tower, Lugia temple, Spear Pillar, etc.) were very hard to stumble onto. This
datapack overrides their `worldgen/structure_set` placements, scaling `spacing`/`separation` by
**0.7** (density ≈ ×2; `spacing > separation` preserved). Already-common monuments (outskirt stand,
shrines, lakes, dyna tree) and non-legendary structures (traditional village) are left untouched.

Regenerate by copying the base `structure_set` JSONs from the LegendaryMonuments jar and re-scaling.
