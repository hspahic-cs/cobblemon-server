package com.cobblemonpokerogue.bridge.presentation;

import org.jetbrains.annotations.Nullable;

/**
 * Everything the presentation layer needs from the core config. The orchestrator adapts the
 * core module's config format to this record at merge.
 *
 * @param dreamGhostEnabled master toggle for the shrine dream-ghost feature; even when true the
 *                          feature stays dormant unless {@code shrinePos} is also configured
 * @param shrinePos         where the Dream Machine shrine is, or {@code null} if not configured
 */
public record PresentationConfig(boolean dreamGhostEnabled, @Nullable ShrinePos shrinePos) {

    /**
     * @param dimension dimension id, e.g. {@code "minecraft:overworld"}
     */
    public record ShrinePos(String dimension, double x, double y, double z) {}
}
