package com.example.stats

import com.example.data.Game

/** A season's head-to-head record against one opponent. */
data class OpponentRecord(
    val name: String,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val runsFor: Int,
    val runsAgainst: Int,
    /** Their best (lowest) national rank in any meeting, 0 if never ranked. */
    val bestRank: Int,
    val lastPlayed: String
) {
    // Ties are neither a win nor a loss, so [games] is counted, not derived.
    val record: String get() = "$wins-$losses"
    val runDifferential: Int get() = runsFor - runsAgainst
}

/**
 * Per-opponent records for a season, most-played first.
 *
 * Softball is played in weekend series, so this says more here than the same
 * screen would in basketball: a team is met two or three times in three days
 * and the aggregate is a real head-to-head, not a one-game sample. The two
 * halves of a doubleheader are folded back together — "Baylor (G2)" is still
 * Baylor — which is the whole reason the names are keyed rather than compared.
 */
fun opponentRecords(games: List<Game>): List<OpponentRecord> =
    games
        .filter { it.teamScore != null && it.opponentScore != null }
        .groupBy { teamKey(it.opponent) }
        .map { (_, met) ->
            OpponentRecord(
                name = opponentName(met.first().opponent),
                games = met.size,
                wins = met.count { (it.teamScore ?: 0) > (it.opponentScore ?: 0) },
                losses = met.count { (it.teamScore ?: 0) < (it.opponentScore ?: 0) },
                runsFor = met.sumOf { it.teamScore ?: 0 },
                runsAgainst = met.sumOf { it.opponentScore ?: 0 },
                // Lowest rank number is the best; 0 means unranked, so it is
                // not a candidate for "best" however many times it appears.
                bestRank = met.mapNotNull { it.opponentRank.takeIf { r -> r > 0 } }.minOrNull() ?: 0,
                lastPlayed = met.maxOf { it.date }
            )
        }
        .sortedWith(
            compareByDescending<OpponentRecord> { it.games }
                .thenByDescending { it.wins }
                .thenBy { it.name }
        )
