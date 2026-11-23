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
