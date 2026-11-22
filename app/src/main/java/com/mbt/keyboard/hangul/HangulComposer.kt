package com.mbt.keyboard.hangul

/**
 * 두벌식 계열 한글 조합 오토마타.
 *
 * 현재 조합 중인 글자를 "입력된 자모 목록"으로 보관하고, 매번 다시 조합한다.
 * 덕분에 백스페이스가 자모 단위로 정확히 되돌아간다. (값 → 갑 → 가 → ㄱ)
 *
 * 사용: 자모 입력 시 [input], 삭제 시 [backspace], 공백·숫자 등 비자모 입력 전에 [flush].
 * 반환된 [Output.committed]는 확정할 텍스트, [Output.composing]은 조합 중인 텍스트.
 */
class HangulComposer {

    data class Output(val committed: String, val composing: String)

    private val buffer = StringBuilder()

    val composing: String get() = render(buffer.toString())
    val isComposing: Boolean get() = buffer.isNotEmpty()

    fun input(jamo: Char): Output {
        val next = buffer.toString() + jamo
        if (parse(next) != null) {
            buffer.append(jamo)
            return Output("", render(buffer.toString()))
        }
        // 받침 뒤에 모음 → 받침(겹받침이면 마지막 자음)이 다음 글자 초성으로 이동
        val cur = parse(buffer.toString())
        if (Jamo.isVowel(jamo) && cur != null && cur.jong.isNotEmpty()) {
            val moved = buffer.last()
            val committed = render(buffer.substring(0, buffer.length - 1))
            buffer.clear()
            buffer.append(moved).append(jamo)
            return Output(committed, render(buffer.toString()))
        }
        val committed = render(buffer.toString())
        buffer.clear()
        buffer.append(jamo)
        return Output(committed, render(buffer.toString()))
    }

    /** 자모 하나 지우기. 조합 중이 아니면 null (호출자가 일반 삭제 처리). */
    fun backspace(): Output? {
        if (buffer.isEmpty()) return null
        buffer.deleteCharAt(buffer.length - 1)
        return Output("", render(buffer.toString()))
    }

    /** 조합 중인 글자를 확정하고 초기화. */
    fun flush(): String {
        val s = render(buffer.toString())
        buffer.clear()
        return s
    }

    fun reset() = buffer.clear()

    // ---- 내부 ----

    private class Syllable(
        val cho: Char?,
        val jung: Char?,
        val jong: String,
    )

    /** 자모 목록이 한 글자로 조합 가능하면 분해 결과, 아니면 null. */
    private fun parse(s: String): Syllable? {
        if (s.isEmpty()) return null
        var i = 0
        var cho: Char? = null
        var jung: Char? = null
        var jong = ""

        if (Jamo.isConsonant(s[i])) { cho = s[i]; i++ }
        if (i < s.length && Jamo.isVowel(s[i])) {
            jung = s[i]; i++
            if (i < s.length && Jamo.isVowel(s[i])) {
                Jamo.JUNG_COMBO["" + jung + s[i]]?.let { jung = it; i++ }
            }
            if (cho != null && i < s.length && isJongable(s[i].toString())) {
                jong = s[i].toString(); i++
                if (i < s.length) {
                    Jamo.JONG_COMBO[jong + s[i]]?.let { jong = it.toString(); i++ }
                }
            }
        }
        return if (i == s.length) Syllable(cho, jung, jong) else null
    }

    private fun isJongable(c: String) = Jamo.JONG.contains(c)

    private fun render(s: String): String {
        val syl = parse(s) ?: return s
        val cho = syl.cho
        val jung = syl.jung
        return when {
            jung == null -> cho?.toString() ?: ""
            cho == null -> jung.toString()
            else -> {
                val code = 0xAC00 +
                    (Jamo.CHO.indexOf(cho) * 21 + Jamo.JUNG.indexOf(jung)) * 28 +
                    Jamo.JONG.indexOf(syl.jong)
                code.toChar().toString()
            }
        }
    }
}
