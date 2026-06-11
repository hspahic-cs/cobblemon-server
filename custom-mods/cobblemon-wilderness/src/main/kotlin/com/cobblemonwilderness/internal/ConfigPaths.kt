package com.cobblemonwilderness.internal

import java.nio.file.Path

/**
 * Resolves authored (human-edited) vs runtime (mod-managed) paths under
 * `config/cobblemon-wilderness/`.
 *
 *   authored/config.json   — the bounding box + schedule knobs you edit
 *   runtime/state.json     — last-reset timestamps + pending-reset flag (do not edit)
 *
 * Mirrors the layout used by the other custom mods (see cobblemon-market).
 */
internal object ConfigPaths {
    private const val MOD_ID = "cobblemon-wilderness"

    fun authored(configDir: Path, name: String): Path =
        configDir.resolve(MOD_ID).resolve("authored").resolve(name)

    fun runtime(configDir: Path, name: String): Path =
        configDir.resolve(MOD_ID).resolve("runtime").resolve(name)
}
