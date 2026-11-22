package com.mbt.keyboard.settings

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 키보드 크기·위치 설정 (순수 계산, 단위 테스트 가능).
 *
 * @param widthPct 가로 길이: 화면 폭 대비 % (50~100)
 * @param heightPct 세로 길이: 키 높이 배율 % (70~150)
 * @param xDp 좌우 위치: 가운데에서 얼마나 옮겼는지(dp, 음수=왼쪽, 양수=오른쪽). 화면 밖으로는 못 나간다
 * @param raiseDp 상하 위치: 화면 바닥에서 위로 띄운 높이 (0~200dp)
 * @param gapPct 갈라진 정도: 나눠쓰기 때 두 묶음 사이 틈이 키보드 가로폭에서 차지하는 % (6~40)
 */
data class KeyboardGeometry(
    val widthPct: Int = WIDTH_DEFAULT,
    val heightPct: Int = HEIGHT_DEFAULT,
    val xDp: Int = X_DEFAULT,
    val raiseDp: Int = RAISE_DEFAULT,
    val gapPct: Int = GAP_DEFAULT,
) {
    /** 범위를 벗어난 값을 허용 범위 안으로 */
    fun clamped() = KeyboardGeometry(
        widthPct.coerceIn(WIDTH_MIN, WIDTH_MAX),
        heightPct.coerceIn(HEIGHT_MIN, HEIGHT_MAX),
        xDp.coerceIn(X_MIN, X_MAX),
        raiseDp.coerceIn(RAISE_MIN, RAISE_MAX),
        gapPct.coerceIn(GAP_MIN, GAP_MAX),
    )

    fun widerBy(steps: Int) = copy(widthPct = widthPct + steps * WIDTH_STEP).clamped()
    fun tallerBy(steps: Int) = copy(heightPct = heightPct + steps * HEIGHT_STEP).clamped()
    fun raisedBy(steps: Int) = copy(raiseDp = raiseDp + steps * RAISE_STEP).clamped()
    fun gapWiderBy(steps: Int) = copy(gapPct = gapPct + steps * GAP_STEP).clamped()

    /** 틈의 비율 (0.18 = 키보드 가로폭의 18%) */
    val gapFraction: Float get() = gapPct / 100f

    val canLower: Boolean get() = raiseDp > RAISE_MIN
    val canRaise: Boolean get() = raiseDp < RAISE_MAX
    val heightScale: Float get() = heightPct / 100f

    fun widthPx(containerWidthPx: Int): Int = (containerWidthPx * widthPct / 100f).roundToInt()

    /** 좌우로 움직일 수 있는 남는 공간(px) */
    fun freePx(containerWidthPx: Int): Int = (containerWidthPx - widthPx(containerWidthPx)).coerceAtLeast(0)

    /** 왼쪽 여백(px): 가운데 정렬 위치에서 [xDp]만큼 옮기되 화면 안으로 제한 */
    fun leftMarginPx(containerWidthPx: Int, density: Float): Int {
        val free = freePx(containerWidthPx)
        return (free / 2f + xDp * density).roundToInt().coerceIn(0, free)
    }

    fun raisePx(density: Float): Int = (raiseDp * density).roundToInt()

    /**
     * 왼쪽(◀)·오른쪽(▶) 이동 가능 여부. 가로가 100%라 남는 공간이 없을 때는
     * 누르면 가로를 [AUTO_SHRINK_PCT]% 줄여서 움직일 공간을 만들기 때문에 눌러볼 수 있다.
     */
    fun canMoveLeft(containerWidthPx: Int, density: Float) =
        freePx(containerWidthPx) == 0 || leftMarginPx(containerWidthPx, density) > 0

    fun canMoveRight(containerWidthPx: Int, density: Float) =
        freePx(containerWidthPx) == 0 || leftMarginPx(containerWidthPx, density) < freePx(containerWidthPx)

    /**
     * 좌우로 [steps]칸([X_STEP_DP]dp씩) 옮긴다. 남는 공간이 없으면(가로 100%) 먼저 가로를 [AUTO_SHRINK_PCT]% 줄인다.
     * 저장되는 값은 항상 실제 위치와 같게 맞춰서, 끝에서 계속 눌러도 값이 쌓이지 않는다.
     */
    fun movedX(steps: Int, containerWidthPx: Int, density: Float): KeyboardGeometry {
        val g = if (freePx(containerWidthPx) == 0) copy(widthPct = WIDTH_MAX - AUTO_SHRINK_PCT) else this
        val free = g.freePx(containerWidthPx)
        val current = g.leftMarginPx(containerWidthPx, density)
        val next = (current + steps * X_STEP_DP * density).roundToInt().coerceIn(0, free)
        // dp로 저장할 때 반올림 때문에 끝에서 1px 남지 않도록, 끝이면 끝 쪽으로 확실히 몰아 저장한다
        val raw = (next - free / 2f) / density
        val xDp = when (next) {
            0 -> floor(raw)
            free -> ceil(raw)
            else -> raw.roundToInt().toFloat()
        }.toInt()
        return g.copy(xDp = xDp).clamped()
    }

    companion object {
        const val WIDTH_MIN = 50
        const val WIDTH_MAX = 100
        const val WIDTH_STEP = 1

        /** 가로가 100%라 남는 공간이 없을 때 ◀ ▶를 누르면 이만큼(%) 줄여서 움직일 공간을 만든다 */
        const val AUTO_SHRINK_PCT = 5
        const val WIDTH_DEFAULT = 100

        const val HEIGHT_MIN = 70
        const val HEIGHT_MAX = 150
        const val HEIGHT_STEP = 1
        const val HEIGHT_DEFAULT = 100

        const val X_MIN = -600
        const val X_MAX = 600
        const val X_STEP_DP = 2
        const val X_DEFAULT = 0

        const val RAISE_MIN = 0
        const val RAISE_MAX = 200
        const val RAISE_STEP = 2
        const val RAISE_DEFAULT = 0

        const val GAP_MIN = 6
        const val GAP_MAX = 40
        const val GAP_STEP = 1
        const val GAP_DEFAULT = 18

        /** 폴드 안쪽 화면·태블릿처럼 넓은 화면의 기준 (가장 짧은 변, dp) */
        const val LARGE_SCREEN_DP = 600

        fun isLargeScreen(smallestScreenWidthDp: Int) = smallestScreenWidthDp >= LARGE_SCREEN_DP
    }
}
