package com.cobblemonsoundtracks

import net.minecraft.client.Minecraft
import net.minecraft.sounds.Music
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.SelectMusicEvent

/**
 * Maps the player's current dimension to a per-world [Music] track and feeds it
 * to NeoForge's [SelectMusicEvent].
 *
 * Only the custom Multiworld dimensions are overridden. Anything else — most
 * importantly `minecraft:overworld` (the wilderness/survival world) and the
 * nether/end — is left alone, so those keep their vanilla biome music.
 */
object SoundtrackManager {

    // Gap (in ticks, 20/s) the MusicManager waits between tracks. Kept short so
    // a world feels like a continuous playlist rather than long vanilla-style
    // silences, but non-zero so successive tracks don't hard-cut into each
    // other. ~5–12s.
    private const val MIN_DELAY = 100
    private const val MAX_DELAY = 240

    // replaceCurrentMusic = true → entering a soundtracked world interrupts
    // whatever was playing (vanilla or the previous world's track) instead of
    // waiting for it to finish. Built lazily so the SoundEvent holders are
    // resolved (registration happens during mod construction, before any
    // SelectMusicEvent can fire).
    private val spawnMusic: Music by lazy { Music(ModSounds.MUSIC_SPAWN, MIN_DELAY, MAX_DELAY, true) }
    private val elite4Music: Music by lazy { Music(ModSounds.MUSIC_ELITE4, MIN_DELAY, MAX_DELAY, true) }
    private val arenaMusic: Music by lazy { Music(ModSounds.MUSIC_ARENA, MIN_DELAY, MAX_DELAY, true) }

    @SubscribeEvent
    fun onSelectMusic(event: SelectMusicEvent) {
        val level = Minecraft.getInstance().level ?: return
        val id = level.dimension().location()
        if (id.namespace != MULTIWORLD) return

        val music = when {
            id.path == "spawn" -> spawnMusic
            id.path == "elite4" -> elite4Music
            // arena1, arena2, and any future arenaN share one playlist.
            id.path.startsWith("arena") -> arenaMusic
            else -> return // other multiworld dims (if any) keep vanilla music
        }

        // overrideMusic also cancels the event so lower-priority listeners
        // (e.g. biome music mods) don't clobber our dimension music.
        event.overrideMusic(music)
    }

    private const val MULTIWORLD = "multiworld"
}
