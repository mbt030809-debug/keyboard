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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
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
        radio.check(if (Prefs.koreanLayout(this) == Layouts.KO) R.id.radioDubeolsik else R.id.radioSingle)
        radio.setOnCheckedChangeListener { _, checked ->
            Prefs.setKoreanLayout(this, if (checked == R.id.radioDubeolsik) Layouts.KO else Layouts.KOS)
        }

        // 상용문구: 입력하는 즉시 저장
        val edit = findViewById<EditText>(R.id.editPhrases)
        edit.setText(Phrases.loadRaw(this))
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Phrases.saveRaw(this@SettingsActivity, s.toString())
        })
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
        btnEnable.isEnabled = !enabled

        if (!enabled) {
            status2.text = getString(R.string.status_wait)
            status2.setTextColor(neutral())
            btnSelect.isEnabled = false
        } else {
            setStatus(status2, selected, getString(R.string.status_todo))
            btnSelect.isEnabled = !selected
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
}
