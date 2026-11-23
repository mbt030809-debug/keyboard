package com.mbt.keyboard.ime

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputKindsTest {
    private val text = InputType.TYPE_CLASS_TEXT

    @Test fun passwordFieldsAreDetected() {
        assertTrue(InputKinds.isPassword(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(InputKinds.isPassword(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(InputKinds.isPassword(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(InputKinds.isPassword(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test fun otherFieldsAreNotPasswords() {
        assertFalse(InputKinds.isPassword(text))
        assertFalse(InputKinds.isPassword(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(InputKinds.isPassword(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(InputKinds.isPassword(InputType.TYPE_CLASS_NUMBER))
        assertFalse(InputKinds.isPassword(InputType.TYPE_CLASS_PHONE))
        assertFalse(InputKinds.isPassword(InputType.TYPE_NULL))
    }
}
