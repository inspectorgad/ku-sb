package com.example

import com.example.ui.BATTING_COLUMNS
import com.example.ui.GLOSSARY
import com.example.ui.PITCHING_COLUMNS
import com.example.ui.explain
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

    @Test
    fun `definitions are sentences, not restatements of the abbreviation`() {
        for ((label, definition) in GLOSSARY) {
            assertTrue("$label is too terse: $definition", definition.length > 12)
            assertTrue("$label should read as prose", definition.trimEnd().endsWith("."))
        }
    }

    @Test
    fun `an unknown label has no definition rather than an empty one`() {
        assertNull(explain("NOT_A_STAT"))
    }
}
