package com.mbt.keyboard.layout

/** 자판 정의 (데이터 기반). 새 자판은 여기에 행 목록만 추가하면 된다. */
object Layouts {
    const val KO = "ko"
    const val EN = "en"

    private fun ch(label: String, shifted: String? = null, hint: String? = null) =
        Key(label = label, shifted = shifted, hints = listOfNotNull(hint))

    private fun spacer(w: Float) = Key(label = "", type = KeyType.SPACER, weight = w)

    private val shift = Key("⇧", KeyType.SHIFT, weight = 1.5f)
    private val backspace = Key("⌫", KeyType.BACKSPACE, weight = 1.5f)
    private val lang = Key("한/영", KeyType.LANG, weight = 1.5f)
    private val space = Key("", KeyType.SPACE, output = " ", weight = 5f)
    private val enter = Key("⏎", KeyType.ENTER, weight = 1.5f)

    /** 꾹 누르기 후보: 윗줄=숫자, 가운데줄/아랫줄=기호. 한글·영문 자판 위치가 같아 공용. */
    private val row1Hints = "1234567890".map { it.toString() }
    private val row2Hints = listOf("@", "#", "$", "%", "&", "*", "-", "+", "=")
    private val row3Hints = listOf("~", "\\", "\"", "'", "_", "|", "/")

    private fun bottom() = listOf(
        lang,
        Key(",", hints = listOf("(", ")", "[", "]", "{", "}")),
        space,
        Key(".", hints = listOf("?", "!", ":", ";", "…")),
        enter,
    )

    private fun row(chars: List<String>, shifted: List<String?>, hints: List<String>) =
        chars.mapIndexed { i, c -> ch(c, shifted[i], hints[i]) }

    /** 한글 두벌식. 쉬프트: ㅃㅉㄸㄲㅆ ㅒ ㅖ */
    val korean = KeyboardLayout(
        KO, "한글",
        listOf(
            row(
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
                listOf("ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", null, null, null, "ㅒ", "ㅖ"),
                row1Hints,
            ),
            listOf(spacer(0.5f)) +
                row(
                    listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"),
                    List(9) { null },
                    row2Hints,
                ) + spacer(0.5f),
            listOf(shift) +
                row(
                    listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ"),
                    List(7) { null },
                    row3Hints,
                ) + backspace,
            bottom(),
        ),
    )

    val english = KeyboardLayout(
        EN, "English",
        listOf(
            row(
                "qwertyuiop".map { it.toString() },
                "qwertyuiop".map { it.uppercase() },
                row1Hints,
            ),
            listOf(spacer(0.5f)) +
                row(
                    "asdfghjkl".map { it.toString() },
                    "asdfghjkl".map { it.uppercase() },
                    row2Hints,
                ) + spacer(0.5f),
            listOf(shift) +
                row(
                    "zxcvbnm".map { it.toString() },
                    "zxcvbnm".map { it.uppercase() },
                    row3Hints,
                ) + backspace,
            bottom(),
        ),
    )

    val all = listOf(korean, english)
    fun byId(id: String) = all.first { it.id == id }
}
