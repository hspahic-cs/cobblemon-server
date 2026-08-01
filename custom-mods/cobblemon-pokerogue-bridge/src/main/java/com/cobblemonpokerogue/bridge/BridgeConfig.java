package com.cobblemonpokerogue.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/cobblemon-pokerogue-bridge/config.json. Missing fields keep the defaults below;
 * a missing file is written out with defaults so the operator has a template to fill in.
 *
 * <p>Defaults are deliberately placeholders — no real hostname or credential is ever
 * committed to this repo (repo rule). The DB defaults match the standard self-hosted
 * rogueserver layout (MariaDB on localhost, database {@code pokeroguedb}).
 */
public final class BridgeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    /** The public PokeRogue frontend URL players open — the /pokerogue clickable link. */
    public String url = "http://CHANGE-ME:8000";
    public Db db = new Db();
    /** DB poll interval in seconds (single background thread, fixed-rate schedule). */
    public int pollSeconds = 10;
    /** Optional shrine anchor for presentation features; null disables it. */
    public Shrine shrine = null;
    /**
     * /pokerogue enter fee in NeoEssentials currency (plan §2.45 pay-to-dream). 0 makes
     * entry free (still writes the arming credit); the gate itself lives in rogueserver.
     */
    public int entryFee = 5000;
    /**
     * Repeatable classic payout: wave threshold -> pokemon-crate key count. Only the DEEPEST
     * band reached pays (not cumulative), via `gacha grant`. Values are §2.45's, marked (tune).
     */
    public Map<String, Integer> payoutBands = defaultPayoutBands();

    private static Map<String, Integer> defaultPayoutBands() {
        Map<String, Integer> bands = new LinkedHashMap<>();
        bands.put("50", 1);
        bands.put("100", 2);
        bands.put("150", 3);
        bands.put("200", 4);
        return bands;
    }

    public static final class Db {
        public String host = "localhost";
        public int port = 3306;
        public String database = "pokeroguedb";
        public String user = "pokerogue";
        public String password = "CHANGE-ME";
    }

    public static final class Shrine {
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
    }

    public static BridgeConfig loadOrCreate(Path file) throws IOException {
        if (Files.exists(file)) {
            try {
                BridgeConfig cfg = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), BridgeConfig.class);
                if (cfg != null) {
                    if (cfg.db == null) cfg.db = new Db();
                    if (cfg.pollSeconds < 1) cfg.pollSeconds = 10;
                    if (cfg.entryFee < 0) cfg.entryFee = 5000;
                    if (cfg.payoutBands == null) cfg.payoutBands = defaultPayoutBands();
                    return cfg;
                }
            } catch (RuntimeException e) {
                throw new IOException("config.json is malformed: " + e.getMessage(), e);
            }
        }
        BridgeConfig cfg = new BridgeConfig();
        Files.writeString(file, GSON.toJson(cfg), StandardCharsets.UTF_8);
        return cfg;
    }
}
