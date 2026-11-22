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

    fun isLarge(widthPx: Int, heightPx: Int, density: Float): Boolean =
        KeyboardGeometry.isLargeScreen((min(widthPx, heightPx) / density).toInt())
}
