package com.mbt.keyboard.ime

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.KeyboardLayout
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 숫자 키를 왼쪽 세로 띠로 옮기는 옵션 (단모음·두벌식·영어) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NumberLeftTest {
    private lateinit var view: KeyboardView
    private val density get() = view.resources.displayMetrics.density
    private val digits = "1234567890".map { it.toString() }
    private val keys = ArrayList<String>()

    @Before fun setUp() {
        view = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        view.listener = object : KeyboardView.Listener {
            override fun onKey(key: Key) { keys += key.label }
            override fun onText(text: String) = Unit
            override fun onSwipeSpace() = Unit
            override fun onCursorMove(steps: Int) = Unit
            override fun onCursorMoveEnd() = Unit
            override fun onShiftPress(down: Boolean) = Unit
            override fun onEnterLongPress() = Unit
        }
        show(Layouts.koreanSingle, numberLeft = true)
    }

    private fun show(l: KeyboardLayout, numberLeft: Boolean, frame: KeyboardFrame = KeyboardFrame(), widthPx: Int = 1000) {
        view.numberLeft = numberLeft
        view.keyboardLayout = l
        view.frame = frame
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun rect(label: String) = view.keyRects().first { it.first.label == label }.second
    private fun inner(label: String) = view.keyInnerRects().first { it.first.label == label }.second

    @Test fun defaultIsOnAndTheSettingIsSaved() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(Prefs.numberKeysLeft(ctx))
        Prefs.setNumberKeysLeft(ctx, false)
        assertFalse(Prefs.numberKeysLeft(ctx))
    }

    @Test fun allThreeLayoutsPutDigitsInATwoColumnStripLeftOfTheLetters() {
        for ((layout, firstLetter) in listOf(Layouts.koreanSingle to "ㅂ", Layouts.korean to "ㅂ", Layouts.english to "q")) {
            show(layout, numberLeft = true)
            val d = view.keyInnerRects().filter { it.first.label in digits }.map { it.second }
            assertEquals(layout.id, 10, d.size)
            assertTrue(layout.id, d.all { it.right <= inner(firstLetter).left + 1f })              // 모두 글자 키 왼쪽
            assertEquals(layout.id, 2, d.map { it.left.toInt() }.toSet().size)                      // 2열
            assertEquals(layout.id, 5, d.map { it.top.toInt() }.toSet().size)                       // 5칸
            // 맨 위 줄에 숫자는 없다: 첫 줄은 글자 줄이다
            assertTrue(layout.id, inner(firstLetter).top <= d.minOf { it.top } + 1f)
        }
    }

    @Test fun selectAllCopyPasteMoveToTheBottomRowWithTheDigitKeySize() {
        for (layout in listOf(Layouts.koreanSingle, Layouts.korean, Layouts.english)) {
            show(layout, numberLeft = true)
            val digit = inner("5")
            val space = view.keyRects().first { it.first.type == KeyType.SPACE }.second
            listOf("전체", "복사", "붙임").forEach { label ->
                val r = rect(label)
                assertEquals("${layout.id} $label 줄", space.top, r.top, 1f)                        // 맨 아래 줄
                assertEquals("${layout.id} $label 가로", digit.width(), inner(label).width(), 1f)
                assertEquals("${layout.id} $label 세로(스페이스바와 같게)", view.keyInnerRects().first { it.first.type == KeyType.SPACE }.second.height(), inner(label).height(), 1f)
            }
        }
    }

    private class SizeRec : android.graphics.Canvas() {
        val sizes = HashMap<String, Float>()
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) { sizes[text] = paint.textSize }
    }

    private fun letterTextSize(label: String): Float = SizeRec().also { view.draw(it) }.sizes.getValue(label)

    @Test fun letterTextDoesNotGrowWhenTheRowsGetTaller() {
        for ((layout, label) in listOf(Layouts.koreanSingle to "ㅂ", Layouts.korean to "ㅂ", Layouts.english to "q")) {
            show(layout, numberLeft = false); val before = letterTextSize(label)
            show(layout, numberLeft = true); val after = letterTextSize(label)
            assertTrue("${layout.id} 글자가 커지면 안 된다: $before -> $after", after <= before + 0.6f)    // 줄이 높아져도 그대로이거나, 키가 좁아지면 작아진다
        }
        // 전체·복사·붙임 글자도 예전 숫자 줄 크기 안쪽
        show(Layouts.english, numberLeft = true)
        assertTrue(letterTextSize("전체") <= 0.34f * view.keyRects().first { it.first.label == "전체" }.second.height() + density + 0.5f)
    }

    @Test fun labelSizeFollowsTheSmallerOfKeyWidthAndHeight() {
        // 키가 좁아질수록(숫자 띠가 넓어져 글자 키 폭이 줄수록) 글자도 작아진다
        view.fontScale = 1f
        show(Layouts.english, numberLeft = true, frame = KeyboardFrame(numberRowScale = 0.4f), widthPx = 420); val wide = letterTextSize("q")
        show(Layouts.english, numberLeft = true, frame = KeyboardFrame(numberRowScale = 1.2f), widthPx = 420); val narrow = letterTextSize("q")
        assertTrue("$wide -> $narrow", narrow < wide)
        // 기준 = min(높이(기본 키 높이까지), 가로×1.35): 폭이 기준이 되는 키는 가로에 비례
        val r = rect("q")
        val ref = minOf(r.height(), view.theme.keyHeightDp * density, r.width() * 1.35f)
        assertEquals(ref * 0.40f * view.theme.textScale + density, narrow, 0.6f)
    }

    @Test fun fontScaleOptionScalesLabelsAndIsSaved() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(100, Prefs.fontScalePercent(ctx))
        Prefs.setFontScalePercent(ctx, 500); assertEquals(Prefs.FONT_SCALE_MAX, Prefs.fontScalePercent(ctx))
        Prefs.setFontScalePercent(ctx, 120); assertEquals(120, Prefs.fontScalePercent(ctx))
        show(Layouts.english, numberLeft = true)
        view.fontScale = 1f; val base = letterTextSize("q")
        view.fontScale = 1.2f; assertEquals(base * 1.2f, letterTextSize("q"), 0.05f)
        view.fontScale = 0.7f; assertEquals(base * 0.7f, letterTextSize("q"), 0.05f)
    }

    @Test fun totalHeightStaysTheSameAsWithTheNumberRowOnTop() {
        for (layout in listOf(Layouts.koreanSingle, Layouts.korean, Layouts.english)) {
            for (frame in listOf(KeyboardFrame(), KeyboardFrame(rowGapDp = 2), KeyboardFrame(rowGapDp = 12))) {
                show(layout, numberLeft = false, frame = frame); val top = view.measuredHeight
                show(layout, numberLeft = true, frame = frame); val left = view.measuredHeight
                assertEquals("${layout.id} $frame", top.toDouble(), left.toDouble(), 2.0)
            }
        }
        // 글자 키는 그만큼 커진다 (줄 5개 → 4개)
        show(Layouts.koreanSingle, numberLeft = false); val before = inner("ㅂ").height()
        show(Layouts.koreanSingle, numberLeft = true); val after = inner("ㅂ").height()
        assertTrue("$before -> $after", after > before * 1.15f)
    }

    @Test fun numberRowSizeSettingBecomesTheStripWidthAndLeavesHeightAlone() {
        val usable = 1000 - 2 * 4 * density
        fun fraction(scale: Float): Float {
            show(Layouts.english, numberLeft = true, frame = KeyboardFrame(numberRowScale = scale))
            val d = view.keyRects().filter { it.first.label in digits }.map { it.second }
            return (d.maxOf { it.right } - d.minOf { it.left }) / usable
        }
        assertEquals(0.19f, fraction(0.8f), 0.01f)
        assertEquals(0.285f, fraction(1.2f), 0.01f)
        assertEquals(0.095f, fraction(0.4f), 0.01f)
        show(Layouts.english, numberLeft = true, frame = KeyboardFrame(numberRowScale = 0.4f)); val h1 = view.measuredHeight
        show(Layouts.english, numberLeft = true, frame = KeyboardFrame(numberRowScale = 1.2f)); val h2 = view.measuredHeight
        assertEquals(h1.toDouble(), h2.toDouble(), 1.0)
    }

    @Test fun numberToLetterGapBecomesTheHorizontalGapBetweenStripAndLetters() {
        for ((layout, label) in listOf(Layouts.koreanSingle to "ㅂ", Layouts.korean to "ㅂ", Layouts.english to "q")) {
            fun gap(g: Int): Float {
                show(layout, numberLeft = true, frame = KeyboardFrame(numberGapDp = g))
                val digit = inner("6")                      // 띠 오른쪽 열
                val letter = inner(label)
                return (letter.left - digit.right) / density
            }
            assertEquals(layout.id, 6f, gap(6), 0.1f)
            assertEquals(layout.id, 0f, gap(0), 0.1f)
            assertEquals(layout.id, 20f, gap(20), 0.1f)
            // 세로 높이와는 상관없다
            show(layout, numberLeft = true, frame = KeyboardFrame(numberGapDp = 0)); val a = view.measuredHeight
            show(layout, numberLeft = true, frame = KeyboardFrame(numberGapDp = 30)); val b = view.measuredHeight
            assertEquals(layout.id, a.toDouble(), b.toDouble(), 1.0)
        }
        // 숫자를 위에 둘 때는 예전처럼 세로 여백
        show(Layouts.english, numberLeft = false, frame = KeyboardFrame(numberGapDp = 20))
        val gapY = (inner("q").top - inner("1").bottom) / density
        assertEquals(20f, gapY, 0.1f)
    }

    @Test fun turningItOffRestoresTheTopNumberRow() {
        show(Layouts.english, numberLeft = false)
        val d = view.keyRects().filter { it.first.label in digits }.map { it.second }
        assertEquals(1, d.map { it.top.toInt() }.toSet().size)                                    // 한 줄
        assertTrue(d.all { it.bottom <= rect("q").top + 1f })                                      // 글자 줄 위
    }

    @Test fun stripColumnsReadOneToFiveThenSixToZero() {
        show(Layouts.english, numberLeft = true)
        val left = "12345".map { rect(it.toString()) }
        val right = "67890".map { rect(it.toString()) }
        assertTrue(left.all { it.left == left[0].left } && right.all { it.left == right[0].left })
        assertTrue(right[0].left > left[0].left)
        assertTrue((0 until 4).all { left[it].top < left[it + 1].top && right[it].top < right[it + 1].top })
        assertEquals(left[0].top, right[0].top, 0.5f)                 // 1과 6은 같은 높이
    }

    @Test fun tappingStripDigitsAndLettersStillWork() {
        keys.clear()
        fun tap(label: String) {
            val t = SystemClock.uptimeMillis()
            val r = rect(label)
            view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0))
            view.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, r.centerX(), r.centerY(), 0))
        }
        show(Layouts.english, numberLeft = true)
        listOf("3", "q", "0", "m").forEach { tap(it) }
        assertEquals(listOf("3", "q", "0", "m"), keys)
    }

    @Test fun rowTouchZoneShiftWorksOnTheShiftedRows() {
        show(Layouts.koreanSingle, numberLeft = true, frame = KeyboardFrame(hitShiftDp = listOf(0, 10, 0, 0)))
        val t = SystemClock.uptimeMillis()
        val b = rect("ㅂ")
        keys.clear()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, b.centerX(), b.bottom + 5 * density, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, b.centerX(), b.bottom + 5 * density, 0))
        assertEquals(listOf("ㅂ"), keys)                      // 첫 글자 줄(윗줄 설정)의 아래 경계를 10dp 내렸으니 아래 5dp도 ㅂ
    }
}

