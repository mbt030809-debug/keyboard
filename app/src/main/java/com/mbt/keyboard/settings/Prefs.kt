package com.mbt.keyboard.settings

import android.content.Context
import com.mbt.keyboard.layout.Layouts

/** 사용자 설정 저장소 */
object Prefs {
    private const val FILE = "mbt_keyboard"
    private const val KEY_KOREAN = "korean_layout"

    /** 한/영 전환 시 사용할 한글 자판 (기본: 단모음) */
    fun koreanLayout(context: Context): String {
        val id = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_KOREAN, Layouts.KOS)
        return if (id == Layouts.KO) Layouts.KO else Layouts.KOS
    }

    fun setKoreanLayout(context: Context, id: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_KOREAN, id).apply()
    }
}
