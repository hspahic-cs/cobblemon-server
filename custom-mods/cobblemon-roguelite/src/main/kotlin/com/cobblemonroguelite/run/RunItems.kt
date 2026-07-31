package com.cobblemonroguelite.run

import com.cobblemon.mod.common.CobblemonItemComponents
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * The item-side twin of [RunPartySwap.RUN_MARKER_KEY]: which stacks belong to a run.
 *
 * ### One classifier, used by every destructive path
 *
 * The isolation design (`docs/roguelite-run-isolation.md` §7.5) has exactly one rule about deleting
 * items: **marker-keyed deletion of run property is the only destructive act permitted anywhere.**
 * That rule is only as safe as the classifier is consistent, so voiding, sweeping, capturing into the
 * run bag and the exit partition must all ask this object and never reimplement the test — a rule
 * that is right in one place and wrong in another deletes somebody's property in the wrong one.
 *
 * ### Where the marker lives
 *
 * `minecraft:custom_data`, under [MARKER_KEY], holding the run's seed. Custom data rather than an
 * attachment because it is plain vanilla NBT: it survives serialization everywhere an ItemStack
 * does, it is visible to `/data get`, and — the load-bearing part, §4 of the design — **any other
 * mod on the host can read it with no compile dependency on this one**, which is what makes
 * host-side guards possible without violating §2.9's standalone rule.
 *
 * ### The F14 read-through, corrected against the actual component
 *
 * The design (F14) requires marker tests to read *through* a Cobblemon `PokemonItem` stack into the
 * contained Pokémon's `persistentData`, on the premise that a run Pokémon serialized into an item
 * carries its marker one level down. On Cobblemon 1.7.3 that premise is false in a way that makes
 * the situation better, not worse: `PokemonItemComponent` holds only `(species, aspects, tint)` — a
 * *rendering reference*, not a serialized Pokémon. There is no `persistentData` inside the stack to
 * read, and equally no way to get the original Pokémon back out of one (`asPokemon` mints a fresh
 * Pokémon from the species). So a `PokemonItem` cannot smuggle a run Pokémon.
 *
 * What remains true is that a `PokemonItem` in a tagged player's inventory is *suspicious* — our
 * GUIs mint them as display copies and block every dupe vector, so none should ever reach a real
 * inventory. [isSuspectPokemonItem] names that case so the exit partition can quarantine it with a
 * sharper log line than a generic unmarked stack; it is deliberately not classified as run property,
 * because deleting on suspicion is the exact thing the one-rule design forbids.
 */
object RunItems {

    /** Namespaced for the same reason [RunPartySwap.STASH_SLOT_KEY] is: `custom_data` is a shared bag. */
    const val MARKER_KEY = "cobblemon_roguelite:run_item"

    /**
     * Stamp [stack] as property of the run with [runSeed].
     *
     * Returns the same stack for call-site convenience. Marking an empty stack is a no-op rather than
     * an error, because callers mint from data (`BuiltInRegistries.ITEM.getOptional(...)`) and an
     * unresolvable id has already been reported by the time the empty stack gets here.
     */
    fun mark(stack: ItemStack, runSeed: Long): ItemStack {
        if (stack.isEmpty) return stack
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag -> tag.putLong(MARKER_KEY, runSeed) }
        return stack
    }

    /** Whether [stack] is run property — the only test any destructive path may use. */
    fun isRunItem(stack: ItemStack): Boolean =
        !stack.isEmpty && stack.get(DataComponents.CUSTOM_DATA)?.contains(MARKER_KEY) == true

    /** The seed the stack was marked with, or null when it is not run property. */
    fun seedOf(stack: ItemStack): Long? {
        if (stack.isEmpty) return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        if (!data.contains(MARKER_KEY)) return null
        return data.copyTag().getLong(MARKER_KEY)
    }

    /**
     * A `PokemonItem` display stack loose in a real inventory — see the class docs. Not run property;
     * the exit partition quarantines it under its own log line.
     */
    fun isSuspectPokemonItem(stack: ItemStack): Boolean =
        !stack.isEmpty && stack.has(CobblemonItemComponents.POKEMON_ITEM)
}
