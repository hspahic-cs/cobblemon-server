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

559 items tiered — T5: 4, T4: 23, T3: 26, T2: 139, T1: 187, T0: 133, TX: 47.

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
| `legendarymonuments:curry_of_justice` | Summons Keldeo | `crate:ultra` 6.5% |
| `legendarymonuments:darkstone` | Summons Zekrom | `crate:ultra` 4.9% |
| `legendarymonuments:fullmoon_whistle` | Summons Cresselia | `crate:ultra` 6.5% |
| `legendarymonuments:griseous_key` | Summons Giratina | `loot:chests/turnback_cave_chest` 11.36% |
| `legendarymonuments:liberty_pass` | Summons Victini | `crate:ultra` 6.5% |
| `legendarymonuments:lightstone` | Summons Reshiram | `crate:ultra` 4.9% |
| `legendarymonuments:newmoon_whistle` | Summons Darkrai | `crate:ultra` 6.5% |
| `legendarymonuments:old_sea_map` | Summons Mew | `loot:chests/liberty_island_chest` 12.32% |
| `legendarymonuments:proof_of_conquest_a` | Summons Azelf | `crate:ultra` 4.9% |
| `legendarymonuments:proof_of_conquest_m` | Summons Mesprit | `crate:ultra` 4.9% |
| `legendarymonuments:proof_of_conquest_u` | Summons Uxie | `crate:ultra` 4.9% |
| `legendarymonuments:rainbow_feather` | Summons Ho-Oh | `crate:ultra` 4.9% |
| `legendarymonuments:red_chain` | Summons Dialga & Palkia | `crate:ultra` 4.9% |
| `legendarymonuments:titan_key` | Titan encounter gate | `crate:rare` 2.8% |
| `legendarymonuments:vortex_stone` | Summons Lugia | `crate:ultra` 4.9% |
| `mega_showdown:adamant_crystal` | Dialga Origin forme unlock | `loot:chests/turnback_cave_vault` 0.11% |
| `mega_showdown:ash_cap` | Uncraftable — craft banned by server-craft-bans; Ultra crate only | *not currently granted anywhere* |
| `mega_showdown:blue_orb` | Primal Kyogre | `loot:chests/lugia_temple_chest` 0.57% |
| `mega_showdown:griseous_core` | Giratina Origin forme unlock | `loot:chests/turnback_cave_vault` 0.11% |
| `mega_showdown:lustrous_globe` | Palkia Origin forme unlock | `loot:chests/turnback_cave_vault` 0.11% |
| `mega_showdown:red_orb` | Primal Groudon | `loot:chests/bell_tower_chest` 0.59% |
| `mega_showdown:zygarde_core` | Zygarde assembly core | `crate:rare` 2.9%; `loot:chests/regigigas_chest` 16.94%; `loot:chests/registeel_chest` 6.87% |
| `minecraft:nether_star` | Wither drop — treat as legendary-scale | *not currently granted anywhere* |

## T3 — Epic

