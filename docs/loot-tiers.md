# Loot tiers

Canonical rarity ladder for every item the server hands out. Consult this
when designing a new game, quest, crate, or reward so payouts stay consistent
with what already exists.

!!! warning "Generated file"

    Built by `ops/loot-tiers/build_tiers.py`. Don't hand-edit — change
    `ops/loot-tiers/overrides.json` (for a specific item) or the category
    rules in the script (for a whole class), then re-run it.

## The ladder

| Tier | Name | Use it for |
|---|---|---|
| **T5** | Mythic | Gates a box legendary or mythical, or guarantees a catch. Never a routine reward. |
| **T4** | Legendary | Summons or permanently unlocks a legendary/forme. One-per-player scale. |
| **T3** | Epic | Permanent competitive power or a hard-gated component. A real chase reward. |
| **T2** | Rare | Strong but repeatable. Fine as the headline reward for a genuine challenge. |
| **T1** | Uncommon | Routine reward scale. Safe for regular play loops. |
| **T0** | Common | Filler. Safe to hand out in bulk. |
| **TX** | Disabled | Intentionally not obtainable — recipe banned and/or stripped from loot. Never use as a reward. If one of these is still dropping, that's a bug to fix, not a tier to change. |

940 items tiered — T5: 4, T4: 39, T3: 76, T2: 197, T1: 381, T0: 149, TX: 94.

## Picking a reward

Rough guidance, not a rule:

- **Daily / repeatable loop** → T0–T1. Bulk is fine.
- **Weekly objective, gym rematch, mid-tier quest** → T1–T2, occasionally T2 as the headline.
- **Genuine one-off challenge (tournament placing, hard boss, long questline)** → T2–T3.
- **T4 and T5 gate legendaries.** Handing these out casually devalues the crate
  economy and the monument hunt at the same time. Prefer a crate key instead —
  it preserves the roll.

The Ultra crate is the rarity benchmark the tiers are calibrated against:
jackpot band 0.8–1.6%, high band 4.9%.

## T5 — Mythic

