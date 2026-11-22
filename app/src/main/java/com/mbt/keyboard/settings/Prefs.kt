package com.mbt.keyboard.settings

import android.content.Context
import com.mbt.keyboard.layout.Layouts

/** 사용자 설정 저장소 */
object Prefs {
    private const val FILE = "mbt_keyboard"
    private const val KEY_KOREAN = "korean_layout"
    private const val KEY_HAPTIC_ON = "haptic_enabled"
    private const val KEY_HAPTIC_STRENGTH = "haptic_strength"

    private const val KEY_SPLIT = "split_on_large_screen"

    /** 폴드를 펼친 것처럼 큰 화면에서 자판 나눠쓰기 (기본: 켬) */
    fun splitOnLargeScreen(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SPLIT, true)

    fun setSplitOnLargeScreen(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPLIT, on).apply()

    const val HAPTIC_MIN = 1
    const val HAPTIC_MAX = 10
    const val HAPTIC_DEFAULT = 5

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 키 입력 진동 사용 여부 (기본: 켬) */
    fun hapticEnabled(context: Context) = prefs(context).getBoolean(KEY_HAPTIC_ON, true)

    fun setHapticEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_HAPTIC_ON, on).apply()

    /** 진동 세기 1(약) ~ 10(강) */
    fun hapticStrength(context: Context) =
        prefs(context).getInt(KEY_HAPTIC_STRENGTH, HAPTIC_DEFAULT).coerceIn(HAPTIC_MIN, HAPTIC_MAX)

    fun setHapticStrength(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_HAPTIC_STRENGTH, v.coerceIn(HAPTIC_MIN, HAPTIC_MAX)).apply()

    /** 한/영 전환 시 사용할 한글 자판 (기본: 단모음) */
    fun koreanLayout(context: Context): String {
        val id = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_KOREAN, Layouts.KOS)
        return if (id == Layouts.KO) Layouts.KO else Layouts.KOS
    }

    fun setKoreanLayout(context: Context, id: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_KOREAN, id).apply()
    }
}
