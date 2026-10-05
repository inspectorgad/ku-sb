package com.example

import com.example.data.OpponentStatLine
import com.example.stats.opponentBatters
import com.example.stats.opponentPitchers
import com.example.stats.opponentPlayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The opposing side of a box score. These players are not in the roster table
 * and are grouped by name, which is the only handle there is — so the things
 * worth pinning are that a series sums correctly across games and that nobody
 * from the other team can leak into a Kansas total.
 */
class OpponentPlayersTest {

    private var nextId = 1L
    private fun line(
        gameId: Long, name: String, ab: Int = 0, h: Int = 0, r: Int = 0, bb: Int = 0,
        hr: Int = 0, rbi: Int = 0, outs: Int = 0, er: Int = 0, ks: Int = 0,
        number: String = "", pos: String = "", win: Boolean = false
    ) = OpponentStatLine(
        id = nextId++, gameId = gameId, playerName = name, jerseyNumber = number,
        position = pos, atBats = ab, hits = h, runs = r, walks = bb, homeRuns = hr,
        runsBattedIn = rbi, pitched = outs > 0, outsPitched = outs, earnedRuns = er,
        pitcherStrikeouts = ks, win = win
    )

    @Test
    fun `a series sums across its games`() {
        val totals = opponentPlayers(listOf(
            line(1, "Aubrey Evans", ab = 3, h = 1, r = 1),
            line(2, "Aubrey Evans", ab = 4, h = 2, r = 1, hr = 1, rbi = 3),
            line(1, "Izzy Mertes", ab = 3, h = 0)
        ))
        val evans = totals.first { it.name == "Aubrey Evans" }
        assertEquals(2, evans.games)
        assertEquals(7, evans.atBats)
        assertEquals(3, evans.hits)
        assertEquals(1, evans.homeRuns)
        assertEquals(3.0 / 7, evans.battingAverage, 0.0001)
    }

    @Test
    fun `best hitters come first`() {
        val totals = opponentPlayers(listOf(
            line(1, "Quiet", ab = 4, h = 0),
            line(1, "Loud", ab = 4, h = 3)
        ))
        assertEquals(listOf("Loud", "Quiet"), totals.map { it.name })
    }

    @Test
    fun `a number or position missing from one game is taken from another`() {
        val totals = opponentPlayers(listOf(
            line(1, "Aubrey Evans", ab = 3, h = 1),
            line(2, "Aubrey Evans", ab = 3, h = 1, number = "3", pos = "ss")
        )).single()
        assertEquals("3", totals.jerseyNumber)
        assertEquals("ss", totals.position)
    }

    @Test
    fun `pitching is summed and ERA scaled to seven innings`() {
        val totals = opponentPlayers(listOf(
            line(1, "Isabella Vega", outs = 21, er = 3, ks = 5, win = true),
            line(2, "Isabella Vega", outs = 3, er = 0, ks = 1)
        )).single()
        assertEquals(2, totals.pitchingAppearances)
        assertEquals(24, totals.outsPitched)
        assertEquals(1, totals.wins)
        // 3 earned over 8 innings, at 7 innings to a game.
        assertEquals(3 * 21.0 / 24, totals.earnedRunAverage, 0.0001)
    }

    @Test
    fun `batters and pitchers are picked out separately`() {
        val totals = opponentPlayers(listOf(
            line(1, "Hitter", ab = 4, h = 2),
            line(1, "Pitcher", outs = 21, er = 2),
            line(1, "Pinch runner", r = 1),
            line(1, "Did not play")
        ))
        val batters = opponentBatters(totals).map { it.name }
        assertTrue("a hitter bats", "Hitter" in batters)
        assertTrue("a pinch runner took a turn", "Pinch runner" in batters)
        assertTrue("someone with nothing recorded did not", "Did not play" !in batters)
        assertEquals(listOf("Pitcher"), opponentPitchers(totals).map { it.name })
    }

    @Test
    fun `pitchers are ordered by how much they threw`() {
        val totals = opponentPlayers(listOf(
            line(1, "Reliever", outs = 3),
            line(1, "Starter", outs = 18)
        ))
        assertEquals(listOf("Starter", "Reliever"), opponentPitchers(totals).map { it.name })
    }

    @Test
    fun `no lines means no players rather than a crash`() {
        assertTrue(opponentPlayers(emptyList()).isEmpty())
        assertTrue(opponentBatters(emptyList()).isEmpty())
    }
}
