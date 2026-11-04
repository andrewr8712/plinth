package dev.andrew.plinth.data.mpd

/** Failures the transport can surface. Every call is bounded, so none of these hang. */
sealed class MpdException(message: String) : Exception(message) {
    class Timeout(message: String = "MPD did not answer in time") : MpdException(message)
    class Protocol(message: String) : MpdException(message)
    class Disconnected(message: String = "Connection closed") : MpdException(message)
}
