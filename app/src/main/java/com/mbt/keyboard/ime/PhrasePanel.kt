package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 상용구 패널. 엔터를 꾹 누르면 키보드 자리에 뜬다.
 * 네모난 칸이 3열로 배치되고, 상용구가 늘어나면 행이 계속 늘어난다(위아래 스크롤).
 * 칸을 탭하면 입력하고 키보드로 돌아간다.
 */
@SuppressLint("ViewConstructor")
class PhrasePanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onInsert(text: String)
        fun onBackspace()
        fun onClose()
    }

    private val theme = KeyboardTheme.system(context)
    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private val container = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private val scroll = ScrollView(context).apply {
        addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener.onBackspace()
            handler.postDelayed(this, 45L)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        addView(
            TextView(context).apply {
                text = "상용구"
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(theme.accent)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(40)),
        )
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(barButton("ABC").apply { setOnClickListener { listener.onClose() } }, LayoutParams(0, dp(44), 1f))
        bar.addView(backspaceButton(), LayoutParams(0, dp(44), 1f))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            repeat(COLUMNS) { i ->
                val text = rowItems.getOrNull(i)
                val cell: android.view.View = if (text != null) cell(text) else android.view.View(context)
                row.addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            container.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    /** 네모난 칸 하나 */
    private fun cell(text: String) = SquareTextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(theme.text)
        gravity = Gravity.CENTER
        maxLines = 4
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(theme.key)
            setStroke(dp(1), theme.keyPressed)
        }
        setOnClickListener { listener.onInsert(text) }
    }

    /** 가로 길이에 맞춰 높이가 최소 비슷한 네모 칸이 되는 텍스트 뷰 (글이 길면 더 커진다) */
    private class SquareTextView(context: Context) : TextView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val square = (measuredWidth * 0.8f).toInt()
            if (measuredHeight < square) setMeasuredDimension(measuredWidth, square)
        }
    }

    private fun barButton(label: String) = TextView(context).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundColor(theme.specialKey)
    }

    /** ⌫: 누르는 즉시 한 번, 길게 누르면 반복 */
    @SuppressLint("ClickableViewAccessibility")
    private fun backspaceButton() = barButton("⌫").apply {
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    listener.onBackspace()
                    handler.postDelayed(repeatRunnable, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(repeatRunnable)
            }
            true
        }
    }

    private fun dp(v: Int) = (v * density).toInt()

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(repeatRunnable)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val COLUMNS = 3
    }
}
