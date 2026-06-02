package com.cobblemonbridge.adapters

import com.cobblemonbridge.CobblemonBridge
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent

/**
 * Compat shim: invoke Legendary Monuments' Fabric `terrablender` entrypoint, which Sinytra
 * Connector does not pass through to NeoForge TerraBlender on 1.21.1.
 *
 * **What's broken upstream.** LM 7.8 is a Fabric mod loaded via Connector. Its TerraBlender
 * region (`legendarymonuments:cherry_plains`, plus its climate-parameter ranges) is registered
 * in `github.jorgaomc.world.biome.LegendaryMonumentsTerraBlender#onTerraBlenderInitialized`,
 * declared in `fabric.mod.json` under `entrypoints.terrablender`. NeoForge TerraBlender 4.1
 * doesn't read Fabric entrypoints, and Connector doesn't translate this one. Result: LM's
 * `main` entrypoint fires (structures/dimensions/items all register), the biome JSON loads, but
 * `Regions.register(...)` is never called, so the biome has no parameter footprint and never
 * generates. Visible symptom: `/locate biome legendarymonuments:cherry_plains` reports "could
 * not find ... within a reasonable distance" because vanilla locate-biome traverses a fixed
 * radius and the biome is empty everywhere.
 *
 * Confirmed on 0.7.43 dev: the only TB region registrations in `latest.log` are the two
 * vanilla defaults (`minecraft:overworld`, `minecraft:nether`). No LM line.
 *
 * **What this does.** Subscribes to [ServerAboutToStartEvent] at [EventPriority.HIGHEST] so we
 * run before TB's own `LOWEST`-priority handler consumes the region map, then reflectively
 * instantiates LM's entrypoint class and invokes `onTerraBlenderInitialized()`. That method
 * itself does the `Regions.register(new Region(... CHERRY_PLAINS ...))` call with LM's
 * authored climate parameters — we don't reimplement parameter math, we just fire the call
 * that Connector skipped.
 *
 * **Reflection vs. compile-time dep.** LM's class only exists at runtime, loaded by Connector
 * from a remapped jar. Compiling against it would force LM onto our compile classpath and
 * break builds where LM is absent. Reflection keeps cobblemon-bridge usable standalone, with
 * graceful degradation if LM isn't installed (or if LM 7.9+ renames the class — see caveat).
 *
 * **Caveat.** Pinned to `github.jorgaomc.world.biome.LegendaryMonumentsTerraBlender`. If LM
 * upstream renames or restructures that class, this shim silently no-ops with a WARN line.
 * If LM ships a real NeoForge-native build (not Connector-loaded), this shim becomes
 * redundant — they'll register their region the normal way. Safe to leave registered;
 * `ModList.isLoaded("legendarymonuments")` is the gate.
 *
 * **Limited scope.** LM only. Terralith ships zero entrypoints (pure datapack); making it work
 * via TerraBlender is a different mechanism and is out of scope here.
 *
 * **Existing chunks unaffected.** Worldgen runs once per chunk. Chunks generated before this
 * shim shipped won't grow a cherry_plains biome retroactively — the biome will only appear in
 * newly explored wilderness.
 */
object LegendaryMonumentsTerraBlenderShim {

    private const val LM_MOD_ID = "legendarymonuments"
    private const val LM_TERRABLENDER_CLASS = "github.jorgaomc.world.biome.LegendaryMonumentsTerraBlender"
    private const val LM_TERRABLENDER_METHOD = "onTerraBlenderInitialized"

    /**
     * Run before TerraBlender's own server-about-to-start handler (which is registered at
     * [EventPriority.LOWEST] — see `terrablender.core.TerraBlenderNeoForge`). NeoForge fires
     * higher priorities first, so this lands while TB's region map is still open.
     */
    @Suppress("UNUSED_PARAMETER")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        if (ModList.get()?.isLoaded(LM_MOD_ID) != true) return
        try {
            val cls = Class.forName(LM_TERRABLENDER_CLASS)
            val instance = cls.getDeclaredConstructor().newInstance()
            cls.getMethod(LM_TERRABLENDER_METHOD).invoke(instance)
            CobblemonBridge.logger.info(
                "Invoked $LM_TERRABLENDER_CLASS.$LM_TERRABLENDER_METHOD() (Connector terrablender-entrypoint shim)"
            )
        } catch (_: ClassNotFoundException) {
            CobblemonBridge.logger.debug(
                "LM detected but $LM_TERRABLENDER_CLASS not on classpath — entrypoint class likely renamed in this LM build, skipping shim"
            )
        } catch (t: Throwable) {
            CobblemonBridge.logger.warn(
                "LM TerraBlender shim failed; cherry_plains biome will not generate this run",
                t,
            )
        }
    }
}
