package com.mbt.keyboard.hangul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HangulComposerTest {

    /** 자모 문자열을 입력하고 (확정 + 조합중) 전체 텍스트를 반환 */
    private fun type(jamo: String): String {
        val c = HangulComposer()
        val sb = StringBuilder()
        jamo.forEach { sb.append(c.input(it).committed) }
        return sb.toString() + c.composing
    }

    @Test fun basicSyllables() {
        assertEquals("가", type("ㄱㅏ"))
        assertEquals("한", type("ㅎㅏㄴ"))
        assertEquals("한글", type("ㅎㅏㄴㄱㅡㄹ"))
        assertEquals("안녕하세요", type("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
    }

    @Test fun finalConsonantMovesToNextSyllable() {
        assertEquals("가나", type("ㄱㅏㄴㅏ"))
        assertEquals("하늘이", type("ㅎㅏㄴㅡㄹㅇㅣ"))
    }

    @Test fun compoundFinal() {
        assertEquals("값", type("ㄱㅏㅂㅅ"))
        assertEquals("닭", type("ㄷㅏㄹㄱ"))
        assertEquals("읽", type("ㅇㅣㄹㄱ"))
    }

    @Test fun compoundFinalSplitsOnVowel() {
        assertEquals("갑사", type("ㄱㅏㅂㅅㅏ"))
        assertEquals("닭이", type("ㄷㅏㄹㄱㅇㅣ"))
        assertEquals("달기", type("ㄷㅏㄹㄱㅣ"))
    }

    @Test fun compoundVowel() {
        assertEquals("와", type("ㅇㅗㅏ"))
        assertEquals("괜", type("ㄱㅗㅐㄴ"))
        assertEquals("의", type("ㅇㅡㅣ"))
        assertEquals("원", type("ㅇㅜㅓㄴ"))
        assertEquals("괜찮아", type("ㄱㅗㅐㄴㅊㅏㄴㅎㅇㅏ"))
    }

    @Test fun doubleConsonantsAndNonJongable() {
        assertEquals("갂", type("ㄱㅏㄲ"))
        assertEquals("깎", type("ㄲㅏㄲ"))
        assertEquals("까", type("ㄲㅏ"))
        // ㄸ은 받침 불가 → 새 글자
        assertEquals("가ㄸ", type("ㄱㅏㄸ"))
        assertEquals("가따", type("ㄱㅏㄸㅏ"))
    }

    @Test fun loneJamo() {
        assertEquals("ㄱ", type("ㄱ"))
        assertEquals("ㄱㄱ", type("ㄱㄱ"))
        assertEquals("ㅏ", type("ㅏ"))
        assertEquals("ㅏㄱ", type("ㅏㄱ"))
    }

    @Test fun backspaceGoesJamoByJamo() {
        val c = HangulComposer()
        "ㄱㅏㅂㅅ".forEach { c.input(it) }
        assertEquals("값", c.composing)
        assertEquals("갑", c.backspace()!!.composing)
        assertEquals("가", c.backspace()!!.composing)
        assertEquals("ㄱ", c.backspace()!!.composing)
        assertEquals("", c.backspace()!!.composing)
        assertNull(c.backspace())
    }

    @Test fun backspaceAfterSplit() {
        val c = HangulComposer()
        "ㄱㅏㅂㅅㅏ".forEach { c.input(it) }   // 갑 + 사(조합중)
        assertEquals("사", c.composing)
        assertEquals("ㅅ", c.backspace()!!.composing)
    }

    @Test fun flushCommitsAndResets() {
        val c = HangulComposer()
        "ㅎㅏㄴ".forEach { c.input(it) }
        assertEquals("한", c.flush())
        assertEquals(false, c.isComposing)
        assertEquals("", c.flush())
    }

    @Test fun committedTextReported() {
        val c = HangulComposer()
        c.input('ㄱ'); c.input('ㅏ'); c.input('ㄴ')
        val out = c.input('ㅏ')           // 간 → 가 확정 + 나 조합
        assertEquals("가", out.committed)
        assertEquals("나", out.composing)
        val out2 = c.input('ㄱ'); val out3 = c.input('ㄱ')
        assertEquals("", out2.committed)
        assertEquals("낙", out2.composing)
        assertEquals("낙", out3.committed)   // ㄱㄱ은 겹받침 불가 → 낙 확정
        assertEquals("ㄱ", out3.composing)
    }

    // ---- 단모음 자판: 연타 변환

    private fun tap(c: HangulComposer, vararg keys: Char) = keys.forEach { c.input(it) }

    @Test fun doubleTapConsonantMakesTense() {
        val c = HangulComposer()
        tap(c, 'ㄱ')
        assertEquals("ㄲ", c.doubleTap('ㄱ')!!.composing)
        tap(c, 'ㅏ')
        assertEquals("까", c.composing)
    }

    @Test fun doubleTapVowelMakesYVowel() {
        val c = HangulComposer()
        tap(c, 'ㅇ', 'ㅏ')
        assertEquals("야", c.doubleTap('ㅏ')!!.composing)
        val d = HangulComposer(); tap(d, 'ㅅ', 'ㅓ'); assertEquals("셔", d.doubleTap('ㅓ')!!.composing)
        val e = HangulComposer(); tap(e, 'ㅇ', 'ㅗ'); assertEquals("요", e.doubleTap('ㅗ')!!.composing)
        val f = HangulComposer(); tap(f, 'ㅇ', 'ㅜ'); assertEquals("유", f.doubleTap('ㅜ')!!.composing)
        val g = HangulComposer(); tap(g, 'ㅇ', 'ㅐ'); assertEquals("얘", g.doubleTap('ㅐ')!!.composing)
        val h = HangulComposer(); tap(h, 'ㅇ', 'ㅔ'); assertEquals("예", h.doubleTap('ㅔ')!!.composing)
    }

    @Test fun doubleTapFinalConsonant() {
        val c = HangulComposer()
        tap(c, 'ㅇ', 'ㅏ', 'ㄱ')
        assertEquals("앆", c.doubleTap('ㄱ')!!.composing)   // 받침 ㄲ
        val out = c.input('ㅏ')              // 받침 ㄲ 이 다음 글자 초성으로 이동
        assertEquals("아", out.committed)
        assertEquals("까", out.composing)
    }

    @Test fun doubleTapNotApplicableReturnsNull() {
        val c = HangulComposer()
        tap(c, 'ㄱ', 'ㅏ', 'ㄷ')
        assertNull(c.doubleTap('ㅏ'))       // 마지막 자모가 다름
        tap(c, 'ㅇ', 'ㅗ', 'ㅏ')
        assertNull(c.doubleTap('ㅏ'))       // ㅗㅑ 는 조합 불가
        assertNull(HangulComposer().doubleTap('ㄱ'))
        val d = HangulComposer(); tap(d, 'ㅇ', 'ㅣ'); assertNull(d.doubleTap('ㅣ'))  // ㅣ 는 변환 없음
    }

    // ---- 어떠 문제: 받침으로 ㄸ 이 안 될 때 다음 글자 초성으로

    @Test fun doubleTapOnFinalThatCannotBeTenseMovesToNextSyllable() {
        val c = HangulComposer()
        tap(c, 'ㅇ', 'ㅓ', 'ㄷ')                 // 얻
        assertEquals("얻", c.composing)
        val out = c.doubleTap('ㄷ')!!
        assertEquals("어", out.committed)       // 받침이 되지 않고
        assertEquals("ㄸ", out.composing)       // 다음 글자의 쌍자음 초성
        assertEquals("떠", c.input('ㅓ').composing)   // 어 + 떠 = 어떠
    }

    @Test fun sameForBieupAndJieut() {
        val b = HangulComposer(); tap(b, 'ㅇ', 'ㅏ', 'ㅂ')
        assertEquals("ㅃ", b.doubleTap('ㅂ')!!.composing)
        val j = HangulComposer(); tap(j, 'ㅇ', 'ㅏ', 'ㅈ')
        assertEquals("ㅉ", j.doubleTap('ㅈ')!!.composing)
    }

    @Test fun finalThatCanBeTenseStaysAsFinal() {
        val c = HangulComposer()
        tap(c, 'ㅇ', 'ㅏ', 'ㄱ')
        val out = c.doubleTap('ㄱ')!!
        assertEquals("", out.committed)
        assertEquals("앆", out.composing)        // 받침 ㄲ (기존 동작 유지)
    }

    @Test fun openSyllableDetection() {
        val c = HangulComposer()
        assertEquals(false, c.isOpenSyllable())
        tap(c, 'ㅇ'); assertEquals(false, c.isOpenSyllable())
        tap(c, 'ㅓ'); assertEquals(true, c.isOpenSyllable())
        tap(c, 'ㄷ'); assertEquals(false, c.isOpenSyllable())
        val v = HangulComposer(); tap(v, 'ㅏ'); assertEquals(false, v.isOpenSyllable())   // 초성 없는 모음
        val w = HangulComposer(); tap(w, 'ㄱ', 'ㅗ', 'ㅏ'); assertEquals(true, w.isOpenSyllable())  // 과
    }

    @Test fun startNewSyllableCommitsCurrentAndStartsWithConsonant() {
        val c = HangulComposer()
        tap(c, 'ㅇ', 'ㅓ')
        val out = c.startNewSyllable('ㄷ')
        assertEquals("어", out.committed)
        assertEquals("ㄷ", out.composing)
        assertEquals("더", c.input('ㅓ').composing)
    }
}
