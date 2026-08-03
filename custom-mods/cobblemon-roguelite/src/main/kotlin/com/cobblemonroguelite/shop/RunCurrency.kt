package com.cobblemonroguelite.shop

/**
 * How run money is written, in one place.
 *
 * ### Why a symbol and not a word
 *
 * It was "credits", which is a placeholder word that reads like a placeholder — a player seeing
 * "40 credit(s)" is being told the mode is unfinished. The Pokémon games have a currency and it has a
 * glyph, so the glyph is what gets used: `₽700` says "this is money in a Pokémon game" without any
 * word at all, and without inventing a name that would have to be translated.
 *
 * It stays a **placeholder in substance**: this is still the run-local currency §2.35 describes, spent
 * only inside a run and gone when it ends, with no connection to the server economy. Only the label
 * changed. If it ever becomes real money, this is the one function that has to notice.
 *
 * ### The glyph
 *
 * U+20BD, which Minecraft renders out of its bundled unifont fallback — so it needs no resource pack
 * and no client mod, the same constraint every other string in this mod is written under. It is not
 * the yen sign and not a plain P: those are a different currency and a letter respectively, and the
 * whole point is to be recognisably the one from the games.
 */
object RunCurrency {

    const val SYMBOL = "₽"

    /** `₽700`. No space, no thousands separator — matches how the games print it. */
    fun format(amount: Int): String = "$SYMBOL$amount"

    /** `₽700` with a colour code in front, for lore and chat that is already styling. */
    fun format(amount: Int, colour: String): String = "$colour$SYMBOL$amount"
}