*Permanent competitive power or a hard-gated component. A real chase reward.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:ability_patch` | Hidden ability. Deliberately pulled from the Ultra crate. | `crate:rare` 7.2%; `loot:chests/registeel_chest` 6.87% |
| `cobblemon:beast_ball` | Best-in-slot for Ultra Beasts, not purchasable | `loot:legendary/pokeballs` |
| `cobblemon:courage_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:health_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:held_item_voucher` | Redeems for a premium held item | `crate:rare` 2.9% |
| `cobblemon:mighty_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:quick_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:smart_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:tough_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95% |
| `cobblemon:tr_voucher` | Redeems for a TR | `crate:rare` 3.9% |
| `legendarymonuments:darkstone_shard` | 9 craft into a T4 Dark Stone | `loot:chests/bell_tower_chest` 9.07%; `loot:chests/liberty_island_chest` 12.32%; `loot:chests/lugia_temple_chest` 20.5% |
| `legendarymonuments:dragon_golem_key` | Regidrago gate | `crate:rare` 2.8% |
| `legendarymonuments:ice_golem_key` | Regice gate | `crate:rare` 2.8% |
| `legendarymonuments:lightstone_shard` | 9 craft into a T4 Light Stone | `loot:chests/bell_tower_chest` 9.07%; `loot:chests/liberty_island_chest` 12.32%; `loot:chests/lugia_temple_chest` 20.5% |
| `legendarymonuments:origin_ingot` | Repairs the Red Chain — component for a T4 | `loot:chests/turnback_cave_vault` 1.11% |
| `legendarymonuments:rock_golem_key` | Regirock gate | `crate:rare` 2.8% |
| `legendarymonuments:steel_golem_key` | Registeel gate | `crate:rare` 2.8% |
| `legendarymonuments:urn_of_embers` | Legendary-adjacent gate component | `crate:rare` 2.8% |
| `legendarymonuments:urn_of_frost` | Legendary-adjacent gate component | `crate:rare` 2.8% |
| `legendarymonuments:urn_of_storms` | Legendary-adjacent gate component | `crate:rare` 2.8% |
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
| `cobblemon:bug_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:chipped_pot` | Evolution item | `crate:common` 0.203% |
| `cobblemon:choice_band` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:choice_scarf` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:choice_specs` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:clear_amulet` | Premium competitive held item | `market` |
| `cobblemon:covert_cloak` | Premium competitive held item | `market` |
| `cobblemon:cracked_pot` | Evolution item | `crate:common` 0.203% |
| `cobblemon:dark_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/turnback_cave_chest` 33.52%; `loot:archaeology/ruins` 22.73% |
| `cobblemon:deep_sea_scale` | Evolution item | `crate:common` 0.203%; `market` |
| `cobblemon:deep_sea_tooth` | Evolution item | `crate:common` 0.203%; `market` |
| `cobblemon:dragon_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/dragoeleki_chest` 74.23% |
| `cobblemon:dragon_scale` | Evolution item | `crate:common` 0.203% |
| `cobblemon:dream_ball` | Specialty ball — Hidden Ability transfer | `crate:common` 4.6%; `loot:legendary/pokeballs`; `market` |
| `cobblemon:dubious_disc` | Evolution item | `crate:common` 0.203% |
| `cobblemon:electirizer` | Evolution item | `crate:common` 0.203% |
| `cobblemon:electric_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/dragoeleki_chest` 74.23% |
| `cobblemon:eviolite` | Premium competitive held item | `crate:common` 0.5%; `market` |
| `cobblemon:exp_candy_l` | Feedstock for IV candies — heavy consumption keeps it scarce despite being on the shelf | `market` |
| `cobblemon:exp_candy_xl` | Feedstock for IV candies — heavy consumption keeps it scarce despite being on the shelf | `market` |
| `cobblemon:expert_belt` | Premium competitive held item | `market` |
| `cobblemon:fairy_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:fighting_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:fire_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/bell_tower_chest` 30.22%; `loot:chests/liberty_island_chest` 39.31% |
| `cobblemon:flame_plate` | Arceus plate / type booster | *not currently granted anywhere* |
| `cobblemon:flying_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/bell_tower_chest` 30.22% |
| `cobblemon:focus_sash` | Premium competitive held item | `crate:common` 0.5%; `loot:uncommon/battle`; `market` |
| `cobblemon:galarica_cuff` | Evolution item | `crate:common` 0.203% |
| `cobblemon:galarica_wreath` | Evolution item | `crate:common` 0.203% |
| `cobblemon:ghost_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/turnback_cave_chest` 33.52%; `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:grass_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:ground_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:heavy_duty_boots` | Premium competitive held item | `crate:common` 0.5%; `loot:rare/battle`; `market` |
| `cobblemon:ice_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/regice_chest` 85.07% |
| `cobblemon:leftovers` | Premium competitive held item | `crate:common` 0.305%; `loot:legendary/battle`; `market` |
| `cobblemon:life_orb` | Premium competitive held item | `crate:common` 0.5%; `loot:legendary/battle`; `market` |
| `cobblemon:link_cable` | Evolution item | `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:loaded_dice` | Premium competitive held item | `market` |
| `cobblemon:magmarizer` | Evolution item | `crate:common` 0.203% |
| `cobblemon:malicious_armor` | Evolution item | `crate:common` 0.203% |
| `cobblemon:masterpiece_teacup` | Evolution item | `crate:common` 0.203% |
| `cobblemon:metal_alloy` | Evolution item | `crate:common` 0.203% |
| `cobblemon:mirror_herb` | Premium competitive held item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:normal_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:oval_stone` | Evolution item | `crate:common` 0.203% |
| `cobblemon:poison_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:prism_scale` | Evolution item | `crate:common` 0.203% |
| `cobblemon:protector` | Evolution item | `crate:common` 0.203%; `loot:rare/battle`; `loot:chests/regirock_chest` 70.66% |
| `cobblemon:psychic_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/liberty_island_chest` 39.31% |
| `cobblemon:punching_glove` | Premium competitive held item | `market` |
| `cobblemon:rare_candy` | Instant level. Purchasable, but heavy demand keeps it scarce. | `crate:common` 5.1%; `crate:rare` 12.0%; `loot:epic/medicine` +3 more |
| `cobblemon:reaper_cloth` | Evolution item | `crate:common` 0.203%; `loot:chests/turnback_cave_vault` 5.48% |
| `cobblemon:rock_gem` | Type gem — one-shot damage boost | `loot:legendary/battle`; `loot:chests/regirock_chest` 70.66% |
| `cobblemon:rocky_helmet` | Premium competitive held item | `crate:common` 0.5%; `market` |
| `cobblemon:sachet` | Evolution item | `crate:common` 0.203% |
| `cobblemon:steel_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:throat_spray` | Premium competitive held item | `market` |
| `cobblemon:water_gem` | Type gem — one-shot damage boost | `loot:legendary/battle` |
| `cobblemon:weakness_policy` | Premium competitive held item | `market` |
| `cobblemon:whipped_dream` | Evolution item | `crate:common` 0.203% |
| `legendarymonuments:ancient_rubble_ore` | Ore block, mid-tier crafting material | `loot:chests/regigigas_chest` 16.94% |
| `legendarymonuments:golem_scrap` | Golem crafting material — a component, not a gate | `loot:chests/turnback_cave_chest` 1.98%; `loot:chests/turnback_cave_vault` 2.22% |
| `mega_showdown:abomasite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:absolite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:adrenaline_orb` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:aggronite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:alakazite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:altarianite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:ampharosite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:audinite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:banettite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:beedrillite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:blank_z` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:blastoisinite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:blazikenite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:cameruptite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:charizardite_x` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:charizardite_y` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:diancite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:draco_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:dread_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:earth_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:fist_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:flame_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:galladite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:garchompite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:gardevoirite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:gengarite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:ghost_memory` | Silvally memory | `loot:chests/turnback_cave_vault` 10.75% |
| `mega_showdown:glalitite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:gyaradosite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:heracronite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:houndoominite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:icicle_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:insect_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:iron_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:kangaskhanite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:lopunnite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:lucarionite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:manectite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mawilite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:meadow_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:medichamite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mega_meteorid_block` | Mega Showdown item (type/forme adjacent) | `loot:archaeology/ruins` 4.55% |
| `mega_showdown:metagrossite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:mind_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:pidgeotite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:pinsirite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:pixie_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:sablenite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:salamencite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sceptilite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:scizorite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sharpedonite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:sky_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:slowbronite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:splash_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:spooky_plate` | Arceus plate / type booster | `loot:chests/turnback_cave_vault` 2.22%; `market` |
| `mega_showdown:steel_memory` | Silvally memory | `loot:chests/registeel_chest` 6.87% |
| `mega_showdown:stone_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:swampertite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:toxic_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:tyranitarite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:venusaurite` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:zap_plate` | Arceus plate / type booster | `market` |
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
| `cobblemon:armor_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:automaton_armor_trim_smithing_template` | Standard held / utility item | `loot:legendary/archeology` |
| `cobblemon:azure_ball` | Specialty ball | `loot:chests/regice_chest` 85.07% |
| `cobblemon:big_root` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:binding_band` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:black_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:black_belt` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:black_glasses` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:black_sludge` | Standard held / utility item | `crate:common` 0.305%; `loot:legendary/battle`; `loot:chests/turnback_cave_chest` 11.36% +1 more |
| `cobblemon:black_tumblestone` | Crafting material | `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:blue_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:blue_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:blue_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:bold_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:brave_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:bright_powder` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:calcium` | Vitamin | `crate:common` 1.183%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:calm_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:carbos` | Vitamin | `crate:common` 1.183%; `loot:chests/dragoeleki_chest` 6.12%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:careful_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:cell_battery` | Standard held / utility item | `loot:rare/battle`; `loot:chests/dragoeleki_chest` 32.01%; `market` |
| `cobblemon:charcoal_stick` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:chople_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:claw_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:cleanse_tag` | Standard held / utility item | `loot:uncommon/battle`; `market` |
| `cobblemon:clever_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:coarse_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:colbur_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:cover_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:cyan_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:cyan_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:damp_rock` | Standard held / utility item | `loot:rare/battle`; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:destiny_knot` | Standard held / utility item | `crate:common` 1.5%; `loot:rare/battle`; `market` |
| `cobblemon:dire_hit` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:dive_ball` | Specialty ball | `loot:chests/liberty_island_chest` 39.31%; `loot:chests/lugia_temple_chest` 58.54% |
| `cobblemon:dive_rod` | Standard held / utility item | `loot:chests/liberty_island_chest` 12.32% |
| `cobblemon:dome_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:dragon_fang` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/dragoeleki_chest` 32.01% +1 more |
| `cobblemon:dusk_ball` | Specialty ball | `loot:chests/regirock_chest` 70.66%; `loot:chests/turnback_cave_vault` 10.75%; `market` |
| `cobblemon:eject_button` | Standard held / utility item | `market` |
| `cobblemon:eject_pack` | Standard held / utility item | `market` |
| `cobblemon:electric_seed` | Standard held / utility item | `loot:chests/dragoeleki_chest` 32.01% |
| `cobblemon:everstone` | Standard held / utility item | `crate:common` 1.5%; `market` |
| `cobblemon:exp_share` | Standard held / utility item | `market` |
| `cobblemon:fairy_feather` | EV feather | `loot:rare/battle`; `loot:chests/bell_tower_chest` 30.22%; `market` |
| `cobblemon:fast_ball` | Specialty ball | `loot:chests/dragoeleki_chest` 74.23% |
| `cobblemon:flame_orb` | Standard held / utility item | `loot:legendary/battle`; `market` |
| `cobblemon:float_stone` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:focus_band` | Standard held / utility item | `market` |
| `cobblemon:fossilized_bird` | Fossil | `loot:legendary/archeology` |
| `cobblemon:fossilized_dino` | Fossil | `loot:legendary/archeology` |
| `cobblemon:fossilized_drake` | Fossil | `loot:legendary/archeology` |
| `cobblemon:fossilized_fish` | Fossil | `loot:legendary/archeology` |
| `cobblemon:genius_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:gentle_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:green_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:green_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:green_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:grip_claw` | Standard held / utility item | `market` |
| `cobblemon:guard_spec` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:hard_stone` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/regirock_chest` 70.66% +1 more |
| `cobblemon:hasty_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:health_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:heat_rock` | Standard held / utility item | `loot:rare/battle`; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:heavy_ball` | Specialty ball | `loot:chests/registeel_chest` 78.61% |
| `cobblemon:helix_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:hp_up` | Vitamin | `crate:common` 1.183%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:humid_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:icy_rock` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:impish_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:iron` | Vitamin | `crate:common` 1.183%; `loot:chests/registeel_chest` 35.31%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:iron_ball` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:jaw_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:jolly_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:kings_rock` | Standard held / utility item | `crate:common` 0.203%; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:lagging_tail` | Standard held / utility item | `market` |
| `cobblemon:lax_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:leppa_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:light_ball` | Standard held / utility item | `market` |
| `cobblemon:light_clay` | Standard held / utility item | `market` |
| `cobblemon:loamy_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:lonely_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:lucky_egg` | Standard held / utility item | `crate:common` 3.1%; `crate:rare` 7.7%; `loot:chests/bell_tower_chest` 9.07% +1 more |
| `cobblemon:lum_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:luminous_moss` | Standard held / utility item | `market` |
| `cobblemon:magnet` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/dragoeleki_chest` 32.01% +2 more |
| `cobblemon:mental_herb` | Standard held / utility item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:metal_coat` | Standard held / utility item | `crate:common` 0.305%; `loot:chests/registeel_chest` 78.61%; `market` |
| `cobblemon:metal_powder` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:metronome` | Standard held / utility item | `market` |
| `cobblemon:mild_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:miracle_seed` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:modest_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:muscle_band` | Standard held / utility item | `crate:common` 0.305%; `market` |
| `cobblemon:muscle_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:mystic_water` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:naive_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:naughty_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:nest_ball` | Specialty ball | `market` |
| `cobblemon:net_ball` | Specialty ball | `loot:chests/lugia_temple_chest` 58.54%; `market` |
| `cobblemon:never_melt_ice` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/regice_chest` 41.11% +1 more |
| `cobblemon:old_amber_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:payapa_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:peat_block` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:pink_apricorn` | Apricorn | `loot:uncommon/nature` |
| `cobblemon:pink_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:pink_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:pink_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:plume_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:poison_barb` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:power_anklet` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_band` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_belt` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_bracer` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_herb` | Standard held / utility item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:power_lens` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:power_weight` | EV training item | `crate:common` 0.667%; `market` |
| `cobblemon:pp_max` | Standard held / utility item | `loot:epic/medicine`; `loot:chests/turnback_cave_chest` 1.98% |
| `cobblemon:pp_up` | Standard held / utility item | `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:protective_pads` | Standard held / utility item | `market` |
| `cobblemon:protein` | Vitamin | `crate:common` 1.183%; `loot:chests/regirock_chest` 5.59%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:psychic_seed` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:quick_ball` | Specialty ball | `crate:common` 3.1%; `market` |
| `cobblemon:quick_claw` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:quick_powder` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:quiet_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:rash_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:razor_claw` | Standard held / utility item | `crate:common` 0.203%; `market` |
| `cobblemon:razor_fang` | Standard held / utility item | `crate:common` 0.203%; `market` |
| `cobblemon:red_apricorn` | Apricorn | `loot:uncommon/nature` |
| `cobblemon:red_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:red_card` | Standard held / utility item | `market` |
| `cobblemon:red_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:red_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:relaxed_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:resist_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:rich_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:ring_target` | Standard held / utility item | `market` |
| `cobblemon:room_service` | Standard held / utility item | `market` |
| `cobblemon:safety_goggles` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:sail_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:sandy_mulch` | Mulch | `loot:uncommon/nature` |
| `cobblemon:sassy_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:scope_lens` | Standard held / utility item | `market` |
| `cobblemon:serious_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:sharp_beak` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:shed_shell` | Standard held / utility item | `market` |
| `cobblemon:shell_bell` | Standard held / utility item | `market` |
| `cobblemon:silk_scarf` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/regigigas_chest` 69.14% +1 more |
| `cobblemon:silver_powder` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:sitrus_berry` | Berry | *not currently granted anywhere* |
| `cobblemon:skull_fossil` | Fossil | `loot:legendary/archeology` |
| `cobblemon:sky_tumblestone` | Crafting material | `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:small_budding_sky_tumblestone` | Crafting material | `loot:legendary/archeology` |
| `cobblemon:smoke_ball` | Standard held / utility item | `loot:rare/battle`; `market` |
| `cobblemon:smooth_rock` | Standard held / utility item | `loot:rare/battle`; `loot:chests/regirock_chest` 29.58%; `market` |
| `cobblemon:soft_sand` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:soothe_bell` | Standard held / utility item | `loot:chests/bell_tower_chest` 9.07%; `market` |
| `cobblemon:spell_tag` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `loot:chests/turnback_cave_chest` 11.36% +2 more |
| `cobblemon:sticky_barb` | Standard held / utility item | `market` |
| `cobblemon:sweet_apple` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:swift_feather` | EV feather | `loot:chests/bell_tower_chest` 62.5%; `loot:chests/turnback_cave_chest` 33.52% |
| `cobblemon:syrupy_apple` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:tart_apple` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:terrain_extender` | Standard held / utility item | `market` |
| `cobblemon:timer_ball` | Specialty ball | `crate:common` 4.6%; `loot:chests/regigigas_chest` 99.11%; `market` |
| `cobblemon:timid_mint` | Nature mint | `crate:common` 0.195% |
| `cobblemon:toxic_orb` | Standard held / utility item | `loot:legendary/battle`; `market` |
| `cobblemon:tumblestone` | Standard held / utility item | `loot:chests/turnback_cave_vault` 10.75% |
| `cobblemon:twisted_spoon` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:unremarkable_teacup` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:upgrade` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:utility_umbrella` | Standard held / utility item | `market` |
| `cobblemon:vivichoke` | Standard held / utility item | `loot:epic/nature` |
| `cobblemon:white_apricorn` | Apricorn | `loot:uncommon/nature` |
| `cobblemon:white_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:white_herb` | Standard held / utility item | `loot:uncommon/battle`; `loot:uncommon/nature`; `market` |
| `cobblemon:white_mint_leaf` | Nature mint | `loot:epic/nature` |
| `cobblemon:white_mint_seeds` | Mint seed | `loot:epic/nature` |
| `cobblemon:wide_lens` | Standard held / utility item | `market` |
| `cobblemon:wise_glasses` | Standard held / utility item | `crate:common` 0.305%; `loot:rare/battle`; `market` |
| `cobblemon:yellow_apricorn_seed` | Apricorn | `loot:epic/nature` |
| `cobblemon:zinc` | Vitamin | `crate:common` 1.183%; `loot:chests/regice_chest` 8.25%; `loot:chests/turnback_cave_chest` 11.36% |
| `cobblemon:zoom_lens` | Standard held / utility item | `market` |
| `minecraft:diamond` | Common enough at this point in progression | `loot:epic/diverse`; `loot:chests/woodland_mansion` 0.0%; `loot:chests/bell_tower_chest` 9.07% +4 more |

## T0 — Common

*Filler. Safe to hand out in bulk.*

| Item | Why | Where it comes from |
|---|---|---|
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
| `mega_showdown:buginium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:darkinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:decidium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:dragon_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:dragonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:eevium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:electric_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:electrium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:fairium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:fightinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:fire_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:firium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:flyinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:ghost_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:ghostium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:grassium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:groundium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:ice_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:icium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:incinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:kommonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:lunalium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:lycanium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:marshadium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:mewnium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:mimikium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:normal_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:normalium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:pikanium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:pikashunium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:poisonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:primarium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:psychium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:rock_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:rockium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:rusted_shield` | Zamazenta-Crowned gate — craft banned by server-craft-bans | *not currently granted anywhere* |
| `mega_showdown:rusted_sword` | Zacian-Crowned gate — craft banned by server-craft-bans | *not currently granted anywhere* |
| `mega_showdown:snorlium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:solganium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:steel_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:steelium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:tapunium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:ultranecrozium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:water_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:waterium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |

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

