package com.mbt.keyboard.settings

import android.content.Context
import android.os.Build
import android.view.WindowManager
import kotlin.math.min

/**
 * 지금 화면이 폴드를 펼친 것처럼 넓은 화면인지 판정한다. 키보드와 설정 앱이 같은 기준을 써야
 * 설정한 값이 서로 다른 쪽(접힘/펼침)에 저장·적용되는 일이 없다.
 *
 * 창 크기(분할 화면 등으로 달라짐)가 아니라 기기 화면 전체 크기를 기준으로 한다.
 */
object ScreenProfile {

    fun isLarge(context: Context): Boolean {
        // 설정 기준(창 크기) 또는 기기 화면 전체 크기 중 하나라도 넓으면 넓은 화면.
        // 창은 화면보다 클 수 없으므로 실제 기기에서는 기기 화면 크기 판정과 같아진다.
        val byConfig = KeyboardGeometry.isLargeScreen(context.resources.configuration.smallestScreenWidthDp)
        var byDisplay = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            if (wm != null) {
                val b = wm.maximumWindowMetrics.bounds
                byDisplay = isLarge(b.width(), b.height(), context.resources.displayMetrics.density)
            }
        }
        return byConfig || byDisplay
    }

    /** 가로 모드인가 (폰을 눕힘, 또는 폴드 펼친 화면을 가로로 둠) */
    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    /**
     * 갈라진 자판이 적용되는가.
     * - 펼침(넓은) 화면: 설정 스위치를 따른다 (세로·가로 모두)
     * - 접힘(일반) 화면: 가로 모드에서만, 그 가로 모드의 "갈라진 정도"가 0보다 클 때
     */
    fun splitApplies(large: Boolean, landscape: Boolean, splitSwitchOn: Boolean, gapPct: Int): Boolean =
        if (large) splitSwitchOn else landscape && gapPct > 0

    /**
     * 키 높이 자동 배율. 폰을 가로로 돌리면 화면 높이가 낮아서(약 360dp) 키보드 5줄이 화면을 대부분 덮는다.
     * 화면 높이가 [LOW_HEIGHT_DP]보다 낮으면 키 높이를 [LOW_HEIGHT_FACTOR]배로 줄인다.
     * (폴드를 펼친 가로 화면처럼 높이가 충분하면 그대로)
     */
    fun heightFactor(screenHeightDp: Int): Float =
        if (screenHeightDp in 1 until LOW_HEIGHT_DP) LOW_HEIGHT_FACTOR else 1f

    const val LOW_HEIGHT_DP = 480
    const val LOW_HEIGHT_FACTOR = 0.65f

    fun isLarge(widthPx: Int, heightPx: Int, density: Float): Boolean =
        KeyboardGeometry.isLargeScreen((min(widthPx, heightPx) / density).toInt())
}
