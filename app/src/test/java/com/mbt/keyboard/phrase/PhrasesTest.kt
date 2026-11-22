package com.mbt.keyboard.phrase

import org.junit.Assert.assertEquals
import org.junit.Test

class PhrasesTest {
    @Test fun parseTrimsAndDropsBlank() {
        assertEquals(listOf("가", "나"), Phrases.parse("  가 \n\n나\n   "))
    }

    @Test fun parseLimitsToMax() {
        val many = (1..Phrases.MAX + 50).joinToString("\n") { "문구$it" }
        assertEquals(Phrases.MAX, Phrases.parse(many).size)
    }

    @Test fun emptyRawMeansNoPhrases() {
        assertEquals(emptyList<String>(), Phrases.parse(""))
    }

    @Test fun noLongerStopsAtFourPhrases() {
        // 예전에는 4개까지만 보였다. 이제는 3열 격자로 행이 계속 늘어난다
        assertEquals(10, Phrases.parse((1..10).joinToString("\n") { "문구$it" }).size)
        assertEquals(true, Phrases.DEFAULT.size > 4)
        assertEquals(true, Phrases.MAX >= 30)
    }
}
