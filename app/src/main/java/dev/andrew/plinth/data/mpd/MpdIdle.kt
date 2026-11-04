package dev.andrew.plinth.data.mpd

/** Sits in `idle` until MPD has something to say, then reports which subsystems changed. */
class MpdIdle(private val socket: MpdSocket) : IdleWaiter {

    override suspend fun awaitChange(): Set<String> {
        // 0 = no socket timeout: idle is meant to block until MPD answers.
        val reply = socket.command("idle $SUBSYSTEMS", timeoutMs = 0)
        val pairs = when (reply) {
            is MpdReply.Ok -> reply.pairs
            is MpdReply.Ack -> throw MpdException.Protocol(reply.message)
        }
        return pairs.filter { it.first == "changed" }.map { it.second }.toSet()
    }

    private companion object {
        /** Only what the app reacts to; `mixer`/`partition` would just cause wakeups. */
        const val SUBSYSTEMS = "player playlist options database"
    }
}

interface IdleWaiter {
    suspend fun awaitChange(): Set<String>
}
