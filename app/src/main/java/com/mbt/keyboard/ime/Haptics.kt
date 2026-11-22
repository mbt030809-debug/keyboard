package com.mbt.keyboard.ime

import android.content.Context
import android.os.Build
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

    private var enabled = true
    private var strength = Prefs.HAPTIC_DEFAULT

    init { refresh() }

    /** 설정이 바뀌었을 수 있을 때 다시 읽기 */
    fun refresh() {
        enabled = Prefs.hapticEnabled(context)
        strength = Prefs.hapticStrength(context)
    }

    /** 키를 누를 때 */
    fun key() = vibrate(1f)

    /** 스와이프 한 칸 이동 등 가벼운 틱 */
    fun tick() = vibrate(0.6f)

    /** 꾹 누르기 팝업이 열릴 때 */
    fun longPress() = vibrate(1.6f)

    private fun vibrate(scale: Float) {
        if (!enabled) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val ms = (durationMs(strength) * scale).toLong().coerceAtLeast(1L)
        val amp = if (v.hasAmplitudeControl()) amplitude(strength) else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    companion object {
        /** 세기 1~10 → 진동 시간(ms): 7 ~ 25 */
        fun durationMs(strength: Int) = 5L + strength.coerceIn(Prefs.HAPTIC_MIN, Prefs.HAPTIC_MAX) * 2

        /** 세기 1~10 → 진폭 25 ~ 250 */
        fun amplitude(strength: Int) =
            (strength.coerceIn(Prefs.HAPTIC_MIN, Prefs.HAPTIC_MAX) * 25).coerceIn(1, 255)
    }
}