*Gates a box legendary or mythical, or guarantees a catch. Never a routine reward.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:master_ball` | Guaranteed catch. Ultra crate only, 2x at 6.5%. | `crate:rare` 0.7%; `crate:ultra` 6.5% |
| `legendarymonuments:azure_flute` | Opens the Hall of Origin — ARCEUS. Ultra crate jackpot at 0.8%. | `crate:ultra` 0.8% |
| `legendarymonuments:celestica_flute` | Crafts into the Azure Flute. Same gate, one step removed. | `loot:chests/turnback_cave_vault` 1.11% |
| `minecraft:totem_of_undying` | Zacian summon gate. Ultra crate 1.6% is the only intended source. | `crate:ultra` 1.6% |

## T4 — Legendary

*Summons or permanently unlocks a legendary/forme. One-per-player scale.*

| Item | Why | Where it comes from |
|---|---|---|
| `legendarymonuments:antimatter_globe` | Azure Flute component — the Arceus path | *not currently granted anywhere* |
| `legendarymonuments:arctic_stone` | Summons a legendary (bird / Heatran) | *not currently granted anywhere* |
| `legendarymonuments:curry_of_justice` | Summons Keldeo | `crate:ultra` 6.5% |
| `legendarymonuments:darkstone` | Summons Zekrom | `crate:ultra` 4.9% |
| `legendarymonuments:entei_treat` | Summons a legendary at its shrine | *not currently granted anywhere* |
| `legendarymonuments:fullmoon_whistle` | Summons Cresselia | `crate:ultra` 6.5% |
| `legendarymonuments:griseous_key` | Summons Giratina | `loot:chests/turnback_cave_chest` 11.36% |
| `legendarymonuments:gs_ball` | Summons a mythical | *not currently granted anywhere* |
| `legendarymonuments:latias_treat` | Summons a legendary at its shrine | *not currently granted anywhere* |
| `legendarymonuments:latios_treat` | Summons a legendary at its shrine | *not currently granted anywhere* |
| `legendarymonuments:liberty_pass` | Summons Victini | `crate:ultra` 6.5% |
| `legendarymonuments:lightstone` | Summons Reshiram | `crate:ultra` 4.9% |
| `legendarymonuments:magma_stone` | Summons a legendary (bird / Heatran) | *not currently granted anywhere* |
| `legendarymonuments:molten_stone` | Summons a legendary (bird / Heatran) | *not currently granted anywhere* |
| `legendarymonuments:newmoon_whistle` | Summons Darkrai | `crate:ultra` 6.5% |
| `legendarymonuments:old_sea_map` | Summons Mew | `loot:chests/liberty_island_chest` 12.32% |
| `legendarymonuments:proof_of_conquest_a` | Summons Azelf | `crate:ultra` 4.9% |
| `legendarymonuments:proof_of_conquest_m` | Summons Mesprit | `crate:ultra` 4.9% |
| `legendarymonuments:proof_of_conquest_u` | Summons Uxie | `crate:ultra` 4.9% |
| `legendarymonuments:raikou_treat` | Summons a legendary at its shrine | *not currently granted anywhere* |
| `legendarymonuments:rainbow_feather` | Summons Ho-Oh | `crate:ultra` 4.9% |
| `legendarymonuments:red_chain` | Summons Dialga & Palkia | `crate:ultra` 4.9% |
| `legendarymonuments:space_globe` | Azure Flute component — the Arceus path | *not currently granted anywhere* |
| `legendarymonuments:suicune_treat` | Summons a legendary at its shrine | *not currently granted anywhere* |
| `legendarymonuments:time_globe` | Azure Flute component — the Arceus path | *not currently granted anywhere* |
| `legendarymonuments:titan_key` | Titan encounter gate | `crate:rare` 2.8% |
| `legendarymonuments:tuft_of_mew_hair` | Summons a mythical | *not currently granted anywhere* |
| `legendarymonuments:vortex_stone` | Summons Lugia | `crate:ultra` 4.9% |
| `legendarymonuments:zap_stone` | Summons a legendary (bird / Heatran) | *not currently granted anywhere* |
| `mega_showdown:adamant_crystal` | Dialga Origin forme unlock | `loot:chests/turnback_cave_vault` 0.11% |
| `mega_showdown:ash_cap` | Uncraftable — craft banned by server-craft-bans; Ultra crate only | *not currently granted anywhere* |
| `mega_showdown:blue_orb` | Primal Kyogre | `loot:chests/lugia_temple_chest` 0.57% |
| `mega_showdown:griseous_core` | Giratina Origin forme unlock | `loot:chests/turnback_cave_vault` 0.11% |
| `mega_showdown:lustrous_globe` | Palkia Origin forme unlock | `loot:chests/turnback_cave_vault` 0.11% |
| `mega_showdown:red_orb` | Primal Groudon | `loot:chests/bell_tower_chest` 0.59% |
| `mega_showdown:rusted_shield` | Zamazenta-Crowned gate. Craft banned — intended to be chest loot instead. NOT YET PLACED IN ANY CHEST. | *not currently granted anywhere* |
| `mega_showdown:rusted_sword` | Zacian-Crowned gate. Craft banned (was iron_sword + netherite_scrap + fire_charge, far too cheap) — intended to be chest loot instead. NOT YET PLACED IN ANY CHEST. | *not currently granted anywhere* |
| `mega_showdown:zygarde_core` | Zygarde assembly core | `crate:rare` 2.9%; `loot:chests/regigigas_chest` 16.94%; `loot:chests/registeel_chest` 6.87% |
| `minecraft:nether_star` | Wither drop — treat as legendary-scale | *not currently granted anywhere* |

## T3 — Epic

*Permanent competitive power or a hard-gated component. A real chase reward.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:ability_patch` | Hidden ability. Deliberately pulled from the Ultra crate. | `crate:rare` 7.2%; `loot:chests/registeel_chest` 6.87% |
| `cobblemon:beast_ball` | Best-in-slot for Ultra Beasts, not purchasable | `loot:legendary/pokeballs` |
| `cobblemon:bug_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:courage_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:dark_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/turnback_cave_chest` 33.52%; `loot:archaeology/ruins` 22.73% |
| `cobblemon:dragon_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/dragoeleki_chest` 74.23% |
| `cobblemon:electric_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/dragoeleki_chest` 74.23% |
| `cobblemon:fairy_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:fighting_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:fire_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/bell_tower_chest` 30.22%; `loot:chests/liberty_island_chest` 39.31% |
| `cobblemon:flying_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/bell_tower_chest` 30.22% |
| `cobblemon:ghost_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/turnback_cave_chest` 33.52%; `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:grass_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:ground_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:health_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:held_item_voucher` | Redeems for a premium held item | `crate:rare` 2.9% |
| `cobblemon:ice_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/regice_chest` 85.07% |
| `cobblemon:mighty_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:normal_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:poison_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:psychic_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/liberty_island_chest` 39.31% |
| `cobblemon:quick_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:rock_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/regirock_chest` 70.66% |
| `cobblemon:smart_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:steel_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:tough_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:tr_voucher` | Redeems for a TR | `crate:rare` 3.9% |
| `cobblemon:water_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `legendarymonuments:azelf_fang` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `legendarymonuments:darkstone_shard` | 9 craft into a T4 Dark Stone | `loot:chests/bell_tower_chest` 9.07%; `loot:chests/liberty_island_chest` 12.32%; `loot:chests/lugia_temple_chest` 20.5% |
| `legendarymonuments:distortion_portal` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:dragon_golem_key` | Regidrago gate | `crate:rare` 2.8% |
| `legendarymonuments:dyna_apple` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:electric_golem_key` | Chamber / monument gate | *not currently granted anywhere* |
| `legendarymonuments:firescourge_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:fragmented_red_chain` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `legendarymonuments:galarian_urn_of_embers` | Legendary-adjacent gate component | *not currently granted anywhere* |
| `legendarymonuments:galarian_urn_of_frost` | Legendary-adjacent gate component | *not currently granted anywhere* |
| `legendarymonuments:galarian_urn_of_storms` | Legendary-adjacent gate component | *not currently granted anywhere* |
| `legendarymonuments:grasswither_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:groundblight_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:heroshield` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:herosword` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:ice_golem_key` | Regice gate | `crate:rare` 2.8% |
| `legendarymonuments:icerend_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:idealsbottle` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:lightstone_shard` | 9 craft into a T4 Light Stone | `loot:chests/bell_tower_chest` 9.07%; `loot:chests/liberty_island_chest` 12.32%; `loot:chests/lugia_temple_chest` 20.5% |
| `legendarymonuments:lugia_key` | Chamber / monument gate | *not currently granted anywhere* |
| `legendarymonuments:mesprit_plume` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `legendarymonuments:nightmare_essence` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:ominous_griseous_key` | Chamber / monument gate | *not currently granted anywhere* |
| `legendarymonuments:origin_ingot` | Repairs the Red Chain — component for a T4 | `loot:chests/turnback_cave_vault` 1.11% |
| `legendarymonuments:raw_origin` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:regice_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:regidrago_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:regieleki_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:regirock_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:registeel_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:rock_golem_key` | Regirock gate | `crate:rare` 2.8% |
| `legendarymonuments:sacred_ash` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:silver_wing` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:steel_golem_key` | Registeel gate | `crate:rare` 2.8% |
| `legendarymonuments:temple_key` | Chamber / monument gate | *not currently granted anywhere* |
| `legendarymonuments:titan_core` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:titan_hammer` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:truthbottle` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:urn_of_embers` | Legendary-adjacent gate component | `crate:rare` 2.8% |
| `legendarymonuments:urn_of_frost` | Legendary-adjacent gate component | `crate:rare` 2.8% |
| `legendarymonuments:urn_of_storms` | Legendary-adjacent gate component | `crate:rare` 2.8% |
| `legendarymonuments:uxie_claw` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `mega_showdown:adamant_orb` | Origin-forme held item | `loot:chests/turnback_cave_vault` 0.56% |
| `mega_showdown:griseous_orb` | Origin-forme held item | `crate:rare` 4.0%; `loot:chests/turnback_cave_vault` 0.56% |
| `mega_showdown:keystone` | Mega evolution enabler | `crate:common` 1.9% |
| `mega_showdown:lustrous_orb` | Origin-forme held item | `loot:chests/turnback_cave_vault` 0.56% |
| `mega_showdown:mega_stone` | Mega evolution enabler | `crate:common` 1.9% |
| `mega_showdown:zygarde_cell` | Zygarde assembly component | `loot:chests/dragoeleki_chest` 32.01%; `loot:chests/regice_chest` 41.11%; `loot:chests/regigigas_chest` 16.94% +6 more |

## T2 — Rare

*Strong but repeatable. Fine as the headline reward for a genuine challenge.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:ability_capsule` | Swaps between normal abilities | `loot:chests/registeel_chest` 78.61%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:ability_shield` | Premium competitive held item | `loot:rare/battle`; `market` |
| `cobblemon:air_balloon` | Premium competitive held item | `loot:rare/battle`; `market` |
| `cobblemon:assault_vest` | Premium competitive held item | `crate:common` 0.5%; `market` |
| `cobblemon:auspicious_armor` | Evolution item | `crate:common` 0.203%; `loot:chests/bell_tower_chest` 9.07% |
| `cobblemon:black_augurite` | Evolution item | `crate:common` 0.203% |
| `cobblemon:blunder_policy` | Premium competitive held item | `loot:rare/battle`; `market` |
| `cobblemon:booster_energy` | Premium competitive held item | *not currently granted anywhere* |
| `cobblemon:chipped_pot` | Evolution item | `crate:common` 0.203% |
| `cobblemon:choice_band` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:choice_scarf` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:choice_specs` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:clear_amulet` | Premium competitive held item | `market` |
| `cobblemon:covert_cloak` | Premium competitive held item | `market` |
| `cobblemon:cracked_pot` | Evolution item | `crate:common` 0.203% |
| `cobblemon:deep_sea_scale` | Evolution item | `crate:common` 0.203%; `market` |
| `cobblemon:deep_sea_tooth` | Evolution item | `crate:common` 0.203%; `market` |
| `cobblemon:dragon_scale` | Evolution item | `crate:common` 0.203% |
| `cobblemon:dream_ball` | Specialty ball — Hidden Ability transfer | `crate:common` 4.6%; `loot:legendary/pokeballs`; `market` |
| `cobblemon:dubious_disc` | Evolution item | `crate:common` 0.203% |
| `cobblemon:electirizer` | Evolution item | `crate:common` 0.203% |
| `cobblemon:eviolite` | Premium competitive held item | `crate:common` 0.5%; `market` |
| `cobblemon:exp_candy_l` | Feedstock for IV candies — heavy consumption keeps it scarce despite being on the shelf | `market` |
| `cobblemon:exp_candy_xl` | Feedstock for IV candies — heavy consumption keeps it scarce despite being on the shelf | `market` |
| `cobblemon:expert_belt` | Premium competitive held item | `market` |
| `cobblemon:flame_plate` | Arceus plate / type booster | *not currently granted anywhere* |
| `cobblemon:focus_sash` | Premium competitive held item | `crate:common` 0.5%; `loot:uncommon/battle`; `market` |
| `cobblemon:galarica_cuff` | Evolution item | `crate:common` 0.203% |
| `cobblemon:galarica_wreath` | Evolution item | `crate:common` 0.203% |
| `cobblemon:heavy_duty_boots` | Premium competitive held item | `crate:common` 0.5%; `loot:rare/battle`; `market` |
| `cobblemon:leftovers` | Premium competitive held item | `crate:common` 0.305%; `loot:legendary/battle`; `market` |
| `cobblemon:life_orb` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:link_cable` | Evolution item | `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:loaded_dice` | Premium competitive held item | `market` |
| `cobblemon:magmarizer` | Evolution item | `crate:common` 0.203% |
| `cobblemon:malicious_armor` | Evolution item | `crate:common` 0.203% |
| `cobblemon:masterpiece_teacup` | Evolution item | `crate:common` 0.203% |
| `cobblemon:metal_alloy` | Evolution item | `crate:common` 0.203% |
| `cobblemon:mirror_herb` | Premium competitive held item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:oval_stone` | Evolution item | `crate:common` 0.203% |
| `cobblemon:prism_scale` | Evolution item | `crate:common` 0.203% |
| `cobblemon:protector` | Evolution item | `crate:common` 0.203%; `loot:rare/battle`; `loot:chests/regirock_chest` 70.66% |
| `cobblemon:punching_glove` | Premium competitive held item | `market` |
| `cobblemon:rare_candy` | Instant level. Purchasable, but heavy demand keeps it scarce. | `crate:common` 5.1%; `crate:rare` 12.0%; `loot:epic/medicine` +3 more |
| `cobblemon:reaper_cloth` | Evolution item | `crate:common` 0.203%; `loot:chests/turnback_cave_vault` 5.48% |
| `cobblemon:rocky_helmet` | Premium competitive held item | `crate:common` 0.5%; `market` |
| `cobblemon:sachet` | Evolution item | `crate:common` 0.203% |
| `cobblemon:throat_spray` | Premium competitive held item | `market` |
| `cobblemon:weakness_policy` | Premium competitive held item | `market` |
| `cobblemon:whipped_dream` | Evolution item | `crate:common` 0.203% |
| `legendarymonuments:ancient_rubble_ore` | Ore block, mid-tier crafting material | `loot:chests/regigigas_chest` 16.94% |
| `legendarymonuments:dragon_golem_ingot` | Golem crafting material | *not currently granted anywhere* |
| `legendarymonuments:dragon_pauldron` | Regi armour component | *not currently granted anywhere* |
| `legendarymonuments:electric_golem_ingot` | Golem crafting material | *not currently granted anywhere* |
| `legendarymonuments:electric_pauldron` | Regi armour component | *not currently granted anywhere* |
| `legendarymonuments:golem_scrap` | Golem crafting material — a component, not a gate | `loot:chests/turnback_cave_chest` 1.98%; `loot:chests/turnback_cave_vault` 2.22% |
| `legendarymonuments:ice_golem_ingot` | Golem crafting material | *not currently granted anywhere* |
| `legendarymonuments:ice_pauldron` | Regi armour component | *not currently granted anywhere* |
| `legendarymonuments:rock_golem_ingot` | Golem crafting material | *not currently granted anywhere* |
| `legendarymonuments:rock_pauldron` | Regi armour component | *not currently granted anywhere* |
| `legendarymonuments:steel_golem_ingot` | Golem crafting material | *not currently granted anywhere* |
| `legendarymonuments:steel_pauldron` | Regi armour component | *not currently granted anywhere* |
| `legendarymonuments:titan_pauldron` | Regi armour component | *not currently granted anywhere* |
| `mega_showdown:abomasite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:absolite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:adrenaline_orb` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:aerodactylite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:aggronite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:alakazite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:altarianite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:ampharosite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:archie_anchor` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:audinite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:banettite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:beedrillite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:blastoisinite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:blazikenite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:booster_energy` | Premium competitive held item | *not currently granted anywhere* |
| `mega_showdown:brendan_mega_cuff` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:bug_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:burn_drive` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:cameruptite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:charizardite_x` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:charizardite_y` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:chill_drive` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:cornerstone_mask` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:dark_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:diancite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:dna_splicer` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:douse_drive` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:draco_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:dragon_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:dread_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:earth_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:electric_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:fairy_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:fighting_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:fire_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:fist_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:flame_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:flying_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:galladite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:garchompite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:gardevoirite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:gengarite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:ghost_memory` | Silvally memory | `loot:chests/turnback_cave_vault` 10.75% |
| `mega_showdown:glalitite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:grass_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:ground_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:gyaradosite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:hearthflame_mask` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:heracronite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:houndoominite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:ice_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:icicle_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:insect_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:iron_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:kangaskhanite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:korrina_glove` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:latiasite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:latiosite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:legend_plate` | Arceus plate / type booster | *not currently granted anywhere* |
| `mega_showdown:likos_pendant` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:lisia_mega_tiara` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:lopunnite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:lucarionite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:lysandre_ring` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:manectite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mawilite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:maxie_glasses` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:may_bracelet` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:meadow_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:medichamite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_bracelet` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_bracelet_black` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_bracelet_blue` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_bracelet_green` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_bracelet_pink` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_bracelet_red` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_bracelet_yellow` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_meteorid_block` | Mega Showdown item (type/forme adjacent) | `loot:archaeology/ruins` 4.55% |
| `mega_showdown:mega_ring` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:meltan` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:metagrossite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mewtwonite_x` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mewtwonite_y` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mind_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:n_lunarizer` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:n_solarizer` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:omni_ring` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:pidgeotite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:pika_case` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:pink_nectar` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:pinsirite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:pixie_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:poison_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:prison_bottle` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:psychic_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:purple_nectar` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:red_nectar` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:reins_of_unity` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:reveal_glass` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:rock_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:rotom_catalogue` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sablenite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:salamencite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sceptilite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:scizorite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sharpedonite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:shock_drive` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sky_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:slowbronite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:soul_dew` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sparkling_stone_dark` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sparkling_stone_light` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:splash_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:spooky_plate` | Arceus plate / type booster | `loot:chests/turnback_cave_vault` 2.22%; `market` |
| `mega_showdown:star_core` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:steel_memory` | Silvally memory | `loot:chests/registeel_chest` 6.87% |
| `mega_showdown:steelixite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:stone_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:swampertite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:toxic_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:tyranitarite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:venusaurite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:water_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:wellspring_mask` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:wishing_star` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:yellow_nectar` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:zap_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:zygarde_cube` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `minecraft:ancient_debris` | Netherite path | *not currently granted anywhere* |
| `minecraft:beacon` | $50,000 — the single most expensive market item | `market` |
| `minecraft:elytra` | Vanilla chase item | *not currently granted anywhere* |
| `minecraft:enchanted_golden_apple` | Vanilla chase item | `loot:chests/bell_tower_chest` 2.34%; `loot:chests/turnback_cave_vault` 2.22% |
| `minecraft:heart_of_the_sea` | Vanilla chase item | `loot:chests/liberty_island_chest` 12.32%; `loot:chests/lugia_temple_chest` 5.54% |
| `minecraft:netherite_scrap` | Netherite path | *not currently granted anywhere* |

## T1 — Uncommon

*Routine reward scale. Safe for regular play loops.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:absorb_bulb` | Standard held / utility item | `loot:uncommon/battle`; `market` |
| `cobblemon:adamant_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:aguav_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:apicot_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:apricorn_boat` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:apricorn_chest_boat` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:aprijuice_black` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:aprijuice_blue` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:aprijuice_green` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:aprijuice_pink` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:aprijuice_red` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:aprijuice_white` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:aprijuice_yellow` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:armor_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:aspear_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:automaton_armor_trim_smithing_template` | Standard held / utility item | `loot:legendary/archeology` |
| `cobblemon:azure_ball` | Specialty ball | `loot:chests/regice_chest` 85.07% |
| `cobblemon:babiri_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:belue_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:berry_juice` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:berry_sweet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:big_malasada` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:big_root` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:binding_band` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:black_apricorn` | Apricorn | *not currently granted anywhere* |
| `cobblemon:black_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:black_belt` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:black_glasses` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:black_sludge` | Standard held / utility item | `crate:common` 0.305%; `loot:legendary/battle`; `loot:chests/turnback_cave_chest` 11.36% +1 more |
| `cobblemon:black_tumblestone` | Crafting material | `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:blue_apricorn` | Apricorn | *not currently granted anywhere* |
| `cobblemon:blue_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:blue_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:blue_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:bluk_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:bold_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:brave_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:bright_powder` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:brittle_candy` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:bugwort` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:bygone_sherd` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:calcium` | Vitamin | `crate:common` 1.183%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:calm_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:candied_apple` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:candied_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:capture_sherd` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:carbos` | Vitamin | `crate:common` 1.183%; `loot:chests/dragoeleki_chest` 6.12%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:careful_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:casteliacone` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:cell_battery` | Standard held / utility item | `loot:rare/battle`; `loot:chests/dragoeleki_chest` 32.01%; `market` |
| `cobblemon:charcoal_stick` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:charti_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:cheri_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:cherish_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:chesto_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:chilan_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:chople_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:citrine_ball` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:claw_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:cleanse_tag` | Standard held / utility item | `loot:uncommon/battle`; `market` |
| `cobblemon:clever_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:clever_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:clover_sweet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:coarse_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:coba_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:colbur_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:cornn_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:cover_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:coward_candy` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:cream_puff` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:custap_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:cyan_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:cyan_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:damp_rock` | Standard held / utility item | `loot:rare/battle`; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:destiny_knot` | Standard held / utility item | `crate:common` 1.5%; `loot:rare/battle`; `market` |
| `cobblemon:dire_hit` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:dive_ball` | Specialty ball | `loot:chests/liberty_island_chest` 39.31%; `loot:chests/lugia_temple_chest` 58.54% |
| `cobblemon:dive_rod` | Standard held / utility item | `loot:chests/liberty_island_chest` 12.32% |
| `cobblemon:dome_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:dome_sherd` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:dragon_fang` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/dragoeleki_chest` 32.01% +1 more |
| `cobblemon:durin_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:dusk_ball` | Specialty ball | `loot:chests/regirock_chest` 70.66%; `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:eggant_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:eject_button` | Standard held / utility item | `market` |
| `cobblemon:eject_pack` | Standard held / utility item | `market` |
| `cobblemon:electric_seed` | Standard held / utility item | `loot:chests/dragoeleki_chest` 32.01% |
| `cobblemon:enigma_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:everstone` | Standard held / utility item | `crate:common` 1.5%; `market` |
| `cobblemon:exp_share` | Standard held / utility item | `market` |
| `cobblemon:fairy_feather` | EV feather | `loot:rare/battle`; `loot:chests/bell_tower_chest` 30.22%; `market` |
| `cobblemon:fast_ball` | Specialty ball | `loot:chests/dragoeleki_chest` 74.23% |
| `cobblemon:figy_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:flame_orb` | Standard held / utility item | `loot:legendary/battle`; `market` |
| `cobblemon:float_stone` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:flower_sweet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:focus_band` | Standard held / utility item | `market` |
| `cobblemon:fossilized_bird` | Fossil | `loot:legendary/archeology` |
| `cobblemon:fossilized_dino` | Fossil | `loot:legendary/archeology` |
| `cobblemon:fossilized_drake` | Fossil | `loot:legendary/archeology` |
| `cobblemon:fossilized_fish` | Fossil | `loot:legendary/archeology` |
| `cobblemon:fresh_start_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:friend_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:galarica_nuts` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:ganlon_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:genius_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:genius_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:gentle_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:grassy_seed` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:green_apricorn` | Apricorn | *not currently granted anywhere* |
| `cobblemon:green_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:green_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:green_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:grepa_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:grip_claw` | Standard held / utility item | `market` |
| `cobblemon:growth_mulch` | Mulch | *not currently granted anywhere* |
| `cobblemon:guard_spec` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:haban_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:hard_stone` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/regirock_chest` 70.66% +1 more |
| `cobblemon:hasty_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:heal_ball` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:heal_powder` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:health_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:health_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:hearty_grains` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:heat_rock` | Standard held / utility item | `loot:rare/battle`; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:heavy_ball` | Specialty ball | `loot:chests/registeel_chest` 78.61% |
| `cobblemon:helix_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:helix_sherd` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:hondew_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:hopo_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:hp_up` | Vitamin | `crate:common` 1.183%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:humid_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:iapapa_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:icy_rock` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:impish_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:iron` | Vitamin | `crate:common` 1.183%; `loot:chests/registeel_chest` 35.31%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:iron_ball` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:jaboca_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:jaw_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:jelly_doughnut` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:jolly_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:jubilife_muffin` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:kasib_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:kebia_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:kee_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:kelpsy_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:kings_rock` | Standard held / utility item | `crate:common` 0.203%; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:lagging_tail` | Standard held / utility item | `market` |
| `cobblemon:lansat_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:lava_cookie` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:lax_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:leek_and_potato_stew` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:leppa_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:level_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:liechi_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:light_ball` | Standard held / utility item | `market` |
| `cobblemon:light_clay` | Standard held / utility item | `market` |
| `cobblemon:loamy_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:lonely_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:love_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:love_sweet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:lucky_egg` | Standard held / utility item | `crate:common` 3.1%; `crate:rare` 7.7%; `loot:chests/bell_tower_chest` 9.07% +1 more |
| `cobblemon:lum_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:luminous_moss` | Standard held / utility item | `market` |
| `cobblemon:lumiose_galette` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:lure_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:luxury_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:magnet` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/dragoeleki_chest` 32.01% +2 more |
| `cobblemon:mago_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:magost_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:maranga_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:medicinal_brew` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:mental_herb` | Standard held / utility item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:metal_coat` | Standard held / utility item | `crate:common` 0.305%; `loot:chests/registeel_chest` 78.61%; `market` |
| `cobblemon:metal_powder` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:metronome` | Standard held / utility item | `market` |
| `cobblemon:micle_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:mild_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:miracle_seed` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:misty_seed` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:modest_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:moomoo_milk` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:moon_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:mulch_base` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:muscle_band` | Standard held / utility item | `crate:common` 0.305%; `market` |
| `cobblemon:muscle_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:muscle_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:mystic_water` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:naive_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:nanab_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:naughty_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:nest_ball` | Specialty ball | `market` |
| `cobblemon:net_ball` | Specialty ball | `loot:chests/lugia_temple_chest` 58.54%; `market` |
| `cobblemon:never_melt_ice` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/regice_chest` 41.11% +1 more |
| `cobblemon:nomel_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:nostalgic_sherd` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:npc_editor` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:numb_candy` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:occa_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:old_amber_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:old_gateau` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:open_faced_sandwich` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:oran_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:pamtre_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:park_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:passho_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:payapa_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:peat_block` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:peat_mulch` | Mulch | *not currently granted anywhere* |
| `cobblemon:pecha_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:persim_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:petaya_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:pewter_crunchies` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pinap_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:pink_apricorn` | Apricorn | `loot:uncommon/nature` |
| `cobblemon:pink_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:pink_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:pink_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:plume_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:poison_barb` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:poke_bait` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:poke_puff` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:poke_rod` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokedex_black` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokedex_blue` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokedex_green` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokedex_pink` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokedex_red` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokedex_white` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokedex_yellow` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pokerod_smithing_template` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:pomeg_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:ponigiri` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:potato_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:power_anklet` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_band` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_belt` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_bracer` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_herb` | Standard held / utility item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:power_lens` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_weight` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:pp_max` | Standard held / utility item | `loot:epic/medicine`; `loot:chests/turnback_cave_chest` 1.98% |
| `cobblemon:pp_up` | Standard held / utility item | `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:premier_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:protective_pads` | Standard held / utility item | `market` |
| `cobblemon:protein` | Vitamin | `crate:common` 1.183%; `loot:chests/regirock_chest` 5.59%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:psychic_seed` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:qualot_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:quick_ball` | Specialty ball | `crate:common` 3.1%; `market` |
| `cobblemon:quick_claw` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:quick_powder` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:quiet_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:rabuta_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:rage_candy_bar` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:rash_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:rawst_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:razor_claw` | Standard held / utility item | `crate:common` 0.203%; `market` |
| `cobblemon:razor_fang` | Standard held / utility item | `crate:common` 0.203%; `market` |
| `cobblemon:razz_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:red_apricorn` | Apricorn | `loot:uncommon/nature` |
| `cobblemon:red_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:red_card` | Standard held / utility item | `market` |
| `cobblemon:red_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:red_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:relaxed_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:repeat_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:resist_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:resist_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:revival_herb` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:ribbon_sweet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:rich_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:rindo_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:ring_target` | Standard held / utility item | `market` |
| `cobblemon:roasted_leek` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:room_service` | Standard held / utility item | `market` |
| `cobblemon:root_fossil` | Fossil | *not currently granted anywhere* |
| `cobblemon:roseate_ball` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:roseli_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:rowap_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:saccharine_boat` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:saccharine_chest_boat` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:saccharine_sapling` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:safari_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:safety_goggles` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:sail_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:salac_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:sandy_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:sassy_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:scope_lens` | Standard held / utility item | `market` |
| `cobblemon:scroll_of_darkness` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:scroll_of_waters` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:serious_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:shalour_sable` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:sharp_beak` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:shed_shell` | Standard held / utility item | `market` |
| `cobblemon:shell_bell` | Standard held / utility item | `market` |
| `cobblemon:shell_helmet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:shuca_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:sickly_candy` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:silk_scarf` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/regigigas_chest` 69.14% +1 more |
| `cobblemon:silver_powder` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:sinister_tea` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:sitrus_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:skull_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:sky_tumblestone` | Crafting material | `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:slate_ball` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:slow_candy` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:small_budding_sky_tumblestone` | Crafting material | `loot:legendary/archeology` |
| `cobblemon:smoke_ball` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:smoked_tail_curry` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:smooth_rock` | Standard held / utility item | `loot:rare/battle`; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:soft_sand` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:soothe_bell` | Standard held / utility item | `loot:chests/bell_tower_chest` 9.07%; `market` |
| `cobblemon:spell_tag` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/turnback_cave_chest` 11.36% +2 more |
| `cobblemon:spelon_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:sport_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:star_sweet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:starf_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:sticky_barb` | Standard held / utility item | `market` |
| `cobblemon:strawberry_sweet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:surprise_mulch` | Mulch | *not currently granted anywhere* |
| `cobblemon:suspicious_sherd` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:sweet_apple` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:swift_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:swift_mochi` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:syrupy_apple` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:tamato_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:tanga_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:tart_apple` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:tasty_tail` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:terrain_extender` | Standard held / utility item | `market` |
| `cobblemon:timer_ball` | Specialty ball | `crate:common` 4.6%; `loot:chests/regigigas_chest` 99.11%; `market` |
| `cobblemon:timid_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:touga_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:toxic_orb` | Standard held / utility item | `loot:legendary/battle`; `market` |
| `cobblemon:tumblestone` | Standard held / utility item | `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:twisted_spoon` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:unremarkable_teacup` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:upgrade` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:utility_umbrella` | Standard held / utility item | `market` |
| `cobblemon:verdant_ball` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:vivichoke` | Standard held / utility item | `loot:epic/nature` |
| `cobblemon:vivichoke_dip` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:vivichoke_seeds` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:wacan_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:watmel_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:weak_candy` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:wepear_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:white_apricorn` | Apricorn | `loot:uncommon/nature` |
| `cobblemon:white_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:white_herb` | Standard held / utility item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:white_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:white_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:wide_lens` | Standard held / utility item | `market` |
| `cobblemon:wiki_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:wise_glasses` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:x_accuracy` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:x_attack` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:x_defence` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:x_special_attack` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:x_special_defence` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:x_speed` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:yache_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:yellow_apricorn` | Apricorn | *not currently granted anywhere* |
| `cobblemon:yellow_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:zinc` | Vitamin | `crate:common` 1.183%; `loot:chests/regice_chest` 8.25%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:zoom_lens` | Standard held / utility item | `market` |
| `legendarymonuments:blue_feather` | EV feather | *not currently granted anywhere* |
| `legendarymonuments:clear_bell` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:cosmic_bag` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:dream_string` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:galar_particle` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:lunar_feather` | EV feather | *not currently granted anywhere* |
| `legendarymonuments:poketreat_box` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:red_feather` | EV feather | *not currently granted anywhere* |
| `legendarymonuments:special_leafy_greens` | Curry ingredient — a Swords of Justice favourite | *not currently granted anywhere* |
| `legendarymonuments:special_meat_chunks` | Curry ingredient — a Swords of Justice favourite | *not currently granted anywhere* |
| `legendarymonuments:special_spices` | Curry ingredient — a Swords of Justice favourite | *not currently granted anywhere* |
| `legendarymonuments:yellow_feather` | EV feather | *not currently granted anywhere* |
| `minecraft:diamond` | Common enough at this point in progression | `loot:epic/diverse`; `loot:chests/woodland_mansion` 0.0%; `loot:chests/bell_tower_chest` 9.07% +4 more |

## T0 — Common

*Filler. Safe to hand out in bulk.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:ancient_azure_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_citrine_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_feather_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_gigaton_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_great_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_heavy_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_ivory_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_jet_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_leaden_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_origin_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_poke_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_roseate_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_slate_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_ultra_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_verdant_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:ancient_wing_ball` | Hisuian basic ball | *not currently granted anywhere* |
| `cobblemon:antidote` | Status heal | `market` |
| `cobblemon:awakening` | Status heal | `market` |
| `cobblemon:burn_heal` | Status heal | `market` |
| `cobblemon:dawn_stone` | Evolution stone | `crate:common` 0.71% |
| `cobblemon:dusk_stone` | Evolution stone | `crate:common` 0.71%; `loot:chests/turnback_cave_chest` 11.36%; `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:dusk_stone_block` | Evolution stone | `loot:chests/turnback_cave_vault` 2.22% |
| `cobblemon:elixir` | PP consumable | `loot:chests/turnback_cave_vault` 5.48%; `market` |
| `cobblemon:energy_root` | Herbal heal | `loot:uncommon/nature` |
| `cobblemon:ether` | PP consumable | `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:exp_candy_m` | Small exp candy | `loot:chests/bell_tower_chest` 9.07%; `loot:chests/liberty_island_chest` 12.32%; `loot:chests/lugia_temple_chest` 20.5% +2 more |
| `cobblemon:exp_candy_s` | Small exp candy | `loot:chests/bell_tower_chest` 30.22%; `loot:chests/dragoeleki_chest` 74.23%; `loot:chests/liberty_island_chest` 39.31% +6 more |
| `cobblemon:exp_candy_xs` | Small exp candy | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/liberty_island_chest` 74.68%; `loot:chests/lugia_temple_chest` 91.65% +1 more |
| `cobblemon:fine_remedy` | Herbal heal | `loot:uncommon/nature`; `loot:chests/turnback_cave_chest` 33.52%; `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:fire_stone` | Evolution stone | `crate:common` 0.71% |
| `cobblemon:full_heal` | Status heal | `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:full_restore` | Healing consumable | `loot:epic/medicine`; `loot:chests/turnback_cave_vault` 5.48%; `market` |
| `cobblemon:great_ball` | Basic ball | `crate:common` 4.1%; `loot:chests/liberty_island_chest` 74.68%; `market` |
| `cobblemon:hyper_potion` | Healing consumable | `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:ice_heal` | Status heal | `market` |
| `cobblemon:ice_stone` | Evolution stone | `crate:common` 0.71%; `loot:chests/regice_chest` 41.11% |
| `cobblemon:leaf_stone` | Evolution stone | `crate:common` 0.71% |
| `cobblemon:max_elixir` | PP consumable | `loot:legendary/medicine`; `loot:chests/turnback_cave_vault` 5.48%; `market` |
| `cobblemon:max_ether` | PP consumable | `loot:epic/medicine`; `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:max_potion` | Healing consumable | `loot:epic/medicine`; `loot:chests/turnback_cave_vault` 5.48%; `market` |
| `cobblemon:max_revive` | Revive consumable | `loot:legendary/medicine`; `loot:chests/turnback_cave_vault` 5.48%; `market` |
| `cobblemon:medicinal_leek` | Herbal heal | `loot:uncommon/nature` |
| `cobblemon:moon_stone` | Evolution stone | `crate:common` 0.71% |
| `cobblemon:paralyze_heal` | Status heal | `market` |
| `cobblemon:poke_ball` | Starter ball | `market` |
| `cobblemon:potion` | Healing consumable | `market` |
| `cobblemon:relic_coin` | Base currency unit | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/dragoeleki_chest` 74.23%; `loot:chests/liberty_island_chest` 74.68% +7 more |
| `cobblemon:relic_coin_pouch` | Currency | `loot:epic/diverse`; `loot:chests/bell_tower_chest` 30.22%; `loot:chests/dragoeleki_chest` 32.01% +4 more |
| `cobblemon:relic_coin_sack` | Currency | `loot:chests/bell_tower_chest` 9.07% |
| `cobblemon:remedy` | Herbal heal | `loot:chests/turnback_cave_chest` 33.52%; `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:revive` | Revive consumable | `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:shiny_stone` | Evolution stone | `crate:common` 0.71% |
| `cobblemon:sun_stone` | Evolution stone | `crate:common` 0.71% |
| `cobblemon:super_potion` | Healing consumable | `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:superb_remedy` | Herbal heal | `loot:chests/turnback_cave_chest` 33.52%; `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:thunder_stone` | Evolution stone | `crate:common` 0.71% |
| `cobblemon:ultra_ball` | Basic ball | `crate:common` 3.1%; `market` |
| `cobblemon:water_stone` | Evolution stone | `crate:common` 0.71% |
| `minecraft:apple` | Vanilla bulk material | `loot:uncommon/nature` |
| `minecraft:arrow` | Vanilla item | `market` |
| `minecraft:axolotl_bucket` | Vanilla item | `loot:chests/liberty_island_chest` 39.31%; `loot:chests/lugia_temple_chest` 20.5%; `market` |
| `minecraft:blaze_rod` | Vanilla item | `loot:epic/diverse`; `market` |
| `minecraft:bone` | Vanilla bulk material | `market` |
| `minecraft:bricks` | Vanilla building block | `market` |
| `minecraft:carrot` | Vanilla item | `loot:uncommon/nature`; `loot:loot_table/empty` 100.0%; `loot:chests/observatory_barrel_2` 99.19% +2 more |
| `minecraft:coal` | Vanilla bulk material | `loot:chests/observatory_barrel_2` 44.44% |
| `minecraft:cobblestone` | Vanilla bulk material | `market` |
| `minecraft:cobweb` | Vanilla item | `loot:chests/observatory_barrel_2` 86.52%; `loot:chests/observatory_chest` 86.52% |
| `minecraft:copper_ingot` | Vanilla bulk material | `loot:chests/turnback_cave_vault` 10.75% |
| `minecraft:creeper_banner_pattern` | Vanilla item | `loot:epic/diverse` |
| `minecraft:dark_prismarine` | Vanilla item | `market` |
| `minecraft:dirt` | Vanilla bulk material | `market` |
| `minecraft:disc_fragment_5` | Vanilla item | `loot:epic/diverse` |
| `minecraft:dragon_breath` | Vanilla item | `loot:chests/dragoeleki_chest` 32.01% |
| `minecraft:echo_shard` | Vanilla item | `loot:epic/diverse`; `loot:chests/turnback_cave_chest` 1.98% |
| `minecraft:egg` | Vanilla item | `loot:chests/observatory_barrel_2` 44.44% |
| `minecraft:emerald` | Vanilla bulk material | `loot:epic/diverse`; `loot:chests/bell_tower_chest` 30.22%; `loot:chests/liberty_island_chest` 39.31% +2 more |
| `minecraft:enchanted_book` | Vanilla item | `loot:epic/diverse` |
| `minecraft:end_crystal` | Vanilla item | *not currently granted anywhere* |
| `minecraft:ender_eye` | Vanilla item | `crate:rare` 2.5%; `loot:epic/diverse` |
| `minecraft:ender_pearl` | Vanilla item | `loot:chests/turnback_cave_chest` 33.52%; `market` |
| `minecraft:experience_bottle` | Vanilla item | `loot:epic/diverse`; `loot:chests/bell_tower_chest` 62.5% |
| `minecraft:fire_charge` | Vanilla item | *not currently granted anywhere* |
| `minecraft:firework_star` | Vanilla item | `loot:epic/diverse` |
| `minecraft:flower_banner_pattern` | Vanilla item | `loot:epic/diverse` |
| `minecraft:ghast_tear` | Vanilla item | `loot:epic/diverse`; `loot:chests/turnback_cave_chest` 33.52%; `market` |
| `minecraft:glistering_melon_slice` | Vanilla item | `loot:epic/nature` |
| `minecraft:globe_banner_pattern` | Vanilla item | `loot:epic/diverse` |
| `minecraft:glow_ink_sac` | Vanilla item | `loot:chests/liberty_island_chest` 74.68% |
| `minecraft:gold_ingot` | Vanilla bulk material | `loot:chests/bell_tower_chest` 30.22%; `loot:chests/lugia_temple_chest` 58.54%; `loot:chests/turnback_cave_chest` 33.52% |
| `minecraft:gold_nugget` | Vanilla bulk material | `loot:chests/observatory_barrel_2` 61.84%; `loot:chests/observatory_chest` 61.84% |
| `minecraft:golden_apple` | Vanilla item | `loot:epic/nature`; `loot:chests/bell_tower_chest` 30.22%; `loot:chests/lugia_temple_chest` 58.54% |
| `minecraft:golden_carrot` | Vanilla item | `loot:epic/nature`; `loot:chests/bell_tower_chest` 30.22% |
| `minecraft:gravel` | Vanilla bulk material | `market` |
| `minecraft:green_wool` | Vanilla item | *not currently granted anywhere* |
| `minecraft:gunpowder` | Vanilla item | `market` |
| `minecraft:iron_axe` | Vanilla item | `loot:chests/woodland_mansion` 0.0% |
| `minecraft:iron_ingot` | Vanilla bulk material | `loot:chests/registeel_chest` 35.31%; `loot:chests/turnback_cave_chest` 33.52%; `loot:chests/turnback_cave_vault` 10.75% |
| `minecraft:iron_nugget` | Vanilla bulk material | `loot:chests/registeel_chest` 78.61% |
| `minecraft:iron_sword` | Vanilla item | *not currently granted anywhere* |
| `minecraft:lapis_lazuli` | Vanilla bulk material | `loot:chests/liberty_island_chest` 74.68% |
| `minecraft:magma_cream` | Vanilla item | `market` |
| `minecraft:map` | Vanilla item | `loot:chests/liberty_island_chest` 74.68% |
| `minecraft:melon_slice` | Vanilla item | `loot:uncommon/nature` |
| `minecraft:mojang_banner_pattern` | Vanilla item | `loot:epic/diverse` |
| `minecraft:music_disc_11` | Vanilla item | `loot:chests/turnback_cave_chest` 11.36% |
| `minecraft:music_disc_cat` | Vanilla item | `loot:epic/diverse` |
| `minecraft:music_disc_otherside` | Vanilla item | `loot:epic/diverse` |
| `minecraft:nautilus_shell` | Vanilla item | `loot:chests/liberty_island_chest` 39.31%; `market` |
| `minecraft:nether_wart` | Vanilla item | `loot:epic/nature` |
| `minecraft:netherite_upgrade_smithing_template` | Vanilla item | `loot:legendary/archeology` |
| `minecraft:oak_log` | Vanilla building block | `market` |
| `minecraft:obsidian` | Vanilla item | *not currently granted anywhere* |
| `minecraft:ochre_froglight` | Vanilla item | `market` |
| `minecraft:pearlescent_froglight` | Vanilla item | `market` |
| `minecraft:phantom_membrane` | Vanilla item | `loot:epic/diverse`; `market` |
| `minecraft:piglin_banner_pattern` | Vanilla item | `loot:epic/diverse` |
| `minecraft:potato` | Vanilla item | `loot:uncommon/nature` |
| `minecraft:potion` | Healing consumable | `loot:epic/diverse` |
| `minecraft:prismarine` | Vanilla item | `market` |
| `minecraft:prismarine_crystals` | Vanilla item | `loot:epic/diverse`; `market` |
| `minecraft:prismarine_shard` | Vanilla item | `loot:epic/diverse`; `market` |
| `minecraft:pufferfish` | Vanilla item | `loot:chests/liberty_island_chest` 74.68% |
| `minecraft:pumpkin_pie` | Vanilla item | `loot:uncommon/nature` |
| `minecraft:raw_gold` | Vanilla item | `loot:chests/bell_tower_chest` 62.5% |
| `minecraft:red_wool` | Vanilla item | *not currently granted anywhere* |
| `minecraft:rotten_flesh` | Vanilla item | `loot:chests/observatory_barrel_2` 61.84%; `loot:chests/observatory_chest` 61.84%; `market` |
| `minecraft:sand` | Vanilla bulk material | `loot:chests/lugia_temple_chest` 91.65%; `market` |
| `minecraft:shelter_pottery_sherd` | Vanilla item | `loot:chests/observatory_barrel_2` 61.84%; `loot:chests/observatory_chest` 61.84% |
| `minecraft:shield` | Vanilla item | *not currently granted anywhere* |
| `minecraft:shulker_shell` | Vanilla item | `market` |
| `minecraft:skull_banner_pattern` | Vanilla item | `loot:epic/diverse` |
| `minecraft:slime_ball` | Vanilla item | `market` |
| `minecraft:smooth_stone` | Vanilla building block | `market` |
| `minecraft:sniffer_egg` | Vanilla item | `loot:chests/bell_tower_chest` 9.07% |
| `minecraft:spider_eye` | Vanilla item | `market` |
| `minecraft:spyglass` | Vanilla item | `loot:chests/liberty_island_chest` 39.31% |
| `minecraft:stone_bricks` | Vanilla building block | `market` |
| `minecraft:string` | Vanilla bulk material | `market` |
| `minecraft:sunflower` | Vanilla item | *not currently granted anywhere* |
| `minecraft:terracotta` | Vanilla item | `market` |
| `minecraft:torchflower` | Vanilla item | `loot:chests/bell_tower_chest` 30.22% |
| `minecraft:torchflower_seeds` | Vanilla item | `loot:epic/nature` |
| `minecraft:tropical_fish_bucket` | Vanilla item | `market` |
| `minecraft:turtle_egg` | Vanilla item | `loot:chests/bell_tower_chest` 9.07% |
| `minecraft:turtle_helmet` | Vanilla item | `loot:chests/liberty_island_chest` 12.32% |
| `minecraft:verdant_froglight` | Vanilla item | `market` |
| `minecraft:water_bucket` | Vanilla item | `loot:chests/lugia_temple_chest` 58.54% |
| `minecraft:white_wool` | Vanilla item | *not currently granted anywhere* |

