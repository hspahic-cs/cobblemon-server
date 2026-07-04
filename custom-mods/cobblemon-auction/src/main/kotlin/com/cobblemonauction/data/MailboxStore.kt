package com.cobblemonauction.data

import com.cobblemonauction.internal.ConfigPaths
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Per-player mailbox of pending item stacks (purchases + returned listings), persisted to
 * `runtime/mailboxes.json` as `{ "<uuid>": [MailEntry, ...] }`. Save-on-mutate, server thread only.
 */
class MailboxStore(private val configDir: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = ConfigPaths.runtime(configDir, "mailboxes.json")
    private val boxes = HashMap<String, MutableList<MailEntry>>()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<HashMap<String, MutableList<MailEntry>>>() {}.type
            val loaded: HashMap<String, MutableList<MailEntry>> = gson.fromJson(file.readText(), type) ?: HashMap()
            boxes.clear()
            boxes.putAll(loaded)
        } catch (e: Exception) {
            log.error("Failed to load mailboxes", e)
        }
    }

    fun save() {
        try {
            file.parent.createDirectories()
            file.writeText(gson.toJson(boxes))
        } catch (e: Exception) {
            log.error("Failed to save mailboxes", e)
        }
    }

    fun entries(uuid: UUID): List<MailEntry> = boxes[uuid.toString()]?.toList() ?: emptyList()

    fun count(uuid: UUID): Int = boxes[uuid.toString()]?.size ?: 0

    fun add(uuid: UUID, entry: MailEntry) {
        boxes.getOrPut(uuid.toString()) { mutableListOf() }.add(entry)
        save()
    }

    /** Remove and return the entry with [entryId], or null if it isn't in this player's box. */
    fun remove(uuid: UUID, entryId: String): MailEntry? {
        val list = boxes[uuid.toString()] ?: return null
        val idx = list.indexOfFirst { it.id == entryId }
        if (idx < 0) return null
        val removed = list.removeAt(idx)
        if (list.isEmpty()) boxes.remove(uuid.toString())
        save()
        return removed
    }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-auction/mailbox")
    }
}
