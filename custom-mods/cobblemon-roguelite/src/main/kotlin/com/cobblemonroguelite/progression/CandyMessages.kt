package com.cobblemonroguelite.progression

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

/**
 * Everything the candy shop says to a player.
 *
 * Its own file rather than lines in `RunMessages` for the reason `RunMessages` gives for existing at
 * all — the wording is shared by two paths (the view and the purchase both have to explain the same
 * four refusals, in the same words) — plus one that is specific to candy: **the refusals are the
 * feature**. [SpendResult] has four cases because they need four different sentences, and the moment
 * two of them render to the same string the split upstream has bought nothing. So [refusal] is one
 * `when` over the whole sealed type: adding a case without wording it fails to compile.
 *
 * Plain English [Component.literal], same as `RunMessages` and for the same reason — this module
 * ships no language file, and a half-translated mod is worse than an untranslated one.
 */
object CandyMessages {

    /**
     * The word a player types for each purchase, and the word this file prints.
     *
     * One function, used by both [CandyCommands] to build the literal and by every message that tells
     * a player what to type. A message naming a command that does not parse is worse than no message:
     * the player concludes the feature is broken, and they are not wrong.
     */
    fun word(purchase: CandyPurchase): String = when (purchase) {
        CandyPurchase.HIDDEN_ABILITY -> "hiddenability"
        CandyPurchase.COST_REDUCTION -> "reduction"
        CandyPurchase.EGG -> "egg"
    }

    /**
     * The sentence a purchase needs when what it buys is recorded but not yet wired up.
     *
     * **Empty today**, because §2.27 wired the unlock up: a bought hidden ability is granted when the
     * starter is built. It was not always, and the caveat is kept rather than deleted for the reason
     * [CandyLedger.HIDDEN_ABILITIES_GRANTED] gives — a build that un-wires the grant flips one
     * constant and every price and receipt goes back to saying so, instead of quietly selling an
     * action that appears to do nothing. That failure is the one this whole module is written to
     * avoid, and it cost a rename to get out of.
     */
    private fun caveat(purchase: CandyPurchase): String =
        if (purchase == CandyPurchase.HIDDEN_ABILITY && !CandyLedger.HIDDEN_ABILITIES_GRANTED) {
            " Note: hidden-ability unlocks are recorded permanently but do not affect runs yet."
        } else {
            ""
        }

    /**
     * What an unlock grants, appended to its offer line — " It grants speedboost." — or nothing when
     * the view does not know.
     *
     * Named for the same reason a cost reduction's effect is named: a price attached to an unnamed
     * benefit is not a decision. It matters more here, because hidden abilities are not
     * interchangeable — Speed Boost wins games and Truant loses them, which is why §2.27 made the
     * granted ability overridable at all. A player who can read which one they are buying can decide
     * whether it is worth forty candy; a player who cannot is gambling with permanent currency.
     */
    private fun grants(view: CandyLedgerView, purchase: CandyPurchase): String {
        if (purchase != CandyPurchase.HIDDEN_ABILITY) return ""
        val ability = view.hiddenAbility ?: return ""
        return " It grants $ability."
    }

    /** What the purchase is called in a sentence. */
    private fun noun(purchase: CandyPurchase): String = when (purchase) {
        CandyPurchase.HIDDEN_ABILITY -> "hidden ability"
        CandyPurchase.COST_REDUCTION -> "starter cost reduction"
        CandyPurchase.EGG -> "egg"
    }

    /**
     * Said when a species id resolves to nothing.
     *
     * Named as an unknown *species* and not as a candy problem, because it is one: a player who
     * mistyped an id would otherwise be shown a perfectly plausible ledger of zeroes and conclude
     * their candy had vanished. The same sentence covers "no such Pokémon" and "not on this server",
     * which are indistinguishable from the player's side and have the same fix.
     */
    fun unknownSpecies(species: ResourceLocation): Component = literal(
        "No Pokémon called '$species' on this server, so it has no candy. Ids look like " +
            "cobblemon:charmander — check the spelling, then /roguelite candy to see what you have.",
    )

