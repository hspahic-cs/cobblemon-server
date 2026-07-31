package com.cobblemonroguelite.run

import com.cobblemonroguelite.integration.StashSlotProviders
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/stash")

/**
 * The inventory half of run isolation: the E and X protocols from
 * `docs/roguelite-run-isolation.md` §5, implemented exactly in the order the design proves safe.
 *
 * ### The one sentence that governs this file
 *
 * **Durable-write-before-clear**: no ordering exists in which the only copy of the player's items is
 * undurable. Entry writes the stash (E3, fsynced) before anything observable changes (E4), and the
 * tag that says "this inventory is the run's" lands in the same vanilla playerdata write as the
 * clear itself (E5) — one indivisible disk event, so there is no reachable disk state where the
 * inventory is empty and the tag absent, or full and the tag present.
 *
 * ### What this file deliberately does not do
 *
 * No teleports (the controller owns movement), no payout (X6 stays in `endRun`, strictly after
 * [exitSwap]), no party — [RunPartySwap] is its sibling, not its subordinate. And no decisions:
 * [StashReconcile] decides, this executes.
 *
 * ### Thread discipline is enforced, not inherited
 *
 * Every entry point asserts the server thread (design §5: `RunCapture` deliberately hops nowhere,
 * so "we are always on the server thread" has a live counterexample in this very module).
 */
object RunInventoryStash {

    /** §4's flag: `swapId` under `PlayerPersisted`, the one subtag that survives the death clone. */
    const val TAG_KEY = "cobblemon_roguelite:stash_id"

    private const val FORMAT_VERSION = 1

    sealed interface EntryResult {
        data object Ok : EntryResult
        data class Refused(val reason: String) : EntryResult
    }

    sealed interface ExitResult {
        /** [returned] stacks restored; [residue] kept on disk; [quarantined] set aside for an op. */
        data class Ok(val returned: Int, val residue: Int, val quarantined: Int) : ExitResult

        /** X4's save failed: the in-memory restore was rolled back; tag and file are intact. */
        data object RolledBack : ExitResult

        /** Row 3: tag without a readable matching file. Nothing was touched. */
        data object Alarm : ExitResult

        data object NothingToDo : ExitResult
    }

    // ------------------------------------------------------------------ tag

    fun isTagged(player: ServerPlayer): Boolean = persisted(player).contains(TAG_KEY)

    fun tagOf(player: ServerPlayer): UUID? =
        persisted(player).getString(TAG_KEY).takeIf { it.isNotEmpty() }?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

