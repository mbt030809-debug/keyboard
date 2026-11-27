package com.mbt.keyboard.ime

/** 고를 수 있는 키보드 테마 한 개 */
data class NamedTheme(val id: String, val name: String, val theme: KeyboardTheme)

/**
 * 키보드 테마 모음.
 * 기본(시스템 모드) 외에 5가지 글래스 테마를 제공한다.
 */
object KeyboardThemes {
    /** 기본값: 휴대폰의 밝은/어두운 모드를 따라간다 */
    const val SYSTEM = "system"
    const val DEFAULT_ID = SYSTEM

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

    val all: List<NamedTheme> = listOf(
        NamedTheme(
            "iosglass_holo", "홀로그램",
            KeyboardTheme(
                background = 0xFFBFC7EC.toInt(), backgroundMid = 0xFFEFF0D2.toInt(), backgroundEnd = 0xFFE9D5EE.toInt(),
                key = 0xCCFFFFFF.toInt(), specialKey = 0x99B7C2EE.toInt(), keyPressed = 0xFFC9D0E8.toInt(),
                text = 0xFF2C3550.toInt(), accent = 0xFF4256D4.toInt(), onAccent = 0xFFFFFFFF.toInt(),
                frost = true, frostStyle = 0, frostTint = 0x1AFFFFFF,
                numberKeyGlass = 0x99C9D3F5.toInt(), enterKeyGlass = 0xB893A8F6.toInt(),
            ),
        ),
        NamedTheme(
            "iosglass_navy", "딥 네이비",
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
        NamedTheme("sunset", "선셋 글로우", frost(3, 0xFFB48A, 0xFF8FA3, 0xB79CFF, 0x85FFFFFF, 0x8CFFAAA0, 0x9EFFD6BE, 0x9EFF6E8C, 0xCCFFD0C0, 0x4A1F5E, 0x7A2CBF, 0xFFFFFF)),
        NamedTheme("pinkcloud", "핑크 클라우드", frost(4, 0xFFE3EE, 0xFFD0E6, 0xE1D3FF, 0x99FFFFFF, 0x8CF0BEE1, 0x9EFFCDE4, 0x80F08CBB, 0xCCF5C9E0, 0x5E2547, 0xC22A6B, 0xFFFFFF)),
    )

    fun byId(id: String?): NamedTheme? = all.firstOrNull { it.id == id }

    /** 저장된 id → 테마. 모르는 id나 [SYSTEM]이면 null (= 시스템 모드를 따르는 기본 테마) */
    fun resolve(id: String?): KeyboardTheme? = byId(id)?.theme
}
