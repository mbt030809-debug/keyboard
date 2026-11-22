package com.mbt.keyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/** 자식 뷰를 가로로 채우다가 넘치면 다음 줄로 넘기는 레이아웃 (알약·이모지 목록용). */
class FlowLayout(context: Context) : ViewGroup(context) {

    var hGap = 0
    var vGap = 0

    private val positions = ArrayList<IntArray>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        val inner = maxW - paddingLeft - paddingRight
        positions.clear()
        var x = 0
        var y = 0
        var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) { positions += intArrayOf(0, 0); continue }
            c.measure(
                MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val cw = c.measuredWidth
            val ch = c.measuredHeight
            if (x > 0 && x + cw > inner) {
                x = 0
                y += lineH + vGap
                lineH = 0
            }
            positions += intArrayOf(x, y)
            x += cw + hGap
            lineH = max(lineH, ch)
        }
        setMeasuredDimension(maxW, paddingTop + y + lineH + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            val p = positions[i]
            val left = paddingLeft + p[0]
            val top = paddingTop + p[1]
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
        }
    }
}
