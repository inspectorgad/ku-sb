package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.FormWindow
import com.example.stats.aggregateBatting
import com.example.stats.aggregatePitching
import com.example.stats.formatAvg
import com.example.stats.formDelta
import com.example.stats.formatEra
import com.example.stats.formatInnings
import com.example.stats.playerForm
import com.example.stats.summarize

@Composable
fun RosterScreen(
    players: List<Player>,
    statLines: List<StatLine>,
    onSavePlayer: (Player) -> Unit,
    onOpenPlayer: (Player) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (players.isEmpty()) {
            EmptyState(
                title = "No players yet",
                subtitle = "Pull down to sync the Jayhawks roster, or add players manually.",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val (current, former) = players.partition { it.active }
            LazyColumn(
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(current, key = { it.id }) { player ->
                    PlayerCard(player, statLines, onOpenPlayer)
                }
                if (former.isNotEmpty()) {
                    item(key = "former-header") {
                        Text(
                            "Former Players",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(former, key = { it.id }) { player ->
                        PlayerCard(player, statLines, onOpenPlayer)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add player")
        }
    }

    if (showAddDialog) {
        PlayerDialog(
            player = null,
            onDismiss = { showAddDialog = false },
            onSave = {
                onSavePlayer(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun PlayerCard(
    player: Player,
    statLines: List<StatLine>,
    onOpenPlayer: (Player) -> Unit
) {
    val lines = statLines.filter { it.playerId == player.id }
    val batting = aggregateBatting(lines)
    val pitching = aggregatePitching(lines)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPlayer(player) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JerseyBadge(player.jerseyNumber)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    player.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (player.position.isNotBlank()) {
                    Text(
                        player.position,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Pitchers lead with their pitching line; everyone else with the bat.
                if (pitching.appearances > 0 && pitching.outsPitched >= batting.atBats) {
                    Text(
                        formatEra(pitching.earnedRunAverage) + " ERA",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${pitching.wins}-${pitching.losses} · ${pitching.strikeouts} K",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        formatAvg(batting.battingAverage) + " AVG",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${batting.homeRuns} HR · ${batting.runsBattedIn} RBI" +
                            if (pitching.appearances > 0) {
                                " · ${formatEra(pitching.earnedRunAverage)} ERA"
                            } else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun JerseyBadge(number: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.ifBlank { "–" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun PlayerDialog(
    player: Player?,
    onDismiss: () -> Unit,
    onSave: (Player) -> Unit
) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var number by remember { mutableStateOf(player?.jerseyNumber ?: "") }
    var position by remember { mutableStateOf(player?.position ?: "") }
    var active by remember { mutableStateOf(player?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (player == null) "Add Player" else "Edit Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { new -> number = new.filter { it.isDigit() }.take(3) },
                    label = { Text("Jersey #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Position (e.g. P, C, INF, OF)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "On current roster",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Player(
                            id = player?.id ?: 0,
                            name = name.trim(),
                            jerseyNumber = number.trim(),
                            position = position.trim(),
                            active = active
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Recent form: the player's last few appearances with the season underneath,
 * because a window only means something next to what it is a departure from.
 *
 * A window too thin for a rate shows its counting line and a dash where the
 * average would be. Printing ".000" off two at-bats would not be a slump, it
 * would be a rounding artifact given the same weight as a season.
 */
@Composable
private fun FormCard(season: String, windows: List<FormWindow>) {
    val seasonRow = windows.last()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Recent form — $season",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            windows.firstOrNull()?.let { latest ->
                formDelta(latest, seasonRow)?.let { delta ->
                    val direction = when {
                        delta > 0.005 -> "up"
                        delta < -0.005 -> "down"
                        else -> "level"
                    }
                    Text(
                        "${latest.label.lowercase()}: $direction on the season" +
                            if (direction == "level") "" else " (${formatDelta(delta)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            StatsTable(
                columns = FORM_BATTING_COLUMNS,
                rows = windows.map { w ->
                    w.label to listOf(
                        w.games.toString(),
                        w.batting.atBats.toString(),
                        w.batting.hits.toString(),
                        rateOrDash(w.battingQualified) { formatAvg(w.batting.battingAverage) },
                        rateOrDash(w.battingQualified) { formatAvg(w.batting.onBasePercentage) },
                        rateOrDash(w.battingQualified) { formatAvg(w.batting.sluggingPercentage) },
                        rateOrDash(w.battingQualified) { formatAvg(w.batting.onBasePlusSlugging) }
                    )
                },
                labelWidth = 72
            )
            if (windows.any { it.pitching.appearances > 0 }) {
                Spacer(modifier = Modifier.height(10.dp))
                StatsTable(
                    columns = FORM_PITCHING_COLUMNS,
                    rows = windows
                        .filter { it.pitching.appearances > 0 }
                        .map { w ->
                            w.label to listOf(
                                w.pitching.appearances.toString(),
                                formatInnings(w.pitching.outsPitched),
                                w.pitching.earnedRuns.toString(),
                                w.pitching.strikeouts.toString(),
                                rateOrDash(w.pitchingQualified) {
                                    formatEra(w.pitching.earnedRunAverage)
                                },
                                rateOrDash(w.pitchingQualified) {
                                    formatEra(w.pitching.walksAndHitsPerInning)
                                }
                            )
                        },
                    labelWidth = 72
                )
            }
        }
    }
}

/** A rate, or an em dash when there is not enough of it to be one. */
private inline fun rateOrDash(qualified: Boolean, rate: () -> String): String =
    if (qualified) rate() else "—"

/** A batting-average difference with its sign kept: "+.045", "-.112". */
private fun formatDelta(delta: Double): String =
    (if (delta >= 0) "+" else "-") + formatAvg(kotlin.math.abs(delta))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    player: Player,
    games: List<Game>,
    statLines: List<StatLine>,
    onSavePlayer: (Player) -> Unit,
    onDeletePlayer: (Player) -> Unit,
    onBack: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val gamesById = games.associateBy { it.id }
    val playerLines = statLines
        .filter { it.playerId == player.id }
        .sortedByDescending { gamesById[it.gameId]?.date ?: "" }

    // Season order: most recent first, based on the latest game date in each season.
    val seasonOrder = games
        .sortedByDescending { it.date }
        .map { it.season }
        .distinct()
    val linesBySeason = playerLines.groupBy { gamesById[it.gameId]?.season ?: "Unknown" }
    val seasonsPlayed = seasonOrder.filter { linesBySeason.containsKey(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (player.jerseyNumber.isBlank()) player.name
                        else "#${player.jerseyNumber} ${player.name}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit player")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete player")
                    }
                }
            )
        }
    ) { innerPadding ->
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
                            "Batting by Season",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val rows = seasonsPlayed
                            .map { s -> s to battingValues(aggregateBatting(linesBySeason.getValue(s))) } +
                            listOf("Career" to battingValues(aggregateBatting(playerLines)))
                        StatsTable(columns = BATTING_COLUMNS, rows = rows)
                    }
                }
            }

            if (playerLines.any { it.pitched }) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Pitching by Season",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val rows = seasonsPlayed
                                .filter { s -> linesBySeason.getValue(s).any { it.pitched } }
                                .map { s -> s to pitchingValues(aggregatePitching(linesBySeason.getValue(s))) } +
                                listOf("Career" to pitchingValues(aggregatePitching(playerLines)))
                            StatsTable(columns = PITCHING_COLUMNS, rows = rows)
                        }
                    }
                }
            }

            // Form belongs to the season being played, so it follows the most
            // recent one the player actually appeared in rather than today's
            // date — which in October would be an empty window.
            val currentSeason = seasonsPlayed.firstOrNull()
            if (currentSeason != null) {
                val form = playerForm(player.id, games, statLines, currentSeason)
                // One row is just the season totals again, already shown above.
                if (form.size > 1) {
                    item { FormCard(currentSeason, form) }
                }
            }

            item {
                Text(
                    "Game Log",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (playerLines.isEmpty()) {
                item {
                    EmptyState(
                        title = "No games recorded",
                        subtitle = "Add this player's stats from a game on the Games tab."
                    )
                }
            } else {
                items(playerLines, key = { it.id }) { line ->
                    val game = gamesById[line.gameId]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    game?.let { "${it.date} vs ${it.opponent}" } ?: "Unknown game",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    summarize(line),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            game?.let {
                                Text(
                                    it.season,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        PlayerDialog(
            player = player,
            onDismiss = { showEditDialog = false },
            onSave = {
                onSavePlayer(it)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${player.name}?") },
            text = { Text("This removes the player and all of their recorded stats. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { onDeletePlayer(player) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
