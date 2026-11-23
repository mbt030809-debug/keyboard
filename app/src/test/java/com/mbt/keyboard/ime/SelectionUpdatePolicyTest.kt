package com.mbt.keyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionUpdatePolicyTest {
    private fun reset(composing: Boolean, selStart: Int, selEnd: Int, candEnd: Int, msSince: Long) =
        SelectionUpdatePolicy.shouldResetComposition(composing, selStart, selEnd, candEnd, msSince)

    @Test fun notComposingNeverResets() {
        assertFalse(reset(false, 3, 3, -1, 10_000))
        assertFalse(reset(false, 1, 9, 5, 10_000))
    }

    @Test fun consistentUpdateKeepsComposition() {
        assertFalse(reset(true, 7, 7, 7, 10))          // 커서 = 조합 영역 끝
        assertFalse(reset(true, 7, 7, 7, 100_000))
    }

    @Test fun userMovingTheCursorAwayEndsComposition() {
        assertTrue(reset(true, 2, 2, 7, 5_000))        // 조합 영역은 그대로인데 커서만 다른 곳
        assertTrue(reset(true, 7, 9, 7, 50))           // 선택 영역이 생김
    }

    @Test fun staleUpdateWithoutComposingRegionRightAfterOurEditIsIgnored() {
        // 빠르게 칠 때: "조합 영역 없음" 옛 알림이 새 글자 조합 중에 도착 → 조합을 끊으면 안 된다
        assertFalse(reset(true, 5, 5, -1, 20))
        assertFalse(reset(true, 5, 5, -1, SelectionUpdatePolicy.STALE_WINDOW_MS))
    }

    @Test fun noComposingRegionLongAfterOurEditMeansTheAppDroppedIt() {
        assertTrue(reset(true, 5, 5, -1, SelectionUpdatePolicy.STALE_WINDOW_MS + 1))
    }

    @Test fun negativeElapsedTimeIsNotTreatedAsStale() {
        assertTrue(reset(true, 5, 5, -1, -1))
    }
}
