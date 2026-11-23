package com.mbt.keyboard.ime

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 키 둘레 여백(위·아래·왼쪽·오른쪽)과 키 사이 간격이 실제 키 배치에 반영되는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardFrameTest {
    private lateinit var view: KeyboardView
    private val density get() = view.resources.displayMetrics.density
    private val widthPx = 1000

    @Before fun setUp() {
        view = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        view.keyboardLayout = Layouts.english
        relayout()
    }

    private fun relayout() {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun defaultFrameKeepsTheOldLayout() {
        val rects = view.keyRects().map { it.second }
        assertEquals(4 * density, rects.minOf { it.left }, 0.01f)
        assertEquals(widthPx - 4 * density, rects.maxOf { it.right }, 0.01f)
        assertEquals(4 * density, rects.minOf { it.top }, 0.01f)
    }

    @Test fun eachSideCanBeSetIndependently() {
        view.frame = KeyboardFrame(leftDp = 20, topDp = 0, rightDp = 2, bottomDp = 9, keyGapDp = 6)
        relayout()
        val rects = view.keyRects().map { it.second }
        assertEquals(20 * density, rects.minOf { it.left }, 0.01f)
        assertEquals(widthPx - 2 * density, rects.maxOf { it.right }, 0.01f)
        assertEquals(0f, rects.minOf { it.top }, 0.01f)
        // 아래쪽 여백은 키보드 전체 높이에 반영 (마지막 줄 아래 9dp)
        assertEquals(rects.maxOf { it.bottom } + 9 * density, view.measuredHeight.toFloat(), 1f)
    }

    @Test fun verticalPaddingsChangeTheKeyboardHeight() {
        val base = view.measuredHeight
        view.frame = KeyboardFrame(topDp = 14, bottomDp = 14)           // 위·아래 각 +10dp
        relayout()
        assertEquals((base + 20 * density).toDouble(), view.measuredHeight.toDouble(), 2.0)
        view.frame = KeyboardFrame(topDp = 0, bottomDp = 0)             // 위·아래 각 -4dp
        relayout()
        assertEquals((base - 8 * density).toDouble(), view.measuredHeight.toDouble(), 2.0)
    }

    @Test fun zeroPaddingLetsKeysTouchTheEdges() {
        view.frame = KeyboardFrame(0, 0, 0, 0, 6)
        relayout()
        val rects = view.keyRects().map { it.second }
        assertEquals(0f, rects.minOf { it.left }, 0.01f)
        assertEquals(widthPx.toFloat(), rects.maxOf { it.right }, 0.01f)
    }

    @Test fun keyGapControlsTheSpaceBetweenDrawnKeys() {
        fun gapBetweenFirstTwo(): Float {
            val inner = view.keyInnerRects().filter { it.first.label == "q" || it.first.label == "w" }.map { it.second }
            return (inner[1].left - inner[0].right) / density
        }
        assertEquals(6f, gapBetweenFirstTwo(), 0.05f)                    // 기본: 키 사이 6dp
        view.frame = KeyboardFrame(keyGapDp = 0); relayout()
        assertEquals(0f, gapBetweenFirstTwo(), 0.05f)
        view.frame = KeyboardFrame(keyGapDp = 12); relayout()
        assertEquals(12f, gapBetweenFirstTwo(), 0.05f)
    }

    @Test fun touchInsideThePaddingStillHitsTheNearestKey() {
        view.frame = KeyboardFrame(leftDp = 30, topDp = 4, rightDp = 4, bottomDp = 4)
        relayout()
        val fired = ArrayList<String>()
        view.listener = object : KeyboardView.Listener {
            override fun onKey(key: com.mbt.keyboard.layout.Key) { fired += key.label }
            override fun onText(text: String) = Unit
            override fun onSwipeSpace() = Unit
            override fun onCursorMove(steps: Int) = Unit
            override fun onCursorMoveEnd() = Unit
            override fun onShiftPress(down: Boolean) = Unit
            override fun onEnterLongPress() = Unit
        }
        val row = view.keyRects().filter { it.first.label == "q" }.first().second
        val y = row.centerY()
        val t = android.os.SystemClock.uptimeMillis()
        val x = 5f                                                    // 왼쪽 여백 영역(키가 없는 곳)
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10, android.view.MotionEvent.ACTION_UP, x, y, 0))
        assertTrue(fired.toString(), fired == listOf("q"))
    }
}
