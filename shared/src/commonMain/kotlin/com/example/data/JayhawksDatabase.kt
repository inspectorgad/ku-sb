package com.example.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [Player::class, Game::class, StatLine::class,
        ConferenceStanding::class, PollEntry::class, OpponentStatLine::class, RankingSnapshot::class],
    version = 9,
    exportSchema = false
)
@ConstructedBy(JayhawksDatabaseConstructor::class)
abstract class JayhawksDatabase : RoomDatabase() {
    abstract fun dao(): JayhawksDao

    // Each phone opens the database its own way: JayhawksDatabase.get(context)
    // on Android (androidMain), createDatabase() on iPhone (iosMain). The
    // migrations live with the Android opener, because only Android has an
    // install old enough to need them.
    companion object
}

/** Builds the database class on every platform; Room's compiler writes each platform's version. */
@Suppress("KotlinNoActualForExpect")
expect object JayhawksDatabaseConstructor : RoomDatabaseConstructor<JayhawksDatabase> {
    override fun initialize(): JayhawksDatabase
}
