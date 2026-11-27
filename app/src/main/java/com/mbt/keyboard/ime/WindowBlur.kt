package com.mbt.keyboard.ime

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager

/**
 * 실시간 배경 가우시안 블러 적용기.
 *
 * - **삼성 One UI (Galaxy)**: `ro.surface_flinger.supports_background_blur=0`이라 AOSP `Window.setBackgroundBlurRadius()`가
 *   무시된다. 대신 삼성 프레임워크의 `android.view.SemBlurInfo` + `View.semSetBlurInfo()`(`BLUR_MODE_WINDOW`)를
 *   리플렉션으로 호출해 키보드 판(`host`)과 보조 탭(`fnTab`) 영역에 정확히 하드웨어 가우시안 블러를 건다.
 * - **AOSP Android 12+ (Pixel 등)**: `SemBlurInfo`가 없는 기기에서는 `Window.setBackgroundBlurRadius()`로 폴백한다.
 */
internal object WindowBlur {
    private const val TAG = "MbtWindowBlur"

    private val semBlurInfoClass: Class<*>? by lazy {
        runCatching { Class.forName("android.view.SemBlurInfo") }.getOrNull()
    }

    private val semBuilderClass: Class<*>? by lazy {
        runCatching { Class.forName("android.view.SemBlurInfo\$Builder") }.getOrNull()
    }

    private val semSetBlurInfoMethod by lazy {
        val cls = semBlurInfoClass ?: return@lazy null
        runCatching { View::class.java.getMethod("semSetBlurInfo", cls) }.getOrNull()
            ?: runCatching {
                View::class.java.declaredMethods.firstOrNull { it.name == "semSetBlurInfo" && it.parameterCount == 1 }
                    ?.apply { isAccessible = true }
            }.getOrNull()
    }

    private val blurModeWindow: Int by lazy {
        runCatching { semBlurInfoClass?.getField("BLUR_MODE_WINDOW")?.getInt(null) }.getOrNull() ?: 0
    }

    val isSamsungBlurSupported: Boolean
        get() = semBlurInfoClass != null && semBuilderClass != null && semSetBlurInfoMethod != null

    /**
     * 삼성 One UI `SemBlurInfo`를 [target] 뷰에 적용(radiusPx > 0) 또는 해제(radiusPx <= 0)한다.
     * 성공하면 true, 미지원 기기이거나 호출 실패 시 false를 반환한다.
     */
    fun applySamsungViewBlur(
        target: View,
        radiusPx: Int,
        topCornerRadiusPx: Float,
        bottomCornerRadiusPx: Float = 0f,
    ): Boolean {
        val setter = semSetBlurInfoMethod ?: return false
        return try {
            if (radiusPx <= 0) {
                setter.invoke(target, null)
                return true
            }
            target.setWillNotDraw(false)
            val builderCls = semBuilderClass ?: return false
            val builder = builderCls.getConstructor(Int::class.javaPrimitiveType).newInstance(blurModeWindow)

            // One UI 버전별로 setRadius(int) 또는 setBlurRadius(int) 사용
            val setRadius = runCatching { builderCls.getMethod("setRadius", Int::class.javaPrimitiveType) }.getOrNull()
                ?: runCatching { builderCls.getMethod("setBlurRadius", Int::class.javaPrimitiveType) }.getOrNull()
            setRadius?.invoke(builder, radiusPx)

            // 상단/하단 개별 라운드 코너(4-float) 우선, 없으면 단일 라운드 코너(1-float)
            val setCorner4 = runCatching {
                builderCls.getMethod(
                    "setBackgroundCornerRadius",
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                )
            }.getOrNull()
            if (setCorner4 != null) {
                setCorner4.invoke(builder, topCornerRadiusPx, topCornerRadiusPx, bottomCornerRadiusPx, bottomCornerRadiusPx)
            } else {
                runCatching {
                    builderCls.getMethod("setBackgroundCornerRadius", Float::class.javaPrimitiveType)
                        .invoke(builder, topCornerRadiusPx)
                }
            }

            runCatching {
                builderCls.getMethod("setBackgroundColor", Int::class.javaPrimitiveType)
                    .invoke(builder, Color.TRANSPARENT)
            }

            val blurInfo = builderCls.getMethod("build").invoke(builder)
            setter.invoke(target, blurInfo)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "SemBlurInfo failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * 키보드 윈도우와 판 뷰에 실시간 가우시안 블러를 적용하거나 해제한다.
     */
    fun apply(
        win: Window,
        plateView: View?,
        tabView: View?,
        active: Boolean,
        strength: Float,
        density: Float,
        plateRadiusPx: Float,
        topInsetPx: Int,
    ) {
        val lp = win.attributes
        lp.format = PixelFormat.TRANSLUCENT
        win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lp.blurBehindRadius = 0
        }
        win.attributes = lp

        val clampedStrength = strength.coerceIn(0f, 1f)
        val semRadius = if (active) (150f * clampedStrength).toInt().coerceIn(32, 220) else 0
        val aospRadius = if (active) (24f * clampedStrength * density).toInt().coerceIn(8, 150) else 0

        val semApplied = plateView != null && applySamsungViewBlur(
            target = plateView,
            radiusPx = semRadius,
            topCornerRadiusPx = plateRadiusPx.coerceAtLeast(0f),
            bottomCornerRadiusPx = 0f,
        )
        if (tabView != null && isSamsungBlurSupported) {
            val tabBlur = active && tabView.visibility == View.VISIBLE
            val tabCorner = (tabView.height / 2f).coerceAtLeast(plateRadiusPx)
            applySamsungViewBlur(
                target = tabView,
                radiusPx = if (tabBlur) semRadius else 0,
                topCornerRadiusPx = tabCorner,
                bottomCornerRadiusPx = tabCorner,
            )
        }

        if (semApplied) {
            // 삼성 SemBlurInfo가 뷰 단위로 직접 블러를 그리므로 윈도우 배경은 완전 투명으로 유지
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win.setBackgroundBlurRadius(0)
            }
            return
        }

        // 비삼성 AOSP Android 12+ 폴백: Window.setBackgroundBlurRadius
        if (aospRadius > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val r = plateRadiusPx.coerceAtLeast(0f)
            val plateBg = GradientDrawable().apply {
                setColor(Color.argb(1, 0, 0, 0))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            val bgDrawable: Drawable = if (topInsetPx > 0) InsetDrawable(plateBg, 0, topInsetPx, 0, 0) else plateBg
            win.setBackgroundDrawable(bgDrawable)
            win.setBackgroundBlurRadius(aospRadius)
        } else {
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win.setBackgroundBlurRadius(0)
            }
        }
    }
}
