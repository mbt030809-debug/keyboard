package com.mbt.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 크기·여백·간격·숫자줄·전복붙 설정은 펼침/접힘 × 세로/가로 네 가지가 각각 따로 저장된다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeometryProfilesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private val unfoldedPortrait = KeyboardGeometry(widthPct = 90, heightPct = 110, padLeftDp = 10, keyGapDp = 3, numHeightPct = 70, fnTabHeightPct = 120, fnTabPadDp = 9, hit0Dp = 5)
    private val unfoldedLandscape = KeyboardGeometry(widthPct = 60, heightPct = 80, padLeftDp = 20, keyGapDp = 9, numHeightPct = 100, fnTabHeightPct = 80, fnTabPadDp = 2, hit0Dp = -2)
    private val foldedPortrait = KeyboardGeometry(widthPct = 100, heightPct = 100, padLeftDp = 1, keyGapDp = 0, rowGapDp = 12, numRowGapDp = 20)
    private val foldedLandscape = KeyboardGeometry(widthPct = 75, heightPct = 90, padLeftDp = 33, keyGapDp = 11, rowGapDp = 2, numRowGapDp = 1, gapPct = 12)

    @Test fun fourProfilesAreSavedIndependently() {
        Prefs.setGeometry(ctx, large = true, unfoldedPortrait, landscape = false)
        Prefs.setGeometry(ctx, large = true, unfoldedLandscape, landscape = true)
        Prefs.setGeometry(ctx, large = false, foldedPortrait, landscape = false)
        Prefs.setGeometry(ctx, large = false, foldedLandscape, landscape = true)
        assertEquals(unfoldedPortrait, Prefs.geometry(ctx, large = true, landscape = false))
        assertEquals(unfoldedLandscape, Prefs.geometry(ctx, large = true, landscape = true))
        assertEquals(foldedPortrait, Prefs.geometry(ctx, large = false, landscape = false))
        assertEquals(foldedLandscape, Prefs.geometry(ctx, large = false, landscape = true))
    }

    @Test fun changingLandscapeNeverTouchesPortraitAndViceVersa() {
        Prefs.setGeometry(ctx, large = true, unfoldedPortrait, landscape = false)
        Prefs.setGeometry(ctx, large = true, unfoldedLandscape, landscape = true)
        Prefs.setGeometry(ctx, large = true, unfoldedLandscape.copy(widthPct = 55), landscape = true)
        assertEquals(unfoldedPortrait, Prefs.geometry(ctx, large = true, landscape = false))
        Prefs.setGeometry(ctx, large = true, unfoldedPortrait.copy(padLeftDp = 13), landscape = false)
        assertEquals(unfoldedLandscape.copy(widthPct = 55), Prefs.geometry(ctx, large = true, landscape = true))
    }

    @Test fun landscapeStartsFromThePortraitValuesOfTheSameScreenUntilItIsSaved() {
        Prefs.setGeometry(ctx, large = true, unfoldedPortrait, landscape = false)
        val land = Prefs.geometry(ctx, large = true, landscape = true)
        assertEquals(unfoldedPortrait, land)                                          // 펼침 가로 = 펼침 세로를 이어받음
        val foldedLand = Prefs.geometry(ctx, large = false, landscape = true)
        assertEquals(KeyboardGeometry.GAP_NORMAL_LANDSCAPE_DEFAULT, foldedLand.gapPct) // 접힘 가로는 나눠쓰기 기본 꺼짐
    }
}
