package com.mbt.keyboard.ime

/**
 * 쉬프트 상태 기계 (안드로이드에 의존하지 않아 단위 테스트 가능).
 *
 * - 짧게 탭: 꺼짐 → 한 번용(ON) → 꺼짐. 두 번 눌러서 푼다 (빠르게 눌러도 그대로 풀림)
 * - 길게 누르기([longPressMs] 이상): 고정(LOCK). 고정 상태에서 탭하면 풀림
 * - 누른 채로 다른 키를 입력: 누르고 있는 동안만 쉬프트, 뗄 때 해제 (고정이었다면 그대로 고정)
 */
class ShiftController(private val longPressMs: Long = 450L) {

    var state: ShiftState = ShiftState.OFF
        private set

    /** 쉬프트 키를 누르고 있는 중 */
    var held: Boolean = false
        private set

    private var usedWhileHeld = false
    private var before = ShiftState.OFF
    private var downTime = 0L

    /** 쉬프트 키를 누름. 꺼져 있으면 누르는 즉시 켜진다 */
    fun press(now: Long) {
        held = true
        usedWhileHeld = false
        before = state
        downTime = now
        if (state == ShiftState.OFF) state = ShiftState.ON
    }

    /** 쉬프트 키를 뗌 */
    fun release(now: Long) {
        held = false
        state = when {
            usedWhileHeld -> if (before == ShiftState.LOCK) ShiftState.LOCK else ShiftState.OFF
            before == ShiftState.LOCK -> ShiftState.OFF
            now - downTime >= longPressMs -> ShiftState.LOCK
            before == ShiftState.OFF -> ShiftState.ON
            else -> ShiftState.OFF            // ON 상태에서 다시 탭 → 해제
        }
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
