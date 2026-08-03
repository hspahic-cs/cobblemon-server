package com.cobblemonpokerogue.bridge;

import com.cobblemonpokerogue.bridge.db.PokerogueDb;
import com.cobblemonpokerogue.bridge.dex.DexFeeder;
import com.cobblemonpokerogue.bridge.econ.FreeRunLedger;
import com.cobblemonpokerogue.bridge.journal.DreamJournal;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.cobblemonpokerogue.bridge.link.RogueserverApi;
import com.cobblemonpokerogue.bridge.milestones.MilestoneEngine;
import com.cobblemonpokerogue.bridge.poll.DbPoller;
import com.cobblemonpokerogue.bridge.poll.RunTracker;
import net.minecraft.server.MinecraftServer;

/** Everything the bridge wires up per server lifetime. Reach it via {@link CobblemonPokerogueBridge#services()}. */
public record BridgeServices(BridgeConfig config, LinkStore links, PokerogueDb db, RogueserverApi api,
                             RunTracker tracker, MilestoneEngine milestones, DreamJournal journal,
                             FreeRunLedger freeRuns, DbPoller poller, DexFeeder dexFeeder,
                             MinecraftServer server) {}
