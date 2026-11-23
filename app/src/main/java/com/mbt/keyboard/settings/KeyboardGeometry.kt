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
 * @param gapPct 갈라진 정도: 나눠쓰기 때 두 묶음 사이 틈이 키보드 가로폭에서 차지하는 % (0~40). 화면 방향·접힘/펼침마다 따로 저장
 * @param padLeftDp 키 둘레 여백(키보드 바깥 프레임): 왼쪽 (0~40dp)
 * @param padTopDp 위쪽 여백
 * @param padRightDp 오른쪽 여백
 * @param padBottomDp 아래쪽 여백
 * @param keyGapDp 글자 키(숫자 줄 아래 모든 줄)의 키와 키 사이 간격 (0~16dp)
 * @param rowGapDp 줄과 줄 사이 세로 여백 (0~24dp). 넓혀도 키 크기는 그대로, 키보드가 세로로 길어진다
 * @param numRowGapDp 숫자 줄과 그 아래 첫 글자 줄 사이 세로 여백 (0~40dp). 키 크기는 그대로
 * @param hit1Dp 첫째 글자 줄(ㅂㅈㄷㄱㅅ)의 인식 영역을 아래로 넓히는 양 (-8~+12dp). 음수면 위로 좁힘
 * @param hit2Dp 둘째 글자 줄(ㅁㄴㅇㄹㅎ) 아래쪽 인식 영역 이동
 * @param hit3Dp 셋째 글자 줄(ㅋㅌㅊㅍ) 아래쪽 인식 영역 이동
 * @param numHeightPct 맨 윗줄(숫자 줄) 높이: 일반 키 높이 대비 % (40~120, 기본 80)
 */
