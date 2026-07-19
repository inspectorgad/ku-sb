package com.example

import com.example.data.StatLine
import com.example.stats.aggregateBatting
import com.example.stats.aggregatePitching
import com.example.stats.formatAvg
import com.example.stats.formatEra
import com.example.stats.formatInnings
import com.example.stats.parseInnings
import com.example.stats.summarize
import org.junit.Assert.assertEquals
import org.junit.Test

class SoftballStatsTest {

    private fun batting(
        ab: Int = 0, r: Int = 0, h: Int = 0, d2: Int = 0, d3: Int = 0, hr: Int = 0,
        rbi: Int = 0, bb: Int = 0, so: Int = 0, hbp: Int = 0, sb: Int = 0,
        sf: Int = 0, started: Boolean = false
    ) = StatLine(
        playerId = 1, gameId = 1, atBats = ab, runs = r, hits = h, doubles = d2,
        triples = d3, homeRuns = hr, runsBattedIn = rbi, walks = bb, strikeouts = so,
        hitByPitch = hbp, stolenBases = sb, sacrificeFlies = sf, started = started
    )

    private fun pitching(
        outs: Int, ha: Int = 0, ra: Int = 0, er: Int = 0, bba: Int = 0, ks: Int = 0,
        win: Boolean = false, loss: Boolean = false, save: Boolean = false
    ) = StatLine(
        playerId = 1, gameId = 1, pitched = true, outsPitched = outs, hitsAllowed = ha,
        runsAllowed = ra, earnedRuns = er, walksAllowed = bba, pitcherStrikeouts = ks,
        win = win, loss = loss, save = save
    )

    @Test
    fun `batting aggregation sums counting stats`() {
        val totals = aggregateBatting(
            listOf(
                batting(ab = 4, h = 2, d2 = 1, rbi = 3, started = true),
                batting(ab = 3, h = 1, hr = 1, rbi = 1, bb = 1),
                batting(ab = 2, h = 0, so = 2)
            )
        )
        assertEquals(3, totals.games)
        assertEquals(1, totals.gamesStarted)
        assertEquals(9, totals.atBats)
        assertEquals(3, totals.hits)
        assertEquals(4, totals.runsBattedIn)
        // TB = 3 hits + 1 double + 3x1 HR = 7
        assertEquals(7, totals.totalBases)
    }

    @Test
    fun `batting average and obp and slg`() {
        // 3-for-9 with a walk, a HBP, and a sac fly.
        val totals = aggregateBatting(
            listOf(
                batting(ab = 4, h = 2, d2 = 1),
                batting(ab = 5, h = 1, hr = 1, bb = 1, hbp = 1, sf = 1)
            )
        )
        assertEquals(3.0 / 9, totals.battingAverage, 1e-9)
        // OBP = (3 + 1 + 1) / (9 + 1 + 1 + 1)
        assertEquals(5.0 / 12, totals.onBasePercentage, 1e-9)
        // TB = 3 + 1 + 3 = 7
        assertEquals(7.0 / 9, totals.sluggingPercentage, 1e-9)
        assertEquals(totals.onBasePercentage + totals.sluggingPercentage,
            totals.onBasePlusSlugging, 1e-9)
    }

    @Test
    fun `empty aggregations are all zeros not NaN`() {
        val batting = aggregateBatting(emptyList())
        assertEquals(0.0, batting.battingAverage, 0.0)
        assertEquals(0.0, batting.onBasePlusSlugging, 0.0)
        val pitching = aggregatePitching(emptyList())
        assertEquals(0.0, pitching.earnedRunAverage, 0.0)
        assertEquals(0.0, pitching.walksAndHitsPerInning, 0.0)
    }

    @Test
    fun `softball era is per seven innings`() {
        // 14 innings (42 outs), 4 earned runs -> ERA 2.00
        val totals = aggregatePitching(
            listOf(pitching(outs = 21, er = 1), pitching(outs = 21, er = 3))
        )
        assertEquals(2.0, totals.earnedRunAverage, 1e-9)
    }

    @Test
    fun `whip counts walks and hits per inning`() {
        // 7 IP, 6 hits + 1 walk -> exactly 1.00
        val totals = aggregatePitching(listOf(pitching(outs = 21, ha = 6, bba = 1)))
        assertEquals(1.0, totals.walksAndHitsPerInning, 1e-9)
    }

    @Test
    fun `pitching aggregation counts only lines that pitched`() {
        val totals = aggregatePitching(
            listOf(
                pitching(outs = 20, ks = 7, win = true),
                pitching(outs = 1, ra = 2, er = 2, loss = true),
                batting(ab = 4, h = 2) // position player: not an appearance
            )
        )
        assertEquals(2, totals.appearances)
        assertEquals(21, totals.outsPitched)
        assertEquals(1, totals.wins)
        assertEquals(1, totals.losses)
    }

    @Test
    fun `innings notation round trip`() {
        assertEquals("7.1", formatInnings(22))
        assertEquals("0.2", formatInnings(2))
        assertEquals("14.0", formatInnings(42))
        assertEquals(22, parseInnings("7.1"))
        assertEquals(21, parseInnings("7"))
        assertEquals(2, parseInnings("0.2"))
        assertEquals(0, parseInnings(""))
        // A malformed third-of-an-inning digit is clamped, not exploded.
        assertEquals(23, parseInnings("7.5"))
    }

    @Test
    fun `format helpers`() {
        assertEquals(".452", formatAvg(0.452))
        assertEquals(".000", formatAvg(0.0))
        assertEquals("1.000", formatAvg(1.0))
        assertEquals("2.45", formatEra(2.449))
        assertEquals("0.00", formatEra(0.0))
    }

    @Test
    fun `summarize covers batting pitching and dual lines`() {
        assertEquals("2-4, HR, 3 RBI, 1 R", summarize(batting(ab = 4, h = 2, hr = 1, rbi = 3, r = 1)))
        assertEquals(
            "6.0 IP, 1 ER, 5 K (W)",
            summarize(pitching(outs = 18, er = 1, ra = 1, ks = 5, win = true))
        )
        val dual = pitching(outs = 21, er = 2, ks = 4).copy(atBats = 3, hits = 1)
        assertEquals("1-3, 7.0 IP, 2 ER, 4 K", summarize(dual))
        assertEquals("1 R, 1 SB", summarize(batting(r = 1, sb = 1)))
        assertEquals("No stats", summarize(batting()))
    }
}
