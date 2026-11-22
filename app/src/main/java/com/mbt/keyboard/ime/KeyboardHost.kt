package com.mbt.keyboard.ime

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.mbt.keyboard.settings.KeyboardGeometry

/**
 * 키보드(와 이모지·상용구 패널)를 담는 틀에 크기·위치 설정을 적용한다.
 * 실제 키보드와 설정 앱의 미리보기가 똑같이 보이도록 같은 코드를 쓴다.
 */
object KeyboardHost {
    /**
     * @param host 키보드를 담은 뷰. 부모는 [FrameLayout]이어야 한다
     * @param containerWidthPx 부모(= 화면) 가로 폭
     */
    fun apply(host: View, containerWidthPx: Int, geometry: KeyboardGeometry, density: Float) {
        val lp = (host.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.width = geometry.widthPx(containerWidthPx)
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.BOTTOM or Gravity.START
        lp.leftMargin = geometry.leftMarginPx(containerWidthPx)
        lp.rightMargin = 0
        lp.bottomMargin = geometry.raisePx(density)
        host.layoutParams = lp
    }
}
