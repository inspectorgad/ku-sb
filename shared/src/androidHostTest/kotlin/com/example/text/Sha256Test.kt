package com.example.text

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sha256.kt against java.security.MessageDigest, which it replaced.
 *
 * SeasonSync keeps the hash of the last data it merged and skips a download
 * whose hash matches. Two different kinds of bug hide in here:
 *
 * - a hash that is wrong but stable would make every phone re-merge once after
 *   the update, which is merely noisy; but
 * - a hash that collides where MessageDigest would not, or that ignores part
 *   of its input, would make the sync decide nothing had changed when it had,
 *   and the reader's season data would silently stop updating.
 *
 * The second is the one worth testing hard, and it is why the length sweep
 * below walks every size through two full blocks rather than sampling: SHA-256
 * padding is the part most likely to drop or double-count the tail, and it
 * changes behaviour exactly at 55, 56, 63, 64 and their multiples.
 */
class Sha256Test {

    private fun reference(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun check(data: ByteArray, what: String) {
        assertEquals(
            reference(data).toHexString(),
            sha256(data).toHexString(),
            "sha256 of $what",
        )
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    @Test
    fun `the published FIPS vectors`() {
        // Independent of MessageDigest: if both implementations were wrong in
        // the same way, the comparisons below would agree and prove nothing.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256(ByteArray(0)).toHexString(),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).toHexString(),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256(
                "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray(),
            ).toHexString(),
        )
        // One million 'a', the long FIPS vector: catches a block loop that
        // drifts only after many blocks.
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            sha256(ByteArray(1_000_000) { 'a'.code.toByte() }).toHexString(),
        )
    }

    @Test
    fun `every length through two blocks and past the padding boundaries`() {
        // 0..160 covers the one-block case, the 55-56 boundary where the
        // length no longer fits beside the message, the exact block at 64, and
        // the same again for the second block.
        for (size in 0..160) {
            check(ByteArray(size) { (it * 31 + 7).toByte() }, "$size bytes of a ramp")
            check(ByteArray(size), "$size zero bytes")
            check(ByteArray(size) { 0xFF.toByte() }, "$size 0xFF bytes")
        }
        // The same boundaries one and two blocks further out.
        for (size in listOf(55, 56, 63, 64, 65, 119, 120, 127, 128, 129, 183, 184, 191, 192, 193)) {
            check(ByteArray(size) { (it * 17).toByte() }, "$size bytes at a boundary")
        }
    }

    @Test
    fun `a random sweep, every byte value and every length`() {
        val random = Random(20260218)
        repeat(4_000) {
            val data = random.nextBytes(random.nextInt(0, 600))
            check(data, "${data.size} random bytes")
        }
        // A few large ones, where a 32-bit length or a signed shift would show.
        for (size in listOf(4_096, 10_000, 65_536, 100_000)) {
            check(random.nextBytes(size), "$size random bytes")
        }
    }

    @Test
    fun `changing any single bit changes the hash`() {
        // The property the sync depends on. If sha256 ever ignored part of its
        // input, this is what would catch it, and the length sweep above would
        // not.
        val random = Random(20260219)
        repeat(300) {
            val data = random.nextBytes(random.nextInt(1, 300))
            val before = sha256(data).toHexString()
            val index = random.nextInt(data.size)
            val bit = 1 shl random.nextInt(8)
            val flipped = data.copyOf()
            flipped[index] = (flipped[index].toInt() xor bit).toByte()
            assertEquals(reference(flipped).toHexString(), sha256(flipped).toHexString())
            kotlin.test.assertNotEquals(
                before,
                sha256(flipped).toHexString(),
                "flipping a bit at $index left the hash unchanged",
            )
        }
    }

    @Test
    fun `the hex string the sync actually stores`() {
        // SeasonSync does sha256(body).joinToString("") { hex2(it) } and keeps
        // that string. Test the whole composition, not just the digest: hex2
        // is the other hand-written piece, and a sign error there would turn
        // every byte above 0x7F into something else.
        val random = Random(20260220)
        repeat(500) {
            val body = random.nextBytes(random.nextInt(0, 400))
            val mine = sha256(body).joinToString("") { hex2(it) }
            val java = reference(body).joinToString("") { "%02x".format(it) }
            assertEquals(java, mine)
            assertEquals(64, mine.length, "a SHA-256 hex string is 64 characters")
        }
        // A realistic payload shape, since that is what actually goes through.
        val json = buildString {
            append("""{"games":[""")
            for (i in 0 until 400) {
                if (i > 0) append(',')
                append("""{"date":"2026-03-%02d","opponent":"Team %d","runs":%d}""".format(i % 28 + 1, i, i % 13))
            }
            append("]}")
        }.encodeToByteArray()
        check(json, "a season-data-shaped payload")
    }
}
