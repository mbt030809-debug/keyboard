package com.mbt.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingTest {
    @Test fun defaultsAreInsideRangeAndOnStep() {
        assertEquals(Timing.PAUSE_DEFAULT_MS, Timing.snapPause(Timing.PAUSE_DEFAULT_MS))
        assertEquals(Timing.LONG_PRESS_DEFAULT_MS, Timing.snapLongPress(Timing.LONG_PRESS_DEFAULT_MS))
    }

    @Test fun snapRoundsToStepAndClampsToRange() {
        assertEquals(700L, Timing.snapPause(730))
        assertEquals(800L, Timing.snapPause(760))
        assertEquals(300L, Timing.snapPause(10))
        assertEquals(3000L, Timing.snapPause(99999))
        assertEquals(350L, Timing.snapLongPress(340))
        assertEquals(200L, Timing.snapLongPress(0))
        assertEquals(800L, Timing.snapLongPress(5000))
    }

    @Test fun sliderUnitsRoundTripWithoutLoss() {
        // 슬라이더는 정수 단위: 멈춤 3..30(0.1초), 꾹 누르기 4..16(0.05초)
        for (v in 3..30) assertEquals(v * 100L, Timing.snapPause(v * Timing.PAUSE_STEP_MS))
        for (v in 4..16) assertEquals(v * 50L, Timing.snapLongPress(v * Timing.LONG_PRESS_STEP_MS))
        assertEquals(Timing.PAUSE_MIN_MS, 3 * Timing.PAUSE_STEP_MS)
        assertEquals(Timing.PAUSE_MAX_MS, 30 * Timing.PAUSE_STEP_MS)
        assertEquals(Timing.LONG_PRESS_MIN_MS, 4 * Timing.LONG_PRESS_STEP_MS)
        assertEquals(Timing.LONG_PRESS_MAX_MS, 16 * Timing.LONG_PRESS_STEP_MS)
    }

    @Test fun secondsLabel() {
        assertEquals("0.7초", Timing.seconds(700))
        assertEquals("0.35초", Timing.seconds(350))
        assertEquals("1.0초", Timing.seconds(1000))
        assertEquals("3.0초", Timing.seconds(3000))
        assertTrue(Timing.seconds(200).endsWith("초"))
    }
}
