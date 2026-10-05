package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.data.PollEntry
import com.example.data.RankingSnapshot
import com.example.stats.formatAvg

private const val KU_SEO = "kansas"

@Composable
fun StandingsScreen(
    standings: List<ConferenceStanding>,
    pollEntries: List<PollEntry>,
    rankingSnapshots: List<RankingSnapshot> = emptyList(),
    modifier: Modifier = Modifier
) {
    val seasons = (standings.map { it.season } + pollEntries.map { it.season })
        .distinct()
        .sortedDescending()
    var selectedSeason by rememberSaveable { mutableStateOf<String?>(null) }
    val season = selectedSeason ?: seasons.firstOrNull()

    if (season == null) {
        EmptyState(
            title = "No Big 12 data yet",
            subtitle = "Conference standings arrive with the nightly sync once the season is underway.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Already sorted by the scraper (conference win %), but sort defensively so
    // the table never depends on JSON ordering surviving the round trip.
    val rows = standings.filter { it.season == season }
        .sortedWith(
            compareByDescending<ConferenceStanding> { it.confPct }
                .thenByDescending { it.confW }
                .thenByDescending { it.overallPct }
                .thenBy { it.team }
        )
    val poll = pollEntries.filter { it.season == season }.sortedBy { it.rank }

    Column(modifier = modifier.fillMaxSize()) {
        if (seasons.size > 1) {
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
        }

        // KU's week-by-week RPI. Hidden until a second week lands — one point
        // is not a trend — which means it appears on its own partway into a
        // season rather than needing a change here.
        val trend = rankingSnapshots
            .filter { it.source == "rpi" && it.season == season && it.team == "Kansas" }
            .sortedBy { it.date }

        LazyColumn(
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (trend.size >= 2) {
                item { RpiTrend(trend) }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Big 12 Standings — $season",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (rows.isEmpty()) {
                            Text(
                                "No conference results for this season yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            StandingsTable(rows)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Ordered by conference win percentage — not official Big 12 " +
                                    "tiebreakers, which use head-to-head results.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (poll.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                poll.first().pollName.ifBlank { "National Poll" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            poll.first().updated.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "${poll.count { it.big12 }} of ${poll.size} from the Big 12",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider()
                            poll.forEach { PollRow(it) }
                        }
                    }
                }
            }
        }
    }
}

private val COL_W = 44.dp

@Composable
private fun StandingsTable(rows: List<ConferenceStanding>) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Team", 132.dp, TextAlign.Start)
            HeaderCell("Conf", 56.dp)
            HeaderCell("PCT", COL_W)
            HeaderCell("Overall", 64.dp)
            HeaderCell("Poll", COL_W)
            HeaderCell("RPI", COL_W)
        }
        HorizontalDivider()
        rows.forEach { r ->
            val isKu = r.seo == KU_SEO
            // KU is marked three ways — fill, bold, and a leading dot — so the
            // highlight never depends on color alone.
            Surface(
                color = if (isKu) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(vertical = 1.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BodyCell(
                        (if (isKu) "• " else "") + r.team,
                        132.dp,
                        TextAlign.Start,
                        bold = isKu
                    )
                    BodyCell("${r.confW}-${r.confL}", 56.dp, bold = isKu)
                    BodyCell(formatAvg(r.confPct), COL_W, bold = isKu)
                    BodyCell("${r.overallW}-${r.overallL}", 64.dp, bold = isKu)
                    BodyCell(r.nationalRank?.let { "#$it" } ?: "—", COL_W, bold = isKu)
                    BodyCell(r.rpiRank?.let { "#$it" } ?: "—", COL_W, bold = isKu)
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text,
        modifier = Modifier
            .width(width)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: TextAlign = TextAlign.Center,
    bold: Boolean = false
) {
    Text(
        text,
        modifier = Modifier
            .width(width)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        color = if (bold) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PollRow(entry: PollEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            entry.rankLabel,
            modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            entry.team + if (entry.firstPlaceVotes > 0) " (${entry.firstPlaceVotes})" else "",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (entry.big12) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        if (entry.big12) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "Big 12",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            entry.record,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/**
 * Kansas week by week in the RPI, drawn with the axis inverted so up is
 * better — a smaller number is a better rank, and a line that falls as the
 * team improves reads backwards.
 *
 * Bars rather than a polyline: Compose has no path primitive here that is
 * worth the risk, and a column per week says the same thing. Each bar's
 * height is how far above the worst week it sits, so the tallest bar is the
 * best rank of the season.
 */
@Composable
private fun RpiTrend(weeks: List<RankingSnapshot>) {
    val best = weeks.minOf { it.rank }
    val worst = weeks.maxOf { it.rank }
    val span = (worst - best).coerceAtLeast(1)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Kansas in the RPI",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            val first = weeks.first().rank
            val last = weeks.last().rank
            val move = when {
                first > last -> "up ${first - last} spots"
                first < last -> "down ${last - first} spots"
                else -> "level"
            }
            Text(
                "#$last · $move over ${weeks.size} weekly snapshots",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                weeks.forEach { week ->
                    val share = (worst - week.rank).toFloat() / span
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${week.rank}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // A floor of 6% so the worst week is still a
                                // bar rather than nothing at all.
                                .fillMaxHeight((0.06f + 0.84f * share).coerceIn(0.06f, 0.9f))
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    weeks.first().date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    weeks.last().date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Taller is better — the axis is inverted, because a lower RPI number is a better rank.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
