package com.cobblemonpokerogue.bridge.command;

import com.cobblemonpokerogue.bridge.BridgeServices;
import com.cobblemonpokerogue.bridge.CobblemonPokerogueBridge;
import com.cobblemonpokerogue.bridge.econ.NeoEssentialsEconomy;
import com.cobblemonpokerogue.bridge.link.LinkStore;
import com.cobblemonpokerogue.bridge.milestones.MilestoneEngine;
import com.cobblemonpokerogue.bridge.presentation.DreamLang;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.sql.SQLException;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * /pokerogue           — clickable play link + linked-account status
 * /pokerogue link <u>  — validate the account exists in the DB, then link (first-come-first-served)
 * /pokerogue unlink    — unlink self; /pokerogue unlink <player> needs permission level 2
 * /pokerogue enter     — charge the entry fee and arm one classic run (§2.45 pay-to-dream)
 * /pokerogue claim     — pay out pending milestone rewards (commands run as the server console)
 */
public final class PokerogueCommand {

    private static final String USERNAME_PATTERN = "[A-Za-z0-9_]{1,16}";

    private PokerogueCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pokerogue")
                .executes(PokerogueCommand::status)
                .then(Commands.literal("link")
                        .then(Commands.argument("username", StringArgumentType.word())
                                .executes(PokerogueCommand::link)))
                .then(Commands.literal("unlink")
                        .executes(PokerogueCommand::unlinkSelf)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(src -> src.hasPermission(2))
                                .executes(PokerogueCommand::unlinkOther)))
                .then(Commands.literal("enter")
                        .executes(PokerogueCommand::enter))
                .then(Commands.literal("claim")
                        .executes(PokerogueCommand::claim)));
    }

    private static BridgeServices services(CommandSourceStack src) {
        BridgeServices s = CobblemonPokerogueBridge.services();
        if (s == null) src.sendFailure(Component.literal("The PokeRogue bridge is not initialized."));
        return s;
    }

    // ---- /pokerogue ----------------------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> ctx) {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        String url = s.config().url;
        MutableComponent msg = Component.literal("PokeRogue: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(url).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))));
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            String username = s.links().usernameFor(player.getUUID());
            if (username == null) {
                msg.append(Component.literal("\nNot linked — use /pokerogue link <username>")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                msg.append(Component.literal("\nLinked account: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(username).withStyle(ChatFormatting.GREEN)));
                int pending = s.milestones().pendingCount(username);
                if (pending > 0) {
                    msg.append(Component.literal("\n" + pending + " milestone reward(s) waiting — /pokerogue claim")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ---- /pokerogue link <username> ------------------------------------------------------

    private static int link(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String requested = StringArgumentType.getString(ctx, "username").trim();
        if (!requested.matches(USERNAME_PATTERN)) {
            ctx.getSource().sendFailure(Component.literal(
                    "That is not a valid PokeRogue username (1-16 letters/digits/underscore)."));
            return 0;
        }
        UUID mcId = player.getUUID();
        String mcName = player.getGameProfile().getName();
        MinecraftServer server = s.server();
        // DB validation happens on the poller thread (the only thread that touches the DB);
        // the outcome hops back to the main thread to mutate the store and reply.
        s.poller().submit(() -> {
            String canonical;
            try {
                canonical = s.db().lookupAccount(requested);
            } catch (SQLException e) {
                s.db().invalidate();
                server.execute(() -> reply(server, mcId,
                        Component.literal("The PokeRogue database is unreachable right now — try again later.")
                                .withStyle(ChatFormatting.RED)));
                return;
            }
            server.execute(() -> {
                if (canonical == null) {
                    reply(server, mcId, Component.literal(
                                    "No PokeRogue account named '" + requested + "' exists — register in the web game first.")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                LinkStore.LinkResult result = s.links().link(mcId, canonical, mcName);
                Component msg = switch (result) {
                    case LINKED -> Component.literal("Linked to PokeRogue account '" + canonical + "'.")
                            .withStyle(ChatFormatting.GREEN);
                    case RELINKED -> Component.literal("Re-linked to PokeRogue account '" + canonical + "'.")
                            .withStyle(ChatFormatting.GREEN);
                    case ALREADY_LINKED -> Component.literal("You are already linked to '" + canonical + "'.")
                            .withStyle(ChatFormatting.YELLOW);
                    case TAKEN -> Component.literal(
                                    "'" + canonical + "' is already linked to another player. Staff have been notified.")
                            .withStyle(ChatFormatting.RED);
                };
                reply(server, mcId, msg);
            });
        });
        ctx.getSource().sendSuccess(() -> Component.literal("Checking PokeRogue account '" + requested + "'...")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static void reply(MinecraftServer server, UUID mcId, Component msg) {
        ServerPlayer p = server.getPlayerList().getPlayer(mcId);
        if (p != null) p.sendSystemMessage(msg);
    }

    // ---- /pokerogue unlink [player] ------------------------------------------------------

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

    // ---- /pokerogue enter ----------------------------------------------------------------

    /**
     * §2.45 pay-to-dream: charge {@code entryFee} of NeoEssentials currency, then write ONE
     * armed-run credit to bridgeRunArming (the patched rogueserver consumes it when a new
     * classic run first saves). Ordering is deliberate — everything that can fail cheaply is
     * checked BEFORE money moves, and the charge itself is an atomic check-and-deduct
     * (verified: EconomyManager.subtractBalance returns false leaving the balance untouched
     * when funds are short), so the only compensation path is a refund when the arming INSERT
     * fails in the narrow window after a successful charge.
     */
    private static int enter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DreamLang lang = DreamLang.shared();
        String username = s.links().usernameFor(player.getUUID());
        if (username == null) {
            ctx.getSource().sendFailure(Component.literal(lang.format("pokerogue.enter.not_linked")));
            return 0;
        }
        int fee = s.config().entryFee;
        if (fee > 0 && !NeoEssentialsEconomy.available()) {
            // Never treat a missing economy as free entry.
            ctx.getSource().sendFailure(Component.literal(lang.format("pokerogue.enter.no_economy")));
            return 0;
        }
        UUID mcId = player.getUUID();
        MinecraftServer server = s.server();
        // Everything below runs on the poller thread — the only thread that touches the DB.
        // The economy manager is thread-safe (atomic ConcurrentHashMap.compute), so charging
        // here keeps charge and credit in one straight-line sequence with no interleaving.
        s.poller().submit(() -> {
            byte[] accountUuid;
            try {
                accountUuid = s.db().lookupAccountUuid(username);
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
            // Charge LAST-but-one: nothing has been taken until this succeeds, and false
            // means nothing was taken either.
            if (fee > 0 && !NeoEssentialsEconomy.withdraw(mcId, fee)) {
                reply(server, mcId, Component.literal(
                                lang.format("pokerogue.enter.insufficient", fee, NeoEssentialsEconomy.balance(mcId)))
                        .withStyle(ChatFormatting.RED));
                return;
            }
            boolean armed;
            try {
                armed = s.db().armRun(accountUuid);
            } catch (SQLException e) {
                s.db().invalidate();
                refundAndReply(server, mcId, fee, lang, "pokerogue.enter.db_failed_refund");
                return;
            }
            if (!armed) {
                // bridgeRunArming does not exist yet (unpatched rogueserver) — degrade clearly.
                refundAndReply(server, mcId, fee, lang, "pokerogue.enter.not_enabled");
                return;
            }
            reply(server, mcId, Component.literal(lang.format("pokerogue.enter.success", fee))
                    .withStyle(ChatFormatting.GREEN));
        });
        ctx.getSource().sendSuccess(() -> Component.literal(lang.format("pokerogue.enter.checking"))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
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

    // ---- /pokerogue claim ----------------------------------------------------------------

    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BridgeServices s = services(ctx.getSource());
        if (s == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String username = s.links().usernameFor(player.getUUID());
        if (username == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "You are not linked to a PokeRogue account — /pokerogue link <username> first."));
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
