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
    private static Anchors anchors;
    private static DreamBoard boardRef;
    private static JournalWall journalWallRef;

    private PresentationFeatures() {}

    /** The live anchors, for {@code /dream admin}; null before init. */
    public static Anchors anchors() {
        return anchors;
    }

    /** Rebuilds the leaderboard now (server main thread only). */
    public static void refreshBoard() {
        if (boardRef != null) {
            boardRef.refresh();
        }
    }

    /** The journal wall renderer; null before init. */
    public static JournalWall journalWall() {
        return journalWallRef;
    }

    public static void init(PresentationConfig cfg,
                            com.cobblemonpokerogue.bridge.link.LinkStore links,
                            com.cobblemonpokerogue.bridge.journal.DreamJournal journal,
                            java.nio.file.Path stateDir) {
        if (initialized) {
            LOG.warn("PresentationFeatures.init called twice; ignoring the second call");
            return;
        }
        initialized = true;

        anchors = new Anchors(cfg.shrinePos(), cfg.boardPos(), cfg.journalPos());
        DreamLang lang = DreamLang.shared();
        DreamAnnouncer announcer = new DreamAnnouncer(lang);
        DreamingPresence presence = new DreamingPresence(lang);
        DreamGhost ghost = new DreamGhost(anchors, lang);
        DreamBoard board = new DreamBoard(anchors, lang, journal, links, stateDir);
        boardRef = board;
        JournalWall wall = new JournalWall(anchors, lang, stateDir);
        journalWallRef = wall;

        NeoForge.EVENT_BUS.register(presence);
        // Always registered: its disk-load sweeper must clean up leftover ghosts even when the
        // feature is currently disabled; spawning itself is gated inside DreamGhost.
        NeoForge.EVENT_BUS.register(ghost);
        NeoForge.EVENT_BUS.register(board); // same rule: its sweeper always runs
        NeoForge.EVENT_BUS.register(wall);  // and again

        BridgeEvents.register(new RunEventListener() {
            @Override
            public void onRunStarted(RunSnapshot s) {
                announcer.onRunStarted(s);
                presence.onRunStarted(s);
                ghost.onRunStartedOrProgress(s);
            }

            @Override
            public void onRunResumed(RunSnapshot s) {
                // Quiet restore after a server restart: tab suffix and ghost, no announcer.
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
                board.refresh(); // run end is when maxClassicWave can move
            }

            @Override
            public void onMilestone(RunSnapshot s, Milestone m) {
                announcer.onMilestone(s, m);
            }
        });

        LOG.info("PokeRogue presentation layer initialised (dream ghost: {}, leaderboard: {})",
                cfg.dreamGhostEnabled() && cfg.shrinePos() != null ? "on" : "off",
                cfg.boardPos() != null ? "on" : "off");
    }
}
