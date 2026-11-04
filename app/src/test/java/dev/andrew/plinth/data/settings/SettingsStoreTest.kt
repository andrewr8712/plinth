package dev.andrew.plinth.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.andrew.plinth.ui.screens.parsePort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    private fun store(name: String): SettingsStore =
        SettingsStore(ApplicationProvider.getApplicationContext<Context>(), fileName = name)

    @Test
    fun `defaults are the streamer and persist changes`() = runBlocking {
        val s = store("defaults")
        assertEquals("10.0.0.219", s.flow.first().host)
        assertEquals(6600, s.flow.first().port)

        s.update { it.copy(port = 6601, stayAwakeWhileCharging = true) }

        val after = s.flow.first()
        assertEquals(6601, after.port)
        assertEquals(true, after.stayAwakeWhileCharging)
        assertEquals("10.0.0.219", after.host)
    }

    @Test
    fun `a later instance reads what an earlier one wrote`() = runBlocking {
        val first = store("handover")
        first.update { it.copy(host = "192.168.1.50", meterOffsetPcmMs = 150) }

        val second = store("handover")
        val read = second.flow.first()
        assertEquals("192.168.1.50", read.host)
        assertEquals(150, read.meterOffsetPcmMs)
    }

    @Test
    fun `invalid port text is rejected before update`() {
        assertNull(parsePort("70000"))
        assertNull(parsePort("0"))
        assertNull(parsePort(""))
        assertNull(parsePort("abc"))
        assertEquals(6600, parsePort("6600"))
        assertEquals(6600, parsePort(" 6600 "))
    }
}
