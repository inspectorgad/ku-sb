package com.example.data

import android.content.Context
import org.json.JSONObject

/**
 * Syncs the bundled assets/seed.json into the database on every launch,
 * gap-filling only — it never overwrites user-entered data:
 * - players are added if their name isn't already present
 * - games are added if no game exists for that date + opponent
 * - an existing game gets seed results only if it has none
 * - an existing game gets seed stat lines only if it has none
 *
 * This lets an updated APK (with fresh season data baked in) install over the
 * old one and pick up the new games while keeping local edits intact.
 *
 * Seed game shape:
 * {
 *   "date": "2026-02-06", "opponent": "Bethune-Cookman", "season": "2026",
 *   "teamScore": 12, "opponentScore": 0,
 *   "inningScores": "3-0, 2-0, 5-0, 0-0, 2-0",
 *   "teamHits": 10, "opponentHits": 2, "teamErrors": 0, "opponentErrors": 1,
 *   "lines": [{"player": "<player name>", "gs": 1, "ab": 3, "r": 1, "h": 2,
 *              "2b": 1, "3b": 0, "hr": 0, "rbi": 2, "bb": 0, "so": 0,
 *              "hbp": 0, "sb": 1, "cs": 0, "sf": 0, "sh": 0,
 *              "p": 1, "outs": 21, "ha": 2, "ra": 0, "er": 0, "bba": 1,
 *              "ks": 8, "hra": 0, "w": 1, "l": 0, "sv": 0}]
 * }
 * The pitching keys (p/outs/ha/ra/er/bba/ks/hra/w/l/sv) may be absent for
 * players who didn't pitch.
 */
object Seeder {

    suspend fun sync(context: Context, dao: JayhawksDao) {
        val json = runCatching {
            context.assets.open("seed.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return

        runCatching { merge(JSONObject(json), dao) }
    }

    private fun gameKey(date: String, opponent: String) = "$date|${opponent.lowercase()}"

    /** Also used by [SeasonSync] for network-fetched season data. */
    suspend fun merge(root: JSONObject, dao: JayhawksDao) {
        val existingByName = dao.playersOnce().associateBy { it.name }
        val playerIdsByName = existingByName.mapValues { it.value.id }.toMutableMap()

        val players = root.optJSONArray("players")
        if (players != null) {
            for (i in 0 until players.length()) {
                val p = players.getJSONObject(i)
                val name = p.getString("name")
                val jersey = p.optString("jerseyNumber", "")
                val position = p.optString("position", "")
                val active = p.optBoolean("active", true)
                val existing = existingByName[name]
                if (existing == null) {
                    playerIdsByName[name] = dao.insertPlayer(
                        Player(
                            name = name,
                            jerseyNumber = jersey,
                            position = position,
                            active = active
                        )
                    )
                } else {
                    // Roster facts (number, position, current-roster status) are
                    // scraper-owned and refreshed on every sync; blank seed values
                    // never erase what's already there.
                    val updated = existing.copy(
                        jerseyNumber = jersey.ifBlank { existing.jerseyNumber },
                        position = position.ifBlank { existing.position },
                        active = active
                    )
                    if (updated != existing) dao.updatePlayer(updated)
                }
            }
        }

        // Doubleheaders put two games on the same date, so games are keyed by
        // date + opponent — and a same-day rematch against the same opponent
        // additionally carries a "game2" marker in the opponent field upstream.
        val gamesByKey = dao.gamesOnce().associateBy { gameKey(it.date, it.opponent) }
        val gamesWithLines = dao.statLinesOnce().map { it.gameId }.toSet()

        val games = root.optJSONArray("games") ?: return
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val date = g.getString("date")
            val opponent = g.getString("opponent")
            val seedTeamScore = if (g.has("teamScore")) g.getInt("teamScore") else null
            val seedOppScore = if (g.has("opponentScore")) g.getInt("opponentScore") else null
            val seedInningScores = g.optString("inningScores").takeIf { it.isNotBlank() }

            val existing = gamesByKey[gameKey(date, opponent)]
            val gameId: Long
            if (existing == null) {
                gameId = dao.insertGame(
                    Game(
                        date = date,
                        opponent = opponent,
                        season = g.getString("season"),
                        teamScore = seedTeamScore,
                        opponentScore = seedOppScore,
                        inningScores = seedInningScores,
                        teamHits = if (g.has("teamHits")) g.getInt("teamHits") else null,
                        opponentHits = if (g.has("opponentHits")) g.getInt("opponentHits") else null,
                        teamErrors = if (g.has("teamErrors")) g.getInt("teamErrors") else null,
                        opponentErrors = if (g.has("opponentErrors")) g.getInt("opponentErrors") else null
                    )
                )
            } else {
                gameId = existing.id
                if (existing.teamScore == null && existing.opponentScore == null &&
                    (seedTeamScore != null || seedOppScore != null)
                ) {
                    dao.updateGame(
                        existing.copy(
                            teamScore = seedTeamScore,
                            opponentScore = seedOppScore,
                            inningScores = existing.inningScores ?: seedInningScores,
                            teamHits = existing.teamHits
                                ?: if (g.has("teamHits")) g.getInt("teamHits") else null,
                            opponentHits = existing.opponentHits
                                ?: if (g.has("opponentHits")) g.getInt("opponentHits") else null,
                            teamErrors = existing.teamErrors
                                ?: if (g.has("teamErrors")) g.getInt("teamErrors") else null,
                            opponentErrors = existing.opponentErrors
                                ?: if (g.has("opponentErrors")) g.getInt("opponentErrors") else null
                        )
                    )
                }
            }

            if (existing != null && gameId in gamesWithLines) continue
            val lines = g.optJSONArray("lines") ?: continue
            for (j in 0 until lines.length()) {
                val l = lines.getJSONObject(j)
                val playerId = playerIdsByName[l.getString("player")] ?: continue
                dao.upsertStatLine(
                    StatLine(
                        playerId = playerId,
                        gameId = gameId,
                        atBats = l.optInt("ab"),
                        runs = l.optInt("r"),
                        hits = l.optInt("h"),
                        doubles = l.optInt("2b"),
                        triples = l.optInt("3b"),
                        homeRuns = l.optInt("hr"),
                        runsBattedIn = l.optInt("rbi"),
                        walks = l.optInt("bb"),
                        strikeouts = l.optInt("so"),
                        hitByPitch = l.optInt("hbp"),
                        stolenBases = l.optInt("sb"),
                        caughtStealing = l.optInt("cs"),
                        sacrificeFlies = l.optInt("sf"),
                        sacrificeHits = l.optInt("sh"),
                        started = l.optInt("gs") == 1,
                        pitched = l.optInt("p") == 1,
                        outsPitched = l.optInt("outs"),
                        hitsAllowed = l.optInt("ha"),
                        runsAllowed = l.optInt("ra"),
                        earnedRuns = l.optInt("er"),
                        walksAllowed = l.optInt("bba"),
                        pitcherStrikeouts = l.optInt("ks"),
                        homeRunsAllowed = l.optInt("hra"),
                        win = l.optInt("w") == 1,
                        loss = l.optInt("l") == 1,
                        save = l.optInt("sv") == 1
                    )
                )
            }
        }
    }
}
