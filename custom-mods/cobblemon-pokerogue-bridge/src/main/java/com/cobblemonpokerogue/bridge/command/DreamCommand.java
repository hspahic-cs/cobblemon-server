package com.cobblemonpokerogue.bridge.command;

import com.cobblemonpokerogue.bridge.BridgeConfig;
import com.cobblemonpokerogue.bridge.BridgeServices;
import com.cobblemonpokerogue.bridge.CobblemonPokerogueBridge;
import com.cobblemonpokerogue.bridge.econ.NeoEssentialsEconomy;
import com.cobblemonpokerogue.bridge.journal.JournalBook;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.cobblemonpokerogue.bridge.link.RogueserverApi;
import com.cobblemonpokerogue.bridge.milestones.MilestoneEngine;
import com.cobblemonpokerogue.bridge.presentation.Anchors;
import com.cobblemonpokerogue.bridge.presentation.DreamLang;
import com.cobblemonpokerogue.bridge.presentation.PresentationConfig;
import com.cobblemonpokerogue.bridge.presentation.PresentationFeatures;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * /dream                        — THE verb (§2.46 + §2.47): mint the account if needed, then
 *                                 route — active session → free resume link; unspent armed
 *                                 credit → free new-run link; neither → charge, arm, new-run
 *                                 link. The tokenized link carries auto=new|resume.
 *                                 (/dream enter is a legacy alias.) A run that would CHARGE
 *                                 stops at a confirmation prompt first — /dream confirm
 *                                 (clickable, 60s window) unlocks the charge; the free
 *                                 resume/armed paths never prompt.
 * /dream status                 — clickable play link, linked account, §2.47 state line
 *                                 (dreaming / armed run waiting / next-run cost)
 * /dream password               — whisper the server-generated password (minted accounts only)
 * /dream claim                  — pay out pending milestone rewards (run as the server console)
 * /dream journal                — open the Dream Journal (run history as a written book)
 * /dream unlink [player]        — self, or any player at permission level 2+
 * /dream link <player> <user>   — STAFF ONLY (perm 2): repair a legacy web-account link
 * /dream admin board            — STAFF ONLY (perm 2): hang the leaderboard on the wall face
 *                                 you are looking at (live rebuild, persisted to config)
 * /dream admin shrine           — STAFF ONLY (perm 2): plant the shrine anchor at your feet
 *
 * <p>§2.46 killed the public link verb: accounts are server-minted (username = MC name, password
 * generated bridge-side — never typed, because MC logs every issued command to latest.log), so
 * squatting on someone else's username dies by construction. Staff keep a link verb for wiring
 * up legacy accounts that were registered in the web game before minting existed.
 */
public final class DreamCommand {

    private static final String USERNAME_PATTERN = "[A-Za-z0-9_]{1,16}";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 20;

    /** Token-mint failure is warned ONCE per JVM (then quietly degraded to the plain link). */
    private static volatile boolean warnedTokenMint = false;

    /**
     * Pending charge confirmations: player → expiry. Bare /dream stops before money moves and
     * asks; /dream confirm within the window re-runs the flow with the charge unlocked. The
     * free paths (resume, banked credit) never prompt. Entries are one-shot.
     */
    private static final ConcurrentHashMap<UUID, Long> PENDING_CONFIRM = new ConcurrentHashMap<>();
    private static final long CONFIRM_WINDOW_MS = 60_000;

