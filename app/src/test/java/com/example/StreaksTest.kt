package com.example

import com.example.data.Game
import com.example.stats.currentStreak
import com.example.stats.longestStreak
import com.example.stats.streakPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreaksTest {

    private var nextId = 1L
    private fun game(
        date: String, us: Int?, them: Int?, startTime: String = ""
    ) = Game(
        id = nextId++, date = date, opponent = "Opp", season = "2026",
        teamScore = us, opponentScore = them, startTime = startTime
    )

    @Test
    fun `current streak counts the run ending with the most recent game`() {
        val games = listOf(
            game("2026-02-06", 1, 0), game("2026-02-07", 0, 5),
            game("2026-02-08", 3, 2), game("2026-02-09", 4, 1)
        )
        assertEquals(2, currentStreak(games))
        assertEquals("Won 2 straight", streakPhrase(currentStreak(games)))
    }

    @Test
    fun `a losing run comes back negative`() {
        val games = listOf(
            game("2026-02-06", 9, 1), game("2026-02-07", 0, 5), game("2026-02-08", 2, 3)
        )
        assertEquals(-2, currentStreak(games))
        assertEquals("Lost 2 straight", streakPhrase(currentStreak(games)))
    }

    @Test
    fun `one result is not a streak`() {
        assertNull(streakPhrase(1))
        assertNull(streakPhrase(-1))
        assertNull(streakPhrase(0))
    }

    @Test
    fun `unplayed games are ignored entirely`() {
        val games = listOf(
            game("2026-02-06", 1, 0), game("2026-02-07", 2, 1),
            game("2027-03-12", null, null)
        )
        assertEquals(2, currentStreak(games))
    }

    @Test
    fun `no games played yields no streak`() {
        assertEquals(0, currentStreak(listOf(game("2027-03-12", null, null))))
        assertEquals(0, currentStreak(emptyList()))
    }

    /**
     * The doubleheader case: both games share a date, so only first pitch
     * separates them. Losing the opener and winning the nightcap is a
     * one-game win streak; ordering them the other way would read as a loss.
     */
    @Test
    fun `a doubleheader is ordered by first pitch, not by chance`() {
        val games = listOf(
            game("2026-09-26", 0, 6, startTime = "12:00 pm"),
            game("2026-09-26", 7, 2, startTime = "2:30 pm")
        )
        assertEquals(1, currentStreak(games))
        // And reversed in the input list, the answer must not change.
        assertEquals(1, currentStreak(games.reversed()))
    }

    @Test
    fun `longest streak scans the whole season, not just the tail`() {
        val games = listOf(
            game("2026-02-06", 1, 0), game("2026-02-07", 1, 0), game("2026-02-08", 1, 0),
            game("2026-02-09", 0, 1), game("2026-02-10", 0, 1),
            game("2026-02-11", 1, 0)
        )
        assertEquals(3, longestStreak(games, wins = true))
        assertEquals(2, longestStreak(games, wins = false))
        assertEquals(1, currentStreak(games))
    }
}
