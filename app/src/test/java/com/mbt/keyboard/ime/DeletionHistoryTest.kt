package com.mbt.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionHistoryTest {
    @Test fun deletedTailIsRecordedAndCanBePopped() {
        val h = DeletionHistory()
        h.begin("안녕하세요")
        assertTrue(h.finish("안녕"))
        val c = h.pop()!!
        assertEquals("하세요", c.restore)
        assertEquals(0, c.removeLen)
        assertNull(h.pop())
    }

    @Test fun nothingDeletedMeansNoRecord() {
        val h = DeletionHistory()
        h.begin("abc")
        assertFalse(h.finish("abc"))
        assertEquals(0, h.size)
        assertFalse(h.finish("abc"))              // 시작하지 않은 묶음
    }

    @Test fun selectionDeleteIsRestoredToo() {
        val h = DeletionHistory()
        h.begin("앞글" + "선택한 글")         // 커서 앞 글자 + 선택된 글자
        assertTrue(h.finish("앞글"))
        assertEquals("선택한 글", h.pop()!!.restore)
    }

    @Test fun composingRemnantIsReplacedNotDuplicated() {
        // 안녕 의 '녕' 조합 중 지우기: 안녀 가 남았다 → 되돌리면 '녀'를 '녕'으로 교체
        val h = DeletionHistory()
        h.begin("안녕")
        assertTrue(h.finish("안녀"))
        val c = h.pop()!!
        assertEquals("녀", c.expectTail)
        assertEquals(1, c.removeLen)
        assertEquals("녕", c.restore)
    }

    @Test fun keepsAtMostTwentyChunksNewestLast() {
        val h = DeletionHistory()
        repeat(25) { i -> h.begin("a$i"); h.finish("a") }
        assertEquals(20, h.size)
        assertEquals("24", h.pop()!!.restore)                    // 가장 최근 묶음부터
    }

    @Test fun newestChunkComesBackFirst() {
        val h = DeletionHistory()
        h.begin("abc"); h.finish("ab")        // c 삭제
        h.begin("ab"); h.finish("a")          // b 삭제
        assertEquals("b", h.pop()!!.restore)
        assertEquals("c", h.pop()!!.restore)
    }

    @Test fun veryLongTextIsAlignedByTheEnd() {
        val body = (0 until 1200).joinToString("") { ('a' + it % 23).toString() + (it % 7) }
        val before = body.takeLast(DeletionHistory.WINDOW)            // 커서 앞 창
        val after = body.dropLast(5).takeLast(DeletionHistory.WINDOW)  // 5글자 지운 뒤의 창 (밀림)
        val h = DeletionHistory()
        h.begin(before)
        assertTrue(h.finish(after))
        assertEquals(body.takeLast(5), h.pop()!!.restore)
    }

    @Test fun clearForgetsEverything() {
        val h = DeletionHistory()
        h.begin("ab"); h.finish("a")
        h.begin("a")
        h.clear()
        assertEquals(0, h.size)
        assertFalse(h.hasRun)
    }
}
