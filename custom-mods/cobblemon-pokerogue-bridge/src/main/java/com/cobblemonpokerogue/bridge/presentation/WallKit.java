package com.cobblemonpokerogue.bridge.presentation;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared entity/canvas plumbing for the wall features (leaderboard, journal wall, ghost labels). */
final class WallKit {

    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");

    private WallKit() {}

    /** An invisible fixed item frame holding a map — one painting tile. */
    @Nullable
    static UUID spawnFrame(ServerLevel level, BlockPos pos, Direction facing, MapId mapId, String tag) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:item_frame");
        // BlockAttachedEntity rejects TileX/Y/Z farther than 16 blocks from Pos, so Pos must
        // be seeded too or the frame deserializes at the origin and discards itself.
        ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(pos.getX() + 0.5));
        posTag.add(DoubleTag.valueOf(pos.getY() + 0.5));
        posTag.add(DoubleTag.valueOf(pos.getZ() + 0.5));
        nbt.put("Pos", posTag);
        nbt.putInt("TileX", pos.getX());
        nbt.putInt("TileY", pos.getY());
        nbt.putInt("TileZ", pos.getZ());
        nbt.putByte("Facing", (byte) facing.get3DDataValue());
        nbt.putBoolean("Invisible", true);
        nbt.putBoolean("Fixed", true);
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:filled_map");
        item.putInt("count", 1);
        CompoundTag components = new CompoundTag();
        components.putInt("minecraft:map_id", mapId.id());
        item.put("components", components);
        nbt.put("Item", item);
        Entity frame = EntityType.loadEntityRecursive(nbt, level, e -> e);
        if (frame == null) {
            LOG.warn("wall kit: could not create an item frame at {}", pos);
            return null;
        }
        frame.addTag(tag);
        if (!level.addFreshEntity(frame)) {
            LOG.warn("wall kit: level rejected an item frame at {}", pos);
            return null;
        }
        return frame.getUUID();
    }

    /**
     * A text display. {@code billboard} is {@code "fixed"} for wall text (faces {@code yaw})
     * or {@code "center"} for labels that always face the viewer; {@code dy} shifts the text
     * up inside its own model space (used to float labels above a ridden entity).
     */
    @Nullable
    static Entity spawnText(ServerLevel level, double x, double y, double z, float yaw, float scale,
                            double dy, String billboard, Component text, String tag) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:text_display");
        nbt.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        nbt.putString("billboard", billboard);
        nbt.putInt("line_width", 200);
        nbt.put("transformation", transformation(scale, dy));
        Entity display = EntityType.loadEntityRecursive(nbt, level, e -> {
            e.moveTo(x, y, z, yaw, 0.0f);
            return e;
        });
        if (display == null) {
            return null;
        }
        display.addTag(tag);
        return level.addFreshEntity(display) ? display : null;
    }

    /** Updates a live text display's text in place. */
    static void retext(ServerLevel level, Entity display, Component text) {
        CompoundTag nbt = display.saveWithoutId(new CompoundTag());
        nbt.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        display.load(nbt);
    }

    /** Writes one painted tile into a (possibly fresh) map. */
    static void paintMap(ServerLevel level, MapId mapId, byte[] pixels) {
        MapItemSavedData data = level.getMapData(mapId);
        if (data == null) {
            data = MapItemSavedData.createFresh(0, 0, (byte) 3, false, false, level.dimension());
            level.setMapData(mapId, data);
        }
        for (int z = 0; z < MapPainter.SIZE; z++) {
            for (int x = 0; x < MapPainter.SIZE; x++) {
                data.setColor(x, z, pixels[x + z * MapPainter.SIZE]);
            }
        }
        data.setDirty();
    }

    /** Decomposed display transformation: identity rotations, uniform scale, vertical shift. */
    private static CompoundTag transformation(float scale, double dy) {
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(0, (float) dy, 0));
        t.put("scale", floats(scale, scale, scale));
        t.put("left_rotation", floats(0, 0, 0, 1));
        t.put("right_rotation", floats(0, 0, 0, 1));
        return t;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }
}
