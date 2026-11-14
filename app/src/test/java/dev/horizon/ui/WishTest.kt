package dev.horizon.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishTest {
    @Test fun `eleven eleven on a 12-hour clock, morning and evening`() {
        assertTrue(WishEgg.isWishMinute(11, 11, is24Hour = false))
        assertTrue(WishEgg.isWishMinute(23, 11, is24Hour = false))
    }

    @Test fun `only 11 11 on a 24-hour clock`() {
        assertTrue(WishEgg.isWishMinute(11, 11, is24Hour = true))
        assertFalse(WishEgg.isWishMinute(23, 11, is24Hour = true))
    }

    @Test fun `not the minutes either side`() {
        assertFalse(WishEgg.isWishMinute(11, 10, is24Hour = false))
        assertFalse(WishEgg.isWishMinute(11, 12, is24Hour = false))
        assertFalse(WishEgg.isWishMinute(1, 11, is24Hour = false))
    }
}
