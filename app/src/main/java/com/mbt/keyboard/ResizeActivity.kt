package com.mbt.keyboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.mbt.keyboard.ime.KeyboardHost
import com.mbt.keyboard.ime.KeyboardView
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.Prefs

/**
 * 키보드 크기·위치 조정 화면.
 * 아래에 실제 키보드와 똑같은 미리보기가 뜨고, 위의 버튼으로 가로·세로 길이와 위치를 바꾸면 바로 반영된다.
 * 폴드를 접었을 때와 펼쳤을 때는 각각 따로 저장된다 (지금 기기 상태 기준).
 */
class ResizeActivity : AppCompatActivity() {

    private val large get() = KeyboardGeometry.isLargeScreen(resources.configuration.smallestScreenWidthDp)
    private lateinit var geometry: KeyboardGeometry
    private lateinit var host: FrameLayout
    private lateinit var keyboard: KeyboardView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resize)
        geometry = Prefs.geometry(this, large)

        // 상태바 영역만 비운다. 아래쪽 내비게이션 바 여백은 키보드 뷰가 실제와 똑같이 스스로 처리한다.
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        // 미리보기: 실제 키보드와 같은 뷰, 같은 틀(KeyboardHost)
        keyboard = KeyboardView(this).also {
            it.keyboardLayout = Layouts.byId(Prefs.koreanLayout(this))
            it.split = Prefs.splitOnLargeScreen(this) && large
        }
        host = FrameLayout(this).apply {
            addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        findViewById<FrameLayout>(R.id.previewContainer).addView(
            host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )

        // 길게 누르면 계속 변하는 버튼들
        bind(R.id.widthMinus) { geometry = geometry.widerBy(-1) }
        bind(R.id.widthPlus) { geometry = geometry.widerBy(1) }
        bind(R.id.heightMinus) { geometry = geometry.tallerBy(-1) }
        bind(R.id.heightPlus) { geometry = geometry.tallerBy(1) }
        bind(R.id.moveLeft) { geometry = geometry.movedX(-1) }
        bind(R.id.moveRight) { geometry = geometry.movedX(1) }
        bind(R.id.moveUp) { geometry = geometry.raisedBy(1) }
        bind(R.id.moveDown) { geometry = geometry.raisedBy(-1) }

        findViewById<MaterialButton>(R.id.resetButton).setOnClickListener {
            geometry = KeyboardGeometry()
            refresh()
        }
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener { finish() }

        refresh()
    }

    /** 눌렀을 때 한 번, 계속 누르고 있으면 반복해서 [change]를 실행한다 */
    @SuppressLint("ClickableViewAccessibility")
    private fun bind(id: Int, change: () -> Unit) {
        val button = findViewById<MaterialButton>(id)
        val repeat = object : Runnable {
            override fun run() {
                change(); refresh()
                handler.postDelayed(this, 90L)
            }
        }
        button.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!v.isEnabled) return@setOnTouchListener false
                    v.isPressed = true
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
        Prefs.setGeometry(this, large, geometry)
        val density = resources.displayMetrics.density
        KeyboardHost.apply(host, resources.displayMetrics.widthPixels, geometry, density)
        keyboard.heightScale = geometry.heightScale

        findViewById<TextView>(R.id.profileInfo).text =
            getString(if (large) R.string.resize_profile_large else R.string.resize_profile_normal)
        findViewById<TextView>(R.id.widthValue).text = "${geometry.widthPct}%"
        findViewById<TextView>(R.id.heightValue).text = "${geometry.heightPct}%"

        findViewById<View>(R.id.widthMinus).isEnabled = geometry.widthPct > KeyboardGeometry.WIDTH_MIN
        findViewById<View>(R.id.widthPlus).isEnabled = geometry.widthPct < KeyboardGeometry.WIDTH_MAX
        findViewById<View>(R.id.heightMinus).isEnabled = geometry.heightPct > KeyboardGeometry.HEIGHT_MIN
        findViewById<View>(R.id.heightPlus).isEnabled = geometry.heightPct < KeyboardGeometry.HEIGHT_MAX
        findViewById<View>(R.id.moveLeft).isEnabled = geometry.canMoveX && geometry.xPct > KeyboardGeometry.X_MIN
        findViewById<View>(R.id.moveRight).isEnabled = geometry.canMoveX && geometry.xPct < KeyboardGeometry.X_MAX
        findViewById<View>(R.id.moveUp).isEnabled = geometry.canRaise
        findViewById<View>(R.id.moveDown).isEnabled = geometry.canLower

        findViewById<TextView>(R.id.moveHint).text =
            if (geometry.canMoveX) getString(R.string.resize_move_hint_x, geometry.raiseDp)
            else getString(R.string.resize_move_hint_full, geometry.raiseDp)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
