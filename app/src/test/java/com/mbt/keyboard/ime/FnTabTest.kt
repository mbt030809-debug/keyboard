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
        val bottomOn = on.keyInnerRects().filter { it.first.type == KeyType.SPACE }.maxOf { it.second.bottom }
        val digit5On = on.keyInnerRects().first { it.first.label == "5" }.second
        val digit0On = on.keyInnerRects().first { it.first.label == "0" }.second
        assertEquals(bottomOn, digit5On.bottom, 1f)                                   // 5·0 이 맨 아래 줄 높이까지
        assertEquals(bottomOn, digit0On.bottom, 1f)
        val digit5Off = off.keyInnerRects().first { it.first.label == "5" }.second
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

    @Test fun plateIsDrawnAsOnePathWithLeftAndRightCornerRadii() {
        val r = FnTab.plateRadii(10f)
        assertEquals(listOf(10f, 10f, 10f, 10f), r.take(4))      // 좌상·우상
        assertEquals(listOf(10f, 10f, 4f, 4f), FnTab.plateRadii(10f, 4f).take(4))
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

    @Test fun tabButtonsLineUpWithTheDigitStripWhateverTheTabPadIs() {
        val d = ctx.resources.displayMetrics.density
        for (pad in listOf(0, 4, 9)) {
            val v = KeyboardView(ctx).also {
                it.numberLeft = true; it.fnTab = true; it.fnTabPadDp = pad; it.keyboardLayout = Layouts.english
                it.frame = KeyboardFrame(leftDp = 12); measure(it)
            }
            val host = FrameLayout(ctx).also { it.layoutParams = FrameLayout.LayoutParams(1000, -2).also { lp -> lp.leftMargin = 6 } }
            val tab = FnTab(ctx, v)
            FrameLayout(ctx).also { it.addView(host); it.addView(tab, FrameLayout.LayoutParams(-2, -2)) }
            com.mbt.keyboard.ime.KeyboardHost.applyFnTab(host, tab, v, true)
            val digit = v.keyInnerRects().first { it.first.label == "1" }.second
            val tabLeft = (tab.layoutParams as FrameLayout.LayoutParams).leftMargin
            // 탭 버튼의 눈에 보이는 왼쪽 = 탭 왼쪽 + 탭 여백 = 숫자 키 왼쪽 + 호스트 왼쪽 여백
            assertEquals("pad=$pad", 6 + digit.left, tabLeft + pad * d, 1f)
            assertEquals(pad * d, v.fnTabLeftPx() + v.fnTabInsetPx(), 0.01f)
            assertEquals(pad * d, v.fnTabRightPx() + v.fnTabInsetPx(), 0.01f)
        }
    }

    @Test fun noLeftoverSpaceWhenAllMarginSettingsAreZero() {
        val f = KeyboardFrame(leftDp = 0, topDp = 0, rightDp = 0, bottomDp = 0, keyGapDp = 0, rowGapDp = 0)
        val v = KeyboardView(ctx).also { it.numberLeft = true; it.fnTab = true; it.fnTabPadDp = 0; it.keyboardLayout = Layouts.english; it.frame = f; measure(it) }
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

    @Test fun tabPaddingIsItsOwnSettingEqualOnAllFourSides() {
        val ctxd = ctx.resources.displayMetrics.density
        assertEquals(4, com.mbt.keyboard.settings.KeyboardGeometry().fnTabPadDp)
        assertEquals(com.mbt.keyboard.settings.KeyboardGeometry.FN_TAB_PAD_MAX, com.mbt.keyboard.settings.KeyboardGeometry(fnTabPadDp = 99).clamped().fnTabPadDp)
        assertEquals(com.mbt.keyboard.settings.KeyboardGeometry.FN_TAB_PAD_MIN, com.mbt.keyboard.settings.KeyboardGeometry(fnTabPadDp = -3).clamped().fnTabPadDp)
        for (pad in listOf(0, 4, 11)) for (f in listOf(KeyboardFrame(), KeyboardFrame(leftDp = 10, topDp = 12, rightDp = 2, bottomDp = 7, keyGapDp = 2, rowGapDp = 14))) {
            val v = KeyboardView(ctx).also { it.numberLeft = true; it.fnTab = true; it.fnTabPadDp = pad; it.keyboardLayout = Layouts.english; it.frame = f; measure(it) }
            val tab = FnTab(ctx, v)
            measure(tab, 1000)
            val rects = ArrayList<FloatArray>()
            tab.draw(object : android.graphics.Canvas() {
                override fun drawPath(path: android.graphics.Path, p: android.graphics.Paint) {
                    val rf = android.graphics.RectF(); path.computeBounds(rf, true); rects += floatArrayOf(rf.left, rf.top, rf.right, rf.bottom)
                }
                override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: android.graphics.Paint) { rects += floatArrayOf(l, t, r, b) }
            })
            val plate = rects.first(); val buttons = rects.drop(1)
            val want = pad * ctxd
            assertEquals("pad=$pad 위", want, buttons.minOf { it[1] } - plate[1], 0.6f)
            assertEquals("pad=$pad 아래", want, plate[3] - buttons.maxOf { it[3] }, 0.6f)
            assertEquals("pad=$pad 왼쪽", want, buttons.minOf { it[0] } - plate[0], 0.6f)
            assertEquals("pad=$pad 오른쪽", want, plate[2] - buttons.maxOf { it[2] }, 1.01f)
        }
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
        tab.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        tab.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 1f, 1f, 0))
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
        assertEquals(100, com.mbt.keyboard.settings.KeyboardGeometry().fnTabHeightPct)
        assertEquals(com.mbt.keyboard.settings.KeyboardGeometry.FN_TAB_HEIGHT_MIN, com.mbt.keyboard.settings.KeyboardGeometry(fnTabHeightPct = 10).clamped().fnTabHeightPct)
        assertEquals(com.mbt.keyboard.settings.KeyboardGeometry.FN_TAB_HEIGHT_MAX, com.mbt.keyboard.settings.KeyboardGeometry(fnTabHeightPct = 900).clamped().fnTabHeightPct)
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
        assertEquals(tab.height + (2 * ctx.resources.displayMetrics.density).toInt(), (host.layoutParams as FrameLayout.LayoutParams).topMargin)
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

