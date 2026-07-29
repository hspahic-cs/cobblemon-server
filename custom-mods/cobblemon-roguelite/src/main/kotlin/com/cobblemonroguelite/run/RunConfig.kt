package com.cobblemonroguelite.run

import com.cobblemonroguelite.arena.ArenaConfig
import com.cobblemonroguelite.arena.ArenaLayout
import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.data.payout.PayoutTables
import com.cobblemonroguelite.starter.DefaultStarterCosts
import com.cobblemonroguelite.starter.PlaceholderStarterPoolSource
import com.cobblemonroguelite.starter.StarterCostSource
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
 * @property trainerRoster which roster a run's trainer and boss waves come from. Copied onto
 *   [RunState.trainerRoster] at run start for the same reason, and it is the sharper case of the two:
 *   a payout is re-read once at the end, whereas this would otherwise be re-read on every one of two
 *   hundred waves, so an operator swapping rosters would change the ladder under a run halfway up it.
 *   See [RunRosters].
 * @property starterCosts §2.13's price per species. The default layers the server's datapack table
 *   over a derived fallback — see [DefaultStarterCosts] for why a published build cannot ship the
 *   real prices and what it uses instead.
 * @property starterBudget §2.13. Points a player has to spend on their starting team. See
 *   [RunConfig.DEFAULT_STARTER_BUDGET] for why this one is a decision and not a placeholder.
 * @property starterLevel §2.21. A run starter begins at 1 and levels on the curve by battle EXP.
 * @property biomeBandLength §2.24: how many waves a run spends in one biome. 10 is PokéRogue's own
 *   region cadence and is a decision rather than a placeholder, in the way [starterBudget] is —
 *   raising it does not make transitions rarer so much as change what a run *is*, since the biome is
 *   also the arena build and, later, the wild pool. It is a length and not a list of bands because a
 *   band with no biome eligible for it is a hole ([BiomeRotation] keeps the previous biome), and
 *   uniform bands are the shape that cannot have holes in the first place.
 * @property arena where runs are fought and how many can be fought at once. Unlike the rest of this
 *   class its defaults are real rather than placeholders — the grid works out of the box — with the
 *   single exception of [com.cobblemonroguelite.arena.ArenaTemplates.default], which names a build
 *   nobody has made. See there for why that fails loudly instead of degrading.
 */
data class RunConfig(
    val depthGate: RunDepthGate = RunDepthGate.UNGATED,
    val composition: WaveCompositionConfig = WaveCompositionConfig(),
    val payoutTable: ResourceLocation = PayoutTables.DEFAULT_TABLE,
    val trainerRoster: ResourceLocation = RunRosters.DEFAULT_ROSTER,
    val starterPool: StarterPoolSource = PlaceholderStarterPoolSource,
    val starterCosts: StarterCostSource = DefaultStarterCosts,
    val starterBudget: Int = DEFAULT_STARTER_BUDGET,
    val starterLevel: Int = 1,
    val biomeBandLength: Int = DEFAULT_BIOME_BAND_LENGTH,
    val arena: ArenaConfig = ArenaConfig(),
) {
    init {
        require(starterLevel >= 1) { "starterLevel must be at least 1, was $starterLevel" }
        // Zero would be a division by zero inside the band arithmetic rather than a run with no
        // biomes; "no biomes" is what an empty biomes folder means, and it is the shipped state.
        require(biomeBandLength >= 1) { "biomeBandLength must be at least 1, was $biomeBandLength" }
        // Zero would be a run with no Pokémon in it. Nothing above enforces a *useful* budget — a
        // budget below the cheapest species is a legal configuration that refuses every start, and it
        // is refused at run start with a message naming both numbers rather than silently here.
        require(starterBudget >= 1) { "starterBudget must be at least 1, was $starterBudget" }
    }

    companion object {
        /**
         * §2.13's budget. Configurable, and unlike most numbers in this class it is a decision rather
         * than a placeholder: 10 is PokéRogue's, and the cost table that gives it meaning is priced
         * against it. Raising it without re-pricing does not make runs slightly stronger — it changes
         * two-or-three Pokémon into a full party, which is a different mode.
         */
        const val DEFAULT_STARTER_BUDGET = 10

        /** §2.24: PokéRogue changes region every ten waves, and a run is 200 waves (§2.19). */
        const val DEFAULT_BIOME_BAND_LENGTH = 10
    }
}

/**
 * The live configuration and the two derived objects built from it.
 *
 * [composition] is rebuilt on [set] rather than on every read: it is stateless and shared by every
 * concurrent run (the class says so), so one instance per configuration is correct and constructing
 * one per wave would be pure waste. `@Volatile` for the same reason the integration seams are —
 * configuration arrives from another mod's setup thread and is read from the server thread.
 *
 * [arenaLayout] is here for the same reason and one sharper one: the arena spawn suppressor consults
 * it on **every Cobblemon spawn on the server**, which is the hottest path this mod touches, and
 * building a layout per spawn would put an allocation in it for no reason.
 */
object RunSettings {

    @Volatile
    var current: RunConfig = RunConfig()
        private set

    @Volatile
    var composition: WaveComposition = WaveComposition(current.composition)
        private set

    @Volatile
    var arenaLayout: ArenaLayout = current.arena.layout()
        private set

    fun set(config: RunConfig) {
        current = config
        composition = WaveComposition(config.composition)
        arenaLayout = config.arena.layout()
    }

    /** Restore the shipped defaults. For tests and for unloading a server-side integration. */
    fun reset() = set(RunConfig())
}
