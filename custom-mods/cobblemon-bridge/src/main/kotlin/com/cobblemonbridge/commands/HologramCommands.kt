package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.gymtp.WarpPos
import com.cobblemonbridge.holograms.HologramEntry
import com.cobblemonbridge.holograms.HologramStore
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import net.neoforged.fml.loading.FMLPaths

/**
 * Persistent server-side holograms via vanilla `text_display` entities.
 *
 * Each hologram is identified by a string id. Metadata lives in
 * `config/cobblemon-bridge/runtime/holograms.json` (id → position/text/billboard). The actual
 * entity in the world carries a `cobblemon_bridge.hologram_id.<id>` tag so we can find and
 * delete it later even if its UUID rotates.
 *
 * The TextDisplay class's `setText` is private in vanilla, so we initialize the entity via
 * NBT (Entity#load) which calls the protected `readAdditionalSaveData` to apply the text +
 * billboard fields.
 *
 * Subcommands (op level 2):
 *   /hologram set <id> <text...>                 capture sender pos+dim, summon
 *   /hologram set <id> at <pos> [dim] <text>     explicit position
 *   /hologram set <id> billboard <mode>          retarget an existing hologram's billboard
 *   /hologram move <id>                          re-summon current id at sender pos
 *   /hologram delete <id>                        remove the tagged entity + JSON entry
 *   /hologram list                               print all entries
 *   /hologram respawn-all                        re-summon any stored entries that have no
 *                                                live entity (e.g. after a world swap)
 */
object HologramCommands {

    private const val OP_LEVEL = 2
    private const val TAG_PREFIX = "cobblemon_bridge.hologram_id."
    private val VALID_BILLBOARDS = setOf("center", "vertical", "horizontal", "fixed")

