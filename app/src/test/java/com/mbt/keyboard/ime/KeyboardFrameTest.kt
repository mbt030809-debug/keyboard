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
        val rects = view.keyInnerRects().map { it.second }       // 눈에 보이는 키 기준
        assertEquals(4 * density, rects.minOf { it.left }, 0.01f)
        assertEquals(widthPx - 4 * density, rects.maxOf { it.right }, 0.01f)
        assertEquals(4 * density, rects.minOf { it.top }, 0.01f)
    }

    @Test fun outerMarginIsExactlyThePaddingNoMatterWhatTheKeyGapIs() {
        for (gap in listOf(0, 6, 20)) {
            view.frame = KeyboardFrame(leftDp = 7, topDp = 3, rightDp = 9, bottomDp = 5, keyGapDp = gap, rowGapDp = gap); relayout()
            val r = view.keyInnerRects().map { it.second }
            assertEquals("gap=$gap", 7 * density, r.minOf { it.left }, 0.05f)
            assertEquals("gap=$gap", widthPx - 9 * density, r.maxOf { it.right }, 0.05f)
            assertEquals("gap=$gap", 3 * density, r.minOf { it.top }, 0.05f)
            assertEquals("gap=$gap", view.measuredHeight - 5 * density, r.maxOf { it.bottom }, 1.5f)
        }
    }

    @Test fun totalSizeNeverChangesWhateverElseIsAdjusted() {
        val base = view.measuredHeight
        for (f in listOf(
            KeyboardFrame(topDp = 20, bottomDp = 0), KeyboardFrame(leftDp = 30, rightDp = 0), KeyboardFrame(keyGapDp = 0, rowGapDp = 0),
            KeyboardFrame(keyGapDp = 14, rowGapDp = 14), KeyboardFrame(numberRowScale = 1.3f), KeyboardFrame(numberRowScale = 0.5f),
            KeyboardFrame(numberGapDp = 0), KeyboardFrame(numberGapDp = 30), KeyboardFrame(topDp = 10, rowGapDp = 12, numberGapDp = 3),
        )) {
            view.frame = f; relayout()
            assertEquals(f.toString(), base.toDouble(), view.measuredHeight.toDouble(), 1.0)
        }
    }

    @Test fun eachSideCanBeSetIndependently() {
        view.frame = KeyboardFrame(leftDp = 20, topDp = 0, rightDp = 2, bottomDp = 9, keyGapDp = 6)
        relayout()
        val rects = view.keyInnerRects().map { it.second }
        assertEquals(20 * density, rects.minOf { it.left }, 0.01f)
        assertEquals(widthPx - 2 * density, rects.maxOf { it.right }, 0.01f)
        assertEquals(0f, rects.minOf { it.top }, 0.01f)
        // 아래쪽 여백은 키보드 전체 높이에 반영 (마지막 줄 아래 9dp)
        assertEquals(rects.maxOf { it.bottom } + 9 * density, view.measuredHeight.toFloat(), 1f)
    }

    @Test fun verticalPaddingsKeepTheKeyboardHeightAndShrinkTheKeysInstead() {
        val base = view.measuredHeight
        val keyH = view.keyInnerRects().first { it.first.label == "q" }.second.height()
        view.frame = KeyboardFrame(topDp = 14, bottomDp = 14)           // 위·아래 각 +10dp
        relayout()
        assertEquals(base.toDouble(), view.measuredHeight.toDouble(), 1.0)
        assertTrue(view.keyInnerRects().first { it.first.label == "q" }.second.height() < keyH)
        view.frame = KeyboardFrame(topDp = 0, bottomDp = 0)             // 위·아래 각 -4dp
        relayout()
        assertEquals(base.toDouble(), view.measuredHeight.toDouble(), 1.0)
        assertTrue(view.keyInnerRects().first { it.first.label == "q" }.second.height() > keyH)
    }

    @Test fun zeroPaddingLetsKeysTouchTheEdges() {
        view.frame = KeyboardFrame(0, 0, 0, 0, 6)
        relayout()
        val rects = view.keyInnerRects().map { it.second }
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

    private fun renderToBitmap(): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(widthPx, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        return bmp
    }

    @Test fun languageIndicatorDrawsForKoreanEnglishAndSplitWithoutProblems() {
        for (layout in Layouts.all) {
            view.keyboardLayout = layout
            relayout()
            assertTrue(renderToBitmap().width == widthPx)
            view.split = true
            relayout()
            assertTrue(renderToBitmap().width == widthPx)
            view.split = false
        }
    }

    /** 어떤 글자를 어떤 색·굵기로 그렸는지 기록하는 캔버스 (가상 환경은 픽셀을 실제로 그리지 않는다) */
    private class RecordingCanvas : android.graphics.Canvas() {
        data class Text(val text: String, val color: Int, val bold: Boolean, val size: Float)
        val texts = ArrayList<Text>()
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) {
            texts += Text(text, paint.color, paint.isFakeBoldText, paint.textSize)
        }
    }

    private fun drawnTexts(): List<RecordingCanvas.Text> {
        val c = RecordingCanvas()
        view.draw(c)
        return c.texts
    }

    @Test fun spaceBarShowsOnlyTheLanguageTokensNotTheLayoutName() {
        view.keyboardLayout = Layouts.koreanSingle; relayout()
        val texts = drawnTexts().map { it.text }
        assertTrue("한" in texts && "EN" in texts)
        assertTrue("예전 글자(자판 이름)는 더 이상 없다", "한글 단모음" !in texts && "English" !in texts && "한글" !in texts)
    }

    @Test fun activeLanguageTokenIsHighlightedInAccentColor() {
        val accent = view.theme.accent
        view.keyboardLayout = Layouts.koreanSingle; relayout()
        var t = drawnTexts()
        assertEquals(accent, t.first { it.text == "한" }.color)
        assertTrue(t.first { it.text == "EN" }.color != accent)         // 쓰지 않는 언어는 흐리게

        view.keyboardLayout = Layouts.english; relayout()
        t = drawnTexts()
        assertEquals(accent, t.first { it.text == "EN" }.color)
        assertTrue(t.first { it.text == "한" }.color != accent)
    }

    @Test fun languageIndicatorAndVoiceIconAreOneDpSmaller() {
        view.keyboardLayout = Layouts.koreanSingle; relayout()
        val texts = drawnTexts()
        // "한 EN": 스페이스바 안쪽 높이의 34%에서 1dp 줄임
        val space = view.keyInnerRects().first { it.first.type == com.mbt.keyboard.layout.KeyType.SPACE }.second
        val expectedLang = space.height() * 0.34f * view.theme.textScale - 1 * density
        assertEquals(expectedLang, texts.first { it.text == "한" }.size, 0.01f)
        assertEquals(expectedLang, texts.first { it.text == "EN" }.size, 0.01f)
        // 🎤 및 전복붙: 기존 크기 대비 70%로 축소
        val voice = view.keyRects().first { it.first.type == com.mbt.keyboard.layout.KeyType.VOICE }.second
        val expectedVoice = (minOf(voice.height(), view.theme.keyHeightDp * density) * 0.34f * view.theme.textScale + density) * 0.70f
        assertEquals(expectedVoice, texts.first { it.text == "🎤" }.size, 0.01f)
        view.numberLeft = true; relayout()
        val edit = view.keyRects().first { it.first.label == "전체" }.second
        val textsLeft = drawnTexts()
        val editRef = minOf(edit.height(), view.theme.keyHeightDp * density, edit.width() * 1.35f)
        assertEquals((editRef * 0.34f * view.theme.textScale + density) * 0.70f, textsLeft.first { it.text == "전체" }.size, 0.01f)
    }

    @Test fun backspaceIsDrawnOnePointTwoFiveTimesALetterKeyOnEveryLayout() {
        for ((layout, letter) in listOf(Layouts.koreanSingle to "ㅋ", Layouts.korean to "ㅋ", Layouts.english to "z")) {
            view.keyboardLayout = layout
            relayout()
            val rects = view.keyRects()
            val backInner = view.keyInnerRects().first { it.first.type == com.mbt.keyboard.layout.KeyType.BACKSPACE }.second
            val back = rects.first { it.first.type == com.mbt.keyboard.layout.KeyType.BACKSPACE }.second
            val key = rects.first { it.first.label == letter }.second
            // 키 폭 비율 = 가로 비중 비율 (예: 1.25 / 글자 키 비중)
            val expected = (if (layout == Layouts.koreanSingle) 1.3f else 1.5f) / rects.first { it.first.label == letter }.first.weight
            assertEquals(layout.id, expected, back.width() / key.width(), 0.01f)
            // 지우기는 줄의 오른쪽 끝
            assertEquals(layout.id, (widthPx - 4 * density), backInner.right, 0.5f)
        }
    }
}

