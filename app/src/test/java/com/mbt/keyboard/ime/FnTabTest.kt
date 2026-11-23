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
            override fun drawPath(path: android.graphics.Path, p: android.graphics.Paint) {
                val rf = android.graphics.RectF(); path.computeBounds(rf, true)
                rects += floatArrayOf(rf.left, rf.top, rf.right, rf.bottom); colors += p.color
            }
        }
        tab.draw(canvas)
        val plate = rects.first()
        val buttons = rects.drop(1)
        assertEquals(3, buttons.size)
        val pad = v.fnTabPadPx()
        assertEquals(pad, buttons.minOf { it[1] } - plate[1], 1f)                        // 위
        assertEquals(v.fnTabPadBottomPx(), plate[3] - buttons.maxOf { it[3] }, 1f)       // 아래
        assertEquals(v.fnTabRightPx() + v.fnTabInsetPx(), plate[2] - buttons.maxOf { it[2] }, 1f)    // 오른쪽
        assertEquals(v.fnTabLeftPx() + v.fnTabInsetPx(), buttons.minOf { it[0] } - plate[0], 1f)     // 왼쪽
        colors.forEach { assertEquals(127f, (it ushr 24).toFloat(), 1f) }                // 바탕·버튼 같은 반투명
    }

    @Test fun plateIsRoundedOnTopAndSquareAtTheBottom() {
        val r = FnTab.plateRadii(10f)
        assertEquals(listOf(10f, 10f, 10f, 10f), r.take(4))      // 좌상·우상
        assertEquals(listOf(10f, 10f, 4f, 4f), FnTab.plateRadii(10f, 4f).take(4))
        assertEquals(listOf(0f, 0f, 0f, 0f), r.drop(4))          // 우하·좌하 = 사각
        // 실제로 바탕은 경로로 그려진다 (둥근 사각형 호출이 아니라)
        val v = keyView(true, true)
        val tab = FnTab(ctx, v)
        measure(tab, 1000)
        var paths = 0
        val canvas = object : android.graphics.Canvas() {
            override fun drawPath(p: android.graphics.Path, paint: android.graphics.Paint) { paths++ }
        }
        tab.draw(canvas)
        assertEquals(1, paths)
    }

    @Test fun plateMarginsFollowTheKeyboardSettings() {
        for (f in listOf(KeyboardFrame(), KeyboardFrame(leftDp = 10, topDp = 12, rightDp = 2, rowGapDp = 14, keyGapDp = 2))) {
            val v = KeyboardView(ctx).also { it.numberLeft = true; it.fnTab = true; it.keyboardLayout = Layouts.english; it.frame = f; measure(it) }
            val d = ctx.resources.displayMetrics.density
            // 키보드의 키 둘레와 똑같다: 첫 숫자 키는 키보드 왼쪽에서 (왼쪽 여백 + 키 간격/2), 위에서는 (위 여백 + 줄 여백/2)
            val digit = v.keyInnerRects().first { it.first.label == "1" }.second
            assertEquals(digit.left, v.fnTabLeftPx() + v.fnTabInsetPx(), 1f)
            assertEquals(digit.top, v.fnTabPadPx(), 1f)
            assertEquals(f.rightDp * d, v.fnTabRightPx(), 0.5f)
        }
    }

    @Test fun noLeftoverSpaceWhenAllMarginSettingsAreZero() {
        val f = KeyboardFrame(leftDp = 0, topDp = 0, rightDp = 0, bottomDp = 0, keyGapDp = 0, rowGapDp = 0)
        val v = KeyboardView(ctx).also { it.numberLeft = true; it.fnTab = true; it.keyboardLayout = Layouts.english; it.frame = f; measure(it) }
        assertEquals(0f, v.fnTabPadPx(), 0.01f)
        assertEquals(0f, v.fnTabPadBottomPx(), 0.01f)
        assertEquals(0f, v.fnTabLeftPx() + v.fnTabInsetPx(), 0.01f)
        assertEquals(0f, v.fnTabRightPx() + v.fnTabInsetPx(), 0.01f)
        val tab = FnTab(ctx, v)
        measure(tab, 1000)
        // 탭 높이 = 버튼 높이, 탭 아래 끝 = 키보드 맨 위 = 첫 숫자 키 윗변
        assertEquals(v.fnKeyHeightPx(), tab.height.toFloat(), 1f)
        assertEquals(0f, v.keyInnerRects().first { it.first.label == "1" }.second.top, 0.01f)
        assertTrue(tab.width.toFloat() - 3 * v.fnKeyWidthPx() < 1f)                        // 오른쪽 덤 폭 없음
        // 바탕 모서리는 버튼 모서리와 같은 둥글기(7dp)라 빈틈이 안 남는다
        val rects = ArrayList<FloatArray>()
        val canvas = object : android.graphics.Canvas() {
            override fun drawPath(path: android.graphics.Path, p: android.graphics.Paint) {
                val rf = android.graphics.RectF(); path.computeBounds(rf, true); rects += floatArrayOf(rf.left, rf.top, rf.right, rf.bottom)
            }
            override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: android.graphics.Paint) { rects += floatArrayOf(l, t, r, b) }
        }
        tab.draw(canvas)
        val plate = rects.first(); val first = rects[1]
        assertEquals(plate[0], first[0], 0.5f); assertEquals(plate[1], first[1], 0.5f)
        assertEquals(plate[3], first[3], 0.5f)
    }

    @Test fun gapBetweenTabAndDigitKeysMatchesTheGapBetweenLetterRows() {
        val v = KeyboardView(ctx).also {
            it.numberLeft = true; it.fnTab = true; it.keyboardLayout = Layouts.english
            it.frame = KeyboardFrame(topDp = 4, rowGapDp = 6)
            measure(it)
        }
        val tab = FnTab(ctx, v)
        measure(tab, 1000)
        val d = ctx.resources.displayMetrics.density
        val firstDigitTop = v.keyInnerRects().first { it.first.label == "1" }.second.top
        // 탭 버튼 아래 끝 = 탭 높이 − 아래 바탕. 키보드 맨 위는 탭 높이만큼 아래에서 시작
        val buttonBottom = tab.height - v.fnTabPadBottomPx()
        val gap = tab.height - buttonBottom + firstDigitTop
        val letterGap = v.keyInnerRects().let { r -> r.first { it.first.label == "a" }.second.top - r.first { it.first.label == "q" }.second.bottom }
        assertEquals(letterGap, gap, 2.5f * d)
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

    @Test fun tabFillAndTextAreBothSemiTransparent() {
        val v = keyView(true, true)
        val tab = FnTab(ctx, v)
        tab.alphaPercent = 50
        measure(tab, 1000)
        val fills = ArrayList<Int>(); val textColors = ArrayList<Int>()
        val canvas = object : android.graphics.Canvas() {
            override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: android.graphics.Paint) { fills += p.color }
            override fun drawPath(path: android.graphics.Path, p: android.graphics.Paint) { fills += p.color }
            override fun drawText(text: String, x: Float, y: Float, p: android.graphics.Paint) { textColors += p.color }
        }
        tab.draw(canvas)
        assertEquals(4, fills.size)                                                  // 바탕 1 + 버튼 3
        fills.forEach { assertEquals(127f, (it ushr 24).toFloat(), 1f) }
        assertEquals(3, textColors.size)
        textColors.forEach { assertEquals(127f, (it ushr 24).toFloat(), 1f) }
    }

    @Test fun tabButtonHeightHasItsOwnScaleSetting() {
        assertEquals(100, Prefs.fnTabHeightPercent(ctx))
        Prefs.setFnTabHeightPercent(ctx, 10); assertEquals(Prefs.FN_TAB_HEIGHT_MIN, Prefs.fnTabHeightPercent(ctx))
        Prefs.setFnTabHeightPercent(ctx, 900); assertEquals(Prefs.FN_TAB_HEIGHT_MAX, Prefs.fnTabHeightPercent(ctx))
        val v = keyView(true, true)
        val base = v.fnKeyHeightPx()
        v.fnTabHeightScale = 0.6f
        assertEquals(base * 0.6f, v.fnKeyHeightPx(), 0.5f)
        v.fnTabHeightScale = 1.4f
        assertEquals(base * 1.4f, v.fnKeyHeightPx(), 0.5f)
        // 키보드 숫자 키 높이는 그대로 (탭만 변한다)
        assertEquals(keyView(true, true).keyInnerRects().first { it.first.label == "1" }.second.height(),
            v.keyInnerRects().first { it.first.label == "1" }.second.height(), 0.5f)
        // 탭 높이가 변하면 키보드 위에 비우는 자리도 같이 변한다
        assertEquals(v.fnTabPadPx() + v.fnKeyHeightPx() + v.fnTabPadBottomPx(), v.fnTabHeightPx(), 0.5f)
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
