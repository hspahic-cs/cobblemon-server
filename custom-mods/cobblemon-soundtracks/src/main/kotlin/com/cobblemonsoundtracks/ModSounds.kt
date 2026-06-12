package com.cobblemonsoundtracks

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * Registers one [SoundEvent] per soundtracked world. Each event id matches a
 * group of `.ogg` files declared in `assets/cobblemon_soundtracks/sounds.json`;
 * when an event lists several sounds, the MusicManager picks one at random each
 * time it starts a track, which is what makes a world's playlist rotate.
 *
 * The event ids here MUST stay in sync with the keys generated into sounds.json
 * by `ops/soundtracks/build-soundtracks.py`.
 */
object ModSounds {
    val SOUND_EVENTS: DeferredRegister<SoundEvent> =
        DeferredRegister.create(Registries.SOUND_EVENT, CobblemonSoundtracks.MOD_ID)

    /** `multiworld:spawn` — main hub. */
    val MUSIC_SPAWN: DeferredHolder<SoundEvent, SoundEvent> = register("music.spawn")

    /** `multiworld:elite4` — Elite Four gauntlet. */
    val MUSIC_ELITE4: DeferredHolder<SoundEvent, SoundEvent> = register("music.elite4")

    /** `multiworld:arena*` — all PvP arenas share this playlist. */
    val MUSIC_ARENA: DeferredHolder<SoundEvent, SoundEvent> = register("music.arena")

    private fun register(name: String): DeferredHolder<SoundEvent, SoundEvent> =
        // The Function<ResourceLocation, SoundEvent> overload hands us the
        // registry id (cobblemon_soundtracks:<name>), which is exactly the
        // location the SoundEvent should carry.
        SOUND_EVENTS.register(name) { id: ResourceLocation ->
            SoundEvent.createVariableRangeEvent(id)
        }
}
