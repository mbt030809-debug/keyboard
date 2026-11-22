package com.mbt.keyboard.layout

/** 자판 정의 (데이터 기반). 새 자판은 여기에 행 목록만 추가하면 된다. */
object Layouts {
    const val KO = "ko"
    const val EN = "en"

    private fun ch(label: String, shifted: String? = null, weight: Float = 1f) =
        Key(label = label, shifted = shifted, weight = weight)

    private fun spacer(w: Float) = Key(label = "", type = KeyType.SPACER, weight = w)

    private val shift = Key("⇧", KeyType.SHIFT, weight = 1.5f)
    private val backspace = Key("⌫", KeyType.BACKSPACE, weight = 1.5f)
    private val lang = Key("한/영", KeyType.LANG, weight = 1.5f)
    private val space = Key("", KeyType.SPACE, output = " ", weight = 5f)
    private val enter = Key("⏎", KeyType.ENTER, weight = 1.5f)

    private fun bottom() = listOf(lang, ch(",", weight = 1f), space, ch(".", weight = 1f), enter)

    /** 한글 두벌식. 쉬프트: ㅃㅉㄸㄲㅆ ㅒ ㅖ */
    val korean = KeyboardLayout(
        KO,
        listOf(
            listOf(
                ch("ㅂ", "ㅃ"), ch("ㅈ", "ㅉ"), ch("ㄷ", "ㄸ"), ch("ㄱ", "ㄲ"), ch("ㅅ", "ㅆ"),
                ch("ㅛ"), ch("ㅕ"), ch("ㅑ"), ch("ㅐ", "ㅒ"), ch("ㅔ", "ㅖ"),
            ),
            listOf(
                spacer(0.5f),
                ch("ㅁ"), ch("ㄴ"), ch("ㅇ"), ch("ㄹ"), ch("ㅎ"),
                ch("ㅗ"), ch("ㅓ"), ch("ㅏ"), ch("ㅣ"),
                spacer(0.5f),
            ),
            listOf(
                shift,
                ch("ㅋ"), ch("ㅌ"), ch("ㅊ"), ch("ㅍ"), ch("ㅠ"), ch("ㅜ"), ch("ㅡ"),
                backspace,
            ),
            bottom(),
        ),
    )

    val english = KeyboardLayout(
        EN,
        listOf(
            "qwertyuiop".map { ch(it.toString(), it.uppercase()) },
            listOf(spacer(0.5f)) + "asdfghjkl".map { ch(it.toString(), it.uppercase()) } + spacer(0.5f),
            listOf(shift) + "zxcvbnm".map { ch(it.toString(), it.uppercase()) } + backspace,
            bottom(),
        ),
    )

    val all = listOf(korean, english)
    fun byId(id: String) = all.first { it.id == id }
}
