package com.cobblemonserver.npc.data

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import java.util.UUID

data class NpcPokemon(
    val species: String,
    var level: Int,
    val poolTag: String,
    var heldItem: String? = null
) {
    companion object {
        val CODEC: Codec<NpcPokemon> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("species").forGetter { it.species },
                Codec.INT.fieldOf("level").forGetter { it.level },
                Codec.STRING.fieldOf("poolTag").forGetter { it.poolTag },
                Codec.STRING.optionalFieldOf("heldItem").forGetter { Optional.ofNullable(it.heldItem) }
            ).apply(instance) { species, level, poolTag, heldItem ->
                NpcPokemon(species, level, poolTag, heldItem.orElse(null))
            }
        }
    }
}

data class PlayerRecord(
    var wins: Int = 0,
    var losses: Int = 0,
    var firstDefeatedAt: Long? = null,
    var lastBattledAt: Long = 0L
) {
    companion object {
        val CODEC: Codec<PlayerRecord> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("wins").forGetter { it.wins },
                Codec.INT.fieldOf("losses").forGetter { it.losses },
                Codec.LONG.optionalFieldOf("firstDefeatedAt").forGetter { Optional.ofNullable(it.firstDefeatedAt) },
                Codec.LONG.fieldOf("lastBattledAt").forGetter { it.lastBattledAt }
            ).apply(instance) { wins, losses, firstDefeatedAt, lastBattledAt ->
                PlayerRecord(wins, losses, firstDefeatedAt.orElse(null), lastBattledAt)
            }
        }
    }
}

private data class PlayerRecordEntry(val playerUuid: String, val record: PlayerRecord) {
    companion object {
        val CODEC: Codec<PlayerRecordEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("playerUuid").forGetter { it.playerUuid },
                PlayerRecord.CODEC.fieldOf("record").forGetter { it.record }
            ).apply(instance) { uuid, record -> PlayerRecordEntry(uuid, record) }
        }
    }
}

data class NpcTeamData(
    val team: MutableList<NpcPokemon> = mutableListOf(),
    var battleCount: Int = 0,
    var lossCount: Int = 0,
    var currentTier: Int = 1,
    var gymLeaderTheme: String? = null,
    var gymHirerUuid: UUID? = null,
    var originalName: String? = null,
    var signatureSpecies: String? = null,
    val playerRecords: MutableMap<UUID, PlayerRecord> = mutableMapOf()
) {
    companion object {
        val CODEC: Codec<NpcTeamData> = RecordCodecBuilder.create { instance ->
            instance.group(
                NpcPokemon.CODEC.listOf().fieldOf("team").forGetter { it.team },
                Codec.INT.fieldOf("battleCount").forGetter { it.battleCount },
                Codec.INT.fieldOf("lossCount").forGetter { it.lossCount },
                Codec.INT.fieldOf("currentTier").forGetter { it.currentTier },
                Codec.STRING.optionalFieldOf("gymLeaderTheme").forGetter { Optional.ofNullable(it.gymLeaderTheme) },
                Codec.STRING.optionalFieldOf("gymHirerUuid").forGetter { Optional.ofNullable(it.gymHirerUuid?.toString()) },
                Codec.STRING.optionalFieldOf("originalName").forGetter { Optional.ofNullable(it.originalName) },
                Codec.STRING.optionalFieldOf("signatureSpecies").forGetter { Optional.ofNullable(it.signatureSpecies) },
                PlayerRecordEntry.CODEC.listOf().optionalFieldOf("playerRecords").forGetter {
                    Optional.of(it.playerRecords.map { (uuid, record) -> PlayerRecordEntry(uuid.toString(), record) })
                }
            ).apply(instance) { team, battleCount, lossCount, currentTier, gymLeaderTheme, gymHirerUuid, originalName, signatureSpecies, playerRecords ->
                NpcTeamData(
                    team.toMutableList(),
                    battleCount,
                    lossCount,
                    currentTier,
                    gymLeaderTheme.orElse(null),
                    gymHirerUuid.map { UUID.fromString(it) }.orElse(null),
                    originalName.orElse(null),
                    signatureSpecies.orElse(null),
                    playerRecords
                        .orElse(emptyList())
                        .associate { UUID.fromString(it.playerUuid) to it.record }
                        .toMutableMap()
                )
            }
        }
    }
}
