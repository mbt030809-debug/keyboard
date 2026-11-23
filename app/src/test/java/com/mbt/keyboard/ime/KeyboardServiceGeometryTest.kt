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

    @Test fun framePaddingsAndKeyGapReachTheRealKeyboardAndFollowLiveChanges() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(padLeftDp = 12, padTopDp = 1, padRightDp = 20, padBottomDp = 3, keyGapDp = 2))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val keyboard = (root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView
        assertEquals(KeyboardFrame(12, 1, 20, 3, 2), keyboard.frame)

        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(padLeftDp = 0, keyGapDp = 10))
        layoutRoot(root, 1000)
        assertEquals(0, keyboard.frame.leftDp)
        assertEquals(10, keyboard.frame.keyGapDp)
    }

    // ---- 마지막 사용 언어로 시작

    private fun startField(service: KeyboardService, inputType: Int) {
        val info = android.view.inputmethod.EditorInfo().apply { this.inputType = inputType }
        service.onStartInputView(info, false)
    }

    private fun currentLayoutId(root: FrameLayout) =
        ((root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView).keyboardLayout!!.id

    private val TEXT = android.text.InputType.TYPE_CLASS_TEXT
    private val URL = TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
    private val PASSWORD = TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

    @Test fun afterAUrlFieldTheNextNormalFieldGoesBackToKorean() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, TEXT)
        assertEquals("kos", currentLayoutId(root))
        startField(service, URL)                    // 주소창: 그 칸에서만 영어
        assertEquals("en", currentLayoutId(root))
        startField(service, PASSWORD)
        assertEquals("en", currentLayoutId(root))
        startField(service, TEXT)                   // 일반 입력칸: 다시 한글 (예전에는 영어로 남아 있었다)
        assertEquals("kos", currentLayoutId(root))
    }

    @Test fun startsInTheLastLanguageTheUserPickedAndRemembersAcrossRestarts() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, TEXT)
        service.onSwipeSpace()                       // 사용자가 직접 영어로 전환
        assertEquals("en", currentLayoutId(root))
        assertTrue(Prefs.lastLanguageEnglish(ctx))

        startField(service, TEXT)                    // 다른 입력칸에서 키보드를 다시 띄움 → 영어 유지
        assertEquals("en", currentLayoutId(root))

        // 서비스가 새로 만들어져도(프로세스 재시작 등) 마지막 언어로 시작
        val service2 = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root2 = service2.onCreateInputView() as FrameLayout
        startField(service2, TEXT)
        assertEquals("en", currentLayoutId(root2))

        service2.onSwipeSpace()                      // 다시 한글로
        assertEquals(false, Prefs.lastLanguageEnglish(ctx))
        startField(service2, TEXT)
        assertEquals("kos", currentLayoutId(root2))
    }

    @Test fun switchingLanguageInsideAnEmailFieldIsRemembered() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, URL)                     // 자동 영어 (사용자 선택 아님 → 기억하지 않음)
        assertEquals(false, Prefs.lastLanguageEnglish(ctx))
        service.onSwipeSpace()                       // 영어에서 직접 전환 → 한글
        assertEquals("kos", currentLayoutId(root))
        assertEquals(false, Prefs.lastLanguageEnglish(ctx))
    }
}
