package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mbt.keyboard.emoji.Category
import com.mbt.keyboard.emoji.EmojiData

/**
 * 이모티콘·이모지 패널. 키보드 자리에 겹쳐서 표시된다.
 *
 * - 이모티콘 탭: 감정·동물 등 카테고리 알약 → 누르면 목록 → 탭하면 입력
 * - 이모지 탭: 카테고리 아이콘 + 이모지 격자
 * - 오른쪽 위 ✕: 닫고 키보드로 돌아가기
 */
@SuppressLint("ViewConstructor")
class EmojiPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onInsert(text: String)
        fun onClose()
    }

    private val theme = KeyboardTheme.system(context)
    private val density = resources.displayMetrics.density

    private val emoticons: List<Category> by lazy { EmojiData.emoticons(context) }
    private val emojis: List<Category> by lazy { EmojiData.emoji(context) }

    private var tab = TAB_EMOTICON
    private var emoticonCategory = -1       // -1: 카테고리 목록
    private var emojiCategory = 0

    private val tabEmoticon = tabView("이모티콘") { selectTab(TAB_EMOTICON) }
    private val tabEmoji = tabView("이모지") { selectTab(TAB_EMOJI) }
    private val container = LinearLayout(context).apply { orientation = VERTICAL }
    private val scroll = ScrollView(context).apply {
        isFillViewport = false
        addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        val tabs = LinearLayout(context).apply { orientation = HORIZONTAL }
        tabs.addView(tabEmoticon, LayoutParams(0, dp(40), 1f))
        tabs.addView(tabEmoji, LayoutParams(0, dp(40), 1f))
        tabs.addView(closeButton(), LayoutParams(dp(48), dp(40)))
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        render()
    }

    /** 하단 내비게이션 바 영역만큼 아래 여백 */
    fun setBottomInset(px: Int) = setPadding(0, 0, 0, px)

    // ---------------------------------------------------------------- 화면 구성

    private fun selectTab(t: Int) {
        tab = t
        render()
    }

    private fun render() {
        styleTab(tabEmoticon, tab == TAB_EMOTICON)
        styleTab(tabEmoji, tab == TAB_EMOJI)
        container.removeAllViews()
        scroll.scrollTo(0, 0)
        if (tab == TAB_EMOTICON) {
            if (emoticonCategory < 0) showEmoticonCategories() else showEmoticonItems(emoticonCategory)
        } else {
            showEmojiGrid()
        }
    }

    private fun showEmoticonCategories() {
        val flow = flow()
        emoticons.forEachIndexed { i, c ->
            flow.addView(pill("${c.sample} ${c.name}", PALETTE[i % PALETTE.size], 13f) {
                emoticonCategory = i
                render()
            })
        }
        container.addView(flow)
    }

    private fun showEmoticonItems(index: Int) {
        val cat = emoticons[index]
        container.addView(
            TextView(context).apply {
                text = "‹  ${cat.name}"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(theme.text)
                setPadding(dp(14), dp(12), dp(14), dp(6))
                setOnClickListener { emoticonCategory = -1; render() }
            },
        )
        val flow = flow()
        cat.items.forEach { e ->
            flow.addView(pill(e, PALETTE[index % PALETTE.size], 15f) { listener.onInsert(e) })
        }
        container.addView(flow)
    }

    private fun showEmojiGrid() {
        // 카테고리 아이콘 줄
        val chips = flow()
        emojis.forEachIndexed { i, c ->
            chips.addView(
                TextView(context).apply {
                    text = c.sample
                    textSize = 20f
                    gravity = Gravity.CENTER
                    alpha = if (i == emojiCategory) 1f else 0.45f
                    background = if (i == emojiCategory) rounded(theme.keyPressed, 0, 10f) else null
                    setOnClickListener { emojiCategory = i; render() }
                },
                LayoutParams(dp(44), dp(40)),
            )
        }
        container.addView(chips)

        val flow = flow()
        emojis[emojiCategory].items.forEach { e ->
            flow.addView(
                TextView(context).apply {
                    text = e
                    textSize = 26f
                    gravity = Gravity.CENTER
                    setOnClickListener { listener.onInsert(e) }
                },
                LayoutParams(dp(44), dp(44)),
            )
        }
        container.addView(flow)
    }

    // ---------------------------------------------------------------- 부품

    private fun flow() = FlowLayout(context).apply {
        hGap = dp(6); vGap = dp(8)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    /** 테두리 색이 있는 알약 */
    private fun pill(text: String, color: Int, sizeSp: Float, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(theme.text)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = rounded(theme.key, color, 20f)      // 불투명 배경
        isSingleLine = true
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Float) = GradientDrawable().apply {
        cornerRadius = radiusDp * density
        if (fill != 0) setColor(fill)
        if (stroke != 0) setStroke(dp(2), stroke)
    }

    private fun tabView(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun styleTab(v: TextView, selected: Boolean) {
        v.setTextColor(if (selected) theme.accent else theme.text)
        v.alpha = if (selected) 1f else 0.6f
        v.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    /** 패널 닫기 (키보드로 돌아가기) */
    private fun closeButton() = TextView(context).apply {
        text = "✕"
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        setOnClickListener { listener.onClose() }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private companion object {
        const val TAB_EMOTICON = 0
        const val TAB_EMOJI = 1

        /** 알약 테두리 색 (빨강·초록·보라·하늘·갈색 순환) */
        val PALETTE = intArrayOf(
            0xFFE57373.toInt(), 0xFF81C784.toInt(), 0xFFB39DDB.toInt(), 0xFF4DD0E1.toInt(), 0xFFBCAAA4.toInt(),
        )
    }
}
