package com.cobblemonpokerogue.bridge;

import com.cobblemonpokerogue.bridge.command.PokerogueCommand;
import com.cobblemonpokerogue.bridge.db.PokerogueDb;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.cobblemonpokerogue.bridge.milestones.MilestoneEngine;
import com.cobblemonpokerogue.bridge.poll.DbPoller;
import com.cobblemonpokerogue.bridge.poll.RunTracker;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges a self-hosted PokeRogue instance (frontend + Go rogueserver + MariaDB, on the same
 * VM) into the Minecraft server: account linking, a clickable play link, a read-only DB poll
 * that derives run lifecycle events, and a milestone reward engine whose payouts are claimed
 * with /pokerogue claim. Decision record: docs/pokerogue-mode-plan.md §2.44.
 *
 * <p>Server-dist only: the modpack ships every jar to clients, so the dist gate below is what
 * keeps this from constructing there (repo rule for paired/server mods).
 */
@Mod(value = CobblemonPokerogueBridge.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class CobblemonPokerogueBridge {

    public static final String MOD_ID = "cobblemon_pokerogue_bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");

    private static volatile BridgeServices services;

    public CobblemonPokerogueBridge(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> PokerogueCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        LOGGER.info("Cobblemon PokeRogue Bridge constructed");
    }

    /** Null until the server starts (and again after it stops). Commands guard for that. */
    public static BridgeServices services() {
        return services;
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("cobblemon-pokerogue-bridge");
            Files.createDirectories(dir);
            writeExampleMilestones(dir.resolve("milestones.example.json"));
            BridgeConfig config = BridgeConfig.loadOrCreate(dir.resolve("config.json"));
            if (config.url.contains("CHANGE-ME") || config.db.password.contains("CHANGE-ME")) {
                LOGGER.warn("config.json still has CHANGE-ME placeholders — set the frontend url and DB credentials");
            }
            LinkStore links = LinkStore.load(dir.resolve("accounts.json"));
            MilestoneEngine milestones = MilestoneEngine.load(dir.resolve("milestones.json"), dir.resolve("state.json"));
            PokerogueDb db = new PokerogueDb(config.db);
            RunTracker tracker = new RunTracker();
            DbPoller poller = new DbPoller(event.getServer(), config, links, db, tracker, milestones);
            services = new BridgeServices(config, links, db, tracker, milestones, poller, event.getServer());
            initPresentation(config);
            com.cobblemonpokerogue.bridge.payout.PayoutEngine.init(); // self-guarded, once per JVM
            poller.start();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("PokeRogue bridge failed to initialize — the bridge is DISABLED this session", e);
            services = null;
        }
    }

    /** Presentation registers game-event handlers, so it must only ever happen once per JVM. */
    private static boolean presentationInited = false;

    private static void initPresentation(BridgeConfig config) {
        if (presentationInited) return;
        presentationInited = true;
        var shrine = config.shrine == null ? null
                : new com.cobblemonpokerogue.bridge.presentation.PresentationConfig.ShrinePos(
                        config.shrine.dimension, config.shrine.x, config.shrine.y, config.shrine.z);
        com.cobblemonpokerogue.bridge.presentation.PresentationFeatures.init(
                new com.cobblemonpokerogue.bridge.presentation.PresentationConfig(shrine != null, shrine));
    }

    private void onServerStopped(ServerStoppedEvent event) {
        BridgeServices s = services;
        services = null;
        if (s == null) return;
        s.poller().close();
        s.db().close();
        s.milestones().save();
    }

    /** Kept current with the mod: the example (placeholder rewards only) is rewritten each boot. */
    private static void writeExampleMilestones(Path target) {
        try (InputStream in = CobblemonPokerogueBridge.class
                .getResourceAsStream("/cobblemon_pokerogue_bridge/milestones.example.json")) {
            if (in == null) {
                LOGGER.warn("bundled milestones.example.json missing from the jar");
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("could not write {}", target, e);
        }
    }
}
