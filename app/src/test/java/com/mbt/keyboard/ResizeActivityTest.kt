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
                assertEquals(7, chips(a).childCount)
                assertTrue(title(a).startsWith("① 전체 크기"))
                listOf("② 상하좌우 여백", "③ 키 사이 간격", "④ 보조탭", "⑤ 숫자줄·스페이스바 줄 크기", "⑥ 숫자줄 여백", "⑦ 터치 인식 영역").forEachIndexed { i, t ->
                    goTo(a, i + 1)
                    assertTrue("${title(a)} vs $t", title(a).startsWith(t))
                }
            }
        }
    }

    @Test fun nextAndPreviousButtonsWalkThroughThePagesAndStopAtTheEnds() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertEquals(false, a.findViewById<View>(R.id.prevButton).isEnabled)
                a.findViewById<View>(R.id.nextButton).performClick()
                assertTrue(title(a).startsWith("② "))
                assertTrue(chips(a).getChildAt(1).let { (it as com.google.android.material.chip.Chip).isChecked })
                a.findViewById<View>(R.id.prevButton).performClick()
                assertTrue(title(a).startsWith("① "))
                repeat(10) { a.findViewById<View>(R.id.nextButton).performClick() }
                assertTrue(title(a).startsWith("⑦ "))
                assertEquals(false, a.findViewById<View>(R.id.nextButton).isEnabled)
            }
        }
    }

    @Test fun sizePageChangesWidthAndHeightByOnePercentAndIsSaved() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertTrue(controls(a).childCount >= 2)                 // 가로·세로 + 글자 크기 슬라이더
                step(a, 0, plus = false); step(a, 0, plus = false); step(a, 0, plus = false)
                step(a, 1, plus = false); step(a, 1, plus = false); step(a, 1, plus = true)
                val g = Prefs.geometry(a, large = false)
                assertEquals(97, g.widthPct); assertEquals(99, g.heightPct)
                assertEquals("97%", value(a, 0))
            }
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w800dp-h1280dp")
    fun splitGapShowsOnLargeScreenSeparatelyFromPhoneAndResetRestoresEverything() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertTrue(controls(a).childCount >= 3)
                step(a, 2, plus = true); step(a, 2, plus = true)
                assertEquals(20, Prefs.geometry(a, large = true).gapPct)
                assertEquals(KeyboardGeometry.GAP_DEFAULT, Prefs.geometry(a, large = false).gapPct)   // 접힘(폰) 설정은 그대로
                a.findViewById<View>(R.id.resetButton).performClick()
                assertEquals(KeyboardGeometry(), Prefs.geometry(a, large = true))
            }
        }
    }

    @Test fun paddingPageAdjustsFourSidesAndKeepsTheKeyboardTotalHeight() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val before = previewKeyboard(a).let { it.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); it.measuredHeight }
                goTo(a, 1)
                assertEquals(4, controls(a).childCount)
                step(a, 0, plus = true); step(a, 0, plus = true)                            // 위쪽 +2
                step(a, 1, plus = false)                                                      // 아래쪽 -1
                step(a, 2, plus = true)                                                       // 왼쪽 +1
                step(a, 3, plus = false); step(a, 3, plus = false)                          // 오른쪽 -2
                val g = Prefs.geometry(a, large = false)
                assertEquals(6, g.padTopDp); assertEquals(3, g.padBottomDp); assertEquals(5, g.padLeftDp); assertEquals(2, g.padRightDp)
                assertEquals("6dp", value(a, 0))
                val kb = previewKeyboard(a)
                kb.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                assertEquals(before.toDouble(), kb.measuredHeight.toDouble(), 1.0)
            }
        }
    }

    @Test fun gapNumberRowAndTabPagesAreSaved() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                goTo(a, 2)                                                                   // ③ 간격
                step(a, 0, plus = true)                                                      // 가로 +1
                step(a, 1, plus = false); step(a, 1, plus = false)                         // 세로 -2
                goTo(a, 3)                                                                   // ④ 보조탭 (스위치 2개+설명 2개 + 높이 + 여백)
                assertTrue(a.findViewById<View>(R.id.switchNumberLeft) != null)
                assertTrue(a.findViewById<View>(R.id.switchFnTab) != null)
                step(a, 4, plus = false)                                                     // 탭 높이 -5%
                step(a, 5, plus = true); step(a, 5, plus = true)                             // 탭 여백 +2
                assertEquals("95%", value(a, 4))
                assertEquals("6dp", value(a, 5))
                assertEquals(View.VISIBLE, a.findViewById<View>(R.id.pageNote).visibility)    // 보조탭 안내
                goTo(a, 4); step(a, 0, plus = true)                                          // ⑤ 숫자줄 크기 +5%
                assertEquals(2, controls(a).childCount)                                      // 숫자줄 크기 + 스페이스바 줄 높이
                step(a, 1, plus = true); step(a, 1, plus = true); step(a, 1, plus = false)   // 스페이스바 줄 +5%
                goTo(a, 4); step(a, 0, plus = true); step(a, 0, plus = true)                 // ⑥ 숫자줄 여백 +2
                val g = Prefs.geometry(a, large = false)
                assertEquals(7, g.keyGapDp); assertEquals(4, g.rowGapDp)
                assertEquals(85, g.numHeightPct); assertEquals(8, g.numRowGapDp)
                assertEquals(105, g.spaceRowPct)
                assertEquals(95, g.fnTabHeightPct)
                assertEquals(6, g.fnTabPadDp)
            }
        }
    }

    @Test fun hitZonePageHasFourSteppersAndResetRestoresTheDefaults() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                goTo(a, 6)
                assertEquals(4, controls(a).childCount)
                step(a, 0, plus = true); step(a, 3, plus = true)
                assertEquals(KeyboardGeometry.HIT_DEFAULT + 1, Prefs.geometry(a, large = false).hit0Dp)
                goTo(a, 5); step(a, 0, plus = true)
                a.findViewById<View>(R.id.resetButton).performClick()
                assertEquals(KeyboardGeometry(), Prefs.geometry(a, large = false))
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
                // 가운데 정렬: 남는 폭의 절반이 왼쪽 여백
                val free = (a.resources.displayMetrics.widthPixels * 0.2f).toInt()
                assertEquals(free / 2.0, lp.leftMargin.toDouble(), 2.0)
                // 저장된 이동값도 0으로 정리된다
                step(a, 0, plus = true)
                assertEquals(0, Prefs.geometry(a, large = false).raiseDp)
                assertEquals(0, Prefs.geometry(a, large = false).xDp)
            }
        }
    }
}
