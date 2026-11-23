package com.mbt.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {
    private class MemoryStore : ClipboardHistory.Store {
        var raw = ""
        var saves = 0
        override fun load() = raw
        override fun save(raw: String) { this.raw = raw; saves++ }
    }

    private val store = MemoryStore()
    private fun history(max: Int = 30, maxChars: Int = 2000) = ClipboardHistory(store, max, maxChars)

    @Test fun newestFirstAndDuplicatesMoveToTheFront() {
        val h = history()
        h.add("a"); h.add("b"); h.add("c")
        assertEquals(listOf("c", "b", "a"), h.items())
        h.add("a")                                        // 같은 내용은 맨 앞으로
        assertEquals(listOf("a", "c", "b"), h.items())
    }

    @Test fun blankAndNullAreNotStored() {
        val h = history()
        assertFalse(h.add(null)); assertFalse(h.add("")); assertFalse(h.add("   \n "))
        assertTrue(h.items().isEmpty())
        assertEquals(0, store.saves)
    }

    @Test fun tooLongTextIsNotStored() {
        val h = history(maxChars = 10)
        assertFalse(h.add("12345678901"))
        assertTrue(h.add("1234567890"))
        assertEquals(listOf("1234567890"), h.items())
    }

    @Test fun keepsOnlyTheNewestMax() {
        val h = history(max = 3)
        (1..5).forEach { h.add("item$it") }
        assertEquals(listOf("item5", "item4", "item3"), h.items())
    }

    @Test fun removeAndClear() {
        val h = history()
        h.add("a"); h.add("b")
        h.remove("a")
        assertEquals(listOf("b"), h.items())
        h.remove("없는 항목")                              // 없어도 문제 없음
        h.clear()
        assertTrue(h.items().isEmpty())
        assertEquals("", store.raw)
    }

    @Test fun persistsAcrossInstancesWithAnyCharacters() {
        val tricky = listOf(
            "한글 문장입니다", "줄\n바꿈\n포함", "특수문자 %&=+ ?# / \\ \"따옴표\"", "이모지 😀❤️", "탭\t과 공백  둘", "a+b=c",
        )
        val h = history()
        tricky.reversed().forEach { h.add(it) }
        val again = history()                             // 새로 만들어도 저장소에서 그대로 복원
        assertEquals(tricky, again.items())
    }

    @Test fun encodeDecodeRoundTrip() {
        val items = listOf("x y", "줄\n바꿈", "100%")
        assertEquals(items, ClipboardHistory.decode(ClipboardHistory.encode(items)))
        assertEquals(emptyList<String>(), ClipboardHistory.decode(""))
    }

    @Test fun defaultLimits() {
        assertEquals(30, ClipboardHistory.MAX_ITEMS)
        assertEquals(2000, ClipboardHistory.MAX_CHARS)
        val h = history()
        (1..40).forEach { h.add("n$it") }
        assertEquals(30, h.items().size)
        assertEquals("n40", h.items().first())
    }
}
