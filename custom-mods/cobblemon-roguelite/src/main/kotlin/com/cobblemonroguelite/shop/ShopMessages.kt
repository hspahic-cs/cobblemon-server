package com.cobblemonroguelite.shop

import com.cobblemonroguelite.data.reward.RewardEntry
import com.cobblemonroguelite.data.shop.ShopEntry
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Everything the between-wave step says to a player.
 *
 * Its own file for the reasons `CandyMessages` gives, plus one specific to this step: **the two halves
 * have to read as different things**. The whole correction that produced [RewardOffer] and [ShopStock]
 * was that a single shop loses the mechanic, and that distinction only reaches the player through
 * wording. So the free options are printed with no prices at all and the paid row always with them —
 * if a price ever appears next to a free option here, the split has been undone somewhere upstream.
 *
 * Plain English [Component.literal], same as the rest of the module: it ships no language file, and a
 * half-translated mod is worse than an untranslated one.
 *
 * Every message that tells a player what to type names a command that parses. A message naming a
 * command that does not is worse than no message — the player concludes the feature is broken, and they
 * are not wrong.
 */
object ShopMessages {

    // ------------------------------------------------------------------ the free half

    /**
     * The three options, with no prices.
     *
     * [rerollPrice] is the one number here, and it belongs to the reroll rather than to any option.
     * Null means the server has not priced rerolling, in which case the line is omitted entirely rather
     * than printed as "reroll: disabled" — an option a player cannot use is noise.
     */
    fun reward(
        offer: List<RewardEntry>,
        credits: Int,
        alreadyTaken: Boolean,
        rerollPrice: Int?,
    ): List<Component> {
        if (offer.isEmpty()) {
            return listOf(grey("Nothing is on offer at this wave — the reward table has no entries for it."))
        }
        val lines = mutableListOf<Component>()
        lines += Component.literal("Choose one — the others are gone.").withStyle(ChatFormatting.GOLD)
        offer.forEachIndexed { index, entry ->
            lines += Component.literal("  ${index + 1}. ${entry.id}").withStyle(ChatFormatting.WHITE)
                .append(grey("  (${entry.tier})"))
        }
        lines += if (alreadyTaken) {
            grey("You have already taken your reward for this wave.")
        } else {
            grey("/roguelite reward take <id> [slot]")
        }
        if (rerollPrice != null && !alreadyTaken) {
            lines += grey("/roguelite reroll — $rerollPrice credit(s). You have $credits.")
        }
        return lines
    }

    fun taken(entryId: String, granted: GrantResult): Component = when (granted) {
        is GrantResult.Ok -> Component.literal("Took $entryId. ${granted.message}").withStyle(ChatFormatting.GREEN)
        // Reported as taken, because it was: the option is spent either way (see ShopCommands.take).
        is GrantResult.NoEffect ->
            Component.literal("Took $entryId, but ${granted.message}").withStyle(ChatFormatting.YELLOW)
        is GrantResult.Failed ->
            Component.literal("Took $entryId and it could not be applied: ${granted.reason}")
                .withStyle(ChatFormatting.RED)
    }

    fun alreadyTaken(): Component =
        red("You have already taken a reward this wave. Fight the next wave for another.")

    fun rerollDisabled(): Component = red("Rerolling is not enabled on this server.")

    fun notOffered(id: String): Component =
        red("'$id' is not one of the three on offer. Run /roguelite reward to see them.")

    // ------------------------------------------------------------------ the paid half

    /** The consumable row, always with prices. */
    fun shop(stock: List<ShopEntry>, wave: Int, credits: Int): List<Component> {
        if (stock.isEmpty()) {
            return listOf(grey("The shop has nothing stocked at this wave."))
        }
        val lines = mutableListOf<Component>()
        lines += Component.literal("Shop — you have $credits credit(s).").withStyle(ChatFormatting.AQUA)
        stock.forEach { entry ->
            val price = entry.priceAt(wave)
            val affordable = if (price <= credits) ChatFormatting.WHITE else ChatFormatting.DARK_GRAY
            lines += Component.literal("  ${entry.id}").withStyle(affordable)
                .append(grey("  $price credit(s)"))
        }
        lines += grey("/roguelite shop buy <id> [slot] — buy as many as you can afford.")
        return lines
    }

    fun bought(entryId: String, price: Int, remaining: Int, granted: GrantResult): Component = when (granted) {
        is GrantResult.Ok ->
            Component.literal("Bought $entryId for $price. ${granted.message} ($remaining left)")
                .withStyle(ChatFormatting.GREEN)
        is GrantResult.NoEffect ->
            Component.literal("Bought $entryId for $price, but ${granted.message} ($remaining left)")
                .withStyle(ChatFormatting.YELLOW)
        // Names the price explicitly, because the credits are gone and a player is entitled to see that
        // stated rather than inferring it from a balance that moved. See RewardGrant.apply on why the
        // charge happens first.
        is GrantResult.Failed ->
            Component.literal("Bought $entryId for $price and it could not be applied: ${granted.reason}")
                .withStyle(ChatFormatting.RED)
    }

    fun notStocked(id: String): Component =
        red("'$id' is not stocked at this wave. Run /roguelite shop to see what is.")

    // ------------------------------------------------------------------ shared refusals

    fun tooPoor(have: Int, need: Int): Component =
        red("That costs $need credit(s) and you have $have.")

    fun noSuchEntry(id: String): Component = red("There is no '$id' in this server's tables.")

    /**
     * The one refusal that is the player's own missing argument rather than anything broken, so it names
     * the range and does not charge. See [RewardTargeting] for why a slot is required rather than
     * defaulted to the lead.
     */
    fun needsSlot(reason: String): Component =
        red("$reason — add the slot number, e.g. `1` for your lead.")

    fun midBattle(): Component =
        red("Not during a battle. Finish the wave first.")

    fun noRun(): Component = red("You have no run in progress. /roguelite start")

    fun noShopTable(): Component =
        red("No shop table is loaded on this server, so there is nothing to buy.")

    fun noRewardTable(): Component =
        red("No reward table is loaded on this server, so there is nothing on offer.")

    private fun grey(text: String) = Component.literal(text).withStyle(ChatFormatting.GRAY)

    private fun red(text: String) = Component.literal(text).withStyle(ChatFormatting.RED)
}
