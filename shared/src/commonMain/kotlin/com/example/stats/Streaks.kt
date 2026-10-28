package com.example.stats

import com.example.data.Game

/**
 * Win and loss runs.
 *
 * Ordering matters more here than in a sport without doubleheaders: two games
 * share a date several times a season, so sorting by date alone leaves their
 * order to chance and can invert a streak. Games are ordered by date, then by
 * scheduled first pitch, then by insertion order — which is the order the
 * seed writes a doubleheader's halves.
 */
private fun chronological(games: Collection<Game>): List<Game> =
    games.filter { it.teamScore != null && it.opponentScore != null }
        .sortedWith(compareBy({ it.date }, { it.startTime }, { it.id }))

private fun won(g: Game) = g.teamScore!! > g.opponentScore!!

/**
 * The run of results ending with the most recent game, as a signed count:
 * +3 for three straight wins, -2 for two straight losses, 0 when nothing has
 * been played. Ties are impossible in softball, so there is no third case.
 */
fun currentStreak(games: Collection<Game>): Int {
    val played = chronological(games)
    if (played.isEmpty()) return 0
    val latestWon = won(played.last())
    var run = 0
    for (g in played.asReversed()) {
        if (won(g) != latestWon) break
        run++
    }
    return if (latestWon) run else -run
}

/** The longest run of wins (or of losses) anywhere in [games]. */
fun longestStreak(games: Collection<Game>, wins: Boolean): Int {
    var best = 0
    var run = 0
    for (g in chronological(games)) {
        if (won(g) == wins) {
            run++
            if (run > best) best = run
        } else {
            run = 0
        }
    }
    return best
}

/**
 * "Won 5 straight" / "Lost 2 straight", or null for a single game either way —
 * one result is not a streak and saying so adds nothing.
 */
fun streakPhrase(streak: Int): String? = when {
    streak >= 2 -> "Won $streak straight"
    streak <= -2 -> "Lost ${-streak} straight"
    else -> null
}
