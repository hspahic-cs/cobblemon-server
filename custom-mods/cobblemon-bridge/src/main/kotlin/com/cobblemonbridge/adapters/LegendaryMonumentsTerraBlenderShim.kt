package com.cobblemonbridge.adapters

import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Biome
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import terrablender.api.Regions
import terrablender.api.RegionType

/**
 * Compat shim: register Legendary Monuments' `cherry_plains` biome with NeoForge TerraBlender,
 * which Sinytra Connector skipped because LM uses a Fabric-side `terrablender` entrypoint that
 * NeoForge TB 4.1 doesn't read.
 *
 * **History.**
 * - 0.7.44: tried `Class.forName(...)` on LM's entrypoint and reflectively call its
 *   `onTerraBlenderInitialized()`. Failed — LM declares `implements terrablender.api.TerraBlenderApi`,
 *   which NeoForge TB 4.1 doesn't ship (NeoForge entrypoints are `@Mod`-class). Threw
 *   `NoClassDefFoundError` before reaching the constructor.
 * - 0.7.45: tried to ship a stub `terrablender.api.TerraBlenderApi` interface in cobblemon-bridge.
 *   Bricked the JVM with a JPMS split-package error (`cobblemon_bridge` and `terrablender`
 *   modules can't both export `terrablender.api`).
 * - 0.7.46 (this): don't load LM's class at all. Replicate what its `addBiomes` would have done,
 *   in our own [LMCherryPlainsRegion] subclass of `terrablender.api.Region`, against TB's actual
 *   NeoForge API. We never reference LM's class symbolically, so no missing-supertype error.
 *
 * **Why a sourceSet of stubs.** TerraBlender isn't on a Maven we have access to, so we vendor
 * three signature-only files in `libs/terrablender-stubs-src/` to compile our subclass against.
 * Stubs aren't shipped in the runtime jar; the JVM resolves `terrablender.api.*` from TB's real
 * jar at runtime.
 *
 * **Climate parameters** are LM 7.8's, extracted by disassembling
 * `LegendaryMonumentsTerraBlender$1.addBiomes`. Three points, region weight 3, type OVERWORLD.
 * If LM 7.9+ retunes them, the biome still spawns — just in slightly different climates than
 * the new LM intended. Worth re-extracting on LM bumps.
 *
 * **Existing chunks unaffected.** Worldgen runs once per chunk; pre-shim chunks won't grow
 * cherry_plains retroactively. Newly explored wilderness will.
 */
object LegendaryMonumentsTerraBlenderShim {

    private const val LM_MOD_ID = "legendarymonuments"
    private const val REGION_WEIGHT = 3

    /**
     * Run before TerraBlender's own `ServerAboutToStartEvent` handler (registered at
     * `EventPriority.LOWEST` — see `terrablender.core.TerraBlenderNeoForge`). NeoForge fires
     * higher priorities first, so this lands while TB's region map is still open.
     */
    @Suppress("UNUSED_PARAMETER")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        if (ModList.get()?.isLoaded(LM_MOD_ID) != true) return
        try {
            val biomeKey: ResourceKey<Biome> = ResourceKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(LM_MOD_ID, "cherry_plains"),
            )
            val region = LMCherryPlainsRegion(
                name = ResourceLocation.fromNamespaceAndPath(LM_MOD_ID, "cherry_plains"),
                type = RegionType.OVERWORLD,
                weight = REGION_WEIGHT,
                biomeKey = biomeKey,
            )
            Regions.register(region)
            CobblemonBridge.logger.info(
                "Registered LM region {}:cherry_plains (3 climate points, OVERWORLD, weight={})",
                LM_MOD_ID, REGION_WEIGHT,
            )
        } catch (_: NoClassDefFoundError) {
            // TB not on classpath at runtime — shouldn't happen if LM is loaded (LM depends on
            // TB), but guard anyway so a missing TB jar doesn't crash bridge.
            CobblemonBridge.logger.debug(
                "TerraBlender API not on runtime classpath, skipping LM cherry_plains shim"
            )
        } catch (t: Throwable) {
            CobblemonBridge.logger.warn(
                "LM TerraBlender shim failed; cherry_plains biome will not generate this run",
                t,
            )
        }
    }
}
