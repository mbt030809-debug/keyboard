package com.mbt.keyboard.layout

enum class KeyType {
    CHAR, SHIFT, BACKSPACE, ENTER, SPACE, VOICE, SPACER,
    /** 맨 윗줄: 전체선택, 복사, 붙여넣기 */
    SELECT_ALL, COPY, PASTE,
    /** 이모티콘·이모지 패널 열기 */
    EMOJI,
    /** 숫자 키패드에서 글자 자판으로 돌아가기 */
    LETTERS,
}

/**
 * @param label 키캡에 표시되는 글자 (기본 상태)
 * @param output 입력되는 문자열 (기본 상태)
 * @param shiftLabel 쉬프트 상태에서 입력되는 문자열이자 키캡 표시 (없으면 기본과 동일)
 */
data class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val output: String = label,
    val shifted: String? = null,
    val weight: Float = 1f,
    /** 꾹 누르면 나오는 후보 (특수기호 등). 비어 있으면 꾹 눌러도 동작 없음 */
    val hints: List<String> = emptyList(),
) {
    fun outputFor(shift: Boolean) = if (shift && shifted != null) shifted else output
    fun labelFor(shift: Boolean) = if (shift && shifted != null) shifted else label
}

/** @param rowHeights 행별 높이 배율 (기본 1.0). 상단 줄은 낮게. */
data class KeyboardLayout(
    val id: String,
    val name: String,
    val rows: List<List<Key>>,
    val rowHeights: List<Float> = List(rows.size) { 1f },
    /** 한글 자판 (자모를 조합 엔진으로 보냄) */
    val hangul: Boolean = false,
    /** 같은 키 연타로 변환 (ㄱ→ㄲ, ㅏ→ㅑ). 단모음 자판용 */
    val doubleTap: Boolean = false,
    /** 나눠쓰기 때 줄별 왼쪽 묶음 키 개수. null이면 자동(가운데 기준). 비어 있으면 모두 자동 */
    val splitAt: List<Int?> = emptyList(),
    /** 나눠쓰기(갈라진 자판)를 적용할 수 있는가. 숫자 키패드는 4칸뿐이라 나누지 않는다 */
    val splittable: Boolean = true,
)
