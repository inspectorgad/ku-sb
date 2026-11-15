package com.example.data

import com.example.json.JSONObject
import com.example.platform.Device
import com.example.platform.newHttp
import com.example.text.currentTimeMs
import com.example.text.hex2
import com.example.text.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

sealed interface SyncResult {
    data class Updated(val generatedAt: String?) : SyncResult
    data object NoChange : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/**
 * Downloads the latest parsed season data (published by CI next to the APK)
 * and folds it into the database via [Seeder.merge] — the same gap-filling
 * merge used for the bundled seed, so user-entered data is never overwritten.
 */
object SeasonSync {

    private const val DATA_URL =
        "https://github.com/inspectorgad/ku-sb/releases/latest/download/season-data.json"
    private const val PREFS = "season_sync"
    private const val KEY_HASH = "last_hash"
    private const val KEY_SUCCESS_MS = "last_success_ms"
    private const val KEY_GENERATED_AT = "last_generated_at"
    private const val AUTO_SYNC_INTERVAL_MS = 6L * 60 * 60 * 1000

    // OkHttp on Android, NSURLSession on iPhone, with the same timeouts as
    // before: ten seconds to connect, twenty for a server that goes quiet.
    private val http by lazy { newHttp(connectSeconds = 10, readSeconds = 20) }

    fun lastGeneratedAt(device: Device): String? =
        device.settings(PREFS).string(KEY_GENERATED_AT)

    /**
     * Forgets the last feed this phone merged, so the next sync treats the
     * feed as new and merges it again. Used to repair a phone whose data an
     * older bundled seed rolled back before [Seeder] learned not to.
     */
    fun mergeAgain(device: Device) = device.settings(PREFS).edit { putString(KEY_HASH, null) }

    fun shouldAutoSync(device: Device): Boolean = device.settings(PREFS).let { p ->
        // A forgotten hash means a repair is owed, so it does not wait out the
        // six hours. A phone that has simply never synced has both conditions.
        p.string(KEY_HASH) == null || currentTimeMs() - p.long(KEY_SUCCESS_MS) > AUTO_SYNC_INTERVAL_MS
    }

    suspend fun sync(device: Device, dao: JayhawksDao): SyncResult =
        withContext(Dispatchers.IO) {
            val body = try {
                val resp = http.get(DATA_URL)
                if (!resp.isSuccessful) {
                    return@withContext SyncResult.Failed("server returned ${resp.code}")
                }
                resp.body.takeIf { it.isNotEmpty() }
                    ?: return@withContext SyncResult.Failed("empty response")
            } catch (e: Exception) {
                return@withContext SyncResult.Failed(e.message ?: "network error")
            }

            val root = SeasonDataValidator.parse(body)
                ?: return@withContext SyncResult.Failed("downloaded data failed validation")

            val hash = sha256(body).joinToString("") { hex2(it) }
            val settings = device.settings(PREFS)
            if (hash == settings.string(KEY_HASH)) {
                settings.edit { putLong(KEY_SUCCESS_MS, currentTimeMs()) }
                return@withContext SyncResult.NoChange
            }

            try {
                Seeder.merge(root, dao)
            } catch (e: Exception) {
                return@withContext SyncResult.Failed("merge failed: ${e.message}")
            }

            val generatedAt = root.optString("generatedAt").takeIf { it.isNotBlank() }
            settings.edit {
                putString(KEY_HASH, hash)
                putLong(KEY_SUCCESS_MS, currentTimeMs())
                putString(KEY_GENERATED_AT, generatedAt)
            }
            SyncResult.Updated(generatedAt)
        }
}

/**
 * Sanity gate for downloaded season data: a truncated, garbled, or
 * newer-format payload is rejected before it can reach the database.
 */
object SeasonDataValidator {
    const val SUPPORTED_FORMAT = 1
    const val MIN_PLAYERS = 8
    const val MIN_GAMES = 20

    fun parse(bytes: ByteArray): JSONObject? = try {
        val root = JSONObject(bytes.decodeToString())
        val valid = root.optInt("formatVersion", 1) <= SUPPORTED_FORMAT &&
            (root.optJSONArray("players")?.length() ?: 0) >= MIN_PLAYERS &&
            (root.optJSONArray("games")?.length() ?: 0) >= MIN_GAMES
        if (valid) root else null
    } catch (e: Exception) {
        null
    }
}
