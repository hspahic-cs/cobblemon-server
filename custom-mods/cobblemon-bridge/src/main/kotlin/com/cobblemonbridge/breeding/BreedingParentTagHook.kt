package com.cobblemonbridge.breeding

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.eggs.BredTagHook
import com.cobblemonbridge.eggs.CobreedingBridge
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.component.CustomData
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID

/**
 * Breeding restriction (parent side): **a Pokémon used as a breeding parent can't be traded.**
 *
 * Why a tick monitor instead of an event subscriber: Cobblemon's `COLLECT_EGG` event only fires for
 * Cobblemon's *native* daycare. This server breeds via Cobreeding, which has its own egg system and
 * never emits `COLLECT_EGG` (verified: its bytecode references neither CollectEggEvent nor the
 * native collect-egg path). So the previous event-based hook never ran and parents stayed tradeable.
 *
 * Instead we watch Cobreeding's per-pasture egg state ([CobreedingBridge.pastureEggCounts], backed
 * by `PastureBreedingData.registry`). When a pasture's egg count rises, an egg was just laid, so we
 * tag every Pokémon currently tethered to that pasture as a breeding parent
 * ([BredTagHook.markBreedingParent]). The trade gates ([com.cobblemonbridge.wild.TradeCapHook] and
 * [com.cobblemonbridge.trade.TradeManager]) then refuse them via [BredTagHook.isTradeLocked].
 *
 * The child side is handled separately and still works — Cobreeding *does* trigger
 * `HATCH_EGG_POST` (via its `hatchEgg` call), so [BredTagHook] tags every hatched mon.
 *
 * A Ditto used as a parent is tagged too; the rule is about trading, not breeding eligibility.
 */
object BreedingParentTagHook {

    private const val TICKS_PER_SECOND = 20
    private var subTickCounter = 0

    /** Last observed egg count per pasture, so we only act on the tick an egg is newly laid. */
    private val lastEggCount = HashMap<BlockPos, Int>()

    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        subTickCounter++
        if (subTickCounter < TICKS_PER_SECOND) return
        subTickCounter = 0

        val counts = CobreedingBridge.pastureEggCounts() ?: return
        for ((pos, count) in counts) {
            val prev = lastEggCount[pos] ?: 0
            if (count > prev) tagParentsAt(event.server, pos)
        }
        // Bound the map to currently-active pastures, then record this tick's counts.
        lastEggCount.keys.retainAll(counts.keys)
        lastEggCount.putAll(counts)
    }

    /** Tag the parents tethered to the pasture at [pos], and stamp the newly-laid egg(s) with the
     *  breeder's UUID so the hatched mon can be routed to the breeder's PC. The registry is keyed by
     *  BlockPos only, so we scan loaded levels for the matching pasture block entity (pastures are
     *  few; this only runs on the tick an egg is laid). */
    private fun tagParentsAt(server: MinecraftServer, pos: BlockPos) {
        for (level in server.allLevels) {
            val pasture = level.getBlockEntity(pos) as? PokemonPastureBlockEntity ?: continue
            // The breeder = the pasture owner. Every tethered mon in a pasture belongs to one
            // player, so any tethering's playerId is the owner.
            val breeder: UUID? = pasture.tetheredPokemon.firstOrNull()?.playerId
            var tagged = 0
            for (tethering in pasture.tetheredPokemon) {
                val pokemon = tethering.getPokemon() ?: continue
                if (!BredTagHook.isBreedingParent(pokemon)) {
                    BredTagHook.markBreedingParent(pokemon)
                    // persistentData edits don't emit a tracked change, so flag the owning store
                    // dirty manually — otherwise the tag is in-memory only and lost on restart.
                    pokemon.storeCoordinates.get()?.store?.onPokemonChanged(pokemon)
                    tagged++
                }
            }
            if (breeder != null) stampEggsWithBreeder(pos, breeder)
            if (tagged > 0) {
                CobblemonBridge.logger.info(
                    "breeding-trade-lock: tagged {} breeding parent(s) at {} as non-tradeable (egg laid)",
                    tagged, pos,
                )
            }
            return
        }
    }

    /** Stamp every not-yet-stamped egg in the pasture's egg inventory with the breeder UUID, so the
     *  baby is routed to the breeder's PC on hatch (regardless of who incubates it). */
    private fun stampEggsWithBreeder(pos: BlockPos, breeder: UUID) {
        val eggs = CobreedingBridge.pastureEggsAt(pos) ?: return
        for (egg in eggs) {
            val existing = egg.get(DataComponents.CUSTOM_DATA)
            val tag = existing?.copyTag() ?: net.minecraft.nbt.CompoundTag()
            if (tag.contains(BredTagHook.EGG_BREEDER_KEY)) continue  // already stamped
            tag.putString(BredTagHook.EGG_BREEDER_KEY, breeder.toString())
            egg.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        }
    }
}
