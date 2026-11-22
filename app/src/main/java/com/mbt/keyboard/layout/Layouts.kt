package com.mbt.keyboard.layout

/** 자판 정의 (데이터 기반). 새 자판은 여기에 행 목록만 추가하면 된다. */
object Layouts {
    /** 한글 두벌식 */
    const val KO = "ko"
    /** 한글 단모음 (자음 + 단모음 8개, 연타로 쌍자음·ㅑㅕㅛㅠ) */
    const val KOS = "kos"
    const val EN = "en"

    /** 글자 줄이 시작되는 행 번호 (위 2줄은 편집·숫자 줄) */
    const val LETTER_ROW_START = 2

    /**
     * 쌍으로 입력되는 기호. 입력 후 커서가 두 글자 사이에 놓인다. (예: "()" → (|))
     */
    val PAIRS = setOf("()", "[]", "{}", "<>", "\"\"", "''")

    /**
     * 꾹 누르기 기호 묶음. 글자 키에 읽는 순서(왼쪽 위 → 오른쪽 아래)대로 배정한다.
     * 각 묶음의 첫 항목이 기본값(꾹 누르고 그대로 떼면 입력), 나머지는 밀어서 선택.
     * 자주 쓰는 것을 앞쪽 키에 두었고, 괄호·따옴표는 쌍이 기본이다.
     * 숫자·`- @ ? ! ❤`는 상단 줄에 이미 있으므로 제외.
     */
    val SYMBOL_GROUPS: List<List<String>> = listOf(
        listOf("()", "(", ")"),
        listOf("[]", "[", "]"),
        listOf("{}", "{", "}"),
        listOf("<>", "<", ">"),
        listOf("\"\"", "\"", "''", "'"),
        listOf("^"),
        listOf("~", "…"),
        listOf("※", "·"),
        listOf("#"),
        listOf("&"),
        listOf("%", "°"),
        listOf("+", "="),
        listOf("*", "×"),
        listOf(":", ";"),
        listOf("/"),
        listOf("|", "\\"),
        listOf("$", "₩"),
        listOf("`"),
        listOf("★", "☆", "♡"),
        listOf("○", "●"),
        listOf("_", "—"),
        listOf("→", "←", "↑", "↓"),
        // 이하 4개는 두벌식·영어(글자 키 26개)에서만 사용
        listOf("€", "£", "¥"),
        listOf("±"),
        listOf("©", "®", "™"),
        listOf("¿", "¡"),
    )

    private fun spacer(w: Float) = Key(label = "", type = KeyType.SPACER, weight = w)

    private val shift = Key("⇧", KeyType.SHIFT, weight = 1.5f)
    private val backspace = Key("⌫", KeyType.BACKSPACE, weight = 1.5f)
    /** 이모티콘·이모지 패널 (한/영 전환은 스페이스바 스와이프) */
    private val emoji = Key("😊", KeyType.EMOJI, weight = 1.5f)
    private val space = Key("", KeyType.SPACE, output = " ", weight = 5f)
    private val enter = Key("⏎", KeyType.ENTER, weight = 1.5f)

    /** 상단 줄: 음성인식, 편집(전체·복사·붙임), 맞춤법 검사, 자주 쓰는 기호 */
    private val toolbarRow = listOf(
        Key("🎤", KeyType.VOICE),
        Key("전체", KeyType.SELECT_ALL),
        Key("복사", KeyType.COPY),
        Key("붙임", KeyType.PASTE),
        Key("맞춤법", KeyType.SPELL),
        Key("-"),
        Key("❤", output = "❤\uFE0F"),
        Key("@"),
        Key("?"),
        Key("!"),
    )

    /** 숫자 줄 */
    private val numberRow = "1234567890".map { Key(it.toString()) }

    /** 상단 두 줄은 낮게 */
    private val rowHeights = listOf(0.7f, 0.75f, 1f, 1f, 1f, 1f)

    /** 좌측하단 쉬프트 (단모음 자판용: 쉬프트로 쌍자음·ㅑㅕㅛㅠ·ㅒㅖ) */
    private val bottomShift = Key("⇧", KeyType.SHIFT, weight = 1.2f)

