package com.mbt.keyboard.ime

import com.mbt.keyboard.hangul.HangulComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapDetectorTest {
    /** 서비스와 같은 방식으로 (키, 시각) 입력을 처리해 최종 글자를 돌려준다 */
    private fun typeAt(windowMs: Long, vararg input: Pair<Char, Long>): String {
        val d = DoubleTapDetector(windowMs)
        val c = HangulComposer()
        val sb = StringBuilder()
        for ((ch, t) in input) {
            val key = ch.toString()
            var done = false
            if (d.isDouble(key, t)) {
                val out = c.doubleTap(ch)
                if (out != null) { sb.append(out.committed); d.reset(); done = true }
            }
            if (!done) { d.record(key, t); sb.append(c.input(ch).committed) }
        }
        return sb.toString() + c.composing
    }

    @Test fun withinTheWindowTheSameKeyBecomesADoubleConsonant() {
        assertEquals("ㄲ", typeAt(300, 'ㄱ' to 0L, 'ㄱ' to 250L))
        assertEquals("까", typeAt(300, 'ㄱ' to 0L, 'ㄱ' to 300L, 'ㅏ' to 500L))     // 정확히 기준 시간이어도 변환
    }

    @Test fun afterTheWindowItIsTwoSeparateConsonants() {
        assertEquals("ㄱㄱ", typeAt(300, 'ㄱ' to 0L, 'ㄱ' to 301L))
        assertEquals("ㄱㄱ", typeAt(300, 'ㄱ' to 0L, 'ㄱ' to 2000L))
    }

    @Test fun finalConsonantThenSeparateConsonantAfterTheWindow() {
        // 가 + ㄱ(받침) 뒤 기준 시간이 지나서 다시 ㄱ → 각 + ㄱ
        assertEquals("각ㄱ", typeAt(300, 'ㄱ' to 0L, 'ㅏ' to 100L, 'ㄱ' to 200L, 'ㄱ' to 700L))
    }

    @Test fun theWindowIsTheSetting() {
        assertEquals("ㄲ", typeAt(700, 'ㄱ' to 0L, 'ㄱ' to 600L))
        assertEquals("ㄱㄱ", typeAt(300, 'ㄱ' to 0L, 'ㄱ' to 600L))
        assertEquals("ㄱㄱ", typeAt(100, 'ㄱ' to 0L, 'ㄱ' to 150L))
    }

    @Test fun vowelsFollowTheSameWindow() {
        assertEquals("ㅑ", typeAt(300, 'ㅏ' to 0L, 'ㅏ' to 200L))
        assertEquals("ㅏㅏ", typeAt(300, 'ㅏ' to 0L, 'ㅏ' to 400L))
    }

    @Test fun differentKeysAndResetNeverCount() {
        val d = DoubleTapDetector(300)
        d.record("ㄱ", 0)
        assertFalse(d.isDouble("ㄴ", 100))
        assertTrue(d.isDouble("ㄱ", 100))
        d.reset()
        assertFalse(d.isDouble("ㄱ", 100))
    }
}
