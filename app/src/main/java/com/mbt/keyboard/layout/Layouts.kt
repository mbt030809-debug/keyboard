package com.mbt.keyboard.layout

/** 자판 정의 (데이터 기반). 새 자판은 여기에 행 목록만 추가하면 된다. */
object Layouts {
    /** 한글 두벌식 */
    const val KO = "ko"
    /** 한글 단모음 (자음 + 단모음 8개, 연타로 쌍자음·ㅑㅕㅛㅠ) */
    const val KOS = "kos"
    const val EN = "en"

    private fun ch(label: String, shifted: String? = null, hint: String? = null) =
        Key(label = label, shifted = shifted, hints = listOfNotNull(hint))

    private fun spacer(w: Float) = Key(label = "", type = KeyType.SPACER, weight = w)

    private val shift = Key("⇧", KeyType.SHIFT, weight = 1.5f)
    private val backspace = Key("⌫", KeyType.BACKSPACE, weight = 1.5f)
    /** 음성인식 (한/영 전환은 스페이스바 스와이프) */
    private val voice = Key("🎤", KeyType.VOICE, weight = 1.5f)
    private val space = Key("", KeyType.SPACE, output = " ", weight = 5f)
    private val enter = Key("⏎", KeyType.ENTER, weight = 1.5f)

    /** 상단 편집·기호 줄 (참고 키보드와 같은 구성) */
    private val toolbarRow = listOf(
        Key("◁", KeyType.LEFT),
        Key("전체", KeyType.SELECT_ALL),
        Key("복사", KeyType.COPY),
        Key("붙임", KeyType.PASTE),
        Key("-"),
        Key("❤", output = "❤\uFE0F"),
        Key("@"),
        Key("?"),
        Key("!"),
        Key("▷", KeyType.RIGHT),
    )

    /** 숫자 줄 */
    private val numberRow = "1234567890".map { Key(it.toString()) }

    /** 상단 두 줄은 낮게 */
    private val rowHeights = listOf(0.7f, 0.75f, 1f, 1f, 1f, 1f)

    /** 글자 줄이 시작되는 행 번호 (위 2줄은 편집·숫자 줄) */
    const val LETTER_ROW_START = 2

    /** 꾹 누르기 후보 (숫자는 숫자 줄이 있으므로 기호만). 한글·영문 자판 위치가 같아 공용. */
    private val row1Hints = listOf("(", ")", "[", "]", "{", "}", "<", ">", "\"", "'")
    private val row2Hints = listOf("#", "$", "%", "&", "*", "+", "=", "/", "\\")
    private val row3Hints = listOf("~", "`", "^", "_", "|", ";", ":")

    private fun bottom() = listOf(
        voice,
        Key(",", hints = listOf("\"", "'", ";", ":")),
        space,
        Key(".", hints = listOf("…", "·", "`")),
        enter,
    )

    /** 단모음 키: 꾹 누르기 후보를 직접 지정 */
    private fun sk(label: String, vararg hints: String) = Key(label = label, hints = hints.toList())

    private fun row(chars: List<String>, shifted: List<String?>, hints: List<String>) =
        chars.mapIndexed { i, c -> ch(c, shifted[i], hints[i]) }

    /** 한글 두벌식. 쉬프트: ㅃㅉㄸㄲㅆ ㅒ ㅖ */
    val korean = KeyboardLayout(
        KO, "한글",
        listOf(
            toolbarRow,
            numberRow,
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
        rowHeights,
        hangul = true,
    )

    /**
     * 한글 단모음. 자음 + 단모음(ㅏㅓㅗㅜㅡㅣㅐㅔ) 8개만 배치하고
     * 같은 키를 연달아 두 번 치면 ㄱ→ㄲ, ㅏ→ㅑ 처럼 변환된다.
     * 쌍자음은 꾹 누르기로도 입력 가능 (키 오른쪽 위 작은 글자).
     */
    val koreanSingle = KeyboardLayout(
        KOS, "한글 단모음",
        listOf(
            toolbarRow,
            numberRow,
            listOf(
                sk("ㅂ", "ㅃ", "("), sk("ㅈ", "ㅉ", ")"), sk("ㄷ", "ㄸ", "["), sk("ㄱ", "ㄲ", "]"), sk("ㅅ", "ㅆ", "{"),
                sk("ㅗ", "%"), sk("ㅐ", "~"), sk("ㅔ", "*"),
            ),
            listOf(
                sk("ㅁ", "$"), sk("ㄴ", "&"), sk("ㅇ", "+"), sk("ㄹ", "="), sk("ㅎ", "}"),
                sk("ㅓ", "#"), sk("ㅏ", "!"), sk("ㅣ", "^"),
            ),
            listOf(spacer(0.5f)) +
                listOf(
                    sk("ㅋ", "<"), sk("ㅌ", ">"), sk("ㅊ", "/"), sk("ㅍ", "\\"), sk("ㅜ", "|"), sk("ㅡ", "_"),
                ) + backspace,
            bottom(),
        ),
        rowHeights,
        hangul = true,
        doubleTap = true,
    )

    val english = KeyboardLayout(
        EN, "English",
        listOf(
            toolbarRow,
            numberRow,
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
        rowHeights,
    )

    val all = listOf(koreanSingle, korean, english)
    fun byId(id: String) = all.first { it.id == id }
}
