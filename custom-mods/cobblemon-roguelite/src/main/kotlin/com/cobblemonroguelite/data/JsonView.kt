package com.cobblemonroguelite.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger

/**
 * Everything wrong with one datapack file, collected in a single pass.
 *
 * The alternative — throw on the first bad field — is what Cobblemon's own [com.cobblemon.mod.common.api.data.JsonDataRegistry]
 * does (it wraps Gson's exception in an `ExecutionException` and lets it out of the reload), and it
 * is the wrong shape for a file a server owner types by hand. It costs them one reload per mistake,
 * and it reports the mistake as a stack trace whose top frame is Gson rather than as the name of the
 * field they got wrong. Collecting instead means a table with four typos is fixable in one sitting.
 *
 * Every message is prefixed with the file it came from, because by the time this reaches a log the
 * only thing the reader has is the log — there is no editor open, and "expected a number" on its own
 * names nothing they can go and fix.
 */
class DataProblems(private val file: ResourceLocation) {

    private val messages = mutableListOf<String>()

    val count: Int get() = messages.size

    fun isEmpty(): Boolean = messages.isEmpty()

    /**
     * The collected lines. Exposed for tests, which assert on the *wording* rather than on a
     * boolean: a validator that rejects the right files but names the wrong field is a validator
     * that has failed at the only job it has.
     */
    internal fun messages(): List<String> = messages.toList()

    /**
     * Record a problem at [path] — a dotted/indexed field path such as `entries[2].reward.stat`,
     * or empty for the file as a whole.
     */
    fun add(path: String, message: String) {
        messages += if (path.isEmpty()) "$file: $message" else "$file at $path: $message"
    }

    /**
     * Emit everything collected, one line each.
     *
     * WARN rather than ERROR even when the file is ultimately rejected: the rejection itself is
     * logged separately at ERROR by the registry, and duplicating the severity here would make a
     * table that lost one optional entry look as alarming as one that failed to load at all.
     */
    fun log(logger: Logger) {
        messages.forEach { logger.warn("roguelite: {}", it) }
    }
}

/**
 * A checked read over one JSON object, reporting into [DataProblems] instead of throwing.
 *
 * ### Why the reader only does structure
 *
 * Accessors here answer "is this field present, and is it the type I asked for" and nothing else.
 * Range and meaning checks — a weight that must be positive, a wave band that must not run
 * backwards — are deliberately left to the caller, because that is the only place a message can say
 * *why* the value is wrong. A generic `must be >= 4.9E-324` from a shared helper is technically a
 * validation error and practically useless to the person reading it.
 *
 * ### Unknown fields are errors, and that is a considered trade
 *
 * [expectNoUnknownKeys] rejects any field this code did not ask for. The failure it exists to catch
 * is `"wieght": 10` — which under a lenient reader loads fine, silently takes the default, and shows
 * up months later as "my rare rewards never appear" with nothing in the log. That failure mode is
 * strictly worse than a rejected file, because a rejected file announces itself.
 *
 * The cost is forward compatibility: a table written against a later version of this mod, using a
 * field this version does not know, is rejected rather than partly honoured. That is accepted — a
 * partly-honoured table is a silently wrong table, which is the thing being avoided.
 *
 * Fields whose name starts with `_` are always ignored, which gives hand-written tables somewhere to
 * put comments. JSON has none, and a format meant to be edited by hand needs one.
 */
