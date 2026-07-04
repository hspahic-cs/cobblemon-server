package com.cobblemonauction.data

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * (De)serializes full [ItemStack]s — components and all — to a portable SNBT string for JSON
 * persistence. No existing store in this repo persists a whole stack (they keep item ids +
 * counts only), so listings/mailbox contents need this to survive a restart with enchants,
 * custom names, durability, etc. intact.
 *
 * Uses `ItemStack.CODEC` over `NbtOps` wrapped in a [RegistryOps] built from the live server
 * registries — component codecs that reference registries (e.g. enchantments) need that access.
 */
object ItemStacks {
    private val log = LoggerFactory.getLogger("cobblemon-auction/itemstacks")

    /** Encode a NON-empty stack to SNBT. Throws if the stack is empty or encoding fails. */
    fun encode(stack: ItemStack, registries: HolderLookup.Provider): String {
        require(!stack.isEmpty) { "Refusing to encode an empty ItemStack" }
        val ops = RegistryOps.create(NbtOps.INSTANCE, registries)
        val tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()
        return tag.toString()
    }

    /** Decode SNBT back to a stack. Returns [ItemStack.EMPTY] on any failure (logged), so a
     *  single corrupt listing/mail entry degrades to an empty slot instead of crashing the GUI. */
    fun decode(snbt: String, registries: HolderLookup.Provider): ItemStack = try {
        val ops = RegistryOps.create(NbtOps.INSTANCE, registries)
        val tag = TagParser.parseTag(snbt)
        ItemStack.CODEC.parse(ops, tag).getOrThrow()
    } catch (e: Throwable) {
        log.error("Failed to decode stored ItemStack (snbt length=${snbt.length})", e)
        ItemStack.EMPTY
    }
}