/** 숫자 키는 일반 키보다 조금 어둡게 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NumberKeyShadeTest {
    private class ColorRec : android.graphics.Canvas() {
        val fills = ArrayList<Pair<android.graphics.RectF, Int>>()
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: android.graphics.Paint) {
            fills += android.graphics.RectF(left, top, right, bottom) to paint.color
        }
        override fun drawRoundRect(rect: android.graphics.RectF, rx: Float, ry: Float, paint: android.graphics.Paint) {
            fills += android.graphics.RectF(rect) to paint.color
        }
    }

    private fun lum(c: Int): Double = 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)

    private fun colorOf(view: KeyboardView, label: String): Int {
        val r = view.keyInnerRects().first { it.first.label == label }.second
        val rec = ColorRec().also { view.draw(it) }
        return rec.fills.first { kotlin.math.abs(it.first.centerX() - r.centerX()) < 1f && kotlin.math.abs(it.first.centerY() - r.centerY()) < 1f }.second
    }

    private fun viewFor(layout: com.mbt.keyboard.layout.KeyboardLayout, numberLeft: Boolean): KeyboardView {
        val v = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        v.numberLeft = numberLeft
        v.keyboardLayout = layout
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }

    @Test fun digitKeysAreDarkerThanLetterKeysOnEveryLayoutAndPlacement() {
        for (numberLeft in listOf(true, false)) for ((layout, letter) in listOf(Layouts.koreanSingle to "ㅂ", Layouts.korean to "ㅂ", Layouts.english to "q")) {
            val v = viewFor(layout, numberLeft)
            val digit = colorOf(v, "5"); val key = colorOf(v, letter)
            assertEquals(v.theme.numberKey, digit)
            assertEquals(v.theme.key, key)
            assertTrue("${layout.id} left=$numberLeft digit=${lum(digit)} letter=${lum(key)}", lum(digit) < lum(key))
        }
        // 모아키 숫자 띠도
        val m = viewFor(Layouts.moaki, false)
        assertTrue(lum(colorOf(m, "5")) < lum(colorOf(m, "ㅂ")))
    }

    @Test fun numberKeyIsDarkerThanTheLetterKeyInEveryTheme() {
        fun lum(c: Int) = ((c shr 16) and 0xFF) * 0.3 + ((c shr 8) and 0xFF) * 0.59 + (c and 0xFF) * 0.11
        KeyboardThemes.all.forEach { n ->
            val t = n.theme
            val nk = t.numberKey
            if (t.numberKeyGlass != 0) return@forEach        // 글래스 테마는 색입힌 유리 (아래 별도 테스트)
            assertEquals("${n.id} 투명도 그대로", t.key ushr 24, nk ushr 24)
            if (lum(t.key) > 8) assertTrue("${n.id} ${lum(nk)} < ${lum(t.key)}", lum(nk) < lum(t.key))
            listOf(16, 8, 0).forEach { sh ->
                assertEquals("${n.id} $sh", ((t.key shr sh) and 0xFF) * (1f - KeyboardTheme.NUMBER_KEY_DARKEN), ((nk shr sh) and 0xFF).toFloat(), 1f)
            }
        }
    }

    @Test fun enterKeyKeepsItsColourButIsSofterInEveryTheme() {
        fun lum(c: Int) = ((c shr 16) and 0xFF) * 0.3 + ((c shr 8) and 0xFF) * 0.59 + (c and 0xFF) * 0.11
        KeyboardThemes.all.forEach { n ->
            val t = n.theme
            val e = t.enterKey
            if (t.enterKeyGlass != 0) return@forEach         // 글래스 테마는 색입힌 유리 (아래 별도 테스트)
            val base = if (t.frost) t.accent else t.specialKey
            val toward = if (t.frost) 0xFFFFFFFF.toInt() else t.key
            // 기준색과 글자 키 색(또는 흰색)의 딱 가운데
            listOf(24, 16, 8, 0).forEach { sh ->
                val expected = (((base shr sh) and 0xFF) + ((toward shr sh) and 0xFF)) / 2f
                assertEquals("${n.id} $sh", expected, ((e shr sh) and 0xFF).toFloat(), 1f)
            }
            if (!t.frost && (t.key ushr 24) == 0xFF && (t.specialKey ushr 24) == 0xFF) {
                assertTrue("${n.id} 엔터가 기능키보다 글자 키에 가깝다", Math.abs(lum(e) - lum(t.key)) <= Math.abs(lum(t.specialKey) - lum(t.key)) + 1)
            }
        }
    }

    @Test fun glassThemesTintTheNumberAndEnterKeysAsTranslucentColouredGlass() {
        fun over(fg: Int, bg: Int): Int {
            val a = ((fg ushr 24) and 0xFF) / 255f
            fun ch(sh: Int) = (((fg shr sh) and 0xFF) * a + ((bg shr sh) and 0xFF) * (1 - a)).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        fun lum(c: Int): Double {
            fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
        }
        fun contrast(a: Int, b: Int) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)
        KeyboardThemes.all.filter { it.theme.frost }.forEach { n ->
            val t = n.theme
            assertTrue("${n.id} 숫자키 유리색", t.numberKeyGlass != 0 && t.numberKey == t.numberKeyGlass)
            assertTrue("${n.id} 엔터 유리색", t.enterKeyGlass != 0 && t.enterKey == t.enterKeyGlass)
            assertTrue("${n.id} 숫자키 투명", (t.numberKey ushr 24) in 0x80..0xE0)       // 불투명이 아니라 투명도가 있다
            assertTrue("${n.id} 엔터 투명", (t.enterKey ushr 24) in 0x80..0xE8)
            t.backgroundStops.forEach { stop ->
                assertTrue("${n.id} 숫자키는 글자 키보다 어둡다", lum(over(t.numberKey, stop)) < lum(over(t.key, stop)))
                assertTrue("${n.id} 숫자 글자 대비 ${contrast(t.text, over(t.numberKey, stop))}", contrast(t.text, over(t.numberKey, stop)) >= 4.5)
                assertTrue("${n.id} 엔터 글자 대비 ${contrast(t.text, over(t.enterKey, stop))}", contrast(t.text, over(t.enterKey, stop)) >= 4.5)
            }
        }
    }

    @Test fun voiceKeyUsesTheSameBackgroundAsShiftAndEmojiKeys() {
        for (id in listOf("charcoal", "iosglass_holo")) {
            val theme = KeyboardThemes.byId(id)!!.theme
            val fills = HashMap<String, Int>()
            val v = KeyboardView(ApplicationProvider.getApplicationContext<Context>()).also { it.theme = theme; it.keyboardLayout = Layouts.english; it.numberLeft = true; it.fnTab = true }
            v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val rects = v.keyInnerRects()
            fun nameOf(l: Float, t: Float): String? = rects.firstOrNull { Math.abs(it.second.left - l) < 1f && Math.abs(it.second.top - t) < 1f }?.first?.type?.name
            v.draw(object : android.graphics.Canvas() {
                override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: android.graphics.Paint) {
                    nameOf(l, t)?.let { if (!fills.containsKey(it)) fills[it] = p.color }
                }
                override fun drawRoundRect(r: android.graphics.RectF, rx: Float, ry: Float, p: android.graphics.Paint) {
                    nameOf(r.left, r.top)?.let { if (!fills.containsKey(it)) fills[it] = p.color }
                }
                override fun saveLayer(l: Float, t: Float, r: Float, b: Float, p: android.graphics.Paint?): Int = save()
                override fun clipPath(path: android.graphics.Path): Boolean = true
            })
            if (id == "charcoal") {
                assertEquals(fills["SHIFT"], fills["VOICE"])
                assertEquals(fills["EMOJI"], fills["VOICE"])
                assertEquals(theme.specialKey, fills["VOICE"])
            }
        }
    }

    @Test fun numericKeypadDigitsAreUnchanged() {
        val v = viewFor(Layouts.numberPad, false)
        assertEquals(v.theme.key, colorOf(v, "5"))
    }
}
