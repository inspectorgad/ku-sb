package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One team's row in a season's Big 12 standings, computed by the nightly
 * scrape from every conference team's scoreboard results.
 */
@Entity(tableName = "standings", primaryKeys = ["season", "seo"])
data class ConferenceStanding(
    val season: String,
    val seo: String,
    val team: String,
    val confW: Int = 0,
    val confL: Int = 0,
    val overallW: Int = 0,
    val overallL: Int = 0,
    // National poll rank as of the season's latest poll snapshot.
    val nationalRank: Int? = null,
    // NCAA RPI — softball's selection metric (basketball uses NET).
    val rpiRank: Int? = null
) {
    val confPct: Double get() = (confW + confL).let { if (it == 0) 0.0 else confW.toDouble() / it }
    val overallPct: Double get() = (overallW + overallL).let { if (it == 0) 0.0 else overallW.toDouble() / it }
}

/**
 * One row of a national poll snapshot (USA Today/NFCA coaches poll). The
 * endpoint only serves the current poll, so each season keeps the latest
 * capture — which at season's end is that season's final poll.
 */
@Entity(tableName = "poll_entries", primaryKeys = ["season", "team"])
data class PollEntry(
    val season: String,
    val team: String,
    val rank: Int,
    // Preserves ties as published, e.g. "T-22".
    val rankLabel: String,
    val record: String = "",
    val points: String = "",
    val previous: String = "",
    val firstPlaceVotes: Int = 0,
    val big12: Boolean = false,
    val pollName: String = "",
    val updated: String = ""
)

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jerseyNumber: String = "",
    val position: String = "",
    // On the current roster. Maintained by the nightly roster scrape; former
    // players keep their stats but are shown in a separate roster section.
    val active: Boolean = true
)

// Dates are stored as ISO yyyy-MM-dd strings so lexicographic order matches
// chronological order without needing java.time (minSdk 24).
@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val opponent: String,
    // Softball seasons fall in a single calendar year; labeled like "2026".
    val season: String,
    // Final score. Null until played.
    val teamScore: Int? = null,
    val opponentScore: Int? = null,
    // Where the game is played, from the schedule scrape: "H" home, "A" away,
    // "N" neutral site, "" unknown. Drives "vs" versus "at" in the UI.
    val site: String = "",
    // Scheduled start ("6 p.m. CT") for games that haven't been played yet.
    val startTime: String = "",
    // Per-inning runs from KU's perspective, e.g. "0-1, 2-0, 1-0, 0-0, 3-1"
    // (extra innings simply append).
    val inningScores: String? = null,
    // Hits and errors for the R/H/E line. Null when unknown.
    val teamHits: Int? = null,
    val opponentHits: Int? = null,
    val teamErrors: Int? = null,
    val opponentErrors: Int? = null
)

/**
 * One player's line for one game: batting counting stats plus, when the
 * player took the circle, their pitching line ([pitched] false leaves every
 * pitching column at zero and hides pitching in the UI).
 */
@Entity(
    tableName = "stat_lines",
    foreignKeys = [
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("gameId"),
        Index(value = ["playerId", "gameId"], unique = true)
    ]
)
data class StatLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerId: Long,
    val gameId: Long,
    // Batting
    val atBats: Int = 0,
    val runs: Int = 0,
    val hits: Int = 0,
    val doubles: Int = 0,
    val triples: Int = 0,
    val homeRuns: Int = 0,
    val runsBattedIn: Int = 0,
    val walks: Int = 0,
    val strikeouts: Int = 0,
    val hitByPitch: Int = 0,
    val stolenBases: Int = 0,
    val caughtStealing: Int = 0,
    val sacrificeFlies: Int = 0,
    val sacrificeHits: Int = 0,
    val started: Boolean = false,
    // Pitching. Innings are stored as outs recorded (7.1 IP = 22 outs) so
    // season totals add correctly.
    val pitched: Boolean = false,
    val outsPitched: Int = 0,
    val hitsAllowed: Int = 0,
    val runsAllowed: Int = 0,
    val earnedRuns: Int = 0,
    val walksAllowed: Int = 0,
    val pitcherStrikeouts: Int = 0,
    val homeRunsAllowed: Int = 0,
    val win: Boolean = false,
    val loss: Boolean = false,
    val save: Boolean = false
)
