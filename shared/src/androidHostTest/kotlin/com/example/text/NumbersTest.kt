package com.example.text

import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Numbers.kt against the java.util.Formatter calls it replaced.
 *
 * The iPhone port could not keep String.format, so fixed(), grouped(), hex2()
 * and roundHalfUp() were written by hand. Android then stopped calling Java's
 * versions and started calling these, which means every batting average, ERA
 * and per-game figure on the phone now comes out of this file. A rounding
 * difference here would not crash anything; it would quietly print a different
 * number, and nobody would know to look.
 *
 * So these tests do not check a handful of cases chosen by hand. They run the
 * hand-written code and the Java original over the same several hundred
 * thousand values and require the same string back every time. Java is the
 * reference, because Java is what the app printed before the port.
 *
 * Everything here is deterministic: the sweeps use a fixed seed, so a failure
 * reproduces exactly rather than appearing once in a while.
 */
class NumbersTest {

    // ---------- fixed(value, decimals) vs String.format("%.Nf") ----------

    private fun checkFixed(value: Double, decimals: Int) {
        val java = "%.${decimals}f".format(Locale.US, value)
        assertEquals(java, fixed(value, decimals), "fixed($value, $decimals)")
    }

    @Test
    fun `the values the app actually prints`() {
        // A batting average is hits over at-bats to three places, and an ERA is
        // earned runs over innings times seven to two. Walk every at-bat count
        // a season can reach and every hit total within it: this is the whole
        // input space of the number the reader looks at most.
        for (atBats in 1..400) {
            for (hits in 0..atBats) checkFixed(hits.toDouble() / atBats, 3)
        }
        // ERA, over every outs-recorded count a season can produce.
        for (outs in 1..1500) {
            val innings = outs / 3.0
            for (earned in 0..60) checkFixed(earned * 7.0 / innings, 2)
        }
        // Per-game figures, one decimal.
        for (games in 1..70) {
            for (total in 0..500) checkFixed(total.toDouble() / games, 1)
        }
    }

    @Test
    fun `halfway cases round Java's way, not C's`() {
        // The case the comment in Numbers.kt calls out: 1/16 is exactly 0.0625,
        // and Java rounds the shortest decimal form half-up to .063 where a
        // naive port of printf gives .062.
        assertEquals("0.063", fixed(1.0 / 16, 3))
        assertEquals("0.063", "%.3f".format(Locale.US, 1.0 / 16))

        // Every n/2^k that lands exactly on a rounding boundary, which is where
        // the two approaches disagree.
        for (k in 1..12) {
            val denominator = 1 shl k
            for (n in 0..denominator * 4) {
                val v = n.toDouble() / denominator
                for (decimals in 0..6) {
                    checkFixed(v, decimals)
                    checkFixed(-v, decimals)
                }
            }
        }
        // And the plain decimal halves.
        for (n in -200..200) {
            checkFixed(n + 0.5, 0)
            checkFixed(n + 0.05, 1)
            checkFixed(n + 0.005, 2)
            checkFixed(n + 0.0005, 3)
        }
    }

    @Test
    fun `signs, zeroes and the things that are not numbers`() {
        assertEquals("%.3f".format(Locale.US, 0.0), fixed(0.0, 3))
        assertEquals("%.3f".format(Locale.US, -0.0), fixed(-0.0, 3))
        // A negative that rounds to zero keeps its sign in Java.
        assertEquals("%.3f".format(Locale.US, -0.0001), fixed(-0.0001, 3))
        assertEquals("%.0f".format(Locale.US, -0.4), fixed(-0.4, 0))

        assertEquals("NaN", fixed(Double.NaN, 3))
        assertEquals("Infinity", fixed(Double.POSITIVE_INFINITY, 3))
        assertEquals("-Infinity", fixed(Double.NEGATIVE_INFINITY, 3))
        // Java agrees on all three.
        assertEquals("%.3f".format(Locale.US, Double.NaN), fixed(Double.NaN, 3))
        assertEquals("%.3f".format(Locale.US, Double.POSITIVE_INFINITY), fixed(Double.POSITIVE_INFINITY, 3))
        assertEquals("%.3f".format(Locale.US, Double.NEGATIVE_INFINITY), fixed(Double.NEGATIVE_INFINITY, 3))
    }