    private fun persisted(player: ServerPlayer): CompoundTag {
        val root = player.persistentData
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, CompoundTag())
        return root.getCompound(Player.PERSISTED_NBT_TAG)
    }

    fun files(server: MinecraftServer): StashFiles =
        StashFiles(server.getWorldPath(LevelResource.ROOT).resolve("data/cobblemon_roguelite/stash"))

    // ------------------------------------------------------------------ entry: E0..E5

    /**
     * The entry swap. On [EntryResult.Refused] nothing observable has changed — refusing is free,
     * a half-done swap is not, and every failure below refuses rather than continuing.
     */
    fun enterSwap(player: ServerPlayer, run: RunState): EntryResult {
        assertServerThread(player)
        val files = files(player.server)

        // E0. Refusals, cheapest first.
        if (player.isCreative || player.isSpectator) {
            return EntryResult.Refused("runs cannot be entered in ${if (player.isCreative) "creative" else "spectator"} mode")
        }
        if (isTagged(player)) {
            // A tag at entry means the last session never reconciled. Entry may proceed only once a
            // reconcile resolves it clean — and the reconcile is the caller's to run, because it may
            // need to eject or restore, which are not this function's verbs.
            return EntryResult.Refused("a previous run's items are still stored — relog, or ask an operator (unreconciled stash)")
        }
        if (files.exists(player.uuid)) {
            // Row 2 inline: a file nobody's tag points at. Archive it before minting a new one so the
            // new stash can never be confused with the stale generation.
            files.archiveStash(player.uuid, System.currentTimeMillis())
            log.warn("roguelite: archived a stale stash for {} at entry (row 2)", player.gameProfile.name)
        }

        // E1. Void run-marked orphans BEFORE the snapshot (F8: snapshot-first adopted them forever).
        // Marker-keyed deletion of run property — the one destructive act permitted anywhere.
        var orphans = 0
        eachInventorySlot(player) { slot, stack ->
            if (RunItems.isRunItem(stack)) {
                player.inventory.setItem(slot, ItemStack.EMPTY)
                orphans++
            }
        }
        StashSlotProviders.all.forEach { provider ->
            val slots = runCatching { provider.slots(player) }.getOrElse {
                log.error("roguelite: worn-slot provider '{}' failed to enumerate", provider.id, it)
                return EntryResult.Refused("worn equipment could not be read — tell an operator (provider ${provider.id})")
            }
            slots.forEach { (key, stack) ->
                if (RunItems.isRunItem(stack)) {
                    runCatching { provider.clear(player, key) }.getOrElse {
                        return EntryResult.Refused("worn equipment could not be cleared (provider ${provider.id})")
                    }
                    orphans++
                }
            }
        }
        if (orphans > 0) log.info("roguelite: voided {} orphaned run stack(s) for {} at entry", orphans, player.gameProfile.name)

        // E2. Snapshot in memory. Any serialization failure refuses here, before anything changed.
        val swapId = UUID.randomUUID()
        val snapshot = runCatching { buildSnapshot(player, run, swapId) }.getOrElse {
            log.error("roguelite: could not snapshot {}'s inventory", player.gameProfile.name, it)
            return EntryResult.Refused("your inventory could not be stored safely, so the run was not started")
        }

        // E3. Durable stash write. Failure: delete temp (StashFiles does), refuse. Nothing changed.
        try {
            files.writeStash(player.uuid, snapshot)
        } catch (failure: Exception) {
            log.error("roguelite: stash write failed for {}", player.gameProfile.name, failure)
            return EntryResult.Refused("your inventory could not be stored safely (disk error), so the run was not started")
        }

        // E4. One in-memory block: clear everything, zero XP, drop effects, tag, install the run bag.
        //
        // "Everything" INCLUDES the worn slots — the first live test proved what happens when it does
        // not: the band and pauldron stayed on through the run (the Dynamax hole wide open), were
        // quarantined at exit as "acquired during the run", AND were restored from the snapshot's
        // providers section — the player kept the originals and the quarantine gained copies, a
        // duplication per exit, three times before anyone noticed. Cleared here, after the snapshot
        // is durable, with per-slot rollback through the provider on any failure.
        val clearedWorn = mutableListOf<Pair<com.cobblemonroguelite.integration.StashSlotProvider, Pair<String, ItemStack>>>()
        for (provider in StashSlotProviders.all) {
            val worn = runCatching { provider.slots(player) }.getOrElse {
                rollBackWorn(player, clearedWorn)
                log.error("roguelite: provider '{}' failed to enumerate at E4", provider.id, it)
                return EntryResult.Refused("worn equipment could not be cleared — tell an operator (provider ${provider.id})")
            }
            for ((key, stack) in worn) {
                val cleared = runCatching { provider.clear(player, key) }.isSuccess
                if (!cleared) {
                    rollBackWorn(player, clearedWorn)
                    return EntryResult.Refused("worn equipment could not be cleared (provider ${provider.id})")
                }
                clearedWorn += provider to (key to stack)
            }
        }

        val memoryRollback = captureLiveState(player)
        // Held aside before installRunBag clears it, because the E5 failure path below must put it
        // back: rolling back the inventory without restoring the bag would delete the run's items
        // from RunState in memory — found in self-review, the exact class of asymmetric-rollback bug
        // F2 was about, one layer down.
        val bagInstalled = run.runBag.toList()
        clearLiveState(player)
        persisted(player).putString(TAG_KEY, swapId.toString())
        installRunBag(player, run)

        // E5. Force the playerdata write and fsync it. This is what welds tag-set and inventory-clear
        // into one disk event. On failure: roll memory back to the pre-swap state, untag, refuse —
        // E3's file becomes row-2 stale and is archived at the next opportunity.
        if (!savePlayerDurably(player)) {
            restoreLiveState(player, memoryRollback)
            rollBackWorn(player, clearedWorn)
            persisted(player).remove(TAG_KEY)
            run.runBag.clear()
            run.runBag.addAll(bagInstalled)
            log.error("roguelite: entry save failed for {} — swap rolled back", player.gameProfile.name)
            return EntryResult.Refused("your inventory could not be stored safely (save failed), so the run was not started")
        }

        log.info(
            "roguelite: stashed {}'s inventory ({} stacks) under {}",
            player.gameProfile.name, snapshot.getCompound("inventory").getList("slots", 10).size, swapId,
        )
        return EntryResult.Ok
    }

    // ------------------------------------------------------------------ exit: X1..X5

    /**
     * The exit swap — one function, five doors (run end, pause, death-respawn, logout, displacement).
     * The caller chooses the door; every door runs the same protocol.
     */
    fun exitSwap(player: ServerPlayer, run: RunState?): ExitResult {
        assertServerThread(player)
        val files = files(player.server)
        val swapId = tagOf(player) ?: return ExitResult.NothingToDo

        val snapshot = runCatching { files.readStash(player.uuid) }.getOrElse {
            log.error("roguelite: {}'s stash is unreadable — row 3", player.gameProfile.name, it)
            return ExitResult.Alarm
        } ?: run {
            log.error("roguelite: {} carries stash tag {} but no stash file exists — row 3", player.gameProfile.name, swapId)
            return ExitResult.Alarm
        }
        val header = snapshot.getCompound("header")
        if (header.getString("swapId") != swapId.toString()) {
            // §6 footnote: alarm for the promised file; the mismatched file itself is row-2 stale.
            files.archiveStash(player.uuid, System.currentTimeMillis())
            log.error(
                "roguelite: {}'s stash file is for swap {} but the tag says {} — mismatched file archived, row 3",
                player.gameProfile.name, header.getString("swapId"), swapId,
            )
            return ExitResult.Alarm
        }

        // X1. Capture the run bag — overwrite, never append. Skipped when the run is gone (row 4).
        if (run != null) {
            run.runBag.clear()
            eachInventorySlot(player) { _, stack ->
                if (RunItems.isRunItem(stack)) run.runBag.add(stack.copy())
            }
            RunStore.of(player.server).checkpoint(player.server, player.uuid)
        }

        // X2. Partition: marked → void (run property); unmarked → quarantine, durably, BEFORE removal.
        val toQuarantine = mutableListOf<ItemStack>()
        eachInventorySlot(player) { slot, stack ->
            when {
                RunItems.isRunItem(stack) -> player.inventory.setItem(slot, ItemStack.EMPTY)
                !stack.isEmpty -> {
                    toQuarantine.add(stack.copy())
                    player.inventory.setItem(slot, ItemStack.EMPTY)
                }
            }
        }
        StashSlotProviders.all.forEach { provider ->
            runCatching {
                provider.slots(player).forEach { (key, stack) ->
                    when {
                        RunItems.isRunItem(stack) -> provider.clear(player, key)
                        !stack.isEmpty -> {
                            toQuarantine.add(stack.copy())
                            provider.clear(player, key)
                        }
                    }
                }
            }.onFailure { log.error("roguelite: provider '{}' failed during exit partition", provider.id, it) }
        }
        var quarantined = 0
        if (toQuarantine.isNotEmpty()) {
            val written = runCatching {
                val tag = CompoundTag()
                val list = ListTag()
                toQuarantine.forEach { list.add(it.save(player.server.registryAccess())) }
                tag.put("stacks", list)
                tag.putString("player", player.gameProfile.name)
                files.writeQuarantine(player.uuid, System.currentTimeMillis(), tag)
            }.isSuccess
            if (written) {
                quarantined = toQuarantine.size
                val names = toQuarantine.joinToString { "${it.count}x ${it.hoverName.string}" }
                log.warn(
                    "roguelite: quarantined {} unmarked stack(s) from {} at exit — something minted " +
                        "unmarked items inside the arena: [{}]",
                    quarantined, player.gameProfile.name, names,
                )
            } else {
                // Invariant 1 outranks invariant 2: if the quarantine cannot be made durable, the
                // stacks go back to the player rather than existing nowhere.
                giveOrDrop(player, toQuarantine)
                log.warn(
                    "roguelite: quarantine write failed for {} — {} unmarked stack(s) RETURNED instead",
                    player.gameProfile.name, toQuarantine.size,
                )
            }
        }

        // X3. Restore, in memory: recorded slots, residue kept, XP SET (never added), effects back.
        val exitRollback = captureLiveState(player)
        val restore = restoreSnapshot(player, snapshot)
        persisted(player).remove(TAG_KEY)

        // X4. The paired durable save. On failure ROLL THE RESTORE BACK (F2): memory must always
        // match one of the two legal disk states, and the disk still says swapped-and-tagged.
        if (!savePlayerDurably(player)) {
            restoreLiveState(player, exitRollback)
            persisted(player).putString(TAG_KEY, swapId.toString())
            log.error("roguelite: exit save failed for {} — restore rolled back, will retry", player.gameProfile.name)
            return ExitResult.RolledBack
        }

        // X5. Archive, strictly after X4's fsync has returned (F9).
        files.archiveStash(player.uuid, System.currentTimeMillis())

        log.info(
            "roguelite: restored {}'s inventory — {} stack(s) back, {} residue, {} quarantined",
            player.gameProfile.name, restore.returned, restore.residue, quarantined,
        )
        return ExitResult.Ok(restore.returned, restore.residue, quarantined)
    }

    // ------------------------------------------------------------------ snapshot codec

    private fun buildSnapshot(player: ServerPlayer, run: RunState, swapId: UUID): CompoundTag {
        val registries = player.server.registryAccess()
        val tag = CompoundTag()

        tag.put("header", CompoundTag().apply {
            putString("swapId", swapId.toString())
            putLong("runSeed", run.seed)
            putLong("at", System.currentTimeMillis())
            putInt("format", FORMAT_VERSION)
        })

        // Vanilla's own inventory codec: every entry carries its slot byte, which is Quick Teams'
        // "remember where it came from" done with the format the game itself trusts.
        tag.put("inventory", CompoundTag().apply {
            put("slots", player.inventory.save(ListTag()))
            putInt("selected", player.inventory.selected)
        })

        tag.put("life", CompoundTag().apply {
            putFloat("health", player.health)
            putInt("food", player.foodData.foodLevel)
            putFloat("saturation", player.foodData.saturationLevel)
            putInt("xpLevel", player.experienceLevel)
            putFloat("xpProgress", player.experienceProgress)
        })

        val effects = ListTag()
        player.activeEffects.forEach { effects.add(it.save()) }
        tag.put("effects", effects)

        val providers = CompoundTag()
        StashSlotProviders.all.forEach { provider ->
            // Throws propagate: E2's caller refuses entry on any section failure (fail closed at the
            // seam — a run that starts with an unreadable worn slot is a run with a hidden bag).
            val section = CompoundTag()
            provider.slots(player).forEach { (key, stack) ->
                if (!stack.isEmpty) section.put(key, stack.save(registries))
            }
            if (!section.isEmpty) providers.put(provider.id, section)
        }
        tag.put("providers", providers)

        return tag
    }

    private data class RestoreOutcome(val returned: Int, val residue: Int)

    private fun restoreSnapshot(player: ServerPlayer, snapshot: CompoundTag): RestoreOutcome {
        val registries = player.server.registryAccess()
        var returned = 0
        var residue = 0

        // Per-entry rather than Inventory.load(), because load() drops unparseable stacks silently
        // and the residue rule (X3) requires the opposite: restore the rest, KEEP the file, tell the
        // player, op re-restores after the missing mod returns.
        clearLiveState(player)
        val slots = snapshot.getCompound("inventory").getList("slots", 10)
        for (i in 0 until slots.size) {
            val entry = slots.getCompound(i)
            val slot = entry.getByte("Slot").toInt() and 255
            val stack = ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY)
            if (stack.isEmpty) {
                residue++
                continue
            }
            when {
                slot in 0 until player.inventory.items.size && player.inventory.items[slot].isEmpty ->
                    player.inventory.items[slot] = stack.also { returned++ }
                slot in 100 until 100 + player.inventory.armor.size && player.inventory.armor[slot - 100].isEmpty ->
                    player.inventory.armor[slot - 100] = stack.also { returned++ }
                slot == 150 && player.inventory.offhand[0].isEmpty ->
                    player.inventory.offhand[0] = stack.also { returned++ }
                else -> {
                    // Occupied or unknown slot index: first free, then the floor —
                    // RunPayoutDelivery's rule, recoverable beats silently discarded.
                    giveOrDrop(player, listOf(stack))
                    returned++
                }
            }
        }
        player.inventory.selected = snapshot.getCompound("inventory").getInt("selected").coerceIn(0, 8)

        val providers = snapshot.getCompound("providers")
        for (providerId in providers.allKeys) {
            val provider = StashSlotProviders.all.firstOrNull { it.id == providerId }
            val section = providers.getCompound(providerId)
            if (provider == null) {
                // The mod is gone (or the id changed): the whole section is residue. Counted, kept —
                // the archive is never deleted, so an op can re-restore after the mod returns.
                residue += section.allKeys.size
                continue
            }
            for (key in section.allKeys) {
                val stack = ItemStack.parse(registries, section.getCompound(key)).orElse(ItemStack.EMPTY)
                if (stack.isEmpty) {
                    residue++
                    continue
                }
                val placed = runCatching { provider.restore(player, key, stack) }.getOrDefault(false)
                if (placed) returned++ else giveOrDrop(player, listOf(stack)).also { returned++ }
            }
        }

        val life = snapshot.getCompound("life")
        player.health = life.getFloat("health").coerceAtLeast(1f)
        player.foodData.foodLevel = life.getInt("food")
        player.foodData.setSaturation(life.getFloat("saturation"))
        player.setExperienceLevels(life.getInt("xpLevel"))
        player.setExperiencePoints((player.xpNeededForNextLevel * life.getFloat("xpProgress")).toInt())

        player.removeAllEffects()
        val effects = snapshot.getList("effects", 10)
        for (i in 0 until effects.size) {
            MobEffectInstance.load(effects.getCompound(i))?.let { player.addEffect(it) }
        }

        return RestoreOutcome(returned, residue)
    }

    // ------------------------------------------------------------------ live-state helpers

    /** The full pre-change state, for the E5/X4 in-memory rollbacks. Same codec as the stash. */
    private fun captureLiveState(player: ServerPlayer): CompoundTag {
        val tag = CompoundTag()
        tag.put("slots", player.inventory.save(ListTag()))
        tag.putInt("selected", player.inventory.selected)
        tag.putInt("xpLevel", player.experienceLevel)
        tag.putFloat("xpProgress", player.experienceProgress)
        tag.putFloat("health", player.health)
        tag.putInt("food", player.foodData.foodLevel)
        tag.putFloat("saturation", player.foodData.saturationLevel)
        val effects = ListTag()
        player.activeEffects.forEach { effects.add(it.save()) }
        tag.put("effects", effects)
        return tag
    }

    private fun restoreLiveState(player: ServerPlayer, state: CompoundTag) {
        clearLiveState(player)
        player.inventory.load(state.getList("slots", 10))
        player.inventory.selected = state.getInt("selected").coerceIn(0, 8)
        player.setExperienceLevels(state.getInt("xpLevel"))
        player.setExperiencePoints((player.xpNeededForNextLevel * state.getFloat("xpProgress")).toInt())
        player.health = state.getFloat("health").coerceAtLeast(1f)
        player.foodData.foodLevel = state.getInt("food")
        player.foodData.setSaturation(state.getFloat("saturation"))
        player.removeAllEffects()
        val effects = state.getList("effects", 10)
        for (i in 0 until effects.size) {
            MobEffectInstance.load(effects.getCompound(i))?.let { player.addEffect(it) }
        }
    }

    private fun clearLiveState(player: ServerPlayer) {
        player.inventory.clearContent()
        player.removeAllEffects()
        player.setExperienceLevels(0)
        player.setExperiencePoints(0)
    }

    private fun installRunBag(player: ServerPlayer, run: RunState) {
        // First-free placement; the bag was captured from arbitrary slots and none of them is sacred.
        val bag = run.runBag.toList()
        run.runBag.clear()
        giveOrDrop(player, bag)
    }

    private fun giveOrDrop(player: ServerPlayer, stacks: List<ItemStack>) {
        stacks.forEach { stack ->
            if (!player.inventory.add(stack)) player.drop(stack, false)
        }
    }

    private inline fun eachInventorySlot(player: ServerPlayer, action: (Int, ItemStack) -> Unit) {
        for (slot in 0 until player.inventory.containerSize) {
            action(slot, player.inventory.getItem(slot))
        }
    }

    // ------------------------------------------------------------------ durable playerdata save

    /**
     * Serialize the player and write `playerdata/<uuid>.dat` ourselves, durably.
     *
     * Not `PlayerDataStorage.save` — that is `protected` behind `PlayerList`, and vanilla's
     * `safeReplaceFile` renames without fsync (F9), which is the exact gap this exists to close.
     * `Entity.saveWithoutId` is the same serializer vanilla's save calls, and the `.dat_old`
     * rotation is preserved so every other reader of playerdata sees the format it expects. Vanilla
     * still saves normally on logout and autosave; this is a supplementary write, not a replacement,
     * so a mixin some other mod put on the save path misses at most durability, never data.
     *
     * §12.1 stands: mid-tick per-player saves on a 69-mod server are a must-verify-live assumption.
     */
    private fun savePlayerDurably(player: ServerPlayer): Boolean = runCatching {
        val dir = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
        Files.createDirectories(dir)
        val target = dir.resolve("${player.stringUUID}.dat")
        val old = dir.resolve("${player.stringUUID}.dat_old")
        val temp = dir.resolve("${player.stringUUID}.dat.tmp")

        Files.newOutputStream(temp).use { out -> NbtIo.writeCompressed(player.saveWithoutId(CompoundTag()), out) }
        FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
        if (Files.exists(target)) Files.move(target, old, StandardCopyOption.REPLACE_EXISTING)
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
        FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
    }.onFailure {
        log.error("roguelite: durable playerdata save failed for {}", player.gameProfile.name, it)
    }.isSuccess

    /** Put worn stacks back through their providers — the E4/E5 refusal path's other half. */
    private fun rollBackWorn(
        player: ServerPlayer,
        cleared: List<Pair<com.cobblemonroguelite.integration.StashSlotProvider, Pair<String, ItemStack>>>,
    ) {
        cleared.asReversed().forEach { (provider, slot) ->
            val (key, stack) = slot
            val back = runCatching { provider.restore(player, key, stack) }.getOrDefault(false)
            if (!back) {
                // Nowhere worse than the inventory: the swap is being rolled back, so the inventory is
                // (or is about to be) the player's own again, and a worn item in a pocket beats one
                // that exists nowhere.
                giveOrDrop(player, listOf(stack))
                log.warn("roguelite: rollback returned a worn item to {}'s inventory instead of slot {}", player.gameProfile.name, key)
            }
        }
    }

    private fun assertServerThread(player: ServerPlayer) {
        check(player.server.isSameThread) {
            "RunInventoryStash must run on the server thread — see the design's RunCapture note"
        }
    }
}
