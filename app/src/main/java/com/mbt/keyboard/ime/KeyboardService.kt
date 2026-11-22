package com.mbt.keyboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mbt.keyboard.PermissionActivity
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
import com.mbt.keyboard.phrase.Phrases
import com.mbt.keyboard.settings.Prefs

class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var view: KeyboardView
    private lateinit var haptics: Haptics
    private lateinit var voice: VoiceInput
    private val composer = HangulComposer()
    private var layoutId = Layouts.KOS
    private var lastShiftTap = 0L

    /** 연타 변환 판정용: 직전에 친 자모 키와 시각 */
    private var lastJamoKey: String? = null
    private var lastJamoTime = 0L

    private val layout get() = Layouts.byId(layoutId)

    override fun onCreate() {
        super.onCreate()
        layoutId = Prefs.koreanLayout(this)
        haptics = Haptics(this)
        voice = VoiceInput(this, voiceCallback)
    }

    override fun onDestroy() {
        voice.destroy()
        super.onDestroy()
    }

    private val voiceCallback = object : VoiceInput.Callback {
        override fun onListening(on: Boolean) {
            if (::view.isInitialized) view.voiceListening = on
        }

        /** 말하는 중 인식된 글자는 조합 중 텍스트로 보여준다 */
        override fun onPartial(text: String) {
            currentInputConnection?.setComposingText(text, 1)
        }

        override fun onResult(text: String) {
            currentInputConnection?.commitText(text, 1)
        }

        override fun onError(message: String) {
            currentInputConnection?.finishComposingText()
            Toast.makeText(this@KeyboardService, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateInputView(): View {
        view = KeyboardView(this).also {
            it.listener = this
            it.haptics = haptics
            it.keyboardLayout = Layouts.byId(layoutId)
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        view.shiftState = ShiftState.OFF
        view.phrases = Phrases.load(this)
        haptics.refresh()   // 설정 화면에서 바꾼 진동 설정 반영
        // 비밀번호·이메일·URL 입력칸은 영문 자판으로 시작
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val wantsLatin = cls == InputType.TYPE_CLASS_TEXT && variation in LATIN_VARIATIONS
        val korean = Prefs.koreanLayout(this)
        when {
            wantsLatin -> setLayout(Layouts.EN)
            layout.hangul && layoutId != korean -> setLayout(korean)   // 설정에서 자판을 바꾼 경우
        }
    }

    override fun onFinishInput() {
        composer.reset()
        lastJamoKey = null
        voice.cancel()
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
        if (key.type != KeyType.VOICE) abortVoice()
        if (key.type != KeyType.CHAR) lastJamoKey = null
        when (key.type) {
            KeyType.CHAR -> onChar(key)
            KeyType.SPACE -> { flushComposing(); commit(" ") }
            KeyType.BACKSPACE -> onBackspace()
            KeyType.ENTER -> onEnter()
            KeyType.SHIFT -> onShift()
            KeyType.VOICE -> onVoice()
            KeyType.LEFT -> moveCursor(-1)
            KeyType.RIGHT -> moveCursor(1)
            KeyType.SELECT_ALL -> editAction(android.R.id.selectAll)
            KeyType.COPY -> editAction(android.R.id.copy)
            KeyType.PASTE -> editAction(android.R.id.paste)
            KeyType.SPACER -> Unit
        }
    }

    /** 꾹 누르기 팝업에서 고른 기호·상용문구: 조합 중인 글자를 확정하고 그대로 입력 */
    override fun onText(text: String) {
        abortVoice()
        lastJamoKey = null
        val ic = currentInputConnection ?: return
        if (layout.hangul && text.length == 1 && Jamo.isJamo(text[0])) {
            // 꾹 누르기로 고른 쌍자음 등은 한글 조합에 참여
            commitJamo(text[0])
        } else {
            flushComposing()
            ic.commitText(text, 1)
        }
        if (view.shiftState == ShiftState.ON) view.shiftState = ShiftState.OFF
    }

    /** 마이크 키: 듣는 중이면 마무리, 아니면 시작 (권한 없으면 요청 화면) */
    private fun onVoice() {
        if (voice.listening) {
            voice.stop()
            return
        }
        flushComposing()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            startActivity(Intent(this, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        voice.start(if (layout.hangul) "ko-KR" else "en-US")
    }

    /** 듣는 중에 다른 키를 누르면 인식을 취소하고, 이미 보인 중간 결과는 그대로 확정 */
    private fun abortVoice() {
        if (!voice.listening) return
        voice.cancel()
        currentInputConnection?.finishComposingText()
    }

    /** 글자 키 위 좌우 스와이프 → 커서 이동 */
    override fun onCursorMove(steps: Int) = moveCursor(steps)

    /** 조합 중인 글자를 확정한 뒤 커서를 [steps]칸 이동 (음수=왼쪽) */
    private fun moveCursor(steps: Int) {
        abortVoice()
        flushComposing()
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    /** 전체선택·복사·붙여넣기 */
    private fun editAction(id: Int) {
        flushComposing()
        currentInputConnection?.performContextMenuAction(id)
    }

    /** 스페이스바 스와이프 → 한/영 전환 */
    override fun onSwipeSpace() {
        abortVoice()
        setLayout(if (layout.hangul) Layouts.EN else Prefs.koreanLayout(this))
    }

    private fun onChar(key: Key) {
        val shifted = view.shiftState != ShiftState.OFF
        val text = key.outputFor(shifted)
        if (layout.hangul && text.length == 1 && Jamo.isJamo(text[0])) {
            if (layout.doubleTap && tryDoubleTap(text)) {
                // 연타 변환 완료
            } else {
                commitJamo(text[0])
            }
        } else {
            lastJamoKey = null
            flushComposing()
            currentInputConnection?.commitText(text, 1)
        }
        if (view.shiftState == ShiftState.ON) view.shiftState = ShiftState.OFF
    }

    /**
     * 단모음 자판: 같은 키를 [DOUBLE_TAP_MS] 안에 다시 치면 마지막 자모를 변환 (ㄱ→ㄲ, ㅏ→ㅑ).
     * 변환 후 같은 키를 또 치면 새 글자로 취급 (3연타는 변환 연쇄 없음).
     * @return 변환했으면 true
     */
    private fun tryDoubleTap(text: String): Boolean {
        val now = SystemClock.uptimeMillis()
        val isDouble = text == lastJamoKey && now - lastJamoTime <= DOUBLE_TAP_MS
        if (isDouble) {
            val out = composer.doubleTap(text[0])
            if (out != null) {
                val ic = currentInputConnection ?: return true
                ic.setComposingText(out.composing, 1)
                lastJamoKey = null
                return true
            }
        }
        lastJamoKey = text
        lastJamoTime = now
        return false
    }

    /** 자모를 조합 엔진에 넣고 확정/조합 텍스트를 입력창에 반영 */
    private fun commitJamo(jamo: Char) {
        val ic = currentInputConnection ?: return
        val out = composer.input(jamo)
        ic.beginBatchEdit()
        if (out.committed.isNotEmpty()) ic.commitText(out.committed, 1)
        ic.setComposingText(out.composing, 1)
        ic.endBatchEdit()
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
        lastJamoKey = null
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
