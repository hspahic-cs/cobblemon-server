package com.cobblemonroguelite.data

import com.cobblemon.mod.common.data.CobblemonDataProvider
import com.cobblemonroguelite.data.biome.RunBiomes
import com.cobblemonroguelite.data.payout.PayoutTables
import com.cobblemonroguelite.data.reward.RewardTables
import com.cobblemonroguelite.data.starter.HiddenAbilityTables
import com.cobblemonroguelite.data.starter.StarterCostTables
import com.cobblemonroguelite.data.trainer.TrainerRosters
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/data")

/**
 * Hands this mod's datapack registries to Cobblemon's reload lifecycle.
 *
 * ### Where this has to be called from, and why it is not called from here
 *
 * [registerAll] must run **inside `FMLCommonSetupEvent`'s `enqueueWork`**, i.e.
 *
 * ```kotlin
 * modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(RogueliteData::registerAll) }
 * ```
 *
 * Not in the `@Mod` constructor and not in the parallel body of common setup. Cobblemon calls
 * `CobblemonDataProvider.registerDefaults()` from its own common-setup handler, which NeoForge
 * dispatches to mods *in parallel*, and the provider's registry list is a plain `LinkedHashSet`.
 * Registering from our parallel body would be two threads mutating one unsynchronized set — a race
 * whose visible symptom is a registry that silently never reloads. `enqueueWork` runs after the
 * whole parallel dispatch, on the main thread, which removes the race and additionally guarantees
 * Cobblemon's own registries are in the list ahead of ours.
 *
 * Registration order matters beyond the race: the provider reloads in insertion order, so being
 * after Cobblemon means a table naming a Cobblemon move or item is read once that registry has
 * answered for itself.
 *
 * ### Why `reloadable = true`
 *
 * Cobblemon marks its own species-shaped registries non-reloadable because live-reloading a species
 * would invalidate Pokémon already in the world. Nothing here is like that: a reward table is read
 * at the moment a reward is rolled and holds no references into live objects, so `/reload` is safe
 * and is the entire point of §2.12 choosing a datapack over a config file. A run in progress simply
 * rolls from the new table on its next wave.
 */
object RogueliteData {

    /**
     * Register every datapack registry this mod owns. Call exactly once. See the class docs for the
     * one place it may be called from.
     *
     * New data-driven features add a line here rather than growing their own reload plumbing: the
     * lifecycle, the failure policy, and the error reporting all live in [RogueliteDataRegistry],
     * and a second copy of them would be a second set of rules for how a bad file behaves.
     */
    fun registerAll() {
        CobblemonDataProvider.register(RewardTables, reloadable = true)
        CobblemonDataProvider.register(PayoutTables, reloadable = true)
        // Reloadable like the rest, with one consequence worth knowing: editing a roster mid-run
        // changes who an in-flight run meets at waves it has not reached yet, the same way editing
        // a reward table changes what it rolls next. Bands and pools are ordered, and selection
        // indexes into them — see TrainerBand.trainers.
        CobblemonDataProvider.register(TrainerRosters, reloadable = true)
        // Reloadable, with the same consequence and one more: a price edit reaches a *pending* start,
        // because the catalogue is rebuilt from the pool every time it is shown (§2.13's budget is
        // not seeded, so there is nothing snapshotted to go stale). A run already under way is
        // unaffected — its party was bought and built at `chooseStarters`.
        CobblemonDataProvider.register(StarterCostTables, reloadable = true)
        // Reloadable, and read at two moments that are hours apart: the candy shop quotes what an
        // unlock grants, and a starter build grants it (§2.27). Editing this between the two means a
        // player was quoted one ability and handed another — which is why the grant re-reads the
        // table rather than trusting anything carried from the purchase, and why a species that lost
        // its assignment logs rather than silently falling back.
        CobblemonDataProvider.register(HiddenAbilityTables, reloadable = true)
        // Reloadable, and this is the one whose reload a player can *see*: editing a biome file
        // changes which build is stamped and which Minecraft biome is painted at the next wave, so a
        // run mid-band will find its arena rebuilt around it. That is the intended behaviour — it is
        // how an author iterates on an arena without restarting — but it is worth knowing before
        // editing one on a live server.
        CobblemonDataProvider.register(RunBiomes, reloadable = true)
        log.info("roguelite: datapack registries registered — tables load from data/<namespace>/{}/", RogueliteDataRegistry.ROOT)
    }
}
