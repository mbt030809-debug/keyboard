package com.mbt.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticStyleTest {
    @Test fun sixStylesWithUniqueIds() {
        assertEquals(6, HapticStyle.values().size)
        assertEquals(6, HapticStyle.values().map { it.id }.toSet().size)
        assertEquals(6, HapticStyle.values().map { it.label }.toSet().size)
    }

    @Test fun everyPatternIsValidAtEveryStrength() {
        for (s in 1..10) HapticStyle.values().forEach { st ->
            val p = st.pattern(s)
            assertEquals(p.timings.size, p.amplitudes.size)
            assertTrue(st.id, p.timings.isNotEmpty() && p.timings.all { it >= 1L })
            assertTrue(st.id, p.amplitudes.all { it in 0..255 } && p.amplitudes.any { it > 0 })
            assertTrue("총 길이 ${st.id} ${p.timings.sum()}", p.timings.sum() in 1L..80L)         // 한 번 누를 때 너무 길지 않게
        }
    }

    @Test fun strongerSettingMeansStrongerPeak() {
        HapticStyle.values().forEach { st ->
            assertTrue(st.id, st.pattern(10).amplitudes.max() > st.pattern(1).amplitudes.max())
        }
    }

    @Test fun stylesFeelDifferent() {
        val feels = HapticStyle.values().map { st -> st.pattern(5).let { it.timings.toList() to it.amplitudes.toList() } }
        assertEquals(feels.size, feels.toSet().size)
        // 청축은 클릭이 두 번(쉬는 구간 사이에 진동이 둘), 흑축은 한 번 길고 묵직
        val blue = HapticStyle.BLUE.pattern(5).amplitudes
        assertEquals(listOf(true, false, true), blue.map { it > 0 })
        assertTrue(HapticStyle.BLACK.pattern(5).timings.first() > HapticStyle.BLUE.pattern(5).timings.first())
        // 적축은 가장 약하다
        val peaks = HapticStyle.values().filter { it != HapticStyle.DEFAULT }.associateWith { it.pattern(5).amplitudes.max() }
        assertEquals(HapticStyle.RED, peaks.minByOrNull { it.value }!!.key)
    }

    @Test fun unknownIdFallsBackToDefault() {
        assertEquals(HapticStyle.DEFAULT, HapticStyle.byId(null))
        assertEquals(HapticStyle.DEFAULT, HapticStyle.byId("nope"))
        assertNotEquals(HapticStyle.DEFAULT, HapticStyle.byId("blue"))
    }
}
