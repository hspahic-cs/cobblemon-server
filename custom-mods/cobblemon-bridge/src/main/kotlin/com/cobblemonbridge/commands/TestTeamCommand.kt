package com.cobblemonbridge.commands

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.util.party
import com.cobblemonbridge.CobblemonBridge
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import kotlin.random.Random

/**
 * Dev tool — `/testteam <player> <tier> <level> [index]` (op level 2+).
 *
 * Stashes the target's current party into their PC, then gives a preset competitive team (from
 * [TestTeams]) at the requested level. `tier` ∈ uber | ou | normal; a team is picked at random
 * unless `index` is given.
 */
object TestTeamCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("testteam").requires { it.hasPermission(2) }
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("tier", StringArgumentType.word())
                        .suggests { _, b -> TestTeams.TIERS.forEach { b.suggest(it) }; b.buildFuture() }
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                            .executes { ctx -> give(ctx, null) }
                            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes { ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "index")) }))))
        )
    }

    private fun give(ctx: CommandContext<CommandSourceStack>, index: Int?): Int {
        val target = EntityArgument.getPlayer(ctx, "player")
        val tier = StringArgumentType.getString(ctx, "tier").lowercase()
        val level = IntegerArgumentType.getInteger(ctx, "level")

        val teams = TestTeams.teams(tier)
        if (teams.isEmpty()) {
            ctx.source.sendFailure(Component.literal("No '$tier' teams configured (tiers: ${TestTeams.TIERS.joinToString()})."))
            return 0
        }
        val pick = when {
            index == null -> Random.nextInt(teams.size)
            index in teams.indices -> index
            else -> {
                ctx.source.sendFailure(Component.literal("Index $index out of range for '$tier' (0..${teams.size - 1})."))
                return 0
            }
        }
        val team = teams[pick]

        stashPartyToPc(target)

        val party = target.party()
        var built = 0
        for (mon in team.mons ?: emptyList()) {
            val species = mon.species ?: continue
            val props = PokemonProperties().apply {
                this.species = species
                this.level = level
                mon.form?.let { this.form = it }
                mon.aspects?.takeIf { it.isNotEmpty() }?.let { this.aspects = it }
                mon.nature?.let { this.nature = it }
                mon.ability?.let { this.ability = it }
                mon.item?.let { this.heldItem = it }
                this.shiny = mon.shiny
                mon.moves?.takeIf { it.isNotEmpty() }?.let { this.moves = it }
            }
            val pokemon = props.create()
            Stats.PERMANENT.forEach { pokemon.setIV(it, team.ivs.coerceIn(0, 31)) }
            (mon.evs ?: emptyMap()).forEach { (k, v) -> statOf(k)?.let { pokemon.evs[it] = v.coerceIn(0, 252) } }
            party.add(pokemon)
            built++
        }

        ctx.source.sendSuccess(
            { Component.literal("Gave ${target.gameProfile.name} the '${team.name ?: tier}' $tier team ($built mons) at L$level. Their old party is in the PC.") },
            true,
        )
        target.sendSystemMessage(Component.literal("§a[TestTeam] §fYou received a §e$tier§f test team (§e${team.name ?: "?"}§f) at L$level. Your previous party is in your PC."))
        CobblemonBridge.logger.info("testteam: gave {} '{}' ({}) L{} ({} mons)", target.gameProfile.name, team.name, tier, level, built)
        return 1
    }

    /** Move every party Pokémon into the PC. Remove-then-add, restoring to the party if the PC is
     *  full, so a Pokémon can never be orphaned. */
    private fun stashPartyToPc(player: ServerPlayer) {
        val party = player.party()
        val pc = Cobblemon.storage.getPC(player)
        for (mon in party.toList()) {
            party.remove(mon)
            if (!pc.add(mon)) party.add(mon)
        }
    }

    private fun statOf(s: String): Stat? = when (s.lowercase()) {
        "hp" -> Stats.HP
        "atk", "attack" -> Stats.ATTACK
        "def", "defence", "defense" -> Stats.DEFENCE
        "spa", "specialattack" -> Stats.SPECIAL_ATTACK
        "spd", "specialdefence", "specialdefense" -> Stats.SPECIAL_DEFENCE
        "spe", "speed" -> Stats.SPEED
        else -> null
    }
}
