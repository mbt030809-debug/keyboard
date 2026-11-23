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

    @Test fun topRowIsEditKeysThenDigitsAllSameWidth() {
        Layouts.all.forEach { l ->
            val top = l.rows[0]
            assertEquals(13, top.size)
            assertEquals(
                listOf(KeyType.SELECT_ALL, KeyType.COPY, KeyType.PASTE),
                top.take(3).map { it.type },
            )
            assertEquals(listOf("전체", "복사", "붙임"), top.take(3).map { it.label })
            assertEquals((1..9).map { it.toString() } + "0", top.drop(3).map { it.output })
            assertTrue("13칸 모두 같은 폭", top.all { it.weight == 1f })
        }
    }

    @Test fun oldToolbarRowIsGone() {
        Layouts.all.forEach { l ->
            assertEquals("맨 윗줄 + 글자 3줄 + 하단 = 5줄", 5, l.rows.size)
            val all = l.rows.flatten()
            // 예전 상단 줄 키들은 일반 키로 남아 있지 않다
            assertTrue(all.none { it.type == KeyType.CHAR && it.output in listOf("-", "@", "?", "!", "❤\uFE0F") })
            assertEquals(1, all.count { it.type == KeyType.VOICE })
        }
    }

    @Test fun voiceKeyIsRightNextToTheSpaceBar() {
        Layouts.all.forEach { l ->
            val bottom = l.rows.last()
            val types = bottom.map { it.type }
            assertEquals(
                listOf(KeyType.EMOJI, KeyType.CHAR, KeyType.SPACE, KeyType.VOICE, KeyType.CHAR, KeyType.ENTER), types,
            )
            assertEquals(listOf(",", "", "🎤", "."), bottom.filter { it.type != KeyType.EMOJI && it.type != KeyType.ENTER }.map { it.label })
            assertEquals(10.0, bottom.sumOf { it.weight.toDouble() }, 0.001)
        }
    }

    // ---- 기호 재배치

    private fun allHints(l: KeyboardLayout) = l.rows.flatten().flatMap { it.hints }

    private fun keyWithHint(l: KeyboardLayout, hint: String) = l.rows.flatten().first { hint in it.hints }

    @Test fun starCircleAndReferenceMarkAreRemoved() {
        Layouts.all.forEach { l ->
            val hints = allHints(l)
            assertTrue("★" !in hints && "○" !in hints && "※" !in hints)
        }
        // 비슷한 기호 중 요청하지 않은 것(☆ ♡ ●)은 그대로
        assertTrue(allHints(Layouts.koreanSingle).containsAll(listOf("☆", "♡", "●")))
    }

    @Test fun middleDotIsUnderThePeriodKey() {
        Layouts.all.forEach { l ->
            assertEquals(listOf("…", ".com", "·"), keyWithHint(l, ".com").hints)
            assertEquals(1, l.rows.flatten().count { "·" in it.hints })       // 다른 키에는 없다
        }
    }

    @Test fun pipeAndBackslashAreUnderTheSlashKey() {
        Layouts.all.forEach { l ->
            val slash = keyWithHint(l, "/")
            assertEquals(listOf("/", "|", "\\"), slash.hints)
            assertEquals(1, l.rows.flatten().count { "|" in it.hints })
            assertEquals(1, l.rows.flatten().count { "\\" in it.hints })
        }
    }

    @Test fun hyphenIsUnderTheUnderscoreKey() {
        Layouts.all.forEach { l ->
            val underscore = keyWithHint(l, "_")
            assertEquals(listOf("_", "-", "—"), underscore.hints)
            assertEquals(1, l.rows.flatten().count { "-" in it.hints })
        }
    }

    @Test fun heartAtQuestionExclamationEachGetTheirOwnFreeKey() {
        Layouts.all.forEach { l ->
            val keys = listOf("!", "@", "?", "❤\uFE0F").map { keyWithHint(l, it) }
            assertEquals("네 기호가 서로 다른 키에", 4, keys.toSet().size)
            // 숫자 1, 2 (PC 키보드의 Shift+1, Shift+2)
            assertEquals(listOf("!"), l.rows[0].first { it.output == "1" }.hints)
            assertEquals(listOf("@"), l.rows[0].first { it.output == "2" }.hints)
            // ? 와 ❤️ 는 비워진 글자 키
            assertEquals(listOf("?"), keyWithHint(l, "?").hints)
            assertEquals(listOf("❤\uFE0F"), keyWithHint(l, "❤\uFE0F").hints)
            // 한 번만 존재
            listOf("!", "@", "?", "❤\uFE0F").forEach { sym -> assertEquals(sym, 1, allHints(l).count { it == sym }) }
        }
    }

    @Test fun noSymbolIsOnTwoKeysExceptIntentionalDuplicates() {
        // 글자 키 묶음 안에서는 같은 기호가 중복되지 않는다
        val flat = Layouts.SYMBOL_GROUPS.flatten()
        assertEquals(flat.size, flat.toSet().size)
        assertTrue(flat.none { it in listOf("★", "○", "※", "·") })
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
            // ❤️ 는 하트 + 이모지 표시 문자라 길이는 2지만 쌍 기호가 아니다
            l.rows.flatten().flatMap { it.hints }.filter { it.length == 2 && it != "❤\uFE0F" }
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

    @Test fun singleLayoutShiftIsOnTheRowAboveBottomRowAtTheLeftEnd() {
        val rows = Layouts.koreanSingle.rows
        val bottom = rows.last()
        assertTrue("하단 줄에는 쉬프트가 없다", bottom.none { it.type == KeyType.SHIFT })
        val shiftRow = rows[rows.size - 2]                              // 하단 줄 바로 위
        assertEquals(KeyType.SHIFT, shiftRow.first().type)              // 맨 왼쪽
        assertEquals(1, rows.flatten().count { it.type == KeyType.SHIFT })
        assertEquals(listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"), shiftRow.filter { it.type == KeyType.CHAR }.map { it.label })
        assertEquals(KeyType.BACKSPACE, shiftRow.last().type)
        assertEquals(8.0, shiftRow.sumOf { it.weight.toDouble() }, 0.001)  // 줄 전체 8칸: 위 줄들과 오른쪽 끝이 맞는다
        // 지우기는 예전처럼 1.5칸, 쉬프트는 1칸보다 작게
        assertEquals(1.5f, shiftRow.last().weight, 0.0001f)
        assertTrue(shiftRow.first().weight < 1f && shiftRow.first().weight > 0.5f)
        // 글자 키 6개는 거의 1칸(위 줄 키와 폭 차이 5% 이내)
        val letterW = shiftRow.filter { it.type == KeyType.CHAR }.map { it.weight }
        assertEquals(6, letterW.size)
        assertTrue(letterW.all { it in 0.95f..1f })
        // 모든 자판의 하단 줄 폭은 10
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
            assertEquals(listOf("…", ".com", "·"), period.hints)
        }
    }

    @Test fun percentKeyAlsoGivesDegreeSign() {
        Layouts.all.forEach { l ->
            val key = l.rows.flatten().first { it.hints.firstOrNull() == "%" }
            assertEquals(listOf("%", "°"), key.hints)
        }
    }
}
