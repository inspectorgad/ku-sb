package com.example.stats

import com.example.data.OpponentStatLine

/**
 * One opposing player's totals across the games in hand.
 *
 * Only ever the games they played against Kansas — those are the only box
 * scores this app holds — so a series is two or three games and the rates are
 * quoted beside the counting line that produced them rather than on their own.
 */
data class OpponentPlayerTotals(
    val name: String,
    val jerseyNumber: String,
    val position: String,
    val games: Int,
    val atBats: Int,
    val runs: Int,
    val hits: Int,
    val doubles: Int,
    val triples: Int,
    val homeRuns: Int,
    val runsBattedIn: Int,
    val walks: Int,
    val strikeouts: Int,
    val putouts: Int,
    val assists: Int,
    val errors: Int,
    val pitchingAppearances: Int,
    val outsPitched: Int,
    val hitsAllowed: Int,
    val runsAllowed: Int,
    val earnedRuns: Int,
    val walksAllowed: Int,
    val pitcherStrikeouts: Int,
    val pitchCount: Int,
    val battersFaced: Int,
    val wins: Int,
    val losses: Int,
    val saves: Int
) {
    val battingAverage: Double get() = if (atBats == 0) 0.0 else hits.toDouble() / atBats
    val earnedRunAverage: Double
        get() = if (outsPitched == 0) 0.0 else earnedRuns * 21.0 / outsPitched
}

/**
 * Opposing players summed over [lines], best hitters first.
 *
 * Grouped by name because that is all there is to group by: these players are
 * not in the roster table and have no id here. A name collision across two
 * different opponents is possible in principle, so callers pass the lines for
 * one opponent — or one game — rather than the whole season.
 */
fun opponentPlayers(lines: List<OpponentStatLine>): List<OpponentPlayerTotals> =
    lines
        .groupBy { it.playerName }
        .map { (name, rows) ->
            OpponentPlayerTotals(
                name = name,
                // The first non-blank wins: a number or position can be missing
                // from one game's box score and present in the next.
                jerseyNumber = rows.firstOrNull { it.jerseyNumber.isNotBlank() }?.jerseyNumber ?: "",
                position = rows.firstOrNull { it.position.isNotBlank() }?.position ?: "",
                games = rows.size,
                atBats = rows.sumOf { it.atBats },
                runs = rows.sumOf { it.runs },
                hits = rows.sumOf { it.hits },
                doubles = rows.sumOf { it.doubles },
                triples = rows.sumOf { it.triples },
                homeRuns = rows.sumOf { it.homeRuns },
                runsBattedIn = rows.sumOf { it.runsBattedIn },
                walks = rows.sumOf { it.walks },
                strikeouts = rows.sumOf { it.strikeouts },
                putouts = rows.sumOf { it.putouts },
                assists = rows.sumOf { it.assists },
                errors = rows.sumOf { it.errors },
                pitchingAppearances = rows.count { it.pitched },
                outsPitched = rows.sumOf { it.outsPitched },
                hitsAllowed = rows.sumOf { it.hitsAllowed },
                runsAllowed = rows.sumOf { it.runsAllowed },
                earnedRuns = rows.sumOf { it.earnedRuns },
                walksAllowed = rows.sumOf { it.walksAllowed },
                pitcherStrikeouts = rows.sumOf { it.pitcherStrikeouts },
                pitchCount = rows.sumOf { it.pitchCount },
                battersFaced = rows.sumOf { it.battersFaced },
                wins = rows.count { it.win },
                losses = rows.count { it.loss },
                saves = rows.count { it.save }
            )
        }
        .sortedWith(
            compareByDescending<OpponentPlayerTotals> { it.hits }
                .thenByDescending { it.atBats }
                .thenBy { it.name }
        )

/** Those who came to the plate, in the order [opponentPlayers] returned. */
fun opponentBatters(totals: List<OpponentPlayerTotals>): List<OpponentPlayerTotals> =
    totals.filter { it.atBats > 0 || it.walks > 0 || it.runs > 0 }

/** Those who pitched, most work first. */
fun opponentPitchers(totals: List<OpponentPlayerTotals>): List<OpponentPlayerTotals> =
    totals.filter { it.pitchingAppearances > 0 }.sortedByDescending { it.outsPitched }
