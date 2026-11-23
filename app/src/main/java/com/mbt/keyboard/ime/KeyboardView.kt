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
import com.mbt.keyboard.settings.Timing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ShiftState { OFF, ON, LOCK }

/**
 * 키 둘레 여백(키보드 바깥 프레임)과 키 사이 간격. 단위 dp.
 * 기본값은 예전 고정값(둘레 4dp, 키 사이 6dp)과 같다.
 */
data class KeyboardFrame(
    val leftDp: Int = 4,
    val topDp: Int = 4,
    val rightDp: Int = 4,
    val bottomDp: Int = 4,
    val keyGapDp: Int = 6,
    /** 줄과 줄 사이 세로 여백. 넓혀도 키 크기는 그대로고 키보드가 그만큼 세로로 길어진다 */
    val rowGapDp: Int = 6,
    /** 맨 윗줄(전체·복사·붙임·숫자) 키 높이: 일반 키 높이 대비 배율 (기본 0.8) */
    val numberRowScale: Float = 0.8f,
    /** 숫자 줄과 그 아래 첫 글자 줄 사이 세로 여백 (키 크기는 그대로) */
    val numberGapDp: Int = 6,
)

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
        /** 엔터 꾹 누르기: 상용문구·최근기록 패널 열기 */
        fun onEnterLongPress()
        /** 지우기 키를 왼쪽으로 밀기: 지운 글자 되돌리기 */
        fun onUndoDelete() {}
    }

    var listener: Listener? = null

    /** 진동 피드백 (키 누를 때, 꾹 누르기, 스와이프) */
    var haptics: Haptics? = null

    /** 키 둘레 여백·키 사이 간격 (크기 조정 설정) */
    var frame: KeyboardFrame = KeyboardFrame()
        set(v) {
            if (field == v) return
            field = v
            computeCells()
            requestLayout()
            invalidate()
        }

    /** 나눠쓰기 틈: 키보드 가로폭 대비 비율 (크기 조정 설정의 "갈라진 정도") */
    var splitGapFraction: Float = 0.18f
        set(v) {
            if (field == v) return
            field = v
            computeCells()
            invalidate()
        }

    /** 키보드 세로 길이 배율 (크기 조정 설정). 1.0 = 기본 */
    var heightScale: Float = 1f
        set(v) {
            if (field == v) return
            field = v
            computeCells()
            requestLayout()
            invalidate()
        }

    /** 꾹 누르기로 인식하는 시간 (설정 화면에서 변경) */
    var longPressMs: Long = Timing.LONG_PRESS_DEFAULT_MS

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

    /**
     * 영어 갈라진 자판에서 안쪽 가장자리에 반대편 손의 첫 글자를 한 개씩 복제해 보여준다 (Gboard식).
     * 엄지가 닿기 어려운 가운데 글자(t·y, g·h, b·n)를 양쪽에서 칠 수 있다. 한글 자판은 해당 없음.
     */
    var splitDuplicate: Boolean = false
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

    private var message: String? = null
    private val clearMessage = Runnable { message = null; invalidate() }

    /**
     * 키보드 위쪽에 잠깐 결과 문구를 띄운다 (맞춤법 결과, 음성인식 오류 등).
     * 토스트는 키보드에 가리거나 꺼져 있으면 안 보이므로 키보드 안에서 확실히 보여준다.
     */
    fun showMessage(text: String, durationMs: Long = 3500L) {
        message = text
        handler.removeCallbacks(clearMessage)
        handler.postDelayed(clearMessage, durationMs)
        invalidate()
    }

    private var undoMark = false
    private val clearUndoMark = Runnable { undoMark = false; invalidate() }

    /** 지운 글자를 되돌렸을 때: 글자 없이 불투명한 ↶ 표시만 잠깐 보여준다 */
    fun showUndoMark(durationMs: Long = UNDO_MARK_MS) {
        undoMark = true
        handler.removeCallbacks(clearUndoMark)
        handler.postDelayed(clearUndoMark, durationMs)
        invalidate()
    }

    /** [rect]는 터치 판정 영역, [inner]는 그려지는 영역(키 사이 틈 제외, 미리 계산해 그릴 때 객체를 만들지 않음) */
    private class Cell(val key: Key, val rect: RectF, val inner: RectF, val duplicate: Boolean = false)

    /** 꾹 누르기 팝업(후보 가로 나열). [rects]는 항목별 영역 */
    private class Popup(
        val items: List<String>,
        val rects: List<RectF>,
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
            if (c.key.type == KeyType.ENTER) {
                // 엔터 꾹: 상용문구·최근기록 패널을 연다. 손가락을 뗄 때 엔터가 입력되지 않도록 이 제스처는 무시
                ignore = true
                haptics?.longPress()
                invalidateCell(c)
                listener?.onEnterLongPress()
                return@Runnable
            }
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
    private val keyHeightPx get() = theme.keyHeightDp * density * heightScale
    private val padL get() = frame.leftDp * density
    private val padT get() = frame.topDp * density
    private val padR get() = frame.rightDp * density
    private val padB get() = frame.bottomDp * density

    /** 맨 윗줄이 전체·복사·붙임 + 숫자 줄인가 (글자 자판만. 키패드는 해당 없음) */
    private fun isNumberRow(r: Int) = r == 0 && keyboardLayout?.rows?.getOrNull(0)?.any { it.type == KeyType.SELECT_ALL } == true

    /**
     * 키 자체의 높이. 일반 키는 (키 높이 − 옛 고정 간격 6dp)이고, 줄마다 배율을 곱한다.
     * 숫자 줄은 사용자가 정한 배율(일반 키 대비)을 쓴다. 세로 여백과 무관하므로 여백을 넓혀도 키는 커지거나 작아지지 않는다.
     */
    private fun keyBodyPx(r: Int): Float {
        val base = (keyHeightPx - LEGACY_GAP_DP * density).coerceAtLeast(1f)
        val scale = if (isNumberRow(r)) frame.numberRowScale else (keyboardLayout?.rowHeights?.getOrNull(r) ?: 1f)
        return base * scale
    }

    /**
     * [r]줄과 그 아랫줄 사이 세로 여백(px). 숫자 줄 바로 아래는 따로 정한 값, 나머지는 줄 사이 여백.
     * 맨 윗줄 위·맨 아랫줄 아래 가장자리 여백은 줄 사이 여백과 같은 값을 쓴다.
     */
    private fun gapBelowPx(r: Int): Float {
        val dp = if (isNumberRow(r) && r + 1 < rowCount) frame.numberGapDp else frame.rowGapDp
        return dp * density
    }

    private fun gapAbovePx(r: Int) = if (r == 0) frame.rowGapDp * density else gapBelowPx(r - 1)

    /** 한 줄이 차지하는 세로 길이 = 키 높이 + 위아래 여백의 절반씩 (이웃 줄과 여백을 반반 나눠 가진다) */
    private fun rowHeightPx(r: Int) = keyBodyPx(r) + gapAbovePx(r) / 2f + gapBelowPx(r) / 2f

    private fun contentHeight() = (0 until rowCount).sumOf { rowHeightPx(it).toDouble() }.toFloat()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (contentHeight() + padT + padB).toInt() + bottomInset
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
        val usableW = width - padL - padR
        rowTops = FloatArray(layout.rows.size)
        var top = padT
        layout.rows.forEachIndexed { r, row ->
            val rowH = rowHeightPx(r)
            // 가로: 키 사이 간격의 절반씩 / 세로: 줄 사이 여백의 절반씩 비운다
            val gapX = frame.keyGapDp / 2f * density
            val gapTop = gapAbovePx(r) / 2f
            val gapBottom = gapBelowPx(r) / 2f
            rowTops[r] = top
            val rowList = ArrayList<Cell>()
            // 나눠쓰기 아닐 때는 틈 0인 한 묶음, 나눠쓰기일 때는 두 묶음 + 가운데 틈. 키 크기 비율은 항상 원래 레이아웃 그대로
            val doSplit = split && layout.splittable
            var halves = if (doSplit) Split.halves(row, layout.splitAt.getOrNull(r))
            else Split.Halves(row.map { Split.Part(it, it.weight) }, emptyList())
            val dups = HashSet<Split.Part>()
            if (doSplit && splitDuplicate && !layout.hangul) {
                val l = halves.left.lastOrNull()
                val rt = halves.right.firstOrNull()
                if (l != null && rt != null && l.key.type == KeyType.CHAR && rt.key.type == KeyType.CHAR) {
                    val dl = Split.Part(rt.key, DUPLICATE_WEIGHT)
                    val dr = Split.Part(l.key, DUPLICATE_WEIGHT)
                    dups += dl; dups += dr
                    halves = Split.Halves(halves.left + dl, listOf(dr) + halves.right)
                }
            }
            val gapW = if (doSplit) usableW * splitGapFraction else 0f
            for (pl in Split.place(halves, padL, usableW, gapW)) {
                if (pl.part.key.type == KeyType.SPACER) continue
                val rect = RectF(pl.left, top, pl.right, top + rowH)
                val cell = Cell(pl.part.key, rect, RectF(rect.left + gapX, rect.top + gapTop, rect.right - gapX, rect.bottom - gapBottom), pl.part in dups)
                out += cell
                rowList += cell
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
                isSpecial || cell.duplicate -> theme.specialKey      // 복제 키는 기능키 색으로 살짝 구분
                else -> theme.key
            }
            val r = cell.inner
            canvas.drawRoundRect(r, radius, radius, paint)

            // 스페이스바: 글자 대신 언어 표시. 나눠쓰기에서 스페이스바가 둘로 갈라져도 왼쪽에만 그린다
            if (key.type == KeyType.SPACE) {
                if (cell.rect.left < width / 2f) drawLanguageIndicator(canvas, r)
                continue
            }
            val label = when {
                key.type == KeyType.SHIFT && shiftState == ShiftState.LOCK -> "⇪"
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
            if (key.type == KeyType.VOICE) paint.textSize -= ICON_SHRINK_DP * density
            if (key.type == KeyType.SPACE || cell.duplicate) paint.alpha = 140
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
        message?.let { drawMessage(canvas, it) }
        if (undoMark) drawUndoMark(canvas)
    }

    /** 키보드 위쪽 가운데의 둥근 ↶ 표시. 완전히 불투명하고 글자는 없다 */
    private fun drawUndoMark(canvas: Canvas) {
        val d = UNDO_MARK_DP * density
        val cx = width / 2f
        val cy = padT + d / 2f + 4 * density
        paint.style = Paint.Style.FILL
        paint.color = theme.accent                    // 불투명 (알파 255)
        canvas.drawCircle(cx, cy, d / 2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.color = theme.onAccent
        canvas.drawCircle(cx, cy, d / 2f - 1.5f * density, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = d * 0.6f
        drawCentered(canvas, "↶", RectF(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f))
    }

    private fun drawMessage(canvas: Canvas, text: String) {
        val h = max(rowHeightPx(0), 40 * density)
        val r = RectF(padL + 3 * density, padT + 3 * density, width - padR - 3 * density, padT + h)
        paint.color = theme.accent
        canvas.drawRoundRect(r, 8 * density, 8 * density, paint)
        paint.color = theme.onAccent
        paint.textSize = 14 * density * theme.textScale
        val shown = TextUtils.ellipsize(text, TextPaint(paint), r.width() - 20 * density, TextUtils.TruncateAt.END)
        drawCentered(canvas, shown.toString(), r)
    }

    /** 키 위치 확인용 (테스트) */
    internal fun keyRects(): List<Pair<Key, RectF>> = cells.map { it.key to RectF(it.rect) }

    /** 실제로 그려지는 키 영역(키 사이 간격을 뺀 것) (테스트) */
    internal fun keyInnerRects(): List<Pair<Key, RectF>> = cells.map { it.key to RectF(it.inner) }

    private fun isPressed(cell: Cell): Boolean {
        for (i in 0 until pointers.size()) if (pointers.valueAt(i).cell === cell) return true
        return false
    }

    /**
     * 스페이스바 가운데의 언어 표시: `한` `EN` 두 토큰, 지금 쓰는 언어만 알약 배경 + 강조색.
     * 스와이프로 바꿀 수 있다는 걸 글자 없이 보여준다.
     */
    private fun drawLanguageIndicator(canvas: Canvas, r: RectF) {
        val korean = keyboardLayout?.hangul == true
        val tokens = listOf("한" to korean, "EN" to !korean)
        paint.textSize = r.height() * 0.34f * theme.textScale - ICON_SHRINK_DP * density
        val padH = 9 * density
        val between = 4 * density
        val boxH = paint.textSize * 1.75f
        val widths = tokens.map { paint.measureText(it.first) + 2 * padH }
        val total = widths.sum() + between
        if (total > r.width()) {
            // 칸이 좁으면(나눠쓰기 등) 지금 언어만
            val (text, _) = tokens.first { it.second }
            paint.color = theme.accent
            paint.isFakeBoldText = true
            drawCentered(canvas, text, r)
            paint.isFakeBoldText = false
            return
        }
        var x = r.centerX() - total / 2f
        tokens.forEachIndexed { i, (text, active) ->
            val box = RectF(x, r.centerY() - boxH / 2f, x + widths[i], r.centerY() + boxH / 2f)
            if (active) {
                paint.color = theme.accent
                paint.alpha = 56
                canvas.drawRoundRect(box, boxH / 2f, boxH / 2f, paint)
                paint.alpha = 255
                paint.color = theme.accent
                paint.isFakeBoldText = true
            } else {
                paint.color = theme.text
                paint.alpha = 105
            }
            drawCentered(canvas, text, box)
            paint.isFakeBoldText = false
            paint.alpha = 255
            x += widths[i] + between
        }
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
            paint.textSize = keyHeightPx * 0.42f * theme.textScale
            drawCentered(canvas, p.items[i], r)
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
            hasLongPress(c.key) -> handler.postDelayed(p.longPress, longPressMs)
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
        // 지우기 키: 왼쪽으로 충분히 밀면 지운 글자 되돌리기 (한 제스처에 1회)
        if (p.cell?.key?.type == KeyType.BACKSPACE) {
            if (p.downX - x > SWIPE_THRESHOLD_DP * density) {
                p.ignore = true
                p.cancelTimers()              // 꾹 눌러 반복 삭제 중이었다면 멈춘다
                invalidateCell(p.cell)
                haptics?.tick()
                listener?.onUndoDelete()
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

        val shiftWasHeld = p.shiftHeld
        if (shiftWasHeld) {
            p.shiftHeld = false
            listener?.onShiftPress(false)
        }
        if (fire && !p.ignore) {
            when {
                pop != null -> if (pop.selected >= 0) listener?.onText(pop.items[pop.selected])
                cell != null && cell.key.type == KeyType.SHIFT -> if (!shiftWasHeld) {
                    // 다른 키에서 쉬프트 위로 미끄러져 뗀 경우만: 한 번 누른 것으로 처리
                    // (쉬프트를 직접 눌렀다 뗀 경우는 위에서 이미 처리했으므로 다시 보내면 안 된다)
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
        KeyType.ENTER -> true
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
                // 칸 너비: 키 너비·최소 44dp, 그리고 가장 긴 후보(예: ".com")가 들어갈 만큼
                paint.textSize = keyHeightPx * 0.42f * theme.textScale
                val widest = key.hints.maxOf { paint.measureText(it) } + 16 * density
                var cellW = max(max(cell.rect.width(), 44 * density), widest)
                cellW = min(cellW, (width - padL - padR) / n)
                val total = cellW * n
                val left = cell.rect.left.coerceAtMost(width - padR - total).coerceAtLeast(padL)
                val top = (cell.rect.top - keyH).coerceAtLeast(0f)
                val rects = List(n) { RectF(left + it * cellW, top, left + (it + 1) * cellW, top + keyH) }
                Popup(key.hints, rects, selected = 0)
            }
            else -> null
        }
    }

    private fun updateSelection(ptr: Pointer, p: Popup, x: Float, y: Float) {
        if (abs(x - ptr.downX) > SLOP_DP * density) {
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
        handler.removeCallbacks(clearMessage)
        for (i in 0 until pointers.size()) pointers.valueAt(i).cancelTimers()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** 갈라진 자판의 복제 키 가로 비중 (원래 키는 1) */
        const val UNDO_MARK_DP = 52f
        const val UNDO_MARK_MS = 900L
        const val DUPLICATE_WEIGHT = 0.85f
        /** 옛 고정 간격(키 높이에서 빼던 값) */
        const val LEGACY_GAP_DP = 6f
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 45L
        const val SWIPE_THRESHOLD_DP = 40f
        const val SLOP_DP = 8f
        /** 스페이스바의 "한 EN" 표시와 🎤 아이콘을 이만큼(dp) 작게 */
        const val ICON_SHRINK_DP = 1f

        const val DRAG_START_DP = 24f
        const val DRAG_STEP_DP = 14f

        /** 누르고 있으면 반복되는 키 */
        val REPEAT_TYPES = setOf(KeyType.BACKSPACE)
        /** 상단 편집 줄: 일반 키 색, 작은 글씨 */
        val TOOLBAR_ACTION_TYPES =
            setOf(KeyType.SELECT_ALL, KeyType.COPY, KeyType.PASTE, KeyType.VOICE)
        /** 일반 키 배경색을 쓰는 키 */
        val NORMAL_STYLE_TYPES = TOOLBAR_ACTION_TYPES + setOf(KeyType.CHAR, KeyType.SPACE)
    }
}
