package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Player::class, Game::class, StatLine::class,
        ConferenceStanding::class, PollEntry::class, OpponentStatLine::class],
    version = 8,
    exportSchema = false
)
abstract class JayhawksDatabase : RoomDatabase() {
    abstract fun dao(): JayhawksDao

    companion object {
        @Volatile
        private var instance: JayhawksDatabase? = null

        // v1 -> v2: Big 12 standings and national poll snapshots.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS standings (
                        season TEXT NOT NULL, seo TEXT NOT NULL, team TEXT NOT NULL,
                        confW INTEGER NOT NULL, confL INTEGER NOT NULL,
                        overallW INTEGER NOT NULL, overallL INTEGER NOT NULL,
                        nationalRank INTEGER, rpiRank INTEGER,
                        PRIMARY KEY(season, seo))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS poll_entries (
                        season TEXT NOT NULL, team TEXT NOT NULL, rank INTEGER NOT NULL,
                        rankLabel TEXT NOT NULL, record TEXT NOT NULL, points TEXT NOT NULL,
                        previous TEXT NOT NULL, firstPlaceVotes INTEGER NOT NULL,
                        big12 INTEGER NOT NULL, pollName TEXT NOT NULL, updated TEXT NOT NULL,
                        PRIMARY KEY(season, team))"""
                )
            }
        }

        // v2 -> v3: one-time cleanup of games seeded from a bad source date.
        // kuathletics served the Mar 1 2026 Arkansas box score dated 3/1/1926;
        // devices that synced before the scraper's century fix hold a phantom
        // game that shows up as its own "1926" season everywhere seasons are
        // listed. Nothing before 2000 can be a real game in this app, and the
        // corrected seed re-adds the same game under 2026, so dropping these
        // rows is safe — the seed merge cannot delete them on its own because
        // it only ever gap-fills.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """DELETE FROM stat_lines WHERE gameId IN
                        (SELECT id FROM games WHERE substr(date, 1, 4) < '2000')"""
                )
                db.execSQL("DELETE FROM games WHERE substr(date, 1, 4) < '2000'")
            }
        }

        // v3 -> v4: home/away/neutral and scheduled start time, so the full
        // posted schedule (including games not yet played) can be shown.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN site TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN startTime TEXT NOT NULL DEFAULT ''")
            }
        }

        // v4 -> v5: the box-score detail the scrape always downloaded and the
        // seed used to discard — advanced hitting, fielding, pitching workload,
        // lineup slot — plus per-game context and the scoring summary.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN `venue` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN `attendance` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN `boxScoreUrl` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN `scoringSummary` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `lineupSpot` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `position` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `substitute` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `strikeoutsLooking` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `reachedOnError` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `fieldersChoice` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `groundOuts` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `flyOuts` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `groundedIntoDoublePlay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `intentionalWalks` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `pickedOff` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `putouts` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `assists` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `errors` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `passedBalls` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `stolenBasesAgainst` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `caughtStealingBy` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `doublePlaysTurned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `pitchCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `battersFaced` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `wildPitches` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `battersHit` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `balks` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `inheritedRunners` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `inheritedRunnersScored` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `completeGames` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `shutouts` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `gamesStartedPitching` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `pitcherStrikeoutsLooking` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stat_lines ADD COLUMN `opponentAtBats` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Who the opponent was on the day — their record and rank at the time. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN `opponentRecord` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN `opponentRank` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * The opposing side of every box score, which the scrape had always
         * downloaded and the seed had always discarded. Its own table because
         * these are not Kansas players: they must never reach the roster, a
         * leaderboard, or a career total, and there is nothing to join them to.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `opponent_stat_lines` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `playerName` TEXT NOT NULL,
                        `jerseyNumber` TEXT NOT NULL,
                        `position` TEXT NOT NULL,
                        `lineupSpot` INTEGER NOT NULL,
                        `started` INTEGER NOT NULL,
                        `substitute` INTEGER NOT NULL,
                        `atBats` INTEGER NOT NULL,
                        `runs` INTEGER NOT NULL,
                        `hits` INTEGER NOT NULL,
                        `doubles` INTEGER NOT NULL,
                        `triples` INTEGER NOT NULL,
                        `homeRuns` INTEGER NOT NULL,
                        `runsBattedIn` INTEGER NOT NULL,
                        `walks` INTEGER NOT NULL,
                        `strikeouts` INTEGER NOT NULL,
                        `hitByPitch` INTEGER NOT NULL,
                        `stolenBases` INTEGER NOT NULL,
                        `putouts` INTEGER NOT NULL,
                        `assists` INTEGER NOT NULL,
                        `errors` INTEGER NOT NULL,
                        `pitched` INTEGER NOT NULL,
                        `outsPitched` INTEGER NOT NULL,
                        `hitsAllowed` INTEGER NOT NULL,
                        `runsAllowed` INTEGER NOT NULL,
                        `earnedRuns` INTEGER NOT NULL,
                        `walksAllowed` INTEGER NOT NULL,
                        `pitcherStrikeouts` INTEGER NOT NULL,
                        `homeRunsAllowed` INTEGER NOT NULL,
                        `pitchCount` INTEGER NOT NULL,
                        `battersFaced` INTEGER NOT NULL,
                        `win` INTEGER NOT NULL,
                        `loss` INTEGER NOT NULL,
                        `save` INTEGER NOT NULL,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_opponent_stat_lines_gameId` " +
                        "ON `opponent_stat_lines` (`gameId`)"
                )
            }
        }

        /**
         * The roster detail and game context that were being scraped and
         * discarded: class year, height, bats/throws, hometown and last
         * school; and the ballpark, first pitch, duration, weather, umpires,
         * event, PDF box score and scheduled innings.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (c in listOf("academicYear", "height", "batsThrows", "hometown", "lastSchool")) {
                    db.execSQL("ALTER TABLE players ADD COLUMN `$c` TEXT NOT NULL DEFAULT ''")
                }
                for (c in listOf("stadium", "firstPitch", "duration", "weather", "umpires",
                                 "event", "pdfUrl")) {
                    db.execSQL("ALTER TABLE games ADD COLUMN `$c` TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE games ADD COLUMN `scheduledInnings` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Every migration in order. Exposed so MigrationTest can drive them
         * against a hand-built old database — the schema is not exported, so
         * this is the only guard that an upgrade on a phone holding real data
         * actually works.
         */
        fun migrations(): List<Migration> =
            listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        fun get(context: Context): JayhawksDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JayhawksDatabase::class.java,
                    "ku_sb.db"
                ).addMigrations(*migrations().toTypedArray())
                    .build().also { instance = it }
            }
    }
}
