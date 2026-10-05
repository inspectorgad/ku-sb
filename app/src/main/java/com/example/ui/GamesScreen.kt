package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Game
import com.example.data.OpponentStatLine
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.formatInnings
import com.example.stats.opponentName
import com.example.stats.parseInnings
import com.example.stats.summarize

/**
 * "at Texas Tech" for away games, "vs Texas Tech" otherwise. Neutral-site
 * games read "vs" the way box scores and schedules print them; the neutral
 * flag is surfaced separately where there is room for it.
 */
fun opponentLabel(game: Game): String =
    (if (game.site == "A") "at " else "vs ") +
        (if (game.opponentRank > 0) "#${game.opponentRank} " else "") +
        game.opponent

@Composable
fun GamesScreen(
    games: List<Game>,
    statLines: List<StatLine>,
    onSaveGame: (Game) -> Unit,
    onOpenGame: (Game) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (games.isEmpty()) {
            EmptyState(
                title = "No games yet",
                subtitle = "Pull down to sync the season, or add games manually to track stats.",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games, key = { it.id }) { game ->
                    val lineCount = statLines.count { it.gameId == game.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenGame(game) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    opponentLabel(game),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${game.date} · ${game.season}" +
                                        (if (game.site == "N") " · neutral" else "") +
                                        if (lineCount > 0) " · $lineCount player${if (lineCount == 1) "" else "s"}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                game.inningScores?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            ResultText(game)
                        }
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
            Icon(Icons.Default.Add, contentDescription = "Add game")
        }
    }

    if (showAddDialog) {
        GameDialog(
            game = null,
            defaultSeason = games.maxByOrNull { it.date }?.season ?: "",
            onDismiss = { showAddDialog = false },
            onSave = {
                onSaveGame(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ResultText(game: Game) {
    val us = game.teamScore
    val them = game.opponentScore
    if (us == null || them == null) {
        // Not played yet: the scheduled first pitch is more useful than "—".
        Text(
            game.startTime.ifBlank { "No result" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val won = us > them
        Text(
            "${if (won) "W" else "L"} $us–$them",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (won) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
    }
}

/**
 * "How the runs scored": one row per scoring play, from [Game.scoringSummary]
 * (`inning|ku|us|them|narrative` per line). Softball-native — neither sibling
 * app has an equivalent, because neither sport's feed carries one.
 */
@Composable
private fun ScoringSummary(game: Game) {
    val plays = game.scoringSummary
        .split('\n')
        .mapNotNull { row ->
            val parts = row.split('|', limit = 5)
            if (parts.size < 5) return@mapNotNull null
            val inning = parts[0].toIntOrNull() ?: return@mapNotNull null
            ScoringPlay(
                inning = inning,
                isKu = parts[1] == "1",
                us = parts[2].toIntOrNull() ?: 0,
                them = parts[3].toIntOrNull() ?: 0,
                narrative = parts[4]
            )
        }
    if (plays.isEmpty()) return

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "How the runs scored",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold
    )
    plays.forEach { play ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Inning number, then who scored — KU filled, opponent outlined, so
            // the distinction never rests on colour alone.
            Text(
                "${play.inning}",
                modifier = Modifier.width(20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Surface(
                color = if (play.isKu) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(44.dp)
            ) {
                Text(
                    if (play.isKu) "KU" else "OPP",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (play.isKu) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = if (play.isKu) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                play.narrative,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "${play.us}-${play.them}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

private data class ScoringPlay(
    val inning: Int,
    val isKu: Boolean,
    val us: Int,
    val them: Int,
    val narrative: String
)

/**
 * Venue, announced crowd, who the opponent was on the day, and a link out to
 * the official box score.
 */
@Composable
private fun GameContext(game: Game) {
    val bits = buildList {
        if (game.venue.isNotBlank()) add(game.venue)
        if (game.attendance > 0) add("${"%,d".format(game.attendance)} fans")
        // The opponent's record through this game. Shown only for a played
        // game: before first pitch the scrape has no record to report, and a
        // blank would read as "0-0" rather than "not known yet".
        if (game.opponentRecord.isNotBlank() && game.teamScore != null) {
            add("${game.opponent} were ${game.opponentRecord}")
        }
    }
    if (bits.isEmpty() && game.boxScoreUrl.isBlank()) return
    val uriHandler = LocalUriHandler.current
    Spacer(modifier = Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (bits.isNotEmpty()) {
            Text(
                bits.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }
        if (game.boxScoreUrl.isNotBlank()) {
            Text(
                "Official box score",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri(game.boxScoreUrl) }
            )
        }
    }
}

/**
 * Traditional R/H/E line score: one column per inning plus runs, hits, and
 * errors totals, KU row first. Built from [Game.inningScores] ("0-1, 2-0, …").
 */
@Composable
private fun LineScore(game: Game) {
    val innings = (game.inningScores ?: "")
        .split(",")
        .map { it.trim() }
        .filter { it.contains('-') }
        .map { part ->
            val (us, them) = part.split('-', limit = 2)
            us.trim() to them.trim()
        }
    if (innings.isEmpty()) return

    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            LineScoreCell("", width = 64, header = true, align = TextAlign.Start)
            innings.forEachIndexed { i, _ -> LineScoreCell("${i + 1}", header = true) }
            LineScoreCell("R", header = true, wide = true)
            LineScoreCell("H", header = true, wide = true)
            LineScoreCell("E", header = true, wide = true)
        }
        HorizontalDivider()
        Row {
            LineScoreCell("KU", width = 64, header = true, align = TextAlign.Start)
            innings.forEach { (us, _) -> LineScoreCell(us) }
            LineScoreCell(game.teamScore?.toString() ?: "-", wide = true, bold = true)
            LineScoreCell(game.teamHits?.toString() ?: "-", wide = true)
            LineScoreCell(game.teamErrors?.toString() ?: "-", wide = true)
        }
        Row {
            LineScoreCell(game.opponent.take(8), width = 64, header = true, align = TextAlign.Start)
            innings.forEach { (_, them) -> LineScoreCell(them) }
            LineScoreCell(game.opponentScore?.toString() ?: "-", wide = true, bold = true)
            LineScoreCell(game.opponentHits?.toString() ?: "-", wide = true)
            LineScoreCell(game.opponentErrors?.toString() ?: "-", wide = true)
        }
    }
}

@Composable
private fun LineScoreCell(
    text: String,
    width: Int = 26,
    wide: Boolean = false,
    header: Boolean = false,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = Modifier
            .width(if (wide) 32.dp else width.dp)
            .padding(vertical = 2.dp),
        fontSize = 12.sp,
        fontWeight = if (header || bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        color = if (header) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun GameDialog(
    game: Game?,
    defaultSeason: String,
    onDismiss: () -> Unit,
    onSave: (Game) -> Unit
) {
    var date by remember { mutableStateOf(game?.date ?: "") }
    var opponent by remember { mutableStateOf(game?.opponent ?: "") }
    var season by remember { mutableStateOf(game?.season ?: defaultSeason) }
    var teamScore by remember { mutableStateOf(game?.teamScore?.toString() ?: "") }
    var oppScore by remember { mutableStateOf(game?.opponentScore?.toString() ?: "") }
    var inningScores by remember { mutableStateOf(game?.inningScores ?: "") }
    var site by remember { mutableStateOf(game?.site ?: "") }
    var startTime by remember { mutableStateOf(game?.startTime ?: "") }
    var teamHits by remember { mutableStateOf(game?.teamHits?.toString() ?: "") }
    var oppHits by remember { mutableStateOf(game?.opponentHits?.toString() ?: "") }
    var teamErrors by remember { mutableStateOf(game?.teamErrors?.toString() ?: "") }
    var oppErrors by remember { mutableStateOf(game?.opponentErrors?.toString() ?: "") }

    val dateValid = Regex("""\d{4}-\d{2}-\d{2}""").matches(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (game == null) "Add Game" else "Edit Game") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = date.isNotEmpty() && !dateValid,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = opponent,
                    onValueChange = { opponent = it },
                    label = { Text("Opponent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = season,
                    onValueChange = { season = it },
                    label = { Text("Season (e.g. 2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("H" to "Home", "A" to "Away", "N" to "Neutral").forEach { (code, label) ->
                        FilterChip(
                            selected = site == code,
                            onClick = { site = if (site == code) "" else code },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("First pitch (e.g. 6 p.m. CT)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Our runs",
                        value = teamScore,
                        onValueChange = { teamScore = it },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Their runs",
                        value = oppScore,
                        onValueChange = { oppScore = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = inningScores,
                    onValueChange = { inningScores = it },
                    label = { Text("Inning runs (0-1, 2-0, …)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Our hits",
                        value = teamHits,
                        onValueChange = { teamHits = it },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Their hits",
                        value = oppHits,
                        onValueChange = { oppHits = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Our errors",
                        value = teamErrors,
                        onValueChange = { teamErrors = it },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Their errors",
                        value = oppErrors,
                        onValueChange = { oppErrors = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = dateValid && opponent.isNotBlank() && season.isNotBlank(),
                onClick = {
                    onSave(
                        Game(
                            id = game?.id ?: 0,
                            date = date,
                            opponent = opponent.trim(),
                            season = season.trim(),
                            site = site,
                            startTime = startTime.trim(),
                            teamScore = teamScore.toIntOrNull(),
                            opponentScore = oppScore.toIntOrNull(),
                            inningScores = inningScores.trim().ifBlank { null },
                            teamHits = teamHits.toIntOrNull(),
                            opponentHits = oppHits.toIntOrNull(),
                            teamErrors = teamErrors.toIntOrNull(),
                            opponentErrors = oppErrors.toIntOrNull()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * A player's name as a box score prints it: everything after the first name,
 * so "Ada Van Dyke" stays "Van Dyke" rather than becoming "Dyke".
 */
private fun boxName(full: String): String {
    val rest = full.substringAfter(' ', "").trim()
    return if (rest.isEmpty()) full else rest
}

/**
 * The box score, in the order the game was actually played rather than in
 * roster order: batters by lineup slot with substitutes indented under the
 * starter whose spot they took, then every pitcher who threw, then the
 * fielding line.
 *
 * Lineup order is the whole point — a box score sorted by surname tells you
 * nothing about who led off or who the game was handed to in the seventh.
 * Players who appear with no slot (a pinch runner a source never numbered)
 * sort to the end rather than being dropped.
 */
@Composable
private fun BoxScore(players: List<Player>, lines: List<StatLine>) {
    val byId = players.associateBy { it.id }
    val named = lines.mapNotNull { line -> byId[line.playerId]?.let { it to line } }
    if (named.isEmpty()) return

    // Row id is insertion order, which is the order the box score listed the
    // players — so it is appearance order, and it is the only thing that is.
    // Every pitcher shares lineup slot 10 (softball's FLEX), so without this
    // they would tie and fall back to something arbitrary: sorting the
    // pitching line by surname would claim the wrong starter.
    val inOrder = named.sortedWith(
        compareBy(
            { it.second.lineupSpot == 0 },      // unnumbered last
            { it.second.lineupSpot },
            { it.second.substitute },           // starter above the sub in the slot
            { it.second.id }
        )
    )

    fun label(player: Player, line: StatLine): String {
        val slot = if (line.lineupSpot > 0 && !line.substitute) "${line.lineupSpot} " else "  "
        val pos = if (line.position.isNotBlank()) " ${line.position}" else ""
        return slot + boxName(player.name) + pos
    }

    val batters = inOrder.filter { (_, l) ->
        l.atBats > 0 || l.walks > 0 || l.hitByPitch > 0 || l.runs > 0 ||
            l.sacrificeFlies > 0 || l.sacrificeHits > 0 || l.stolenBases > 0 ||
            (l.lineupSpot > 0 && !l.pitched)
    }
    val pitchers = inOrder.filter { it.second.pitched }
    val fielders = inOrder.filter { (_, l) ->
        l.putouts > 0 || l.assists > 0 || l.errors > 0 || l.doublePlaysTurned > 0
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Box score",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Batting order · press and hold a heading for what it means",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (batters.isNotEmpty()) {
                StatsTable(
                    columns = BOX_BATTING_COLUMNS,
                    rows = batters.map { (p, l) ->
                        label(p, l) to listOf(
                            l.atBats.toString(), l.runs.toString(), l.hits.toString(),
                            l.runsBattedIn.toString(), l.walks.toString(), l.strikeouts.toString()
                        )
                    },
                    labelWidth = 132
                )
                val totals = batters.map { it.second }
                Text(
                    "Totals: ${totals.sumOf { it.hits }}-for-${totals.sumOf { it.atBats }}, " +
                        "${totals.sumOf { it.runsBattedIn }} RBI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (pitchers.isNotEmpty()) {
                SectionLabel("Pitching")
                StatsTable(
                    columns = BOX_PITCHING_COLUMNS,
                    rows = pitchers.map { (p, l) ->
                        val decision = when {
                            l.win -> " (W)"
                            l.loss -> " (L)"
                            l.save -> " (S)"
                            else -> ""
                        }
                        (boxName(p.name) + decision) to listOf(
                            formatInnings(l.outsPitched), l.hitsAllowed.toString(),
                            l.runsAllowed.toString(), l.earnedRuns.toString(),
                            l.walksAllowed.toString(), l.pitcherStrikeouts.toString(),
                            // The scrape does not always carry these; a dash
                            // says "not reported", which a 0 would misstate.
                            if (l.pitchCount > 0) l.pitchCount.toString() else "–",
                            if (l.battersFaced > 0) l.battersFaced.toString() else "–"
                        )
                    },
                    labelWidth = 132
                )
            }

            if (fielders.isNotEmpty()) {
                SectionLabel("Fielding")
                StatsTable(
                    columns = BOX_FIELDING_COLUMNS,
                    rows = fielders.map { (p, l) ->
                        boxName(p.name) to listOf(
                            l.putouts.toString(), l.assists.toString(),
                            l.errors.toString(), l.doublePlaysTurned.toString()
                        )
                    },
                    labelWidth = 132
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(2.dp))
}

/**
 * The opposing side of one game's box score: their batting order, then
 * whoever pitched. Shorter than Kansas's, because this app keeps fewer
 * columns for a team it only ever sees two or three times.
 */
@Composable
private fun OpponentBoxScore(opponent: String, lines: List<OpponentStatLine>) {
    // Row id is insertion order, which is the order the box score listed them,
    // so this is lineup order with substitutes following the starter whose
    // slot they took — the same ordering Kansas's box score uses.
    val inOrder = lines.sortedWith(
        compareBy({ it.lineupSpot == 0 }, { it.lineupSpot }, { it.substitute }, { it.id })
    )
    val batters = inOrder.filter {
        it.atBats > 0 || it.walks > 0 || it.runs > 0 || it.hitByPitch > 0 ||
            (it.lineupSpot > 0 && !it.pitched)
    }
    val pitchers = inOrder.filter { it.pitched }
    if (batters.isEmpty() && pitchers.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "$opponent box score",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (batters.isNotEmpty()) {
                StatsTable(
                    columns = BOX_BATTING_COLUMNS,
                    rows = batters.map { l ->
                        val slot = if (l.lineupSpot > 0 && !l.substitute) "${l.lineupSpot} " else "  "
                        val pos = if (l.position.isNotBlank()) " ${l.position}" else ""
                        (slot + boxName(l.playerName) + pos) to listOf(
                            l.atBats.toString(), l.runs.toString(), l.hits.toString(),
                            l.runsBattedIn.toString(), l.walks.toString(), l.strikeouts.toString()
                        )
                    },
                    labelWidth = 132
                )
            }
            if (pitchers.isNotEmpty()) {
                SectionLabel("Pitching")
                StatsTable(
                    columns = BOX_PITCHING_COLUMNS,
                    rows = pitchers.map { l ->
                        val decision = when {
                            l.win -> " (W)"
                            l.loss -> " (L)"
                            l.save -> " (S)"
                            else -> ""
                        }
                        (boxName(l.playerName) + decision) to listOf(
                            formatInnings(l.outsPitched), l.hitsAllowed.toString(),
                            l.runsAllowed.toString(), l.earnedRuns.toString(),
                            l.walksAllowed.toString(), l.pitcherStrikeouts.toString(),
                            if (l.pitchCount > 0) l.pitchCount.toString() else "\u2013",
                            if (l.battersFaced > 0) l.battersFaced.toString() else "\u2013"
                        )
                    },
                    labelWidth = 132
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    game: Game,
    players: List<Player>,
    statLines: List<StatLine>,
    opponentStatLines: List<OpponentStatLine>,
    onSaveGame: (Game) -> Unit,
    onDeleteGame: (Game) -> Unit,
    onSaveStatLine: (StatLine) -> Unit,
    onDeleteStatLine: (StatLine) -> Unit,
    onBack: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingLineFor by remember { mutableStateOf<Player?>(null) }

    val gameLines = statLines.filter { it.gameId == game.id }
    val linesByPlayer = gameLines.associateBy { it.playerId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(opponentLabel(game)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit game")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete game")
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${game.date} · ${game.season}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            ResultText(game)
                        }
                        LineScore(game)
                        ScoringSummary(game)
                        GameContext(game)
                    }
                }
            }

            if (gameLines.isNotEmpty()) {
                item { BoxScore(players, gameLines) }
            }

            // The other half of the box score. A game has two sides and the
            // scrape downloads both; until now the app showed one.
            val theirLines = opponentStatLines.filter { it.gameId == game.id }
            if (theirLines.isNotEmpty()) {
                item { OpponentBoxScore(opponentName(game.opponent), theirLines) }
            }

            if (players.isEmpty()) {
                item {
                    EmptyState(
                        title = "No players on the roster",
                        subtitle = "Add players on the Roster tab first, then record their stats here."
                    )
                }
            } else {
                // Former players only clutter stat entry unless they actually
                // played in this game (e.g. seeded past-season box scores).
                val relevant = players
                    .filter { it.active || it.id in linesByPlayer }
                    // Same order as the box score above, so the two readings of
                    // the game agree: whoever hit leadoff is first in both.
                    .sortedWith(
                        compareBy(
                            { (linesByPlayer[it.id]?.lineupSpot ?: 0) == 0 },
                            { linesByPlayer[it.id]?.lineupSpot ?: 0 },
                            { linesByPlayer[it.id]?.substitute ?: false },
                            { it.name }
                        )
                    )
                item {
                    Text(
                        if (gameLines.isEmpty())
                            "Tap a player to enter their batting and pitching line."
                        else "Tap a player to edit their line.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(relevant, key = { it.id }) { player ->
                    val line = linesByPlayer[player.id]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingLineFor = player }
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
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    line?.let { summarize(it) } ?: "Did not play — tap to add",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (line != null) {
                                IconButton(onClick = { onDeleteStatLine(line) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove stat line",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingLineFor?.let { player ->
        StatLineDialog(
            player = player,
            existing = linesByPlayer[player.id],
            gameId = game.id,
            onDismiss = { editingLineFor = null },
            onSave = {
                onSaveStatLine(it)
                editingLineFor = null
            }
        )
    }

    if (showEditDialog) {
        GameDialog(
            game = game,
            defaultSeason = game.season,
            onDismiss = { showEditDialog = false },
            onSave = {
                onSaveGame(it)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this game?") },
            text = { Text("This removes the game and every stat line recorded for it. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { onDeleteGame(game) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StatLineDialog(
    player: Player,
    existing: StatLine?,
    gameId: Long,
    onDismiss: () -> Unit,
    onSave: (StatLine) -> Unit
) {
    fun init(value: Int?) = value?.takeIf { it != 0 }?.toString() ?: ""

    // Batting
    var ab by remember { mutableStateOf(init(existing?.atBats)) }
    var r by remember { mutableStateOf(init(existing?.runs)) }
    var h by remember { mutableStateOf(init(existing?.hits)) }
    var doubles by remember { mutableStateOf(init(existing?.doubles)) }
    var triples by remember { mutableStateOf(init(existing?.triples)) }
    var hr by remember { mutableStateOf(init(existing?.homeRuns)) }
    var rbi by remember { mutableStateOf(init(existing?.runsBattedIn)) }
    var bb by remember { mutableStateOf(init(existing?.walks)) }
    var so by remember { mutableStateOf(init(existing?.strikeouts)) }
    var hbp by remember { mutableStateOf(init(existing?.hitByPitch)) }
    var sb by remember { mutableStateOf(init(existing?.stolenBases)) }
    var cs by remember { mutableStateOf(init(existing?.caughtStealing)) }
    var sf by remember { mutableStateOf(init(existing?.sacrificeFlies)) }
    var sh by remember { mutableStateOf(init(existing?.sacrificeHits)) }
    var started by remember { mutableStateOf(existing?.started ?: false) }
    // Pitching
    var pitched by remember { mutableStateOf(existing?.pitched ?: false) }
    var ip by remember {
        mutableStateOf(existing?.takeIf { it.pitched }?.let { formatInnings(it.outsPitched) } ?: "")
    }
    var ha by remember { mutableStateOf(init(existing?.hitsAllowed)) }
    var ra by remember { mutableStateOf(init(existing?.runsAllowed)) }
    var er by remember { mutableStateOf(init(existing?.earnedRuns)) }
    var bba by remember { mutableStateOf(init(existing?.walksAllowed)) }
    var ks by remember { mutableStateOf(init(existing?.pitcherStrikeouts)) }
    var hra by remember { mutableStateOf(init(existing?.homeRunsAllowed)) }
    var win by remember { mutableStateOf(existing?.win ?: false) }
    var loss by remember { mutableStateOf(existing?.loss ?: false) }
    var save by remember { mutableStateOf(existing?.save ?: false) }

    fun num(s: String) = s.toIntOrNull() ?: 0

    val statError = when {
        num(h) > num(ab) -> "Hits can't exceed at-bats."
        num(doubles) + num(triples) + num(hr) > num(h) -> "Extra-base hits can't exceed hits."
        num(so) > num(ab) -> "Strikeouts can't exceed at-bats."
        pitched && num(er) > num(ra) -> "Earned runs can't exceed runs allowed."
        pitched && (win && loss) -> "A pitcher can't get both the win and the loss."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${player.name} — Game Line") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Batting",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                StatFieldRow(
                    "AB" to ab to { v: String -> ab = v },
                    "R" to r to { v: String -> r = v },
                    "H" to h to { v: String -> h = v }
                )
                StatFieldRow(
                    "2B" to doubles to { v: String -> doubles = v },
                    "3B" to triples to { v: String -> triples = v },
                    "HR" to hr to { v: String -> hr = v }
                )
                StatFieldRow(
                    "RBI" to rbi to { v: String -> rbi = v },
                    "BB" to bb to { v: String -> bb = v },
                    "SO" to so to { v: String -> so = v }
                )
                StatFieldRow(
                    "HBP" to hbp to { v: String -> hbp = v },
                    "SB" to sb to { v: String -> sb = v },
                    "CS" to cs to { v: String -> cs = v }
                )
                StatFieldRow(
                    "SF" to sf to { v: String -> sf = v },
                    "SAC" to sh to { v: String -> sh = v }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Started",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = started, onCheckedChange = { started = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pitched",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = pitched, onCheckedChange = { pitched = it })
                }
                if (pitched) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { new -> ip = new.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text("IP (e.g. 6.2)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    StatFieldRow(
                        "H" to ha to { v: String -> ha = v },
                        "R" to ra to { v: String -> ra = v },
                        "ER" to er to { v: String -> er = v }
                    )
                    StatFieldRow(
                        "BB" to bba to { v: String -> bba = v },
                        "SO" to ks to { v: String -> ks = v },
                        "HR" to hra to { v: String -> hra = v }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Win",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = win, onCheckedChange = { win = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Loss",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = loss, onCheckedChange = { loss = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Save",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = save, onCheckedChange = { save = it })
                    }
                }
                statError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = statError == null,
                onClick = {
                    onSave(
                        StatLine(
                            id = existing?.id ?: 0,
                            playerId = player.id,
                            gameId = gameId,
                            atBats = num(ab),
                            runs = num(r),
                            hits = num(h),
                            doubles = num(doubles),
                            triples = num(triples),
                            homeRuns = num(hr),
                            runsBattedIn = num(rbi),
                            walks = num(bb),
                            strikeouts = num(so),
                            hitByPitch = num(hbp),
                            stolenBases = num(sb),
                            caughtStealing = num(cs),
                            sacrificeFlies = num(sf),
                            sacrificeHits = num(sh),
                            started = started,
                            pitched = pitched,
                            outsPitched = if (pitched) parseInnings(ip) else 0,
                            hitsAllowed = if (pitched) num(ha) else 0,
                            runsAllowed = if (pitched) num(ra) else 0,
                            earnedRuns = if (pitched) num(er) else 0,
                            walksAllowed = if (pitched) num(bba) else 0,
                            pitcherStrikeouts = if (pitched) num(ks) else 0,
                            homeRunsAllowed = if (pitched) num(hra) else 0,
                            win = pitched && win,
                            loss = pitched && loss,
                            save = pitched && save
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StatFieldRow(
    vararg fields: Pair<Pair<String, String>, (String) -> Unit>
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { (labelAndValue, onChange) ->
            val (label, value) = labelAndValue
            NumberField(
                label = label,
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
