package com.example.stats

import com.example.data.StatLine
import java.util.Locale

/**
 * Aggregated batting totals for any collection of stat lines
 * (one player's game, a season, a career, or the whole team).
 */
data class BattingTotals(
    val games: Int = 0,
    val gamesStarted: Int = 0,
    val atBats: Int = 0,
    val runs: Int = 0,
    val hits: Int = 0,
    val doubles: Int = 0,
    val triples: Int = 0,
    val homeRuns: Int = 0,
    val runsBattedIn: Int = 0,
    val walks: Int = 0,
    val strikeouts: Int = 0,
    val hitByPitch: Int = 0,
    val stolenBases: Int = 0,
    val caughtStealing: Int = 0,
    val sacrificeFlies: Int = 0,
    val sacrificeHits: Int = 0
) {
    val totalBases: Int
        get() = hits + doubles + 2 * triples + 3 * homeRuns

    val battingAverage: Double
        get() = ratio(hits, atBats)

    // Official OBP denominator: AB + BB + HBP + SF (sacrifice bunts excluded).
    val onBasePercentage: Double
        get() = ratio(hits + walks + hitByPitch, atBats + walks + hitByPitch + sacrificeFlies)

    val sluggingPercentage: Double
        get() = ratio(totalBases, atBats)

    val onBasePlusSlugging: Double
        get() = onBasePercentage + sluggingPercentage

    private fun ratio(num: Int, den: Int): Double =
        if (den == 0) 0.0 else num.toDouble() / den
}

/**
 * Aggregated pitching totals. Only stat lines with [StatLine.pitched] count
 * as appearances; innings are carried as outs so partial innings add up.
 */
data class PitchingTotals(
    val appearances: Int = 0,
    val outsPitched: Int = 0,
    val hitsAllowed: Int = 0,
    val runsAllowed: Int = 0,
    val earnedRuns: Int = 0,
    val walksAllowed: Int = 0,
    val strikeouts: Int = 0,
    val homeRunsAllowed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val saves: Int = 0
) {
    // Softball ERA is per 7 innings: ER * 7 / IP, with IP = outs/3.
    val earnedRunAverage: Double
        get() = if (outsPitched == 0) 0.0 else earnedRuns * 21.0 / outsPitched

    val walksAndHitsPerInning: Double
        get() = if (outsPitched == 0) 0.0 else (walksAllowed + hitsAllowed) * 3.0 / outsPitched

    val strikeoutsPerSeven: Double
        get() = if (outsPitched == 0) 0.0 else strikeouts * 21.0 / outsPitched
}

/** Sums the batting side of a set of stat lines into one totals row. */
fun aggregateBatting(lines: Collection<StatLine>): BattingTotals = BattingTotals(
    games = lines.size,
    gamesStarted = lines.count { it.started },
    atBats = lines.sumOf { it.atBats },
    runs = lines.sumOf { it.runs },
    hits = lines.sumOf { it.hits },
    doubles = lines.sumOf { it.doubles },
    triples = lines.sumOf { it.triples },
    homeRuns = lines.sumOf { it.homeRuns },
    runsBattedIn = lines.sumOf { it.runsBattedIn },
    walks = lines.sumOf { it.walks },
    strikeouts = lines.sumOf { it.strikeouts },
    hitByPitch = lines.sumOf { it.hitByPitch },
    stolenBases = lines.sumOf { it.stolenBases },
    caughtStealing = lines.sumOf { it.caughtStealing },
    sacrificeFlies = lines.sumOf { it.sacrificeFlies },
    sacrificeHits = lines.sumOf { it.sacrificeHits }
)

/** Sums the pitching side; lines where the player never pitched are ignored. */
fun aggregatePitching(lines: Collection<StatLine>): PitchingTotals {
    val pitched = lines.filter { it.pitched }
    return PitchingTotals(
        appearances = pitched.size,
        outsPitched = pitched.sumOf { it.outsPitched },
        hitsAllowed = pitched.sumOf { it.hitsAllowed },
        runsAllowed = pitched.sumOf { it.runsAllowed },
        earnedRuns = pitched.sumOf { it.earnedRuns },
        walksAllowed = pitched.sumOf { it.walksAllowed },
        strikeouts = pitched.sumOf { it.pitcherStrikeouts },
        homeRunsAllowed = pitched.sumOf { it.homeRunsAllowed },
        wins = pitched.count { it.win },
        losses = pitched.count { it.loss },
        saves = pitched.count { it.save }
    )
}

/** Formats a batting rate softball-style: .452, .000, 1.000 */
fun formatAvg(value: Double): String {
    val formatted = String.format(Locale.US, "%.3f", value)
    return formatted
        .replace("0.", ".")
        .let { if (it == "-.000") ".000" else it }
}

/** Formats an ERA/WHIP-style rate: 2.45, 0.98 */
fun formatEra(value: Double): String = String.format(Locale.US, "%.2f", value)

/** Formats a per-game rate: 1.4, 0.4 */
fun formatPerGame(value: Double): String = String.format(Locale.US, "%.1f", value)

/** Outs recorded -> conventional innings notation: 22 outs = "7.1". */
fun formatInnings(outs: Int): String = "${outs / 3}.${outs % 3}"

/** Parses innings notation back to outs: "7.1" = 22. Bad third-digits are clamped. */
fun parseInnings(text: String): Int {
    val parts = text.trim().split('.')
    val whole = parts.getOrNull(0)?.toIntOrNull() ?: return 0
    val frac = (parts.getOrNull(1)?.take(1)?.toIntOrNull() ?: 0).coerceIn(0, 2)
    return whole * 3 + frac
}

/** Short human summary of a game line, e.g. "2-4, HR, 3 RBI · 6.0 IP, 1 ER, 5 K (W)". */
fun summarize(line: StatLine): String {
    val parts = mutableListOf<String>()
    if (line.atBats > 0 || line.hits > 0 || line.walks > 0) {
        parts.add("${line.hits}-${line.atBats}")
        if (line.homeRuns > 0) parts.add(if (line.homeRuns == 1) "HR" else "${line.homeRuns} HR")
        else if (line.triples > 0) parts.add(if (line.triples == 1) "3B" else "${line.triples} 3B")
        else if (line.doubles > 0) parts.add(if (line.doubles == 1) "2B" else "${line.doubles} 2B")
        if (line.runsBattedIn > 0) parts.add("${line.runsBattedIn} RBI")
        if (line.runs > 0) parts.add("${line.runs} R")
        if (line.stolenBases > 0) parts.add("${line.stolenBases} SB")
        if (line.walks > 0) parts.add("${line.walks} BB")
    } else if (line.runs > 0 || line.stolenBases > 0) {
        // Pinch runner: scored or stole without an at-bat.
        if (line.runs > 0) parts.add("${line.runs} R")
        if (line.stolenBases > 0) parts.add("${line.stolenBases} SB")
    }
    if (line.pitched) {
        val decision = when {
            line.win -> " (W)"
            line.loss -> " (L)"
            line.save -> " (S)"
            else -> ""
        }
        parts.add(
            "${formatInnings(line.outsPitched)} IP, ${line.earnedRuns} ER, " +
                "${line.pitcherStrikeouts} K$decision"
        )
    }
    if (parts.isEmpty()) return "No stats"
    return parts.joinToString(", ")
}
