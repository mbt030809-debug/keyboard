package com.mbt.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.mbt.keyboard.ime.FnTab
import com.mbt.keyboard.ime.KeyboardView
import com.mbt.keyboard.settings.KeyboardGeometry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 미리보기([previewContainer]) 위에 겹쳐 그려지는 직접 드래그 & 클릭 선택 조절 오버레이.
 * - 점선이나 핸들을 터치하면 해당 항목이 하이라이트되어 아래 － ＋ 버튼으로 정밀 조절 가능
 * - 핸들을 잡고 바로 끌어당기면 실시간 드래그로도 조절 가능
 */
@SuppressLint("ViewConstructor")
class ResizeOverlayView(
    context: Context,
    private val host: FrameLayout,
    private val keyboard: KeyboardView,
    private val fnTab: FnTab,
    private val getGeometry: () -> KeyboardGeometry,
    private val onGeometryChanged: (KeyboardGeometry) -> Unit,
    private val isSplitActive: () -> Boolean,
    private val getContainerWidth: () -> Int,
    private val onTargetSelected: (Target) -> Unit,
) : View(context) {

    enum class Target {
        NONE,
        KB_HEIGHT, KB_GAP,
        PAD_LEFT, PAD_TOP, PAD_RIGHT, PAD_BOTTOM,
        TAB_MOVE, TAB_WIDTH, TAB_HEIGHT,
        NUM_WIDTH, SPACE_HEIGHT,
        HIT_LINE,
    }

    var activePage: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    var selectedTarget: Target = Target.KB_HEIGHT
        set(v) {
            field = v
            invalidate()
        }

    var selectedHitLine: Int = 1
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val accent = 0xFF1A73E8.toInt()
    private val padAccent = 0xFF7B1FA2.toInt()
    private val tabAccent = 0xFF0F9D58.toInt()
    private val hitAccent = 0xFFE65100.toInt()
    private val highlightColor = 0xFF00E5FF.toInt()

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * d
        pathEffect = DashPathEffect(floatArrayOf(7 * d, 5 * d), 0f)
    }
    private val hiDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * d
        color = highlightColor
        pathEffect = DashPathEffect(floatArrayOf(8 * d, 4 * d), 0f)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        color = 0xFFFFFFFF.toInt()
    }

    private var dragTarget = Target.NONE
    private var hitLineIndex = -1
    private var downRawX = 0f
    private var downRawY = 0f
    private var startGeo = KeyboardGeometry()
    private var startKbBaseH = 1f
    private var startTabBaseW = 1f
    private var startTabBaseH = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val lp = host.layoutParams as? FrameLayout.LayoutParams
        val hostH = host.measuredHeight + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
        val tabH = if (fnTab.visibility == VISIBLE) fnTab.measuredHeight + ((fnTab.layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: 0) else 0
        setMeasuredDimension(w, maxOf(hostH, tabH, 1))
    }

    private fun hostRect(): RectF = RectF(host.left.toFloat(), host.top.toFloat(), host.right.toFloat(), host.bottom.toFloat())
    private fun keysRect(hr: RectF, g: KeyboardGeometry): RectF = RectF(
        (hr.left + g.padLeftDp * d).coerceAtMost(hr.centerX()),
        (hr.top + g.padTopDp * d).coerceAtMost(hr.centerY()),
        (hr.right - g.padRightDp * d).coerceAtLeast(hr.centerX()),
        (hr.bottom - g.padBottomDp * d).coerceAtLeast(hr.centerY()),
    )
    private fun tabRect(): RectF = RectF(fnTab.left.toFloat(), fnTab.top.toFloat(), fnTab.right.toFloat(), fnTab.bottom.toFloat())

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, baseColor: Int, selected: Boolean, radiusDp: Float = 8f) {
        val r = (if (selected) radiusDp + 2f else radiusDp) * d
        handleFill.color = if (selected) highlightColor else baseColor
        canvas.drawCircle(cx, cy, r, handleFill)
        canvas.drawCircle(cx, cy, r, handleStroke)
    }

    override fun onDraw(canvas: Canvas) {
        val hr = hostRect()
        if (hr.width() <= 0f || hr.height() <= 0f) return
        val g = getGeometry()
        when (activePage) {
            0 -> {
                dashPaint.color = accent
                canvas.drawRect(hr.left + d, hr.top + d, hr.right - d, hr.bottom - d, if (selectedTarget == Target.KB_HEIGHT) hiDashPaint else dashPaint)
                val outTopX = hr.left + hr.width() * 0.18f
                drawHandle(canvas, outTopX, hr.top + 2 * d, accent, selectedTarget == Target.KB_HEIGHT)
                if (isSplitActive()) {
                    val gx = hr.centerX()
                    canvas.drawLine(gx, hr.top + 8 * d, gx, hr.bottom - 8 * d, if (selectedTarget == Target.KB_GAP) hiDashPaint else dashPaint)
                    drawHandle(canvas, gx, hr.top + hr.height() * 0.25f, accent, selectedTarget == Target.KB_GAP, 7.5f)
                }

                val kr = keysRect(hr, g)
                dashPaint.color = padAccent
                canvas.drawRect(kr, dashPaint)
                if (selectedTarget == Target.PAD_LEFT) canvas.drawLine(kr.left, kr.top, kr.left, kr.bottom, hiDashPaint)
                if (selectedTarget == Target.PAD_RIGHT) canvas.drawLine(kr.right, kr.top, kr.right, kr.bottom, hiDashPaint)
                if (selectedTarget == Target.PAD_TOP) canvas.drawLine(kr.left, kr.top, kr.right, kr.top, hiDashPaint)
                if (selectedTarget == Target.PAD_BOTTOM) canvas.drawLine(kr.left, kr.bottom, kr.right, kr.bottom, hiDashPaint)
                drawHandle(canvas, kr.left, kr.centerY(), padAccent, selectedTarget == Target.PAD_LEFT, 7.5f)
                drawHandle(canvas, kr.right, kr.centerY(), padAccent, selectedTarget == Target.PAD_RIGHT, 7.5f)
                drawHandle(canvas, kr.centerX(), kr.top, padAccent, selectedTarget == Target.PAD_TOP, 7.5f)
                drawHandle(canvas, kr.centerX(), kr.bottom, padAccent, selectedTarget == Target.PAD_BOTTOM, 7.5f)
            }
            2 -> {
                if (fnTab.visibility != VISIBLE) return
                val tr = tabRect()
                if (tr.width() <= 0f || tr.height() <= 0f) return
                dashPaint.color = tabAccent
                canvas.drawRoundRect(tr, 8 * d, 8 * d, if (selectedTarget == Target.TAB_MOVE) hiDashPaint else dashPaint)
                if (selectedTarget == Target.TAB_WIDTH) canvas.drawLine(tr.right, tr.top, tr.right, tr.bottom, hiDashPaint)
                if (selectedTarget == Target.TAB_HEIGHT) canvas.drawLine(tr.left, tr.top, tr.right, tr.top, hiDashPaint)
                drawHandle(canvas, tr.centerX(), tr.centerY(), tabAccent, selectedTarget == Target.TAB_MOVE, 7f)
                drawHandle(canvas, tr.right - 1 * d, tr.centerY(), tabAccent, selectedTarget == Target.TAB_WIDTH, 7.5f)
                drawHandle(canvas, tr.centerX(), tr.top + 1 * d, tabAccent, selectedTarget == Target.TAB_HEIGHT, 7.5f)
            }
            3 -> {
                dashPaint.color = accent
                if (keyboard.hasSideStrip) {
                    val sx = hr.left + keyboard.stripRightPx()
                    canvas.drawLine(sx, hr.top + 4 * d, sx, hr.bottom - 4 * d, if (selectedTarget == Target.NUM_WIDTH) hiDashPaint else dashPaint)
                    drawHandle(canvas, sx, hr.centerY(), accent, selectedTarget == Target.NUM_WIDTH)
                }
                val sy = hr.top + keyboard.spaceRowTopPx()
                if (sy > hr.top && sy < hr.bottom) {
                    canvas.drawLine(hr.left + 4 * d, sy, hr.right - 4 * d, sy, if (selectedTarget == Target.SPACE_HEIGHT) hiDashPaint else dashPaint)
                    drawHandle(canvas, hr.centerX(), sy, accent, selectedTarget == Target.SPACE_HEIGHT)
                }
            }
            5 -> {
                val ys = keyboard.hitZoneYs()
                dashPaint.color = hitAccent
                for (r in 1 until ys.size) {
                    val y = hr.top + ys[r]
                    val sel = selectedTarget == Target.HIT_LINE && selectedHitLine == r
                    canvas.drawLine(hr.left + 4 * d, y, hr.right - 4 * d, y, if (sel) hiDashPaint else dashPaint)
                    val hx = hr.left + hr.width() * (0.3f + 0.2f * ((r - 1) % 3))
                    drawHandle(canvas, hx, y, hitAccent, sel, 7.5f)
                }
            }
        }
    }

    private fun near(px: Float, py: Float, tx: Float, ty: Float, tolDp: Float = 24f): Boolean {
        val tol = tolDp * d
        return abs(px - tx) <= tol && abs(py - ty) <= tol
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (activePage !in setOf(0, 2, 3, 5)) return false
        val hr = hostRect()
        val g = getGeometry()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX; downRawY = e.rawY
                startGeo = g
                startKbBaseH = ((keyboard.height.takeIf { it > 0 } ?: 1).toFloat() / (g.heightPct / 100f)).coerceAtLeast(1f)
                startTabBaseW = ((fnTab.width.takeIf { it > 0 } ?: 1).toFloat() / (g.fnTabWidthPct / 100f)).coerceAtLeast(1f)
                startTabBaseH = ((fnTab.height.takeIf { it > 0 } ?: 1).toFloat() / (g.fnTabHeightPct / 100f)).coerceAtLeast(1f)
                dragTarget = Target.NONE
                hitLineIndex = -1
                when (activePage) {
                    0 -> {
                        val kr = keysRect(hr, g)
                        val outTopX = hr.left + hr.width() * 0.18f
                        dragTarget = when {
                            isSplitActive() && near(e.x, e.y, hr.centerX(), hr.top + hr.height() * 0.25f, 24f) -> Target.KB_GAP
                            near(e.x, e.y, outTopX, hr.top, 26f) || (abs(e.y - hr.top) <= 14 * d && e.x < kr.centerX() - 28 * d) -> Target.KB_HEIGHT
                            near(e.x, e.y, kr.left, kr.centerY(), 24f) || (abs(e.x - kr.left) <= 14 * d && e.y in kr.top..kr.bottom) -> Target.PAD_LEFT
                            near(e.x, e.y, kr.right, kr.centerY(), 24f) || (abs(e.x - kr.right) <= 14 * d && e.y in kr.top..kr.bottom) -> Target.PAD_RIGHT
                            near(e.x, e.y, kr.centerX(), kr.top, 24f) || (abs(e.y - kr.top) <= 14 * d && e.x in kr.left..kr.right) -> Target.PAD_TOP
                            near(e.x, e.y, kr.centerX(), kr.bottom, 24f) || (abs(e.y - kr.bottom) <= 14 * d && e.x in kr.left..kr.right) -> Target.PAD_BOTTOM
                            else -> Target.NONE
                        }
                    }
                    2 -> {
                        if (fnTab.visibility == VISIBLE) {
                            val tr = tabRect()
                            dragTarget = when {
                                near(e.x, e.y, tr.right, tr.centerY(), 22f) -> Target.TAB_WIDTH
                                near(e.x, e.y, tr.centerX(), tr.top, 22f) -> Target.TAB_HEIGHT
                                e.x in (tr.left - 12 * d)..(tr.right + 12 * d) && e.y in (tr.top - 12 * d)..(tr.bottom + 12 * d) -> Target.TAB_MOVE
                                else -> Target.NONE
                            }
                        }
                    }
                    3 -> {
                        val sx = hr.left + keyboard.stripRightPx()
                        val sy = hr.top + keyboard.spaceRowTopPx()
                        val numHit = keyboard.hasSideStrip && (near(e.x, e.y, sx, hr.centerY(), 24f) || (abs(e.x - sx) <= 14 * d && e.y in hr.top..hr.bottom))
                        val spaceHit = sy > hr.top && (near(e.x, e.y, hr.centerX(), sy, 24f) || (abs(e.y - sy) <= 14 * d && e.x in hr.left..hr.right))
                        dragTarget = when {
                            numHit -> Target.NUM_WIDTH
                            spaceHit -> Target.SPACE_HEIGHT
                            else -> Target.NONE
                        }
                    }
                    5 -> {
                        val ys = keyboard.hitZoneYs()
                        var bestIdx = -1
                        var bestDist = 20f * d
                        for (r in 1 until ys.size) {
                            val dist = abs(e.y - (hr.top + ys[r]))
                            if (dist < bestDist) {
                                bestDist = dist
                                bestIdx = r
                            }
                        }
                        if (bestIdx >= 1) {
                            dragTarget = Target.HIT_LINE
                            hitLineIndex = bestIdx
                            selectedHitLine = bestIdx
                        }
                    }
                }
                if (dragTarget != Target.NONE) {
                    selectedTarget = dragTarget
                    onTargetSelected(dragTarget)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragTarget == Target.NONE) return false
                val dx = e.rawX - downRawX
                val dy = e.rawY - downRawY
                val dxDp = (dx / d).roundToInt()
                val dyDp = (dy / d).roundToInt()
                val cw = getContainerWidth().coerceAtLeast(1)
                val updated = when (dragTarget) {
                    Target.KB_HEIGHT -> {
                        val deltaPct = (-dy / startKbBaseH * 100f).roundToInt()
                        startGeo.copy(heightPct = startGeo.heightPct + deltaPct).clamped()
                    }
                    Target.KB_GAP -> {
                        val deltaPct = (dx / cw * 160f).roundToInt()
                        startGeo.copy(gapPct = startGeo.gapPct + deltaPct).clamped()
                    }
                    Target.PAD_LEFT -> startGeo.copy(padLeftDp = startGeo.padLeftDp + dxDp).clamped()
                    Target.PAD_RIGHT -> startGeo.copy(padRightDp = startGeo.padRightDp - dxDp).clamped()
                    Target.PAD_TOP -> startGeo.copy(padTopDp = startGeo.padTopDp + dyDp).clamped()
                    Target.PAD_BOTTOM -> startGeo.copy(padBottomDp = startGeo.padBottomDp - dyDp).clamped()
                    Target.TAB_MOVE -> {
                        val tx = if (keyboard.hasTopNumberRow) 0 else dxDp
                        startGeo.copy(fnTabXdp = startGeo.fnTabXdp + tx, fnTabYdp = startGeo.fnTabYdp + dyDp).clamped()
                    }
                    Target.TAB_WIDTH -> {
                        val maxW = if (keyboard.hasTopNumberRow) 100 else KeyboardGeometry.FN_TAB_WIDTH_MAX
                        val wPct = (startGeo.fnTabWidthPct + (dx / startTabBaseW * 100f).roundToInt()).coerceIn(KeyboardGeometry.FN_TAB_WIDTH_MIN, maxW)
                        startGeo.copy(fnTabWidthPct = wPct).clamped()
                    }
                    Target.TAB_HEIGHT -> {
                        val hPct = startGeo.fnTabHeightPct + (-dy / startTabBaseH * 100f).roundToInt()
                        startGeo.copy(fnTabHeightPct = hPct).clamped()
                    }
                    Target.NUM_WIDTH -> {
                        val baseW = (hr.width() * 0.19f / 0.8f).coerceAtLeast(1f)
                        val deltaPct = (dx / baseW * 100f).roundToInt()
                        startGeo.copy(numHeightPct = startGeo.numHeightPct + deltaPct).clamped()
                    }
                    Target.SPACE_HEIGHT -> {
                        val rowH = (hr.height() / 4f).coerceAtLeast(1f)
                        val deltaPct = (-dy / rowH * 100f).roundToInt()
                        startGeo.copy(spaceRowPct = startGeo.spaceRowPct + deltaPct).clamped()
                    }
                    Target.HIT_LINE -> {
                        val first = if ((keyboard.hasSideStrip || keyboard.hasTopNumberRow) && keyboard.keyboardLayout?.moaki != true) 1 else 0
                        when (hitLineIndex - 1 + first) {
                            0 -> startGeo.copy(hit0Dp = startGeo.hit0Dp + dyDp).clamped()
                            1 -> startGeo.copy(hit1Dp = startGeo.hit1Dp + dyDp).clamped()
                            2 -> startGeo.copy(hit2Dp = startGeo.hit2Dp + dyDp).clamped()
                            3 -> startGeo.copy(hit3Dp = startGeo.hit3Dp + dyDp).clamped()
                            else -> startGeo
                        }
                    }
                    Target.NONE -> startGeo
                }
                if (updated != getGeometry()) {
                    onGeometryChanged(updated)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragTarget != Target.NONE) {
                    dragTarget = Target.NONE
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
                return false
            }
        }
        return false
    }
}
