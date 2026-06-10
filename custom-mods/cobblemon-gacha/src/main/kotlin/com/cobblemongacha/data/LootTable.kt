package com.cobblemongacha.data

/** Tier banner within a loot table: drives lore/announcements (e.g. "(HIGH)" tag). */
enum class LootTier { Floor, Mid, High, Jackpot }

/**
 * One materialisable item inside a `LootEntry`. Four forms (sealed):
 *   - `Vanilla` — a regular vanilla or modded item id with count and optional name/lore overrides.
 *   - `GachaKeyRef` — emit a Common/Rare/Ultra Key ItemStack (so jackpot entries can grant keys).
 *   - `Placeholder` — emit a placeholder ItemStack (vouchers, TBD ultra rewards).
 *   - `CobbreedingEgg` — pick a species from the named egg pool and dispatch the Cobbreeding
 *     `givepokemonegg` command at grant time. Doesn't materialise into a single ItemStack
 *     (the egg is created server-side by Cobbreeding); `RewardGranter` short-circuits this case.
 *
 * `RewardGranter` walks one of these into an actual `ItemStack` (or, for `CobbreedingEgg`, a
 * server-side command dispatch).
 */
sealed class ItemSpec {
    data class Vanilla(
        val id: String,
        val count: Int,
        val nameOverride: String? = null,
        val loreLines: List<String> = emptyList(),
    ) : ItemSpec()

    data class GachaKeyRef(val tier: KeyTier, val count: Int) : ItemSpec()

    /** kind: "voucher" | "tbd_ultra" — picks the vanilla base item. */
    data class Placeholder(val kind: String, val label: String, val count: Int) : ItemSpec()

    /**
     * Cobbreeding Pokémon egg. `pool` references a rarity tier in `EggPools` ("common",
     * "uncommon", "rare", "ultra_rare"). At grant time, RewardGranter picks a random species
     * from the whole pool and runs `givepokemonegg`; Hidden Ability (`ha=yes`) is granted when
     * the rolled species is flagged `hasHiddenAbility` in the pool — it is NOT gated by this spec.
     *
     * `requireHiddenAbility` is retained only for JSON back-compat (older tables / the admin
     * `giveegg ... ha` override) and no longer filters the pool on a normal pull.
     */
    data class CobbreedingEgg(
        val pool: String,
        val shiny: Boolean = false,
        val requireHiddenAbility: Boolean = false,
    ) : ItemSpec()

    /**
     * Pick one item id at random from [ids] and grant [count] of it. Used for the gacha
     * "Legendary Monument" rewards, which the loot table maps to a random pedestal block
     * from the LegendaryMonuments mod with equal probability across all entries.
     */
    data class RandomItem(val ids: List<String>, val count: Int = 1) : ItemSpec()

    /**
     * Enchanted book carrying a single stored enchantment at the given level. Materialises to
     * `minecraft:enchanted_book` with `DataComponents.STORED_ENCHANTMENTS` set so the book can
     * be applied at an anvil. [enchantment] is a fully-qualified id (e.g. `minecraft:silk_touch`).
     */
    data class EnchantedBook(
        val enchantment: String,
        val level: Int,
        val count: Int = 1,
    ) : ItemSpec()
}

/**
 * One row in a loot table. `weightPct` is the raw percentage from the CSV (before normalisation).
 * 0% entries are kept in the table but skipped by RewardRoller (used to record unfinished entries).
 * `label` is the human-readable string shown in announcements; copied verbatim from the CSV "Item" cell.
 * `items` is the list of stacks delivered if this entry is rolled (one entry may bundle several stacks).
 */
data class LootEntry(
    val lootTier: LootTier,
    val label: String,
    val weightPct: Double,
    val items: List<ItemSpec>,
    val notes: String = "",
)

/**
 * A whole loot table. `entries` preserves CSV order. `totalWeightPct` is the raw sum of `weightPct`
 * before normalisation — kept so admins editing the JSON can see if their odds drift from 100%.
 */
data class LootTable(
    val tier: KeyTier,
    val totalWeightPct: Double,
    val entries: List<LootEntry>,
)
