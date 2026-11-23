package com.mbt.keyboard.ime

import android.content.Context
import android.content.res.Configuration

/** 색·크기 묶음. 사용자가 고른 테마는 [KeyboardThemes] 참고. */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val keyPressed: Int,
    val text: Int,
    val accent: Int,
    val onAccent: Int,
    val keyHeightDp: Float = 52f,
    val textScale: Float = 1f,
    /** 그라데이션 배경의 가운데·끝 색 (0이면 없음. 둘 다 0이면 [background] 한 색). 시작 색은 [background] */
    val backgroundMid: Int = 0,
    val backgroundEnd: Int = 0,
    /** 키 테두리 색 (0이면 없음). 반투명 흰색이면 유리 느낌 */
    val keyBorder: Int = 0,
    /** 키 아래에 옅은 그림자를 깐다 */
    val keyShadow: Boolean = false,
) {
    val hasGradient: Boolean get() = backgroundEnd != 0

    /** 배경 색들 (시작 → 가운데 → 끝). 그라데이션이 아니면 한 색 */
    val backgroundStops: IntArray
        get() = when {
            !hasGradient -> intArrayOf(background)
            backgroundMid != 0 -> intArrayOf(background, backgroundMid, backgroundEnd)
            else -> intArrayOf(background, backgroundEnd)
        }

    /** [w]×[h] 크기 배경에 깔 그라데이션 (그라데이션이 아니면 null) */
    fun backgroundShader(w: Float, h: Float): android.graphics.Shader? =
        if (!hasGradient) null
        else android.graphics.LinearGradient(0f, 0f, w * 0.45f, h, backgroundStops, null, android.graphics.Shader.TileMode.CLAMP)

    /** 패널(이모지·상용문구)용 배경 */
    fun backgroundDrawable(): android.graphics.drawable.Drawable =
        if (!hasGradient) android.graphics.drawable.ColorDrawable(background)
        else android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, backgroundStops)

    /** 반투명 색 [c]를 배경 평균색 위에 올린 불투명 색 (말풍선처럼 뒤가 비치면 안 되는 곳용) */
    fun solid(c: Int): Int {
        val stops = backgroundStops
        var r = 0; var g = 0; var b = 0
        for (s in stops) { r += (s shr 16) and 0xFF; g += (s shr 8) and 0xFF; b += s and 0xFF }
        val bg = intArrayOf(r / stops.size, g / stops.size, b / stops.size)
        val a = ((c ushr 24) and 0xFF) / 255f
        fun mix(fg: Int, back: Int) = (fg * a + back * (1 - a)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix((c shr 16) and 0xFF, bg[0]) shl 16) or (mix((c shr 8) and 0xFF, bg[1]) shl 8) or mix(c and 0xFF, bg[2])
    }

    companion object {
        val LIGHT = KeyboardTheme(
            background = 0xFFD9DCE1.toInt(), key = 0xFFFFFFFF.toInt(), specialKey = 0xFFB9BEC7.toInt(),
            keyPressed = 0xFF9AA3B5.toInt(), text = 0xFF1B1B1F.toInt(),
            accent = 0xFF3F51B5.toInt(), onAccent = 0xFFFFFFFF.toInt(),
        )
        val DARK = KeyboardTheme(
            background = 0xFF1E1F24.toInt(), key = 0xFF3A3C44.toInt(), specialKey = 0xFF2A2C33.toInt(),
            keyPressed = 0xFF5A5E6B.toInt(), text = 0xFFF2F2F5.toInt(),
            accent = 0xFF8C9EFF.toInt(), onAccent = 0xFF0A1A6B.toInt(),
        )

        /** 설정에서 고른 테마. 고르지 않았으면(또는 모르는 값이면) 시스템 모드를 따른다 */
        fun current(context: Context): KeyboardTheme =
            KeyboardThemes.resolve(com.mbt.keyboard.settings.Prefs.themeId(context)) ?: system(context)

        fun system(context: Context): KeyboardTheme {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
        }
    }
}
