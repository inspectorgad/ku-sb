package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.JayhawksDatabase
import com.example.data.Seeder
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StandingsMergeTest {

    private lateinit var db: JayhawksDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JayhawksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun seed(standings: String, polls: String = "") = JSONObject(
        """
        {"players": [], "games": [], "standings": [$standings]
         ${if (polls.isNotBlank()) ", \"polls\": [$polls]" else ""}}
        """
    )

    @Test
    fun `standings merge inserts rows with ranks`() = runTest {
        Seeder.merge(
            seed(
                """{"season":"2026","seo":"texas-tech","team":"Texas Tech","confW":22,"confL":2,
                    "overallW":61,"overallL":10,"nationalRank":2,"rpiRank":3}"""
            ),
            db.dao()
        )
        val row = db.dao().standingsOnce().single()
        assertEquals("Texas Tech", row.team)
        assertEquals(22, row.confW)
        assertEquals(2, row.nationalRank)
        assertEquals(3, row.rpiRank)
        assertEquals(22.0 / 24.0, row.confPct, 1e-9)
    }

    @Test
    fun `standings are replaced per season, not accumulated`() = runTest {
        val dao = db.dao()
        Seeder.merge(
            seed("""{"season":"2026","seo":"kansas","team":"Kansas","confW":11,"confL":13,
                     "overallW":35,"overallL":22}"""),
            dao
        )
        // A later sync with an updated record must not leave the old row behind.
        Seeder.merge(
            seed("""{"season":"2026","seo":"kansas","team":"Kansas","confW":12,"confL":13,
                     "overallW":36,"overallL":22}"""),
            dao
        )
        val rows = dao.standingsOnce()
        assertEquals(1, rows.size)
        assertEquals(12, rows.single().confW)
        assertEquals(36, rows.single().overallW)
    }

    @Test
    fun `missing ranks stay null instead of zero`() = runTest {
        Seeder.merge(
            seed("""{"season":"2026","seo":"kansas","team":"Kansas","confW":12,"confL":13,
                     "overallW":36,"overallL":22}"""),
            db.dao()
        )
        val row = db.dao().standingsOnce().single()
        assertNull(row.nationalRank)
        assertNull(row.rpiRank)
    }

    @Test
    fun `poll entries merge and are replaced per season`() = runTest {
        val dao = db.dao()
        val poll = """
            {"season":"2026","name":"ESPN.com/USA Softball Top 25",
             "updated":"Through Games JUN. 5, 2026",
             "rows":[{"rank":1,"rankLabel":"1","team":"Texas","record":"53-12",
                      "points":"625","previous":"3","firstPlaceVotes":25,"big12":false},
                     {"rank":2,"rankLabel":"2","team":"Texas Tech","record":"61-10",
                      "points":"597","previous":"6","firstPlaceVotes":0,"big12":true}]}
        """
        Seeder.merge(seed("", poll), dao)
        val rows = dao.pollEntriesOnce().sortedBy { it.rank }
        assertEquals(2, rows.size)
        assertEquals(25, rows[0].firstPlaceVotes)
        assertTrue(rows[1].big12)
        assertEquals("ESPN.com/USA Softball Top 25", rows[1].pollName)

        // Re-sync replaces rather than accumulates.
        Seeder.merge(seed("", poll), dao)
        assertEquals(2, dao.pollEntriesOnce().size)
    }

    @Test
    fun `seeds without standings keys leave stored rows untouched`() = runTest {
        val dao = db.dao()
        Seeder.merge(
            seed("""{"season":"2026","seo":"kansas","team":"Kansas","confW":12,"confL":13,
                     "overallW":36,"overallL":22}"""),
            dao
        )
        // A pre-feature payload (players/games only) must not clear standings.
        Seeder.merge(JSONObject("""{"players": [], "games": []}"""), dao)
        assertEquals(1, dao.standingsOnce().size)
    }
}
