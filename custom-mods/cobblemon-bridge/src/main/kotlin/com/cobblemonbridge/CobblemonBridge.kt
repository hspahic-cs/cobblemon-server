package com.cobblemonbridge

import com.cobblemonbridge.adapters.CobbleloootsAdapter
import com.cobblemonbridge.adapters.LegendaryMonumentsTerraBlenderShim
import com.cobblemonbridge.battle.AdjustLevelHook
import com.cobblemonbridge.battle.BattleThemeHook
import com.cobblemonbridge.battle.E4GauntletHook
import com.cobblemonbridge.battle.GivePartyExpHook
import com.cobblemonbridge.battle.GymBattleAdjustHook
import com.cobblemonbridge.battle.GymDefeatHook
import com.cobblemonbridge.battle.GymPrereqHook
import com.cobblemonbridge.commands.CommandAliases
import com.cobblemonbridge.commands.GymTpCommands
import com.cobblemonbridge.commands.HologramCommands
import com.cobblemonbridge.commands.MonumentCommand
import com.cobblemonbridge.commands.ProfileCommand
import com.cobblemonbridge.commands.SpawnCommands
import com.cobblemonbridge.commands.TowerCommands
import com.cobblemonbridge.commands.GymReturnCommands
import com.cobblemonbridge.commands.HomeAliases
import com.cobblemonbridge.commands.QuestCommand
import com.cobblemonbridge.commands.TradeCommand
import com.cobblemonbridge.commands.WildCommand
import com.cobblemonbridge.eggs.BredTagHook
import com.cobblemonbridge.eggs.EggDefeatHook
import com.cobblemonbridge.gymtp.GymTpNpcHook
import com.cobblemonbridge.gymtp.GymTpRegistry
import com.cobblemonbridge.npc.EntityAnchor
import com.cobblemonbridge.trade.TradeLifecycle
import com.cobblemonbridge.quests.HealQuestHook
import com.cobblemonbridge.quests.PartyLevelHook
import com.cobblemonbridge.quests.PokedexProgressHook
import com.cobblemonbridge.quests.SetHomeHook
import com.cobblemonbridge.wild.LegendaryMonumentLock
import com.cobblemonbridge.wild.TradeCapHook
import com.cobblemonbridge.wild.WildBattleAdjustHook
import com.cobblemonbridge.wild.WildBattleRewardHook
import com.cobblemonbridge.wild.WildSpawnLevelCapHook
import com.cobblemonbridge.worldrules.WorldRulesHook
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Tag-driven hooks that bridge Cobblemon's systems. Each hook lives in its own object and is
 * activated by a `cobblemon_bridge:<hook>/<arg>` tag on an entity, a Cobblemon event, or a
 * NeoForge event. Adding a new hook means: add the file, register it here.
 */
