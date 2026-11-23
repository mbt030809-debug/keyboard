package com.mbt.keyboard.layout

/** 자판 정의 (데이터 기반). 새 자판은 여기에 행 목록만 추가하면 된다. */
object Layouts {
    /** 한글 두벌식 */
    const val KO = "ko"
    /** 한글 단모음 (자음 + 단모음 8개, 연타로 쌍자음·ㅑㅕㅛㅠ) */
    const val KOS = "kos"
    const val EN = "en"

    /** 글자 줄이 시작되는 행 번호 (맨 위 한 줄은 [전체 복사 붙임 1~0]) */
    const val LETTER_ROW_START = 1

    /**
     * 쌍으로 입력되는 기호. 입력 후 커서가 두 글자 사이에 놓인다. (예: "()" → (|))
     */
    val PAIRS = setOf("()", "[]", "{}", "<>", "\"\"", "''")

    /**
     * 꾹 누르기 기호 묶음. 글자 키에 읽는 순서(왼쪽 위 → 오른쪽 아래)대로 배정한다.
     * 각 묶음의 첫 항목이 기본값(꾹 누르고 그대로 떼면 입력), 나머지는 밀어서 선택.
     * 자주 쓰는 것을 앞쪽 키에 두었고, 괄호·따옴표는 쌍이 기본이다.
     * 숫자는 맨 윗줄에 있으므로 제외.
     *
     * 뒤쪽 키 배치(단모음 기준): `ㅔ`=→←↑↓, `ㅣ`=하트, `ㅊ`=@, `ㅍ`=_ - —, `ㅜ`=?, `ㅡ`=!
     * (☆ 키를 없애고 뒤의 키들을 한 칸씩 앞으로 당긴 결과. ? 와 → 는 서로 자리를 바꿨다)
     */
    val SYMBOL_GROUPS: List<List<String>> = listOf(
        listOf("()", "(", ")"),
        listOf("[]", "[", "]"),
        listOf("{}", "{", "}"),
        listOf("<>", "<", ">"),
        listOf("\"\"", "\"", "''", "'"),
        listOf("^"),
        listOf("~"),
        listOf("→", "←", "↑", "↓"),         // ? 와 자리를 바꿈
        listOf("#"),
        listOf("&"),
        listOf("%", "°"),
        listOf("+", "="),
        listOf("*", "×"),
        listOf(":", ";"),
        listOf("/", "|", "\\"),             // | \ 는 / 의 하위 후보
        listOf("❤\uFE0F", "♡", "💕", "💖", "💙", "💔"),   // 하트 6개 (한 줄 말풍선)
        listOf("$", "₩"),
        listOf("`"),
        listOf("@"),                       // ☆ 키를 없애고 한 칸씩 당김: ● 가 있던 자리에 골뱅이
        listOf("_", "-", "—"),            // - 는 _ 의 하위 후보
        listOf("?"),
        listOf("!"),
        // 이하 4개는 두벌식·영어(글자 키 26개)에서만 사용
        listOf("€", "£", "¥"),
        listOf("±"),
        listOf("©", "®", "™"),
        listOf("¿", "¡"),
    )

    private fun spacer(w: Float) = Key(label = "", type = KeyType.SPACER, weight = w)

    private val shift = Key("⇧", KeyType.SHIFT, weight = 1.5f)
    private val backspace = Key("⌫", KeyType.BACKSPACE, weight = 1.5f)

    /** 맨 윗줄 한 줄 (13칸, 모두 같은 폭): 전체 · 복사 · 붙임 + 숫자 1~0 */
    private val numberRow = listOf(
        Key("전체", KeyType.SELECT_ALL),
        Key("복사", KeyType.COPY),
        Key("붙임", KeyType.PASTE),
    ) + "1234567890".map { Key(it.toString()) }

    /** 맨 윗줄(전체·복사·붙임·숫자)은 낮게 */
    private val rowHeights = listOf(0.8f, 1f, 1f, 1f, 1f)

