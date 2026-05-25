package com.cobblemonserver.npc.command

import com.cobblemonserver.npc.data.NpcTeamStore
import com.cobblemonserver.npc.data.PlayerRecord
import com.cobblemonserver.npc.economy.EconomyBridge
import com.cobblemonserver.npc.economy.RewardsConfig
import com.cobblemonserver.npc.gym.GymLeaderManager
import com.minecolonies.core.entity.citizen.EntityCitizen
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.UuidArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object GymCommands {

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(build())
    }

    private fun build(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("cobblemon-npc")
            .then(
                Commands.literal("gym")
                    .then(
                        Commands.literal("choose-theme")
                            .then(
                                Commands.argument("citizen", UuidArgument.uuid())
                                    .then(
                                        Commands.argument("themeId", StringArgumentType.word())
                                            .executes(::runChooseTheme)
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("rewards")
                    .requires { it.hasPermission(2) }
                    .then(Commands.literal("show").executes(::runRewardsShow))
                    .then(Commands.literal("reload").executes(::runRewardsReload))
                    .then(
                        Commands.literal("set")
                            .then(
                                Commands.literal("enabled")
                                    .then(
                                        Commands.argument("value", BoolArgumentType.bool())
                                            .executes { ctx ->
                                                RewardsConfig.enabled = BoolArgumentType.getBool(ctx, "value")
                                                saveAndAck(ctx, "enabled", RewardsConfig.enabled.toString())
                                            }
                                    )
                            )
                            .then(
                                Commands.literal("gymLeaderMultiplier")
                                    .then(
                                        Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                            .executes { ctx ->
                                                RewardsConfig.gymLeaderMultiplier = DoubleArgumentType.getDouble(ctx, "value")
                                                saveAndAck(ctx, "gymLeaderMultiplier", RewardsConfig.gymLeaderMultiplier.toString())
                                            }
                                    )
                            )
                            .then(tierSet("tier1Reward") { RewardsConfig.tier1Reward = it })
                            .then(tierSet("tier2Reward") { RewardsConfig.tier2Reward = it })
                            .then(tierSet("tier3Reward") { RewardsConfig.tier3Reward = it })
                            .then(tierSet("tier4Reward") { RewardsConfig.tier4Reward = it })
                            .then(tierSet("tier5Reward") { RewardsConfig.tier5Reward = it })
                            .then(tierSet("tier6Reward") { RewardsConfig.tier6Reward = it })
                    )
            )
            .then(
                Commands.literal("record")
                    .executes(::runRecordNearest)
                    .then(
                        Commands.argument("citizen", UuidArgument.uuid())
                            .requires { it.hasPermission(2) }
                            .executes(::runRecordByUuid)
                    )
            )
    }

    private fun tierSet(key: String, setter: (Int) -> Unit): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(key).then(
            Commands.argument("value", IntegerArgumentType.integer(0))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "value")
                    setter(v)
                    saveAndAck(ctx, key, v.toString())
                }
        )
    }

    private fun saveAndAck(ctx: CommandContext<CommandSourceStack>, key: String, value: String): Int {
        RewardsConfig.save()
        ctx.source.sendSuccess(
            { Component.literal("rewards.$key = $value").withStyle(ChatFormatting.GREEN) },
            true
        )
        return Command.SINGLE_SUCCESS
    }

    private fun runRewardsShow(ctx: CommandContext<CommandSourceStack>): Int {
        val econ = if (EconomyBridge.isAvailable()) "available" else "MISSING"
        ctx.source.sendSuccess(
            {
                Component.literal(
                    "cobblemon-npc rewards:\n" +
                        "  economy bridge: $econ\n" +
                        "  enabled: ${RewardsConfig.enabled}\n" +
                        "  gymLeaderMultiplier: ${RewardsConfig.gymLeaderMultiplier}\n" +
                        "  tier1Reward: ${RewardsConfig.tier1Reward}\n" +
                        "  tier2Reward: ${RewardsConfig.tier2Reward}\n" +
                        "  tier3Reward: ${RewardsConfig.tier3Reward}\n" +
                        "  tier4Reward: ${RewardsConfig.tier4Reward}\n" +
                        "  tier5Reward: ${RewardsConfig.tier5Reward}\n" +
                        "  tier6Reward: ${RewardsConfig.tier6Reward}"
                ).withStyle(ChatFormatting.AQUA)
            },
            false
        )
        return Command.SINGLE_SUCCESS
    }

    private fun runRewardsReload(ctx: CommandContext<CommandSourceStack>): Int {
        RewardsConfig.load()
        ctx.source.sendSuccess(
            { Component.literal("rewards.json reloaded.").withStyle(ChatFormatting.GREEN) },
            true
        )
        return Command.SINGLE_SUCCESS
    }

    @Throws(CommandSyntaxException::class)
    private fun runChooseTheme(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val citizenUuid: UUID = UuidArgument.getUuid(ctx, "citizen")
        val themeId = StringArgumentType.getString(ctx, "themeId")

        val level = player.level() as? ServerLevel ?: return 0
        val entity = level.getEntity(citizenUuid) as? EntityCitizen ?: run {
            ctx.source.sendFailure(Component.literal("Target citizen no longer exists."))
            return 0
        }

        val ok = GymLeaderManager.chooseTheme(player, entity, themeId)
        if (!ok) {
            ctx.source.sendFailure(
                Component.literal("Couldn't lock in that theme — it may already be taken or the picker has expired.")
            )
            return 0
        }
        return Command.SINGLE_SUCCESS
    }

    @Throws(CommandSyntaxException::class)
    private fun runRecordNearest(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val level = player.level() as? ServerLevel ?: return 0

        val nearest = level.getEntitiesOfClass(
            EntityCitizen::class.java,
            player.boundingBox.inflate(5.0)
        ).minByOrNull { it.distanceToSqr(player) }

        if (nearest == null) {
            ctx.source.sendFailure(Component.literal("No citizen within 5 blocks."))
            return 0
        }

        sendRecord(ctx, nearest, player.uuid)
        return Command.SINGLE_SUCCESS
    }

    @Throws(CommandSyntaxException::class)
    private fun runRecordByUuid(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val citizenUuid: UUID = UuidArgument.getUuid(ctx, "citizen")

        val level = player.level() as? ServerLevel ?: return 0
        val entity = level.getEntity(citizenUuid) as? EntityCitizen ?: run {
            ctx.source.sendFailure(Component.literal("Target citizen not found in this dimension."))
            return 0
        }

        sendRecord(ctx, entity, player.uuid)
        return Command.SINGLE_SUCCESS
    }

    private fun sendRecord(
        ctx: CommandContext<CommandSourceStack>,
        citizen: EntityCitizen,
        playerUuid: UUID
    ) {
        val data = NpcTeamStore.get(citizen)
        val record = data?.playerRecords?.get(playerUuid) ?: PlayerRecord()
        val citizenName = citizen.name.string

        val firstDefeated = record.firstDefeatedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        } ?: "never"

        ctx.source.sendSuccess(
            {
                Component.literal(
                    "$citizenName — your record:\n" +
                        "  Wins: ${record.wins}\n" +
                        "  Losses: ${record.losses}\n" +
                        "  First defeated: $firstDefeated"
                ).withStyle(ChatFormatting.AQUA)
            },
            false
        )
    }

}
