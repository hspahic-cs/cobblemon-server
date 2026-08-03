package com.cobblemonroguelite.data.arena

import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import net.minecraft.resources.ResourceLocation

/**
 * Every arena palette on the server, loaded from `data/<namespace>/roguelite/arena_palettes/<name>.json`.
 *
 * ### The file
 *
 * ```json
 * {
 *   "floor": "minecraft:basalt",
 *   "width": 41,
 *   "depth": 41,
 *   "rim": { "block": "minecraft:polished_basalt", "height": 2 },
 *   "pillars": { "block": "minecraft:magma_block", "height": 8, "inset": 3 },
 *   "power_spot": true
 * }
 * ```
 *
 * One palette per file, [com.cobblemonroguelite.data.biome.RunBiomes]' reason exactly: a palette is
 * named from a biome file and from the arena config, so it needs a stable id, and this registry
 * already derives one from the path.
 *
 * ### A bad file costs that palette and nothing else — but the cost is a run that cannot be fought
 *
 * The containment rule is the usual one, and the consequence is sharper than anywhere else in this
 * package. A reward table that fails to load costs a reward; a palette that fails to load costs a
 * *floor*, and the arena dimension is void. So a rejected palette is reported here, and the biome
 * that names it fails again — loudly, naming the id — at [com.cobblemonroguelite.arena.ArenaStamper],
 * which refuses the stamp rather than teleporting somebody into empty space.
 *
 * ### What is checked here and what cannot be
 *
 * Checked: the shape, the numbers, and that no field names `minecraft:air` — an air floor is the
 * silently-empty arena this whole path exists to prevent, and it is the one mistake that reads as a
 * legal palette.
 *
 * Not checked: **that the blocks exist**. The block registry is frozen long before any datapack
 * reload, so we *could* check it here — but the message would then name a file being read at
 * `/reload` rather than the arena that failed to build, and an owner whose modpack lost a mod would
 * see the palette vanish rather than see the arena refuse. It is checked at generation time instead,
 * by [com.cobblemonroguelite.arena.ArenaGenerator], which is the layer that was about to place it.
 *
 * Also not checked: **that the palette fits the arena box**. It cannot be — the box is
 * [com.cobblemonroguelite.arena.ArenaConfig] and can be changed after this file was read, so the fit
 * is decided in [com.cobblemonroguelite.arena.ArenaPlan] against the box actually in force.
 */
object ArenaPalettes : RogueliteDataRegistry<ArenaPalette>("arena_palettes") {

    /**
     * Refused everywhere a block id is accepted.
     *
     * `"floor": "minecraft:air"` parses, registers, generates, and produces an arena with no floor in
     * a void dimension — a run that cannot be fought in, arrived at through nothing that looks like a
     * mistake. Refusing it by name is the only place that reads as an error to the person who typed
     * it.
     */
    private val AIR = ResourceLocation.withDefaultNamespace("air")

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): ArenaPalette? {
        val before = problems.count

        val floor = parseBlock(root, "floor")
        val shape = parseShape(root)
        val width = root.optionalInt("width")
        val depth = root.optionalInt("depth")
        val rim = root.optionalObject("rim")?.let(::parseRim)
        val pillars = root.optionalObject("pillars")?.let(::parsePillars)
        val powerSpot = root.optionalBoolean("power_spot") ?: true
        root.expectNoUnknownKeys()

        // Bounds that do not depend on the arena box. The box-relative ones — does the platform fit,
        // is the rim taller than the box — belong to [ArenaPlan], because the box is configuration
        // and can change under a palette that was perfectly valid when it was written.
        if (width != null) checkFootprint(root, "width", width)
        if (depth != null) checkFootprint(root, "depth", depth)

        if (problems.count != before) return null
        return ArenaPalette(
            id = id,
            floor = floor!!,
            shape = shape,
            width = width,
            depth = depth,
            rim = rim,
            pillars = pillars,
            powerSpot = powerSpot,
        )
    }

    /**
     * `"shape": "circle" | "square"`, defaulting to square.
     *
     * An unrecognised value is a problem rather than a fallback to the default: a typo like `"round"`
     * would otherwise produce a square island with nothing in the log, and the author would be left
     * looking at their blocks wondering which field they got wrong.
     */
    private fun parseShape(view: JsonView): ArenaShape {
        val text = view.optionalString("shape") ?: return ArenaShape.SQUARE
        return ArenaShape.entries.firstOrNull { it.name.equals(text, ignoreCase = true) } ?: run {
            view.problem(
                "shape",
                "'$text' is not a shape — expected one of ${ArenaShape.entries.joinToString("/") { it.name.lowercase() }}",
            )
            ArenaShape.SQUARE
        }
    }

    private fun parseRim(view: JsonView): ArenaRim? {
        val block = parseBlock(view, "block")
        val height = view.optionalInt("height") ?: 1
        view.expectNoUnknownKeys()
        // Zero is refused rather than treated as "no rim". An author who wanted no rim omits the
        // block; one who wrote `"height": 0` meant something and did not get it, and silently
        // honouring the omission spelling would hide that.
        if (height < 1) view.problem("height", "a rim is at least 1 block tall, was $height — omit `rim` for no rim")
        return if (block == null || height < 1) null else ArenaRim(block, height)
    }

    private fun parsePillars(view: JsonView): ArenaPillars? {
        val block = parseBlock(view, "block")
        val height = view.optionalInt("height") ?: 1
        val inset = view.optionalInt("inset") ?: 0
        view.expectNoUnknownKeys()
        if (height < 1) view.problem("height", "a pillar is at least 1 block tall, was $height — omit `pillars` for none")
        if (inset < 0) view.problem("inset", "an inset is measured inwards from the platform corner, was $inset")
        return if (block == null || height < 1 || inset < 0) null else ArenaPillars(block, height, inset)
    }

    /**
     * The platform's smallest useful footprint.
     *
     * Three, and it is load-bearing rather than tidy-minded: a rim on a 2-wide platform is the whole
     * platform, and the power spot sits one block off the platform centre (see [com.cobblemonroguelite.arena.ArenaPlan]),
     * which only lands inside the floor from three wide up.
     */
    private fun checkFootprint(view: JsonView, field: String, value: Int) {
        if (value < MIN_FOOTPRINT) {
            view.problem(field, "$value is too small — an arena platform is at least ${MIN_FOOTPRINT}x$MIN_FOOTPRINT")
        }
    }

    private fun parseBlock(view: JsonView, field: String): ResourceLocation? {
        val text = view.requireString(field) ?: return null
        val parsed = ResourceLocation.tryParse(text)
        if (parsed == null) {
            view.problem(field, "'$text' is not a valid block id (expected namespace:path)")
            return null
        }
        if (parsed == AIR) {
            view.problem(field, "'$text' places nothing — an arena of air is a run with no floor in a void dimension")
            return null
        }
        return parsed
    }

    const val MIN_FOOTPRINT = 3
}
