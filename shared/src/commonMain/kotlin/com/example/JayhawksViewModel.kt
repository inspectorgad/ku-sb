package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConferenceStanding
import com.example.data.OpponentStatLine
import com.example.data.RankingSnapshot
import com.example.data.Game
import com.example.data.Player
import com.example.data.PollEntry
import com.example.data.JayhawksDao
import com.example.data.SeasonSync
import com.example.platform.Device
import com.example.data.Seeder
import com.example.data.StatLine
import com.example.data.SyncResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The screens' one source of data, built by each app with that phone's
 * database and storage — so the same screens run on Android and on iPhone.
 */
class JayhawksViewModel(
    private val dao: JayhawksDao,
    private val device: Device,
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _dataUpdatedAt = MutableStateFlow(SeasonSync.lastGeneratedAt(device))
    val dataUpdatedAt: StateFlow<String?> = _dataUpdatedAt.asStateFlow()

    // Snackbar messages, emitted only for user-initiated refreshes.
    private val _syncMessages = MutableSharedFlow<String>()
    val syncMessages: SharedFlow<String> = _syncMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            // Bundled data first (also covers first launch offline), then a
            // throttled network sync for anything newer.
            Seeder.sync(device, dao)
            if (SeasonSync.shouldAutoSync(device)) refreshInternal(manual = false)
        }
    }

    fun refresh() = viewModelScope.launch { refreshInternal(manual = true) }

    private suspend fun refreshInternal(manual: Boolean) {
        if (_isSyncing.value) return
        _isSyncing.value = true
        val result = try {
            SeasonSync.sync(device, dao)
        } finally {
            _isSyncing.value = false
        }
        _dataUpdatedAt.value = SeasonSync.lastGeneratedAt(device)
        if (manual) {
            _syncMessages.emit(
                when (result) {
                    is SyncResult.Updated -> "Season data updated"
                    SyncResult.NoChange -> "Already up to date"
                    is SyncResult.Failed -> "Sync failed: ${result.reason}"
                }
            )
        }
    }

    val players: StateFlow<List<Player>> = dao.observePlayers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val games: StateFlow<List<Game>> = dao.observeGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statLines: StateFlow<List<StatLine>> = dao.observeStatLines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val opponentStatLines: StateFlow<List<OpponentStatLine>> = dao.observeOpponentStatLines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rankingSnapshots: StateFlow<List<RankingSnapshot>> = dao.observeRankingSnapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val standings: StateFlow<List<ConferenceStanding>> = dao.observeStandings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pollEntries: StateFlow<List<PollEntry>> = dao.observePollEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun savePlayer(player: Player) = viewModelScope.launch {
        if (player.id == 0L) dao.insertPlayer(player) else dao.updatePlayer(player)
    }

    fun deletePlayer(player: Player) = viewModelScope.launch { dao.deletePlayer(player) }

    fun saveGame(game: Game, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = if (game.id == 0L) dao.insertGame(game) else {
            dao.updateGame(game)
            game.id
        }
        onSaved(id)
    }

    fun deleteGame(game: Game) = viewModelScope.launch { dao.deleteGame(game) }

    fun saveStatLine(line: StatLine) = viewModelScope.launch { dao.upsertStatLine(line) }

    fun deleteStatLine(line: StatLine) = viewModelScope.launch { dao.deleteStatLine(line) }
}
