package dev.andrew.plinth.data.loudness

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** One 200 ms window of a track: left RMS, right RMS and peak, as 0..255 dBFS bytes. */
data class StereoLevel(val left: Int, val right: Int, val peak: Int) {

    /** The louder channel in dBFS, for a numeric header. */
    val dbfs: Int get() = -(60 - (maxOf(left, right) * 60 / 255))

    companion object {
        fun fromBytes(l: Int, r: Int, p: Int) = StereoLevel(l and 0xFF, r and 0xFF, p and 0xFF)
    }
}

/**
 * The precomputed loudness envelope, memory-mapped.
 *
 * The file is a header plus one record per track, keyed by MPD URI. Lookups are a
 * binary search over an in-memory offset table and one three-byte read, so drawing
 * the meter costs nothing per frame.
 */
class LoudnessIndex private constructor(
    private val buffer: MappedByteBuffer,
    private val records: Map<String, Offsets>,
    val dbUpdate: Long,
    val rate: Int = 5,
) {

    private class Offsets(val start: Int, val frames: Int)

    val trackCount: Int get() = records.size

    /**
     * The level at [positionSec] into the track, or null when the track is absent.
     *
     * A position before the start clamps to the first frame: the meter runs behind
     * playback by the output's latency, so at the very start of a track the offset
     * lands slightly before zero and the honest answer is the first window, not silence.
     */
    fun levelAt(uri: String, positionSec: Double): StereoLevel? {
        val record = records[uri] ?: return null
        val frame = maxOf(0, (positionSec * rate).toInt())
        if (frame >= record.frames) return null
        val offset = record.start + frame * BYTES_PER_FRAME
        return StereoLevel.fromBytes(
            buffer.get(offset).toInt(),
            buffer.get(offset + 1).toInt(),
            buffer.get(offset + 2).toInt(),
        )
    }

    fun close() {
        // A mapped read-only buffer needs no teardown: the GC unmaps it. Kept so callers
        // that open and close per import read the same either way.
    }

    companion object {
        const val MAGIC = "VU01"
        const val VERSION = 1
        private const val HEADER_BYTES = 4 + 2 + 8 + 4
        private const val BYTES_PER_FRAME = 3

        /** Opens an envelope file, or returns null when it is not one. */
        fun open(file: File): LoudnessIndex? {
            if (!file.isFile || file.length() < HEADER_BYTES) return null
            return try {
                RandomAccessFile(file, "r").use { raf ->
                    val channel = raf.channel
                    val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                    // Compare the magic as bytes: an int read depends on the byte order
                    // the rest of the header needs, which is a trap waiting to be set wrong.
                    val magic = ByteArray(4) { buffer.get(it) }
                    if (String(magic, Charsets.US_ASCII) != MAGIC) return null
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    // Absolute gets do not move the position, and the header reads that
                    // follow are relative — start them at the first field.
                    buffer.position(4)
                    val version = buffer.short.toInt() and 0xFFFF
                    if (version != VERSION) return null
                    val dbUpdate = buffer.long
                    val count = buffer.int
                    if (count < 0) return null

                    val records = HashMap<String, Offsets>(count * 2)
                    var pos = HEADER_BYTES
                    repeat(count) {
                        if (pos + 2 > buffer.capacity()) return null
                        val uriLen = buffer.getShort(pos).toInt() and 0xFFFF
                        pos += 2
                        if (pos + uriLen + 4 > buffer.capacity()) return null
                        val raw = ByteArray(uriLen)
                        for (i in 0 until uriLen) raw[i] = buffer.get(pos + i)
                        pos += uriLen
                        val frames = buffer.getInt(pos)
                        pos += 4
                        if (frames < 0 || pos + frames * BYTES_PER_FRAME > buffer.capacity()) return null
                        records[String(raw, Charsets.UTF_8)] = Offsets(pos, frames)
                        pos += frames * BYTES_PER_FRAME
                    }
                    LoudnessIndex(buffer, records, dbUpdate)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** What an import produced, so the UI can say something true. */
sealed interface ImportResult {
    data class Imported(val tracks: Int, val dbUpdate: Long) : ImportResult
    data class Rejected(val reason: String) : ImportResult
}

/**
 * Copies a chosen `loudness.bin` into app storage next to the app's other data, so the
 * meter can map it without holding a content-URI permission.
 */
class LoudnessImporter(private val filesDir: File) {

    val file: File get() = File(filesDir, FILE_NAME)

    fun importFrom(input: java.io.InputStream): ImportResult {
        val target = file
        val temp = File(filesDir, "$FILE_NAME.part")
        return try {
            input.use { stream ->
                temp.outputStream().use { out -> stream.copyTo(out, 1 shl 16) }
            }
            val opened = LoudnessIndex.open(temp)
                ?: run {
                    temp.delete()
                    return ImportResult.Rejected("Not a Plinth loudness file")
                }
            val tracks = opened.trackCount
            val dbUpdate = opened.dbUpdate
            opened.close()
            if (!temp.renameTo(target)) {
                temp.delete()
                return ImportResult.Rejected("Could not replace the existing file")
            }
            ImportResult.Imported(tracks, dbUpdate)
        } catch (e: Exception) {
            temp.delete()
            ImportResult.Rejected(e.message ?: "Could not read the file")
        }
    }

    /** The imported envelope, or null when nothing has been imported yet. */
    fun open(): LoudnessIndex? = LoudnessIndex.open(file)

    companion object {
        const val FILE_NAME = "loudness.bin"
    }
}
