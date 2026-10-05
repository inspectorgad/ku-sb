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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.BattingTotals
import com.example.stats.aggregateBatting
import com.example.stats.aggregatePitching
import com.example.stats.formatAvg
import com.example.stats.formatEra
import com.example.stats.formatInnings
import com.example.stats.opponentName
import com.example.stats.opponentRecords
import com.example.stats.teamKey

/**
 * Who Kansas played, and how the series went.
 *
 * Softball schedules weekend series, so this is a list of two- and three-game
 * sets rather than of single meetings — which is why it earns a tab here and
 * would not in a sport that plays each opponent once.
 *
 * Every figure is Kansas's, stated from Kansas's side: "2-1" means KU won two.
 * The caption says so outright, because a bare record beside a school's name
 * reads just as naturally as that school's own, and the two are opposites.
 */
@Composable
fun OpponentsScreen(
    games: List<Game>,
    onOpenOpponent: (opponentKey: String, season: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val seasons = games
        .filter { it.teamScore != null }
        .sortedByDescending { it.date }
        .map { it.season }
        .distinct()
    var selectedSeason by rememberSaveable { mutableStateOf<String?>(null) }
    val season = selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull() ?: ""
    val opponents = opponentRecords(games.filter { it.season == season })

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

        if (opponents.isEmpty()) {
            EmptyState(
                title = "No opponents yet",
                subtitle = "Each team Kansas has played appears here once a game has a result."
            )
            return@Column
        }

        LazyColumn(
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Records are Kansas's against each opponent in $season — not that " +
                        "opponent's own season. The two halves of a doubleheader count as " +
                        "two games.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(opponents.size) { index ->
                val o = opponents[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOpponent(teamKey(o.name), season) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (o.bestRank > 0) "#${o.bestRank} ${o.name}" else o.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${o.games} game${if (o.games == 1) "" else "s"} · " +
                                    "${o.runsFor}-${o.runsAgainst} on runs " +
                                    "(${if (o.runDifferential >= 0) "+" else ""}" +
                                    "${o.runDifferential})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            o.record,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Everything one opponent did to this season, and everything done back.
 *
 * Softball schedules weekend series, so a head-to-head here is two or three
 * games in three days rather than a single meeting — enough that "who hit well
 * against them" is a real question with a real answer, which is why this screen
 * exists at all and why the sibling apps have no equivalent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentDetailScreen(
    opponentKey: String,
    season: String,
    players: List<Player>,
    games: List<Game>,
    statLines: List<StatLine>,
    onBack: () -> Unit
) {
    val met = games
        .filter {
            it.season == season && teamKey(it.opponent) == opponentKey &&
                it.teamScore != null && it.opponentScore != null
        }
        .sortedWith(compareBy({ it.date }, { it.startTime }, { it.id }))
    val title = met.firstOrNull()?.let { opponentName(it.opponent) } ?: "Opponent"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (met.isEmpty()) {
            EmptyState(
                title = "No games against $title",
                subtitle = "Nothing has been played against them in $season.",
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        val metIds = met.map { it.id }.toSet()
        val lines = statLines.filter { it.gameId in metIds }
        val byPlayer = players.associateBy { it.id }
        val wins = met.count { it.teamScore!! > it.opponentScore!! }
        val losses = met.count { it.teamScore!! < it.opponentScore!! }
        val runsFor = met.sumOf { it.teamScore ?: 0 }
        val runsAgainst = met.sumOf { it.opponentScore ?: 0 }
        val bestRank = met.mapNotNull { it.opponentRank.takeIf { r -> r > 0 } }.minOrNull()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                            "${met.size} game${if (met.size == 1) "" else "s"} in $season · " +
                                "$runsFor-$runsAgainst on runs " +
                                "(${if (diff >= 0) "+" else ""}$diff)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bestRank != null) {
                            Text(
                                "Ranked as high as #$bestRank when they met",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(met.size) { index ->
                val game = met[index]
                val won = game.teamScore!! > game.opponentScore!!
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${game.date} · ${if (game.site == "A") "away" else
                                    if (game.site == "N") "neutral" else "home"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (game.venue.isNotBlank()) {
                                Text(
                                    game.venue,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            "${if (won) "W" else "L"} ${game.teamScore}-${game.opponentScore}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (won) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Who hit them, best first. Anyone who never came to the plate in
            // the series is left out rather than filling the table with zeroes.
            val batters = lines
                .groupBy { it.playerId }
                .mapValues { (_, playerLines) -> aggregateBatting(playerLines) }
                .filterValues { it.atBats > 0 }
                .toList()
                .sortedWith(
                    compareByDescending<Pair<Long, BattingTotals>> {
                        it.second.hits
                    }.thenByDescending { it.second.totalBases }
                )
            if (batters.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "At the plate",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            StatsTable(
                                columns = OPPONENT_BATTING_COLUMNS,
                                rows = batters.map { (playerId, t) ->
                                    (byPlayer[playerId]?.name ?: "Unknown") to listOf(
                                        t.atBats.toString(), t.hits.toString(),
                                        t.doubles.toString(), t.triples.toString(),
                                        t.homeRuns.toString(), t.runsBattedIn.toString(),
                                        formatAvg(t.battingAverage)
                                    )
                                },
                                labelWidth = 124
                            )
                        }
                    }
                }
            }

            val pitchers = lines
                .filter { it.pitched }
                .groupBy { it.playerId }
                .mapValues { (_, playerLines) -> aggregatePitching(playerLines) }
                .toList()
                .sortedByDescending { it.second.outsPitched }
            if (pitchers.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "In the circle",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            StatsTable(
                                columns = FORM_PITCHING_COLUMNS,
                                rows = pitchers.map { (playerId, t) ->
                                    (byPlayer[playerId]?.name ?: "Unknown") to listOf(
                                        t.appearances.toString(),
                                        formatInnings(t.outsPitched),
                                        t.earnedRuns.toString(),
                                        t.strikeouts.toString(),
                                        formatEra(t.earnedRunAverage),
                                        formatEra(t.walksAndHitsPerInning)
                                    )
                                },
                                labelWidth = 124
                            )
                        }
                    }
                }
            }
        }
    }
}
