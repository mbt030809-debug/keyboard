package com.mbt.keyboard.ime

/**
 * 모아키 제스처 해석 (안드로이드 비의존). 자음을 누른 채 긋는 동안 방향이 꺾일 때마다 한 "획"이 끝난다.
 * 획의 방향은 글자로 적는다: R(→) L(←) U(↑) D(↓), 대각선 I(↖) J(↗) P(↘) Q(↙).
 * 대각선 획은 ㅣ(I·J) 또는 ㅡ(P·Q)다.
 *
 * - → ← ↑ ↓ 로 간 뒤 대각선으로 꺾으면 앞 방향은 버리고 그 대각선 모음(ㅣ/ㅡ)이 입력된다.
 * - 겹모음·왕복은 → ← ↑ ↓ 만으로 잇는다: ㅚ(↑↓) ㅟ(↓↑) ㅘ(↑→) ㅝ(↓←) ㅐ(→←) ㅔ(←→) …
 *
 * 표는 오픈소스 iOS 모아키(vkehfdl1/ios-moaki)의 제스처 표와 삼성 모아키 사용 가이드를 바탕으로 했다.
 */
object MoakiGesture {

    private val TABLE: Map<String, Char> = mapOf(
        // 한 획: 기본 모음 (대각선은 ㅣ ㅡ)
        "R" to 'ㅏ', "L" to 'ㅓ', "U" to 'ㅗ', "D" to 'ㅜ',
        "I" to 'ㅣ', "J" to 'ㅣ', "P" to 'ㅡ', "Q" to 'ㅡ',
        // 왕복: 갔다가 돌아와 다시 같은 방향
        "RLR" to 'ㅑ', "LRL" to 'ㅕ', "UDU" to 'ㅛ', "DUD" to 'ㅠ',
        // ㅐ ㅔ ㅒ ㅖ (되돌아오기)
        "RL" to 'ㅐ', "LR" to 'ㅔ', "RLRL" to 'ㅒ', "LRLR" to 'ㅖ',
        // 겹모음
        "UR" to 'ㅘ', "URL" to 'ㅙ', "DL" to 'ㅝ', "DLR" to 'ㅞ', "UD" to 'ㅚ', "DU" to 'ㅟ',
        "PI" to 'ㅢ', "PJ" to 'ㅢ', "QI" to 'ㅢ', "QJ" to 'ㅢ', "PU" to 'ㅢ', "QU" to 'ㅢ',   // ㅡ 다음 ㅣ
    )

    /** 각도(화면 기준, 아래가 +90°) → 획 글자. 대각선은 I(↖) J(↗) P(↘) Q(↙), 나머지는 45° 칸의 가운데 */
    fun token(deg: Float): Char = when {
        deg in -157.5f..-112.5f -> 'I'
        deg in -67.5f..-22.5f -> 'J'
        deg in 22.5f..67.5f -> 'P'
        deg in 112.5f..157.5f -> 'Q'
        kotlin.math.abs(deg) <= 22.5f -> 'R'
        deg in 67.5f..112.5f -> 'D'
        kotlin.math.abs(deg) >= 157.5f -> 'L'
        else -> 'U'
    }

    /**
     * 획 순서 → 모음. 표에 없으면 가장 긴 앞부분으로 해석한다 (예: "RU"는 "R" → ㅏ). 해석할 수 없으면 null.
     */
    fun decode(strokes: String): Char? {
        if (strokes.isEmpty()) return null
        // → ← ↑ ↓ 로 시작했어도 이어서 대각선이 나오면 그 대각선 모음(ㅣ/ㅡ)으로 읽는다
        if (strokes[0] in "RLUD") strokes.firstOrNull { it in "IJPQ" }?.let { return TABLE.getValue(it.toString()) }
        TABLE[strokes]?.let { return it }
        for (n in strokes.length - 1 downTo 1) TABLE[strokes.substring(0, n)]?.let { return it }
        return null
    }
}
