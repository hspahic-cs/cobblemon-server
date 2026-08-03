package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemonpokerogue.bridge.journal.DreamJournal;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The leaderboard screen (§2.44, sprite form): one row per player, each row an invisible fixed
 * item frame holding a locked map painted by {@link MapPainter} — the run's team as PokéRogue's
 * own party icons inside a bubble whose border color encodes the game mode — plus a floating
 * "rank. name — wave" text beside it, colored to match. Rows stack downward from the config
 * anchor and come from {@link DreamJournal#bests}: each player's deepest recorded run.
 *
 * <p>Map ids are allocated once per row and persisted in {@code board-maps.json}, so redeploys
 * repaint the same maps instead of leaking new ids. The entities follow {@link DreamGhost}'s
 * disk rules: ephemeral, command-tagged, and any tagged copy that comes back {@code
 * loadedFromDisk} is discarded and a refresh scheduled (also how the board reappears when its
 * chunk reloads). Refreshed on server start and on every run end.
 */
public final class DreamBoard {

    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    private static final String BOARD_TAG = "pokerogue_dream_board";

    private final Anchors anchors;
    private final DreamLang lang;
    private final DreamJournal journal;
    private final LinkStore links;
    private final Path mapIdFile;
    /** Lazy — the board can be pointed somewhere at runtime by /dream admin. */
    @Nullable
    private MapPainter painter;

    /** Persistent per-row map ids (board-maps.json). */
    private final List<Integer> mapIds = new ArrayList<>();
    /** Live entities, rebuilt on refresh; only touched on the server main thread. */
    private final List<UUID> rowEntities = new ArrayList<>();
    @Nullable
    private UUID headerEntity;

    DreamBoard(Anchors anchors, DreamLang lang, DreamJournal journal, LinkStore links,
               Path stateDir) {
        this.anchors = anchors;
        this.lang = lang;
        this.journal = journal;
        this.links = links;
        this.mapIdFile = stateDir.resolve("board-maps.json");
        loadMapIds();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        refresh();
    }

    void refresh() {
        PresentationConfig.BoardPos pos = anchors.board();
        if (pos == null) {
            return;
        }
        if (painter == null) {
            painter = new MapPainter(Path.of(pos.spriteDir()));
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? boardLevel(server) : null;
        if (level == null) {
            return;
        }
        Direction facing = Direction.byName(pos.facing());
        if (facing == null || facing.getAxis().isVertical()) {
            LOG.warn("dream board: bad facing '{}' (want north/south/east/west)", pos.facing());
            return;
        }
        if (!level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()))) {
            return; // the disk-load sweeper re-triggers us when the chunk comes back
        }

        List<Map.Entry<String, DreamJournal.Entry>> rows = journal.bests(pos.size());
        discardTracked(level);

        // Geometry: each row is a TILES-wide, 1-tall strip of real item frames (only frames
        // render map CONTENT — item displays draw the map item sprite instead). The anchor
        // block is the CENTER tile of the top row; rows stack straight down.
        float textScale = pos.scale() * 0.5f;
        Direction right = facing.getCounterClockWise();
        double cx = pos.x() + 0.5 + facing.getStepX() * 0.1;
        double cz = pos.z() + 0.5 + facing.getStepZ() * 0.1;
        double topCenterY = pos.y() + 0.5;
        float yaw = facing.toYRot();

        // Row reading order is "<rank>. <name> : <wave> <team strip>" — text left, strip
        // right — and the anchor is the composition's center, so the header sits at cx.
        Entity header = WallKit.spawnText(level, cx, topCenterY + 0.62, cz, yaw, textScale, 0,
                "fixed", Component.literal(lang.format("pokerogue.board.header"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), BOARD_TAG);
        headerEntity = header == null ? null : header.getUUID();

        if (rows.isEmpty()) {
            Entity empty = WallKit.spawnText(level, cx, topCenterY - 0.2, cz, yaw, textScale, 0,
                    "fixed", Component.literal(lang.format("pokerogue.board.empty"))
                            .withStyle(ChatFormatting.GRAY), BOARD_TAG);
            if (empty != null) {
                rowEntities.add(empty.getUUID());
            }
            return;
        }

        Map<String, LinkStore.Entry> byUser = links.byUsernameLower();
        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<String, DreamJournal.Entry> row = rows.get(i);
            DreamJournal.Entry best = row.getValue();
            byte[][] tiles = paintTiles(best);
            for (int t = 0; t < MapPainter.TILES; t++) {
                MapId mapId = mapIdFor(level, i * MapPainter.TILES + t);
                WallKit.paintMap(level, mapId, tiles[t]);
                int side = t + 1; // strip fills the right half: viewer-left tile nearest the text
                BlockPos framePos = new BlockPos(
                        pos.x() + right.getStepX() * side,
                        pos.y() - i,
                        pos.z() + right.getStepZ() * side);
                UUID frame = WallKit.spawnFrame(level, framePos, facing, mapId, BOARD_TAG);
                if (frame != null) {
                    rowEntities.add(frame);
                }
            }

            LinkStore.Entry link = byUser.get(row.getKey());
            String name = link != null ? link.username() : row.getKey();
            String label = (i + 1) + ". " + name + " : " + best.wave();
            // Right edge of the text hugs the strip's left edge (+0.5): estimate the label's
            // width (~6px/char at 0.025 blocks/px) and center-anchor accordingly.
            double textCenter = 0.25 - (label.length() * 0.15 * textScale) / 2.0;
            Entity text = WallKit.spawnText(level,
                    cx + right.getStepX() * textCenter,
                    topCenterY - i - 0.19,      // text anchors at its bottom — center it on the strip
                    cz + right.getStepZ() * textCenter,
                    yaw, textScale, 0, "fixed",
                    Component.literal(label).withStyle(textColor(best.gameMode())), BOARD_TAG);
            if (text != null) {
                rowEntities.add(text.getUUID());
            }
        }
    }

    // ---- rendering pieces ----------------------------------------------------------------

    private byte[][] paintTiles(DreamJournal.Entry best) {
        List<Integer> species = new ArrayList<>();
        for (String id : best.party().split(",")) {
            try {
                species.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException ignored) {
                // empty or junk segment — party may be unknown entirely (lead-only degrade)
            }
        }
        if (species.isEmpty() && !best.leadSpecies().isBlank()) {
            try {
                species.add(Integer.parseInt(best.leadSpecies().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return painter.paintRowTiles(species, MapPainter.borderRgb(best.gameMode()));
    }

    private void discardTracked(ServerLevel level) {
        if (headerEntity != null) {
            Entity e = level.getEntity(headerEntity);
            if (e != null) {
                e.discard();
            }
            headerEntity = null;
        }
        for (UUID id : rowEntities) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        rowEntities.clear();
    }

    static ChatFormatting textColor(String mode) {
        return switch (mode) {
            case "classic" -> ChatFormatting.GOLD;
            case "challenge" -> ChatFormatting.RED;
            case "endless" -> ChatFormatting.DARK_PURPLE;
            case "spliced_endless" -> ChatFormatting.LIGHT_PURPLE;
            case "daily" -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
    }

    // ---- lifecycle ------------------------------------------------------------------------

    /**
     * Since 1.20.5 spawn chunks are not kept loaded, so the boot-time refresh usually finds the
     * board chunk unloaded and skips. First materialization therefore happens HERE: when the
     * board's chunk loads (a player walks up), rebuild. May fire off-thread — hop to main.
     */
    @SubscribeEvent
    public void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        PresentationConfig.BoardPos pos = anchors.board();
        if (pos == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.level.ChunkPos cp = event.getChunk().getPos();
        if (cp.x != (pos.x() >> 4) || cp.z != (pos.z() >> 4)
                || !level.dimension().location().toString().equals(pos.dimension())) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(this::refresh);
        }
    }

    /** Board entities are ephemeral: a tagged copy from disk is a leftover — discard, rebuild. */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(BOARD_TAG)) {
            event.setCanceled(true);
            event.getEntity().discard();
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(this::refresh);
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = boardLevel(event.getServer());
        if (level != null) {
            discardTracked(level);
        } else {
            rowEntities.clear();
            headerEntity = null;
        }
    }

    @Nullable
    private ServerLevel boardLevel(MinecraftServer server) {
        PresentationConfig.BoardPos pos = anchors.board();
        if (pos == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(pos.dimension());
        if (id == null) {
            LOG.warn("dream board: bad dimension id '{}'", pos.dimension());
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            LOG.warn("dream board: dimension '{}' is not loaded", pos.dimension());
        }
        return level;
    }

    // ---- persistent map ids --------------------------------------------------------------

    private MapId mapIdFor(ServerLevel level, int index) {
        while (mapIds.size() <= index) {
            MapId fresh = level.getFreeMapId();
            mapIds.add(fresh.id());
            saveMapIds();
        }
        return new MapId(mapIds.get(index));
    }

    private void loadMapIds() {
        if (!Files.exists(mapIdFile)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(mapIdFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            root.getAsJsonArray("mapIds").forEach(e -> mapIds.add(e.getAsInt()));
        } catch (IOException | RuntimeException e) {
            LOG.warn("dream board: {} unreadable — fresh map ids will be allocated", mapIdFile, e);
            mapIds.clear();
        }
    }

    private void saveMapIds() {
        JsonArray arr = new JsonArray();
        mapIds.forEach(arr::add);
        JsonObject root = new JsonObject();
        root.add("mapIds", arr);
        try {
            Files.writeString(mapIdFile, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("dream board: failed to write {}", mapIdFile, e);
        }
    }
}
