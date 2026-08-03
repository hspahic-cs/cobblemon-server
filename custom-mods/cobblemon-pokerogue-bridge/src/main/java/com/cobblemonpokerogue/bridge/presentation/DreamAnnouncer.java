package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemonpokerogue.bridge.api.Milestone;
import com.cobblemonpokerogue.bridge.api.RunEndSummary;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat announcements, loudness gated by tier, plus the private wake-up moment.
 *
 * <ul>
 *   <li>run started: log only</li>
 *   <li>wave progress: broadcast only when a meaningful threshold is crossed</li>
 *   <li>run ended: broadcast; victory gets the tier-3 treatment (server-wide title)</li>
 *   <li>milestones: tier 1 log, tier 2 chat broadcast, tier 3+ server-wide title + chat</li>
 *   <li>wake-up: the runner (if online) privately gets a title fade and a one-line summary</li>
 * </ul>
 */
final class DreamAnnouncer {
    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    private static final int[] WAVE_THRESHOLDS = {25, 50, 100, 150, 200};

    private final DreamLang lang;

    DreamAnnouncer(DreamLang lang) {
        this.lang = lang;
    }

    void onRunStarted(RunSnapshot s) {
        LOG.info("pokerogue run started: {} ({}) slot {} mode {} lead {}",
                s.pokerogueUsername(), s.mcPlayerId(), s.slot(), s.gameMode(), s.leadSpecies());
    }

    void onWaveProgress(RunSnapshot s, int previousWave) {
        // Announce the highest threshold crossed by this progress step, if any — one line even if
        // a slow poll skipped several thresholds at once.
        int crossed = -1;
        for (int t : WAVE_THRESHOLDS) {
            if (previousWave < t && t <= s.wave()) {
                crossed = t;
            }
        }
        if (crossed < 0) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        broadcast(server, lang.format("pokerogue.presentation.wave.threshold", playerName(server, s), crossed));
    }

    void onRunEnded(RunSnapshot s, RunEndSummary summary) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        String name = playerName(server, s);
        if (summary.victory()) {
            // Tier-3 treatment: server-wide title plus the chat line.
            broadcastTitle(server,
                    lang.format("pokerogue.presentation.run.victory.title"),
                    lang.format("pokerogue.presentation.run.victory.subtitle", name, summary.finalWave()));
            broadcast(server, lang.format("pokerogue.presentation.run.victory.chat", name, summary.finalWave()));
        } else {
            broadcast(server, lang.format("pokerogue.presentation.run.ended", name, summary.finalWave()));
        }
        wakeUp(server, s, summary);
    }

    void onMilestone(RunSnapshot s, Milestone m) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        String name = playerName(server, s);
        LOG.info("pokerogue milestone: {} reached {} (tier {})", name, m.id(), m.tier());
        if (m.tier() <= 1) {
            return; // tier 1: log only
        }
        if (m.tier() >= 3) {
            broadcastTitle(server,
                    lang.format("pokerogue.presentation.milestone.title", m.display()),
                    lang.format("pokerogue.presentation.milestone.subtitle", name));
        }
        broadcast(server, lang.format("pokerogue.presentation.milestone.chat", name, m.display()));
    }

    /** The private wake-up moment for the runner, if they are online. Victory wakes gold, ordinary wakes blue. */
    private void wakeUp(MinecraftServer server, RunSnapshot s, RunEndSummary summary) {
        ServerPlayer player = server.getPlayerList().getPlayer(s.mcPlayerId());
        if (player == null) {
            return;
        }
        ChatFormatting titleColor = summary.victory() ? ChatFormatting.GOLD : ChatFormatting.BLUE;
        ChatFormatting subtitleColor = summary.victory() ? ChatFormatting.YELLOW : ChatFormatting.AQUA;
        String subtitle = summary.victory()
                ? lang.format("pokerogue.presentation.wake.subtitle.victory", summary.finalWave())
                : lang.format("pokerogue.presentation.wake.subtitle", summary.finalWave());
        sendTitle(player,
                Component.literal(lang.format("pokerogue.presentation.wake.title")).withStyle(titleColor),
                Component.literal(subtitle).withStyle(subtitleColor));
        String chat = summary.victory()
                ? lang.format("pokerogue.presentation.wake.chat.victory", summary.finalWave())
                : lang.format("pokerogue.presentation.wake.chat", summary.finalWave());
        player.sendSystemMessage(Component.literal(chat).withStyle(titleColor));
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static void broadcastTitle(MinecraftServer server, String title, String subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTitle(player, Component.literal(title), Component.literal(subtitle));
        }
    }

    private static void sendTitle(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    /** Prefer the live MC name; fall back to the PokeRogue username for offline runners. */
    static String playerName(MinecraftServer server, RunSnapshot s) {
        ServerPlayer player = server.getPlayerList().getPlayer(s.mcPlayerId());
        return player != null ? player.getGameProfile().getName() : s.pokerogueUsername();
    }
}
