package com.example

import com.example.ui.BATTING_COLUMNS
import com.example.ui.BOX_BATTING_COLUMNS
import com.example.ui.BOX_FIELDING_COLUMNS
import com.example.ui.BOX_PITCHING_COLUMNS
import com.example.ui.FORM_BATTING_COLUMNS
import com.example.ui.FORM_PITCHING_COLUMNS
import com.example.ui.GLOSSARY
import com.example.ui.PITCHING_COLUMNS
import com.example.ui.explain
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glossary is only useful if it covers what is actually on screen. A
 * column added to a stats table without a definition is exactly the silent
 * gap this catches — the press-and-hold just does nothing and nobody knows why.
 */
class GlossaryTest {

    @Test
    fun `every batting column has a definition`() {
        val missing = BATTING_COLUMNS.map { it.first }.filter { explain(it) == null }
        assertTrue("no definition for: $missing", missing.isEmpty())
    }

    @Test
    fun `every pitching column has a definition`() {
        val missing = PITCHING_COLUMNS.map { it.first }.filter { explain(it) == null }
        assertTrue("no definition for: $missing", missing.isEmpty())
    }

    /**
     * The box score has its own, narrower column sets. They are listed here
     * explicitly rather than folded into the two tests above because a label
     * can be in a box table and no season table (PO, A, E, DP, NP, BF) — the
     * fielding line in particular exists nowhere else.
     */
    @Test
    fun `every box score column has a definition`() {
        val labels = (
            BOX_BATTING_COLUMNS + BOX_PITCHING_COLUMNS + BOX_FIELDING_COLUMNS +
                FORM_BATTING_COLUMNS + FORM_PITCHING_COLUMNS
            ).map { it.first }
        val missing = labels.filter { explain(it) == null }
        assertTrue("no definition for: $missing", missing.isEmpty())
    }

    @Test
    fun `the derived rate stats are all explained`() {
        for (label in listOf("AVG", "OBP", "SLG", "OPS", "ERA", "WHIP")) {
            assertNotNull("no definition for $label", explain(label))
        }
    }

    /**
     * H, R, BB, SO and HR are in both tables meaning opposite things. A
     * definition that only describes the batting side would mislead anyone
     * reading a pitching line, so each must name both.
     */
    @Test
    fun `abbreviations shared by both tables explain both meanings`() {
        val shared = BATTING_COLUMNS.map { it.first }
            .intersect(PITCHING_COLUMNS.map { it.first }.toSet())
        assertTrue("expected overlapping labels", shared.isNotEmpty())
        for (label in shared) {
            val definition = explain(label)
            assertNotNull("no definition for $label", definition)
            assertTrue(
                "$label only describes one side: $definition",
                definition!!.contains("batting line") && definition.contains("pitching line")
            )
        }
    }

    /**
     * The point of tapping a label is to learn something, so an entry has to do
     * more than expand the letters: "2B" answered with "Doubles" leaves anyone
     * who did not already know it none the wiser. Counting words rather than
     * characters is deliberate — a character threshold called "Doubles." too
     * terse and "Games played." acceptable, which is not the distinction worth
     * enforcing.
     */
    @Test
    fun `definitions explain the abbreviation rather than restating it`() {
        for ((label, definition) in GLOSSARY) {
            val body = definition.trimEnd().trimEnd('.')
            assertNotEquals("$label is defined as itself", label.lowercase(), body.lowercase())
            assertTrue(
                "$label is too terse to explain anything: $definition",
                body.split(" ").filter { it.isNotBlank() }.size >= 4
            )
            assertTrue("$label should read as prose", definition.trimEnd().endsWith("."))
        }
    }

    @Test
    fun `an unknown label has no definition rather than an empty one`() {
        assertNull(explain("NOT_A_STAT"))
    }
}
