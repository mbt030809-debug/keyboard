package com.mbt.keyboard.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.mbt.keyboard.ime.KeyboardTheme

/** 테마 미리보기: 배경 위에 문자키 2줄(각 4개) + 하단(쉬프트 · 스페이스바 · 엔터) 색을 모두 보여준다 */
class ThemeSwatch(context: Context, val theme: KeyboardTheme) : View(context) {
    var chosen = false
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    private fun drawKey(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, cr: Float) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(RectF(l, t, r, b), cr, cr, paint)
        if (theme.keyBorder != 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * d
            paint.color = theme.keyBorder
            canvas.drawRoundRect(RectF(l + 0.4f * d, t + 0.4f * d, r - 0.4f * d, b - 0.4f * d), cr, cr, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 10 * d
        paint.style = Paint.Style.FILL
        paint.color = theme.background
        paint.shader = theme.backgroundShader(w, h)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)
        paint.shader = null

        val pad = 7 * d
        val gap = 3.5f * d
        val cr = 3.5f * d
        val rows = 3
        val keyH = (h - pad * 2 - gap * (rows - 1)) / rows
        val availW = w - pad * 2

        val charW = (availW - gap * 3) / 4f
        for (row in 0..1) {
            val top = pad + row * (keyH + gap)
            for (i in 0 until 4) {
                val left = pad + i * (charW + gap)
                drawKey(canvas, left, top, left + charW, top + keyH, theme.key, cr)
            }
        }

        val bTop = pad + 2 * (keyH + gap)
        val bAvail = availW - gap * 2
        val shiftW = bAvail * 0.25f
        val spaceW = bAvail * 0.50f
        drawKey(canvas, pad, bTop, pad + shiftW, bTop + keyH, theme.specialKey, cr)
        drawKey(canvas, pad + shiftW + gap, bTop, pad + shiftW + gap + spaceW, bTop + keyH, theme.effectiveSpaceKey, cr)
        drawKey(canvas, pad + shiftW + gap + spaceW + gap, bTop, w - pad, bTop + keyH, theme.enterKey, cr)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (chosen) 2.5f else 1f) * d
        paint.color = if (chosen) {
            androidx.core.content.ContextCompat.getColor(context, com.mbt.keyboard.R.color.brand)
        } else {
            androidx.core.content.ContextCompat.getColor(context, com.mbt.keyboard.R.color.hairline)
        }
        val inset = paint.strokeWidth / 2f
        canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r, r, paint)
    }
}
