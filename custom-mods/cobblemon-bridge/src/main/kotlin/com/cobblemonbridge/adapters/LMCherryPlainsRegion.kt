package com.cobblemonbridge.adapters

import com.mojang.datafixers.util.Pair
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import terrablender.api.Region
import terrablender.api.RegionType
import java.util.function.Consumer

/**
 * Replicates LegendaryMonuments 7.8's `cherry_plains` TerraBlender region for NeoForge — the
 * one Sinytra Connector skipped because LM's registration runs through the Fabric-side
 * `terrablender` entrypoint that NeoForge TB 4.1 doesn't read.
 *
 * Climate parameters extracted from the bytecode of
 * `LegendaryMonumentsTerraBlender$1.addBiomes` in the LM 7.8 jar (after Connector remap to
 * Mojmap). Each row is one of LM's three `addBiome` calls, in the same parameter order as
 * `Climate.parameters(temperature, humidity, continentalness, erosion, depth, weirdness, offset)`.
 *
 * If LM 7.9+ retunes these spans, this class will register the 7.8 values; LM bumps that
 * change them should re-extract from the new bytecode. The shape (3 points, weight 3, OVERWORLD)
 * is unlikely to drift, but the float spans might.
 */
class LMCherryPlainsRegion(
    name: ResourceLocation,
    type: RegionType,
    weight: Int,
    private val biomeKey: ResourceKey<Biome>,
) : Region(name, type, weight) {

    override fun addBiomes(
        registry: Registry<Biome>,
        mapper: Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>>,
    ) {
        // Point 1
        addBiome(
            mapper,
            Climate.parameters(
                Climate.Parameter.span(-0.2f, 0.5f),    // temperature
                Climate.Parameter.span(-0.4f, 0.4f),    // humidity
                Climate.Parameter.span(0.35f, 1.0f),    // continentalness
                Climate.Parameter.span(-0.8f, -0.3f),   // erosion
                Climate.Parameter.span(0.0f, 0.0f),     // depth
                Climate.Parameter.span(-0.3f, 0.3f),    // weirdness
                0.0f,                                   // offset
            ),
            biomeKey,
        )
        // Point 2
        addBiome(
            mapper,
            Climate.parameters(
                Climate.Parameter.span(-0.1f, 0.6f),
                Climate.Parameter.span(-0.5f, 0.5f),
                Climate.Parameter.span(0.4f, 0.9f),
                Climate.Parameter.span(-0.75f, -0.25f),
                Climate.Parameter.span(0.0f, 0.0f),
                Climate.Parameter.span(-0.4f, 0.4f),
                0.0f,
            ),
            biomeKey,
        )
        // Point 3
        addBiome(
            mapper,
            Climate.parameters(
                Climate.Parameter.span(0.0f, 0.7f),
                Climate.Parameter.span(-0.3f, 0.6f),
                Climate.Parameter.span(0.5f, 1.0f),
                Climate.Parameter.span(-0.7f, -0.2f),
                Climate.Parameter.span(0.0f, 0.0f),
                Climate.Parameter.span(-0.5f, 0.5f),
                0.0f,
            ),
            biomeKey,
        )
    }
}
