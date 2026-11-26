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

    /** 글래스(프로스트) 테마 한 벌: 판 한 장의 유리 + 색입힌 투명 유리 키. 색은 모두 ARGB, 테마 고유의 번짐 배경은 [style] */
    private fun frost(
        style: Int, start: Long, mid: Long, end: Long, key: Long, special: Long, number: Long, enter: Long, pressed: Long,
        text: Long, accent: Long, onAccent: Long, tint: Long = 0x1AFFFFFF, space: Long = 0,
    ) = KeyboardTheme(
        background = (0xFF000000 or start).toInt(), backgroundMid = (0xFF000000 or mid).toInt(), backgroundEnd = (0xFF000000 or end).toInt(),
        key = key.toInt(), specialKey = special.toInt(), keyPressed = pressed.toInt(),
        text = (0xFF000000 or text).toInt(), accent = (0xFF000000 or accent).toInt(), onAccent = (0xFF000000 or onAccent).toInt(),
        frost = true, frostStyle = style, frostTint = tint.toInt(), numberKeyGlass = number.toInt(), enterKeyGlass = enter.toInt(),
        spaceKey = space.toInt(),
    )

    /** 총 28가지: 감성(그라데이션) 8 + 밝은 12 + 어두운 8 (모두 같은 색의 명도만 달리한 톤온톤, 네온 나이트만 예외) */
    val all: List<NamedTheme> = listOf(
        // 감성 테마: 그라데이션 배경 + 반투명 유리 키
        // 프로스티드 글래스 (iOS 27 스타일): 유리 판 한 장 + 선명한 흰 유리 키
        NamedTheme(
            "iosglass_holo", "iOS 글래스 · 홀로그램",
            KeyboardTheme(
                background = 0xFFBFC7EC.toInt(), backgroundMid = 0xFFEFF0D2.toInt(), backgroundEnd = 0xFFE9D5EE.toInt(),
                key = 0xCCFFFFFF.toInt(), specialKey = 0x99B7C2EE.toInt(), keyPressed = 0xFFC9D0E8.toInt(),
                text = 0xFF2C3550.toInt(), accent = 0xFF4256D4.toInt(), onAccent = 0xFFFFFFFF.toInt(),
                frost = true, frostStyle = 0, frostTint = 0x1AFFFFFF,
                numberKeyGlass = 0x99C9D3F5.toInt(), enterKeyGlass = 0xB893A8F6.toInt(),
            ),
        ),
        NamedTheme(
            "iosglass_taupe", "iOS 글래스 · 웜 그레이지",
            KeyboardTheme(
                background = 0xFFA89E8D.toInt(), backgroundMid = 0xFFB8AC98.toInt(), backgroundEnd = 0xFF9EA0B2.toInt(),
                key = 0xD0FFFFFF.toInt(), specialKey = 0x99CDBFA6.toInt(), keyPressed = 0xFFC9CFDC.toInt(),
                text = 0xFF1C1F2A.toInt(), accent = 0xFF4660C8.toInt(), onAccent = 0xFFFFFFFF.toInt(),
                frost = true, frostStyle = 1, frostTint = 0x24FFFFFF,
                numberKeyGlass = 0x99D9CCB8.toInt(), enterKeyGlass = 0xB89DB2E8.toInt(),
            ),
        ),
        NamedTheme(
            "iosglass_navy", "iOS 글래스 · 딥 네이비",
            KeyboardTheme(
                background = 0xFF1B1F3D.toInt(), backgroundMid = 0xFF2A2F5C.toInt(), backgroundEnd = 0xFF1F3A55.toInt(),
                key = 0xB8FFFFFF.toInt(), specialKey = 0xB0B9C9EE.toInt(), keyPressed = 0xFFB4C2E0.toInt(),
                text = 0xFF151A2E.toInt(), accent = 0xFF5A96FF.toInt(), onAccent = 0xFF0A1A3A.toInt(),
                frost = true, frostStyle = 2, frostTint = 0x1AFFFFFF,
                numberKeyGlass = 0xB8C4D2F2.toInt(), enterKeyGlass = 0xC298BEFF.toInt(),
            ),
        ),
        NamedTheme(
            "iosglass_taupe_refract", "웜 그레이지 · 옵티컬 굴절",
            KeyboardTheme(
                background = 0xFFA69C8B.toInt(), backgroundMid = 0xFFB8AC98.toInt(), backgroundEnd = 0xFF9BA0B4.toInt(),
                key = 0xB8FFFFFF.toInt(), specialKey = 0x99CDBFA6.toInt(), keyPressed = 0xFFC9CFDC.toInt(),
                text = 0xFF1C1F2A.toInt(), accent = 0xFF4660C8.toInt(), onAccent = 0xFFFFFFFF.toInt(),
                frost = true, frostStyle = 7, frostTint = 0x1AFFFFFF,
                numberKeyGlass = 0x99D9CCB8.toInt(), enterKeyGlass = 0xB89DB2E8.toInt(),
            ),
        ),
        // 글래스 리디자인: 각 테마 고유의 색은 그대로, 판은 유리 한 장 + 키는 색입힌 투명 유리
        NamedTheme("sunset", "선셋 글로우", frost(3, 0xFFB48A, 0xFF8FA3, 0xB79CFF, 0x85FFFFFF, 0x8CFFAAA0, 0x9EFFD6BE, 0x9EFF6E8C, 0xCCFFD0C0, 0x4A1F5E, 0x7A2CBF, 0xFFFFFF)),
        NamedTheme("pinkcloud", "핑크 클라우드", frost(4, 0xFFE3EE, 0xFFD0E6, 0xE1D3FF, 0x99FFFFFF, 0x8CF0BEE1, 0x9EFFCDE4, 0x80F08CBB, 0xCCF5C9E0, 0x5E2547, 0xC22A6B, 0xFFFFFF)),
        NamedTheme("aurora", "오로라", frost(5, 0x0B1030, 0x14305A, 0x3B2A7A, 0x33FFFFFF, 0x386EF3D6, 0x4D5046AA, 0x7A2FA898, 0xFF4A5A9A, 0xEAF6FF, 0x6EF3D6, 0x04201A, tint = 0x14FFFFFF)),
        NamedTheme("mintsoda", "민트 소다", frost(6, 0xC9F7E6, 0xA6ECDC, 0xBFE9FF, 0x94FFFFFF, 0x8CAAD7F5, 0x9EA0E6D7, 0x8C8FE6D6, 0xFFBFE6E0, 0x1F5B63, 0x137A6E, 0xFFFFFF)),
        NamedTheme("glass_violet_coral", "바이올렛 코랄 3D", frost(8, 0x2B0E6E, 0x3F148C, 0x521899, 0xFF342B4F, 0xFF251E3B, 0xFF251E3B, 0xFF9E4226, 0xFF473B68, 0xFFF8F5, 0xFF8A4C, 0x260800, tint = 0x16FFFFFF, space = 0xFF251E3B)),
        NamedTheme("glass_midnight_neon", "미드나이트 네온", frost(9, 0x060A18, 0x0B132B, 0x121A3A, 0xFF222B44, 0xFF171E32, 0xFF171E32, 0xFF983868, 0xFF334062, 0xF4F8FF, 0xFF6EC7, 0x260418, tint = 0x14FFFFFF, space = 0xFF171E32)),
        NamedTheme("glass_royal_prism", "로열 프리즘", frost(10, 0x0D1466, 0x182899, 0x281B7A, 0xFF2A3254, 0xFF1E2440, 0xFF1E2440, 0xFF9E4820, 0xFF3E4976, 0xF5F7FF, 0xFFB85C, 0x241400, tint = 0x14FFFFFF, space = 0xFF1E2440)),
        NamedTheme("glass_pastel_orb", "파스텔 오브 화이트", frost(11, 0xE8ECF4, 0xF2F4FA, 0xE4E9F5, 0xFFF5F6FA, 0xFFE4E8F2, 0xFFE4E8F2, 0xFFECBDD4, 0xFFFFFFFF, 0x1E2436, 0xE86FA8, 0x2B0418, tint = 0x18FFFFFF, space = 0xFFE4E8F2)),
        NamedTheme("glass_aurora_grain", "오로라 실리카", frost(12, 0x181038, 0x2D1A5E, 0x1B2654, 0xFF32294A, 0xFF231C36, 0xFF231C36, 0xFF9C462A, 0xFF453B64, 0xFBF6FF, 0xFFC06E, 0x261400, tint = 0x14FFFFFF, space = 0xFF231C36)),
        NamedTheme("glass_coral_bloom", "코랄 블룸", frost(13, 0x2D7A84, 0x7A7E78, 0xB86E56, 0xFF314448, 0xFF233236, 0xFF233236, 0xFF9A4428, 0xFF455D63, 0xFFF9F4, 0xFF9F68, 0x260E00, tint = 0x16FFFFFF, space = 0xFF233236)),
        NamedTheme("glass_emerald_orb", "에메랄드 오브", frost(14, 0x071C18, 0x0C302A, 0x124238, 0xFF203A35, 0xFF162A27, 0xFF162A27, 0xFF28603A, 0xFF31544E, 0xF0FFF9, 0x6EF3D0, 0x04221A, tint = 0x14FFFFFF, space = 0xFF162A27)),
        NamedTheme("glass_cyber_orb", "사이버 핑크 오브", frost(15, 0x0A0B1E, 0x161438, 0x24164A, 0xFF2B2744, 0xFF1E1B32, 0xFF1E1B32, 0xFF225B78, 0xFF403A62, 0xF6F4FF, 0x5CE1FF, 0x041C26, tint = 0x14FFFFFF, space = 0xFF1E1B32)),
        NamedTheme("glass_sunset_orb", "선셋 피치 오브", frost(16, 0x2E0E24, 0x4E1634, 0x6B2038, 0xFF442A36, 0xFF321E28, 0xFF321E28, 0xFFA0482A, 0xFF5C3A4A, 0xFFF6F2, 0xFFB068, 0x261200, tint = 0x14FFFFFF, space = 0xFF321E28)),
        NamedTheme("glass_ice_orb", "아이스 민트 오브", frost(17, 0xE4F2F6, 0xEDF6FA, 0xE2ECF8, 0xFFF2F7F8, 0xFFE2ECEF, 0xFFE2ECEF, 0xFFB0DCD4, 0xFFFFFFFF, 0x182632, 0x38B2AC, 0x04221E, tint = 0x18FFFFFF, space = 0xFFE2ECEF)),
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
