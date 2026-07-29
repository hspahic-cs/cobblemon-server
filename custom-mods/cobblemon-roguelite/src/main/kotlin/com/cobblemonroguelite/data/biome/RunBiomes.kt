package com.cobblemonroguelite.data.biome

import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import net.minecraft.resources.ResourceLocation

/**
 * Every run biome on the server, loaded from `data/<namespace>/roguelite/biomes/<name>.json`.
 *
 * ### The file
 *
 * ```json
 * {
 *   "display_name": "Grassy Field",
 *   "arena_template": "ns:arena/grassland",
 *   "minecraft_biome": "minecraft:plains",
 *   "min_wave": 1,
 *   "max_wave": 40,
 *   "weight": 1.0
 * }
 * ```
 *
 * One biome per file rather than a list in one file, unlike every other registry here. The reason is
 * the id: a biome is named in a run's checkpoint ([com.cobblemonroguelite.run.BiomeVisit]), so it
 * needs a stable id of its own, and this registry already derives one from the file path. Entries
 * inside a list would need an `id` field that duplicates the filename and can disagree with it.
 *
 * ### Containment: a bad file costs that biome and nothing else
 *
 * The same split [com.cobblemonroguelite.data.reward.RewardTables] makes rather than
 * [com.cobblemonroguelite.data.trainer.TrainerRosters]' whole-file rejection, because the blast
 * radius is different: a roster with a band missing leaves a wave with no opponent, whereas a biome
 * that fails to load simply is not offered — the rotation draws from the ones that did, and the run
 * carries on somewhere else. A file with no biome definitions at all is likewise not an error state:
 * an unconfigured server keeps [com.cobblemonroguelite.arena.ArenaConfig.templates]' build and the
 * arena dimension's own biome, which is exactly how the mode behaves today.
 *
 * ### What is deliberately *not* checked
 *
 * That [RunBiome.minecraftBiome] names a biome that exists, and that [RunBiome.arenaTemplate] names a
 * structure that exists. Both registries are populated by the same datapack reload this parse runs
 * inside, so a check here would report ids that are about to be perfectly valid. Both are reported at
 * use time instead — by [com.cobblemonroguelite.arena.ArenaStamper] and
 * [com.cobblemonroguelite.arena.ArenaBiomePainter] — which are the layers that actually looked.
 */
object RunBiomes : RogueliteDataRegistry<RunBiome>("biomes") {

    /**
     * The biomes a run may be in at [wave], in an order that does not depend on how they were loaded.
     *
     * Sorted for [com.cobblemonroguelite.wave.WildWaveGenerator]'s reason, which is the one that
     * matters here too: the rotation is a weighted walk down this list, so the same set delivered in a
     * different order picks a different biome for the same seed. Datapack iteration order is not a
     * thing we control, so a run resumed after a restart would find itself somewhere else — with a
     * different arena build — for no reason a player could ever explain.
     *
     * Zero-weight entries are dropped here rather than by the caller, so "disabled" means the same
     * thing to every reader of this registry.
     */
    fun eligibleAt(wave: Int): List<RunBiome> = eligibleIn(entries.values, wave)

    /**
     * The same question over a collection somebody else holds.
     *
     * Split out for one reason: [entries] is only ever populated by a datapack reload, so everything
     * above would otherwise ship having never run — and "which biomes may a run enter at wave 41" is
     * the kind of decision that is wrong quietly. This overload is what the tests drive.
     */
    fun eligibleIn(biomes: Collection<RunBiome>, wave: Int): List<RunBiome> =
        biomes
            .filter { it.weight > 0.0 && it.covers(wave) }
            .sortedWith(compareBy({ it.id.toString() }, { it.weight }))

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): RunBiome? {
        val before = problems.count

        val displayName = root.requireString("display_name")
        val template = parseId(root, "arena_template")
        val biome = parseId(root, "minecraft_biome")
        val minWave = root.optionalInt("min_wave") ?: 1
        val maxWave = root.optionalInt("max_wave")
        val weight = root.optionalDouble("weight") ?: 1.0
        root.expectNoUnknownKeys()

        if (displayName != null && displayName.isBlank()) {
            // Blank is refused rather than defaulted to the id: this string is what a player is told
            // they have walked into, and "you have entered " is a message that reads as a bug.
            root.problem("display_name", "must not be blank — it is what the player is told they entered")
        }
        if (minWave < 1) root.problem("min_wave", "waves are 1-based, was $minWave")
        if (maxWave != null && maxWave < minWave) {
            root.problem("max_wave", "$maxWave is before min_wave $minWave, so this biome could never be entered")
        }

        // Any problem at all drops the file, including one raised for a field that has a default:
        // a biome loaded with a mis-typed template is a biome that stamps the wrong build for ten
        // waves, and that reads as a content mistake rather than as the parse error it is.
        if (problems.count != before) return null
        return RunBiome(
            id = id,
            displayName = displayName!!,
            arenaTemplate = template!!,
            minecraftBiome = biome!!,
            minWave = minWave,
            maxWave = maxWave,
            weight = weight,
        )
    }

    private fun parseId(view: JsonView, field: String): ResourceLocation? {
        val text = view.requireString(field) ?: return null
        return ResourceLocation.tryParse(text) ?: run {
            view.problem(field, "'$text' is not a valid id (expected namespace:path)")
            null
        }
    }
}
