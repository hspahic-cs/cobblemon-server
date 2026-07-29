package com.cobblemonroguelite.data.biome

import com.cobblemonroguelite.arena.ArenaBuild
import net.minecraft.resources.ResourceLocation

/**
 * One place a run can be, as §2.24 means "place": a build to stand in and a Minecraft biome painted
 * over it.
 *
 * ### Why a biome carries its arena rather than sitting beside one
 *
 * §2.19 already re-stamps the arena when the wave band's build changes, and §2.24 says biomes *are*
 * those bands. Two lists — one saying which build wave 40 gets, another saying which biome it gets —
 * would be two answers to one question, and they would disagree the first time somebody edited one of
 * them: a volcano build under a snowy sky, with nothing in the log to say which half was wrong. So
 * the build id lives here, and [com.cobblemonroguelite.arena.ArenaConfig.builds] stays as the answer
 * for a server that has no biomes configured at all.
 *
 * ### The palette is the reason this is authorable
 *
 * §2.29: a biome names an [ArenaBuild.Palette] — floor block, rim block, two numbers — and the
 * atmosphere comes from [minecraftBiome]'s repaint rather than from the geometry. That is what makes
 * a biome something a server owner who cannot build can write. [ArenaBuild.Template] is still
 * accepted for one who can.
 *
 * @property displayName what the player is told they have walked into. Free text and not a
 *   translation key, for [com.cobblemonroguelite.run.RunMessages]' reason — there is no language file
 *   to put a key in.
 * @property arenaBuild what stands in the arena while the run is here — §2.29's generated palette,
 *   or a hand-built `.nbt` for an owner who has one. Resolved by
 *   [com.cobblemonroguelite.arena.ArenaStamper], which is the layer that can say whether it exists; a
 *   build named here and missing on disk fails loudly there rather than quietly at load.
 * @property minecraftBiome what the arena box is repainted to. Not checked at parse time either, and
 *   for a sharper reason than the template: the biome registry is built from datapacks that load
 *   alongside ours, so an id that resolves at paint time may well not resolve while this file is
 *   being read.
 * @property minWave first wave this biome may be entered on. The band bounds are what stop a seeded
 *   rotation putting the endgame's biome at wave 1 — without them a "path through biomes" is a
 *   shuffle, and §2.24 wants progression.
 * @property maxWave inclusive, null for open-ended.
 * @property weight relative likelihood among the biomes eligible for a band. **Zero or less disables
 *   the entry** — the same convention [com.cobblemonroguelite.wave.WaveSpecies] uses, and it is what
 *   lets this mod ship an example file that documents the schema without the mode actually sending
 *   anybody there.
 */
data class RunBiome(
    val id: ResourceLocation,
    val displayName: String,
    val arenaBuild: ArenaBuild,
    val minecraftBiome: ResourceLocation,
    val minWave: Int = 1,
    val maxWave: Int? = null,
    val weight: Double = 1.0,
) {
    fun covers(wave: Int): Boolean = wave >= minWave && (maxWave == null || wave <= maxWave)
}
