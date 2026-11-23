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
import com.mbt.keyboard.clipboard.ClipboardHistory
import com.mbt.keyboard.clipboard.PrefsClipboardStore
import com.mbt.keyboard.emoji.Category
import com.mbt.keyboard.emoji.EmojiData

/**
 * 이모티콘·이모지 패널. 키보드 자리에 겹쳐서 표시된다.
 *
 * - 이모티콘 탭: 최근 쓴 줄 + 분류별 한 줄 이어쓰기, 탭하면 바로 입력
 * - 이모지 탭: 카테고리 아이콘 + 이모지 격자
 * - 오른쪽 위 ✕: 닫고 키보드로 돌아가기
 */
@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val listener: Listener,
    private val theme: KeyboardTheme = KeyboardTheme.current(context),
) : LinearLayout(context) {

    interface Listener {
        fun onInsert(text: String)
        fun onClose()
    }

    private val density = resources.displayMetrics.density

    private val emoticons: List<Category> by lazy { EmojiData.emoticons(context) }
    private val emojis: List<Category> by lazy { EmojiData.emoji(context) }

    /** 최근 쓴 이모티콘 (최신순, 최대 [RECENT_MAX]개). 이모티콘 탭 맨 위 "최근" 줄에 보인다 */
    private val recent = ClipboardHistory(PrefsClipboardStore(context, "recent_emoticons"), max = RECENT_MAX)

    private var tab = TAB_EMOTICON
    private var emojiCategory = 0

    /** 이모티콘 탭에서 분류별 블록 (바로가기 칩이 이 위치로 스크롤한다) */
    private val blocks = ArrayList<View>()

    private val tabEmoticon = tabView("이모티콘") { selectTab(TAB_EMOTICON) }
    private val tabEmoji = tabView("이모지") { selectTab(TAB_EMOJI) }
    private val container = LinearLayout(context).apply { orientation = VERTICAL }

    /** 분류 바로가기 칩 줄 (이모티콘 탭에서만 보인다) */
    private val indexChips = LinearLayout(context).apply {
        orientation = HORIZONTAL
        setPadding(dp(6), dp(2), dp(6), dp(2))
    }
    private val indexBar = android.widget.HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(indexChips, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
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
        addView(indexBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(38)))

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
        blocks.clear()
        scroll.scrollTo(0, 0)
        indexBar.visibility = if (tab == TAB_EMOTICON) View.VISIBLE else View.GONE
        if (tab == TAB_EMOTICON) showEmoticonsGrouped() else showEmojiGrid()
    }

    /**
     * 이모티콘: 분류별로 한 화면에 이어서 보여준다. 한 번만 눌러 바로 입력된다.
     *   행복 (*´∪`) (´▽`) (*^▽^*) …
     *   화남 (`Д´) ヽ(`Д´)ﾉ (╬ ಠ益ಠ) …
     * 위쪽 칩(행복·화남·…)을 누르면 그 분류로 바로 스크롤한다.
     */
    private fun showEmoticonsGrouped() {
        indexChips.removeAllViews()
        // (이름, 이모티콘들, 색): 최근 줄이 있으면 맨 앞
        val groups = ArrayList<Triple<String, List<String>, Int>>()
        val recentItems = recent.items()
        if (recentItems.isNotEmpty()) groups += Triple(RECENT_LABEL, recentItems, RECENT_COLOR)
        emoticons.forEachIndexed { i, c -> groups += Triple(c.label, c.items, PALETTE[i % PALETTE.size]) }

        groups.forEachIndexed { i, (label, _, color) ->
            indexChips.addView(
                TextView(context).apply {
                    text = label
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(theme.text)
                    setPadding(dp(10), 0, dp(10), 0)
                    background = rounded(0, color, 14f)
                    setOnClickListener { blocks.getOrNull(i)?.let { b -> scroll.smoothScrollTo(0, b.top) } }
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) },
            )
        }
        groups.forEach { (label, items, color) ->
            val block = flow()
            // 분류 이름: 색을 채운 알약. 이 줄 맨 앞에 오고, 이모티콘들이 이어서 나열된다
            block.addView(
                TextView(context).apply {
                    text = label
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(theme.text)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = rounded(withAlpha(color, 70), 0, 20f)
                    isSingleLine = true
                },
            )
            items.forEach { e ->
                block.addView(pill(e, color, 15f) {
                    recent.add(e)           // 화면은 그대로 두고(튀지 않게) 다음에 열 때 최근 줄에 반영
                    listener.onInsert(e)
                })
            }
            blocks += block
            container.addView(block)
        }
    }

    /** 패널을 다시 열 때 호출: 최근 줄을 갱신하고 맨 위로 돌아간다 */
    fun refresh() = render()

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

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
        const val RECENT_MAX = 12
        const val RECENT_LABEL = "최근"
        val RECENT_COLOR = 0xFFFFB300.toInt()

        /** 알약 테두리 색 (빨강·초록·보라·하늘·갈색 순환) */
        val PALETTE = intArrayOf(
            0xFFE57373.toInt(), 0xFF81C784.toInt(), 0xFFB39DDB.toInt(), 0xFF4DD0E1.toInt(), 0xFFBCAAA4.toInt(),
        )
    }
}
