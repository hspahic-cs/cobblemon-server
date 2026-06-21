package com.cobblemonbridge.eggs

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

/**
 * Tags freshly-hatched Pokémon with `cobblemon_bridge:bred=true` in their persistent NBT,
 * EXCEPT when the source egg came from `/gacha giveegg` (i.e. a crate/quest reward). Used
 * downstream by [com.cobblemonbridge.wild.TradeCapHook] and
 * [com.cobblemonbridge.trade.TradeManager] to refuse trades of bred Pokémon.
 *
 * Detection: Cobreeding's `PokemonEgg.inventoryTick` synchronously calls `hatchEgg(player,
 * props)` which then fires `CobblemonEvents.HATCH_EGG_POST` (verified against 2.2.1 bytecode).
 * [com.cobblemonbridge.mixin.PokemonEggMixin] injects at the `hatchEgg` call site, reads the
 * egg ItemStack's `custom_data.cobblemongacha_tier` (set by cobblemon-gacha's RewardGranter),
 * and on hit calls [markGachaHatch]. The HATCH_EGG_POST subscriber consumes the marker on the
 * same tick and skips the bred tag.
 *
 * Legacy mons hatched before this hook landed are NOT tagged retroactively — they remain
 * tradeable. This is by design; backfilling from heuristics is fragile.
 */
object BredTagHook {

    const val TAG_KEY = "cobblemon_bridge:bred"

    /** Set on a Pokémon when it is used as a breeding parent (see [BreedingTradeLockHook]).
     *  Like [TAG_KEY], it makes the Pokémon non-tradeable — "parents and children can't be traded." */
    const val PARENT_TAG_KEY = "cobblemon_bridge:bred_parent"

    /**
     * Player UUID → server tick on which the mixin observed a gacha-tier marker on the egg
     * being hatched. The HATCH_EGG_POST subscriber removes the entry; entries older than
     * 1 tick are treated as stale and ignored (self-heals if HATCH_EGG_PRE gets cancelled and
     * the marker would otherwise leak to the player's next hatch).
     */
    private val gachaHatchTicks: ConcurrentHashMap<UUID, Long> = ConcurrentHashMap()

    /** Egg custom-data key holding the breeder's UUID string (stamped at lay-time by
     *  [com.cobblemonbridge.breeding.BreedingParentTagHook]). */
    const val EGG_BREEDER_KEY = "cobblemonbridge_breeder"

    /** Incubator UUID → (tick observed, breeder UUID) for a bred egg being hatched. Mirrors the
     *  gacha-marker mechanism: the mixin records it before hatch; the HATCH_EGG_POST subscriber
     *  consumes it on the same tick and routes the hatched mon to the breeder's PC. */
    private val breederHatchTicks: ConcurrentHashMap<UUID, Pair<Long, UUID>> = ConcurrentHashMap()

    @JvmStatic
    fun markGachaHatch(uuid: UUID, tickCount: Int) {
        gachaHatchTicks[uuid] = tickCount.toLong()
    }

    @JvmStatic
    fun markBreederHatch(incubator: UUID, tickCount: Int, breederUuid: String) {
        val breeder = try { UUID.fromString(breederUuid) } catch (e: Exception) { return }
        breederHatchTicks[incubator] = tickCount.toLong() to breeder
    }

