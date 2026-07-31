package com.cobblemonroguelite.integration

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/integration")

/**
 * Inventory the vanilla 41 slots cannot see — Accessories slots, a Sophisticated Backpacks back
 * slot, whatever a host straps to a player (isolation design D3/§3).
 *
 * ### Why a seam and not direct compat code
 *
 * §2.9: this module depends on Cobblemon and nothing else. Accessories is live on our server but is
 * not a dependency this jar may take at compile time without a classloading guard, and a *published*
 * build has no idea what worn-slot mods a host runs. So the module owns the seam and the driving
 * logic (snapshot, void, clear, restore — all in `RunInventoryStash`), and a host or a guarded
 * compat module registers the mod-specific slot access here, exactly like [RunTrainerBattles] and
 * the AI provider.
 *
 * ### The contract is slots, not behaviour
 *
 * Three primitives, all dumb on purpose: enumerate `(slotKey, stack)`, clear one slot, put a stack
 * back into one slot. Everything with a policy — which stacks are run property, what happens to
 * residue, what order things run in — stays in the engine, where it is written once and tested. A
 * provider that took "snapshotAndClear" whole would end up owning half the durability protocol.
 *
 * ### Fail closed, and the engine enforces it
 *
 * A provider that throws during enumeration or clearing refuses arena entry (design E0): a run that
 * starts with an unreadable worn slot is a run with a hidden bag, which is invariant 3 lost before
 * the first wave. The engine wraps every call; a provider absent at restore time makes its section
 * residue — kept, never dropped.
 */
interface StashSlotProvider {

    /** Stable id, used as the snapshot section name. A renamed id orphans its sections into residue. */
    val id: String

    /** Every worn slot this provider manages, keyed stably. Empty slots may be omitted. */
    fun slots(player: ServerPlayer): Map<String, ItemStack>

    /** Empty one slot. Missing keys are a no-op, not an error — the slot may have emptied itself. */
    fun clear(player: ServerPlayer, slotKey: String)

    /**
     * Put [stack] back into [slotKey]. False when the slot refused it (shrunk config, occupied,
     * key unknown) — the engine falls back to the ordinary inventory rather than losing it.
     */
    fun restore(player: ServerPlayer, slotKey: String, stack: ItemStack): Boolean
}

/** The registry, same shape as every other integration seam: hosts add, tests reset. */
object StashSlotProviders {

    @Volatile
    private var providers: List<StashSlotProvider> = emptyList()

    val all: List<StashSlotProvider> get() = providers

    fun register(provider: StashSlotProvider) {
        if (providers.any { it.id == provider.id }) {
            // Same-id re-registration replaces, because a reload re-running host setup must not stack
            // duplicates — but it is logged, because two MODS colliding on an id would silently
            // shadow each other's slots.
            log.warn("roguelite: stash slot provider '{}' replaced", provider.id)
            providers = providers.filterNot { it.id == provider.id } + provider
            return
        }
        providers = providers + provider
        log.info("roguelite: stash slot provider '{}' registered", provider.id)
    }

    fun reset() {
        providers = emptyList()
    }
}