@Mod(value = CobblemonBridge.MOD_ID, dist = [Dist.DEDICATED_SERVER])
class CobblemonBridge(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Bridge initializing...")

        com.cobblemonbridge.quests.GymCaps.init()  // authored gym/E4 level caps (config-driven, not number-derived)
        com.cobblemonbridge.commands.TestTeams.init()  // dev /testteam preset competitive teams
        AdjustLevelHook.registerEvents()
        NeoForge.EVENT_BUS.register(AdjustLevelHook)
        NeoForge.EVENT_BUS.register(GivePartyExpHook)
        GymDefeatHook.registerEvents()
        // 0.7.26: NeoForge.EVENT_BUS.register(GymDefeatHook) removed — the simplified
        // hook (gyms migrated to rctmod:defeat_count trigger) no longer has any
        // @SubscribeEvent methods. NeoForge throws on register() when the target has
        // none, so registering would crash mod loading. The hook still subscribes to
        // Cobblemon's BATTLE_VICTORY via the registerEvents() call above.
        NeoForge.EVENT_BUS.register(GymPrereqHook)
        GymBattleAdjustHook.registerEvents()
        NeoForge.EVENT_BUS.register(GymBattleAdjustHook)
        E4GauntletHook.registerEvents()
        NeoForge.EVENT_BUS.register(E4GauntletHook)
        // Custom battle music: per-member Elite Four, a shared pool for regular gyms +
        // the Battle Tower, and a pool for PvP arenas. Subscribes BATTLE_STARTED_PRE only;
        // the theme is stamped per battle by GymBattleGate (the universal trainer-battle gate).
        BattleThemeHook.registerEvents()
        com.cobblemonbridge.battle.TowerGauntletHook.registerEvents()
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.battle.TowerGauntletHook)
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.battle.TowerEntryHook)
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.tower.TowerManager)
        // GymReturnHook subscribes BATTLE_VICTORY at HIGH — must see the tower's activeFloor
        // before TowerGauntletHook's NORMAL handler consumes it (tower fights don't gym-teleport).
        com.cobblemonbridge.battle.GymReturnHook.registerEvents()
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.util.DelayedTeleports)
        NeoForge.EVENT_BUS.register(SetHomeHook)
        NeoForge.EVENT_BUS.register(HealQuestHook)
        PartyLevelHook.registerEvents()
        PokedexProgressHook.registerEvents()
        com.cobblemonbridge.voice.TrainerVoiceHook.registerEvents()  // per-player trainer voice lines
        NeoForge.EVENT_BUS.register(PokedexProgressHook)  // PlayerEvent.Clone — carry PokéNav flag across respawn
        WildBattleRewardHook.registerEvents()
        WildSpawnLevelCapHook.registerEvents()
        com.cobblemonbridge.battle.TrainerExpBoostHook.registerEvents()
        com.cobblemonbridge.battle.PveLossExpHook.registerEvents()
        com.cobblemonbridge.quests.EvolutionHook.registerEvents()
        // AdvancementHook (0.7.29) handles gym + Elite Four bounty payment via
        // NeoForge AdvancementEarnEvent — replaces the broken /eco give in mcfunctions.
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.quests.AdvancementHook)
        // WildBattleAdjustHook intentionally NOT registered: wild battles don't downlevel the
        // player's team. Gym battles DO downlevel — via GymBattleAdjustHook (above), not via
        // RCT's adjustPlayerLevels (which turned out to be dead config — its BattleRules field
        // is parsed from JSON but never consumed).
        TradeCapHook.registerEvents()
        BredTagHook.registerEvents()
        // Breeding restriction: parents + children of a breeding can't be traded. Child side is
        // BredTagHook (HATCH_EGG_POST, which Cobreeding fires). Parent side is the tick monitor
        // below — Cobblemon's COLLECT_EGG never fires for Cobreeding, so we watch its egg registry.
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.breeding.BreedingParentTagHook)
        NeoForge.EVENT_BUS.register(LegendaryMonumentLock)
        // Strip the LM Entrepreneur's Light/Dark Stone Shard (Reshiram/Zekrom) trades — they're
        // code-registered by the mod, so a datapack can't touch them.
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.villager.EntrepreneurTradeFilter)
        // EggDefeatHook is timer-based now; only the server-tick subscriber is needed.
        NeoForge.EVENT_BUS.register(EggDefeatHook)

        // Cobbleworkers leaks global harvest claims over long uptime (no TTL on ClaimService),
        // which silently stops apricorn/berry workers. Reaper periodically releases them so a
        // prod restart isn't needed. Fail-open: no-ops if Cobbleworkers is absent/changed.
        NeoForge.EVENT_BUS.register(com.cobblemonbridge.cobbleworkers.CobbleworkersClaimReaperHook)

        NeoForge.EVENT_BUS.register(GymTpNpcHook)
        NeoForge.EVENT_BUS.register(EntityAnchor)
        NeoForge.EVENT_BUS.register(TradeLifecycle)
        NeoForge.EVENT_BUS.register(WorldRulesHook)
        NeoForge.EVENT_BUS.register(LegendaryMonumentsTerraBlenderShim)

        val cobbleloots = CobbleloootsAdapter.isPresent()
        if (cobbleloots) {
            NeoForge.EVENT_BUS.register(CobbleloootsAdapter)
        }

        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarting)

        logger.info(
            "Cobblemon Bridge initialized — adjust_level + give_party_exp + party_level + " +
                "set_home + home aliases active (cobbleloots adapter: {})",
            if (cobbleloots) "on" else "off",
        )
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        QuestCommand.register(event.dispatcher)
        HomeAliases.register(event.dispatcher)
        CommandAliases.register(event.dispatcher)
        WildCommand.register(event.dispatcher)
        com.cobblemonbridge.commands.TestTeamCommand.register(event.dispatcher)  // dev: give a preset competitive team
        GymTpCommands.register(event.dispatcher)
        SpawnCommands.register(event.dispatcher)
        TowerCommands.register(event.dispatcher)
        GymReturnCommands.register(event.dispatcher)
        com.cobblemonbridge.commands.E4Command.register(event.dispatcher)  // op-only E4 test bypass
        HologramCommands.register(event.dispatcher)
        ProfileCommand.register(event.dispatcher)
        // /pay — our own money-transfer command. NeoEssentials' /pay is disabled via
        // config/neoessentials/commands.json ("pay": false); this one uses the same economy.
        com.cobblemonbridge.commands.PayCommand.register(event.dispatcher)
        // /trade disabled — the custom Pokémon+money trade GUI was too buggy. Players use the
        // native Cobblemon trade (look at a player, press R → Trade) and /pay for money instead.
        // TradeCommand.register(event.dispatcher)
        MonumentCommand.register(event.dispatcher)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onServerStarting(event: ServerStartingEvent) {
        GymTpRegistry.init()
        SpawnCommands.init()
        com.cobblemonbridge.tower.TowerManager.init()
        com.cobblemonbridge.battle.GymReturnHook.init()
        WildCommand.init()
        HologramCommands.init()
        LegendaryMonumentLock.init()
        com.cobblemonbridge.profile.FavoriteTracker.init()
    }

    companion object {
        const val MOD_ID = "cobblemon_bridge"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
