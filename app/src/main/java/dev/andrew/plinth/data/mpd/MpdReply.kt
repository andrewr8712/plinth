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