    private fun bottom(withShift: Boolean = false) =
        if (withShift) {
            listOf(
                bottomShift,
                Key("😊", KeyType.EMOJI, weight = 1.2f),
                Key(",", hints = listOf("'", "\"")),
                Key("", KeyType.SPACE, output = " ", weight = 4.1f),
                Key(".", hints = listOf("…", ".com")),
                enter,
            )
        } else {
            listOf(
                emoji,
                Key(",", hints = listOf("'", "\"")),
                space,
                Key(".", hints = listOf("…", ".com")),
                enter,
            )
        }

    /**
     * 글자 줄 생성. 기호 묶음은 [symbolStart]번부터 키 순서대로 배정된다.
     * @param shifted 쉬프트 상태 글자 (없으면 null)
     */
    private fun letters(chars: List<String>, shifted: List<String?>, symbolStart: Int) =
        chars.mapIndexed { i, c ->
            Key(label = c, shifted = shifted[i], hints = SYMBOL_GROUPS[symbolStart + i])
        }

    /** 한글 두벌식. 쉬프트: ㅃㅉㄸㄲㅆ ㅒ ㅖ */
    val korean = KeyboardLayout(
        KO, "한글",
        listOf(
            toolbarRow,
            numberRow,
            letters(
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
                listOf("ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", null, null, null, "ㅒ", "ㅖ"),
                0,
            ),
            listOf(spacer(0.5f)) +
                letters(
                    listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"),
                    List(9) { null },
                    10,
                ) + spacer(0.5f),
            listOf(shift) +
                letters(
                    listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ"),
                    List(7) { null },
                    19,
                ) + backspace,
            bottom(),
        ),
        rowHeights,
        hangul = true,
        // 나눠쓰기: 왼쪽=자음, 오른쪽=모음. 마지막 글자 줄은 ⇧ㅋㅌㅊㅍ | ㅠㅜㅡ⌫ (ㅠ는 모음이라 오른쪽)
        splitAt = listOf(null, null, null, null, 5, null),
    )

    /**
     * 한글 단모음. 자음 + 단모음(ㅏㅓㅗㅜㅡㅣㅐㅔ) 8개만 배치하고
     * 같은 키를 연달아 두 번 치거나 쉬프트를 누르면 ㄱ→ㄲ, ㅏ→ㅑ 처럼 바뀐다.
     * 꾹 누르기는 기호.
     */
    val koreanSingle = KeyboardLayout(
        KOS, "한글 단모음",
        listOf(
            toolbarRow,
            numberRow,
            // 쉬프트: 쌍자음(ㅃㅉㄸㄲㅆ), 쌍모음(ㅛ ㅒ ㅖ ㅕ ㅑ ㅠ)
            letters(
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"),
                listOf("ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", "ㅛ", "ㅒ", "ㅖ"),
                0,
            ),
            letters(
                listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"),
                listOf(null, null, null, null, null, "ㅕ", "ㅑ", null),
                8,
            ),
            listOf(spacer(0.5f)) +
                letters(
                    listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"),
                    listOf(null, null, null, null, "ㅠ", null),
                    16,
                ) + backspace,
            bottom(withShift = true),
        ),
        rowHeights,
        hangul = true,
        doubleTap = true,
        // 나눠쓰기: 왼쪽=자음(ㅂㅈㄷㄱㅅ / ㅁㄴㅇㄹㅎ / ㅋㅌㅊㅍ), 오른쪽=모음(ㅗㅐㅔ / ㅓㅏㅣ / ㅜㅡ⌫)
        // 3번째 줄은 맨 앞의 여백 칸이 왼쪽 묶음에 들어가므로 5개
        splitAt = listOf(null, null, 5, 5, 5, null),
    )

    val english = KeyboardLayout(
        EN, "English",
        listOf(
            toolbarRow,
            numberRow,
            letters("qwertyuiop".map { it.toString() }, "qwertyuiop".map { it.uppercase() }, 0),
            listOf(spacer(0.5f)) +
                letters("asdfghjkl".map { it.toString() }, "asdfghjkl".map { it.uppercase() }, 10) +
                spacer(0.5f),
            listOf(shift) +
                letters("zxcvbnm".map { it.toString() }, "zxcvbnm".map { it.uppercase() }, 19) + backspace,
            bottom(),
        ),
        rowHeights,
        // 마지막 글자 줄: ⇧zxcvb | nm⌫  (b는 왼손 키)
        splitAt = listOf(null, null, null, null, 6, null),
    )

    val all = listOf(koreanSingle, korean, english)
    fun byId(id: String) = all.first { it.id == id }
}