/** 영어 갈라진 자판의 안쪽 키 복제 (Gboard식) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitDuplicateTest {
    private lateinit var view: KeyboardView
    private val widthPx = 1400

    @Before fun setUp() {
        view = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        view.keyboardLayout = Layouts.english
        view.split = true
        relayout()
    }

    private fun relayout() {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun count(label: String) = view.keyRects().count { it.first.label == label }

    @Test fun offByDefaultNoKeyIsDuplicated() {
        "qwertyuiopasdfghjklzxcvbnm".forEach { assertEquals("$it", 1, count(it.toString())) }
    }

    @Test fun onShowsTheOtherHandsFirstKeyAtEachInnerEdge() {
        view.splitDuplicate = true; relayout()
        // 왼쪽 안쪽: y h n / 오른쪽 안쪽: t g b
        "ythgnb".forEach { assertEquals("$it", 2, count(it.toString())) }
        "qwerasdfzxcvuiopjklm".forEach { assertEquals("$it", 1, count(it.toString())) }
        val mid = widthPx / 2f
        val y = view.keyRects().filter { it.first.label == "y" }.map { it.second.centerX() }
        assertTrue(y.min() < mid && y.max() > mid)                 // 하나는 왼쪽 묶음, 하나는 오른쪽 묶음
    }

    @Test fun duplicatedKeyTypesTheSameLetter() {
        view.splitDuplicate = true; relayout()
        val fired = ArrayList<String>()
        view.listener = object : KeyboardView.Listener {
            override fun onKey(key: com.mbt.keyboard.layout.Key) { fired += key.output }
            override fun onText(text: String) = Unit
            override fun onSwipeSpace() = Unit
            override fun onCursorMove(steps: Int) = Unit
            override fun onCursorMoveEnd() = Unit
            override fun onShiftPress(down: Boolean) = Unit
            override fun onEnterLongPress() = Unit
        }
        val leftY = view.keyRects().filter { it.first.label == "y" }.minBy { it.second.centerX() }.second
        val t = android.os.SystemClock.uptimeMillis()
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, leftY.centerX(), leftY.centerY(), 0))
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10, android.view.MotionEvent.ACTION_UP, leftY.centerX(), leftY.centerY(), 0))
        assertEquals(listOf("y"), fired)
    }

    @Test fun neverAppliesToKoreanOrUnsplitKeyboards() {
        view.splitDuplicate = true
        view.keyboardLayout = Layouts.koreanSingle; relayout()
        assertEquals(1, count("ㅂ"))
        view.keyboardLayout = Layouts.english; view.split = false; relayout()
        assertEquals(1, count("y"))
    }

    @Test fun duplicateKeysAreInsideTheScreenAndDoNotOverlap() {
        view.splitDuplicate = true; relayout()
        view.keyRects().groupBy { it.second.top }.values.forEach { row ->
            val sorted = row.map { it.second }.sortedBy { it.left }
            sorted.zipWithNext { a, b -> assertTrue(a.right <= b.left + 0.5f) }
            assertTrue(sorted.first().left >= 0f && sorted.last().right <= widthPx)
        }
    }
}

/** 줄 사이 세로 여백(키 크기는 그대로)과 숫자 줄 높이(일반 키 대비) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RowGapFrameTest {
    private lateinit var view: KeyboardView
    private val density get() = view.resources.displayMetrics.density

    @Before fun setUp() {
        view = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        view.keyboardLayout = Layouts.koreanSingle
        relayout()
    }

    private fun relayout() {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun inner(label: String) = view.keyInnerRects().first { it.first.label == label }.second

    @Test fun widerRowGapShrinksTheKeysButKeepsTheTotalHeightAndOuterMargin() {
        val keyH = inner("ㅂ").height()
        val gapBefore = inner("ㅁ").top - inner("ㅂ").bottom          // ㅂ 줄과 ㅁ 줄 사이
        val heightBefore = view.measuredHeight
        assertEquals(6f, gapBefore / density, 0.05f)
        view.frame = KeyboardFrame(rowGapDp = 14); relayout()
        assertTrue(inner("ㅂ").height() < keyH)                         // 키가 그만큼 작아지고
        assertEquals(14f, (inner("ㅁ").top - inner("ㅂ").bottom) / density, 0.05f)
        assertEquals(heightBefore.toDouble(), view.measuredHeight.toDouble(), 1.0)   // 전체 높이는 그대로
        assertEquals(4f, inner("ㅂ").top / density, 0.05f)                // 바깥 여백도 그대로 (위 여백 설정값)
        view.frame = KeyboardFrame(rowGapDp = 0); relayout()
        assertTrue(inner("ㅂ").height() > keyH)
        assertEquals(0f, (inner("ㅁ").top - inner("ㅂ").bottom) / density, 0.05f)
        assertEquals(heightBefore.toDouble(), view.measuredHeight.toDouble(), 1.0)
    }

    @Test fun gapBetweenNumberRowAndLetterRowIsSeparate() {
        view.numberLeft = true; relayout()
        fun numGap() = (inner("ㅂ").left - inner("6").right) / density
        fun letterGap() = (inner("ㅁ").top - inner("ㅂ").bottom) / density
        assertEquals(6f, numGap(), 0.05f)
        val before = view.measuredHeight
        view.frame = KeyboardFrame(numberGapDp = 20); relayout()
        assertEquals(20f, numGap(), 0.05f)
        assertEquals(6f, letterGap(), 0.05f)                            // 글자 줄 사이는 그대로
        assertEquals(before.toDouble(), view.measuredHeight.toDouble(), 1.0)
        view.frame = KeyboardFrame(rowGapDp = 12, numberGapDp = 0); relayout()
        assertEquals(0f, numGap(), 0.05f)
        assertEquals(12f, letterGap(), 0.05f)
        assertEquals(4f, inner("1").top / density, 0.1f)
    }

    @Test fun horizontalGapStaysIndependentOfTheRowGap() {
        fun hgap() = (inner("ㅈ").left - inner("ㅂ").right) / density
        assertEquals(6f, hgap(), 0.05f)
        view.frame = KeyboardFrame(rowGapDp = 20); relayout()
        assertEquals(6f, hgap(), 0.05f)
        view.frame = KeyboardFrame(keyGapDp = 2, rowGapDp = 20); relayout()
        assertEquals(2f, hgap(), 0.05f)
    }

    @Test fun topNumberTabHeightMatchesLeftDigitKeyHeight() {
        view.numberLeft = false; relayout()
        val topTabH = view.fnKeyHeightPx()
        view.numberLeft = true; view.fnTab = true; relayout()
        val leftTabH = view.fnKeyHeightPx()
        assertEquals(leftTabH, topTabH, 1f)
    }

    @Test fun keypadsKeepFourEqualRows() {
        view.keyboardLayout = Layouts.numberPad
        view.frame = KeyboardFrame(numberRowScale = 0.4f, rowGapDp = 12); relayout()
        val tops = view.keyInnerRects().groupBy { Math.round(it.second.top) }.keys.sorted().map { it.toFloat() }
        val pitches = tops.zipWithNext { a, b -> b - a }
        assertTrue(pitches.all { kotlin.math.abs(it - pitches[0]) < 1f })
    }

    @Test fun touchInTheGapBetweenRowsStillHitsTheNearestKey() {
        view.frame = KeyboardFrame(rowGapDp = 24); relayout()
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
        val a = inner("ㅂ"); val b = inner("ㅁ")
        val x = a.centerX(); val y = (a.bottom + b.top) / 2f             // 두 줄 사이 빈 곳
        val t = android.os.SystemClock.uptimeMillis()
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10, android.view.MotionEvent.ACTION_UP, x, y, 0))
        assertEquals(1, fired.size)
    }
}

/** 되돌렸을 때의 표시: 불투명한 ↶ 하나만, 글자는 없다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndoMarkTest {
    private lateinit var view: KeyboardView

    private class Rec : android.graphics.Canvas() {
        val texts = ArrayList<String>()
        val circleAlphas = ArrayList<Int>()
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) { texts += text }
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: android.graphics.Paint) {
            if (paint.style == android.graphics.Paint.Style.FILL) circleAlphas += android.graphics.Color.alpha(paint.color)
        }
    }

    @Before fun setUp() {
        view = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        view.keyboardLayout = Layouts.koreanSingle
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun draw(): Rec = Rec().also { view.draw(it) }

    @Test fun nothingIsShownByDefault() {
        assertTrue("↶" !in draw().texts)
    }

    @Test fun showsOnlyTheArrowOpaqueAndNoWords() {
        val before = draw().texts.toSet()
        view.showUndoMark()
        val r = draw()
        assertTrue("↶" in r.texts)
        assertEquals(setOf("↶"), r.texts.toSet() - before)                        // 새로 생긴 글자는 ↶ 뿐
        assertTrue(r.texts.none { it.contains("되돌") })
        assertTrue(r.circleAlphas.isNotEmpty() && r.circleAlphas.all { it == 255 })   // 불투명
    }
}

/** 감성 테마(그라데이션 배경·유리 키)가 실제로 그려지는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlassThemeDrawTest {
    private class Rec : android.graphics.Canvas() {
        var gradientRects = 0
        var borders = 0
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint) {
            if (paint.shader != null) gradientRects++
        }
        override fun drawPath(path: android.graphics.Path, paint: android.graphics.Paint) {
            if (paint.shader != null) gradientRects++
        }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: android.graphics.Paint) {
            if (paint.style == android.graphics.Paint.Style.STROKE) borders++
        }
    }

    private fun viewWith(id: String): KeyboardView {
        val v = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        v.keyboardLayout = Layouts.koreanSingle
        v.theme = KeyboardThemes.byId(id)!!.theme
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }

    @Test fun gradientThemePaintsAShaderBackgroundAndKeyBorders() {
        val r = Rec(); viewWith("midnightglass").draw(r)
        assertEquals(1, r.gradientRects)
        assertTrue(r.borders > 20)
    }

    @Test fun plainThemeHasNeitherGradientNorBorders() {
        val r = Rec(); viewWith("cream").draw(r)
        assertEquals(0, r.gradientRects)
        assertEquals(0, r.borders)
    }
}

/** 줄별 인식 영역 이동: 눈에 보이는 키는 그대로, 터치가 어느 줄로 읽히는지만 바뀐다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HitShiftTest {
    private lateinit var view: KeyboardView
    private val density get() = view.resources.displayMetrics.density
    private val fired = ArrayList<String>()

    @Before fun setUp() {
        view = KeyboardView(ApplicationProvider.getApplicationContext<Context>())
        view.keyboardLayout = Layouts.koreanSingle
        view.listener = object : KeyboardView.Listener {
            override fun onKey(key: com.mbt.keyboard.layout.Key) { fired += key.label }
            override fun onText(text: String) = Unit
            override fun onSwipeSpace() = Unit
            override fun onCursorMove(steps: Int) = Unit
            override fun onCursorMoveEnd() = Unit
            override fun onShiftPress(down: Boolean) = Unit
            override fun onEnterLongPress() = Unit
        }
        relayout()
    }

    private fun relayout() {
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun tapAt(x: Float, y: Float): String? {
        fired.clear()
        val t = android.os.SystemClock.uptimeMillis()
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 10, android.view.MotionEvent.ACTION_UP, x, y, 0))
        return fired.firstOrNull()
    }

    private fun rect(label: String) = view.keyRects().first { it.first.label == label }.second

    @Test fun withoutShiftTheBoundaryIsWhereTheRowsMeet() {
        val b = rect("ㅂ"); val m = rect("ㅁ")
        assertEquals("ㅂ", tapAt(b.centerX(), b.bottom - 2))
        assertEquals("ㅁ", tapAt(m.centerX(), m.top + 2))
    }

    @Test fun shiftingTheTopRowDownCatchesTouchesThatLandedLow() {
        view.frame = KeyboardFrame(hitShiftDp = listOf(0, 10, 0, 0)); relayout()
        val b = rect("ㅂ"); val m = rect("ㅁ")
        // ㅂ 줄 바로 아래(원래는 ㅁ 영역) 5dp 지점이 이제 ㅂ으로 읽힌다
        assertEquals("ㅂ", tapAt(b.centerX(), b.bottom + 5 * density))
        // 그 아래 깊은 곳은 여전히 ㅁ
        assertEquals("ㅁ", tapAt(m.centerX(), m.centerY()))
    }

    @Test fun fourRowMainLayoutKeepsTopLetterRowAtTopEdge() {
        view.numberLeft = false; relayout()
        assertEquals(4f, view.keyInnerRects().first { it.first.label == "ㅂ" }.second.top / density, 0.1f)
    }

    @Test fun eachRowHasItsOwnShiftAndNegativeShrinksTheRowAbove() {
        view.frame = KeyboardFrame(hitShiftDp = listOf(0, 0, 8, -6)); relayout()
        val m = rect("ㅁ"); val k = rect("ㅋ"); val b = rect("ㅂ")
        assertEquals("ㅁ", tapAt(m.centerX(), m.bottom + 4 * density))          // 둘째 줄은 아래로 8dp 더 넓다
        assertEquals("ㅂ", tapAt(b.centerX(), b.bottom - 2))                     // 첫째 줄은 그대로
        // 셋째 줄 아래 경계를 위로 6dp 옮김: 스페이스 줄이 위로 넓어진다
        val space = rect("😊")
        assertEquals("😊", tapAt(space.centerX(), space.top - 3 * density))
        assertEquals("ㅋ", tapAt(k.centerX(), k.centerY()))
    }

    @Test fun visibleKeysDoNotMoveWhenShiftChanges() {
        val before = view.keyInnerRects().map { it.second.top }
        view.frame = KeyboardFrame(hitShiftDp = listOf(12, 12, 12, 12)); relayout()
        assertEquals(before, view.keyInnerRects().map { it.second.top })
    }

    @Test fun extremeShiftsNeverReorderRows() {
        view.frame = KeyboardFrame(hitShiftDp = listOf(0, -8, 12, -8)); relayout()
        listOf("ㅂ", "ㅁ", "ㅋ", "😊").forEach { label ->
            val r = rect(label)
            assertEquals(label, label, tapAt(r.centerX(), r.centerY()))              // 키 한가운데는 항상 그 키
        }
    }

    @Test fun keypadsAreNotAffected() {
        view.keyboardLayout = Layouts.numberPad
        view.frame = KeyboardFrame(hitShiftDp = listOf(12, 12, 12, 12)); relayout()
        val r1 = view.keyRects().first { it.first.label == "1" }.second
        val r4 = view.keyRects().first { it.first.label == "4" }.second
        assertEquals("1", tapAt(r1.centerX(), r1.bottom - 2))
        assertEquals("4", tapAt(r4.centerX(), r4.top + 2))
    }
}

/** 맨 아래(스페이스바) 줄 키 높이 조절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpaceRowScaleTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun view(l: com.mbt.keyboard.layout.KeyboardLayout, f: KeyboardFrame, numberLeft: Boolean = false): KeyboardView {
        val v = KeyboardView(ctx).also { it.numberLeft = numberLeft; it.keyboardLayout = l; it.frame = f }
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }

    private fun h(v: KeyboardView, type: com.mbt.keyboard.layout.KeyType) = v.keyInnerRects().first { it.first.type == type }.second.height()
    private fun hq(v: KeyboardView, label: String) = v.keyInnerRects().first { it.first.label == label }.second.height()

    @Test fun spaceRowCanBeMadeTallerOrShorterWhileTheTotalHeightStays() {
        for ((layout, letter, nl) in listOf(Triple(Layouts.english, "q", false), Triple(Layouts.english, "q", true), Triple(Layouts.koreanSingle, "ㅂ", false))) {
            val base = view(layout, KeyboardFrame(), nl)
            val ratio0 = h(base, com.mbt.keyboard.layout.KeyType.SPACE) / hq(base, letter)
            assertEquals("${layout.id} 기본은 같은 높이", 1f, ratio0, 0.02f)
            for (scale in listOf(0.6f, 1.3f, 1.5f)) {
                val v = view(layout, KeyboardFrame(spaceRowScale = scale), nl)
                assertEquals("${layout.id} x$scale", scale, h(v, com.mbt.keyboard.layout.KeyType.SPACE) / hq(v, letter), 0.02f)
                assertEquals("${layout.id} 전체 높이 고정", base.measuredHeight.toDouble(), v.measuredHeight.toDouble(), 1.0)
            }
        }
    }

    @Test fun geometrySettingIsClampedSteppedAndSavedPerProfile() {
        val g = com.mbt.keyboard.settings.KeyboardGeometry()
        assertEquals(100, g.spaceRowPct)
        assertEquals(105, g.spaceRowBy(1).spaceRowPct)
        assertEquals(50, g.copy(spaceRowPct = 1).clamped().spaceRowPct)
        assertEquals(150, g.copy(spaceRowPct = 999).clamped().spaceRowPct)
        com.mbt.keyboard.settings.Prefs.setGeometry(ctx, large = true, g.copy(spaceRowPct = 130), landscape = false)
        com.mbt.keyboard.settings.Prefs.setGeometry(ctx, large = true, g.copy(spaceRowPct = 70), landscape = true)
        assertEquals(130, com.mbt.keyboard.settings.Prefs.geometry(ctx, large = true, landscape = false).spaceRowPct)
        assertEquals(70, com.mbt.keyboard.settings.Prefs.geometry(ctx, large = true, landscape = true).spaceRowPct)
        assertEquals(100, com.mbt.keyboard.settings.Prefs.geometry(ctx, large = false, landscape = false).spaceRowPct)
        assertEquals(1.3f, g.copy(spaceRowPct = 130).spaceRowScale, 0.001f)
    }
}
