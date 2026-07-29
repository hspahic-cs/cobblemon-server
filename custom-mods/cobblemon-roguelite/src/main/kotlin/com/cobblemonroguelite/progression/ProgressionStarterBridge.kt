package com.cobblemonroguelite.progression

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonroguelite.starter.StarterIvFloor
import com.cobblemonroguelite.starter.StarterProgression
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/progression")

/**
 * Answers the two questions starter selection asks of progression, from the store.
 *
 * [StarterProgression] is declared on the *consumer* side, by `starter`, precisely so that neither
 * package has to know the other's types — selection asks in its own terms ([StarterIvFloor], a base
 * cost to discount) and gets an answer without ever seeing a candy count. This class is the only
 * translation between the two vocabularies. The one other place this package touches `starter` at all
 * is [CandyCommands], which reads a species' base cost so the candy shop quotes the same price the
 * store will charge — a lookup, not a translation.
 *
 * ### Why it holds a server
 *
 * The store is world [net.minecraft.world.level.saveddata.SavedData], reached through
 * `server.overworld().dataStorage`, and [StarterProgression]'s methods take a player UUID and nothing
 * else — correctly, since selection has no business threading a server through its pure parts. So the
 * server is bound here, once, when the server starts, and released when it stops. Holding it in a
 * long-lived object across a stop is what leaks an entire integrated-server world, which is why
 * [onServerStopped] exists rather than being left to the next start to overwrite.
 *
 * ### Failure is base, not an exception
 *
 * Every lookup degrades to the pre-progression answer if the store cannot be read. A starter offer
 * that throws is a run the player has already been charged for and cannot start (see
 * [com.cobblemonroguelite.starter.StarterFactory] on that direction of failure); a starter offer at
 * base prices and base IVs is a worse offer that still plays. Only one of those is recoverable.
 */
class ProgressionStarterBridge(private val server: MinecraftServer) : StarterProgression {

    override fun effectiveCost(player: UUID, species: ResourceLocation, baseCost: Int): Int =
        runCatching { RunProgression.effectiveStarterCost(server, player, species, baseCost) }
            .onFailure { log.warn("roguelite: could not price {} for {} — using base cost", species, player, it) }
            .getOrDefault(baseCost)

    override fun ivFloor(player: UUID, species: ResourceLocation): StarterIvFloor =
        runCatching { RunProgression.ivFloor(server, player, species).asStarterFloor() }
            .onFailure { log.warn("roguelite: could not read an IV floor for {} — using base", species, it) }
            .getOrDefault(StarterIvFloor.Base)

    /**
     * Our [IvFloor] in the shape selection wants.
     *
     * Both sides independently define "base 10" — [IvFloor.BASE_IV] here, [StarterIvFloor.BASE] there —
     * and that duplication is deliberate rather than sloppy: it is what lets `starter` be complete and
     * playable with no store registered at all. This translation is the one place they meet, so if
     * they ever disagree it shows up here as a floor that changes when the store loads, not as a
     * silent difference between two code paths.
     */
    private fun IvFloor.asStarterFloor(): StarterIvFloor = StarterIvFloor(
        mapOf(
            Stats.HP to hp,
            Stats.ATTACK to attack,
            Stats.DEFENCE to defence,
            Stats.SPECIAL_ATTACK to specialAttack,
            Stats.SPECIAL_DEFENCE to specialDefence,
            Stats.SPEED to speed,
        ),
    )
}

/**
 * Binds the store to starter selection for the lifetime of a server.
 *
 * A game-bus listener rather than something the mod class does at setup, because there is no server
 * at setup time and the store is world data. Registered in
 * [com.cobblemonroguelite.CobblemonRoguelite] beside the other game-bus listeners.
 */
object ProgressionHooks {

    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        StarterProgression.set(ProgressionStarterBridge(event.server))
        log.debug("roguelite: starter selection is reading candy reductions and IV floors from the store")
    }

    /**
     * Unbind on shutdown, so nothing holds the stopped server.
     *
     * Also the correct state to leave behind: [StarterProgression.Base] is the answer a server with no
     * store gives, and an integrated server reopening a world will bind a fresh one at its next start.
     */
    @SubscribeEvent
    fun onServerStopped(event: ServerStoppedEvent) {
        StarterProgression.reset()
    }
}
