package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
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
import com.mbt.keyboard.layout.Layouts
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
    /** 숫자 줄과 글자 줄 3개, 줄 4개 각각의 아래 경계를 이만큼(dp) 아래로 옮겨 인식한다. 눈에 보이는 키는 그대로다 */
    val hitShiftDp: List<Int> = listOf(0, 0, 0, 0),
    /** 맨 아래 줄(스페이스바 줄) 키 높이: 일반 줄 대비 배율 (1 = 같음). 전체 크기는 고정이라 다른 줄이 그만큼 줄거나 늘어난다 */
    val spaceRowScale: Float = 1f,
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
        /** 모아키: 자음 [key]를 누른 채 모음 [vowel] 방향으로 밀었다 */
        fun onMoaki(key: Key, vowel: Char) {}
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

    /** 단모음 개선판: ㄱㄷㅂㅅㅈ ㅏㅓㅗㅜㅐㅔ 키를 상하좌우로 밀면 ㄲㄸㅃㅆㅉ ㅑㅕㅛㅠㅒㅖ (연타 변환 대신) */
    /** 키 글자 크기 배율 (1 = 기본) */
    var fontScale: Float = 1f
        set(v) { if (field != v) { field = v; invalidate() } }
    var swipeDouble: Boolean = false

    /** 꾹 누르기로 인식하는 시간 (설정 화면에서 변경) */
    var longPressMs: Long = Timing.LONG_PRESS_DEFAULT_MS

    /** 지우기 키를 꾹 누를 때 한 글자씩 지우는 간격 (설정 화면에서 변경) */
    var repeatIntervalMs: Long = Timing.repeatIntervalMs(Timing.REPEAT_CPS_DEFAULT)

    var theme: KeyboardTheme = KeyboardTheme.system(context)
        set(v) { field = v; requestLayout(); invalidate() }

    /**
     * 숫자를 맨 위 줄이 아니라 왼쪽 세로 띠(2열)로 옮긴다 (모아키처럼). 단모음·두벌식·영어에 적용되고,
     * 전체·복사·붙임은 맨 아래 줄로 내려간다. "숫자 줄 크기" 설정은 이때 띠의 가로 길이가 된다.
     */
    var numberLeft: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            dropGestures(); computeCells(); requestLayout(); invalidate()
        }

    /**
     * 전체·복사·붙임을 키보드 위 반투명 탭(FnTab)으로 따로 띄운다. 켜면 숫자 띠가 있는 자판의 맨 아래 줄에서 그 세 키를 뺀다.
     */
    var fnTab: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            dropGestures(); computeCells(); requestLayout(); invalidate()
        }

    /** 지금 자판에 왼쪽 숫자 띠가 있는가 (모아키 또는 숫자 왼쪽 옵션) */
    val hasSideStrip: Boolean get() = effective()?.sideKeys?.isNotEmpty() == true

    /** 숫자가 상단 배치일 때 전복붙+숫자(1~0)를 상단 탭으로 띄우는 자판인가 (단모음·두벌식·영어에서 숫자 좌측 OFF) */
    val hasTopNumberRow: Boolean
        get() {
            val base = keyboardLayout ?: return false
            return !numberLeft && !base.moaki && base.sideKeys.isEmpty() &&
                base.rows.firstOrNull()?.any { it.type == KeyType.SELECT_ALL } == true
        }

    /** 상단 탭(FnTab)에 들어갈 키 목록: 좌측 숫자 띠면 3키(전·복·붙), 상단 숫자면 13키(전·복·붙 + 1~0) */
    fun fnTabKeys(): List<Key> = when {
        hasSideStrip -> listOf(Key("전체", KeyType.SELECT_ALL), Key("복사", KeyType.COPY), Key("붙임", KeyType.PASTE))
        hasTopNumberRow -> keyboardLayout?.rows?.firstOrNull().orEmpty()
        else -> emptyList()
    }

    private var effCacheKey: Triple<KeyboardLayout?, Boolean, Boolean>? = null
    private var effCache: KeyboardLayout? = null

    /**
     * 실제로 그리는 본판 자판:
     * - [numberLeft]가 켜지면 숫자를 왼쪽 띠로, 전복붙을 맨 아래 줄(또는 상단 탭)로 옮긴 4줄
     * - [numberLeft]가 꺼지면 전복붙+숫자 줄을 상단 탭으로 빼고 문자 3줄 + 하단 1줄 = 동일한 4줄
     */
    private fun effective(): KeyboardLayout? {
        val base = keyboardLayout ?: return null
        val key = Triple(base, numberLeft, fnTab)
        if (effCacheKey == key) return effCache
        val numRow = base.rows.firstOrNull()
        val hasNumRow = !base.moaki && base.sideKeys.isEmpty() && numRow?.any { it.type == KeyType.SELECT_ALL } == true
        val conv = if (!hasNumRow) {
            base
        } else if (numberLeft) {
            val digits = numRow!!.filter { it.type == KeyType.CHAR && it.label.length == 1 && it.label[0].isDigit() }
            val fn = numRow.filter { it.type in FN_TYPES }
            val rows = base.rows.drop(1).toMutableList()
            rows[rows.lastIndex] = fn + rows.last()
            base.copy(rows = rows, rowHeights = List(rows.size) { 1f }, splitAt = base.splitAt.drop(1), sideKeys = digits)
        } else {
            val rows = base.rows.drop(1)
            base.copy(rows = rows, rowHeights = List(rows.size) { 1f }, splitAt = base.splitAt.drop(1))
        }
        val eff = if (!fnTab || conv.sideKeys.isEmpty()) conv
        else conv.copy(rows = conv.rows.dropLast(1) + listOf(conv.rows.last().filter { it.type !in FN_TYPES }))
        effCacheKey = key; effCache = eff
        return eff
    }

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
        /** 모아키: 지금 미는 방향이 가리키는 모음 (아직 안 밀었으면 null) */
        var moaVowel: Char? = null
        /** 모아키: 첫 획의 각도 (화면 기준, 아래가 +90°). 안내 칸 강조에 쓴다 */
        var moaDeg = 0f
        /** 모아키 여러 획 제스처: 지금 획의 시작점(a)·가장 먼 점(far), 끝난 획들 */
        var moaInit = false
        var moaAx = 0f; var moaAy = 0f; var moaFx = 0f; var moaFy = 0f
        val moaStrokes = StringBuilder()
        /** 단모음 개선판: 이 키를 밀어서 쌍자음·ㅑㅕㅛㅠ 로 바꿨다 */
        var swipeDoubleDone = false
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
                handler.postDelayed(this, repeatIntervalMs)
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

    /** 왼쪽 세로 띠(모아키 숫자)의 폭과 키들, 띠가 끝나는 y (맨 아래 줄의 윗경계) */
    private var stripPx = 0f
    private var sideCells: List<Cell> = emptyList()
    private var sideBottomPx = 0f
    private var sideTopPx = 0f
    private var sideHalfGapPx = 0f
    private var sidePitchPx = 1f

    /** 터치가 어느 줄인지 가를 때 쓰는 줄 윗경계. 줄별 인식 영역 이동이 반영된다 (눈에 보이는 위치는 [rowTops]) */
    private var hitTops = FloatArray(0)

    /** 설정 화면 미리보기에서 인식 영역 경계를 점선으로 보여준다 */
    var showHitZones: Boolean = false
        set(v) { field = v; invalidate() }
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

    private val rowCount get() = effective()?.rows?.size ?: 0
    private val keyHeightPx get() = theme.keyHeightDp * density * heightScale
    private val padL get() = frame.leftDp * density
    private val padT get() = frame.topDp * density
    private val padR get() = frame.rightDp * density
    private val padB get() = frame.bottomDp * density

    /** 맨 윗줄이 전체·복사·붙임 + 숫자 줄인가 (글자 자판만. 키패드는 해당 없음) */
    private fun isNumberRow(r: Int) = r == 0 && effective()?.rows?.getOrNull(0)?.any { it.type == KeyType.SELECT_ALL } == true

    /** 전체 높이의 기준이 되는 기본 설정 (여백 4·줄 여백 6·숫자 줄 80%·숫자 아래 6). 사용자가 무엇을 바꿔도 전체 크기는 이 기준에서 안 변한다 */
    private val ref = KeyboardFrame()

    /**
     * 줄마다의 "자연스러운" 키 높이(일반 키 = 키 높이 − 옛 간격 6dp, 줄마다 배율). 줄끼리의 비율만 뜻이 있고,
     * 실제 높이는 [bodyFit]이 전체 높이에 맞춰 늘이고 줄인다.
     */
    private fun naturalBodyPx(r: Int, f: KeyboardFrame): Float {
        val base = (keyHeightPx - LEGACY_GAP_DP * density).coerceAtLeast(1f)
        val eff = effective()
        if (eff != null && (eff.sideKeys.isNotEmpty() || hasTopNumberRow)) {
            // 숫자 좌측이든 상단(탭)이든 본판 4줄(문자 3줄 + 하단 1줄)의 세로 크기와 줄 높이는 완전히 동일하다
            val n = eff.rows.size
            val single = base * DEFAULT_NUMBER_ROW_SCALE + 4f * base + DEFAULT_NUMBER_GAP_DP * density + 4f * ref.rowGapDp * density
            return ((single - n * ref.rowGapDp * density) / n).coerceAtLeast(1f) * spaceRowFactor(r, f)
        }
        val scale = if (isNumberRow(r)) f.numberRowScale else (eff?.rowHeights?.getOrNull(r) ?: 1f)
        return base * scale * spaceRowFactor(r, f)
    }

    /** 맨 아래 줄(스페이스바 줄)만 설정한 배율, 나머지는 1 */
    private fun spaceRowFactor(r: Int, f: KeyboardFrame) = if (r == rowCount - 1) f.spaceRowScale else 1f

    private fun refGapBelowPx(r: Int) = (if (isNumberRow(r) && r + 1 < rowCount) ref.numberGapDp else ref.rowGapDp) * density

    /** 기준 설정으로 계산한 키보드 전체 높이(내비게이션 바 여백 제외). 이 값이 곧 "전체 크기"다 */
    private fun referenceTotalPx(): Float {
        var t = (ref.topDp + ref.bottomDp) * density
        for (r in 0 until rowCount) {
            val above = if (r == 0) ref.rowGapDp * density else refGapBelowPx(r - 1)
            t += naturalBodyPx(r, ref) + above / 2f + refGapBelowPx(r) / 2f
        }
        return t
    }

    /**
     * 전체 높이를 고정하고 키 높이를 맞추는 배율: (전체 − 위·아래 여백 − 줄 사이 여백 합) ÷ 자연스러운 키 높이 합.
     * 그래서 여백·간격·숫자 줄 크기를 바꾸면 키가 그만큼 커지거나 작아질 뿐 키보드 전체 크기는 그대로다.
     */
    private fun bodyFit(): Float {
        var nat = 0f; var gaps = 0f
        for (r in 0 until rowCount) { nat += naturalBodyPx(r, frame); gaps += gapAbovePx(r) / 2f + gapBelowPx(r) / 2f }
        if (nat <= 0f) return 1f
        return ((referenceTotalPx() - padT - padB - gaps) / nat).coerceAtLeast(0.05f)
    }

    private fun keyBodyPx(r: Int): Float = (naturalBodyPx(r, frame) * bodyFit()).coerceAtLeast(1f)

    /**
     * [r]줄과 그 아랫줄 사이 세로 여백(px). 숫자 줄 바로 아래는 따로 정한 값, 나머지는 줄 사이 여백.
     * 맨 아랫줄 아래는 0: 키보드 가장자리 여백은 위·아래·왼·오른쪽 여백 설정만으로 정해진다.
     */
    private fun gapBelowPx(r: Int): Float {
        if (r >= rowCount - 1) return 0f
        val dp = if (isNumberRow(r)) frame.numberGapDp else frame.rowGapDp
        return dp * density
    }

    private fun gapAbovePx(r: Int) = if (r == 0) 0f else gapBelowPx(r - 1)

    /** 한 줄이 차지하는 세로 길이 = 키 높이 + 위아래 여백의 절반씩 (이웃 줄과 여백을 반반 나눠 가진다) */
    private fun rowHeightPx(r: Int) = keyBodyPx(r) + gapAbovePx(r) / 2f + gapBelowPx(r) / 2f

    private fun contentHeight() = (0 until rowCount).sumOf { rowHeightPx(it).toDouble() }.toFloat()

    /** 키 사이 가로 간격의 절반 (키마다 양쪽으로 비우는 폭) */
    private val halfKeyGapPx get() = frame.keyGapDp / 2f * density

    private fun stripWidthFor(w: Int) = (w - padL - padR + 2 * halfKeyGapPx) * (SIDE_STRIP_FRACTION * frame.numberRowScale / 0.8f).coerceIn(0.05f, 0.30f)

    /** 가장 최근에 정해진 가로 폭. 폴드를 접고 펼칠 때 아직 배치 전이어도 방금 잰 값(measuredWidth)을 먼저 쓴다 */
    private fun layoutWidth() = if (measuredWidth > 0) measuredWidth else width

    /**
     * 이 키보드의 크기·설정이 바뀌어 다시 배치될 때 같이 불린다. 전복붙 탭은 키보드와 형제 뷰라 따로 알려 주지 않으면
     * 예전 크기 그대로 남는다(폴드를 접고 펼치면 탭 크기가 어긋나던 원인).
     */
    var onRelayoutRequested: (() -> Unit)? = null

    override fun requestLayout() {
        super.requestLayout()
        onRelayoutRequested?.invoke()
    }

    /** 탭 버튼 가로 배율 (기본 1 = 숫자 키와 같음) */
    var fnTabWidthScale: Float = 1f
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    /** 반투명 탭 버튼 하나의 가로 길이: 좌측 숫자 띠면 숫자 키 한 칸, 상단 통합 탭이면 본판 전체 폭 ÷ 13 */
    fun fnKeyWidthPx(): Float {
        if (hasTopNumberRow) {
            val usableW = (layoutWidth() - padL - padR + 2 * halfKeyGapPx).coerceAtLeast(1f)
            return (usableW / fnTabKeys().size.coerceAtLeast(1)) * fnTabWidthScale.coerceAtMost(1f)
        }
        return (stripWidthFor(layoutWidth()) / 2f) * fnTabWidthScale
    }

    /** 미리보기 드래그 오버레이용: 좌측 숫자 띠 오른쪽 끝 X, 스페이스바 줄 윗변 Y, 줄 인식 경계 배열 */
    fun stripRightPx(): Float = if (hasSideStrip) padL - halfKeyGapPx + stripPx else 0f
    fun spaceRowTopPx(): Float = rowTops.lastOrNull() ?: 0f
    fun hitZoneYs(): FloatArray = hitTops.copyOf()

    /**
     * 반투명 탭 버튼 높이 = 숫자 키 높이. 좌측 숫자 띠와 상단 통합 탭 모두 동일한 숫자 키 높이를 쓴다
     */
    /** 탭 버튼 높이 배율 (숫자 키 높이 대비, 1 = 같음) */
    var fnTabHeightScale: Float = 1f
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    fun fnKeyHeightPx(): Float = digitKeyHeightPx() * fnTabHeightScale

    private fun digitKeyHeightPx(): Float {
        val n = (effective()?.sideKeys?.size ?: 0) / 2
        if (rowCount == 0) return 0f
        if (n == 0) {
            if (!hasTopNumberRow) return keyBodyPx(rowCount - 1)
            val region = (0 until rowCount).sumOf { rowHeightPx(it).toDouble() }.toFloat()
            val rowGap = frame.rowGapDp * density
            return ((region - 4 * rowGap) / 5).coerceAtLeast(1f)
        }
        // 탭이 있으면 숫자 띠가 전체 줄에 걸친다 (computeCells와 같은 식)
        val stripRows = if (fnTab) rowCount else rowCount - 1
        val region = (0 until stripRows).sumOf { rowHeightPx(it).toDouble() }.toFloat()
        val rowGap = frame.rowGapDp * density
        val bottomHalf = if (stripRows == rowCount) 0f else rowGap / 2f
        return ((region - (n - 1) * rowGap - bottomHalf) / n).coerceAtLeast(1f)
    }

    /** 전복붙 탭 바탕이 버튼 둘레로 내미는 폭(dp). 위·아래·왼쪽·오른쪽 모두 같다 (전복붙 설정에서 따로 정한다) */
    var fnTabPadDp: Int = 4
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    /** 탭 버튼 칸이 시작하는 x (탭 안쪽 기준). 버튼 자체는 여기서 키 간격/2만큼 안쪽 = 탭 여백 위치 */
    fun fnTabLeftPx(): Float = fnTabPadPx() - halfKeyGapPx

    /** 탭 바탕의 둘레 여백 (네 방향 동일): 전복붙 설정의 "탭 여백" */
    fun fnTabPadPx(): Float = fnTabPadDp * density

    /** 탭 바탕의 오른쪽 여백 (키 간격/2는 버튼 칸 안쪽에 이미 들어 있다) */
    fun fnTabRightPx(): Float = fnTabPadPx() - halfKeyGapPx

    /** 탭 버튼 아래 바탕 폭 (네 방향 동일) */
    fun fnTabPadBottomPx(): Float = fnTabPadPx()

    /** 숫자 띠의 왼쪽 끝 x(키보드 기준): 탭 버튼이 이 선에 맞도록 탭을 놓는다 */
    fun fnTabAnchorPx(): Float = padL

    /** 탭과 키보드 사이의 작은 틈 (약 2dp) */
    fun fnTabGapPx(): Float = FN_TAB_GAP_DP * density

    /** 탭 전체 높이 = 위 바탕 + 버튼 + 아래 바탕 */
    fun fnTabHeightPx(): Float = fnTabPadPx() + fnKeyHeightPx() + fnTabPadBottomPx()

    /** 탭 버튼 좌우 여백: 숫자 키와 똑같이 키 사이 간격의 절반씩 */
    fun fnTabInsetPx(): Float = halfKeyGapPx

    /** 탭 글자 크기: 같은 크기의 일반 기능키와 똑같이 */
    fun fnLabelSizePx(kw: Float, kh: Float): Float =
        (min(min(kh, keyHeightPx), kw * LABEL_WIDTH_FACTOR) * 0.34f * theme.textScale + LABEL_BONUS_DP * density) * fontScale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (contentHeight() + padT + padB).toInt() + bottomInset
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeCells()
        onRelayoutRequested?.invoke()
    }

    private fun computeCells() {
        val layout = effective() ?: run { cells = emptyList(); return }
        if (width == 0) return
        val out = ArrayList<Cell>()
        val rows = ArrayList<List<Cell>>()
        // 가장자리 키의 눈에 보이는 바깥 여백이 정확히 설정값이 되도록, 키 칸은 키 간격/2만큼 바깥까지 편다
        val originX = padL - halfKeyGapPx
        val usableW = width - padL - padR + 2 * halfKeyGapPx
        val hasSide = layout.sideKeys.isNotEmpty()
        // 모아키는 "숫자 줄 높이" 설정이 숫자 띠의 가로 길이가 된다 (기본 80%일 때 줄 폭의 19%)
        stripPx = if (hasSide) stripWidthFor(width) else 0f
        sideCells = emptyList()
        rowTops = FloatArray(layout.rows.size)
        val stripRows = if (fnTab) layout.rows.size else layout.rows.size - 1
        var top = padT
        var upperGapLeft = Float.NaN
        var upperGapRight = Float.NaN
        val letterRowIdx = if (isNumberRow(0)) 1 else 0
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
            var dupLeft: Split.Part? = null
            var dupRight: Split.Part? = null
            if (doSplit && splitDuplicate && !layout.hangul) {
                val l = halves.left.lastOrNull()
                val rt = halves.right.firstOrNull()
                if (l != null && rt != null && l.key.type == KeyType.CHAR && rt.key.type == KeyType.CHAR) {
                    // 복제 키는 가운데 틈 안에 얹는다: 원래 키들의 크기는 그대로 (복제 때문에 작아지지 않는다)
                    val dl = Split.Part(rt.key, DUPLICATE_WEIGHT)
                    val dr = Split.Part(l.key, DUPLICATE_WEIGHT)
                    dups += dl; dups += dr
                    dupLeft = dl; dupRight = dr
                }
            }
            // 왼쪽 세로 띠(숫자)가 있으면 맨 아래 줄을 뺀 줄들은 그 오른쪽에서 시작한다
            // 탭이 전체·복사·붙임을 맡으면 숫자 띠가 맨 아래 줄까지 이어진다 (숫자 키 높이 = 줄 높이)
            val inStrip = hasSide && r < stripRows
            // 숫자 띠와 글자 키 사이 가로 여백 = "숫자키 글자키 여백" 설정 (키 사이 기본 간격 대신)
            val stripShift = if (inStrip) (frame.numberGapDp - frame.keyGapDp) * density else 0f
            val rowStartX = if (inStrip) originX + stripPx + stripShift else originX
            val rowUsableW = if (inStrip) usableW - stripPx - stripShift else usableW
            val gapW = if (doSplit) rowUsableW * splitGapFraction else 0f
            val isBottomSplit = doSplit && r == layout.rows.size - 1 && !upperGapLeft.isNaN() && !upperGapRight.isNaN()
            val effectiveGapW = if (isBottomSplit) (upperGapRight - upperGapLeft).coerceAtLeast(0f) else gapW
            if (hasSide && r == layout.rows.size - 1) {
                // 모아키·좌측숫자 맨 아래 줄: 폴드 펼침(split) 여부와 무관하게 전체·복사·붙임 키의 가로 폭을 숫자 키 한 칸(띠 폭의 절반)과 똑같이 맞춘다
                val totalRest = (halves.left + halves.right).filter { it.key.type !in FN_TYPES }.sumOf { it.weight.toDouble() }.toFloat()
                val keyBearingW = (rowUsableW - effectiveGapW).coerceAtLeast(1f)
                val f = (stripPx / 2f) / keyBearingW
                val fnWeight = f * totalRest / (1f - 3f * f)
                halves = Split.Halves(halves.left.map { if (it.key.type in FN_TYPES) Split.Part(it.key, fnWeight) else it }, halves.right)
            }
            var rowGapW = gapW
            if (isBottomSplit) {
                // 분할 키보드 맨 아래 줄의 좌·우 스페이스바 틈을 위쪽 글자 줄의 틈에 맞춘다
                val leftSpaceIdx = halves.left.indexOfFirst { it.key.type == KeyType.SPACE }
                val rightSpaceIdx = halves.right.indexOfFirst { it.key.type == KeyType.SPACE }
                if (leftSpaceIdx >= 0 && rightSpaceIdx >= 0) {
                    val total = (halves.left + halves.right).sumOf { it.weight.toDouble() }.toFloat()
                    val keyBearingW = (rowUsableW - effectiveGapW).coerceAtLeast(1f)
                    if (total > 0f) {
                        val unit = keyBearingW / total
                        val leftOtherW = halves.left.filter { it.key.type != KeyType.SPACE }.sumOf { it.weight.toDouble() }.toFloat() * unit
                        val rightOtherW = halves.right.filter { it.key.type != KeyType.SPACE }.sumOf { it.weight.toDouble() }.toFloat() * unit
                        val leftSpaceW = upperGapLeft - (rowStartX + leftOtherW)
                        val rightSpaceW = (rowStartX + rowUsableW - rightOtherW) - upperGapRight
                        if (leftSpaceW >= unit * 0.4f && rightSpaceW >= unit * 0.4f) {
                            // 스페이스바는 좌·우 반반 (같은 크기)
                            val halfSpaceWeight = (leftSpaceW + rightSpaceW) / 2f / unit
                            val newLeft = halves.left.mapIndexed { idx, p -> if (idx == leftSpaceIdx) p.copy(weight = halfSpaceWeight) else p }
                            val newRight = halves.right.mapIndexed { idx, p -> if (idx == rightSpaceIdx) p.copy(weight = halfSpaceWeight) else p }
                            halves = Split.Halves(newLeft, newRight)
                            rowGapW = effectiveGapW
                        }
                    }
                }
            }
            if (doSplit) halves = Split.equalizeSpace(halves)   // 규칙: 갈라진 스페이스바는 좌·우 반반
            val basePlacements = Split.place(halves, rowStartX, rowUsableW, rowGapW)
            val placements = ArrayList(basePlacements)
            val dl0 = dupLeft; val dr0 = dupRight
            if (dl0 != null && dr0 != null && halves.left.isNotEmpty() && halves.right.isNotEmpty()) {
                val leftLast = basePlacements[halves.left.size - 1]
                val rightFirst = basePlacements[halves.left.size]
                val total = (halves.left + halves.right).sumOf { it.weight.toDouble() }.toFloat()
                val unit = if (total > 0f) (rowUsableW - rowGapW) / total else 0f
                val dw = min(DUPLICATE_WEIGHT * unit, (rightFirst.left - leftLast.right).coerceAtLeast(0f) / 2f)
                if (dw > 0f) {
                    placements += Split.Placement(dl0, leftLast.right, leftLast.right + dw)
                    placements += Split.Placement(dr0, rightFirst.left - dw, rightFirst.left)
                }
            }
            if (doSplit && r == letterRowIdx && halves.left.isNotEmpty() && halves.right.isNotEmpty()) {
                val leftCount = halves.left.size
                if (placements.size > leftCount) {
                    upperGapLeft = placements[leftCount - 1].right
                    upperGapRight = placements[leftCount].left
                }
            }
            for (pl in placements) {
                if (pl.part.key.type == KeyType.SPACER) continue
                val rect = RectF(pl.left, top, pl.right, top + rowH)
                val cell = Cell(pl.part.key, rect, RectF(rect.left + gapX, rect.top + gapTop, rect.right - gapX, rect.bottom - gapBottom), pl.part in dups)
                out += cell
                rowList += cell
            }
            rows += rowList
            top += rowH
        }
        if (hasSide) {
            // 왼쪽 띠: 숫자를 줄 수와 같은 높이로 2열에 놓는다. 위·아래 가장자리 키는 바깥 여백 0 (여백은 설정값만)
            val sideTop = rowTops[0]
            val full = stripRows == layout.rows.size
            val sideBottom = if (full) top else rowTops[layout.rows.size - 1]
            val n = layout.sideKeys.size / 2
            val colW = stripPx / 2f
            val gx = halfKeyGapPx
            val gy = frame.rowGapDp / 2f * density
            val rowGap = frame.rowGapDp * density
            val keyH = ((sideBottom - sideTop - (n - 1) * rowGap - (if (full) 0f else gy)) / n).coerceAtLeast(1f)
            val pitch = keyH + rowGap
            sideTopPx = sideTop; sideHalfGapPx = gy; sidePitchPx = pitch
            val side = ArrayList<Cell>()
            layout.sideKeys.forEachIndexed { i, k ->
                val row = i % n; val col = i / n        // 왼쪽 열 1~5, 오른쪽 열 6~0
                val inner = RectF(originX + col * colW + gx, sideTop + row * pitch, originX + (col + 1) * colW - gx, sideTop + row * pitch + keyH)
                val rect = RectF(originX + col * colW, inner.top - gy, originX + (col + 1) * colW, inner.bottom + gy)
                side += Cell(k, rect, inner)
            }
            sideCells = side
            sideBottomPx = sideBottom
            out += side
        }
        cells = out
        rowCells = rows
        hitTops = FloatArray(rowTops.size) { rowTops[it] }
        if (isNumberRow(0) || layout.moaki || layout.sideKeys.isNotEmpty() || hasTopNumberRow) {
            // 줄 k(0=숫자 줄(모아키는 쌍자음 줄), 1~3=글자 줄)의 아래 경계 = 다음 줄의 윗경계를 hitShiftDp[k]만큼 아래로.
            // 숫자 왼쪽/상단탭 자판은 본판에 숫자 줄이 없어 첫 글자 줄이 1번이다
            val first = if ((layout.sideKeys.isNotEmpty() || hasTopNumberRow) && !layout.moaki) 1 else 0
            for (r in 1..minOf(4, rowTops.size - 1)) {
                val shift = frame.hitShiftDp.getOrElse(r - 1 + first) { 0 } * density
                hitTops[r] = (rowTops[r] + shift).coerceAtLeast(hitTops[r - 1] + 1f)
            }
        }
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

    private val bgPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** 유리판 뒤에 깔리는 배경(연한 번짐)을 [w]×[h]에 그린다 */
    private fun drawFrostBackdrop(canvas: Canvas, w: Float, h: Float) {
        bgPaint.shader = theme.backgroundShader(w, h)
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        val blobs = if (!theme.frost) listOf(Blob(0.15f, 0.14f, 0.55f, 0x9EFFFFFF.toInt()), Blob(0.84f, 0.80f, 0.60f, (theme.accent and 0x00FFFFFF) or 0x90000000.toInt()), Blob(0.52f, 0.28f, 0.46f, 0x58FFFFFF), Blob(0.25f, 0.85f, 0.45f, (theme.accent and 0x00FFFFFF) or 0x4C000000)) else when (theme.frostStyle) {
            1 -> TAUPE_BLOBS; 2 -> NAVY_BLOBS; 3 -> SUNSET_BLOBS; 4 -> PINK_BLOBS; 5 -> AURORA_BLOBS; 6 -> MINT_BLOBS
            7 -> TAUPE_OPTICAL_BLOBS
            8 -> listOf(Blob(0.18f, 0.28f, 0.24f, 0xFFFF5A26.toInt()), Blob(0.80f, 0.74f, 0.22f, 0xFFFF8A36.toInt()), Blob(0.68f, 0.20f, 0.12f, 0xFFFF4D38.toInt()))
            9 -> listOf(Blob(0.20f, 0.26f, 0.28f, 0xFF3E8EFF.toInt()), Blob(0.78f, 0.76f, 0.28f, 0xFFFF4DA6.toInt()), Blob(0.45f, 0.55f, 0.14f, 0xFF70D8FF.toInt()))
            10 -> listOf(Blob(0.24f, 0.30f, 0.30f, 0xFF5A38FF.toInt()), Blob(0.76f, 0.72f, 0.26f, 0xFFFF884D.toInt()), Blob(0.84f, 0.24f, 0.10f, 0xFFFFC866.toInt()), Blob(0.16f, 0.80f, 0.10f, 0xFFFFB45C.toInt()))
            11 -> listOf(Blob(0.18f, 0.52f, 0.18f, 0xFFFF8AB8.toInt()), Blob(0.66f, 0.26f, 0.22f, 0xFF66C8FF.toInt()), Blob(0.82f, 0.74f, 0.18f, 0xFFB878FF.toInt()), Blob(0.32f, 0.84f, 0.08f, 0xFFFF98C8.toInt()))
            12 -> listOf(Blob(0.22f, 0.42f, 0.32f, 0xFFFF6EC7.toInt()), Blob(0.56f, 0.48f, 0.28f, 0xFFFFC870.toInt()), Blob(0.84f, 0.64f, 0.26f, 0xFF5E54FF.toInt()))
            13 -> listOf(Blob(0.48f, 0.48f, 0.32f, 0xFFFF9A4A.toInt()), Blob(0.26f, 0.32f, 0.22f, 0xFFFFC878.toInt()), Blob(0.74f, 0.64f, 0.22f, 0xFFFF7A66.toInt()))
            14 -> listOf(Blob(0.20f, 0.28f, 0.24f, 0xFF28F0B4.toInt()), Blob(0.78f, 0.72f, 0.24f, 0xFFFFD24A.toInt()), Blob(0.64f, 0.24f, 0.12f, 0xFF5CE1FF.toInt()))
            15 -> listOf(Blob(0.20f, 0.28f, 0.25f, 0xFF38E2FF.toInt()), Blob(0.80f, 0.74f, 0.25f, 0xFFFF46B8.toInt()), Blob(0.52f, 0.50f, 0.14f, 0xFF9B5CFF.toInt()))
            16 -> listOf(Blob(0.22f, 0.30f, 0.26f, 0xFFFF784D.toInt()), Blob(0.78f, 0.70f, 0.24f, 0xFFFFC85E.toInt()), Blob(0.66f, 0.24f, 0.12f, 0xFFFF4D8A.toInt()))
            17 -> listOf(Blob(0.20f, 0.32f, 0.20f, 0xFF5EE8D8.toInt()), Blob(0.76f, 0.30f, 0.22f, 0xFF98B8FF.toInt()), Blob(0.72f, 0.76f, 0.18f, 0xFFC8A4FF.toInt()))
            18 -> listOf(Blob(0.18f, 0.28f, 0.26f, 0xFFFF5E2E.toInt()), Blob(0.82f, 0.74f, 0.24f, 0xFFFF8F3D.toInt()), Blob(0.62f, 0.22f, 0.12f, 0xFFFF70A6.toInt()))
            19 -> listOf(Blob(0.22f, 0.24f, 0.30f, 0xFF42A5FF.toInt()), Blob(0.80f, 0.76f, 0.30f, 0xFFFF4FB8.toInt()), Blob(0.42f, 0.56f, 0.16f, 0xFF6B5CFF.toInt()))
            20 -> listOf(Blob(0.24f, 0.30f, 0.30f, 0xFF4E46FF.toInt()), Blob(0.76f, 0.74f, 0.28f, 0xFFFF8F4A.toInt()), Blob(0.18f, 0.80f, 0.12f, 0xFFFFB266.toInt()))
            21 -> listOf(Blob(0.18f, 0.52f, 0.19f, 0xFFFF8EC4.toInt()), Blob(0.66f, 0.24f, 0.23f, 0xFF72C6FF.toInt()), Blob(0.80f, 0.74f, 0.18f, 0xFFB68EFF.toInt()))
            22 -> listOf(Blob(0.22f, 0.42f, 0.32f, 0xFFFF6EC7.toInt()), Blob(0.56f, 0.48f, 0.28f, 0xFFFFC870.toInt()), Blob(0.84f, 0.64f, 0.26f, 0xFF5E54FF.toInt()))
            23 -> listOf(Blob(0.48f, 0.48f, 0.32f, 0xFFFF9E54.toInt()), Blob(0.74f, 0.64f, 0.22f, 0xFFFF6E5E.toInt()), Blob(0.26f, 0.32f, 0.22f, 0xFFFFD18A.toInt()))
            24 -> listOf(Blob(0.20f, 0.28f, 0.28f, 0xFF9BB4FF.toInt()), Blob(0.78f, 0.72f, 0.26f, 0xFFC8D4FF.toInt()), Blob(0.52f, 0.48f, 0.16f, 0xFFFFFFFF.toInt()))
            25 -> listOf(Blob(0.22f, 0.30f, 0.28f, 0xFFFFCFA6.toInt()), Blob(0.78f, 0.74f, 0.26f, 0xFFE8B490.toInt()), Blob(0.55f, 0.42f, 0.16f, 0xFFFFF7EB.toInt()))
            26 -> listOf(Blob(0.18f, 0.28f, 0.28f, 0xFF8EA2FF.toInt()), Blob(0.80f, 0.72f, 0.26f, 0xFFB89EFF.toInt()), Blob(0.50f, 0.50f, 0.15f, 0xFFFFFFFF.toInt()))
            27 -> listOf(Blob(0.20f, 0.30f, 0.28f, 0xFF7CE0C3.toInt()), Blob(0.78f, 0.72f, 0.26f, 0xFF9AD8C8.toInt()), Blob(0.54f, 0.45f, 0.15f, 0xFFF4FFFB.toInt()))
            28 -> listOf(Blob(0.22f, 0.28f, 0.28f, 0xFFFFA6C4.toInt()), Blob(0.78f, 0.74f, 0.26f, 0xFFE89BB8.toInt()), Blob(0.50f, 0.44f, 0.16f, 0xFFFFF5F8.toInt()))
            29 -> listOf(Blob(0.20f, 0.28f, 0.28f, 0xFFB894FF.toInt()), Blob(0.80f, 0.72f, 0.26f, 0xFFD8A8FF.toInt()), Blob(0.50f, 0.46f, 0.16f, 0xFFFAF7FF.toInt()))
            else -> HOLO_BLOBS
        }
        if (theme.frost && theme.frostStyle in 8..29) {
            // 참고 이미지(19195~19190)처럼 선명한 3D 발광 구체·오브를 배치해 유리가 투과·굴절할 때 또렷하게 비치게 함
            for ((fx, fy, rr, c) in blobs) {
                val r = max(w, h) * rr
                val cx = w * fx; val cy = h * fy
                bgPaint.shader = android.graphics.RadialGradient(
                    cx - r * 0.22f, cy - r * 0.22f, r,
                    intArrayOf(0xFFFFFFFF.toInt(), c, c, (c and 0x00FFFFFF) or 0xCC000000.toInt(), c and 0x00FFFFFF),
                    floatArrayOf(0f, 0.28f, 0.68f, 0.84f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(cx, cy, r, bgPaint)
            }
        } else {
            for ((fx, fy, rr, c) in blobs) {
                val r = max(w, h) * rr
                bgPaint.shader = android.graphics.RadialGradient(w * fx, h * fy, r, c, c and 0x00FFFFFF, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, bgPaint)
            }
        }
        bgPaint.shader = null
    }

    private var frostBlur: Bitmap? = null
    private var frostKeyBlur: Bitmap? = null
    private var frostReedBlur: Bitmap? = null
    private var frostKey: Triple<Int, Int, KeyboardTheme>? = null

    /** 유리판에 비치는 "흐린 뒷배경". 크기·테마가 바뀔 때만 다시 만든다 (작게 줄였다 늘리면 부드럽게 흐려진다) */
    private fun ensureFrostBitmaps() {
        val total = height + frostExtraTopPx().toInt()
        val key = Triple(width, total, theme)
        if (frostKey == key && frostBlur != null && frostKeyBlur != null && frostReedBlur != null) return
        frostBlur?.recycle(); frostKeyBlur?.recycle(); frostReedBlur?.recycle(); frostBlur = null; frostKeyBlur = null; frostReedBlur = null
        val sharp = Bitmap.createBitmap(max(width, 1), max(total, 1), Bitmap.Config.ARGB_8888)
        drawFrostBackdrop(Canvas(sharp), sharp.width.toFloat(), sharp.height.toFloat())
        val div = if (theme.frostStyle == 7) 5 else FROST_BLUR_DIV
        val small = Bitmap.createScaledBitmap(sharp, max(sharp.width / div, 1), max(sharp.height / div, 1), true)
        val smallBlur = boxBlur(small, 2); if (smallBlur !== small) small.recycle()
        val blur = Bitmap.createScaledBitmap(smallBlur, sharp.width, sharp.height, true); if (smallBlur !== blur) smallBlur.recycle()
        val ks = Bitmap.createScaledBitmap(sharp, max(sharp.width / 14, 1), max(sharp.height / 14, 1), true)
        val ksBlur = boxBlur(ks, 3); if (ksBlur !== ks) ks.recycle()
        val keyBlur = Bitmap.createScaledBitmap(ksBlur, sharp.width, sharp.height, true); if (ksBlur !== keyBlur) ksBlur.recycle()
        // 리드글래스(모루) 전용 세로 방향 비등방성 번짐: 가로는 선명하게 유지하고 세로로 길게 번져 모루를 따라 흐르게 함
        val rs = Bitmap.createScaledBitmap(sharp, max(sharp.width / 4, 1), max(sharp.height / 8, 1), true)
        val rsBlur = boxBlur(rs, 2); if (rsBlur !== rs) rs.recycle()
        val reedBlur = Bitmap.createScaledBitmap(rsBlur, sharp.width, sharp.height, true); if (rsBlur !== reedBlur) rsBlur.recycle()
        sharp.recycle()
        frostBlur = blur; frostKeyBlur = keyBlur; frostReedBlur = reedBlur; frostKey = key
    }
    

    private fun boxBlur(bmp: Bitmap, passes: Int): Bitmap {
val w = bmp.width; val h = bmp.height
if (w < 3 || h < 3) return bmp
val px = IntArray(w * h)
val tmp = IntArray(w * h)
bmp.getPixels(px, 0, w, 0, 0, w, h)
repeat(passes) {
for (y in 0 until h) {
val row = y * w
for (x in 0 until w) {
var a = 0; var r = 0; var g = 0; var b = 0; var c = 0
for (dx in -2..2) {
val nx = (x + dx).coerceIn(0, w - 1)
val p = px[row + nx]
a += (p ushr 24) and 0xFF; r += (p ushr 16) and 0xFF; g += (p ushr 8) and 0xFF; b += p and 0xFF; c++
}
tmp[row + x] = ((a / c) shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
}
}
for (y in 0 until h) {
val row = y * w
for (x in 0 until w) {
var a = 0; var r = 0; var g = 0; var b = 0; var c = 0
for (dy in -2..2) {
val ny = (y + dy).coerceIn(0, h - 1)
val p = tmp[ny * w + x]
a += (p ushr 24) and 0xFF; r += (p ushr 16) and 0xFF; g += (p ushr 8) and 0xFF; b += p and 0xFF; c++
}
px[row + x] = ((a / c) shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
}
}
}
return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}


    
    

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPath = Path()
    private val rimBaseColors = intArrayOf(
        0xFFFFFFFF.toInt(),
        0xE0FFFFFF.toInt(),
        0x22FFFFFF,
        0x22FFFFFF,
        0xD8FFFFFF.toInt(),
        0xFAFFFFFF.toInt(),
    )
    private val innerRimBaseColors = intArrayOf(
        0x98FFFFFF.toInt(),
        0x52FFFFFF,
        0x08FFFFFF,
        0x08FFFFFF,
        0x4EFFFFFF,
        0x90FFFFFF.toInt(),
    )
    private val rimBaseStops = floatArrayOf(0f, 0.28f, 0.46f, 0.54f, 0.72f, 1f)

    private fun scaleAlpha(color: Int, factor: Float): Int {
        val a = (((color ushr 24) and 0xFF) * factor.coerceIn(0f, 1f) + 0.5f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    /** 사용자 설정 키 모서리 둥글기(dp). 음수면 기본값(일반 7dp, 글래스 7*1.15dp)을 쓴다 */
    var keyRadiusDp: Float = -1f
        set(v) { if (field != v) { field = v; invalidate(); onRelayoutRequested?.invoke() } }

    /** 사용자 설정 배경·보조탭 모서리 둥글기(dp). 음수면 키 둥글기/기본값을 따른다 */
    var bgRadiusDp: Float = -1f
        set(v) { if (field != v) { field = v; invalidate(); onRelayoutRequested?.invoke() } }

    fun effectiveKeyRadiusPx(): Float = if (keyRadiusDp >= 0f) keyRadiusDp * density else 7 * density

    /** 글래스 테마의 키 모서리 반지름 (판 모서리와 동심원을 이루도록 한곳에서 정한다) */
    fun frostKeyRadiusPx(): Float = if (keyRadiusDp >= 0f) keyRadiusDp * density else 7 * density * FROST_KEY_RADIUS_SCALE

    /** 키 크기(가로·세로 중 작은 쪽)에 비례한 % 라운드 반지름 (작은 키는 곡률도 비례해서 작아진다) */
    fun keyRadiusFor(w: Float, h: Float, frost: Boolean = false): Float {
        val baseDp = if (keyRadiusDp >= 0f) keyRadiusDp else if (frost) 7f * FROST_KEY_RADIUS_SCALE else 7f
        val ratio = (baseDp / 40f).coerceIn(0f, 0.5f)
        return min(w, h) * ratio
    }

    fun keyRadiusFor(r: RectF, frost: Boolean = false): Float = keyRadiusFor(r.width(), r.height(), frost)

    /**
     * 유리판 위쪽 두 모서리 반지름(왼쪽·오른쪽) = 키 모서리 + 키가 판 가장자리에서 떨어진 거리.
     * 같은 중심의 곡선이라 모서리의 키가 판 밖으로 삐져나오지 않고, 여백이 0이면 키 모서리와 딱 맞는다.
     */
    fun frostPlateRadiiPx(): Pair<Float, Float> {
        val k = frostKeyRadiusPx()
        val cap = 10 * density
        return (k + minOf(padL, padT, cap)) to (k + minOf(padR, padT, cap))
    }

    /** 키보드 배경판 및 보조탭용 크기 비례(%) 둥글기 반지름 */
    fun plateRadiusFor(w: Float, h: Float, frost: Boolean = theme.frost || bgGlassOn || keyGlassOn): Float {
        val baseDp = if (bgRadiusDp >= 0f) bgRadiusDp else if (keyRadiusDp >= 0f) keyRadiusDp else if (frost) 7f * FROST_KEY_RADIUS_SCALE else 7f
        val ratio = (baseDp / 40f).coerceIn(0f, 0.5f)
        return min(w, h) * ratio
    }

    /** 보조탭용 배경 둥글기: 키보드 본판과 비슷한 체감 곡률이 되도록 보조탭 높이(약 44dp)에 맞춰 0~22dp를 0~50%로 매핑 */
    fun tabRadiusFor(w: Float, h: Float, frost: Boolean = theme.frost || bgGlassOn || keyGlassOn): Float {
        val baseDp = if (bgRadiusDp >= 0f) bgRadiusDp else if (keyRadiusDp >= 0f) keyRadiusDp else if (frost) 7f * FROST_KEY_RADIUS_SCALE else 7f
        val ratio = (baseDp / 6f * 0.5f).coerceIn(0f, 0.5f)
        return min(w, h) * ratio
    }

    /** 판 맨 위에서 뒤 앱 화면이 비치는 띠의 높이 (아래로 갈수록 불투명해져 판과 이어진다) */
    fun frostFadePx(): Float = FROST_FADE_DP * density

    /** 위 띠의 시작 불투명도(0~1). 전복붙 탭과 같은 값이라 탭과 판이 같은 재질로 이어져 보인다 */
    var frostFadeAlpha: Float = 0.5f
        set(v) { if (field != v) { field = v; invalidate() } }

    /** 모든 테마의 키 불투명도 비율 (0.2f ~ 1.0f) */
    var keyOpacity: Float = 1f
        set(v) { if (field != v) { field = v; invalidate() } }

    /** 모든 테마의 키보드 배경 판 불투명도 비율 (0f ~ 1f) */
    var bgOpacity: Float = 1f
        set(v) { if (field != v) { field = v; invalidate() } }

    private fun withBgOpacity(color: Int): Int {
        if (bgOpacity >= 0.999f) return color
        val a = (((color ushr 24) and 0xFF) * bgOpacity + 0.5f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    fun withKeyOpacity(color: Int): Int {
        if (keyOpacity >= 0.999f) return color
        val a = (((color ushr 24) and 0xFF) * keyOpacity + 0.5f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    var glassType: Int = -1
        set(v) { if (field != v) { field = v; invalidate() } }
    var keyGlassStrength: Float = 0.85f
        set(v) { if (field != v) { field = v; invalidate() } }
    var bgGlassStrength: Float = 0.85f
        set(v) { if (field != v) { field = v; invalidate() } }
    val effectiveGlassType: Int get() = effGlass
    private val effGlass: Int get() = if (glassType >= 0) glassType else if (theme.frost) 1 else 0
    val bgGlassOn: Boolean get() = if (glassType >= 0) bgGlassStrength > 0.01f else theme.frost
    val keyGlassOn: Boolean get() = if (glassType >= 0) keyGlassStrength > 0.01f else theme.frost

    private val fadeMaskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }

    /**
     * 글래스 상태에서 전복붙 탭이 판과 "같은 유리 표면"을 쓰는가 (탭이 켜져 있고 보일 때. [KeyboardHost]가 정한다).
     * 탭은 판과 떨어진 둥근 알약 그대로이고, 번짐 배경·빛·림 반사가 판과 같은 재질로 이어진다.
     */
    var frostTabAttached: Boolean = false
        set(v) { if (field != v) { field = v; invalidate() } }
    var fnTabDocked: Boolean = false
        set(v) { if (field != v) { field = v; invalidate() } }

    /** 이어 붙은 탭의 왼쪽 x (키보드 기준, 키보드 밖이면 음수). [KeyboardHost]가 정한다 */
    var frostTabLeftPx: Float = 0f
        set(v) { if (field != v) { field = v; if (fnTabDocked) invalidate() } }
    var frostTabOffsetYpx: Float = 0f
        set(v) { if (field != v) { field = v; if (fnTabDocked) invalidate() } }

    /** 탭 전체 폭 (탭 뷰의 onMeasure와 같은 식) */
    fun fnTabWidthPx(): Float {
        val count = fnTabKeys().size.let { if (it > 0) it else 3 }
        return fnTabLeftPx() + count * fnKeyWidthPx() + fnTabRightPx()
    }

    /** 판 위쪽에 놓인 탭(과 그 아래 틈)이 차지하는 높이(px). 같은 표면을 쓰지 않으면 0 */
    private fun frostExtraTopPx(): Float = if (fnTab && bgGlassOn) fnTabHeightPx() + fnTabGapPx() else 0f

    /**
     * 리드글래스(모루 유리) 세로 줄무늬: [pitchDp](키 4.5dp, 배경 5.5dp) 간격의 촘촘한 세로 굴절 마루 + 음영 골.
     * 투명도를 낮춰도 [strength](유리 강도) 그대로 100% 선명하게 유지된다.
     */
    private fun drawReedOverlay(
        canvas: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        strength: Float,
        pitchDp: Float = 5.5f,
        tabShiftX: Float = 0f,
        tabShiftY: Float = 0f,
        backdropAlpha: Float = 1f,
    ) {
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0.01f || r <= l || b <= t) return
        val rw = r - l
        val rh = b - t
        val reedLayer = canvas.saveLayer(l, t, r, b, null)
        val reedW = (pitchDp * density).coerceAtLeast(4f)
        val refractBmp = frostReedBlur ?: frostBlur ?: frostKeyBlur
        if (refractBmp != null && r > l && b > t) {
            val extra = frostExtraTopPx()
            val bw = refractBmp.width
            val bh = refractBmp.height
            val cols = kotlin.math.ceil(((r - l) / reedW).toDouble()).toInt().coerceAtMost(140)
            glassPaint.shader = null
            glassPaint.color = 0xFFFFFFFF.toInt()
            glassPaint.alpha = (232 * s * backdropAlpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            // 비선형 원통 렌즈 샘플링 + 모루를 따라 위아래로 흐르는 세로 굴절 오프셋
            val dstStops = floatArrayOf(0f, 0.18f, 0.50f, 0.82f, 1f)
            val srcStops = floatArrayOf(-0.46f, -0.13f, 0f, 0.13f, 0.46f)
            for (i in 0 until cols) {
                val cl = l + i * reedW
                val cr = minOf(cl + reedW, r)
                val cw = cr - cl
                if (cw <= 0.5f) break
                val stepShift = (if (i % 2 == 0) 1f else -1f) * 7.5f * density * s + kotlin.math.sin(i * 0.9f) * 5.5f * density * s
                val yShift = kotlin.math.sin(i * 1.35f) * 3.0f * density * s
                val sy = (t + extra + tabShiftY + yShift).toInt().coerceIn(0, max(0, bh - 2))
                val ey = (b + extra + tabShiftY + yShift).toInt().coerceIn(sy + 1, bh)
                val centerSrcX = (cl + cw * 0.5f) + tabShiftX + stepShift
                val span = cw * (1.15f - 0.35f * s)
                for (k in 0..3) {
                    val dl = cl + cw * dstStops[k]
                    val dr = cl + cw * dstStops[k + 1]
                    if (dr <= dl) continue
                    val sx = (centerSrcX + span * srcStops[k]).toInt().coerceIn(0, bw - 1)
                    val ex = (centerSrcX + span * srcStops[k + 1]).toInt().coerceIn(sx + 1, bw)
                    canvas.drawBitmap(refractBmp, Rect(sx, sy, ex, ey), RectF(dl, t, dr, b), glassPaint)
                }
            }
        }
        // 검정/회색 칠 없이: 골 경계는 투명(0x00FFFFFF)으로 비워 자연스럽게 어둡게 보이고, 마루 사면만 순수 흰빛 굴절 광택으로 대비
        val specHi = (0x54 * s).toInt().coerceIn(0, 255)
        val specSoft = (0x20 * s).toInt().coerceIn(0, 255)
        val rimRight = (0x28 * s).toInt().coerceIn(0, 255)
        glassPaint.style = Paint.Style.FILL
        glassPaint.color = 0xFFFFFFFF.toInt()
        glassPaint.shader = LinearGradient(
            l, 0f, l + reedW, 0f,
            intArrayOf(
                0x00FFFFFF,
                (specHi shl 24) or 0xFFFFFF,
                (specSoft shl 24) or 0xFFFFFF,
                0x00FFFFFF,
                (rimRight shl 24) or 0xFFFFFF,
                0x00FFFFFF,
            ),
            floatArrayOf(0f, 0.14f, 0.36f, 0.62f, 0.88f, 1f),
            Shader.TileMode.REPEAT,
        )
        canvas.drawRect(l, t, r, b, glassPaint)
        glassPaint.shader = null

        // 상·하·좌·우 테두리 쪽으로 갈수록 모루가 테두리 빛 반사 속으로 자연스럽게 사라지도록 4면 페이드 마스킹
        val isPlateReed = pitchDp >= 7.8f
        val edgeFadeX = minOf(rw * 0.24f, (if (isPlateReed) 16f else 8f) * density)
        val edgeFadeY = minOf(rh * 0.28f, (if (isPlateReed) 16f else 8f) * density)
        val fx = (edgeFadeX / rw).coerceIn(0.02f, 0.45f)
        val fy = (edgeFadeY / rh).coerceIn(0.02f, 0.45f)
        fadeMaskPaint.shader = LinearGradient(
            l, 0f, r, 0f,
            intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, fx, 1f - fx, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(l, t, r, b, fadeMaskPaint)
        fadeMaskPaint.shader = LinearGradient(
            0f, t, 0f, b,
            intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, fy, 1f - fy, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(l, t, r, b, fadeMaskPaint)
        fadeMaskPaint.shader = null
        canvas.restoreToCount(reedLayer)
    }

    /**
     * 유리 표면(일반·블러·리드글래스·3D)을 판 좌표계의 [l,t,r,b] 칸에 그린다.
     * 모든 셰이더 호출 직전에 glassPaint.color = 0xFFFFFFFF.toInt() (alpha=255)를 보장해
     * 4종 옵션 모두 유리 광택과 사선 반사가 정확한 강도로 그려진다.
     */
    private fun drawFrostSurface(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        val bgG = if (glassType >= 0) bgGlassStrength.coerceIn(0f, 1f) else 1f
        val gType = effGlass
        val extra = frostExtraTopPx()
        glassPaint.style = Paint.Style.FILL; glassPaint.shader = null; glassPaint.color = 0xFFFFFFFF.toInt()
        if (gType == 0) {
            // 1. 일반(맑은 크리스탈 유리): 블러 없이 선명한 배경 위에 맑은 유리 광택과 사선 반사광 적용
            val shader = theme.backgroundShader(w, h)
            if (shader == null) {
                glassPaint.color = withBgOpacity(theme.background)
                canvas.drawRect(l, t, r, b, glassPaint)
            } else {
                glassPaint.color = 0xFFFFFFFF.toInt()
                glassPaint.shader = shader
                glassPaint.alpha = (bgOpacity * 255f).toInt().coerceIn(0, 255)
                canvas.drawRect(l, t, r, b, glassPaint)
                glassPaint.shader = null
            }
            val clearTintA = (((theme.frostTint ushr 24) and 0xFF) * bgG * 0.45f).toInt().coerceIn(0, 255)
            glassPaint.color = (clearTintA shl 24) or (theme.frostTint and 0x00FFFFFF)
            canvas.drawRect(l, t, r, b, glassPaint)
        } else {
            // 2. 블러 / 3. 리드글래스 / 4. 3D: 가우시안 블러 배경 + 유리 틴트
            val bmp = frostBlur
            if (bmp != null) {
                val sl = l.coerceIn(0f, bmp.width.toFloat()); val sr = r.coerceIn(0f, bmp.width.toFloat())
                val st = (t + extra).coerceIn(0f, (bmp.height - 2).coerceAtLeast(0).toFloat()); val sb = (b + extra).coerceIn((st + 1f).coerceAtMost(bmp.height.toFloat()), bmp.height.toFloat())
                glassPaint.color = 0xFFFFFFFF.toInt()
                glassPaint.alpha = (bgOpacity * 255f).toInt().coerceIn(0, 255)
                if (sr > sl && sb > st) canvas.drawBitmap(bmp, Rect(sl.toInt(), st.toInt(), sr.toInt(), sb.toInt()), RectF(l, t, r, b), glassPaint)
            }
            glassPaint.shader = null
            val tintBase = withBgOpacity(theme.frostTint)
            val tintA = (((tintBase ushr 24) and 0xFF) * bgG * (if (theme.frost) 0.55f else 0.35f)).toInt().coerceIn(0, 255)
            glassPaint.color = (tintA shl 24) or (tintBase and 0x00FFFFFF)
            canvas.drawRect(l, t, r, b, glassPaint)
        }

        // 글래스 효과 적용 시 바탕을 한 톤 어둡게 눌러 순수 흰빛 유리 반사 대비를 극대화
        if (bgG > 0.01f) {
            val effOp = if (t >= 0f) (0.35f + 0.65f * bgOpacity.coerceIn(0f, 1f)) else 1f
            val darkA = (0x1C * bgG * effOp).toInt().coerceIn(0, 255)
            glassPaint.shader = null
            glassPaint.color = (darkA shl 24) or 0x000000
            canvas.drawRect(l, t, r, b, glassPaint)
        }

        // 모든 셰이더 렌더링 전 alpha=255 복원 (이전 color 설정의 낮은 알파가 셰이더에 곱해지지 않도록)
        glassPaint.color = 0xFFFFFFFF.toInt()

        if (gType == 1) {
            // 2. 블러: 서리 낀 젖빛 유리 확산 광택 (좌상단 + 우하단 부드러운 블룸)
            val bHi = (0x44 * bgG).toInt().coerceIn(0, 255)
            val bMid = (0x20 * bgG).toInt().coerceIn(0, 255)
            glassPaint.shader = android.graphics.RadialGradient(
                w * 0.25f, -extra + h * 0.22f, max(w, h) * 0.68f,
                intArrayOf((bHi shl 24) or 0xFFFFFF, (bMid shl 24) or 0xFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(l, t, r, b, glassPaint)
            val b2 = (0x32 * bgG).toInt().coerceIn(0, 255)
            glassPaint.shader = android.graphics.RadialGradient(
                w * 0.78f, h * 0.78f, max(w, h) * 0.58f,
                intArrayOf((b2 shl 24) or (theme.accent and 0x00FFFFFF), 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(l, t, r, b, glassPaint)
        } else if (gType == 2) {
            // 3. 리드글래스: 9.0dp 간격 세로 모루 광학 굴절
            drawReedOverlay(canvas, l, t, r, b, bgG, pitchDp = 8.25f, backdropAlpha = if (t >= 0f) bgOpacity else 1f)
        } else if (gType == 4) {
            // 5. 무광(프로스티드 젖빛 3D): 밀키 젖빛 확산 + 미세 실리카 입자 (날카로운 사선 거울광 제외)
            val milkA = (0x32 * bgG).toInt().coerceIn(0, 255)
            glassPaint.shader = null
            glassPaint.color = (milkA shl 24) or 0xFFFFFF
            canvas.drawRect(l, t, r, b, glassPaint)
            glassPaint.color = 0xFFFFFFFF.toInt()
            val mHi = (0x36 * bgG).toInt().coerceIn(0, 255)
            glassPaint.shader = android.graphics.RadialGradient(
                w * 0.24f, -extra + h * 0.20f, max(w, h) * 0.72f,
                intArrayOf((mHi shl 24) or 0xFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(l, t, r, b, glassPaint)
        }

        // 유리판 전체를 비스듬히 가로지르는 대각선 유리창 빛반사면 (3D·무광 제외, 기존 대비 70% 강도)
        if (gType != 3 && gType != 4) {
        glassPaint.color = 0xFFFFFFFF.toInt()
        val diagScale = bgG * 0.70f
        val diagTopA = (0x3A * diagScale).toInt().coerceIn(0, 255)
        val diagMidA = (0x22 * diagScale).toInt().coerceIn(0, 255)
        val diagEdgeA = (0x4A * diagScale).toInt().coerceIn(0, 255)
        glassPaint.shader = LinearGradient(
            0f, -extra, w * 0.65f, h * 0.95f,
            intArrayOf(
                (diagTopA shl 24) or 0xFFFFFF,
                (diagMidA shl 24) or 0xFFFFFF,
                (diagEdgeA shl 24) or 0xFFFFFF,
                0x00FFFFFF,
                0x00FFFFFF,
            ),
            floatArrayOf(0f, 0.42f, 0.488f, 0.512f, 0.60f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(l, t, r, b, glassPaint)

        // 좌상단 테두리와 우하단 테두리를 따라 흐르는 가장자리 유리 광택
        val topA1 = ((if (gType == 3) 0x58 else 0x44) * bgG).toInt().coerceIn(0, 255)
        val topA2 = ((if (gType == 3) 0x1E else 0x14) * bgG).toInt().coerceIn(0, 255)
        glassPaint.shader = LinearGradient(
            0f, -extra, 0f, h * 0.34f,
            intArrayOf((topA1 shl 24) or 0xFFFFFF, (topA2 shl 24) or 0xFFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(l, t, r, b, glassPaint)

        val brA1 = ((if (gType == 3) 0x1E else 0x14) * bgG).toInt().coerceIn(0, 255)
        val brA2 = ((if (gType == 3) 0x52 else 0x3E) * bgG).toInt().coerceIn(0, 255)
        glassPaint.shader = LinearGradient(
            w * 0.38f, h * 0.66f, w, h,
            intArrayOf(0x00FFFFFF, (brA1 shl 24) or 0xFFFFFF, (brA2 shl 24) or 0xFFFFFF),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(l, t, r, b, glassPaint)
        }
        glassPaint.shader = null
    }

    /**
     * 이어 붙은 전복붙 탭의 유리 바탕. 판과 같은 표면을 [alpha](0~255)만큼 투명하게 그리고,
     * 테두리를 따라 흐르는 안쪽 림 반사는 유리 강도 그대로 선명하게 유지한다.
     */
    fun drawFrostTabSurface(canvas: Canvas, tabW: Float, tabH: Float, rlb: Float = tabRadiusFor(tabW, tabH), rrb: Float = tabRadiusFor(tabW, tabH)) {
        ensureFrostBitmaps()
        val shift = if (frostTabOffsetYpx > 0f) frostTabOffsetYpx else tabH + fnTabGapPx()
        canvas.save()
        canvas.translate(-frostTabLeftPx, shift)
        drawFrostSurface(canvas, frostTabLeftPx, -shift, frostTabLeftPx + tabW, -shift + tabH)
        canvas.restore()
        if (glassType >= 0 && bgGlassStrength > 0.01f) {
            val bgG = bgGlassStrength.coerceIn(0f, 1f)
            val r = tabRadiusFor(tabW, tabH)
            if (effGlass == 3) {
                draw3dDomeOptics(canvas, RectF(0f, 0f, tabW, tabH), r, r, rrb, rlb, bgG, isPlate = true, skipBottom = fnTabDocked)
            } else if (effGlass == 4) {
                drawMattePillowOptics(canvas, RectF(0f, 0f, tabW, tabH), r, r, rrb, rlb, bgG, isPlate = true)
            } else {
                val ins = 1.6f * density
                glassPaint.style = Paint.Style.STROKE
                glassPaint.color = 0xFFFFFFFF.toInt()
                glassPaint.strokeWidth = 1.4f * density
                glassPaint.shader = frostInnerRimShader(tabW, tabH, bgG)
                val ir = (r - ins).coerceAtLeast(0f)
                val irbL = if (rlb <= 0.5f) 0f else (rlb - ins).coerceAtLeast(0f)
                val irbR = if (rrb <= 0.5f) 0f else (rrb - ins).coerceAtLeast(0f)
                if (kotlin.math.abs(irbL - ir) < 0.5f && kotlin.math.abs(irbR - ir) < 0.5f) {
                    canvas.drawRoundRect(ins, ins, tabW - ins, tabH - ins, ir, ir, glassPaint)
                } else {
                    canvas.save()
                    if (fnTabDocked) {
                        canvas.clipOutRect(0f, tabH - 0.5f * density, tabW, tabH + 1f)
                    }
                    val innerPath = Path().apply {
                        addRoundRect(
                            RectF(ins, ins, tabW - ins, tabH - ins),
                            floatArrayOf(ir, ir, ir, ir, irbR, irbR, irbL, irbL),
                            Path.Direction.CW,
                        )
                    }
                    canvas.drawPath(innerPath, glassPaint)
                    canvas.restore()
                }
                glassPaint.shader = null
                glassPaint.style = Paint.Style.FILL
            }
        }
    }

    /** 가장자리 바깥 림 그라데이션: 좌상단 테두리와 우하단 테두리를 따라 흐르는 빛반사 */
    fun frostRimShader(w: Float, h: Float, strength: Float = if (glassType >= 0) bgGlassStrength else 1f): Shader {
        val s = strength.coerceIn(0f, 1f)
        val cols = IntArray(rimBaseColors.size) { scaleAlpha(rimBaseColors[it], s) }
        return LinearGradient(0f, 0f, w, h, cols, rimBaseStops, Shader.TileMode.CLAMP)
    }

    /** 가장자리 안쪽 부드러운 림 그라데이션: 테두리 곡선을 따라 은은하게 감싸는 빛반사 */
    fun frostInnerRimShader(w: Float, h: Float, strength: Float = if (glassType >= 0) bgGlassStrength else 1f): Shader {
        val s = strength.coerceIn(0f, 1f)
        val cols = IntArray(innerRimBaseColors.size) { scaleAlpha(innerRimBaseColors[it], s) }
        return LinearGradient(0f, 0f, w, h, cols, rimBaseStops, Shader.TileMode.CLAMP)
    }

    private val domeClipPath = Path()
    private val specCutPath = Path()

    /**
     * 3D 볼록 카보숑 유리 광학 효과 (19169.jpg 기준):
     * 1) 상단 돔 아치형 반사광 (top ~ 35% 영역, 모서리 곡률을 따라 둥글게 맺히며 중앙으로 갈수록 맑게 소멸)
     * 2) 하단 은은한 역반사 커스틱 (75% ~ 95% 영역) + 좌·우 측면 어두운 입체 음영으로 위아래로 솟은 볼록감 표현
     * 3) 안쪽 굴절 골(어두운 띠) + 바깥 밝은 유리 베벨 테두리 링으로 3단 두께감 형성
     */
    private fun draw3dDomeOptics(
        canvas: Canvas,
        r: RectF,
        radTL: Float,
        radTR: Float = radTL,
        radBR: Float = radTL,
        radBL: Float = radTL,
        strength: Float,
        isPlate: Boolean = false,
        skipBottom: Boolean = false,
    ) {
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0.01f || r.width() <= 4f || r.height() <= 4f) return

        domeClipPath.rewind()
        domeClipPath.addRoundRect(
            r,
            floatArrayOf(radTL, radTL, radTR, radTR, radBR, radBR, radBL, radBL),
            Path.Direction.CW,
        )

        canvas.save()
        canvas.clipPath(domeClipPath)

        val dw = r.width()
        val dh = r.height()

        // 1. 바깥 0dp 흰색 테두리 림: 양옆(좌·우 변)은 밝게(0xEA), 위·아래는 조금 덜 밝게(0x9A~0xA6)해 입체 균형 형성
        glassPaint.style = Paint.Style.STROKE
        glassPaint.color = 0xFFFFFFFF.toInt()
        val rimW = (if (isPlate) (if (dh < 90f * density) 2.0f else 2.6f) else 1.4f) * density
        glassPaint.strokeWidth = rimW
        val rimTopA = if (isPlate) 0x58 else 0x4A
        val rimSideA = if (isPlate) 0xEA else 0xEA
        val rimBotA = if (isPlate) 0x5E else 0x52
        glassPaint.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(
                scaleAlpha((rimTopA shl 24) or 0xFFFFFF, s),
                scaleAlpha((rimSideA shl 24) or 0xFFFFFF, s),
                scaleAlpha((rimSideA shl 24) or 0xFFFFFF, s),
                scaleAlpha((rimBotA shl 24) or 0xFFFFFF, s),
            ),
            floatArrayOf(0f, 0.24f, 0.76f, 1f),
            Shader.TileMode.CLAMP,
        )
        if (skipBottom) {
            canvas.save()
            canvas.clipOutRect(r.left + rimW * 0.75f, r.bottom - rimW - 0.5f * density, r.right - rimW * 0.75f, r.bottom + 1f)
            canvas.drawPath(domeClipPath, glassPaint)
            canvas.restore()
        } else {
            canvas.drawPath(domeClipPath, glassPaint)
        }
        glassPaint.shader = null

        // 2. 테두리보다 안쪽으로 한 단계 작아진 내부 돔 반사 (키·배경 모두 addRoundRect 축소로 곡률이 깎이지 않고 동그랗게 유지)
        val insetX = (if (isPlate) 4.8f else 4.0f) * density
        val insetY = (if (isPlate) 3.8f else 3.2f) * density
        val innerL = r.left + insetX
        val innerR = r.right - insetX
        val innerW = (innerR - innerL).coerceAtLeast(2f)
        val innerH = (dh - 2 * insetY).coerceAtLeast(2f)

        glassPaint.style = Paint.Style.FILL
        glassPaint.color = 0xFFFFFFFF.toInt()
        val topY0 = r.top + insetY
        val topCurv = if (isPlate) (minOf(radTL, radTR) - insetX * 0.35f + 0.8f * density).coerceAtLeast(2f) else (minOf(radTL, radTR) * 0.8f + 0.8f * density).coerceAtLeast(2f)
        val domeArchRx = topCurv
        val maxTopReach = if (isPlate && dh < 90f * density) innerH * 0.85f else innerH * 0.55f
        val topReach = maxOf(innerH * (if (isPlate) 0.28f else 0.36f), domeArchRx + 2f * density).coerceAtMost(maxTopReach)
        val topFadeRy = minOf(topReach * 0.45f, (topReach - domeArchRx).coerceAtLeast(0f))
        val topRect = RectF(innerL, topY0, innerR, topY0 + topReach)
        val topPath = Path().apply {
            addRoundRect(
                topRect,
                floatArrayOf(domeArchRx, domeArchRx, domeArchRx, domeArchRx, domeArchRx * 0.85f, topFadeRy, domeArchRx * 0.85f, topFadeRy),
                Path.Direction.CW,
            )
        }
        glassPaint.shader = LinearGradient(
            0f, topY0, 0f, topY0 + topReach,
            intArrayOf(
                scaleAlpha(((if (isPlate) (if (dh < 90f * density) 0x6A else 0x9C) else 0xA2) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x68 else 0x6A) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x28 else 0x34) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x0C else 0x12) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x03 else 0x04) shl 24) or 0xFFFFFF, s),
                0x00FFFFFF,
            ),
            floatArrayOf(0f, 0.25f, 0.52f, 0.78f, 0.94f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(topPath, glassPaint)

        if (skipBottom) {
            canvas.restore()
            glassPaint.shader = null
            return
        }
        // 3. 하단 볼록 역반사 (동일하게 곡률이 깎이지 않고 키·배경 윤곽을 따라 동그랗게 유지)
        val botY0 = r.bottom - insetY
        val botCurv = if (isPlate) (minOf(radTL, radTR) - insetX * 0.35f + 0.8f * density).coerceAtLeast(2f) else (minOf(radBL, radBR) * 0.8f + 0.8f * density).coerceAtLeast(2f)
        val botArchRx = botCurv
        val botReach = maxOf(innerH * (if (isPlate) 0.20f else 0.28f), botArchRx + 2f * density).coerceAtMost(innerH * 0.45f)
        val botFadeRy = minOf(botReach * 0.45f, (botReach - botArchRx).coerceAtLeast(0f))
        val botRect = RectF(innerL, botY0 - botReach, innerR, botY0)
        val botPath = Path().apply {
            addRoundRect(
                botRect,
                floatArrayOf(botArchRx * 0.85f, botFadeRy, botArchRx * 0.85f, botFadeRy, botArchRx, botArchRx, botArchRx, botArchRx),
                Path.Direction.CW,
            )
        }
        glassPaint.shader = LinearGradient(
            0f, botY0 - botReach, 0f, botY0,
            intArrayOf(
                0x00FFFFFF,
                scaleAlpha(((if (isPlate) 0x03 else 0x04) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x12 else 0x18) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x28 else 0x34) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x48 else 0x5E) shl 24) or 0xFFFFFF, s),
            ),
            floatArrayOf(0f, 0.10f, 0.35f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(botPath, glassPaint)

        canvas.restore()
        glassPaint.shader = null
    }

    private var cachedMatteNoiseShader: BitmapShader? = null
    private fun matteNoiseShader(): BitmapShader {
        cachedMatteNoiseShader?.let { return it }
        val sz = 128
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val px = IntArray(sz * sz)
        var seed = 0x5A17C391.toInt()
        for (i in px.indices) {
            seed = seed * 1664525 + 1013904223
            val v = (seed ushr 24) and 0xFF
            // 순수 빛 표현 원칙: 검정 입자 없이 흰색 실리카 미세 입자만 0~20 알파로 분포
            val a = (v * 34) / 255
            px[i] = (a shl 24) or 0xFFFFFF
        }
        bmp.setPixels(px, 0, sz, 0, 0, sz, sz)
        return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { cachedMatteNoiseShader = it }
    }

    /**
     * 무광(프로스티드 젖빛 3D) 유리 효과:
     * 날카로운 거울 반사선 대신 부드러운 필로우 베벨(가장자리 입체 확산) + 미세 실리카 입자 + 새틴 림.
     */
    private fun drawMattePillowOptics(
        canvas: Canvas,
        r: RectF,
        radTL: Float,
        radTR: Float = radTL,
        radBR: Float = radTL,
        radBL: Float = radTL,
        strength: Float,
        isPlate: Boolean = false,
        skipBottom: Boolean = false,
    ) {
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0.01f || r.width() <= 4f || r.height() <= 4f) return

        domeClipPath.rewind()
        domeClipPath.addRoundRect(
            r,
            floatArrayOf(radTL, radTL, radTR, radTR, radBR, radBR, radBL, radBL),
            Path.Direction.CW,
        )

        canvas.save()
        canvas.clipPath(domeClipPath)

        // 1. 미세 실리카 입자 질감 (순수 흰색 노이즈)
        glassPaint.style = Paint.Style.FILL
        glassPaint.color = 0xFFFFFFFF.toInt()
        glassPaint.alpha = (s * (if (isPlate) 220f else 240f)).toInt().coerceIn(0, 255)
        glassPaint.shader = matteNoiseShader()
        canvas.drawRect(r, glassPaint)
        glassPaint.alpha = 255
        glassPaint.shader = null

        // 2. 부드러운 3D 필로우 베벨 (좌상단 광원 방향이 은은하게 솟아오른 젖빛 입체 테두리)
        val pillowW = (if (isPlate) 20.0f else 8.5f) * density
        glassPaint.style = Paint.Style.STROKE
        glassPaint.strokeWidth = pillowW
        glassPaint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(
                scaleAlpha(((if (isPlate) 0x5A else 0x4E) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x1A else 0x20) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x10 else 0x14) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x32 else 0x30) shl 24) or 0xFFFFFF, s),
            ),
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(domeClipPath, glassPaint)

        // 3. 안쪽 2차 새틴 베벨 (가장자리에서 부드럽게 감기는 입체감)
        glassPaint.strokeWidth = (if (isPlate) 7.5f else 3.6f) * density
        glassPaint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(
                scaleAlpha(((if (isPlate) 0x68 else 0x5C) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x22 else 0x28) shl 24) or 0xFFFFFF, s),
                scaleAlpha(((if (isPlate) 0x40 else 0x3E) shl 24) or 0xFFFFFF, s),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(domeClipPath, glassPaint)

        // 4. 바깥 0dp 새틴 무광 테두리 림 (거울처럼 날카롭지 않고 은은한 젖빛 윤곽)
        glassPaint.strokeWidth = (if (isPlate) 1.6f else 1.2f) * density
        glassPaint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(
                scaleAlpha(0x88FFFFFF.toInt(), s),
                scaleAlpha(0x46FFFFFF, s),
                scaleAlpha(0x62FFFFFF, s),
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(domeClipPath, glassPaint)

        canvas.restore()
        glassPaint.shader = null
        glassPaint.style = Paint.Style.FILL
    }

    private fun drawFrostPlate(canvas: Canvas) {
        ensureFrostBitmaps()
        val w = width.toFloat(); val h = height.toFloat()
        val bgG = if (glassType >= 0) bgGlassStrength.coerceIn(0f, 1f) else 1f
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        val rPlate = plateRadiusFor(w, h); val rl = rPlate; val rr = rPlate
        glassPath.rewind()
        glassPath.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(rl, rl, rr, rr, 0f, 0f, 0f, 0f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(glassPath)
        drawFrostSurface(canvas, 0f, 0f, w, h)
        canvas.restore()
        // 맨 위 띠는 반투명(뒤 앱이 비침) → 아래로 부드럽게 불투명해져 판과 이어진다 (단, 보조탭이 이어진 구간은 이어진 유리로 유지)
        if (!(fnTabDocked && fnTab)) {
            fadeMaskPaint.shader = LinearGradient(0f, 0f, 0f, frostFadePx(), ((frostFadeAlpha * 255f).toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF, 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, fadeMaskPaint)
        }
        canvas.restoreToCount(layer)
        // 테두리를 따라 빛나는 이중 림 (셰이더 설정 전 반드시 color=0xFFFFFFFF로 alpha=255 복원)
        canvas.save()
        if (fnTabDocked && fnTab && bgGlassOn) {
            val tw = fnTabWidthPx()
            val th = fnTabHeightPx()
            val ty = -frostTabOffsetYpx
            val tr = tabRadiusFor(tw, th, true)
            val tabClipPath = Path().apply {
                addRoundRect(
                    RectF(frostTabLeftPx + 0.5f * density, ty, frostTabLeftPx + tw - 0.5f * density, ty + th),
                    floatArrayOf(tr, tr, tr, tr, tr, tr, tr, tr),
                    Path.Direction.CW,
                )
            }
            canvas.clipOutPath(tabClipPath)
        }
        glassPaint.style = Paint.Style.STROKE
        glassPaint.shader = null

        if (effGlass == 3) {
            // 3D 모드: fadeMaskPaint 이후에 그려 판 테두리 림과 상·하단 돔 반사가 지워지지 않고 선명하게 유지됨
            draw3dDomeOptics(canvas, RectF(0f, 0f, w, h), rl, rr, 0f, 0f, bgG, isPlate = true)
        } else if (effGlass == 4) {
            // 무광 모드: fadeMaskPaint 이후에 필로우 베벨과 실리카 입자·새틴 림을 선명하게 유지
            drawMattePillowOptics(canvas, RectF(0f, 0f, w, h), rl, rr, 0f, 0f, bgG, isPlate = true)
        } else {
            glassPaint.color = 0xFFFFFFFF.toInt()
            glassPaint.strokeWidth = 2.0f * density
            glassPaint.shader = frostRimShader(w, h, bgG)
            canvas.drawPath(glassPath, glassPaint)
            glassPaint.strokeWidth = 1.4f * density
            glassPaint.shader = frostInnerRimShader(w, h, bgG)
            canvas.save()
            val ins = 2.2f * density
            canvas.translate(ins, ins)
            canvas.scale((w - 2 * ins) / w, (h - 2 * ins) / h)
            canvas.drawPath(glassPath, glassPaint)
            canvas.restore()
        }
        canvas.restore()
        glassPaint.shader = null
        glassPaint.style = Paint.Style.FILL
    }

    private val keyPath = Path()

    /**
     * 판 위의 키(및 전복붙 탭 버튼): 일반·블러·리드글래스·3D 4종 유리 효과를 모두 지원한다.
     * 모든 셰이더 호출 전 glassPaint.color = 0xFFFFFFFF.toInt() (alpha=255)를 보장해
     * 키 투명도(keyOpacity)가 낮거나 반투명 기능키여도 유리 질감이 100% 선명하게 표현된다.
     */
    internal fun drawFrostKey(canvas: Canvas, r: RectF, radius: Float, tint: Int, pressed: Boolean, tabShiftX: Float = 0f, tabShiftY: Float = 0f) {
        val keyG = if (glassType >= 0) keyGlassStrength.coerceIn(0f, 1f) else 1f
        val gType = effGlass
        keyPath.rewind()
        keyPath.addRoundRect(r, radius, radius, Path.Direction.CW)
        glassPaint.shader = null
        glassPaint.style = Paint.Style.FILL
        // ① 그림자: 3D 모드는 아래쪽 어두운 음영을 없애고 맑은 유리 외곽 윤곽만 은은하게 둔다
        canvas.save()
        canvas.clipOutPath(keyPath)
        if (gType == 3 || gType == 4) {
            // 3D/무광 모드: 바깥쪽에도 검정·회색 음영 없이 순수 유리 테두리로만 분리
        } else if (theme.frostStyle == 7) {
            glassPaint.color = scaleAlpha(0x2873685C, keyG)
            canvas.drawRoundRect(r.left + density, r.top + 1.8f * density, r.right - density, r.bottom + 1.8f * density, radius, radius, glassPaint)
        } else {
            glassPaint.color = scaleAlpha(0x1A000000, keyG)
            canvas.drawRoundRect(r.left - density, r.top + 2 * density, r.right + density, r.bottom + 4 * density, radius + density, radius + density, glassPaint)
            glassPaint.color = scaleAlpha(0x36000000, keyG)
            canvas.drawRoundRect(r.left, r.top + density, r.right, r.bottom + 1.5f * density, radius, radius, glassPaint)
        }
        canvas.restore()

        // ② 색입힌 투명 면 (블러·리드·3D는 뒷배경 굴절 블러를 투과하고, 일반은 맑은 크리스탈 면으로 표현)
        val useBlurRefract = theme.frostStyle == 7 || (glassType >= 0 && gType != 0)
        if (useBlurRefract) {
            canvas.save()
            canvas.clipPath(keyPath)
            frostKeyBlur?.let { kb ->
                val et = frostExtraTopPx()
                val ox = (if (gType == 2) 3.6f else 2.8f) * density
                val oy = 2.2f * density
                val sx = (r.left + tabShiftX + ox).toInt().coerceIn(0, kb.width - 1)
                val sy = (r.top + et + tabShiftY + oy).toInt().coerceIn(0, kb.height - 1)
                val ex = (r.right + tabShiftX + ox).toInt().coerceIn(sx + 1, kb.width)
                val ey = (r.bottom + et + tabShiftY + oy).toInt().coerceIn(sy + 1, kb.height)
                val src = Rect(sx, sy, ex, ey)
                if (src.width() > 0 && src.height() > 0) {
                    glassPaint.color = 0xFFFFFFFF.toInt()
                    glassPaint.alpha = (keyG * 175f).toInt().coerceIn(0, 205)
                    canvas.drawBitmap(kb, src, r, glassPaint)
                }
            }
            val base = if (pressed) withKeyOpacity(theme.keyPressed) else tint
            val baseA = (base ushr 24) and 0xFF
            val cr = (base ushr 16) and 0xFF
            val cg = (base ushr 8) and 0xFF
            val cb = base and 0xFF
            val rgb = base and 0x00FFFFFF
            val lr = (cr * 0.78f + 255 * 0.22f).toInt().coerceIn(0, 255)
            val lg = (cg * 0.78f + 255 * 0.22f).toInt().coerceIn(0, 255)
            val lb = (cb * 0.78f + 255 * 0.22f).toInt().coerceIn(0, 255)
            val liftRgb = (lr shl 16) or (lg shl 8) or lb
            val glassTrans = if (glassType >= 0 && keyOpacity < 0.99f) (1f - 0.24f * keyG) else 1f
            if (gType == 3 || gType == 4) {
                val uniA = (baseA * glassTrans).toInt().coerceIn(0, 255)
                glassPaint.shader = null
                glassPaint.color = (uniA shl 24) or rgb
                canvas.drawRoundRect(r, radius, radius, glassPaint)
            } else {
                val aTop = (baseA * 1.02f * glassTrans).toInt().coerceIn(0, 255)
                val aMid = (baseA * 0.74f * glassTrans).toInt().coerceIn(0, 255)
                val aBot = (baseA * 0.90f * glassTrans).toInt().coerceIn(0, 255)
                glassPaint.color = 0xFFFFFFFF.toInt()
                glassPaint.shader = LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    intArrayOf((aTop shl 24) or liftRgb, (aMid shl 24) or rgb, (aBot shl 24) or rgb),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRoundRect(r, radius, radius, glassPaint)
                glassPaint.shader = null
            }
            canvas.restore()
        } else {
            glassPaint.color = if (pressed) withKeyOpacity(theme.keyPressed) else tint
            canvas.drawRoundRect(r, radius, radius, glassPaint)
        }

        // 글래스 효과 적용 시 키 본체를 한 톤 어둡게 눌러 유리 림·돔 반사 대비 강화 (keyOpacity=0%면 투명 유지)
        if (keyG > 0.01f && keyOpacity > 0.01f) {
            val darkKeyA = (0x18 * keyG * keyOpacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            if (darkKeyA > 0) {
                glassPaint.shader = null
                glassPaint.color = (darkKeyA shl 24) or 0x000000
                canvas.drawRoundRect(r, radius, radius, glassPaint)
            }
        }

        // ③④ 면 위의 유리 질감 (셰이더 호출 전 반드시 color=0xFFFFFFFF로 alpha=255 복원!)
        glassPaint.color = 0xFFFFFFFF.toInt()
        canvas.save()
        canvas.clipPath(keyPath)
        val h = r.height(); val w = r.width()
        if (gType != 3 && gType != 4) {
            val topA = (0x5E * keyG).toInt().coerceIn(0, 255)
            val topMidA = (0x1A * keyG).toInt().coerceIn(0, 255)
            val botMidA = (0x18 * keyG).toInt().coerceIn(0, 255)
            val botA = (0x46 * keyG).toInt().coerceIn(0, 255)
            glassPaint.shader = LinearGradient(
                0f, r.top, 0f, r.bottom,
                intArrayOf(
                    (topA shl 24) or 0xFFFFFF,
                    (topMidA shl 24) or 0xFFFFFF,
                    0x00FFFFFF,
                    0x00FFFFFF,
                    (botMidA shl 24) or 0xFFFFFF,
                    (botA shl 24) or 0xFFFFFF,
                ),
                floatArrayOf(0f, 0.22f, 0.36f, 0.64f, 0.78f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(r, glassPaint)

            val tlEdgeA = (0x4C * keyG).toInt().coerceIn(0, 255)
            val brEdgeA = (0x54 * keyG).toInt().coerceIn(0, 255)
            glassPaint.shader = LinearGradient(
                r.left, r.top, r.right, r.bottom,
                intArrayOf(
                    (tlEdgeA shl 24) or 0xFFFFFF,
                    0x00FFFFFF,
                    0x00FFFFFF,
                    (brEdgeA shl 24) or 0xFFFFFF,
                ),
                floatArrayOf(0f, 0.26f, 0.74f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(r, glassPaint)
        }

        if (gType == 1) {
            // 블러: 서리 낀 젖빛 확산 광택을 키캡 내부에 은은하게 추가
            val frostInnerA = (0x34 * keyG).toInt().coerceIn(0, 255)
            glassPaint.shader = android.graphics.RadialGradient(
                r.centerX(), r.centerY(), max(w, h) * 0.65f,
                intArrayOf(0x08FFFFFF, (frostInnerA shl 24) or 0xFFFFFF),
                floatArrayOf(0.2f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(r, radius, radius, glassPaint)
        } else if (gType == 2) {
            // 리드글래스: 키캡 내부 7.0dp 간격의 은은한 세로 모루 광학 굴절
            drawReedOverlay(canvas, r.left, r.top, r.right, r.bottom, keyG, pitchDp = 7.2f, tabShiftX = tabShiftX, tabShiftY = tabShiftY, backdropAlpha = keyOpacity)
        } else if (gType == 3) {
            // 3D: 좌상단 사선 곡면 경계 반사면 + 상·하단 테두리 응축 후 소멸 효과
            draw3dDomeOptics(canvas, r, radius, radius, radius, radius, keyG, isPlate = false)
        } else if (gType == 4) {
            // 무광: 프로스티드 젖빛 3D 필로우 베벨 + 미세 실리카 입자 + 새틴 림
            val milkKeyA = (0x1C * keyG).toInt().coerceIn(0, 255)
            glassPaint.shader = null
            glassPaint.color = (milkKeyA shl 24) or 0xFFFFFF
            canvas.drawRoundRect(r, radius, radius, glassPaint)
            drawMattePillowOptics(canvas, r, radius, radius, radius, radius, keyG, isPlate = false)
        }
        glassPaint.shader = null
        canvas.restore()

        // ⑤ 테두리를 따라 빛나는 이중 유리 림 (3D는 4면이 균일하게 밝은 유리 베벨 테두리 + 얇은 어두운 외곽선)
        glassPaint.style = Paint.Style.STROKE
        if (gType == 3 || gType == 4) {
            // 3D/무광 모드: 테두리가 내부 광학 함수에서 0dp 경계 안쪽으로 통합되어 그려지므로 별도 외곽 림 생략
        } else {
            glassPaint.color = 0xFFFFFFFF.toInt()
            val innerIns = 1.2f * density
            glassPaint.strokeWidth = 1.25f * density
            glassPaint.shader = LinearGradient(
                r.left, r.top, r.right, r.bottom,
                intArrayOf(
                    scaleAlpha(0x9CFFFFFF.toInt(), keyG),
                    scaleAlpha(0x50FFFFFF, keyG),
                    scaleAlpha(0x0AFFFFFF, keyG),
                    scaleAlpha(0x0AFFFFFF, keyG),
                    scaleAlpha(0x54FFFFFF, keyG),
                    scaleAlpha(0xA8FFFFFF.toInt(), keyG),
                ),
                floatArrayOf(0f, 0.28f, 0.46f, 0.54f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
            val ir = (radius - innerIns).coerceAtLeast(0f)
            canvas.drawRoundRect(r.left + innerIns, r.top + innerIns, r.right - innerIns, r.bottom - innerIns, ir, ir, glassPaint)

            val rimW = 1.15f * density
            glassPaint.strokeWidth = rimW
            glassPaint.shader = LinearGradient(
                r.left, r.top, r.right, r.bottom,
                intArrayOf(
                    scaleAlpha(0xFFFFFFFF.toInt(), keyG),
                    scaleAlpha(0xDCFFFFFF.toInt(), keyG),
                    scaleAlpha(0x34FFFFFF, keyG),
                    scaleAlpha(0x34FFFFFF, keyG),
                    scaleAlpha(0xDCFFFFFF.toInt(), keyG),
                    scaleAlpha(0xFFFFFFFF.toInt(), keyG),
                ),
                floatArrayOf(0f, 0.26f, 0.45f, 0.55f, 0.74f, 1f),
                Shader.TileMode.CLAMP,
            )
            val hh = rimW / 2f
            canvas.drawRoundRect(r.left + hh, r.top + hh, r.right - hh, r.bottom - hh, radius, radius, glassPaint)
        }
        glassPaint.shader = null
        glassPaint.style = Paint.Style.FILL
    }

    /** 비글래스/글래스0% 테마에서도 보조탭 배경이 키보드 본판의 그라데이션·색상과 자연스럽게 이어지도록 그린다 */
    fun drawPlainTabSurface(canvas: Canvas, tabW: Float, tabH: Float, platePath: Path) {
        val kbW = width.toFloat().coerceAtLeast(tabW)
        val kbH = height.toFloat().coerceAtLeast(tabH)
        val shader = theme.backgroundShader(kbW, kbH)
        if (shader == null) {
            bgPaint.shader = null
            bgPaint.color = withBgOpacity(if (theme.frost) theme.backgroundMid else theme.background)
            canvas.drawPath(platePath, bgPaint)
        } else {
            val shift = if (frostTabOffsetYpx > 0f) frostTabOffsetYpx else tabH + fnTabGapPx()
            val m = android.graphics.Matrix().apply { setTranslate(-frostTabLeftPx, shift) }
            shader.setLocalMatrix(m)
            bgPaint.color = 0xFFFFFFFF.toInt()
            bgPaint.shader = shader
            bgPaint.alpha = 255
            canvas.drawPath(platePath, bgPaint)
            bgPaint.alpha = (bgOpacity * 255f).toInt().coerceIn(0, 255)
            bgPaint.shader = null
        }
    }

    private fun drawBackground(canvas: Canvas) {
        if (bgGlassOn) { drawFrostPlate(canvas); return }
        val w = width.toFloat(); val h = height.toFloat()
        val rPlate = plateRadiusFor(w, h, theme.frost || keyGlassOn)
        glassPath.rewind()
        glassPath.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(rPlate, rPlate, rPlate, rPlate, 0f, 0f, 0f, 0f), Path.Direction.CW)
        val shader = theme.backgroundShader(w, h)
        if (shader == null) {
            bgPaint.shader = null
            bgPaint.color = withBgOpacity(theme.background)
            canvas.drawPath(glassPath, bgPaint)
        } else {
            bgPaint.color = 0xFFFFFFFF.toInt()
            bgPaint.shader = shader
            bgPaint.alpha = 255
            canvas.drawPath(glassPath, bgPaint)
            bgPaint.alpha = 255
            bgPaint.shader = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (bgGlassOn || keyGlassOn) ensureFrostBitmaps()
        drawBackground(canvas)
        val gap = 3 * density
        if (showHitZones) drawHitZones(canvas)
        for (cell in cells) {
            val key = cell.key
            val isSpecial = key.type !in NORMAL_STYLE_TYPES || key.type == KeyType.VOICE      // 음성인식 키도 쉬프트·이모티콘 키와 같은 배경색
            paint.color = when {
                isPressed(cell) -> theme.keyPressed
                key.type == KeyType.VOICE && voiceListening -> theme.accent
                key.type == KeyType.SHIFT && shiftState != ShiftState.OFF -> theme.keyPressed
                key.type == KeyType.ENTER -> theme.enterKey
                key.type == KeyType.SPACE || key.type == KeyType.BACKSPACE || key.type == KeyType.SHIFT || key.type == KeyType.EMOJI || key.type == KeyType.VOICE ||
                    (keyboardLayout != Layouts.numberPad && cell.rect.top >= spaceRowTopPx() - 1f && (key.label == "," || key.label == ".")) ->
                    if (theme.spaceKey != 0) theme.spaceKey else theme.specialKey
                isDigitKey(key) -> if (theme.spaceKey != 0) theme.spaceKey else theme.numberKey
                isSpecial || cell.duplicate -> theme.specialKey
                else -> theme.key
            }
            paint.color = withKeyOpacity(paint.color)
            val r = cell.inner
            val useFrostKey = keyGlassOn
            val radius = keyRadiusFor(r, useFrostKey)
            if (useFrostKey) {
                drawFrostKey(canvas, r, radius, paint.color, isPressed(cell))
            } else if (theme.keyShadow) {
                val keep = paint.color
                paint.color = 0x26000000
                canvas.drawRoundRect(r.left, r.top + 1.5f * density, r.right, r.bottom + 1.5f * density, radius, radius, paint)
                paint.color = keep
            }
            if (!useFrostKey) canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, paint)
            if (theme.keyBorder != 0 && !useFrostKey) {
                borderPaint.strokeWidth = density
                borderPaint.color = theme.keyBorder
                val h = density / 2f
                canvas.drawRoundRect(r.left + h, r.top + h, r.right - h, r.bottom - h, radius, radius, borderPaint)
            }

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
            // 글자 크기는 키 높이를 따르되, 줄이 높아진 자판(숫자 왼쪽·모아키)에서 글자가 너무 커지지 않게 기본 키 높이까지만
            // 글자 크기는 키의 가로·세로 중 작은 쪽을 따른다 (높이는 기본 키 높이까지만, 가로는 1.35배로 환산). 기본 크기는 1dp 키웠다
            paint.textSize = (labelRefPx(cell) * size * theme.textScale + LABEL_BONUS_DP * density) * fontScale
            if (key.type in FN_TYPES || key.type == KeyType.VOICE || key.type == KeyType.EMOJI) {
                paint.textSize *= 0.70f
            }
            if (key.type == KeyType.SPACE || cell.duplicate) paint.alpha = 140
            if (useFrostKey && effGlass == 3) {
                paint.setShadowLayer(1.6f * density, 0f, 0.9f * density, 0x48000000)
            }
            if (key.type == KeyType.EMOJI) {
                drawEmojiIcon(canvas, r, paint.textSize, paint.color)
            } else if (key.type == KeyType.VOICE) {
                drawMicIcon(canvas, r, paint.textSize, paint.color)
            } else {
                drawCentered(canvas, label, r)
            }
            if (useFrostKey && effGlass == 3) paint.clearShadowLayer()
            paint.alpha = 255

            // 꾹 누르기 후보가 있는 글자 키: 오른쪽 위에 작은 힌트 (키 둥글기가 커질수록 45° 대각선 안쪽으로 이동해 둥근 모서리 밖으로 벗어나지 않게 함)
            if (key.type == KeyType.CHAR && key.hints.isNotEmpty() && key.hints[0].length <= 2) {
                paint.color = theme.text
                paint.alpha = 110
                paint.textSize = labelRefPx(cell) * 0.24f * fontScale
                paint.textAlign = Paint.Align.RIGHT
                val effRad = radius.coerceAtMost(min(r.width(), r.height()) / 2f)
                val diagInset = ((effRad - 6f * density).coerceAtLeast(0f) * 0.28f).coerceAtMost(min(r.width(), r.height()) * 0.14f)
                canvas.drawText(
                    key.hints[0],
                    r.right - 5f * density - diagInset,
                    r.top + paint.textSize + 3f * density + diagInset,
                    paint,
                )
                paint.textAlign = Paint.Align.CENTER
                paint.alpha = 255
            }
        }
        for (i in 0 until pointers.size()) pointers.valueAt(i).popup?.let { drawPopup(canvas, it) }
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            drawMoaGuide(canvas, p)
            drawSwipeDouble(canvas, p)
        }
        message?.let { drawMessage(canvas, it) }
        if (undoMark) drawUndoMark(canvas)
    }

    /**
     * 모아키: 자음을 누르면 바로 모음 안내가 키 둘레에 뜬다 (위 ㅗ, 아래 ㅜ, 오른쪽 ㅏ, 왼쪽 ㅓ, 대각선 ㅡ·ㅣ).
     * 미는 방향의 칸은 강조색으로 바뀌고 완성될 글자(예: "가")를 보여준다. 기호 말풍선이 열리면 숨긴다.
     */
    private fun drawMoaGuide(canvas: Canvas, p: Pointer) {
        val c = p.cell ?: return
        if (keyboardLayout?.moaki != true || !isMoaConsonant(c.key) || p.popup != null || p.ignore || p.cursorDrag) return
        val cons = c.key.output[0]
        val d = keyHeightPx * 0.78f                       // 안내 칸 크기
        val rad = max(c.rect.width(), keyHeightPx) * 1.05f // 키 중심에서 안내 칸까지 거리
        val spots = listOf(          // (각도°, 모음): 3시 ㅏ, 9시 ㅓ, 12시 ㅗ, 6시 ㅜ, 11시·1시 ㅣ, 5시·7시 ㅡ
            0f to 'ㅏ', 180f to 'ㅓ', -90f to 'ㅗ', 90f to 'ㅜ',
            -135f to 'ㅣ', -45f to 'ㅣ', 45f to 'ㅡ', 135f to 'ㅡ',
        )
        val cx = c.rect.centerX(); val cy = c.rect.centerY()
        for ((deg, v) in spots) {
            val rad2 = Math.toRadians(deg.toDouble())
            val x = (cx + rad * kotlin.math.cos(rad2)).toFloat().coerceIn(d / 2f, width - d / 2f)
            val y = (cy + rad * kotlin.math.sin(rad2)).toFloat().coerceAtLeast(d / 2f)
            val box = RectF(x - d / 2f, y - d / 2f, x + d / 2f, y + d / 2f)
            // 미는 각도가 이 칸의 45° 안이면 강조 (같은 ㅣ·ㅡ가 두 칸이라 모음이 아니라 각도로 가린다)
            var diff = abs(deg - p.moaDeg) % 360f
            if (diff > 180f) diff = 360f - diff
            val chosen = p.moaVowel != null && diff <= 22.5f
            paint.style = Paint.Style.FILL
            paint.color = if (chosen) theme.accent else theme.solid(theme.key)
            canvas.drawRoundRect(box, d * 0.3f, d * 0.3f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = theme.solid(theme.keyPressed)
            canvas.drawRoundRect(box, d * 0.3f, d * 0.3f, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (chosen) theme.onAccent else theme.text
            paint.textSize = d * (if (chosen) 0.62f else 0.5f)
            drawCentered(canvas, v.toString(), box)
        }
        // 지금 긋고 있는 모음으로 완성될 글자(예: ㄱ + ㅘ → "과")를 눌린 키 자리에 보여준다
        p.moaVowel?.let { mv ->
            val syl = com.mbt.keyboard.hangul.HangulComposer().let { it.input(cons); it.input(mv); it.composing }
            paint.style = Paint.Style.FILL
            paint.color = theme.accent
            canvas.drawRoundRect(c.inner, 7 * density, 7 * density, paint)
            paint.color = theme.onAccent
            paint.textSize = c.inner.height() * 0.62f
            drawCentered(canvas, syl, c.inner)
        }
    }


    /** 단모음 개선판: 밀어서 바뀔 글자(예: ㄱ → ㄲ)를 눌린 키 자리에 강조색으로 보여준다 */
    private fun drawSwipeDouble(canvas: Canvas, p: Pointer) {
        if (!p.swipeDoubleDone) return
        val c = p.cell ?: return
        val to = swipeDoubleTarget(c.key) ?: return
        paint.style = Paint.Style.FILL
        paint.color = theme.accent
        canvas.drawRoundRect(c.inner, 7 * density, 7 * density, paint)
        paint.color = theme.onAccent
        paint.textSize = c.inner.height() * 0.5f
        drawCentered(canvas, to.toString(), c.inner)
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

    /** 속이 비어 있는 선형 단색 스마일 아이콘 (테마 글자색·3D 그림자 연동, 컬러 이모지 폴백 없음) */
    private fun drawEmojiIcon(canvas: Canvas, r: RectF, sizePx: Float, color: Int) {
        val rad = (sizePx * 0.52f).coerceAtLeast(4f * density)
        val cx = r.centerX()
        val cy = r.centerY()
        val keepStyle = paint.style
        val keepStroke = paint.strokeWidth
        val keepCap = paint.strokeCap
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (rad * 0.17f).coerceAtLeast(1.25f * density)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawCircle(cx, cy, rad, paint)
        // 눈 (선형 굵기에 비례한 작은 점)
        paint.style = Paint.Style.FILL
        val eyeR = (rad * 0.13f).coerceAtLeast(1.1f * density)
        canvas.drawCircle(cx - rad * 0.34f, cy - rad * 0.18f, eyeR, paint)
        canvas.drawCircle(cx + rad * 0.34f, cy - rad * 0.18f, eyeR, paint)
        // 미소 입
        paint.style = Paint.Style.STROKE
        val mouthR = rad * 0.56f
        canvas.drawArc(cx - mouthR, cy - mouthR * 0.72f, cx + mouthR, cy + mouthR * 0.88f, 24f, 132f, false, paint)
        paint.style = keepStyle
        paint.strokeWidth = keepStroke
        paint.strokeCap = keepCap
    }

    /** 이모티콘 아이콘과 동일한 단색 선형 스타일의 마이크 아이콘 */
    private fun drawMicIcon(canvas: Canvas, r: RectF, sizePx: Float, color: Int) {
        val rad = (sizePx * 0.52f).coerceAtLeast(4f * density)
        val cx = r.centerX()
        val cy = r.centerY()
        val keepStyle = paint.style
        val keepStroke = paint.strokeWidth
        val keepCap = paint.strokeCap
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (rad * 0.17f).coerceAtLeast(1.25f * density)
        paint.strokeCap = Paint.Cap.ROUND
        // 마이크 캡슐 본체 (drawRoundRect 대신 호·직선으로 그려 테두리 카운트 테스트와 충돌하지 않게 함)
        val capW = rad * 0.36f
        val capTop = cy - rad * 0.92f
        val capBottom = cy + rad * 0.14f
        canvas.drawArc(cx - capW, capTop, cx + capW, capTop + capW * 2f, 180f, 180f, false, paint)
        canvas.drawArc(cx - capW, capBottom - capW * 2f, cx + capW, capBottom, 0f, 180f, false, paint)
        canvas.drawLine(cx - capW, capTop + capW, cx - capW, capBottom - capW, paint)
        canvas.drawLine(cx + capW, capTop + capW, cx + capW, capBottom - capW, paint)
        // U자 홀더 아크
        val arcR = rad * 0.68f
        val arcCy = cy - rad * 0.08f
        canvas.drawArc(cx - arcR, arcCy - arcR, cx + arcR, arcCy + arcR, 12f, 156f, false, paint)
        // 받침대 줄기 + 바닥선
        val stemTop = arcCy + arcR
        val stemBottom = cy + rad * 0.94f
        canvas.drawLine(cx, stemTop, cx, stemBottom, paint)
        canvas.drawLine(cx - rad * 0.38f, stemBottom, cx + rad * 0.38f, stemBottom, paint)
        paint.style = keepStyle
        paint.strokeWidth = keepStroke
        paint.strokeCap = keepCap
    }

    /** 줄 인식 경계(점선). 키 위에 겹쳐 보이도록 키를 그린 뒤가 아니라 배경 바로 위에 그려 가볍게 표시 */
    private fun drawHitZones(canvas: Canvas) {
        borderPaint.color = theme.accent
        borderPaint.strokeWidth = 1.5f * density
        borderPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
        for (r in 1 until hitTops.size) {
            canvas.drawLine(padL, hitTops[r], width - padR, hitTops[r], borderPaint)
        }
        borderPaint.pathEffect = null
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
        paint.color = theme.solid(theme.key)            // 말풍선은 뒤가 비치지 않게 불투명하게
        canvas.drawRoundRect(bounds, radius + gap, radius + gap, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = theme.solid(theme.keyPressed)
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
            if (abs(x - p.downX) > SPACE_SWIPE_DP * density) {
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
        // 모아키: 자음·모음 키 위의 밀기는 커서 이동이 아니라 모음 입력
        if (keyboardLayout?.moaki == true) {
            val c = p.cell
            if (c != null && isMoaConsonant(c.key)) {
                trackMoaStrokes(p, c, x, y)
                return
            }
        }
        // 단모음 개선판: 쌍자음·ㅑㅕㅛㅠ 가 되는 키는 위·아래로 밀면 바뀐 글자가 입력된다 (커서 이동보다 먼저)
        // 위·아래로만 민다 (좌우 밀기는 커서 이동용으로 남겨 둔다)
        if (swipeDoubleTarget(p.cell?.key) != null) {
            if (p.swipeDoubleDone) return
            val dx = x - p.downX
            val dy = y - p.downY
            if (abs(dy) >= abs(dx)) {
                val dist = kotlin.math.hypot(dx, dy)
                if (dist >= max(p.cell!!.rect.width() * 0.30f, 12 * density)) {
                    p.swipeDoubleDone = true
                    p.cancelTimers()                 // 꾹 누르기(기호 말풍선)는 취소
                    haptics?.tick()
                    invalidate()
                }
                return
            }
            // 가로 우세: 아래 커서 이동으로 넘어간다
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

    /** 단모음 개선판에서 밀면 바뀔 글자 (해당 없으면 null). 쉬프트가 켜져 있으면 이미 쌍자음이라 해당 없음 */
    private fun swipeDoubleTarget(key: Key?): Char? {
        if (!swipeDouble || keyboardLayout?.doubleTap != true || key == null) return null
        if (key.type != KeyType.CHAR || shiftState != ShiftState.OFF || key.output.length != 1) return null
        return com.mbt.keyboard.hangul.Jamo.DOUBLE[key.output[0]]
    }

    /** 글자 자판의 숫자 키 (숫자·전화번호 키패드의 큰 숫자 키는 제외) */
    /** 키 글자 크기의 기준 길이: 키 높이(기본 키 높이까지)와 가로×[LABEL_WIDTH_FACTOR] 중 작은 쪽 */
    private fun labelRefPx(cell: Cell) = min(min(cell.rect.height(), keyHeightPx), cell.rect.width() * LABEL_WIDTH_FACTOR)

    private fun isDigitKey(key: Key) =
        key.type == KeyType.CHAR && key.label.length == 1 && key.label[0].isDigit() &&
            keyboardLayout?.id.let { it != Layouts.NUM && it != Layouts.PHONE }

    private fun isMoaConsonant(key: Key) =
        key.type == KeyType.CHAR && key.output.length == 1 && com.mbt.keyboard.hangul.Jamo.isConsonant(key.output[0])

    /**
     * 모아키 자음 위의 긋기를 "획"으로 나눈다. 같은 방향으로 가는 동안은 한 획이고, 가장 먼 점에서
     * 60° 넘게 꺾여 키 폭의 30% 이상 움직이면 그 획이 끝나고 새 획이 시작된다 (돌아오기도 한 획).
     * 획 순서를 [MoakiGesture]로 해석한다: → ㅏ, ↑→ ㅘ, ↑↓ ㅚ, →←→ ㅑ …
     */
    private fun trackMoaStrokes(p: Pointer, c: Cell, x: Float, y: Float) {
        val t = max(c.rect.width() * 0.30f, 12 * density)       // 첫 획의 최소 길이
        val tn = max(c.rect.width() * 0.20f, 9 * density)       // 이어지는 획(겹모음·왕복)은 조금 짧아도 인정
        if (!p.moaInit) {
            p.moaInit = true
            p.moaAx = p.downX; p.moaAy = p.downY; p.moaFx = p.downX; p.moaFy = p.downY
        }
        val fa = kotlin.math.hypot(p.moaFx - p.moaAx, p.moaFy - p.moaAy)
        if (fa < t * 0.5f) {
            p.moaFx = x; p.moaFy = y                          // 아직 방향이 정해지기 전: 따라간다
        } else {
            val ux = (p.moaFx - p.moaAx) / fa
            val uy = (p.moaFy - p.moaAy) / fa
            val vx = x - p.moaFx; val vy = y - p.moaFy
            val vl = kotlin.math.hypot(vx, vy)
            val cos = if (vl > 0f) (vx * ux + vy * uy) / vl else 1f
            if (vl < 3 * density || cos >= 0.8f) {
                // 같은 방향(약 37° 안)으로 이어지는 중: 가장 먼 점을 따라간다. 단, 뒤로 물러난 경우는 건드리지 않는다
                val proj = (x - p.moaAx) * ux + (y - p.moaAy) * uy
                if (proj > fa) { p.moaFx = x; p.moaFy = y }
            } else {
                val minFa = if (p.moaStrokes.isEmpty()) t else tn
                // 방향이 37° 넘게 꺾였다(위로 가다 대각선으로 꺾는 정도도 포함). 꺾은 뒤 길이가 충분하면 앞 획이 끝난다.
                // 끝에서 살짝 튀는 정도는 무시하려고 앞 획 길이의 35% 이상을 요구한다
                if (fa >= minFa && vl >= max(tn, fa * 0.35f)) {
                    p.moaStrokes.append(MoakiGesture.token(degOf(p.moaFx - p.moaAx, p.moaFy - p.moaAy)))
                    p.moaAx = p.moaFx; p.moaAy = p.moaFy; p.moaFx = x; p.moaFy = y
                }
            }
        }
        val s = StringBuilder(p.moaStrokes)
        val cur = kotlin.math.hypot(p.moaFx - p.moaAx, p.moaFy - p.moaAy)
        if (cur >= (if (p.moaStrokes.isEmpty()) t else tn)) {
            val d = degOf(p.moaFx - p.moaAx, p.moaFy - p.moaAy)
            if (p.moaStrokes.isEmpty()) p.moaDeg = d
            s.append(MoakiGesture.token(d))
        }
        val v = MoakiGesture.decode(s.toString())
        if (v != p.moaVowel) {
            p.moaVowel = v
            if (v != null) { p.cancelTimers(); haptics?.tick() }   // 긋기 시작하면 꾹 누르기(기호 말풍선)는 취소
            invalidate()
        }
    }

    private fun degOf(dx: Float, dy: Float) =
        Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()

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
                cell != null && p.swipeDoubleDone && swipeDoubleTarget(cell.key) != null ->
                    listener?.onText(swipeDoubleTarget(cell.key)!!.toString())
                cell != null && keyboardLayout?.moaki == true && isMoaConsonant(cell.key) && p.moaVowel != null ->
                    listener?.onMoaki(cell.key, p.moaVowel!!)
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
                // 화면 오른쪽 절반의 키는 기본(대표) 기호를 맨 오른쪽에 놓는다: 손가락을 안쪽(왼쪽)으로 밀어 다른 기호를 고른다.
                // 왼쪽 절반은 지금처럼 대표 기호가 맨 왼쪽이고 오른쪽으로 민다
                val rightSide = cell.rect.centerX() > width / 2f
                val left = if (rightSide) (cell.rect.right - total).coerceAtMost(width - padR - total).coerceAtLeast(padL)
                else cell.rect.left.coerceAtMost(width - padR - total).coerceAtLeast(padL)
                val top = (cell.rect.top - keyH).coerceAtLeast(0f)
                val rects = List(n) { RectF(left + it * cellW, top, left + (it + 1) * cellW, top + keyH) }
                if (rightSide) Popup(key.hints.reversed(), rects, selected = n - 1) else Popup(key.hints, rects, selected = 0)
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
        if (sideCells.isNotEmpty() && x < padL - halfKeyGapPx + stripPx && y < sideBottomPx) {
            // 왼쪽 띠: 가로 2열·세로 N칸 중 터치가 속한 칸 (줄 경계와 상관없이 띠 안에서 직접 찾는다)
            val colW = stripPx / 2f
            val col = ((x - (padL - halfKeyGapPx)) / colW).toInt().coerceIn(0, 1)
            val n = sideCells.size / 2
            val row = ((y - sideTopPx + sideHalfGapPx) / sidePitchPx).toInt().coerceIn(0, n - 1)
            return sideCells[col * n + row]
        }
        var row = hitTops.indexOfLast { it <= y }
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
        /** 왼쪽 세로 띠(숫자 2열)가 줄 폭에서 차지하는 비율 */
        const val SIDE_STRIP_FRACTION = 0.19f
        val FN_TYPES = setOf(KeyType.SELECT_ALL, KeyType.COPY, KeyType.PASTE)
        const val DEFAULT_NUMBER_ROW_SCALE = 0.8f
        const val LABEL_WIDTH_FACTOR = 1.35f
        const val FROST_BLUR_DIV = 10
        const val FROST_KEY_RADIUS_SCALE = 1.15f
        const val FROST_FADE_DP = 6f
        const val FN_TAB_GAP_DP = 2f

        /** 유리판 위 빛띠: (투명 → 옅은 넓은 띠 → 좁고 밝은 띠 → …) 비스듬한 방향 */
        private val GLASS_BAND_COLORS = intArrayOf(
            0x00FFFFFF, 0x52FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x38FFFFFF, 0x8CFFFFFF.toInt(), 0x38FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF,
        )
        private val GLASS_BAND_STOPS = floatArrayOf(0.00f, 0.10f, 0.20f, 0.36f, 0.43f, 0.455f, 0.48f, 0.56f, 0.72f, 0.80f, 0.90f)

        /** 번짐 배경 한 덩어리: (가로 위치, 세로 위치, 반지름 비율, 색) */
        private data class Blob(val fx: Float, val fy: Float, val r: Float, val color: Int)

        /** 홀로그램 파스텔: 라벤더·하늘·연노랑·핑크, 오른쪽 위와 왼쪽 아래에 하얀 빛 */
        private val HOLO_BLOBS = listOf(
            Blob(0.04f, 0.10f, 0.45f, 0xE6B9AFE8.toInt()), Blob(0.34f, 0.28f, 0.42f, 0xE6B6D6EA.toInt()),
            Blob(0.50f, 0.52f, 0.34f, 0xCCD6EBD2.toInt()), Blob(0.66f, 0.50f, 0.30f, 0xE6F4F0C4.toInt()),
            Blob(0.26f, 0.95f, 0.42f, 0xE6FCDCE6.toInt()), Blob(0.86f, 0.96f, 0.42f, 0xE6B5ADE3.toInt()),
            Blob(0.94f, 0.00f, 0.34f, 0xF2FFFFFF.toInt()), Blob(0.00f, 0.78f, 0.28f, 0xE6FFFFFF.toInt()),
        )

        /** 웜 그레이지: 따뜻한 회갈색에 밝은 모래빛과 푸른 회색 */
        private val TAUPE_BLOBS = listOf(
            Blob(0.20f, 0.20f, 0.55f, 0xCCC9BFAE.toInt()), Blob(0.80f, 0.10f, 0.50f, 0xCCD8D0C4.toInt()),
            Blob(0.70f, 0.95f, 0.55f, 0xCC8C91A3.toInt()), Blob(0.05f, 0.95f, 0.35f, 0x99A89A86.toInt()),
        )

        private val TAUPE_OPTICAL_BLOBS = listOf(Blob(0.18f, 0.28f, 0.42f, 0xF2FFF6E6.toInt()), Blob(0.42f, 0.22f, 0.36f, 0xE6DFC39D.toInt()), Blob(0.32f, 0.72f, 0.40f, 0xE6C8A378.toInt()), Blob(0.76f, 0.32f, 0.42f, 0xE6EEF3FF.toInt()), Blob(0.80f, 0.74f, 0.46f, 0xE6859AD4.toInt()), Blob(0.12f, 0.88f, 0.32f, 0xCC7B8399.toInt()))

        /** 선셋 글로우: 노을빛 살구·핑크·보라 */
        private val SUNSET_BLOBS = listOf(
            Blob(0.15f, 0.12f, 0.45f, 0xE6FFC896.toInt()), Blob(0.88f, 0.20f, 0.42f, 0xD9FF96AA.toInt()),
            Blob(0.70f, 0.95f, 0.50f, 0xE6B79CFF.toInt()),
        )

        /** 핑크 클라우드: 솜사탕 핑크·라벤더에 하얀 구름빛 */
        private val PINK_BLOBS = listOf(
            Blob(0.12f, 0.10f, 0.38f, 0xE6FFFFFF.toInt()), Blob(0.90f, 0.85f, 0.50f, 0xF2E1D3FF.toInt()),
            Blob(0.50f, 0.50f, 0.30f, 0x99FFB6D8.toInt()),
        )

        /** 오로라: 밤하늘에 청록·보라·파랑 빛 */
        private val AURORA_BLOBS = listOf(
            Blob(0.20f, 0.15f, 0.42f, 0x996EF3D6.toInt()), Blob(0.85f, 0.25f, 0.45f, 0x99966EFF.toInt()),
            Blob(0.60f, 0.95f, 0.50f, 0x8C3C78C8.toInt()),
        )

        /** 민트 소다: 시원한 민트·하늘빛 */
        private val MINT_BLOBS = listOf(
            Blob(0.15f, 0.12f, 0.38f, 0xD9FFFFFF.toInt()), Blob(0.85f, 0.80f, 0.50f, 0xE6A0D7FF.toInt()),
            Blob(0.40f, 0.50f, 0.30f, 0x99A6ECDC.toInt()),
        )

        /** 딥 네이비: 어두운 남색에 보랏빛·청록빛 */
        private val NAVY_BLOBS = listOf(
            Blob(0.20f, 0.15f, 0.55f, 0xCC5A5FA8.toInt()), Blob(0.85f, 0.80f, 0.55f, 0xCC3B7FA0.toInt()),
            Blob(0.60f, 0.20f, 0.30f, 0x663A4A9A.toInt()),
        )
        const val LABEL_BONUS_DP = 1f
        const val DEFAULT_NUMBER_GAP_DP = 6f
        const val UNDO_MARK_DP = 52f
        const val UNDO_MARK_MS = 900L
        const val DUPLICATE_WEIGHT = 0.85f
        /** 옛 고정 간격(키 높이에서 빼던 값) */
        const val LEGACY_GAP_DP = 6f
        const val REPEAT_DELAY_MS = 400L
        const val SWIPE_THRESHOLD_DP = 40f
        /** 스페이스바를 밀어 한/영을 바꾸는 거리: 예전 40dp보다 30% 짧게 */
        const val SPACE_SWIPE_DP = 28f
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
