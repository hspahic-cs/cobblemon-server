package com.cobblemonpokerogue.bridge.presentation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side flavor-text lookup. All player-facing strings live in the mod's
 * {@code assets/cobblemon_pokerogue_bridge/lang/en_us.json} so a human can rewrite them without
 * touching code. We resolve them server-side (plain {@code String.format} with {@code %s}/{@code %d})
 * because this is a server-only mod: clients do not have the lang file, so translatable
 * components would render as raw keys for them.
 */
final class DreamLang {
    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    private static final String PATH = "/assets/cobblemon_pokerogue_bridge/lang/en_us.json";

    private final Map<String, String> strings;

    private DreamLang(Map<String, String> strings) {
        this.strings = strings;
    }

    static DreamLang load() {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = DreamLang.class.getResourceAsStream(PATH)) {
            if (in == null) {
                LOG.warn("presentation lang file {} missing from the jar; falling back to raw keys", PATH);
                return new DreamLang(map);
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                map.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            LOG.warn("could not load presentation lang file {}", PATH, e);
        }
        return new DreamLang(map);
    }

    /** Formats the pattern for {@code key}; falls back to the key itself if missing or malformed. */
    String format(String key, Object... args) {
        String pattern = strings.get(key);
        if (pattern == null) {
            return key;
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (RuntimeException e) {
            LOG.warn("bad format pattern for lang key {}: {}", key, pattern);
            return pattern;
        }
    }
}
