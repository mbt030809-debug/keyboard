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
        assertEquals(200, KeyboardGeometry().raisedBy(99).raiseDp)
        assertEquals(95, KeyboardGeometry().widerBy(-1).widthPct)
        assertEquals(105, KeyboardGeometry().tallerBy(1).heightPct)
        assertEquals(10, KeyboardGeometry().raisedBy(1).raiseDp)
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

    @Test fun movingStepsAreVisibleEvenWhenAlmostFullWidth() {
        // 예전에는 "남는 공간의 10%"씩이라 가로 95%에서는 몇 px밖에 안 움직였다. 이제는 항상 16dp씩.
        val g = KeyboardGeometry(widthPct = 90)                      // 남는 공간 100px(=50dp)
        val moved = g.movedX(-1, screen, density)
        assertEquals(50, g.leftMarginPx(screen, density))
        assertEquals(18, moved.leftMarginPx(screen, density))        // 50 - 32px
    }

    @Test fun pressingMoveAtFullWidthShrinksOneStepAndMoves() {
        val g = KeyboardGeometry()                                    // 가로 100%: 남는 공간 0
        assertTrue(g.canMoveLeft(screen, density) && g.canMoveRight(screen, density))   // 눌러볼 수 있다
        val moved = g.movedX(-1, screen, density)
        assertEquals(95, moved.widthPct)
        assertTrue(moved.leftMarginPx(screen, density) < moved.freePx(screen) / 2)       // 왼쪽으로 갔다
        assertEquals(0, moved.leftMarginPx(screen, density))                              // 50px 중 25 - 32 → 왼쪽 끝
    }

    @Test fun movingStopsAtEdgesAndStoredValueDoesNotPileUp() {
        var g = KeyboardGeometry(widthPct = 90)
        repeat(30) { g = g.movedX(-1, screen, density) }            // 끝에서 계속 눌러도
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
        assertEquals(20, KeyboardGeometry().gapWiderBy(1).gapPct)
        assertEquals(16, KeyboardGeometry().gapWiderBy(-1).gapPct)
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
}
