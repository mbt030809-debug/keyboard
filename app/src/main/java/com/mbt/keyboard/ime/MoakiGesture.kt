package com.mbt.keyboard.ime

/**
 * 모아키 제스처 해석 (안드로이드 비의존). 자음을 누른 채 긋는 동안 방향이 크게 꺾일 때마다 한 "획"이 끝난다.
 * 획의 방향은 6가지 글자로 적는다: R(→) L(←) U(↑) D(↓), I(위쪽 대각선 ↖·↗ = ㅣ), E(아래쪽 대각선 ↙·↘ = ㅡ).
 *
 * 표는 오픈소스 iOS 모아키(vkehfdl1/ios-moaki)의 제스처 표와 삼성 모아키 사용 가이드를 따랐다.
 */
object MoakiGesture {

    private val TABLE: Map<String, Char> = mapOf(
        // 한 획: 기본 모음
        "R" to 'ㅏ', "L" to 'ㅓ', "U" to 'ㅗ', "D" to 'ㅜ', "I" to 'ㅣ', "E" to 'ㅡ',
        // 왕복: 갔다가 돌아와 다시 같은 방향
        "RLR" to 'ㅑ', "LRL" to 'ㅕ', "UDU" to 'ㅛ', "DUD" to 'ㅠ',
        // ㅐ ㅔ ㅒ ㅖ
        "RL" to 'ㅐ', "LR" to 'ㅔ', "RLRL" to 'ㅒ', "LRLR" to 'ㅖ',
        // 겹모음
        "UR" to 'ㅘ', "URL" to 'ㅙ', "DL" to 'ㅝ', "DLR" to 'ㅞ', "UD" to 'ㅚ', "DU" to 'ㅟ',
        "EI" to 'ㅢ', "EU" to 'ㅢ',
    )

    /** 한 획만 길게 밀었을 때의 ㅑㅕㅛㅠ (예전 방식, 왕복과 함께 쓸 수 있다) */
    private val LONG: Map<Char, Char> = mapOf('ㅏ' to 'ㅑ', 'ㅓ' to 'ㅕ', 'ㅗ' to 'ㅛ', 'ㅜ' to 'ㅠ')

    /** 각도(화면 기준, 아래가 +90°) → 획 글자. 위쪽 대각선=ㅣ(I), 아래쪽 대각선=ㅡ(E), 나머지는 45° 칸의 가운데 */
    fun token(deg: Float): Char = when {
        deg in -157.5f..-112.5f || deg in -67.5f..-22.5f -> 'I'
        deg in 22.5f..67.5f || deg in 112.5f..157.5f -> 'E'
        kotlin.math.abs(deg) <= 22.5f -> 'R'
        deg in 67.5f..112.5f -> 'D'
        kotlin.math.abs(deg) >= 157.5f -> 'L'
        else -> 'U'
    }

    /**
     * 획 순서 → 모음. 표에 없으면 가장 긴 앞부분으로 해석한다 (예: "RU"는 "R" → ㅏ). 해석할 수 없으면 null.
     * @param longSingle 한 획을 길게 밀었는가 (ㅏ→ㅑ 등)
     */
    fun decode(strokes: String, longSingle: Boolean = false): Char? {
        if (strokes.isEmpty()) return null
        TABLE[strokes]?.let { base ->
            return if (strokes.length == 1 && longSingle) LONG[base] ?: base else base
        }
        for (n in strokes.length - 1 downTo 1) {
            val base = TABLE[strokes.substring(0, n)] ?: continue
            return if (n == 1 && longSingle) LONG[base] ?: base else base
        }
        return null
    }
}
