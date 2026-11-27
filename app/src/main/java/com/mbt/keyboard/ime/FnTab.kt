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
    private val dockMaskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
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
        val w = width.toFloat(); val h = height.toFloat()
        val attached = src.bgGlassOn && (src.frostTabAttached || src.fnTabDocked)
        val useGlassKey = src.glassType >= 0 && src.keyGlassOn
        val isGlassRadius = theme.frost || attached || useGlassKey
        val radius = src.keyRadiusFor(kw - 2 * src.fnTabInsetPx(), kh - top, isGlassRadius)
        val docked = src.fnTabDocked
        val tabAlpha = (alphaPercent * 255 / 100).coerceIn(0, 255)
        val clipBottom = if (docked && src.frostTabOffsetYpx > 0f) src.frostTabOffsetYpx.coerceIn(0f, h) else h
        val layer = if (!docked) canvas.saveLayerAlpha(0f, 0f, w, h, tabAlpha) else -1
        // 탭 자체 모양은 항상 4모서리 둥근 알약 그대로 유지하고, 도킹 시에는 키보드 안쪽(clipBottom..h)만 가려 키를 덮지 않으며 유리 테두리만 합친다
        val plateLeft = 0f
        paint.color = if (theme.frost) theme.backgroundMid else theme.background
        val gx = src.fnTabInsetPx()
        val tabRadius = src.tabRadiusFor(w, h, isGlassRadius)
        val rl = tabRadius; val rr = tabRadius
        val rlb = tabRadius; val rrb = tabRadius
        plate.rewind()
        plate.addRoundRect(RectF(plateLeft, 0f, w, h), floatArrayOf(rl, rl, rr, rr, rrb, rrb, rlb, rlb), Path.Direction.CW)
        val kbPlatePath = if (docked) {
            val pw = src.width.toFloat().coerceAtLeast(w)
            val ph = src.height.toFloat().coerceAtLeast(h)
            val pr = src.plateRadiusFor(pw, ph, isGlassRadius)
            val kx = -src.frostTabLeftPx
            Path().apply {
                addRoundRect(
                    RectF(kx, clipBottom, kx + pw, clipBottom + ph),
                    floatArrayOf(pr, pr, pr, pr, 0f, 0f, 0f, 0f),
                    Path.Direction.CW,
                )
            }
        } else null
        if (clipBottom > 1f) {
            val dockLayer = if (docked) canvas.saveLayer(0f, 0f, w, h, null) else -1
            canvas.save()
            if (kbPlatePath != null) canvas.clipOutPath(kbPlatePath)
            if (attached || (src.bgGlassOn && src.glassType >= 0)) {
                canvas.save()
                canvas.clipPath(plate)
                src.drawFrostTabSurface(canvas, w, h, rlb, rrb)
                canvas.restore()
                if (src.effectiveGlassType != 3 && src.effectiveGlassType != 4) {
                    canvas.save()
                    if (docked) {
                        canvas.clipOutRect(1.5f * d, clipBottom - 1.2f * d, w - 1.5f * d, h + 2f)
                    }
                    paint.style = Paint.Style.STROKE
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.strokeWidth = 2.0f * d
                    paint.shader = src.frostRimShader(w, h)
                    canvas.drawPath(plate, paint)
                    paint.shader = null
                    paint.style = Paint.Style.FILL
                    canvas.restore()
                }
            } else {
                src.drawPlainTabSurface(canvas, w, h, plate)
            }
            canvas.restore()
            if (docked && dockLayer >= 0) {
                // 삐져나온 부분은 위(tabAlpha) → 키보드와 만나는 아래(255)로 부드럽게 그라데이션 불투명 적용
                val gradSpan = 12.6f * d
                val gradTop = (clipBottom - gradSpan).coerceAtLeast(0f)
                val a0 = tabAlpha
                val a1 = tabAlpha + ((255 - tabAlpha) * 0.20f).toInt()
                val a2 = tabAlpha + ((255 - tabAlpha) * 0.45f).toInt()
                val a3 = tabAlpha + ((255 - tabAlpha) * 0.72f).toInt()
                dockMaskPaint.shader = android.graphics.LinearGradient(
                    0f, gradTop, 0f, clipBottom,
                    intArrayOf(
                        (a0 shl 24) or 0x00FFFFFF,
                        (a1 shl 24) or 0x00FFFFFF,
                        (a2 shl 24) or 0x00FFFFFF,
                        (a3 shl 24) or 0x00FFFFFF,
                        0xFFFFFFFF.toInt(),
                    ),
                    floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, dockMaskPaint)
                dockMaskPaint.shader = null
                canvas.restoreToCount(dockLayer)
            }
        }
        val keyLayer = if (docked) canvas.saveLayerAlpha(0f, 0f, w, h, tabAlpha) else -1
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
            var textSize = src.fnLabelSizePx(kw, kh - top) * (if (isDigit) 1f else 0.70f)
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
        if (keyLayer >= 0) canvas.restoreToCount(keyLayer)
        if (layer >= 0) canvas.restoreToCount(layer)
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
