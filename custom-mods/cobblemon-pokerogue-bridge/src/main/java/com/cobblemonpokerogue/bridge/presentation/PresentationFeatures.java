package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemonpokerogue.bridge.api.BridgeEvents;
import com.cobblemonpokerogue.bridge.api.Milestone;
import com.cobblemonpokerogue.bridge.api.RunEndSummary;
import com.cobblemonpokerogue.bridge.api.RunEventListener;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single wiring point for everything players see of a PokeRogue run: chat announcements,
 * the tab-list dreaming presence, the private wake-up moment, and the shrine dream ghost.
 * The core module calls {@link #init} once at mod construction; all run events are delivered by
 * one {@link RunEventListener} registered with {@link BridgeEvents} (server main thread).
 */
public final class PresentationFeatures {
    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    private static boolean initialized;

    private PresentationFeatures() {}

    public static void init(PresentationConfig cfg) {
        if (initialized) {
            LOG.warn("PresentationFeatures.init called twice; ignoring the second call");
            return;
        }
        initialized = true;

        DreamLang lang = DreamLang.load();
        DreamAnnouncer announcer = new DreamAnnouncer(lang);
        DreamingPresence presence = new DreamingPresence(lang);
        DreamGhost ghost = new DreamGhost(cfg, lang);

        NeoForge.EVENT_BUS.register(presence);
        // Always registered: its disk-load sweeper must clean up leftover ghosts even when the
        // feature is currently disabled; spawning itself is gated inside DreamGhost.
        NeoForge.EVENT_BUS.register(ghost);

        BridgeEvents.register(new RunEventListener() {
            @Override
            public void onRunStarted(RunSnapshot s) {
                announcer.onRunStarted(s);
                presence.onRunStarted(s);
                ghost.onRunStartedOrProgress(s);
            }

            @Override
            public void onWaveProgress(RunSnapshot s, int previousWave) {
                announcer.onWaveProgress(s, previousWave);
                presence.onWaveProgress(s);
                ghost.onRunStartedOrProgress(s);
            }

            @Override
            public void onRunEnded(RunSnapshot s, RunEndSummary summary) {
                announcer.onRunEnded(s, summary);
                presence.onRunEnded(s);
                ghost.onRunEnded(s, summary);
            }

            @Override
            public void onMilestone(RunSnapshot s, Milestone m) {
                announcer.onMilestone(s, m);
            }
        });

        LOG.info("PokeRogue presentation layer initialised (dream ghost: {})",
                cfg.dreamGhostEnabled() && cfg.shrinePos() != null ? "on" : "off");
    }
}
