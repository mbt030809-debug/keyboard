package com.mbt.keyboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.mbt.keyboard.ime.FnTab
import com.mbt.keyboard.ime.KeyboardFrame
import com.mbt.keyboard.ime.KeyboardHost
import com.mbt.keyboard.ime.KeyboardView
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.ResizeDiagram
import com.mbt.keyboard.settings.Prefs
import com.mbt.keyboard.settings.ScreenProfile

/**
 * 키보드 크기·위치 조정 화면 (미리보기 고정 + 항목 탭).
 * 위에 실제 키보드와 똑같은 미리보기가 고정되고, 아래 ①~⑦ 탭을 넘기며 항목마다 그림과 설명, 조절 버튼을 보여준다.
 * 우선순위: ① 전체 크기 → ② 여백 → ③ 키 간격 → ④ 숫자줄 크기 → ⑤ 숫자줄 여백 → ⑥ 전복붙 높이. 앞 항목은 뒤 항목을 바꿔도 변하지 않는다.
 * 폴드를 접었을 때와 펼쳤을 때는 각각 따로 저장된다 (지금 기기 상태 기준).
 */
class ResizeActivity : AppCompatActivity() {
    /** 키보드와 같은 기준(기기 화면 전체 크기)으로 접힘/펼침을 판정해야 저장한 값이 키보드에 적용된다 */
    private val large get() = ScreenProfile.isLarge(this)
    private val landscape get() = ScreenProfile.isLandscape(this)
    private lateinit var geometry: KeyboardGeometry
    private lateinit var host: FrameLayout
    private lateinit var keyboard: KeyboardView
    private lateinit var fnTab: FnTab

    /** 갈라진 자판이 적용 중인가 (펼침 화면 + 설정 켬). 아닐 때는 "갈라진 정도" 행을 숨긴다 */
    private var splitActive = false

    /** 지금 보이는 항목의 [－ 값 ＋] 줄 (표시 갱신용) */
    private class Stepper(val value: TextView, val minus: View, val plus: View, val text: () -> String,
                          val canMinus: () -> Boolean, val canPlus: () -> Boolean)
    private val steppers = ArrayList<Stepper>()

    /** 항목(탭) 하나: 제목·배지·설명·그림·조절 버튼 */
    private inner class Page(
        val chip: Int, val title: Int, val badge: Int, val desc: Int, val part: ResizeDiagram.Part,
        val note: Int = 0, val build: () -> Unit,
    )
    private lateinit var pages: List<Page>
    private var pageIndex = 0

