package com.mbt.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftControllerTest {
    private val c = ShiftController(doubleTapMs = 400)

    /** 탭: [at]에 눌렀다가 곧바로 뗀다 */
    private fun tap(at: Long) { c.press(at); c.release() }

    @Test fun singleTapTurnsOnAndPressAppliesImmediately() {
        c.press(0)
        assertEquals(ShiftState.ON, c.state)
        assertTrue(c.held)
        c.release()
        assertEquals(ShiftState.ON, c.state)
        assertFalse(c.held)
    }

    @Test fun oneShotIsConsumedByFirstKey() {
        tap(0)
        c.consume()
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun quickDoubleTapLocksAndOneMoreTapReleases() {
        tap(0)
        tap(250)                                  // 빠르게 두 번 → 캡스락
        assertEquals(ShiftState.LOCK, c.state)
        tap(1500)                                 // 한 번 더 → 풀림
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun lockedStaysLockedWhileTyping() {
        tap(0); tap(250)
        c.consume(); c.consume(); c.consume()
        assertEquals(ShiftState.LOCK, c.state)
    }

    @Test fun lockedReleasesEvenIfTappedAgainVeryQuickly() {
        tap(0); tap(250)                          // LOCK
        tap(400)                                  // 곧바로 한 번 더 → 풀림 (다시 잠기지 않는다)
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun slowSecondTapTurnsOffInsteadOfLocking() {
        tap(0)
        tap(2000)
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun afterReleaseAnotherTapTurnsOnAgain() {
        tap(0); tap(250); tap(1500)               // ON → LOCK → OFF
        tap(3000)
        assertEquals(ShiftState.ON, c.state)
    }

    @Test fun holdingShiftWhileTypingStaysOnUntilReleased() {
        c.press(0)
        c.consume(); c.consume()                  // 누른 채로 두 글자
        assertEquals(ShiftState.ON, c.state)
        c.release()
        assertEquals(ShiftState.OFF, c.state)
        assertFalse(c.held)
    }

    @Test fun holdingFromLockKeepsLock() {
        tap(0); tap(250)                          // LOCK
        c.press(2000); c.consume(); c.release()
        assertEquals(ShiftState.LOCK, c.state)
    }

    @Test fun holdingAfterTypingDoesNotCountAsTapForDoubleTap() {
        c.press(0); c.consume(); c.release()      // 누른 채 입력 (탭 아님)
        tap(100)                                  // 바로 이어진 탭은 첫 탭
        assertEquals(ShiftState.ON, c.state)
    }

    @Test fun resetClearsEverything() {
        tap(0); tap(250)
        c.reset()
        assertEquals(ShiftState.OFF, c.state)
        assertFalse(c.held)
    }

    @Test fun shiftSwipeSelectionConsumesOneShotButNotLock() {
        tap(0)
        c.consume()
        assertEquals(ShiftState.OFF, c.state)
        tap(5000); tap(5200)
        c.consume()
        assertEquals(ShiftState.LOCK, c.state)
    }
}