    /** 녹음(음성인식)은 스페이스바 바로 오른쪽. 🎤·마침표·쉼표는 같은 폭(1칸). 폭 합계 10 */
    private fun bottom() = listOf(
        Key("😊", KeyType.EMOJI, weight = 1.3f),
        Key(",", hints = listOf("'", "\"")),
        Key("", KeyType.SPACE, output = " ", weight = 4.4f),
        Key("🎤", KeyType.VOICE, weight = 1.0f),
        Key(".", hints = listOf("…", ".com", "·")),   // · 는 . 의 하위 후보
        Key("⏎", KeyType.ENTER, weight = 1.3f),
    )

    /**
     * 단모음 자판의 쉬프트·지우기 (ㅋㅌㅊㅍㅜㅡ 줄의 양 끝).
     * - 지우기는 예전과 같은 1.5칸 폭, 줄의 오른쪽 끝
     * - 쉬프트는 1칸보다 작게 0.75칸
     * - 그 사이 글자 6개는 남는 폭을 똑같이 나눠 가진다 (줄 전체 8칸 유지 → 위 줄들과 오른쪽 끝이 맞는다)
     * 쉬프트: 쌍자음·ㅑㅕㅛㅠ·ㅒㅖ
     */
    private const val SHIFT_NARROW = 0.75f
    private const val BACKSPACE_WEIGHT = 1.5f
    private const val SINGLE_ROW_TOTAL = 8f
    private val shiftNarrow = Key("⇧", KeyType.SHIFT, weight = SHIFT_NARROW)
    private val singleRowLetterWeight = (SINGLE_ROW_TOTAL - SHIFT_NARROW - BACKSPACE_WEIGHT) / 6f

    /**
     * 글자 줄 생성. 기호 묶음은 [symbolStart]번부터 키 순서대로 배정된다.
     * @param shifted 쉬프트 상태 글자 (없으면 null)
     */
    private fun letters(chars: List<String>, shifted: List<String?>, symbolStart: Int, weight: Float = 1f) =
        chars.mapIndexed { i, c ->
            Key(label = c, shifted = shifted[i], hints = SYMBOL_GROUPS[symbolStart + i], weight = weight)
        }

    /** 한글 두벌식. 쉬프트: ㅃㅉㄸㄲㅆ ㅒ ㅖ */
    val korean = KeyboardLayout(
        KO, "한글",
        listOf(
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
        splitAt = listOf(null, null, null, 5, null),
    )

    /**
     * 한글 단모음. 자음 + 단모음(ㅏㅓㅗㅜㅡㅣㅐㅔ) 8개만 배치하고
     * 같은 키를 연달아 두 번 치거나 쉬프트를 누르면 ㄱ→ㄲ, ㅏ→ㅑ 처럼 바뀐다.
     * 꾹 누르기는 기호.
     */
    val koreanSingle = KeyboardLayout(
        KOS, "한글 단모음",
        listOf(
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
            // 쉬프트는 이 줄의 맨 왼쪽 (예전 하단 왼쪽에서 한 줄 위로)
            listOf(shiftNarrow) +
                letters(
                    listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"),
                    listOf(null, null, null, null, "ㅠ", null),
                    16,
                    weight = singleRowLetterWeight,
                ) + backspace,
            bottom(),
        ),
        rowHeights,
        hangul = true,
        doubleTap = true,
        // 나눠쓰기: 왼쪽=자음(ㅂㅈㄷㄱㅅ / ㅁㄴㅇㄹㅎ / ㅋㅌㅊㅍ), 오른쪽=모음(ㅗㅐㅔ / ㅓㅏㅣ / ㅜㅡ⌫)
        // 3번째 줄은 맨 앞의 쉬프트가 왼쪽 묶음에 들어가므로 5개 (⇧ㅋㅌㅊㅍ | ㅜㅡ⌫)
        // 맨 윗줄은 전체·복사·붙임 + 1~5 | 6~0
        splitAt = listOf(8, 5, 5, 5, null),
    )

    val english = KeyboardLayout(
        EN, "English",
        listOf(
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
        splitAt = listOf(null, null, null, 6, null),
    )

    val all = listOf(koreanSingle, korean, english)
    fun byId(id: String) = all.first { it.id == id }
}
