package com.example

import com.example.data.Game
import com.example.data.StatLine
import com.example.stats.formDelta
import com.example.stats.playerForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormTest {

    private var nextGameId = 1L
    private var nextLineId = 1L

    private fun game(date: String, season: String = "2026", startTime: String = "") = Game(
        id = nextGameId++, date = date, opponent = "Opp", season = season,
        teamScore = 1, opponentScore = 0, startTime = startTime
    )

    private fun line(
        gameId: Long, playerId: Long = 1, ab: Int = 0, h: Int = 0,
        outs: Int = 0, er: Int = 0
    ) = StatLine(
        id = nextLineId++, playerId = playerId, gameId = gameId,
        atBats = ab, hits = h, pitched = outs > 0, outsPitched = outs, earnedRuns = er
    )

    @Test
    fun `a player who has not appeared has no form`() {
        val g = game("2026-03-01")
        assertTrue(playerForm(99, listOf(g), listOf(line(g.id)), "2026").isEmpty())
    }

    @Test
    fun `the window covers the player's most recent appearances`() {
        // Ten games: 2-for-4 in each of the first five, 0-for-4 in the last five.
        val games = (1..10).map { game("2026-03-%02d".format(it)) }
        val lines = games.mapIndexed { index, g ->
            if (index < 5) line(g.id, ab = 4, h = 2) else line(g.id, ab = 4, h = 0)
        }
        val form = playerForm(1, games, lines, "2026", windows = listOf(5))
        val last5 = form.first { it.label == "Last 5" }
        val season = form.first { it.label == "Season" }
        assertEquals(5, last5.games)
        assertEquals(0, last5.batting.hits)
        assertEquals(10, season.batting.hits)
        // Ice cold against a .250 season: the gap is the point of the screen.
        assertEquals(-0.25, formDelta(last5, season)!!, 0.0001)
    }

    @Test
    fun `a window no larger than the appearance count is dropped`() {
        val games = (1..5).map { game("2026-03-%02d".format(it)) }
        val lines = games.map { line(it.id, ab = 3, h = 1) }
        // Exactly five appearances: "Last 5" would just be the season again.
        val labels = playerForm(1, games, lines, "2026", windows = listOf(5, 10)).map { it.label }
        assertEquals(listOf("Season"), labels)
    }

    @Test
    fun `only the asked-for season counts`() {
        val thisYear = game("2026-03-01")
        val lastYear = game("2025-03-01", season = "2025")
        val lines = listOf(line(thisYear.id, ab = 4, h = 4), line(lastYear.id, ab = 4, h = 0))
        val season = playerForm(1, listOf(thisYear, lastYear), lines, "2026").single()
        assertEquals(1, season.games)
        assertEquals(4, season.batting.hits)
    }

    @Test
    fun `an unplayed game is not an appearance`() {
        val played = game("2026-03-01")
        val scheduled = Game(
            id = nextGameId++, date = "2026-03-02", opponent = "Opp", season = "2026",
            teamScore = null, opponentScore = null
        )
        val lines = listOf(line(played.id, ab = 3, h = 1), line(scheduled.id, ab = 9, h = 9))
        val season = playerForm(1, listOf(played, scheduled), lines, "2026").single()
        assertEquals(1, season.games)
        assertEquals(1, season.batting.hits)
    }

    @Test
    fun `thin volume is reported but not treated as a rate`() {
        // Six appearances, one at-bat each: a real .167 and a meaningless one.
        val games = (1..6).map { game("2026-03-%02d".format(it)) }
        val lines = games.map { line(it.id, ab = 1, h = 0) }
        val form = playerForm(1, games, lines, "2026", windows = listOf(5))
        val last5 = form.first { it.label == "Last 5" }
        assertEquals(5, last5.batting.atBats)
        assertTrue("5 at-bats in 5 games is not a rate", !last5.battingQualified)
        assertNull("too thin to compare", formDelta(last5, form.first { it.label == "Season" }))
    }

    @Test
    fun `pitching volume is judged on the appearances where she pitched`() {
        // Four games in the field, then two relief innings.
        val fielding = (1..4).map { game("2026-03-%02d".format(it)) }
        val relief = (5..6).map { game("2026-03-%02d".format(it)) }
        val lines = fielding.map { line(it.id, ab = 3, h = 1) } +
            relief.map { line(it.id, ab = 1, h = 0, outs = 3) }
        val form = playerForm(1, fielding + relief, lines, "2026", windows = listOf(5))
        val season = form.first { it.label == "Season" }
        assertEquals(2, season.pitching.appearances)
        assertEquals(6, season.pitching.outsPitched)
        // Two appearances, an inning each — qualified, even though she "played"
        // six games. Judging her against six innings would wrongly gate it.
        assertTrue(season.pitchingQualified)
    }

    @Test
    fun `a player who never pitched is not held to a pitching gate`() {
        val games = (1..6).map { game("2026-03-%02d".format(it)) }
        val lines = games.map { line(it.id, ab = 4, h = 2) }
        val season = playerForm(1, games, lines, "2026").first { it.label == "Season" }
        assertEquals(0, season.pitching.appearances)
        assertTrue(!season.pitchingQualified)
        assertTrue(season.battingQualified)
    }

    /**
     * Doubleheaders share a date, so date alone leaves their order to chance —
     * and a last-N window built on the wrong order silently reports the wrong
     * games. This is the same hazard the streak code guards against.
     */
    @Test
    fun `the halves of a doubleheader keep their order in a window`() {
        val older = game("2026-03-01")
        val g1 = game("2026-03-02", startTime = "12:00 pm")
        val g2 = game("2026-03-02", startTime = "2:30 pm")
        val lines = listOf(
            line(older.id, ab = 4, h = 4),
            line(g1.id, ab = 4, h = 1),
            line(g2.id, ab = 4, h = 0)
        )
        val last2 = playerForm(1, listOf(older, g1, g2), lines, "2026", windows = listOf(2))
            .first { it.label == "Last 2" }
        assertEquals(2, last2.games)
        // The two halves of the 2nd, not the opener's 4-for-4.
        assertEquals(1, last2.batting.hits)
    }
}
