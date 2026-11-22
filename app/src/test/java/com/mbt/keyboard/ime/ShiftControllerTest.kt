package com.mbt.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftControllerTest {
    private val c = ShiftController(longPressMs = 450)

    private fun tap(at: Long, holdMs: Long = 80) { c.press(at); c.release(at + holdMs) }

    @Test fun tapTurnsOnAndSecondTapTurnsOffEvenIfFast() {
        tap(0)
        assertEquals(ShiftState.ON, c.state)
        tap(120)                       // 아주 빠르게 다시 탭해도 고정이 아니라 해제
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun secondTapTurnsOffWhenSlowToo() {
        tap(0); tap(2000)
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun pressAppliesImmediately() {
        c.press(0)
        assertEquals(ShiftState.ON, c.state)
        assertTrue(c.held)
    }

    @Test fun oneShotIsConsumedByFirstKey() {
        tap(0)
        c.consume()
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun longPressLocksAndTapUnlocks() {
        c.press(0); c.release(600)
        assertEquals(ShiftState.LOCK, c.state)
        c.consume()                                   // 고정은 글자를 쳐도 유지
        assertEquals(ShiftState.LOCK, c.state)
        tap(1000)
        assertEquals(ShiftState.OFF, c.state)
    }

    @Test fun holdingShiftWhileTypingStaysOnUntilReleased() {
        c.press(0)
        c.consume(); c.consume()                      // 누른 채로 두 글자
        assertEquals(ShiftState.ON, c.state)
        c.release(1000)
        assertEquals(ShiftState.OFF, c.state)         // 오래 눌렀어도 입력했으면 고정되지 않는다
        assertFalse(c.held)
    }

    @Test fun holdingFromLockKeepsLock() {
        c.press(0); c.release(600)                    // LOCK
        c.press(2000); c.consume(); c.release(2500)
        assertEquals(ShiftState.LOCK, c.state)
    }

    @Test fun resetClearsEverything() {
        c.press(0); c.release(600)
        c.reset()
        assertEquals(ShiftState.OFF, c.state)
        assertFalse(c.held)
    }

    @Test fun shiftPressedThenSelectionSwipeConsumesOneShot() {
        tap(0)                                         // ON
        c.consume()                                    // 스와이프(선택)가 끝나면 한 번용 해제
        assertEquals(ShiftState.OFF, c.state)
    }
}
