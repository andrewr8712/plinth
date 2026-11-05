package dev.andrew.plinth.data.art

import dev.andrew.plinth.data.mpd.MpdClient
import dev.andrew.plinth.data.mpd.MpdException
import java.io.File

sealed interface ArtResult {
    class Jpeg(val bytes: ByteArray) : ArtResult
    data object None : ArtResult
    data class Failed(val reason: String) : ArtResult
}

/**
 * Covers on disk, keyed by album id.
 *
 * `ACK [50]` is the only evidence that an album has no cover, so it earns a negative
 * marker; a timeout or a dropped connection is not evidence of anything and must not,
 * or a stale share would convince the app that half the library has no art.
 */
class ArtRepository(
    private val client: MpdClient,
    cacheDir: File,
) {

    private val root = File(cacheDir, "art").apply { mkdirs() }
    private var limitRaised = false

    fun cachedFile(albumId: String): File? =
        File(root, "$albumId.jpg").takeIf { it.isFile && it.length() > 0 }

    fun negativeCached(albumId: String): Boolean {
        val marker = File(root, "none-$albumId")
        if (!marker.isFile) return false
        return System.currentTimeMillis() - marker.lastModified() < NEGATIVE_TTL_MS
    }

    suspend fun forAlbum(albumId: String, firstTrackUri: String): ArtResult {
        cachedFile(albumId)?.let { file ->
            return try {
                ArtResult.Jpeg(file.readBytes())
            } catch (e: Exception) {
                ArtResult.Failed(e.message ?: "cache read failed")
            }
        }
        if (negativeCached(albumId)) return ArtResult.None

        raiseLimitOnce()

        return try {
            val reply = client.albumArt(firstTrackUri)
            if (reply.bytes.isEmpty()) {
                ArtResult.Failed("empty reply")
            } else {
                File(root, "$albumId.jpg").writeBytes(reply.bytes)
                ArtResult.Jpeg(reply.bytes)
            }
        } catch (e: MpdException.Ack) {
            if (e.code == NO_FILE) {
                markNone(albumId)
                ArtResult.None
            } else {
                ArtResult.Failed(e.message ?: "ack ${e.code}")
            }
        } catch (e: MpdException) {
            ArtResult.Failed(e.message ?: "unavailable")
        }
    }

    private suspend fun raiseLimitOnce() {
        if (limitRaised) return
        limitRaised = true
        // An ACK here is harmless: the default chunking still works, just slower.
        runCatching { client.binaryLimit(CHUNK_LIMIT) }
    }

    private fun markNone(albumId: String) {
        runCatching { File(root, "none-$albumId").writeText("") }
    }

    private companion object {
        const val NO_FILE = 50
        const val CHUNK_LIMIT = 4_000_000
        const val NEGATIVE_TTL_MS = 7L * 24 * 60 * 60 * 1_000
    }
}