    fun registerEvents() {
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.NORMAL) { event ->
            val player = event.player
            val markedTick = gachaHatchTicks.remove(player.uuid)
            val currentTick = player.server.tickCount.toLong()
            val isGachaHatch = markedTick != null && abs(currentTick - markedTick) <= 1L
            if (isGachaHatch) {
                breederHatchTicks.remove(player.uuid)  // gacha eggs aren't breeder-owned
                CobblemonBridge.logger.debug(
                    "Skipped bred tag for gacha-hatched {} (uuid={}, player={})",
                    event.pokemon.species.name, event.pokemon.uuid, player.uuid,
                )
                return@subscribe
            }
            event.pokemon.persistentData.putBoolean(TAG_KEY, true)
            // Shiny breeding is disabled on this server: a bred (non-gacha) Pokémon never hatches
            // shiny, regardless of cobbreeding's Masuda/method shiny rolls. Gacha eggs are exempt
            // (handled above) — those keep their own shiny pool.
            if (event.pokemon.shiny) {
                event.pokemon.shiny = false
                CobblemonBridge.logger.debug("Forced bred {} non-shiny (shiny breeding disabled)", event.pokemon.species.name)
            }
            // Hidden-ability breeding is nerfed: a bred Pokémon that inherited its hidden ability
            // keeps it only ~HA_KEEP_CHANCE of the time; otherwise it's rerolled to a normal ability.
            nerfBredHiddenAbility(event.pokemon)
            CobblemonBridge.logger.debug(
                "Tagged hatched {} (uuid={}) as bred",
                event.pokemon.species.name, event.pokemon.uuid,
            )

            // Egg ownership: a bred egg belongs to the breeder (pasture owner). Route the hatched mon
            // to the breeder's PC and set its OT, regardless of who incubated it — this closes the
            // "hand someone your egg to launder a bred mon" loophole.
            val breederMark = breederHatchTicks.remove(player.uuid)
            if (breederMark != null && abs(currentTick - breederMark.first) <= 1L) {
                routeToBreeder(event.pokemon, breederMark.second, player)
            }
        }
    }

    /** Set OT = breeder and move the hatched [pokemon] into the breeder's PC (offline-safe). PC full
     *  → breeder's party; if even that fails, it stays where Cobreeding placed it. */
    private fun routeToBreeder(pokemon: Pokemon, breeder: UUID, incubator: ServerPlayer) {
        val server = incubator.server
        val registryAccess = server.registryAccess()
        try {
            pokemon.setOriginalTrainer(breeder)
            pokemon.refreshOriginalTrainer()
        } catch (e: Throwable) {
            CobblemonBridge.logger.warn("egg-ownership: failed to set OT to breeder {}: {}", breeder, e.message)
        }
        try {
            // Detach from wherever Cobreeding just placed it (the incubator's party/PC).
            pokemon.storeCoordinates.get()?.store?.remove(pokemon)
            val pc = Cobblemon.storage.getPC(breeder, registryAccess)
            val placed = pc.add(pokemon)
            val dest = if (placed) "PC" else {
                val ok = Cobblemon.storage.getParty(breeder, registryAccess).add(pokemon)
                if (ok) "party (PC full)" else "left in place (PC + party full)"
            }
            val breederName = server.playerList.getPlayer(breeder)?.name?.string ?: breeder.toString().take(8)
            CobblemonBridge.logger.info(
                "egg-ownership: routed bred {} to breeder {}'s {} (incubated by {})",
                pokemon.species.name, breederName, dest, incubator.gameProfile.name,
            )
            // Tell the incubator if they hatched someone else's egg.
            if (breeder != incubator.uuid) {
                incubator.sendSystemMessage(Component.literal(
                    "§7[Breeding] That egg belonged to §f$breederName§7 — the hatched ${pokemon.species.name} went to their PC."))
                server.playerList.getPlayer(breeder)?.sendSystemMessage(Component.literal(
                    "§a[Breeding] Your bred §f${pokemon.species.name}§a hatched and was deposited in your PC."))
            }
        } catch (e: Throwable) {
            CobblemonBridge.logger.warn("egg-ownership: failed to route bred mon to breeder PC: {}", e.message)
        }
    }

    fun isBred(pokemon: Pokemon): Boolean =
        pokemon.persistentData.getBoolean(TAG_KEY)

    /** Mark a Pokémon as a breeding parent (called for both parents when an egg is collected). */
    fun markBreedingParent(pokemon: Pokemon) =
        pokemon.persistentData.putBoolean(PARENT_TAG_KEY, true)

    fun isBreedingParent(pokemon: Pokemon): Boolean =
        pokemon.persistentData.getBoolean(PARENT_TAG_KEY)

    /** True if the Pokémon may not be traded due to breeding — either it was bred (a child) or it
     *  has been used as a breeding parent. The single check used by the trade gates. */
    fun isTradeLocked(pokemon: Pokemon): Boolean =
        isBred(pokemon) || isBreedingParent(pokemon)

    /** Fraction of bred Pokémon that get to KEEP an inherited hidden ability (rest are rerolled). */
    private const val HA_KEEP_CHANCE = 0.5

    /** If [pokemon] hatched with its species' hidden ability, keep it only [HA_KEEP_CHANCE] of the
     *  time — otherwise swap to a randomly-chosen normal ability from the form's pool. No-op if the
     *  current ability isn't hidden or the form has no normal abilities to fall back to. */
    private fun nerfBredHiddenAbility(pokemon: Pokemon) {
        val pool = pokemon.form.abilities
        val currentName = pokemon.ability.template.name
        val isHidden = pool.any { it is HiddenAbility && it.template.name == currentName }
        if (!isHidden || Random.nextDouble() < HA_KEEP_CHANCE) return
        val normal = pool.filter { it !is HiddenAbility }.randomOrNull() ?: return
        pokemon.updateAbility(normal.template.create(false, Priority.NORMAL))
        CobblemonBridge.logger.debug(
            "Rerolled bred {} hidden ability {} -> {} (HA breeding nerf)",
            pokemon.species.name, currentName, normal.template.name,
        )
    }
}
