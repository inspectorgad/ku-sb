package com.example

import com.example.data.ConferenceStanding
import com.example.data.Game
import com.example.data.StatLine
import com.example.stats.competitionSplits
import com.example.stats.conferenceOpponents
import com.example.stats.inningSplits
import com.example.stats.marginSplits
import com.example.stats.opponentQualitySplits
import com.example.stats.opponentRecords
import com.example.stats.playedGames
import com.example.stats.siteSplits
import com.example.stats.teamKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitsTest {

    private var nextGameId = 1L
    private var nextLineId = 1L

    private fun game(
        date: String,
        opponent: String,
        us: Int?,
        them: Int?,
        site: String = "H",
        rank: Int = 0,
        innings: String? = null,
        season: String = "2026"
    ) = Game(
        id = nextGameId++, date = date, opponent = opponent, season = season,
        teamScore = us, opponentScore = them, site = site, opponentRank = rank,
        inningScores = innings
    )

    private fun line(gameId: Long, ab: Int = 0, h: Int = 0, outs: Int = 0, er: Int = 0) =
        StatLine(
            id = nextLineId++, playerId = 1, gameId = gameId,
            atBats = ab, hits = h,
            pitched = outs > 0, outsPitched = outs, earnedRuns = er
        )

    // -- team name keys ------------------------------------------------------

    @Test
    fun `team keys match the spellings different sources use`() {
        assertEquals(teamKey("Iowa St."), teamKey("Iowa State"))
        assertEquals(teamKey("Arizona St."), teamKey("Arizona State"))
        assertEquals(teamKey("Baylor"), teamKey("Baylor (G2)"))
        assertEquals(teamKey("Texas"), teamKey("Texas (25)"))
    }

    /**
     * The one that matters: Utah State is not Utah. In 2026 KU played both,
     * and collapsing them would move two non-conference games into the Big 12.
     */
    @Test
    fun `a differently named school does not collapse into a conference team`() {
        assertTrue(teamKey("UTAH STATE") != teamKey("Utah"))
        assertTrue(teamKey("Indiana State") != teamKey("Indiana"))
    }

    // -- splits --------------------------------------------------------------

    @Test
    fun `site splits separate home away and neutral and cover every game`() {
        val games = listOf(
            game("2026-02-06", "A", 5, 1, site = "H"),
            game("2026-02-07", "B", 2, 3, site = "A"),
            game("2026-02-08", "C", 4, 0, site = "N"),
            game("2026-02-09", "D", 1, 7, site = "H")
        )
        val splits = siteSplits(games, emptyList())
        assertEquals(listOf("Home", "Away", "Neutral"), splits.map { it.label })
        assertEquals(games.size, splits.sumOf { it.games })
        val home = splits.first { it.label == "Home" }
        assertEquals("1-1", home.record)
        assertEquals(6, home.runsFor)
        assertEquals(8, home.runsAgainst)
        assertEquals(-2, home.runDifferential)
    }

    @Test
    fun `a bucket no game falls into is left out rather than shown empty`() {
        val games = listOf(game("2026-02-06", "A", 5, 1, site = "H"))
        assertEquals(listOf("Home"), siteSplits(games, emptyList()).map { it.label })
    }

    @Test
    fun `conference opponents come from the standings and exclude Kansas`() {
        val standings = listOf(
            standing("2026", "Kansas"), standing("2026", "Iowa St."),
            standing("2026", "Texas Tech"), standing("2025", "Houston")
        )
        val conference = conferenceOpponents(standings, "2026")
        assertEquals(setOf(teamKey("Iowa St."), teamKey("Texas Tech")), conference)
        // A team only in another season's standings is not this season's.
        assertTrue(teamKey("Houston") !in conference)
    }

    @Test
    fun `competition splits use the standings to decide what is a conference game`() {
        val games = listOf(
            game("2026-03-01", "Iowa State", 3, 2),
            game("2026-03-02", "Iowa State (G2)", 1, 4),
            game("2026-03-08", "Wichita State", 8, 0)
        )
        val conference = conferenceOpponents(listOf(standing("2026", "Iowa St.")), "2026")
        val splits = competitionSplits(games, emptyList(), conference)
        assertEquals(2, splits.first { it.label == "Big 12 opponents" }.games)
        assertEquals(1, splits.first { it.label == "Non-conference" }.games)
    }

    @Test
    fun `opponent quality splits key off the rank carried on the game`() {
        val games = listOf(
            game("2026-03-01", "Arkansas", 3, 11, rank = 8),
            game("2026-03-02", "Omaha", 9, 0)
        )
        val splits = opponentQualitySplits(games, emptyList())
        assertEquals("0-1", splits.first { it.label == "vs ranked" }.record)
        assertEquals("1-0", splits.first { it.label == "vs unranked" }.record)
    }

    @Test
    fun `margin splits bucket by how close the game was`() {
        val games = listOf(
            game("2026-03-01", "A", 3, 2),   // one run
            game("2026-03-02", "B", 1, 4),   // three
            game("2026-03-03", "C", 11, 1)   // ten
        )
        val splits = marginSplits(games, emptyList())
        assertEquals(1, splits.first { it.label == "One-run games" }.games)
        assertEquals(1, splits.first { it.label == "Decided by 2-4" }.games)
        assertEquals(1, splits.first { it.label == "Decided by 5+" }.games)
    }

    @Test
    fun `a split carries only the stat lines from its own games`() {
        val home = game("2026-03-01", "A", 3, 2, site = "H")
        val away = game("2026-03-02", "B", 1, 4, site = "A")
        val lines = listOf(
            line(home.id, ab = 4, h = 3),
            line(away.id, ab = 4, h = 0)
        )
        val splits = siteSplits(listOf(home, away), lines)
        assertEquals(3, splits.first { it.label == "Home" }.batting.hits)
        assertEquals(0, splits.first { it.label == "Away" }.batting.hits)
    }

    @Test
    fun `a tie counts as neither a win nor a loss`() {
        val games = listOf(game("2026-03-01", "A", 4, 4, site = "H"))
        val home = siteSplits(games, emptyList()).single()
        assertEquals(1, home.games)
        assertEquals("0-0", home.record)
    }

    @Test
    fun `only played games in the asked-for season are considered`() {
        val games = listOf(
            game("2026-03-01", "A", 3, 2),
            game("2026-03-02", "B", null, null),          // not yet played
            game("2026-09-26", "C", 0, 6, season = "Fall 2026")
        )
        assertEquals(1, playedGames(games, "2026").size)
    }

    // -- inning splits -------------------------------------------------------

    @Test
    fun `inning splits add up the line scores`() {
        val games = listOf(
            game("2026-03-01", "A", 3, 1, innings = "1-0, 0-1, 2-0"),
            game("2026-03-02", "B", 2, 2, innings = "0-2, 2-0, 0-0")
        )
        val innings = inningSplits(games)
        assertEquals(listOf(1, 2, 3), innings.map { it.inning })
        assertEquals(1, innings[0].scored)
        assertEquals(2, innings[0].allowed)
        assertEquals(2, innings[1].scored)
        assertEquals(5, innings.sumOf { it.scored })
        assertEquals(3, innings.sumOf { it.allowed })
    }

    /**
     * An "x" means that side never batted — the home team leading after the
     * visitors' last at-bat. Counting it as a zero would make the last inning
     * look like a failure to score rather than a turn nobody took.
     */
    @Test
    fun `an unbatted half inning is not counted as a scoreless one`() {
        val games = listOf(game("2026-03-01", "A", 3, 1, innings = "1-0, 2-1, x-0"))
        val third = inningSplits(games).first { it.inning == 3 }
        assertEquals(0, third.scored)
        assertEquals(0, third.gamesBatted)
        assertEquals(1, third.gamesPitched)
    }

    @Test
    fun `extra innings simply extend the list`() {
        val games = listOf(game("2026-03-01", "A", 4, 3, innings = "0-0, 1-1, 2-2, 1-0"))
        assertEquals(4, inningSplits(games).size)
    }

    @Test
    fun `a game with no line score contributes nothing rather than throwing`() {
        val games = listOf(
            game("2026-03-01", "A", 3, 1, innings = null),
            game("2026-03-02", "B", 1, 0, innings = "")
        )
        assertTrue(inningSplits(games).isEmpty())
    }

    // -- opponents -----------------------------------------------------------

    @Test
    fun `the halves of a doubleheader fold into one opponent`() {
        val games = listOf(
            game("2026-03-01", "Baylor", 3, 2),
            game("2026-03-01", "Baylor (G2)", 1, 4),
            game("2026-03-02", "Baylor", 7, 0)
        )
        val record = opponentRecords(games).single()
        assertEquals("Baylor", record.name)
        assertEquals(3, record.games)
        assertEquals("2-1", record.record)
        assertEquals(11, record.runsFor)
        assertEquals(6, record.runsAgainst)
        assertEquals("2026-03-02", record.lastPlayed)
    }

    @Test
    fun `an opponent's best rank is their lowest number across the meetings`() {
        val games = listOf(
            game("2026-04-02", "Arizona State", 5, 8, rank = 20),
            game("2026-04-04", "Arizona State", 5, 4, rank = 14),
            game("2026-04-05", "Arizona State", 2, 1, rank = 0)
        )
        assertEquals(14, opponentRecords(games).single().bestRank)
    }

    @Test
    fun `an opponent never ranked reports no rank rather than rank zero winning`() {
        val games = listOf(game("2026-03-01", "Omaha", 9, 0))
        assertEquals(0, opponentRecords(games).single().bestRank)
    }

    @Test
    fun `opponents are ordered by how often they were played`() {
        val games = listOf(
            game("2026-03-01", "Houston", 3, 2),
            game("2026-03-02", "Houston", 4, 1),
            game("2026-03-08", "Yale", 6, 0)
        )
        assertEquals(listOf("Houston", "Yale"), opponentRecords(games).map { it.name })
    }

    @Test
    fun `an unplayed game is not a head-to-head result`() {
        val games = listOf(game("2027-02-05", "Houston", null, null))
        assertNull(opponentRecords(games).firstOrNull())
    }

    private fun standing(season: String, team: String) =
        ConferenceStanding(season = season, seo = team.lowercase(), team = team)
}