    @Test
    fun `carries that run the whole way up`() {
        // 9.9995 to three places has to carry through every digit.
        for (nines in 1..12) {
            val text = "0." + "9".repeat(nines) + "5"
            val v = text.toDouble()
            for (decimals in 0..nines) checkFixed(v, decimals)
        }
        for (decimals in 0..6) {
            checkFixed(0.9999999999, decimals)
            checkFixed(9.9999999999, decimals)
            checkFixed(99.995, decimals)
            checkFixed(-99.995, decimals)
        }
    }

    @Test
    fun `a random sweep across every magnitude`() {
        val random = Random(20260215)
        repeat(60_000) {
            // Spread across the exponents the app could ever hold, in both signs.
            val magnitude = random.nextInt(-8, 10)
            val mantissa = random.nextDouble()
            val sign = if (random.nextBoolean()) 1.0 else -1.0
            val value = sign * mantissa * Math.pow(10.0, magnitude.toDouble())
            checkFixed(value, random.nextInt(0, 7))
        }
        // Arbitrary bit patterns, which reach denormals and the extremes that
        // a mantissa-times-power-of-ten sweep never produces.
        repeat(40_000) {
            val value = Double.fromBits(random.nextLong())
            if (value.isFinite()) checkFixed(value, random.nextInt(0, 7))
        }
    }

    // ---------- roundHalfUp vs Math.round ----------

    @Test
    fun `roundHalfUp is Math round, including the cases Kotlin gets wrong`() {
        // The three the doc comment names.
        assertEquals(3L, roundHalfUp(2.5))
        assertEquals(-2L, roundHalfUp(-2.5))
        assertEquals(0L, roundHalfUp(0.49999999999999994))
        assertEquals(0L, roundHalfUp(Double.NaN))
        assertEquals(java.lang.Math.round(0.49999999999999994), roundHalfUp(0.49999999999999994))
        assertEquals(java.lang.Math.round(Double.NaN), roundHalfUp(Double.NaN))

        for (n in -1000..1000) {
            for (offset in listOf(0.0, 0.5, -0.5, 0.49999999999999994, 0.4999999, 0.5000001)) {
                val v = n + offset
                assertEquals(java.lang.Math.round(v), roundHalfUp(v), "roundHalfUp($v)")
            }
        }
        val random = Random(20260216)
        repeat(60_000) {
            val v = random.nextDouble(-1.0e9, 1.0e9)
            assertEquals(java.lang.Math.round(v), roundHalfUp(v), "roundHalfUp($v)")
        }
    }

    // ---------- hex2 vs "%02x" ----------

    @Test
    fun `hex2 matches percent-02x for every byte`() {
        for (i in -128..127) {
            val b = i.toByte()
            assertEquals("%02x".format(b), hex2(b), "hex2($i)")
        }
    }

    // ---------- grouped vs "%,d" ----------

    @Test
    fun `grouped matches percent-comma-d`() {
        for (n in -10_000..10_000) {
            assertEquals("%,d".format(Locale.US, n), grouped(n), "grouped($n)")
        }
        // Every boundary where a separator appears or a digit count changes.
        var power = 1L
        repeat(18) {
            for (delta in -2L..2L) {
                for (sign in listOf(1L, -1L)) {
                    val v = sign * (power + delta)
                    assertEquals("%,d".format(Locale.US, v), grouped(v), "grouped($v)")
                }
            }
            power *= 10
        }
        // Long.MIN_VALUE has no positive counterpart; the comment says the
        // digits are grouped as text for exactly this reason.
        assertEquals("%,d".format(Locale.US, Long.MIN_VALUE), grouped(Long.MIN_VALUE))
        assertEquals("%,d".format(Locale.US, Long.MAX_VALUE), grouped(Long.MAX_VALUE))
        assertEquals("%,d".format(Locale.US, Int.MIN_VALUE), grouped(Int.MIN_VALUE))
        assertEquals("%,d".format(Locale.US, Int.MAX_VALUE), grouped(Int.MAX_VALUE))

        val random = Random(20260217)
        repeat(40_000) {
            val v = random.nextLong()
            assertEquals("%,d".format(Locale.US, v), grouped(v), "grouped($v)")
        }
    }
}
