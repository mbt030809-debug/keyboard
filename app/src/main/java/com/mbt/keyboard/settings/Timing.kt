package com.mbt.keyboard.settings

import java.util.Locale
import kotlin.math.roundToLong

/** 설정 가능한 입력 타이밍(밀리초)의 기본값·범위·단위. 슬라이더와 저장값을 같은 단위로 맞춘다. */
object Timing {
    /**
     * 단모음 연타 기준(쌍자음·쌍모음): 같은 키를 이 시간 안에 다시 치면 ㄱ→ㄲ, ㅏ→ㅑ 로 바뀌고,
     * 이 시간이 지난 뒤에 치면 변환 없이 따로(ㄱ + ㄱ) 입력된다.
     */
    const val DOUBLE_TAP_DEFAULT_MS = 350L
    const val DOUBLE_TAP_MIN_MS = 100L
    const val DOUBLE_TAP_MAX_MS = 1000L
    const val DOUBLE_TAP_STEP_MS = 50L

    /** 꾹 누르기(특수기호 말풍선, 상용구 열기) 인식 시간 */
    const val LONG_PRESS_DEFAULT_MS = 350L
    const val LONG_PRESS_MIN_MS = 200L
    const val LONG_PRESS_MAX_MS = 800L
    const val LONG_PRESS_STEP_MS = 50L

    /** 지우기 키를 꾹 누를 때 초당 지워지는 글자 수 (기본 22자 ≈ 45ms 간격) */
    const val REPEAT_CPS_DEFAULT = 22
    const val REPEAT_CPS_MIN = 5
    const val REPEAT_CPS_MAX = 40

    fun snapRepeatCps(cps: Int) = cps.coerceIn(REPEAT_CPS_MIN, REPEAT_CPS_MAX)

    /** 초당 글자 수 → 반복 간격(밀리초) */
    fun repeatIntervalMs(cps: Int): Long = (1000.0 / snapRepeatCps(cps)).roundToLong()

    fun snapDoubleTap(ms: Long) = snap(ms, DOUBLE_TAP_MIN_MS, DOUBLE_TAP_MAX_MS, DOUBLE_TAP_STEP_MS)

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
