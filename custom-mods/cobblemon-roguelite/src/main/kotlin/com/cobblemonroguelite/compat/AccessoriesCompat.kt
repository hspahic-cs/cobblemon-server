package com.cobblemonroguelite.compat

import com.cobblemonroguelite.integration.StashSlotProvider
import io.wispforest.accessories.api.AccessoriesCapability
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * The D3 worn-slot provider for the Accessories API — which on this modpack is also what covers
 * Sophisticated Backpacks' back slot, since SB registers through Accessories.
 *
 * ### The classloading contract, stated because it is load-bearing
 *
 * This class references `io.wispforest` types **only in method bodies and private helpers** — its
 * supertype is our own [StashSlotProvider] and it has no fields of Accessories types — so the JVM's
 * lazy constant-pool resolution means the class can be *loaded* on a server without Accessories and
 * only explodes if a method actually runs. It never runs there: registration happens behind
 * `ModList.isLoaded("accessories")` in the mod's init, which is the same soft-dependency pattern the
 * arena uses for Mega Showdown's power spot. Compiled against the exact version the modpack pins
 * (`accessories-neoforge-1.1.0-beta.53+1.21.1`, hash-verified), because a worn-slot API drifting
 * under a compat class is how "supported" becomes "silently skips a slot".
 *
 * ### Containers, not `getAllEquipped()`
 *
 * The capability offers `getAllEquipped()`, and it is deliberately not used: it can surface *nested*
 * entries (accessories inside accessories — `NestedSlotReferenceImpl` exists), and a snapshot that
 * captured a worn backpack's stack **and** its nested entries separately would restore both — a
 * duplication, the exact failure the isolation design ranks worse than loss. Iterating the top-level
 * containers captures each worn stack once; whatever rides inside it rides inside its NBT.
 *
 * ### Cosmetic slots are included
 *
 * `AccessoriesContainer` has a functional container and a cosmetic one, and a cosmetic slot holds a
 * real `ItemStack` — snapshotting only the functional side would leave a cosmetic slot as the one
 * worn pocket the stash cannot see, which is invariant 3 lost to a fashion feature. Cosmetic keys
 * carry a marker segment so restore puts them back on the right side.
 *
 * ### After every mutation, `markChanged`
 *
 * The accessories containers are `SimpleContainer`s under the hood, but Accessories syncs on its own
 * change tracking — mutating without marking is how a cleared slot keeps rendering its item until
 * the next relog. Whether the sync is complete under mid-tick mutation is on the live-verify list
 * (design §12.4 neighbours it).
 */
object AccessoriesCompat : StashSlotProvider {

    override val id: String = "accessories"

    /** Key shape: `slotName#index` functional, `slotName!c#index` cosmetic. Slot names are plain
     *  identifiers ("back", "ring"), but parsing still anchors on the LAST '#' so a hostile name
     *  cannot shift the index. */
    private const val COSMETIC_MARK = "!c"

    override fun slots(player: ServerPlayer): Map<String, ItemStack> {
        val capability = AccessoriesCapability.get(player) ?: return emptyMap()
        val found = LinkedHashMap<String, ItemStack>()
        capability.containers.forEach { (name, container) ->
            val functional = container.accessories
            for (i in 0 until functional.containerSize) {
                val stack = functional.getItem(i)
                if (!stack.isEmpty) found["$name#$i"] = stack.copy()
            }
            val cosmetic = container.cosmeticAccessories
            for (i in 0 until cosmetic.containerSize) {
                val stack = cosmetic.getItem(i)
                if (!stack.isEmpty) found["$name$COSMETIC_MARK#$i"] = stack.copy()
            }
        }
        return found
    }

    override fun clear(player: ServerPlayer, slotKey: String) {
        withSlot(player, slotKey) { container, index, cosmetic ->
            target(container, cosmetic).setItem(index, ItemStack.EMPTY)
            container.markChanged()
        }
    }

    override fun restore(player: ServerPlayer, slotKey: String, stack: ItemStack): Boolean =
        withSlot(player, slotKey) { container, index, cosmetic ->
            val slots = target(container, cosmetic)
            if (index >= slots.containerSize) return@withSlot false // slot config shrank mid-run
            if (!slots.getItem(index).isEmpty) return@withSlot false // occupied — engine falls back
            slots.setItem(index, stack)
            container.markChanged()
            true
        } ?: false

    private fun target(
        container: io.wispforest.accessories.api.AccessoriesContainer,
        cosmetic: Boolean,
    ) = if (cosmetic) container.cosmeticAccessories else container.accessories

    private fun <T> withSlot(
        player: ServerPlayer,
        slotKey: String,
        action: (io.wispforest.accessories.api.AccessoriesContainer, Int, Boolean) -> T,
    ): T? {
        val hash = slotKey.lastIndexOf('#')
        if (hash <= 0) return null
        var name = slotKey.substring(0, hash)
        val index = slotKey.substring(hash + 1).toIntOrNull() ?: return null
        val cosmetic = name.endsWith(COSMETIC_MARK)
        if (cosmetic) name = name.removeSuffix(COSMETIC_MARK)
        val container = AccessoriesCapability.get(player)?.containers?.get(name) ?: return null
        return action(container, index, cosmetic)
    }
}
