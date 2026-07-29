package com.cobblemonroguelite.progression

import net.minecraft.resources.ResourceLocation

/**
 * The shop side of §2.15: what a player's candy is worth to them *right now*, and whether a purchase
 * they asked for should be quoted, refused, or performed.
 *
 * ### Why this is a separate file from [SpeciesProgress.buy]
 *
 * [SpeciesProgress.buy] decides one purchase. A player looking at a species is asking three
 * questions at once — passive, cost reductions, eggs — and the answers have to be the *same* answers
 * the purchase path will give a moment later, including the prices. So the view is built by
 * **dry-running the real purchase** for each kind and keeping the [SpendResult]. That is what
 * [SpeciesProgress.buy]'s purity is for ("what lets a UI quote a price without committing to it"),
 * and it means a price shown and a price charged cannot drift: there is one function that decides,
 * and the view is its output with nothing stored.
 *
 * The alternative — re-deriving prices here from [CandyPrices] — would work today and would be a
 * second copy of the rules that decides "sold out" and "already owned" for itself. The first time
 * the two disagree, the player sees a price and gets a refusal, which is the failure this module
 * has spent [SpendResult]'s four cases avoiding.
 *
 * ### Nothing here touches a server, a player or a Pokémon
 *
 * Same split [PlayerProgression] makes and for the same reason: this is the part worth testing and
 * none of it needs a booted game. [CandyCommands] does the impure half — resolving a species,
 * finding its §2.13 cost, and writing through [ProgressionStore].
 */
object CandyLedger {

    /**
     * Whether anything in this build can actually hand a player an egg. **It cannot.**
     *
     * [CandyPrices.eggCandy] is null by default, so an egg is normally refused as
     * [SpendResult.NotPriced] and never reaches this flag. The flag exists for the case where an
     * operator, reasonably, sets a price: [SpeciesProgress.buy] would then succeed and deduct the
     * candy, and *nothing would happen* — the record deliberately keeps no egg count ("what an egg is
     * belongs to whatever grants it") and this module has no such grantor. That is the one failure
     * worse than refusing: candy destroyed for nothing, with no way to give it back.
     *
     * So the refusal is forced here rather than left to the price being absent, and it is expressed as
     * [SpendResult.NotPriced] because that is what it honestly is — nobody has decided what an egg is
     * yet, let alone what it costs. Flip this to `true` in the same change that adds a grantor, not
     * before.
     */
    const val EGGS_GRANTABLE = false

    /**
     * Whether an unlocked passive does anything inside a run yet. **It does not** — nothing outside
     * this package reads [SpeciesProgress.passiveUnlocked].
     *
     * Unlike an egg this does *not* make the purchase a candy sink, which is why it is a wording flag
     * rather than a refusal: the unlock is durable, per-species and permanent, so a passive bought
     * today is a passive the player owns on the day the ability is wired up. Refusing the sale would
     * cost them nothing and gain them nothing.
     *
     * What would be dishonest is selling it as a working ability. A player who spends forty candy and
     * sees no difference in their next run has bought the thing this whole module is written to avoid
     * — an action that appears to do nothing — so [CandyMessages] says plainly that it is recorded and
     * not yet in effect. Flip this in the change that reads the flag in a run, not before.
     */
    const val PASSIVES_ACTIVE = false

    /** Display order. Passive first because it is the purchase players save toward. */
    val PURCHASE_ORDER: List<CandyPurchase> =
        listOf(CandyPurchase.PASSIVE, CandyPurchase.COST_REDUCTION, CandyPurchase.EGG)

    /**
     * What [purchase] would do to [progress] if it were attempted now, without attempting it.
     *
     * Every caller — the view, the confirmation prompt and the purchase itself — goes through this,
     * so "what the player was told" and "what the store was asked" are the same decision.
     */
    fun quote(
        progress: SpeciesProgress,
        purchase: CandyPurchase,
        starterCost: Int = SpeciesProgress.UNKNOWN_STARTER_COST,
        prices: CandyPrices = ProgressionSettings.prices,
        eggsGrantable: Boolean = EGGS_GRANTABLE,
    ): SpendResult {
        // Before the price is consulted, deliberately: a priced egg on a build with no grantor must
        // refuse, not succeed. See [EGGS_GRANTABLE].
        if (purchase == CandyPurchase.EGG && !eggsGrantable) return SpendResult.NotPriced
        return progress.buy(purchase, starterCost, prices)
    }

    /** One purchase kind, quoted, with the counters the display needs beside it. */
    fun offer(
        progress: SpeciesProgress,
        purchase: CandyPurchase,
        starterCost: Int = SpeciesProgress.UNKNOWN_STARTER_COST,
        prices: CandyPrices = ProgressionSettings.prices,
        eggsGrantable: Boolean = EGGS_GRANTABLE,
    ): CandyOffer = CandyOffer(
        purchase = purchase,
        quote = quote(progress, purchase, starterCost, prices, eggsGrantable),
        owned = if (purchase == CandyPurchase.COST_REDUCTION) progress.costReductions else 0,
        // The price list's length *is* the cap (see [CandyPrices.costReductionCandy]), so the
        // "3 of 3 bought" the player reads and the [SpendResult.SoldOut] they hit are the same number.
        cap = if (purchase == CandyPurchase.COST_REDUCTION) prices.maxCostReductions else 0,
    )

