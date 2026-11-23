package com.mbt.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoakiGestureTest {
    @Test fun eightWayTokens() {
        assertEquals('R', MoakiGesture.token(0f)); assertEquals('L', MoakiGesture.token(180f)); assertEquals('L', MoakiGesture.token(-170f))
        assertEquals('U', MoakiGesture.token(-90f)); assertEquals('D', MoakiGesture.token(90f))
        assertEquals('I', MoakiGesture.token(-135f)); assertEquals('J', MoakiGesture.token(-45f))     // 11시 ↖ / 1시 ↗
        assertEquals('P', MoakiGesture.token(45f)); assertEquals('Q', MoakiGesture.token(135f))       // 5시 ↘ / 7시 ↙
    }

    @Test fun basicAndCompoundTable() {
        mapOf(
            "R" to 'ㅏ', "L" to 'ㅓ', "U" to 'ㅗ', "D" to 'ㅜ', "I" to 'ㅣ', "J" to 'ㅣ', "P" to 'ㅡ', "Q" to 'ㅡ',
            "RLR" to 'ㅑ', "LRL" to 'ㅕ', "UDU" to 'ㅛ', "DUD" to 'ㅠ',
            "RL" to 'ㅐ', "LR" to 'ㅔ', "RLRL" to 'ㅒ', "LRLR" to 'ㅖ',
            "UR" to 'ㅘ', "URL" to 'ㅙ', "DL" to 'ㅝ', "DLR" to 'ㅞ', "UD" to 'ㅚ', "DU" to 'ㅟ',
            "PI" to 'ㅢ', "QJ" to 'ㅢ', "PU" to 'ㅢ',
        ).forEach { (k, v) -> assertEquals(k, v, MoakiGesture.decode(k)) }
    }

    @Test fun diagonalAfterABasicDirectionBecomesEuOrIAgain() {
        assertEquals('ㅣ', MoakiGesture.decode("UI")); assertEquals('ㅣ', MoakiGesture.decode("RJ"))
        assertEquals('ㅣ', MoakiGesture.decode("LI")); assertEquals('ㅣ', MoakiGesture.decode("DJ"))
        assertEquals('ㅡ', MoakiGesture.decode("UP")); assertEquals('ㅡ', MoakiGesture.decode("RQ"))
        assertEquals('ㅡ', MoakiGesture.decode("LP")); assertEquals('ㅡ', MoakiGesture.decode("DQ"))
        assertEquals('ㅣ', MoakiGesture.decode("URI"))        // 겹모음 도중에 대각선이 나와도 그 대각선 모음
        assertEquals('ㅚ', MoakiGesture.decode("UD"))          // 되돌아오기는 그대로
        assertEquals('ㅘ', MoakiGesture.decode("UR")); assertEquals('ㅝ', MoakiGesture.decode("DL"))
    }

    @Test fun unknownSequencesFallBackToTheLongestKnownPrefix() {
        assertEquals('ㅏ', MoakiGesture.decode("RU"))
        assertEquals('ㅐ', MoakiGesture.decode("RLU"))
        assertNull(MoakiGesture.decode(""))
        assertEquals('ㅣ', MoakiGesture.decode("IR"))      // 표에 없는 뒤 획은 버리고 앞부분만
    }
}
