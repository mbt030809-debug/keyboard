package com.mbt.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** 1단계: 빈 IME. 이후 단계에서 키보드 뷰/입력 로직 추가. */
class KeyboardService : InputMethodService() {
    override fun onCreateInputView(): View =
        TextView(this).apply {
            text = "MBT 키보드 (준비 중)"
            gravity = Gravity.CENTER
            minHeight = (200 * resources.displayMetrics.density).toInt()
        }
}
