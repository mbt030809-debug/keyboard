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

    @Test fun voiceKeyIsAtTheFarLeftNextToTheEmojiKey() {
        Layouts.all.forEach { l ->
            val bottom = l.rows.last()
            val types = bottom.map { it.type }
            assertEquals(
                listOf(KeyType.VOICE, KeyType.EMOJI, KeyType.CHAR, KeyType.SPACE, KeyType.CHAR, KeyType.ENTER), types,
            )
            assertEquals(listOf("🎤", ",", "", "."), bottom.filter { it.type != KeyType.EMOJI && it.type != KeyType.ENTER }.map { it.label })
            assertEquals(10.0, bottom.sumOf { it.weight.toDouble() }, 0.001)
            // 녹음 키와 마침표(와 쉼표)는 폭이 똑같다
            val voice = bottom.first { it.type == KeyType.VOICE }
            val period = bottom.first { it.label == "." }
            val comma = bottom.first { it.label == "," }
            assertEquals(period.weight, voice.weight, 0.0001f)
            assertEquals(period.weight, comma.weight, 0.0001f)
        }
    }

    // ---- 기호 재배치

    private fun allHints(l: KeyboardLayout) = l.rows.flatten().flatMap { it.hints }

    private fun keyWithHint(l: KeyboardLayout, hint: String) = l.rows.flatten().first { hint in it.hints }

    @Test fun circlesAreRemovedButStarsAndReferenceMarkAreBack() {
        Layouts.all.forEach { l ->
            val hints = allHints(l)
            assertTrue(listOf("○", "●").none { it in hints })
            assertTrue(listOf("★", "☆", "※").all { it in hints })      // 많이 쓰는 기호라 다시 넣었다
        }
        // 빈 모양 하트 ♡ 는 하트 목록으로 옮겨 왔다
        assertTrue("♡" in keyWithHint(Layouts.koreanSingle, "❤\uFE0F").hints)
    }

    @Test fun ellipsisIsNoLongerUnderTheTildeKey() {
        Layouts.all.forEach { l ->
            assertEquals("~", keyWithHint(l, "~").hints[0])
            assertTrue("…" !in keyWithHint(l, "~").hints)
            // … 는 마침표 키에만 있다
            assertEquals(1, l.rows.flatten().count { "…" in it.hints })
            assertTrue("…" in keyWithHint(l, ".com").hints)
        }
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

    @Test fun digitKeysHaveNoLongPress() {
        Layouts.all.forEach { l ->
            l.rows[0].filter { it.type == KeyType.CHAR }.forEach { assertTrue("${l.id}:${it.label}", it.hints.isEmpty()) }
        }
    }

    @Test fun heartKeyHasSixHeartsInOneRow() {
        Layouts.all.forEach { l ->
            assertEquals(listOf("❤\uFE0F", "♡", "💕", "💖", "💙", "💔"), keyWithHint(l, "❤\uFE0F").hints)
        }
    }

    @Test fun atSignQuestionAndExclamationEachHaveTheirOwnKey() {
        Layouts.all.forEach { l ->
            val keys = listOf("@", "?", "!").map { keyWithHint(l, it) }
            assertEquals(3, keys.toSet().size)
            assertEquals(listOf("@", "@naver.com", "@gmail.com"), keyWithHint(l, "@").hints)
            assertEquals(listOf("?"), keyWithHint(l, "?").hints)
            assertEquals(listOf("!"), keyWithHint(l, "!").hints)
            listOf("@", "?", "!").forEach { sym -> assertEquals(sym, 1, allHints(l).count { it == sym }) }
        }
    }

    @Test fun singleVowelTailKeysAfterThePull() {
        // ☆ 키를 없애고 한 칸씩 당긴 결과 (단모음 기준)
        val m = Layouts.koreanSingle.rows.flatten().filter { it.type == KeyType.CHAR }.associate { it.label to it.hints }
        assertEquals(listOf("→", "←", "↑", "↓"), m["ㅔ"])      // ? 와 → 가 자리를 바꿈
        assertEquals(listOf("@", "@naver.com", "@gmail.com"), m["ㅊ"])   // 골뱅이 + 이메일 뒷부분
        assertEquals(listOf("_", "-", "—"), m["ㅍ"])           // 한 칸 당겨짐
        assertEquals(listOf("?"), m["ㅜ"])
        assertEquals(listOf("!"), m["ㅡ"])                     // 비워진 마지막 키에 느낌표
        assertEquals(listOf("~", "♪", "♩", "♫", "♬", "🎵", "🎶"), m["ㅐ"])   // 음표는 ~ 의 하위 후보
    }

    @Test fun questionAndExclamationSitOnNeighbouringKeys() {
        // 물음표와 느낌표는 붙어 있는 두 키라 연달아 누르기 쉽다
        val row = Layouts.koreanSingle.rows[3].filter { it.type == KeyType.CHAR }
        val q = row.indexOfFirst { "?" in it.hints }
        val e = row.indexOfFirst { "!" in it.hints }
        assertEquals(1, e - q)
    }

    @Test fun noSymbolIsOnTwoKeysExceptIntentionalDuplicates() {
        // 글자 키 묶음 안에서는 같은 기호가 중복되지 않는다
        val flat = Layouts.SYMBOL_GROUPS.flatten()
        assertEquals(flat.size, flat.toSet().size)
        assertTrue(flat.none { it in listOf("○", "●", "·", "…") })
        // 잘 안 쓰는 기호(¿ ¡ © ® ™)는 뺐고, 많이 쓰는 ★ ☆ ※ ♪ 는 넣었다
        assertTrue(flat.none { it in listOf("¿", "¡", "©", "®", "™") })
        assertTrue(flat.containsAll(listOf("★", "☆", "※", "♪")))
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
            // 이모지(💕 등)는 UTF-16으로 2글자, ❤️ 는 하트 + 표시 문자라 길이가 2지만 쌍 기호가 아니다.
            // 그래서 글자 수는 코드포인트로 세고 이모지는 제외한다
            l.rows.flatten().flatMap { it.hints }
                .filter { it.codePointCount(0, it.length) == 2 && it != "❤\uFE0F" }
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
        // 지우기는 1.25칸(예전 1.5칸과 일반 키 1칸의 중간), 쉬프트는 1칸보다 작게
        assertEquals(1.25f, shiftRow.last().weight, 0.0001f)
        assertTrue(shiftRow.last().weight > 1f && shiftRow.last().weight < 1.5f)
        assertTrue(shiftRow.first().weight < 1f && shiftRow.first().weight > 0.5f)
        // 글자 키 6개는 거의 1칸(위 줄 키와 폭 차이 5% 이내)
        val letterW = shiftRow.filter { it.type == KeyType.CHAR }.map { it.weight }
        assertEquals(6, letterW.size)
        assertTrue(letterW.all { kotlin.math.abs(it - 1f) < 0.0001f })      // 글자 키는 위 줄 키와 같은 1칸
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

    @Test fun backspaceIsOnePointTwoFiveUnitsInEveryLayout() {
        Layouts.all.forEach { l ->
            val backspaces = l.rows.flatten().filter { it.type == KeyType.BACKSPACE }
            assertEquals(1, backspaces.size)
            assertEquals("${l.id}", 1.25f, backspaces[0].weight, 0.0001f)
            // 그 줄의 전체 폭은 다른 글자 줄과 같다
            val row = l.rows.first { r -> r.any { it.type == KeyType.BACKSPACE } }
            val expectedTotal = if (l.id == Layouts.KOS) 8.0 else 10.0
            assertEquals("${l.id}", expectedTotal, row.sumOf { it.weight.toDouble() }, 0.001)
        }
    }

    // ---- 숫자·전화번호 키패드

    @Test fun padsAreNotLetterLayouts() {
        assertEquals(listOf("kos", "ko", "en"), Layouts.all.map { it.id })     // 한/영 전환 대상은 그대로
        assertEquals(listOf("num", "phone"), Layouts.pads.map { it.id })
        assertTrue(Layouts.isPad("num") && Layouts.isPad("phone") && !Layouts.isPad("en"))
        assertEquals("num", Layouts.byId("num").id)
        assertEquals("phone", Layouts.byId("phone").id)
        Layouts.pads.forEach { assertTrue(!it.hangul && !it.splittable) }
    }

    @Test fun padsHaveAllDigitsOnceAndFourEqualColumns() {
        Layouts.pads.forEach { p ->
            val digits = p.rows.flatten().filter { it.type == KeyType.CHAR && it.label.length == 1 && it.label[0].isDigit() }
            assertEquals("0123456789".toList().map { it.toString() }, digits.map { it.label }.sorted())
            assertEquals(4, p.rows.size)
            p.rows.forEach { r -> assertEquals(4, r.size); assertTrue(r.all { it.weight == 1f }) }
            assertEquals(p.rows.size, p.rowHeights.size)
            val types = p.rows.flatten().map { it.type }
            assertTrue(KeyType.BACKSPACE in types && KeyType.ENTER in types && KeyType.LETTERS in types)
        }
    }

    @Test fun numberPadHasDecimalMinusAndPlusOnZero() {
        val keys = Layouts.numberPad.rows.flatten().filter { it.type == KeyType.CHAR }.map { it.label }
        assertTrue(listOf(".", ",", "-").all { it in keys })
        assertEquals(listOf("+"), Layouts.numberPad.rows.flatten().first { it.label == "0" }.hints)
    }

    @Test fun phonePadHasStarHashAndPlusOnZero() {
        val keys = Layouts.phonePad.rows.flatten().filter { it.type == KeyType.CHAR }.map { it.label }
        assertTrue(listOf("*", "#", "-").all { it in keys })
        assertEquals(listOf("+"), Layouts.phonePad.rows.flatten().first { it.label == "0" }.hints)
    }

    // ---- 자판 간 기호 배치 일관성

    private fun letterRowChars(l: KeyboardLayout, row: Int) = l.rows[row].filter { it.type == KeyType.CHAR }

    /** 두벌식·영어의 가운데 보조 키 위치 (줄 번호 → 글자 키 순번들) */
    private val centerIdx = mapOf(1 to listOf(6, 7), 2 to listOf(4), 3 to listOf(3))

    @Test fun wideLayoutsAreTheSingleLayoutSplitInTheMiddleSoBothEndsMatch() {
        for (r in Layouts.LETTER_ROW_START until Layouts.LETTER_ROW_START + 3) {
            val single = letterRowChars(Layouts.koreanSingle, r)
            listOf(Layouts.korean, Layouts.english).forEach { wide ->
                val w = letterRowChars(wide, r)
                val main = w.filterIndexed { i, _ -> i !in centerIdx.getValue(r) }
                assertEquals(single.size, main.size)
                main.forEachIndexed { i, key ->
                    // 같은 줄, 왼쪽(오른쪽) 끝에서 같은 순서의 키는 같은 기호가 기본값
                    assertEquals("${wide.id} 줄$r ${i}번째", single[i].hints[0], key.hints[0])
                    // 단모음의 하위 후보는 두벌식·영어보다 많거나 같다 (★ ☆ ※ ♪ 는 가운데 키가 맡음)
                    assertTrue(single[i].hints.containsAll(key.hints))
                }
            }
        }
    }

    @Test fun keysNextToBackspaceHoldTheMostUsedSymbolsOnEveryLayout() {
        // 지우기 옆 오른쪽 끝: 윗줄 ~ →, 가운데줄 : / ❤, 아랫줄 ? !
        val tails = mapOf(1 to listOf("~", "→"), 2 to listOf(":", "/", "❤\uFE0F"), 3 to listOf("?", "!"))
        for ((r, expected) in tails) {
            Layouts.all.forEach { l ->
                assertEquals("${l.id} 줄$r", expected, letterRowChars(l, r).takeLast(expected.size).map { it.hints[0] })
            }
        }
    }

    @Test fun similarSymbolsShareAKeyAsSubCandidates() {
        val hints = Layouts.english.rows.flatten().filter { it.hints.isNotEmpty() }.associate { it.label to it.hints }
        assertTrue(hints.values.any { h -> h[0] == "+" && "±" in h })
        assertTrue(hints.values.any { h -> h[0] == "$" && h.containsAll(listOf("₩", "€", "£", "¥")) })
    }

    @Test fun singleLayoutKeepsStarsNotesAsSubCandidates() {
        val hints = Layouts.koreanSingle.rows.flatten().flatMap { it.hints }
        assertTrue(hints.containsAll(listOf("※", "★", "☆", "♪")))
    }

    @Test fun koreanDubeolsikAndEnglishHaveIdenticalSymbolsKeyByKey() {
        for (r in Layouts.LETTER_ROW_START until Layouts.LETTER_ROW_START + 3) {
            assertEquals(
                letterRowChars(Layouts.korean, r).map { it.hints },
                letterRowChars(Layouts.english, r).map { it.hints },
            )
        }
    }

    @Test fun extraKeysOnWideLayoutsHoldTheAuxiliarySymbolsInTheMiddleOfTheRow() {
        val extras = Layouts.SYMBOL_GROUPS.subList(22, 26)
        val expected = mapOf(1 to extras.subList(0, 2), 2 to extras.subList(2, 3), 3 to extras.subList(3, 4))
        for ((row, ex) in expected) {
            listOf(Layouts.korean, Layouts.english).forEach { wide ->
                val keys = letterRowChars(wide, row)
                assertEquals("${wide.id} 줄$row", ex, centerIdx.getValue(row).map { keys[it].hints })
            }
        }
    }

    @Test fun everyMainSymbolSitsOnTheSameRowAcrossLayouts() {
        // 예: ? 는 어느 자판에서든 맨 아래 글자 줄, ( ) 는 맨 윗 글자 줄
        listOf("()", "?", "!", "@", "/", "_", "❤\uFE0F", "→").forEach { sym ->
            val rows = Layouts.all.map { l ->
                (Layouts.LETTER_ROW_START until Layouts.LETTER_ROW_START + 3).first { r ->
                    letterRowChars(l, r).any { sym in it.hints }
                }
            }
            assertEquals("$sym 의 줄", 1, rows.toSet().size)
        }
    }
}

class LayoutsRequestedTweaksTest {
    private fun keys(l: KeyboardLayout) = l.rows.flatten().filter { it.type == KeyType.CHAR }

    @Test fun quotesAndCaretSitRightNextToAngleBracketsOnEveryLayout() {
        Layouts.all.forEach { l ->
            val row = keys(l).filter { k -> k.hints.isNotEmpty() && k.hints[0] in listOf("<>", "\"\"", "^") }
            val all = Layouts.english.rows[1].filter { it.type == KeyType.CHAR }
            val idx = { h: String -> keys(l).indexOfFirst { it.hints.firstOrNull() == h } }
            assertEquals("${l.id}: <> 바로 오른쪽이 \"\"", idx("<>") + 1, idx("\"\""))
            assertEquals("${l.id}: \"\" 바로 오른쪽이 ^", idx("\"\"") + 1, idx("^"))
            assertTrue(row.size == 3 && all.isNotEmpty())
        }
    }

    @Test fun emailSuffixesLiveUnderAt() {
        Layouts.all.forEach { l ->
            val at = keys(l).first { it.hints.firstOrNull() == "@" }
            assertEquals(listOf("@", "@naver.com", "@gmail.com"), at.hints)
        }
    }

    @Test fun starsAreOneKeyWithHollowStarAsSubCandidate() {
        assertTrue(Layouts.SYMBOL_GROUPS.contains(listOf("★", "☆")))
        assertTrue(Layouts.SYMBOL_GROUPS.none { it == listOf("★") || it == listOf("☆") })
    }

    @Test fun noteKeyHasSeveralNotes() {
        val notes = Layouts.SYMBOL_GROUPS.first { it[0] == "♪" }
        assertTrue(notes.size >= 5 && notes.containsAll(listOf("♩", "♫", "♬")))
    }
}
