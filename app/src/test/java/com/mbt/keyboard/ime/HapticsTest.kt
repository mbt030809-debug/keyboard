package com.mbt.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsTest {
    @Test fun strengthMapsToDurationAndAmplitude() {
        assertEquals(7L, Haptics.durationMs(1))
        assertEquals(25L, Haptics.durationMs(10))
        assertEquals(25, Haptics.amplitude(1))
        assertEquals(250, Haptics.amplitude(10))
    }

    @Test fun outOfRangeStrengthIsClamped() {
        assertEquals(Haptics.durationMs(1), Haptics.durationMs(-5))
        assertEquals(Haptics.durationMs(10), Haptics.durationMs(99))
        assertTrue(Haptics.amplitude(99) in 1..255)
    }

    @Test fun strongerMeansLongerAndHarder() {
        for (s in 1 until 10) {
            assertTrue(Haptics.durationMs(s + 1) > Haptics.durationMs(s))
            assertTrue(Haptics.amplitude(s + 1) > Haptics.amplitude(s))
        }
    }
}
