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
import com.mbt.keyboard.settings.ScreenProfile

/**
 * 키보드 크기·위치 조정 화면.
 * 아래에 실제 키보드와 똑같은 미리보기가 뜨고, 위의 버튼으로 가로·세로 길이와 위치를 바꾸면 바로 반영된다.
 * 폴드를 접었을 때와 펼쳤을 때는 각각 따로 저장된다 (지금 기기 상태 기준).
 */
class ResizeActivity : AppCompatActivity() {

    /** 키보드와 같은 기준(기기 화면 전체 크기)으로 접힘/펼침을 판정해야 저장한 값이 키보드에 적용된다 */
    private val large get() = ScreenProfile.isLarge(this)
    private lateinit var geometry: KeyboardGeometry
    private lateinit var host: FrameLayout
    private lateinit var keyboard: KeyboardView

    /** 갈라진 자판이 적용 중인가 (펼침 화면 + 설정 켬). 아닐 때는 "갈라진 정도" 행을 숨긴다 */
    private var splitActive = false

    /** 미리보기가 놓이는 실제 가로 폭(px). 측정되기 전에는 화면 폭으로 대신한다 */
    private var containerWidthPx = 0
    private val density get() = resources.displayMetrics.density
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
        splitActive = Prefs.splitOnLargeScreen(this) && large
        keyboard = KeyboardView(this).also {
            it.keyboardLayout = Layouts.byId(Prefs.koreanLayout(this))
            it.split = splitActive
        }
        host = FrameLayout(this).apply {
            addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val previewContainer = findViewById<FrameLayout>(R.id.previewContainer)
        previewContainer.addView(
            host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
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

        // 길게 누르면 계속 변하는 버튼들
        bind(R.id.widthMinus) { geometry = geometry.widerBy(-1) }
        bind(R.id.widthPlus) { geometry = geometry.widerBy(1) }
        bind(R.id.heightMinus) { geometry = geometry.tallerBy(-1) }
        bind(R.id.heightPlus) { geometry = geometry.tallerBy(1) }
        bind(R.id.gapMinus) { geometry = geometry.gapWiderBy(-1) }
        bind(R.id.gapPlus) { geometry = geometry.gapWiderBy(1) }
        bind(R.id.moveLeft) { geometry = geometry.movedX(-1, containerWidthPx, density) }
        bind(R.id.moveRight) { geometry = geometry.movedX(1, containerWidthPx, density) }
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
                // 끝에 닿아 버튼이 비활성이 되면 반복을 멈춘다 (값이 계속 쌓이지 않게)
                if (!button.isEnabled) return
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
        KeyboardHost.apply(host, containerWidthPx, geometry, density)
        keyboard.heightScale = geometry.heightScale
        keyboard.splitGapFraction = geometry.gapFraction

        findViewById<TextView>(R.id.profileInfo).text =
            getString(if (large) R.string.resize_profile_large else R.string.resize_profile_normal)
        findViewById<TextView>(R.id.widthValue).text = "${geometry.widthPct}%"
        findViewById<TextView>(R.id.heightValue).text = "${geometry.heightPct}%"

        findViewById<View>(R.id.widthMinus).isEnabled = geometry.widthPct > KeyboardGeometry.WIDTH_MIN
        findViewById<View>(R.id.widthPlus).isEnabled = geometry.widthPct < KeyboardGeometry.WIDTH_MAX
        findViewById<View>(R.id.heightMinus).isEnabled = geometry.heightPct > KeyboardGeometry.HEIGHT_MIN
        findViewById<View>(R.id.heightPlus).isEnabled = geometry.heightPct < KeyboardGeometry.HEIGHT_MAX
        findViewById<View>(R.id.gapRow).visibility = if (splitActive) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.gapValue).text = "${geometry.gapPct}%"
        findViewById<View>(R.id.gapMinus).isEnabled = geometry.gapPct > KeyboardGeometry.GAP_MIN
        findViewById<View>(R.id.gapPlus).isEnabled = geometry.gapPct < KeyboardGeometry.GAP_MAX
        findViewById<View>(R.id.moveLeft).isEnabled = geometry.canMoveLeft(containerWidthPx, density)
        findViewById<View>(R.id.moveRight).isEnabled = geometry.canMoveRight(containerWidthPx, density)
        findViewById<View>(R.id.moveUp).isEnabled = geometry.canRaise
        findViewById<View>(R.id.moveDown).isEnabled = geometry.canLower

        findViewById<TextView>(R.id.moveHint).text =
            if (geometry.freePx(containerWidthPx) > 0) getString(R.string.resize_move_hint_x, geometry.raiseDp)
            else getString(R.string.resize_move_hint_full, geometry.raiseDp)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
