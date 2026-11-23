package com.mbt.keyboard.ime

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 전체·복사·붙임을 키보드 위 반투명 탭으로 띄우는 기능 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FnTabTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun measure(v: View, w: Int = 1000) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun keyView(numberLeft: Boolean, tab: Boolean, layout: com.mbt.keyboard.layout.KeyboardLayout = Layouts.english): KeyboardView =
        KeyboardView(ctx).also {
            it.numberLeft = numberLeft
            it.fnTab = tab
            it.keyboardLayout = layout
            measure(it)
        }

    private fun labels(v: KeyboardView) = v.keyRects().map { it.first.label }

    @Test fun tabModeRemovesTheThreeKeysFromTheBottomRowOnlyWhenDigitsAreOnTheLeft() {
        assertTrue(labels(keyView(true, false)).containsAll(listOf("전체", "복사", "붙임")))
        assertTrue(labels(keyView(true, true)).none { it in listOf("전체", "복사", "붙임") })
        assertTrue(labels(keyView(true, true, Layouts.moaki)).none { it in listOf("전체", "복사", "붙임") })
        // 숫자가 위에 있는 자판은 그대로 (탭을 켜도 영향 없음)
        assertTrue(labels(keyView(false, true)).containsAll(listOf("전체", "복사", "붙임")))
    }

    @Test fun hasSideStripReportsDigitsOnTheLeft() {
        assertTrue(keyView(true, false).hasSideStrip)
        assertTrue(keyView(false, false, Layouts.moaki).hasSideStrip)
        assertFalse(keyView(false, false).hasSideStrip)
    }

    @Test fun tabButtonsMatchDigitKeyWidthAndHeight() {
        val v = keyView(true, true)
        val digit = v.keyInnerRects().first { it.first.label == "1" }.second
        assertEquals(digit.width(), v.fnKeyWidthPx() - 2 * v.fnTabInsetPx(), 1f)    // 눈에 보이는 키 폭
        assertEquals(digit.left, v.fnTabLeftPx() + v.fnTabInsetPx(), 1f)             // 숫자 띠와 같은 선
        assertEquals(digit.height(), v.fnKeyHeightPx(), 1f)                          // 숫자 키와 세로도 같다
    }

    @Test fun digitStripReachesTheBottomRowWhenTheTabIsOn() {
        val on = keyView(true, true)
        val off = keyView(true, false)
        val bottomOn = on.keyRects().filter { it.first.type == KeyType.SPACE }.maxOf { it.second.bottom }
        val digit5On = on.keyRects().first { it.first.label == "5" }.second
        val digit0On = on.keyRects().first { it.first.label == "0" }.second
        assertEquals(bottomOn, digit5On.bottom, 1f)                                   // 5·0 이 맨 아래 줄 높이까지
        assertEquals(bottomOn, digit0On.bottom, 1f)
        val digit5Off = off.keyRects().first { it.first.label == "5" }.second
        assertTrue(digit5On.bottom > digit5Off.bottom)
        // 맨 아래 줄 키는 띠 오른쪽에서 시작한다
        val spaceLeft = on.keyRects().first { it.first.type == KeyType.SPACE }.second.left
        val emojiLeft = on.keyRects().filter { it.first.type == KeyType.VOICE }.minOf { it.second.left }
        assertTrue(emojiLeft >= digit0On.right - 1f)
        assertTrue(spaceLeft > emojiLeft)
        // 모아키(줄 5개)에서는 숫자 키 높이가 글자 줄 높이와 같다
        val moa = keyView(false, true, Layouts.moaki)
        val rowH = moa.keyRects().first { it.first.label == "ㅂ" }.second.height()
        assertEquals(rowH, moa.keyRects().first { it.first.label == "3" }.second.height(), 1f)
    }

    @Test fun plateSurroundsTheButtonsOnAllFourSidesWithTheSameTranslucency() {
        val v = keyView(true, true)
        val tab = FnTab(ctx, v)
        tab.alphaPercent = 50
        measure(tab, 1000)
        val rects = ArrayList<FloatArray>(); val colors = ArrayList<Int>()
        val canvas = object : android.graphics.Canvas() {
            override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: android.graphics.Paint) { rects += floatArrayOf(l, t, r, b); colors += p.color }
        }
        tab.draw(canvas)
        val plate = rects.first()
        val buttons = rects.drop(1)
        assertEquals(3, buttons.size)
        val pad = v.fnTabPadPx()
        assertEquals(pad, buttons.minOf { it[1] } - plate[1], 1f)                        // 위
        assertEquals(pad, plate[3] - buttons.maxOf { it[3] }, 1f)                        // 아래
        assertEquals(pad, plate[2] - buttons.maxOf { it[2] } - v.fnTabInsetPx(), 1f)     // 오른쪽
        assertTrue(buttons.minOf { it[0] } - plate[0] > 0f)                              // 왼쪽
        colors.forEach { assertEquals(127f, (it ushr 24).toFloat(), 1f) }                // 바탕·버튼 같은 반투명
    }

    @Test fun spaceBarGetsWiderWhenTheTabTakesOverTheThreeKeys() {
        val off = keyView(true, false).keyRects().first { it.first.type == KeyType.SPACE }.second.width()
        val on = keyView(true, true).keyRects().first { it.first.type == KeyType.SPACE }.second.width()
        assertTrue("$off -> $on", on > off)
    }

    @Test fun tapsFireTheFunctionKeys() {
        val v = keyView(true, true)
        val tab = FnTab(ctx, v)
        val fired = ArrayList<KeyType>()
        tab.onKey = { fired += it.type }
        measure(tab, 1000)
        val kw = v.fnKeyWidthPx()
        fun tap(i: Int) {
            val x = v.fnTabLeftPx() + i * kw + kw / 2f
            val t = SystemClock.uptimeMillis()
            tab.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, tab.height / 2f, 0))
            tab.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, tab.height / 2f, 0))
        }
        tap(0); tap(1); tap(2)
        assertEquals(listOf(KeyType.SELECT_ALL, KeyType.COPY, KeyType.PASTE), fired)
        // 버튼 밖(왼쪽 여백)을 눌러도 아무 일 없다
        val t = SystemClock.uptimeMillis()
        tab.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1f, 5f, 0))
        tab.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 1f, 5f, 0))
        assertEquals(3, fired.size)
    }

    @Test fun tabFillIsSemiTransparentButTextIsOpaque() {
        val v = keyView(true, true)
        val tab = FnTab(ctx, v)
        tab.alphaPercent = 50
        measure(tab, 1000)
        val fills = ArrayList<Int>(); val textColors = ArrayList<Int>()
        val canvas = object : android.graphics.Canvas() {
            override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: android.graphics.Paint) { fills += p.color }
            override fun drawText(text: String, x: Float, y: Float, p: android.graphics.Paint) { textColors += p.color }
        }
        tab.draw(canvas)
        assertEquals(4, fills.size)                                                  // 바탕 1 + 버튼 3
        fills.forEach { assertEquals(127f, (it ushr 24).toFloat(), 1f) }
        assertEquals(3, textColors.size)
        textColors.forEach { assertEquals(255, it ushr 24) }
    }

    @Test fun prefsDefaultsAndClamping() {
        assertTrue(Prefs.fnTabEnabled(ctx))
        assertEquals(50, Prefs.fnTabAlphaPercent(ctx))
        Prefs.setFnTabAlphaPercent(ctx, 5); assertEquals(Prefs.FN_TAB_ALPHA_MIN, Prefs.fnTabAlphaPercent(ctx))
        Prefs.setFnTabAlphaPercent(ctx, 99); assertEquals(Prefs.FN_TAB_ALPHA_MAX, Prefs.fnTabAlphaPercent(ctx))
        Prefs.setFnTabEnabled(ctx, false); assertFalse(Prefs.fnTabEnabled(ctx))
    }

    @Test fun serviceShowsTheTabOnlyWithDigitsOnTheLeftAndReservesItsHeight() {
        Prefs.setNumberKeysLeft(ctx, true)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        measure(root); shadowOf(android.os.Looper.getMainLooper()).idle(); measure(root)
        val host = root.getChildAt(0)
        val tab = root.getChildAt(1) as FnTab
        assertEquals(View.VISIBLE, tab.visibility)
        assertTrue((host.layoutParams as FrameLayout.LayoutParams).topMargin > 0)
        assertEquals((host.layoutParams as FrameLayout.LayoutParams).topMargin, tab.height)
        assertTrue("탭 높이만큼 전체가 더 크다", root.measuredHeight >= host.measuredHeight + tab.height - 1)

        // 끄면 탭도, 확보한 자리도 사라진다
        Prefs.setFnTabEnabled(ctx, false)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        measure(root)
        assertEquals(View.GONE, tab.visibility)
        assertEquals(0, (host.layoutParams as FrameLayout.LayoutParams).topMargin)
    }

    @Test fun noTabWhenDigitsStayOnTop() {
        Prefs.setNumberKeysLeft(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        measure(root); shadowOf(android.os.Looper.getMainLooper()).idle(); measure(root)
        assertEquals(View.GONE, root.getChildAt(1).visibility)
    }
}
