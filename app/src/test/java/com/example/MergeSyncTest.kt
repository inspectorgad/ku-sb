package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.JayhawksDatabase
import com.example.data.Seeder
import com.example.data.StatLine
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MergeSyncTest {

    private lateinit var db: JayhawksDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JayhawksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedJson(): JSONObject = JSONObject(
        """
        {
          "players": [{"name": "Ada Alpha", "jerseyNumber": "1", "position": "P"}],
          "games": [
            {"date": "2026-02-06", "opponent": "Bethune-Cookman", "season": "2026",
             "teamScore": 12, "opponentScore": 0,
             "inningScores": "3-0, 2-0, 5-0, 0-0, 2-0",
             "teamHits": 10, "opponentHits": 2, "teamErrors": 0, "opponentErrors": 1,
             "lines": [{"player": "Ada Alpha", "gs": 1, "ab": 3, "r": 1, "h": 2,
                        "2b": 1, "hr": 0, "rbi": 2, "bb": 0, "so": 0,
                        "p": 1, "outs": 15, "ha": 2, "ra": 0, "er": 0, "bba": 1,
                        "ks": 8, "w": 1}]}
          ]
        }
        """
    )

    @Test
    fun `merge into empty database inserts everything`() = runTest {
        Seeder.merge(seedJson(), db.dao())
        assertEquals(1, db.dao().playersOnce().size)
        val game = db.dao().gamesOnce().single()
        assertEquals(12, game.teamScore)
        assertEquals(0, game.opponentScore)
        assertEquals("3-0, 2-0, 5-0, 0-0, 2-0", game.inningScores)
        assertEquals(10, game.teamHits)
        assertEquals(1, game.opponentErrors)
        val line = db.dao().statLinesOnce().single()
        assertEquals(3, line.atBats)
        assertEquals(2, line.hits)
        assertEquals(1, line.doubles)
        assertEquals(2, line.runsBattedIn)
        assertEquals(true, line.started)
        assertEquals(true, line.pitched)
        assertEquals(15, line.outsPitched)
        assertEquals(8, line.pitcherStrikeouts)
        assertEquals(true, line.win)
        assertEquals(false, line.loss)
    }

    @Test
    fun `merge is idempotent`() = runTest {
        Seeder.merge(seedJson(), db.dao())
        Seeder.merge(seedJson(), db.dao())
        assertEquals(1, db.dao().playersOnce().size)
        assertEquals(1, db.dao().gamesOnce().size)
        assertEquals(1, db.dao().statLinesOnce().size)
    }

    @Test
    fun `doubleheader games on the same date but different opponents both merge`() = runTest {
        val json = seedJson().apply {
            getJSONArray("games").put(
                JSONObject(
                    """{"date": "2026-02-06", "opponent": "Illinois St.", "season": "2026",
                        "teamScore": 13, "opponentScore": 5}"""
                )
            )
        }
        Seeder.merge(json, db.dao())
        assertEquals(2, db.dao().gamesOnce().size)
    }

    @Test
    fun `merge fills result of an existing resultless game but never changes an existing result`() =
        runTest {
            val dao = db.dao()
            dao.insertGame(
                com.example.data.Game(date = "2026-02-06", opponent = "Bethune-Cookman", season = "2026")
            )
            Seeder.merge(seedJson(), dao)
            assertEquals(12, dao.gamesOnce().single().teamScore)

            // A second merge with a different result must NOT overwrite.
            val altered = seedJson().apply {
                getJSONArray("games").getJSONObject(0).put("teamScore", 99)
            }
            Seeder.merge(altered, dao)
            assertEquals(12, dao.gamesOnce().single().teamScore)
        }

    @Test
    fun `merge never adds lines to a game that already has any`() = runTest {
        val dao = db.dao()
        Seeder.merge(seedJson(), dao)
        val game = dao.gamesOnce().single()
        val player = dao.playersOnce().single()
        // User records their own corrected line set: one line only.
        dao.statLinesOnce().forEach { dao.deleteStatLine(it) }
        dao.upsertStatLine(
            StatLine(playerId = player.id, gameId = game.id, atBats = 4, hits = 3)
        )

        Seeder.merge(seedJson(), dao)
        val lines = dao.statLinesOnce()
        assertEquals(1, lines.size)
        assertEquals(3, lines.single().hits)
    }

    @Test
    fun `merge refreshes roster facts on existing players without duplicating them`() = runTest {
        Seeder.merge(seedJson(), db.dao())
        // Next season: new number, new position, off the roster.
        val nextSeason = seedJson().apply {
            getJSONArray("players").getJSONObject(0)
                .put("jerseyNumber", "12")
                .put("position", "OF")
                .put("active", false)
        }
        Seeder.merge(nextSeason, db.dao())
        val player = db.dao().playersOnce().single()
        assertEquals("12", player.jerseyNumber)
        assertEquals("OF", player.position)
        assertEquals(false, player.active)
    }

    @Test
    fun `blank seed fields never erase existing roster facts`() = runTest {
        Seeder.merge(seedJson(), db.dao())
        val blanked = seedJson().apply {
            getJSONArray("players").getJSONObject(0)
                .put("jerseyNumber", "")
                .put("position", "")
        }
        Seeder.merge(blanked, db.dao())
        val player = db.dao().playersOnce().single()
        assertEquals("1", player.jerseyNumber)
        assertEquals("P", player.position)
    }

    @Test
    fun `player missing an active flag defaults to active and user-added players are untouched`() =
        runTest {
            val dao = db.dao()
            dao.insertPlayer(
                com.example.data.Player(name = "Hand Entered", jerseyNumber = "99", active = false)
            )
            Seeder.merge(seedJson(), dao)
            val byName = dao.playersOnce().associateBy { it.name }
            assertEquals(true, byName.getValue("Ada Alpha").active)
            assertEquals(false, byName.getValue("Hand Entered").active)
            assertEquals("99", byName.getValue("Hand Entered").jerseyNumber)
        }

    @Test
    fun `schedule fields land on new games and fill in on existing ones`() = runTest {
        val dao = db.dao()
        // An away game arriving fresh from the schedule scrape, not yet played.
        Seeder.merge(
            JSONObject(
                """{"players": [], "games": [
                     {"date":"2027-02-05","opponent":"Creighton","season":"2027",
                      "site":"A","startTime":"6 p.m. CT"}]}"""
            ),
            dao
        )
        val upcoming = dao.gamesOnce().single()
        assertEquals("A", upcoming.site)
        assertEquals("6 p.m. CT", upcoming.startTime)
        assertNull(upcoming.teamScore)

        // A game recorded before the schedule scrape existed picks up its
        // site and first pitch without disturbing anything else.
        dao.insertGame(
            com.example.data.Game(
                date = "2026-03-01", opponent = "Arkansas", season = "2026",
                teamScore = 3, opponentScore = 11
            )
        )
        Seeder.merge(
            JSONObject(
                """{"players": [], "games": [
                     {"date":"2026-03-01","opponent":"Arkansas","season":"2026",
                      "site":"A","startTime":"1 p.m. CT","teamScore":3,"opponentScore":11}]}"""
            ),
            dao
        )
        val played = dao.gamesOnce().first { it.date == "2026-03-01" }
        assertEquals("A", played.site)
        assertEquals("1 p.m. CT", played.startTime)
        assertEquals(3, played.teamScore)
    }

    @Test
    fun `a hand-set site is never overwritten by the seed`() = runTest {
        val dao = db.dao()
        dao.insertGame(
            com.example.data.Game(
                date = "2026-04-15", opponent = "Missouri", season = "2026", site = "H"
            )
        )
        Seeder.merge(
            JSONObject(
                """{"players": [], "games": [
                     {"date":"2026-04-15","opponent":"Missouri","season":"2026","site":"N"}]}"""
            ),
            dao
        )
        assertEquals("H", dao.gamesOnce().single().site)
    }

    @Test
    fun `unknown player in lines is skipped without error`() = runTest {
        val json = seedJson().apply {
            getJSONArray("games").getJSONObject(0).getJSONArray("lines").getJSONObject(0)
                .put("player", "Nobody Known")
        }
        Seeder.merge(json, db.dao())
        assertEquals(0, db.dao().statLinesOnce().size)
        assertEquals(1, db.dao().gamesOnce().size)
    }
}
