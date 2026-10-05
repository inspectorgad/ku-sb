package com.example

import com.example.data.StatLine
import com.example.stats.topPerformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopPerformerTest {

    private var nextId = 1L
    private fun line(
        player: Long, ab: Int = 0, h: Int = 0, d2: Int = 0, d3: Int = 0,
        hr: Int = 0, rbi: Int = 0
    ) = StatLine(
        id = nextId++, playerId = player, gameId = 1, atBats = ab, hits = h,
        doubles = d2, triples = d3, homeRuns = hr, runsBattedIn = rbi
    )

    @Test
    fun `total bases decide, not hits alone`() {
        // Two hits including a home run beats three singles.
        val top = topPerformer(listOf(
            line(1, ab = 4, h = 3),
            line(2, ab = 4, h = 2, hr = 1)
        ))!!
        assertEquals(2L, top.playerId)
        assertEquals("2-for-4, HR", top.summary)
    }

    @Test
    fun `runs batted in break a tie on total bases`() {
        val top = topPerformer(listOf(
            line(1, ab = 4, h = 2, rbi = 1),
            line(2, ab = 4, h = 2, rbi = 3)
        ))!!
        assertEquals(2L, top.playerId)
        assertEquals("2-for-4, 3 RBI", top.summary)
    }

    /**
     * A player can drive in three on sacrifice flies and ground-outs without
     * a hit. That is a useful day, but it is not the line a recap leads with.
     */
    @Test
    fun `someone with no hit is never the pick`() {
        assertNull(topPerformer(listOf(line(1, ab = 3, h = 0, rbi = 3))))
    }

    @Test
    fun `a game with no lines has no top performer`() {
        assertNull(topPerformer(emptyList()))
    }

    @Test
    fun `multiple home runs are counted in the summary`() {
        val top = topPerformer(listOf(line(1, ab = 5, h = 3, hr = 2, rbi = 5)))!!
        assertEquals("3-for-5, 2 HR, 5 RBI", top.summary)
    }
}
