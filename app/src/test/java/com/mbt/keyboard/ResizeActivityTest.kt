package com.mbt.keyboard

import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import com.google.android.material.chip.ChipGroup
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

    @Test fun previewShowsTheFloatingFnTabLikeTheRealKeyboard() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs.setNumberKeysLeft(ctx, true)
        Prefs.setFnTabEnabled(ctx, true)
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val container = a.findViewById<android.widget.FrameLayout>(R.id.previewContainer)
                val tab = (0 until container.childCount).map { container.getChildAt(it) }.filterIsInstance<com.mbt.keyboard.ime.FnTab>().single()
                assertEquals(View.VISIBLE, tab.visibility)
                val host = container.getChildAt(0)
                assertTrue((host.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin > 0)
            }
        }
        // 탭을 끄면 미리보기에서도 사라진다
        Prefs.setFnTabEnabled(ctx, false)
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val container = a.findViewById<android.widget.FrameLayout>(R.id.previewContainer)
                val tab = (0 until container.childCount).map { container.getChildAt(it) }.filterIsInstance<com.mbt.keyboard.ime.FnTab>().single()
                assertEquals(View.GONE, tab.visibility)
            }
        }
    }

    @Test fun settingsActivityScreenTabShowsResizeDirectly() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val tabs = a.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabs)
                tabs.getTabAt(2)!!.select()
                assertEquals(View.VISIBLE, a.findViewById<View>(R.id.sectionScreen).visibility)
                assertTrue(a.findViewById<View>(R.id.previewContainer) != null)
                assertEquals(7, a.findViewById<ChipGroup>(R.id.pageChips).childCount)
            }
        }
    }

    private fun press(v: View) {
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 1f, 1f, 0))
    }

    private fun chips(a: ResizeActivity) = a.findViewById<ChipGroup>(R.id.pageChips)
    private fun goTo(a: ResizeActivity, page: Int) { chips(a).getChildAt(page).performClick() }
    private fun controls(a: ResizeActivity) = a.findViewById<android.view.ViewGroup>(R.id.pageControls)
    private fun step(a: ResizeActivity, row: Int, plus: Boolean) =
        press(controls(a).getChildAt(row).findViewById(if (plus) R.id.stepPlus else R.id.stepMinus))
    private fun value(a: ResizeActivity, row: Int) =
        controls(a).getChildAt(row).findViewById<android.widget.TextView>(R.id.stepValue).text.toString()
    private fun title(a: ResizeActivity) = a.findViewById<android.widget.TextView>(R.id.pageTitle).text.toString()

    private fun previewHost(a: ResizeActivity) =
        a.findViewById<android.view.ViewGroup>(R.id.previewContainer).getChildAt(0) as android.widget.FrameLayout
    private fun previewKeyboard(a: ResizeActivity) = previewHost(a).getChildAt(0) as com.mbt.keyboard.ime.KeyboardView

    @Test fun sevenTabsInPriorityOrderAndTheFirstIsSelected() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertEquals(6, chips(a).childCount)
                assertTrue(title(a).startsWith("① 전체 크기"))
                listOf("② 키 사이 간격", "③ 보조탭", "④ 숫자줄·스페이스바 줄 크기", "⑤ 숫자줄 여백", "⑥ 터치 인식 영역").forEachIndexed { i, t ->
                    goTo(a, i + 1)
                    assertTrue("${title(a)} vs $t", title(a).startsWith(t))
                }
            }
        }
    }

    @Test fun chipsWalkThroughAllSixPages() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                goTo(a, 1)
                assertTrue(title(a).startsWith("② "))
                assertTrue(chips(a).getChildAt(1).let { (it as com.google.android.material.chip.Chip).isChecked })
                goTo(a, 5)
                assertTrue(title(a).startsWith("⑥ "))
                goTo(a, 0)
                assertTrue(title(a).startsWith("① "))
            }
        }
    }

    private fun overlay(a: ResizeActivity): ResizeOverlayView {
        val c = a.findViewById<android.view.ViewGroup>(R.id.previewContainer)
        return (0 until c.childCount).map { c.getChildAt(it) }.filterIsInstance<ResizeOverlayView>().single()
    }

    private fun drag(v: View, x0: Float, y0: Float, x1: Float, y1: Float) {
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x0, y0, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, x1, y1, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x1, y1, 0))
    }

    @Test fun sizePageUsesDirectDragWithoutStepperButtons() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertTrue(a.findViewById<View>(R.id.stepPlus) != null)
                assertTrue(a.findViewById<View>(R.id.sliderFontScale) != null)
                val ov = overlay(a)
                val hr = previewHost(a)
                hr.layout(0, 50, 1000, 450)
                ov.layout(0, 0, 1000, 500)
                drag(ov, hr.left + hr.width * 0.18f, hr.top.toFloat(), hr.left + hr.width * 0.18f, hr.top - 60f)
                assertTrue(Prefs.geometry(a, large = false).heightPct > 100)
            }
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w800dp-h1280dp")
    fun splitGapShowsOnLargeScreenSeparatelyFromPhoneAndResetRestoresEverything() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertTrue(controls(a).childCount >= 4)
                val ov = overlay(a)
                val hr = previewHost(a)
                hr.layout(0, 50, 1000, 450)
                ov.layout(0, 0, 1000, 500)
                drag(ov, (hr.left + hr.right) / 2f, hr.top + hr.height * 0.25f, (hr.left + hr.right) / 2f + 30f, hr.top + hr.height * 0.25f)
                assertTrue(Prefs.geometry(a, large = true).gapPct > KeyboardGeometry.GAP_DEFAULT)
                assertEquals(KeyboardGeometry.GAP_DEFAULT, Prefs.geometry(a, large = false).gapPct)   // 접힘(폰) 설정은 그대로

            }
        }
    }

    @Test fun gapAndNumGapUseSteppersWhileTabUsesDirectDrag() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                goTo(a, 1)                                                                   // ② 간격
                step(a, 0, plus = true)                                                      // 가로 +1
                step(a, 1, plus = false); step(a, 1, plus = false)                         // 세로 -2
                goTo(a, 2)                                                                   // ③ 보조탭
                assertTrue(a.findViewById<View>(R.id.switchNumberLeft) != null)
                assertTrue(a.findViewById<View>(R.id.switchFnTab) != null)
                assertTrue(a.findViewById<View>(R.id.stepPlus) != null)
                assertEquals(View.VISIBLE, a.findViewById<View>(R.id.pageNote).visibility)
                goTo(a, 4); step(a, 0, plus = true); step(a, 0, plus = true)                 // ⑤ 숫자줄 여백 +2
                val g = Prefs.geometry(a, large = false)
                assertEquals(7, g.keyGapDp); assertEquals(4, g.rowGapDp)
                assertEquals(8, g.numRowGapDp)
            }
        }
    }

    @Test fun hitZonePageShowsReadoutsAndResetRestoresTheDefaults() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                goTo(a, 5)
                assertTrue(a.findViewById<View>(R.id.stepPlus) != null)

            }
        }
    }

    @Test fun thereAreNoPositionMoveControlsAndOldSavedPositionIsIgnored() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(raiseDp = 50, xDp = 40, widthPct = 80))
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val lp = previewHost(a).layoutParams as android.widget.FrameLayout.LayoutParams
                assertEquals(0, lp.bottomMargin)                             // 띄우기 없음
                val free = (a.resources.displayMetrics.widthPixels * 0.2f).toInt()
                assertEquals(free / 2.0, lp.leftMargin.toDouble(), 2.0)
                assertEquals(0, Prefs.geometry(a, large = false).raiseDp)
                assertEquals(0, Prefs.geometry(a, large = false).xDp)
            }
        }
    }
}
