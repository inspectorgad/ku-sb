package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConferenceStanding
import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.Split
import com.example.stats.aggregateBatting
import com.example.stats.aggregatePitching
import com.example.stats.competitionSplits
import com.example.stats.conferenceOpponents
import com.example.stats.currentStreak
import com.example.stats.formatAvg
import com.example.stats.formatEra
import com.example.stats.inningSplits
import com.example.stats.marginSplits
import com.example.stats.opponentQualitySplits
import com.example.stats.seasonHighlights
import com.example.stats.siteSplits
import com.example.stats.streakPhrase

/**
 * The season read sideways: what the record is made of, rather than what it is.
 *
 * A record and a batting average are two numbers for five months of softball.
 * Everything here exists to break one of them apart — by where the game was
 * played, who it was against, how close it was, which inning the runs came in,
 * and which single games were worth remembering. Head-to-head records moved to
 * their own tab once this screen grew long enough to bury them.
 */
@Composable
fun SeasonScreen(
    players: List<Player>,
    games: List<Game>,
    statLines: List<StatLine>,
    standings: List<ConferenceStanding>,
    onOpenAsk: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val seasons = games.sortedByDescending { it.date }.map { it.season }.distinct()
    var selectedSeason by rememberSaveable { mutableStateOf<String?>(null) }
    val season = selectedSeason ?: seasons.firstOrNull() ?: ""

    val seasonGames = games.filter { it.season == season }
    val played = seasonGames.filter { it.teamScore != null && it.opponentScore != null }
    val playedIds = played.map { it.id }.toSet()
    val lines = statLines.filter { it.gameId in playedIds }
    val conference = conferenceOpponents(standings, season)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            seasons.forEach { s ->
                FilterChip(
                    selected = season == s,
                    onClick = { selectedSeason = s },
                    label = { Text(s) }
                )
            }
        }

        if (played.isEmpty()) {
            EmptyState(
                title = "Nothing played yet",
                subtitle = "Splits, highlights and head-to-head records appear once " +
                    "games have results."
            )
            return@Column
        }

        val batting = aggregateBatting(lines)
        val pitching = aggregatePitching(lines)
        val wins = played.count { it.teamScore!! > it.opponentScore!! }
        val losses = played.count { it.teamScore!! < it.opponentScore!! }
        val runsFor = played.sumOf { it.teamScore ?: 0 }
        val runsAgainst = played.sumOf { it.opponentScore ?: 0 }

        LazyColumn(
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "$wins-$losses",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val diff = runsFor - runsAgainst
                        Text(
                            "${played.size} games · $runsFor runs scored, $runsAgainst allowed " +
                                "(${if (diff >= 0) "+" else ""}$diff)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        streakPhrase(currentStreak(played))?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row {
                            GlanceStat("AVG", formatAvg(batting.battingAverage))
                            GlanceStat("OPS", formatAvg(batting.onBasePlusSlugging))
                            GlanceStat("ERA", formatEra(pitching.earnedRunAverage))
                            GlanceStat("WHIP", formatEra(pitching.walksAndHitsPerInning))
                        }
                    }
                }
            }

            // Offered here rather than as a sixth tab: this is already the
            // screen for questions about the season that the other tabs do
            // not answer, and Ask is the same thing without a fixed shape.
            onOpenAsk?.let { open ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = open)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Ask about the team \u203a",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Type a question — \"which inning do we score most of our runs " +
                                    "in?\" — and get an answer worked out from the season data.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Explanation(
                                "Answered by Claude, Anthropic's AI, using your own Anthropic " +
                                    "API key. Each question usually costs a few cents on your " +
                                    "Anthropic account."
                            )
                        }
                    }
                }
            }

            val highlights = seasonHighlights(played, players, lines)
            if (highlights.isNotEmpty()) {
                item { SectionHeading("Highlights") }
                items(highlights.size) { index ->
                    val h = highlights[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                h.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                h.headline,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (h.detail.isNotBlank()) {
                                Text(
                                    h.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val groups = listOf(
                "Where" to siteSplits(played, lines),
                "Who" to competitionSplits(played, lines, conference),
                "Quality of opponent" to opponentQualitySplits(played, lines),
                "How close" to marginSplits(played, lines)
            ).filter { it.second.size > 1 }   // a single bucket is not a split

            if (groups.isNotEmpty()) {
                item { SectionHeading("Splits") }
                items(groups.size) { index ->
                    val (title, splits) = groups[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SplitHeader()
                            splits.forEach { SplitRow(it) }
                        }
                    }
                }
            }

            val innings = inningSplits(played)
            if (innings.isNotEmpty()) {
                item { SectionHeading("Runs by inning") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val peak = innings.maxOf { maxOf(it.scored, it.allowed) }.coerceAtLeast(1)
                            innings.forEach { inning ->
                                InningRow(
                                    label = "${inning.inning}",
                                    scored = inning.scored,
                                    allowed = inning.allowed,
                                    peak = peak,
                                    // A late inning the team often never batted
                                    // in is not a late-inning collapse, so say
                                    // how many turns it actually got.
                                    note = if (inning.gamesBatted < played.size)
                                        "batted in ${inning.gamesBatted}" else ""
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Blue is runs scored, red runs allowed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun GlanceStat(label: String, value: String) {
    Explainable(label) {
        Column(modifier = Modifier.width(72.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private const val SPLIT_LABEL_WIDTH = 124
private const val SPLIT_CELL_WIDTH = 58

@Composable
private fun SplitHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SplitCell("", SPLIT_LABEL_WIDTH, header = true, align = TextAlign.Start)
        SplitCell("W-L", SPLIT_CELL_WIDTH, header = true)
        listOf("AVG", "OPS", "ERA", "WHIP").forEach {
            Explainable(it) { SplitCell(it, SPLIT_CELL_WIDTH, header = true) }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SplitRow(split: Split) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SplitCell(split.label, SPLIT_LABEL_WIDTH, header = true, align = TextAlign.Start)
        SplitCell(split.record, SPLIT_CELL_WIDTH)
        SplitCell(formatAvg(split.batting.battingAverage), SPLIT_CELL_WIDTH)
        SplitCell(formatAvg(split.batting.onBasePlusSlugging), SPLIT_CELL_WIDTH)
        SplitCell(formatEra(split.pitching.earnedRunAverage), SPLIT_CELL_WIDTH)
        SplitCell(formatEra(split.pitching.walksAndHitsPerInning), SPLIT_CELL_WIDTH)
    }
}

@Composable
private fun SplitCell(
    text: String,
    width: Int,
    header: Boolean = false,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = Modifier.width(width.dp),
        fontSize = 12.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        color = if (header) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InningRow(label: String, scored: Int, allowed: Int, peak: Int, note: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { scored.toFloat() / peak },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { allowed.toFloat() / peak },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error
            )
        }
        Column(modifier = Modifier.width(96.dp).padding(start = 8.dp)) {
            Text(
                "$scored / $allowed",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            if (note.isNotBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
