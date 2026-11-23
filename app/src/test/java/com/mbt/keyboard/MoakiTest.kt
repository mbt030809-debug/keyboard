package com.mbt.keyboard

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.hangul.HangulComposer
import com.mbt.keyboard.ime.KeyboardView
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 모아키 자판: 배열, 자음 밀기로 모음 입력, 모음 키, 설정 선택 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoakiTest {
    private lateinit var view: KeyboardView
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val density get() = view.resources.displayMetrics.density
    private val moa = ArrayList<Pair<String, Char>>()
    private val keys = ArrayList<String>()
    private var downTime = 0L

    @Before fun setUp() {
        view = KeyboardView(ctx)
        view.keyboardLayout = Layouts.moaki
        view.listener = object : KeyboardView.Listener {
            override fun onKey(key: Key) { keys += key.label }
            override fun onText(text: String) = Unit
            override fun onSwipeSpace() = Unit
            override fun onCursorMove(steps: Int) { keys += "cursor" }
            override fun onCursorMoveEnd() = Unit
            override fun onShiftPress(down: Boolean) = Unit
            override fun onEnterLongPress() = Unit
            override fun onMoaki(key: Key, vowel: Char) { moa += key.label to vowel }
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun rect(label: String) = view.keyRects().first { it.first.label == label }.second

    private fun drag(label: String, dxKeys: Float = 0f, dyKeys: Float = 0f) {
        downTime = SystemClock.uptimeMillis()
        val r = rect(label)
        val x = r.centerX(); val y = r.centerY()
        val ex = x + dxKeys * r.width(); val ey = y + dyKeys * r.width()
        fun ev(a: Int, px: Float, py: Float) = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), a, px, py, 0)
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, x, y))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, (x + ex) / 2, (y + ey) / 2))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, ex, ey))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, ex, ey))
    }

    @Test fun layoutHasFiveRowsSideDigitsAndNoVowelKey() {
        fun row(i: Int) = Layouts.moaki.rows[i].map { it.label }
        assertEquals(5, Layouts.moaki.rows.size)
        assertEquals(listOf("ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", "!"), row(0))
        assertEquals(listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "?"), row(1))
        assertEquals(listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "."), row(2))
        assertEquals(listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "⌫"), row(3))
        assertEquals(2f, Layouts.moaki.rows[3].last().weight, 0.0001f)
        assertEquals(listOf("전체", "복사", "붙임", "🎤", "😊", ",", "", "⏎"), row(4))
        // 숫자는 왼쪽 띠 (2열): 1 2 / 3 4 / 5 6 / 7 8 / 9 0
        assertEquals("1234567890".map { it.toString() }, Layouts.moaki.sideKeys.map { it.label })
        // ~ ^ ; : 는 쌍자음 키를 꾹 누르면 나온다
        val dbl = Layouts.moaki.rows[0].take(4).associate { it.label to it.hints }
        assertEquals(mapOf("ㅃ" to listOf("~"), "ㅉ" to listOf("^"), "ㄸ" to listOf(";"), "ㄲ" to listOf(":")), dbl)
        // ㅡㅣ 모음 키는 없다
        assertTrue(Layouts.moaki.rows.flatten().none { it.label == "ㅡㅣ" })
    }

    @Test fun sideDigitsSitLeftOfTheConsonantsAndSpanFourRowHeights() {
        val digits = view.keyRects().filter { it.first.label in "1234567890".map { c -> c.toString() } }.map { it.second }
        val firstConsonant = rect("ㅂ")
        assertEquals(10, digits.size)
        digits.forEach { assertTrue(it.right <= firstConsonant.left + 1f) }                 // 모두 자음 키 왼쪽
        val top = rect("ㅃ").top; val bottom = rect("전체").top                              // 글자 4줄이 차지하는 높이
        assertEquals(top, digits.minOf { it.top }, 1f)
        assertEquals(bottom, digits.maxOf { it.bottom }, 1f)
        // 같은 높이를 5칸으로 나눈다
        val h = digits.map { it.height() }
        h.forEach { assertEquals(h[0], it, 0.5f) }
        assertEquals((bottom - top) / 5f, h[0], 1f)
        // 하단 줄은 띠 아래에서 화면 전체 폭
        assertTrue(rect("전체").left < digits.minOf { it.right })
    }

    @Test fun sideDigitStripDefaultWidthIsNineteenPercent() {
        val digits = view.keyRects().filter { it.first.label in "1234567890".map { c -> c.toString() } }.map { it.second }
        val stripW = digits.maxOf { it.right } - digits.minOf { it.left }
        val usable = view.width - 2 * 4 * density
        assertEquals(0.19f, stripW / usable, 0.01f)                           // 기본 크기: 줄 폭의 19%
    }

    @Test fun rowTouchZoneShiftAlsoWorksOnMoaki() {
        fun tapAt(x: Float, y: Float): String? {
            keys.clear(); moa.clear()
            downTime = SystemClock.uptimeMillis()
            view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
            view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, x, y, 0))
            return keys.firstOrNull()
        }
        val b = rect("ㅂ"); val m = rect("ㅁ")
        view.frame = com.mbt.keyboard.ime.KeyboardFrame(); relayout()
        assertEquals("ㅁ", tapAt(m.centerX(), m.top + 2 * density))                                          // 기본(0): 경계 바로 아래는 ㅁ 줄
        view.frame = com.mbt.keyboard.ime.KeyboardFrame(hitShiftDp = listOf(0, 10, 0, 0)); relayout()
        // ㅂ 줄의 아래 경계를 10dp 아래로: ㅂ 줄 아래 5dp 지점이 이제 ㅂ 줄로 읽힌다
        assertEquals("ㅂ", tapAt(b.centerX(), b.bottom + 5 * density))
        assertEquals("ㅁ", tapAt(m.centerX(), m.centerY()))                                                       // 깊은 곳은 그대로 ㅁ
        // 맨 아래 줄 경계(ㅋ 줄 아래)도 따로
        view.frame = com.mbt.keyboard.ime.KeyboardFrame(hitShiftDp = listOf(0, 0, 0, 12)); relayout()
        val k = rect("ㅋ")
        assertEquals("ㅋ", tapAt(k.centerX(), k.bottom + 6 * density))
        // 보이는 키는 그대로
        val before = view.keyInnerRects().map { it.second.top }
        view.frame = com.mbt.keyboard.ime.KeyboardFrame(hitShiftDp = listOf(12, 12, 12, 12)); relayout()
        assertEquals(before, view.keyInnerRects().map { it.second.top })
    }

    private fun relayout() {
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun numberRowHeightSettingBecomesTheDigitStripWidthOnMoaki() {
        val usable = view.width - 2 * 4 * density
        fun stripFraction(scale: Float): Float {
            view.frame = com.mbt.keyboard.ime.KeyboardFrame(numberRowScale = scale); relayout()
            val d = view.keyRects().filter { it.first.label in "1234567890".map { c -> c.toString() } }.map { it.second }
            return (d.maxOf { it.right } - d.minOf { it.left }) / usable
        }
        assertEquals(0.19f, stripFraction(0.8f), 0.01f)       // 기본 80% = 줄 폭의 19% (처음 크기 그대로)
        assertEquals(0.285f, stripFraction(1.2f), 0.01f)      // 120%면 더 넓게
        assertEquals(0.095f, stripFraction(0.4f), 0.01f)      // 40%면 더 좁게
        // 키보드 높이는 이 설정과 상관없다
        val h1 = measuredHeightWith(Layouts.moaki, com.mbt.keyboard.ime.KeyboardFrame(numberRowScale = 0.4f))
        val h2 = measuredHeightWith(Layouts.moaki, com.mbt.keyboard.ime.KeyboardFrame(numberRowScale = 1.2f))
        assertEquals(h1.toDouble(), h2.toDouble(), 1.0)
    }

    @Test fun selectAllCopyPasteHaveTheSameSizeAsTheDigitKeys() {
        val digit = view.keyInnerRects().first { it.first.label == "5" }.second
        listOf("전체", "복사", "붙임").forEach { label ->
            val k = view.keyInnerRects().first { it.first.label == label }.second
            assertEquals("$label 가로", digit.width(), k.width(), 1.0f)
            assertEquals("$label 세로", digit.height(), k.height(), 1.0f)
        }
        // 숫자 띠 폭을 바꿔도 따라간다
        view.frame = com.mbt.keyboard.ime.KeyboardFrame(numberRowScale = 1.2f); relayout()
        val d2 = view.keyInnerRects().first { it.first.label == "5" }.second
        val k2 = view.keyInnerRects().first { it.first.label == "복사" }.second
        assertEquals(d2.width(), k2.width(), 1.0f)
        // 아래 줄의 나머지 키는 남는 폭을 쓴다 (스페이스가 여전히 가장 넓다)
        val space = view.keyRects().first { it.first.type == KeyType.SPACE }.second
        assertTrue(space.width() > view.keyRects().first { it.first.label == "복사" }.second.width())
    }

    @Test fun tappingASideDigitTypesIt() {
        fun tapKey(label: String) {
            downTime = SystemClock.uptimeMillis()
            val r = rect(label)
            view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0))
            view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, r.centerX(), r.centerY(), 0))
        }
        keys.clear()
        "1572089".forEach { tapKey(it.toString()) }
        assertEquals("1572089".map { it.toString() }, keys)
    }

    @Test fun consonantSymbolHintsMatchTheSingleVowelLayout() {
        val single = Layouts.koreanSingle.rows.flatten().filter { it.type == KeyType.CHAR }.associate { it.label to it.hints }
        "ㅂㅈㄷㄱㅅㅁㄴㅇㄹㅎㅋㅌㅊㅍ".forEach { c ->
            val k = Layouts.moaki.rows.flatten().first { it.label == c.toString() }
            assertEquals("$c", single[c.toString()], k.hints)
        }
    }

    @Test fun dragDirectionPicksTheVowelAndTapStaysAConsonant() {
        drag("ㄱ", dxKeys = 0.6f); assertEquals("ㄱ" to 'ㅏ', moa.last())
        drag("ㄱ", dxKeys = -0.6f); assertEquals("ㄱ" to 'ㅓ', moa.last())
        drag("ㄱ", dyKeys = -0.6f); assertEquals("ㄱ" to 'ㅗ', moa.last())
        drag("ㄱ", dyKeys = 0.6f); assertEquals("ㄱ" to 'ㅜ', moa.last())
        assertEquals(4, moa.size)
        assertTrue(keys.isEmpty())                                  // 밀었을 때는 자음만 따로 입력되지 않는다
        drag("ㄴ")                                                   // 탭
        assertEquals(listOf("ㄴ"), keys)
        assertEquals(4, moa.size)
    }

    /** [label] 키를 누른 채 점들을 차례로 지나 마지막 점에서 뗀다 (점은 키 폭 단위, 사이를 잘게 채운다) */
    private fun dragPath(label: String, vararg pts: Pair<Float, Float>) {
        downTime = SystemClock.uptimeMillis()
        val r = rect(label)
        val x0 = r.centerX(); val y0 = r.centerY()
        fun ev(a: Int, px: Float, py: Float) = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), a, px, py, 0)
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, x0, y0))
        var px = 0f; var py = 0f
        for ((tx, ty) in pts) {
            for (i in 1..6) {
                val fx = px + (tx - px) * i / 6f; val fy = py + (ty - py) * i / 6f
                view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, x0 + fx * r.width(), y0 + fy * r.width()))
            }
            px = tx; py = ty
        }
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, x0 + px * r.width(), y0 + py * r.width()))
    }

    private fun gesture(vararg pts: Pair<Float, Float>): Char? {
        moa.clear(); dragPath("ㄱ", *pts); return moa.lastOrNull()?.second
    }

    @Test fun compoundVowelGesturesFollowTheMoakiTable() {
        // ㅘ ↑→ / ㅙ ↑→← / ㅝ ↓← / ㅞ ↓←→ / ㅚ ↑↓ / ㅟ ↓↑
        assertEquals('ㅘ', gesture(0f to -0.5f, 0.5f to -0.5f))
        assertEquals('ㅙ', gesture(0f to -0.5f, 0.5f to -0.5f, 0f to -0.5f))
        assertEquals('ㅝ', gesture(0f to 0.5f, -0.5f to 0.5f))
        assertEquals('ㅞ', gesture(0f to 0.5f, -0.5f to 0.5f, 0f to 0.5f))
        assertEquals('ㅚ', gesture(0f to -0.5f, 0f to 0f))
        assertEquals('ㅟ', gesture(0f to 0.5f, 0f to 0f))
    }

    @Test fun roundTripGesturesGiveYVowelsAndAeE() {
        assertEquals('ㅑ', gesture(0.5f to 0f, 0f to 0f, 0.5f to 0f))          // →←→
        assertEquals('ㅕ', gesture(-0.5f to 0f, 0f to 0f, -0.5f to 0f))        // ←→←
        assertEquals('ㅛ', gesture(0f to -0.5f, 0f to 0f, 0f to -0.5f))        // ↑↓↑
        assertEquals('ㅠ', gesture(0f to 0.5f, 0f to 0f, 0f to 0.5f))          // ↓↑↓
        assertEquals('ㅐ', gesture(0.5f to 0f, 0f to 0f))                       // →←
        assertEquals('ㅔ', gesture(-0.5f to 0f, 0f to 0f))                      // ←→
        assertEquals('ㅒ', gesture(0.5f to 0f, 0f to 0f, 0.5f to 0f, 0f to 0f)) // →←→←
        assertEquals('ㅖ', gesture(-0.5f to 0f, 0f to 0f, -0.5f to 0f, 0f to 0f))
    }

    @Test fun euThenIMakesUiAndSingleStrokesStillWork() {
        assertEquals('ㅢ', gesture(0.45f to 0.45f, 0f to 0f))                   // 5시로 갔다가 11시 방향으로 돌아옴 (ㅡ → ㅣ)
        assertEquals('ㅏ', gesture(0.5f to 0f))
        assertEquals('ㅜ', gesture(0f to 0.5f))
    }

    @Test fun diagonalAfterABasicDirectionIsEuOrIAsBefore() {
        assertEquals('ㅣ', gesture(0f to -0.5f, 0.4f to -0.9f))           // ↑ 다음 ↗
        assertEquals('ㅡ', gesture(0f to -0.5f, 0.4f to -0.1f))           // ↑ 다음 ↘
        assertEquals('ㅡ', gesture(0f to 0.5f, -0.4f to 0.9f))            // ↓ 다음 ↙
        assertEquals('ㅣ', gesture(0f to 0.5f, -0.4f to 0.1f))            // ↓ 다음 ↖
        assertEquals('ㅣ', gesture(0.5f to 0f, 0.9f to -0.4f))            // → 다음 ↗
        assertEquals('ㅡ', gesture(-0.5f to 0f, -0.9f to 0.4f))           // ← 다음 ↙
        // 되돌아오기·겹모음은 그대로
        assertEquals('ㅚ', gesture(0f to -0.5f, 0f to 0f))
        assertEquals('ㅟ', gesture(0f to 0.5f, 0f to 0f))
        assertEquals('ㅘ', gesture(0f to -0.5f, 0.5f to -0.5f))
    }

    @Test fun smallWigglesDoNotSplitStrokes() {
        assertEquals('ㅏ', gesture(0.2f to 0.05f, 0.5f to -0.03f, 0.55f to 0.05f))   // 약간 흔들려도 한 획
    }

    @Test fun movingCenterLabelShowsTheCompoundSyllable() {
        downTime = SystemClock.uptimeMillis()
        val r = rect("ㄱ"); val x0 = r.centerX(); val y0 = r.centerY()
        fun ev(a: Int, px: Float, py: Float) = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), a, px, py, 0)
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, x0, y0))
        for (i in 1..6) view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, x0, y0 - i * 0.5f / 6 * r.width()))
        for (i in 1..6) view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, x0 + i * 0.5f / 6 * r.width(), y0 - 0.5f * r.width()))
        assertTrue("과" in drawnTexts())                                             // ㄱ + ㅘ
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, x0 + 0.5f * r.width(), y0 - 0.5f * r.width()))
    }

    @Test fun upperDiagonalsAreIAndLowerDiagonalsAreEu() {
        drag("ㄱ", dxKeys = -0.6f, dyKeys = -0.6f); assertEquals("ㄱ" to 'ㅣ', moa.last())     // 11시
        drag("ㄱ", dxKeys = 0.6f, dyKeys = -0.6f); assertEquals("ㄱ" to 'ㅣ', moa.last())      // 1시
        drag("ㄱ", dxKeys = 0.6f, dyKeys = 0.6f); assertEquals("ㄱ" to 'ㅡ', moa.last())       // 5시
        drag("ㄱ", dxKeys = -0.6f, dyKeys = 0.6f); assertEquals("ㄱ" to 'ㅡ', moa.last())      // 7시
    }

    @Test fun cardinalsStillGiveTheirVowelsWithEightWaySectors() {
        drag("ㄴ", dxKeys = 0.6f, dyKeys = 0.15f); assertEquals("ㄴ" to 'ㅏ', moa.last())     // 3시 근처
        drag("ㄴ", dxKeys = -0.6f, dyKeys = -0.15f); assertEquals("ㄴ" to 'ㅓ', moa.last())   // 9시 근처
        drag("ㄴ", dxKeys = 0.15f, dyKeys = -0.6f); assertEquals("ㄴ" to 'ㅗ', moa.last())    // 12시 근처
        drag("ㄴ", dxKeys = -0.15f, dyKeys = 0.6f); assertEquals("ㄴ" to 'ㅜ', moa.last())    // 6시 근처
    }

    @Test fun aSingleLongDragIsStillJustTheBasicVowel() {
        // 예전의 "길게 밀기 = ㅑㅕㅛㅠ"는 없앴다: 길게 밀어도 ㅏ ㅓ ㅗ ㅜ
        drag("ㅎ", dxKeys = 1.5f); assertEquals("ㅎ" to 'ㅏ', moa.last())
        drag("ㅎ", dxKeys = -1.5f); assertEquals("ㅎ" to 'ㅓ', moa.last())
        drag("ㅎ", dyKeys = -1.5f); assertEquals("ㅎ" to 'ㅗ', moa.last())
        drag("ㅎ", dyKeys = 1.5f); assertEquals("ㅎ" to 'ㅜ', moa.last())
    }

    @Test fun doubleConsonantRowAlsoTakesVowelDrags() {
        drag("ㄲ", dxKeys = 0.6f); assertEquals("ㄲ" to 'ㅏ', moa.last())
    }

    @Test fun horizontalDragOnAConsonantIsNeverACursorMove() {
        drag("ㅂ", dxKeys = 1.5f)
        assertTrue("cursor" !in keys)
    }

    @Test fun sideSymbolKeysAreOrdinaryKeys() {
        drag("!"); drag("?"); drag(".")
        assertEquals(listOf("!", "?", "."), keys)
    }

    // ---- 모음 안내 팝업 / 키 높이

    private class Rec : android.graphics.Canvas() {
        val texts = ArrayList<String>()
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) { texts += text }
    }

    private fun drawnTexts() = Rec().also { view.draw(it) }.texts

    private class PosRec : android.graphics.Canvas() {
        val pos = HashMap<String, Pair<Float, Float>>()
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) { pos[text] = x to y }
    }

    @Test fun pressingAConsonantShowsTheSixVowelsAroundItImmediately() {
        val before = drawnTexts().toList()
        downTime = SystemClock.uptimeMillis()
        val r = rect("ㄱ")
        view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0))
        val shown = drawnTexts() - before.toSet()
        // 안내 칸: ㅏ ㅓ ㅗ ㅜ ㅡ ㅣ (일부 글자는 키 위에도 있어 개수 대신 새로 나타난 글자로 확인)
        listOf("ㅏ", "ㅓ", "ㅗ", "ㅜ", "ㅡ", "ㅣ").forEach { v ->
            assertTrue("$v 안내가 떠야 한다: $shown", drawnTexts().count { it == v } > before.count { it == v })
        }
        view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, r.centerX(), r.centerY(), 0))
        assertEquals(before.count { it == "ㅏ" }, drawnTexts().count { it == "ㅏ" })            // 떼면 사라진다
    }

    @Test fun draggingHighlightsTheChosenSyllableInTheGuide() {
        downTime = SystemClock.uptimeMillis()
        val r = rect("ㄱ")
        view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0))
        view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 5, MotionEvent.ACTION_MOVE, r.centerX() + 0.6f * r.width(), r.centerY(), 0))
        assertTrue("가" in drawnTexts())                                    // 고른 칸에 완성될 글자
        view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, r.centerX() + 0.6f * r.width(), r.centerY(), 0))
    }

    private fun measuredHeightWith(layout: com.mbt.keyboard.layout.KeyboardLayout, frame: com.mbt.keyboard.ime.KeyboardFrame): Int {
        view.keyboardLayout = layout
        view.frame = frame
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view.measuredHeight
    }

    @Test fun moakiKeyboardHasTheSameTotalHeightAsTheSingleVowelKeyboard() {
        listOf(
            com.mbt.keyboard.ime.KeyboardFrame(),
            com.mbt.keyboard.ime.KeyboardFrame(rowGapDp = 0, numberGapDp = 0),
            com.mbt.keyboard.ime.KeyboardFrame(rowGapDp = 14, numberGapDp = 3),
            com.mbt.keyboard.ime.KeyboardFrame(topDp = 10, bottomDp = 2, keyGapDp = 2),
        ).forEach { f ->
            val single = measuredHeightWith(Layouts.koreanSingle, f)
            val moaki = measuredHeightWith(Layouts.moaki, f)
            assertEquals("$f", single.toDouble(), moaki.toDouble(), 2.0)
        }
    }

    // ---- 조합 (엔진)

    private fun typeMoa(vararg steps: Any): String {
        val c = HangulComposer(); val sb = StringBuilder()
        for (s in steps) {
            val out = when (s) {
                is Char -> c.input(s)
                else -> error("bad")
            }
            sb.append(out.committed)
        }
        return sb.toString() + c.composing
    }

    @Test fun compoundVowelJamoComposeIntoSyllables() {
        assertEquals("과", typeMoa('ㄱ', 'ㅘ'))
        assertEquals("괘", typeMoa('ㄱ', 'ㅙ'))
        assertEquals("괴", typeMoa('ㄱ', 'ㅚ'))
        assertEquals("권", typeMoa('ㄱ', 'ㅝ', 'ㄴ'))
        assertEquals("귀", typeMoa('ㄱ', 'ㅟ'))
        assertEquals("의", typeMoa('ㅇ', 'ㅢ'))
        assertEquals("걔", typeMoa('ㄱ', 'ㅒ'))
        assertEquals("궈나", typeMoa('ㄱ', 'ㅝ', 'ㄴ', 'ㅏ'))              // 받침이 다음 글자 초성으로
    }

    @Test fun consonantPlusVowelComposesSyllablesAndMovesFinals() {
        assertEquals("가", typeMoa('ㄱ', 'ㅏ'))
        assertEquals("한글", typeMoa('ㅎ', 'ㅏ', 'ㄴ', 'ㄱ', 'ㅡ', 'ㄹ'))
        assertEquals("가나", typeMoa('ㄱ', 'ㅏ', 'ㄴ', 'ㅏ'))                 // 받침이 다음 글자 초성으로
    }

    // ---- 선택 / 저장

    @Test fun moakiCanBeChosenAsTheKoreanLayoutAndIsKnownById() {
        Prefs.setKoreanLayout(ctx, Layouts.MOA)
        assertEquals(Layouts.MOA, Prefs.koreanLayout(ctx))
        assertEquals(Layouts.moaki, Layouts.byId(Layouts.MOA))
        assertTrue(Layouts.moaki.hangul && Layouts.moaki.moaki)
        assertFalse(Layouts.korean.moaki || Layouts.koreanSingle.moaki || Layouts.english.moaki)
    }
}
