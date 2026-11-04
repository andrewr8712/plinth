package dev.andrew.plinth.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceSizeTest {

    @Test
    fun `width under 600dp is Compact and 900dp is Expanded`() {
        assertEquals(SurfaceSize.Compact, surfaceSizeFor(599f))
        assertEquals(SurfaceSize.Compact, surfaceSizeFor(320f))
        assertEquals(SurfaceSize.Medium, surfaceSizeFor(600f))
        assertEquals(SurfaceSize.Medium, surfaceSizeFor(839f))
        assertEquals(SurfaceSize.Expanded, surfaceSizeFor(840f))
        assertEquals(SurfaceSize.Expanded, surfaceSizeFor(1_400f))
    }

    @Test
    fun `the tablet in portrait is Expanded`() {
        // TB520FU: 1840 px at 340 dpi
        assertEquals(SurfaceSize.Expanded, surfaceSizeFor(1840f / (340f / 160f)))
    }
}