    /** The bare command, for a player who has never earned any. */
    fun noCandy(): Component = literal(
        "You have no candy yet. Candy is earned inside runs — catching a Pokémon banks candy for the " +
            "first form of its evolution line, and so does clearing waves with one in your party. " +
            "/roguelite candy <species> prices what it will buy.",
    )

    /**
     * The ledger listing.
     *
     * Balances are listed under the species the candy is *banked* under, which is a first form and
     * frequently not the species the player caught. The header says so rather than leaving them to
     * work out why a run full of Charizards produced a line that says Charmander.
     */
    fun ledger(rows: List<CandyLedgerRow>): Component {
        val listed = rows.joinToString(", ") { row ->
            val marks = buildList {
                if (row.progress.hiddenAbilityUnlocked) add("hidden ability")
                if (row.progress.costReductions > 0) add("-${row.progress.costReductions}")
            }
            val suffix = if (marks.isEmpty()) "" else " [${marks.joinToString(" ")}]"
            "${row.species} ${row.progress.candy}$suffix"
        }
        return literal(
            "Your candy, banked under each line's first form: $listed. " +
                "/roguelite candy <species> for what it buys.",
        )
    }

    /**
     * The species view: a header, then one line per purchase.
     *
     * A list rather than one long component so the offers stay one-per-line in chat. Each offer line
     * ends in the exact command that buys it, because a shop whose prices are visible and whose till
     * is not is a shop nobody uses.
     */
    fun view(view: CandyLedgerView): List<Component> =
        listOf(header(view)) + view.offers.map { offerLine(view, it) }

    /**
     * §2.17's line-root rule, said out loud whenever it applies.
     *
     * This is the single most confusing thing about candy — a caught Charizard candies Charmander —
     * and it is invisible in the numbers. A player who asked about Charizard and was shown a total
     * without this sentence would read it as "my Charizard candy is gone", and the honest answer
     * ("it is all here, under Charmander") is one they have no way to guess.
     */
    private fun header(view: CandyLedgerView): Component {
        val cost = view.effectiveStarterCost?.let { effective ->
            val base = view.starterCost
            if (effective < base) " Starts a run at $effective point(s), down from $base." else " Starts a run at $effective point(s)."
        } ?: ""
        val head = if (view.redirected) {
            "${view.requested} banks its candy under ${view.credited} — the whole evolution line pays " +
                "into its first form, so this is where a caught ${view.requested} went. " +
                "${view.credited}: ${view.candy} candy."
        } else {
            "${view.credited}: ${view.candy} candy."
        }
        return literal(head + cost)
    }

    /**
     * One purchase, in whatever state it is in.
     *
     * The five states of [SpendResult] each get their own line, including the two that are not
     * failures at all from the player's side ("you own this", "there are none left"). A line that
     * merely omitted the unbuyable ones would leave a player who saved for an unlock staring at a
     * view that had silently stopped mentioning it.
     */
    private fun offerLine(view: CandyLedgerView, offer: CandyOffer): Component {
        val label = noun(offer.purchase).replaceFirstChar { it.uppercase() }
        val buy = "/roguelite candy ${view.requested} buy ${word(offer.purchase)}"
        val counted = if (offer.cap > 0) " (${offer.owned} of ${offer.cap} bought)" else ""
        return when (val quote = offer.quote) {
            is SpendResult.Ok -> literal(
                "$label$counted: ${quote.spent} candy — you can afford it. $buy" +
                    effectOf(view, offer) + grants(view, offer.purchase) + caveat(offer.purchase),
            ).withStyle(ChatFormatting.YELLOW)

            is SpendResult.NotEnoughCandy -> literal(
                "$label$counted: ${quote.need} candy — ${quote.need - quote.have} short." +
                    effectOf(view, offer) + grants(view, offer.purchase) + caveat(offer.purchase),
            )

            SpendResult.AlreadyOwned -> literal("$label: already unlocked.").withStyle(ChatFormatting.GREEN)

            SpendResult.SoldOut -> literal(
                "$label: all ${offer.cap} bought" +
                    (view.effectiveStarterCost?.let { ", and ${view.credited} now starts at $it point(s)" } ?: "") +
                    ". There are no more to buy.",
            ).withStyle(ChatFormatting.GREEN)

            // Eggs, always, on this build. Worded as a server that does not offer them rather than as
            // an error, because that is what it is: nobody has decided what an egg costs or what
            // hands one over, and a player reading "unpriced" would report it as a bug.
            SpendResult.NotPriced -> literal("$label: not available on this server.").withStyle(ChatFormatting.GRAY)

            // Listed rather than omitted, like every other unbuyable state: a player looking at a
            // species wants to know it has nothing to unlock, and a line that simply vanished would
            // read as a display bug and be saved toward anyway.
            SpendResult.NoHiddenAbility -> literal(
                "$label: ${view.credited} has none, so there is nothing to unlock.",
            ).withStyle(ChatFormatting.GRAY)
        }
    }

