package com.example.stats

import com.example.data.StatLine

/**
 * The Kansas player whose line stood out most in one game.
 *
 * Ranked on total bases first, then runs batted in — the same ordering the
 * dashboard's "Top Jayhawk" column uses, so the two never disagree about who
 * had the day. A player with no hit is never the pick, however many runners
 * she drove in on outs.
 */
data class TopLine(
    val playerId: Long,
    val hits: Int,
    val atBats: Int,
    val homeRuns: Int,
    val runsBattedIn: Int
) {
    /** "3-for-4, HR, 2 RBI" — what a recap would say. */
    val summary: String
        get() = buildString {
            append("$hits-for-$atBats")
            if (homeRuns > 0) append(if (homeRuns == 1) ", HR" else ", $homeRuns HR")
            if (runsBattedIn > 0) append(", $runsBattedIn RBI")
        }
}

fun topPerformer(lines: List<StatLine>): TopLine? =
    lines
        .filter { it.hits > 0 }
        .maxWithOrNull(
            compareBy(
                { it.hits + it.doubles + 2 * it.triples + 3 * it.homeRuns },
                { it.runsBattedIn }
            )
        )
        ?.let {
            TopLine(
                playerId = it.playerId,
                hits = it.hits,
                atBats = it.atBats,
                homeRuns = it.homeRuns,
                runsBattedIn = it.runsBattedIn
            )
        }