    /**
     * Everything one player's candy for one species can currently buy.
     *
     * [requested] and [credited] are both carried because they are routinely different and the
     * difference is the single most confusing thing about §2.17's candy: a player who asks about
     * Charizard is shown Charmander's ledger, because that is where every Charizard they ever caught
     * paid in. Showing only the credited species would look like their candy went missing; showing
     * only the requested one would be a lie about which record is being spent.
     *
     * @param starterCost the species' **base** §2.13 cost, or [SpeciesProgress.UNKNOWN_STARTER_COST].
     *   Base and not the reduced one: [CandyPrices.passiveCandyByCost] prices a passive by how strong
     *   the species inherently is, and keying it off the reduced cost would make a passive get cheaper
     *   every time the player bought a reduction — a moving price on a purchase they are saving for.
     */
    fun view(
        requested: ResourceLocation,
        credited: ResourceLocation,
        progress: SpeciesProgress,
        starterCost: Int = SpeciesProgress.UNKNOWN_STARTER_COST,
        prices: CandyPrices = ProgressionSettings.prices,
        eggsGrantable: Boolean = EGGS_GRANTABLE,
    ): CandyLedgerView = CandyLedgerView(
        requested = requested,
        credited = credited,
        progress = progress,
        starterCost = starterCost,
        effectiveStarterCost = starterCost
            .takeIf { it != SpeciesProgress.UNKNOWN_STARTER_COST }
            ?.let { prices.effectiveStarterCost(it, progress.costReductions) },
        // What the *next* reduction would leave it at. Carried rather than left to the display to
        // subtract [CandyPrices.costReductionAmount] itself, because the subtraction is clamped at
        // [CandyPrices.minimumStarterCost] — a display doing its own arithmetic would promise a
        // discount that the floor is about to swallow, which is a purchase that visibly buys nothing.
        nextStarterCost = starterCost
            .takeIf { it != SpeciesProgress.UNKNOWN_STARTER_COST }
            ?.let { prices.effectiveStarterCost(it, progress.costReductions + 1) },
        floorStarterCost = prices.minimumStarterCost,
        offers = PURCHASE_ORDER.map { offer(progress, it, starterCost, prices, eggsGrantable) },
    )

    /**
     * What a `buy` command should do, given the offer and whether the player typed `confirm`.
     *
     * ### Why a refusal is decided before the confirmation, not after
     *
     * A player who cannot afford a passive is told so on the *bare* command. Asking them to confirm a
     * purchase that is going to be refused teaches them that `confirm` is a formality, which is
     * exactly the habit the confirmation exists to prevent — and it puts the only useful sentence
     * ("you are 12 short") behind a second command they have no reason to type.
     *
     * ### Why `confirm` still re-quotes at the store
     *
     * [Commit] carries the price the player was shown, but the store is asked again and its answer is
     * what the player is told. Candy can change between the two commands (a friendship threshold
     * lands, another purchase goes through), and the store's decision is the one that actually moved
     * the balance. This is a quote, not a reservation.
     */
    fun plan(offer: CandyOffer, confirmed: Boolean): CandyPurchasePlan {
        val quote = offer.quote
        if (quote !is SpendResult.Ok) return CandyPurchasePlan.Refuse(offer, quote)
        return if (confirmed) {
            CandyPurchasePlan.Commit(offer, quote.spent)
        } else {
            CandyPurchasePlan.Confirm(offer, quote.spent)
        }
    }

    /**
     * The player's whole candy ledger, for the bare command.
     *
     * Rows with nothing candy-related are dropped: a species can have a record because §2.17 raised
     * its IV floor and nothing else, and listing it under "your candy" as a zero would pad the list
     * with entries the command cannot act on. Sorted by balance so the species nearest a purchase is
     * read first, then by id so two equal balances always come out in the same order.
     */
    fun summary(all: Map<ResourceLocation, SpeciesProgress>): List<CandyLedgerRow> = all
        .filter { (_, progress) -> progress.candy > 0 || progress.passiveUnlocked || progress.costReductions > 0 }
        .map { (species, progress) -> CandyLedgerRow(species, progress) }
        .sortedWith(compareByDescending<CandyLedgerRow> { it.progress.candy }.thenBy { it.species.toString() })
}

/**
 * One purchase kind as it stands for one player and one species.
 *
 * @property quote the dry-run [SpendResult]. Carried whole rather than flattened to a price and a
 *   boolean, because the four refusals need four different sentences and a boolean would collapse
 *   them — the exact thing [SpendResult] was split up to prevent.
 * @property owned how many of this purchase are already held. Only meaningful for cost reductions.
 * @property cap how many may ever be held, i.e. [CandyPrices.maxCostReductions]. Zero elsewhere.
 */
