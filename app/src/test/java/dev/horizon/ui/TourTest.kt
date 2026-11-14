package dev.horizon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourTest {
    private val main = TourScreen(onLyrics = false, onClock = false, controlsOpen = false, hasMusic = true, canShowClock = true)

    @Test fun `with music, the full tour in order`() {
        assertEquals(TourStep.Back, Tour.next(TourStep.Lyrics, main))
        assertEquals(TourStep.Clock, Tour.next(TourStep.Back, main))
        assertEquals(TourStep.Controls, Tour.next(TourStep.Clock, main))
        assertEquals(TourStep.Done, Tour.next(TourStep.Controls, main))
    }

    @Test fun `each step is done by its gesture`() {
        assertFalse(Tour.isDone(TourStep.Lyrics, main))
        assertTrue(Tour.isDone(TourStep.Lyrics, main.copy(onLyrics = true)))
        assertFalse(Tour.isDone(TourStep.Back, main.copy(onLyrics = true)))
        assertTrue(Tour.isDone(TourStep.Back, main))
        assertTrue(Tour.isDone(TourStep.Clock, main.copy(onClock = true)))
        assertTrue(Tour.isDone(TourStep.Controls, main.copy(controlsOpen = true)))
        assertFalse(Tour.isDone(TourStep.Done, main.copy(controlsOpen = true)))
    }

    @Test fun `with nothing playing, the lyrics step waits for Next and skips coming back`() {
        val quiet = main.copy(hasMusic = false)
        assertFalse(Tour.isDone(TourStep.Lyrics, quiet.copy(onLyrics = true)))
        assertEquals(TourStep.Clock, Tour.next(TourStep.Lyrics, quiet))
    }

    @Test fun `no full-clock page, no clock step`() {
        val noClock = main.copy(canShowClock = false)
        assertEquals(TourStep.Controls, Tour.next(TourStep.Back, noClock))
        assertEquals(TourStep.Controls, Tour.next(TourStep.Lyrics, noClock.copy(hasMusic = false)))
    }
}
