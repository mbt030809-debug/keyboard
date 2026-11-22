package com.mbt.keyboard

import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResizeActivityTest {
    @Test fun opensWithoutCrashing() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a -> assertTrue(a.findViewById<View>(R.id.previewContainer) != null) }
        }
    }

    @Test fun settingsActivityOpensAndResizeButtonLaunchesResize() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a -> a.findViewById<View>(R.id.btnResize).performClick() }
        }
    }

    private fun tap(a: ResizeActivity, id: Int) {
        val v = a.findViewById<View>(id)
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 1f, 1f, 0))
    }

    @Test fun everyAdjustButtonWorksAndIsSavedOnPhone() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                tap(a, R.id.widthMinus); tap(a, R.id.heightPlus); tap(a, R.id.moveUp)
                tap(a, R.id.moveLeft)      // 가로 95%라 좌우 이동 가능
                val g = Prefs.geometry(a, large = false)
                assertEquals(95, g.widthPct); assertEquals(105, g.heightPct)
                assertEquals(10, g.raiseDp); assertEquals(40, g.xPct)
                // 폰(접힘)에서는 갈라진 정도 행이 숨겨진다
                assertEquals(View.GONE, a.findViewById<View>(R.id.gapRow).visibility)
            }
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w800dp-h1280dp")
    fun gapRowShowsAndSavesOnLargeScreenSeparatelyFromPhone() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertEquals(View.VISIBLE, a.findViewById<View>(R.id.gapRow).visibility)
                tap(a, R.id.gapPlus); tap(a, R.id.gapPlus)
                assertEquals(22, Prefs.geometry(a, large = true).gapPct)
                // 접힘(폰) 설정은 그대로
                assertEquals(KeyboardGeometry.GAP_DEFAULT, Prefs.geometry(a, large = false).gapPct)
                a.findViewById<View>(R.id.resetButton).performClick()
                assertEquals(KeyboardGeometry(), Prefs.geometry(a, large = true))
            }
        }
    }
}