data class CandyOffer(
    val purchase: CandyPurchase,
    val quote: SpendResult,
    val owned: Int = 0,
    val cap: Int = 0,
) {

    /**
     * What it costs, whether or not the player can afford it — [SpendResult.Ok] carries the price it
     * would charge and [SpendResult.NotEnoughCandy] the price it could not. Null for the two refusals
     * that have no price at all, which is a different statement from "it costs nothing".
     */
    val price: Int? get() = when (quote) {
        is SpendResult.Ok -> quote.spent
        is SpendResult.NotEnoughCandy -> quote.need
        else -> null
    }

    val affordable: Boolean get() = quote is SpendResult.Ok
}

/** One species' balance in the ledger listing. */
data class CandyLedgerRow(val species: ResourceLocation, val progress: SpeciesProgress)

/**
 * Everything the candy view says about one species.
 *
 * @property requested the species the player asked about.
 * @property credited the species the candy is actually banked under — §2.17's evolution-line root.
 *   Equal to [requested] for a first-stage species and different for every other stage.
 * @property starterCost the base §2.13 cost, or [SpeciesProgress.UNKNOWN_STARTER_COST] when this
 *   server has no price for it. Unknown is normal rather than broken: prices fall back to the flat
 *   ones, which is what [SpeciesProgress.UNKNOWN_STARTER_COST] documents.
 * @property effectiveStarterCost what the species costs to start with after the reductions bought for
 *   it, or null when [starterCost] is unknown — the whole visible point of a cost reduction, and the
 *   only number that shows a player what they got for their candy.
 * @property nextStarterCost what one more reduction would leave it at, already clamped. Equal to
 *   [effectiveStarterCost] when the floor has been reached, which is how the display knows a
 *   reduction that is still for sale would nonetheless buy nothing.
 * @property floorStarterCost [CandyPrices.minimumStarterCost], so the display can name the floor.
 */
data class CandyLedgerView(
    val requested: ResourceLocation,
    val credited: ResourceLocation,
    val progress: SpeciesProgress,
    val starterCost: Int,
    val effectiveStarterCost: Int?,
    val nextStarterCost: Int?,
    val floorStarterCost: Int,
    val offers: List<CandyOffer>,
) {

    val candy: Int get() = progress.candy

    /** True when the player asked about a species that is not where their candy lives. */
    val redirected: Boolean get() = requested != credited

    fun offer(purchase: CandyPurchase): CandyOffer =
        offers.first { it.purchase == purchase }
}

/**
 * Turning what a player typed into a species id this server actually has.
 *
 * ### Why the namespace fallback exists
 *
 * Brigadier's `ResourceLocationArgument` fills in `minecraft:` for a bare word, so a player who types
 * `charmander` hands us `minecraft:charmander` — which does not exist, and would be reported as an
 * unknown Pokémon on a server where Charmander plainly exists. That is a wrong answer, not a strict
 * one. So a bare word is retried under `cobblemon:` before it is refused.
 *
 * The retry is *only* for the namespace the parser invented. An id the player namespaced themselves
 * is taken at their word and refused if it is wrong, because `someaddon:charmander` and
 * `cobblemon:charmander` are different Pokémon with different ledgers, and quietly substituting one
 * for the other would spend candy on the wrong species.
 *
 * Pure, with the existence check passed in, because [com.cobblemon.mod.common.api.pokemon.PokemonSpecies]
 * needs a booted server and this decision does not.
 */
object CandySpeciesArgument {

    /** What Brigadier fills in when the player omits a namespace. */
    const val PARSER_DEFAULT_NAMESPACE = "minecraft"

    /** Where Cobblemon's own species live, and the only namespace a bare word is retried under. */
    const val FALLBACK_NAMESPACE = "cobblemon"

    fun resolve(typed: ResourceLocation, exists: (ResourceLocation) -> Boolean): ResourceLocation? {
        if (exists(typed)) return typed
        if (typed.namespace != PARSER_DEFAULT_NAMESPACE) return null
        val fallback = runCatching { ResourceLocation.fromNamespaceAndPath(FALLBACK_NAMESPACE, typed.path) }
            .getOrNull() ?: return null
        return fallback.takeIf(exists)
    }
}

/** What a `buy` command should do. See [CandyLedger.plan]. */
sealed interface CandyPurchasePlan {

    val offer: CandyOffer

    /** Bare form: nothing has happened, name the price and the command that acts. */
    data class Confirm(override val offer: CandyOffer, val price: Int) : CandyPurchasePlan

    /** The store would refuse. [refusal] is never [SpendResult.Ok]. */
    data class Refuse(override val offer: CandyOffer, val refusal: SpendResult) : CandyPurchasePlan

    /** `confirm` typed against a good quote. [quotedPrice] is what the player was shown. */
    data class Commit(override val offer: CandyOffer, val quotedPrice: Int) : CandyPurchasePlan
}
