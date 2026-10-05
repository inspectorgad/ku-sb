package com.example

import com.example.data.Game
import com.example.stats.GameEnding
import com.example.stats.endingPhrase
import com.example.stats.gameEnding
import com.example.stats.inningsPlayed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameShapeTest {

    private fun game(innings: String?, scheduled: Int, us: Int?, them: Int?) = Game(
        id = 1, date = "2026-02-06", opponent = "Opp", season = "2026",
        teamScore = us, opponentScore = them, inningScores = innings,
        scheduledInnings = scheduled
    )

    @Test
    fun `innings come from the line score`() {
        assertEquals(5, inningsPlayed(game("1-0, 2-0, 0-0, 4-0, 5-0", 7, 12, 0)))
        assertEquals(0, inningsPlayed(game(null, 7, 1, 0)))
    }

    @Test
    fun `a game short of its scheduled length with a big margin is the run rule`() {
        // Bethune-Cookman, Feb 6: 12-0 in five of seven.
        val g = game("6-0, 0-0, 2-0, 0-0, 4-0", 7, 12, 0)
        assertEquals(GameEnding.RUN_RULE, gameEnding(g))
        assertEquals("Run rule · 5 innings", endingPhrase(g))
    }

    @Test
    fun `the run rule applies to a loss just the same`() {
        assertEquals(GameEnding.RUN_RULE, gameEnding(game("0-2, 0-3, 0-0, 0-3, 0-0", 7, 0, 8)))
    }

    /**
     * Both halves are needed. A short game with a close score was called for
     * weather or darkness, and saying "run rule" would invent a reason.
     */
    @Test
    fun `a short game with a close score is shortened, not run-ruled`() {
        val g = game("1-0, 0-1, 1-0", 7, 2, 1)
        assertEquals(GameEnding.SHORTENED, gameEnding(g))
        assertEquals("Shortened · 3 innings", endingPhrase(g))
    }

    @Test
    fun `the rule cannot apply before the fifth inning`() {
        assertEquals(GameEnding.SHORTENED, gameEnding(game("9-0, 0-0, 1-0, 0-0", 7, 10, 0)))
    }

    @Test
    fun `a full game says nothing`() {
        assertEquals(GameEnding.REGULATION, gameEnding(game("0-0, 1-0, 0-0, 2-1, 0-0, 0-0, 1-0", 7, 4, 1)))
        assertNull(endingPhrase(game("0-0, 1-0, 0-0, 2-1, 0-0, 0-0, 1-0", 7, 4, 1)))
    }

    @Test
    fun `extra innings are named as such`() {
        val g = game("0-1, 1-0, 2-0, 0-1, 0-0, 0-1, 0-0, 1-3", 7, 4, 6)
        assertEquals(GameEnding.EXTRAS, gameEnding(g))
        assertEquals("8 innings", endingPhrase(g))
    }

    @Test
    fun `without a scheduled length nothing is claimed`() {
        // Every game recorded before the scheduled innings were captured.
        assertEquals(GameEnding.REGULATION, gameEnding(game("1-0, 2-0, 0-0, 4-0, 5-0", 0, 12, 0)))
    }

    @Test
    fun `an unplayed game is not an ending`() {
        assertEquals(GameEnding.REGULATION, gameEnding(game(null, 7, null, null)))
    }
}
