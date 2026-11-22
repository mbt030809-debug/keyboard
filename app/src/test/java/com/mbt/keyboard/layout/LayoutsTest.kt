package com.mbt.keyboard.layout

import com.mbt.keyboard.hangul.Jamo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    @Test fun koreanLettersAreJamo() {
        Layouts.korean.rows.flatten()
            .filter { it.type == KeyType.CHAR && it.label !in listOf(",", ".") }
            .forEach {
                assertTrue("${it.label}", Jamo.isJamo(it.output[0]))
                it.shifted?.let { s -> assertTrue(s, Jamo.isJamo(s[0])) }
            }
    }

    @Test fun koreanHas21VowelAndConsonantKeysCovered() {
        val outs = Layouts.korean.rows.flatten().filter { it.type == KeyType.CHAR }
            .flatMap { listOfNotNull(it.output, it.shifted) }.toSet()
        // 두벌식은 쉬프트 포함 모든 기본 자음(19)·단모음 중 겹모음 제외 전부 입력 가능
        "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅑㅒㅓㅔㅕㅖㅗㅛㅜㅠㅡㅣ".forEach {
            assertTrue("missing $it", it.toString() in outs)
        }
    }

    @Test fun englishShiftIsUppercase() {
        val q = Layouts.english.rows[0][0]
        assertEquals("q", q.outputFor(false))
        assertEquals("Q", q.outputFor(true))
    }

    @Test fun everyRowHasWeight() {
        Layouts.all.forEach { l -> l.rows.forEach { r -> assertTrue(r.sumOf { it.weight.toDouble() } > 0) } }
    }
}
