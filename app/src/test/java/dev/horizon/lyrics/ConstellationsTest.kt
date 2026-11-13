package dev.horizon.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ConstellationsTest {
    @Test fun `every figure is well formed`() {
        for (c in Constellations.all) {
            assertEquals(c.name, c.starCount * 2, c.stars.size)
            assertTrue(c.name, c.edges.size % 2 == 0 && c.edges.isNotEmpty())
            assertTrue(c.name, c.edges.all { it in 0 until c.starCount })
            assertTrue(c.name, c.stars.all { it in 0f..1f })
            assertTrue(c.name, c.width in 0f..1f && c.height in 0f..1f && maxOf(c.width, c.height) == 1f)
            assertTrue(c.name, c.hindi.isNotBlank())
        }
    }

    @Test fun `real brightness`() {
        // Sirius (Canis Major) is the brightest star in the sky, Vega (Lyra) close to magnitude 0.
        assertEquals(-1.44f, Constellations.all.first { it.name == "Canis Major" }.magnitudes.min(), 0.05f)
        assertEquals(0.03f, Constellations.all.first { it.name == "Lyra" }.magnitudes.min(), 0.05f)
    }

    @Test fun `very rare`() {
        val random = Random(7)
        val hits = (1..100_000).count { ConstellationEgg.roll(random) != null }
        // About 1 in 500: 200 expected.
        assertTrue("$hits", hits in 140..260)
    }

    @Test fun `debug builds can force one, once`() {
        ConstellationEgg.forceNext = true
        assertNotNull(ConstellationEgg.roll(Random(1)))
        assertNull(ConstellationEgg.roll(object : Random() {
            override fun nextBits(bitCount: Int) = 0x7FFFFFFF ushr (31 - bitCount)
        }))
    }
}
