package com.example

import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.seasonHighlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightsTest {

    private var nextGameId = 1L
    private var nextLineId = 1L

    private val ada = Player(id = 1, name = "Ada Alpha")
    private val bea = Player(id = 2, name = "Bea Beta")
    private val roster = listOf(ada, bea)

    private fun game(
        date: String, opponent: String, us: Int?, them: Int?,
        site: String = "H", attendance: Int = 0
    ) = Game(
        id = nextGameId++, date = date, opponent = opponent, season = "2026",
        teamScore = us, opponentScore = them, site = site, attendance = attendance
    )

    private fun batting(gameId: Long, player: Player, ab: Int, h: Int, rbi: Int = 0) =
        StatLine(
            id = nextLineId++, playerId = player.id, gameId = gameId,
            atBats = ab, hits = h, runsBattedIn = rbi
        )

    private fun pitching(gameId: Long, player: Player, k: Int) =
        StatLine(
            id = nextLineId++, playerId = player.id, gameId = gameId,
            pitched = true, outsPitched = 21, pitcherStrikeouts = k
        )

    private fun titles(highlights: List<com.example.stats.Highlight>) = highlights.map { it.title }

    @Test
    fun `a season with nothing played has nothing to say`() {
        assertTrue(seasonHighlights(emptyList(), roster, emptyList()).isEmpty())
        val scheduled = listOf(game("2027-02-05", "Houston", null, null))
        assertTrue(seasonHighlights(scheduled, roster, emptyList()).isEmpty())
    }

    @Test
    fun `the headline facts come out of the games`() {
        val blowout = game("2026-03-14", "Houston", 18, 1, attendance = 900)
        val close = game("2026-03-15", "Houston", 2, 1, attendance = 4235)
        val highlights = seasonHighlights(listOf(blowout, close), roster, emptyList())

        val runs = highlights.single { it.title == "Most runs scored" }
        assertEquals("18 runs", runs.headline)
        assertTrue(runs.detail.contains("Houston"))
        assertTrue(runs.detail.contains("2026-03-14"))

        assertEquals("18-1", highlights.single { it.title == "Biggest win" }.headline)
        assertEquals("4,235", highlights.single { it.title == "Biggest crowd" }.headline)
    }

    @Test
    fun `player facts name the player and the game`() {
        val g1 = game("2026-03-14", "Houston", 9, 1)
        val g2 = game("2026-03-15", "DePaul", 7, 2, site = "A")
        val lines = listOf(
            batting(g1.id, ada, ab = 4, h = 4, rbi = 2),
            batting(g1.id, bea, ab = 4, h = 1, rbi = 1),
            batting(g2.id, bea, ab = 3, h = 2, rbi = 5),
            pitching(g2.id, ada, k = 13)
        )
        val highlights = seasonHighlights(listOf(g1, g2), roster, lines)

        val hits = highlights.single { it.title == "Most hits in a game" }
        assertEquals("Ada Alpha — 4-for-4", hits.headline)
        val rbi = highlights.single { it.title == "Most RBI in a game" }
        assertEquals("Bea Beta — 5 RBI", rbi.headline)
        // Away games read "at", home games "vs".
        assertTrue(rbi.detail.startsWith("at DePaul"))
        assertEquals(
            "Ada Alpha — 13 K",
            highlights.single { it.title == "Most strikeouts in a game" }.headline
        )
    }

    /**
     * A highlight must never cite a game outside the set it was given — a
     * career-high from 2026 has no business appearing on a 2027 season screen.
     */
    @Test
    fun `stat lines from games outside the set are ignored`() {
        val thisSeason = game("2026-03-14", "Houston", 9, 1)
        val elsewhere = game("2025-03-14", "Michigan", 9, 1)
        val lines = listOf(
            batting(thisSeason.id, ada, ab = 4, h = 2),
            batting(elsewhere.id, bea, ab = 5, h = 5, rbi = 9)
        )
        val highlights = seasonHighlights(listOf(thisSeason), roster, lines)
        assertEquals(
            "Ada Alpha — 2-for-4",
            highlights.single { it.title == "Most hits in a game" }.headline
        )
        assertTrue(highlights.none { it.headline.contains("Bea Beta") })
    }

    @Test
    fun `a category with nothing to report is left out entirely`() {
        val g = game("2026-03-14", "Houston", 3, 2)
        val highlights = seasonHighlights(listOf(g), roster, listOf(batting(g.id, ada, 4, 0)))
        // Nobody got a hit, drove in a run, struck anybody out, and no crowd
        // was announced, so none of those appear.
        assertTrue("Most hits in a game" !in titles(highlights))
        assertTrue("Most RBI in a game" !in titles(highlights))
        assertTrue("Most strikeouts in a game" !in titles(highlights))
        assertTrue("Biggest crowd" !in titles(highlights))
    }

    @Test
    fun `a season of only losses reports no biggest win`() {
        val games = listOf(
            game("2026-03-14", "Houston", 1, 9),
            game("2026-03-15", "Houston", 0, 4)
        )
        assertTrue("Biggest win" !in titles(seasonHighlights(games, roster, emptyList())))
    }

    @Test
    fun `a winning streak is only reported once it is actually a streak`() {
        val one = listOf(game("2026-03-14", "Houston", 3, 2), game("2026-03-15", "Houston", 1, 2))
        assertTrue("Longest winning streak" !in titles(seasonHighlights(one, roster, emptyList())))

        val two = listOf(game("2026-03-14", "Houston", 3, 2), game("2026-03-15", "Houston", 5, 2))
        val streak = seasonHighlights(two, roster, emptyList())
            .single { it.title == "Longest winning streak" }
        assertEquals("2 straight", streak.headline)
    }
}
