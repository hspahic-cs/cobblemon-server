package com.cobblemonroguelite.data

import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemonroguelite.CobblemonRoguelite
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.io.Reader

private val log = LoggerFactory.getLogger("cobblemon_roguelite/data")

/**
 * Base for every datapack-driven table this mod reads. Reward tables are the first; species pools
 * and starter pools are expected to follow, and are expected to follow *this*.
 *
 * ### The convention
 *
 * A registry owns one folder and reads every `.json` under it, from every namespace:
 *
 * ```
 * data/<any-namespace>/roguelite/<folder>/<name>.json   ->   <any-namespace>:<name>
 * ```
 *
 * Subfolders are part of the id (`.../reward_tables/boss/wave10.json` is `ns:boss/wave10`), which is
 * a deliberate departure from Cobblemon's [com.cobblemon.mod.common.api.data.JsonDataRegistry]: that
 * one keys on `File(path).nameWithoutExtension`, so two files with the same basename in different
 * subfolders overwrite each other with nothing in the log. Keeping the full relative path means
 * folders are free to use for organisation.
 *
 * Overriding is left to the resource manager. `ResourceManager.listResources` already returns one
 * winning resource per path, so a datapack that ships a file at the same path as ours replaces ours
 * wholesale — the standard datapack rule, and the reason this format has no `"replace"` flag.
 *
 * ### Why a datapack and not a config file
 *
 * §2.12 of the plan: tables must reload without a restart, and a server owner running a published
 * build (§1.2) has to be able to write their own without touching the jar. A config file gives them
 * neither — it is inside our directory, and reloading it means reimplementing the machinery below.
 *
 * ### Why we register with Cobblemon's data provider rather than NeoForge's reload event
 *
 * `AddReloadListenerEvent` would work and would cost us no Cobblemon coupling, but this module
 * depends on Cobblemon regardless (§2.9 forbids depending on *our* mods, not on Cobblemon), and
 * riding their provider buys ordering we would otherwise have to arrange ourselves: their
 * registries are already loaded when ours runs, so a table that names a Cobblemon move or item is
 * being read after the registry that would answer for it. It also means our reload is skipped in
 * exactly the same situations theirs is — notably the create-world screen, which fires datapack
 * reloads before any server exists.
 *
 * ### Failure policy
 *
 * A file that fails to parse is dropped **on its own**; the rest of the folder still loads. A reload
 * that loads nothing leaves the registry empty rather than keeping the previous contents, because a
 * table the owner deleted has to actually disappear — but that case is logged at ERROR, since an
 * empty table is silently indistinguishable from a working one until a player is owed a reward.
 */
abstract class RogueliteDataRegistry<T : Any>(
    /** Folder under `data/<ns>/roguelite/`. Also this registry's id, and its name in the log. */
    private val folder: String,
) : DataRegistry {

    override val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CobblemonRoguelite.MOD_ID, folder)

    override val type: PackType = PackType.SERVER_DATA

    override val observable = SimpleObservable<DataRegistry>()

    private val resourceRoot = "$ROOT/$folder"

    /**
     * Everything currently loaded, keyed by id.
     *
     * Replaced wholesale by [reload] rather than mutated, and `@Volatile` so that a reader on the
     * server thread sees either the old map or the new one and never a half-built one — `/reload`
     * runs off the server thread.
     */
    @Volatile
    var entries: Map<ResourceLocation, T> = emptyMap()
        private set

    operator fun get(id: ResourceLocation): T? = entries[id]

    fun isEmpty(): Boolean = entries.isEmpty()

    /**
     * Turn one parsed file into a value, or null if it is unusable.
     *
     * Implementations report *every* problem they find into [problems] before giving up, rather than
     * returning at the first one — see [DataProblems] for why. Returning non-null with problems
     * recorded is legitimate and means "partly loaded": the file survived, some of its contents did
     * not, and the log says which.
     */
    protected abstract fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): T?

    /**
     * Server-side only, so there is nothing to send. Reward tables decide what a run *may* hand out;
     * the client is told what it actually got, by the run itself. Syncing the table would put the
     * whole rarity curve on every client for no feature.
     */
    override fun sync(player: ServerPlayer) = Unit

    override fun reload(manager: ResourceManager) {
        val loaded = linkedMapOf<ResourceLocation, T>()
        var rejected = 0

        manager.listResources(resourceRoot) { it.path.endsWith(JSON_SUFFIX) }.forEach { (path, resource) ->
            val fileId = idOf(path) ?: return@forEach
            val problems = DataProblems(fileId)
            val parsed = runCatching { resource.open().use { stream -> stream.bufferedReader().use { parseJson(fileId, it, problems) } } }
                .onFailure { problems.add("", "could not be read: ${it.message}") }
                .getOrNull()
            problems.log(log)
            if (parsed == null) {
                rejected++
                // Named separately from the problems above so that "this file contributed nothing"
                // is greppable on its own — a partly-loaded file also logs warnings, and the two
                // outcomes are very different to the owner.
                log.error("roguelite: {} was rejected and none of it is loaded ({} problem(s))", fileId, problems.count)
            } else {
                loaded[fileId] = parsed
            }
        }

        entries = loaded
        when {
            loaded.isEmpty() && rejected > 0 ->
                log.error("roguelite: {} is EMPTY — all {} file(s) were rejected", id, rejected)
            loaded.isEmpty() ->
                log.warn("roguelite: {} is empty — no files found under data/<namespace>/{}/", id, resourceRoot)
            else ->
                log.info("roguelite: loaded {} file(s) into {} ({} rejected)", loaded.size, id, rejected)
        }
        loaded.keys.forEach { log.debug("roguelite: {} loaded {}", id, it) }
        observable.emit(this)
    }

    /**
     * Parse one document. Separate from [reload] so it can be driven from a test without a resource
     * manager, which is the only way any of this is testable — the parse rules are where a server
     * owner's mistake either gets named or gets swallowed, and that has to be under test even though
     * a booted server is not.
     */
    fun parseJson(fileId: ResourceLocation, reader: Reader, problems: DataProblems): T? {
        val element = runCatching { JsonParser.parseReader(reader) }
            .onFailure { problems.add("", "is not valid JSON: ${it.message}") }
            .getOrNull() ?: return null
        val obj = element as? JsonObject
        if (obj == null) {
            problems.add("", "expected a JSON object at the top level")
            return null
        }
        return parse(fileId, JsonView.root(obj, problems), problems)
    }

    /**
     * `roguelite/reward_tables/boss/wave10.json` in namespace `ns` becomes `ns:boss/wave10`.
     * Returns null for anything that somehow does not sit under our root, which the predicate in
     * [reload] should already have excluded.
     */
    private fun idOf(path: ResourceLocation): ResourceLocation? {
        val prefix = "$resourceRoot/"
        if (!path.path.startsWith(prefix) || !path.path.endsWith(JSON_SUFFIX)) return null
        val name = path.path.substring(prefix.length, path.path.length - JSON_SUFFIX.length)
        return runCatching { ResourceLocation.fromNamespaceAndPath(path.namespace, name) }.getOrNull()
    }

    companion object {
        /**
         * Folder every table of ours lives under. Namespaced by feature rather than dumped at the
         * top of `data/<ns>/` so that a server owner's pack can hold our tables next to their own
         * content without either having to guess what `reward_tables` belongs to.
         */
        const val ROOT = "roguelite"

        private const val JSON_SUFFIX = ".json"
    }
}
