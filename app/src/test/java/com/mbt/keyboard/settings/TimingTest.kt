package com.mbt.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingTest {
    @Test fun defaultsAreInsideRangeAndOnStep() {
        assertEquals(Timing.DOUBLE_TAP_DEFAULT_MS, Timing.snapDoubleTap(Timing.DOUBLE_TAP_DEFAULT_MS))
        assertEquals(Timing.LONG_PRESS_DEFAULT_MS, Timing.snapLongPress(Timing.LONG_PRESS_DEFAULT_MS))
    }

    @Test fun snapRoundsToStepAndClampsToRange() {
        assertEquals(300L, Timing.snapDoubleTap(310))
        assertEquals(350L, Timing.snapDoubleTap(340))
        assertEquals(100L, Timing.snapDoubleTap(10))
        assertEquals(1000L, Timing.snapDoubleTap(99999))
        assertEquals(350L, Timing.snapLongPress(340))
        assertEquals(200L, Timing.snapLongPress(0))
        assertEquals(800L, Timing.snapLongPress(5000))
    }

    @Test fun sliderUnitsRoundTripWithoutLoss() {
        // 슬라이더는 정수 단위: 연타 2..20(0.05초), 꾹 누르기 4..16(0.05초)
        for (v in 2..20) assertEquals(v * 50L, Timing.snapDoubleTap(v * Timing.DOUBLE_TAP_STEP_MS))
        for (v in 4..16) assertEquals(v * 50L, Timing.snapLongPress(v * Timing.LONG_PRESS_STEP_MS))
        assertEquals(Timing.DOUBLE_TAP_MIN_MS, 2 * Timing.DOUBLE_TAP_STEP_MS)
        assertEquals(Timing.DOUBLE_TAP_MAX_MS, 20 * Timing.DOUBLE_TAP_STEP_MS)
        assertEquals(Timing.LONG_PRESS_MIN_MS, 4 * Timing.LONG_PRESS_STEP_MS)
        assertEquals(Timing.LONG_PRESS_MAX_MS, 16 * Timing.LONG_PRESS_STEP_MS)
    }

    @Test fun secondsLabel() {
        assertEquals("0.7초", Timing.seconds(700))
        assertEquals("0.35초", Timing.seconds(350))
        assertEquals("1.0초", Timing.seconds(1000))
        assertEquals("0.3초", Timing.seconds(300))
        assertTrue(Timing.seconds(200).endsWith("초"))
    }

    @Test fun repeatSpeedMapsToInterval() {
        assertEquals(45L, Timing.repeatIntervalMs(22))             // 기본은 예전 고정값(45ms)
        assertEquals(200L, Timing.repeatIntervalMs(5))
        assertEquals(25L, Timing.repeatIntervalMs(40))
        assertEquals(5L, Timing.repeatIntervalMs(200))             // 최대 초당 200자
        assertEquals(5L, Timing.repeatIntervalMs(999))             // 범위 밖은 끝값으로
        assertEquals(200L, Timing.repeatIntervalMs(0))
        assertTrue((Timing.REPEAT_CPS_MIN..Timing.REPEAT_CPS_MAX).map { Timing.repeatIntervalMs(it) }.zipWithNext().all { (a, b) -> a >= b })  // 빠를수록 간격이 같거나 짧다
    }
}
