package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.KeyboardLayout
import kotlin.math.max

enum class ShiftState { OFF, ON, LOCK }

/** 자판 데이터를 Canvas로 그리고 터치를 키 이벤트로 변환하는 뷰. */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    fun interface Listener { fun onKey(key: Key) }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardTheme.system(context)
        set(v) { field = v; requestLayout(); invalidate() }

    var keyboardLayout: KeyboardLayout? = null
        set(v) { field = v; pressed = null; computeCells(); requestLayout(); invalidate() }

    var shiftState: ShiftState = ShiftState.OFF
        set(v) { field = v; invalidate() }

    private class Cell(val key: Key, val rect: RectF)

    private var cells: List<Cell> = emptyList()
    private var pressed: Cell? = null
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (rowCount * keyHeightPx + 2 * padPx).toInt() + bottomInset
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
        val rowH = keyHeightPx
        val usableW = width - 2 * padPx
        layout.rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = padPx
            val top = padPx + r * rowH
            for (key in row) {
                val w = usableW * key.weight / total
                if (key.type != KeyType.SPACER) out += Cell(key, RectF(x, top, x + w, top + rowH))
                x += w
            }
        }
        cells = out
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val gap = 3 * density
        val radius = 7 * density
        for (cell in cells) {
            val key = cell.key
            val isSpecial = key.type != KeyType.CHAR && key.type != KeyType.SPACE
            paint.color = when {
                cell === pressed -> theme.keyPressed
                key.type == KeyType.SHIFT && shiftState != ShiftState.OFF -> theme.keyPressed
                isSpecial -> theme.specialKey
                else -> theme.key
            }
            val r = RectF(cell.rect).apply { inset(gap, gap) }
            canvas.drawRoundRect(r, radius, radius, paint)

            val label = if (key.type == KeyType.SHIFT && shiftState == ShiftState.LOCK) "⇪"
            else key.labelFor(shiftState != ShiftState.OFF)
            if (label.isEmpty()) continue
            paint.color = theme.text
            paint.textSize = keyHeightPx * (if (isSpecial) 0.30f else 0.40f) * theme.textScale
            val y = r.centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            canvas.drawText(label, r.centerX(), y, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hit(e.x, e.y)
                invalidate()
                pressed?.let {
                    if (it.key.type == KeyType.BACKSPACE) {
                        listener?.onKey(it.key)
                        handler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val h = hit(e.x, e.y)
                if (h !== pressed) {
                    stopRepeat()
                    pressed = h
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val p = pressed
                val repeating = p?.key?.type == KeyType.BACKSPACE
                stopRepeat()
                pressed = null
                invalidate()
                if (p != null && !repeating) {
                    performClick()
                    listener?.onKey(p.key)
                }
            }
            MotionEvent.ACTION_CANCEL -> { stopRepeat(); pressed = null; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun stopRepeat() = handler.removeCallbacks(repeatRunnable)

    /** 정확히 맞는 키가 없으면 같은 행에서 가장 가까운 키 (키 사이 틈 터치 보정) */
    private fun hit(x: Float, y: Float): Cell? {
        if (cells.isEmpty()) return null
        val rowH = keyHeightPx
        val row = ((y - padPx) / rowH).toInt().coerceIn(0, rowCount - 1)
        val top = padPx + row * rowH
        return cells.filter { it.rect.top == top }.minByOrNull {
            val cx = x.coerceIn(it.rect.left, it.rect.right)
            max(0f, kotlin.math.abs(x - cx))
        }
    }

    override fun onDetachedFromWindow() { stopRepeat(); super.onDetachedFromWindow() }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 45L
    }
}
