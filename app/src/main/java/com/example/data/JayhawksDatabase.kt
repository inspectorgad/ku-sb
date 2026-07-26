package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Player::class, Game::class, StatLine::class,
        ConferenceStanding::class, PollEntry::class],
    version = 4,
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

        fun get(context: Context): JayhawksDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JayhawksDatabase::class.java,
                    "ku_sb.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}
