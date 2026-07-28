package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.data.payout.PayoutTables
import com.cobblemonroguelite.starter.PlaceholderStarterPoolSource
import com.cobblemonroguelite.starter.StarterPoolSource
import net.minecraft.resources.ResourceLocation

/**
 * Everything the run loop is tuned by, in one object.
 *
 * ### What is decided here and what is deliberately absent
 *
 * The **shapes** are decided: a badge gate is a list of advancement ids with a depth each, a run
 * pays from a named table, a starter pool is a source. The **values** mostly are not, and the
 * defaults say so — [RunDepthGate.UNGATED] and [PlaceholderStarterPoolSource] are both documented
 * placeholders rather than balance. Two things that look like values are not: [starterLevel] is 1
 * because §2.21 decided it, and [composition] carries PokéRogue's own curve because §2.19 adopted
 * their run length and therefore their constants.
 *
 * There is **no fee amount** here and there never can be — §2.18 prices a run in currency, this
 * module has no economy (§2.2), and a number here would be denominated in a unit it cannot name.
 * See [com.cobblemonroguelite.integration.RunCharges].
 *
 * ### Where this is loaded from
 *
 * Nowhere yet, and that is on purpose. A config file and a datapack are both defensible and the
 * choice interacts with reload semantics that the mode has no live runs to care about yet, so what
 * exists today is the shape plus [RunSettings.set] — enough for our host mod to configure it at
 * setup and enough for a loader to be dropped in without moving anything.
 *
 * @property depthGate §2.18. Ours points at `gym_01`–`gym_10`; what ships points at nothing.
 * @property payoutTable which table a run pays from. Copied onto [RunState.payoutTable] at run start
 *   and read from there at run end — see that property for why a run pins it rather than re-reading
 *   this at the end of a multi-day run.
 * @property starterLevel §2.21. A run starter begins at 1 and levels on the curve by battle EXP.
 */
data class RunConfig(
    val depthGate: RunDepthGate = RunDepthGate.UNGATED,
    val composition: WaveCompositionConfig = WaveCompositionConfig(),
    val payoutTable: ResourceLocation = PayoutTables.DEFAULT_TABLE,
    val starterPool: StarterPoolSource = PlaceholderStarterPoolSource,
    val starterLevel: Int = 1,
) {
    init {
        require(starterLevel >= 1) { "starterLevel must be at least 1, was $starterLevel" }
    }
}

/**
 * The live configuration and the one [WaveComposition] built from it.
 *
 * [composition] is rebuilt on [set] rather than on every read: it is stateless and shared by every
 * concurrent run (the class says so), so one instance per configuration is correct and constructing
 * one per wave would be pure waste. `@Volatile` for the same reason the integration seams are —
 * configuration arrives from another mod's setup thread and is read from the server thread.
 */
object RunSettings {

    @Volatile
    var current: RunConfig = RunConfig()
        private set

    @Volatile
    var composition: WaveComposition = WaveComposition(current.composition)
        private set

    fun set(config: RunConfig) {
        current = config
        composition = WaveComposition(config.composition)
    }

    /** Restore the shipped defaults. For tests and for unloading a server-side integration. */
    fun reset() = set(RunConfig())
}
