package com.cobblemonpokerogue.bridge.link;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * rogueserver's HTTP API, for the two calls the §2.46 entry flow needs: account registration
 * and one-time entry-token minting. MUST only be called from the poller executor — these are
 * blocking network calls and share the "never on the main thread" rule with the DB.
 *
 * <p><b>Username legality (verified against the vendor checkout, api/account/common.go):</b>
 * rogueserver validates usernames with {@code ^\w{1,16}$} — Go's {@code \w} is
 * {@code [0-9A-Za-z_]} — and Minecraft names are 3-16 chars of exactly that class, so every MC
 * name is a legal rogueserver username <b>as-is</b>; no mapping is needed. Case is preserved
 * (username = MC name verbatim): the {@code accounts.username} column is UNIQUE under
 * MariaDB's case-insensitive default collation and every rogueserver lookup goes through
 * {@code WHERE username = ?} on that same collation, so case never has to be normalized.
 * Passwords need only {@code len >= 6} (api/account/register.go).
 *
 * <p>Registration failures come back as HTTP 500 with the Go error text as the body
 * (api/endpoints.go httpError); a duplicate username surfaces as MariaDB's
 * "Duplicate entry" INSERT error inside that text, which {@link #register} maps to
 * {@code taken} — the caller pre-checks the DB, so hitting it here is a race, but it must
 * still not be mistaken for a transient failure.
 */
public final class RogueserverApi {

    /** Exactly one of {@code created}/{@code taken} true, or both false with {@code failDetail} set. */
    public record RegisterOutcome(boolean created, boolean taken, String failDetail) {}

    /** {@code token} non-null on success, else {@code failDetail} says why (for the once-warn). */
    public record MintOutcome(String token, String failDetail) {}

    private final String apiBase;
    private final String tokenSecret;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public RogueserverApi(String apiBase, String tokenSecret) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.tokenSecret = tokenSecret == null ? "" : tokenSecret;
    }

    /** False when config.tokenSecret is empty — the operator has not enabled the token flow. */
    public boolean tokenEnabled() {
        return !tokenSecret.isEmpty();
    }

    /** POST /account/register (rogueserver's stock endpoint), form-encoded. 200 = created. */
    public RegisterOutcome register(String username, String password) {
        HttpResponse<String> response;
        try {
            response = send(HttpRequest.newBuilder(URI.create(apiBase + "/account/register"))
                    .timeout(Duration.ofSeconds(10))
                    // Registration is walled behind the shared secret (§2.46 follow-up):
                    // the bridge is the only place accounts are minted.
                    .header("X-Bridge-Secret", tokenSecret)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form("username", username, "password", password)))
                    .build());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new RegisterOutcome(false, false, "rogueserver unreachable: " + e.getMessage());
        }
        if (response.statusCode() == 200) return new RegisterOutcome(true, false, null);
        String body = response.body() == null ? "" : response.body().trim();
        if (body.contains("Duplicate entry")) return new RegisterOutcome(false, true, null);
        return new RegisterOutcome(false, false, "HTTP " + response.statusCode() + ": " + body);
    }

    /**
     * POST /bridge/minttoken with X-Bridge-Secret (frozen contract with the rogueserver patch,
     * §2.46): 200 {@code {"token": ...}}; 403 bad secret; 404 unknown username; 503 secret
     * unconfigured server-side (unpatched or undeployed).
     */
    public MintOutcome mintToken(String username) {
        HttpResponse<String> response;
        try {
            response = send(HttpRequest.newBuilder(URI.create(apiBase + "/bridge/minttoken"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Bridge-Secret", tokenSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(form("username", username)))
                    .build());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new MintOutcome(null, "rogueserver unreachable: " + e.getMessage());
        }
        return switch (response.statusCode()) {
            case 200 -> parseToken(response.body());
            case 403 -> new MintOutcome(null, "bridge secret rejected (403) — bridge and rogueserver secrets disagree");
            case 404 -> new MintOutcome(null, "unknown username '" + username + "' (404)");
            case 503 -> new MintOutcome(null, "token endpoint unconfigured on rogueserver (503) — patch not deployed");
            default -> new MintOutcome(null, "HTTP " + response.statusCode());
        };
    }

    private static MintOutcome parseToken(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            JsonElement token = obj.get("token");
            if (token != null && !token.isJsonNull() && !token.getAsString().isEmpty()) {
                return new MintOutcome(token.getAsString(), null);
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return new MintOutcome(null, "200 but no token in response body");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append('&');
            sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
