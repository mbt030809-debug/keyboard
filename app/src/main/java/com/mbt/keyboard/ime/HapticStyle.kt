package com.mbt.keyboard.ime

import com.mbt.keyboard.settings.Prefs

/**
 * 키를 누를 때의 진동 "느낌". 기계식 키보드 스위치를 흉내 낸 짧은 파형이다.
 * 각 단계는 (길이 ms, 세기 0~1)이고 세기 0은 쉬는 구간이다.
 * 실제 세기·길이는 설정의 "진동 세기"(1~10)로 키우고 줄인다. 안드로이드 비의존이라 단위 테스트가 가능하다.
 */
enum class HapticStyle(val id: String, val label: String, val desc: String, private val steps: List<Pair<Int, Float>>) {
    /** 예전 방식: 한 번 짧게 */
    DEFAULT("default", "기본", "한 번 짧게 (원래 방식)", listOf(10 to 1f)),

    /** 청축: 딸깍-딸깍, 날카로운 두 번의 클릭 */
    BLUE("blue", "청축 · 딸깍", "날카롭게 두 번 \"딸깍\" (클릭감 최대)", listOf(6 to 1f, 14 to 0f, 5 to 0.65f)),

    /** 갈축: 걸림 후 눌림, 부드러운 턱 */
    BROWN("brown", "갈축 · 톡", "살짝 걸렸다 \"톡\" (적당한 턱)", listOf(5 to 0.45f, 3 to 0f, 8 to 0.9f)),

    /** 적축: 걸림 없이 매끈하고 약하게 */
    RED("red", "적축 · 스르륵", "걸림 없이 가볍고 부드럽게", listOf(13 to 0.38f)),

    /** 흑축: 무겁고 깊게 */
    BLACK("black", "흑축 · 묵직", "깊고 묵직하게 \"덜컥\"", listOf(16 to 1f, 8 to 0.4f)),

    /** 정전용량(토프레): 낮게 퍽 하고 가라앉는 소리 */
    TOPRE("topre", "정전식 · 퍽", "낮고 둔하게 \"퍽\" 하고 가라앉음", listOf(11 to 0.8f, 5 to 0.25f, 14 to 0.55f));

    /** 세기 [strength](1~10)에 맞춘 단계들. 길이는 최소 1ms, 세기는 0~255 */
    fun pattern(strength: Int): Pattern {
        val s = strength.coerceIn(Prefs.HAPTIC_MIN, Prefs.HAPTIC_MAX)
        val timeScale = (Haptics.durationMs(s) / 15f).coerceIn(0.5f, 1.6f)
        val base = 100 + s * 15                                  // 115 ~ 250
        val timings = LongArray(steps.size) { (steps[it].first * timeScale).toLong().coerceAtLeast(1L) }
        val amps = IntArray(steps.size) { i ->
            val f = steps[i].second
            if (f <= 0f) 0 else (base * f).toInt().coerceIn(1, 255)
        }
        return Pattern(timings, amps)
    }

    class Pattern(val timings: LongArray, val amplitudes: IntArray)

    companion object {
        fun byId(id: String?): HapticStyle = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
