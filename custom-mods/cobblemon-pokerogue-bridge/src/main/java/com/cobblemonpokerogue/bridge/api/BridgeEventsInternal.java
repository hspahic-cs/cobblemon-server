package com.cobblemonpokerogue.bridge.api;

import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL dispatch side of {@link BridgeEvents} — core only, not API. Lives in this package
 * so the listener list can stay package-private and {@link BridgeEvents}' public surface can
 * stay exactly the published contract. Every fire hops to the server main thread via
 * {@code server.execute()}; a throwing listener is logged and never breaks the poller or
 * other listeners.
 */
public final class BridgeEventsInternal {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");

    private BridgeEventsInternal() {}

    public static void fireRunStarted(MinecraftServer server, RunSnapshot s) {
        dispatch(server, l -> l.onRunStarted(s));
    }

    public static void fireWaveProgress(MinecraftServer server, RunSnapshot s, int previousWave) {
        dispatch(server, l -> l.onWaveProgress(s, previousWave));
    }

    public static void fireRunEnded(MinecraftServer server, RunSnapshot s, RunEndSummary summary) {
        dispatch(server, l -> l.onRunEnded(s, summary));
    }

    public static void fireMilestone(MinecraftServer server, RunSnapshot s, Milestone m) {
        dispatch(server, l -> l.onMilestone(s, m));
    }

    private static void dispatch(MinecraftServer server, Consumer<RunEventListener> call) {
        server.execute(() -> {
            for (RunEventListener l : BridgeEvents.LISTENERS) {
                try {
                    call.accept(l);
                } catch (Throwable t) {
                    LOGGER.error("RunEventListener {} threw", l.getClass().getName(), t);
                }
            }
        });
    }
}
