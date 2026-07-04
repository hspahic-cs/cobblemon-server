package com.cobblemonauction.internal

import java.nio.file.Path

/**
 * Resolves authored/runtime paths under `config/cobblemon-auction/`.
 *
 *  - `authored/` — design-time config (deploy overwrites it).
 *  - `runtime/`  — player state (listings, mailboxes); deploys must never touch it.
 *
 * See docs/design/mod-state-vs-config.md.
 */
internal object ConfigPaths {
    private const val MOD_ID = "cobblemon-auction"

    fun authored(configDir: Path, name: String): Path =
        configDir.resolve(MOD_ID).resolve("authored").resolve(name)

    fun runtime(configDir: Path, name: String): Path =
        configDir.resolve(MOD_ID).resolve("runtime").resolve(name)
}
