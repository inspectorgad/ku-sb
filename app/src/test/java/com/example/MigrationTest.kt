package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.JayhawksDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room's schema is not exported (`exportSchema = false`), so there is no golden
 * schema to diff against. These tests are the only guard that upgrading a phone
 * that already holds a season of data actually works: a typo in the migration
 * SQL, or a column added to an entity but forgotten in the migration, surfaces
 * on first open after the upgrade rather than at build time.
 *
 * The migrations are driven directly against hand-built old databases rather
 * than through MigrationTestHelper, which expects an instrumented environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
    }

    /** The schema exactly as v1 shipped: three tables, no standings, no site. */
    private fun openV1(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val created = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE players (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL, jerseyNumber TEXT NOT NULL,
                                position TEXT NOT NULL, active INTEGER NOT NULL)"""
                        )
                        db.execSQL(
                            """CREATE TABLE games (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                date TEXT NOT NULL, opponent TEXT NOT NULL, season TEXT NOT NULL,
                                teamScore INTEGER, opponentScore INTEGER, inningScores TEXT,
                                teamHits INTEGER, opponentHits INTEGER,
                                teamErrors INTEGER, opponentErrors INTEGER)"""
                        )
                        db.execSQL(
                            """CREATE TABLE stat_lines (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                playerId INTEGER NOT NULL, gameId INTEGER NOT NULL,
                                atBats INTEGER NOT NULL, runs INTEGER NOT NULL, hits INTEGER NOT NULL,
                                doubles INTEGER NOT NULL, triples INTEGER NOT NULL,
                                homeRuns INTEGER NOT NULL, runsBattedIn INTEGER NOT NULL,
                                walks INTEGER NOT NULL, strikeouts INTEGER NOT NULL,
                                hitByPitch INTEGER NOT NULL, stolenBases INTEGER NOT NULL,
                                caughtStealing INTEGER NOT NULL, sacrificeFlies INTEGER NOT NULL,
                                sacrificeHits INTEGER NOT NULL, started INTEGER NOT NULL,
                                pitched INTEGER NOT NULL, outsPitched INTEGER NOT NULL,
                                hitsAllowed INTEGER NOT NULL, runsAllowed INTEGER NOT NULL,
                                earnedRuns INTEGER NOT NULL, walksAllowed INTEGER NOT NULL,
                                pitcherStrikeouts INTEGER NOT NULL, homeRunsAllowed INTEGER NOT NULL,
                                win INTEGER NOT NULL, loss INTEGER NOT NULL, save INTEGER NOT NULL)"""
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        helper = created
        return created.writableDatabase
    }

    private fun migrateAll(db: SupportSQLiteDatabase) {
        for (m in JayhawksDatabase.migrations()) m.migrate(db)
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val found = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) found += c.getString(nameIndex)
        }
        return found
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { if (it.moveToFirst()) it.getInt(0) else -1 }

    @Test
    fun `a v1 database with real data survives every migration to the current version`() {
        val db = openV1()
        db.execSQL("INSERT INTO players VALUES (1, 'Ada Alpha', '7', 'P', 1)")
        db.execSQL(
            """INSERT INTO games VALUES
               (1, '2026-04-17', 'UCF', '2026', 3, 6, '0-1, 2-0', 7, 9, 1, 0)"""
        )
        db.execSQL(
            """INSERT INTO stat_lines VALUES
               (1, 1, 1, 4, 1, 2, 1, 0, 0, 2, 1, 1, 0, 1, 0, 0, 0, 1, 1, 18, 9, 6, 5, 2, 7, 1, 0, 1, 0)"""
        )

        migrateAll(db)

        // Pre-existing values are untouched...
        db.query("SELECT opponent, teamScore, inningScores FROM games").use {
            assertTrue(it.moveToFirst())
            assertEquals("UCF", it.getString(0))
            assertEquals(3, it.getInt(1))
            assertEquals("0-1, 2-0", it.getString(2))
        }
        db.query("SELECT atBats, hits, earnedRuns FROM stat_lines").use {
            assertTrue(it.moveToFirst())
            assertEquals(4, it.getInt(0))
            assertEquals(2, it.getInt(1))
            assertEquals(5, it.getInt(2))
        }
        // ...and every column each migration promised now exists.
        val gameCols = columns(db, "games")
        for (c in listOf(
            "site", "startTime", "venue", "attendance", "boxScoreUrl", "scoringSummary",
            "opponentRecord", "opponentRank"
        )) {
            assertTrue("games is missing $c", c in gameCols)
        }
        val lineCols = columns(db, "stat_lines")
        for (c in listOf(
            "lineupSpot", "position", "substitute", "strikeoutsLooking", "reachedOnError",
            "fieldersChoice", "groundOuts", "flyOuts", "groundedIntoDoublePlay",
            "intentionalWalks", "pickedOff", "putouts", "assists", "errors", "passedBalls",
            "stolenBasesAgainst", "caughtStealingBy", "doublePlaysTurned", "pitchCount",
            "battersFaced", "wildPitches", "battersHit", "balks", "inheritedRunners",
            "inheritedRunnersScored", "completeGames", "shutouts", "gamesStartedPitching",
            "pitcherStrikeoutsLooking", "opponentAtBats"
        )) {
            assertTrue("stat_lines is missing $c", c in lineCols)
        }
        // The v2 tables exist and are queryable; their contents are covered
        // by the dedicated test below.
        assertEquals(0, count(db, "SELECT COUNT(*) FROM standings"))
    }

    @Test
    fun `the standings and poll tables are created by the v1 to v2 step`() {
        val db = openV1()
        migrateAll(db)
        assertEquals(0, count(db, "SELECT COUNT(*) FROM standings"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM poll_entries"))
        assertTrue("seo" in columns(db, "standings"))
        assertTrue("rpiRank" in columns(db, "standings"))
        assertTrue("firstPlaceVotes" in columns(db, "poll_entries"))
    }

    /**
     * The v2 -> v3 cleanup exists because kuathletics served the Mar 1 2026
     * Arkansas box score dated 3/1/1926, and devices that synced before the
     * fix hold a phantom game that shows up as its own "1926" season.
     */
    @Test
    fun `the v2 to v3 step removes a phantom pre-2000 game and its lines`() {
        val db = openV1()
        db.execSQL("INSERT INTO players VALUES (1, 'Ada Alpha', '7', 'P', 1)")
        db.execSQL("INSERT INTO games VALUES (1, '1926-03-01', 'Arkansas', '1926', 3, 11, NULL, NULL, NULL, NULL, NULL)")
        db.execSQL("INSERT INTO games VALUES (2, '2026-03-01', 'Arkansas', '2026', 3, 11, NULL, NULL, NULL, NULL, NULL)")
        db.execSQL("INSERT INTO stat_lines VALUES (1, 1, 1, 3, 0, 1, 0,0,0,0,0,0,0,0,0,0,0, 1, 0,0,0,0,0,0,0,0,0,0,0)")
        db.execSQL("INSERT INTO stat_lines VALUES (2, 1, 2, 3, 0, 1, 0,0,0,0,0,0,0,0,0,0,0, 1, 0,0,0,0,0,0,0,0,0,0,0)")

        migrateAll(db)

        assertEquals(1, count(db, "SELECT COUNT(*) FROM games"))
        db.query("SELECT date FROM games").use {
            assertTrue(it.moveToFirst())
            assertEquals("2026-03-01", it.getString(0))
        }
        // The phantom's stat line goes with it, leaving no orphan.
        assertEquals(1, count(db, "SELECT COUNT(*) FROM stat_lines"))
        assertEquals(
            0,
            count(db, "SELECT COUNT(*) FROM stat_lines WHERE gameId NOT IN (SELECT id FROM games)")
        )
    }

    /**
     * The target is read off the @Database annotation rather than written out
     * here, so bumping the schema without adding a migration fails this test
     * instead of quietly needing the number updated in two places.
     */
    @Test
    fun `migrations are contiguous from 1 to the current version`() {
        val declared = JayhawksDatabase::class.java
            .getAnnotation(androidx.room.Database::class.java)!!.version
        val steps = JayhawksDatabase.migrations().sortedBy { it.startVersion }
        var expected = 1
        for (m in steps) {
            assertEquals("gap before migration ${m.startVersion}", expected, m.startVersion)
            assertEquals("migration ${m.startVersion} should step by one", expected + 1, m.endVersion)
            expected = m.endVersion
        }
        assertEquals("migrations must reach the database version", declared, expected)
    }
}
