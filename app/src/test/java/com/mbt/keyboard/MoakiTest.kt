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
    private val vowels = ArrayList<Char>()
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
            override fun onMoaVowel(vowel: Char) { vowels += vowel }
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

    @Test fun layoutMatchesTheSamsungMoakiArrangement() {
        fun row(i: Int) = Layouts.moaki.rows[i].map { it.label }
        assertEquals(listOf("~", "ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", "!"), row(1))
        assertEquals(listOf("^", "ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "?"), row(2))
        assertEquals(listOf(";", "ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "."), row(3))
        assertEquals(listOf("*", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "⌫"), row(4))
        assertEquals(2f, Layouts.moaki.rows[4].last().weight, 0.0001f)                  // 지우기는 두 칸 폭
        assertEquals(listOf(KeyType.VOICE, KeyType.EMOJI, KeyType.CHAR, KeyType.SPACE, KeyType.MOA_VOWEL, KeyType.ENTER), Layouts.moaki.rows[5].map { it.type })
        // 숫자는 맨 윗줄 (전체·복사·붙임 + 1~0)
        assertEquals(listOf("전체", "복사", "붙임") + "1234567890".map { it.toString() }, row(0))
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
        assertEquals('ㅑ', gesture(1.5f to 0f))                                  // 길게 밀기(예전 방식)도 유지
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

    @Test fun longDragMakesTheYVowels() {
        drag("ㅎ", dxKeys = 1.5f); assertEquals("ㅎ" to 'ㅑ', moa.last())
        drag("ㅎ", dxKeys = -1.5f); assertEquals("ㅎ" to 'ㅕ', moa.last())
        drag("ㅎ", dyKeys = -1.5f); assertEquals("ㅎ" to 'ㅛ', moa.last())
        drag("ㅎ", dyKeys = 1.5f); assertEquals("ㅎ" to 'ㅠ', moa.last())
    }

    @Test fun doubleConsonantRowAlsoTakesVowelDrags() {
        drag("ㄲ", dxKeys = 0.6f); assertEquals("ㄲ" to 'ㅏ', moa.last())
    }

    @Test fun horizontalDragOnAConsonantIsNeverACursorMove() {
        drag("ㅂ", dxKeys = 1.5f)
        assertTrue("cursor" !in keys)
    }

    @Test fun vowelKeyTapIsIAndSwipesGiveOtherVowels() {
        drag("ㅡㅣ"); assertEquals('ㅣ', vowels.last())
        drag("ㅡㅣ", dxKeys = -0.5f); assertEquals('ㅡ', vowels.last())
        drag("ㅡㅣ", dxKeys = -1.5f); assertEquals('ㅓ', vowels.last())
        drag("ㅡㅣ", dxKeys = 0.5f); assertEquals('ㅏ', vowels.last())
        drag("ㅡㅣ", dyKeys = -0.5f); assertEquals('ㅗ', vowels.last())
        drag("ㅡㅣ", dyKeys = 0.5f); assertEquals('ㅜ', vowels.last())
    }

    @Test fun sideSymbolKeysAreOrdinaryKeys() {
        drag("!"); drag("?"); drag("~")
        assertEquals(listOf("!", "?", "~"), keys)
    }

    // ---- 모음 안내 팝업 / 키 높이

    private class Rec : android.graphics.Canvas() {
        val texts = ArrayList<String>()
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) { texts += text }
    }

    private fun drawnTexts() = Rec().also { view.draw(it) }.texts

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
            com.mbt.keyboard.ime.KeyboardFrame(rowGapDp = 14, numberGapDp = 3, numberRowScale = 1.1f),
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
                is Char -> if (s == 'ㅣ') (c.combineWithI() ?: c.input(s)) else c.input(s)
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

    @Test fun iAfterAVowelMakesAeEAndCompounds() {
        assertEquals("개", typeMoa('ㄱ', 'ㅏ', 'ㅣ'))
        assertEquals("게", typeMoa('ㄱ', 'ㅓ', 'ㅣ'))
        assertEquals("걔", typeMoa('ㄱ', 'ㅑ', 'ㅣ'))
        assertEquals("계", typeMoa('ㄱ', 'ㅕ', 'ㅣ'))
        assertEquals("괘", typeMoa('ㄱ', 'ㅗ', 'ㅏ', 'ㅣ'))                  // ㅘ + ㅣ → ㅙ
        assertEquals("괴", typeMoa('ㄱ', 'ㅗ', 'ㅣ'))
        assertEquals("기", typeMoa('ㄱ', 'ㅣ'))
        assertEquals("의", typeMoa('ㅇ', 'ㅡ', 'ㅣ'))
    }

    @Test fun combineWithINeedsAVowelToCombine() {
        val c = HangulComposer()
        assertNull(c.combineWithI())                                         // 비어 있음
        c.input('ㄱ'); assertNull(c.combineWithI())                          // 자음뿐
        c.input('ㅗ'); assertNull(c.combineWithI())                          // ㅗ는 따로 ㅣ를 입력(ㅚ)
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
