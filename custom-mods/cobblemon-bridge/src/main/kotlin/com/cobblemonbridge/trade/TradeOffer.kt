package com.cobblemonbridge.trade

import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID

/**
 * One side's contribution to an active trade. Mutable — both this object and the shared
 * container ([TradeSession.container]) are updated live as the player interacts with the GUI,
 * and both sides see the changes via Minecraft's standard container-sync.
 *
 *  - [pokemon] holds the actual `Pokemon` instances currently staged from the player's party.
 *    They aren't moved out of the party at this stage — the party is the source of truth.
 *    On execute we read the offered list, locate each Pokemon by UUID in the sender's party,
 *    and re-validate it's still there (party can change mid-trade; we abort if it has).
 *  - [money] is a non-escrowed intent. Money isn't withdrawn at +money time; we validate at
 *    execute time that the sender still has it (and abort if they spent it elsewhere).
 *  - Items aren't tracked here — they live in the shared [TradeSession.container] slots
 *    directly (vanilla drag-and-drop escrow). Per-slot ownership is enforced in [TradeMenu]'s
 *    click handler.
 *
 * [confirmed] flips when the player clicks their confirm slot. ANY change to the offer
 * (pokemon staged/unstaged, money changed, item added/removed) un-confirms both sides — both
 * players have to re-confirm to commit. This matches canon Pokémon trading.
 */
class TradeOffer {
    val pokemon: MutableList<Pokemon> = mutableListOf()
    var money: Int = 0
    var confirmed: Boolean = false

    /** True iff this side has staged anything (pokemon, money > 0, or has items in container).
     *  Note: item-presence check happens in [TradeSession] since the container is there. */
    val hasPokemon: Boolean get() = pokemon.isNotEmpty()
    val hasMoney: Boolean get() = money > 0

    fun pokemonUuids(): List<UUID> = pokemon.map { it.uuid }
}
