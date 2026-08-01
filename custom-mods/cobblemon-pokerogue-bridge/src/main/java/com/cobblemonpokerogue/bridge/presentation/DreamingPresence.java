package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
        Component base = event.getDisplayName() != null ? event.getDisplayName() : event.getEntity().getName();
        event.setDisplayName(base.copy()
                .append(Component.literal(lang.format("pokerogue.presentation.tab.suffix", wave))));
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
