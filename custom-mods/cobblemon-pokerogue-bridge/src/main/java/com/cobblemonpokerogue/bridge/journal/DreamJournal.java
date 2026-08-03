package com.cobblemonpokerogue.bridge.journal;

import com.cobblemonpokerogue.bridge.api.BridgeEvents;
import com.cobblemonpokerogue.bridge.api.RunEndSummary;
import com.cobblemonpokerogue.bridge.api.RunEventListener;
import com.cobblemonpokerogue.bridge.api.RunSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Dream Journal (§2.44): a per-account history of ended runs, persisted in
 * {@code journal.json} next to the other bridge state and read back by {@code /dream journal}.
 * One entry is appended per {@link RunEventListener#onRunEnded} (server main thread — the
 * BridgeEvents contract), newest kept, capped per account.
 *
 * <p>Cosmetic data, so loading is lenient where {@code state.json} is strict: a corrupt file is
 * moved aside and the journal starts fresh rather than disabling the bridge.
 */
public final class DreamJournal {

    private static final Logger LOG = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ENTRIES_PER_ACCOUNT = 40;

    /**
     * {@code wave}/{@code leadSpecies}/{@code party} follow RunSnapshot conventions
     * (-1 / "" when unknown); {@code party} is the lineup as a CSV of numeric SpeciesIds.
     */
    public record Entry(long endedAtMs, int wave, boolean victory, String gameMode,
                        String leadSpecies, String party) {}

    /** Listener registration mutates a JVM-global registry, so it must only ever happen once. */
    private static boolean listenerRegistered;

    private final Path file;
    private final Map<String, List<Entry>> accounts = new HashMap<>(); // key: lowercased username

    private DreamJournal(Path file) {
        this.file = file;
    }

    public static DreamJournal load(Path file) {
        DreamJournal journal = new DreamJournal(file);
        journal.loadFile();
        if (!listenerRegistered) {
            listenerRegistered = true;
            BridgeEvents.register(new RunEventListener() {
                @Override
                public void onRunEnded(RunSnapshot s, RunEndSummary summary) {
                    journal.record(s.pokerogueUsername(), new Entry(
                            System.currentTimeMillis(),
                            Math.max(summary.finalWave(), summary.maxObservedWave()),
                            summary.victory(), s.gameMode(), s.leadSpecies(), s.party()));
                }
            });
        }
        return journal;
    }

    public synchronized void record(String username, Entry entry) {
        if (username == null || username.isBlank()) {
            return;
        }
        List<Entry> list = accounts.computeIfAbsent(username.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
        list.add(entry);
        while (list.size() > MAX_ENTRIES_PER_ACCOUNT) {
            list.remove(0);
        }
        save();
    }

    /** Newest first. */
    public synchronized List<Entry> entriesFor(String username) {
        List<Entry> list = accounts.get(username.toLowerCase(Locale.ROOT));
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>(list);
        java.util.Collections.reverse(out);
        return out;
    }

    /**
     * Deepest entry per account, deepest first, at most {@code n}. Keys are lowercased
     * usernames. Entries with no known wave are skipped.
     */
    public synchronized List<Map.Entry<String, Entry>> bests(int n) {
        List<Map.Entry<String, Entry>> out = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> e : accounts.entrySet()) {
            Entry best = null;
            for (Entry en : e.getValue()) {
                if (best == null || en.wave() > best.wave()) {
                    best = en;
                }
            }
            if (best != null && best.wave() > 0) {
                out.add(Map.entry(e.getKey(), best));
            }
        }
        out.sort((a, b) -> Integer.compare(b.getValue().wave(), a.getValue().wave()));
        return out.subList(0, Math.min(Math.max(0, n), out.size()));
    }

    // ---- journal.json --------------------------------------------------------------------

    private void loadFile() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject accs = root.getAsJsonObject("accounts");
            if (accs == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> e : accs.entrySet()) {
                List<Entry> list = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new Entry(
                            o.get("endedAt").getAsLong(),
                            o.has("wave") ? o.get("wave").getAsInt() : -1,
                            o.has("victory") && o.get("victory").getAsBoolean(),
                            o.has("mode") ? o.get("mode").getAsString() : "",
                            o.has("lead") ? o.get("lead").getAsString() : "",
                            o.has("party") ? o.get("party").getAsString() : ""));
                }
                accounts.put(e.getKey().toLowerCase(Locale.ROOT), list);
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("journal.json is unreadable — moving it aside and starting a fresh journal", e);
            try {
                Files.move(file, file.resolveSibling("journal.json.corrupt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException move) {
                LOG.error("could not move the corrupt journal aside", move);
            }
            accounts.clear();
        }
    }

    private void save() {
        JsonObject accs = new JsonObject();
        for (Map.Entry<String, List<Entry>> e : accounts.entrySet()) {
            JsonArray arr = new JsonArray();
            for (Entry en : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("endedAt", en.endedAtMs());
                o.addProperty("wave", en.wave());
                o.addProperty("victory", en.victory());
                o.addProperty("mode", en.gameMode());
                o.addProperty("lead", en.leadSpecies());
                o.addProperty("party", en.party());
                arr.add(o);
            }
            accs.add(e.getKey(), arr);
        }
        JsonObject root = new JsonObject();
        root.add("accounts", accs);
        try {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("failed to write {}", file, e);
        }
    }
}
