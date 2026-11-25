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
 * 미리보기([previewContainer]) 위에 겹쳐 그려지는 직접 드래그 조절 오버레이.
 * - page 0 (① 크기): 바깥 파란 점선(전체 가로·세로·갈라짐) + 안쪽 보라 점선(키 영역 상하좌우 테두리 여백)
 * - page 2 (③ 보조탭): 가운데(위치 이동) + 오른쪽 변(가로)·아래 변(세로)·우하단 모서리(가로+세로)
 * - page 3 (④ 숫자·스페이스줄): 숫자 띠 경계선(좌우) + 스페이스바 윗변(상하)
 * - page 5 (⑥ 인식): 줄별 인식 경계 점선 상하 드래그
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
) : View(context) {

    var activePage: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val accent = 0xFF1A73E8.toInt()
    private val padAccent = 0xFF7B1FA2.toInt()
    private val tabAccent = 0xFF0F9D58.toInt()
    private val hitAccent = 0xFFE65100.toInt()

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        pathEffect = DashPathEffect(floatArrayOf(7 * d, 5 * d), 0f)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        color = 0xFFFFFFFF.toInt()
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
        strokeCap = Paint.Cap.ROUND
    }

    private enum class DragTarget {
        NONE,
        KB_WIDTH, KB_HEIGHT, KB_GAP,
        PAD_LEFT, PAD_TOP, PAD_RIGHT, PAD_BOTTOM, PAD_MOVE,
        TAB_MOVE, TAB_WIDTH, TAB_HEIGHT, TAB_CORNER,
        NUM_WIDTH, SPACE_HEIGHT,
        HIT_LINE,
    }

    private var dragTarget = DragTarget.NONE
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

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, color: Int, radiusDp: Float = 8.5f) {
        val r = radiusDp * d
        handleFill.color = color
        canvas.drawCircle(cx, cy, r, handleFill)
        canvas.drawCircle(cx, cy, r, handleStroke)
    }

    override fun onDraw(canvas: Canvas) {
        val hr = hostRect()
        if (hr.width() <= 0f || hr.height() <= 0f) return
        val g = getGeometry()
        when (activePage) {
            0 -> {
                // 바깥 파란 점선: 바깥 배경 상하 크기 조절 (위쪽 핸들만 사용)
                dashPaint.color = accent
                canvas.drawRect(hr.left + d, hr.top + d, hr.right - d, hr.bottom - d, dashPaint)
                val outTopX = hr.left + hr.width() * 0.18f
                drawHandle(canvas, outTopX, hr.top + 2 * d, accent)
                if (isSplitActive()) {
                    val gx = hr.centerX()
                    canvas.drawLine(gx, hr.top + 8 * d, gx, hr.bottom - 8 * d, dashPaint)
                    drawHandle(canvas, gx, hr.top + hr.height() * 0.25f, accent, 7.5f)
                }

                // 안쪽 보라 점선: 키 영역 테두리 여백 (상하좌우 중앙 핸들 + 가운데 이동)
                val kr = keysRect(hr, g)
                dashPaint.color = padAccent
                canvas.drawRect(kr, dashPaint)
                drawHandle(canvas, kr.left, kr.centerY(), padAccent, 7.5f)
                drawHandle(canvas, kr.right, kr.centerY(), padAccent, 7.5f)
                drawHandle(canvas, kr.centerX(), kr.top, padAccent, 7.5f)
                drawHandle(canvas, kr.centerX(), kr.bottom, padAccent, 7.5f)
            }
            2 -> {
                if (fnTab.visibility != VISIBLE) return
                val tr = tabRect()
                if (tr.width() <= 0f || tr.height() <= 0f) return
                dashPaint.color = tabAccent
                canvas.drawRoundRect(tr, 8 * d, 8 * d, dashPaint)
                // 가운데 이동 핸들
                drawHandle(canvas, tr.centerX(), tr.centerY(), tabAccent, 7f)
                // 오른쪽 변(가로) + 위쪽 변(세로: 위로 끌면 위로 커짐) 핸들
                drawHandle(canvas, tr.right - 1 * d, tr.centerY(), tabAccent, 7.5f)
                drawHandle(canvas, tr.centerX(), tr.top + 1 * d, tabAccent, 7.5f)
            }
            3 -> {
                dashPaint.color = accent
                if (keyboard.hasSideStrip) {
                    val sx = hr.left + keyboard.stripRightPx()
                    canvas.drawLine(sx, hr.top + 4 * d, sx, hr.bottom - 4 * d, dashPaint)
                    drawHandle(canvas, sx, hr.centerY(), accent)
                }
                val sy = hr.top + keyboard.spaceRowTopPx()
                if (sy > hr.top && sy < hr.bottom) {
                    canvas.drawLine(hr.left + 4 * d, sy, hr.right - 4 * d, sy, dashPaint)
                    drawHandle(canvas, hr.centerX(), sy, accent)
                }
            }
            5 -> {
                val ys = keyboard.hitZoneYs()
                dashPaint.color = hitAccent
                for (r in 1 until ys.size) {
                    val y = hr.top + ys[r]
                    canvas.drawLine(hr.left + 4 * d, y, hr.right - 4 * d, y, dashPaint)
                    val hx = hr.left + hr.width() * (0.3f + 0.2f * ((r - 1) % 3))
                    drawHandle(canvas, hx, y, hitAccent, 7.5f)
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
                dragTarget = DragTarget.NONE
                hitLineIndex = -1
                when (activePage) {
                    0 -> {
                        val kr = keysRect(hr, g)
                        val outTopX = hr.left + hr.width() * 0.18f
                        dragTarget = when {
                            isSplitActive() && near(e.x, e.y, hr.centerX(), hr.top + hr.height() * 0.25f, 24f) -> DragTarget.KB_GAP
                            near(e.x, e.y, outTopX, hr.top, 26f) -> DragTarget.KB_HEIGHT
                            near(e.x, e.y, kr.left, kr.centerY(), 24f) -> DragTarget.PAD_LEFT
                            near(e.x, e.y, kr.right, kr.centerY(), 24f) -> DragTarget.PAD_RIGHT
                            near(e.x, e.y, kr.centerX(), kr.top, 24f) -> DragTarget.PAD_TOP
                            near(e.x, e.y, kr.centerX(), kr.bottom, 24f) -> DragTarget.PAD_BOTTOM
                            else -> DragTarget.NONE
                        }
                    }
                    2 -> {
                        if (fnTab.visibility == VISIBLE) {
                            val tr = tabRect()
                            dragTarget = when {
                                near(e.x, e.y, tr.right, tr.centerY(), 22f) -> DragTarget.TAB_WIDTH
                                near(e.x, e.y, tr.centerX(), tr.top, 22f) -> DragTarget.TAB_HEIGHT
                                e.x in (tr.left - 12 * d)..(tr.right + 12 * d) && e.y in (tr.top - 12 * d)..(tr.bottom + 12 * d) -> DragTarget.TAB_MOVE
                                else -> DragTarget.NONE
                            }
                        }
                    }
                    3 -> {
                        val sx = hr.left + keyboard.stripRightPx()
                        val sy = hr.top + keyboard.spaceRowTopPx()
                        val numHit = keyboard.hasSideStrip && near(e.x, e.y, sx, hr.centerY(), 24f)
                        val spaceHit = sy > hr.top && near(e.x, e.y, hr.centerX(), sy, 24f)
                        dragTarget = when {
                            numHit -> DragTarget.NUM_WIDTH
                            spaceHit -> DragTarget.SPACE_HEIGHT
                            else -> DragTarget.NONE
                        }
                    }
                    5 -> {
                        val ys = keyboard.hitZoneYs()
                        var bestIdx = -1
                        var bestDist = 26f * d
                        for (r in 1 until ys.size) {
                            val hx = hr.left + hr.width() * (0.3f + 0.2f * ((r - 1) % 3))
                            val hy = hr.top + ys[r]
                            val dist = kotlin.math.hypot((e.x - hx).toDouble(), (e.y - hy).toDouble()).toFloat()
                            if (dist < bestDist) {
                                bestDist = dist
                                bestIdx = r
                            }
                        }
                        if (bestIdx >= 1) {
                            dragTarget = DragTarget.HIT_LINE
                            hitLineIndex = bestIdx
                        }
                    }
                }
                if (dragTarget != DragTarget.NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragTarget == DragTarget.NONE) return false
                val dx = e.rawX - downRawX
                val dy = e.rawY - downRawY
                val dxDp = (dx / d).roundToInt()
                val dyDp = (dy / d).roundToInt()
                val cw = getContainerWidth().coerceAtLeast(1)
                val updated = when (dragTarget) {
                    DragTarget.KB_WIDTH -> {
                        val deltaPct = (dx / cw * 100f).roundToInt()
                        startGeo.copy(widthPct = startGeo.widthPct + deltaPct).clamped()
                    }
                    DragTarget.KB_HEIGHT -> {
                        val deltaPct = (-dy / startKbBaseH * 100f).roundToInt()
                        startGeo.copy(heightPct = startGeo.heightPct + deltaPct).clamped()
                    }
                    DragTarget.KB_GAP -> {
                        val deltaPct = (dx / cw * 160f).roundToInt()
                        startGeo.copy(gapPct = startGeo.gapPct + deltaPct).clamped()
                    }
                    DragTarget.PAD_LEFT -> startGeo.copy(padLeftDp = startGeo.padLeftDp + dxDp).clamped()
                    DragTarget.PAD_RIGHT -> startGeo.copy(padRightDp = startGeo.padRightDp - dxDp).clamped()
                    DragTarget.PAD_TOP -> startGeo.copy(padTopDp = startGeo.padTopDp + dyDp).clamped()
                    DragTarget.PAD_BOTTOM -> startGeo.copy(padBottomDp = startGeo.padBottomDp - dyDp).clamped()
                    DragTarget.PAD_MOVE -> startGeo.copy(
                        padLeftDp = startGeo.padLeftDp + dxDp,
                        padRightDp = startGeo.padRightDp - dxDp,
                        padTopDp = startGeo.padTopDp + dyDp,
                        padBottomDp = startGeo.padBottomDp - dyDp,
                    ).clamped()
                    DragTarget.TAB_MOVE -> {
                        val tx = if (keyboard.hasTopNumberRow) 0 else dxDp
                        startGeo.copy(fnTabXdp = startGeo.fnTabXdp + tx, fnTabYdp = startGeo.fnTabYdp + dyDp).clamped()
                    }
                    DragTarget.TAB_WIDTH -> {
                        val maxW = if (keyboard.hasTopNumberRow) 100 else KeyboardGeometry.FN_TAB_WIDTH_MAX
                        val wPct = (startGeo.fnTabWidthPct + (dx / startTabBaseW * 100f).roundToInt()).coerceIn(KeyboardGeometry.FN_TAB_WIDTH_MIN, maxW)
                        startGeo.copy(fnTabWidthPct = wPct).clamped()
                    }
                    DragTarget.TAB_HEIGHT -> {
                        val hPct = startGeo.fnTabHeightPct + (-dy / startTabBaseH * 100f).roundToInt()
                        startGeo.copy(fnTabHeightPct = hPct).clamped()
                    }
                    DragTarget.TAB_CORNER -> {
                        val maxW = if (keyboard.hasTopNumberRow) 100 else KeyboardGeometry.FN_TAB_WIDTH_MAX
                        val wPct = (startGeo.fnTabWidthPct + (dx / startTabBaseW * 100f).roundToInt()).coerceIn(KeyboardGeometry.FN_TAB_WIDTH_MIN, maxW)
                        val hPct = startGeo.fnTabHeightPct + (dy / startTabBaseH * 100f).roundToInt()
                        startGeo.copy(fnTabWidthPct = wPct, fnTabHeightPct = hPct).clamped()
                    }
                    DragTarget.NUM_WIDTH -> {
                        val baseW = (hr.width() * 0.19f / 0.8f).coerceAtLeast(1f)
                        val deltaPct = (dx / baseW * 100f).roundToInt()
                        startGeo.copy(numHeightPct = startGeo.numHeightPct + deltaPct).clamped()
                    }
                    DragTarget.SPACE_HEIGHT -> {
                        val rowH = (hr.height() / 4f).coerceAtLeast(1f)
                        val deltaPct = (-dy / rowH * 100f).roundToInt()
                        startGeo.copy(spaceRowPct = startGeo.spaceRowPct + deltaPct).clamped()
                    }
                    DragTarget.HIT_LINE -> {
                        val first = if ((keyboard.hasSideStrip || keyboard.hasTopNumberRow) && keyboard.keyboardLayout?.moaki != true) 1 else 0
                        when (hitLineIndex - 1 + first) {
                            0 -> startGeo.copy(hit0Dp = startGeo.hit0Dp + dyDp).clamped()
                            1 -> startGeo.copy(hit1Dp = startGeo.hit1Dp + dyDp).clamped()
                            2 -> startGeo.copy(hit2Dp = startGeo.hit2Dp + dyDp).clamped()
                            3 -> startGeo.copy(hit3Dp = startGeo.hit3Dp + dyDp).clamped()
                            else -> startGeo
                        }
                    }
                    DragTarget.NONE -> startGeo
                }
                if (updated != getGeometry()) {
                    onGeometryChanged(updated)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragTarget != DragTarget.NONE) {
                    dragTarget = DragTarget.NONE
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
