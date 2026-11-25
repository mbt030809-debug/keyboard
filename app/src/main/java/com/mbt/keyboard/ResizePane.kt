package com.mbt.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.mbt.keyboard.ime.FnTab
import com.mbt.keyboard.ime.KeyboardFrame
import com.mbt.keyboard.ime.KeyboardHost
import com.mbt.keyboard.ime.KeyboardTheme
import com.mbt.keyboard.ime.KeyboardView
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.Prefs
import com.mbt.keyboard.settings.ResizeDiagram
import com.mbt.keyboard.settings.ScreenProfile

/**
 * 키보드 크기·여백·보조탭·인식영역 조절 UI 컨트롤러.
 * [SettingsActivity]의 '화면' 분류와 [ResizeActivity]가 동일한 레이아웃과 로직을 공유한다.
 */
class ResizePane(
    private val context: Context,
    private val root: View,
    private val layoutInflater: LayoutInflater,
) {
    private val large get() = ScreenProfile.isLarge(context)
    private val landscape get() = ScreenProfile.isLandscape(context)
    private var geometry: KeyboardGeometry = Prefs.geometry(context, large, landscape).copy(xDp = 0, raiseDp = 0)
    private val host: FrameLayout
    private val keyboard: KeyboardView
    private val fnTab: FnTab

    private var splitActive = false

    private class Stepper(
        val value: TextView,
        val minus: View,
        val plus: View,
        val text: () -> String,
        val canMinus: () -> Boolean,
        val canPlus: () -> Boolean,
    )
    private val steppers = ArrayList<Stepper>()

    private inner class Page(
        val chip: Int,
        val title: Int,
        val badge: Int,
        val desc: Int,
        val part: ResizeDiagram.Part,
        val note: Int = 0,
        val build: () -> Unit,
    )
    private val pages: List<Page>
    private var pageIndex = 0

    private var containerWidthPx = context.resources.displayMetrics.widthPixels
    private val density get() = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    init {
        splitActive = if (large) Prefs.splitOnLargeScreen(context) else landscape
        keyboard = KeyboardView(context).also {
            it.showHitZones = true
            it.theme = KeyboardTheme.current(context)
            it.numberLeft = Prefs.numberKeysLeft(context)
            it.keyboardLayout = Layouts.byId(Prefs.koreanLayout(context))
            it.split = splitActive && (large || geometry.gapPct > 0)
        }
        host = FrameLayout(context).apply {
            addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val previewContainer = root.findViewById<FrameLayout>(R.id.previewContainer)
        previewContainer.addView(
            host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        fnTab = FnTab(context, keyboard).also { it.visibility = View.GONE }
        keyboard.onRelayoutRequested = { fnTab.requestLayout(); fnTab.invalidate() }
        previewContainer.addView(
            fnTab, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP or android.view.Gravity.START),
        )
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
                    { if (!large && geometry.gapPct == 0) context.getString(R.string.resize_gap_off) else "${geometry.gapPct}%" },
                    { geometry.gapPct > KeyboardGeometry.GAP_MIN }, { geometry.gapPct < KeyboardGeometry.GAP_MAX })
                addFontScaleSlider()
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
            Page(R.string.resize_page_tab, R.string.resize_title_tab, R.string.resize_badge_tab, R.string.resize_desc_tab, ResizeDiagram.Part.TAB, note = R.string.resize_note_tab) {
                addSwitch(R.id.switchNumberLeft, R.string.number_left_switch, R.string.number_left_desc,
                    { Prefs.numberKeysLeft(context) }, { on -> Prefs.setNumberKeysLeft(context, on) })
                addSwitch(R.id.switchFnTab, R.string.fn_tab_switch, R.string.fn_tab_desc,
                    { Prefs.fnTabEnabled(context) }, { on -> Prefs.setFnTabEnabled(context, on) })
                addStepper(R.string.resize_tab_height, { geometry = geometry.fnTabHeightBy(it) }, { "${geometry.fnTabHeightPct}%" },
                    { geometry.fnTabHeightPct > KeyboardGeometry.FN_TAB_HEIGHT_MIN }, { geometry.fnTabHeightPct < KeyboardGeometry.FN_TAB_HEIGHT_MAX })
                addStepper(R.string.resize_tab_pad, { geometry = geometry.fnTabPadBy(it) }, { "${geometry.fnTabPadDp}dp" },
                    { geometry.fnTabPadDp > KeyboardGeometry.FN_TAB_PAD_MIN }, { geometry.fnTabPadDp < KeyboardGeometry.FN_TAB_PAD_MAX })
            },
            Page(R.string.resize_page_numrow, R.string.resize_title_numrow, R.string.resize_badge_123, R.string.resize_desc_numrow, ResizeDiagram.Part.NUMROW) {
                addStepper(R.string.resize_num_height, { geometry = geometry.numHeightBy(it) }, { "${geometry.numHeightPct}%" },
                    { geometry.numHeightPct > KeyboardGeometry.NUM_HEIGHT_MIN }, { geometry.numHeightPct < KeyboardGeometry.NUM_HEIGHT_MAX })
                addStepper(R.string.resize_space_row, { geometry = geometry.spaceRowBy(it) }, { "${geometry.spaceRowPct}%" },
                    { geometry.spaceRowPct > KeyboardGeometry.SPACE_ROW_MIN }, { geometry.spaceRowPct < KeyboardGeometry.SPACE_ROW_MAX })
            },
            Page(R.string.resize_page_numgap, R.string.resize_title_numgap, R.string.resize_badge_1234, R.string.resize_desc_numgap, ResizeDiagram.Part.NUMGAP) {
                addStepper(R.string.resize_num_row_gap, { geometry = geometry.numRowGapBy(it) }, { "${geometry.numRowGapDp}dp" },
                    { geometry.numRowGapDp > KeyboardGeometry.NUM_ROW_GAP_MIN }, { geometry.numRowGapDp < KeyboardGeometry.NUM_ROW_GAP_MAX })
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

        val chips = root.findViewById<ChipGroup>(R.id.pageChips)
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
        root.findViewById<View>(R.id.prevButton).setOnClickListener { selectPage(pageIndex - 1) }
        root.findViewById<View>(R.id.nextButton).setOnClickListener { selectPage(pageIndex + 1) }

        root.findViewById<MaterialButton>(R.id.resetButton).setOnClickListener {
            geometry = KeyboardGeometry()
            Prefs.setKeyOpacityPercent(context, Prefs.KEY_OPACITY_DEFAULT)
            Prefs.setFontScalePercent(context, 100)
            showPage(pageIndex)
        }

        selectPage(0)
        refresh()
    }

    private fun signedDp(v: Int) = if (v > 0) "+${v}dp" else "${v}dp"

    fun selectPage(index: Int) {
        val i = index.coerceIn(0, pages.size - 1)
        val chips = root.findViewById<ChipGroup>(R.id.pageChips)
        chips.check(chips.getChildAt(i).id)
        showPage(i)
    }

    private fun showPage(index: Int) {
        pageIndex = index
        val page = pages[index]
        root.findViewById<TextView>(R.id.pageTitle).setText(page.title)
        root.findViewById<TextView>(R.id.pageBadge).setText(page.badge)
        root.findViewById<TextView>(R.id.pageDesc).setText(page.desc)
        root.findViewById<ResizeDiagram>(R.id.diagram).part = page.part
        root.findViewById<TextView>(R.id.pageNote).apply {
            if (page.note != 0) { setText(page.note); visibility = View.VISIBLE } else visibility = View.GONE
        }
        root.findViewById<ViewGroup>(R.id.pageControls).removeAllViews()
        steppers.clear()
        page.build()
        root.findViewById<View>(R.id.prevButton).isEnabled = index > 0
        root.findViewById<View>(R.id.nextButton).isEnabled = index < pages.size - 1
        refresh()
    }

    private fun addStepper(
        labelRes: Int,
        change: (Int) -> Unit,
        text: () -> String,
        canMinus: () -> Boolean,
        canPlus: () -> Boolean,
    ) {
        val container = root.findViewById<ViewGroup>(R.id.pageControls)
        val row = layoutInflater.inflate(R.layout.row_stepper, container, false)
        row.findViewById<TextView>(R.id.stepLabel).setText(labelRes)
        val minus = row.findViewById<View>(R.id.stepMinus)
        val plus = row.findViewById<View>(R.id.stepPlus)
        container.addView(row)
        bindButton(minus) { change(-1) }
        bindButton(plus) { change(1) }
        steppers += Stepper(row.findViewById(R.id.stepValue), minus, plus, text, canMinus, canPlus)
    }

    private fun addSwitch(
        switchId: Int,
        titleRes: Int,
        descRes: Int,
        get: () -> Boolean,
        set: (Boolean) -> Unit,
    ) {
        val container = root.findViewById<ViewGroup>(R.id.pageControls)
        val sw = MaterialSwitch(context).apply {
            id = switchId
            setText(titleRes)
            isChecked = get()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = (8 * density).toInt() }
            setOnCheckedChangeListener { _, on ->
                set(on)
                refresh()
            }
        }
        val desc = TextView(context).apply {
            setText(descRes)
            textSize = 12f
            alpha = 0.7f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = (4 * density).toInt() }
        }
        container.addView(sw)
        container.addView(desc)
    }

    private fun addFontScaleSlider() {
        val container = root.findViewById<ViewGroup>(R.id.pageControls)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = (12 * density).toInt() }
        }
        val label = TextView(context).apply {
            setText(R.string.font_scale_label)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueTv = TextView(context).apply {
            id = R.id.valueFontScale
            setTypeface(null, android.graphics.Typeface.BOLD)
            text = context.getString(R.string.font_scale_value, Prefs.fontScalePercent(context))
        }
        header.addView(label)
        header.addView(valueTv)

        val slider = Slider(context).apply {
            id = R.id.sliderFontScale
            valueFrom = Prefs.FONT_SCALE_MIN.toFloat()
            valueTo = Prefs.FONT_SCALE_MAX.toFloat()
            stepSize = 5f
            value = Prefs.fontScalePercent(context).toFloat()
            addOnChangeListener { _, v, fromUser ->
                val pct = v.toInt()
                valueTv.text = context.getString(R.string.font_scale_value, pct)
                if (fromUser) {
                    Prefs.setFontScalePercent(context, pct)
                    refresh()
                }
            }
        }
        val desc = TextView(context).apply {
            setText(R.string.font_scale_desc)
            textSize = 12f
            alpha = 0.7f
        }
        container.addView(header)
        container.addView(slider)
        container.addView(desc)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindButton(button: View, change: () -> Unit) {
        var ticks = 0
        val repeat = object : Runnable {
            override fun run() {
                if (!button.isEnabled) return
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

    fun refresh() {
        Prefs.setGeometry(context, large, geometry, landscape)
        splitActive = if (large) Prefs.splitOnLargeScreen(context) else landscape
        KeyboardHost.apply(host, containerWidthPx, geometry, density)
        keyboard.theme = KeyboardTheme.current(context)
        keyboard.numberLeft = Prefs.numberKeysLeft(context)
        keyboard.keyboardLayout = Layouts.byId(Prefs.koreanLayout(context))
        keyboard.fontScale = Prefs.fontScalePercent(context) / 100f
        keyboard.heightScale = geometry.heightScale
        keyboard.splitGapFraction = geometry.gapFraction
        keyboard.keyOpacity = Prefs.keyOpacityPercent(context) / 100f
        keyboard.bgOpacity = Prefs.bgOpacityPercent(context) / 100f
        keyboard.glassType = Prefs.glassType(context)
        keyboard.keyGlassStrength = Prefs.keyGlassStrengthPercent(context) / 100f
        keyboard.bgGlassStrength = Prefs.bgGlassStrengthPercent(context) / 100f
        keyboard.split = splitActive && (large || geometry.gapPct > 0)
        keyboard.frame = KeyboardFrame(
            geometry.padLeftDp, geometry.padTopDp, geometry.padRightDp, geometry.padBottomDp, geometry.keyGapDp,
            geometry.rowGapDp, geometry.numHeightScale, geometry.numRowGapDp, geometry.hitShifts, geometry.spaceRowScale,
        )
        val tabOn = Prefs.fnTabEnabled(context) && (keyboard.hasSideStrip || keyboard.hasTopNumberRow)
        keyboard.fnTab = tabOn
        keyboard.fnTabHeightScale = geometry.fnTabHeightPct / 100f
        keyboard.fnTabPadDp = geometry.fnTabPadDp
        fnTab.theme = keyboard.theme
        fnTab.alphaPercent = Prefs.fnTabAlphaPercent(context)
        KeyboardHost.applyFnTab(host, fnTab, keyboard, tabOn)
        steppers.forEach {
            it.value.text = it.text()
            it.minus.isEnabled = it.canMinus()
            it.plus.isEnabled = it.canPlus()
        }

        root.findViewById<TextView>(R.id.profileInfo).text =
            context.getString(
                when {
                    large && landscape -> R.string.resize_profile_large_land
                    large -> R.string.resize_profile_large
                    landscape -> R.string.resize_profile_normal_land
                    else -> R.string.resize_profile_normal
                },
            )
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
    }
}
