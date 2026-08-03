package com.cobblemonpokerogue.bridge.econ;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The weekly free dream: every player gets one unpaid {@code /dream} entry per ISO week (server
 * timezone), so newcomers can try the mode without scraping together the fee. Usage is recorded
 * per MC uuid as the week it was spent ({@code free-runs.json}); a new week resets everyone
 * implicitly. The free entry still arms a run normally — only the charge is waived.
 */
public final class FreeRunLedger {

    private static final Logger LOG = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, String> usedWeek = new HashMap<>(); // uuid -> "2026-W31"

    private FreeRunLedger(Path file) {
        this.file = file;
    }

    public static FreeRunLedger load(Path file) {
        FreeRunLedger ledger = new FreeRunLedger(file);
        if (Files.exists(file)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    ledger.usedWeek.put(e.getKey(), e.getValue().getAsString());
                }
            } catch (IOException | RuntimeException e) {
                // Worst case of starting fresh: some players get an extra free run this week.
                LOG.warn("free-runs.json unreadable — starting a fresh ledger", e);
                ledger.usedWeek.clear();
            }
        }
        return ledger;
    }

    public synchronized boolean availableNow(UUID player) {
        return !currentWeek().equals(usedWeek.get(player.toString()));
    }

    public synchronized void markUsed(UUID player) {
        usedWeek.put(player.toString(), currentWeek());
        save();
    }

    private static String currentWeek() {
        LocalDate now = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        return now.get(wf.weekBasedYear()) + "-W" + now.get(wf.weekOfWeekBasedYear());
    }

    private void save() {
        JsonObject root = new JsonObject();
        usedWeek.forEach(root::addProperty);
        try {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("failed to write {}", file, e);
        }
    }
}
