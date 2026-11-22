package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.KeyboardLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ShiftState { OFF, ON, LOCK }

/**
 * 자판 데이터를 Canvas로 그리고 터치를 키 이벤트로 변환하는 뷰.
 *
 * 제스처
 * - 탭: 키 입력 (백스페이스는 누르는 즉시 + 길게 누르면 반복)
 * - 글자 키 꾹: 후보(숫자·특수기호) 말풍선 → 손가락 밀어서 선택, 떼면 입력
 * - 엔터 꾹: 상용문구 목록 → 손가락을 올려 선택, 떼면 입력 (선택 없이 떼면 취소)
 * - 스페이스 좌우 스와이프: 한/영 전환
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(key: Key)
        /** 꾹 누르기 팝업에서 선택된 문자열 (특수기호, 상용문구) */
        fun onText(text: String)
        /** 스페이스바 스와이프 */
        fun onSwipeSpace()
        /** 글자 키 위 좌우 스와이프: 커서 이동 (음수=왼쪽, 양수=오른쪽, 절댓값=글자 수) */
        fun onCursorMove(steps: Int)
        /** 커서 이동 제스처(스와이프, ◁▷ 키)가 끝남 */
        fun onCursorMoveEnd()
    }

    var listener: Listener? = null

    /** 진동 피드백 (키 누를 때, 꾹 누르기, 스와이프) */
    var haptics: Haptics? = null

    var theme: KeyboardTheme = KeyboardTheme.system(context)
        set(v) { field = v; requestLayout(); invalidate() }

    var keyboardLayout: KeyboardLayout? = null
        set(v) { field = v; resetGesture(); computeCells(); requestLayout(); invalidate() }

    var shiftState: ShiftState = ShiftState.OFF
        set(v) { field = v; invalidate() }

    /** 음성인식 듣는 중: 마이크 키를 강조색으로 표시 */
    var voiceListening: Boolean = false
        set(v) { field = v; invalidate() }

    /** 엔터 꾹 누르기로 보여줄 상용문구 */
    var phrases: List<String> = emptyList()

    private class Cell(val key: Key, val rect: RectF)

    /** 꾹 누르기 팝업. [rects]는 항목별 영역(후보 가로 나열 / 문구 세로 목록) */
    private class Popup(
        val items: List<String>,
        val rects: List<RectF>,
        val vertical: Boolean,
        var selected: Int,
    )

    private var cells: List<Cell> = emptyList()
    private var pressed: Cell? = null
    private var popup: Popup? = null
    private var downX = 0f
    private var downY = 0f
    private var rowTops = FloatArray(0)
    /** 글자 키 위 스와이프로 커서 이동 중 */
    private var cursorDrag = false
    private var dragAnchorX = 0f
    /** 스와이프로 자판이 바뀐 뒤, 손가락을 뗄 때까지 나머지 터치 무시 */
    private var ignoreUntilUp = false
    private var bottomInset = 0

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())

    private val repeatRunnable = object : Runnable {
        override fun run() {
            pressed?.let { listener?.onKey(it.key) }
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    private val longPressRunnable = Runnable {
        val cell = pressed ?: return@Runnable
        val p = buildPopup(cell) ?: return@Runnable
        popup = p
        haptics?.longPress()
        invalidate()
    }

    init {
        // Android 15+ edge-to-edge: 하단 내비게이션 바 영역만큼 여백 확보
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            if (b != bottomInset) { bottomInset = b; requestLayout() }
            insets
        }
    }

    private val rowCount get() = keyboardLayout?.rows?.size ?: 0
    private val keyHeightPx get() = theme.keyHeightDp * density
    private val padPx get() = 4 * density

    private fun rowHeightPx(r: Int) = keyHeightPx * (keyboardLayout?.rowHeights?.getOrNull(r) ?: 1f)

    private fun contentHeight() = (0 until rowCount).sumOf { rowHeightPx(it).toDouble() }.toFloat()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (contentHeight() + 2 * padPx).toInt() + bottomInset
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeCells()
    }

    private fun computeCells() {
        val layout = keyboardLayout ?: run { cells = emptyList(); return }
        if (width == 0) return
        val out = ArrayList<Cell>()
        val usableW = width - 2 * padPx
        rowTops = FloatArray(layout.rows.size)
        var top = padPx
        layout.rows.forEachIndexed { r, row ->
            val rowH = rowHeightPx(r)
            rowTops[r] = top
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = padPx
            for (key in row) {
                val w = usableW * key.weight / total
                if (key.type != KeyType.SPACER) out += Cell(key, RectF(x, top, x + w, top + rowH))
                x += w
            }
            top += rowH
        }
        cells = out
    }

    // ---------------------------------------------------------------- 그리기

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val gap = 3 * density
        val radius = 7 * density
        for (cell in cells) {
            val key = cell.key
            val isSpecial = key.type !in NORMAL_STYLE_TYPES
            paint.color = when {
                cell === pressed -> theme.keyPressed
                key.type == KeyType.VOICE && voiceListening -> theme.accent
                key.type == KeyType.SHIFT && shiftState != ShiftState.OFF -> theme.keyPressed
                isSpecial -> theme.specialKey
                else -> theme.key
            }
            val r = RectF(cell.rect).apply { inset(gap, gap) }
            canvas.drawRoundRect(r, radius, radius, paint)

            val label = when {
                key.type == KeyType.SHIFT && shiftState == ShiftState.LOCK -> "⇪"
                key.type == KeyType.SPACE -> keyboardLayout?.name.orEmpty()
                else -> key.labelFor(shiftState != ShiftState.OFF)
            }
            if (label.isEmpty()) continue
            paint.color = if (key.type == KeyType.VOICE && voiceListening) theme.onAccent else theme.text
            val size = when {
                key.type == KeyType.SPACE -> 0.26f
                key.type in TOOLBAR_ACTION_TYPES -> 0.34f
                isSpecial -> 0.30f
                else -> 0.40f
            }
            paint.textSize = cell.rect.height() * size * theme.textScale
            if (key.type == KeyType.SPACE) paint.alpha = 140
            drawCentered(canvas, label, r)
            paint.alpha = 255

            // 꾹 누르기 후보가 있는 글자 키: 오른쪽 위에 작은 힌트
            if (key.type == KeyType.CHAR && key.hints.isNotEmpty() && key.hints[0].length <= 2) {
                paint.color = theme.text
                paint.alpha = 110
                paint.textSize = cell.rect.height() * 0.22f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(key.hints[0], r.right - 4 * density, r.top + paint.textSize + 2 * density, paint)
                paint.textAlign = Paint.Align.CENTER
                paint.alpha = 255
            }
        }
        popup?.let { drawPopup(canvas, it) }
    }

    private fun drawCentered(canvas: Canvas, text: String, r: RectF) {
        val y = r.centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
        canvas.drawText(text, r.centerX(), y, paint)
    }

    private fun drawPopup(canvas: Canvas, p: Popup) {
        val gap = 3 * density
        val radius = 8 * density
        val bounds = RectF(p.rects.first())
        p.rects.forEach { bounds.union(it) }
        bounds.inset(-gap, -gap)

        // 배경 + 테두리
        paint.color = theme.key
        canvas.drawRoundRect(bounds, radius + gap, radius + gap, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = theme.keyPressed
        canvas.drawRoundRect(bounds, radius + gap, radius + gap, paint)
        paint.style = Paint.Style.FILL

        p.rects.forEachIndexed { i, rect ->
            val r = RectF(rect).apply { inset(gap, gap) }
            val sel = i == p.selected
            if (sel) {
                paint.color = theme.accent
                canvas.drawRoundRect(r, radius, radius, paint)
            }
            paint.color = if (sel) theme.onAccent else theme.text
            if (p.vertical) {
                paint.textSize = keyHeightPx * 0.30f * theme.textScale
                paint.textAlign = Paint.Align.LEFT
                val text = TextUtils.ellipsize(
                    p.items[i], android.text.TextPaint(paint), r.width() - 16 * density, TextUtils.TruncateAt.END,
                ).toString()
                val y = r.centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
                canvas.drawText(text, r.left + 8 * density, y, paint)
                paint.textAlign = Paint.Align.CENTER
            } else {
                paint.textSize = keyHeightPx * 0.42f * theme.textScale
                drawCentered(canvas, p.items[i], r)
            }
        }
    }

    // ---------------------------------------------------------------- 터치

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (ignoreUntilUp && e.actionMasked != MotionEvent.ACTION_DOWN) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                ignoreUntilUp = false
                resetGesture()
            }
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ignoreUntilUp = false
                downX = e.x
                downY = e.y
                pressed = hit(e.x, e.y)
                if (pressed != null) haptics?.key()
                invalidate()
                onPressedChanged()
            }
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_UP -> onUp()
            MotionEvent.ACTION_CANCEL -> {
                val cursorGesture = isCursorGesture(pressed)
                resetGesture()
                if (cursorGesture) listener?.onCursorMoveEnd()
            }
        }
        return true
    }

    /** 새로 눌린 키에 맞게 반복·꾹 누르기 타이머 시작 */
    private fun onPressedChanged() {
        val p = pressed ?: return
        when {
            p.key.type in REPEAT_TYPES -> {
                listener?.onKey(p.key)
                handler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
            }
            hasLongPress(p.key) -> handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
        }
    }

    private fun onMove(e: MotionEvent) {
        // 팝업이 열린 상태: 선택 항목만 갱신
        popup?.let {
            updateSelection(it, e.x, e.y)
            invalidate()
            return
        }
        // 스페이스바: 좌우로 충분히 밀면 한/영 전환 (한 제스처에 1회)
        if (pressed?.key?.type == KeyType.SPACE) {
            if (abs(e.x - downX) > SWIPE_THRESHOLD_DP * density) {
                ignoreUntilUp = true          // 자판이 바뀌면 셀이 교체되므로 이후 터치는 무시
                stopTimers()
                haptics?.tick()
                listener?.onSwipeSpace()
                invalidate()
            }
            return
        }
        // 글자 키 위에서 좌우로 밀면 커서 이동
        if (cursorDrag || startsCursorDrag(e)) {
            handleCursorDrag(e)
            return
        }
        val h = hit(e.x, e.y)
        if (h !== pressed) {
            stopTimers()
            pressed = h
            invalidate()
            onPressedChanged()
        }
    }

    private fun startsCursorDrag(e: MotionEvent): Boolean {
        val key = pressed?.key ?: return false
        if (key.type != KeyType.CHAR) return false
        val dx = e.x - downX
        val dy = e.y - downY
        return abs(dx) > DRAG_START_DP * density && abs(dx) > abs(dy) * 1.2f
    }

    private fun handleCursorDrag(e: MotionEvent) {
        if (!cursorDrag) {
            cursorDrag = true
            dragAnchorX = e.x
            stopTimers()
            pressed = null          // 뗄 때 글자가 입력되지 않도록
            invalidate()
            return
        }
        val stepPx = DRAG_STEP_DP * density
        val steps = ((e.x - dragAnchorX) / stepPx).toInt()
        if (steps != 0) {
            dragAnchorX += steps * stepPx
            haptics?.tick()
            listener?.onCursorMove(steps)
        }
    }

    private fun onUp() {
        val p = pressed
        val pop = popup
        val repeating = p?.key?.type in REPEAT_TYPES
        val cursorGesture = isCursorGesture(p)
        resetGesture()
        when {
            pop != null -> if (pop.selected >= 0) listener?.onText(pop.items[pop.selected])
            p != null && !repeating -> {
                performClick()
                listener?.onKey(p.key)
            }
        }
        if (cursorGesture) listener?.onCursorMoveEnd()
    }

    /** 커서를 움직인 제스처인가 (스와이프 이동 중이거나 ◁▷ 키) */
    private fun isCursorGesture(p: Cell?) =
        cursorDrag || p?.key?.type == KeyType.LEFT || p?.key?.type == KeyType.RIGHT

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun resetGesture() {
        stopTimers()
        pressed = null
        popup = null
        cursorDrag = false
        invalidate()
    }

    private fun stopTimers() {
        handler.removeCallbacks(repeatRunnable)
        handler.removeCallbacks(longPressRunnable)
    }

    private fun hasLongPress(key: Key) = when (key.type) {
        KeyType.CHAR -> key.hints.isNotEmpty()
        KeyType.ENTER -> phrases.isNotEmpty()
        else -> false
    }

    // ---------------------------------------------------------------- 팝업

    private fun buildPopup(cell: Cell): Popup? {
        val key = cell.key
        val keyH = cell.rect.height()
        return when (key.type) {
            KeyType.CHAR -> {
                if (key.hints.isEmpty()) return null
                val n = key.hints.size
                var cellW = max(cell.rect.width(), 44 * density)
                cellW = min(cellW, (width - 2 * padPx) / n)
                val total = cellW * n
                val left = cell.rect.left.coerceAtMost(width - padPx - total).coerceAtLeast(padPx)
                val top = (cell.rect.top - keyH).coerceAtLeast(0f)
                val rects = List(n) { RectF(left + it * cellW, top, left + (it + 1) * cellW, top + keyH) }
                Popup(key.hints, rects, vertical = false, selected = 0)
            }
            KeyType.ENTER -> {
                if (phrases.isEmpty()) return null
                val n = min(phrases.size, 4)
                val itemH = keyHeightPx * 0.75f
                val w = min(width - 2 * padPx, 240 * density)
                val left = (cell.rect.right - w).coerceAtLeast(padPx)
                // 마지막 줄 바로 위부터 위로 쌓는다 (0번이 가장 아래, 손가락에 가까움)
                val bottom = rowTops.last()
                val rects = List(n) { RectF(left, bottom - (it + 1) * itemH, left + w, bottom - it * itemH) }
                Popup(phrases.take(n), rects, vertical = true, selected = -1)
            }
            else -> null
        }
    }

    private fun updateSelection(p: Popup, x: Float, y: Float) {
        if (p.vertical) {
            p.selected = p.rects.indexOfFirst { x >= it.left && x <= it.right && y >= it.top && y < it.bottom }
        } else if (abs(x - downX) > SLOP_DP * density) {
            val first = p.rects.first().left
            val w = p.rects.first().width()
            p.selected = ((x - first) / w).toInt().coerceIn(0, p.rects.size - 1)
        }
    }

    /** 정확히 맞는 키가 없으면 같은 행에서 가장 가까운 키 (키 사이 틈 터치 보정) */
    private fun hit(x: Float, y: Float): Cell? {
        if (cells.isEmpty()) return null
        val row = rowTops.indexOfLast { it <= y }.coerceAtLeast(0)
        val top = rowTops[row]
        return cells.filter { it.rect.top == top }.minByOrNull {
            abs(x - x.coerceIn(it.rect.left, it.rect.right))
        }
    }

    override fun onDetachedFromWindow() { stopTimers(); super.onDetachedFromWindow() }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 45L
        const val LONG_PRESS_MS = 350L
        const val SWIPE_THRESHOLD_DP = 40f
        const val SLOP_DP = 8f
        const val DRAG_START_DP = 24f
        const val DRAG_STEP_DP = 14f

        /** 누르고 있으면 반복되는 키 */
        val REPEAT_TYPES = setOf(KeyType.BACKSPACE, KeyType.LEFT, KeyType.RIGHT)
        /** 상단 편집 줄: 일반 키 색, 작은 글씨 */
        val TOOLBAR_ACTION_TYPES = setOf(KeyType.LEFT, KeyType.RIGHT, KeyType.SELECT_ALL, KeyType.COPY, KeyType.PASTE)
        /** 일반 키 배경색을 쓰는 키 */
        val NORMAL_STYLE_TYPES = TOOLBAR_ACTION_TYPES + setOf(KeyType.CHAR, KeyType.SPACE)
    }
}
