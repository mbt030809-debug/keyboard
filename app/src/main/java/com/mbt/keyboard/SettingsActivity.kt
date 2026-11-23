package com.mbt.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.RadioGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.mbt.keyboard.ime.Haptics
import com.mbt.keyboard.ime.HapticStyle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.mbt.keyboard.clipboard.ClipboardHistory
import com.mbt.keyboard.clipboard.PrefsClipboardStore
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.phrase.Phrases
import com.mbt.keyboard.ime.KeyboardTheme
import com.mbt.keyboard.ime.KeyboardThemes
import com.mbt.keyboard.settings.Prefs
import com.mbt.keyboard.settings.ThemeSwatch
import com.mbt.keyboard.settings.Timing

/** 온보딩 화면: 키보드 활성화 → 선택 → 입력 테스트. 상태는 돌아올 때마다 자동 갱신. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var status1: TextView
    private lateinit var status2: TextView
    private lateinit var btnEnable: MaterialButton
    private lateinit var btnSelect: MaterialButton

    private val imm get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // edge-to-edge: 상태바·내비게이션 바 영역 피하기
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        status1 = findViewById(R.id.status1)
        status2 = findViewById(R.id.status2)
        btnEnable = findViewById(R.id.btnEnable)
        btnSelect = findViewById(R.id.btnSelect)

        setupTabs(savedInstanceState)
        findViewById<View>(R.id.btnTry).setOnClickListener { showTryDialog() }

        btnEnable.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btnSelect.setOnClickListener { imm.showInputMethodPicker() }

        // 설치된 빌드 표시 (새 APK가 실제로 설치됐는지 확인용)
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.version_label, info.versionName, info.versionCode)

        // 클립보드 기록: 켜기/끄기, 전체 삭제
        val clip = findViewById<MaterialSwitch>(R.id.switchClipboard)
        clip.isChecked = Prefs.clipboardEnabled(this)
        clip.setOnCheckedChangeListener { _, on -> Prefs.setClipboardEnabled(this, on) }
        findViewById<MaterialButton>(R.id.btnClearClipboard).setOnClickListener {
            ClipboardHistory(PrefsClipboardStore(this)).clear()
            Toast.makeText(this, R.string.clip_cleared, Toast.LENGTH_SHORT).show()
        }

        // 영어 첫 글자 자동 대문자: 켜고 끄는 즉시 저장
        val autoCaps = findViewById<MaterialSwitch>(R.id.switchAutoCaps)
        autoCaps.isChecked = Prefs.autoCapitalize(this)
        autoCaps.setOnCheckedChangeListener { _, on -> Prefs.setAutoCapitalize(this, on) }

        buildThemePicker(findViewById(R.id.themeList))

        // 키보드 크기·위치 조정 화면
        findViewById<MaterialButton>(R.id.btnResize).setOnClickListener {
            startActivity(Intent(this, ResizeActivity::class.java))
        }

        // 입력 타이밍: 슬라이더 값은 정수 단위(멈춤 0.1초, 꾹 누르기 0.05초)라 저장값과 어긋나지 않는다
        val sliderPause = findViewById<Slider>(R.id.sliderPause)
        val valuePause = findViewById<TextView>(R.id.valuePause)
        val sliderLong = findViewById<Slider>(R.id.sliderLong)
        val valueLong = findViewById<TextView>(R.id.valueLong)
        val pauseUnit = Timing.DOUBLE_TAP_STEP_MS
        val longUnit = Timing.LONG_PRESS_STEP_MS
        sliderPause.value = (Prefs.doubleTapMs(this) / pauseUnit).toFloat()
        valuePause.text = Timing.seconds(Prefs.doubleTapMs(this))
        sliderPause.addOnChangeListener { _, value, fromUser ->
            val ms = value.toLong() * pauseUnit
            valuePause.text = Timing.seconds(ms)
            if (fromUser) Prefs.setDoubleTapMs(this, ms)
        }
        sliderLong.value = (Prefs.longPressMs(this) / longUnit).toFloat()
        valueLong.text = Timing.seconds(Prefs.longPressMs(this))
        sliderLong.addOnChangeListener { _, value, fromUser ->
            val ms = value.toLong() * longUnit
            valueLong.text = Timing.seconds(ms)
            if (fromUser) Prefs.setLongPressMs(this, ms)
        }

        // 지우기 속도: 초당 글자 수
        val sliderRepeat = findViewById<Slider>(R.id.sliderRepeat)
        val valueRepeat = findViewById<TextView>(R.id.valueRepeat)
        sliderRepeat.value = Prefs.repeatCps(this).toFloat()
        valueRepeat.text = getString(R.string.timing_repeat_value, Prefs.repeatCps(this))
        sliderRepeat.addOnChangeListener { _, value, fromUser ->
            valueRepeat.text = getString(R.string.timing_repeat_value, value.toInt())
            if (fromUser) Prefs.setRepeatCps(this, value.toInt())
        }

        // 진동 피드백: 바꾸는 즉시 저장하고 체험 진동
        val preview = Haptics(this)
        val sw = findViewById<MaterialSwitch>(R.id.switchHaptic)
        val slider = findViewById<Slider>(R.id.sliderHaptic)
        sw.isChecked = Prefs.hapticEnabled(this)
        slider.value = Prefs.hapticStrength(this).toFloat()
        slider.isEnabled = sw.isChecked
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.setHapticEnabled(this, on)
            slider.isEnabled = on
            preview.refresh(); preview.key()
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            Prefs.setHapticStrength(this, value.toInt())
            preview.refresh(); preview.key()
        }

        // 진동 느낌: 고르는 즉시 저장하고 한 번 울려서 느껴 보게 한다
        val styleGroup = findViewById<RadioGroup>(R.id.radioHapticStyle)
        val currentStyle = HapticStyle.byId(Prefs.hapticStyle(this))
        HapticStyle.values().forEach { st ->
            val rb = com.google.android.material.radiobutton.MaterialRadioButton(this).apply {
                id = View.generateViewId()
                tag = st
                text = "${st.label}  —  ${st.desc}"
                textSize = 14f
            }
            styleGroup.addView(rb)
            if (st == currentStyle) styleGroup.check(rb.id)
        }
        styleGroup.setOnCheckedChangeListener { group, checked ->
            val st = group.findViewById<View>(checked)?.tag as? HapticStyle ?: return@setOnCheckedChangeListener
            Prefs.setHapticStyle(this, st.id)
            preview.refresh(); preview.key()
        }

        // 갈라진 자판: 켜고 끄는 즉시 저장
        val split = findViewById<MaterialSwitch>(R.id.switchSplit)
        split.isChecked = Prefs.splitOnLargeScreen(this)
        split.setOnCheckedChangeListener { _, on -> Prefs.setSplitOnLargeScreen(this, on) }

        // 영어 갈라진 자판: 안쪽 키 복제 (켜고 끄는 즉시 저장)
        val splitDup = findViewById<MaterialSwitch>(R.id.switchSplitDup)
        splitDup.isChecked = Prefs.splitDuplicateKeys(this)
        splitDup.setOnCheckedChangeListener { _, on -> Prefs.setSplitDuplicateKeys(this, on) }

        // 한글 자판 선택: 고르는 즉시 저장
        val radio = findViewById<RadioGroup>(R.id.radioLayout)
        radio.check(
            when (Prefs.koreanLayout(this)) {
                Layouts.KO -> R.id.radioDubeolsik
                Layouts.MOA -> R.id.radioMoaki
                else -> R.id.radioSingle
            },
        )
        radio.setOnCheckedChangeListener { _, checked ->
            Prefs.setKoreanLayout(
                this,
                when (checked) { R.id.radioDubeolsik -> Layouts.KO; R.id.radioMoaki -> Layouts.MOA; else -> Layouts.KOS },
            )
        }

        // 숫자 키를 왼쪽 띠로 (모아키처럼)
        val numberLeft = findViewById<MaterialSwitch>(R.id.switchNumberLeft)
        numberLeft.isChecked = Prefs.numberKeysLeft(this)
        numberLeft.setOnCheckedChangeListener { _, on -> Prefs.setNumberKeysLeft(this, on) }

        // 단모음 개선판 (밀어서 쌍자음·ㅑㅕㅛㅠ)
        val swipeDouble = findViewById<MaterialSwitch>(R.id.switchSwipeDouble)
        swipeDouble.isChecked = Prefs.singleSwipeDouble(this)
        swipeDouble.setOnCheckedChangeListener { _, on -> Prefs.setSingleSwipeDouble(this, on) }

        // 상용문구: 입력하는 즉시 저장
        val edit = findViewById<EditText>(R.id.editPhrases)
        edit.setText(Phrases.loadRaw(this))
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Phrases.saveRaw(this@SettingsActivity, s.toString())
        })
    }

    /** 위쪽 탭(시작·입력·화면·정보)을 누르면 그 탭의 카드들만 보인다 */
    private fun setupTabs(saved: Bundle?) {
        val ids = listOf(R.id.sectionStart, R.id.sectionInput, R.id.sectionScreen, R.id.sectionInfo)
        val tabs = findViewById<TabLayout>(R.id.tabs)
        val scroll = findViewById<android.widget.ScrollView>(R.id.scroll)
        fun show(i: Int) {
            ids.forEachIndexed { j, id -> findViewById<View>(id).visibility = if (j == i) View.VISIBLE else View.GONE }
            scroll.scrollTo(0, 0)
            currentTab = i
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        // 이미 준비가 끝났으면 바로 "입력" 설정부터, 아니면 "시작" 안내부터 보여준다
        val start = saved?.getInt(STATE_TAB) ?: if (isEnabled() && isSelected()) TAB_INPUT else TAB_START
        tabs.getTabAt(start)?.select()
        show(start)
    }

    private var currentTab = 0

    /** 어느 탭에서든 오른쪽 위 버튼으로 열어 보는 입력 테스트 창 (설정을 바꾸고 바로 눌러 보기 좋다) */
    private fun showTryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_try, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.test_title)
            .setView(view)
            .setPositiveButton(R.string.try_close, null)
            .create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        view.findViewById<View>(R.id.tryField).requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, currentTab)
    }

    /** 테마 미리보기 카드를 2열로 늘어놓고, 누르면 바로 저장·적용 */
    private fun buildThemePicker(list: android.widget.LinearLayout) {
        val d = resources.displayMetrics.density
        val items = listOf(
            Triple(KeyboardThemes.SYSTEM, getString(R.string.theme_system), KeyboardTheme.system(this)),
        ) + KeyboardThemes.all.map { Triple(it.id, it.name, it.theme) }
        val swatches = ArrayList<Pair<String, ThemeSwatch>>()
        fun refresh() {
            val cur = Prefs.themeId(this)
            swatches.forEach { (id, v) -> v.chosen = id == cur || (cur !in items.map { it.first } && id == KeyboardThemes.SYSTEM) }
        }
        items.chunked(2).forEach { pair ->
            val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, (id, name, theme) ->
                val cell = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (8 * d).toInt())
                    setOnClickListener { Prefs.setThemeId(this@SettingsActivity, id); refresh() }
                }
                val sw = ThemeSwatch(this, theme)
                swatches += id to sw
                cell.addView(sw, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (64 * d).toInt()))
                cell.addView(TextView(this).apply { text = name; textSize = 13f; setPadding((2 * d).toInt(), (4 * d).toInt(), 0, 0) })
                row.addView(cell, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            if (pair.size == 1) row.addView(android.view.View(this), android.widget.LinearLayout.LayoutParams(0, 1, 1f))
            list.addView(row)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    /** 키보드 선택 창에서 돌아오면 포커스가 바뀌므로 포커스 변경 시에도 갱신 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun isEnabled() = imm.enabledInputMethodList.any { it.packageName == packageName }

    private fun isSelected(): Boolean {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return id?.startsWith("$packageName/") == true
    }

    private fun refreshStatus() {
        val enabled = isEnabled()
        val selected = enabled && isSelected()

        setStatus(status1, enabled, getString(R.string.status_todo))
        btnEnable.isEnabled = true          // 이미 켜져 있어도 설정 화면을 다시 열 수 있게 늘 눌러볼 수 있다

        // 맨 위 한 줄 요약: 지금 무엇을 하면 되는지
        findViewById<TextView>(R.id.statusBanner).text = getString(
            when {
                !enabled -> R.string.status_need_enable
                !selected -> R.string.status_need_select
                else -> R.string.status_ready
            },
        )

        if (!enabled) {
            status2.text = getString(R.string.status_wait)
            status2.setTextColor(neutral())
            btnSelect.isEnabled = false
        } else {
            setStatus(status2, selected, getString(R.string.status_todo))
            btnSelect.isEnabled = true      // 이미 선택돼 있어도 다시 고를 수 있게 둔다
        }
    }

    private fun setStatus(v: TextView, done: Boolean, todo: String) {
        if (done) {
            v.text = "✓ " + getString(R.string.status_done)
            v.setTextColor(ContextCompat.getColor(this, if (isNight()) R.color.ok_night else R.color.ok))
        } else {
            v.text = todo
            v.setTextColor(neutral())
        }
    }

    private fun neutral() = ContextCompat.getColor(this, if (isNight()) R.color.brand_night else R.color.brand)

    private fun isNight() =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val STATE_TAB = "tab"
        const val TAB_START = 0
        const val TAB_INPUT = 1
    }
}
