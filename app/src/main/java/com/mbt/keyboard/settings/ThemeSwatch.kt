package com.mbt.keyboard.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.mbt.keyboard.ime.KeyboardTheme

/** 테마 미리보기: 배경 위에 키 몇 개와 강조색 점을 작게 그린 그림. [selected]면 강조색 테두리 */
class ThemeSwatch(context: Context, val theme: KeyboardTheme) : View(context) {
    var chosen = false
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 10 * d
        paint.style = Paint.Style.FILL
        paint.color = theme.background
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)

        // 키 2줄: 일반 키 4개 + (둘째 줄) 기능키 + 강조색 엔터
        val pad = 8 * d
        val gap = 4 * d
        val rows = 2
        val keyH = (h - pad * 2 - gap * (rows - 1)) / rows
        for (row in 0 until rows) {
            val top = pad + row * (keyH + gap)
            val n = 5
            val keyW = (w - pad * 2 - gap * (n - 1)) / n
            for (i in 0 until n) {
                paint.color = when {
                    row == 1 && i == 0 -> theme.specialKey
                    row == 1 && i == n - 1 -> theme.accent
                    row == 0 && i == 2 -> theme.keyPressed
                    else -> theme.key
                }
                val left = pad + i * (keyW + gap)
                canvas.drawRoundRect(RectF(left, top, left + keyW, top + keyH), 4 * d, 4 * d, paint)
            }
        }
        // 글자색 표시: 첫 키 가운데 작은 점
        paint.color = theme.text
        canvas.drawCircle(pad + (w - pad * 2 - gap * 4) / 10f, pad + keyH / 2f, 2.5f * d, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (chosen) 3 else 1) * d
        paint.color = if (chosen) theme.accent else 0x33000000
        val inset = paint.strokeWidth / 2f
        canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r, r, paint)
    }
}
