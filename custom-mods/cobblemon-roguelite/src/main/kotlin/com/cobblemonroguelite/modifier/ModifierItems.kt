package com.cobblemonroguelite.modifier

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.item.components.HeldItemEffectComponent
import com.cobblemonroguelite.run.RunItems
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/**
 * PokéRogue's stacking battle modifiers as **tiered player held items** — §2.33's harness, built on
 * the channel §2.32's boss shields proved.
 *
 * ### The mapping this whole file exists to exploit
 *
 * A run Pokémon holds a real `ItemStack`, and Cobblemon decides what Showdown sees via
 * `BaseCobblemonHeldItemManager.showdownId`: it reads the stack's
 * `cobblemon:held_item_effect` data component **first**, and only falls back to the item's registry
 * path when the component is absent. So the visible item is pure theatre — a spyglass carrying
 * `{showdownId: "multilens1"}` *is* Multi Lens tier 1 inside the simulator, and no Minecraft item is
 * registered anywhere (registering one would make this mod client-required; §2.32 spent real
 * argument on not paying that price).
 *
 * This is the same component the boss shields write, but from the other direction: shields ride on
 * *opponent team generation*, where a properties-string fragment names the component and no stack
 * ever exists ([com.cobblemonroguelite.boss.BossShields.heldItemProperty]). A player-side modifier
 * has to be a stack the Pokémon actually holds — so it is minted here, and **run-marked**
 * ([RunItems.mark]) so the isolation machinery voids it with the run like every other piece of run
 * property.
 *
 * ### The component's `consumed` flag is the item's death warrant
 *
 * `CobblemonHeldItemManager.shouldConsumeItem` reads it when Showdown reports the item used
 * (`-enditem`): `true` deletes the real ItemStack, `false` keeps it for the next wave. Multi Lens is
 * never consumed in battle, so its flag is inert-but-false; Reviver Seed sets `true` because §2.34
 * ruled the seed IS consumed — one revive, then the stack is gone for good.
 *
 * ### Tier is identity, not quantity (§2.34)
 *
 * Showdown gives a set one item id and no free fields, so a tier travels as *which* item is held:
 * `multilens1` and `multilens2` are separate datapack items
 * (`data/cobblemon_roguelite/held_items/multi_lens_*.js`), exactly like `bossshield1..5`. A reward
 * pick therefore never adds a second copy — [decide] turns "you already hold tier 1" into "replace
 * with tier 2", and a holder at the ceiling gets a NoEffect rather than a duplicate.
 *
 * ### The same two-sided contract as [com.cobblemonroguelite.boss.BossShields]
 *
 * The behaviour lives in GraalJS where no unit test can see it; what is tested here is the contract
 * that breaks silently if it drifts: the Showdown id a given (modifier, tier) produces must match
 * the `name:` in the JS file, or the Pokémon quietly holds an item Showdown has never heard of —
 * which, from Cobblemon's point of view, is a perfectly normal Pokémon holding nothing.
 */
enum class PlayerModifier(
    /** The id reward tables use: `{"type": "modifier_item", "item": "multi_lens"}`. */
    val id: String,
    /** Showdown id prefix. Tiered lines append the tier (`multilens` -> `multilens2`). */
    val showdownBase: String,
    /** How many tiers ship, i.e. how many JS files exist. The ceiling is OUR balance choice (§2.34). */
    val maxTier: Int,
    /**
     * The visible item the stack is minted from. Vanilla on purpose: always present, never
     * client-required, and an honest failure — a stack that loses its component degrades to a plain
     * spyglass that maps to no Showdown item, not to some other item.
     */
    val baseItem: ResourceLocation,
    /** Written into the component; see the class docs for what Cobblemon does with it. */
    val consumed: Boolean,
    val displayName: String,
    /** One line for the between-wave menu. */
    val blurb: String,
) {
    /**
     * PokéRogue's Multi Lens (their MASTER pool): each stack converts 25% of an attack's damage
     * into an additional strike. Tier 1 = 2 hits at 75%/25%, tier 2 = 3 hits at 50%/25%/25%.
     * The split arithmetic is [firstHitPermille]'s spec; the JS implements it via
     * `onModifyMove` -> `move.multihit` plus an `onModifyDamage` chain (§2.34).
     */
    MULTI_LENS(
        id = "multi_lens",
        showdownBase = "multilens",
        maxTier = 2,
        baseItem = ResourceLocation.withDefaultNamespace("spyglass"),
        consumed = false,
        displayName = "Multi Lens",
        blurb = "converts 25% of attack damage into an extra strike per tier",
    ),

    /**
     * PokéRogue's Reviver Seed (their ULTRA pool, weight 4): when the holder would faint, it
     * survives and recovers to half HP instead — once, and the seed is consumed.
     */
    REVIVER_SEED(
        id = "reviver_seed",
        showdownBase = "reviverseed",
        maxTier = 1,
        baseItem = ResourceLocation.withDefaultNamespace("beetroot_seeds"),
        consumed = true,
        displayName = "Reviver Seed",
        blurb = "survive a would-be faint at half HP, once — then it is gone",
    ),
    ;

    companion object {
        /** The reward-table id lookup. Null rather than throwing: parse reports, it does not crash. */
        fun byId(id: String): PlayerModifier? = entries.firstOrNull { it.id == id }
    }
}

object ModifierItems {

