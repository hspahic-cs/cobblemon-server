package com.cobblemonranked.commands

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.economy.EconomyBridge
import com.cobblemonranked.rental.DraftTeams
import com.cobblemonranked.rental.PokePasteFetcher
import com.cobblemonranked.rental.RentalTeams
import com.cobblemonranked.rental.ShowdownPasteParser
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import java.time.Duration
import java.time.Instant

/**
 * `/ranked draft` — player-drafted custom rental teams (docs/rental-drafts-plan.md).
 *
 * The paste travels in a book & quill the player is holding: Showdown teambuilder export, pasted
 * across as many pages as needed. Creation prices climb the `draftSlotCosts` ladder per concurrent
 * draft; each draft's first edit is free and later edits cost the flat `draftEditCost`. Validation
 * runs (and reports problems) BEFORE any money moves.
 */
object DraftCommands {

    fun buildDraftCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("draft")
            .requires { CobblemonRanked.config.allowDraftTeams }
            .executes { ctx -> showDraftHelp(ctx.source); 1 }
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes { ctx ->
                        handleSaveFromBook(ctx.source.playerOrException, StringArgumentType.getString(ctx, "name"), isEdit = false); 1
                    }
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes { ctx ->
                            handleSaveFromUrl(ctx.source.playerOrException,
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "url"), isEdit = false); 1
                        })))
            .then(Commands.literal("edit")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes { ctx ->
                        handleSaveFromBook(ctx.source.playerOrException, StringArgumentType.getString(ctx, "name"), isEdit = true); 1
                    }
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes { ctx ->
                            handleSaveFromUrl(ctx.source.playerOrException,
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "url"), isEdit = true); 1
                        })))
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes { ctx ->
                        handleDelete(ctx.source.playerOrException, StringArgumentType.getString(ctx, "name")); 1
                    }))
            .then(Commands.literal("list")
                .executes { ctx -> handleList(ctx.source.playerOrException); 1 })
            .then(Commands.literal("export")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes { ctx ->
                        handleExport(ctx.source.playerOrException, StringArgumentType.getString(ctx, "name")); 1
                    }))

    private fun showDraftHelp(source: CommandSourceStack) {
        val config = CobblemonRanked.config
        val ladder = (0 until minOf(config.maxDraftSlots, config.draftSlotCosts.size))
            .joinToString("§7 → §e") { "\$${DraftTeams.slotCost(it)}" }
        listOf(
            "§e[Ranked] §fDraft teams — design a custom rental:",
            "§7  1. Buy a §fDraft Team Slot§7 at the Shopkeeper's §fUpgrades§7 tab (permanent).",
            "§7  2. Build the team in the Showdown teambuilder, upload it to §fpokepast.es§7.",
            "§7  3. /ranked draft create <name> <link> §f— your slot's first team is §ffree§f; later fills §e\$${config.draftRefillCost}§f",
            "§7     §8Quote names with spaces: create \"Rain Team\" <link>. No link? Hold a book &",
            "§7     §8quill containing the pasted export instead.",
            "§7  /ranked draft edit <name> §f— tune a draft (keep ${DraftTeams.TUNE_MIN_SHARED_SPECIES}+ species; 1st free, then §e\$${config.draftEditCost}§f)",
            "§7  §8Swapping to a mostly-new team costs §e\$${config.draftSwapCost}§8, once per slot per ${config.draftIdentityCooldownHours / 24} days.",
            "§7  /ranked draft delete <name> §f— empty the slot; you keep it forever",
            "§7  /ranked draft list §f— your drafts and open slots",
            "§7  /ranked draft export <name> §f— build sheet for making the team real",
            "§7Slot prices rise: §e$ladder§7 — each is a one-time unlock.",
            "§7Drafts battle with rental de-tune: §f${DraftTeams.RENTAL_EV_CAP} EV cap, ${DraftTeams.RENTAL_IVS} IVs§7. Rental rules apply.",
        ).forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    private fun handleSaveFromBook(player: ServerPlayer, name: String, isEdit: Boolean) {
        if (!prereqsOk(player, name, isEdit)) return
        val text = heldBookText(player)
        if (text.isNullOrBlank()) {
            player.sendSystemMessage(Component.literal(
                "§c[Ranked] Hold a book & quill containing your Showdown export — or skip the book: " +
                    "§f/ranked draft ${if (isEdit) "edit" else "create"} $name <pokepast.es link>"))
            return
        }
        saveFromText(player, name, isEdit, text)
    }

    private fun handleSaveFromUrl(player: ServerPlayer, name: String, rawUrl: String, isEdit: Boolean) {
        if (!prereqsOk(player, name, isEdit)) return
        val url = PokePasteFetcher.normalize(rawUrl)
        if (url == null) {
            player.sendSystemMessage(Component.literal(
                "§c[Ranked] Only §fpokepast.es§c links work here (e.g. https://pokepast.es/abc123ef)."))
            player.sendSystemMessage(Component.literal(
                "§7Draft names with spaces need quotes: /ranked draft create \"Rain Team\" <link>"))
            return
        }
        player.sendSystemMessage(Component.literal("§7[Ranked] Fetching your team from pokepast.es…"))
        PokePasteFetcher.fetch(url).whenComplete { text, err ->
            // Hop back to the server thread before touching player/draft state.
            player.server.execute {
                if (err != null || text == null) {
                    val why = (err?.cause ?: err)?.message ?: "unknown error"
                    player.sendSystemMessage(Component.literal("§c[Ranked] Couldn't fetch that paste: §f$why"))
                } else {
                    saveFromText(player, name, isEdit, text)
                }
            }
        }
    }

    /** Fast shared checks for both input paths. The URL path runs them before the fetch for a
     *  quick answer and again inside [saveFromText] in case state changed while fetching. */
    private fun prereqsOk(player: ServerPlayer, name: String, isEdit: Boolean): Boolean {
        val config = CobblemonRanked.config
        if (DraftTeams.slugify(name).isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Ranked] \"$name\" isn't a usable draft name."))
            return false
        }
        val existing = DraftTeams.byName(player.uuid, name)
        if (!isEdit && existing != null) {
            player.sendSystemMessage(Component.literal(
                "§c[Ranked] You already have a draft named §f${existing.name}§c — use /ranked draft edit $name."))
            return false
        }
        if (isEdit && existing == null) {
            player.sendSystemMessage(Component.literal(
                "§c[Ranked] No draft named §f$name§c to edit — use /ranked draft create $name."))
            return false
        }
        if (!isEdit) {
            val owned = DraftTeams.ownedSlots(player.uuid)
            if (owned == 0) {
                player.sendSystemMessage(Component.literal(
                    "§c[Ranked] You don't own a draft slot yet — buy one at the Shopkeeper's §fUpgrades§c tab (first slot §e\$${DraftTeams.slotCost(0)}§c)."))
                return false
            }
            if (DraftTeams.availableEmptySlots(player.uuid) == 0) {
                val used = DraftTeams.list(player.uuid).size
                if (used < owned) {
                    // Empty slot(s) exist but are cooldown-locked from a recent delete.
                    val unlock = DraftTeams.nextSlotUnlockAt(player.uuid)
                    player.sendSystemMessage(Component.literal(
                        "§c[Ranked] Your empty slot is cooling down after its last team change — " +
                            "free in §f${unlock?.let(::remaining) ?: "a while"}§c."))
                } else {
                    val hint = if (owned < config.maxDraftSlots)
                        "buy another at the Shopkeeper's §fUpgrades§c tab (§e\$${DraftTeams.slotCost(owned)}§c) or delete a draft"
                    else "delete a draft to make room"
                    player.sendSystemMessage(Component.literal(
                        "§c[Ranked] All $owned of your slots are full — $hint."))
                }
                return false
            }
        }
        return true
    }

    private fun saveFromText(player: ServerPlayer, name: String, isEdit: Boolean, text: String) {
        if (!prereqsOk(player, name, isEdit)) return
        val config = CobblemonRanked.config
        val existing = DraftTeams.byName(player.uuid, name)

        val members = try {
            ShowdownPasteParser.parse(text).also { DraftTeams.validate(it) }
        } catch (e: Exception) {
            player.sendSystemMessage(Component.literal("§c[Ranked] Draft rejected: §f${e.message}"))
            return
        }

        // Classify the edit: keeping ≥4 of 6 species is a tune (unrestricted); fewer is a team
        // SWAP — cooldown-gated per slot and priced separately, so one slot can't be re-teamed
        // daily instead of buying more slots. Slot purchases live at the market's Upgrades vendor.
        val isSwap = isEdit && existing != null &&
            DraftTeams.sharedSpecies(existing.members, members) < DraftTeams.TUNE_MIN_SHARED_SPECIES
        if (isSwap) {
            val at = DraftTeams.swapAvailableAt(existing!!)
            if (at.isAfter(Instant.now())) {
                player.sendSystemMessage(Component.literal(
                    "§c[Ranked] That's a new team (fewer than ${DraftTeams.TUNE_MIN_SHARED_SPECIES} species kept) — " +
                        "this slot can swap teams in §f${remaining(at)}§c."))
                player.sendSystemMessage(Component.literal(
                    "§7Tune edits that keep ${DraftTeams.TUNE_MIN_SHARED_SPECIES}+ of the current species are always allowed."))
                return
            }
        }
        // Grace fees: each purchased slot includes its first team, and each team's first edit is
        // free whatever its size (a free swap still had to clear the cooldown above).
        val usesFreeFill = !isEdit && DraftTeams.freeFills(player.uuid) > 0
        val usesFreeEdit = isEdit && existing?.freeEditUsed == false
        val cost = when {
            !isEdit -> if (usesFreeFill) 0 else config.draftRefillCost
            usesFreeEdit -> 0
            isSwap -> config.draftSwapCost
            else -> config.draftEditCost
        }

        // Money moves only after the team is proven legal.
        if (!EconomyBridge.withdraw(player.uuid, cost)) {
            val what = if (isSwap) "team swap" else if (isEdit) "edit" else "team"
            player.sendSystemMessage(Component.literal(
                "§c[Ranked] This $what costs §e\$$cost§c — you have §e\$${EconomyBridge.getBalance(player.uuid)}§c."))
            return
        }
        val draft = DraftTeams.save(
            player.uuid, name, members,
            consumedFreeEdit = usesFreeEdit, identityChange = !isEdit || isSwap,
            consumedFreeFill = usesFreeFill,
        )
        val verb = if (isSwap) "swapped to a new team" else if (isEdit) "updated" else "created"
        val price = when {
            usesFreeFill -> "§ffree§a (included with your slot)"
            usesFreeEdit -> "your free edit"
            else -> "§e\$$cost§a"
        }
        player.sendSystemMessage(Component.literal(
            "§a[Ranked] Draft §f${draft.name}§a $verb for $price — " +
                "find it under §fMy Drafts§a in the rental picker."))
        if (usesFreeEdit) player.sendSystemMessage(Component.literal(
            "§7That was this team's one free edit — from now on tunes cost \$${config.draftEditCost}, team swaps \$${config.draftSwapCost}."))
        if (!isEdit || isSwap) player.sendSystemMessage(Component.literal(
            "§7This slot's next team swap unlocks in ${config.draftIdentityCooldownHours / 24} days (tunes keeping " +
                "${DraftTeams.TUNE_MIN_SHARED_SPECIES}+ species stay available)."))
        player.sendSystemMessage(Component.literal(
            "§7Rental de-tune applies in battle: EVs capped at ${DraftTeams.RENTAL_EV_CAP}, all IVs ${DraftTeams.RENTAL_IVS}."))
    }

    private fun handleDelete(player: ServerPlayer, name: String) {
        val victim = DraftTeams.byName(player.uuid, name)
        if (victim == null) {
            player.sendSystemMessage(Component.literal("§c[Ranked] No draft named §f$name§c. §7Try /ranked draft list"))
            return
        }
        val lockUntil = DraftTeams.swapAvailableAt(victim)
        DraftTeams.delete(player.uuid, name)
        val whenFree = if (lockUntil.isAfter(Instant.now()))
            "it can take a new team in §f${remaining(lockUntil)}§a (its team-change cooldown keeps running)"
        else "put a new team in it for §e\$${CobblemonRanked.config.draftRefillCost}§a"
        player.sendSystemMessage(Component.literal(
            "§a[Ranked] Draft §f$name§a deleted. The slot stays yours — $whenFree."))
    }

    private fun handleList(player: ServerPlayer) {
        val config = CobblemonRanked.config
        val drafts = DraftTeams.list(player.uuid)
        val owned = DraftTeams.ownedSlots(player.uuid)
        player.sendSystemMessage(Component.literal(
            "§e[Ranked] §fYour drafts (§f${drafts.size}§7 in §f$owned§7 owned slots, max ${config.maxDraftSlots}§f):"))
        if (drafts.isEmpty() && owned == 0) {
            player.sendSystemMessage(Component.literal("§7  None yet — /ranked draft for how to make one."))
            return
        }
        drafts.forEach { d ->
            val roster = d.members.joinToString("§7, §f") { prettySpecies(it) }
            val edit = if (d.freeEditUsed) "tunes \$${config.draftEditCost}" else "free tune available"
            val swapAt = DraftTeams.swapAvailableAt(d)
            val swap = if (swapAt.isAfter(Instant.now())) "swap in ${remaining(swapAt)}" else "swap ready"
            player.sendSystemMessage(Component.literal("§7  • §f${d.name}§7 ($edit, $swap): §f$roster"))
        }
        if (drafts.size < owned) {
            val open = DraftTeams.availableEmptySlots(player.uuid)
            val locked = owned - drafts.size - open
            val freeFills = minOf(DraftTeams.freeFills(player.uuid), open)
            if (open > 0) player.sendSystemMessage(Component.literal(
                if (freeFills > 0)
                    "§7  $open empty slot${if (open == 1) "" else "s"} — $freeFills free fill${if (freeFills == 1) "" else "s"} included, then §e\$${config.draftRefillCost}§7 each."
                else
                    "§7  $open empty slot${if (open == 1) "" else "s"} — fill for §e\$${config.draftRefillCost}§7 each."))
            if (locked > 0) player.sendSystemMessage(Component.literal(
                "§7  $locked slot${if (locked == 1) "" else "s"} cooling down — next free in §f${
                    DraftTeams.nextSlotUnlockAt(player.uuid)?.let(::remaining) ?: "?"}§7."))
        }
        if (owned < config.maxDraftSlots) {
            player.sendSystemMessage(Component.literal(
                "§7  Next slot: §e\$${DraftTeams.slotCost(owned)}§7 at the Shopkeeper's Upgrades tab (one-time, permanent)."))
        }
    }

    private fun handleExport(player: ServerPlayer, name: String) {
        val draft = DraftTeams.byName(player.uuid, name)
        if (draft == null) {
            player.sendSystemMessage(Component.literal("§c[Ranked] No draft named §f$name§c. §7Try /ranked draft list"))
            return
        }
        player.sendSystemMessage(Component.literal(
            "§e[Ranked] §fBuild sheet for §e${draft.name}§f (your raw spread — train the real one to this):"))
        draft.members.forEach { m ->
            player.sendSystemMessage(Component.literal(
                "§7  • §f${prettySpecies(m)} §7@ §f${prettyItem(m.item)} §8| §7${m.nature}/${m.ability}"))
            player.sendSystemMessage(Component.literal(
                "§8      ${evLine(m.evs)} §8| ${m.moves.joinToString(", ")}"))
        }
        val paste = draft.members.joinToString("\n\n") { showdownExport(it) }
        player.sendSystemMessage(Component.literal("§a[Click here to copy the Showdown paste]").withStyle(
            Style.EMPTY
                .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, paste))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Copies the full team, importable in the Showdown teambuilder")))
        ))
    }

    // ---- text helpers -----------------------------------------------------------------------

    /** "6d 4h", "3h 12m", "under a minute" — time from now until [at]. */
    private fun remaining(at: Instant): String {
        val d = Duration.between(Instant.now(), at)
        if (d.isNegative || d.isZero) return "now"
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = d.toMinutes() % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "under a minute"
        }
    }

    /** Pages of a held book & quill (or signed book), main hand first, joined with newlines. */
    private fun heldBookText(player: ServerPlayer): String? {
        for (hand in listOf(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND)) {
            val stack = player.getItemInHand(hand)
            stack.get(DataComponents.WRITABLE_BOOK_CONTENT)?.let { content ->
                return content.pages().map { it.raw() }.toList().joinToString("\n")
            }
            stack.get(DataComponents.WRITTEN_BOOK_CONTENT)?.let { content ->
                return content.pages().map { it.raw().string }.toList().joinToString("\n")
            }
        }
        return null
    }

    private fun prettySpecies(m: RentalTeams.RentalMon): String {
        val base = m.species.replaceFirstChar { it.uppercase() }
        return if (m.form != null) "$base-${m.form.replaceFirstChar { it.uppercase() }}" else base
    }

    private fun prettyItem(id: String): String =
        id.substringAfter(":").split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    private fun evLine(evs: RentalTeams.RentalEVs): String =
        listOf(evs.hp to "HP", evs.atk to "Atk", evs.def to "Def", evs.spa to "SpA", evs.spd to "SpD", evs.spe to "Spe")
            .filter { it.first > 0 }
            .joinToString(" / ") { "${it.first} ${it.second}" }
            .ifEmpty { "no EVs" }

    /** Showdown's importer normalises names, so ids ("roughskin") import cleanly. */
    private fun showdownExport(m: RentalTeams.RentalMon): String = buildString {
        append(prettySpecies(m))
        m.gender?.let { append(" ($it)") }
        append(" @ ").append(prettyItem(m.item)).append('\n')
        append("Ability: ").append(m.ability).append('\n')
        val evs = evLine(m.evs)
        if (evs != "no EVs") append("EVs: ").append(evs).append('\n')
        append(m.nature.replaceFirstChar { it.uppercase() }).append(" Nature\n")
        m.moves.forEach { append("- ").append(it).append('\n') }
    }.trimEnd()
}
