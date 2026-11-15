package com.example.stats

import com.example.data.Game

/**
 * Runs scored and allowed in one inning across a season, with how many games
 * actually reached it.
 *
 * [gamesBatted] is not decoration. KU batted in the seventh in 27 of 57 games
 * in 2026 — the home team does not bat in the last inning of a game it is
 * already winning, and a run-rule game simply ends. Without that count, the
 * seventh looks like a collapse (10 scored, 26 allowed) rather than a third of
 * the plate appearances everyone else got.
 */
data class InningSplit(
    val inning: Int,
    val scored: Int,
    val allowed: Int,
    val gamesBatted: Int,
    val gamesPitched: Int
)

/**
 * Per-inning run totals, read off each game's line score ("0-1, 2-0, …", one
 * "us-them" pair per inning from KU's perspective).
 *
 * An "x" in either half means that side never came to bat, which is a
 * different thing from being held scoreless and must not be counted as a zero.
 * Extra innings simply extend the list, so a 2026 season reaches the eighth.
 */
fun inningSplits(games: List<Game>): List<InningSplit> {
    val scored = mutableMapOf<Int, Int>()
    val allowed = mutableMapOf<Int, Int>()
    val batted = mutableMapOf<Int, Int>()
    val pitched = mutableMapOf<Int, Int>()

    for (game in games) {
        val innings = game.inningScores?.split(",").orEmpty()
        innings.forEachIndexed { index, raw ->
            val part = raw.trim()
            if (part.isEmpty()) return@forEachIndexed
            val halves = part.split("-")
            if (halves.size != 2) return@forEachIndexed
            val inning = index + 1
            halves[0].trim().toIntOrNull()?.let {
                scored[inning] = (scored[inning] ?: 0) + it
                batted[inning] = (batted[inning] ?: 0) + 1
            }
            halves[1].trim().toIntOrNull()?.let {
                allowed[inning] = (allowed[inning] ?: 0) + it
                pitched[inning] = (pitched[inning] ?: 0) + 1
            }
        }
    }

    val innings = (scored.keys + allowed.keys).sorted()
    return innings.map {
        InningSplit(
            inning = it,
            scored = scored[it] ?: 0,
            allowed = allowed[it] ?: 0,
            gamesBatted = batted[it] ?: 0,
            gamesPitched = pitched[it] ?: 0
        )
    }
}
