package com.mbt.keyboard.ime

import android.text.InputType

/** 입력칸 종류 판별 (안드로이드 상수만 쓰는 순수 계산) */
object InputKinds {
    /** 비밀번호 입력칸인가 (글자·숫자·웹 비밀번호 모두) */
    fun isPassword(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** 클립보드 기록 등에 민감 정보로 취급할 표시가 복사 내용에 붙어 있는가 (비밀번호 관리자 등이 붙인다) */
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
