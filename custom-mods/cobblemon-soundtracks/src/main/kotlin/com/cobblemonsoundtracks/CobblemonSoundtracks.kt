package com.cobblemonsoundtracks

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Client-only mod that gives each custom Multiworld dimension its own looping
 * soundtrack.
 *
 * Mechanism: NeoForge fires [net.neoforged.neoforge.client.event.SelectMusicEvent]
 * every time the [net.minecraft.client.sounds.MusicManager] decides what
 * situational music to play. [SoundtrackManager] listens for it and, when the
 * player is in one of our mapped dimensions (`multiworld:spawn`,
 * `multiworld:elite4`, `multiworld:arena*`), overrides the selection with a
 * per-world [net.minecraft.sounds.Music] track. In every other dimension —
 * crucially the survival overworld, i.e. the wilderness — we don't touch the
 * event, so vanilla music plays as normal.
 *
 * The music itself is a set of `.ogg` files shipped inside this jar under
 * `assets/cobblemon_soundtracks/sounds/music/<world>/`, grouped into one
 * SoundEvent per world (see [ModSounds]). A SoundEvent with several variants
 * makes the MusicManager pick a random track each cycle, which is what gives
 * each world a rotating playlist rather than a single looped song.
 *
 * Why a mod and not a datapack: Minecraft's biome `music` field is per-biome,
 * not per-dimension. The custom worlds share a flat/void biome, so biome music
 * can neither tell them apart nor avoid leaking into the wilderness. Driving it
 * off the dimension id from the client is the only clean way to get distinct
 * per-world music with the wilderness left vanilla.
 */
@Mod(value = CobblemonSoundtracks.MOD_ID, dist = [Dist.CLIENT])
class CobblemonSoundtracks(modBus: IEventBus) {

    init {
        logger.info("Cobblemon Soundtracks initializing (client)...")
        // SoundEvents are registry objects → mod bus.
        ModSounds.SOUND_EVENTS.register(modBus)
        // SelectMusicEvent is fired on the game bus (NeoForge.EVENT_BUS).
        NeoForge.EVENT_BUS.register(SoundtrackManager)
        logger.info("Cobblemon Soundtracks initialized")
    }

    companion object {
        // Mod ids / resource-location namespaces can't contain '-'; use '_'.
        const val MOD_ID = "cobblemon_soundtracks"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
