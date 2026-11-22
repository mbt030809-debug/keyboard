package com.mbt.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGeometryTest {
    @Test fun defaultsAreFullWidthCenteredOnTheBottom() {
        val g = KeyboardGeometry()
        assertEquals(100, g.widthPct); assertEquals(100, g.heightPct)
        assertEquals(50, g.xPct); assertEquals(0, g.raiseDp)
        assertEquals(1080, g.widthPx(1080)); assertEquals(0, g.leftMarginPx(1080))
    }

    @Test fun stepsAreClampedToRange() {
        assertEquals(50, KeyboardGeometry().widerBy(-99).widthPct)
        assertEquals(100, KeyboardGeometry().widerBy(99).widthPct)
        assertEquals(70, KeyboardGeometry().tallerBy(-99).heightPct)
        assertEquals(150, KeyboardGeometry().tallerBy(99).heightPct)
        assertEquals(0, KeyboardGeometry().movedX(-99).xPct)
        assertEquals(100, KeyboardGeometry().movedX(99).xPct)
        assertEquals(0, KeyboardGeometry().raisedBy(-5).raiseDp)
        assertEquals(200, KeyboardGeometry().raisedBy(99).raiseDp)
    }

    @Test fun stepSizes() {
        assertEquals(95, KeyboardGeometry().widerBy(-1).widthPct)
        assertEquals(105, KeyboardGeometry().tallerBy(1).heightPct)
        assertEquals(60, KeyboardGeometry().movedX(1).xPct)
        assertEquals(10, KeyboardGeometry().raisedBy(1).raiseDp)
    }

    @Test fun narrowKeyboardCanBeMovedWithinFreeSpace() {
        val w = 1000
        val narrow = KeyboardGeometry(widthPct = 60)           // 남는 공간 400px
        assertEquals(600, narrow.widthPx(w))
        assertEquals(200, narrow.copy(xPct = 50).leftMarginPx(w))   // 가운데
        assertEquals(0, narrow.copy(xPct = 0).leftMarginPx(w))      // 왼쪽 끝
        assertEquals(400, narrow.copy(xPct = 100).leftMarginPx(w))  // 오른쪽 끝 (오른쪽 여백 0)
    }

    @Test fun fullWidthCannotMoveSideways() {
        assertFalse(KeyboardGeometry(widthPct = 100).canMoveX)
        assertTrue(KeyboardGeometry(widthPct = 95).canMoveX)
        assertEquals(0, KeyboardGeometry(widthPct = 100, xPct = 100).leftMarginPx(1080))
    }

    @Test fun raiseAndLowerLimits() {
        assertFalse(KeyboardGeometry().canLower)
        assertTrue(KeyboardGeometry().canRaise)
        assertFalse(KeyboardGeometry(raiseDp = 200).canRaise)
        assertEquals(60, KeyboardGeometry(raiseDp = 20).raisePx(3f))
    }

    @Test fun clampedFixesOutOfRangeValues() {
        val g = KeyboardGeometry(10, 500, -5, 999).clamped()
        assertEquals(KeyboardGeometry(50, 150, 0, 200), g)
    }

    @Test fun heightScaleAndLargeScreenRule() {
        assertEquals(1.25f, KeyboardGeometry(heightPct = 125).heightScale, 0.0001f)
        assertTrue(KeyboardGeometry.isLargeScreen(600))
        assertTrue(KeyboardGeometry.isLargeScreen(673))
        assertFalse(KeyboardGeometry.isLargeScreen(411))
    }

    @Test fun gapDefaultsStepsAndLimits() {
        assertEquals(18, KeyboardGeometry().gapPct)
        assertEquals(0.18f, KeyboardGeometry().gapFraction, 0.0001f)
        assertEquals(20, KeyboardGeometry().gapWiderBy(1).gapPct)
        assertEquals(16, KeyboardGeometry().gapWiderBy(-1).gapPct)
        assertEquals(6, KeyboardGeometry().gapWiderBy(-99).gapPct)
        assertEquals(40, KeyboardGeometry().gapWiderBy(99).gapPct)
        assertEquals(KeyboardGeometry(50, 150, 0, 200, 40), KeyboardGeometry(1, 999, -1, 999, 999).clamped())
    }
}