## TX — Disabled

*Intentionally not obtainable — recipe banned and/or stripped from loot. Never use as a reward. If one of these is still dropping, that's a bug to fix, not a tier to change.*

| Item | Why | Where it comes from |
|---|---|---|
| `legendarymonuments:arc_phone` | Recipe disabled by server-no-arc-phone | *not currently granted anywhere* |
| `mega_showdown:aloraichium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:blank_z` | Blank Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:bug_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:buginium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:dark_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:darkinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:decidium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:dragon_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:dragonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:dynamax_band` | Dynamax is disabled on this server | *not currently granted anywhere* |
| `mega_showdown:dynamax_candy` | Dynamax is disabled on this server | *not currently granted anywhere* |
| `mega_showdown:eevium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:electric_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:electrium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:fairium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:fairy_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:fighting_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:fightinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:fire_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:firium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:flying_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:flyinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:ghost_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:ghostium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:gladion_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:grass_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:grassium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:ground_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:groundium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:hapu_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:hapus_z_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:ice_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:icium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:incinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:kommonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:lunalium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:lycanium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:marshadium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:max_honey` | Dynamax is disabled on this server | *not currently granted anywhere* |
| `mega_showdown:max_mushroom` | Dynamax is disabled on this server | *not currently granted anywhere* |
| `mega_showdown:max_soup` | Dynamax is disabled on this server | *not currently granted anywhere* |
| `mega_showdown:mewnium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:mimikium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:nanu_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:normal_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:normalium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:olivia_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:olivias_z_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:pikanium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:pikashunium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:poison_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:poisonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:primarium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:psychic_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:psychium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:rock_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:rocket_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:rockium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:snorlium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:solganium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:steel_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:steelium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:stellar_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:sweet_max_soup` | Dynamax is disabled on this server | *not currently granted anywhere* |
| `mega_showdown:tapunium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:tera_orb` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_black` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_blue` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_brown` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_cyan` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_gray` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_green` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_light_blue` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_light_gray` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_lime` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_magenta` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_orange` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_pink` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_purple` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_red` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_white` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:tera_pouch_yellow` | Tera enabler — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:ultranecrozium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:water_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:waterium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:z_ring` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:z_ring_black` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:z_ring_blue` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:z_ring_green` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:z_ring_pink` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:z_ring_red` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |
| `mega_showdown:z_ring_yellow` | Z-Ring — enabler for the disabled Z-crystals | *not currently granted anywhere* |

## SimpleTMs (collapsed)

631 SimpleTMs are collapsed into two rules rather than enumerated. The premium split is curated by competitive relevance -- TM item ids carry no move-power data to derive it from.

- **Default: T1** — every TM not listed below.

- **Premium: T2** — 37 competitively load-bearing moves (hazards, setup, pivots, recovery, top coverage):

  `calmmind`, `closecombat`, `defog`, `dracometeor`, `dragondance`, `earthquake`, `flamethrower`, `flipturn`, `focusblast`, `hurricane`, `hydropump`, `icebeam`, `ironhead`, `knockoff`, `leechseed`, `moonblast`, `nastyplot`, `playrough`, `protect`, `psyshock`, `rapidspin`, `recover`, `roost`, `scald`, `shadowball`, `spikes`, `stealthrock`, `substitute`, `surf`, `swordsdance`, `thunderbolt`, `thunderwave`, `toxicspikes`, `trickroom`, `uturn`, `voltswitch`, `willowisp`

## How tiers are assigned

In priority order:

1. **`overrides.json`** — explicit pins. Judgment calls live here.
2. **Category rules** — patterns over item ids in `build_tiers.py`.
3. **Namespace default** — last resort.

Drop rates are recorded as *evidence* but deliberately do **not** drive the tier.
How often we currently hand something out is the thing we're trying to
sanity-check against, so deriving the tier from it would be circular — a
mispriced item would justify its own mispricing.