    private DreamCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dream")
                .executes(DreamCommand::enter)
                .then(Commands.literal("enter") // legacy alias for the bare verb
                        .executes(DreamCommand::enter))
                .then(Commands.literal("confirm")
                        .executes(DreamCommand::confirm))
                .then(Commands.literal("status")
                        .executes(DreamCommand::status))
                .then(Commands.literal("password")
                        .executes(DreamCommand::password))
                .then(Commands.literal("claim")
                        .executes(DreamCommand::claim))
                .then(Commands.literal("journal")
                        .executes(DreamCommand::journal))
                .then(Commands.literal("unlink")
                        .executes(DreamCommand::unlinkSelf)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(src -> src.hasPermission(2))
                                .executes(DreamCommand::unlinkOther)))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("board")
                                .executes(DreamCommand::adminBoard))
                        .then(Commands.literal("journal")
                                .executes(DreamCommand::adminJournal))
                        .then(Commands.literal("shrine")
                                .executes(DreamCommand::adminShrine)))
                .then(Commands.literal("link")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("username", StringArgumentType.word())
                                        .executes(DreamCommand::staffLink)))));
    }

    private static BridgeServices services(CommandSourceStack src) {
        BridgeServices s = CobblemonPokerogueBridge.services();
        if (s == null) src.sendFailure(Component.literal("The PokeRogue bridge is not initialized."));
        return s;
    }

    /** Whisper to the player from any thread — hops to the main thread before touching them. */
    private static void reply(MinecraftServer server, UUID mcId, Component msg) {
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mcId);
            if (p != null) p.sendSystemMessage(msg);
        });
    }

    // ---- /dream --------------------------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> ctx) {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        DreamLang lang = DreamLang.shared();
        String url = s.config().url;
        MutableComponent msg = Component.literal("PokeRogue: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(url).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))));
        ServerPlayer player = ctx.getSource().getPlayer();
        String username = player == null ? null : s.links().usernameFor(player.getUUID());
        if (player != null) {
            if (username == null) {
                msg.append(Component.literal("\n" + lang.format("pokerogue.status.not_linked"))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                msg.append(Component.literal("\n" + lang.format("pokerogue.status.linked", username))
                        .withStyle(ChatFormatting.GRAY));
                int pending = s.milestones().pendingCount(username);
                if (pending > 0) {
                    msg.append(Component.literal("\n" + pending + " milestone reward(s) waiting — /dream claim")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        if (player == null || username == null) return 1;
        // The §2.47 state line needs the DB, so it arrives as a follow-up line off the
        // poller executor (the only thread that may touch the DB). Same routing order as
        // /dream enter: session → armed credit → pay.
        UUID mcId = player.getUUID();
        MinecraftServer server = s.server();
        String finalUsername = username;
        int fee = s.config().entryFee;
        s.poller().submit(() -> {
            String line;
            try {
                byte[] accountUuid = s.db().lookupAccountUuid(finalUsername);
                if (accountUuid == null) {
                    line = lang.format("pokerogue.status.armed_unavailable");
                } else if (s.db().hasSession(accountUuid)) {
                    line = lang.format("pokerogue.status.dreaming");
                } else {
                    int credits = s.db().armedCredits(accountUuid);
                    line = credits < 0
                            ? lang.format("pokerogue.status.armed_disabled")
                            : credits > 0
                            ? lang.format("pokerogue.status.armed_ready")
                            : fee > 0 && s.config().weeklyFreeRun && s.freeRuns().availableNow(mcId)
                            ? lang.format("pokerogue.status.free_ready")
                            : lang.format("pokerogue.status.next_cost", fee);
                }
            } catch (SQLException e) {
                s.db().invalidate();
                line = lang.format("pokerogue.status.armed_unavailable");
            }
            reply(server, mcId, Component.literal(line).withStyle(ChatFormatting.GRAY));
        });
        return 1;
    }

    // ---- /dream enter ----------------------------------------------------------------

    /**
     * §2.46 entry flow + §2.47 one-verb routing, in order, entirely on the poller executor
     * (all DB + HTTP off-thread):
     *
     * <ol>
     *   <li><b>Mint</b> (unlinked players only): pre-check {@code accounts} for the MC name —
     *       an existing account the bridge did not create belongs to someone (a legacy web
     *       account), so refuse and point at staff rather than silently binding it. Otherwise
     *       register via rogueserver with a generated password and store the link. Nothing has
     *       been charged anywhere in this step.</li>
     *   <li><b>Route</b> (§2.47) — three mutually exclusive paths, decided by DB reads that
     *       charge nothing and can only refuse (db_down) when the DB is unreachable:
     *       <ul>
     *         <li><b>Active session</b> (any sessionSaveData row) → FREE resume: no charge,
     *             no arm, link carries {@code auto=resume}.</li>
     *         <li><b>No session, armed credit &gt; 0</b> (paid earlier, abandoned before the
     *             first save) → FREE new run: the banked credit is reused, no charge, link
     *             carries {@code auto=new}.</li>
     *         <li><b>Neither</b> → charge → arm (§2.45 semantics, unchanged): the fee is
     *             withdrawn only after every cheap check passed, and the only compensation
     *             path is a refund when the arming INSERT fails after a successful charge.
     *             Link carries {@code auto=new}.</li>
     *       </ul></li>
     *   <li><b>Tokenized link</b>: mint a one-time session token (frozen §2.46/§2.47
     *       contract) and send {@code <url>/#pt=<token>&auto=new|resume} — the browser opens
     *       already logged in and the frontend consumes the directive one-shot at the first
     *       TitlePhase. Any mint failure (secret off, 403/404/503, rogueserver down) degrades
     *       to the plain URL plus a manual-login hint — with NO auto param, because a manual
     *       login cannot guarantee the right account; whatever charge state the flow reached
     *       stays (the run is armed / the session exists), so the player just logs in and
     *       picks the mode by hand.</li>
     * </ol>
     */
    private static int enter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return enterFlow(ctx, false);
    }

    /** /dream confirm — one-shot, windowed unlock of the charge path prompted by bare /dream. */
    private static int confirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Long expiry = PENDING_CONFIRM.remove(player.getUUID());
        if (expiry == null || expiry < System.currentTimeMillis()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                            DreamLang.shared().format("pokerogue.enter.confirm_expired"))
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        return enterFlow(ctx, true);
    }

    private static int enterFlow(CommandContext<CommandSourceStack> ctx, boolean confirmed)
            throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DreamLang lang = DreamLang.shared();
        int fee = s.config().entryFee;
        if (fee > 0 && !NeoEssentialsEconomy.available()) {
            // Never treat a missing economy as free entry.
            ctx.getSource().sendFailure(Component.literal(lang.format("pokerogue.enter.no_economy")));
            return 0;
        }
        UUID mcId = player.getUUID();
        String mcName = player.getGameProfile().getName();
        String linkedUsername = s.links().usernameFor(mcId);
        MinecraftServer server = s.server();
        s.poller().submit(() -> {
            String username = linkedUsername;
            if (username == null) {
                username = mintAccount(s, lang, server, mcId, mcName);
                if (username == null) return; // refused or failed; player already told, nothing charged
            }
            // §2.47 routing. Both reads happen BEFORE any money moves, so a DB failure here
            // refuses with nothing charged.
            byte[] accountUuid;
            boolean hasSession;
            try {
                accountUuid = s.db().lookupAccountUuid(username);
                hasSession = accountUuid != null && s.db().hasSession(accountUuid);
            } catch (SQLException e) {
                s.db().invalidate();
                reply(server, mcId, Component.literal(lang.format("pokerogue.enter.db_down"))
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (accountUuid == null) {
                reply(server, mcId, Component.literal(lang.format("pokerogue.enter.no_account", username))
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (hasSession) {
                // FREE: a session exists — resume it. No charge, no arm.
                reply(server, mcId, Component.literal(lang.format("pokerogue.enter.resume"))
                        .withStyle(ChatFormatting.GREEN));
                sendPlayLink(s, lang, server, mcId, username, "resume");
                return;
            }
            int credits;
            try {
                credits = s.db().armedCredits(accountUuid);
            } catch (SQLException e) {
                s.db().invalidate();
                reply(server, mcId, Component.literal(lang.format("pokerogue.enter.db_down"))
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (credits > 0) {
                // FREE: an unspent armed credit is already banked (paid earlier, abandoned
                // before the first save) — reuse it. No charge.
                reply(server, mcId, Component.literal(lang.format("pokerogue.enter.armed_reuse"))
                        .withStyle(ChatFormatting.GREEN));
                sendPlayLink(s, lang, server, mcId, username, "new");
                return;
            }
            // credits < 0 means bridgeRunArming does not exist (unpatched rogueserver) —
            // fall through: chargeAndArm's armRun sees the same missing table and refuses
            // with a refund, which keeps that failure path in exactly one place.
            boolean freeRun = fee > 0 && s.config().weeklyFreeRun && s.freeRuns().availableNow(mcId);
            if (!confirmed && fee > 0 && !freeRun) {
                // This is the only path where money would move: stop and ask first.
                PENDING_CONFIRM.put(mcId, System.currentTimeMillis() + CONFIRM_WINDOW_MS);
                reply(server, mcId, Component.literal(lang.format("pokerogue.enter.confirm", fee))
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("\n" + lang.format("pokerogue.enter.confirm_click"))
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.GOLD)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.RUN_COMMAND, "/dream confirm")))));
                return;
            }
            if (chargeAndArm(s, lang, server, mcId, accountUuid, freeRun ? 0 : fee, freeRun)) {
                if (freeRun) {
                    s.freeRuns().markUsed(mcId);
                }
                sendPlayLink(s, lang, server, mcId, username, "new");
            }
        });
        ctx.getSource().sendSuccess(() -> Component.literal(lang.format("pokerogue.enter.checking"))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Server-mints the PokeRogue account for an unlinked player (poller thread). Every MC name
     * is a legal rogueserver username as-is (see {@link RogueserverApi}), so username = MC name
     * verbatim. Returns the username on success, or null after telling the player why not.
     * No path in here charges anything.
     */
    private static String mintAccount(BridgeServices s, DreamLang lang, MinecraftServer server,
                                      UUID mcId, String mcName) {
        String existing;
        try {
            existing = s.db().lookupAccount(mcName);
        } catch (SQLException e) {
            s.db().invalidate();
            reply(server, mcId, Component.literal(lang.format("pokerogue.enter.db_down"))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        if (existing != null) {
            // A legacy web account with this MC name exists and is not linked to this player.
            // Never silently bind it — staff verify ownership and repair with /dream link.
            CobblemonPokerogueBridge.LOGGER.warn(
                    "STAFF: cannot mint a PokeRogue account for MC player {} ({}) — account '{}' already"
                            + " exists (legacy web account?); verify ownership and link with"
                            + " /dream link {} {}",
                    mcName, mcId, existing, mcName, existing);
            reply(server, mcId, Component.literal(lang.format("pokerogue.enter.account_taken", existing))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        String password = generatePassword();
        RogueserverApi.RegisterOutcome outcome = s.api().register(mcName, password);
        if (outcome.taken()) {
            // Race with the pre-check above — same staff path as an existing legacy account.
            CobblemonPokerogueBridge.LOGGER.warn(
                    "STAFF: registration for MC player {} ({}) hit a duplicate username '{}' — verify"
                            + " ownership and link with /dream link {} {}",
                    mcName, mcId, mcName, mcName, mcName);
            reply(server, mcId, Component.literal(lang.format("pokerogue.enter.account_taken", mcName))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        if (!outcome.created()) {
            CobblemonPokerogueBridge.LOGGER.warn("PokeRogue account registration for {} failed: {}",
                    mcName, outcome.failDetail());
            reply(server, mcId, Component.literal(lang.format("pokerogue.enter.register_failed"))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        LinkStore.LinkResult linked = s.links().link(mcId, mcName, password, mcName);
        if (linked == LinkStore.LinkResult.TAKEN) {
            // Only reachable if another MC player linked this exact username in the last few
            // milliseconds; the freshly registered account now dangles unlinked — staff's call.
            CobblemonPokerogueBridge.LOGGER.warn(
                    "STAFF: minted PokeRogue account '{}' but the username got linked to another MC"
                            + " player mid-mint; the account is registered but UNLINKED", mcName);
            reply(server, mcId, Component.literal(lang.format("pokerogue.enter.account_taken", mcName))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        reply(server, mcId, Component.literal(lang.format("pokerogue.enter.account_created", mcName))
                .withStyle(ChatFormatting.GREEN));
        return mcName;
    }

    /**
     * §2.45 charge → arm (poller thread), semantics unchanged: everything that can fail
     * cheaply is checked BEFORE money moves (the account lookup + §2.47 routing already
     * happened in the caller), the charge is an atomic check-and-deduct, and the only
     * compensation path is a refund when the arming INSERT fails after a successful charge.
     * Returns true when a run is armed (fee charged and kept).
     */
    private static boolean chargeAndArm(BridgeServices s, DreamLang lang, MinecraftServer server,
                                        UUID mcId, byte[] accountUuid, int fee, boolean freeRun) {
        // Charge LAST-but-one: nothing has been taken until this succeeds, and false means
        // nothing was taken either (verified atomic check-and-deduct).
        if (fee > 0 && !NeoEssentialsEconomy.withdraw(mcId, fee)) {
            reply(server, mcId, Component.literal(
                            lang.format("pokerogue.enter.insufficient", fee, NeoEssentialsEconomy.balance(mcId)))
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        boolean armed;
        try {
            armed = s.db().armRun(accountUuid);
        } catch (SQLException e) {
            s.db().invalidate();
            refundAndReply(server, mcId, fee, lang, "pokerogue.enter.db_failed_refund");
            return false;
        }
        if (!armed) {
            // bridgeRunArming does not exist yet (unpatched rogueserver) — degrade clearly.
            refundAndReply(server, mcId, fee, lang, "pokerogue.enter.not_enabled");
            return false;
        }
        reply(server, mcId, Component.literal(freeRun
                        ? lang.format("pokerogue.enter.free_used")
                        : lang.format("pokerogue.enter.success", fee))
                .withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Compensation for a charge whose follow-up failed: refund, then explain. */
    private static void refundAndReply(MinecraftServer server, UUID mcId, int fee, DreamLang lang, String key) {
        if (fee > 0 && !NeoEssentialsEconomy.deposit(mcId, fee)) {
            // Should be unreachable (deposit only fails when the economy vanished mid-command);
            // loud log so staff can compensate by hand.
            CobblemonPokerogueBridge.LOGGER.error(
                    "STAFF: failed to refund {} entry fee ({}) after a failed arming write — refund manually",
                    mcId, fee);
        }
        reply(server, mcId, Component.literal(lang.format(key)).withStyle(ChatFormatting.RED));
    }

    /**
     * The tokenized link (§2.46/§2.47 frozen contract), poller thread: the fragment is
     * {@code #pt=<URL-encoded token>&auto=new|resume}. Failure never unwinds anything —
     * whatever charge state the flow reached stays — and the degraded plain URL carries NO
     * auto param, because a manual login cannot guarantee the right account.
     *
     * @param auto the §2.47 directive, {@code "new"} or {@code "resume"}.
     */
    private static void sendPlayLink(BridgeServices s, DreamLang lang, MinecraftServer server,
                                     UUID mcId, String username, String auto) {
        String base = s.config().url;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (s.api().tokenEnabled()) {
            RogueserverApi.MintOutcome minted = s.api().mintToken(username);
            if (minted.token() != null) {
                String tokenUrl = base + "/#pt=" + URLEncoder.encode(minted.token(), StandardCharsets.UTF_8)
                        + "&auto=" + auto;
                reply(server, mcId, playLink(lang, tokenUrl)
                        .append(Component.literal("\n" + lang.format("pokerogue.enter.logged_in"))
                                .withStyle(ChatFormatting.GRAY)));
                return;
            }
            if (!warnedTokenMint) {
                warnedTokenMint = true;
                CobblemonPokerogueBridge.LOGGER.warn(
                        "entry-token mint failed ({}) — sending plain links until it recovers", minted.failDetail());
            }
        }
        reply(server, mcId, playLink(lang, base)
                .append(Component.literal("\n" + lang.format("pokerogue.enter.manual_login", username))
                        .withStyle(ChatFormatting.GRAY)));
    }

    private static MutableComponent playLink(DreamLang lang, String url) {
        return Component.literal(lang.format("pokerogue.enter.play")).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(url).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))));
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ---- /dream password -------------------------------------------------------------

    private static int password(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DreamLang lang = DreamLang.shared();
        String username = s.links().usernameFor(player.getUUID());
        if (username == null) {
            ctx.getSource().sendFailure(Component.literal(lang.format("pokerogue.password.not_linked")));
            return 0;
        }
        String stored = s.links().passwordFor(player.getUUID());
        if (stored == null) {
            ctx.getSource().sendFailure(Component.literal(lang.format("pokerogue.password.legacy")));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(lang.format("pokerogue.password.show", username, stored))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ---- /dream admin (perm 2): live anchor placement ------------------------------------

    /** Hangs the leaderboard on the wall face the staff member is looking at, item-frame style. */
    private static int adminBoard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return adminWall(ctx, false);
    }

    /** Same placement flow for the personal journal wall. */
    private static int adminJournal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return adminWall(ctx, true);
    }

    private static int adminWall(CommandContext<CommandSourceStack> ctx, boolean journalWall)
            throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Anchors anchors = PresentationFeatures.anchors();
        if (anchors == null) {
            ctx.getSource().sendFailure(Component.literal("The presentation layer is not initialized."));
            return 0;
        }
        HitResult hit = player.pick(10.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Look at the wall block you want it hung on (within 10 blocks)."));
            return 0;
        }
        Direction face = blockHit.getDirection();
        if (face.getAxis().isVertical()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Look at a SIDE face of the wall, not its top or bottom."));
            return 0;
        }
        BlockPos pos = blockHit.getBlockPos().relative(face);
        String dimension = player.level().dimension().location().toString();
        BridgeConfig.Board prev = journalWall ? s.config().journalWall : s.config().leaderboard;
        BridgeConfig.Board board = new BridgeConfig.Board();
        board.dimension = dimension;
        board.x = pos.getX();
        board.y = pos.getY();
        board.z = pos.getZ();
        board.facing = face.getName();
        if (prev != null) {
            board.size = prev.size;
            board.scale = prev.scale;
            board.spriteDir = prev.spriteDir;
        }
        PresentationConfig.BoardPos anchor = new PresentationConfig.BoardPos(
                dimension, board.x, board.y, board.z, board.facing, board.size, board.scale,
                board.spriteDir);
        String what;
        if (journalWall) {
            s.config().journalWall = board;
            anchors.setJournal(anchor);
            what = "Journal wall";
        } else {
            s.config().leaderboard = board;
            anchors.setBoard(anchor);
            what = "Leaderboard";
        }
        saveConfig(s);
        if (!journalWall) {
            PresentationFeatures.refreshBoard();
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                        what + " hung at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                + " facing " + face.getName() + " — rows stack downward. Saved.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Plants the shrine anchor (ghost ring center) at the staff member's feet. */
    private static int adminShrine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Anchors anchors = PresentationFeatures.anchors();
        if (anchors == null) {
            ctx.getSource().sendFailure(Component.literal("The presentation layer is not initialized."));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        String dimension = player.level().dimension().location().toString();
        BridgeConfig.Shrine prev = s.config().shrine;
        BridgeConfig.Shrine shrine = new BridgeConfig.Shrine();
        shrine.dimension = dimension;
        shrine.x = pos.getX();
        shrine.y = pos.getY();
        shrine.z = pos.getZ();
        if (prev != null) {
            shrine.radius = prev.radius;
        }
        s.config().shrine = shrine;
        saveConfig(s);
        anchors.setShrine(new PresentationConfig.ShrinePos(
                dimension, pos.getX(), pos.getY(), pos.getZ(), Math.max(2, shrine.radius)));
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "Shrine anchored at your feet (" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                + ") — dream ghosts ring this point. Saved.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static void saveConfig(BridgeServices s) {
        try {
            s.config().save(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("cobblemon-pokerogue-bridge").resolve("config.json"));
        } catch (IOException e) {
            CobblemonPokerogueBridge.LOGGER.error(
                    "failed to persist config after /dream admin — the anchor is live but will not survive a restart", e);
        }
    }

    // ---- /dream link <player> <username> (staff repair, perm 2) ----------------------

    /**
     * Staff-only legacy repair: wires an MC player to a pre-minting web account after staff
     * verified ownership out of band. Validates the account exists in the DB first; stores no
     * password (the player knows their own).
     */
    private static int staffLink(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            ctx.getSource().sendFailure(Component.literal("Select exactly one player to link."));
            return 0;
        }
        GameProfile profile = profiles.iterator().next();
        String requested = StringArgumentType.getString(ctx, "username").trim();
        if (!requested.matches(USERNAME_PATTERN)) {
            ctx.getSource().sendFailure(Component.literal(
                    "That is not a valid PokeRogue username (1-16 letters/digits/underscore)."));
            return 0;
        }
        UUID mcId = profile.getId();
        String mcName = profile.getName();
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = s.server();
        // DB validation happens on the poller thread (the only thread that touches the DB);
        // the outcome hops back to the main thread to mutate the store and reply.
        s.poller().submit(() -> {
            String canonical;
            try {
                canonical = s.db().lookupAccount(requested);
            } catch (SQLException e) {
                s.db().invalidate();
                server.execute(() -> source.sendFailure(Component.literal(
                        "The PokeRogue database is unreachable right now — try again later.")));
                return;
            }
            server.execute(() -> {
                if (canonical == null) {
                    source.sendFailure(Component.literal(
                            "No PokeRogue account named '" + requested + "' exists."));
                    return;
                }
                LinkStore.LinkResult result = s.links().link(mcId, canonical, mcName);
                Component msg = switch (result) {
                    case LINKED -> Component.literal("Linked " + mcName + " to PokeRogue account '" + canonical + "'.")
                            .withStyle(ChatFormatting.GREEN);
                    case RELINKED -> Component.literal("Re-linked " + mcName + " to PokeRogue account '" + canonical + "'.")
                            .withStyle(ChatFormatting.GREEN);
                    case ALREADY_LINKED -> Component.literal(mcName + " is already linked to '" + canonical + "'.")
                            .withStyle(ChatFormatting.YELLOW);
                    case TAKEN -> Component.literal(
                                    "'" + canonical + "' is already linked to a different player — unlink them first.")
                            .withStyle(ChatFormatting.RED);
                };
                if (result == LinkStore.LinkResult.TAKEN || result == LinkStore.LinkResult.ALREADY_LINKED) {
                    source.sendFailure(msg);
                } else {
                    source.sendSuccess(() -> msg, true);
                }
            });
        });
        source.sendSuccess(() -> Component.literal("Checking PokeRogue account '" + requested + "'...")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ---- /dream unlink [player] ------------------------------------------------------

    private static int unlinkSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String previous = s.links().unlink(player.getUUID());
        if (previous == null) {
            ctx.getSource().sendFailure(Component.literal("You are not linked to a PokeRogue account."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Unlinked PokeRogue account '" + previous + "'.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int unlinkOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        int count = 0;
        for (GameProfile profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
            String previous = s.links().unlink(profile.getId());
            String name = profile.getName();
            if (previous == null) {
                ctx.getSource().sendFailure(Component.literal(name + " is not linked to a PokeRogue account."));
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Unlinked " + name + " from PokeRogue account '" + previous + "'."), true);
                count++;
            }
        }
        return count;
    }

    // ---- /dream journal ------------------------------------------------------------------

    private static int journal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DreamLang lang = DreamLang.shared();
        String username = s.links().usernameFor(player.getUUID());
        if (username == null) {
            ctx.getSource().sendFailure(Component.literal(lang.format("pokerogue.journal.not_linked")));
            return 0;
        }
        var entries = s.journal().entriesFor(username);
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(lang.format("pokerogue.journal.empty"))
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        long deepest = s.milestones().maxClassicWave(username);
        for (var e : entries) {
            deepest = Math.max(deepest, e.wave());
        }
        JournalBook.open(player, lang, entries, deepest);
        // And the sprite view: render this player's history on the journal wall, if one is hung.
        var wall = PresentationFeatures.journalWall();
        if (wall != null && wall.show(player, entries)) {
            ctx.getSource().sendSuccess(() -> Component.literal(lang.format("pokerogue.journalwall.shown"))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    // ---- /dream claim ----------------------------------------------------------------

    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String username = s.links().usernameFor(player.getUUID());
        if (username == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "You have no PokeRogue account yet — /dream creates one."));
            return 0;
        }
        var claims = s.milestones().takePending(username);
        if (claims.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No pending PokeRogue rewards."));
            return 0;
        }
        MinecraftServer server = s.server();
        String playerName = player.getGameProfile().getName();
        // Reward commands run as the SERVER CONSOLE (full permission), synchronously on this
        // (main) thread, with %player% substituted. Their output stays in the server log.
        CommandSourceStack console = server.createCommandSourceStack();
        for (MilestoneEngine.MilestoneDef def : claims) {
            for (String command : def.rewards()) {
                String cmd = command.replace("%player%", playerName);
                try {
                    server.getCommands().performPrefixedCommand(console, cmd);
                } catch (RuntimeException e) {
                    CobblemonPokerogueBridge.LOGGER.error("milestone '{}' reward command failed: {}", def.id(), cmd, e);
                }
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Milestone claimed: ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(def.display()).withStyle(ChatFormatting.GREEN)), false);
        }
        return claims.size();
    }
}
