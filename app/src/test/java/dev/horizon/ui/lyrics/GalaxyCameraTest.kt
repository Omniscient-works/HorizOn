package dev.horizon.ui.lyrics

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GalaxyCameraTest {
    private val elevation = (30 * PI / 180).toFloat()
    private fun cam(tx: Float = 0f) = GalaxyCamera(Offset(500f, 300f), elevation, dist = 1000f, tx = tx)

    @Test fun `the near side looks bigger than the far side`() {
        val c = cam()
        c.project(0f, -200f, 0f) // near
        val near = c.scale
        c.project(0f, 200f, 0f) // far
        assertTrue(near > 1f && c.scale < 1f)
    }

    @Test fun `parallax leaves the centre still and slides near and far apart`() {
        val still = cam()
        val moved = cam(tx = 100f)
        still.project(0f, 0f, 0f)
        moved.project(0f, 0f, 0f)
        assertEquals(still.sx, moved.sx, 0.001f)

        still.project(0f, -200f, 0f)
        moved.project(0f, -200f, 0f)
        val nearShift = moved.sx - still.sx
        still.project(0f, 200f, 0f)
        moved.project(0f, 200f, 0f)
        val farShift = moved.sx - still.sx
        assertTrue("near $nearShift far $farShift", nearShift < 0f && farShift > 0f)
    }
}
