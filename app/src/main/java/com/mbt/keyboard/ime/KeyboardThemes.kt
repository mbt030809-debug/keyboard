package com.mbt.keyboard.ime

import kotlin.math.abs

/** 고를 수 있는 키보드 테마 한 개 */
data class NamedTheme(val id: String, val name: String, val theme: KeyboardTheme)

/**
 * 키보드 테마 모음. 모두 한 가지 색(hue)의 명도만 달리한 톤온톤·톤인톤이다.
 * 배경 < 키 < 기능키 < 눌림 순으로 같은 색 계열을 단계적으로 쓰고, 글자는 같은 색의 가장 진한(또는 연한) 톤.
 * 밝은 테마 12개 + 어두운 테마 8개.
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
        accent = hsl(h, (s + 0.2f).coerceAtMost(0.8f), 0.27f),
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

    /** 네온 계열처럼 한 색 명도만 바꾸는 방식이 아닌 테마는 직접 색을 정한다 */
    private fun custom(bg: Long, key: Long, special: Long, pressed: Long, text: Long, accent: Long, onAccent: Long) =
        KeyboardTheme(
            background = bg.toInt(), key = key.toInt(), specialKey = special.toInt(), keyPressed = pressed.toInt(),
            text = text.toInt(), accent = accent.toInt(), onAccent = onAccent.toInt(),
        )

    /** 그라데이션·반투명 유리 키 같은 "감성" 테마. 색은 모두 ARGB (유리 키는 알파가 들어 있다) */
    private fun glass(
        start: Long, end: Long, key: Long, border: Long, special: Long, pressed: Long,
        text: Long, accent: Long, onAccent: Long, mid: Long = 0, shadow: Boolean = true,
    ) = KeyboardTheme(
        background = (0xFF000000 or start).toInt(), key = key.toInt(), specialKey = special.toInt(),
        keyPressed = pressed.toInt(), text = (0xFF000000 or text).toInt(),
        accent = (0xFF000000 or accent).toInt(), onAccent = (0xFF000000 or onAccent).toInt(),
        backgroundMid = if (mid == 0L) 0 else (0xFF000000 or mid).toInt(),
        backgroundEnd = (0xFF000000 or end).toInt(),
        keyBorder = border.toInt(), keyShadow = shadow,
    )

    /** 총 28가지: 감성(그라데이션) 8 + 밝은 12 + 어두운 8 (모두 같은 색의 명도만 달리한 톤온톤, 네온 나이트만 예외) */
    val all: List<NamedTheme> = listOf(
        // 감성 테마: 그라데이션 배경 + 반투명 유리 키
        NamedTheme(
            "rainbowfrost", "레인보우 프로스트 (유리)",
            KeyboardTheme(
                background = 0xFFFFD9E8.toInt(), backgroundMid = 0xFFD9F6E4.toInt(), backgroundEnd = 0xFFE4D6FF.toInt(),
                key = 0x38FFFFFF, specialKey = 0x24FFFFFF, keyPressed = 0x8CB4C6FF.toInt(),
                text = 0xFF37405A.toInt(), accent = 0xFF3A50C8.toInt(), onAccent = 0xFFFFFFFF.toInt(),
                keyBorder = 0x99FFFFFF.toInt(), keyShadow = false, frost = true, numberKeyOverride = 0x2EFFFFFF,
            ),
        ),
        NamedTheme("sunset", "선셋 글로우", glass(0xFFB48A, 0xB79CFF, 0x66FFFFFF, 0x99FFFFFF, 0x33FFFFFF, 0x99FFFFFF, 0x4A1F5E, 0x7A2CBF, 0xFFFFFF, mid = 0xFF8FA3)),
        NamedTheme("pinkcloud", "핑크 클라우드", glass(0xFFE3EE, 0xE1D3FF, 0xD9FFFFFF, 0xFFFFFFFF, 0x99FFFFFF, 0xFFF6BFD6, 0x7B3A5E, 0xC22A6B, 0xFFFFFF, mid = 0xFFD0E6)),
        NamedTheme("aurora", "오로라", glass(0x0B1030, 0x3B2A7A, 0x1AFFFFFF, 0x47FFFFFF, 0x0FFFFFFF, 0x40FFFFFF, 0xEAF6FF, 0x6EF3D6, 0x04201A, mid = 0x14305A, shadow = false)),
        NamedTheme("mintsoda", "민트 소다", glass(0xC9F7E6, 0xBFE9FF, 0xBFFFFFFF, 0xFFFFFFFF, 0x80FFFFFF, 0xFF9FDCD2, 0x1F5B63, 0x137A6E, 0xFFFFFF)),
        NamedTheme("buttercream", "버터 크림", glass(0xFFF4C9, 0xFFD8B5, 0xCCFFFFFF, 0xFFFFFFFF, 0x80FFFFFF, 0xFFF0D080, 0x6A4A1F, 0x8A5A00, 0xFFFFFF, mid = 0xFFE9A3)),
        NamedTheme("film", "필름 베이지", glass(0xEFE6D8, 0xE6DAC8, 0x00000000, 0x00000000, 0x1A5B4636, 0x33A0714B, 0x5B4636, 0x7A4A26, 0xFFFFFF, shadow = false)),
        NamedTheme("midnightglass", "미드나이트 글래스", glass(0x07070F, 0x241A4A, 0x14FFFFFF, 0x2EFFFFFF, 0x0DFFFFFF, 0x38FFFFFF, 0xF1EEFF, 0xB69CFF, 0x120A2E, mid = 0x14122B, shadow = false)),
        NamedTheme("monoline", "모노 아웃라인", glass(0xFFFFFF, 0xFFFFFE, 0x00000000, 0x59000000, 0x0D000000, 0x26000000, 0x111111, 0x111111, 0xFFFFFF, shadow = false)),
        // 밝은 테마
        NamedTheme("cream", "크림 베이지", light(38f, 0.45f)),
        NamedTheme("butter", "버터 옐로", light(48f, 0.65f)),
        NamedTheme("peach", "피치", light(15f, 0.7f)),
        NamedTheme("rose", "로즈 핑크", light(345f, 0.5f)),
        NamedTheme("lavender", "라벤더", light(262f, 0.45f)),
        NamedTheme("periwinkle", "페리윙클", light(232f, 0.55f)),
        NamedTheme("sky", "스카이 블루", light(207f, 0.55f)),
        NamedTheme("mint", "민트", light(160f, 0.38f)),
        NamedTheme("sage", "세이지 그린", light(100f, 0.24f)),
        NamedTheme("matcha", "말차", light(75f, 0.38f)),
        NamedTheme("oat", "오트밀", light(34f, 0.2f)),
        NamedTheme("cloud", "클라우드 (모노)", light(220f, 0.04f)),
        // 어두운 테마
        NamedTheme("charcoal", "차콜", dark(220f, 0.08f)),
        NamedTheme("navy", "미드나이트 네이비", dark(222f, 0.45f)),
        NamedTheme("ocean", "딥 오션", dark(190f, 0.4f)),
        NamedTheme("forest", "딥 포레스트", dark(150f, 0.3f)),
        NamedTheme("mocha", "모카 브라운", dark(24f, 0.25f)),
        NamedTheme("wine", "와인", dark(345f, 0.35f)),
        NamedTheme("plum", "다크 플럼", dark(290f, 0.28f)),
        NamedTheme(
            "neon", "네온 나이트",
            custom(0xFF0D0E1A, 0xFF1C1E36, 0xFF14152B, 0xFF30345E, 0xFFEEF0FF, 0xFF6EF3FF, 0xFF03141A),
        ),
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
