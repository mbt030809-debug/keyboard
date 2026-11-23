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
                tap(a, R.id.moveLeft)      // 가로 99%: 남는 공간 안에서 왼쪽으로
                val g = Prefs.geometry(a, large = false)
                assertEquals(99, g.widthPct); assertEquals(101, g.heightPct)
                assertEquals(2, g.raiseDp); assertTrue(g.xDp < 0)
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
                assertEquals(20, Prefs.geometry(a, large = true).gapPct)
                // 접힘(폰) 설정은 그대로
                assertEquals(KeyboardGeometry.GAP_DEFAULT, Prefs.geometry(a, large = false).gapPct)
                a.findViewById<View>(R.id.resetButton).performClick()
                assertEquals(KeyboardGeometry(), Prefs.geometry(a, large = true))
            }
        }
    }

    @Test fun holdingAMoveButtonDoesNotPileUpValuesPastTheEdge() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                repeat(20) { tap(a, R.id.widthMinus) }      // 가로 최소(50%)로
                repeat(40) { tap(a, R.id.moveLeft) }          // 왼쪽 끝을 지나 계속 눌러도
                val atEdge = Prefs.geometry(a, large = false)
                tap(a, R.id.moveRight)                         // 한 번 반대로 누르면 바로 움직여야 한다
                val after = Prefs.geometry(a, large = false)
                assertTrue("xDp ${atEdge.xDp} -> ${after.xDp}", after.xDp > atEdge.xDp)
            }
        }
    }

    private fun previewLp(a: ResizeActivity): android.widget.FrameLayout.LayoutParams {
        val container = a.findViewById<android.view.ViewGroup>(R.id.previewContainer)
        return container.getChildAt(0).layoutParams as android.widget.FrameLayout.LayoutParams
    }

    @Test fun moveUpRaisesThePreviewEveryTime() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val density = a.resources.displayMetrics.density
                repeat(5) { i ->
                    tap(a, R.id.moveUp)
                    assertEquals("after ${i + 1} taps", (2 * (i + 1) * density).toInt(), previewLp(a).bottomMargin)
                }
                tap(a, R.id.moveDown)
                assertEquals((8 * density).toInt(), previewLp(a).bottomMargin)
            }
        }
    }

    @Test fun afterShrinkingWidthLeftAndRightMoveThePreview() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                repeat(20) { tap(a, R.id.widthMinus) }             // 가로 80% (1%씩)
                val start = previewLp(a).leftMargin
                tap(a, R.id.moveLeft)
                val left = previewLp(a).leftMargin
                assertTrue("left: $start -> $left", left < start)
                tap(a, R.id.moveRight); tap(a, R.id.moveRight)
                val right = previewLp(a).leftMargin
                assertTrue("right: $left -> $right", right > left)
            }
        }
    }

    @Test fun movingSideToSideWorksEvenRightAfterTheDefaultFullWidth() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                // 가로 100%에서 ◀를 눌러도 반응한다 (가로가 한 단계 줄며 왼쪽으로)
                tap(a, R.id.moveLeft)
                val g = Prefs.geometry(a, large = false)
                assertEquals(100 - KeyboardGeometry.AUTO_SHRINK_PCT, g.widthPct)
                assertTrue(g.xDp < 0)
                val free = (a.resources.displayMetrics.widthPixels * 0.05f).toInt()
                assertTrue(previewLp(a).leftMargin < free / 2 + 1)         // 가운데보다 왼쪽
            }
        }
    }

    @Test fun widthAndHeightChangeByExactlyOnePercentPerTap() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                tap(a, R.id.widthMinus); tap(a, R.id.widthMinus); tap(a, R.id.widthMinus)
                tap(a, R.id.heightMinus); tap(a, R.id.heightMinus)
                tap(a, R.id.heightPlus)
                val g = Prefs.geometry(a, large = false)
                assertEquals(97, g.widthPct)
                assertEquals(99, g.heightPct)
            }
        }
    }

    private fun stepperButton(a: ResizeActivity, row: Int, plus: Boolean): Int {
        val container = a.findViewById<android.view.ViewGroup>(R.id.frameControls)
        val v = container.getChildAt(row).findViewById<View>(if (plus) R.id.stepPlus else R.id.stepMinus)
        val t = android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 1f, 1f, 0))
        return row
    }

    @Test fun frameAndKeyGapControlsWorkAndAreSaved() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val container = a.findViewById<android.view.ViewGroup>(R.id.frameControls)
                assertEquals(7, container.childCount)            // 위·아래·왼쪽·오른쪽·글자 키 간격·숫자 줄 키 간격·숫자 줄 높이
                stepperButton(a, 0, plus = true); stepperButton(a, 0, plus = true)   // 위쪽 +2
                stepperButton(a, 1, plus = false)                                      // 아래쪽 -1
                stepperButton(a, 2, plus = true)                                       // 왼쪽 +1
                stepperButton(a, 3, plus = false); stepperButton(a, 3, plus = false)  // 오른쪽 -2
                stepperButton(a, 4, plus = true)                                       // 글자 키 간격 +1
                stepperButton(a, 5, plus = false); stepperButton(a, 5, plus = false)  // 숫자 줄 간격 -2
                stepperButton(a, 6, plus = true)                                       // 숫자 줄 높이 +5%
                val g = Prefs.geometry(a, large = false)
                assertEquals(4, g.numGapDp); assertEquals(85, g.numHeightPct)
                // 위쪽 4+2, 아래쪽 4-1, 왼쪽 4+1, 오른쪽 4-2, 키 간격 6+1
                assertEquals(6, g.padTopDp); assertEquals(3, g.padBottomDp)
                assertEquals(5, g.padLeftDp); assertEquals(2, g.padRightDp); assertEquals(7, g.keyGapDp)
                // 표시도 갱신
                assertEquals("6dp", container.getChildAt(0).findViewById<android.widget.TextView>(R.id.stepValue).text)
            }
        }
    }

    @Test fun resetRestoresFrameDefaults() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a ->
                stepperButton(a, 0, plus = true); stepperButton(a, 4, plus = true)
                a.findViewById<View>(R.id.resetButton).performClick()
                assertEquals(KeyboardGeometry(), Prefs.geometry(a, large = false))
            }
        }
    }
}
