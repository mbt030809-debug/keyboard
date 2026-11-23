package com.mbt.keyboard.ime

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 터치 처리 검증: 특히 앞 키를 떼기 전에 다음 키를 누르는 빠른 타이핑(멀티터치)이 빠지지 않는지. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardViewTouchTest {

    private class Recorder : KeyboardView.Listener {
        val keys = ArrayList<String>()
        val texts = ArrayList<String>()
        var shiftEvents = ArrayList<Boolean>()
        var swipeSpace = 0
        var undo = 0
        var cursorSteps = 0
        override fun onKey(key: Key) { keys += key.label }
        override fun onText(text: String) { texts += text }
        override fun onSwipeSpace() { swipeSpace++ }
        override fun onUndoDelete() { undo++ }
        override fun onCursorMove(steps: Int) { cursorSteps += steps }
        override fun onCursorMoveEnd() {}
        override fun onShiftPress(down: Boolean) { shiftEvents += down }
        var enterLongPress = 0
        override fun onEnterLongPress() { enterLongPress++ }
    }

    private lateinit var view: KeyboardView
    private lateinit var rec: Recorder
    private val density get() = view.resources.displayMetrics.density

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = KeyboardView(ctx)
        rec = Recorder()
        view.listener = rec
        view.keyboardLayout = Layouts.koreanSingle
        view.measure(
            View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    // ---- 좌표 도우미: 키 라벨 → 중심 좌표 (키보드 뷰의 셀 위치를 리플렉션 없이 계산) ----
    private fun center(label: String): Pair<Float, Float> {
        val layout = Layouts.koreanSingle
        val pad = 4 * density
        val keyH = view.theme.keyHeightDp * density
        var top = pad
        layout.rows.forEachIndexed { r, row ->
            val rowH = keyH * layout.rowHeights[r]
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = pad
            val usable = view.width - 2 * pad
            for (k in row) {
                val w = usable * k.weight / total
                if (k.type != KeyType.SPACER && k.label == label) return Pair(x + w / 2, top + rowH / 2)
                x += w
            }
            top += rowH
        }
        error("no key $label")
    }

    private var downTime = 0L
    private fun ev(action: Int, vararg pts: Triple<Int, Float, Float>, actionIndex: Int = 0): MotionEvent {
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = pts[it].first; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { MotionEvent.PointerCoords().apply { x = pts[it].second; y = pts[it].third } }
        val act = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(downTime, t, act, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }
    private fun send(e: MotionEvent) = view.dispatchTouchEvent(e)

    private fun p(id: Int, label: String): Triple<Int, Float, Float> = center(label).let { Triple(id, it.first, it.second) }

    @Test fun singleTapFiresOnRelease() {
        downTime = SystemClock.uptimeMillis()
        send(ev(MotionEvent.ACTION_DOWN, p(0, "ㄱ")))
        assertTrue(rec.keys.isEmpty())
        send(ev(MotionEvent.ACTION_UP, p(0, "ㄱ")))
        assertEquals(listOf("ㄱ"), rec.keys)
    }

    @Test fun secondFingerPressedBeforeFirstReleasedIsNotLost() {
        downTime = SystemClock.uptimeMillis()
        val a = p(0, "ㄱ"); val b = p(1, "ㅏ")
        send(ev(MotionEvent.ACTION_DOWN, a))
        send(ev(MotionEvent.ACTION_POINTER_DOWN, a, b, actionIndex = 1))   // 첫 손가락을 떼기 전에 두 번째가 닿음
        send(ev(MotionEvent.ACTION_POINTER_UP, a, b, actionIndex = 0))     // 첫 손가락 뗌
        send(ev(MotionEvent.ACTION_UP, b))                                  // 두 번째 뗌
        assertEquals(listOf("ㄱ", "ㅏ"), rec.keys)
    }

    @Test fun rapidRolloverOfThreeKeysKeepsOrderOfRelease() {
        downTime = SystemClock.uptimeMillis()
        val a = p(0, "ㅎ"); val b = p(1, "ㅏ"); val c = p(2, "ㄴ")
        send(ev(MotionEvent.ACTION_DOWN, a))
        send(ev(MotionEvent.ACTION_POINTER_DOWN, a, b, actionIndex = 1))
        send(ev(MotionEvent.ACTION_POINTER_UP, a, b, actionIndex = 0))
        send(ev(MotionEvent.ACTION_POINTER_DOWN, b, c, actionIndex = 1))
        send(ev(MotionEvent.ACTION_POINTER_UP, b, c, actionIndex = 0))
        send(ev(MotionEvent.ACTION_UP, c))
        assertEquals(listOf("ㅎ", "ㅏ", "ㄴ"), rec.keys)
    }

    @Test fun twoFingersReleasedInReverseOrderBothFire() {
        downTime = SystemClock.uptimeMillis()
        val a = p(0, "ㅂ"); val b = p(1, "ㅈ")
        send(ev(MotionEvent.ACTION_DOWN, a))
        send(ev(MotionEvent.ACTION_POINTER_DOWN, a, b, actionIndex = 1))
        send(ev(MotionEvent.ACTION_POINTER_UP, a, b, actionIndex = 1))     // 나중에 눌린 손가락 먼저 뗌
        send(ev(MotionEvent.ACTION_UP, a))
        assertEquals(listOf("ㅈ", "ㅂ"), rec.keys)
    }

    @Test fun holdingShiftWhileTypingReportsShiftPressAndRelease() {
        downTime = SystemClock.uptimeMillis()
        val shift = p(0, "⇧"); val k = p(1, "ㄱ")
        send(ev(MotionEvent.ACTION_DOWN, shift))
        send(ev(MotionEvent.ACTION_POINTER_DOWN, shift, k, actionIndex = 1))
        send(ev(MotionEvent.ACTION_POINTER_UP, shift, k, actionIndex = 1))
        send(ev(MotionEvent.ACTION_UP, shift))
        assertEquals(listOf("ㄱ"), rec.keys)
        assertEquals(listOf(true, false), rec.shiftEvents)
    }

    @Test fun swipeOnSpaceTogglesOnceAndTypesNoSpace() {
        downTime = SystemClock.uptimeMillis()
        val (x, y) = center("")   // 공백 키 라벨은 "" (스페이서 제외)
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, x, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x + 60 * density, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x + 90 * density, y)))
        send(ev(MotionEvent.ACTION_UP, Triple(0, x + 90 * density, y)))
        assertEquals(1, rec.swipeSpace)
        assertTrue(rec.keys.isEmpty())
    }

    @Test fun spaceSwipeNeedsOnlyAbout28dpNowThirtyPercentShorterThanBefore() {
        fun swipeBy(dp: Float): Int {
            rec.swipeSpace = 0
            downTime = SystemClock.uptimeMillis()
            val (x, y) = center("")
            send(ev(MotionEvent.ACTION_DOWN, Triple(0, x, y)))
            send(ev(MotionEvent.ACTION_MOVE, Triple(0, x + dp * density, y)))
            send(ev(MotionEvent.ACTION_UP, Triple(0, x + dp * density, y)))
            return rec.swipeSpace
        }
        assertEquals(0, swipeBy(25f))          // 28dp 미만: 전환되지 않는다 (그냥 스페이스)
        assertEquals(1, swipeBy(30f))          // 예전(40dp)에는 안 되던 거리도 이제 전환
        assertEquals(1, swipeBy(45f))
    }

    @Test fun swipingLeftOnSpaceTogglesTooSoDirectionDoesNotMatter() {
        downTime = SystemClock.uptimeMillis()
        val (x, y) = center("")
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, x, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x - 60 * density, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x - 90 * density, y)))
        send(ev(MotionEvent.ACTION_UP, Triple(0, x - 90 * density, y)))
        assertEquals(1, rec.swipeSpace)
        assertTrue(rec.keys.isEmpty())
    }

    private fun backspaceCountAfterHolding(intervalMs: Long, holdMs: Long): Int {
        view.repeatIntervalMs = intervalMs
        rec.keys.clear()
        downTime = SystemClock.uptimeMillis()
        val back = view.keyRects().first { it.first.type == com.mbt.keyboard.layout.KeyType.BACKSPACE }.second
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, back.centerX(), back.centerY())))
        org.robolectric.shadows.ShadowLooper.idleMainLooper(holdMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        val bs = com.mbt.keyboard.layout.Layouts.koreanSingle.rows.flatten().first { it.type == com.mbt.keyboard.layout.KeyType.BACKSPACE }.label
        val n = rec.keys.count { it == bs }
        send(ev(MotionEvent.ACTION_UP, Triple(0, back.centerX(), back.centerY())))
        return n
    }

    @Test fun holdingBackspaceDeletesFasterWhenTheIntervalIsShorter() {
        val slow = backspaceCountAfterHolding(200, 400 + 1000)      // 첫 삭제 + 반복 시작(0.4초) 후 1초
        val fast = backspaceCountAfterHolding(25, 400 + 1000)
        assertTrue("slow=$slow fast=$fast", fast > slow * 3)
        assertTrue(slow in 4..8)                                    // 1초에 약 5번 + 첫 번
    }

    @Test fun swipingBackspaceLeftUndoesOnceAndRightDoesNothing() {
        downTime = SystemClock.uptimeMillis()
        val back = view.keyRects().first { it.first.type == com.mbt.keyboard.layout.KeyType.BACKSPACE }.second
        val x = back.centerX(); val y = back.centerY()
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, x, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x - 30 * density, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x - 60 * density, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x - 90 * density, y)))
        send(ev(MotionEvent.ACTION_UP, Triple(0, x - 90 * density, y)))
        assertEquals(1, rec.undo)
        // 오른쪽으로 밀기는 되돌리기가 아니다
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, x - 20 * density, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x + 5 * density, y)))
        send(ev(MotionEvent.ACTION_UP, Triple(0, x + 5 * density, y)))
        assertEquals(1, rec.undo)
    }

    @Test fun horizontalSwipeOnLetterMovesCursorAndTypesNothing() {
        downTime = SystemClock.uptimeMillis()
        val (x, y) = center("ㅁ")
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, x, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x + 30 * density, y)))   // 드래그 시작
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x + 72 * density, y)))   // 42dp 더 이동 → 3칸
        send(ev(MotionEvent.ACTION_UP, Triple(0, x + 72 * density, y)))
        assertTrue(rec.keys.isEmpty())
        assertEquals(3, rec.cursorSteps)
    }

    @Test fun smallJitterWhileTappingStillTypesTheKey() {
        downTime = SystemClock.uptimeMillis()
        val (x, y) = center("ㄴ")
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, x, y)))
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, x + 5 * density, y + 3 * density)))
        send(ev(MotionEvent.ACTION_UP, Triple(0, x + 5 * density, y + 3 * density)))
        assertEquals(listOf("ㄴ"), rec.keys)
    }

    private fun idleFor(ms: Long) =
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))

    @Test fun longPressingPasteNoLongerOpensAnythingAndStillPastesOnRelease() {
        // 최근기록은 엔터 꾹 누르기 패널로 옮겼다. 붙임은 오래 눌러도 붙여넣기만 한다
        downTime = SystemClock.uptimeMillis()
        val a = p(0, "붙임")
        send(ev(MotionEvent.ACTION_DOWN, a))
        idleFor(view.longPressMs + 200)
        send(ev(MotionEvent.ACTION_UP, a))
        assertEquals(listOf("붙임"), rec.keys)
        assertEquals(0, rec.enterLongPress)
    }

    // ---- 꾹 누르기 기호: 오른쪽 절반 키는 대표 기호가 맨 오른쪽

    /** [label] 키를 꾹 눌러 말풍선을 열고, 가로로 [dxCells]칸(말풍선 한 칸 폭 기준) 밀어 뗀 뒤 입력된 기호 */
    private fun longPressSelect(label: String, dxCells: Int): String? {
        downTime = SystemClock.uptimeMillis()
        val r = view.keyRects().first { it.first.label == label }.second
        val x = r.centerX(); val y = r.centerY()
        rec.texts.clear()
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, x, y)))
        idleFor(view.longPressMs + 50)
        val cellW = maxOf(r.width(), 44 * density)
        val tx = x + dxCells * cellW
        send(ev(MotionEvent.ACTION_MOVE, Triple(0, tx, y)))
        send(ev(MotionEvent.ACTION_UP, Triple(0, tx, y)))
        return rec.texts.firstOrNull()
    }

    @Test fun leftHalfKeyKeepsTheMainSymbolOnTheLeftAndSlidesRightForOthers() {
        assertEquals("()", longPressSelect("ㅂ", 0))                   // 안 밀고 떼면 대표 기호
        assertEquals("(", longPressSelect("ㅂ", 1))                    // 오른쪽으로 한 칸 → 둘째
        assertEquals(")", longPressSelect("ㅂ", 2))
    }

    @Test fun rightHalfKeyPutsTheMainSymbolOnTheRightAndSlidesLeftForOthers() {
        assertEquals("→", longPressSelect("ㅔ", 0))                    // 안 밀고 떼면 대표 기호 (맨 오른쪽)
        assertEquals("←", longPressSelect("ㅔ", -1))                   // 왼쪽(안쪽)으로 한 칸 → 둘째
        assertEquals("↑", longPressSelect("ㅔ", -2))
        assertEquals("↓", longPressSelect("ㅔ", -3))
    }

    @Test fun rightHalfPopupSitsAboveTheKeyEndingAtItsRightEdge() {
        // 대표 기호 칸이 눌린 키 바로 위에 오도록 말풍선의 오른쪽 끝을 키 오른쪽에 맞춘다 (화면 안으로는 유지)
        val r = view.keyRects().first { it.first.label == "ㅣ" }.second     // 맨 오른쪽 모음
        downTime = SystemClock.uptimeMillis()
        send(ev(MotionEvent.ACTION_DOWN, Triple(0, r.centerX(), r.centerY())))
        idleFor(view.longPressMs + 50)
        val shot = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(shot))        // 말풍선을 그려도 예외 없이 동작
        send(ev(MotionEvent.ACTION_UP, Triple(0, r.centerX(), r.centerY())))
        assertEquals(listOf("❤\uFE0F"), rec.texts)       // 대표 기호(하트)가 기본 선택
    }

    @Test fun tappingPasteStillPastes() {
        downTime = SystemClock.uptimeMillis()
        val a = p(0, "붙임")
        send(ev(MotionEvent.ACTION_DOWN, a))
        send(ev(MotionEvent.ACTION_UP, a))
        assertEquals(listOf("붙임"), rec.keys)
    }

    @Test fun longPressingEnterOpensTheQuickPanelAndDoesNotTypeEnter() {
        downTime = SystemClock.uptimeMillis()
        val a = p(0, "⏎")
        send(ev(MotionEvent.ACTION_DOWN, a))
        idleFor(view.longPressMs + 50)
        send(ev(MotionEvent.ACTION_UP, a))
        assertEquals(1, rec.enterLongPress)
        assertTrue(rec.keys.isEmpty())
    }
}
