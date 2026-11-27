package com.mbt.keyboard.ime

import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairCursorTest {

    @Test fun movesCursorLeftViaSetSelectionWhenExtractedTextAvailable() {
        val svc = Robolectric.buildService(KeyboardService::class.java).create().get()
        val edit = EditText(ApplicationProvider.getApplicationContext())
        edit.setText("안녕()")
        edit.setSelection(4)

        var selectedStart = -1
        var selectedEnd = -1
        val sentKeys = ArrayList<Int>()
        val ic = object : BaseInputConnection(edit, true) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText =
                ExtractedText().apply {
                    text = edit.text
                    startOffset = 0
                    selectionStart = edit.selectionStart
                    selectionEnd = edit.selectionEnd
                }

            override fun setSelection(start: Int, end: Int): Boolean {
                selectedStart = start
                selectedEnd = end
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                sentKeys += event.keyCode
                return true
            }
        }

        svc.moveCursorLeftAfterPair(ic)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(3, selectedStart)
        assertEquals(3, selectedEnd)
        assertTrue("setSelection 성공 시 DPAD_LEFT 폴백 불필요", sentKeys.isEmpty())
    }

    @Test fun respectsStartOffsetWhenMovingCursorLeft() {
        val svc = Robolectric.buildService(KeyboardService::class.java).create().get()
        val edit = EditText(ApplicationProvider.getApplicationContext())
        var selectedStart = -1
        var selectedEnd = -1
        val ic = object : BaseInputConnection(edit, true) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText =
                ExtractedText().apply {
                    text = "()"
                    startOffset = 100
                    selectionStart = 2
                    selectionEnd = 2
                }

            override fun setSelection(start: Int, end: Int): Boolean {
                selectedStart = start
                selectedEnd = end
                return true
            }
        }

        svc.moveCursorLeftAfterPair(ic)
        assertEquals(101, selectedStart)
        assertEquals(101, selectedEnd)
    }
}
