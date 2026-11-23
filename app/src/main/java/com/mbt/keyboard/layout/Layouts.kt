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
     * 꾹 누르기 기호 묶음 (줄별로 8개·8개·6개 + 두벌식·영어용 보조 4개). 어느 줄에 어느 묶음이 가는지는 [ROW1_GROUPS] 등 참고.
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

    /**
     * 줄별 기호 묶음. **같은 줄·같은 순서(왼쪽부터 몇 번째 글자 키)에는 어느 자판이든 같은 기호**가 온다.
     * 단모음(8·8·6키)을 기준으로 정하고, 두벌식·영어(10·9·7키)는 그 줄의 앞쪽 키에 같은 기호를 놓은 뒤
     * 남는 맨 오른쪽 키에만 보조 기호([EXTRA_GROUPS])를 놓는다.
     */
    private val ROW1_GROUPS = SYMBOL_GROUPS.subList(0, 8)
    private val ROW2_GROUPS = SYMBOL_GROUPS.subList(8, 16)
    private val ROW3_GROUPS = SYMBOL_GROUPS.subList(16, 22)
    private val EXTRA_GROUPS = SYMBOL_GROUPS.subList(22, 26)

    private val wideRow1Groups = ROW1_GROUPS + EXTRA_GROUPS.subList(0, 2)     // 10키: 뒤 2키가 보조 기호
    private val wideRow2Groups = ROW2_GROUPS + EXTRA_GROUPS.subList(2, 3)     // 9키: 뒤 1키가 보조 기호
    private val wideRow3Groups = ROW3_GROUPS + EXTRA_GROUPS.subList(3, 4)     // 7키: 뒤 1키가 보조 기호

    private fun spacer(w: Float) = Key(label = "", type = KeyType.SPACER, weight = w)

    private val shift = Key("⇧", KeyType.SHIFT, weight = 1.5f)

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
     * - 지우기는 1.25칸 (예전 1.5칸과 일반 키 1칸의 중간), 줄의 오른쪽 끝
     * - 쉬프트는 1칸보다 작게 0.75칸
     * - 그 사이 글자 6개는 남는 폭을 똑같이 나눠 가진다. 줄 전체 8칸을 유지하므로 위 줄들과 오른쪽 끝이 맞고,
     *   이 폭 구성에서는 글자 키가 정확히 1칸이라 위 줄 키들과 폭도 같다
     * 쉬프트: 쌍자음·ㅑㅕㅛㅠ·ㅒㅖ
     */
    private const val SHIFT_NARROW = 0.75f
    private const val BACKSPACE_WEIGHT = 1.25f
    private const val SINGLE_ROW_TOTAL = 8f
    private val shiftNarrow = Key("⇧", KeyType.SHIFT, weight = SHIFT_NARROW)
    private val backspaceMid = Key("⌫", KeyType.BACKSPACE, weight = BACKSPACE_WEIGHT)
    private val singleRowLetterWeight = (SINGLE_ROW_TOTAL - SHIFT_NARROW - BACKSPACE_WEIGHT) / 6f

    /**
     * 두벌식·영어의 마지막 글자 줄: 쉬프트 1.5 + 글자 7개 + 지우기 1.25 = 10칸 (다른 줄과 같은 전체 폭).
     * 지우기를 1.25칸으로 줄이고 남는 폭은 글자 7개가 똑같이 나눠 가진다.
     */
    private val wideRowLetterWeight = (10f - 1.5f - BACKSPACE_WEIGHT) / 7f

    /**
     * 글자 줄 생성.
     * @param shifted 쉬프트 상태 글자 (없으면 null)
     * @param groups 키마다 꾹 누르기 기호 묶음 (글자 개수와 같아야 한다)
     */
    private fun letters(chars: List<String>, shifted: List<String?>, groups: List<List<String>>, weight: Float = 1f): List<Key> {
        require(groups.size == chars.size) { "기호 묶음 ${groups.size}개 != 글자 ${chars.size}개" }
        return chars.mapIndexed { i, c -> Key(label = c, shifted = shifted[i], hints = groups[i], weight = weight) }
    }

    /** 한글 두벌식. 쉬프트: ㅃㅉㄸㄲㅆ ㅒ ㅖ */
    val korean = KeyboardLayout(
        KO, "한글",
        listOf(
            numberRow,
            letters(
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
                listOf("ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", null, null, null, "ㅒ", "ㅖ"),
                wideRow1Groups,
            ),
            listOf(spacer(0.5f)) +
                letters(
                    listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"),
                    List(9) { null },
                    wideRow2Groups,
                ) + spacer(0.5f),
            listOf(shift) +
                letters(
                    listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ"),
                    List(7) { null },
                    wideRow3Groups,
                    weight = wideRowLetterWeight,
                ) + backspaceMid,
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
                ROW1_GROUPS,
            ),
            letters(
                listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"),
                listOf(null, null, null, null, null, "ㅕ", "ㅑ", null),
                ROW2_GROUPS,
            ),
            // 쉬프트는 이 줄의 맨 왼쪽 (예전 하단 왼쪽에서 한 줄 위로)
            listOf(shiftNarrow) +
                letters(
                    listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"),
                    listOf(null, null, null, null, "ㅠ", null),
                    ROW3_GROUPS,
                    weight = singleRowLetterWeight,
                ) + backspaceMid,
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
            letters("qwertyuiop".map { it.toString() }, "qwertyuiop".map { it.uppercase() }, wideRow1Groups),
            listOf(spacer(0.5f)) +
                letters("asdfghjkl".map { it.toString() }, "asdfghjkl".map { it.uppercase() }, wideRow2Groups) +
                spacer(0.5f),
            listOf(shift) +
                letters(
                    "zxcvbnm".map { it.toString() }, "zxcvbnm".map { it.uppercase() }, wideRow3Groups,
                    weight = wideRowLetterWeight,
                ) + backspaceMid,
            bottom(),
        ),
        rowHeights,
        // 마지막 글자 줄: ⇧zxcvb | nm⌫  (b는 왼손 키)
        splitAt = listOf(null, null, null, 6, null),
    )

    /** 숫자 입력칸(숫자·날짜) 전용 키패드 */
    const val NUM = "num"
    /** 전화번호 입력칸 전용 키패드 */
    const val PHONE = "phone"

    private val padRowHeights = List(4) { 1f }

    /** 숫자 키패드: 1~9, 0, 소수점·쉼표·부호, 지우기, 엔터, 글자 자판 복귀(ABC) */
    val numberPad = KeyboardLayout(
        NUM, "숫자",
        listOf(
            listOf(Key("1"), Key("2"), Key("3"), Key("⌫", KeyType.BACKSPACE)),
            listOf(Key("4"), Key("5"), Key("6"), Key(".", hints = listOf("/", ":"))),
            listOf(Key("7"), Key("8"), Key("9"), Key("-")),
            listOf(Key("ABC", KeyType.LETTERS), Key("0", hints = listOf("+")), Key(","), Key("⏎", KeyType.ENTER)),
        ),
        padRowHeights,
        splittable = false,
    )

    /** 전화번호 키패드: 1~9, 0(꾹 누르면 +), * # -, 지우기, 엔터, 글자 자판 복귀 */
    val phonePad = KeyboardLayout(
        PHONE, "전화번호",
        listOf(
            listOf(Key("1"), Key("2"), Key("3"), Key("⌫", KeyType.BACKSPACE)),
            listOf(Key("4"), Key("5"), Key("6"), Key("-", hints = listOf("(", ")"))),
            listOf(Key("7"), Key("8"), Key("9"), Key("*")),
            listOf(Key("ABC", KeyType.LETTERS), Key("0", hints = listOf("+")), Key("#"), Key("⏎", KeyType.ENTER)),
        ),
        padRowHeights,
        splittable = false,
    )

    /** 글자 자판 (한글 단모음·두벌식·영어). 한/영 전환 대상이 되는 것들 */
    val all = listOf(koreanSingle, korean, english)

    /** 입력칸 종류에 따라 자동으로 뜨는 키패드 */
    val pads = listOf(numberPad, phonePad)

    fun byId(id: String) = (all + pads).first { it.id == id }

    fun isPad(id: String) = id == NUM || id == PHONE
}
