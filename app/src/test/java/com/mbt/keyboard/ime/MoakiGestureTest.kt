package com.mbt.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoakiGestureTest {
    @Test fun sixWayTokens() {
        assertEquals('R', MoakiGesture.token(0f)); assertEquals('L', MoakiGesture.token(180f)); assertEquals('L', MoakiGesture.token(-170f))
        assertEquals('U', MoakiGesture.token(-90f)); assertEquals('D', MoakiGesture.token(90f))
        listOf(-135f, -45f).forEach { assertEquals("$it", 'I', MoakiGesture.token(it)) }          // 11시·1시 = ㅣ
        listOf(45f, 135f).forEach { assertEquals("$it", 'E', MoakiGesture.token(it)) }            // 5시·7시 = ㅡ
    }

    @Test fun basicAndCompoundTable() {
        mapOf(
            "R" to 'ㅏ', "L" to 'ㅓ', "U" to 'ㅗ', "D" to 'ㅜ', "I" to 'ㅣ', "E" to 'ㅡ',
            "RLR" to 'ㅑ', "LRL" to 'ㅕ', "UDU" to 'ㅛ', "DUD" to 'ㅠ',
            "RL" to 'ㅐ', "LR" to 'ㅔ', "RLRL" to 'ㅒ', "LRLR" to 'ㅖ',
            "UR" to 'ㅘ', "URL" to 'ㅙ', "DL" to 'ㅝ', "DLR" to 'ㅞ', "UD" to 'ㅚ', "DU" to 'ㅟ', "EI" to 'ㅢ', "EU" to 'ㅢ',
        ).forEach { (k, v) -> assertEquals(k, v, MoakiGesture.decode(k)) }
    }

    @Test fun unknownSequencesFallBackToTheLongestKnownPrefix() {
        assertEquals('ㅏ', MoakiGesture.decode("RU"))
        assertEquals('ㅐ', MoakiGesture.decode("RLU"))
        assertNull(MoakiGesture.decode(""))
        assertEquals('ㅣ', MoakiGesture.decode("IR"))      // 표에 없는 뒤 획은 버리고 앞부분만
    }

    @Test fun longSingleStrokeMakesTheYVowel() {
        assertEquals('ㅑ', MoakiGesture.decode("R", longSingle = true))
        assertEquals('ㅠ', MoakiGesture.decode("D", longSingle = true))
        assertEquals('ㅣ', MoakiGesture.decode("I", longSingle = true))          // 대각선은 길게 밀어도 그대로
        assertEquals('ㅐ', MoakiGesture.decode("RL", longSingle = true))         // 여러 획이면 길게 밀기는 무시
    }
}
