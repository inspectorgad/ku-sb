package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Game
import com.example.stats.gameEnding
import com.example.stats.GameEnding
import com.example.data.JayhawksDatabase
import com.example.data.Seeder
import com.example.data.StatLine
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `harvested box-score detail lands on games and lines`() = runTest {
        val dao = db.dao()
        Seeder.merge(
            JSONObject(
                """
                {"players": [{"name": "Ada Alpha", "jerseyNumber": "1", "position": "P"}],
                 "games": [{"date": "2026-04-17", "opponent": "UCF", "season": "2026",
                   "teamScore": 3, "opponentScore": 6,
                   "venue": "Lawrence, Kan.", "attendance": 1807,
                   "opponentRecord": "34-11-1", "opponentRank": 20,
                   "boxScoreUrl": "https://kuathletics.com/box/20500",
                   "scoring": [
                     {"inn": 1, "ku": false, "text": "B. Damon doubled, RBI; A. Evans scored.", "us": 0, "them": 1},
                     {"inn": 3, "ku": true, "text": "Soles flied out to rf, SF, RBI; Limbaugh scored.", "us": 2, "them": 1}
                   ],
                   "lines": [{"player": "Ada Alpha", "gs": 1, "spot": 3, "pos": "ss", "ab": 4, "h": 2,
                              "kl": 1, "roe": 1, "go": 2, "ao": 1, "gidp": 1,
                              "po": 2, "a": 3, "e": 1, "dp": 1,
                              "p": 1, "outs": 18, "np": 97, "bf": 27, "wp": 2, "cg": 1}]}]}
                """
            ),
            dao
        )
        val game = dao.gamesOnce().single()
        assertEquals("Lawrence, Kan.", game.venue)
        assertEquals(1807, game.attendance)
        assertEquals("https://kuathletics.com/box/20500", game.boxScoreUrl)
        // Who UCF were on the day, not how their season finished.
        assertEquals("34-11-1", game.opponentRecord)
        assertEquals(20, game.opponentRank)
        // Encoded one play per line: inning|ku|us|them|narrative
        val rows = game.scoringSummary.split("\n")
        assertEquals(2, rows.size)
        assertTrue(rows[0].startsWith("1|0|0|1|"))
        assertTrue(rows[1].startsWith("3|1|2|1|"))
        assertTrue(rows[1].endsWith("Limbaugh scored."))

        val line = dao.statLinesOnce().single()
        assertEquals(3, line.lineupSpot)
        assertEquals("ss", line.position)
        assertEquals(1, line.strikeoutsLooking)
        assertEquals(1, line.reachedOnError)
        assertEquals(2, line.groundOuts)
        assertEquals(1, line.groundedIntoDoublePlay)
        assertEquals(2, line.putouts)
        assertEquals(3, line.assists)
        assertEquals(1, line.errors)
        assertEquals(97, line.pitchCount)
        assertEquals(27, line.battersFaced)
        assertEquals(2, line.wildPitches)
        assertEquals(1, line.completeGames)
    }

    @Test
    fun `game context fills in on an existing game but never overwrites it`() = runTest {
        val dao = db.dao()
        // A game recorded before any of these columns existed.
        dao.insertGame(
            Game(date = "2026-04-17", opponent = "UCF", season = "2026",
                teamScore = 3, opponentScore = 6, venue = "Hand-typed Park",
                opponentRecord = "hand-typed record")
        )
        Seeder.merge(
            JSONObject(
                """
                {"players": [], "games": [{"date": "2026-04-17", "opponent": "UCF", "season": "2026",
                  "teamScore": 3, "opponentScore": 6,
                  "venue": "Scraped Stadium", "attendance": 900,
                  "opponentRecord": "34-11-1", "opponentRank": 20,
                  "boxScoreUrl": "https://kuathletics.com/box/20500",
                  "scoring": [{"inn": 2, "ku": true, "text": "Cripe homered.", "us": 1, "them": 0}]}]}
                """
            ),
            dao
        )
        val game = dao.gamesOnce().single()
        assertEquals("Hand-typed Park", game.venue)   // never overwritten
        assertEquals(900, game.attendance)            // filled, was blank
        assertEquals("https://kuathletics.com/box/20500", game.boxScoreUrl)
        assertEquals("hand-typed record", game.opponentRecord)  // never overwritten
        assertEquals(20, game.opponentRank)                     // filled, was 0
        assertTrue(game.scoringSummary.startsWith("2|1|1|0|Cripe homered."))
    }

    @Test
    fun `a seed with no scoring summary leaves the stored one alone`() = runTest {
        val dao = db.dao()
        Seeder.merge(seedJson(), dao)
        val before = dao.gamesOnce().single()
        assertEquals("", before.scoringSummary)
        // Older payloads simply omit the key; that must not throw or clobber.
        Seeder.merge(seedJson(), dao)
        assertEquals("", dao.gamesOnce().single().scoringSummary)
    }

    /**
     * The opposing side of a box score. Scraper-owned, so a sync replaces a
     * game's rows outright — and crucially these must never reach the roster,
     * where they would turn up in leaderboards and career totals.
     */
    @Test
    fun `opposing lines land on their own table and never on the roster`() = runTest {
        val dao = db.dao()
        Seeder.merge(
            JSONObject(
                """
                {"players": [{"name": "Ada Alpha", "jerseyNumber": "1", "position": "SS"}],
                 "games": [{"date": "2026-04-17", "opponent": "UCF", "season": "2026",
                   "teamScore": 3, "opponentScore": 6,
                   "lines": [{"player": "Ada Alpha", "ab": 4, "h": 2}],
                   "opponentLines": [
                     {"player": "Aubrey Evans", "number": "3", "pos": "ss", "spot": 1,
                      "gs": 1, "ab": 3, "r": 1, "h": 1, "bb": 2, "po": 2, "a": 3},
                     {"player": "Isabella Vega", "number": "9", "pos": "p", "spot": 10,
                      "p": 1, "outs": 21, "ha": 7, "ra": 3, "er": 3, "ks": 4,
                      "np": 102, "bf": 28, "w": 1}
                   ]}]}
                """
            ),
            dao
        )
        val theirs = dao.opponentStatLinesOnce()
        assertEquals(2, theirs.size)
        val evans = theirs.first { it.playerName == "Aubrey Evans" }
        assertEquals("3", evans.jerseyNumber)
        assertEquals(1, evans.lineupSpot)
        assertTrue(evans.started)
        assertEquals(3, evans.assists)
        val vega = theirs.first { it.playerName == "Isabella Vega" }
        assertTrue(vega.pitched)
        assertEquals(21, vega.outsPitched)
        assertEquals(102, vega.pitchCount)
        assertTrue(vega.win)
        // The roster is Kansas only. This is the whole point of the separate
        // table: an opposing name here would surface on the Leaders board.
        assertEquals(listOf("Ada Alpha"), dao.playersOnce().map { it.name })
        assertEquals(1, dao.statLinesOnce().size)
    }

    @Test
    fun `re-syncing replaces a game's opposing lines rather than doubling them`() = runTest {
        val dao = db.dao()
        val json = """
            {"players": [], "games": [{"date": "2026-04-17", "opponent": "UCF", "season": "2026",
              "teamScore": 3, "opponentScore": 6,
              "opponentLines": [{"player": "Aubrey Evans", "ab": 3, "h": 1}]}]}
        """
        Seeder.merge(JSONObject(json), dao)
        Seeder.merge(JSONObject(json), dao)
        assertEquals(1, dao.opponentStatLinesOnce().size)
    }

    @Test
    fun `roster detail arrives and a blank never erases it`() = runTest {
        val dao = db.dao()
        Seeder.merge(
            JSONObject(
                """
                {"players": [{"name": "Tehya Pitts", "jerseyNumber": "1", "position": "OF",
                  "academicYear": "Sr.", "height": "5' 4''", "batsThrows": "L/L",
                  "hometown": "Corinth, Texas", "lastSchool": "McLennan Community College"}],
                 "games": []}
                """
            ),
            dao
        )
        val pitts = dao.playersOnce().single()
        assertEquals("Sr.", pitts.academicYear)
        assertEquals("L/L", pitts.batsThrows)
        assertEquals("Corinth, Texas", pitts.hometown)

        // A later sync from a page that no longer lists her — she graduated —
        // must not blank the class year she had.
        Seeder.merge(
            JSONObject("""{"players": [{"name": "Tehya Pitts", "active": false}], "games": []}"""),
            dao
        )
        val after = dao.playersOnce().single()
        assertEquals("Sr.", after.academicYear)
        assertEquals("Corinth, Texas", after.hometown)
        assertTrue(!after.active)
    }

    @Test
    fun `game context lands, including the scheduled innings`() = runTest {
        val dao = db.dao()
        Seeder.merge(
            JSONObject(
                """
                {"players": [], "games": [{"date": "2026-02-06", "opponent": "Bethune-Cookman",
                  "season": "2026", "teamScore": 12, "opponentScore": 0,
                  "inningScores": "6-0, 0-0, 2-0, 0-0, 4-0",
                  "stadium": "USF Softball Stadium", "firstPitch": "9 am",
                  "duration": "1:51", "weather": "Sunny and 45",
                  "umpires": "Home Plate: Brady Sanderson", "event": "USF-Rawlings Invitational",
                  "pdfUrl": "https://example.test/box.pdf", "scheduledInnings": 7}]}
                """
            ),
            dao
        )
        val game = dao.gamesOnce().single()
        assertEquals("USF Softball Stadium", game.stadium)
        assertEquals("1:51", game.duration)
        assertEquals("USF-Rawlings Invitational", game.event)
        assertEquals(7, game.scheduledInnings)
        assertTrue(game.umpires.startsWith("Home Plate"))
        // Five innings of a scheduled seven, won by twelve: the run rule.
        assertEquals(GameEnding.RUN_RULE, gameEnding(game))
    }

    /**
     * The ranking history shipped in the seed for weeks and nothing read it,
     * so every sync imported the season and discarded the archive. This is
     * the test that would have caught that.
     */
    @Test
    fun `the weekly ranking history is imported`() = runTest {
        val dao = db.dao()
        Seeder.merge(
            JSONObject(
                """
                {"players": [], "games": [],
                 "rankingHistory": [
                   {"date": "2026-06-04", "source": "rpi", "season": "2026",
                    "teams": [{"team": "Kansas", "rank": 37, "record": "36-21"},
                              {"team": "Texas Tech", "rank": 7, "record": "61-10"},
                              {"team": "Nobody", "rank": 0}]},
                   {"date": "2026-05-28", "source": "rpi", "season": "2026",
                    "teams": [{"team": "Kansas", "rank": 41, "record": "34-20"}]}
                 ]}
                """
            ),
            dao
        )
        val rows = dao.rankingSnapshotsOnce()
        // The unranked team is not a row: storing a zero would draw it at the
        // top of a trend line.
        assertEquals(3, rows.size)
        val ku = rows.filter { it.team == "Kansas" }.sortedBy { it.date }
        assertEquals(listOf(41, 37), ku.map { it.rank })
        assertEquals("36-21", ku.last().record)
    }

    @Test
    fun `re-syncing a week replaces it rather than stacking a copy`() = runTest {
        val dao = db.dao()
        val json = """
            {"players": [], "games": [],
             "rankingHistory": [{"date": "2026-06-04", "source": "rpi", "season": "2026",
               "teams": [{"team": "Kansas", "rank": 37, "record": "36-21"}]}]}
        """
        Seeder.merge(JSONObject(json), dao)
        Seeder.merge(JSONObject(json), dao)
        assertEquals(1, dao.rankingSnapshotsOnce().size)
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
