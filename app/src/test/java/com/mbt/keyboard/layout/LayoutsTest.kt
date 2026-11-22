package com.mbt.keyboard.layout

import com.mbt.keyboard.hangul.Jamo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    @Test fun koreanLettersAreJamo() {
        Layouts.korean.rows.subList(Layouts.LETTER_ROW_START, Layouts.LETTER_ROW_START + 3).flatten()
            .filter { it.type == KeyType.CHAR }
            .forEach {
                assertTrue("${it.label}", Jamo.isJamo(it.output[0]))
                it.shifted?.let { s -> assertTrue(s, Jamo.isJamo(s[0])) }
            }
    }

    @Test fun koreanHas21VowelAndConsonantKeysCovered() {
        val outs = Layouts.korean.rows.subList(Layouts.LETTER_ROW_START, Layouts.LETTER_ROW_START + 3)
            .flatten().filter { it.type == KeyType.CHAR }
            .flatMap { listOfNotNull(it.output, it.shifted) }.toSet()
        // 두벌식은 쉬프트 포함 모든 기본 자음(19)·단모음 중 겹모음 제외 전부 입력 가능
        "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅑㅒㅓㅔㅕㅖㅗㅛㅜㅠㅡㅣ".forEach {
            assertTrue("missing $it", it.toString() in outs)
        }
    }

    @Test fun englishShiftIsUppercase() {
        val q = Layouts.english.rows[Layouts.LETTER_ROW_START][0]
        assertEquals("q", q.outputFor(false))
        assertEquals("Q", q.outputFor(true))
    }

    @Test fun everyRowHasWeight() {
        Layouts.all.forEach { l -> l.rows.forEach { r -> assertTrue(r.sumOf { it.weight.toDouble() } > 0) } }
    }

    @Test fun everyLetterKeyHasLongPressHint() {
        Layouts.all.forEach { l ->
            l.rows.subList(Layouts.LETTER_ROW_START, Layouts.LETTER_ROW_START + 3).flatten()
                .filter { it.type == KeyType.CHAR }.forEach {
                    assertTrue("${l.id}:${it.label}", it.hints.isNotEmpty())
                }
        }
    }

    @Test fun numberRowIsDigits() {
        Layouts.all.forEach { l ->
            assertEquals((1..9).map { it.toString() } + "0", l.rows[1].map { it.output })
        }
    }

    @Test fun toolbarRowHasEditAndSymbolKeys() {
        Layouts.all.forEach { l ->
            val types = l.rows[0].map { it.type }
            assertTrue(KeyType.LEFT in types && KeyType.RIGHT in types)
            assertTrue(KeyType.SELECT_ALL in types && KeyType.COPY in types && KeyType.PASTE in types)
            assertEquals(listOf("-", "❤\uFE0F", "@", "?", "!"), l.rows[0].filter { it.type == KeyType.CHAR }.map { it.output })
        }
    }

    @Test fun rowHeightsMatchRows() {
        Layouts.all.forEach { assertEquals(it.rows.size, it.rowHeights.size) }
    }

    @Test fun singleVowelLayoutHasOnlyBasicJamo() {
        val keys = Layouts.koreanSingle.rows.subList(Layouts.LETTER_ROW_START, Layouts.LETTER_ROW_START + 3)
            .flatten().filter { it.type == KeyType.CHAR }.map { it.output }
        assertEquals("ㅂㅈㄷㄱㅅㅗㅐㅔㅁㄴㅇㄹㅎㅓㅏㅣㅋㅌㅊㅍㅜㅡ".map { it.toString() }, keys)
        assertTrue(Layouts.koreanSingle.doubleTap && Layouts.koreanSingle.hangul)
        assertTrue(!Layouts.korean.doubleTap && Layouts.korean.hangul)
        assertTrue(!Layouts.english.hangul)
    }

    @Test fun singleVowelRowsFillWidth() {
        Layouts.koreanSingle.rows.subList(Layouts.LETTER_ROW_START, Layouts.LETTER_ROW_START + 3).forEach { r ->
            assertEquals(8.0, r.sumOf { it.weight.toDouble() }, 0.001)
        }
    }

    @Test fun tenseConsonantHintsComeFirstOnSingleLayout() {
        val m = Layouts.koreanSingle.rows.flatten().filter { it.type == KeyType.CHAR }.associate { it.output to it.hints }
        assertEquals("ㄲ", m["ㄱ"]!![0]); assertEquals("ㅃ", m["ㅂ"]!![0]); assertEquals("ㅆ", m["ㅅ"]!![0])
    }

    @Test fun bottomRowHasVoiceKeyAndNoLangKey() {
        Layouts.all.forEach { l ->
            val bottom = l.rows.last()
            assertEquals(KeyType.VOICE, bottom.first().type)
            assertEquals(1, l.rows.flatten().count { it.type == KeyType.VOICE })
            assertTrue(KeyType.SPACE in bottom.map { it.type })
        }
    }
}
