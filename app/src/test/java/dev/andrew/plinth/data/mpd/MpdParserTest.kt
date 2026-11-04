package dev.andrew.plinth.data.mpd

import org.junit.Assert.assertEquals
import org.junit.Test

class MpdParserTest {

    @Test
    fun `parses key value pairs including values containing colons`() {
        val r = MpdParser.parse("state: play\ntime: 0:78\nfile: a:b/c.flac\nOK\n")
        assertEquals(
            MpdReply.Ok(
                listOf(
                    "state" to "play",
                    "time" to "0:78",
                    "file" to "a:b/c.flac",
                ),
            ),
            r,
        )
    }

    @Test
    fun `parses ACK with code, command index and message`() {
        val r = MpdParser.parse("ACK [5@0] {setvol} No mixer\n")
        assertEquals(MpdReply.Ack(5, 0, "setvol", "No mixer"), r)
    }

    @Test
    fun `keeps repeated keys in order`() {
        val p = MpdParser.pairs("file: a\nTime: 10\nfile: b\nTime: 20\nOK\n")
        assertEquals(4, p.size)
        assertEquals("a", p[0].second)
        assertEquals("b", p[2].second)
    }
}
