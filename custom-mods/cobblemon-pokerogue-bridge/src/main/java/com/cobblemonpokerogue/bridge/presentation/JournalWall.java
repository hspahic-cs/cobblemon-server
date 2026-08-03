package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemonpokerogue.bridge.journal.DreamJournal;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The journal wall: {@code /dream journal} also renders the ASKING player's run history as
 * sprite strips — same painting style as the leaderboard — on a second wall anchored by
 * {@code /dream admin journal}. It is a shared viewer: whoever asked last is on the wall
 * (small-server contention accepted by design), headed "<name>'s dreams".
 *
 * <p>Same disk rules as the board, except a leftover loaded from disk is just discarded —
 * never rebuilt — because the content belongs to whoever asked last, not to the world.
 */
public final class JournalWall {

    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    private static final String WALL_TAG = "pokerogue_dream_journalwall";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Anchors anchors;
    private final DreamLang lang;
    private final Path mapIdFile;
    @Nullable
    private MapPainter painter;

    private final List<Integer> mapIds = new ArrayList<>();
    /** Live entities; only touched on the server main thread. */
    private final List<UUID> entities = new ArrayList<>();

    JournalWall(Anchors anchors, DreamLang lang, Path stateDir) {
        this.anchors = anchors;
        this.lang = lang;
        this.mapIdFile = stateDir.resolve("journal-maps.json");
        loadMapIds();
    }

    /** Renders the player's history on the wall. False when no wall is anchored. */
    public boolean show(ServerPlayer viewer, List<DreamJournal.Entry> entries) {
        PresentationConfig.BoardPos pos = anchors.journal();
        if (pos == null) {
            return false;
        }
        if (painter == null) {
            painter = new MapPainter(Path.of(pos.spriteDir()));
        }
        MinecraftServer server = viewer.getServer();
        ServerLevel level = server == null ? null : wallLevel(server, pos);
        if (level == null || !level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()))) {
            return false;
        }
        Direction facing = Direction.byName(pos.facing());
        if (facing == null || facing.getAxis().isVertical()) {
            LOG.warn("journal wall: bad facing '{}' (want north/south/east/west)", pos.facing());
            return false;
        }

        discardTracked(level);

        float textScale = pos.scale() * 0.5f;
        Direction right = facing.getCounterClockWise();
        double cx = pos.x() + 0.5 + facing.getStepX() * 0.1;
        double cz = pos.z() + 0.5 + facing.getStepZ() * 0.1;
        double topCenterY = pos.y() + 0.5;
        float yaw = facing.toYRot();

        Entity header = WallKit.spawnText(level, cx, topCenterY + 0.62, cz, yaw, textScale, 0,
                "fixed", Component.literal(lang.format("pokerogue.journalwall.header",
                                viewer.getGameProfile().getName()))
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), WALL_TAG);
        if (header != null) {
            entities.add(header.getUUID());
        }

        int rows = Math.min(entries.size(), pos.size());
        for (int i = 0; i < rows; i++) {
            DreamJournal.Entry entry = entries.get(i);
            byte[][] tiles = painter.paintRowTiles(species(entry), MapPainter.borderRgb(entry.gameMode()));
            for (int t = 0; t < MapPainter.TILES; t++) {
                MapId mapId = mapIdFor(level, i * MapPainter.TILES + t);
                WallKit.paintMap(level, mapId, tiles[t]);
                int side = t + 1;
                BlockPos framePos = new BlockPos(
                        pos.x() + right.getStepX() * side,
                        pos.y() - i,
                        pos.z() + right.getStepZ() * side);
                UUID frame = WallKit.spawnFrame(level, framePos, facing, mapId, WALL_TAG);
                if (frame != null) {
                    entities.add(frame);
                }
            }
            String label = (entry.victory() ? "★ " : "")
                    + (entry.wave() >= 0 ? "Wave " + entry.wave() : "Wave ?")
                    + " : " + DATE.format(Instant.ofEpochMilli(entry.endedAtMs()).atZone(ZoneId.systemDefault()));
            double textCenter = 0.25 - (label.length() * 0.15 * textScale) / 2.0;
            Entity text = WallKit.spawnText(level,
                    cx + right.getStepX() * textCenter,
                    topCenterY - i - 0.19,
                    cz + right.getStepZ() * textCenter,
                    yaw, textScale, 0, "fixed",
                    Component.literal(label).withStyle(DreamBoard.textColor(entry.gameMode())), WALL_TAG);
            if (text != null) {
                entities.add(text.getUUID());
            }
        }
        return true;
    }

    private static List<Integer> species(DreamJournal.Entry entry) {
        List<Integer> out = new ArrayList<>();
        for (String id : entry.party().split(",")) {
            try {
                out.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (out.isEmpty() && !entry.leadSpecies().isBlank()) {
            try {
                out.add(Integer.parseInt(entry.leadSpecies().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private void discardTracked(ServerLevel level) {
        for (UUID id : entities) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        entities.clear();
    }

    /** Leftovers from disk are just removed — the wall only exists while someone is viewing. */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(WALL_TAG)) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PresentationConfig.BoardPos pos = anchors.journal();
        ServerLevel level = pos == null ? null : wallLevel(event.getServer(), pos);
        if (level != null) {
            discardTracked(level);
        } else {
            entities.clear();
        }
    }

    @Nullable
    private ServerLevel wallLevel(MinecraftServer server, PresentationConfig.BoardPos pos) {
        ResourceLocation id = ResourceLocation.tryParse(pos.dimension());
        if (id == null) {
            LOG.warn("journal wall: bad dimension id '{}'", pos.dimension());
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // ---- persistent map ids --------------------------------------------------------------

    private MapId mapIdFor(ServerLevel level, int index) {
        while (mapIds.size() <= index) {
            mapIds.add(level.getFreeMapId().id());
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
            LOG.warn("journal wall: {} unreadable — fresh map ids will be allocated", mapIdFile, e);
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
            LOG.warn("journal wall: failed to write {}", mapIdFile, e);
        }
    }
}