    @Volatile
    private var store: HologramStore? = null

    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("holograms.json")
        store = HologramStore.load(file)
        CobblemonBridge.logger.info(
            "holograms: loaded {} entries from {}", store?.entries()?.size ?: 0, file,
        )
    }

    private fun store(): HologramStore = store
        ?: error("HologramCommands not initialized — CobblemonBridge should call HologramCommands.init()")

    private val ID_SUGGESTIONS: SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { _, builder ->
            store().entries().keys.forEach { builder.suggest(it) }
            builder.buildFuture()
        }

    private val BILLBOARD_SUGGESTIONS: SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { _, builder ->
            VALID_BILLBOARDS.forEach { builder.suggest(it) }
            builder.buildFuture()
        }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("hologram")
                .requires { it.hasPermission(OP_LEVEL) }
                .executes { ctx -> printHelp(ctx.source); 1 }
                .then(Commands.literal("help").executes { ctx -> printHelp(ctx.source); 1 })
                .then(Commands.literal("list").executes { ctx -> listEntries(ctx.source); 1 })
                .then(Commands.literal("respawn-all").executes { ctx -> respawnAll(ctx.source) })
                .then(Commands.literal("delete")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ID_SUGGESTIONS)
                        .executes { ctx -> deleteHologram(ctx.source, StringArgumentType.getString(ctx, "id")) }
                    )
                )
                .then(Commands.literal("move")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ID_SUGGESTIONS)
                        .executes { ctx -> moveHologram(ctx.source, StringArgumentType.getString(ctx, "id")) }
                    )
                )
                .then(Commands.literal("set")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ID_SUGGESTIONS)
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes { ctx ->
                                setFromSender(
                                    ctx.source,
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "text"),
                                )
                            }
                        )
                        .then(Commands.literal("at")
                            .then(Commands.argument("pos", Vec3Argument.vec3(true))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                    .executes { ctx -> setExplicit(ctx, includeDim = false) }
                                )
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                    .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes { ctx -> setExplicit(ctx, includeDim = true) }
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("billboard")
                            .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(BILLBOARD_SUGGESTIONS)
                                .executes { ctx ->
                                    setBillboard(
                                        ctx.source,
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "mode"),
                                    )
                                }
                            )
                        )
                    )
                )
        )
    }

    // ─── handlers ──────────────────────────────────────────────────────────

    private fun printHelp(source: CommandSourceStack) {
        listOf(
            "§e[Hologram] §fAdmin (op level $OP_LEVEL):",
            "§7  /hologram set <id> <text…> §f— summon at your pos+dim",
            "§7  /hologram set <id> at <pos> [dim] <text…> §f— explicit",
            "§7  /hologram set <id> billboard <center|vertical|horizontal|fixed>",
            "§7  /hologram move <id> §f— re-summon at your position",
            "§7  /hologram delete <id>",
            "§7  /hologram list",
            "§7  /hologram respawn-all §f— recreate stored entries with no live entity",
        ).forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    private fun setFromSender(source: CommandSourceStack, id: String, text: String): Int {
        val pos = source.position
        val dim = source.level.dimension().location().toString()
        val entry = HologramEntry(
            position = WarpPos(pos.x, pos.y, pos.z, dim, 0f, 0f),
            text = text,
            billboard = store().get(id)?.billboard ?: "center",
        )
        return upsert(source, id, entry)
    }

    private fun setExplicit(ctx: CommandContext<CommandSourceStack>, includeDim: Boolean): Int {
        val source = ctx.source
        val id = StringArgumentType.getString(ctx, "id")
        val coord = Vec3Argument.getVec3(ctx, "pos")
        val text = StringArgumentType.getString(ctx, "text")
        val dim = if (includeDim) {
            DimensionArgument.getDimension(ctx, "dimension").dimension().location().toString()
        } else source.level.dimension().location().toString()
        val entry = HologramEntry(
            position = WarpPos(coord.x, coord.y, coord.z, dim, 0f, 0f),
            text = text,
            billboard = store().get(id)?.billboard ?: "center",
        )
        return upsert(source, id, entry)
    }

    private fun setBillboard(source: CommandSourceStack, id: String, mode: String): Int {
        val existing = store().get(id) ?: run {
            source.sendSystemMessage(Component.literal("§c[Hologram] '$id' doesn't exist. Use /hologram set $id <text> first."))
            return 0
        }
        if (mode !in VALID_BILLBOARDS) {
            source.sendSystemMessage(Component.literal("§c[Hologram] Invalid billboard mode '$mode' (use ${VALID_BILLBOARDS.joinToString("|")})"))
            return 0
        }
        return upsert(source, id, existing.copy(billboard = mode))
    }

    /** Idempotent write: kill any existing entity with this id's tag, store + spawn fresh. */
    private fun upsert(source: CommandSourceStack, id: String, entry: HologramEntry): Int {
        val level = resolveLevel(source, entry.position.world) ?: return 0
        killTagged(level, id)
        store().set(id, entry)
        return if (spawnAt(level, id, entry)) {
            val p = entry.position
            source.sendSystemMessage(Component.literal(
                "§a[Hologram] '$id' → ${"%.1f".format(p.x)}, ${"%.1f".format(p.y)}, ${"%.1f".format(p.z)} §8(${p.world}, billboard=${entry.billboard})"
            ))
            1
        } else {
            source.sendSystemMessage(Component.literal("§c[Hologram] Failed to spawn entity for '$id'"))
            0
        }
    }

    private fun moveHologram(source: CommandSourceStack, id: String): Int {
        val existing = store().get(id) ?: run {
            source.sendSystemMessage(Component.literal("§c[Hologram] '$id' doesn't exist"))
            return 0
        }
        return setFromSender(source, id, existing.text)
    }

    private fun deleteHologram(source: CommandSourceStack, id: String): Int {
        val entry = store().get(id) ?: run {
            source.sendSystemMessage(Component.literal("§c[Hologram] '$id' doesn't exist"))
            return 0
        }
        val level = resolveLevel(source, entry.position.world)
        val killed = level?.let { killTagged(it, id) } ?: 0
        store().remove(id)
        source.sendSystemMessage(Component.literal("§a[Hologram] Removed '$id'§7 (killed $killed entity)"))
        return 1
    }

    private fun listEntries(source: CommandSourceStack) {
        val entries = store().entries()
        source.sendSystemMessage(Component.literal("§e[Hologram] §f${entries.size} entr${if (entries.size == 1) "y" else "ies"}:"))
        if (entries.isEmpty()) {
            source.sendSystemMessage(Component.literal("§7  (none — /hologram set <id> <text> to add one)"))
            return
        }
        for ((id, entry) in entries) {
            val p = entry.position
            val preview = entry.text.take(40) + if (entry.text.length > 40) "…" else ""
            source.sendSystemMessage(Component.literal(
                "§7  §e$id§7 → §f${"%.0f".format(p.x)}, ${"%.0f".format(p.y)}, ${"%.0f".format(p.z)} §8(${p.world}, billboard=${entry.billboard}) §f\"$preview\""
            ))
        }
    }

    private fun respawnAll(source: CommandSourceStack): Int {
        var spawned = 0
        var skipped = 0
        for ((id, entry) in store().entries()) {
            val level = resolveLevel(source, entry.position.world) ?: run { skipped++; continue }
            val hasLive = findTagged(level, id).isNotEmpty()
            if (hasLive) { skipped++; continue }
            if (spawnAt(level, id, entry)) spawned++ else skipped++
        }
        source.sendSystemMessage(Component.literal("§a[Hologram] Respawned $spawned§7, skipped $skipped (already live or load failed)"))
        return spawned
    }

    // ─── entity ops ──────────────────────────────────────────────────────────

    private fun resolveLevel(source: CommandSourceStack, world: String): ServerLevel? {
        val rl = ResourceLocation.tryParse(world) ?: run {
            source.sendSystemMessage(Component.literal("§c[Hologram] Invalid world: $world"))
            return null
        }
        val key = ResourceKey.create(Registries.DIMENSION, rl)
        val level = source.server.getLevel(key) ?: run {
            source.sendSystemMessage(Component.literal("§c[Hologram] Dimension not loaded: $world"))
            return null
        }
        return level
    }

    private fun findTagged(level: ServerLevel, id: String): List<Display.TextDisplay> {
        val tag = TAG_PREFIX + id
        // World-wide scan capped at 30M-block AABB — enough for any reasonable world.
        val box = AABB(-30_000_000.0, -512.0, -30_000_000.0, 30_000_000.0, 2048.0, 30_000_000.0)
        return level.getEntitiesOfClass(Display.TextDisplay::class.java, box) { it.tags.contains(tag) }
    }

    private fun killTagged(level: ServerLevel, id: String): Int {
        val matches = findTagged(level, id)
        for (e in matches) e.discard()
        return matches.size
    }

    private fun spawnAt(level: ServerLevel, id: String, entry: HologramEntry): Boolean {
        val entity = EntityType.TEXT_DISPLAY.create(level) as? Display.TextDisplay ?: return false
        entity.moveTo(entry.position.x, entry.position.y, entry.position.z, 0f, 0f)

        // setText is private on TextDisplay, so initialize via NBT load(). The "text" key takes
        // a JSON text-component string; we wrap raw text as {"text":"..."} and escape quotes.
        // §-color codes inside the raw text are honored by vanilla text component renderer.
        val tag = CompoundTag().apply {
            put("Tags", ListTag().also { it.add(StringTag.valueOf(TAG_PREFIX + id)) })
            putString("billboard", entry.billboard)
            putString("text", jsonTextComponent(entry.text))
        }
        entity.load(tag)
        // moveTo had to come BEFORE load (load may reset position from missing Pos field) — but
        // load with empty Pos preserves the moveTo position. Re-apply to be defensive.
        entity.moveTo(entry.position.x, entry.position.y, entry.position.z, 0f, 0f)
        entity.addTag(TAG_PREFIX + id)
        return level.addFreshEntity(entity)
    }

    private fun jsonTextComponent(raw: String): String {
        // Minimal valid JSON text component. Escape backslashes and quotes; preserve § codes.
        val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"text\":\"$escaped\"}"
    }
}
