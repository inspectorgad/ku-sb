package com.example.stats

import com.example.data.Game

/**
 * How a game ended, where that is not simply "after seven innings".
 *
 * Softball stops a game when a team leads by eight after five innings, and a
 * third of the 2026 season ended that way — 19 of 57. The app showed every one
 * of them as an ordinary game, because without the scheduled length there is
 * no way to tell a run-rule win from a game called for weather: both are five
 * innings with a winner.
 */
enum class GameEnding { REGULATION, EXTRAS, RUN_RULE, SHORTENED }

/** Innings in a line score ("0-1, 2-0, 1-0" is three), 0 when there is none. */
fun inningsPlayed(game: Game): Int =
    game.inningScores?.split(",")?.count { it.isBlank().not() } ?: 0

/**
 * [GameEnding.RUN_RULE] needs both halves: short of the scheduled length, and
 * a margin that explains why. Five innings is the earliest the rule applies,
 * so a shorter game is something else however lopsided it was.
 */
fun gameEnding(game: Game): GameEnding {
    val played = inningsPlayed(game)
    val scheduled = game.scheduledInnings
    val us = game.teamScore
    val them = game.opponentScore
    if (played == 0 || scheduled == 0 || us == null || them == null) return GameEnding.REGULATION
    if (played > scheduled) return GameEnding.EXTRAS
    if (played == scheduled) return GameEnding.REGULATION
    val margin = kotlin.math.abs(us - them)
    return if (played >= 5 && margin >= 8) GameEnding.RUN_RULE else GameEnding.SHORTENED
}

/** "Run rule, 5 innings" / "8 innings" / null when it ended as scheduled. */
fun endingPhrase(game: Game): String? {
    val played = inningsPlayed(game)
    return when (gameEnding(game)) {
        GameEnding.RUN_RULE -> "Run rule · $played innings"
        GameEnding.EXTRAS -> "$played innings"
        GameEnding.SHORTENED -> "Shortened · $played innings"
        GameEnding.REGULATION -> null
    }
}
