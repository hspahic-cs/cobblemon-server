package com.cobblemonroguelite.run

import com.cobblemonroguelite.shop.RunCurrency
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The run's corner display: a boss bar reading `Wave 12 — ₽430`, up for as long as the player has a
 * run and is online. PokéRogue keeps its wave and money on screen permanently, and that is the whole
 * feature — a player deciding whether to reroll a shop or push one more wave is doing arithmetic on
 * exactly these two numbers, and making them a `/roguelite status` away means the arithmetic is done
 * from memory instead.
 *
 * ### Why a boss bar
 *
 * It is the one piece of always-visible HUD real estate the vanilla protocol hands a server: no
 * client mod, no resource pack — the same constraint every string in this mod is written under (see
 * [RunCurrency] for the ₽ glyph clearing that bar). The action bar was the alternative and fails the
 * "persistent" requirement: it fades, so keeping it up means rebroadcasting every couple of seconds
 * forever, which is exactly the per-tick chatter this object exists to avoid. BLUE, because the
 * vanilla palette has no neutral and blue is the least combative of it — pink and red read as a boss
 * health bar, which is the one thing this must not be mistaken for (§2.32's boss shields will want
 * that reading for themselves).
 *
 * ### One idempotent entry point, not create/update/destroy
 *
 * Everything goes through [sync]: it reads the store and makes the bar agree — shown and current if
 * a run exists, gone if not. Callers therefore never need to know which transition they are sitting
 * on, and a hook that fires on a path where the run just ended (a resume that walks into an
 * operator-shrunk `runLength`, say) self-corrects instead of painting a bar for a dead run. The only
 * other verb is [remove], for the two sites where the player is leaving rather than the run changing.
 *
 * ### Update discipline
 *
 * Event-driven only — synced from the sites that *write* `run.wave` or `run.credits`, not from a
 * tick. Those write sites were the alternative to a once-per-second sweep, and they won because
 * there are only a handful and every one already ends in a `checkpoint(...)` call: the store's own
 * design has already funnelled mutation into few places, so the HUD rides the funnel rather than
 * polling around it. [ServerBossEvent] helps: its setters compare before broadcasting, so a sync
 * that changes nothing sends nothing.
 */
object RunHud {

    /**
     * Live bars by player. A map here rather than a field on [RunState] because the bar is session
     * state, not run state: it holds [ServerPlayer] references and must die with the connection
     * ([remove] on logout is what keeps a relogging player from accumulating ghost bars), while the
     * run it describes persists across both logouts and reboots. Concurrent as cheap insurance —
     * every current caller is on the server thread, but nothing here is worth being the reason that
     * invariant can never change.
     */
    private val bars = ConcurrentHashMap<UUID, ServerBossEvent>()

    /** The uuid overload, for callers deep in the controller that only have one. Offline = no bar. */
    fun sync(server: MinecraftServer, player: UUID) {
        val online = server.playerList.getPlayer(player)
        if (online == null) {
            remove(player)
            return
        }
        sync(online)
    }

    /** Make the bar agree with the store: shown and current if the player has a run, gone if not. */
    fun sync(player: ServerPlayer) {
        val run = RunStore.of(player.server).get(player.uuid)
        if (run == null) {
            remove(player.uuid)
            return
        }
        val bar = bars.computeIfAbsent(player.uuid) {
            ServerBossEvent(text(run), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS)
        }
        // Both setters no-op (and send nothing) when the value is unchanged, so an over-eager sync
        // costs a map lookup and two comparisons — which is why every hook site can afford to call
        // this unconditionally instead of working out whether its write actually changed anything.
        bar.name = text(run)
        bar.progress = progress(run)
        // addPlayer is set-backed and equally idempotent; calling it every sync is what makes
        // login-with-a-run and mid-run updates the same code path.
        bar.addPlayer(player)
    }

    /**
     * Drop the bar. [ServerBossEvent.removeAllPlayers] sends the removal packet to whoever can still
     * receive it and is safe on a player mid-disconnect, which is why logout can call this without
     * caring how the session ended.
     */
    fun remove(player: UUID) {
        bars.remove(player)?.removeAllPlayers()
    }

    private fun text(run: RunState): Component =
        Component.literal("Wave ${run.wave} — ${RunCurrency.format(run.credits)}")

    /**
     * How deep into the run they are, against the configured [runLength]. Read live from
     * [RunSettings] rather than pinned on the run — unlike the roster and payout table this is
     * display, so tracking an operator's config change instantly is the correct behaviour, not drift.
     * Coerced because a shrunk `runLength` can leave a live run past the end; the run itself is
     * handled by [RunProgress] (it ends on the next step), and the bar just holds full.
     */
    private fun progress(run: RunState): Float =
        (run.wave.toFloat() / RunSettings.composition.config.runLength).coerceIn(0f, 1f)
}
