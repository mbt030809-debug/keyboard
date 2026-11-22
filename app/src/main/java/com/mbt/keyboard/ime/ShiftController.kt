package com.mbt.keyboard.ime

/**
 * 쉬프트 상태 기계 (안드로이드에 의존하지 않아 단위 테스트 가능).
 *
 * - 한 번 탭: 켜짐(ON, 다음 글자 하나만). 글자를 치면 자동으로 꺼진다
 * - 켜진 상태에서 빠르게([doubleTapMs] 안에) 한 번 더 탭: 고정(LOCK, 캡스락)
 * - 켜진 상태에서 천천히 한 번 더 탭: 꺼짐
 * - 고정 상태에서 탭: 꺼짐
 * - 누른 채로 다른 키를 입력: 누르고 있는 동안만 쉬프트, 뗄 때 해제 (고정이었다면 그대로 고정)
 */
class ShiftController(private val doubleTapMs: Long = 400L) {

    var state: ShiftState = ShiftState.OFF
        private set

    /** 쉬프트 키를 누르고 있는 중 */
    var held: Boolean = false
        private set

    private var usedWhileHeld = false
    private var before = ShiftState.OFF
    private var downTime = 0L
    private var lastTapDownTime = Long.MIN_VALUE / 2

    /** 쉬프트 키를 누름. 꺼져 있으면 누르는 즉시 켜진다 */
    fun press(now: Long) {
        held = true
        usedWhileHeld = false
        before = state
        downTime = now
        if (state == ShiftState.OFF) state = ShiftState.ON
    }

    /** 쉬프트 키를 뗌 */
    fun release() {
        held = false
        if (usedWhileHeld) {
            // 누른 채로 글자를 입력한 경우: 탭이 아니다
            state = if (before == ShiftState.LOCK) ShiftState.LOCK else ShiftState.OFF
            return
        }
        val quick = downTime - lastTapDownTime <= doubleTapMs
        state = when (before) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> if (quick) ShiftState.LOCK else ShiftState.OFF
            ShiftState.LOCK -> ShiftState.OFF
        }
        lastTapDownTime = downTime
    }

    /** 글자를 입력(또는 선택 이동)한 뒤. 누르고 있는 쉬프트는 유지, 한 번용(ON)은 해제 */
    fun consume() {
        if (held) usedWhileHeld = true
        else if (state == ShiftState.ON) state = ShiftState.OFF
    }

    fun reset() {
        state = ShiftState.OFF
        held = false
        usedWhileHeld = false
        before = ShiftState.OFF
    }
}
