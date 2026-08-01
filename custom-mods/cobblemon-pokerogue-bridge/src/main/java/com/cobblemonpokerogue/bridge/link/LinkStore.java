package com.cobblemonpokerogue.bridge.link;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MC player UUID &lt;-&gt; PokeRogue username mapping, persisted as
 * config/cobblemon-pokerogue-bridge/accounts.json ({@code {"links": {"<mc-uuid>": "<username>"}}}).
 *
 * <p>Ownership model (plan §2.44 open question, resolved for now): first-come-first-served on a
 * small trusted server. If a username is already linked to a DIFFERENT MC player the link is
 * refused and a staff-visible warning is logged; the challenge-code scheme is the escalation if
 * that ever proves insufficient. Usernames are compared case-insensitively (rogueserver
 * usernames are unique VARCHAR(16)).
 */
public final class LinkStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Entry(UUID mcId, String username) {}

    public enum LinkResult { LINKED, RELINKED, ALREADY_LINKED, TAKEN }

    private final Path file;
    private final Map<UUID, String> byPlayer = new HashMap<>();

    private LinkStore(Path file) {
        this.file = file;
    }

    public static LinkStore load(Path file) {
        LinkStore store = new LinkStore(file);
        if (Files.exists(file)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject links = root.getAsJsonObject("links");
                if (links != null) {
                    for (Map.Entry<String, JsonElement> e : links.entrySet()) {
                        try {
                            store.byPlayer.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                        } catch (RuntimeException bad) {
                            LOGGER.warn("accounts.json: skipping malformed entry '{}'", e.getKey());
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Refuse to silently start with an empty store over a corrupt file — that could
                // let a second player claim someone's already-linked username.
                throw new IllegalStateException("accounts.json is unreadable; fix or remove it: " + file, e);
            }
        }
        return store;
    }

    /** Snapshot for the poller: lowercased username -> entry. */
    public synchronized Map<String, Entry> byUsernameLower() {
        Map<String, Entry> out = new HashMap<>();
        for (Map.Entry<UUID, String> e : byPlayer.entrySet()) {
            out.put(e.getValue().toLowerCase(Locale.ROOT), new Entry(e.getKey(), e.getValue()));
        }
        return out;
    }

    public synchronized String usernameFor(UUID mcId) {
        return byPlayer.get(mcId);
    }

    public synchronized UUID ownerOf(String username) {
        for (Map.Entry<UUID, String> e : byPlayer.entrySet()) {
            if (e.getValue().equalsIgnoreCase(username)) return e.getKey();
        }
        return null;
    }

    public synchronized LinkResult link(UUID mcId, String username, String mcNameForLog) {
        UUID owner = ownerOf(username);
        if (owner != null && !owner.equals(mcId)) {
            // First-come-first-served: refuse, and make sure staff can see the collision.
            LOGGER.warn("STAFF: MC player {} ({}) tried to link PokeRogue account '{}' already linked to MC uuid {}",
                    mcNameForLog, mcId, username, owner);
            return LinkResult.TAKEN;
        }
        String previous = byPlayer.put(mcId, username);
        if (previous != null && previous.equalsIgnoreCase(username)) return LinkResult.ALREADY_LINKED;
        save();
        return previous == null ? LinkResult.LINKED : LinkResult.RELINKED;
    }

    /** @return the username that was unlinked, or null if none was linked. */
    public synchronized String unlink(UUID mcId) {
        String previous = byPlayer.remove(mcId);
        if (previous != null) save();
        return previous;
    }

    private void save() {
        JsonObject links = new JsonObject();
        for (Map.Entry<UUID, String> e : byPlayer.entrySet()) {
            links.addProperty(e.getKey().toString(), e.getValue());
        }
        JsonObject root = new JsonObject();
        root.add("links", links);
        try {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("failed to write {}", file, e);
        }
    }
}