class JsonView internal constructor(
    private val obj: JsonObject,
    private val path: String,
    private val problems: DataProblems,
) {

    /** Every key this code has looked at — the basis for [expectNoUnknownKeys]. */
    private val consumed = linkedSetOf<String>()

    fun problem(field: String?, message: String) = problems.add(pathOf(field), message)

    /**
     * Whether [key] was written at all, regardless of whether it could be read as the wanted type.
     *
     * Exists because `optionalX` returning null is ambiguous — absent and present-but-wrong-type look
     * the same to the caller — and for optional *blocks* the difference matters: an omitted `tiers`
     * means "flat table", while `"tiers": 5` means the author made a mistake and must not be handed
     * the omitted-field behaviour on top of it.
     */
    fun hasField(key: String): Boolean = obj.get(key)?.isJsonNull == false

    fun requireString(key: String): String? = string(key, required = true)

    fun optionalString(key: String): String? = string(key, required = false)

    fun requireInt(key: String): Int? = int(key, required = true)

    fun optionalInt(key: String): Int? = int(key, required = false)

    fun requireDouble(key: String): Double? = double(key, required = true)

    fun optionalDouble(key: String): Double? = double(key, required = false)

    fun requireObject(key: String): JsonView? = obj(key, required = true)

    fun optionalObject(key: String): JsonView? = obj(key, required = false)

    fun requireObjectList(key: String): List<JsonView>? = objectList(key, required = true)

    fun optionalObjectList(key: String): List<JsonView>? = objectList(key, required = false)

    fun requireStringList(key: String): List<String>? = stringList(key, required = true)

    fun optionalStringList(key: String): List<String>? = stringList(key, required = false)

    /**
     * Report any field left untouched by the reads above. Call this **after** every read on this
     * object, including the optional ones — an optional field that was never asked for looks exactly
     * like a typo from here.
     */
    fun expectNoUnknownKeys() {
        obj.keySet()
            .filterNot { it.startsWith("_") || it in consumed }
            .forEach { unknown ->
                problem(unknown, "unknown field (fields read here: ${consumed.joinToString(", ")})")
            }
    }

    private fun string(key: String, required: Boolean): String? {
        val element = raw(key, required) ?: return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            problem(key, "expected a string, found ${describe(element)}")
            return null
        }
        return primitive.asString
    }

    private fun int(key: String, required: Boolean): Int? {
        val number = number(key, required) ?: return null
        // Rejecting 3.5 rather than truncating it: a fractional wave index or item count is a typo,
        // and silently flooring it hands back a table that does something the author did not write.
        if (number != Math.floor(number) || number.isInfinite()) {
            problem(key, "expected a whole number, found $number")
            return null
        }
        if (number > Int.MAX_VALUE || number < Int.MIN_VALUE) {
            problem(key, "$number is out of range for a whole number")
            return null
        }
        return number.toInt()
    }

    private fun double(key: String, required: Boolean): Double? {
        val number = number(key, required) ?: return null
        if (number.isNaN() || number.isInfinite()) {
            problem(key, "expected a finite number, found $number")
            return null
        }
        return number
    }

    private fun number(key: String, required: Boolean): Double? {
        val element = raw(key, required) ?: return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isNumber) {
            problem(key, "expected a number, found ${describe(element)}")
            return null
        }
        return primitive.asDouble
    }

    private fun obj(key: String, required: Boolean): JsonView? {
        val element = raw(key, required) ?: return null
        val child = element as? JsonObject
        if (child == null) {
            problem(key, "expected an object, found ${describe(element)}")
            return null
        }
        return JsonView(child, pathOf(key), problems)
    }

    private fun objectList(key: String, required: Boolean): List<JsonView>? {
        val element = raw(key, required) ?: return null
        val array = element as? JsonArray
        if (array == null) {
            problem(key, "expected a list, found ${describe(element)}")
            return null
        }
        val views = mutableListOf<JsonView>()
        array.forEachIndexed { index, item ->
            val child = item as? JsonObject
            if (child == null) {
                problems.add("${pathOf(key)}[$index]", "expected an object, found ${describe(item)}")
            } else {
                views += JsonView(child, "${pathOf(key)}[$index]", problems)
            }
        }
        // A list that lost members to the check above still returns what survived. The dropped ones
        // are already named in the log, and rejecting the whole list would hide the good entries
        // behind one bad one.
        return views
    }

    /**
     * A list of plain strings — an enum-ish field such as a set of run outcomes.
     *
     * Unlike [objectList], one bad member fails the **whole list** rather than being dropped from
     * it. A list of strings is normally a *set of conditions*, and silently returning the members
     * that happened to parse would narrow the condition to something the author did not write, which
     * is the failure this reader exists to prevent. A list of objects has no equivalent risk: its
     * members are independent entries, and the survivors still mean what they say.
     */
    private fun stringList(key: String, required: Boolean): List<String>? {
        val element = raw(key, required) ?: return null
        val array = element as? JsonArray
        if (array == null) {
            problem(key, "expected a list, found ${describe(element)}")
            return null
        }
        var ok = true
        val values = mutableListOf<String>()
        array.forEachIndexed { index, item ->
            val primitive = item as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                problems.add("${pathOf(key)}[$index]", "expected a string, found ${describe(item)}")
                ok = false
            } else {
                values += primitive.asString
            }
        }
        return if (ok) values else null
    }

    /**
     * Fetch a field and mark it read. An explicit JSON `null` is treated as absence, so
     * `"max_wave": null` means "no upper bound" rather than "a bound that failed to parse".
     */
    private fun raw(key: String, required: Boolean): JsonElement? {
        consumed += key
        val element = obj.get(key)
        if (element == null || element.isJsonNull) {
            if (required) problem(key, "missing required field")
            return null
        }
        return element
    }

    private fun pathOf(field: String?): String = when {
        field == null -> path
        path.isEmpty() -> field
        else -> "$path.$field"
    }

    companion object {
        internal fun root(obj: JsonObject, problems: DataProblems) = JsonView(obj, "", problems)

        /** What the author actually wrote, for "expected X, found Y" messages. */
        private fun describe(element: JsonElement): String = when {
            element.isJsonObject -> "an object"
            element.isJsonArray -> "a list"
            element.isJsonNull -> "null"
            element is JsonPrimitive && element.isBoolean -> "a boolean (${element.asBoolean})"
            element is JsonPrimitive && element.isNumber -> "a number (${element.asString})"
            element is JsonPrimitive -> "a string (\"${element.asString}\")"
            else -> element.toString()
        }
    }
}
