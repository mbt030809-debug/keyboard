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
}
