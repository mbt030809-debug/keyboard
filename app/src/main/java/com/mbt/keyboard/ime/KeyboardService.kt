package com.mbt.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.mbt.keyboard.hangul.HangulComposer
import com.mbt.keyboard.hangul.Jamo
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.Layouts

class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var view: KeyboardView
    private val composer = HangulComposer()
    private var layoutId = Layouts.KO
    private var lastShiftTap = 0L

    override fun onCreateInputView(): View {
        view = KeyboardView(this).also {
            it.listener = this
            it.keyboardLayout = Layouts.byId(layoutId)
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        view.shiftState = ShiftState.OFF
        // 비밀번호·이메일·URL 입력칸은 영문 자판으로 시작
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val wantsLatin = cls == InputType.TYPE_CLASS_TEXT && variation in LATIN_VARIATIONS
        if (wantsLatin) setLayout(Layouts.EN)
    }

    override fun onFinishInput() {
        composer.reset()
        super.onFinishInput()
    }

    /** 커서가 앱 쪽에서 움직였으면 조합 상태를 버린다. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (composer.isComposing &&
            (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            composer.reset()
            currentInputConnection?.finishComposingText()
        }
    }

    override fun onKey(key: Key) {
        when (key.type) {
            KeyType.CHAR -> onChar(key)
            KeyType.SPACE -> { flushComposing(); commit(" ") }
            KeyType.BACKSPACE -> onBackspace()
            KeyType.ENTER -> onEnter()
            KeyType.SHIFT -> onShift()
            KeyType.LANG -> {
                setLayout(if (layoutId == Layouts.KO) Layouts.EN else Layouts.KO)
            }
            KeyType.SPACER -> Unit
        }
    }

    private fun onChar(key: Key) {
        val shifted = view.shiftState != ShiftState.OFF
        val text = key.outputFor(shifted)
        val ic = currentInputConnection ?: return
        if (layoutId == Layouts.KO && text.length == 1 && Jamo.isJamo(text[0])) {
            val out = composer.input(text[0])
            ic.beginBatchEdit()
            if (out.committed.isNotEmpty()) ic.commitText(out.committed, 1)
            ic.setComposingText(out.composing, 1)
            ic.endBatchEdit()
        } else {
            flushComposing()
            ic.commitText(text, 1)
        }
        if (view.shiftState == ShiftState.ON) view.shiftState = ShiftState.OFF
    }

    private fun onBackspace() {
        val ic = currentInputConnection ?: return
        val out = composer.backspace()
        if (out != null) {
            if (out.composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(out.composing, 1)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun onEnter() {
        flushComposing()
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** 한 번: 다음 글자만 쉬프트, 빠르게 두 번: 고정 */
    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        view.shiftState = when (view.shiftState) {
            ShiftState.OFF -> if (now - lastShiftTap < DOUBLE_TAP_MS) ShiftState.LOCK else ShiftState.ON
            ShiftState.ON -> if (now - lastShiftTap < DOUBLE_TAP_MS) ShiftState.LOCK else ShiftState.OFF
            ShiftState.LOCK -> ShiftState.OFF
        }
        lastShiftTap = now
    }

    private fun setLayout(id: String) {
        flushComposing()
        layoutId = id
        view.keyboardLayout = Layouts.byId(id)
        view.shiftState = ShiftState.OFF
    }

    private fun flushComposing() {
        if (!composer.isComposing) return
        currentInputConnection?.commitText(composer.flush(), 1)
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private companion object {
        const val DOUBLE_TAP_MS = 350L
        val LATIN_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
    }
}
