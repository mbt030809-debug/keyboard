package com.mbt.keyboard.layout

enum class KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, LANG, SPACER }

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
) {
    fun outputFor(shift: Boolean) = if (shift && shifted != null) shifted else output
    fun labelFor(shift: Boolean) = if (shift && shifted != null) shifted else label
}

data class KeyboardLayout(val id: String, val rows: List<List<Key>>)
