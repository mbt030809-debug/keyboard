package com.mbt.keyboard.ime

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 실제 키보드(서비스)가 설정한 크기·위치를 적용하고, 켜져 있는 동안 바뀐 설정도 바로 반영하는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardServiceGeometryTest {

    private fun layoutRoot(root: View, widthPx: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        // 틀의 크기가 바뀌었으니 한 번 더 배치
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    @Test fun appliesSavedWidthAndHeightAndFollowsLiveSettingChanges() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 70, heightPct = 120))

        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)

        val host = root.getChildAt(0)
        assertEquals(700, host.layoutParams.width)

        // 키보드가 켜져 있는 동안 설정 앱에서 바꿈 → 새 입력창을 열지 않아도 바로 반영
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 50, heightPct = 100))
        layoutRoot(root, 1000)
        assertEquals(500, host.layoutParams.width)
    }

    @Test fun usesTheRealMeasuredWidthNotAGuess() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 80))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 640)                                  // 분할 화면처럼 화면 폭과 다른 폭
        assertEquals(512, root.getChildAt(0).layoutParams.width)
    }

    @Test fun raiseAndSideOffsetBecomeMargins() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val density = ctx.resources.displayMetrics.density
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 60, xDp = -16, raiseDp = 30))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val lp = root.getChildAt(0).layoutParams as FrameLayout.LayoutParams
        assertEquals((30 * density).toInt(), lp.bottomMargin)
        assertTrue(lp.leftMargin in 1..199)                    // 가운데(200)보다 왼쪽
    }
}
