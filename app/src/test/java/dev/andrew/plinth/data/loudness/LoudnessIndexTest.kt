package dev.andrew.plinth.data.loudness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LoudnessIndexTest {

    private fun envelope(vararg tracks: Triple<String, Long, ByteArray>): File {
        val out = ByteArrayOutputStream()
        val count = tracks.size
        out.write("VU01".toByteArray(Charsets.US_ASCII))
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(1_793_352_754L).array())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(count).array())
        tracks.forEach { (uri, frames, bytes) ->
            val raw = uri.toByteArray(Charsets.UTF_8)
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(raw.size.toShort()).array())
            out.write(raw)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(frames.toInt()).array())
            out.write(bytes)
        }
        val file = File.createTempFile("loudness", ".bin")
        file.deleteOnExit()
        file.writeBytes(out.toByteArray())
        return file
    }

    private fun frames(count: Int, left: Int, right: Int, peak: Int): ByteArray =
        ByteArray(count * 3) { i ->
            when (i % 3) {
                0 -> left.toByte()
                1 -> right.toByte()
                else -> peak.toByte()
            }
        }

    @Test
    fun `absent track returns null, not zero`() {
        val index = LoudnessIndex.open(envelope(Triple("a.flac", 10L, frames(10, 128, 120, 200))))!!
        assertNull(index.levelAt("nope.flac", 10.0))
    }

    @Test
    fun `position maps to the right frame and past the end is null`() {
        val bytes = ByteArray(30) // 10 frames
        bytes[0 * 3] = 10; bytes[0 * 3 + 1] = 11; bytes[0 * 3 + 2] = 12
        bytes[5 * 3] = 100; bytes[5 * 3 + 1] = 101; bytes[5 * 3 + 2] = 102
        val index = LoudnessIndex.open(envelope(Triple("a.flac", 10L, bytes)))!!

        val atZero = index.levelAt("a.flac", 0.0)!!
        assertEquals(10, atZero.left)
        assertEquals(11, atZero.right)
        assertEquals(12, atZero.peak)

        // 5 Hz: 1.0 s is frame 5
        val atOne = index.levelAt("a.flac", 1.0)!!
        assertEquals(100, atOne.left)

        assertNull("past the last frame must not clamp", index.levelAt("a.flac", 9999.0))
        // The meter subtracts the output latency, so a track's first frames are read at
        // a slightly negative position. That is the first window, not a missing frame.
        val beforeStart = index.levelAt("a.flac", -0.12)!!
        assertEquals(10, beforeStart.left)
    }

    @Test
    fun `header carries the db_update and the track count`() {
        val index = LoudnessIndex.open(
            envelope(
                Triple("a.flac", 2L, frames(2, 1, 1, 1)),
                Triple("b.flac", 3L, frames(3, 1, 1, 1)),
            ),
        )!!
        assertEquals(2, index.trackCount)
        assertEquals(1_793_352_754L, index.dbUpdate)
    }

    @Test
    fun `bad magic or a short file returns null from open`() {
        val zeros = File.createTempFile("loudness-bad", ".bin").apply {
            deleteOnExit()
            writeBytes(ByteArray(64))
        }
        assertNull(LoudnessIndex.open(zeros))

        val tiny = File.createTempFile("loudness-tiny", ".bin").apply {
            deleteOnExit()
            writeBytes(ByteArray(4))
        }
        assertNull(LoudnessIndex.open(tiny))
        assertNull(LoudnessIndex.open(File("/nonexistent/loudness.bin")))
    }

    @Test
    fun `a truncated record is rejected rather than read past the buffer`() {
        val data = ByteArrayOutputStream()
        data.write("VU01".toByteArray(Charsets.US_ASCII))
        data.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
        data.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(0).array())
        data.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array())
        val uri = "a.flac".toByteArray()
        data.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(6).array())
        data.write(uri)
        data.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1000).array())
        data.write(ByteArray(9)) // claims 1000 frames, holds 3
        val file = File.createTempFile("loudness-trunc", ".bin").apply {
            deleteOnExit()
            writeBytes(data.toByteArray())
        }
        assertNull(LoudnessIndex.open(file))
    }

    @Test
    fun `importer keeps a good file and rejects a bad one`() {
        val dir = File.createTempFile("loudness-dir", "").let { f ->
            f.delete()
            f.mkdirs()
            f
        }
        val importer = LoudnessImporter(dir)
        val good = envelope(Triple("a.flac", 4L, frames(4, 0, 0, 0)))

        val ok = importer.importFrom(good.inputStream()) as ImportResult.Imported
        assertEquals(1, ok.tracks)
        assertNotNull(importer.open())

        val bad = File.createTempFile("junk", ".bin").apply { writeBytes(ByteArray(128)) }
        val rejected = importer.importFrom(bad.inputStream())
        assertTrue(rejected is ImportResult.Rejected)
        // the good file survives a bad import
        assertNotNull(importer.open())
    }

    @Test
    fun `dbfs turns a byte back into a readable level`() {
        assertEquals(0, StereoLevel(255, 255, 255).dbfs)
        assertEquals(-60, StereoLevel(0, 0, 0).dbfs)
        assertEquals(-30, StereoLevel(128, 128, 128).dbfs)
    }
}
