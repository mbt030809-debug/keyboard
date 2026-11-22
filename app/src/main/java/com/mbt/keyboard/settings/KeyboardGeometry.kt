package com.mbt.keyboard.settings

import kotlin.math.roundToInt

/**
 * 키보드 크기·위치 설정 (순수 계산, 단위 테스트 가능).
 *
 * @param widthPct 가로 길이: 화면 폭 대비 % (50~100)
 * @param heightPct 세로 길이: 키 높이 배율 % (70~150)
 * @param xPct 좌우 위치: 남는 공간 안에서 왼쪽 끝=0, 가운데=50, 오른쪽 끝=100 (가로가 100%면 움직일 곳이 없다)
 * @param raiseDp 상하 위치: 화면 바닥에서 위로 띄운 높이 (0~200dp)
 * @param gapPct 갈라진 정도: 나눠쓰기 때 두 묶음 사이 틈이 키보드 가로폭에서 차지하는 % (6~40)
 */
data class KeyboardGeometry(
    val widthPct: Int = WIDTH_DEFAULT,
    val heightPct: Int = HEIGHT_DEFAULT,
    val xPct: Int = X_DEFAULT,
    val raiseDp: Int = RAISE_DEFAULT,
    val gapPct: Int = GAP_DEFAULT,
) {
    /** 범위를 벗어난 값을 허용 범위 안으로 */
    fun clamped() = KeyboardGeometry(
        widthPct.coerceIn(WIDTH_MIN, WIDTH_MAX),
        heightPct.coerceIn(HEIGHT_MIN, HEIGHT_MAX),
        xPct.coerceIn(X_MIN, X_MAX),
        raiseDp.coerceIn(RAISE_MIN, RAISE_MAX),
        gapPct.coerceIn(GAP_MIN, GAP_MAX),
    )

    fun widerBy(steps: Int) = copy(widthPct = widthPct + steps * WIDTH_STEP).clamped()
    fun tallerBy(steps: Int) = copy(heightPct = heightPct + steps * HEIGHT_STEP).clamped()
    fun movedX(steps: Int) = copy(xPct = xPct + steps * X_STEP).clamped()
    fun raisedBy(steps: Int) = copy(raiseDp = raiseDp + steps * RAISE_STEP).clamped()
    fun gapWiderBy(steps: Int) = copy(gapPct = gapPct + steps * GAP_STEP).clamped()

    /** 틈의 비율 (0.18 = 키보드 가로폭의 18%) */
    val gapFraction: Float get() = gapPct / 100f

    /** 가로를 줄여야 좌우로 움직일 수 있다 */
    val canMoveX: Boolean get() = widthPct < WIDTH_MAX
    val canLower: Boolean get() = raiseDp > RAISE_MIN
    val canRaise: Boolean get() = raiseDp < RAISE_MAX

    fun widthPx(containerWidthPx: Int): Int = (containerWidthPx * widthPct / 100f).roundToInt()

    /** 왼쪽 여백(px): 남는 공간을 [xPct] 비율로 나눈다 */
    fun leftMarginPx(containerWidthPx: Int): Int =
        ((containerWidthPx - widthPx(containerWidthPx)) * xPct / 100f).roundToInt()

    fun raisePx(density: Float): Int = (raiseDp * density).roundToInt()

    val heightScale: Float get() = heightPct / 100f

    companion object {
        const val WIDTH_MIN = 50
        const val WIDTH_MAX = 100
        const val WIDTH_STEP = 5
        const val WIDTH_DEFAULT = 100

        const val HEIGHT_MIN = 70
        const val HEIGHT_MAX = 150
        const val HEIGHT_STEP = 5
        const val HEIGHT_DEFAULT = 100

        const val X_MIN = 0
        const val X_MAX = 100
        const val X_STEP = 10
        const val X_DEFAULT = 50

        const val RAISE_MIN = 0
        const val RAISE_MAX = 200
        const val RAISE_STEP = 10
        const val RAISE_DEFAULT = 0

        const val GAP_MIN = 6
        const val GAP_MAX = 40
        const val GAP_STEP = 2
        const val GAP_DEFAULT = 18

        /** 폴드 안쪽 화면·태블릿처럼 넓은 화면의 기준 (가장 짧은 변, dp) */
        const val LARGE_SCREEN_DP = 600

        fun isLargeScreen(smallestScreenWidthDp: Int) = smallestScreenWidthDp >= LARGE_SCREEN_DP
    }
}
