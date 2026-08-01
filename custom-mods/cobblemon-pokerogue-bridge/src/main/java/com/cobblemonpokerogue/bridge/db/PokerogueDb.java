package com.cobblemonpokerogue.bridge.db;

import com.cobblemonpokerogue.bridge.BridgeConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only access to the self-hosted rogueserver MariaDB ({@code pokeroguedb}).
 *
 * <p>Single connection, owned and used exclusively by the poller thread (link validation is
 * routed onto the same executor), recreated lazily after failures. DriverManager is
 * deliberately avoided: the driver ships jar-in-jar and FML's module layers make
 * DriverManager's caller-classloader driver lookup unreliable — instantiating
 * {@code org.mariadb.jdbc.Driver} directly sidesteps that entirely.
 *
 * <p>Run DETAIL (seed/wave/species/mode) comes from {@code bridgeRunState}, a side table our
 * patched rogueserver maintains (REPLACEd on every session save, DELETEd with the session).
 * {@code sessionSaveData.data} itself is zstd-wrapped Go gob — not decodable from Java — and
 * the HTTP session-get endpoint must never be used (it overwrites activeClientSessions and
 * kicks the live player's client). Against an UNPATCHED rogueserver, {@link #fetchRunStates}
 * returns null and the bridge degrades to row-existence/timestamp lifecycle only.
 */
public final class PokerogueDb implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-pokerogue-bridge");

    /** One sessionSaveData row header — existence + timestamp is the authoritative lifecycle signal. */
    public record SessionHeader(String usernameLower, int slot, long timestampMs) {}

    /** One bridgeRunState row: live run detail maintained by the patched rogueserver. */
    public record RunStateRow(String usernameLower, int slot, String seed, int gameMode,
                              int waveIndex, int leadSpecies, long updatedAtMs) {}

    private final BridgeConfig.Db cfg;
    private Connection conn;

    public PokerogueDb(BridgeConfig.Db cfg) {
        this.cfg = cfg;
    }

    private synchronized Connection connection() throws SQLException {
        if (conn != null) {
            try {
                if (conn.isValid(2)) return conn;
            } catch (SQLException ignored) {
                // fall through and reconnect
            }
            invalidate();
        }
        String url = "jdbc:mariadb://" + cfg.host + ":" + cfg.port + "/" + cfg.database;
        Properties props = new Properties();
        props.setProperty("user", cfg.user == null ? "" : cfg.user);
        props.setProperty("password", cfg.password == null ? "" : cfg.password);
        // Bounded so a dead DB can never wedge the poller thread.
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "15000");
        Connection c = new org.mariadb.jdbc.Driver().connect(url, props);
        if (c == null) throw new SQLException("mariadb driver rejected url " + url);
        c.setReadOnly(true);
        conn = c;
        return c;
    }

    /** Drop the cached connection so the next call reconnects. Call after any SQLException. */
    public synchronized void invalidate() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
            conn = null;
        }
    }

    @Override
    public synchronized void close() {
        invalidate();
    }

    /**
     * @return the canonical-case username if the account exists in {@code accounts}, else null.
     */
    public synchronized String lookupAccount(String username) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT username FROM accounts WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * All numeric accountStats columns for the given usernames, keyed by lowercased username
     * then by column name (playTime, battles, classicSessionsPlayed, sessionsWon,
     * highestEndlessWave, highestLevel, pokemonSeen, pokemonDefeated, pokemonCaught,
     * pokemonHatched, eggsPulled, the voucher counters, ...). Columns are discovered from
     * result-set metadata rather than hardcoded so a rogueserver schema bump cannot break the
     * poll, and so milestones.json may reference any counter the table actually has.
     */
    public synchronized Map<String, Map<String, Long>> fetchStats(Collection<String> usernames) throws SQLException {
        Map<String, Map<String, Long>> out = new HashMap<>();
        if (usernames.isEmpty()) return out;
        String sql = "SELECT a.username AS bridge_username, s.* FROM accountStats s"
                + " JOIN accounts a ON a.uuid = s.uuid WHERE a.username IN (" + placeholders(usernames.size()) + ")";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, usernames);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Long> stats = new HashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        String label = md.getColumnLabel(i);
                        if (label.equalsIgnoreCase("bridge_username") || label.equalsIgnoreCase("uuid")) continue;
                        try {
                            long v = rs.getLong(i);
                            if (!rs.wasNull()) stats.put(label, v);
                        } catch (SQLException nonNumeric) {
                            // defensively skip anything that isn't a counter
                        }
                    }
                    out.put(rs.getString("bridge_username").toLowerCase(Locale.ROOT), stats);
                }
            }
        }
        return out;
    }

    /** Session-save headers (no blob) for the given usernames. */
    public synchronized List<SessionHeader> fetchSessionHeaders(Collection<String> usernames) throws SQLException {
        List<SessionHeader> out = new ArrayList<>();
        if (usernames.isEmpty()) return out;
        String sql = "SELECT a.username AS bridge_username, s.slot, s.timestamp FROM sessionSaveData s"
                + " JOIN accounts a ON a.uuid = s.uuid WHERE a.username IN (" + placeholders(usernames.size()) + ")";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, usernames);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("timestamp");
                    out.add(new SessionHeader(
                            rs.getString("bridge_username").toLowerCase(Locale.ROOT),
                            rs.getInt("slot"),
                            ts == null ? 0L : ts.getTime()));
                }
            }
        }
        return out;
    }

    /** Lowercased usernames that currently have a row in activeClientSessions (game tab open). */
    public synchronized Set<String> fetchActiveUsernames(Collection<String> usernames) throws SQLException {
        Set<String> out = new HashSet<>();
        if (usernames.isEmpty()) return out;
        String sql = "SELECT a.username FROM activeClientSessions c"
                + " JOIN accounts a ON a.uuid = c.uuid WHERE a.username IN (" + placeholders(usernames.size()) + ")";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, usernames);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private boolean warnedNoBridgeTable = false;
    private boolean warnedNoCompletionsTable = false;

    /**
     * bridgeRunState rows for the given usernames, or NULL when the table does not exist —
     * i.e. rogueserver is unpatched — in which case the caller runs in degraded mode
     * (start/end from sessionSaveData row existence + timestamp; no waves, no species).
     * The missing table is warned about exactly once.
     */
    public synchronized List<RunStateRow> fetchRunStates(Collection<String> usernames) throws SQLException {
        if (usernames.isEmpty()) return List.of();
        String sql = "SELECT a.username AS bridge_username, r.slot, r.seed, r.gameMode, r.waveIndex,"
                + " r.leadSpecies, r.updatedAt FROM bridgeRunState r"
                + " JOIN accounts a ON a.uuid = r.uuid WHERE a.username IN (" + placeholders(usernames.size()) + ")";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, usernames);
            try (ResultSet rs = ps.executeQuery()) {
                List<RunStateRow> out = new ArrayList<>();
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("updatedAt");
                    out.add(new RunStateRow(
                            rs.getString("bridge_username").toLowerCase(Locale.ROOT),
                            rs.getInt("slot"),
                            rs.getString("seed"),
                            rs.getInt("gameMode"),
                            rs.getInt("waveIndex"),
                            rs.getInt("leadSpecies"),
                            ts == null ? 0L : ts.getTime()));
                }
                return out;
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) {
                if (!warnedNoBridgeTable) {
                    LOGGER.warn("bridgeRunState table not found — rogueserver is unpatched; running in"
                            + " DEGRADED mode (run start/end only, no wave/species detail)");
                    warnedNoBridgeTable = true;
                }
                return null;
            }
            throw e;
        }
    }

    /**
     * True if dailyRunCompletions has a row for this account + seed. Despite the name the
     * table records CLASSIC victories too (written server-side on /savedata/session/clear) —
     * the strongest victory signal. Any error (including a missing table on ancient schemas)
     * degrades to false; the caller then falls back to the sessionsWon delta.
     */
    public synchronized boolean hasCompletion(String username, String seed) {
        if (username == null || seed == null || seed.isEmpty()) return false;
        String sql = "SELECT 1 FROM dailyRunCompletions d JOIN accounts a ON a.uuid = d.uuid"
                + " WHERE a.username = ? AND d.seed = ? LIMIT 1";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, seed);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) {
                if (!warnedNoCompletionsTable) {
                    LOGGER.warn("dailyRunCompletions table not found — victory detection falls back to"
                            + " the sessionsWon delta only");
                    warnedNoCompletionsTable = true;
                }
            }
            return false;
        }
    }

    /** MariaDB "table doesn't exist": error 1146 / SQLSTATE 42S02. */
    private static boolean isMissingTable(SQLException e) {
        return e.getErrorCode() == 1146 || "42S02".equals(e.getSQLState());
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static void bind(PreparedStatement ps, Collection<String> values) throws SQLException {
        int i = 1;
        for (String v : values) ps.setString(i++, v);
    }
}
