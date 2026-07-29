# Loot tiers

Canonical rarity ladder for every item the server hands out. Consult this
when designing a new game, quest, crate, or reward so payouts stay consistent
with what already exists.

!!! info "Draft — under review"

    These tiers are a first pass and are being verified. If something looks
    mis-ranked, say so — especially anything you actually play around.

    A tier is what an item *should* be worth. The **Where it comes from**
    column is what currently grants it, and the two disagreeing is exactly
    the kind of thing worth reporting.

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
| **TX** | Disabled | Never award. Two different situations live here, and the per-item status says which: **not obtainable** (recipe banned and/or stripped from loot — if one of these is still dropping, that's a bug to fix, not a tier to change), or **banned to use** (freely obtainable, but the mechanic is disabled and using it is a bannable offence, so the item is worthless as a reward). |

1175 items tiered — T5: 4, T4: 38, T3: 120, T2: 220, T1: 540, T0: 159, TX: 94.

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
| `cobblemon:master_ball` | Guaranteed catch. Ultra crate only, 2x at 6.5%. | `crate:rare` 0.7%; `crate:ultra` 6.5%; `mod:rctmod/generic/unique/pokeballs` +1 more |
| `legendarymonuments:azure_flute` | Opens the Hall of Origin — ARCEUS. Ultra crate jackpot at 0.8%. | `crate:ultra` 0.8%; `craft` |
| `legendarymonuments:celestica_flute` | Crafts into the Azure Flute. Same gate, one step removed. | `loot:legendarymonuments/chests/turnback_cave_vault` 1.11% |
| `minecraft:totem_of_undying` | Zacian summon gate. Ultra crate 1.6% is the only intended source. | `crate:ultra` 1.6% |

## T4 — Legendary

*Summons or permanently unlocks a legendary/forme. One-per-player scale.*

| Item | Why | Where it comes from |
|---|---|---|
| `legendarymonuments:antimatter_globe` | Azure Flute component — the Arceus path | *not currently granted anywhere* |
| `legendarymonuments:arctic_stone` | Summons a legendary (bird / Heatran) | *not currently granted anywhere* |
| `legendarymonuments:curry_of_justice` | Summons Keldeo | `crate:ultra` 6.5%; `craft` |
| `legendarymonuments:darkstone` | Summons Zekrom | `crate:ultra` 4.9%; `craft` |
| `legendarymonuments:entei_treat` | Summons a legendary at its shrine | `craft` |
| `legendarymonuments:fullmoon_whistle` | Summons Cresselia | `crate:ultra` 6.5%; `craft` |
| `legendarymonuments:griseous_key` | Summons Giratina | `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% |
| `legendarymonuments:gs_ball` | Summons a mythical | `craft` |
| `legendarymonuments:latias_treat` | Summons a legendary at its shrine | `craft` |
| `legendarymonuments:latios_treat` | Summons a legendary at its shrine | `craft` |
| `legendarymonuments:liberty_pass` | Summons Victini | `crate:ultra` 6.5% |
| `legendarymonuments:lightstone` | Summons Reshiram | `crate:ultra` 4.9%; `craft` |
| `legendarymonuments:magma_stone` | Summons a legendary (bird / Heatran) | `craft` |
| `legendarymonuments:molten_stone` | Summons a legendary (bird / Heatran) | *not currently granted anywhere* |
| `legendarymonuments:newmoon_whistle` | Summons Darkrai | `crate:ultra` 6.5%; `craft` |
| `legendarymonuments:old_sea_map` | Summons Mew | `loot:legendarymonuments/chests/liberty_island_chest` 12.32% |
| `legendarymonuments:proof_of_conquest_a` | Summons Azelf | `crate:ultra` 4.9% |
| `legendarymonuments:proof_of_conquest_m` | Summons Mesprit | `crate:ultra` 4.9% |
| `legendarymonuments:proof_of_conquest_u` | Summons Uxie | `crate:ultra` 4.9% |
| `legendarymonuments:raikou_treat` | Summons a legendary at its shrine | `craft` |
| `legendarymonuments:rainbow_feather` | Summons Ho-Oh | `crate:ultra` 4.9%; `craft` |
| `legendarymonuments:red_chain` | Summons Dialga & Palkia | `crate:ultra` 4.9%; `craft` |
| `legendarymonuments:space_globe` | Azure Flute component — the Arceus path | *not currently granted anywhere* |
| `legendarymonuments:suicune_treat` | Summons a legendary at its shrine | `craft` |
| `legendarymonuments:time_globe` | Azure Flute component — the Arceus path | *not currently granted anywhere* |
| `legendarymonuments:titan_key` | Titan encounter gate | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:tuft_of_mew_hair` | Summons a mythical | *not currently granted anywhere* |
| `legendarymonuments:vortex_stone` | Summons Lugia | `crate:ultra` 4.9%; `craft` |
| `legendarymonuments:zap_stone` | Summons a legendary (bird / Heatran) | *not currently granted anywhere* |
| `mega_showdown:adamant_crystal` | Dialga Origin forme unlock | `loot:legendarymonuments/chests/turnback_cave_vault` 0.11% |
| `mega_showdown:ash_cap` | Uncraftable — craft banned by server-craft-bans; Ultra crate only | *not currently granted anywhere* |
| `mega_showdown:blue_orb` | Primal Kyogre | `loot:legendarymonuments/chests/lugia_temple_chest` 0.57% |
| `mega_showdown:griseous_core` | Giratina Origin forme unlock | `loot:legendarymonuments/chests/turnback_cave_vault` 0.11% |
| `mega_showdown:lustrous_globe` | Palkia Origin forme unlock | `loot:legendarymonuments/chests/turnback_cave_vault` 0.11% |
| `mega_showdown:red_orb` | Primal Groudon | `loot:legendarymonuments/chests/bell_tower_chest` 0.59% |
| `mega_showdown:rusted_shield` | Zamazenta-Crowned gate. Craft banned — intended to be chest loot instead. NOT YET PLACED IN ANY CHEST. | *not currently granted anywhere* |
| `mega_showdown:rusted_sword` | Zacian-Crowned gate. Craft banned (was iron_sword + netherite_scrap + fire_charge, far too cheap) — intended to be chest loot instead. NOT YET PLACED IN ANY CHEST. | *not currently granted anywhere* |
| `mega_showdown:zygarde_core` | Zygarde assembly core | `crate:rare` 2.9%; `loot:legendarymonuments/chests/regigigas_chest` 16.94%; `loot:legendarymonuments/chests/registeel_chest` 6.87% |

## T3 — Epic

*Permanent competitive power or a hard-gated component. A real chase reward.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:ability_patch` | Hidden ability. Deliberately pulled from the Ultra crate. | `crate:rare` 7.2%; `loot:legendarymonuments/chests/registeel_chest` 6.87%; `mod:rctmod/generic/epic/training` +1 more |
| `cobblemon:beast_ball` | Best-in-slot for Ultra Beasts, not purchasable | `loot:rctmod/generic/legendary/pokeballs`; `craft` |
| `cobblemon:bug_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:cobblemon/ruins/common/rooted_arch_ruins`; `mod:cobblemon/sets/any_type_gem` |
| `cobblemon:courage_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95%; `craft` |
| `cobblemon:dark_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `loot:mega_showdown/archaeology/ruins` 22.73% +4 more |
| `cobblemon:dragon_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/dragoeleki_chest` 74.23%; `mod:cobbleworkers/dive_treasure` 20.0% +3 more |
| `cobblemon:electric_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/dragoeleki_chest` 74.23%; `mod:cobblemon/ruins/common/unstable_cave_ruins` +1 more |
| `cobblemon:fairy_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:cobblemon/ruins/common/toppled_pillars_ruins`; `mod:cobblemon/sets/any_type_gem` |
| `cobblemon:fighting_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:cobblemon/ruins/common/deserted_town_center_ruins`; `mod:cobblemon/sets/any_type_gem` |
| `cobblemon:fire_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `loot:legendarymonuments/chests/liberty_island_chest` 39.31% +4 more |
| `cobblemon:flying_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `mod:cobblemon/ruins/common/deserted_tower_ruins` +1 more |
| `cobblemon:ghost_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75% +4 more |
| `cobblemon:grass_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:cobblemon/ruins/common/rooted_arch_ruins`; `mod:cobblemon/sets/any_type_gem` |
| `cobblemon:ground_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:cobblemon/ruins/common/deserted_tower_ruins`; `mod:cobblemon/ruins/common/fallen_statue_ruins` +1 more |
| `cobblemon:health_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95%; `craft` |
| `cobblemon:held_item_voucher` | Redeems for a premium held item | `crate:rare` 2.9% |
| `cobblemon:ice_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/regice_chest` 85.07%; `mod:cobblemon/ruins/common/hidden_bunker_ruins` +1 more |
| `cobblemon:mighty_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95%; `craft` |
| `cobblemon:normal_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:mega_showdown/chests/observatory_barrel_3` 59.22%; `mod:cobblemon/ruins/common/deserted_house_ruins` +1 more |
| `cobblemon:poison_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:cobblemon/ruins/common/mossy_oubliette_ruins`; `mod:cobblemon/sets/any_type_gem` |
| `cobblemon:psychic_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/liberty_island_chest` 39.31%; `mod:cobblemon/ruins/common/crumbling_arch_ruins` +3 more |
| `cobblemon:quick_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95%; `craft` |
| `cobblemon:rock_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/regirock_chest` 70.66%; `mod:cobblemon/ruins/common/decaying_crypt_ruins` +4 more |
| `cobblemon:smart_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95%; `craft` |
| `cobblemon:steel_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:legendarymonuments/chests/heatran_cave_chest` 12.01%; `mod:cobblemon/ruins/common/hidden_bunker_ruins` +1 more |
| `cobblemon:tough_candy` | IV candy — raises a stat's effective IV by 1; heavy player demand | `crate:rare` 0.95%; `craft` |
| `cobblemon:tr_voucher` | Redeems for a TR | `crate:rare` 3.9% |
| `cobblemon:water_gem` | Type gem — one-shot damage boost | `loot:rctmod/generic/legendary/battle`; `mod:cobbleworkers/dive_treasure` 20.0%; `mod:cobblemon/ruins/common/deserted_tower_ruins` +3 more |
| `gacha:ultra_key` | Ultra Crate key — minecraft:nether_star + gacha_key tag. Not obtainable outside quests and events. | `crate:rare` 0.7%; `crate:ultra` 0.8% |
| `legendarymonuments:azelf_fang` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `legendarymonuments:darkstone_shard` | 9 craft into a T4 Dark Stone | `loot:legendarymonuments/chests/bell_tower_chest` 9.07%; `loot:legendarymonuments/chests/liberty_island_chest` 12.32%; `loot:legendarymonuments/chests/lugia_temple_chest` 20.5% |
| `legendarymonuments:distortion_button` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_button` 100.0%; `craft` |
| `legendarymonuments:distortion_cobblestone` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_cobblestone` |
| `legendarymonuments:distortion_cobblestone_bricks` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_cobblestone_bricks` 100.0%; `craft` |
| `legendarymonuments:distortion_cobblestone_bricks_slab` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_cobblestone_bricks_slab` 100.0%; `craft` |
| `legendarymonuments:distortion_cobblestone_bricks_stairs` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_cobblestone_bricks_stairs` 100.0%; `craft` |
| `legendarymonuments:distortion_cobblestone_bricks_wall` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_cobblestone_bricks_wall` 100.0%; `craft` |
| `legendarymonuments:distortion_crystal` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_crystal` 100.0% |
| `legendarymonuments:distortion_crystal_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_crystal_block` 100.0% |
| `legendarymonuments:distortion_deepslate` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_deepslate` |
| `legendarymonuments:distortion_deepslate_bricks` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_deepslate_bricks` 100.0%; `craft` |
| `legendarymonuments:distortion_deepslate_bricks_slab` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_deepslate_bricks_slab` 100.0%; `craft` |
| `legendarymonuments:distortion_deepslate_bricks_stairs` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_deepslate_bricks_stairs` 100.0%; `craft` |
| `legendarymonuments:distortion_deepslate_bricks_wall` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_deepslate_bricks_wall` 100.0%; `craft` |
| `legendarymonuments:distortion_door` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_door` 100.0%; `craft` |
| `legendarymonuments:distortion_fence` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_fence` 100.0%; `craft` |
| `legendarymonuments:distortion_fence_gate` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_fence_gate` 100.0%; `craft` |
| `legendarymonuments:distortion_hanging_sign` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_hanging_sign` 100.0%; `mod:legendarymonuments/blocks/distortion_wall_hanging_sign` 100.0%; `craft` |
| `legendarymonuments:distortion_log` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_log` |
| `legendarymonuments:distortion_planks` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_planks` 100.0%; `craft` |
| `legendarymonuments:distortion_portal` | Monument item — treat as gated until pinned otherwise | `craft` |
| `legendarymonuments:distortion_sapling` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_sapling` 100.0% |
| `legendarymonuments:distortion_sign` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_sign` 100.0%; `mod:legendarymonuments/blocks/distortion_wall_sign` 100.0%; `craft` |
| `legendarymonuments:distortion_slab` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_slab` 100.0%; `craft` |
| `legendarymonuments:distortion_stairs` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_stairs` 100.0%; `craft` |
| `legendarymonuments:distortion_stone` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_stone` |
| `legendarymonuments:distortion_trapdoor` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/distortion_trapdoor` 100.0%; `craft` |
| `legendarymonuments:dragon_golem_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/dragon_golem_block`; `craft` |
| `legendarymonuments:dragon_golem_key` | Regidrago gate | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:dream_catcher` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/dream_catcher` 100.0%; `craft` |
| `legendarymonuments:dyna_apple` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:electric_golem_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/electric_golem_block`; `craft` |
| `legendarymonuments:electric_golem_key` | Chamber / monument gate | `craft` |
| `legendarymonuments:firescourge_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:fragmented_red_chain` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `legendarymonuments:galar_particle_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/galar_particle_block` |
| `legendarymonuments:galarian_torch` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/galarian_torch`; `mod:legendarymonuments/blocks/galarian_wall_torch`; `craft` |
| `legendarymonuments:galarian_urn_of_embers` | Legendary-adjacent gate component | `craft` |
| `legendarymonuments:galarian_urn_of_embers_block` | Legendary-adjacent gate component | `mod:legendarymonuments/blocks/galarian_urn_of_embers_block`; `craft` |
| `legendarymonuments:galarian_urn_of_frost` | Legendary-adjacent gate component | `craft` |
| `legendarymonuments:galarian_urn_of_frost_block` | Legendary-adjacent gate component | `mod:legendarymonuments/blocks/galarian_urn_of_frost_block`; `craft` |
| `legendarymonuments:galarian_urn_of_storms` | Legendary-adjacent gate component | `craft` |
| `legendarymonuments:galarian_urn_of_storms_block` | Legendary-adjacent gate component | `mod:legendarymonuments/blocks/galarian_urn_of_storms_block`; `craft` |
| `legendarymonuments:grasswither_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:groundblight_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:heroshield` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:herosword` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:ice_golem_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/ice_golem_block`; `craft` |
| `legendarymonuments:ice_golem_key` | Regice gate | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:icerend_seal` | Locates a shrine | *not currently granted anywhere* |
| `legendarymonuments:idealsbottle` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:lightstone_shard` | 9 craft into a T4 Light Stone | `loot:legendarymonuments/chests/bell_tower_chest` 9.07%; `loot:legendarymonuments/chests/liberty_island_chest` 12.32%; `loot:legendarymonuments/chests/lugia_temple_chest` 20.5% |
| `legendarymonuments:lugia_key` | Chamber / monument gate | *not currently granted anywhere* |
| `legendarymonuments:meltan_box` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/meltan_box`; `craft` |
| `legendarymonuments:mesprit_plume` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `legendarymonuments:nightmare_essence` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:ominous_griseous_key` | Chamber / monument gate | *not currently granted anywhere* |
| `legendarymonuments:origin_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/origin_block` 100.0%; `craft` |
| `legendarymonuments:origin_ingot` | Repairs the Red Chain — component for a T4 | `loot:legendarymonuments/chests/turnback_cave_vault` 1.11%; `craft` |
| `legendarymonuments:raw_origin` | Monument item — treat as gated until pinned otherwise | `craft` |
| `legendarymonuments:regice_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:regidrago_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:regieleki_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:regirock_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:registeel_tablet` | Regi chamber gate | *not currently granted anywhere* |
| `legendarymonuments:rock_golem_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/rock_golem_block`; `craft` |
| `legendarymonuments:rock_golem_key` | Regirock gate | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:sacred_ash` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:silver_wing` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:steel_golem_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/steel_golem_block`; `craft` |
| `legendarymonuments:steel_golem_key` | Registeel gate | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:suitcase_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/suitcase_block` 100.0%; `craft` |
| `legendarymonuments:temple_key` | Chamber / monument gate | *not currently granted anywhere* |
| `legendarymonuments:titan_core` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:titan_hammer` | Monument item — treat as gated until pinned otherwise | `craft` |
| `legendarymonuments:truthbottle` | Monument item — treat as gated until pinned otherwise | *not currently granted anywhere* |
| `legendarymonuments:urn_of_embers` | Legendary-adjacent gate component | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:urn_of_embers_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/urn_of_embers_block`; `craft` |
| `legendarymonuments:urn_of_frost` | Legendary-adjacent gate component | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:urn_of_frost_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/urn_of_frost_block`; `craft` |
| `legendarymonuments:urn_of_storms` | Legendary-adjacent gate component | `crate:rare` 2.8%; `craft` |
| `legendarymonuments:urn_of_storms_block` | Monument item — treat as gated until pinned otherwise | `mod:legendarymonuments/blocks/urn_of_storms_block`; `craft` |
| `legendarymonuments:uxie_claw` | Red Chain component — crafts into a T4 | *not currently granted anywhere* |
| `mega_showdown:adamant_orb` | Origin-forme held item | `loot:legendarymonuments/chests/turnback_cave_vault` 0.56%; `craft` |
| `mega_showdown:griseous_orb` | Origin-forme held item | `crate:rare` 4.0%; `loot:legendarymonuments/chests/turnback_cave_vault` 0.56%; `craft` |
| `mega_showdown:keystone` | Mega evolution enabler | `crate:common` 1.9%; `craft` |
| `mega_showdown:lustrous_orb` | Origin-forme held item | `loot:legendarymonuments/chests/turnback_cave_vault` 0.56%; `craft` |
| `mega_showdown:mega_stone` | Mega evolution enabler | `crate:common` 1.9% |
| `mega_showdown:zygarde_cell` | Zygarde assembly component | `loot:legendarymonuments/chests/dragoeleki_chest` 32.01%; `loot:legendarymonuments/chests/regice_chest` 41.11%; `loot:legendarymonuments/chests/regigigas_chest` 16.94% +7 more |
| `minecraft:nether_star` | Base item for the Ultra Crate key — awarding one reads as awarding a key, even though an untagged star will not open a crate | *not currently granted anywhere* |

## T2 — Rare

*Strong but repeatable. Fine as the headline reward for a genuine challenge.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:ability_capsule` | Swaps between normal abilities | `loot:legendarymonuments/chests/registeel_chest` 78.61%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36%; `mod:rctmod/generic/epic/training` +1 more |
| `cobblemon:ability_shield` | Premium competitive held item | `loot:rctmod/generic/rare/battle`; `mod:cobblemon/ruins/uncommon/deserted_town_center_ruins`; `mod:cobblemon/ruins/uncommon/fallen_statue_ruins` +6 more |
| `cobblemon:air_balloon` | Premium competitive held item | `loot:rctmod/generic/rare/battle`; `craft`; `market` |
| `cobblemon:apricorn_pressure_plate` | Arceus plate / type booster | `mod:cobblemon/blocks/apricorn_pressure_plate`; `craft` |
| `cobblemon:assault_vest` | Premium competitive held item | `crate:common` 0.5%; `mod:rctmod/generic/epic/battle`; `craft` +1 more |
| `cobblemon:auspicious_armor` | Evolution item | `crate:common` 0.203%; `loot:legendarymonuments/chests/bell_tower_chest` 9.07%; `mod:cobblemon/injection/chests/bastion_bridge` 100.0% +4 more |
| `cobblemon:black_augurite` | Evolution item | `crate:common` 0.203%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_eroded_pillar` +2 more |
| `cobblemon:blunder_policy` | Premium competitive held item | `loot:rctmod/generic/rare/battle`; `mod:cobblemon/blocks/blunder_policy`; `craft` +1 more |
| `cobblemon:booster_energy` | Premium competitive held item | *not currently granted anywhere* |
| `cobblemon:chipped_pot` | Evolution item | `crate:common` 0.203%; `mod:cobblemon/ruins/uncommon/decaying_crypt_ruins`; `mod:cobblemon/ruins/uncommon/deserted_tower_ruins` +4 more |
| `cobblemon:choice_band` | Premium competitive held item | `crate:common` 0.5%; `loot:rctmod/generic/legendary/battle`; `craft` +1 more |
| `cobblemon:choice_scarf` | Premium competitive held item | `crate:common` 0.5%; `loot:rctmod/generic/legendary/battle`; `craft` +1 more |
| `cobblemon:choice_specs` | Premium competitive held item | `crate:common` 0.5%; `loot:rctmod/generic/legendary/battle`; `craft` +1 more |
| `cobblemon:clear_amulet` | Premium competitive held item | `mod:mega_showdown/sets/any_showdown_held_item`; `craft`; `market` |
| `cobblemon:covert_cloak` | Premium competitive held item | `mod:cobblemon/ruins/uncommon/deserted_town_center_ruins`; `mod:cobblemon/ruins/uncommon/gimmi_tower_frozen`; `mod:cobblemon/ruins/uncommon/gimmi_tower_junk` +5 more |
| `cobblemon:cracked_pot` | Evolution item | `crate:common` 0.203%; `mod:cobblemon/ruins/uncommon/decaying_crypt_ruins`; `mod:cobblemon/ruins/uncommon/deserted_tower_ruins` +4 more |
| `cobblemon:deep_sea_scale` | Evolution item | `crate:common` 0.203%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fishing/pokerod_treasure` +5 more |
| `cobblemon:deep_sea_tooth` | Evolution item | `crate:common` 0.203%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fishing/pokerod_treasure` +5 more |
| `cobblemon:dragon_scale` | Evolution item | `crate:common` 0.203%; `mod:cobblemon/fishing/pokerod_treasure`; `mod:cobblemon/shipwreck_coves/gilded_chests/big_treasure` +1 more |
| `cobblemon:dream_ball` | Specialty ball — Hidden Ability transfer | `crate:common` 4.6%; `loot:rctmod/generic/legendary/pokeballs`; `craft` +1 more |
| `cobblemon:dubious_disc` | Evolution item | `crate:common` 0.203%; `mod:rctmod/generic/legendary/evolution`; `craft` |
| `cobblemon:electirizer` | Evolution item | `crate:common` 0.203%; `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:eviolite` | Premium competitive held item | `crate:common` 0.5%; `mod:rctmod/generic/epic/battle`; `craft` +1 more |
| `cobblemon:exp_candy_l` | Feedstock for IV candies — heavy consumption keeps it scarce despite being on the shelf | `craft`; `market` |
| `cobblemon:exp_candy_xl` | Feedstock for IV candies — heavy consumption keeps it scarce despite being on the shelf | `craft`; `market` |
| `cobblemon:expert_belt` | Premium competitive held item | `mod:cobblemon/sets/any_ancient_held_item`; `mod:rctmod/generic/epic/battle`; `craft` +1 more |
| `cobblemon:flame_plate` | Arceus plate / type booster | *not currently granted anywhere* |
| `cobblemon:focus_sash` | Premium competitive held item | `crate:common` 0.5%; `loot:rctmod/generic/uncommon/battle`; `mod:cobblemon/ruins/uncommon/deserted_town_center_ruins` +7 more |
| `cobblemon:galarica_cuff` | Evolution item | `crate:common` 0.203%; `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:galarica_wreath` | Evolution item | `crate:common` 0.203%; `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:heavy_duty_boots` | Premium competitive held item | `crate:common` 0.5%; `loot:rctmod/generic/rare/battle`; `craft` +1 more |
| `cobblemon:leftovers` | Premium competitive held item | `crate:common` 0.305%; `loot:rctmod/generic/legendary/battle`; `market` |
| `cobblemon:life_orb` | Premium competitive held item | `crate:common` 0.5%; `loot:rctmod/generic/legendary/battle`; `craft` +1 more |
| `cobblemon:link_cable` | Evolution item | `loot:legendarymonuments/chests/turnback_cave_chest` 11.36%; `mod:cobblemon/injection/chests/ancient_city` 100.0%; `mod:cobblemon/injection/chests/end_city_treasure` 100.0% +5 more |
| `cobblemon:loaded_dice` | Premium competitive held item | `mod:rctmod/generic/epic/battle`; `craft`; `market` |
| `cobblemon:magmarizer` | Evolution item | `crate:common` 0.203%; `mod:legendarymonuments/chests/heatran_cave_chest` 12.01%; `mod:rctmod/generic/epic/evolution` +1 more |
| `cobblemon:malicious_armor` | Evolution item | `crate:common` 0.203%; `mod:cobblemon/injection/chests/nether_bridge` 100.0%; `mod:rctmod/generic/epic/evolution` |
| `cobblemon:masterpiece_teacup` | Evolution item | `crate:common` 0.203%; `mod:cobblemon/ruins/uncommon/crumbling_arch_ruins`; `mod:cobblemon/ruins/uncommon/deserted_house_ruins` +4 more |
| `cobblemon:metal_alloy` | Evolution item | `crate:common` 0.203% |
| `cobblemon:mirror_herb` | Premium competitive held item | `loot:rctmod/generic/uncommon/battle`; `loot:rctmod/generic/uncommon/nature`; `mod:cobbleworkers/archaeology_treasure` 3.45% +6 more |
| `cobblemon:oval_stone` | Evolution item | `crate:common` 0.203%; `mod:rctmod/generic/epic/evolution` |
| `cobblemon:prism_scale` | Evolution item | `crate:common` 0.203%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fishing/pokerod_treasure` +4 more |
| `cobblemon:protector` | Evolution item | `crate:common` 0.203%; `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/regirock_chest` 70.66% +6 more |
| `cobblemon:punching_glove` | Premium competitive held item | `craft`; `market` |
| `cobblemon:rare_candy` | Instant level. Purchasable, but heavy demand keeps it scarce. | `crate:common` 5.1%; `crate:rare` 12.0%; `loot:rctmod/generic/epic/medicine` +4 more |
| `cobblemon:reaper_cloth` | Evolution item | `crate:common` 0.203%; `loot:legendarymonuments/chests/turnback_cave_vault` 5.48%; `mod:rctmod/generic/epic/evolution` +1 more |
| `cobblemon:rocky_helmet` | Premium competitive held item | `crate:common` 0.5%; `mod:rctmod/generic/epic/battle`; `craft` +1 more |
| `cobblemon:saccharine_pressure_plate` | Arceus plate / type booster | `mod:cobblemon/blocks/saccharine_pressure_plate`; `craft` |
| `cobblemon:sachet` | Evolution item | `crate:common` 0.203%; `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:throat_spray` | Premium competitive held item | `craft`; `market` |
| `cobblemon:weakness_policy` | Premium competitive held item | `mod:cobblemon/blocks/weakness_policy`; `mod:rctmod/generic/epic/battle`; `craft` +1 more |
| `cobblemon:whipped_dream` | Evolution item | `crate:common` 0.203%; `mod:rctmod/generic/epic/evolution`; `craft` |
| `gacha:pokemon_key` | Pokemon Crate key — minecraft:turtle_egg + gacha_key tag | *not currently granted anywhere* |
| `gacha:rare_key` | Rare Crate key — minecraft:ominous_trial_key + gacha_key tag | `crate:common` 0.5%; `crate:rare` 1.4% |
| `legendarymonuments:ancient_rubble_ore` | Ore block, mid-tier crafting material | `loot:legendarymonuments/chests/regigigas_chest` 16.94%; `mod:legendarymonuments/blocks/ancient_rubble_ore` 100.0% |
| `legendarymonuments:distortion_pressure_plate` | Arceus plate / type booster | `mod:legendarymonuments/blocks/distortion_pressure_plate` 100.0%; `craft` |
| `legendarymonuments:dragon_golem_ingot` | Golem crafting material | `craft` |
| `legendarymonuments:dragon_pauldron` | Regi armour component | `craft` |
| `legendarymonuments:electric_golem_ingot` | Golem crafting material | `craft` |
| `legendarymonuments:electric_pauldron` | Regi armour component | `craft` |
| `legendarymonuments:golem_scrap` | Golem crafting material — a component, not a gate | `loot:legendarymonuments/chests/turnback_cave_chest` 1.98%; `loot:legendarymonuments/chests/turnback_cave_vault` 2.22%; `craft` |
| `legendarymonuments:ice_golem_ingot` | Golem crafting material | `craft` |
| `legendarymonuments:ice_pauldron` | Regi armour component | `craft` |
| `legendarymonuments:rock_golem_ingot` | Golem crafting material | `craft` |
| `legendarymonuments:rock_pauldron` | Regi armour component | `craft` |
| `legendarymonuments:sandstone_pressure_plate` | Arceus plate / type booster | `mod:legendarymonuments/blocks/sandstone_pressure_plate` 100.0%; `craft` |
| `legendarymonuments:steel_golem_ingot` | Golem crafting material | `craft` |
| `legendarymonuments:steel_pauldron` | Regi armour component | `craft` |
| `legendarymonuments:titan_pauldron` | Regi armour component | `craft` |
| `mega_showdown:abomasite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:absolite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:adrenaline_orb` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/sets/any_showdown_held_item`; `craft` |
| `mega_showdown:aerodactylite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:aggronite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:alakazite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:altarianite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:ampharosite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:archie_anchor` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:audinite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:banettite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:beedrillite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:blastoisinite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:blazikenite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:booster_energy` | Premium competitive held item | `craft` |
| `mega_showdown:brendan_mega_cuff` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:bug_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:burn_drive` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:cameruptite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:charizardite_x` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:charizardite_y` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:chill_drive` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:chiseled_mega_meteorid_block` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/chiseled_mega_meteorid_block`; `craft` |
| `mega_showdown:chiseled_mega_meteorid_brick` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/chiseled_mega_meteorid_brick`; `craft` |
| `mega_showdown:cornerstone_mask` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:dark_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:deoxys_meteorite` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/deoxys_meteorite` |
| `mega_showdown:diancite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:dna_splicer` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:dormant_crystal` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/dormant_crystal` |
| `mega_showdown:douse_drive` | Mega Showdown item (type/forme adjacent) | `craft` |
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
| `mega_showdown:galladite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:garchompite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:gardevoirite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:gengarite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:ghost_memory` | Silvally memory | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75% |
| `mega_showdown:glalitite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:gracidea_flower` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/gracidea_flower`; `mod:mega_showdown/blocks/potted_gracidea` |
| `mega_showdown:grass_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:ground_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:gyaradosite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:hearthflame_mask` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:heracronite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:houndoominite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:ice_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:icicle_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:insect_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:iron_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:kangaskhanite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:keystone_block` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/keystone_block`; `craft` |
| `mega_showdown:korrina_glove` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:latiasite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:latiosite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:legend_plate` | Arceus plate / type booster | *not currently granted anywhere* |
| `mega_showdown:likos_pendant` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:lisia_mega_tiara` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:lopunnite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:lucarionite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:lysandre_ring` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:manectite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mawilite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:maxie_glasses` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:may_bracelet` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:meadow_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:medichamite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_bracelet` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_bracelet_black` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_bracelet_blue` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_bracelet_green` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_bracelet_pink` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_bracelet_red` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_bracelet_yellow` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mega_meteorid_block` | Mega Showdown item (type/forme adjacent) | `loot:mega_showdown/archaeology/ruins` 4.55%; `mod:mega_showdown/chests/observatory_barrel` 59.22%; `mod:mega_showdown/chests/observatory_barrel_3` 59.22% +1 more |
| `mega_showdown:mega_meteorid_brick` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/mega_meteorid_brick`; `craft` |
| `mega_showdown:mega_meteorid_radiated_block` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/mega_meteorid_radiated_block` |
| `mega_showdown:mega_ring` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:meltan` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:metagrossite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mewtwonite_x` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mewtwonite_y` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:mind_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:n_lunarizer` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:n_solarizer` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:omni_ring` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:pedestal` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/pedestal`; `craft` |
| `mega_showdown:pidgeotite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:pika_case` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:pink_nectar` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:pinsirite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:pixie_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:poison_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:polished_mega_meteorid_block` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/polished_mega_meteorid_block`; `craft` |
| `mega_showdown:power_spot` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/power_spot`; `craft` |
| `mega_showdown:prison_bottle` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:psychic_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:purple_nectar` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:reassembly_unit` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/reassembly_unit`; `craft` |
| `mega_showdown:red_nectar` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:reins_of_unity` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:reveal_glass` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:rock_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:rotom_catalogue` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:rotom_fan` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/rotom_fan`; `craft` |
| `mega_showdown:rotom_fridge` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/rotom_fridge`; `craft` |
| `mega_showdown:rotom_mow` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/rotom_mow`; `craft` |
| `mega_showdown:rotom_oven` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/rotom_oven`; `craft` |
| `mega_showdown:rotom_washing_machine` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/blocks/rotom_washing_machine`; `craft` |
| `mega_showdown:sablenite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:salamencite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:sceptilite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:scizorite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:sharpedonite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:shock_drive` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:sky_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:slowbronite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:soul_dew` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/sets/any_showdown_held_item`; `craft` |
| `mega_showdown:sparkling_stone_dark` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/archaeological_site/archaeological_site_rare` 16.67% |
| `mega_showdown:sparkling_stone_light` | Mega Showdown item (type/forme adjacent) | `mod:mega_showdown/archaeological_site/archaeological_site_rare` 83.33% |
| `mega_showdown:splash_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:spooky_plate` | Arceus plate / type booster | `loot:legendarymonuments/chests/turnback_cave_vault` 2.22%; `market` |
| `mega_showdown:star_core` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:steel_memory` | Silvally memory | `loot:legendarymonuments/chests/registeel_chest` 6.87% |
| `mega_showdown:steelixite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:stone_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:swampertite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:toxic_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:tyranitarite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:venusaurite` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:water_memory` | Silvally memory | *not currently granted anywhere* |
| `mega_showdown:wellspring_mask` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:wishing_star` | Mega Showdown item (type/forme adjacent) | *not currently granted anywhere* |
| `mega_showdown:yellow_nectar` | Mega Showdown item (type/forme adjacent) | `craft` |
| `mega_showdown:zap_plate` | Arceus plate / type booster | `market` |
| `mega_showdown:zygarde_cube` | Mega Showdown item (type/forme adjacent) | `craft` |
| `minecraft:ancient_debris` | Netherite path | *not currently granted anywhere* |
| `minecraft:beacon` | $50,000 — the single most expensive market item | `market` |
| `minecraft:elytra` | Vanilla chase item | *not currently granted anywhere* |
| `minecraft:enchanted_golden_apple` | Vanilla chase item | `loot:legendarymonuments/chests/bell_tower_chest` 2.34%; `loot:legendarymonuments/chests/turnback_cave_vault` 2.22% |
| `minecraft:heart_of_the_sea` | Vanilla chase item | `loot:legendarymonuments/chests/liberty_island_chest` 12.32%; `loot:legendarymonuments/chests/lugia_temple_chest` 5.54% |
| `minecraft:netherite_scrap` | Netherite path | *not currently granted anywhere* |

## T1 — Uncommon

*Routine reward scale. Safe for regular play loops.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:absorb_bulb` | Standard held / utility item | `loot:rctmod/generic/uncommon/battle`; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_lush_den` +3 more |
| `cobblemon:adamant_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:aguav_berry` | Berry | `mod:cobblemon/blocks/aguav_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:ancient_azure_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_citrine_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_feather_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_gigaton_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_great_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_heavy_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_ivory_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_jet_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_leaden_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_origin_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_poke_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_roseate_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_slate_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_ultra_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_verdant_rod` | Standard held / utility item | `craft` |
| `cobblemon:ancient_wing_rod` | Standard held / utility item | `craft` |
| `cobblemon:apicot_berry` | Berry | `mod:cobblemon/blocks/apicot_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:apricorn_boat` | Standard held / utility item | `craft` |
| `cobblemon:apricorn_button` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_button`; `craft` |
| `cobblemon:apricorn_chest_boat` | Standard held / utility item | `craft` |
| `cobblemon:apricorn_door` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_door`; `craft` |
| `cobblemon:apricorn_fence` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_fence`; `craft` |
| `cobblemon:apricorn_fence_gate` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_fence_gate`; `craft` |
| `cobblemon:apricorn_hanging_sign` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_hanging_sign`; `mod:cobblemon/blocks/apricorn_wall_hanging_sign`; `craft` |
| `cobblemon:apricorn_log` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_log` 100.0%; `mod:botanypots/cobblemon/crop/apricorn_log` |
| `cobblemon:apricorn_planks` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_planks` 100.0%; `craft` |
| `cobblemon:apricorn_sign` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_sign`; `mod:cobblemon/blocks/apricorn_wall_sign`; `craft` |
| `cobblemon:apricorn_slab` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_slab`; `craft` |
| `cobblemon:apricorn_stairs` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_stairs`; `craft` |
| `cobblemon:apricorn_trapdoor` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_trapdoor`; `craft` |
| `cobblemon:apricorn_wood` | Standard held / utility item | `mod:cobblemon/blocks/apricorn_wood` 100.0%; `craft` |
| `cobblemon:aprijuice_black` | Standard held / utility item | `craft` |
| `cobblemon:aprijuice_blue` | Standard held / utility item | `craft` |
| `cobblemon:aprijuice_green` | Standard held / utility item | `craft` |
| `cobblemon:aprijuice_pink` | Standard held / utility item | `craft` |
| `cobblemon:aprijuice_red` | Standard held / utility item | `craft` |
| `cobblemon:aprijuice_white` | Standard held / utility item | `craft` |
| `cobblemon:aprijuice_yellow` | Standard held / utility item | `craft` |
| `cobblemon:armor_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/armor_fossil` +4 more |
| `cobblemon:aspear_berry` | Berry | `mod:cobblemon/blocks/aspear_berry` 100.0%; `mod:rctmod/generic/uncommon/berries` |
| `cobblemon:automaton_armor_trim_smithing_template` | Standard held / utility item | `loot:rctmod/generic/legendary/archeology`; `mod:cobblemon/ruins/gilded_chests/ruins`; `mod:cobblemon/ruins/rare/automaton_armor_trim_smithing_template` +2 more |
| `cobblemon:azure_ball` | Specialty ball | `loot:legendarymonuments/chests/regice_chest` 85.07%; `mod:cobblemon/injection/chests/spawn_bonus_chest` 14.29%; `mod:cobblemon/sets/any_common_pokeball` +2 more |
| `cobblemon:azure_rod` | Standard held / utility item | `mod:rctmod/generic/epic/fishing`; `craft` |
| `cobblemon:babiri_berry` | Berry | `mod:cobblemon/blocks/babiri_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:beast_rod` | Standard held / utility item | `craft` |
| `cobblemon:belue_berry` | Berry | `mod:cobblemon/blocks/belue_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:berry_juice` | Standard held / utility item | `mod:rctmod/generic/uncommon/berries`; `craft` |
| `cobblemon:berry_sweet` | Standard held / utility item | `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:big_malasada` | Standard held / utility item | `craft` |
| `cobblemon:big_root` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `mod:cobblemon/blocks/big_root` 100.0%; `mod:cobbleworkers/archaeology_treasure` 3.45% +5 more |
| `cobblemon:binding_band` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `craft`; `market` |
| `cobblemon:black_apricorn` | Apricorn | `mod:cobblemon/blocks/black_apricorn` 100.0%; `mod:rctmod/generic/rare/nature` |
| `cobblemon:black_apricorn_seed` | Apricorn | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/black_apricorn` 100.0%; `mod:cobblemon/blocks/black_apricorn_sapling` +4 more |
| `cobblemon:black_belt` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `craft` +1 more |
| `cobblemon:black_gilded_chest` | Standard held / utility item | `mod:cobblemon/blocks/black_gilded_chest` 100.0%; `craft` |
| `cobblemon:black_glasses` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `craft` +1 more |
| `cobblemon:black_plaque` | Standard held / utility item | `mod:cobblemon/blocks/black_plaque`; `craft` |
| `cobblemon:black_sludge` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/legendary/battle`; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% +1 more |
| `cobblemon:black_tumblestone` | Crafting material | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/ruins/rare/black_tumblestone`; `mod:rctmod/generic/rare/archeology` +1 more |
| `cobblemon:black_tumblestone_block` | Standard held / utility item | `craft` |
| `cobblemon:black_tumblestone_brick_slab` | Standard held / utility item | `mod:cobblemon/blocks/black_tumblestone_brick_slab`; `craft` |
| `cobblemon:black_tumblestone_brick_stairs` | Standard held / utility item | `mod:cobblemon/blocks/black_tumblestone_brick_stairs` 100.0%; `craft` |
| `cobblemon:black_tumblestone_brick_wall` | Standard held / utility item | `mod:cobblemon/blocks/black_tumblestone_brick_wall` 100.0%; `craft` |
| `cobblemon:black_tumblestone_bricks` | Standard held / utility item | `mod:cobblemon/blocks/black_tumblestone_bricks` 100.0%; `craft` |
| `cobblemon:blue_apricorn` | Apricorn | `mod:cobblemon/blocks/blue_apricorn` 100.0%; `mod:rctmod/generic/rare/nature` |
| `cobblemon:blue_apricorn_seed` | Apricorn | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/blue_apricorn` 100.0%; `mod:cobblemon/blocks/blue_apricorn_sapling` +4 more |
| `cobblemon:blue_gilded_chest` | Standard held / utility item | `mod:cobblemon/blocks/blue_gilded_chest` 100.0%; `craft` |
| `cobblemon:blue_mint_leaf` | Nature mint | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/blue_mint` 100.0%; `mod:cobblemon/blocks/blue_mint` 100.0% |
| `cobblemon:blue_mint_seeds` | Mint seed | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/blue_mint` 100.0%; `mod:cobblemon/blocks/blue_mint` 100.0% +2 more |
| `cobblemon:blue_plaque` | Standard held / utility item | `mod:cobblemon/blocks/blue_plaque`; `craft` |
| `cobblemon:bluk_berry` | Berry | `mod:cobblemon/blocks/bluk_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:bold_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:brave_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:bright_powder` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `craft`; `market` |
| `cobblemon:brittle_candy` | Standard held / utility item | `craft` |
| `cobblemon:brown_plaque` | Standard held / utility item | `mod:cobblemon/blocks/brown_plaque`; `craft` |
| `cobblemon:bugwort` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:bygone_sherd` | Standard held / utility item | `mod:cobblemon/ruins/common/decaying_crypt_ruins`; `mod:cobblemon/ruins/uncommon/crumbling_arch_ruins`; `mod:cobblemon/ruins/uncommon/decaying_crypt_ruins` +3 more |
| `cobblemon:calcium` | Vitamin | `crate:common` 1.183%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36%; `mod:rctmod/generic/uncommon/medicine` +2 more |
| `cobblemon:calm_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:campfire_pot_black` | Standard held / utility item | `mod:cobblemon/blocks/campfire_pot_black` 100.0%; `craft` |
| `cobblemon:campfire_pot_blue` | Standard held / utility item | `mod:cobblemon/blocks/campfire_pot_blue` 100.0%; `craft` |
| `cobblemon:campfire_pot_green` | Standard held / utility item | `mod:cobblemon/blocks/campfire_pot_green` 100.0%; `craft` |
| `cobblemon:campfire_pot_pink` | Standard held / utility item | `mod:cobblemon/blocks/campfire_pot_pink` 100.0%; `craft` |
| `cobblemon:campfire_pot_red` | Standard held / utility item | `mod:cobblemon/blocks/campfire_pot_red` 100.0%; `craft` |
| `cobblemon:campfire_pot_white` | Standard held / utility item | `mod:cobblemon/blocks/campfire_pot_white` 100.0%; `craft` |
| `cobblemon:campfire_pot_yellow` | Standard held / utility item | `mod:cobblemon/blocks/campfire_pot_yellow` 100.0%; `craft` |
| `cobblemon:candied_apple` | Standard held / utility item | `craft` |
| `cobblemon:candied_berry` | Berry | `craft` |
| `cobblemon:capture_sherd` | Standard held / utility item | `mod:cobblemon/ruins/common/deserted_house_ruins`; `mod:cobblemon/ruins/common/deserted_tower_ruins`; `mod:cobblemon/ruins/uncommon/deserted_house_ruins` +3 more |
| `cobblemon:carbos` | Vitamin | `crate:common` 1.183%; `loot:legendarymonuments/chests/dragoeleki_chest` 6.12%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% +3 more |
| `cobblemon:careful_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:casteliacone` | Standard held / utility item | `craft` |
| `cobblemon:cell_battery` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/dragoeleki_chest` 32.01%; `craft` +1 more |
| `cobblemon:charcoal_stick` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_birch_tree` +4 more |
| `cobblemon:charti_berry` | Berry | `mod:cobblemon/blocks/charti_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:cheri_berry` | Berry | `mod:cobblemon/blocks/cheri_berry` 100.0%; `mod:rctmod/generic/uncommon/berries` |
| `cobblemon:cherish_ball` | Specialty ball | *not currently granted anywhere* |
| `cobblemon:cherish_rod` | Standard held / utility item | `craft` |
| `cobblemon:chesto_berry` | Berry | `mod:cobblemon/blocks/chesto_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:chilan_berry` | Berry | `mod:cobblemon/blocks/chilan_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:chiseled_black_tumblestone_bricks` | Standard held / utility item | `mod:cobblemon/blocks/chiseled_black_tumblestone_bricks` 100.0%; `craft` |
| `cobblemon:chiseled_polished_black_tumblestone` | Crafting material | `mod:cobblemon/blocks/chiseled_polished_black_tumblestone` 100.0%; `craft` |
| `cobblemon:chiseled_polished_sky_tumblestone` | Crafting material | `mod:cobblemon/blocks/chiseled_polished_sky_tumblestone` 100.0%; `craft` |
| `cobblemon:chiseled_polished_tumblestone` | Crafting material | `mod:cobblemon/blocks/chiseled_polished_tumblestone` 100.0%; `craft` |
| `cobblemon:chiseled_sky_tumblestone_bricks` | Standard held / utility item | `mod:cobblemon/blocks/chiseled_sky_tumblestone_bricks` 100.0%; `craft` |
| `cobblemon:chiseled_tumblestone_bricks` | Standard held / utility item | `mod:cobblemon/blocks/chiseled_tumblestone_bricks` 100.0%; `craft` |
| `cobblemon:chople_berry` | Berry | `mod:cobblemon/blocks/chople_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:citrine_ball` | Standard held / utility item | `mod:cobblemon/injection/chests/spawn_bonus_chest` 14.29%; `mod:cobblemon/sets/any_common_pokeball`; `mod:rctmod/generic/common/pokeballs` +1 more |
| `cobblemon:citrine_rod` | Standard held / utility item | `mod:rctmod/generic/epic/fishing`; `craft` |
| `cobblemon:claw_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/claw_fossil` +4 more |
| `cobblemon:cleanse_tag` | Standard held / utility item | `loot:rctmod/generic/uncommon/battle`; `mod:cobblemon/blocks/cleanse_tag`; `mod:cobblemon/ruins/uncommon/sol_henge_ruins` +2 more |
| `cobblemon:clever_feather` | EV feather | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `mod:rctmod/generic/uncommon/training` +1 more |
| `cobblemon:clever_mochi` | Standard held / utility item | `craft` |
| `cobblemon:clover_sweet` | Standard held / utility item | `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:coarse_mulch` | Mulch | `loot:rctmod/generic/uncommon/nature`; `craft` |
| `cobblemon:coba_berry` | Berry | `mod:cobblemon/blocks/coba_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:colbur_berry` | Berry | `mod:cobblemon/blocks/colbur_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:cornn_berry` | Berry | `mod:cobblemon/blocks/cornn_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:cover_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/cover_fossil` +4 more |
| `cobblemon:coward_candy` | Standard held / utility item | `craft` |
| `cobblemon:cream_puff` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:custap_berry` | Berry | `mod:cobblemon/blocks/custap_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:cyan_mint_leaf` | Nature mint | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/cyan_mint` 100.0%; `mod:cobblemon/blocks/cyan_mint` 100.0% |
| `cobblemon:cyan_mint_seeds` | Mint seed | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/cyan_mint` 100.0%; `mod:cobblemon/blocks/cyan_mint` 100.0% +2 more |
| `cobblemon:cyan_plaque` | Standard held / utility item | `mod:cobblemon/blocks/cyan_plaque`; `craft` |
| `cobblemon:damp_rock` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/regirock_chest` 29.58%; `mod:cobbleworkers/archaeology_treasure` 3.45% +6 more |
| `cobblemon:destiny_knot` | Standard held / utility item | `crate:common` 1.5%; `loot:rctmod/generic/rare/battle`; `mod:cobblemon/ruins/uncommon/deserted_town_center_ruins` +7 more |
| `cobblemon:dire_hit` | Standard held / utility item | `mod:cobblemon/blocks/dire_hit`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:display_case` | Standard held / utility item | `mod:cobblemon/blocks/display_case`; `craft` |
| `cobblemon:dive_ball` | Specialty ball | `loot:legendarymonuments/chests/liberty_island_chest` 39.31%; `loot:legendarymonuments/chests/lugia_temple_chest` 58.54%; `mod:cobbleworkers/dive_treasure` 33.33% +4 more |
| `cobblemon:dive_rod` | Standard held / utility item | `loot:legendarymonuments/chests/liberty_island_chest` 12.32%; `mod:rctmod/generic/legendary/fishing`; `craft` |
| `cobblemon:dome_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/dome_fossil` +4 more |
| `cobblemon:dome_sherd` | Standard held / utility item | `mod:cobblemon/ruins/common/luna_henge_ruins`; `mod:cobblemon/ruins/common/mossy_oubliette_ruins`; `mod:cobblemon/ruins/uncommon/decaying_crypt_ruins` +3 more |
| `cobblemon:dragon_fang` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/dragoeleki_chest` 32.01% +7 more |
| `cobblemon:dream_rod` | Standard held / utility item | `craft` |
| `cobblemon:durin_berry` | Berry | `mod:cobblemon/blocks/durin_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:dusk_ball` | Specialty ball | `loot:legendarymonuments/chests/regirock_chest` 70.66%; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:rctmod/generic/epic/pokeballs` +2 more |
| `cobblemon:dusk_rod` | Standard held / utility item | `craft` |
| `cobblemon:eggant_berry` | Berry | `mod:cobblemon/blocks/eggant_berry` 100.0% |
| `cobblemon:eject_button` | Standard held / utility item | `mod:rctmod/generic/epic/battle`; `craft`; `market` |
| `cobblemon:eject_pack` | Standard held / utility item | `mod:rctmod/generic/epic/battle`; `craft`; `market` |
| `cobblemon:electric_seed` | Standard held / utility item | `loot:legendarymonuments/chests/dragoeleki_chest` 32.01%; `craft` |
| `cobblemon:enigma_berry` | Berry | `mod:cobblemon/blocks/enigma_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:everstone` | Standard held / utility item | `crate:common` 1.5%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_birch_tree` +5 more |
| `cobblemon:exp_share` | Standard held / utility item | `mod:rctmod/generic/legendary/training`; `craft`; `market` |
| `cobblemon:fairy_feather` | EV feather | `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `mod:cobbleworkers/archaeology_treasure` 3.45% +5 more |
| `cobblemon:fast_ball` | Specialty ball | `loot:legendarymonuments/chests/dragoeleki_chest` 74.23%; `mod:rctmod/generic/rare/pokeballs`; `craft` |
| `cobblemon:fast_rod` | Standard held / utility item | `craft` |
| `cobblemon:figy_berry` | Berry | `mod:cobblemon/blocks/figy_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:flame_orb` | Standard held / utility item | `loot:rctmod/generic/legendary/battle`; `craft`; `market` |
| `cobblemon:float_stone` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `market` |
| `cobblemon:flower_sweet` | Standard held / utility item | `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:focus_band` | Standard held / utility item | `mod:cobblemon/sets/any_ancient_held_item`; `mod:rctmod/generic/epic/battle`; `craft` +1 more |
| `cobblemon:fossil_analyzer` | Fossil | `mod:cobblemon/blocks/fossil_analyzer`; `craft` |
| `cobblemon:fossilized_bird` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/bird_fossil` +4 more |
| `cobblemon:fossilized_dino` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/dino_fossil` +4 more |
| `cobblemon:fossilized_drake` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/drake_fossil` +4 more |
| `cobblemon:fossilized_fish` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/fish_fossil` +4 more |
| `cobblemon:fresh_start_mochi` | Standard held / utility item | `craft` |
| `cobblemon:friend_ball` | Specialty ball | `mod:rctmod/generic/rare/pokeballs`; `craft` |
| `cobblemon:friend_rod` | Standard held / utility item | `craft` |
| `cobblemon:galarica_nuts` | Standard held / utility item | `mod:cobblemon/blocks/galarica_nut_bush`; `mod:cobblemon/blocks/galarica_nut_bush` |
| `cobblemon:ganlon_berry` | Berry | `mod:cobblemon/blocks/ganlon_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:genius_feather` | EV feather | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `mod:rctmod/generic/uncommon/training` +1 more |
| `cobblemon:genius_mochi` | Standard held / utility item | `craft` |
| `cobblemon:gentle_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:gilded_chest` | Standard held / utility item | `mod:cobblemon/blocks/gilded_chest` 100.0%; `craft` |
| `cobblemon:grassy_seed` | Standard held / utility item | `craft` |
| `cobblemon:gray_plaque` | Standard held / utility item | `mod:cobblemon/blocks/gray_plaque`; `craft` |
| `cobblemon:great_rod` | Standard held / utility item | `craft` |
| `cobblemon:green_apricorn` | Apricorn | `mod:cobblemon/blocks/green_apricorn` 100.0%; `mod:rctmod/generic/rare/nature` |
| `cobblemon:green_apricorn_seed` | Apricorn | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/green_apricorn` 100.0%; `mod:cobblemon/blocks/green_apricorn_sapling` +4 more |
| `cobblemon:green_gilded_chest` | Standard held / utility item | `mod:cobblemon/blocks/green_gilded_chest` 100.0%; `craft` |
| `cobblemon:green_mint_leaf` | Nature mint | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/green_mint` 100.0%; `mod:cobblemon/blocks/green_mint` 100.0% |
| `cobblemon:green_mint_seeds` | Mint seed | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/green_mint` 100.0%; `mod:cobblemon/blocks/green_mint` 100.0% +2 more |
| `cobblemon:green_plaque` | Standard held / utility item | `mod:cobblemon/blocks/green_plaque`; `craft` |
| `cobblemon:grepa_berry` | Berry | `mod:cobblemon/blocks/grepa_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:grip_claw` | Standard held / utility item | `mod:mega_showdown/sets/any_showdown_held_item`; `market` |
| `cobblemon:growth_mulch` | Mulch | `mod:rctmod/generic/common/nature`; `craft` |
| `cobblemon:guard_spec` | Standard held / utility item | `mod:cobblemon/blocks/guard_spec`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:haban_berry` | Berry | `mod:cobblemon/blocks/haban_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:hard_stone` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/regirock_chest` 70.66% +2 more |
| `cobblemon:hasty_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:heal_ball` | Standard held / utility item | `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:heal_powder` | Standard held / utility item | `mod:cobblemon/ruins/pots/ruins`; `mod:cobblemon/sets/any_natural_heal_item`; `mod:rctmod/generic/rare/medicine` +1 more |
| `cobblemon:heal_rod` | Standard held / utility item | `craft` |
| `cobblemon:healing_machine` | Standard held / utility item | `mod:cobblemon/blocks/healing_machine`; `craft` |
| `cobblemon:health_feather` | EV feather | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `mod:rctmod/generic/uncommon/training` +1 more |
| `cobblemon:health_mochi` | Standard held / utility item | `craft` |
| `cobblemon:hearty_grain_bale` | Standard held / utility item | `mod:cobblemon/blocks/hearty_grain_bale` 100.0%; `craft` |
| `cobblemon:hearty_grains` | Standard held / utility item | `mod:cobblemon/blocks/hearty_grains` 100.0%; `mod:cobblemon/blocks/hearty_grains` 100.0%; `craft` |
| `cobblemon:heat_rock` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/regirock_chest` 29.58%; `mod:legendarymonuments/chests/heatran_cave_chest` 12.01% +6 more |
| `cobblemon:heavy_ball` | Specialty ball | `loot:legendarymonuments/chests/registeel_chest` 78.61%; `mod:rctmod/generic/rare/pokeballs`; `craft` |
| `cobblemon:heavy_rod` | Standard held / utility item | `craft` |
| `cobblemon:helix_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:mega_showdown/archaeology/observatory_sus` 6.25% +4 more |
| `cobblemon:helix_sherd` | Standard held / utility item | `mod:cobblemon/ruins/common/deserted_town_center_ruins`; `mod:cobblemon/ruins/common/fallen_statue_ruins`; `mod:cobblemon/ruins/common/sol_henge_ruins` +3 more |
| `cobblemon:hondew_berry` | Berry | `mod:cobblemon/blocks/hondew_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:hopo_berry` | Berry | `mod:cobblemon/blocks/hopo_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:hp_up` | Vitamin | `crate:common` 1.183%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36%; `mod:rctmod/generic/uncommon/medicine` +2 more |
| `cobblemon:humid_mulch` | Mulch | `loot:rctmod/generic/uncommon/nature`; `craft` |
| `cobblemon:iapapa_berry` | Berry | `mod:cobblemon/blocks/iapapa_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:icy_rock` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_frozen_pond` +6 more |
| `cobblemon:impish_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:iron` | Vitamin | `crate:common` 1.183%; `loot:legendarymonuments/chests/registeel_chest` 35.31%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% +3 more |
| `cobblemon:iron_ball` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `mod:cobblemon/sets/any_ancient_held_item`; `craft` +1 more |
| `cobblemon:jaboca_berry` | Berry | `mod:cobblemon/blocks/jaboca_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:jaw_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/jaw_fossil` +4 more |
| `cobblemon:jelly_doughnut` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:jolly_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:jubilife_muffin` | Standard held / utility item | `craft` |
| `cobblemon:kasib_berry` | Berry | `mod:cobblemon/blocks/kasib_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:kebia_berry` | Berry | `mod:cobblemon/blocks/kebia_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:kee_berry` | Berry | `mod:cobblemon/blocks/kee_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:kelpsy_berry` | Berry | `mod:cobblemon/blocks/kelpsy_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:kings_rock` | Standard held / utility item | `crate:common` 0.203%; `loot:legendarymonuments/chests/regirock_chest` 29.58%; `mod:cobbleworkers/archaeology_treasure` 3.45% +6 more |
| `cobblemon:lagging_tail` | Standard held / utility item | `mod:mega_showdown/sets/any_showdown_held_item`; `market` |
| `cobblemon:lansat_berry` | Berry | `mod:cobblemon/blocks/lansat_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:lava_cookie` | Standard held / utility item | `craft` |
| `cobblemon:lax_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:leek_and_potato_stew` | Standard held / utility item | `mod:rctmod/generic/common/diverse`; `craft` |
| `cobblemon:leppa_berry` | Berry | `mod:cobblemon/blocks/leppa_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:level_ball` | Specialty ball | `mod:rctmod/generic/rare/pokeballs`; `craft` |
| `cobblemon:level_rod` | Standard held / utility item | `craft` |
| `cobblemon:liechi_berry` | Berry | `mod:cobblemon/blocks/liechi_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:light_ball` | Standard held / utility item | `mod:rctmod/generic/epic/battle`; `market` |
| `cobblemon:light_blue_plaque` | Standard held / utility item | `mod:cobblemon/blocks/light_blue_plaque`; `craft` |
| `cobblemon:light_clay` | Standard held / utility item | `craft`; `market` |
| `cobblemon:light_gray_plaque` | Standard held / utility item | `mod:cobblemon/blocks/light_gray_plaque`; `craft` |
| `cobblemon:lime_plaque` | Standard held / utility item | `mod:cobblemon/blocks/lime_plaque`; `craft` |
| `cobblemon:loamy_mulch` | Mulch | `loot:rctmod/generic/uncommon/nature`; `craft` |
| `cobblemon:lonely_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:love_ball` | Specialty ball | `mod:rctmod/generic/epic/pokeballs`; `craft` |
| `cobblemon:love_rod` | Standard held / utility item | `craft` |
| `cobblemon:love_sweet` | Standard held / utility item | `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:lucky_egg` | Standard held / utility item | `crate:common` 3.1%; `crate:rare` 7.7%; `loot:legendarymonuments/chests/bell_tower_chest` 9.07% +2 more |
| `cobblemon:lum_berry` | Berry | `mod:cobblemon/blocks/lum_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:luminous_moss` | Standard held / utility item | `craft`; `market` |
| `cobblemon:lumiose_galette` | Standard held / utility item | `craft` |
| `cobblemon:lure_ball` | Specialty ball | `mod:cobbleworkers/dive_treasure` 33.33%; `mod:cobblemon/shipwreck_coves/fishing_boats/fishing_boat`; `mod:cobblemon/shipwreck_coves/gilded_chests/lesser_treasure` +2 more |
| `cobblemon:lure_rod` | Standard held / utility item | `mod:rctmod/generic/legendary/fishing`; `craft` |
| `cobblemon:luxury_ball` | Specialty ball | `mod:rctmod/generic/epic/pokeballs`; `craft` |
| `cobblemon:luxury_rod` | Standard held / utility item | `craft` |
| `cobblemon:magenta_plaque` | Standard held / utility item | `mod:cobblemon/blocks/magenta_plaque`; `craft` |
| `cobblemon:magnet` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/dragoeleki_chest` 32.01% +3 more |
| `cobblemon:mago_berry` | Berry | `mod:cobblemon/blocks/mago_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:magost_berry` | Berry | `mod:cobblemon/blocks/magost_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:maranga_berry` | Berry | `mod:cobblemon/blocks/maranga_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:master_rod` | Standard held / utility item | `craft` |
| `cobblemon:medicinal_brew` | Standard held / utility item | `mod:rctmod/generic/uncommon/medicine`; `craft` |
| `cobblemon:mental_herb` | Standard held / utility item | `loot:rctmod/generic/uncommon/battle`; `loot:rctmod/generic/uncommon/nature`; `mod:cobbleworkers/archaeology_treasure` 3.45% +6 more |
| `cobblemon:metal_coat` | Standard held / utility item | `crate:common` 0.305%; `loot:legendarymonuments/chests/registeel_chest` 78.61%; `mod:rctmod/generic/epic/battle` +3 more |
| `cobblemon:metal_powder` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `market` |
| `cobblemon:metronome` | Standard held / utility item | `craft`; `market` |
| `cobblemon:micle_berry` | Berry | `mod:cobblemon/blocks/micle_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:mild_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:miracle_seed` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `craft` +1 more |
| `cobblemon:misty_seed` | Standard held / utility item | `craft` |
| `cobblemon:modest_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:monitor` | Standard held / utility item | `mod:cobblemon/blocks/monitor`; `craft` |
| `cobblemon:moomoo_milk` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:moon_ball` | Specialty ball | `mod:rctmod/generic/rare/pokeballs`; `craft` |
| `cobblemon:moon_rod` | Standard held / utility item | `craft` |
| `cobblemon:mulch_base` | Standard held / utility item | `mod:rctmod/generic/common/nature`; `craft` |
| `cobblemon:muscle_band` | Standard held / utility item | `crate:common` 0.305%; `mod:rctmod/generic/legendary/training`; `craft` +1 more |
| `cobblemon:muscle_feather` | EV feather | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `mod:rctmod/generic/uncommon/training` +1 more |
| `cobblemon:muscle_mochi` | Standard held / utility item | `craft` |
| `cobblemon:mystic_water` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `mod:cobblemon/shipwreck_coves/gilded_chests/big_treasure` +2 more |
| `cobblemon:naive_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:nanab_berry` | Berry | `mod:cobblemon/blocks/nanab_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:naughty_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:nest_ball` | Specialty ball | `mod:rctmod/generic/rare/pokeballs`; `craft`; `market` |
| `cobblemon:nest_rod` | Standard held / utility item | `craft` |
| `cobblemon:net_ball` | Specialty ball | `loot:legendarymonuments/chests/lugia_temple_chest` 58.54%; `mod:cobbleworkers/dive_treasure` 33.33%; `mod:cobblemon/shipwreck_coves/fishing_boats/fishing_boat` +4 more |
| `cobblemon:net_rod` | Standard held / utility item | `mod:rctmod/generic/legendary/fishing`; `craft` |
| `cobblemon:never_melt_ice` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/regice_chest` 41.11% +7 more |
| `cobblemon:nomel_berry` | Berry | `mod:cobblemon/blocks/nomel_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:nostalgic_sherd` | Standard held / utility item | `mod:cobblemon/ruins/common/crumbling_arch_ruins`; `mod:cobblemon/ruins/common/rooted_arch_ruins`; `mod:cobblemon/ruins/common/stonjourner_henge_ruins` +3 more |
| `cobblemon:npc_editor` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:numb_candy` | Standard held / utility item | `craft` |
| `cobblemon:occa_berry` | Berry | `mod:cobblemon/blocks/occa_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:old_amber_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/old_amber_fossil` +4 more |
| `cobblemon:old_gateau` | Standard held / utility item | `craft` |
| `cobblemon:open_faced_sandwich` | Standard held / utility item | `craft` |
| `cobblemon:oran_berry` | Berry | `mod:cobblemon/blocks/oran_berry` 100.0%; `mod:cobblemon/villages/village_pokecenters`; `mod:rctmod/generic/common/berries` |
| `cobblemon:orange_plaque` | Standard held / utility item | `mod:cobblemon/blocks/orange_plaque`; `craft` |
| `cobblemon:pamtre_berry` | Berry | `mod:cobblemon/blocks/pamtre_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:park_ball` | Specialty ball | `mod:rctmod/generic/rare/pokeballs`; `craft` |
| `cobblemon:park_rod` | Standard held / utility item | `craft` |
| `cobblemon:passho_berry` | Berry | `mod:cobblemon/blocks/passho_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:pasture` | Standard held / utility item | `mod:cobblemon/blocks/pasture`; `craft` |
| `cobblemon:payapa_berry` | Berry | `mod:cobblemon/blocks/payapa_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:pc` | Standard held / utility item | `mod:cobblemon/blocks/pc`; `craft` |
| `cobblemon:peat_block` | Standard held / utility item | `crate:common` 0.203%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_birch_tree` +5 more |
| `cobblemon:peat_mulch` | Mulch | `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:pecha_berry` | Berry | `mod:cobblemon/blocks/pecha_berry` 100.0%; `mod:rctmod/generic/uncommon/berries` |
| `cobblemon:pep_up_flower` | Standard held / utility item | `mod:cobblemon/blocks/revival_herb` 100.0%; `mod:cobblemon/blocks/pep_up_flower`; `mod:cobblemon/blocks/potted_pep_up_flower` +1 more |
| `cobblemon:persim_berry` | Berry | `mod:cobblemon/blocks/persim_berry` 100.0%; `mod:rctmod/generic/uncommon/berries` |
| `cobblemon:petaya_berry` | Berry | `mod:cobblemon/blocks/petaya_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:pewter_crunchies` | Standard held / utility item | `craft` |
| `cobblemon:pinap_berry` | Berry | `mod:cobblemon/blocks/pinap_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:pink_apricorn` | Apricorn | `loot:rctmod/generic/uncommon/nature`; `mod:cobblemon/blocks/pink_apricorn` 100.0% |
| `cobblemon:pink_apricorn_seed` | Apricorn | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/pink_apricorn` 100.0%; `mod:cobblemon/blocks/pink_apricorn_sapling` +4 more |
| `cobblemon:pink_gilded_chest` | Standard held / utility item | `mod:cobblemon/blocks/pink_gilded_chest` 100.0%; `craft` |
| `cobblemon:pink_mint_leaf` | Nature mint | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/pink_mint` 100.0%; `mod:cobblemon/blocks/pink_mint` 100.0% |
| `cobblemon:pink_mint_seeds` | Mint seed | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/pink_mint` 100.0%; `mod:cobblemon/blocks/pink_mint` 100.0% +2 more |
| `cobblemon:pink_plaque` | Standard held / utility item | `mod:cobblemon/blocks/pink_plaque`; `craft` |
| `cobblemon:plume_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/plume_fossil` +4 more |
| `cobblemon:poison_barb` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `mod:cobblemon/ruins/common/decaying_crypt_ruins` +4 more |
| `cobblemon:poke_bait` | Standard held / utility item | `craft` |
| `cobblemon:poke_cake` | Standard held / utility item | `craft` |
| `cobblemon:poke_puff` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:poke_rod` | Standard held / utility item | `mod:rctmod/generic/epic/fishing`; `craft` |
| `cobblemon:poke_snack` | Standard held / utility item | `craft` |
| `cobblemon:pokedex_black` | Standard held / utility item | `craft` |
| `cobblemon:pokedex_blue` | Standard held / utility item | `craft` |
| `cobblemon:pokedex_green` | Standard held / utility item | `craft` |
| `cobblemon:pokedex_pink` | Standard held / utility item | `craft` |
| `cobblemon:pokedex_red` | Standard held / utility item | `craft` |
| `cobblemon:pokedex_white` | Standard held / utility item | `craft` |
| `cobblemon:pokedex_yellow` | Standard held / utility item | `craft` |
| `cobblemon:pokerod_smithing_template` | Standard held / utility item | `mod:cobblemon/injection/gameplay/fishing/treasure` 100.0%; `mod:cobblemon/shipwreck_coves/fishing_boats/fishing_boat`; `mod:cobblemon/shipwreck_coves/gilded_chests/big_treasure` +2 more |
| `cobblemon:polished_black_tumblestone` | Crafting material | `mod:cobblemon/blocks/polished_black_tumblestone` 100.0%; `craft` |
| `cobblemon:polished_black_tumblestone_slab` | Standard held / utility item | `mod:cobblemon/blocks/polished_black_tumblestone_slab`; `craft` |
| `cobblemon:polished_black_tumblestone_stairs` | Standard held / utility item | `mod:cobblemon/blocks/polished_black_tumblestone_stairs` 100.0%; `craft` |
| `cobblemon:polished_black_tumblestone_wall` | Standard held / utility item | `mod:cobblemon/blocks/polished_black_tumblestone_wall` 100.0%; `craft` |
| `cobblemon:polished_sky_tumblestone` | Crafting material | `mod:cobblemon/blocks/polished_sky_tumblestone` 100.0%; `craft` |
| `cobblemon:polished_sky_tumblestone_slab` | Standard held / utility item | `mod:cobblemon/blocks/polished_sky_tumblestone_slab`; `craft` |
| `cobblemon:polished_sky_tumblestone_stairs` | Standard held / utility item | `mod:cobblemon/blocks/polished_sky_tumblestone_stairs` 100.0%; `craft` |
| `cobblemon:polished_sky_tumblestone_wall` | Standard held / utility item | `mod:cobblemon/blocks/polished_sky_tumblestone_wall` 100.0%; `craft` |
| `cobblemon:polished_tumblestone` | Crafting material | `mod:cobblemon/blocks/polished_tumblestone` 100.0%; `craft` |
| `cobblemon:polished_tumblestone_slab` | Standard held / utility item | `mod:cobblemon/blocks/polished_tumblestone_slab`; `craft` |
| `cobblemon:polished_tumblestone_stairs` | Standard held / utility item | `mod:cobblemon/blocks/polished_tumblestone_stairs` 100.0%; `craft` |
| `cobblemon:polished_tumblestone_wall` | Standard held / utility item | `mod:cobblemon/blocks/polished_tumblestone_wall` 100.0%; `craft` |
| `cobblemon:pomeg_berry` | Berry | `mod:cobblemon/blocks/pomeg_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:ponigiri` | Standard held / utility item | `craft` |
| `cobblemon:potato_mochi` | Standard held / utility item | `craft` |
| `cobblemon:power_anklet` | EV training item | `crate:common` 0.667%; `mod:rctmod/generic/rare/training`; `craft` +1 more |
| `cobblemon:power_band` | EV training item | `crate:common` 0.667%; `mod:rctmod/generic/rare/training`; `craft` +1 more |
| `cobblemon:power_belt` | EV training item | `crate:common` 0.667%; `mod:rctmod/generic/rare/training`; `craft` +1 more |
| `cobblemon:power_bracer` | EV training item | `crate:common` 0.667%; `mod:rctmod/generic/rare/training`; `craft` +1 more |
| `cobblemon:power_herb` | Standard held / utility item | `loot:rctmod/generic/uncommon/battle`; `loot:rctmod/generic/uncommon/nature`; `mod:cobbleworkers/archaeology_treasure` 3.45% +6 more |
| `cobblemon:power_lens` | EV training item | `crate:common` 0.667%; `mod:rctmod/generic/rare/training`; `craft` +1 more |
| `cobblemon:power_weight` | EV training item | `crate:common` 0.667%; `mod:rctmod/generic/rare/training`; `craft` +1 more |
| `cobblemon:pp_max` | Standard held / utility item | `loot:rctmod/generic/epic/medicine`; `loot:legendarymonuments/chests/turnback_cave_chest` 1.98%; `craft` |
| `cobblemon:pp_up` | Standard held / utility item | `loot:legendarymonuments/chests/turnback_cave_chest` 11.36%; `mod:rctmod/generic/rare/medicine`; `craft` |
| `cobblemon:premier_ball` | Specialty ball | `mod:cobblemon/injection/chests/spawn_bonus_chest` 7.14%; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:premier_rod` | Standard held / utility item | `craft` |
| `cobblemon:protective_pads` | Standard held / utility item | `craft`; `market` |
| `cobblemon:protein` | Vitamin | `crate:common` 1.183%; `loot:legendarymonuments/chests/regirock_chest` 5.59%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% +2 more |
| `cobblemon:psychic_seed` | Standard held / utility item | `craft` |
| `cobblemon:purple_plaque` | Standard held / utility item | `mod:cobblemon/blocks/purple_plaque`; `craft` |
| `cobblemon:qualot_berry` | Berry | `mod:cobblemon/blocks/qualot_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:quick_ball` | Specialty ball | `crate:common` 3.1%; `mod:rctmod/generic/epic/pokeballs`; `craft` +1 more |
| `cobblemon:quick_claw` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `market` |
| `cobblemon:quick_powder` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `market` |
| `cobblemon:quick_rod` | Standard held / utility item | `craft` |
| `cobblemon:quiet_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:rabuta_berry` | Berry | `mod:cobblemon/blocks/rabuta_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:rage_candy_bar` | Standard held / utility item | `craft` |
| `cobblemon:rash_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:rawst_berry` | Berry | `mod:cobblemon/blocks/rawst_berry` 100.0%; `mod:rctmod/generic/uncommon/berries` |
| `cobblemon:razor_claw` | Standard held / utility item | `crate:common` 0.203%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_frozen_spike` +3 more |
| `cobblemon:razor_fang` | Standard held / utility item | `crate:common` 0.203%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/uncommon/prehistoric_sandy_den` +5 more |
| `cobblemon:razz_berry` | Berry | `mod:cobblemon/blocks/razz_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:red_apricorn` | Apricorn | `loot:rctmod/generic/uncommon/nature`; `mod:cobblemon/blocks/red_apricorn` 100.0% |
| `cobblemon:red_apricorn_seed` | Apricorn | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/red_apricorn` 100.0%; `mod:cobblemon/blocks/potted_red_apricorn_sapling` +4 more |
| `cobblemon:red_card` | Standard held / utility item | `mod:rctmod/generic/epic/battle`; `craft`; `market` |
| `cobblemon:red_mint_leaf` | Nature mint | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/red_mint` 100.0%; `mod:cobblemon/blocks/red_mint` 100.0% |
| `cobblemon:red_mint_seeds` | Mint seed | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/red_mint` 100.0%; `mod:cobblemon/blocks/red_mint` 100.0% +2 more |
| `cobblemon:red_plaque` | Standard held / utility item | `mod:cobblemon/blocks/red_plaque`; `craft` |
| `cobblemon:relaxed_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:repeat_ball` | Specialty ball | `mod:rctmod/generic/epic/pokeballs`; `craft` |
| `cobblemon:repeat_rod` | Standard held / utility item | `craft` |
| `cobblemon:resist_feather` | EV feather | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `mod:rctmod/generic/uncommon/training` +1 more |
| `cobblemon:resist_mochi` | Standard held / utility item | `craft` |
| `cobblemon:restoration_tank` | Standard held / utility item | `mod:cobblemon/blocks/restoration_tank`; `craft` |
| `cobblemon:revival_herb` | Standard held / utility item | `mod:cobblemon/blocks/revival_herb` 100.0%; `mod:cobblemon/sets/any_natural_heal_item`; `mod:rctmod/generic/rare/medicine` +1 more |
| `cobblemon:ribbon_sweet` | Standard held / utility item | `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:rich_mulch` | Mulch | `loot:rctmod/generic/uncommon/nature`; `craft` |
| `cobblemon:rindo_berry` | Berry | `mod:cobblemon/blocks/rindo_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:ring_target` | Standard held / utility item | `mod:rctmod/generic/epic/battle`; `craft`; `market` |
| `cobblemon:roasted_leek` | Standard held / utility item | `mod:rctmod/generic/common/diverse`; `craft` |
| `cobblemon:room_service` | Standard held / utility item | `craft`; `market` |
| `cobblemon:root_fossil` | Fossil | `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/root_fossil`; `mod:cobblemon/fossils/uncommon/prehistoric_hydrothermal_vents` +3 more |
| `cobblemon:roseate_ball` | Standard held / utility item | `mod:cobblemon/injection/chests/spawn_bonus_chest` 14.29%; `mod:cobblemon/sets/any_common_pokeball`; `mod:rctmod/generic/common/pokeballs` +1 more |
| `cobblemon:roseate_rod` | Standard held / utility item | `mod:rctmod/generic/epic/fishing`; `craft` |
| `cobblemon:roseli_berry` | Berry | `mod:cobblemon/blocks/roseli_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:rowap_berry` | Berry | `mod:cobblemon/blocks/rowap_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:saccharine_boat` | Standard held / utility item | `craft` |
| `cobblemon:saccharine_button` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_button`; `craft` |
| `cobblemon:saccharine_chest_boat` | Standard held / utility item | `craft` |
| `cobblemon:saccharine_door` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_door`; `craft` |
| `cobblemon:saccharine_fence` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_fence`; `craft` |
| `cobblemon:saccharine_fence_gate` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_fence_gate`; `craft` |
| `cobblemon:saccharine_hanging_sign` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_hanging_sign`; `mod:cobblemon/blocks/saccharine_wall_hanging_sign`; `craft` |
| `cobblemon:saccharine_log` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_log` 100.0%; `mod:cobblemon/blocks/saccharine_log_slathered` 100.0% |
| `cobblemon:saccharine_planks` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_planks` 100.0%; `craft` |
| `cobblemon:saccharine_sapling` | Standard held / utility item | `mod:cobblemon/blocks/potted_saccharine_sapling`; `mod:cobblemon/blocks/saccharine_sapling` |
| `cobblemon:saccharine_sign` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_sign`; `mod:cobblemon/blocks/saccharine_wall_sign`; `craft` |
| `cobblemon:saccharine_slab` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_slab`; `craft` |
| `cobblemon:saccharine_stairs` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_stairs`; `craft` |
| `cobblemon:saccharine_trapdoor` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_trapdoor`; `craft` |
| `cobblemon:saccharine_wood` | Standard held / utility item | `mod:cobblemon/blocks/saccharine_wood` 100.0%; `craft` |
| `cobblemon:safari_ball` | Specialty ball | `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:safari_rod` | Standard held / utility item | `craft` |
| `cobblemon:safety_goggles` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `craft`; `market` |
| `cobblemon:sail_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/sail_fossil` +4 more |
| `cobblemon:salac_berry` | Berry | `mod:cobblemon/blocks/salac_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:sandy_mulch` | Mulch | `loot:rctmod/generic/uncommon/nature`; `mod:mega_showdown/archaeology/observatory_sus` 18.75%; `craft` |
| `cobblemon:sassy_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:scope_lens` | Standard held / utility item | `craft`; `market` |
| `cobblemon:scroll_of_darkness` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:scroll_of_waters` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:serious_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:shalour_sable` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:sharp_beak` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `market` |
| `cobblemon:shed_shell` | Standard held / utility item | `market` |
| `cobblemon:shell_bell` | Standard held / utility item | `mod:cobblemon/ruins/uncommon/deserted_house_ruins`; `mod:cobblemon/ruins/uncommon/deserted_tower_ruins`; `mod:cobblemon/ruins/uncommon/deserted_town_center_ruins` +5 more |
| `cobblemon:shell_helmet` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:shuca_berry` | Berry | `mod:cobblemon/blocks/shuca_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:sickly_candy` | Standard held / utility item | `craft` |
| `cobblemon:silk_scarf` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/regigigas_chest` 69.14% +2 more |
| `cobblemon:silver_powder` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `craft` +1 more |
| `cobblemon:sinister_tea` | Standard held / utility item | `craft` |
| `cobblemon:sitrus_berry` | Berry | `mod:cobblemon/blocks/sitrus_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:skull_fossil` | Fossil | `loot:rctmod/generic/legendary/archeology`; `mod:cobbleworkers/archaeology_treasure` 6.67%; `mod:cobblemon/fossils/rare/skull_fossil` +4 more |
| `cobblemon:sky_tumblestone` | Crafting material | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/ruins/rare/sky_tumblestone`; `mod:rctmod/generic/uncommon/archeology` +1 more |
| `cobblemon:sky_tumblestone_block` | Standard held / utility item | `craft` |
| `cobblemon:sky_tumblestone_brick_slab` | Standard held / utility item | `mod:cobblemon/blocks/sky_tumblestone_brick_slab`; `craft` |
| `cobblemon:sky_tumblestone_brick_stairs` | Standard held / utility item | `mod:cobblemon/blocks/sky_tumblestone_brick_stairs` 100.0%; `craft` |
| `cobblemon:sky_tumblestone_brick_wall` | Standard held / utility item | `mod:cobblemon/blocks/sky_tumblestone_brick_wall` 100.0%; `craft` |
| `cobblemon:sky_tumblestone_bricks` | Standard held / utility item | `mod:cobblemon/blocks/sky_tumblestone_bricks` 100.0%; `craft` |
| `cobblemon:slate_ball` | Standard held / utility item | `mod:cobblemon/injection/chests/spawn_bonus_chest` 14.29%; `mod:cobblemon/sets/any_common_pokeball`; `mod:rctmod/generic/common/pokeballs` +1 more |
| `cobblemon:slate_rod` | Standard held / utility item | `mod:rctmod/generic/epic/fishing`; `craft` |
| `cobblemon:slow_candy` | Standard held / utility item | `craft` |
| `cobblemon:small_budding_sky_tumblestone` | Crafting material | `loot:rctmod/generic/legendary/archeology`; `mod:rctmod/generic/common/archeology` |
| `cobblemon:smoke_ball` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `mod:cobblemon/ruins/uncommon/gimmi_tower_rooted`; `market` |
| `cobblemon:smoked_tail_curry` | Standard held / utility item | `craft` |
| `cobblemon:smooth_black_tumblestone` | Crafting material | `mod:cobblemon/blocks/smooth_black_tumblestone` 100.0%; `craft` |
| `cobblemon:smooth_black_tumblestone_slab` | Standard held / utility item | `mod:cobblemon/blocks/smooth_black_tumblestone_slab`; `craft` |
| `cobblemon:smooth_black_tumblestone_stairs` | Standard held / utility item | `mod:cobblemon/blocks/smooth_black_tumblestone_stairs` 100.0%; `craft` |
| `cobblemon:smooth_rock` | Standard held / utility item | `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/regirock_chest` 29.58%; `mod:cobbleworkers/archaeology_treasure` 3.45% +6 more |
| `cobblemon:smooth_sky_tumblestone` | Crafting material | `mod:cobblemon/blocks/smooth_sky_tumblestone` 100.0%; `craft` |
| `cobblemon:smooth_sky_tumblestone_slab` | Standard held / utility item | `mod:cobblemon/blocks/smooth_sky_tumblestone_slab`; `craft` |
| `cobblemon:smooth_sky_tumblestone_stairs` | Standard held / utility item | `mod:cobblemon/blocks/smooth_sky_tumblestone_stairs` 100.0%; `craft` |
| `cobblemon:smooth_tumblestone` | Crafting material | `mod:cobblemon/blocks/smooth_tumblestone` 100.0%; `craft` |
| `cobblemon:smooth_tumblestone_slab` | Standard held / utility item | `mod:cobblemon/blocks/smooth_tumblestone_slab`; `craft` |
| `cobblemon:smooth_tumblestone_stairs` | Standard held / utility item | `mod:cobblemon/blocks/smooth_tumblestone_stairs` 100.0%; `craft` |
| `cobblemon:soft_sand` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `craft` +1 more |
| `cobblemon:soothe_bell` | Standard held / utility item | `loot:legendarymonuments/chests/bell_tower_chest` 9.07%; `mod:rctmod/generic/rare/training`; `craft` +1 more |
| `cobblemon:spell_tag` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% +5 more |
| `cobblemon:spelon_berry` | Berry | `mod:cobblemon/blocks/spelon_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:sport_ball` | Specialty ball | `mod:rctmod/generic/rare/pokeballs`; `craft` |
| `cobblemon:sport_rod` | Standard held / utility item | `craft` |
| `cobblemon:star_sweet` | Standard held / utility item | `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:starf_berry` | Berry | `mod:cobblemon/blocks/starf_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:sticky_barb` | Standard held / utility item | `mod:rctmod/generic/epic/battle`; `market` |
| `cobblemon:strawberry_sweet` | Standard held / utility item | `mod:rctmod/generic/epic/evolution`; `craft` |
| `cobblemon:stripped_apricorn_log` | Standard held / utility item | `mod:cobblemon/blocks/stripped_apricorn_log` 100.0% |
| `cobblemon:stripped_apricorn_wood` | Standard held / utility item | `mod:cobblemon/blocks/stripped_apricorn_wood` 100.0%; `craft` |
| `cobblemon:stripped_saccharine_log` | Standard held / utility item | `mod:cobblemon/blocks/stripped_saccharine_log` 100.0% |
| `cobblemon:stripped_saccharine_wood` | Standard held / utility item | `mod:cobblemon/blocks/stripped_saccharine_wood` 100.0% |
| `cobblemon:surprise_mulch` | Mulch | `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:suspicious_sherd` | Standard held / utility item | `mod:cobblemon/ruins/common/hidden_bunker_ruins`; `mod:cobblemon/ruins/common/unstable_cave_ruins`; `mod:cobblemon/ruins/uncommon/deserted_house_ruins` +3 more |
| `cobblemon:sweet_apple` | Standard held / utility item | `crate:common` 0.203%; `mod:mega_showdown/archaeology/observatory_sus` 18.75%; `mod:rctmod/generic/epic/evolution` |
| `cobblemon:swift_feather` | EV feather | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `mod:rctmod/generic/uncommon/training` +1 more |
| `cobblemon:swift_mochi` | Standard held / utility item | `craft` |
| `cobblemon:syrupy_apple` | Standard held / utility item | `crate:common` 0.203% |
| `cobblemon:tamato_berry` | Berry | `mod:cobblemon/blocks/tamato_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:tanga_berry` | Berry | `mod:cobblemon/blocks/tanga_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:tart_apple` | Standard held / utility item | `crate:common` 0.203%; `mod:rctmod/generic/epic/evolution` |
| `cobblemon:tasty_tail` | Standard held / utility item | *not currently granted anywhere* |
| `cobblemon:tatami_block` | Standard held / utility item | `mod:cobblemon/blocks/tatami_block` 100.0%; `craft` |
| `cobblemon:tatami_mat` | Standard held / utility item | `mod:cobblemon/blocks/tatami_mat` 100.0%; `craft` |
| `cobblemon:terrain_extender` | Standard held / utility item | `craft`; `market` |
| `cobblemon:timer_ball` | Specialty ball | `crate:common` 4.6%; `loot:legendarymonuments/chests/regigigas_chest` 99.11%; `mod:rctmod/generic/epic/pokeballs` +2 more |
| `cobblemon:timer_rod` | Standard held / utility item | `craft` |
| `cobblemon:timid_mint` | Nature mint | `crate:common` 0.195%; `mod:rctmod/generic/rare/nature`; `craft` |
| `cobblemon:touga_berry` | Berry | `mod:cobblemon/blocks/touga_berry` 100.0%; `mod:rctmod/generic/uncommon/berries` |
| `cobblemon:toxic_orb` | Standard held / utility item | `loot:rctmod/generic/legendary/battle`; `craft`; `market` |
| `cobblemon:tumblestone` | Standard held / utility item | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/ruins/rare/tumblestone`; `mod:rctmod/generic/rare/archeology` +1 more |
| `cobblemon:tumblestone_block` | Standard held / utility item | `craft` |
| `cobblemon:tumblestone_brick_slab` | Standard held / utility item | `mod:cobblemon/blocks/tumblestone_brick_slab`; `craft` |
| `cobblemon:tumblestone_brick_stairs` | Standard held / utility item | `mod:cobblemon/blocks/tumblestone_brick_stairs` 100.0%; `craft` |
| `cobblemon:tumblestone_brick_wall` | Standard held / utility item | `mod:cobblemon/blocks/tumblestone_brick_wall` 100.0%; `craft` |
| `cobblemon:tumblestone_bricks` | Standard held / utility item | `mod:cobblemon/blocks/tumblestone_bricks` 100.0%; `craft` |
| `cobblemon:twisted_spoon` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `mod:cobblemon/ruins/uncommon/crumbling_arch_ruins` +7 more |
| `cobblemon:ultra_rod` | Standard held / utility item | `craft` |
| `cobblemon:unremarkable_teacup` | Standard held / utility item | `crate:common` 0.203%; `mod:cobblemon/ruins/uncommon/crumbling_arch_ruins`; `mod:cobblemon/ruins/uncommon/deserted_house_ruins` +4 more |
| `cobblemon:upgrade` | Standard held / utility item | `crate:common` 0.203%; `mod:rctmod/generic/legendary/evolution`; `craft` |
| `cobblemon:utility_umbrella` | Standard held / utility item | `craft`; `market` |
| `cobblemon:verdant_ball` | Standard held / utility item | `mod:cobblemon/injection/chests/spawn_bonus_chest` 14.29%; `mod:cobblemon/sets/any_common_pokeball`; `mod:rctmod/generic/common/pokeballs` +1 more |
| `cobblemon:verdant_rod` | Standard held / utility item | `mod:rctmod/generic/epic/fishing`; `craft` |
| `cobblemon:vivichoke` | Standard held / utility item | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/vivichoke_seeds` 100.0% |
| `cobblemon:vivichoke_dip` | Standard held / utility item | `mod:rctmod/generic/rare/diverse`; `craft` |
| `cobblemon:vivichoke_seeds` | Standard held / utility item | `mod:cobblemon/blocks/vivichoke_seeds` 100.0%; `mod:cobblemon/injection/chests/abandoned_mineshaft` 100.0%; `mod:cobblemon/injection/chests/jungle_temple` 100.0% +4 more |
| `cobblemon:wacan_berry` | Berry | `mod:cobblemon/blocks/wacan_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:watmel_berry` | Berry | `mod:cobblemon/blocks/watmel_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:weak_candy` | Standard held / utility item | `craft` |
| `cobblemon:wepear_berry` | Berry | `mod:cobblemon/blocks/wepear_berry` 100.0%; `mod:rctmod/generic/rare/berries` |
| `cobblemon:white_apricorn` | Apricorn | `loot:rctmod/generic/uncommon/nature`; `mod:cobblemon/blocks/white_apricorn` 100.0% |
| `cobblemon:white_apricorn_seed` | Apricorn | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/white_apricorn` 100.0%; `mod:cobblemon/blocks/potted_white_apricorn_sapling` +4 more |
| `cobblemon:white_gilded_chest` | Standard held / utility item | `mod:cobblemon/blocks/white_gilded_chest` 100.0%; `craft` |
| `cobblemon:white_herb` | Standard held / utility item | `loot:rctmod/generic/uncommon/battle`; `loot:rctmod/generic/uncommon/nature`; `mod:cobbleworkers/archaeology_treasure` 3.45% +6 more |
| `cobblemon:white_mint_leaf` | Nature mint | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/white_mint` 100.0%; `mod:cobblemon/blocks/white_mint` 100.0% |
| `cobblemon:white_mint_seeds` | Mint seed | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/white_mint` 100.0%; `mod:cobblemon/blocks/white_mint` 100.0% +1 more |
| `cobblemon:white_plaque` | Standard held / utility item | `mod:cobblemon/blocks/white_plaque`; `craft` |
| `cobblemon:wide_lens` | Standard held / utility item | `craft`; `market` |
| `cobblemon:wiki_berry` | Berry | `mod:cobblemon/blocks/wiki_berry` 100.0%; `mod:rctmod/generic/legendary/berries` |
| `cobblemon:wise_glasses` | Standard held / utility item | `crate:common` 0.305%; `loot:rctmod/generic/rare/battle`; `craft` +1 more |
| `cobblemon:x_accuracy` | Standard held / utility item | `mod:cobblemon/blocks/x_accuracy`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:x_attack` | Standard held / utility item | `mod:cobblemon/blocks/x_attack`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:x_defence` | Standard held / utility item | `mod:cobblemon/blocks/x_defence`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:x_special_attack` | Standard held / utility item | `mod:cobblemon/blocks/x_special_attack`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:x_special_defence` | Standard held / utility item | `mod:cobblemon/blocks/x_special_defence`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:x_speed` | Standard held / utility item | `mod:cobblemon/blocks/x_speed`; `mod:rctmod/generic/common/battle`; `craft` |
| `cobblemon:yache_berry` | Berry | `mod:cobblemon/blocks/yache_berry` 100.0%; `mod:rctmod/generic/epic/berries` |
| `cobblemon:yellow_apricorn` | Apricorn | `mod:cobblemon/blocks/yellow_apricorn` 100.0%; `mod:rctmod/generic/rare/nature` |
| `cobblemon:yellow_apricorn_seed` | Apricorn | `loot:rctmod/generic/epic/nature`; `mod:cobblemon/blocks/yellow_apricorn` 100.0%; `mod:cobblemon/blocks/potted_yellow_apricorn_sapling` +4 more |
| `cobblemon:yellow_gilded_chest` | Standard held / utility item | `mod:cobblemon/blocks/yellow_gilded_chest` 100.0%; `craft` |
| `cobblemon:yellow_plaque` | Standard held / utility item | `mod:cobblemon/blocks/yellow_plaque`; `craft` |
| `cobblemon:zinc` | Vitamin | `crate:common` 1.183%; `loot:legendarymonuments/chests/regice_chest` 8.25%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% +2 more |
| `cobblemon:zoom_lens` | Standard held / utility item | `craft`; `market` |
| `legendarymonuments:blue_feather` | EV feather | *not currently granted anywhere* |
| `legendarymonuments:clear_bell` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:cosmic_bag` | Utility / crafting material | `craft` |
| `legendarymonuments:dream_string` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:galar_particle` | Utility / crafting material | `craft` |
| `legendarymonuments:lunar_feather` | EV feather | *not currently granted anywhere* |
| `legendarymonuments:poketreat_box` | Utility / crafting material | *not currently granted anywhere* |
| `legendarymonuments:red_feather` | EV feather | *not currently granted anywhere* |
| `legendarymonuments:special_leafy_greens` | Curry ingredient — a Swords of Justice favourite | *not currently granted anywhere* |
| `legendarymonuments:special_meat_chunks` | Curry ingredient — a Swords of Justice favourite | *not currently granted anywhere* |
| `legendarymonuments:special_spices` | Curry ingredient — a Swords of Justice favourite | *not currently granted anywhere* |
| `legendarymonuments:yellow_feather` | EV feather | *not currently granted anywhere* |
| `minecraft:diamond` | Common enough at this point in progression | `loot:rctmod/generic/epic/diverse`; `loot:chests/woodland_mansion` 0.0%; `loot:legendarymonuments/chests/bell_tower_chest` 9.07% +4 more |

## T0 — Common

*Filler. Safe to hand out in bulk.*

| Item | Why | Where it comes from |
|---|---|---|
| `cobblemon:ancient_azure_ball` | Hisuian basic ball | `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:ancient_citrine_ball` | Hisuian basic ball | `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:ancient_feather_ball` | Hisuian basic ball | `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:ancient_gigaton_ball` | Hisuian basic ball | `mod:legendarymonuments/chests/heatran_cave_chest` 40.83%; `mod:rctmod/generic/epic/archeology`; `mod:rctmod/generic/epic/pokeballs` +1 more |
| `cobblemon:ancient_great_ball` | Hisuian basic ball | `mod:legendarymonuments/chests/heatran_cave_chest` 32.32%; `mod:rctmod/generic/rare/archeology`; `mod:rctmod/generic/rare/pokeballs` +1 more |
| `cobblemon:ancient_heavy_ball` | Hisuian basic ball | `mod:legendarymonuments/chests/heatran_cave_chest` 40.83%; `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs` +1 more |
| `cobblemon:ancient_ivory_ball` | Hisuian basic ball | `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:ancient_jet_ball` | Hisuian basic ball | `mod:rctmod/generic/epic/archeology`; `mod:rctmod/generic/epic/pokeballs`; `craft` |
| `cobblemon:ancient_leaden_ball` | Hisuian basic ball | `mod:legendarymonuments/chests/heatran_cave_chest` 40.83%; `mod:rctmod/generic/rare/archeology`; `mod:rctmod/generic/rare/pokeballs` +1 more |
| `cobblemon:ancient_origin_ball` | Hisuian basic ball | `craft` |
| `cobblemon:ancient_poke_ball` | Hisuian basic ball | `mod:legendarymonuments/chests/heatran_cave_chest` 48.39%; `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs` +1 more |
| `cobblemon:ancient_roseate_ball` | Hisuian basic ball | `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:ancient_slate_ball` | Hisuian basic ball | `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:ancient_ultra_ball` | Hisuian basic ball | `mod:legendarymonuments/chests/heatran_cave_chest` 22.75%; `mod:rctmod/generic/epic/archeology`; `mod:rctmod/generic/epic/pokeballs` +1 more |
| `cobblemon:ancient_verdant_ball` | Hisuian basic ball | `mod:rctmod/generic/uncommon/archeology`; `mod:rctmod/generic/uncommon/pokeballs`; `craft` |
| `cobblemon:ancient_wing_ball` | Hisuian basic ball | `mod:mega_showdown/chests/observatory_dome_chest` 100.0%; `mod:rctmod/generic/rare/archeology`; `mod:rctmod/generic/rare/pokeballs` +1 more |
| `cobblemon:antidote` | Status heal | `mod:cobblemon/blocks/antidote`; `mod:cobblemon/villages/village_pokecenters`; `mod:rctmod/generic/common/medicine` +2 more |
| `cobblemon:awakening` | Status heal | `mod:cobblemon/blocks/awakening`; `mod:cobblemon/villages/village_pokecenters`; `mod:rctmod/generic/common/medicine` +2 more |
| `cobblemon:burn_heal` | Status heal | `mod:legendarymonuments/chests/heatran_cave_chest` 40.83%; `mod:cobblemon/blocks/burn_heal`; `mod:cobblemon/villages/village_pokecenters` +3 more |
| `cobblemon:dawn_stone` | Evolution stone | `crate:common` 0.71%; `mod:mega_showdown/archaeology/observatory_sus` 12.5%; `mod:cobblemon/ruins/uncommon/gimmi_tower_frozen` +4 more |
| `cobblemon:dawn_stone_block` | Evolution stone | `mod:cobblemon/blocks/dawn_stone_block` 100.0%; `craft` |
| `cobblemon:dusk_stone` | Evolution stone | `crate:common` 0.71%; `loot:legendarymonuments/chests/turnback_cave_chest` 11.36%; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75% +4 more |
| `cobblemon:dusk_stone_block` | Evolution stone | `loot:legendarymonuments/chests/turnback_cave_vault` 2.22%; `mod:cobblemon/blocks/dusk_stone_block` 100.0%; `craft` |
| `cobblemon:elixir` | PP consumable | `loot:legendarymonuments/chests/turnback_cave_vault` 5.48%; `mod:cobblemon/shipwreck_coves/spawners/extra_normal` 59.04%; `mod:cobblemon/blocks/elixir` +3 more |
| `cobblemon:energy_root` | Herbal heal | `loot:rctmod/generic/uncommon/nature`; `mod:cobblemon/blocks/energy_root` 100.0%; `mod:cobbleworkers/archaeology_treasure` 3.45% +4 more |
| `cobblemon:ether` | PP consumable | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/shipwreck_coves/spawners/extra_normal` 59.04%; `mod:cobblemon/blocks/ether` +3 more |
| `cobblemon:exp_candy_m` | Small exp candy | `loot:legendarymonuments/chests/bell_tower_chest` 9.07%; `loot:legendarymonuments/chests/liberty_island_chest` 12.32%; `loot:legendarymonuments/chests/lugia_temple_chest` 20.5% +5 more |
| `cobblemon:exp_candy_s` | Small exp candy | `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `loot:legendarymonuments/chests/dragoeleki_chest` 74.23%; `loot:legendarymonuments/chests/liberty_island_chest` 39.31% +7 more |
| `cobblemon:exp_candy_xs` | Small exp candy | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/liberty_island_chest` 74.68%; `loot:legendarymonuments/chests/lugia_temple_chest` 91.65% +3 more |
| `cobblemon:fine_remedy` | Herbal heal | `loot:rctmod/generic/uncommon/nature`; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75% +4 more |
| `cobblemon:fire_stone` | Evolution stone | `crate:common` 0.71%; `mod:mega_showdown/archaeology/observatory_sus` 12.5%; `mod:cobblemon/sets/any_evo_stone` +2 more |
| `cobblemon:fire_stone_block` | Evolution stone | `mod:cobblemon/blocks/fire_stone_block` 100.0%; `craft` |
| `cobblemon:full_heal` | Status heal | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/shipwreck_coves/spawners/extra_ominous` 67.23%; `mod:cobblemon/shipwreck_coves/spawners/extra_normal` 59.04% +4 more |
| `cobblemon:full_restore` | Healing consumable | `loot:rctmod/generic/epic/medicine`; `loot:legendarymonuments/chests/turnback_cave_vault` 5.48%; `mod:cobblemon/blocks/full_restore` +2 more |
| `cobblemon:great_ball` | Basic ball | `crate:common` 4.1%; `loot:legendarymonuments/chests/liberty_island_chest` 74.68%; `mod:mega_showdown/chests/observatory_barrel` 33.33% +4 more |
| `cobblemon:hyper_potion` | Healing consumable | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/shipwreck_coves/spawners/extra_normal` 59.04%; `mod:legendarymonuments/chests/heatran_cave_chest` 22.75% +4 more |
| `cobblemon:ice_heal` | Status heal | `mod:cobblemon/blocks/ice_heal`; `mod:cobblemon/villages/village_pokecenters`; `mod:rctmod/generic/common/medicine` +2 more |
| `cobblemon:ice_stone` | Evolution stone | `crate:common` 0.71%; `loot:legendarymonuments/chests/regice_chest` 41.11%; `mod:cobbleworkers/archaeology_treasure` 3.45% +5 more |
| `cobblemon:ice_stone_block` | Evolution stone | `mod:cobblemon/blocks/ice_stone_block` 100.0%; `craft` |
| `cobblemon:leaf_stone` | Evolution stone | `crate:common` 0.71%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/common/prehistoric_birch_tree` +5 more |
| `cobblemon:leaf_stone_block` | Evolution stone | `mod:cobblemon/blocks/leaf_stone_block` 100.0%; `craft` |
| `cobblemon:max_elixir` | PP consumable | `loot:rctmod/generic/legendary/medicine`; `loot:legendarymonuments/chests/turnback_cave_vault` 5.48%; `mod:cobblemon/shipwreck_coves/spawners/extra_ominous` 67.23% +3 more |
| `cobblemon:max_ether` | PP consumable | `loot:rctmod/generic/epic/medicine`; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/shipwreck_coves/spawners/extra_ominous` 67.23% +3 more |
| `cobblemon:max_potion` | Healing consumable | `loot:rctmod/generic/epic/medicine`; `loot:legendarymonuments/chests/turnback_cave_vault` 5.48%; `mod:cobblemon/shipwreck_coves/spawners/extra_ominous` 67.23% +4 more |
| `cobblemon:max_revive` | Revive consumable | `loot:rctmod/generic/legendary/medicine`; `loot:legendarymonuments/chests/turnback_cave_vault` 5.48%; `mod:cobblemon/shipwreck_coves/spawners/extra_ominous` 67.23% +3 more |
| `cobblemon:medicinal_leek` | Herbal heal | `loot:rctmod/generic/uncommon/nature`; `mod:cobblemon/blocks/medicinal_leek`; `mod:cobblemon/blocks/medicinal_leek` |
| `cobblemon:moon_stone` | Evolution stone | `crate:common` 0.71%; `mod:cobblemon/sets/any_evo_stone`; `mod:rctmod/generic/rare/evolution` +1 more |
| `cobblemon:moon_stone_block` | Evolution stone | `mod:cobblemon/blocks/moon_stone_block` 100.0%; `craft` |
| `cobblemon:paralyze_heal` | Status heal | `mod:cobblemon/blocks/paralyze_heal`; `mod:cobblemon/villages/village_pokecenters`; `mod:rctmod/generic/common/medicine` +2 more |
| `cobblemon:poke_ball` | Starter ball | `mod:mega_showdown/chests/observatory_barrel` 33.33%; `mod:cobblemon/injection/chests/spawn_bonus_chest` 21.43%; `mod:cobblemon/sets/any_common_pokeball` +3 more |
| `cobblemon:potion` | Healing consumable | `mod:cobblemon/blocks/potion`; `mod:rctmod/generic/common/medicine`; `craft` +1 more |
| `cobblemon:relic_coin` | Base currency unit | `loot:legendarymonuments/chests/bell_tower_chest` 62.5%; `loot:legendarymonuments/chests/dragoeleki_chest` 74.23%; `loot:legendarymonuments/chests/liberty_island_chest` 74.68% +12 more |
| `cobblemon:relic_coin_pouch` | Currency | `loot:rctmod/generic/epic/diverse`; `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `loot:legendarymonuments/chests/dragoeleki_chest` 32.01% +7 more |
| `cobblemon:relic_coin_sack` | Currency | `loot:legendarymonuments/chests/bell_tower_chest` 9.07%; `mod:cobblemon/blocks/relic_coin_sack` 50.0%; `craft` |
| `cobblemon:remedy` | Herbal heal | `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/ruins/pots/ruins` +4 more |
| `cobblemon:revive` | Revive consumable | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/shipwreck_coves/spawners/extra_normal` 59.04%; `mod:legendarymonuments/chests/heatran_cave_chest` 32.32% +3 more |
| `cobblemon:shiny_stone` | Evolution stone | `crate:common` 0.71%; `mod:cobblemon/ruins/uncommon/gimmi_tower_deserted`; `mod:cobblemon/ruins/uncommon/gimmi_tower_temperate` +3 more |
| `cobblemon:shiny_stone_block` | Evolution stone | `mod:cobblemon/blocks/shiny_stone_block` 100.0%; `craft` |
| `cobblemon:sun_stone` | Evolution stone | `crate:common` 0.71%; `mod:cobbleworkers/archaeology_treasure` 3.45%; `mod:cobblemon/fossils/common/prehistoric_eroded_pillar` +5 more |
| `cobblemon:sun_stone_block` | Evolution stone | `mod:cobblemon/blocks/sun_stone_block` 100.0%; `craft` |
| `cobblemon:super_potion` | Healing consumable | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/blocks/super_potion`; `mod:rctmod/generic/common/medicine` +2 more |
| `cobblemon:superb_remedy` | Herbal heal | `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75%; `mod:cobblemon/sets/any_natural_heal_item` +3 more |
| `cobblemon:thunder_stone` | Evolution stone | `crate:common` 0.71%; `mod:cobblemon/sets/any_evo_stone`; `mod:rctmod/generic/uncommon/evolution` +1 more |
| `cobblemon:thunder_stone_block` | Evolution stone | `mod:cobblemon/blocks/thunder_stone_block` 100.0%; `craft` |
| `cobblemon:ultra_ball` | Basic ball | `crate:common` 3.1%; `mod:mega_showdown/chests/observatory_barrel` 33.33%; `mod:cobblemon/sets/any_common_pokeball` +3 more |
| `cobblemon:water_stone` | Evolution stone | `crate:common` 0.71%; `mod:cobbleworkers/dive_treasure` 20.0%; `mod:cobbleworkers/archaeology_treasure` 3.45% +5 more |
| `cobblemon:water_stone_block` | Evolution stone | `mod:cobblemon/blocks/water_stone_block` 100.0%; `craft` |
| `gacha:common_key` | Common Crate key — minecraft:trial_key + gacha_key tag | `crate:common` 0.5% |
| `minecraft:apple` | Vanilla bulk material | `loot:rctmod/generic/uncommon/nature` |
| `minecraft:arrow` | Vanilla item | `market` |
| `minecraft:axolotl_bucket` | Vanilla item | `loot:legendarymonuments/chests/liberty_island_chest` 39.31%; `loot:legendarymonuments/chests/lugia_temple_chest` 20.5%; `market` |
| `minecraft:blaze_rod` | Vanilla item | `loot:rctmod/generic/epic/diverse`; `market` |
| `minecraft:bone` | Vanilla bulk material | `market` |
| `minecraft:bricks` | Vanilla building block | `market` |
| `minecraft:carrot` | Vanilla item | `loot:rctmod/generic/uncommon/nature`; `loot:server/empty` 100.0%; `loot:mega_showdown/chests/observatory_barrel_2` 99.19% +2 more |
| `minecraft:coal` | Vanilla bulk material | `loot:mega_showdown/chests/observatory_barrel_2` 44.44% |
| `minecraft:cobblestone` | Vanilla bulk material | `market` |
| `minecraft:cobweb` | Vanilla item | `loot:mega_showdown/chests/observatory_barrel_2` 86.52%; `loot:mega_showdown/chests/observatory_chest` 86.52% |
| `minecraft:copper_ingot` | Vanilla bulk material | `loot:legendarymonuments/chests/turnback_cave_vault` 10.75% |
| `minecraft:creeper_banner_pattern` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:dark_prismarine` | Vanilla item | `market` |
| `minecraft:dirt` | Vanilla bulk material | `market` |
| `minecraft:disc_fragment_5` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:dragon_breath` | Vanilla item | `loot:legendarymonuments/chests/dragoeleki_chest` 32.01% |
| `minecraft:echo_shard` | Vanilla item | `loot:rctmod/generic/epic/diverse`; `loot:legendarymonuments/chests/turnback_cave_chest` 1.98% |
| `minecraft:egg` | Vanilla item | `loot:mega_showdown/chests/observatory_barrel_2` 44.44% |
| `minecraft:emerald` | Vanilla bulk material | `loot:rctmod/generic/epic/diverse`; `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `loot:legendarymonuments/chests/liberty_island_chest` 39.31% +2 more |
| `minecraft:enchanted_book` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:end_crystal` | Vanilla item | *not currently granted anywhere* |
| `minecraft:ender_eye` | Vanilla item | `crate:rare` 2.5%; `loot:rctmod/generic/epic/diverse` |
| `minecraft:ender_pearl` | Vanilla item | `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `market` |
| `minecraft:experience_bottle` | Vanilla item | `loot:rctmod/generic/epic/diverse`; `loot:legendarymonuments/chests/bell_tower_chest` 62.5% |
| `minecraft:fire_charge` | Vanilla item | *not currently granted anywhere* |
| `minecraft:firework_star` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:flower_banner_pattern` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:ghast_tear` | Vanilla item | `loot:rctmod/generic/epic/diverse`; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `market` |
| `minecraft:glistering_melon_slice` | Vanilla item | `loot:rctmod/generic/epic/nature` |
| `minecraft:globe_banner_pattern` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:glow_ink_sac` | Vanilla item | `loot:legendarymonuments/chests/liberty_island_chest` 74.68% |
| `minecraft:gold_ingot` | Vanilla bulk material | `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `loot:legendarymonuments/chests/lugia_temple_chest` 58.54%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52% |
| `minecraft:gold_nugget` | Vanilla bulk material | `loot:mega_showdown/chests/observatory_barrel_2` 61.84%; `loot:mega_showdown/chests/observatory_chest` 61.84% |
| `minecraft:golden_apple` | Vanilla item | `loot:rctmod/generic/epic/nature`; `loot:legendarymonuments/chests/bell_tower_chest` 30.22%; `loot:legendarymonuments/chests/lugia_temple_chest` 58.54% |
| `minecraft:golden_carrot` | Vanilla item | `loot:rctmod/generic/epic/nature`; `loot:legendarymonuments/chests/bell_tower_chest` 30.22% |
| `minecraft:gravel` | Vanilla bulk material | `market` |
| `minecraft:green_wool` | Vanilla item | *not currently granted anywhere* |
| `minecraft:gunpowder` | Vanilla item | `market` |
| `minecraft:iron_axe` | Vanilla item | `loot:chests/woodland_mansion` 0.0% |
| `minecraft:iron_ingot` | Vanilla bulk material | `loot:legendarymonuments/chests/registeel_chest` 35.31%; `loot:legendarymonuments/chests/turnback_cave_chest` 33.52%; `loot:legendarymonuments/chests/turnback_cave_vault` 10.75% |
| `minecraft:iron_nugget` | Vanilla bulk material | `loot:legendarymonuments/chests/registeel_chest` 78.61% |
| `minecraft:iron_sword` | Vanilla item | *not currently granted anywhere* |
| `minecraft:lapis_lazuli` | Vanilla bulk material | `loot:legendarymonuments/chests/liberty_island_chest` 74.68% |
| `minecraft:magma_cream` | Vanilla item | `market` |
| `minecraft:map` | Vanilla item | `loot:legendarymonuments/chests/liberty_island_chest` 74.68% |
| `minecraft:melon_slice` | Vanilla item | `loot:rctmod/generic/uncommon/nature` |
| `minecraft:mojang_banner_pattern` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:music_disc_11` | Vanilla item | `loot:legendarymonuments/chests/turnback_cave_chest` 11.36% |
| `minecraft:music_disc_cat` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:music_disc_otherside` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:nautilus_shell` | Vanilla item | `loot:legendarymonuments/chests/liberty_island_chest` 39.31%; `market` |
| `minecraft:nether_wart` | Vanilla item | `loot:rctmod/generic/epic/nature` |
| `minecraft:netherite_upgrade_smithing_template` | Vanilla item | `loot:rctmod/generic/legendary/archeology` |
| `minecraft:oak_log` | Vanilla building block | `market` |
| `minecraft:obsidian` | Vanilla item | *not currently granted anywhere* |
| `minecraft:ochre_froglight` | Vanilla item | `market` |
| `minecraft:pearlescent_froglight` | Vanilla item | `market` |
| `minecraft:phantom_membrane` | Vanilla item | `loot:rctmod/generic/epic/diverse`; `market` |
| `minecraft:piglin_banner_pattern` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:potato` | Vanilla item | `loot:rctmod/generic/uncommon/nature` |
| `minecraft:potion` | Healing consumable | `loot:rctmod/generic/epic/diverse` |
| `minecraft:prismarine` | Vanilla item | `market` |
| `minecraft:prismarine_crystals` | Vanilla item | `loot:rctmod/generic/epic/diverse`; `market` |
| `minecraft:prismarine_shard` | Vanilla item | `loot:rctmod/generic/epic/diverse`; `market` |
| `minecraft:pufferfish` | Vanilla item | `loot:legendarymonuments/chests/liberty_island_chest` 74.68% |
| `minecraft:pumpkin_pie` | Vanilla item | `loot:rctmod/generic/uncommon/nature` |
| `minecraft:raw_gold` | Vanilla item | `loot:legendarymonuments/chests/bell_tower_chest` 62.5% |
| `minecraft:red_wool` | Vanilla item | *not currently granted anywhere* |
| `minecraft:rotten_flesh` | Vanilla item | `loot:mega_showdown/chests/observatory_barrel_2` 61.84%; `loot:mega_showdown/chests/observatory_chest` 61.84%; `market` |
| `minecraft:sand` | Vanilla bulk material | `loot:legendarymonuments/chests/lugia_temple_chest` 91.65%; `market` |
| `minecraft:shelter_pottery_sherd` | Vanilla item | `loot:mega_showdown/chests/observatory_barrel_2` 61.84%; `loot:mega_showdown/chests/observatory_chest` 61.84% |
| `minecraft:shield` | Vanilla item | *not currently granted anywhere* |
| `minecraft:shulker_shell` | Vanilla item | `market` |
| `minecraft:skull_banner_pattern` | Vanilla item | `loot:rctmod/generic/epic/diverse` |
| `minecraft:slime_ball` | Vanilla item | `market` |
| `minecraft:smooth_stone` | Vanilla building block | `market` |
| `minecraft:sniffer_egg` | Vanilla item | `loot:legendarymonuments/chests/bell_tower_chest` 9.07% |
| `minecraft:spider_eye` | Vanilla item | `market` |
| `minecraft:spyglass` | Vanilla item | `loot:legendarymonuments/chests/liberty_island_chest` 39.31% |
| `minecraft:stone_bricks` | Vanilla building block | `market` |
| `minecraft:string` | Vanilla bulk material | `market` |
| `minecraft:sunflower` | Vanilla item | *not currently granted anywhere* |
| `minecraft:terracotta` | Vanilla item | `market` |
| `minecraft:torchflower` | Vanilla item | `loot:legendarymonuments/chests/bell_tower_chest` 30.22% |
| `minecraft:torchflower_seeds` | Vanilla item | `loot:rctmod/generic/epic/nature` |
| `minecraft:tropical_fish_bucket` | Vanilla item | `market` |
| `minecraft:turtle_egg` | Vanilla item | `loot:legendarymonuments/chests/bell_tower_chest` 9.07% |
| `minecraft:turtle_helmet` | Vanilla item | `loot:legendarymonuments/chests/liberty_island_chest` 12.32% |
| `minecraft:verdant_froglight` | Vanilla item | `market` |
| `minecraft:water_bucket` | Vanilla item | `loot:legendarymonuments/chests/lugia_temple_chest` 58.54% |
| `minecraft:white_wool` | Vanilla item | *not currently granted anywhere* |

## TX — Disabled

*Never award. Two different situations live here, and the per-item status says which: **not obtainable** (recipe banned and/or stripped from loot — if one of these is still dropping, that's a bug to fix, not a tier to change), or **banned to use** (freely obtainable, but the mechanic is disabled and using it is a bannable offence, so the item is worthless as a reward).*

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
| `mega_showdown:dynamax_band` | Dynamax is banned — item works, the mechanic does not | `craft` |
| `mega_showdown:dynamax_candy` | Dynamax is banned — item works, the mechanic does not | `craft` |
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
| `mega_showdown:gladion_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:grass_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:grassium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:ground_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:groundium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:hapu_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:hapus_z_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:ice_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:icium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:incinium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:kommonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:lunalium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:lycanium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:marshadium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:max_honey` | Dynamax is banned — item works, the mechanic does not | `craft` |
| `mega_showdown:max_mushroom` | Dynamax is banned — item works, the mechanic does not | `mod:mega_showdown/blocks/max_mushroom`; `mod:mega_showdown/blocks/max_mushroom` |
| `mega_showdown:max_soup` | Dynamax is banned — item works, the mechanic does not | `craft` |
| `mega_showdown:mewnium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:mimikium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:nanu_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:normal_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:normalium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:olivia_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:olivias_z_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:pikanium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:pikashunium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:poison_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:poisonium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:primarium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:psychic_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:psychium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:rock_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:rocket_z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:rockium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:snorlium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:solganium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:steel_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:steelium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:stellar_tera_shard` | Tera shard — Tera is banned on this server | *not currently granted anywhere* |
| `mega_showdown:sweet_max_soup` | Dynamax is banned — item works, the mechanic does not | `craft` |
| `mega_showdown:tapunium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:tera_orb` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_black` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_blue` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_brown` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_cyan` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_gray` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_green` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_light_blue` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_light_gray` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_lime` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_magenta` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_orange` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_pink` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_purple` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_red` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_white` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:tera_pouch_yellow` | Tera enabler — Tera is banned on this server | `craft` |
| `mega_showdown:ultranecrozium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:water_tera_shard` | Tera is banned on this server — stripped from monument chest loot (was up to 95%/chest) | *not currently granted anywhere* |
| `mega_showdown:waterium_z` | Z-crystal — disabled on this server | *not currently granted anywhere* |
| `mega_showdown:z_power_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:z_ring` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:z_ring_black` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:z_ring_blue` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:z_ring_green` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:z_ring_pink` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:z_ring_red` | Z-Ring — enabler for the disabled Z-crystals | `craft` |
| `mega_showdown:z_ring_yellow` | Z-Ring — enabler for the disabled Z-crystals | `craft` |

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

