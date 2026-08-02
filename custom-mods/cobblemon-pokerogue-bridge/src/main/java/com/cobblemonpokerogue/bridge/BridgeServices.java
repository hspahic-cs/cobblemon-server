package com.cobblemonpokerogue.bridge;

import com.cobblemonpokerogue.bridge.db.PokerogueDb;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.cobblemonpokerogue.bridge.link.RogueserverApi;
import com.cobblemonpokerogue.bridge.milestones.MilestoneEngine;
import com.cobblemonpokerogue.bridge.poll.DbPoller;
import com.cobblemonpokerogue.bridge.poll.RunTracker;
import net.minecraft.server.MinecraftServer;

/** Everything the bridge wires up per server lifetime. Reach it via {@link CobblemonPokerogueBridge#services()}. */
public record BridgeServices(BridgeConfig config, LinkStore links, PokerogueDb db, RogueserverApi api,
                             RunTracker tracker, MilestoneEngine milestones, DbPoller poller,
                             MinecraftServer server) {}
