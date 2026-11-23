package com.mbt.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGeometryTest {
    private val density = 2f
    private val screen = 1000          // 화면 폭(px)

    @Test fun defaultsAreFullWidthCenteredOnTheBottom() {
        val g = KeyboardGeometry()
        assertEquals(100, g.widthPct); assertEquals(100, g.heightPct)
        assertEquals(0, g.xDp); assertEquals(0, g.raiseDp); assertEquals(18, g.gapPct)
        assertEquals(1000, g.widthPx(screen)); assertEquals(0, g.leftMarginPx(screen, density))
    }

    @Test fun sizeStepsAreClampedToRange() {
        assertEquals(50, KeyboardGeometry().widerBy(-99).widthPct)
        assertEquals(100, KeyboardGeometry().widerBy(99).widthPct)
        assertEquals(70, KeyboardGeometry().tallerBy(-99).heightPct)
        assertEquals(150, KeyboardGeometry().tallerBy(99).heightPct)
        assertEquals(0, KeyboardGeometry().raisedBy(-5).raiseDp)
        assertEquals(200, KeyboardGeometry().raisedBy(999).raiseDp)
        // 1%씩 조절
        assertEquals(99, KeyboardGeometry().widerBy(-1).widthPct)
        assertEquals(98, KeyboardGeometry().widerBy(-2).widthPct)
        assertEquals(101, KeyboardGeometry().tallerBy(1).heightPct)
        assertEquals(99, KeyboardGeometry().tallerBy(-1).heightPct)
        assertEquals(2, KeyboardGeometry().raisedBy(1).raiseDp)
    }

    @Test fun narrowKeyboardStartsCenteredAndOffsetMovesIt() {
        val narrow = KeyboardGeometry(widthPct = 60)                  // 남는 공간 400px
        assertEquals(600, narrow.widthPx(screen))
        assertEquals(200, narrow.leftMarginPx(screen, density))      // 가운데
        assertEquals(168, narrow.copy(xDp = -16).leftMarginPx(screen, density))   // 왼쪽으로 16dp = 32px
        assertEquals(232, narrow.copy(xDp = 16).leftMarginPx(screen, density))
    }

    @Test fun marginNeverLeavesTheScreen() {
        val narrow = KeyboardGeometry(widthPct = 60, xDp = -600)
        assertEquals(0, narrow.leftMarginPx(screen, density))
        assertEquals(400, narrow.copy(xDp = 600).leftMarginPx(screen, density))
        assertEquals(0, KeyboardGeometry(widthPct = 100, xDp = 500).leftMarginPx(screen, density))
    }

    @Test fun movingStepsAreFixedRegardlessOfFreeSpace() {
        // 예전에는 "남는 공간의 10%"씩이라 가로 95%에서는 몇 px밖에 안 움직였다. 이제는 항상 2dp씩.
        assertEquals(2, KeyboardGeometry.X_STEP_DP)
        assertEquals(2, KeyboardGeometry.RAISE_STEP)
        val g = KeyboardGeometry(widthPct = 90)                      // 남는 공간 100px(=50dp)
        val moved = g.movedX(-1, screen, density)
        assertEquals(50, g.leftMarginPx(screen, density))
        assertEquals(46, moved.leftMarginPx(screen, density))        // 50 - 4px(2dp)
        assertEquals(50, moved.movedX(1, screen, density).leftMarginPx(screen, density))   // 되돌리면 정확히 제자리
        val wide = KeyboardGeometry(widthPct = 60)                   // 남는 공간 400px
        assertEquals(4, wide.leftMarginPx(screen, density) - wide.movedX(-1, screen, density).leftMarginPx(screen, density))
    }

    @Test fun pressingMoveAtFullWidthShrinksOneStepAndMoves() {
        val g = KeyboardGeometry()                                    // 가로 100%: 남는 공간 0
        assertTrue(g.canMoveLeft(screen, density) && g.canMoveRight(screen, density))   // 눌러볼 수 있다
        val moved = g.movedX(-1, screen, density)
        assertEquals(100 - KeyboardGeometry.AUTO_SHRINK_PCT, moved.widthPct)
        assertEquals(25 - 4, moved.leftMarginPx(screen, density))                         // 남는 50px의 가운데(25)에서 2dp(4px) 왼쪽
    }

    @Test fun movingStopsAtEdgesAndStoredValueDoesNotPileUp() {
        var g = KeyboardGeometry(widthPct = 90)
        repeat(30) { g = g.movedX(-1, screen, density) }            // 끝에서 계속 눌러도 (30 × 4px > 50px)
        assertEquals(0, g.leftMarginPx(screen, density))
        assertFalse(g.canMoveLeft(screen, density))
        val back = g.movedX(1, screen, density)                      // 한 번 반대로 누르면 바로 움직인다
        assertTrue(back.leftMarginPx(screen, density) > 0)
        assertTrue(Math.abs(g.xDp) <= 50)                            // 저장값이 실제 위치와 같다 (쌓이지 않음)
    }

    @Test fun rightEdge() {
        var g = KeyboardGeometry(widthPct = 90)
        repeat(30) { g = g.movedX(1, screen, density) }
        assertEquals(g.freePx(screen), g.leftMarginPx(screen, density))
        assertFalse(g.canMoveRight(screen, density))
        assertTrue(g.canMoveLeft(screen, density))
    }

    @Test fun raiseAndLowerLimits() {
        assertFalse(KeyboardGeometry().canLower)
        assertTrue(KeyboardGeometry().canRaise)
        assertFalse(KeyboardGeometry(raiseDp = 200).canRaise)
        assertEquals(60, KeyboardGeometry(raiseDp = 20).raisePx(3f))
    }

    @Test fun clampedFixesOutOfRangeValues() {
        assertEquals(KeyboardGeometry(50, 150, -600, 200, 40), KeyboardGeometry(1, 999, -9999, 999, 999).clamped())
    }

    @Test fun gapDefaultsStepsAndLimits() {
        assertEquals(0.18f, KeyboardGeometry().gapFraction, 0.0001f)
        assertEquals(19, KeyboardGeometry().gapWiderBy(1).gapPct)
        assertEquals(17, KeyboardGeometry().gapWiderBy(-1).gapPct)
        assertEquals(6, KeyboardGeometry().gapWiderBy(-99).gapPct)
        assertEquals(40, KeyboardGeometry().gapWiderBy(99).gapPct)
    }

    @Test fun heightScaleAndLargeScreenRule() {
        assertEquals(1.25f, KeyboardGeometry(heightPct = 125).heightScale, 0.0001f)
        assertTrue(KeyboardGeometry.isLargeScreen(600))
        assertFalse(KeyboardGeometry.isLargeScreen(411))
    }

    @Test fun screenProfileUsesTheShorterSideOfTheWholeDisplay() {
        assertTrue(ScreenProfile.isLarge(1812, 2176, 2.625f))        // 폴드 안쪽 화면
        assertFalse(ScreenProfile.isLarge(1080, 2316, 2.625f))       // 폴드 바깥(접힘)
        assertFalse(ScreenProfile.isLarge(2316, 1080, 2.625f))       // 폰 가로 모드
    }

    @Test fun frameDefaultsMatchTheOldFixedValues() {
        val g = KeyboardGeometry()
        assertEquals(listOf(4, 4, 4, 4), listOf(g.padLeftDp, g.padTopDp, g.padRightDp, g.padBottomDp))
        assertEquals(6, g.keyGapDp)
    }

    @Test fun framePaddingsAreIndependentAndStepByOneDp() {
        val g = KeyboardGeometry().padTopBy(3).padLeftBy(-2).padRightBy(10)
        assertEquals(7, g.padTopDp); assertEquals(2, g.padLeftDp); assertEquals(14, g.padRightDp)
        assertEquals(4, g.padBottomDp)                       // 건드리지 않은 쪽은 그대로
        assertEquals(5, KeyboardGeometry().padBottomBy(1).padBottomDp)
        assertEquals(7, KeyboardGeometry().keyGapBy(1).keyGapDp)
    }

    @Test fun frameValuesAreClamped() {
        assertEquals(0, KeyboardGeometry().padTopBy(-99).padTopDp)
        assertEquals(40, KeyboardGeometry().padLeftBy(999).padLeftDp)
        assertEquals(0, KeyboardGeometry().keyGapBy(-99).keyGapDp)
        assertEquals(16, KeyboardGeometry().keyGapBy(99).keyGapDp)
        val c = KeyboardGeometry(padLeftDp = -5, padTopDp = 99, padRightDp = -1, padBottomDp = 41, keyGapDp = 99).clamped()
        assertEquals(listOf(0, 40, 0, 40, 16), listOf(c.padLeftDp, c.padTopDp, c.padRightDp, c.padBottomDp, c.keyGapDp))
    }
}
