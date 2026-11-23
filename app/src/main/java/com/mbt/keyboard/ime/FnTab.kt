package com.mbt.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val plate = Path()
    private val rim = Path()
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    companion object {

        /** 바탕 모서리 반지름(좌상·우상·우하·좌하, x·y 순): 위쪽만 둥글고 아래쪽은 사각 */
        fun plateRadii(left: Float, right: Float = left, bottom: Boolean = false) =
            if (bottom) floatArrayOf(left, left, right, right, right, right, left, left) else floatArrayOf(left, left, right, right, 0f, 0f, 0f, 0f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = src.fnTabLeftPx() + keys.size * src.fnKeyWidthPx() + src.fnTabRightPx()
        setMeasuredDimension(kotlin.math.ceil(w).toInt(), src.fnTabHeightPx().toInt().coerceAtLeast(1))
    }

    private fun keyLeft(i: Int) = src.fnTabLeftPx() + i * src.fnKeyWidthPx()

    private fun indexAt(x: Float, y: Float): Int {
        val kw = src.fnKeyWidthPx()
        if (y < src.fnTabPadPx() || y > height - src.fnTabPadBottomPx()) return -1
        return keys.indices.firstOrNull { x >= keyLeft(it) && x < keyLeft(it) + kw } ?: -1      // 인식은 간격까지 포함
    }

    private fun withAlpha(color: Int, pct: Int) = (color and 0x00FFFFFF) or ((pct * 255 / 100) shl 24)

    override fun onDraw(canvas: Canvas) {
        val kw = src.fnKeyWidthPx()
        val pad = src.fnTabPadPx()
        val top = pad
        val kh = height - src.fnTabPadBottomPx()          // 버튼 아래 끝
        val radius = if (theme.frost) src.frostKeyRadiusPx() else 7 * d
        // 키보드 바탕: 버튼 상하좌우로 같은 폭만큼, 버튼과 똑같이 반투명
        val plateLeft = 0f
        // 탭 뒤에는 앱 화면이 있으므로 글래스 테마도 불투명도 슬라이더만큼 비치게 한다 (색은 판의 번짐색)
        paint.color = withAlpha(if (theme.frost) theme.backgroundMid else theme.background, alphaPercent)
        // 모서리는 버튼 모서리와 같은 중심(동심원): 여백이 0이면 버튼 모서리와 똑같이 맞물려 빈틈이 남지 않는다
        val gx = src.fnTabInsetPx()
        val rl = radius + minOf(pad, src.fnTabLeftPx() + gx, 10 * d)
        val rr = radius + minOf(pad, src.fnTabRightPx() + gx, 10 * d)
        plate.rewind()
        val bp = src.fnTabPadBottomPx()
        val rlb = radius + minOf(bp, src.fnTabLeftPx() + gx, 10 * d)
        val rrb = radius + minOf(bp, src.fnTabRightPx() + gx, 10 * d)
        val attached = theme.frost && src.frostTabAttached
        if (attached) {
            // 글래스 테마: 탭이 키보드 판 위에 딱 붙어 한 장의 유리처럼 이어진다 (위쪽 모서리만 둥글고 아래는 판으로 이어짐)
            val w = width.toFloat(); val h = height.toFloat()
            plate.addRoundRect(RectF(plateLeft, 0f, w, h), floatArrayOf(rl, rl, rr, rr, 0f, 0f, 0f, 0f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(plate)
            src.drawFrostTabSurface(canvas, w, h, (alphaPercent * 255 / 100).coerceIn(0, 255))
            canvas.restore()
            // 림: 위·양옆만 (아래는 판과 이어지므로 선 없음)
            rim.rewind()
            rim.moveTo(0f, h); rim.lineTo(0f, rl)
            rim.arcTo(RectF(0f, 0f, 2 * rl, 2 * rl), 180f, 90f)
            rim.lineTo(w - rr, 0f)
            rim.arcTo(RectF(w - 2 * rr, 0f, w, 2 * rr), 270f, 90f)
            rim.lineTo(w, h)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * d
            paint.shader = src.frostRimShader(w, h)
            canvas.drawPath(rim, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        } else {
            plate.addRoundRect(RectF(plateLeft, 0f, width.toFloat(), height.toFloat()),
                floatArrayOf(rl, rl, rr, rr, rrb, rrb, rlb, rlb), Path.Direction.CW)         // 네 모서리 모두 둥글게
            canvas.drawPath(plate, paint)
        }
        keys.forEachIndexed { i, k ->
            val cl = keyLeft(i)
            val l = cl + gx
            val r = cl + kw - gx
            paint.color = if (theme.frost) (if (i == pressed) theme.keyPressed else theme.key) else withAlpha(if (i == pressed) theme.keyPressed else theme.specialKey, alphaPercent)
            canvas.drawRoundRect(l, top, r, kh, radius, radius, paint)
            if (theme.keyBorder != 0) {
                paint.color = withAlpha(theme.keyBorder, alphaPercent.coerceAtLeast(60))
                // 테두리는 채움과 같은 방식으로 얇은 선만 (스타일 전환 없이 안쪽을 한 번 더 칠하지 않는다)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = d
                canvas.drawRoundRect(l + d / 2, top + d / 2, r - d / 2, kh - d / 2, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
            paint.color = if (theme.frost) theme.text else withAlpha(theme.text, alphaPercent)          // 글씨도 버튼·바탕과 같은 반투명
            paint.textSize = src.fnLabelSizePx(kw, kh - top)
            val y = (top + kh) / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(k.label, (l + r) / 2f, y, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = indexAt(e.x, e.y)
                if (pressed >= 0) haptics?.key()
                invalidate()
                return pressed >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressed >= 0 && indexAt(e.x, e.y) != pressed) { pressed = -1; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val hit = pressed
                pressed = -1
                invalidate()
                if (hit >= 0 && indexAt(e.x, e.y) == hit) onKey?.invoke(keys[hit])
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}