    /**
     * What a cost reduction would actually do to the budget, appended to its offer line.
     *
     * A reduction's price is meaningless without it — "50 candy" for an unnamed benefit is not a
     * decision a player can make — and the clamped case has to be said too, since a reduction that is
     * still for sale at the floor is candy spent for no change at all.
     */
    private fun effectOf(view: CandyLedgerView, offer: CandyOffer): String {
        if (offer.purchase != CandyPurchase.COST_REDUCTION) return ""
        val now = view.effectiveStarterCost ?: return ""
        val next = view.nextStarterCost ?: return ""
        return if (next < now) {
            " ${view.credited} would start at $next point(s) instead of $now."
        } else {
            " ${view.credited} already starts at the ${view.floorStarterCost}-point floor, so this " +
                "would change nothing."
        }
    }

    /**
     * The confirmation. Names the price and the balance it comes out of, per the module's rule that
     * the bare form of an irreversible command warns and the trailing `confirm` acts.
     *
     * "Cannot be undone" is stated because it genuinely cannot: there is no sell-back, and candy is
     * earned at one per catch (§2.15). A player who buys a reduction meaning to buy an unlock has
     * lost a real number of runs' worth of catching.
     */
    fun confirm(view: CandyLedgerView, plan: CandyPurchasePlan.Confirm): Component = Component.literal(
        "Buy ${view.credited}'s ${noun(plan.offer.purchase)} for ${plan.price} candy? You have " +
            "${view.candy}, leaving ${view.candy - plan.price}. Candy is not refundable and there is " +
            "no selling it back. Type /roguelite candy ${view.requested} buy " +
            "${word(plan.offer.purchase)} confirm.",
    ).withStyle(ChatFormatting.YELLOW)

    /** Bought. Names the remaining balance so the next decision needs no second command. */
    fun bought(
        credited: ResourceLocation,
        purchase: CandyPurchase,
        spent: Int,
        remaining: Int,
    ): Component {
        val what = when (purchase) {
            CandyPurchase.HIDDEN_ABILITY ->
                "$credited's hidden ability is unlocked, permanently and for every future run — every " +
                    "starter from that evolution line now begins with it." +
                    caveat(purchase)

            CandyPurchase.COST_REDUCTION ->
                "$credited costs less to start with from now on. /roguelite candy $credited shows the new price."

            CandyPurchase.EGG -> "Bought an egg for $credited."
        }
        return Component.literal("$what Spent $spent candy; $remaining left.").withStyle(ChatFormatting.GREEN)
    }

