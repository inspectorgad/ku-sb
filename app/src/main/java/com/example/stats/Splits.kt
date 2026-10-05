package com.example.stats

import com.example.data.ConferenceStanding
import com.example.data.Game
import com.example.data.StatLine

/**
 * One slice of a season: the games matching some condition, with the record
 * and the batting and pitching lines that came out of them.
 *
 * A season average is one number and hides everything interesting. The same
 * 2026 team hit .349 with a 3.04 ERA against unranked opponents and .244 with
 * a 7.89 ERA against ranked ones, and only a split says so.
 */
data class Split(
    val label: String,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val runsFor: Int,
    val runsAgainst: Int,
    val batting: BattingTotals,
    val pitching: PitchingTotals
) {
    val record: String get() = "$wins-$losses"
    val runDifferential: Int get() = runsFor - runsAgainst
}

/**
 * Canonical key for matching a team across sources, which spell the same
 * school differently: "Iowa State" in a schedule, "Iowa St." in the standings.
 * A doubleheader's "(G2)" marker and a poll's first-place-vote count are
 * dropped, periods go, and "State" contracts to "St".
 *
 * Deliberately conservative: "Utah State" must not collapse into "Utah", which
 * is a different school and, in 2026, the difference between a conference game
 * and a non-conference one. This mirrors `norm_team` in update-seed.py; the two
 * have to agree or the app and the seed would disagree about who KU played.
 */
fun teamKey(name: String): String =
    name
        .replace(Regex("""\s*\(G\d\)\s*$"""), "")
        .replace(Regex("""\s*\(\d+\)\s*$"""), "")
        .lowercase()
        .replace(".", "")
        .replace(Regex("""\bstate\b"""), "st")
        .replace(Regex("""\s+"""), " ")
        .trim()

/** The opponent's name without a doubleheader marker: "Baylor (G2)" -> "Baylor". */
fun opponentName(name: String): String =
    name.replace(Regex("""\s*\(G\d\)\s*$"""), "").trim()

/**
 * The conference opponents for a season, from the computed standings rather
 * than a hand-kept list — so realignment arrives with the data instead of
 * needing a code change. Kansas is excluded; it is never its own opponent.
 */
fun conferenceOpponents(standings: List<ConferenceStanding>, season: String): Set<String> =
    standings
        .filter { it.season == season }
        .map { teamKey(it.team) }
        .toSet() - teamKey("Kansas")

/** Games with a final score, which are the only ones a split can say anything about. */
fun playedGames(games: List<Game>, season: String): List<Game> =
    games.filter { it.season == season && it.teamScore != null && it.opponentScore != null }

/**
 * Builds one split per bucket, dropping buckets no game fell into — an empty
 * "Neutral" row early in a season is noise, not information.
 */
fun splitBy(
    games: List<Game>,
    lines: List<StatLine>,
    buckets: List<Pair<String, (Game) -> Boolean>>
): List<Split> {
    val linesByGame = lines.groupBy { it.gameId }
    return buckets.mapNotNull { (label, matches) ->
        val matched = games.filter(matches)
        if (matched.isEmpty()) return@mapNotNull null
        val matchedLines = matched.flatMap { linesByGame[it.id].orEmpty() }
        Split(
            label = label,
            games = matched.size,
            // A tie is neither, so wins + losses can be short of [games].
            // Softball does play them — weather shortens a game and it stands —
            // and calling one a loss would quietly misstate a record.
            wins = matched.count { (it.teamScore ?: 0) > (it.opponentScore ?: 0) },
            losses = matched.count { (it.teamScore ?: 0) < (it.opponentScore ?: 0) },
            runsFor = matched.sumOf { it.teamScore ?: 0 },
            runsAgainst = matched.sumOf { it.opponentScore ?: 0 },
            batting = aggregateBatting(matchedLines),
            pitching = aggregatePitching(matchedLines)
        )
    }
}

/** Home, away and neutral — softball plays a lot of tournaments, so neutral matters. */
fun siteSplits(games: List<Game>, lines: List<StatLine>): List<Split> = splitBy(
    games, lines,
    listOf(
        "Home" to { g: Game -> g.site == "H" },
        "Away" to { g: Game -> g.site == "A" },
        "Neutral" to { g: Game -> g.site == "N" }
    )
)

/**
 * Big 12 opponents against everyone else. Note this counts every meeting with
 * a conference team, including the conference tournament — which the standings'
 * own conference record excludes. The label says "opponents" rather than
 * "record" for exactly that reason.
 */
fun competitionSplits(
    games: List<Game>,
    lines: List<StatLine>,
    conference: Set<String>
): List<Split> = splitBy(
    games, lines,
    listOf(
        "Big 12 opponents" to { g: Game -> teamKey(g.opponent) in conference },
        "Non-conference" to { g: Game -> teamKey(g.opponent) !in conference }
    )
)

/** Against a nationally ranked opponent, or not. */
fun opponentQualitySplits(games: List<Game>, lines: List<StatLine>): List<Split> = splitBy(
    games, lines,
    listOf(
        "vs ranked" to { g: Game -> g.opponentRank > 0 },
        "vs unranked" to { g: Game -> g.opponentRank == 0 }
    )
)

/** How the team fared when the game was tight versus when it was not. */
fun marginSplits(games: List<Game>, lines: List<StatLine>): List<Split> {
    fun margin(g: Game) = kotlin.math.abs((g.teamScore ?: 0) - (g.opponentScore ?: 0))
    return splitBy(
        games, lines,
        listOf(
            "One-run games" to { g: Game -> margin(g) == 1 },
            "Decided by 2-4" to { g: Game -> margin(g) in 2..4 },
            "Decided by 5+" to { g: Game -> margin(g) >= 5 }
        )
    )
}
