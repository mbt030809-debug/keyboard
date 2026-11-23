package com.mbt.keyboard.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * 크기 조정 화면의 설명 그림: 키보드 모양을 간단히 그리고, 지금 고르는 항목이 가리키는 부분을 주황색으로 강조한다.
 */
class ResizeDiagram @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Part { SIZE, PAD, GAP, NUMROW, NUMGAP, TAB, HIT }

    var part: Part = Part.SIZE
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orange = 0xFFFF8A00.toInt()

    private fun attr(res: Int, fallback: Int) = MaterialColors.getColor(this, res, fallback)

    override fun onDraw(canvas: Canvas) {
        val surface = attr(com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt())
        val variant = attr(com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE0E0E8.toInt())
        val outline = attr(com.google.android.material.R.attr.colorOutline, 0xFF8A93A8.toInt())
        val onSurface = attr(com.google.android.material.R.attr.colorOnSurface, 0xFF222222.toInt())
        val primary = attr(com.google.android.material.R.attr.colorPrimary, 0xFF3F51B5.toInt())

        val w = width.toFloat()
        val h = height.toFloat()
        val x0 = 28 * d
        val frameW = w - x0 * 2
        val tabH = 24 * d
        val y0 = tabH + 10 * d
        val frameH = h - y0 - 24 * d
        val pad = 8 * d
        val strip = 52 * d
        val gk = if (part == Part.GAP) 6 * d else 3 * d
        val numGap = if (part == Part.NUMGAP) 14 * d else 6 * d

        // 전체 틀
        paint.style = Paint.Style.FILL
        paint.color = variant
        canvas.drawRoundRect(x0, y0, x0 + frameW, y0 + frameH, 10 * d, 10 * d, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (part == Part.SIZE) 3 * d else 1.5f * d
        paint.color = if (part == Part.SIZE) orange else outline
        if (part != Part.SIZE) paint.pathEffect = DashPathEffect(floatArrayOf(5 * d, 4 * d), 0f)
        canvas.drawRoundRect(x0, y0, x0 + frameW, y0 + frameH, 10 * d, 10 * d, paint)
        paint.pathEffect = null

        // 여백 띠 (바깥 틀과 안쪽 사이)
        if (part == Part.PAD) {
            paint.style = Paint.Style.FILL
            paint.color = orange; paint.alpha = 140
            canvas.drawRect(x0, y0, x0 + frameW, y0 + pad, paint)
            canvas.drawRect(x0, y0 + frameH - pad, x0 + frameW, y0 + frameH, paint)
            canvas.drawRect(x0, y0 + pad, x0 + pad, y0 + frameH - pad, paint)
            canvas.drawRect(x0 + frameW - pad, y0 + pad, x0 + frameW, y0 + frameH - pad, paint)
            paint.alpha = 255
        }

        // 전복붙 탭
        paint.style = Paint.Style.FILL
        paint.color = if (part == Part.TAB) orange else outline
        paint.alpha = if (part == Part.TAB) 220 else 90
        canvas.drawRoundRect(x0, y0 - tabH - 2 * d, x0 + strip + pad * 2 + 26 * d, y0, 8 * d, 8 * d, paint)
        paint.alpha = 255
        val cell = 26 * d
        for (i in 0 until 3) {
            paint.color = if (part == Part.TAB) surface else variant
            canvas.drawRoundRect(x0 + pad + i * cell + d, y0 - tabH, x0 + pad + (i + 1) * cell - d, y0 - 3 * d, 5 * d, 5 * d, paint)
        }

        // 숫자 띠
        val n = 5
        val kh = (frameH - pad * 2 - (n - 1) * 3 * d) / n
        for (c in 0 until 2) for (r in 0 until n) {
            paint.color = if (part == Part.NUMROW) primary else outline
            paint.alpha = if (part == Part.NUMROW) 255 else 150
            val l = x0 + pad + c * cell
            val t = y0 + pad + r * (kh + 3 * d)
            canvas.drawRoundRect(l, t, l + cell - 3 * d, t + kh, 4 * d, 4 * d, paint)
            paint.alpha = 255
        }

        // 숫자 띠와 글자 키 사이
        val lx = x0 + pad + strip + numGap
        if (part == Part.NUMGAP) {
            paint.color = orange; paint.alpha = 150
            canvas.drawRect(x0 + pad + strip - 3 * d, y0 + pad, lx, y0 + frameH - pad, paint)
            paint.alpha = 255
        }

        // 글자 키
        val cols = 7
        val rows = 4
        val lw = x0 + frameW - pad - lx
        val kw = (lw - (cols - 1) * gk) / cols
        val kh2 = (frameH - pad * 2 - (rows - 1) * gk) / rows
        for (r in 0 until rows) for (c in 0 until cols) {
            val l = lx + c * (kw + gk)
            val t = y0 + pad + r * (kh2 + gk)
            paint.color = surface
            canvas.drawRoundRect(l, t, l + kw, t + kh2, 4 * d, 4 * d, paint)
            if (part == Part.GAP) {
                paint.color = orange
                if (c < cols - 1) canvas.drawRect(l + kw, t, l + kw + gk, t + kh2, paint)
                if (r < rows - 1) canvas.drawRect(l, t + kh2, l + kw, t + kh2 + gk, paint)
            }
        }

        // 터치 인식 경계
        if (part == Part.HIT) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * d
            paint.color = orange
            paint.pathEffect = DashPathEffect(floatArrayOf(6 * d, 4 * d), 0f)
            for (r in 1 until rows) {
                val y = y0 + pad + r * (kh2 + gk) - gk / 2
                canvas.drawLine(lx, y, lx + lw, y, paint)
            }
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
        }

        // 설명 글자 (주황 강조 + 화살표)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12 * d
        paint.isFakeBoldText = true
        paint.color = if (part == Part.NUMROW) primary else orange
        val label = when (part) {
            Part.SIZE -> "◀ 가로 ▶"
            Part.PAD -> "위·아래·왼쪽·오른쪽 여백"
            Part.GAP -> "키 사이 간격"
            Part.NUMROW -> "◀ 숫자줄 크기 ▶"
            Part.NUMGAP -> "숫자줄 여백"
            Part.TAB -> "전복붙 높이 ↕"
            Part.HIT -> "눈에 안 보이는 터치 경계"
        }
        val lxCenter = if (part == Part.NUMROW || part == Part.NUMGAP) x0 + pad + strip / 2 + (if (part == Part.NUMGAP) strip / 2 else 0f) else w / 2
        val ly = if (part == Part.TAB) y0 - tabH / 2 + 4 * d else h - 6 * d
        val lxx = if (part == Part.TAB) x0 + strip + pad * 2 + 26 * d + 62 * d else lxCenter
        canvas.drawText(label, lxx, ly, paint)
        if (part == Part.SIZE) {
            canvas.save()
            canvas.rotate(-90f, 12 * d, y0 + frameH / 2)
            canvas.drawText("◀ 세로 ▶", 12 * d, y0 + frameH / 2 + 4 * d, paint)
            canvas.restore()
        }
        paint.isFakeBoldText = false
        paint.color = onSurface
    }
}
