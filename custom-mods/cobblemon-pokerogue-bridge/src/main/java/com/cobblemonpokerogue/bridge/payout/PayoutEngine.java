package com.cobblemonpokerogue.bridge.payout;

import com.cobblemonpokerogue.bridge.BridgeServices;
import com.cobblemonpokerogue.bridge.CobblemonPokerogueBridge;
import com.cobblemonpokerogue.bridge.api.BridgeEvents;
import com.cobblemonpokerogue.bridge.api.RunEndSummary;
import com.cobblemonpokerogue.bridge.api.RunEventListener;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import com.cobblemonpokerogue.bridge.milestones.MilestoneEngine;
import com.cobblemonpokerogue.bridge.presentation.DreamLang;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * §2.45's repeatable payout: pokemon-crate keys by deepest CLASSIC wave. On run end the
 * deepest {@code payoutBands} threshold reached pays its key count — the HIGHEST band only,
 * never cumulative — as a pending {@code gacha grant} claimed via {@code /pokerogue claim}
 * (the shrine flow; nothing is auto-mailed). Non-classic modes are free practice: no payout,
 * matching the rogueserver gate that only charges classic.
 *
 * <p>Depth comes from the tracker's max OBSERVED wave, i.e. the save cadence (waves X1/X6
 * plus 300s autosaves): band thresholds 50/100/150/200 register at the wave-51/101/151/201
 * save, so a player who *survives* a threshold wave is always observed past it; dying ON the
 * threshold wave itself only counts if an autosave (or a poll racing the game-over flow's
 * final write) caught it — accepted under-credit. The one systematic case, victory (the
 * final wave-200 write is deleted seconds later and usually unobservable), is corrected
 * explicitly: a classic victory IS wave 200 by rogueserver's own validation.
 *
 * <p>Also feeds the run's final depth into the milestone engine's {@code maxClassicWave}
 * virtual stat, which the poller otherwise only advances from live saves.
 */
public final class PayoutEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");
    private static final int CLASSIC_VICTORY_WAVE = 200;

    private static boolean initialized = false;

    private PayoutEngine() {}

    /** Registers the run-end listener; guarded once per JVM like the presentation layer. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        BridgeEvents.register(new RunEventListener() {
            @Override
            public void onRunEnded(RunSnapshot s, RunEndSummary summary) {
                PayoutEngine.onRunEnded(s, summary);
            }
        });
    }

    /** Fires on the server main thread (BridgeEvents contract). */
    private static void onRunEnded(RunSnapshot s, RunEndSummary summary) {
        BridgeServices svc = CobblemonPokerogueBridge.services();
        if (svc == null) return;
        if (!"classic".equals(s.gameMode())) return; // classic-only, §2.45
        int depth = summary.maxObservedWave();
        // A classic victory is wave 200 by definition (validateSessionCompleted); the final
        // wave-200 save is deleted by the clear seconds later, so polls rarely observe it.
        if (summary.victory()) depth = Math.max(depth, CLASSIC_VICTORY_WAVE);
        if (depth < 0) return; // degraded mode: no wave detail ever, nothing to pay
        svc.milestones().observeClassicWave(s.pokerogueUsername(), depth);

        int bestThreshold = -1;
        int keys = 0;
        for (Map.Entry<String, Integer> band : svc.config().payoutBands.entrySet()) {
            int threshold;
            try {
                threshold = Integer.parseInt(band.getKey().trim());
            } catch (NumberFormatException bad) {
                LOGGER.warn("payoutBands has a non-numeric wave threshold '{}' — ignored", band.getKey());
                continue;
            }
            if (threshold <= depth && threshold > bestThreshold) {
                bestThreshold = threshold;
                keys = band.getValue() == null ? 0 : band.getValue();
            }
        }
        if (bestThreshold < 0 || keys <= 0) return;

        DreamLang lang = DreamLang.shared();
        String id = "payout_wave" + bestThreshold + "_" + System.currentTimeMillis();
        svc.milestones().enqueuePayout(s.pokerogueUsername(), new MilestoneEngine.MilestoneDef(
                id, "", 0, 1,
                lang.format("pokerogue.payout.display", bestThreshold, keys),
                List.of("gacha grant %player% pokemon " + keys)));
        LOGGER.info("classic run payout: {} reached wave {} (band {}) -> {} pokemon-crate key(s) pending",
                s.pokerogueUsername(), depth, bestThreshold, keys);

        ServerPlayer player = svc.server().getPlayerList().getPlayer(s.mcPlayerId());
        if (player != null) {
            player.sendSystemMessage(Component
                    .literal(lang.format("pokerogue.payout.earned", keys, bestThreshold))
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
