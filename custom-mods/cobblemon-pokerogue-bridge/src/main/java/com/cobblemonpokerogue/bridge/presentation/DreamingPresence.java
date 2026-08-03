package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dreaming presence: while a player has an active run their tab-list name is suffixed with
 * a sleeping marker and the current wave. State lives in a map keyed by player UUID; entries are
 * cleared on run end and on logout (a mid-run relogin repopulates on the next wave event).
 *
 * <p>Registered on the NeoForge event bus for {@link PlayerEvent.TabListNameFormat}.
 */
public final class DreamingPresence {
    private final DreamLang lang;
    private final Map<UUID, Integer> activeWaves = new ConcurrentHashMap<>();

    DreamingPresence(DreamLang lang) {
        this.lang = lang;
    }

    void onRunStarted(RunSnapshot s) {
        activeWaves.put(s.mcPlayerId(), s.wave());
        refresh(s.mcPlayerId());
    }

    void onWaveProgress(RunSnapshot s) {
        activeWaves.put(s.mcPlayerId(), s.wave());
        refresh(s.mcPlayerId());
    }

    void onRunEnded(RunSnapshot s) {
        activeWaves.remove(s.mcPlayerId());
        refresh(s.mcPlayerId());
    }

    @SubscribeEvent
    public void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        Integer wave = activeWaves.get(event.getEntity().getUUID());
        if (wave == null) {
            return;
        }
        event.setDisplayName(baseName(event).copy()
                .append(Component.literal(lang.format("pokerogue.presentation.tab.suffix", wave))
                        .withStyle(net.minecraft.ChatFormatting.BLUE)));
    }

    /**
     * The name to hang the wave marker off.
     *
     * <p>The team fallback is load-bearing. Setting a tab-list display name makes the client render
     * it verbatim and skip scoreboard-team decoration entirely — and team prefix/suffix is exactly
     * how NeoEssentials paints the {@code [Admin]}/{@code [Mod]} rank tag (see its
     * {@code TablistManager.updatePlayerTeam}). Falling back to the bare
     * {@code getEntity().getName()} therefore dropped a moderator's tag for as long as they had a
     * run going, and restored it when the run ended.
     *
     * <p>{@link PlayerTeam#formatNameForTeam} is the same helper vanilla uses when no display name
     * is set, so this reproduces the undecorated-case rendering and then appends to it. Players on
     * no team are unaffected — it returns the name unchanged.
     */
    private static Component baseName(PlayerEvent.TabListNameFormat event) {
        Component explicit = event.getDisplayName();
        if (explicit != null) {
            return explicit;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        return PlayerTeam.formatNameForTeam(player.getTeam(), player.getName());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        activeWaves.remove(event.getEntity().getUUID());
    }

    private void refresh(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.refreshTabListName();
        }
    }
}
