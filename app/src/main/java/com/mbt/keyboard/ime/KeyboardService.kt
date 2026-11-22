package com.mbt.keyboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.ViewGroup
import android.widget.FrameLayout
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
import com.mbt.keyboard.R
import com.mbt.keyboard.settings.Prefs
import com.mbt.keyboard.spell.SpellChecker

class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var root: FrameLayout
    private lateinit var view: KeyboardView
    private lateinit var panel: EmojiPanel
    private lateinit var haptics: Haptics
    private lateinit var voice: VoiceInput
    private val composer = HangulComposer()
    private var layoutId = Layouts.KOS
    private var lastShiftTap = 0L

    /** 쉬프트 키를 누르고 있는 동안: 그 사이 다른 키를 쳤는지, 누르기 전 상태 */
    private var shiftHeld = false
    private var shiftUsedWhileHeld = false
    private var shiftBeforeHold = ShiftState.OFF

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
        updateSplit(resources.configuration)
        panel = EmojiPanel(this, emojiListener).also { it.visibility = View.GONE }
        // 키보드와 이모지 패널을 같은 자리에 겹쳐 두고 번갈아 보여준다 (패널 높이 = 키보드 높이)
        root = FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return root
    }

    /** 폴드를 펼치거나 접으면 화면 크기가 바뀐다 → 나눠쓰기 전환 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::view.isInitialized) updateSplit(newConfig)
    }

    /** 가장 짧은 변이 [LARGE_SCREEN_DP] 이상(폴드 안쪽 화면, 태블릿)이면 나눠쓰기. 폰 가로 모드는 해당 없음 */
    private fun updateSplit(config: Configuration) {
        view.split = Prefs.splitOnLargeScreen(this) && config.smallestScreenWidthDp >= LARGE_SCREEN_DP
    }

    private val emojiListener = object : EmojiPanel.Listener {
        override fun onInsert(text: String) {
            haptics.key()
            currentInputConnection?.commitText(text, 1)
        }

        override fun onBackspace() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        override fun onClose() = hideEmojiPanel()
    }

    private fun showEmojiPanel() {
        flushComposing()
        panel.setBottomInset(view.bottomInsetPx)
        view.visibility = View.INVISIBLE   // 높이는 유지해야 패널이 같은 크기로 뜬다
        panel.visibility = View.VISIBLE
    }

    private fun hideEmojiPanel() {
        panel.visibility = View.GONE
        view.visibility = View.VISIBLE
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        hideEmojiPanel()
        updateSplit(resources.configuration)   // 설정에서 나눠쓰기를 바꿨을 수 있다
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
            KeyType.SHIFT -> Unit   // 쉬프트는 onShiftPress로 처리
            KeyType.VOICE -> onVoice()
            KeyType.EMOJI -> showEmojiPanel()
            KeyType.SPELL -> onSpellCheck()
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
            // 괄호·따옴표 쌍: 커서를 두 글자 사이로
            if (text in Layouts.PAIRS) sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
        }
        consumeShift()
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
        // 쉬프트가 켜져 있으면 이동하면서 글자 선택 (쉬프트 + 방향키)
        val select = view.shiftState != ShiftState.OFF
        repeat(kotlin.math.abs(steps)) { if (select) sendDpadWithShift(code) else sendDownUpKeyEvents(code) }
    }

    private fun sendDpadWithShift(code: Int) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /** 선택 제스처가 끝나면 한 번용 쉬프트(ON)는 해제. 고정(LOCK)은 유지 */
    override fun onCursorMoveEnd() = consumeShift()

    /**
     * 맞춤법 검사 (온디바이스 규칙). 선택한 글자가 있으면 그 부분, 없으면 커서 앞 글자(최대 [SPELL_MAX_CHARS]자)를 고친다.
     */
    private fun onSpellCheck() {
        val ic = currentInputConnection ?: return
        flushComposing()
        val selected = ic.getSelectedText(0)?.toString()
        val hasSelection = !selected.isNullOrEmpty()
        val source = if (hasSelection) selected!! else ic.getTextBeforeCursor(SPELL_MAX_CHARS, 0)?.toString().orEmpty()
        if (source.isBlank()) {
            toast(getString(R.string.spell_nothing))
            return
        }
        val result = SpellChecker.check(source)
        if (result.changes.isEmpty()) {
            toast(getString(R.string.spell_clean))
            return
        }
        ic.beginBatchEdit()
        if (!hasSelection) ic.deleteSurroundingText(source.length, 0)
        ic.commitText(result.text, 1)
        ic.endBatchEdit()
        val shown = result.changes.take(3).joinToString(", ") { "${it.from}→${it.to}" }
        val more = if (result.changes.size > 3) " …" else ""
        toast(getString(R.string.spell_done, result.changes.size, shown + more))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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
        consumeShift()
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

    /**
     * 쉬프트 키 누름/뗌.
     * - 누른 채로 다른 키를 치면 그 동안만 쉬프트 (뗄 때 해제)
     * - 키를 치지 않고 바로 떼면 탭: 한 번은 다음 글자만, 빠르게 두 번은 고정
     */
    override fun onShiftPress(down: Boolean) {
        if (down) {
            shiftHeld = true
            shiftUsedWhileHeld = false
            shiftBeforeHold = view.shiftState
            if (view.shiftState == ShiftState.OFF) view.shiftState = ShiftState.ON   // 누르는 즉시 적용
        } else {
            shiftHeld = false
            if (shiftUsedWhileHeld) {
                view.shiftState = if (shiftBeforeHold == ShiftState.LOCK) ShiftState.LOCK else ShiftState.OFF
            } else {
                view.shiftState = shiftBeforeHold
                onShift()
            }
        }
    }

    /** 글자를 입력한 뒤: 누르고 있는 쉬프트는 유지, 한 번용(ON) 쉬프트는 해제 */
    private fun consumeShift() {
        if (shiftHeld) shiftUsedWhileHeld = true
        else if (view.shiftState == ShiftState.ON) view.shiftState = ShiftState.OFF
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
        const val SPELL_MAX_CHARS = 1000
        const val LARGE_SCREEN_DP = 600
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
