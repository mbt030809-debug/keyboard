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
        lp.leftMargin = geometry.leftMarginPx(containerWidthPx, density)
        lp.rightMargin = 0
        lp.bottomMargin = geometry.raisePx(density)
        host.layoutParams = lp
    }

    /**
     * 전체·복사·붙임 반투명 탭을 키보드 틀 위에 맞춘다: [on]이면 틀 위쪽에 탭 높이만큼 자리를 비우고 탭을 왼쪽 끝에 붙인다.
     * 탭과 틀은 같은 [FrameLayout]의 자식이어야 한다. 실제 키보드와 크기 조정 미리보기가 같이 쓴다.
     */
    fun applyFnTab(host: View, tab: FnTab, keyboard: KeyboardView, on: Boolean, keyboardVisible: Boolean = true) {
        val hostLp = host.layoutParams as FrameLayout.LayoutParams
        val top = if (on) (keyboard.fnTabHeightPx() + keyboard.fnTabGapPx()).toInt().coerceAtLeast(1) else 0
        if (hostLp.topMargin != top) { hostLp.topMargin = top; host.layoutParams = hostLp }
        val tabLp = tab.layoutParams as FrameLayout.LayoutParams
        if (tabLp.leftMargin != hostLp.leftMargin) { tabLp.leftMargin = hostLp.leftMargin; tab.layoutParams = tabLp }
        tab.visibility = when {
            !on -> View.GONE
            keyboardVisible -> View.VISIBLE
            else -> View.INVISIBLE      // 이모지·상용구 패널이 떠 있는 동안은 숨기되 자리는 유지
        }
        tab.requestLayout()
        tab.invalidate()
    }
}
