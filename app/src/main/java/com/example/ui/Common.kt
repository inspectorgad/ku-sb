package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stats.BattingTotals
import com.example.stats.PitchingTotals
import com.example.stats.formatAvg
import com.example.stats.formatEra
import com.example.stats.formatInnings

// Column label to cell width for the batting table.
val BATTING_COLUMNS = listOf(
    "GP" to 40, "GS" to 40, "AVG" to 52, "AB" to 44, "R" to 40, "H" to 40,
    "2B" to 40, "3B" to 40, "HR" to 40, "RBI" to 44, "BB" to 40, "SO" to 40,
    "HBP" to 44, "SB" to 40, "CS" to 40, "OBP" to 52, "SLG" to 52, "OPS" to 56
)

fun battingValues(t: BattingTotals): List<String> = listOf(
    t.games.toString(), t.gamesStarted.toString(), formatAvg(t.battingAverage),
    t.atBats.toString(), t.runs.toString(), t.hits.toString(),
    t.doubles.toString(), t.triples.toString(), t.homeRuns.toString(),
    t.runsBattedIn.toString(), t.walks.toString(), t.strikeouts.toString(),
    t.hitByPitch.toString(), t.stolenBases.toString(), t.caughtStealing.toString(),
    formatAvg(t.onBasePercentage), formatAvg(t.sluggingPercentage),
    formatAvg(t.onBasePlusSlugging)
)

// Column label to cell width for the pitching table.
val PITCHING_COLUMNS = listOf(
    "APP" to 44, "W" to 36, "L" to 36, "SV" to 40, "ERA" to 52, "IP" to 52,
    "H" to 40, "R" to 40, "ER" to 40, "BB" to 40, "SO" to 40, "HR" to 40,
    "WHIP" to 56
)

fun pitchingValues(t: PitchingTotals): List<String> = listOf(
    t.appearances.toString(), t.wins.toString(), t.losses.toString(),
    t.saves.toString(), formatEra(t.earnedRunAverage), formatInnings(t.outsPitched),
    t.hitsAllowed.toString(), t.runsAllowed.toString(), t.earnedRuns.toString(),
    t.walksAllowed.toString(), t.strikeouts.toString(), t.homeRunsAllowed.toString(),
    formatEra(t.walksAndHitsPerInning)
)

// The three tables of a softball box score. Deliberately narrower than the
// season tables above: a box score is read a row at a time, and every extra
// column pushes the ones that matter off the side of a phone. The richer
// fields the scrape now keeps (pitch count, K-looking, GIDP) stay one tap
// away in the stat-line detail rather than crowding these.
val BOX_BATTING_COLUMNS = listOf(
    "AB" to 40, "R" to 36, "H" to 36, "RBI" to 44, "BB" to 36, "SO" to 36
)
val BOX_PITCHING_COLUMNS = listOf(
    "IP" to 48, "H" to 36, "R" to 36, "ER" to 40, "BB" to 36, "SO" to 36,
    "NP" to 40, "BF" to 40
)
val BOX_FIELDING_COLUMNS = listOf(
    "PO" to 40, "A" to 36, "E" to 36, "DP" to 40
)

// Recent-form tables. Narrow on purpose: a form window is read against the
// season row directly beneath it, and anything wider than a phone defeats the
// comparison. Every label here is also a season-table label, so the
// press-and-hold definitions already cover them.
val FORM_BATTING_COLUMNS = listOf(
    "GP" to 36, "AB" to 44, "H" to 36, "AVG" to 52, "OBP" to 52, "SLG" to 52, "OPS" to 56
)
// A head-to-head is a handful of games, so this stays on counting stats with
// one rate at the end: "8-for-12" is the fact, ".667" is only its shorthand,
// and over three games the fact is the part worth leading with.
val OPPONENT_BATTING_COLUMNS = listOf(
    "AB" to 44, "H" to 36, "2B" to 40, "3B" to 40, "HR" to 40, "RBI" to 44, "AVG" to 52
)
// An opposing batting line across a series. Counting stats with one rate at
// the end, same as the head-to-head table: over three games "5-for-11" is the
// fact and ".455" is only its shorthand.
val OPPONENT_PLAYER_COLUMNS = listOf(
    "GP" to 36, "AB" to 44, "R" to 36, "H" to 36, "2B" to 40, "3B" to 40,
    "HR" to 40, "RBI" to 44, "BB" to 36, "SO" to 36, "AVG" to 52
)
val FORM_PITCHING_COLUMNS = listOf(
    "APP" to 44, "IP" to 52, "ER" to 40, "SO" to 40, "ERA" to 52, "WHIP" to 56
)

/**
 * A horizontally scrollable stats table. Each row is a label (e.g. season
 * name) plus pre-formatted cell values matching [columns]. The label column
 * stays compact; stat cells use the per-column widths.
 */
@Composable
fun StatsTable(
    columns: List<Pair<String, Int>>,
    rows: List<Pair<String, List<String>>>,
    modifier: Modifier = Modifier,
    labelWidth: Int = 84
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TableCell("", width = labelWidth.dp, header = true)
            // Press and hold a column heading for a plain-English definition.
            columns.forEach { (label, w) ->
                Explainable(label) { TableCell(label, width = w.dp, header = true) }
            }
        }
        HorizontalDivider()
        rows.forEach { (label, values) ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(label, width = labelWidth.dp, header = true, align = TextAlign.Start)
                values.forEachIndexed { i, value ->
                    TableCell(value, width = columns[i].second.dp)
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    header: Boolean = false,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(vertical = 4.dp),
        fontSize = 12.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        color = if (header) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Compact numeric entry field used in the stat line editor. */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(3)) },
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

/**
 * A quiet italic aside under something, for the sentence that explains what it
 * is or what it costs. Smaller and softer than body text so it reads as a note
 * rather than as part of the content.
 */
@Composable
fun Explanation(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

val ListContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
