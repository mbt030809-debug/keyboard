package com.mbt.keyboard.phrase

import android.content.Context

/** 상용문구: 엔터 길게 누르기로 입력. 설정 화면에서 한 줄에 하나씩 편집. */
object Phrases {
    const val MAX = 4

    val DEFAULT = listOf(
        "안녕하세요",
        "감사합니다",
        "네, 알겠습니다",
        "지금 통화 가능하세요?",
    )

    private const val PREFS = "mbt_keyboard"
    private const val KEY = "phrases"

    /** 줄바꿈으로 구분된 원문 → 문구 목록 (공백 줄 제거, 앞뒤 공백 제거, 최대 [MAX]개) */
    fun parse(raw: String): List<String> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return if (raw == null) DEFAULT else parse(raw)
    }

    /** 설정 화면 편집창에 보여줄 원문 */
    fun loadRaw(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: DEFAULT.joinToString("\n")

    fun saveRaw(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }
}
