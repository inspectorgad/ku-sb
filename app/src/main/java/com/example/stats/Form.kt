package com.example.stats

import com.example.data.Game
import com.example.data.StatLine

/**
 * How a player has been going lately, over their most recent appearances.
 *
 * Windows count the player's own appearances, not the team's games. A reserve
 * who has played four times in the last month is "last 5 games" by her own
 * reckoning, and measuring her against the team's calendar would hand back an
 * almost empty window.
 */
data class FormWindow(
    val label: String,
    val games: Int,
    val batting: BattingTotals,
    val pitching: PitchingTotals,
    /** Whether there is enough volume for the rate stats to mean anything. */
    val battingQualified: Boolean,
    val pitchingQualified: Boolean
)

// The same shape of gate the Leaders board uses: 2 at-bats per game for a
// batting rate, an inning per game for a pitching one. Below that, an average
// is one swing wide and says more about luck than about form.
private const val MIN_AB_PER_GAME = 2
private const val MIN_OUTS_PER_GAME = 3

/**
 * A player's recent form in one season, one entry per requested window plus
 * the season itself to read them against — a .300 window means one thing for a
 * .250 hitter and the opposite for a .350 one.
 *
 * Windows larger than the player's appearance count are dropped rather than
 * silently returning the whole season twice under two different labels.
 */
fun playerForm(
    playerId: Long,
    games: List<Game>,
    lines: List<StatLine>,
    season: String,
    windows: List<Int> = listOf(5, 10)
): List<FormWindow> {
    val seasonGames = games
        .filter { it.season == season && it.teamScore != null && it.opponentScore != null }
        .associateBy { it.id }
    // Oldest first, so the window is the tail. Doubleheaders share a date, so
    // first pitch and then insertion order break the tie — the same ordering
    // the streak code uses, for the same reason.
    val appearances = lines
        .filter { it.playerId == playerId && it.gameId in seasonGames }
        .sortedWith(
            compareBy(
                { seasonGames.getValue(it.gameId).date },
                { seasonGames.getValue(it.gameId).startTime },
                { it.id }
            )
        )
    if (appearances.isEmpty()) return emptyList()

    val out = mutableListOf<FormWindow>()
    for (size in windows.sorted()) {
        if (appearances.size <= size) continue
        out += window("Last $size", appearances.takeLast(size))
    }
    out += window("Season", appearances)
    return out
}

private fun window(label: String, lines: List<StatLine>): FormWindow {
    val batting = aggregateBatting(lines)
    val pitching = aggregatePitching(lines)
    return FormWindow(
        label = label,
        games = lines.size,
        batting = batting,
        pitching = pitching,
        battingQualified = batting.atBats >= lines.size * MIN_AB_PER_GAME,
        // Gated on the appearances where she actually pitched, not on every
        // game she appeared in — a starter who also plays the field would
        // otherwise be held to an inning per game she spent at shortstop.
        pitchingQualified = pitching.appearances > 0 &&
            pitching.outsPitched >= pitching.appearances * MIN_OUTS_PER_GAME
    )
}

/**
 * The gap between a window and the season behind it, as a signed difference —
 * +.045 for a hot stretch. Null when either side is too thin to compare, which
 * is the honest answer rather than a large meaningless number.
 */
fun formDelta(window: FormWindow, season: FormWindow): Double? {
    if (!window.battingQualified || !season.battingQualified) return null
    return window.batting.battingAverage - season.batting.battingAverage
}
