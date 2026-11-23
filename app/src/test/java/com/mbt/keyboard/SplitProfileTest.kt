package com.mbt.keyboard

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.ime.KeyboardService
import com.mbt.keyboard.ime.KeyboardView
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.Prefs
import com.mbt.keyboard.settings.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 갈라진 정도를 접힘/펼침 × 세로/가로 별로 따로 저장하고, 접힘 화면의 가로 모드에서도 갈라지게 하는 기능 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitProfileTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun splitAppliesRules() {
        // 펼침: 스위치를 따름 (방향 무관)
        assertTrue(ScreenProfile.splitApplies(true, false, true, 18))
        assertTrue(ScreenProfile.splitApplies(true, true, true, 18))
        assertFalse(ScreenProfile.splitApplies(true, true, false, 18))
        // 접힘: 세로는 절대 안 나눔, 가로는 갈라진 정도가 0보다 클 때만
        assertFalse(ScreenProfile.splitApplies(false, false, true, 30))
        assertFalse(ScreenProfile.splitApplies(false, true, true, 0))
        assertTrue(ScreenProfile.splitApplies(false, true, false, 10))
    }

    @Test fun gapIsStoredSeparatelyPerProfile() {
        Prefs.setGeometry(ctx, large = true, KeyboardGeometry(gapPct = 20))                       // 펼침·세로
        Prefs.setGeometry(ctx, large = true, KeyboardGeometry(gapPct = 30), landscape = true)    // 펼침·가로
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(gapPct = 12), landscape = true)   // 접힘·가로
        assertEquals(20, Prefs.geometry(ctx, true, false).gapPct)
        assertEquals(30, Prefs.geometry(ctx, true, true).gapPct)
        assertEquals(12, Prefs.geometry(ctx, false, true).gapPct)
        // 다른 설정(가로 길이 등)은 방향과 상관없이 함께 쓴다
        Prefs.setGeometry(ctx, large = true, KeyboardGeometry(widthPct = 80, gapPct = 30), landscape = true)
        assertEquals(80, Prefs.geometry(ctx, true, false).widthPct)
        assertEquals(20, Prefs.geometry(ctx, true, false).gapPct)                                 // 세로 틈은 그대로
    }

    @Test fun numberRowSettingsAreSavedAndLoaded() {
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(keyGapDp = 3, rowGapDp = 9, numHeightPct = 110))
        val g = Prefs.geometry(ctx, large = false)
        assertEquals(3, g.keyGapDp); assertEquals(9, g.rowGapDp); assertEquals(110, g.numHeightPct)
        assertEquals(80, Prefs.geometry(ctx, large = true).numHeightPct)     // 펼침 화면은 따로
    }

    @Test fun hitShiftsAreSavedPerProfile() {
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(hit0Dp = 9, hit1Dp = 6, hit2Dp = -2, hit3Dp = 0))
        assertEquals(listOf(9, 6, -2, 0), Prefs.geometry(ctx, large = false).hitShifts)
        assertEquals(listOf(3, 3, 3, 3), Prefs.geometry(ctx, large = true).hitShifts)
    }

    @Test fun landscapeDefaultsKeepOldBehaviour() {
        // 가로 값을 저장한 적이 없으면: 펼침은 세로 값 그대로, 접힘은 0(안 나눔)
        Prefs.setGeometry(ctx, large = true, KeyboardGeometry(gapPct = 25))
        assertEquals(25, Prefs.geometry(ctx, true, true).gapPct)
        assertEquals(0, Prefs.geometry(ctx, false, true).gapPct)
        assertEquals(KeyboardGeometry.GAP_DEFAULT, Prefs.geometry(ctx, false, false).gapPct)
    }

    private fun keyboardOf(service: KeyboardService): KeyboardView {
        val root = service.onCreateInputView() as FrameLayout
        return (root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView
    }

    private fun splitOf(): Boolean {
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        return keyboardOf(service).split
    }

    @Test @Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
    fun foldedLandscapeIsNotSplitUntilAGapIsSet() {
        assertFalse(splitOf())
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(gapPct = 14), landscape = true)
        assertTrue(splitOf())
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h800dp-port")
    fun foldedPortraitIsNeverSplit() {
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(gapPct = 14), landscape = true)
        assertFalse(splitOf())
    }

    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
    fun unfoldedLandscapeUsesItsOwnGap() {
        Prefs.setGeometry(ctx, large = true, KeyboardGeometry(gapPct = 10))
        Prefs.setGeometry(ctx, large = true, KeyboardGeometry(gapPct = 33), landscape = true)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val kb = keyboardOf(service)
        val root = kb.parent.parent as FrameLayout
        root.measure(View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, 1600, root.measuredHeight)
        assertTrue(kb.split)
        assertEquals(0.33f, kb.splitGapFraction, 0.001f)
    }

    private fun tap(a: ResizeActivity, id: Int) {
        val v = a.findViewById<View>(id)
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 1f, 1f, 0))
    }

    @Test @Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
    fun resizeScreenOnFoldedLandscapeEditsOnlyTheLandscapeGap() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertEquals(View.VISIBLE, a.findViewById<View>(R.id.gapRow).visibility)   // 접힘이어도 가로면 보인다
                repeat(8) { tap(a, R.id.gapPlus) }
                assertEquals(8, Prefs.geometry(a, large = false, landscape = true).gapPct)
                assertEquals(KeyboardGeometry.GAP_DEFAULT, Prefs.geometry(a, large = false, landscape = false).gapPct)
            }
        }
    }
}
