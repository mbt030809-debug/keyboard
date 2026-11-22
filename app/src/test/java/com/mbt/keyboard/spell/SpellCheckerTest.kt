package com.mbt.keyboard.spell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellCheckerTest {
    private fun fix(s: String) = SpellChecker.check(s).text

    @Test fun commonKoreanTypos() {
        assertEquals("며칠 걸려?", fix("몇일 걸려?"))
        assertEquals("며칠", fix("몇 일"))
        assertEquals("어떡해", fix("어떻해"))
        assertEquals("안돼요", fix("안되요"))
        assertEquals("안돼서 미안", fix("안되서 미안"))
        assertEquals("끝났어 이제 괜찮아졌다 그랬는데 됐어", fix("끝났어 이제 괜찮아졌다 그랬는데 됬어"))
        assertEquals("웬일이야", fix("왠일이야"))
        assertEquals("왠지 이상해", fix("웬지 이상해"))
        assertEquals("있습니다", fix("있읍니다"))
        assertEquals("오랜만이야", fix("오랫만이야"))
        assertEquals("역할", fix("역활"))
        assertEquals("깨끗이 열심히", fix("깨끗히 열심이"))
    }

    @Test fun rieulFinalRules() {
        assertEquals("갈게", fix("갈께"))
        assertEquals("내일 갈게요", fix("내일 갈께요"))
        assertEquals("말씀드릴게요", fix("말씀드릴께요"))
        assertEquals("할 거야", fix("할꺼야"))
        assertEquals("갈걸", fix("갈껄"))
        assertEquals("할 수 있어", fix("할수있어"))
        assertEquals("할 수 없어", fix("할수 없어"))
    }

    @Test fun doesNotTouchCorrectText() {
        listOf(
            "안녕하세요 오늘 날씨가 좋네요",
            "선생님께 말씀드렸어요",
            "선생님께서 오셨다",
            "돼요 됩니다 되었습니다 안돼",
            "며칠 뒤에 만나요",
            "살려고 노력했다",
            "가수가 있어",          // 수 앞이 ㄹ 받침이 아님
            "I can't go",
        ).forEach { assertEquals(it, fix(it)) }
        assertTrue(SpellChecker.check("괜찮아요").changes.isEmpty())
    }

    @Test fun englishAndSpacing() {
        assertEquals("the cat don't know", fix("teh cat dont know"))
        assertEquals("a lot", fix("alot"))
        assertEquals("안녕 하세요", fix("안녕  하세요"))
    }

    @Test fun reportsEachChange() {
        val r = SpellChecker.check("몇일 뒤에 갈께")
        assertEquals("며칠 뒤에 갈게", r.text)
        assertEquals(listOf(SpellChecker.Change("몇일", "며칠"), SpellChecker.Change("갈께", "갈게")), r.changes)
    }
}
