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

    @Test fun toolbarRowHasVoiceEditSpellAndSymbolKeys() {
        Layouts.all.forEach { l ->
            val types = l.rows[0].map { it.type }
            assertEquals(KeyType.VOICE, types.first())
            assertTrue(KeyType.SELECT_ALL in types && KeyType.COPY in types && KeyType.PASTE in types)
            assertTrue(KeyType.SPELL in types)
            // 좌우 화살표 키는 없다
            assertEquals(10, l.rows[0].size)
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

    @Test fun singleLayoutLongPressGivesSymbolsNotTenseConsonants() {
        val m = Layouts.koreanSingle.rows.flatten().filter { it.type == KeyType.CHAR }
        m.forEach { k -> k.hints.forEach { assertTrue("${k.label}:$it", !com.mbt.keyboard.hangul.Jamo.isJamo(it[0])) } }
    }

    @Test fun bracketsAndQuotesComeAsPairsByDefault() {
        val byKey = Layouts.koreanSingle.rows.flatten().filter { it.type == KeyType.CHAR }.associate { it.label to it.hints }
        assertEquals("()", byKey["ㅂ"]!![0]); assertEquals("[]", byKey["ㅈ"]!![0])
        assertEquals("{}", byKey["ㄷ"]!![0]); assertEquals("<>", byKey["ㄱ"]!![0])
        assertEquals("\"\"", byKey["ㅅ"]!![0])
        // 한 짝씩도 고를 수 있어야 한다
        assertTrue(byKey["ㅂ"]!!.containsAll(listOf("(", ")")))
    }

    @Test fun everyTwoCharHintIsADeclaredPair() {
        Layouts.all.forEach { l ->
            l.rows.flatten().flatMap { it.hints }.filter { it.length == 2 }
                .forEach { assertTrue("${l.id}:$it", it in Layouts.PAIRS) }
        }
        // 선언된 쌍은 모두 어딘가에 배치돼 있어야 한다
        val all = Layouts.english.rows.flatten().flatMap { it.hints }.toSet()
        assertTrue(all.containsAll(Layouts.PAIRS))
    }

    @Test fun symbolGroupsAreUniqueAndEnough() {
        val flat = Layouts.SYMBOL_GROUPS.flatten()
        assertEquals(flat.size, flat.toSet().size)
        assertTrue(Layouts.SYMBOL_GROUPS.size >= 26)
        assertTrue(Layouts.SYMBOL_GROUPS.all { it.isNotEmpty() })
    }

    @Test fun voiceKeyIsOnTopAndEmojiKeyIsAtBottom() {
        Layouts.all.forEach { l ->
            assertEquals(1, l.rows.flatten().count { it.type == KeyType.VOICE })
            assertEquals(1, l.rows.flatten().count { it.type == KeyType.EMOJI })
            val bottom = l.rows.last()
            assertTrue(KeyType.EMOJI in bottom.take(2).map { it.type })
            assertTrue(KeyType.SPACE in bottom.map { it.type })
        }
    }

    @Test fun singleLayoutHasBottomLeftShift() {
        val bottom = Layouts.koreanSingle.rows.last()
        assertEquals(KeyType.SHIFT, bottom.first().type)
        assertEquals(10.0, bottom.sumOf { it.weight.toDouble() }, 0.001)
        // 단모음 자판은 글자 줄에 쉬프트가 없다
        val letterRows = Layouts.koreanSingle.rows.subList(Layouts.LETTER_ROW_START, Layouts.LETTER_ROW_START + 3)
        assertTrue(letterRows.flatten().none { it.type == KeyType.SHIFT })
        // 다른 자판의 하단 줄 폭도 10
        Layouts.all.forEach { l -> assertEquals(10.0, l.rows.last().sumOf { it.weight.toDouble() }, 0.001) }
    }

    @Test fun shiftGivesTenseConsonantsAndDoubleVowelsOnSingleLayout() {
        val keys = Layouts.koreanSingle.rows.subList(Layouts.LETTER_ROW_START, Layouts.LETTER_ROW_START + 3)
            .flatten().filter { it.type == KeyType.CHAR }.associateBy { it.output }
        val expected = mapOf(
            "ㅂ" to "ㅃ", "ㅈ" to "ㅉ", "ㄷ" to "ㄸ", "ㄱ" to "ㄲ", "ㅅ" to "ㅆ",
            "ㅏ" to "ㅑ", "ㅓ" to "ㅕ", "ㅗ" to "ㅛ", "ㅜ" to "ㅠ", "ㅐ" to "ㅒ", "ㅔ" to "ㅖ",
        )
        expected.forEach { (base, shifted) -> assertEquals(base, shifted, keys[base]!!.outputFor(true)) }
        // 연타 변환 표와 일치해야 한다
        expected.forEach { (base, shifted) -> assertEquals(com.mbt.keyboard.hangul.Jamo.DOUBLE[base[0]], shifted[0]) }
        // 쉬프트가 없는 키는 그대로
        assertEquals("ㅁ", keys["ㅁ"]!!.outputFor(true))
    }

    @Test fun periodKeyLongPressGivesEllipsisAndDotCom() {
        Layouts.all.forEach { l ->
            val period = l.rows.last().first { it.label == "." }
            assertEquals(listOf("…", ".com"), period.hints)
        }
    }

    @Test fun percentKeyAlsoGivesDegreeSign() {
        Layouts.all.forEach { l ->
            val key = l.rows.flatten().first { it.hints.firstOrNull() == "%" }
            assertEquals(listOf("%", "°"), key.hints)
        }
    }
}
