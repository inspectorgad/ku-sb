package com.example

import com.example.data.Seeder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The guard that stops the seed bundled in the app from rolling back data the
 * phone has already synced.
 *
 * The seed inside an APK is a snapshot from whenever that APK was built, so a
 * phone that has synced since holds newer data. The cases below are the ones
 * where getting it wrong either loses corrections (merging something older) or
 * silently stops the app updating at all (holding back something it should
 * apply).
 */
class SeedRollbackTest {

    private val older = "2026-10-20T07:42:36Z"
    private val newer = "2026-10-25T07:42:36Z"

    @Test
    fun `a seed older than the synced feed is held back`() {
        assertTrue(Seeder.olderThan(seed = older, feed = newer))
    }

    @Test
    fun `a seed newer than the feed is applied`() {
        assertFalse(Seeder.olderThan(seed = newer, feed = older))
    }

    @Test
    fun `the same stamp is applied, not held back`() {
        // A build carrying exactly the feed's data must still merge: that is
        // what puts back anything an older copy already rolled back.
        assertFalse(Seeder.olderThan(seed = newer, feed = newer))
    }

    @Test
    fun `a phone that has never synced applies the seed`() {
        assertFalse(Seeder.olderThan(seed = newer, feed = null))
    }

    @Test
    fun `an unreadable stamp never holds the seed back`() {
        // Better to merge than to freeze the app on a stamp we cannot parse.
        assertFalse(Seeder.olderThan(seed = "today", feed = newer))
        assertFalse(Seeder.olderThan(seed = newer, feed = "2026-10-25"))
        assertFalse(Seeder.olderThan(seed = "", feed = newer))
    }

    @Test
    fun `stamps compare by time, not by digit position`() {
        // They are compared as text, which is only right because the scraper's
        // format is fixed-width. These would be wrong under a naive compare if
        // it ever stopped being.
        assertTrue(Seeder.olderThan("2026-09-30T23:59:59Z", "2026-10-01T00:00:00Z"))
        assertTrue(Seeder.olderThan("2026-10-25T07:42:36Z", "2026-10-25T07:42:37Z"))
        assertFalse(Seeder.olderThan("2027-01-01T00:00:00Z", "2026-12-31T23:59:59Z"))
    }
}
