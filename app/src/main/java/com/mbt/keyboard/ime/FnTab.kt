package com.mbt.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType

/**
 * 키보드 위 왼쪽에 띄우는 반투명 탭: 전체·복사·붙임.
 * 버튼 크기는 [src] 키보드의 숫자 키 가로·스페이스바 높이와 같고, 배경만 반투명이라 뒤의 앱 화면이 비친다.
 */
class FnTab(context: Context, private val src: KeyboardView) : View(context) {
    var onKey: ((Key) -> Unit)? = null
    var haptics: Haptics? = null

    var theme: KeyboardTheme = src.theme
        set(v) { field = v; invalidate() }

    /** 배경 불투명도 % (낮을수록 뒤가 많이 비친다) */
    var alphaPercent: Int = 50
        set(v) { field = v; invalidate() }

    val keys = listOf(Key("전체", KeyType.SELECT_ALL), Key("복사", KeyType.COPY), Key("붙임", KeyType.PASTE))

    private var pressed = -1
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = src.fnTabLeftPx() + keys.size * src.fnKeyWidthPx()
        setMeasuredDimension(w.toInt() + 1, src.fnKeyHeightPx().toInt().coerceAtLeast(1))
    }

    private fun keyLeft(i: Int) = src.fnTabLeftPx() + i * src.fnKeyWidthPx()

    private fun indexAt(x: Float): Int {
        val kw = src.fnKeyWidthPx()
        return keys.indices.firstOrNull { x >= keyLeft(it) && x < keyLeft(it) + kw } ?: -1      // 인식은 간격까지 포함
    }

    private fun withAlpha(color: Int, pct: Int) = (color and 0x00FFFFFF) or ((pct * 255 / 100) shl 24)

    override fun onDraw(canvas: Canvas) {
        val kw = src.fnKeyWidthPx()
        val kh = height.toFloat()
        val radius = 7 * d
        val gx = src.fnTabInsetPx()
        keys.forEachIndexed { i, k ->
            val cl = keyLeft(i)
            val l = cl + gx
            val r = cl + kw - gx
            paint.color = withAlpha(if (i == pressed) theme.keyPressed else theme.specialKey, alphaPercent)
            canvas.drawRoundRect(l, 0f, r, kh, radius, radius, paint)
            if (theme.keyBorder != 0) {
                paint.color = withAlpha(theme.keyBorder, alphaPercent.coerceAtLeast(60))
                // 테두리는 채움과 같은 방식으로 얇은 선만 (스타일 전환 없이 안쪽을 한 번 더 칠하지 않는다)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = d
                canvas.drawRoundRect(l + d / 2, d / 2, r - d / 2, kh - d / 2, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
            paint.color = theme.text
            paint.textSize = src.fnLabelSizePx(kw, kh)
            val y = kh / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(k.label, (l + r) / 2f, y, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = indexAt(e.x)
                if (pressed >= 0) haptics?.key()
                invalidate()
                return pressed >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressed >= 0 && indexAt(e.x) != pressed) { pressed = -1; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val hit = pressed
                pressed = -1
                invalidate()
                if (hit >= 0 && indexAt(e.x) == hit) onKey?.invoke(keys[hit])
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}
