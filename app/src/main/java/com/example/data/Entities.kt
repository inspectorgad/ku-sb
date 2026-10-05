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
    // Roster-page detail, blank for anyone not on the current roster — only
    // that page carries it, so a player who last appeared in an earlier
    // season keeps whatever was recorded then. "batsThrows" is which hand
    // she bats and throws with, as "L/R".
    val academicYear: String = "",
    val height: String = "",
    val batsThrows: String = "",
    val hometown: String = "",
    val lastSchool: String = "",
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
    val opponentErrors: Int? = null,
    // Game context from the box score: where it was played, the announced
    // crowd (0 = not reported) and a link to the official box score.
    val venue: String = "",
    val attendance: Int = 0,
    val boxScoreUrl: String = "",
    // How every run scored, as a compact encoded list — one play per line:
    // "inning|ku(1/0)|usScore|themScore|narrative". Stored denormalised
    // because it is display-only and always read whole with its game.
    val scoringSummary: String = "",
    // Who the opponent was on the day: their overall record including this
    // game ("36-13", "" if unreported) and their national rank at the time
    // (0 = unranked). Both describe THIS game rather than how the opponent's
    // season finished, which is what makes a result readable years later.
    val opponentRecord: String = "",
    val opponentRank: Int = 0,
    // What it was like to be there, from the box score's own header.
    val stadium: String = "",
    val firstPitch: String = "",
    val duration: String = "",
    val weather: String = "",
    // The crew, flattened: "Home Plate: … · First: …".
    val umpires: String = "",
    // The event this game belonged to, named by the schedule rather than
    // guessed from its date ("USF-Rawlings Invitational").
    val event: String = "",
    // A link to the official PDF box score, alongside the HTML one.
    val pdfUrl: String = "",
    // How long the game was scheduled to be, which is the only way to tell a
    // run-rule ending from a game that was called. 0 when unknown.
    val scheduledInnings: Int = 0
)

/**
 * One opposing player's line in one game against Kansas.
 *
 * Kept in its own table rather than through [Player] and [StatLine] on
 * purpose: these are not Kansas players and must never reach the roster, a
 * leaderboard, or a career total. The name is stored as text for the same
 * reason — there is nothing to join to.
 *
 * Deliberately fewer columns than a Kansas line carries. This exists so an
 * opposing box score can be read — who hit, who pitched, who made the plays —
 * not so an opponent's season rates can be computed from the two or three
 * games they played against Kansas, which would be a worse number than none.
 *
 * One caveat worth knowing: softball scoring allows an error charged to the
 * team rather than to a fielder, so the errors here can sum to less than the
 * game's reported total. It happened once in 2026 — Houston on March 13,
 * three errors reported and two charged to players.
 */
@Entity(
    tableName = "opponent_stat_lines",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class OpponentStatLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val playerName: String,
    val jerseyNumber: String = "",
    val position: String = "",
    val lineupSpot: Int = 0,
    val started: Boolean = false,
    val substitute: Boolean = false,
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
    // Fielding
    val putouts: Int = 0,
    val assists: Int = 0,
    val errors: Int = 0,
    // Pitching
    val pitched: Boolean = false,
    val outsPitched: Int = 0,
    val hitsAllowed: Int = 0,
    val runsAllowed: Int = 0,
    val earnedRuns: Int = 0,
    val walksAllowed: Int = 0,
    val pitcherStrikeouts: Int = 0,
    val homeRunsAllowed: Int = 0,
    val pitchCount: Int = 0,
    val battersFaced: Int = 0,
    val win: Boolean = false,
    val loss: Boolean = false,
    val save: Boolean = false
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
    val save: Boolean = false,
    // Where this player hit in the order (1-9; 0 when not in the lineup) and
    // the position played in THIS game, so a box score can be shown in
    // batting order with substitutes marked.
    val lineupSpot: Int = 0,
    val position: String = "",
    val substitute: Boolean = false,
    // Advanced hitting detail. Sparse — the common value is zero.
    val strikeoutsLooking: Int = 0,
    val reachedOnError: Int = 0,
    val fieldersChoice: Int = 0,
    val groundOuts: Int = 0,
    val flyOuts: Int = 0,
    val groundedIntoDoublePlay: Int = 0,
    val intentionalWalks: Int = 0,
    val pickedOff: Int = 0,
    // Fielding — the third stat category, alongside hitting and pitching.
    val putouts: Int = 0,
    val assists: Int = 0,
    val errors: Int = 0,
    val passedBalls: Int = 0,
    val stolenBasesAgainst: Int = 0,
    val caughtStealingBy: Int = 0,
    val doublePlaysTurned: Int = 0,
    // Pitching workload and efficiency.
    val pitchCount: Int = 0,
    val battersFaced: Int = 0,
    val wildPitches: Int = 0,
    val battersHit: Int = 0,
    val balks: Int = 0,
    val inheritedRunners: Int = 0,
    val inheritedRunnersScored: Int = 0,
    val completeGames: Int = 0,
    val shutouts: Int = 0,
    val gamesStartedPitching: Int = 0,
    val pitcherStrikeoutsLooking: Int = 0,
    val opponentAtBats: Int = 0
)