    /**
     * The store itself failed — a save-data read that threw, not a refusal.
     *
     * Deliberately does not claim the candy is safe. Whether the deduction landed before the throw is
     * unknown from here, and "nothing was taken" is the reassurance a player would act on by trying
     * again — which, if it *was* taken, spends twice. Naming the balance to check is the only honest
     * instruction.
     */
    fun purchaseFailed(view: CandyLedgerView): Component = literal(
        "That purchase could not be completed. Check /roguelite candy ${view.requested} before trying " +
            "again — your balance may or may not have changed — and tell an operator.",
    ).withStyle(ChatFormatting.RED)

    /**
     * Why a purchase did not happen — **five refusals, five sentences**.
     *
     * Collapsing any two of these is the failure [SpendResult] exists to prevent: "you are 12 short",
     * "you already own it", "there are none left", "this server does not sell it" and "this species
     * has none" call for five different actions from the player — catch more, do nothing, do nothing,
     * tell an operator, pick another species — and a shared "cannot buy that" tells them to do nothing
     * at all. The `when` is exhaustive over the sealed type so a sixth case cannot be added silently.
     *
     * [SpendResult.Ok] is worded rather than thrown for the reason `RunMessages.starterRejected` gives:
     * it is unreachable from here, and a crash would cost the player their session over a message they
     * were never meant to see.
     */
    fun refusal(view: CandyLedgerView, purchase: CandyPurchase, refusal: SpendResult): Component =
        when (refusal) {
            is SpendResult.Ok -> literal(
                "That went through. /roguelite candy ${view.requested} shows where you stand.",
            )

            is SpendResult.NotEnoughCandy -> literal(
                "${view.credited}'s ${noun(purchase)} costs ${refusal.need} candy and you have " +
                    "${refusal.have} — ${refusal.need - refusal.have} short. Catch more of the " +
                    "${view.credited} line in a run, or clear waves with one in your party.",
            ).withStyle(ChatFormatting.RED)

            SpendResult.AlreadyOwned -> literal(
                "You already own ${view.credited}'s ${noun(purchase)}, and nothing was taken. Buying " +
                    "it twice would spend candy for something you have.",
            ).withStyle(ChatFormatting.RED)

            SpendResult.SoldOut -> literal(
                "${view.credited} has all ${view.offer(purchase).cap} cost reduction(s) already" +
                    (view.effectiveStarterCost?.let { ", and starts at $it point(s)" } ?: "") +
                    ". There are no more to buy and nothing was taken.",
            ).withStyle(ChatFormatting.RED)

            // Not an error and not the player's fault. See [CandyLedger.EGGS_GRANTABLE]: eggs are
            // refused because nobody has decided what one is, not because something went wrong.
            SpendResult.NotPriced -> if (purchase == CandyPurchase.EGG) {
                literal(
                    "Eggs are not available on this server, so there is nothing to buy and your candy " +
                        "is untouched. Hidden abilities and cost reductions are: " +
                        "/roguelite candy ${view.requested}.",
                ).withStyle(ChatFormatting.GRAY)
            } else {
                literal(
                    "This server has not priced ${view.credited}'s ${noun(purchase)}, so it cannot be " +
                        "bought and nothing was taken. Tell an operator.",
                ).withStyle(ChatFormatting.RED)
            }

            // The refusal §2.27 was written to make possible. Not an error, not the operator's
            // fault, and not fixable by anyone — the species simply has no hidden ability, so there
            // is nothing an unlock could grant. Selling it anyway is the exact outcome that made the
            // old "passive" a purchase that did nothing, and the sentence says which species and what
            // to do instead rather than leaving the player to re-read the price.
            SpendResult.NoHiddenAbility -> literal(
                "${view.credited} has no hidden ability recorded on this server, so there is nothing " +
                    "to unlock and nothing was taken. Its candy still buys cost reductions: " +
                    "/roguelite candy ${view.requested}.",
            ).withStyle(ChatFormatting.GRAY)
        }

    /** Mutable so callers may style it; `RunMessages` returns a plain [Component] and styles inline. */
    private fun literal(text: String): MutableComponent = Component.literal(text)
}
