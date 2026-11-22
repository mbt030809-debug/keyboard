package com.mbt.keyboard.ime

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mbt.keyboard.settings.Prefs

/**
 * 진동 피드백. 설정(켜기/끄기, 세기 1~10)을 [Prefs]에서 읽는다.
 * 세기는 진동 시간과 진폭(지원 기기)에 함께 반영된다.
 */
class Haptics(private val context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // 기기 기능은 한 번만 조회 (매 키마다 시스템 호출을 하지 않는다)
    private val available = vibrator?.hasVibrator() == true
    private val amplitudeControl = available && vibrator?.hasAmplitudeControl() == true

    private var enabled = true
    private var keyEffect: VibrationEffect? = null
    private var tickEffect: VibrationEffect? = null
    private var longEffect: VibrationEffect? = null

    init { refresh() }

    /** 설정이 바뀌었을 수 있을 때 다시 읽기 */
    fun refresh() {
        enabled = Prefs.hapticEnabled(context)
        val strength = Prefs.hapticStrength(context)
        keyEffect = effect(strength, 1f)
        tickEffect = effect(strength, 0.6f)
        longEffect = effect(strength, 1.6f)
    }

    /** 키를 누를 때 */
    fun key() = vibrate(keyEffect)

    /** 스와이프 한 칸 이동 등 가벼운 틱 */
    fun tick() = vibrate(tickEffect)

    /** 꾹 누르기 팝업이 열릴 때 */
    fun longPress() = vibrate(longEffect)

    private fun effect(strength: Int, scale: Float): VibrationEffect {
        val ms = (durationMs(strength) * scale).toLong().coerceAtLeast(1L)
        val amp = if (amplitudeControl) amplitude(strength) else VibrationEffect.DEFAULT_AMPLITUDE
        return VibrationEffect.createOneShot(ms, amp)
    }

    /**
     * 진동 요청은 시스템 서비스로 가는 동기 호출이라 UI 스레드에서 하면 키 입력이 밀릴 수 있다.
     * 별도 스레드에서 실행한다.
     */
    private fun vibrate(effect: VibrationEffect?) {
        if (!enabled || !available || effect == null) return
        val v = vibrator ?: return
        worker.post { v.vibrate(effect) }
    }

    companion object {
        private val worker: Handler by lazy {
            Handler(HandlerThread("mbt-haptics").also { it.start() }.looper)
        }

        /** 세기 1~10 → 진동 시간(ms): 7 ~ 25 */
        fun durationMs(strength: Int) = 5L + strength.coerceIn(Prefs.HAPTIC_MIN, Prefs.HAPTIC_MAX) * 2

        /** 세기 1~10 → 진폭 25 ~ 250 */
        fun amplitude(strength: Int) =
            (strength.coerceIn(Prefs.HAPTIC_MIN, Prefs.HAPTIC_MAX) * 25).coerceIn(1, 255)
    }
}
