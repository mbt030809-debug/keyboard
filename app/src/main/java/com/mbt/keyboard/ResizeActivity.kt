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
import com.mbt.keyboard.ime.KeyboardFrame
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
    private val landscape get() = ScreenProfile.isLandscape(this)
    private lateinit var geometry: KeyboardGeometry
    private lateinit var host: FrameLayout
    private lateinit var keyboard: KeyboardView

    /** 갈라진 자판이 적용 중인가 (펼침 화면 + 설정 켬). 아닐 때는 "갈라진 정도" 행을 숨긴다 */
    private var splitActive = false

    /** 키 둘레 여백·키 간격 조절 줄 (표시 갱신용) */
    private class Stepper(val value: TextView, val minus: View, val plus: View, val text: () -> String,
                          val canMinus: () -> Boolean, val canPlus: () -> Boolean)
    private val steppers = ArrayList<Stepper>()

    /** 미리보기가 놓이는 실제 가로 폭(px). 측정되기 전에는 화면 폭으로 대신한다 */
    private var containerWidthPx = 0
    private val density get() = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resize)
        geometry = Prefs.geometry(this, large, landscape)

        // 상태바 영역만 비운다. 아래쪽 내비게이션 바 여백은 키보드 뷰가 실제와 똑같이 스스로 처리한다.
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
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

        // 키 둘레 여백 4방향 + 키 사이 간격 (1dp씩)
        addStepper(R.string.resize_pad_top, { geometry = geometry.padTopBy(it) }, { "${geometry.padTopDp}dp" },
            { geometry.padTopDp > KeyboardGeometry.PAD_MIN }, { geometry.padTopDp < KeyboardGeometry.PAD_MAX })
        addStepper(R.string.resize_pad_bottom, { geometry = geometry.padBottomBy(it) }, { "${geometry.padBottomDp}dp" },
            { geometry.padBottomDp > KeyboardGeometry.PAD_MIN }, { geometry.padBottomDp < KeyboardGeometry.PAD_MAX })
        addStepper(R.string.resize_pad_left, { geometry = geometry.padLeftBy(it) }, { "${geometry.padLeftDp}dp" },
            { geometry.padLeftDp > KeyboardGeometry.PAD_MIN }, { geometry.padLeftDp < KeyboardGeometry.PAD_MAX })
        addStepper(R.string.resize_pad_right, { geometry = geometry.padRightBy(it) }, { "${geometry.padRightDp}dp" },
            { geometry.padRightDp > KeyboardGeometry.PAD_MIN }, { geometry.padRightDp < KeyboardGeometry.PAD_MAX })
        addStepper(R.string.resize_key_gap, { geometry = geometry.keyGapBy(it) }, { "${geometry.keyGapDp}dp" },
            { geometry.keyGapDp > KeyboardGeometry.KEY_GAP_MIN }, { geometry.keyGapDp < KeyboardGeometry.KEY_GAP_MAX })
        addStepper(R.string.resize_row_gap, { geometry = geometry.rowGapBy(it) }, { "${geometry.rowGapDp}dp" },
            { geometry.rowGapDp > KeyboardGeometry.ROW_GAP_MIN }, { geometry.rowGapDp < KeyboardGeometry.ROW_GAP_MAX })
        addStepper(R.string.resize_num_row_gap, { geometry = geometry.numRowGapBy(it) }, { "${geometry.numRowGapDp}dp" },
            { geometry.numRowGapDp > KeyboardGeometry.NUM_ROW_GAP_MIN }, { geometry.numRowGapDp < KeyboardGeometry.NUM_ROW_GAP_MAX })
        addStepper(R.string.resize_hit0, { geometry = geometry.hit0By(it) }, { signedDp(geometry.hit0Dp) },
            { geometry.hit0Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit0Dp < KeyboardGeometry.HIT_MAX })
        addStepper(R.string.resize_hit1, { geometry = geometry.hit1By(it) }, { signedDp(geometry.hit1Dp) },
            { geometry.hit1Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit1Dp < KeyboardGeometry.HIT_MAX })
        addStepper(R.string.resize_hit2, { geometry = geometry.hit2By(it) }, { signedDp(geometry.hit2Dp) },
            { geometry.hit2Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit2Dp < KeyboardGeometry.HIT_MAX })
        addStepper(R.string.resize_hit3, { geometry = geometry.hit3By(it) }, { signedDp(geometry.hit3Dp) },
            { geometry.hit3Dp > KeyboardGeometry.HIT_MIN }, { geometry.hit3Dp < KeyboardGeometry.HIT_MAX })
        // 숫자가 왼쪽 띠에 있으면(모아키·숫자 왼쪽 옵션) "숫자 줄 크기"는 띠의 가로 길이, 아니면 줄 높이
        addStepper(R.string.resize_num_height, { geometry = geometry.numHeightBy(it) }, { "${geometry.numHeightPct}%" },
            { geometry.numHeightPct > KeyboardGeometry.NUM_HEIGHT_MIN }, { geometry.numHeightPct < KeyboardGeometry.NUM_HEIGHT_MAX })

        findViewById<MaterialButton>(R.id.resetButton).setOnClickListener {
            geometry = KeyboardGeometry()
            refresh()
        }
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener { finish() }

        refresh()
    }

    private fun signedDp(v: Int) = if (v > 0) "+${v}dp" else "${v}dp"

    /** [－ 값 ＋] 한 줄을 만들어 "키 둘레 여백" 영역에 붙인다 */
    private fun addStepper(
        labelRes: Int, change: (Int) -> Unit, text: () -> String, canMinus: () -> Boolean, canPlus: () -> Boolean,
    ) {
        val row = layoutInflater.inflate(R.layout.row_stepper, findViewById<ViewGroup>(R.id.frameControls), false)
        row.findViewById<TextView>(R.id.stepLabel).setText(labelRes)
        val minus = row.findViewById<View>(R.id.stepMinus)
        val plus = row.findViewById<View>(R.id.stepPlus)
        findViewById<ViewGroup>(R.id.frameControls).addView(row)
        bindButton(minus) { change(-1) }
        bindButton(plus) { change(1) }
        steppers += Stepper(row.findViewById(R.id.stepValue), minus, plus, text, canMinus, canPlus)
    }

    private fun bind(id: Int, change: () -> Unit) = bindButton(findViewById(id), change)

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
        keyboard.split = splitActive && (large || geometry.gapPct > 0)
        keyboard.frame = KeyboardFrame(
            geometry.padLeftDp, geometry.padTopDp, geometry.padRightDp, geometry.padBottomDp, geometry.keyGapDp,
            geometry.rowGapDp, geometry.numHeightScale, geometry.numRowGapDp, geometry.hitShifts,
        )
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
        findViewById<TextView>(R.id.widthValue).text = "${geometry.widthPct}%"
        findViewById<TextView>(R.id.heightValue).text = "${geometry.heightPct}%"

        findViewById<View>(R.id.widthMinus).isEnabled = geometry.widthPct > KeyboardGeometry.WIDTH_MIN
        findViewById<View>(R.id.widthPlus).isEnabled = geometry.widthPct < KeyboardGeometry.WIDTH_MAX
        findViewById<View>(R.id.heightMinus).isEnabled = geometry.heightPct > KeyboardGeometry.HEIGHT_MIN
        findViewById<View>(R.id.heightPlus).isEnabled = geometry.heightPct < KeyboardGeometry.HEIGHT_MAX
        findViewById<View>(R.id.gapRow).visibility = if (splitActive) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.gapValue).text =
            if (!large && geometry.gapPct == 0) getString(R.string.resize_gap_off) else "${geometry.gapPct}%"
        findViewById<View>(R.id.gapMinus).isEnabled = geometry.gapPct > KeyboardGeometry.GAP_MIN
        findViewById<View>(R.id.gapPlus).isEnabled = geometry.gapPct < KeyboardGeometry.GAP_MAX
        findViewById<View>(R.id.moveLeft).isEnabled = geometry.canMoveLeft(containerWidthPx, density)
        findViewById<View>(R.id.moveRight).isEnabled = geometry.canMoveRight(containerWidthPx, density)
        findViewById<View>(R.id.moveUp).isEnabled = geometry.canRaise
        findViewById<View>(R.id.moveDown).isEnabled = geometry.canLower

        findViewById<TextView>(R.id.moveHint).text =
            if (geometry.freePx(containerWidthPx) > 0) getString(R.string.resize_move_hint_x, geometry.raiseDp, KeyboardGeometry.X_STEP_DP)
            else getString(R.string.resize_move_hint_full, geometry.raiseDp)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
