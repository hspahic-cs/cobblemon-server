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
 * MC player UUID &lt;-&gt; PokeRogue account mapping, persisted as
 * config/cobblemon-pokerogue-bridge/accounts.json:
 * <pre>{"links": {"&lt;mc-uuid&gt;": {"username": "&lt;name&gt;", "password": "&lt;generated&gt;"}}}</pre>
 *
 * <p>Since §2.46 accounts are server-minted: the bridge registers the PokeRogue account itself
 * (username = MC name) and stores the generated password here so {@code /pokerogue password}
 * can whisper it. Legacy entries — a bare string value instead of an object, or an object with
 * no password — remain valid; they are accounts the player registered in the web game and
 * linked before minting existed, and the bridge does not know their password.
 *
 * <p>Ownership model: usernames are first-come-first-served. If a username is already linked
 * to a DIFFERENT MC player the link is refused and a staff-visible warning is logged.
 * Usernames are compared case-insensitively (rogueserver usernames are unique VARCHAR(16)
 * under MariaDB's case-insensitive default collation).
 *
 * <p>All methods are synchronized — callers on the poller thread and the main thread may both
 * touch the store.
 */
public final class LinkStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code password} is null for legacy (pre-minting) links. */
    public record Entry(UUID mcId, String username, String password) {}

    public enum LinkResult { LINKED, RELINKED, ALREADY_LINKED, TAKEN }

    private record Account(String username, String password) {}

    private final Path file;
    private final Map<UUID, Account> byPlayer = new HashMap<>();

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
                            store.byPlayer.put(UUID.fromString(e.getKey()), parseAccount(e.getValue()));
                        } catch (RuntimeException bad) {
                            LOGGER.warn("accounts.json: skipping malformed entry '{}'", e.getKey());
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Refuse to silently start with an empty store over a corrupt file — that could
                // let a second player claim someone's already-linked username, and it would
                // orphan every stored generated password.
                throw new IllegalStateException("accounts.json is unreadable; fix or remove it: " + file, e);
            }
        }
        return store;
    }

    /** Legacy entries are bare strings; minted entries are {"username": ..., "password": ...}. */
    private static Account parseAccount(JsonElement value) {
        if (value.isJsonPrimitive()) return new Account(value.getAsString(), null);
        JsonObject obj = value.getAsJsonObject();
        JsonElement password = obj.get("password");
        return new Account(obj.get("username").getAsString(),
                password == null || password.isJsonNull() ? null : password.getAsString());
    }

    /** Snapshot for the poller: lowercased username -> entry. */
    public synchronized Map<String, Entry> byUsernameLower() {
        Map<String, Entry> out = new HashMap<>();
        for (Map.Entry<UUID, Account> e : byPlayer.entrySet()) {
            out.put(e.getValue().username().toLowerCase(Locale.ROOT),
                    new Entry(e.getKey(), e.getValue().username(), e.getValue().password()));
        }
        return out;
    }

    public synchronized String usernameFor(UUID mcId) {
        Account a = byPlayer.get(mcId);
        return a == null ? null : a.username();
    }

    /** The stored generated password, or null when unlinked OR the link predates minting. */
    public synchronized String passwordFor(UUID mcId) {
        Account a = byPlayer.get(mcId);
        return a == null ? null : a.password();
    }

    public synchronized UUID ownerOf(String username) {
        for (Map.Entry<UUID, Account> e : byPlayer.entrySet()) {
            if (e.getValue().username().equalsIgnoreCase(username)) return e.getKey();
        }
        return null;
    }

    /** Legacy/staff link with no stored password. */
    public synchronized LinkResult link(UUID mcId, String username, String mcNameForLog) {
        return link(mcId, username, null, mcNameForLog);
    }

    /**
     * @param password the bridge-generated password for a server-minted account, or null for a
     *                 legacy link (player-registered account, password unknown to the bridge)
     */
    public synchronized LinkResult link(UUID mcId, String username, String password, String mcNameForLog) {
        UUID owner = ownerOf(username);
        if (owner != null && !owner.equals(mcId)) {
            // First-come-first-served: refuse, and make sure staff can see the collision.
            LOGGER.warn("STAFF: MC player {} ({}) tried to link PokeRogue account '{}' already linked to MC uuid {}",
                    mcNameForLog, mcId, username, owner);
            return LinkResult.TAKEN;
        }
        Account previous = byPlayer.get(mcId);
        if (previous != null && previous.username().equalsIgnoreCase(username)) {
            // Same account: never let a password-less staff re-link erase a stored password.
            if (password != null && !password.equals(previous.password())) {
                byPlayer.put(mcId, new Account(username, password));
                save();
            }
            return LinkResult.ALREADY_LINKED;
        }
        byPlayer.put(mcId, new Account(username, password));
        save();
        return previous == null ? LinkResult.LINKED : LinkResult.RELINKED;
    }

    /** @return the username that was unlinked, or null if none was linked. */
    public synchronized String unlink(UUID mcId) {
        Account previous = byPlayer.remove(mcId);
        if (previous != null) save();
        return previous == null ? null : previous.username();
    }

    private void save() {
        JsonObject links = new JsonObject();
        for (Map.Entry<UUID, Account> e : byPlayer.entrySet()) {
            JsonObject account = new JsonObject();
            account.addProperty("username", e.getValue().username());
            if (e.getValue().password() != null) account.addProperty("password", e.getValue().password());
            links.add(e.getKey().toString(), account);
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
