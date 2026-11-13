package dev.horizon.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {
    private fun words(vararg lines: String): List<TimedWord> {
        var t = 0L
        return lines.flatMapIndexed { line, text -> text.split(' ').map { TimedWord(line, it, t++) } }
    }

    @Test fun `finds every time the title is sung`() {
        val ws = words("I'm in love with the shape of you", "We push and pull", "The shape of you, yeah")
        assertEquals(listOf(5..7, 13..15), ws.titleRuns("Shape of You"))
    }

    @Test fun `ignores case, punctuation and video noise`() {
        val ws = words("Hello, HELLO darkness")
        assertEquals(listOf(0..0, 1..1), ws.titleRuns("Hello (Official Video)"))
    }

    @Test fun `can run across a line break`() {
        val ws = words("all I want is", "you tonight")
        assertEquals(listOf(3..4), ws.titleRuns("Is You"))
    }

    @Test fun `short one-word titles and missing titles match nothing`() {
        val ws = words("Up up and away")
        assertTrue(ws.titleRuns("Up").isEmpty())
        assertTrue(ws.titleRuns("Somewhere Else").isEmpty())
        assertTrue(ws.titleRuns("").isEmpty())
    }
}
