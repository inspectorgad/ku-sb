package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.json.JSONArray as MineArray
import com.example.json.JSONObject as MineObject
import org.json.JSONArray as TheirsArray
import org.json.JSONObject as TheirsObject

/**
 * com.example.json against Android's org.json, which it replaced.
 *
 * iOS has no org.json, so the port wrote a stand-in rather than rewriting
 * every reader of the seed, the sync feed, the NCAA scoreboard and the Claude
 * API. That makes this a drop-in: the callers were written and tested against
 * Android's behaviour, quirks included, so "correct" here means "whatever
 * Android does", not "whatever the JSON spec says".
 *
 * Robolectric supplies the real Android implementation. On a plain JVM the
 * org.json classes are stubs that throw, so this test has to run under
 * Robolectric to be comparing against anything at all.
 *
 * Every check runs the same input through both and requires the same answer,
 * including the same decision about whether to throw. Where one throws and the
 * other returns, that is a difference a caller could see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JsonCompatTest {

    private object Threw {
        override fun toString() = "<threw>"
    }

    /** The value, or [Threw] — so "both refused" counts as agreement. */
    private fun probe(block: () -> Any?): Any? = try {
        val v = block()
        when {
            // Each library has its own NULL sentinel object, so they can never
            // be the same instance. What matters is that they behave alike,
            // which `the two NULL sentinels behave alike` below checks directly.
            v === MineObject.NULL || v === TheirsObject.NULL -> "<json-null>"
            // Normalise the two libraries' containers to text so they compare.
            v is MineObject || v is MineArray || v is TheirsObject || v is TheirsArray -> v.toString()
            else -> v
        }
    } catch (e: Exception) {
        Threw
    }

    private fun agree(what: String, mine: () -> Any?, theirs: () -> Any?) {
        assertEquals(what, probe(theirs), probe(mine))
    }

    // Documents chosen to cover the shapes the app actually receives, plus the
    // edges where two JSON libraries usually drift apart.
    private val documents = listOf(
        """{"a":1}""",
        """{"a":0,"b":-0,"c":-1}""",
        // Int / Long / Double boundaries: Android picks the narrowest type that
        // fits, and the typed getters behave differently per type.
        """{"i":2147483647,"j":2147483648,"k":9223372036854775807,"l":9223372036854775808}""",
        """{"d":1.5,"e":1e3,"f":1E-3,"g":0.1,"h":-0.0,"m":2.0}""",
        // Numbers sent as strings, which the feeds do.
        """{"s":"12","t":"12.9","u":" 12 ","v":"abc","w":"","x":"true","y":"TRUE","z":"0x10"}""",
        """{"b1":true,"b2":false,"n":null}""",
        """{"nested":{"a":[1,2,{"b":"c"}]},"arr":[]}""",
        """{"esc":"a\"b\\c/d\te\nf\rg\bh\u000Ci\u0001j"}""",
        """{"unicode":"café ☂ 😀"}""",
        """[]""",
        """[1,"two",3.0,true,null,{"a":1},[2]]""",
        """{"empty":{},"emptyArr":[]}""",
    )

    @Test
    fun `the same document reads the same through both`() {
        for (text in documents) {
            if (text.trimStart().startsWith("[")) {
                val mine = MineArray(text)
                val theirs = TheirsArray(text)
                agree("length of $text", { mine.length() }, { theirs.length() })
                agree("toString of $text", { mine.toString() }, { theirs.toString() })
                for (i in 0 until theirs.length()) {
                    agree("get($i) of $text", { mine.get(i) }, { theirs.get(i) })
                    agree("opt($i) of $text", { mine.opt(i) }, { theirs.opt(i) })
                    agree("isNull($i) of $text", { mine.isNull(i) }, { theirs.isNull(i) })
                    agree("getString($i) of $text", { mine.getString(i) }, { theirs.getString(i) })
                    agree("optString($i) of $text", { mine.optString(i) }, { theirs.optString(i) })
                    agree("getInt($i) of $text", { mine.getInt(i) }, { theirs.getInt(i) })
                    agree("optInt($i) of $text", { mine.optInt(i) }, { theirs.optInt(i) })
                    agree("getLong($i) of $text", { mine.getLong(i) }, { theirs.getLong(i) })
                    agree("optLong($i) of $text", { mine.optLong(i) }, { theirs.optLong(i) })
                    agree("getDouble($i) of $text", { mine.getDouble(i) }, { theirs.getDouble(i) })
                    agree("optDouble($i) of $text", { mine.optDouble(i) }, { theirs.optDouble(i) })
                    agree("getBoolean($i) of $text", { mine.getBoolean(i) }, { theirs.getBoolean(i) })
                    agree("optBoolean($i) of $text", { mine.optBoolean(i) }, { theirs.optBoolean(i) })
                }
                continue
            }

            val mine = MineObject(text)
            val theirs = TheirsObject(text)
            agree("length of $text", { mine.length() }, { theirs.length() })
            agree("toString of $text", { mine.toString() }, { theirs.toString() })

            val names = theirs.keys().asSequence().toList()
            assertEquals("key order of $text", names, mine.keys().asSequence().toList())

            // Probe every name that is there, and one that is not.
            for (name in names + "missingName") {
                agree("get($name) of $text", { mine.get(name) }, { theirs.get(name) })
                agree("opt($name) of $text", { mine.opt(name) }, { theirs.opt(name) })
                agree("has($name) of $text", { mine.has(name) }, { theirs.has(name) })
                agree("isNull($name) of $text", { mine.isNull(name) }, { theirs.isNull(name) })
                agree("getString($name) of $text", { mine.getString(name) }, { theirs.getString(name) })
                agree("optString($name) of $text", { mine.optString(name) }, { theirs.optString(name) })
                agree("optString($name, fb) of $text", { mine.optString(name, "FB") }, { theirs.optString(name, "FB") })
                agree("getInt($name) of $text", { mine.getInt(name) }, { theirs.getInt(name) })
                agree("optInt($name) of $text", { mine.optInt(name) }, { theirs.optInt(name) })
                agree("optInt($name, fb) of $text", { mine.optInt(name, -7) }, { theirs.optInt(name, -7) })
                agree("getLong($name) of $text", { mine.getLong(name) }, { theirs.getLong(name) })
                agree("optLong($name) of $text", { mine.optLong(name) }, { theirs.optLong(name) })
                agree("getDouble($name) of $text", { mine.getDouble(name) }, { theirs.getDouble(name) })
                agree("optDouble($name) of $text", { mine.optDouble(name) }, { theirs.optDouble(name) })
                agree("getBoolean($name) of $text", { mine.getBoolean(name) }, { theirs.getBoolean(name) })
                agree("optBoolean($name) of $text", { mine.optBoolean(name) }, { theirs.optBoolean(name) })
                agree("getJSONObject($name) of $text", { mine.getJSONObject(name) }, { theirs.getJSONObject(name) })
                agree("optJSONObject($name) of $text", { mine.optJSONObject(name) }, { theirs.optJSONObject(name) })
                agree("getJSONArray($name) of $text", { mine.getJSONArray(name) }, { theirs.getJSONArray(name) })
                agree("optJSONArray($name) of $text", { mine.optJSONArray(name) }, { theirs.optJSONArray(name) })
            }
        }
    }

    // ---------- the four quirks the header comment names ----------

    @Test
    fun `optString of a JSON null is the string null, not the fallback`() {
        val text = """{"n":null}"""
        val mine = MineObject(text)
        val theirs = TheirsObject(text)
        assertEquals("null", theirs.optString("n", "FALLBACK"))
        assertEquals(theirs.optString("n", "FALLBACK"), mine.optString("n", "FALLBACK"))
        assertEquals(theirs.optString("n"), mine.optString("n"))
        // But a name that is simply absent does take the fallback.
        assertEquals("FALLBACK", theirs.optString("absent", "FALLBACK"))
        assertEquals(theirs.optString("absent", "FALLBACK"), mine.optString("absent", "FALLBACK"))
        assertEquals(theirs.isNull("n"), mine.isNull("n"))
    }

    @Test
    fun `the two NULL sentinels behave alike`() {
        // probe() maps both to one marker because they can never be the same
        // instance, so the behaviour they share is asserted here instead.
        // Android's NULL equals null as well as itself, which callers rely on.
        for (nul in listOf(MineObject.NULL, TheirsObject.NULL)) {
            assertEquals("null", nul.toString())
            assertEquals(true, nul == nul)
            @Suppress("SENSELESS_COMPARISON")
            assertEquals(true, nul.equals(null))
            assertEquals(false, nul.equals("null"))
        }
        // And a parsed null lands on that sentinel on both sides.
        assertEquals(true, MineObject("""{"n":null}""").get("n") === MineObject.NULL)
        assertEquals(true, TheirsObject("""{"n":null}""").get("n") === TheirsObject.NULL)
    }

    @Test
    fun `the numeric getters read numbers sent as strings`() {
        val text = """{"a":"12","b":"12.9","c":" 12 ","d":"abc"}"""
        val mine = MineObject(text)
        val theirs = TheirsObject(text)
        assertEquals(12, theirs.getInt("a"))
        for (name in listOf("a", "b", "c", "d")) {
            agree("getInt($name)", { mine.getInt(name) }, { theirs.getInt(name) })
            agree("getLong($name)", { mine.getLong(name) }, { theirs.getLong(name) })
            agree("getDouble($name)", { mine.getDouble(name) }, { theirs.getDouble(name) })
            agree("optInt($name)", { mine.optInt(name) }, { theirs.optInt(name) })
            agree("optLong($name)", { mine.optLong(name) }, { theirs.optLong(name) })
            agree("optDouble($name)", { mine.optDouble(name) }, { theirs.optDouble(name) })
        }
    }

    @Test
    fun `put of a null removes the name rather than storing it`() {
        val mine = MineObject().put("a", 1).put("b", 2)
        val theirs = TheirsObject().put("a", 1).put("b", 2)
        mine.put("a", null as Any?)
        theirs.put("a", null as Any?)
        assertEquals(theirs.has("a"), mine.has("a"))
        assertEquals(theirs.length(), mine.length())
        assertEquals(theirs.toString(), mine.toString())
        // putOpt of null leaves the existing value alone in both.
        val mine2 = MineObject().put("a", 1)
        val theirs2 = TheirsObject().put("a", 1)
        mine2.putOpt("a", null)
        theirs2.putOpt("a", null)
        assertEquals(theirs2.toString(), mine2.toString())
    }

    @Test
    fun `toString escapes slash and writes a whole Double without its point`() {
        val mine = MineObject().put("path", "a/b").put("two", 2.0).put("half", 2.5)
        val theirs = TheirsObject().put("path", "a/b").put("two", 2.0).put("half", 2.5)
        // The documented quirks, asserted outright so the expectation is visible.
        assertEquals("""{"path":"a\/b","two":2,"half":2.5}""", theirs.toString())
        assertEquals(theirs.toString(), mine.toString())
    }

    // ---------- building and writing ----------

    @Test
    fun `the same structure built by hand writes identically`() {
        fun <O, A> build(
            newObject: () -> O,
            newArray: () -> A,
            putO: O.(String, Any?) -> Unit,
            putA: A.(Any?) -> Unit,
        ): O {
            val root = newObject()
            root.putO("int", 42)
            root.putO("long", 9_000_000_000L)
            root.putO("double", 1.5)
            root.putO("wholeDouble", 3.0)
            root.putO("bool", true)
            root.putO("string", "plain")
            root.putO("escaped", "q\"b\\s/t\tn\nr\rctrl\u0001")
            root.putO("unicode", "café ☂")
            val inner = newObject()
            inner.putO("k", "v")
            root.putO("object", inner)
            val arr = newArray()
            arr.putA(1)
            arr.putA("two")
            arr.putA(3.5)
            arr.putA(false)
            root.putO("array", arr)
            return root
        }

        val mine = build(::MineObject, ::MineArray, { n, v -> put(n, v) }, { v -> put(v) })
        val theirs = build(::TheirsObject, ::TheirsArray, { n, v -> put(n, v) }, { v -> put(v) })
        assertEquals(theirs.toString(), mine.toString())

        // And the text each produced reads back the same through both.
        assertEquals(
            TheirsObject(theirs.toString()).toString(),
            MineObject(mine.toString()).toString(),
        )
    }

    @Test
    fun `numbers keep the type Android would have given them`() {
        // Android's tokener narrows to Int where it fits, which decides whether
        // getLong, getDouble and toString see an Int, a Long or a Double. A
        // stand-in that made everything a Double would print "1.0" where the
        // app has always printed "1".
        val text = """{"small":1,"big":2147483648,"huge":9223372036854775807,"real":1.0,"exp":1e2}"""
        val mine = MineObject(text)
        val theirs = TheirsObject(text)
        for (name in listOf("small", "big", "huge", "real", "exp")) {
            // Both sides through Java reflection: Kotlin's simpleName would say
            // "Int" where Java says "Integer" for the very same class.
            assertEquals(
                "type of $name",
                theirs.get(name)::class.java.simpleName,
                mine.get(name)::class.java.simpleName,
            )
        }
        assertEquals(theirs.toString(), mine.toString())
    }

    @Test
    fun `plainly broken text is refused by both`() {
        val bad = listOf("", "{", "}", "[", "{\"a\"}", "{\"a\":}", "not json")
        for (text in bad) {
            agree("object from $text", { MineObject(text) }, { TheirsObject(text) })
            agree("array from $text", { MineArray(text) }, { TheirsArray(text) })
        }
    }

    /**
     * Where the two genuinely part company, pinned deliberately.
     *
     * Android's JSONTokener is lenient in ways the JSON grammar is not: single
     * quotes, unquoted names, a semicolon for a comma, hex literals, both
     * styles of comment, trailing commas, and text after the document. The
     * stand-in parses with kotlinx.serialization, which is strict, so these
     * inputs do not survive the move.
     *
     * This is accepted rather than fixed. Every byte of JSON this app parses is
     * machine-generated — its own seed and sync feed, the NCAA scoreboard, and
     * the Claude API — and none of those emit any of the above. Matching
     * Android here would mean hand-writing a lenient tokenizer, which is a lot
     * of new code and new risk guarding input that does not occur.
     *
     * It is pinned so the boundary is a decision on the record rather than a
     * surprise, and so that anyone who later makes the parser lenient is told
     * by a failing test exactly which cases they changed.
     */
    @Test
    fun `Android's lenient tokener accepts things the stand-in refuses`() {
        // The loud half: the stand-in throws where Android would have parsed.
        // A caller sees an exception rather than a wrong number.
        val refused = listOf(
            "{'a':1}", "{a:1}", """{"a":1;"b":2}""", """{"a":1}// trailing""",
            """{"a":1}/*c*/""", """{"a":"\x41"}""", """{"a":1}extra""", """{"a":1}}""",
        )
        for (text in refused) {
            assertNotEquals("Android still parses $text", Threw, probe { TheirsObject(text) })
            assertEquals("stand-in refuses $text", Threw, probe { MineObject(text) })
        }
        val refusedArrays = listOf("[1,]", "[,]", "[1,,2]", "[1,2,]", "[1]extra")
        for (text in refusedArrays) {
            assertNotEquals("Android still parses $text", Threw, probe { TheirsArray(text) })
            assertEquals("stand-in refuses $text", Threw, probe { MineArray(text) })
        }

        // The quiet half, and the reason this test exists. Here the stand-in
        // does NOT throw — it returns a different value, which a caller would
        // never notice. If a feed ever did send one of these, the app would
        // read a string where it used to read a number.
        assertEquals(16, TheirsObject("""{"a":0x10}""").get("a"))
        assertEquals("0x10", MineObject("""{"a":0x10}""").get("a"))

        assertEquals("x", TheirsObject("""{"a":'x'}""").get("a"))
        assertEquals("'x'", MineObject("""{"a":'x'}""").get("a"))

        assertEquals("a", TheirsArray("['a']").get(0))
        assertEquals("'a'", MineArray("['a']").get(0))
    }

    @Test
    fun `an infinite or NaN double is refused by both`() {
        agree("put NaN", { MineObject().put("a", Double.NaN) }, { TheirsObject().put("a", Double.NaN) })
        agree(
            "put Infinity",
            { MineObject().put("a", Double.POSITIVE_INFINITY) },
            { TheirsObject().put("a", Double.POSITIVE_INFINITY) },
        )
    }
}
