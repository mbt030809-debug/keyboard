package com.mbt.keyboard.ime

import kotlin.math.abs

/** 고를 수 있는 키보드 테마 한 개 */
data class NamedTheme(val id: String, val name: String, val theme: KeyboardTheme)

/**
 * 키보드 테마 모음. 모두 한 가지 색(hue)의 명도만 달리한 톤온톤·톤인톤이다.
 * 배경 < 키 < 기능키 < 눌림 순으로 같은 색 계열을 단계적으로 쓰고, 글자는 같은 색의 가장 진한(또는 연한) 톤.
 * 밝은 테마 6개 + 어두운 테마 4개.
 */
object KeyboardThemes {
    /** 기본값: 휴대폰의 밝은/어두운 모드를 따라간다 */
    const val SYSTEM = "system"
    const val DEFAULT_ID = SYSTEM

    private fun light(h: Float, s: Float) = KeyboardTheme(
        background = hsl(h, s, 0.87f),
        key = hsl(h, s * 0.9f, 0.95f),
        specialKey = hsl(h, s, 0.78f),
        keyPressed = hsl(h, s, 0.68f),
        text = hsl(h, (s * 0.8f).coerceAtMost(0.5f), 0.15f),
        accent = hsl(h, (s + 0.2f).coerceAtMost(0.8f), 0.30f),
        onAccent = hsl(h, 0.4f, 0.97f),
    )

    private fun dark(h: Float, s: Float) = KeyboardTheme(
        background = hsl(h, s, 0.13f),
        key = hsl(h, s, 0.22f),
        specialKey = hsl(h, s, 0.17f),
        keyPressed = hsl(h, s, 0.32f),
        text = hsl(h, (s * 0.5f).coerceAtMost(0.3f), 0.93f),
        accent = hsl(h, (s + 0.2f).coerceAtMost(0.85f), 0.72f),
        onAccent = hsl(h, s, 0.1f),
    )

    /** 인기 있는 색 계열 10가지 (밝은 6 + 어두운 4) */
    val all: List<NamedTheme> = listOf(
        NamedTheme("cream", "크림 베이지", light(38f, 0.45f)),
        NamedTheme("lavender", "라벤더", light(262f, 0.45f)),
        NamedTheme("mint", "민트", light(160f, 0.38f)),
        NamedTheme("sky", "스카이 블루", light(207f, 0.55f)),
        NamedTheme("rose", "로즈 핑크", light(345f, 0.5f)),
        NamedTheme("sage", "세이지 그린", light(100f, 0.24f)),
        NamedTheme("charcoal", "차콜", dark(220f, 0.08f)),
        NamedTheme("navy", "미드나이트 네이비", dark(222f, 0.45f)),
        NamedTheme("mocha", "모카 브라운", dark(24f, 0.25f)),
        NamedTheme("forest", "딥 포레스트", dark(150f, 0.3f)),
    )

    fun byId(id: String?): NamedTheme? = all.firstOrNull { it.id == id }

    /** 저장된 id → 테마. 모르는 id나 [SYSTEM]이면 null (= 시스템 모드를 따르는 기본 테마) */
    fun resolve(id: String?): KeyboardTheme? = byId(id)?.theme

    /** HSL → ARGB (h: 0~360, s·l: 0~1) */
    fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}