data class KeyboardGeometry(
    val widthPct: Int = WIDTH_DEFAULT,
    val heightPct: Int = HEIGHT_DEFAULT,
    val xDp: Int = X_DEFAULT,
    val raiseDp: Int = RAISE_DEFAULT,
    val gapPct: Int = GAP_DEFAULT,
    val padLeftDp: Int = PAD_DEFAULT,
    val padTopDp: Int = PAD_DEFAULT,
    val padRightDp: Int = PAD_DEFAULT,
    val padBottomDp: Int = PAD_DEFAULT,
    val keyGapDp: Int = KEY_GAP_DEFAULT,
    val rowGapDp: Int = ROW_GAP_DEFAULT,
    val numHeightPct: Int = NUM_HEIGHT_DEFAULT,
    val numRowGapDp: Int = NUM_ROW_GAP_DEFAULT,
    val hit1Dp: Int = HIT_DEFAULT,
    val hit2Dp: Int = HIT_DEFAULT,
    val hit3Dp: Int = HIT_DEFAULT,
) {
    /** 범위를 벗어난 값을 허용 범위 안으로 */
    fun clamped() = KeyboardGeometry(
        widthPct.coerceIn(WIDTH_MIN, WIDTH_MAX),
        heightPct.coerceIn(HEIGHT_MIN, HEIGHT_MAX),
        xDp.coerceIn(X_MIN, X_MAX),
        raiseDp.coerceIn(RAISE_MIN, RAISE_MAX),
        gapPct.coerceIn(GAP_MIN, GAP_MAX),
        padLeftDp.coerceIn(PAD_MIN, PAD_MAX),
        padTopDp.coerceIn(PAD_MIN, PAD_MAX),
        padRightDp.coerceIn(PAD_MIN, PAD_MAX),
        padBottomDp.coerceIn(PAD_MIN, PAD_MAX),
        keyGapDp.coerceIn(KEY_GAP_MIN, KEY_GAP_MAX),
        rowGapDp.coerceIn(ROW_GAP_MIN, ROW_GAP_MAX),
        numHeightPct.coerceIn(NUM_HEIGHT_MIN, NUM_HEIGHT_MAX),
        numRowGapDp.coerceIn(NUM_ROW_GAP_MIN, NUM_ROW_GAP_MAX),
        hit1Dp.coerceIn(HIT_MIN, HIT_MAX),
        hit2Dp.coerceIn(HIT_MIN, HIT_MAX),
        hit3Dp.coerceIn(HIT_MIN, HIT_MAX),
    )

    fun widerBy(steps: Int) = copy(widthPct = widthPct + steps * WIDTH_STEP).clamped()
    fun tallerBy(steps: Int) = copy(heightPct = heightPct + steps * HEIGHT_STEP).clamped()
    fun raisedBy(steps: Int) = copy(raiseDp = raiseDp + steps * RAISE_STEP).clamped()
    fun gapWiderBy(steps: Int) = copy(gapPct = gapPct + steps * GAP_STEP).clamped()

    fun padLeftBy(steps: Int) = copy(padLeftDp = padLeftDp + steps * PAD_STEP).clamped()
    fun padTopBy(steps: Int) = copy(padTopDp = padTopDp + steps * PAD_STEP).clamped()
    fun padRightBy(steps: Int) = copy(padRightDp = padRightDp + steps * PAD_STEP).clamped()
    fun padBottomBy(steps: Int) = copy(padBottomDp = padBottomDp + steps * PAD_STEP).clamped()
    fun keyGapBy(steps: Int) = copy(keyGapDp = keyGapDp + steps * KEY_GAP_STEP).clamped()
    fun hit1By(steps: Int) = copy(hit1Dp = hit1Dp + steps).clamped()
    fun hit2By(steps: Int) = copy(hit2Dp = hit2Dp + steps).clamped()
    fun hit3By(steps: Int) = copy(hit3Dp = hit3Dp + steps).clamped()

    /** 글자 줄 3개의 아래 경계 이동량(dp) */
    val hitShifts: List<Int> get() = listOf(hit1Dp, hit2Dp, hit3Dp)

    fun numRowGapBy(steps: Int) = copy(numRowGapDp = numRowGapDp + steps * ROW_GAP_STEP).clamped()
    fun rowGapBy(steps: Int) = copy(rowGapDp = rowGapDp + steps * ROW_GAP_STEP).clamped()
    fun numHeightBy(steps: Int) = copy(numHeightPct = numHeightPct + steps * NUM_HEIGHT_STEP).clamped()

    /** 숫자 줄 높이 배율 (0.8 = 일반 키의 80%) */
    val numHeightScale: Float get() = numHeightPct / 100f

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

        /** 0이면 나누지 않는다 (접힘 화면의 가로 모드에서 갈라진 자판을 끄는 값) */
        const val GAP_MIN = 0
        const val GAP_MAX = 40
        const val GAP_STEP = 1
        const val GAP_DEFAULT = 18

        /** 접힘 화면의 가로 모드 기본값: 나누지 않음 (직접 올려야 갈라진다) */
        const val GAP_NORMAL_LANDSCAPE_DEFAULT = 0

        const val PAD_MIN = 0
        const val PAD_MAX = 40
        const val PAD_STEP = 1
        const val PAD_DEFAULT = 4

        const val KEY_GAP_MIN = 0
        const val KEY_GAP_MAX = 16
        const val KEY_GAP_STEP = 1
        const val KEY_GAP_DEFAULT = 6

        const val ROW_GAP_MIN = 0
        const val ROW_GAP_MAX = 24
        const val ROW_GAP_STEP = 1
        const val ROW_GAP_DEFAULT = 6

        /** 줄 인식 영역 이동: 터치가 평소 아래로 밀리는 것을 보정하려고 기본은 아래로 3dp */
        const val HIT_MIN = -8
        const val HIT_MAX = 12
        const val HIT_DEFAULT = 3

        const val NUM_ROW_GAP_MIN = 0
        const val NUM_ROW_GAP_MAX = 40
        const val NUM_ROW_GAP_DEFAULT = 6

        const val NUM_HEIGHT_MIN = 40
        const val NUM_HEIGHT_MAX = 120
        const val NUM_HEIGHT_STEP = 5
        const val NUM_HEIGHT_DEFAULT = 80          // 예전 고정값(0.8배)

        /** 폴드 안쪽 화면·태블릿처럼 넓은 화면의 기준 (가장 짧은 변, dp) */
        const val LARGE_SCREEN_DP = 600

        fun isLargeScreen(smallestScreenWidthDp: Int) = smallestScreenWidthDp >= LARGE_SCREEN_DP
    }
}
