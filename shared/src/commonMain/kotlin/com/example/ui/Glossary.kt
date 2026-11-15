package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Plain-English definitions for the abbreviations and derived stats.
 *
 * A softball box score is eighteen columns of initials before you reach the
 * rate stats; nobody should need to already know what OPS or WHIP mean to read
 * their own team's season. Press and hold anything with a definition to get one.
 *
 * Note that five abbreviations — H, R, BB, SO, HR — appear in BOTH the batting
 * and pitching tables with opposite meanings (a hit you got versus a hit you
 * gave up). There is one glossary, so those entries name both readings rather
 * than pretending the column means only one thing.
 */
val GLOSSARY: Map<String, String> = mapOf(
    // Batting — exactly the BATTING_COLUMNS headings. A test asserts none is
    // left without a definition.
    "GP" to "Games played — any game the player appeared in, at bat or in the field.",
    "GS" to "Games started — in the lineup for the first pitch, rather than coming off the bench.",
    "AVG" to "Batting average: hits divided by at-bats. Walks, hit-by-pitches and " +
        "sacrifices are not at-bats, so they neither help nor hurt it.",
    "AB" to "At-bats. A plate appearance that ends in a hit or an out, excluding " +
        "walks, hit-by-pitches and sacrifices.",
    "2B" to "Doubles — a hit on which the batter reached second base.",
    "3B" to "Triples — a hit on which the batter reached third base.",
    "RBI" to "Runs batted in: runners who scored because of this batter, " +
        "excluding runs scored on an error or a double play.",
    "HBP" to "Hit by pitch. Counts toward on-base percentage but not batting average.",
    "SB" to "Stolen bases — bases advanced on the pitch, without a hit, walk or error to help.",
    "CS" to "Caught stealing — thrown out attempting to steal.",
    "OBP" to "On-base percentage: how often a batter reaches base, counting walks " +
        "and hit-by-pitches: (H + BB + HBP) / (AB + BB + HBP + SF).",
    "SLG" to "Slugging percentage: total bases per at-bat. A double counts twice, " +
        "a home run four times, so it measures power rather than frequency.",
    "OPS" to "On-base plus slugging — OBP added to SLG. One number for getting on " +
        "base and hitting for power; around .800 is a strong college season.",

    // Shared between the two tables, which is why each names both readings.
    "H" to "Hits. In a batting line, hits the batter got; in a pitching line, hits allowed.",
    "R" to "Runs. In a batting line, runs the player scored; in a pitching line, runs allowed " +
        "(earned and unearned together).",
    "BB" to "Walks — four balls. In a batting line, walks drawn; in a pitching line, walks issued.",
    "SO" to "Strikeouts. In a batting line, times the batter struck out; in a pitching line, " +
        "batters the pitcher struck out.",
    "HR" to "Home runs. In a batting line, home runs hit; in a pitching line, home runs allowed.",

    // Pitching — exactly the PITCHING_COLUMNS headings.
    "APP" to "Appearances: games in which the pitcher threw at least one pitch, " +
        "whether starting or in relief.",
    "W" to "Wins credited to the pitcher.",
    "L" to "Losses charged to the pitcher.",
    "SV" to "Saves: finishing a close win without being the winning pitcher.",
    "ERA" to "Earned run average: earned runs per seven innings, softball's full game. " +
        "Runs that scored only because of an error are unearned and left out.",
    "IP" to "Innings pitched, counted in outs — 6.2 means six innings and two outs, " +
        "not six and two-tenths.",
    "ER" to "Earned runs: runs that scored without the help of an error or a passed ball. " +
        "ERA is built on these, not on total runs.",
    "WHIP" to "Walks and hits per inning pitched: (BB + H) / IP. How many runners a " +
        "pitcher puts on per inning, regardless of whether they score. Around 1.00 is excellent.",
    "BAA" to "Batting average against: what opposing hitters batted off this pitcher. " +
        "ERA says how many of them scored; this says how often they got a hit at all, " +
        "which tells a pitcher who was unlucky from one who was hit hard.",

    // Fielding — exactly the BOX_FIELDING_COLUMNS headings. Softball's third
    // stat category, and the one neither sibling app has an equivalent for.
    "PO" to "Putouts — outs this fielder personally recorded: catching a fly ball, " +
        "tagging a runner, or standing on the base for a force.",
    "A" to "Assists — throws or deflections that led to an out someone else recorded. " +
        "A shortstop's throw to first is an assist for her, a putout for the first baseman.",
    "E" to "Errors — a misplay that let a batter or runner advance when ordinary " +
        "effort would have got the out. Runs that score afterwards are usually unearned.",
    "DP" to "Double plays this fielder took part in, whether she started it, turned it " +
        "or caught the final throw.",

    // Pitching detail kept from the box score
    "NP" to "Number of pitches thrown. The honest measure of a pitcher's workload — " +
        "innings hide a long inning, a pitch count does not.",
    "BF" to "Batters faced — every hitter who came to the plate against this pitcher, " +
        "including those who walked or were hit, which at-bats leave out.",

    // Standings and rankings
    "Conf" to "Record against Big 12 opponents in the regular season. The conference " +
        "tournament is not counted.",
    "Overall" to "Record against everyone, including non-conference, the Big 12 " +
        "tournament and the NCAA tournament.",
    "PCT" to "Winning percentage in conference play.",
    "Poll" to "Rank in the ESPN.com/USA Softball top 25, voted by coaches and media. " +
        "Teams outside the 25 are unranked — softball has no AP poll.",
    "RPI" to "The NCAA's Rating Percentage Index: the selection committee's team rating, " +
        "built from winning percentage and strength of schedule. Lower is better.",

    // Game classification
    "Neutral" to "Played at neither team's home field — a tournament or a showcase. " +
        "The NCAA counts it separately from home and away.",
    "Fall" to "Fall exhibition play, in the weeks before the spring season. The results " +
        "count toward no official record, so they are kept as their own season.",
)

/** The definition for a label, if there is one. */
fun explain(label: String): String? = GLOSSARY[label]

/**
 * Wraps content so a long press explains it. Does nothing when the label has
 * no definition, so callers need not check first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Explainable(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val definition = explain(label)
    var showing by remember { mutableStateOf(false) }

    if (definition == null && onClick == null) {
        content()
        return
    }
    Column(
        modifier = modifier.combinedClickable(
            enabled = definition != null || onClick != null,
            onClick = { onClick?.invoke() },
            onLongClick = { if (definition != null) showing = true }
        )
    ) { content() }

    if (showing && definition != null) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = { Text(definition, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showing = false }) { Text("Got it") }
            }
        )
    }
}
