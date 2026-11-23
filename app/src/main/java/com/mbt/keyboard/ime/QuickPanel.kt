package com.mbt.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 엔터를 꾹 누르면 뜨는 패널. 위쪽 탭으로 두 화면을 오간다.
 *
 * - **상용문구**: 네모난 칸이 3열로 배치되고 행은 개수만큼 늘어난다(스크롤). 칸을 탭하면 입력
 * - **최근기록**: 최근에 복사한 글자 목록(최신이 위). 항목을 탭하면 입력, 항목 오른쪽 ✕로 삭제,
 *   목록 위 "모두 지우기"로 전체 삭제
 *
 * 입력하면 키보드로 돌아가고, 오른쪽 위 ✕로도 닫을 수 있다.
 */
@SuppressLint("ViewConstructor")
class QuickPanel(
    context: Context,
    private val listener: Listener,
    private val theme: KeyboardTheme = KeyboardTheme.current(context),
) : LinearLayout(context) {

    interface Listener {
        /** 상용문구·최근기록 항목을 눌러 입력 */
        fun onInsert(text: String)
        /** 최근기록 항목 하나 삭제 */
        fun onDeleteClip(text: String)
        /** 최근기록 모두 삭제 */
        fun onClearClips()
        fun onClose()
        /** 탭을 바꿨다 (다음에 열 때 같은 탭으로 열기 위한 기억용) */
        fun onTabChanged(tab: Int) {}
    }

    private val density = resources.displayMetrics.density

    private var phrases: List<String> = emptyList()
    private var clips: List<String> = emptyList()

    /** 지금 보이는 탭 ([TAB_PHRASES] 또는 [TAB_CLIPS]) */
    var tab = TAB_PHRASES
        private set

    private val container = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(10), dp(6), dp(10), dp(8))
    }
    private val scroll = ScrollView(context).apply {
        addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    val phrasesTab = tabView("상용문구") { select(TAB_PHRASES, notify = true) }
    val clipsTab = tabView("최근기록") { select(TAB_CLIPS, notify = true) }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        val header = LinearLayout(context).apply { orientation = HORIZONTAL }
        header.addView(phrasesTab, LayoutParams(0, dp(40), 1f))
        header.addView(clipsTab, LayoutParams(0, dp(40), 1f))
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
        render()
    }

    /** 하단 내비게이션 바 영역만큼 아래 여백 */
    fun setBottomInset(px: Int) = setPadding(0, 0, 0, px)

    /** 패널을 열 때 두 목록과 처음 보여줄 탭을 넘긴다 */
    fun open(phrases: List<String>, clips: List<String>, startTab: Int) {
        this.phrases = phrases
        this.clips = clips
        select(if (startTab == TAB_CLIPS) TAB_CLIPS else TAB_PHRASES, notify = false)
    }

    /** 최근기록이 바뀌었을 때(삭제 등) 목록만 갱신 */
    fun setClips(clips: List<String>) {
        this.clips = clips
        if (tab == TAB_CLIPS) render()
    }

    private fun select(t: Int, notify: Boolean) {
        tab = t
        render()
        if (notify) listener.onTabChanged(t)
    }

    // ---------------------------------------------------------------- 화면

    private fun render() {
        styleTab(phrasesTab, tab == TAB_PHRASES)
        styleTab(clipsTab, tab == TAB_CLIPS)
        container.removeAllViews()
        scroll.scrollTo(0, 0)
        if (tab == TAB_PHRASES) renderPhrases() else renderClips()
    }

    private fun renderPhrases() {
        if (phrases.isEmpty()) {
            container.addView(guide("저장된 상용구가 없어요\n앱 설정 화면에서 추가해 주세요"))
            return
        }
        phrases.chunked(COLUMNS).forEach { rowItems ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                isBaselineAligned = false
            }
            repeat(COLUMNS) { i ->
                val text = rowItems.getOrNull(i)
                val cell: View = if (text != null) phraseCell(text) else View(context)
                // 가로는 3등분(열 정렬), 세로는 MATCH_PARENT: 같은 줄 칸들은 가장 큰 칸 높이로 통일(행 정렬)
                row.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            container.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun renderClips() {
        if (clips.isEmpty()) {
            container.addView(guide("복사한 내용이 여기에 쌓여요\n(설정에서 끌 수 있어요)"))
            return
        }
        // 목록 맨 위 오른쪽: 모두 지우기
        container.addView(
            TextView(context).apply {
                text = "모두 지우기"
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setTextColor(theme.text)
                alpha = 0.7f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { listener.onClearClips() }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(32)),
        )
        clips.forEach { container.addView(clipRow(it)) }
    }

    private fun guide(message: String) = TextView(context).apply {
        text = message
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        alpha = 0.7f
        setPadding(dp(16), dp(32), dp(16), dp(32))
    }

    /** 네모난 칸 하나: 최소 높이 [CELL_MIN_HEIGHT_DP], 글이 길면 최대 3줄까지 늘어난다 */
    private fun phraseCell(text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(theme.text)
        gravity = Gravity.CENTER
        minHeight = dp(CELL_MIN_HEIGHT_DP)
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = box()
        setOnClickListener { listener.onInsert(text) }
    }

    private fun clipRow(text: String): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = box()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        }
        row.addView(
            TextView(context).apply {
                this.text = text
                textSize = 14f
                setTextColor(theme.text)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                minHeight = dp(44)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
                setOnClickListener { listener.onInsert(text) }
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            TextView(context).apply {
                this.text = "✕"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(theme.text)
                alpha = 0.6f
                setOnClickListener { listener.onDeleteClip(text) }
            },
            LayoutParams(dp(44), dp(44)),
        )
        return row
    }

    /** 불투명 칸 배경 */
    private fun box() = GradientDrawable().apply {
        cornerRadius = 10 * density
        setColor(theme.key)
        setStroke(dp(1), theme.keyPressed)
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

    private fun dp(v: Int) = (v * density).toInt()

    companion object {
        const val TAB_PHRASES = 0
        const val TAB_CLIPS = 1
        private const val COLUMNS = 3
        private const val CELL_MIN_HEIGHT_DP = 52
    }
}
