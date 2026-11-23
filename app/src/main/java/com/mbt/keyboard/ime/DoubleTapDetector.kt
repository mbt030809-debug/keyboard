package com.mbt.keyboard.ime

/**
 * 단모음 연타 판정: 같은 키를 [windowMs] 안에 다시 치면 연타(쌍자음·쌍모음 변환).
 * 그 시간이 지난 뒤에 치면 연타가 아니라 새 입력이다 (예: 0.3초라면 ㄱ ㄱ → ㄲ, 0.3초 넘게 쉬고 ㄱ → ㄱ + ㄱ).
 */
class DoubleTapDetector(var windowMs: Long) {
    private var lastKey: String? = null
    private var lastTime = 0L

    /** [key]를 [now]에 친 것이 직전 입력의 연타인가 (상태는 바꾸지 않는다) */
    fun isDouble(key: String, now: Long) = key == lastKey && now - lastTime <= windowMs

    /** 이번 입력을 기록한다 */
    fun record(key: String, now: Long) {
        lastKey = key
        lastTime = now
    }

    /** 연타 상태를 비운다 (변환이 끝났거나 다른 종류 입력이 끼어들 때) */
    fun reset() {
        lastKey = null
    }
}
