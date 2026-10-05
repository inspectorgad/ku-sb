package com.example

import com.example.data.Player
import com.example.data.duplicatePlayerFolds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folding two spellings of one player back together.
 *
 * The thing worth pinning hardest is what must NOT fold. Merging two real
 * players is unrecoverable from the app's side — their lines would be summed
 * into one career with nothing left to say they were ever separate — whereas
 * a pair left unfolded is merely the bug we already had, and visible.
 *
 * Kansas carried both Chloe and Blakely Barber in 2026, so that case is not
 * hypothetical.
 */
class PlayerFoldTest {

    private var nextId = 0L
    private fun player(name: String, jersey: String) =
        Player(id = ++nextId, name = name, jerseyNumber = jersey)

    @Test
    fun `an initial folds into the full name on the same jersey`() {
        val short = player("P. Limbaugh", "4")
        val full = player("Presley Limbaugh", "4")
        assertEquals(mapOf(short.id to full.id), duplicatePlayerFolds(listOf(short, full)))
    }

    @Test
    fun `the fold is to the full name regardless of listing order`() {
        val full = player("Presley Limbaugh", "4")
        val short = player("P. Limbaugh", "4")
        assertEquals(mapOf(short.id to full.id), duplicatePlayerFolds(listOf(full, short)))
    }

    @Test
    fun `two players sharing a surname are left alone`() {
        // Real 2026 team-mates: different people, different jerseys.
        val chloe = player("Chloe Barber", "26")
        val blakely = player("Blakely Barber", "3")
        assertTrue(duplicatePlayerFolds(listOf(chloe, blakely)).isEmpty())
    }

    @Test
    fun `two real first names on one jersey do not fold`() {
        // A shared number is not enough on its own: which of the two would be
        // the name to keep? Left visible instead of guessed at.
        val a = player("Chloe Barber", "26")
        val b = player("Carly Barber", "26")
        assertTrue(duplicatePlayerFolds(listOf(a, b)).isEmpty())
    }

    @Test
    fun `a different jersey means a different player`() {
        val short = player("P. Limbaugh", "4")
        val full = player("Presley Limbaugh", "9")
        assertTrue(duplicatePlayerFolds(listOf(short, full)).isEmpty())
    }

    @Test
    fun `a different first initial does not fold`() {
        val short = player("B. Barber", "26")
        val full = player("Chloe Barber", "26")
        assertTrue(duplicatePlayerFolds(listOf(short, full)).isEmpty())
    }

    @Test
    fun `a blank jersey is not evidence of anything`() {
        val short = player("P. Limbaugh", "")
        val full = player("Presley Limbaugh", "")
        assertTrue(duplicatePlayerFolds(listOf(short, full)).isEmpty())
    }

    @Test
    fun `a single-word name is left alone`() {
        val one = player("Limbaugh", "4")
        val two = player("Presley Limbaugh", "4")
        assertTrue(duplicatePlayerFolds(listOf(one, two)).isEmpty())
    }

    @Test
    fun `three spellings all fold into the one full name`() {
        val a = player("P. Limbaugh", "4")
        val b = player("P Limbaugh", "4")
        val full = player("Presley Limbaugh", "4")
        assertEquals(
            mapOf(a.id to full.id, b.id to full.id),
            duplicatePlayerFolds(listOf(a, b, full))
        )
    }

    @Test
    fun `a roster with nothing to fold yields nothing`() {
        val squad = listOf(
            player("Presley Limbaugh", "4"),
            player("Chloe Barber", "26"),
            player("Anna Soles", "22")
        )
        assertTrue(duplicatePlayerFolds(squad).isEmpty())
        assertTrue(duplicatePlayerFolds(emptyList()).isEmpty())
    }

    @Test
    fun `the eight real 2026 splits all fold and nobody else does`() {
        val squad = listOf(
            player("P. Limbaugh", "4") to player("Presley Limbaugh", "4"),
            player("A. Linduff", "39") to player("Aynslee Linduff", "39"),
            player("H. Cripe", "41") to player("Hailey Cripe", "41"),
            player("C. Bagshaw", "6") to player("Campbell Bagshaw", "6"),
            player("E. Tatum", "20") to player("Emma Tatum", "20"),
            player("K. Diggs", "2") to player("Kennedy Diggs", "2"),
            player("K. Griggs", "14") to player("Karsen Griggs", "14"),
            player("L. Ludwig", "11") to player("Lizzy Ludwig", "11")
        )
        val bystanders = listOf(
            player("Chloe Barber", "26"),
            player("Blakely Barber", "3"),
            player("Anna Soles", "22")
        )
        val all = squad.flatMap { listOf(it.first, it.second) } + bystanders
        val folds = duplicatePlayerFolds(all)

        assertEquals(8, folds.size)
        squad.forEach { (short, full) -> assertEquals(full.id, folds[short.id]) }
        bystanders.forEach { assertTrue(it.id !in folds) }
    }
}
