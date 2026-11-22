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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.phrase.Phrases
import com.mbt.keyboard.settings.Prefs

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
