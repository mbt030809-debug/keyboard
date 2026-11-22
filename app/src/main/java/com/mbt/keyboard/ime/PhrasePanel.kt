package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 상용구 패널. 엔터를 꾹 누르면 키보드 자리에 뜬다.
 * 네모난 칸이 3열로 배치되고, 상용구가 늘어나면 행이 계속 늘어난다(위아래 스크롤).
 * 칸을 탭하면 입력하고 키보드로 돌아간다. 오른쪽 위 ✕로도 닫을 수 있다.
 */
@SuppressLint("ViewConstructor")
class PhrasePanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onInsert(text: String)
        fun onClose()
    }

    private val theme = KeyboardTheme.system(context)
    private val density = resources.displayMetrics.density

    private val container = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private val scroll = ScrollView(context).apply {
        addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        // 제목 + 닫기(✕)
        val header = LinearLayout(context).apply { orientation = HORIZONTAL }
        header.addView(android.view.View(context), LayoutParams(dp(48), dp(40)))     // 제목을 가운데 두기 위한 빈칸
        header.addView(
            TextView(context).apply {
                text = "상용구"
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(theme.accent)
            },
            LayoutParams(0, dp(40), 1f),
        )
        header.addView(
            TextView(context).apply {
                text = "✕"
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(theme.text)
                setOnClickListener { listener.onClose() }
            },
            LayoutParams(dp(48), dp(40)),
        )
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** 하단 내비게이션 바 영역만큼 아래 여백 */
    fun setBottomInset(px: Int) = setPadding(0, 0, 0, px)

    /** 상용구 목록을 3열 격자로 그린다. 행 수는 개수에 따라 늘어난다. */
    fun setPhrases(phrases: List<String>) {
        container.removeAllViews()
        scroll.scrollTo(0, 0)
        if (phrases.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text = "저장된 상용구가 없어요\n앱 설정 화면에서 추가해 주세요"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(theme.text)
                    alpha = 0.7f
                    setPadding(dp(16), dp(32), dp(16), dp(32))
                },
            )
            return
        }
        phrases.chunked(COLUMNS).forEach { rowItems ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                isBaselineAligned = false
            }
            repeat(COLUMNS) { i ->
                val text = rowItems.getOrNull(i)
                val cell: android.view.View = if (text != null) cell(text) else android.view.View(context)
                // 가로는 3등분(열 정렬), 세로는 MATCH_PARENT: 같은 줄 칸들은 가장 큰 칸 높이로 통일(행 정렬)
                row.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            container.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    /** 네모난 칸 하나: 최소 높이 [CELL_MIN_HEIGHT_DP], 글이 길면 최대 3줄까지 늘어난다 */
    private fun cell(text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(theme.text)
        gravity = Gravity.CENTER
        minHeight = dp(CELL_MIN_HEIGHT_DP)
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = 10 * density
            setColor(theme.key)
            setStroke(dp(1), theme.keyPressed)
        }
        setOnClickListener { listener.onInsert(text) }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private companion object {
        const val COLUMNS = 3
        const val CELL_MIN_HEIGHT_DP = 52
    }
}
