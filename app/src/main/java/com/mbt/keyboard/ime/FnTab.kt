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
 * 키보드 위에 띄우는 반투명 탭:
 * - 숫자 좌측 배치: [전체·복사·붙임] 3키 미니 탭
 * - 숫자 상단 배치: [전체·복사·붙임 + 1~0] 13키 1줄 통합 탭
 * 탭 높이·여백·불투명도·4종 유리 효과는 두 모드 모두 동일하게 공유한다.
 */
class FnTab(context: Context, private val src: KeyboardView) : View(context) {
    var onKey: ((Key) -> Unit)? = null
    var haptics: Haptics? = null

    var theme: KeyboardTheme = src.theme
        set(v) { field = v; invalidate() }

    /** 배경 불투명도 % (낮을수록 뒤가 많이 비친다) */
    var alphaPercent: Int = 50
        set(v) { field = v; invalidate() }

    val keys: List<Key> get() = src.fnTabKeys().ifEmpty { DEFAULT_KEYS }

    private var pressed = -1
    private val plate = Path()
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    companion object {
        private val DEFAULT_KEYS = listOf(
            Key("전체", KeyType.SELECT_ALL),
            Key("복사", KeyType.COPY),
            Key("붙임", KeyType.PASTE),
        )

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
        val list = keys
        val kw = src.fnKeyWidthPx()
        if (y < src.fnTabPadPx() || y > height - src.fnTabPadBottomPx()) return -1
        return list.indices.firstOrNull { x >= keyLeft(it) && x < keyLeft(it) + kw } ?: -1      // 인식은 간격까지 포함
    }

    private fun withAlpha(color: Int, pct: Int) = (color and 0x00FFFFFF) or ((pct * 255 / 100) shl 24)

    override fun onDraw(canvas: Canvas) {
        val list = keys
        val kw = src.fnKeyWidthPx()
        val pad = src.fnTabPadPx()
        val top = pad
        val kh = height - src.fnTabPadBottomPx()          // 버튼 아래 끝
        val attached = src.bgGlassOn && src.frostTabAttached
        val useGlassKey = src.glassType >= 0 && src.keyGlassOn
        val radius = if (theme.frost || attached || useGlassKey) src.frostKeyRadiusPx() else src.effectiveKeyRadiusPx()
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
        val w = width.toFloat(); val h = height.toFloat()
        plate.addRoundRect(RectF(plateLeft, 0f, w, h), floatArrayOf(rl, rl, rr, rr, rrb, rrb, rlb, rlb), Path.Direction.CW)   // 네 모서리 모두 둥글게
        if (attached) {
            // 글래스 표면: 판과 같은 유리 표면(일반·블러·리드·3D)을 탭 불투명도만큼 비치게 그리고, 테두리 림 반사는 유리 강도 100% 유지
            canvas.save()
            canvas.clipPath(plate)
            src.drawFrostTabSurface(canvas, w, h, (alphaPercent * 255 / 100).coerceIn(0, 255))
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.color = 0xFFFFFFFF.toInt()
            paint.strokeWidth = 1.6f * d
            paint.shader = src.frostRimShader(w, h)
            canvas.drawPath(plate, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        } else {
            canvas.drawPath(plate, paint)
        }
        list.forEachIndexed { i, k ->
            val cl = keyLeft(i)
            val l = cl + gx
            val r = cl + kw - gx
            val isDigit = k.type == KeyType.CHAR
            val baseColor = when {
                i == pressed -> theme.keyPressed
                isDigit -> theme.numberKey
                else -> theme.specialKey
            }
            if (useGlassKey) {
                src.drawFrostKey(canvas, RectF(l, top, r, kh), radius, src.withKeyOpacity(baseColor), i == pressed, tabShiftX = src.frostTabLeftPx, tabShiftY = -(height + src.fnTabGapPx()))
            } else {
                paint.color = src.withKeyOpacity(baseColor)
                canvas.drawRoundRect(l, top, r, kh, radius, radius, paint)
                if (theme.keyBorder != 0) {
                    paint.color = src.withKeyOpacity(theme.keyBorder)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = d
                    canvas.drawRoundRect(l + d / 2, top + d / 2, r - d / 2, kh - d / 2, radius, radius, paint)
                    paint.style = Paint.Style.FILL
                }
            }
            paint.color = theme.text
            var textSize = src.fnLabelSizePx(kw, kh - top)
            paint.textSize = textSize
            val maxW = (r - l - 2 * d).coerceAtLeast(4f)
            if (k.label.length >= 2) {
                val measured = paint.measureText(k.label)
                if (measured > maxW) {
                    textSize *= maxW / measured
                    paint.textSize = textSize
                }
            }
            val y = (top + kh) / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(k.label, (l + r) / 2f, y, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val list = keys
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
                if (hit >= 0 && indexAt(e.x, e.y) == hit && hit < list.size) onKey?.invoke(list[hit])
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}