    /**
     * The Showdown item id for [modifier] at [tier]. **Must match `name:` in the JS file**,
     * lowercased and stripped of spaces — `"Multi Lens 1"` -> `multilens1`, `"Reviver Seed"` ->
     * `reviverseed`. A single-tier line carries no numeral, on both sides, so the two cannot agree
     * by accident while disagreeing in shape.
     */
    fun showdownId(modifier: PlayerModifier, tier: Int): String {
        require(tier in 1..modifier.maxTier) { "tier must be 1..${modifier.maxTier} for ${modifier.id}, was $tier" }
        return if (modifier.maxTier == 1) modifier.showdownBase else "${modifier.showdownBase}$tier"
    }

    /**
     * Which tier of [modifier] the id [heldShowdownId] represents, or 0 when it is not one of this
     * line's ids at all. Matched as an exact id per tier rather than by prefix, for the same reason
     * [com.cobblemonroguelite.boss.BossShields.isShieldItem] is: somebody else's `multilens9000`
     * must not read as ours.
     */
    fun tierHeld(modifier: PlayerModifier, heldShowdownId: String?): Int {
        if (heldShowdownId == null) return 0
        return (1..modifier.maxTier).firstOrNull { showdownId(modifier, it) == heldShowdownId } ?: 0
    }

    /** What granting a modifier pick should do to this holder. */
    sealed interface Decision {
        /** Put [tier] on the holder. [upgradedFrom] is the displaced tier, null on a fresh grant. */
        data class Grant(val tier: Int, val upgradedFrom: Int?) : Decision

        /** Holder is at the line's ceiling; the pick must not overwrite a max item with itself. */
        data object AlreadyMax : Decision
    }

    /**
     * §2.34's rule, as a pure function: *"a reward pick is always upgrade 1→2, never add a second."*
     *
     * Holding a lower tier of the same line upgrades it; holding the top tier is [Decision.AlreadyMax];
     * holding anything else — nothing, a berry, a different modifier line — is a fresh tier-1 grant
     * that displaces whatever was there (the displacement is the granter's problem to report, same as
     * every other held-item reward).
     */
    fun decide(modifier: PlayerModifier, heldShowdownId: String?): Decision {
        val held = tierHeld(modifier, heldShowdownId)
        return when {
            held == 0 -> Decision.Grant(1, null)
            held >= modifier.maxTier -> Decision.AlreadyMax
            else -> Decision.Grant(held + 1, held)
        }
    }

    /**
     * "Multi Lens II", "Reviver Seed". Roman numerals because a trailing arabic digit reads as a
     * count, and the whole point of §2.34's stacking model is that this is NOT a count of items.
     * Single-tier lines take no numeral at all.
     */
    fun displayName(modifier: PlayerModifier, tier: Int): String {
        require(tier in 1..modifier.maxTier) { "tier must be 1..${modifier.maxTier} for ${modifier.id}, was $tier" }
        if (modifier.maxTier == 1) return modifier.displayName
        return "${modifier.displayName} ${ROMAN[tier - 1]}"
    }

    /** What the item does at [tier], stated as the player will experience it. Shown as lore. */
    fun effectLore(modifier: PlayerModifier, tier: Int): String {
        require(tier in 1..modifier.maxTier) { "tier must be 1..${modifier.maxTier} for ${modifier.id}, was $tier" }
        return when (modifier) {
            PlayerModifier.MULTI_LENS -> when (tier) {
                1 -> "Attacks strike twice: 75% + 25% power."
                else -> "Attacks strike three times: 50% + 25% + 25% power."
            }
            PlayerModifier.REVIVER_SEED ->
                "When the holder would faint, it survives at half HP instead. Consumed on use."
        }
    }

    /**
     * The lore line every minted modifier carries, so nobody plans their post-run life around one.
     * Stated because it is true twice over: the stack is run-marked, and the void at run exit is
     * exactly the marker-keyed deletion [RunItems] exists to scope.
     */
    const val RUN_ITEM_LORE = "Run item — vanishes when the run ends."

    /**
     * Mint the real stack: base item + `held_item_effect` component (the mapping — see the class
     * docs) + name/lore + run mark. Impure end of this file; everything above it is the tested part.
     *
     * Empty when the base item is unresolvable, which for a vanilla id means something is deeply
     * wrong — the caller reports it the same way as any other missing-id grant.
     */
    fun mintStack(modifier: PlayerModifier, tier: Int, runSeed: Long): ItemStack {
        val item = BuiltInRegistries.ITEM.getOptional(modifier.baseItem).orElse(null) ?: return ItemStack.EMPTY
        val stack = ItemStack(item, 1)
        stack.set(
            CobblemonItemComponents.HELD_ITEM_EFFECT,
            HeldItemEffectComponent(showdownId(modifier, tier), modifier.consumed),
        )
        // Vanilla renders custom names in italics (a "renamed at an anvil" signal that is noise
        // here); the style override keeps the name plain so it reads as what the item IS.
        stack.set(
            DataComponents.CUSTOM_NAME,
            Component.literal(displayName(modifier, tier)).withStyle { it.withItalic(false) },
        )
        stack.set(
            DataComponents.LORE,
            ItemLore(
                listOf(effectLore(modifier, tier), RUN_ITEM_LORE).map { line ->
                    Component.literal(line).withStyle { it.withItalic(false) }
                },
            ),
        )
        return RunItems.mark(stack, runSeed)
    }

    /**
     * The Showdown id a held stack maps to via the component channel, or null when the stack does
     * not carry one — which for [decide]'s purposes correctly lumps "holding Leftovers" with
     * "holding nothing our mapping knows about".
     */
    fun heldShowdownId(stack: ItemStack): String? =
        if (stack.isEmpty) null else stack.get(CobblemonItemComponents.HELD_ITEM_EFFECT)?.showdownId

    private val ROMAN = listOf("I", "II", "III", "IV", "V")
}