/** 프로스티드 글래스(iOS 27 스타일) 테마: 유리 판 한 장 + 선명한 유리 키 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrostThemeTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val ids = listOf("iosglass_holo", "iosglass_taupe", "iosglass_navy")

    @Test fun plateIsOneGlassSheetAndEveryKeyIsAClearGlassButton() {
        for (id in ids) {
            val theme = com.mbt.keyboard.ime.KeyboardThemes.byId(id)!!.theme
            assertTrue(id, theme.frost)
            val v = KeyboardView(ctx).also { it.theme = theme; it.numberLeft = true; it.fnTab = true; it.keyboardLayout = Layouts.english }
            v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            var bitmaps = 0; var rounds = 0; var paths = 0
            val canvas = object : android.graphics.Canvas() {
                override fun drawBitmap(b: android.graphics.Bitmap, l: Float, t: Float, p: android.graphics.Paint?) { bitmaps++ }
                override fun drawBitmap(b: android.graphics.Bitmap, s: android.graphics.Rect?, dst: android.graphics.RectF, p: android.graphics.Paint?) { bitmaps++ }
                override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: android.graphics.Paint) { rounds++ }
                override fun drawRoundRect(r: android.graphics.RectF, rx: Float, ry: Float, p: android.graphics.Paint) { rounds++ }
                override fun drawPath(path: android.graphics.Path, p: android.graphics.Paint) { paths++ }
                override fun clipPath(path: android.graphics.Path): Boolean = true
            }
            v.draw(canvas)
            val keys = v.keyRects().size
            assertEquals("$id 판 배경(흐린 번짐) 한 장", 1, bitmaps)
            assertTrue("$id 판 림 선 (바깥 + 안쪽)", paths >= 2)
            assertTrue("$id 키마다 그림자 + 아랫선 + 면 + 하이라이트: $rounds / $keys", rounds >= keys * 4)
        }
    }

    @Test fun tabIsRoundedOnTheBottomInEveryTheme() {
        val round = FnTab.plateRadii(10f, 6f, bottom = true)
        assertTrue(round.all { it > 0f })                                          // 네 모서리 모두 둥글게
        // 실제 탭도 글래스 테마에서는 경로를 4모서리 둥글게 그린다
        val theme = com.mbt.keyboard.ime.KeyboardThemes.byId("iosglass_holo")!!.theme
        val v = KeyboardView(ctx).also { it.theme = theme; it.numberLeft = true; it.fnTab = true; it.keyboardLayout = Layouts.english }
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val tab = FnTab(ctx, v)
        tab.theme = theme
        tab.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        tab.layout(0, 0, tab.measuredWidth, tab.measuredHeight)
        var paths = 0
        tab.draw(object : android.graphics.Canvas() { override fun drawPath(p: android.graphics.Path, paint: android.graphics.Paint) { paths++ } })
        assertEquals(1, paths)
    }

    @Test fun plateCornersAreConcentricWithTheKeysSoNothingSticksOut() {
        val theme = com.mbt.keyboard.ime.KeyboardThemes.byId("iosglass_holo")!!.theme
        for (f in listOf(KeyboardFrame(), KeyboardFrame(leftDp = 0, topDp = 0, rightDp = 0), KeyboardFrame(leftDp = 9, topDp = 6, rightDp = 2))) {
            val v = KeyboardView(ctx).also { it.theme = theme; it.keyboardLayout = Layouts.english; it.frame = f }
            v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val d = ctx.resources.displayMetrics.density
            val (rl, rr) = v.frostPlateRadiiPx()
            val k = v.frostKeyRadiusPx()
            assertEquals(k + minOf(f.leftDp, f.topDp, 10) * d, rl, 0.01f)      // 판 모서리 = 키 모서리 + 여백
            assertEquals(k + minOf(f.rightDp, f.topDp, 10) * d, rr, 0.01f)
            // 모서리 키 바깥 꼭짓점이 판 곡선 안쪽에 있다: 판 곡선 중심에서 키 모서리 곡선까지 거리 <= 판 반지름
            val padL = f.leftDp * d; val padT = f.topDp * d
            val cx = rl; val cy = rl                       // 판 왼쪽 위 곡선의 중심
            val kcx = padL + k; val kcy = padT + k          // 키 왼쪽 위 곡선의 중심
            val far = Math.hypot((kcx - cx).toDouble(), (kcy - cy).toDouble()) + k   // 키 곡선에서 판 중심까지 가장 먼 거리
            assertTrue("$f: $far <= $rl", far <= rl + 0.01)
        }
    }

    @Test fun threeThemesAreListedFirstWithWhiteKeysOnAGlassPlate() {
        val all = com.mbt.keyboard.ime.KeyboardThemes.all
        assertEquals(ids + listOf("iosglass_taupe_refract", "sunset", "pinkcloud", "aurora", "mintsoda"), all.filter { it.theme.frost }.map { it.id })
        assertEquals(ids, all.take(3).map { it.id })
        ids.forEach { id ->
            val t = all.first { it.id == id }.theme
            assertTrue(t.hasGradient)
            assertEquals(3, t.backgroundStops.size)
            assertTrue("$id 키는 흰색 유리(뒤가 살짝 비침)", (t.key ushr 24) in 0xA0..0xE0 && ((t.key shr 16) and 0xFF) == 0xFF)
            assertTrue("$id 기능키는 키보다 투명", (t.specialKey ushr 24) < (t.key ushr 24))
        }
    }
}

/** 글래스 테마: 키보드 판 맨 위 6dp는 뒤 앱이 비치는 띠 (탭과 같은 불투명도 → 아래로 페이드) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrostFadeTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun layout(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun insetsFor(themeId: String, tab: Boolean): Triple<Int, Int, android.graphics.Rect> {
        Prefs.setNumberKeysLeft(ctx, true)
        Prefs.setFnTabEnabled(ctx, tab)
        Prefs.setThemeId(ctx, themeId)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layout(root)
        val ins = android.inputmethodservice.InputMethodService.Insets()
        service.onComputeInsets(ins)
        val host = root.getChildAt(0)
        return Triple(ins.contentTopInsets, (host.layoutParams as FrameLayout.LayoutParams).topMargin, ins.touchableRegion.bounds)
    }

    @Test fun appIsLaidUnderTheTabAndTheFadeStripOfAGlassKeyboard() {
        val d = ctx.resources.displayMetrics.density
        val (content, tabH, region) = insetsFor("iosglass_holo", tab = true)
        assertTrue(tabH > 0)
        assertEquals(tabH + 6 * d, content.toFloat(), 1.5f)            // 탭 높이 + 띠 6dp 만큼 앱을 더 깐다
    }

    @Test fun glassThemeWithoutTabStillGetsTheFadeStrip() {
        val d = ctx.resources.displayMetrics.density
        val (content, tabH, region) = insetsFor("iosglass_holo", tab = false)
        assertEquals(0, tabH)
        assertEquals(6 * d, content.toFloat(), 1.5f)
    }

    @Test fun tabAndKeyboardAreSeparatedByATwoDpGap() {
        val d = ctx.resources.displayMetrics.density
        val v = KeyboardView(ctx).also { it.numberLeft = true; it.fnTab = true; it.keyboardLayout = Layouts.english }
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertEquals(2 * d, v.fnTabGapPx(), 0.01f)
        val host = FrameLayout(ctx)
        val tab = FnTab(ctx, v)
        val parent = FrameLayout(ctx).also { it.addView(host, FrameLayout.LayoutParams(1000, FrameLayout.LayoutParams.WRAP_CONTENT)); it.addView(tab, FrameLayout.LayoutParams(-2, -2)) }
        com.mbt.keyboard.ime.KeyboardHost.applyFnTab(host, tab, v, true)
        assertEquals((v.fnTabHeightPx() + 2 * d).toInt(), (host.layoutParams as FrameLayout.LayoutParams).topMargin)
    }

    @Test fun glassPlateHasSeveralSoftLightBandsAndATopGlow() {
        val theme = com.mbt.keyboard.ime.KeyboardThemes.byId("iosglass_holo")!!.theme
        val v = KeyboardView(ctx).also { it.theme = theme; it.keyboardLayout = Layouts.english }
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val shaders = ArrayList<android.graphics.Shader>()
        v.draw(object : android.graphics.Canvas() {
            override fun drawRect(l: Float, t: Float, r: Float, b: Float, p: android.graphics.Paint) { p.shader?.let { shaders += it } }
            override fun saveLayer(l: Float, t: Float, r: Float, b: Float, p: android.graphics.Paint?): Int = 1
        })
        // 위쪽 빛 + 빛띠 + 위 띠 페이드 마스크 (모두 그라데이션)
        assertTrue("그라데이션 ${shaders.size}", shaders.count { it is android.graphics.LinearGradient } >= 3)
    }

    @Test fun otherThemesAreUnchanged() {
        val d = ctx.resources.displayMetrics.density
        val (content, tabH, _) = insetsFor("charcoal", tab = true)
        assertEquals(tabH.toFloat(), content.toFloat(), 1.5f)           // 탭 높이만큼만
        val (content2, _, _) = insetsFor("charcoal", tab = false)
        assertEquals(0, content2)
    }

    @Test fun plateTopIsMaskedByAFadeFromTheTabAlpha() {
        val theme = com.mbt.keyboard.ime.KeyboardThemes.byId("iosglass_holo")!!.theme
        val v = KeyboardView(ctx).also { it.theme = theme; it.keyboardLayout = Layouts.english; it.frostFadeAlpha = 0.5f }
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        assertEquals(6 * ctx.resources.displayMetrics.density, v.frostFadePx(), 0.01f)
        var layers = 0
        v.draw(object : android.graphics.Canvas() {
            override fun saveLayer(l: Float, t: Float, r: Float, b: Float, p: android.graphics.Paint?): Int { layers++; return 1 }
        })
        assertEquals(1, layers)                                         // 판을 한 겹으로 그린 뒤 위 띠를 페이드 마스크
    }
}

/** 글래스 테마: 전복붙 탭은 판과 떨어진 둥근 알약 그대로, 재질(번짐 배경·빛·빛띠)만 판과 같은 한 장의 유리 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrostTabJoinTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val d get() = ctx.resources.displayMetrics.density

    private fun setup(themeId: String): Triple<KeyboardView, FnTab, FrameLayout> {
        val theme = com.mbt.keyboard.ime.KeyboardThemes.byId(themeId)!!.theme
        val v = KeyboardView(ctx).also { it.theme = theme; it.numberLeft = true; it.fnTab = true; it.keyboardLayout = Layouts.english }
        val host = FrameLayout(ctx).also { it.addView(v, FrameLayout.LayoutParams(-1, -2)) }
        val tab = FnTab(ctx, v).also { it.theme = theme }
        FrameLayout(ctx).also { it.addView(host, FrameLayout.LayoutParams(1000, -2)); it.addView(tab, FrameLayout.LayoutParams(-2, -2)) }
        com.mbt.keyboard.ime.KeyboardHost.applyFnTab(host, tab, v, true)
        host.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        tab.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        tab.layout(0, 0, tab.measuredWidth, tab.measuredHeight)
        return Triple(v, tab, host)
    }

    @Test fun everyThemeKeepsTheTwoDpGapBetweenTabAndPlate() {
        for (id in listOf("iosglass_holo", "charcoal")) {
            val (v, tab, host) = setup(id)
            assertEquals(id, 2 * d, v.fnTabGapPx(), 0.01f)
            assertEquals(id, tab.height + (2 * d).toInt(), (host.layoutParams as FrameLayout.LayoutParams).topMargin)
        }
        assertTrue(setup("iosglass_holo").first.frostTabAttached)
        assertTrue(!setup("charcoal").first.frostTabAttached)
    }

    @Test fun glassTabStaysARoundedPillOnAllFourCornersAndSharesTheSurface() {
        val (v, tab, _) = setup("iosglass_holo")
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        // 판: 림을 건너뛰는 일이 없다 (탭은 떨어져 있으므로)
        var clipOuts = 0
        v.draw(object : android.graphics.Canvas() {
            override fun saveLayer(l: Float, t: Float, r: Float, b: Float, p: android.graphics.Paint?): Int = save()
            override fun clipOutRect(l: Float, t: Float, r: Float, b: Float): Boolean { clipOuts++; return true }
            override fun clipPath(path: android.graphics.Path): Boolean = true
        })
        assertEquals(0, clipOuts)
        // 탭: 판과 같은 표면을 알파 레이어로 그리고, 둥근 알약 테두리를 긋는다
        tab.alphaPercent = 50
        var layerAlphas = 0; var strokes = 0
        tab.draw(object : android.graphics.Canvas() {
            override fun saveLayerAlpha(l: Float, t: Float, r: Float, b: Float, a: Int): Int { layerAlphas++; assertEquals(127, a); return save() }
            override fun clipPath(path: android.graphics.Path): Boolean = true
            override fun drawPath(path: android.graphics.Path, p: android.graphics.Paint) { strokes++ }
        })
        assertEquals(1, layerAlphas)
        assertEquals(1, strokes)
        assertTrue(FnTab.plateRadii(10f, 6f, bottom = true).all { it > 0f })       // 네 모서리 모두 둥글게
    }

    @Test fun backdropCoversTheTabAreaToo() {
        val (v, _, _) = setup("iosglass_holo")
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val sizes = ArrayList<Int>()
        v.draw(object : android.graphics.Canvas() {
            override fun saveLayer(l: Float, t: Float, r: Float, b: Float, p: android.graphics.Paint?): Int = save()
            override fun clipPath(path: android.graphics.Path): Boolean = true
            override fun drawBitmap(b: android.graphics.Bitmap, s: android.graphics.Rect?, dst: android.graphics.RectF, p: android.graphics.Paint?) { sizes += b.height }
        })
        assertTrue(sizes.isNotEmpty())
        assertTrue("판 높이 + 탭 + 틈: ${sizes.first()} vs ${v.measuredHeight}", sizes.first() > v.measuredHeight)
    }
}

/** 폴드를 접고 펼쳐도 전복붙 탭이 키보드 숫자 키와 같은 크기로 다시 맞춰져야 한다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FnTabFoldResizeTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun layout(root: View, w: Int) {
        repeat(2) {
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    @Test fun tabIsMeasuredAgainWhenTheKeyboardWidthChanges() {
        Prefs.setNumberKeysLeft(ctx, true)
        Prefs.setFnTabEnabled(ctx, true)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layout(root, 1000)
        val host = root.getChildAt(0) as FrameLayout
        val kb = host.getChildAt(0) as KeyboardView
        val tab = root.getChildAt(1) as FnTab
        val wide = tab.width
        assertEquals(Math.ceil(kb.fnTabWidthPx().toDouble()).toInt(), wide)
        // 접힘(좁은 화면)으로 바뀐 것처럼 폭을 줄이면 탭도 다시 맞춰진다
        layout(root, 540)
        assertEquals(Math.ceil(kb.fnTabWidthPx().toDouble()).toInt(), tab.width)
        assertTrue("접힘에서는 탭이 더 좁다: $wide -> ${tab.width}", tab.width < wide)
        // 다시 펼치면 원래대로
        layout(root, 1000)
        assertEquals(wide, tab.width)
        // 탭 버튼은 항상 숫자 키와 같은 크기
        val digit = kb.keyInnerRects().first { it.first.label == "1" }.second
        assertEquals(digit.width(), kb.fnKeyWidthPx() - 2 * kb.fnTabInsetPx(), 1f)
    }
}
