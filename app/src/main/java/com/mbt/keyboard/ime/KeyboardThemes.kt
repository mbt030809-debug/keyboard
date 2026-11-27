package com.mbt.keyboard.ime

/** 고를 수 있는 키보드 테마 한 개 */
data class NamedTheme(val id: String, val name: String, val theme: KeyboardTheme)

/**
 * 키보드 테마 모음.
 * 기본(시스템 모드) 외에 11가지 글래스 테마를 제공한다.
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
                numberKeyGlass = 0xA6A6B8E6.toInt(), enterKeyGlass = 0xC298BEFF.toInt(),
            ),
        ),
        NamedTheme(
            "iosglass_taupe_refract", "웜 그레이지 · 옵티컬 굴절",
            KeyboardTheme(
                background = 0xFFA69C8B.toInt(), backgroundMid = 0xFFB8AC98.toInt(), backgroundEnd = 0xFF9BA0B4.toInt(),
                key = 0xB8FFFFFF.toInt(), specialKey = 0x99CDBFA6.toInt(), keyPressed = 0xFFC9CFDC.toInt(),
                text = 0xFF1C1F2A.toInt(), accent = 0xFF4660C8.toInt(), onAccent = 0xFFFFFFFF.toInt(),
                frost = true, frostStyle = 7, frostTint = 0x1AFFFFFF,
                numberKeyGlass = 0xA8BFB098.toInt(), enterKeyGlass = 0xB8B5A48A.toInt(),
            ),
        ),
        NamedTheme("sunset", "선셋 글로우", frost(3, 0xFFB48A, 0xFF8FA3, 0xB79CFF, 0x85FFFFFF, 0x8CFFAAA0, 0x9EFFD6BE, 0xA6F79086, 0xCCFFD0C0, 0x4A1F5E, 0x7A2CBF, 0xFFFFFF)),
        NamedTheme("pinkcloud", "핑크 클라우드", frost(4, 0xFFE3EE, 0xFFD0E6, 0xE1D3FF, 0x99FFFFFF, 0x8CF0BEE1, 0x9EFFCDE4, 0x9CEBA4CF, 0xCCF5C9E0, 0x5E2547, 0xC22A6B, 0xFFFFFF)),
        NamedTheme("violet_coral_orb", "바이올렛 · 코랄 오브", frost(18, 0x210D5C, 0x331478, 0x1A0F42, 0xEBDDD2FF, 0xE8C2B0F5, 0xECAE98ED, 0xF09B80E6, 0xFFEDE6FF, 0x160B33, 0x5D3EE6, 0xFFFFFF, tint = 0x18FFFFFF)),
        NamedTheme("midnight_magenta_orb", "미드나이트 · 마젠타 글로우", frost(19, 0x05081A, 0x0B1230, 0x130D2E, 0xEBD6E0FF, 0xE8B4C6F7, 0xEC9BB0EE, 0xF0859CE8, 0xFFE8EEFF, 0x0A102E, 0x3E5CE0, 0xFFFFFF, tint = 0x16FFFFFF)),
        NamedTheme("royal_cobalt_sunset", "로열 코발트 · 선셋 오브", frost(20, 0x070B4E, 0x12228C, 0x0C1366, 0xEBD9E2FF, 0xE8B8C8FA, 0xEC9FB2F0, 0xF0899EEB, 0xFFEBF0FF, 0x0A123D, 0x3852D6, 0xFFFFFF, tint = 0x16FFFFFF)),
        NamedTheme("pastel_pearl_orb", "파스텔 펄 · 오브 화이트", frost(21, 0xEAEFF6, 0xF4F6FB, 0xE5EAF5, 0xF5F2F4FC, 0xEBC8D1EE, 0xEFAEB9E0, 0xF299A7D9, 0xFFDCE2F7, 0x171C36, 0x4557D6, 0xFFFFFF, tint = 0x1AFFFFFF)),
        NamedTheme("aurora_prism_grain", "오로라 · 프리즘 블러", frost(22, 0x151136, 0x281952, 0x16224A, 0xEBEAD4F7, 0xE8D4B2EB, 0xECC098DF, 0xF0AE80D6, 0xFFF5E8FC, 0x1A0D2E, 0x7042C2, 0xFFFFFF, tint = 0x18FFFFFF)),
        NamedTheme("teal_peach_bloom", "틸 · 피치 블룸", frost(23, 0x3F8E9B, 0x8E8B82, 0xE89578, 0xEEFFE8DB, 0xE8F2C1AC, 0xECDF9F85, 0xF0CB8266, 0xFFFFD5C2, 0x26120E, 0xA63E26, 0xFFFFFF, tint = 0x18FFFFFF)),
    )

    fun byId(id: String?): NamedTheme? = all.firstOrNull { it.id == id }

    /** 저장된 id → 테마. 모르는 id나 [SYSTEM]이면 null (= 시스템 모드를 따르는 기본 테마) */
    fun resolve(id: String?): KeyboardTheme? = byId(id)?.theme
}