    /** 미리보기가 놓이는 실제 가로 폭(px). 측정되기 전에는 화면 폭으로 대신한다 */
    private var containerWidthPx = 0
    private val density get() = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resize)
        // 위치 이동은 없앴다 (크기와 여백으로만 맞춘다): 예전에 저장된 이동값은 무시
        geometry = Prefs.geometry(this, large, landscape).copy(xDp = 0, raiseDp = 0)

        // 상태바 영역만 비운다. 아래쪽 내비게이션 바 여백은 키보드 뷰가 실제와 똑같이 스스로 처리한다.
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 미리보기: 실제 키보드와 같은 뷰, 같은 틀(KeyboardHost)
        // 펼침 화면은 설정 스위치, 접힘 화면은 가로 모드일 때만 갈라진 정도를 조절할 수 있다
        splitActive = if (large) Prefs.splitOnLargeScreen(this) else landscape
        keyboard = KeyboardView(this).also {
            it.showHitZones = true          // 조절하는 동안 줄 인식 경계를 점선으로 보여준다
            it.numberLeft = Prefs.numberKeysLeft(this)
            it.keyboardLayout = Layouts.byId(Prefs.koreanLayout(this))
            it.split = splitActive && (large || geometry.gapPct > 0)
        }
        host = FrameLayout(this).apply {
            addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val previewContainer = findViewById<FrameLayout>(R.id.previewContainer)
        previewContainer.addView(
            host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        // 실제 키보드처럼 전체·복사·붙임 반투명 탭도 미리보기에 같이 보인다
        fnTab = FnTab(this, keyboard).also { it.visibility = View.GONE }
        keyboard.onRelayoutRequested = { fnTab.requestLayout(); fnTab.invalidate() }
        previewContainer.addView(
            fnTab, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP or android.view.Gravity.START),
        )
        containerWidthPx = resources.displayMetrics.widthPixels
        // 실제 폭이 정해지거나 바뀌면(분할 화면, 폴드 펼침 등) 그 폭으로 미리보기를 다시 계산
        previewContainer.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val w = r - l
            if (w > 0 && w != containerWidthPx) {
                containerWidthPx = w
                previewContainer.post { refresh() }
            }
        }

        pages = listOf(
            Page(R.string.resize_page_size, R.string.resize_title_size, R.string.resize_badge_base, R.string.resize_desc_size, ResizeDiagram.Part.SIZE) {
                addStepper(R.string.resize_width, { geometry = geometry.widerBy(it) }, { "${geometry.widthPct}%" },
                    { geometry.widthPct > KeyboardGeometry.WIDTH_MIN }, { geometry.widthPct < KeyboardGeometry.WIDTH_MAX })
                addStepper(R.string.resize_height, { geometry = geometry.tallerBy(it) }, { "${geometry.heightPct}%" },
                    { geometry.heightPct > KeyboardGeometry.HEIGHT_MIN }, { geometry.heightPct < KeyboardGeometry.HEIGHT_MAX })
                if (splitActive) addStepper(R.string.resize_gap, { geometry = geometry.gapWiderBy(it) },
                    { if (!large && geometry.gapPct == 0) getString(R.string.resize_gap_off) else "${geometry.gapPct}%" },
                    { geometry.gapPct > KeyboardGeometry.GAP_MIN }, { geometry.gapPct < KeyboardGeometry.GAP_MAX })
            },
            Page(R.string.resize_page_pad, R.string.resize_title_pad, R.string.resize_badge_1, R.string.resize_desc_pad, ResizeDiagram.Part.PAD) {
                addStepper(R.string.resize_pad_top, { geometry = geometry.padTopBy(it) }, { "${geometry.padTopDp}dp" },
                    { geometry.padTopDp > KeyboardGeometry.PAD_MIN }, { geometry.padTopDp < KeyboardGeometry.PAD_MAX })
                addStepper(R.string.resize_pad_bottom, { geometry = geometry.padBottomBy(it) }, { "${geometry.padBottomDp}dp" },
                    { geometry.padBottomDp > KeyboardGeometry.PAD_MIN }, { geometry.padBottomDp < KeyboardGeometry.PAD_MAX })
                addStepper(R.string.resize_pad_left, { geometry = geometry.padLeftBy(it) }, { "${geometry.padLeftDp}dp" },
                    { geometry.padLeftDp > KeyboardGeometry.PAD_MIN }, { geometry.padLeftDp < KeyboardGeometry.PAD_MAX })
                addStepper(R.string.resize_pad_right, { geometry = geometry.padRightBy(it) }, { "${geometry.padRightDp}dp" },
                    { geometry.padRightDp > KeyboardGeometry.PAD_MIN }, { geometry.padRightDp < KeyboardGeometry.PAD_MAX })
            },
            Page(R.string.resize_page_gap, R.string.resize_title_gap, R.string.resize_badge_12, R.string.resize_desc_gap, ResizeDiagram.Part.GAP) {
                addStepper(R.string.resize_key_gap, { geometry = geometry.keyGapBy(it) }, { "${geometry.keyGapDp}dp" },
                    { geometry.keyGapDp > KeyboardGeometry.KEY_GAP_MIN }, { geometry.keyGapDp < KeyboardGeometry.KEY_GAP_MAX })
                addStepper(R.string.resize_row_gap, { geometry = geometry.rowGapBy(it) }, { "${geometry.rowGapDp}dp" },
                    { geometry.rowGapDp > KeyboardGeometry.ROW_GAP_MIN }, { geometry.rowGapDp < KeyboardGeometry.ROW_GAP_MAX })
            },
            Page(R.string.resize_page_numrow, R.string.resize_title_numrow, R.string.resize_badge_123, R.string.resize_desc_numrow, ResizeDiagram.Part.NUMROW) {
                // 숫자가 왼쪽 띠에 있으면(모아키·숫자 왼쪽 옵션) "숫자줄 크기"는 띠의 가로 길이, 아니면 줄 높이
                addStepper(R.string.resize_num_height, { geometry = geometry.numHeightBy(it) }, { "${geometry.numHeightPct}%" },
                    { geometry.numHeightPct > KeyboardGeometry.NUM_HEIGHT_MIN }, { geometry.numHeightPct < KeyboardGeometry.NUM_HEIGHT_MAX })
                // 맨 아래 줄(스페이스바 줄) 키 높이: 늘리거나 줄이거나 (전체 크기는 고정이라 다른 줄이 그만큼 변한다)
                addStepper(R.string.resize_space_row, { geometry = geometry.spaceRowBy(it) }, { "${geometry.spaceRowPct}%" },
                    { geometry.spaceRowPct > KeyboardGeometry.SPACE_ROW_MIN }, { geometry.spaceRowPct < KeyboardGeometry.SPACE_ROW_MAX })
            },
            Page(R.string.resize_page_numgap, R.string.resize_title_numgap, R.string.resize_badge_1234, R.string.resize_desc_numgap, ResizeDiagram.Part.NUMGAP) {
                addStepper(R.string.resize_num_row_gap, { geometry = geometry.numRowGapBy(it) }, { "${geometry.numRowGapDp}dp" },
                    { geometry.numRowGapDp > KeyboardGeometry.NUM_ROW_GAP_MIN }, { geometry.numRowGapDp < KeyboardGeometry.NUM_ROW_GAP_MAX })
            },
            Page(R.string.resize_page_tab, R.string.resize_title_tab, R.string.resize_badge_tab, R.string.resize_desc_tab, ResizeDiagram.Part.TAB, note = R.string.resize_note_tab) {
                addStepper(R.string.resize_tab_height, { geometry = geometry.fnTabHeightBy(it) }, { "${geometry.fnTabHeightPct}%" },
                    { geometry.fnTabHeightPct > KeyboardGeometry.FN_TAB_HEIGHT_MIN }, { geometry.fnTabHeightPct < KeyboardGeometry.FN_TAB_HEIGHT_MAX })
                addStepper(R.string.resize_tab_pad, { geometry = geometry.fnTabPadBy(it) }, { "${geometry.fnTabPadDp}dp" },
                    { geometry.fnTabPadDp > KeyboardGeometry.FN_TAB_PAD_MIN }, { geometry.fnTabPadDp < KeyboardGeometry.FN_TAB_PAD_MAX })
            },
            Page(R.string.resize_page_hit, R.string.resize_title_hit, R.string.resize_badge_hit, R.string.resize_desc_hit, ResizeDiagram.Part.HIT) {
                addStepper(R.string.resize_hit0, { geometry = geometry.hit0By(it) }, { signedDp(geometry.hit0Dp) },
                    { geometry.hit0Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit0Dp < KeyboardGeometry.HIT_MAX })
                addStepper(R.string.resize_hit1, { geometry = geometry.hit1By(it) }, { signedDp(geometry.hit1Dp) },
                    { geometry.hit1Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit1Dp < KeyboardGeometry.HIT_MAX })
                addStepper(R.string.resize_hit2, { geometry = geometry.hit2By(it) }, { signedDp(geometry.hit2Dp) },
                    { geometry.hit2Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit2Dp < KeyboardGeometry.HIT_MAX })
                addStepper(R.string.resize_hit3, { geometry = geometry.hit3By(it) }, { signedDp(geometry.hit3Dp) },
                    { geometry.hit3Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit3Dp < KeyboardGeometry.HIT_MAX })
            },
        )

        // 항목 탭(칩)
        val chips = findViewById<ChipGroup>(R.id.pageChips)
        pages.forEachIndexed { i, page ->
            val chip = layoutInflater.inflate(R.layout.chip_page, chips, false) as Chip
            chip.id = View.generateViewId()
            chip.setText(page.chip)
            chip.tag = i
            chips.addView(chip)
        }
        chips.setOnCheckedStateChangeListener { group, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            showPage(group.findViewById<View>(id).tag as Int)
        }
        findViewById<View>(R.id.prevButton).setOnClickListener { selectPage(pageIndex - 1) }
        findViewById<View>(R.id.nextButton).setOnClickListener { selectPage(pageIndex + 1) }


         findViewById<MaterialButton>(R.id.resetButton).setOnClickListener {
            geometry = KeyboardGeometry()
            Prefs.setKeyOpacityPercent(this, Prefs.KEY_OPACITY_DEFAULT)
            refresh()
        }
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener { finish() }

        selectPage(0)
        refresh()
    }

    private fun signedDp(v: Int) = if (v > 0) "+${v}dp" else "${v}dp"

    /** 탭(칩)을 눌러 고른 것과 같게 항목 [index]로 이동 */
    private fun selectPage(index: Int) {
        val i = index.coerceIn(0, pages.size - 1)
        val chips = findViewById<ChipGroup>(R.id.pageChips)
        chips.check(chips.getChildAt(i).id)
        showPage(i)
    }

    /** 항목 [index]의 제목·설명·그림·조절 버튼을 보여준다 */
    private fun showPage(index: Int) {
        pageIndex = index
        val page = pages[index]
        findViewById<TextView>(R.id.pageTitle).setText(page.title)
        findViewById<TextView>(R.id.pageBadge).setText(page.badge)
        findViewById<TextView>(R.id.pageDesc).setText(page.desc)
        findViewById<ResizeDiagram>(R.id.diagram).part = page.part
        findViewById<TextView>(R.id.pageNote).apply {
            if (page.note != 0) { setText(page.note); visibility = View.VISIBLE } else visibility = View.GONE
        }
        findViewById<ViewGroup>(R.id.pageControls).removeAllViews()
        steppers.clear()
        page.build()
        findViewById<View>(R.id.prevButton).isEnabled = index > 0
        findViewById<View>(R.id.nextButton).isEnabled = index < pages.size - 1
        refresh()
    }

    /** [－ 값 ＋] 한 줄을 만들어 지금 항목의 조절 영역에 붙인다 */
    private fun addStepper(
        labelRes: Int, change: (Int) -> Unit, text: () -> String, canMinus: () -> Boolean, canPlus: () -> Boolean,
    ) {
        val container = findViewById<ViewGroup>(R.id.pageControls)
        val row = layoutInflater.inflate(R.layout.row_stepper, container, false)
        row.findViewById<TextView>(R.id.stepLabel).setText(labelRes)
        val minus = row.findViewById<View>(R.id.stepMinus)
        val plus = row.findViewById<View>(R.id.stepPlus)
        container.addView(row)
        bindButton(minus) { change(-1) }
        bindButton(plus) { change(1) }
        steppers += Stepper(row.findViewById(R.id.stepValue), minus, plus, text, canMinus, canPlus)
    }

    /** 눌렀을 때 한 번, 계속 누르고 있으면 반복해서 [change]를 실행한다 */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindButton(button: View, change: () -> Unit) {
        var ticks = 0
        val repeat = object : Runnable {
            override fun run() {
                // 끝에 닿아 버튼이 비활성이 되면 반복을 멈춘다 (값이 계속 쌓이지 않게)
                if (!button.isEnabled) return
                // 1% 단위라 오래 누르면 점점 빨라진다 (1초쯤 지나면 틱당 2칸, 이후 최대 5칸)
                val steps = (1 + ticks / 12).coerceAtMost(5)
                repeat(steps) { change() }
                refresh()
                ticks++
                handler.postDelayed(this, 70L)
            }
        }
        button.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!v.isEnabled) return@setOnTouchListener false
                    v.isPressed = true
                    ticks = 0
                    change(); refresh()
                    handler.postDelayed(repeat, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
    }

    /** 값을 저장하고 미리보기·표시를 갱신 */
    private fun refresh() {
        Prefs.setGeometry(this, large, geometry, landscape)
        KeyboardHost.apply(host, containerWidthPx, geometry, density)
        keyboard.heightScale = geometry.heightScale
        keyboard.splitGapFraction = geometry.gapFraction
        keyboard.keyOpacity = Prefs.keyOpacityPercent(this) / 100f
        keyboard.bgOpacity = Prefs.bgOpacityPercent(this) / 100f
        keyboard.mirrorEnabled = Prefs.mirrorEnabled(this)
        keyboard.mirrorStrength = Prefs.mirrorStrengthPercent(this) / 100f
        keyboard.split = splitActive && (large || geometry.gapPct > 0)
        keyboard.frame = KeyboardFrame(
            geometry.padLeftDp, geometry.padTopDp, geometry.padRightDp, geometry.padBottomDp, geometry.keyGapDp,
            geometry.rowGapDp, geometry.numHeightScale, geometry.numRowGapDp, geometry.hitShifts, geometry.spaceRowScale,
        )
        val tabOn = Prefs.fnTabEnabled(this) && keyboard.hasSideStrip
        keyboard.fnTab = tabOn
        keyboard.fnTabHeightScale = geometry.fnTabHeightPct / 100f
        keyboard.fnTabPadDp = geometry.fnTabPadDp
        fnTab.alphaPercent = Prefs.fnTabAlphaPercent(this)
        KeyboardHost.applyFnTab(host, fnTab, keyboard, tabOn)
        steppers.forEach {
            it.value.text = it.text()
            it.minus.isEnabled = it.canMinus()
            it.plus.isEnabled = it.canPlus()
        }

        findViewById<TextView>(R.id.profileInfo).text =
            getString(
                when {
                    large && landscape -> R.string.resize_profile_large_land
                    large -> R.string.resize_profile_large
                    landscape -> R.string.resize_profile_normal_land
                    else -> R.string.resize_profile_normal
                },
            )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
    }
}
