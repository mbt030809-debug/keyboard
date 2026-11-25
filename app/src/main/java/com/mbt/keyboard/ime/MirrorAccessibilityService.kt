package com.mbt.keyboard.ime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.mbt.keyboard.settings.Prefs

class MirrorAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastCaptureMs = 0L
    private var pendingCapture = false
    private val captureRunnable = Runnable {
        pendingCapture = false
        captureNow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        requestMirrorCapture()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            requestMirrorCapture()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacks(captureRunnable)
        super.onDestroy()
    }

    fun requestMirrorCapture() {
        if (Build.VERSION.SDK_INT < 30) return
        if (!keyboardVisible || keyboardTopPx <= 80 || keyboardHeightPx <= 40) return
        if (!Prefs.mirrorEnabled(this)) return
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastCaptureMs
        if (elapsed >= MIN_INTERVAL_MS) {
            handler.removeCallbacks(captureRunnable)
            pendingCapture = false
            captureNow()
        } else if (!pendingCapture) {
            pendingCapture = true
            handler.postDelayed(captureRunnable, (MIN_INTERVAL_MS - elapsed).coerceAtLeast(16L))
        }
    }

    private fun captureNow() {
        if (Build.VERSION.SDK_INT < 30) return
        if (!keyboardVisible || keyboardTopPx <= 80 || keyboardHeightPx <= 40) return
        lastCaptureMs = SystemClock.uptimeMillis()
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hwBuffer = screenshot.hardwareBuffer
                            val raw = Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                            hwBuffer.close()
                            if (raw == null) return
                            val soft = raw.copy(Bitmap.Config.ARGB_8888, false)
                            raw.recycle()
                            if (soft == null) return
                            val topY = (keyboardTopPx - keyboardHeightPx).coerceIn(0, (soft.height - 1).coerceAtLeast(0))
                            val cropH = (keyboardTopPx - topY).coerceIn(1, soft.height - topY)
                            if (cropH > 10 && soft.width > 10) {
                                val matrix = Matrix().apply { preScale(1f, -1f) }
                                val flipped = Bitmap.createBitmap(soft, 0, topY, soft.width, cropH, matrix, true)
                                if (flipped !== soft) soft.recycle()
                                updateMirrorBitmap(flipped)
                            } else {
                                soft.recycle()
                            }
                        } catch (_: Throwable) {
                        }
                    }

                    override fun onFailure(errorCode: Int) = Unit
                },
            )
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 33L

        @Volatile var instance: MirrorAccessibilityService? = null
        @Volatile var keyboardVisible: Boolean = false
        @Volatile var keyboardTopPx: Int = 0
        @Volatile var keyboardHeightPx: Int = 0
        @Volatile var mirrorBitmap: Bitmap? = null
        @Volatile var mirrorVersion: Int = 0
        var onMirrorUpdated: (() -> Unit)? = null

        fun updateMirrorBitmap(bmp: Bitmap) {
            mirrorBitmap = bmp
            mirrorVersion++
            onMirrorUpdated?.invoke()
        }

        fun requestCapture() {
            instance?.requestMirrorCapture()
        }

        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.contains("${context.packageName}/${MirrorAccessibilityService::class.java.name}") ||
                enabled.contains("${context.packageName}/.ime.MirrorAccessibilityService")
        }
    }
}
