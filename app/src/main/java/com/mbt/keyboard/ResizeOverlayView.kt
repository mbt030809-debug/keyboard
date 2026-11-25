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
 * - page 0 (① 전체 크기): 키보드 오른쪽(가로), 위쪽(세로), 가운데 틈(갈라짐) 핸들 드래그
 * - page 3 (④ 보조탭): 탭 가운데 드래그(위치 이동) + 우하단 모서리 핸들 드래그(크기 조절)
 * - page 4 (⑤ 숫자·스페이스줄): 숫자 띠 경계선(좌우) + 스페이스바 윗변(상하) 드래그
 * - page 6 (⑦ 인식): 줄별 인식 경계 점선 상하 드래그
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
        TAB_MOVE, TAB_RESIZE,
        NUM_WIDTH, SPACE_HEIGHT,
        HIT_LINE,
    }

    private var dragTarget = DragTarget.NONE
    private var hitLineIndex = -1
    private var downX = 0f
    private var downY = 0f
    private var startGeo = KeyboardGeometry()

    private fun hostRect(): RectF = RectF(host.left.toFloat(), host.top.toFloat(), host.right.toFloat(), host.bottom.toFloat())
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
        when (activePage) {
            0 -> {
                dashPaint.color = accent
                canvas.drawRect(hr.left + d, hr.top + d, hr.right - d, hr.bottom - d, dashPaint)
                drawHandle(canvas, hr.right - 2 * d, hr.centerY(), accent)
                drawHandle(canvas, hr.centerX(), hr.top + 2 * d, accent)
                if (isSplitActive()) {
                    val gx = hr.centerX()
                    canvas.drawLine(gx, hr.top + 8 * d, gx, hr.bottom - 8 * d, dashPaint)
                    drawHandle(canvas, gx, hr.centerY(), accent, 7.5f)
                }
            }
            3 -> {
                if (fnTab.visibility != VISIBLE) return
                val tr = tabRect()
                if (tr.width() <= 0f || tr.height() <= 0f) return
                dashPaint.color = tabAccent
                canvas.drawRoundRect(tr, 8 * d, 8 * d, dashPaint)
                // 이동 중심 핸들
                drawHandle(canvas, tr.centerX(), tr.centerY(), tabAccent, 7f)
                // 우하단 모서리 크기 조절 핸들
                cornerPaint.color = tabAccent
                val r = 10 * d
                canvas.drawLine(tr.right - r, tr.bottom, tr.right, tr.bottom, cornerPaint)
                canvas.drawLine(tr.right, tr.bottom - r, tr.right, tr.bottom, cornerPaint)
                drawHandle(canvas, tr.right - 2 * d, tr.bottom - 2 * d, tabAccent, 8.5f)
            }
            4 -> {
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
            6 -> {
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

    private fun near(px: Float, py: Float, tx: Float, ty: Float, tolDp: Float = 26f): Boolean {
        val tol = tolDp * d
        return abs(px - tx) <= tol && abs(py - ty) <= tol
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (activePage !in setOf(0, 3, 4, 6)) return false
        val hr = hostRect()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                startGeo = getGeometry()
                dragTarget = DragTarget.NONE
                hitLineIndex = -1
                when (activePage) {
                    0 -> {
                        val rightHit = near(e.x, e.y, hr.right, hr.centerY(), 30f) ||
                            (abs(e.x - hr.right) <= 24 * d && e.y in hr.top..hr.bottom)
                        val topHit = near(e.x, e.y, hr.centerX(), hr.top, 30f) ||
                            (abs(e.y - hr.top) <= 24 * d && e.x in hr.left..hr.right)
                        val gapHit = isSplitActive() && (near(e.x, e.y, hr.centerX(), hr.centerY(), 30f) ||
                            (abs(e.x - hr.centerX()) <= 20 * d && e.y in hr.top..hr.bottom))
                        dragTarget = when {
                            gapHit -> DragTarget.KB_GAP
                            rightHit -> DragTarget.KB_WIDTH
                            topHit -> DragTarget.KB_HEIGHT
                            else -> DragTarget.NONE
                        }
                    }
                    3 -> {
                        if (fnTab.visibility == VISIBLE) {
                            val tr = tabRect()
                            if (near(e.x, e.y, tr.right, tr.bottom, 26f)) {
                                dragTarget = DragTarget.TAB_RESIZE
                            } else if (e.x in (tr.left - 12 * d)..(tr.right + 12 * d) && e.y in (tr.top - 12 * d)..(tr.bottom + 12 * d)) {
                                dragTarget = DragTarget.TAB_MOVE
                            }
                        }
                    }
                    4 -> {
                        val sx = hr.left + keyboard.stripRightPx()
                        val sy = hr.top + keyboard.spaceRowTopPx()
                        val numHit = keyboard.hasSideStrip && abs(e.x - sx) <= 26 * d && e.y in hr.top..hr.bottom
                        val spaceHit = sy > hr.top && abs(e.y - sy) <= 26 * d && e.x in hr.left..hr.right
                        dragTarget = when {
                            numHit -> DragTarget.NUM_WIDTH
                            spaceHit -> DragTarget.SPACE_HEIGHT
                            else -> DragTarget.NONE
                        }
                    }
                    6 -> {
                        val ys = keyboard.hitZoneYs()
                        var bestIdx = -1
                        var bestDist = 28f * d
                        for (r in 1 until ys.size) {
                            val dist = abs(e.y - (hr.top + ys[r]))
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
                val dx = e.x - downX
                val dy = e.y - downY
                val cw = getContainerWidth().coerceAtLeast(1)
                val updated = when (dragTarget) {
                    DragTarget.KB_WIDTH -> {
                        val deltaPct = (dx / cw * 100f).roundToInt()
                        startGeo.copy(widthPct = startGeo.widthPct + deltaPct).clamped()
                    }
                    DragTarget.KB_HEIGHT -> {
                        val baseH = (keyboard.height.takeIf { it > 0 } ?: 1).toFloat() / (startGeo.heightPct / 100f)
                        val deltaPct = (-dy / baseH * 100f).roundToInt()
                        startGeo.copy(heightPct = startGeo.heightPct + deltaPct).clamped()
                    }
                    DragTarget.KB_GAP -> {
                        val deltaPct = (dx / cw * 160f).roundToInt()
                        startGeo.copy(gapPct = startGeo.gapPct + deltaPct).clamped()
                    }
                    DragTarget.TAB_MOVE -> {
                        val dxDp = if (keyboard.hasTopNumberRow) 0 else (dx / d).roundToInt()
                        val dyDp = (dy / d).roundToInt()
                        startGeo.copy(
                            fnTabXdp = startGeo.fnTabXdp + dxDp,
                            fnTabYdp = startGeo.fnTabYdp + dyDp,
                        ).clamped()
                    }
                    DragTarget.TAB_RESIZE -> {
                        val baseW = (fnTab.width.takeIf { it > 0 } ?: 1).toFloat() / (startGeo.fnTabWidthPct / 100f)
                        val baseH = (fnTab.height.takeIf { it > 0 } ?: 1).toFloat() / (startGeo.fnTabHeightPct / 100f)
                        val wPct = if (keyboard.hasTopNumberRow) startGeo.fnTabWidthPct else startGeo.fnTabWidthPct + (dx / baseW * 100f).roundToInt()
                        val hPct = startGeo.fnTabHeightPct + (dy / baseH * 100f).roundToInt()
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
                        val deltaDp = (dy / d).roundToInt()
                        val first = if ((keyboard.hasSideStrip || keyboard.hasTopNumberRow) && keyboard.keyboardLayout?.moaki != true) 1 else 0
                        when (hitLineIndex - 1 + first) {
                            0 -> startGeo.copy(hit0Dp = startGeo.hit0Dp + deltaDp).clamped()
                            1 -> startGeo.copy(hit1Dp = startGeo.hit1Dp + deltaDp).clamped()
                            2 -> startGeo.copy(hit2Dp = startGeo.hit2Dp + deltaDp).clamped()
                            3 -> startGeo.copy(hit3Dp = startGeo.hit3Dp + deltaDp).clamped()
                            else -> startGeo
                        }
                    }
                    DragTarget.NONE -> startGeo
                }
                onGeometryChanged(updated)
                invalidate()
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
