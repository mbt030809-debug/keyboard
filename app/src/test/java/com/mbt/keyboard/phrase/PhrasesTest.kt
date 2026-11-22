package com.mbt.keyboard.phrase

import org.junit.Assert.assertEquals
import org.junit.Test

class PhrasesTest {
    @Test fun parseTrimsAndDropsBlank() {
        assertEquals(listOf("가", "나"), Phrases.parse("  가 \n\n나\n   "))
    }

    @Test fun parseLimitsToMax() {
        assertEquals(Phrases.MAX, Phrases.parse("1\n2\n3\n4\n5\n6").size)
    }

    @Test fun emptyRawMeansNoPhrases() {
        assertEquals(emptyList<String>(), Phrases.parse(""))
    }
}
