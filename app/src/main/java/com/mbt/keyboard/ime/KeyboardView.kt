package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.KeyboardLayout
import com.mbt.keyboard.layout.Split
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ShiftState { OFF, ON, LOCK }

/**
 * 자판 데이터를 Canvas로 그리고 터치를 키 이벤트로 변환하는 뷰.
 *
 * 터치는 손가락(pointer)마다 독립적으로 처리한다. 앞 키를 떼기 전에 다음 키를 눌러도(양손·빠른 타자)
 * 입력이 빠지지 않는다.
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
        /** 커서 이동 제스처(스와이프)가 끝남 */
        fun onCursorMoveEnd()
        /** 쉬프트 키를 누름(true)/뗌(false). 누른 채로 다른 키를 치면 그 동안만 쉬프트 */
        fun onShiftPress(down: Boolean)
    }

    var listener: Listener? = null

    /** 진동 피드백 (키 누를 때, 꾹 누르기, 스와이프) */
    var haptics: Haptics? = null

    var theme: KeyboardTheme = KeyboardTheme.system(context)
        set(v) { field = v; requestLayout(); invalidate() }

    var keyboardLayout: KeyboardLayout? = null
        set(v) { field = v; dropGestures(); computeCells(); requestLayout(); invalidate() }

    var shiftState: ShiftState = ShiftState.OFF
        set(v) { field = v; invalidate() }

    /** 나눠쓰기(갈라진 자판): 폴드를 펼친 넓은 화면에서 가운데를 비우고 양손으로 나눈다 */
    var split: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            dropGestures()
            computeCells()
            invalidate()
        }

    /** 음성인식 듣는 중: 마이크 키를 강조색으로 표시 */
    var voiceListening: Boolean = false
        set(v) { field = v; invalidate() }

    /** 엔터 꾹 누르기로 보여줄 상용문구 */
    var phrases: List<String> = emptyList()

    /** [rect]는 터치 판정 영역, [inner]는 그려지는 영역(키 사이 틈 제외, 미리 계산해 그릴 때 객체를 만들지 않음) */
    private class Cell(val key: Key, val rect: RectF, val inner: RectF)

    /** 꾹 누르기 팝업. [rects]는 항목별 영역(후보 가로 나열 / 문구 세로 목록) */
    private class Popup(
        val items: List<String>,
        val rects: List<RectF>,
        val vertical: Boolean,
        var selected: Int,
    )

    /** 손가락 하나의 제스처 상태 */
    private inner class Pointer(val downX: Float, val downY: Float) {
        var cell: Cell? = null
        var popup: Popup? = null
        /** 글자 키 위 스와이프로 커서 이동 중 */
        var cursorDrag = false
        var dragAnchorX = 0f
        /** 쉬프트 키를 누르고 있음 */
        var shiftHeld = false
        /** 자판이 바뀌는 등으로 손가락을 뗄 때까지 무시 */
        var ignore = false

        val longPress = Runnable {
            val c = cell ?: return@Runnable
            val pop = buildPopup(c) ?: return@Runnable
            popup = pop
            haptics?.longPress()
            invalidate()
        }

        val repeat = object : Runnable {
            override fun run() {
                cell?.let { listener?.onKey(it.key) }
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }

        fun cancelTimers() {
            handler.removeCallbacks(longPress)
            handler.removeCallbacks(repeat)
        }
    }

    private var cells: List<Cell> = emptyList()
    private var rowCells: List<List<Cell>> = emptyList()
    private val pointers = SparseArray<Pointer>()
    private var rowTops = FloatArray(0)
    private var bottomInset = 0

    /** 하단 내비게이션 바 여백 (이모지 패널이 같은 여백을 쓰도록 공개) */
    val bottomInsetPx: Int get() = bottomInset

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())
    private val exclusionRects = listOf(Rect(), Rect())

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
        val rows = ArrayList<List<Cell>>()
        val gap = 3 * density
        val usableW = width - 2 * padPx
        rowTops = FloatArray(layout.rows.size)
        var top = padPx
        layout.rows.forEachIndexed { r, row ->
            val rowH = rowHeightPx(r)
            rowTops[r] = top
            val rowList = ArrayList<Cell>()
            fun place(parts: List<Split.Part>, startX: Float, spanW: Float) {
                val total = parts.sumOf { it.weight.toDouble() }.toFloat()
                if (total <= 0f) return
                var x = startX
                for (part in parts) {
                    val w = spanW * part.weight / total
                    if (part.key.type != KeyType.SPACER) {
                        val rect = RectF(x, top, x + w, top + rowH)
                        val cell = Cell(part.key, rect, RectF(rect).apply { inset(gap, gap) })
                        out += cell
                        rowList += cell
                    }
                    x += w
                }
            }
            if (split) {
                // 왼손/오른손 두 묶음으로 나눠 각각 자기 쪽 절반을 채운다
                val halves = Split.halves(row, layout.splitAt.getOrNull(r))
                val gapW = usableW * SPLIT_GAP_FRACTION
                val halfW = (usableW - gapW) / 2f
                place(halves.left, padPx, halfW)
                place(halves.right, padPx + halfW + gapW, halfW)
            } else {
                place(row.map { Split.Part(it, it.weight) }, padPx, usableW)
            }
            rows += rowList
            top += rowH
        }
        cells = out
        rowCells = rows
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // 화면 가장자리 키를 누를 때 시스템 뒤로가기 제스처가 터치를 가로채 입력이 씹히는 것을 방지
        val w = right - left
        val h = bottom - top
        val edge = (48 * density).toInt()
        val bandTop = (h - (200 * density).toInt()).coerceAtLeast(0)
        exclusionRects[0].set(0, bandTop, edge, h)
        exclusionRects[1].set(w - edge, bandTop, w, h)
        ViewCompat.setSystemGestureExclusionRects(this, exclusionRects)
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
                isPressed(cell) -> theme.keyPressed
                key.type == KeyType.VOICE && voiceListening -> theme.accent
                key.type == KeyType.SHIFT && shiftState != ShiftState.OFF -> theme.keyPressed
                isSpecial -> theme.specialKey
                else -> theme.key
            }
            val r = cell.inner
            canvas.drawRoundRect(r, radius, radius, paint)

            val label = when {
                key.type == KeyType.SHIFT && shiftState == ShiftState.LOCK -> "⇪"
                // 나눠쓰기에서 스페이스바가 둘로 갈라져도 자판 이름은 왼쪽에만 표시
                key.type == KeyType.SPACE -> if (cell.rect.left >= width / 2f) "" else keyboardLayout?.name.orEmpty()
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
        for (i in 0 until pointers.size()) pointers.valueAt(i).popup?.let { drawPopup(canvas, it) }
    }

    private fun isPressed(cell: Cell): Boolean {
        for (i in 0 until pointers.size()) if (pointers.valueAt(i).cell === cell) return true
        return false
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
                    p.items[i], TextPaint(paint), r.width() - 16 * density, TextUtils.TruncateAt.END,
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
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                onDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = pointers.get(e.getPointerId(i)) ?: continue
                    onMove(p, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onUp(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        pointers.get(id)?.let { finish(it, fire = false) }   // 비정상적으로 남은 상태 정리
        val p = Pointer(x, y)
        pointers.put(id, p)
        p.cell = hit(x, y)
        if (p.cell != null) haptics?.key()
        invalidateCell(p.cell)
        onPressedChanged(p, isDown = true)
    }

    /** 새로 눌린 키에 맞게 쉬프트·반복·꾹 누르기 처리 시작 */
    private fun onPressedChanged(p: Pointer, isDown: Boolean) {
        val c = p.cell ?: return
        when {
            c.key.type == KeyType.SHIFT -> if (isDown) {
                p.shiftHeld = true
                listener?.onShiftPress(true)
            }
            c.key.type in REPEAT_TYPES -> {
                listener?.onKey(c.key)
                handler.postDelayed(p.repeat, REPEAT_DELAY_MS)
            }
            hasLongPress(c.key) -> handler.postDelayed(p.longPress, LONG_PRESS_MS)
        }
    }

    private fun onMove(p: Pointer, x: Float, y: Float) {
        if (p.ignore || p.shiftHeld) return
        // 팝업이 열린 상태: 선택 항목만 갱신
        p.popup?.let {
            updateSelection(p, it, x, y)
            invalidate()
            return
        }
        // 스페이스바: 좌우로 충분히 밀면 한/영 전환 (한 제스처에 1회)
        if (p.cell?.key?.type == KeyType.SPACE) {
            if (abs(x - p.downX) > SWIPE_THRESHOLD_DP * density) {
                p.ignore = true               // 자판이 바뀌면 셀이 교체되므로 이후 터치는 무시
                p.cancelTimers()
                invalidateCell(p.cell)
                haptics?.tick()
                listener?.onSwipeSpace()
            }
            return
        }
        // 글자 키 위에서 좌우로 밀면 커서 이동
        if (p.cursorDrag || startsCursorDrag(p, x, y)) {
            handleCursorDrag(p, x)
            return
        }
        val h = hit(x, y)
        if (h !== p.cell) {
            p.cancelTimers()
            invalidateCell(p.cell)
            p.cell = h
            invalidateCell(h)
            onPressedChanged(p, isDown = false)
        }
    }

    private fun startsCursorDrag(p: Pointer, x: Float, y: Float): Boolean {
        if (p.cell?.key?.type != KeyType.CHAR) return false
        val dx = x - p.downX
        val dy = y - p.downY
        return abs(dx) > DRAG_START_DP * density && abs(dx) > abs(dy) * 1.2f
    }

    private fun handleCursorDrag(p: Pointer, x: Float) {
        if (!p.cursorDrag) {
            p.cursorDrag = true
            p.dragAnchorX = x
            p.cancelTimers()
            invalidateCell(p.cell)
            p.cell = null          // 뗄 때 글자가 입력되지 않도록
            return
        }
        val stepPx = DRAG_STEP_DP * density
        val steps = ((x - p.dragAnchorX) / stepPx).toInt()
        if (steps != 0) {
            p.dragAnchorX += steps * stepPx
            haptics?.tick()
            listener?.onCursorMove(steps)
        }
    }

    private fun onUp(id: Int) {
        val p = pointers.get(id) ?: return
        finish(p, fire = true)
        pointers.remove(id)
    }

    /** 손가락 하나의 제스처를 끝낸다. [fire]가 true면 키 입력·팝업 선택을 실행 */
    private fun finish(p: Pointer, fire: Boolean) {
        p.cancelTimers()
        val cell = p.cell
        val pop = p.popup
        p.cell = null
        p.popup = null
        if (pop != null) invalidate() else invalidateCell(cell)

        if (p.shiftHeld) {
            p.shiftHeld = false
            listener?.onShiftPress(false)
        }
        if (fire && !p.ignore) {
            when {
                pop != null -> if (pop.selected >= 0) listener?.onText(pop.items[pop.selected])
                cell != null && cell.key.type == KeyType.SHIFT -> {
                    // 다른 키에서 쉬프트 위로 미끄러져 뗀 경우: 한 번 누른 것으로 처리
                    listener?.onShiftPress(true)
                    listener?.onShiftPress(false)
                }
                cell != null && cell.key.type !in REPEAT_TYPES -> listener?.onKey(cell.key)
            }
        }
        if (p.cursorDrag) {
            p.cursorDrag = false
            listener?.onCursorMoveEnd()
        }
    }

    private fun cancelAll() {
        val all = (0 until pointers.size()).map { pointers.valueAt(it) }
        pointers.clear()
        all.forEach { finish(it, fire = false) }
    }

    /** 자판이 바뀌었을 때: 진행 중인 모든 손가락은 뗄 때까지 무시 (셀이 교체돼 의미가 없어짐) */
    private fun dropGestures() {
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            p.cancelTimers()
            p.cell = null
            p.popup = null
            p.ignore = true
        }
        invalidate()
    }

    /** 키 하나만 다시 그리기 (전체를 다시 그리지 않아 반응이 빠르다) */
    private fun invalidateCell(c: Cell?) {
        if (c == null) return
        invalidate(
            c.rect.left.toInt(), c.rect.top.toInt(),
            kotlin.math.ceil(c.rect.right).toInt(), kotlin.math.ceil(c.rect.bottom).toInt(),
        )
    }

    override fun performClick(): Boolean { super.performClick(); return true }

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

    private fun updateSelection(ptr: Pointer, p: Popup, x: Float, y: Float) {
        if (p.vertical) {
            p.selected = p.rects.indexOfFirst { x >= it.left && x <= it.right && y >= it.top && y < it.bottom }
        } else if (abs(x - ptr.downX) > SLOP_DP * density) {
            val first = p.rects.first().left
            val w = p.rects.first().width()
            p.selected = ((x - first) / w).toInt().coerceIn(0, p.rects.size - 1)
        }
    }

    /** 정확히 맞는 키가 없으면 같은 행에서 가장 가까운 키 (키 사이 틈 터치 보정) */
    private fun hit(x: Float, y: Float): Cell? {
        if (rowCells.isEmpty()) return null
        var row = rowTops.indexOfLast { it <= y }
        if (row < 0) row = 0
        var best: Cell? = null
        var bestDist = Float.MAX_VALUE
        for (c in rowCells[row]) {           // 객체를 만들지 않고 가장 가까운 키를 찾는다
            val d = abs(x - x.coerceIn(c.rect.left, c.rect.right))
            if (d < bestDist) { bestDist = d; best = c }
        }
        return best
    }

    override fun onDetachedFromWindow() {
        for (i in 0 until pointers.size()) pointers.valueAt(i).cancelTimers()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 45L
        const val LONG_PRESS_MS = 350L
        const val SWIPE_THRESHOLD_DP = 40f
        const val SLOP_DP = 8f
        const val DRAG_START_DP = 24f
        const val DRAG_STEP_DP = 14f

        /** 나눠쓰기 때 가운데 빈 공간: 키보드 가로폭의 18% */
        const val SPLIT_GAP_FRACTION = 0.18f

        /** 누르고 있으면 반복되는 키 */
        val REPEAT_TYPES = setOf(KeyType.BACKSPACE)
        /** 상단 편집 줄: 일반 키 색, 작은 글씨 */
        val TOOLBAR_ACTION_TYPES =
            setOf(KeyType.SELECT_ALL, KeyType.COPY, KeyType.PASTE, KeyType.SPELL, KeyType.VOICE)
        /** 일반 키 배경색을 쓰는 키 */
        val NORMAL_STYLE_TYPES = TOOLBAR_ACTION_TYPES + setOf(KeyType.CHAR, KeyType.SPACE)
    }
}
