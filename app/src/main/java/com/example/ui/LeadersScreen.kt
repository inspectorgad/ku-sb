package com.example.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.BattingTotals
import com.example.stats.PitchingTotals
import com.example.stats.aggregateBatting
import com.example.stats.aggregatePitching
import com.example.stats.formatAvg
import com.example.stats.formatEra
import com.example.stats.formatPerGame

private const val ALL_SEASONS = "All"

// NCAA-style qualification minimums, scaled to games played:
// rate-stat batters need 2 AB per team game, pitchers 1 IP per team game.
private const val MIN_AB_PER_TEAM_GAME = 2
private const val MIN_OUTS_PER_TEAM_GAME = 3

@Composable
fun LeadersScreen(
    players: List<Player>,
    games: List<Game>,
    statLines: List<StatLine>,
    modifier: Modifier = Modifier,
    dataUpdatedAt: String? = null
) {
    // Seasons ordered most recent first; default selection is the current (latest) season.
    val seasons = games.sortedByDescending { it.date }.map { it.season }.distinct()
    var selectedSeason by rememberSaveable { mutableStateOf<String?>(null) }
    val season = selectedSeason ?: seasons.firstOrNull() ?: ALL_SEASONS

    val seasonGames =
        if (season == ALL_SEASONS) games else games.filter { it.season == season }
    val seasonGameIds = seasonGames.map { it.id }.toSet()
    val seasonLines = statLines.filter { it.gameId in seasonGameIds }
    val playersById = players.associateBy { it.id }
    val battingByPlayer: Map<Long, BattingTotals> = seasonLines
        .groupBy { it.playerId }
        .mapValues { (_, lines) -> aggregateBatting(lines) }
    val pitchingByPlayer: Map<Long, PitchingTotals> = seasonLines
        .groupBy { it.playerId }
        .mapValues { (_, lines) -> aggregatePitching(lines) }
        .filterValues { it.appearances > 0 }

    val playedGames = seasonGames.filter { it.teamScore != null && it.opponentScore != null }
    val wins = playedGames.count { it.teamScore!! > it.opponentScore!! }
    val losses = playedGames.count { it.teamScore!! < it.opponentScore!! }
    val runsFor = playedGames.sumOf { it.teamScore ?: 0 }
    val runsAgainst = playedGames.sumOf { it.opponentScore ?: 0 }
    val teamBatting = aggregateBatting(seasonLines)
    val teamPitching = aggregatePitching(seasonLines)
    val minAb = playedGames.size * MIN_AB_PER_TEAM_GAME
    val minOuts = playedGames.size * MIN_OUTS_PER_TEAM_GAME

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (seasons + ALL_SEASONS).forEach { s ->
                FilterChip(
                    selected = season == s,
                    onClick = { selectedSeason = s },
                    label = { Text(s) }
                )
            }
        }

        if (seasonGames.isEmpty()) {
            EmptyState(
                title = "No games recorded",
                subtitle = "Add games and stat lines to see team totals and leaderboards here."
            )
            return@Column
        }

        LazyColumn(
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Jayhawks — $season",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val rpg = if (playedGames.isEmpty()) 0.0
                        else runsFor.toDouble() / playedGames.size
                        val oppRpg = if (playedGames.isEmpty()) 0.0
                        else runsAgainst.toDouble() / playedGames.size
                        Text(
                            "Record ${wins}-${losses}" +
                                " · ${formatPerGame(rpg)} R/G / ${formatPerGame(oppRpg)} allowed",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Team ${formatAvg(teamBatting.battingAverage)} AVG · " +
                                "${teamBatting.homeRuns} HR · " +
                                "${formatEra(teamPitching.earnedRunAverage)} ERA",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        dataUpdatedAt?.let {
                            Text(
                                "Data updated ${it.take(16).replace('T', ' ')} UTC · pull down to refresh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                LeaderSectionHeader("Batting")
            }

            item {
                LeaderCard(
                    title = "Batting Average" + if (minAb > 0) " (min $minAb AB)" else "",
                    entries = battingByPlayer
                        .filterValues { it.atBats >= minAb && it.atBats > 0 }
                        .entries
                        .sortedByDescending { it.value.battingAverage }
                        .take(3)
                        .mapNotNull { (playerId, totals) ->
                            playersById[playerId]?.let {
                                it.name to formatAvg(totals.battingAverage)
                            }
                        }
                )
            }

            val countingCategories = listOf<Pair<String, (BattingTotals) -> Int>>(
                "Home Runs" to { it.homeRuns },
                "Runs Batted In" to { it.runsBattedIn },
                "Hits" to { it.hits },
                "Runs Scored" to { it.runs },
                "Stolen Bases" to { it.stolenBases }
            )
            countingCategories.forEach { (title, selector) ->
                item {
                    LeaderCard(
                        title = title,
                        entries = battingByPlayer.entries
                            .filter { selector(it.value) > 0 }
                            .sortedByDescending { selector(it.value) }
                            .take(3)
                            .mapNotNull { (playerId, totals) ->
                                playersById[playerId]?.let {
                                    it.name to selector(totals).toString()
                                }
                            }
                    )
                }
            }

            item {
                LeaderCard(
                    title = "OPS" + if (minAb > 0) " (min $minAb AB)" else "",
                    entries = battingByPlayer
                        .filterValues { it.atBats >= minAb && it.atBats > 0 }
                        .entries
                        .sortedByDescending { it.value.onBasePlusSlugging }
                        .take(3)
                        .mapNotNull { (playerId, totals) ->
                            playersById[playerId]?.let {
                                it.name to formatAvg(totals.onBasePlusSlugging)
                            }
                        }
                )
            }

            item {
                LeaderSectionHeader("Pitching")
            }

            item {
                LeaderCard(
                    title = "ERA" + if (minOuts > 0) " (min ${minOuts / 3} IP)" else "",
                    entries = pitchingByPlayer
                        .filterValues { it.outsPitched >= minOuts && it.outsPitched > 0 }
                        .entries
                        .sortedBy { it.value.earnedRunAverage }
                        .take(3)
                        .mapNotNull { (playerId, totals) ->
                            playersById[playerId]?.let {
                                it.name to formatEra(totals.earnedRunAverage)
                            }
                        }
                )
            }

            val pitchCounting = listOf<Pair<String, (PitchingTotals) -> Int>>(
                "Strikeouts (Pitching)" to { it.strikeouts },
                "Wins" to { it.wins },
                "Saves" to { it.saves }
            )
            pitchCounting.forEach { (title, selector) ->
                item {
                    LeaderCard(
                        title = title,
                        entries = pitchingByPlayer.entries
                            .filter { selector(it.value) > 0 }
                            .sortedByDescending { selector(it.value) }
                            .take(3)
                            .mapNotNull { (playerId, totals) ->
                                playersById[playerId]?.let {
                                    it.name to selector(totals).toString()
                                }
                            }
                    )
                }
            }

            item {
                LeaderCard(
                    title = "WHIP" + if (minOuts > 0) " (min ${minOuts / 3} IP)" else "",
                    entries = pitchingByPlayer
                        .filterValues { it.outsPitched >= minOuts && it.outsPitched > 0 }
                        .entries
                        .sortedBy { it.value.walksAndHitsPerInning }
                        .take(3)
                        .mapNotNull { (playerId, totals) ->
                            playersById[playerId]?.let {
                                it.name to formatEra(totals.walksAndHitsPerInning)
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun LeaderSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun LeaderCard(title: String, entries: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (entries.isEmpty()) {
                Text(
                    "No qualifying players yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEachIndexed { index, (name, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
