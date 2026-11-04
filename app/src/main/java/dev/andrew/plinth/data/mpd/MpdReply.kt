package dev.andrew.plinth.data.mpd

/** A parsed reply from the MPD line protocol. */
sealed interface MpdReply {

    /** A reply that terminated with `OK`, carrying every `key: value` line in order. */
    data class Ok(val pairs: List<Pair<String, String>>) : MpdReply

    /** A reply that terminated with `ACK [code@cmdIndex] {command} message`. */
    data class Ack(
        val code: Int,
        val commandIndex: Int,
        val command: String,
        val message: String,
    ) : MpdReply
}

fun Pair<String, String>.intOrNull(): Int? = second.trim().toIntOrNull()

fun Pair<String, String>.doubleOrNull(): Double? = second.trim().toDoubleOrNull()

/** A binary reply: `size` is the whole payload, `bytes` what this read returned. */
data class BinaryReply(val size: Int, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryReply) return false
        return size == other.size && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * size + bytes.contentHashCode()
}
