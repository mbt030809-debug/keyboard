package com.mbt.keyboard.settings

import java.util.Locale
import kotlin.math.roundToLong

/** 설정 가능한 입력 타이밍(밀리초)의 기본값·범위·단위. 슬라이더와 저장값을 같은 단위로 맞춘다. */
object Timing {
    /** 단모음: 모음으로 끝난 글자 뒤에서 이 시간 이상 멈췄다 친 자음은 다음 글자 초성 */
    const val PAUSE_DEFAULT_MS = 700L
    const val PAUSE_MIN_MS = 300L
    const val PAUSE_MAX_MS = 3000L
    const val PAUSE_STEP_MS = 100L

    /** 꾹 누르기(특수기호 말풍선, 상용구 열기) 인식 시간 */
    const val LONG_PRESS_DEFAULT_MS = 350L
    const val LONG_PRESS_MIN_MS = 200L
    const val LONG_PRESS_MAX_MS = 800L
    const val LONG_PRESS_STEP_MS = 50L

    fun snapPause(ms: Long) = snap(ms, PAUSE_MIN_MS, PAUSE_MAX_MS, PAUSE_STEP_MS)

    fun snapLongPress(ms: Long) = snap(ms, LONG_PRESS_MIN_MS, LONG_PRESS_MAX_MS, LONG_PRESS_STEP_MS)

    /** 가장 가까운 단위로 맞추고 범위 안으로 */
    fun snap(ms: Long, min: Long, max: Long, step: Long): Long =
        ((ms.toDouble() / step).roundToLong() * step).coerceIn(min, max)

    /** 화면에 보여줄 글자: 700 → "0.7초", 350 → "0.35초" */
    fun seconds(ms: Long): String {
        val s = ms / 1000.0
        val text = String.format(Locale.US, if (ms % 100L == 0L) "%.1f" else "%.2f", s)
        return "${text}초"
    }
}
