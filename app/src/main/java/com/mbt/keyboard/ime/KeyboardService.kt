package com.mbt.keyboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mbt.keyboard.PermissionActivity
import android.os.SystemClock
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.mbt.keyboard.hangul.HangulComposer
import com.mbt.keyboard.hangul.Jamo
import com.mbt.keyboard.layout.Key
import com.mbt.keyboard.layout.KeyType
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.phrase.Phrases
import com.mbt.keyboard.R
import com.mbt.keyboard.clipboard.ClipboardHistory
import com.mbt.keyboard.clipboard.PrefsClipboardStore
import com.mbt.keyboard.settings.Prefs
import com.mbt.keyboard.settings.ScreenProfile
import com.mbt.keyboard.settings.Timing

class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var root: FrameLayout
    private lateinit var fnTab: FnTab
    private lateinit var host: FrameLayout

    /** 키보드 틀이 실제로 놓이는 가로 폭(px). 화면 크기를 추측하지 않고 실제 측정값을 쓴다 */
    private var rootWidthPx = 0

    /** 우리가 마지막으로 입력창을 편집한 시각 (옛 선택 변경 알림 구분용) */
    private var lastEditTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var view: KeyboardView
    private lateinit var panel: EmojiPanel
    private lateinit var quickPanel: QuickPanel
    private lateinit var history: ClipboardHistory
    private lateinit var haptics: Haptics
    private lateinit var voice: VoiceInput
    private val composer = HangulComposer()
    private var layoutId = Layouts.KOS
    private val shift = ShiftController()

    /** 설정 화면에서 바꾸는 입력 타이밍 */

    /** 연타 변환 판정용: 직전에 친 자모 키와 시각 */
    /** 지운 글자 기록 (지우기 키를 왼쪽으로 밀면 되돌린다) */
    private val deletions = DeletionHistory()
    private var lastBackspaceTime = 0L
    /** 가장 최근 지우기가 새 묶음의 시작이었는가 (되돌리기 제스처의 첫 터치가 만든 삭제를 함께 되돌리기 위함) */
    private var backspaceStartedRun = false

    private val doubleTap = DoubleTapDetector(Timing.DOUBLE_TAP_DEFAULT_MS)

    private val layout get() = Layouts.byId(layoutId)

    override fun onCreate() {
        setTheme(R.style.Theme_MbtKeyboard_Ime)
        super.onCreate()
        layoutId = Prefs.koreanLayout(this)
        haptics = Haptics(this)
        voice = VoiceInput(this, voiceCallback)
        history = ClipboardHistory(PrefsClipboardStore(this))
        (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener(clipListener)
        // 설정 앱에서 크기·위치를 바꾸면 키보드가 켜져 있는 동안에도 바로 반영한다
        getSharedPreferences(Prefs.FILE, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)

    }
    
    /** SharedPreferences는 리스너를 약하게만 잡으므로 필드로 들고 있어야 한다 */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (::host.isInitialized && (key == Prefs.KEY_THEME || key == Prefs.KEY_TEXT_COLOR)) applyTheme()
        if (::view.isInitialized && (key == "double_tap_ms" || key == Prefs.KEY_REPEAT_CPS || key == Prefs.KEY_SWIPE_DOUBLE || key == Prefs.KEY_NUMBER_LEFT || key == Prefs.KEY_FONT_SCALE || key == Prefs.KEY_FN_TAB || key == Prefs.KEY_FN_TAB_ALPHA || key == Prefs.KEY_KEY_OPACITY || key == Prefs.KEY_BG_OPACITY || key == Prefs.KEY_GLASS_TYPE || key == Prefs.KEY_KEY_GLASS_STRENGTH || key == Prefs.KEY_BG_GLASS_STRENGTH || key == Prefs.KEY_KEY_RADIUS_DP || key == Prefs.KEY_BG_RADIUS_DP)) loadTimings()
        if (::view.isInitialized && key != null && (key.startsWith("geo_") || key == "split_on_large_screen" || key == Prefs.KEY_SPLIT_DUP)) {
            updateSplit(resources.configuration)
            applyGeometry(resources.configuration)
        }
    }

    /** 클립보드 내용이 바뀔 때마다 기록에 남긴다 (키보드가 떠 있는 동안) */
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { recordClipboard() }

    /**
     * 지금 클립보드 내용을 기록에 추가한다. 다음은 저장하지 않는다:
     * 설정에서 끈 경우 / 비밀번호 입력칸 / 비밀번호 관리자 등이 "민감 정보"로 표시한 복사 / 글자가 아닌 내용.
     */
    internal fun recordClipboard(inputType: Int? = currentInputEditorInfo?.inputType) {
        if (!Prefs.clipboardEnabled(this)) return
        if (inputType != null && InputKinds.isPassword(inputType)) return
        val clip = try {
            (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
        } catch (e: SecurityException) {
            null
        } ?: return
        if (clip.description?.extras?.getBoolean(InputKinds.EXTRA_IS_SENSITIVE, false) == true) return
        if (clip.itemCount == 0) return
        history.add(clip.getItemAt(0)?.coerceToText(this)?.toString())
    }

    override fun onDestroy() {
        (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(clipListener)
        getSharedPreferences(Prefs.FILE, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        voice.destroy()
        super.onDestroy()
    }

    private val voiceCallback = object : VoiceInput.Callback {
        override fun onListening(on: Boolean) {
            if (::view.isInitialized) view.voiceListening = on
        }

        /** 말하는 중 인식된 글자는 조합 중 텍스트로 보여준다 */
        override fun onPartial(text: String) {
            endDeletionRun()
            currentInputConnection?.setComposingText(text, 1)
        }

        override fun onResult(text: String) {
            endDeletionRun()
            currentInputConnection?.commitText(text, 1)
        }

        override fun onError(message: String) {
            currentInputConnection?.finishComposingText()
            say(message)
        }
    }

    override fun onCreateInputView(): View {
        view = KeyboardView(this).also {
            it.listener = this
            it.haptics = haptics
            it.keyboardLayout = Layouts.byId(layoutId)
        }
        updateSplit(resources.configuration)
        loadTimings()
        val theme = KeyboardTheme.current(this)
        view.theme = theme
        panel = EmojiPanel(this, emojiListener, theme).also { it.visibility = View.GONE }
        quickPanel = QuickPanel(this, quickListener, theme).also { it.visibility = View.GONE }
        // 키보드와 이모지 패널을 같은 자리에 겹쳐 두고 번갈아 보여준다 (패널 높이 = 키보드 높이)
        // host: 키보드·패널을 담는 틀. 설정한 가로/세로 크기와 위치(좌우, 바닥에서 띄우기)는 이 틀에 적용한다
        host = FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(quickPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        fnTab = FnTab(this, view).also {
            it.haptics = haptics
            it.theme = theme
            it.visibility = View.GONE
            it.onKey = { key -> onKey(key) }
        }
        view.onRelayoutRequested = { fnTab.requestLayout(); fnTab.invalidate() }
        root = FrameLayout(this).apply {
            addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // 전체·복사·붙임 반투명 탭: 키보드 맨 위 왼쪽에 겹쳐 띄운다 (자리는 host 위쪽 여백으로 확보)
            addView(fnTab, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP or android.view.Gravity.START))
            // 실제 가로 폭이 정해지거나 바뀌면(폴드 펼침/접힘, 회전, 분할 화면) 그 폭으로 다시 계산
            addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                val w = r - l
                if (w > 0 && w != rootWidthPx) {
                    rootWidthPx = w
                    // 레이아웃 도중에 다시 레이아웃을 요청하지 않도록 다음 루프에서 적용
                    mainHandler.post { applyGeometry(resources.configuration) }
                }
            }
        }
        applyGeometry(resources.configuration)
        return root
    }

    /**
     * 테마를 바꾸면 키보드와 두 패널의 색을 바로 바꾼다.
     * 패널은 색을 만들 때 한 번만 읽으므로 새로 만들어 같은 자리에 끼운다 (보이던 패널은 닫힌다).
     */
    internal fun applyTheme() {
        val theme = KeyboardTheme.current(this)
        view.theme = theme
        view.invalidate()
        fnTab.theme = theme
        host.removeView(panel)
        host.removeView(quickPanel)
        panel = EmojiPanel(this, emojiListener, theme).also { it.visibility = View.GONE }
        quickPanel = QuickPanel(this, quickListener, theme).also { it.visibility = View.GONE }
        host.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.addView(quickPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.visibility = View.VISIBLE
        refreshFnTab()
        applyWindowBlur()
    }

    /** 설정 화면에서 바꾼 입력 타이밍(멈춤 기준, 꾹 누르기 시간)을 반영 */
    private fun loadTimings() {
        doubleTap.windowMs = Prefs.doubleTapMs(this)
        view.longPressMs = Prefs.longPressMs(this)
        view.repeatIntervalMs = Timing.repeatIntervalMs(Prefs.repeatCps(this))
        view.swipeDouble = Prefs.singleSwipeDouble(this)
        view.numberLeft = Prefs.numberKeysLeft(this)
        view.fontScale = Prefs.fontScalePercent(this) / 100f
        view.frostFadeAlpha = Prefs.fnTabAlphaPercent(this) / 100f
        view.keyOpacity = Prefs.keyOpacityPercent(this) / 100f
        view.bgOpacity = Prefs.bgOpacityPercent(this) / 100f
        view.glassType = Prefs.glassType(this)
        view.keyGlassStrength = Prefs.keyGlassStrengthPercent(this) / 100f
        view.bgGlassStrength = Prefs.bgGlassStrengthPercent(this) / 100f
        view.keyRadiusDp = Prefs.keyRadiusDp(this).toFloat()
        view.bgRadiusDp = Prefs.bgRadiusDp(this).toFloat()
        refreshFnTab()
        applyWindowBlur()
    }

    /**
     * 일반(0)을 제외한 유리효과(블러·리드·3D·무광)에서 배경 불투명도를 낮춰 뒤가 비칠 때,
     * 삼성 One UI(`SemBlurInfo`) 또는 Android 12+(`setBackgroundBlurRadius`)로 키보드 판 뒤 화면에
     * 실시간 가우시안 블러를 적용한다.
     */
    internal fun applyWindowBlur() {
        val win = window?.window ?: return
        val blurActive = ::view.isInitialized &&
            view.bgGlassOn &&
            view.effectiveGlassType != 0 &&
            view.bgOpacity < 0.999f
        val density = resources.displayMetrics.density
        val plateRadiusPx = if (::view.isInitialized) (view.bgRadiusDp * density).coerceAtLeast(0f) else 0f
        val topInsetPx = if (::host.isInitialized) (host.layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: 0 else 0
        WindowBlur.apply(
            win = win,
            plateView = if (::host.isInitialized) host else null,
            tabView = if (::fnTab.isInitialized) fnTab else null,
            active = blurActive,
            strength = if (::view.isInitialized) view.bgGlassStrength else 0f,
            density = density,
            plateRadiusPx = plateRadiusPx,
            topInsetPx = topInsetPx,
        )
    }

    override fun onWindowShown() {
        super.onWindowShown()
        applyWindowBlur()
        if (::root.isInitialized) root.post { applyWindowBlur() }
    }

    /**
     * 숫자 띠가 있는 자판에서만 전체·복사·붙임을 반투명 탭으로 띄운다.
     * 탭 자리는 키보드 위쪽 여백으로 잡고, 앱 화면이 그 자리를 비우지 않도록 [onComputeInsets]에서 뺀다.
     */
    private fun refreshFnTab() {
        if (!::fnTab.isInitialized || !::host.isInitialized) return
        val large = ScreenProfile.isLarge(this)
        val g = Prefs.geometry(this, large, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val on = Prefs.fnTabEnabled(this) && (view.hasSideStrip || view.hasTopNumberRow)
        view.fnTab = on
        view.fnTabWidthScale = g.fnTabWidthPct / 100f
        fnTab.theme = view.theme
        fnTab.alphaPercent = Prefs.fnTabAlphaPercent(this)
        val cw = if (rootWidthPx > 0) rootWidthPx else resources.displayMetrics.widthPixels
        KeyboardHost.applyFnTab(host, fnTab, view, on, view.visibility == View.VISIBLE, g.fnTabXdp, g.fnTabYdp, cw)
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {

        super.onComputeInsets(outInsets)
        if (!::fnTab.isInitialized || isFullscreenMode) return
        val tabH = if (fnTab.visibility == View.GONE) 0 else (host.layoutParams as FrameLayout.LayoutParams).topMargin
        // 글래스 테마는 판 맨 위 띠(약 6dp)도 뒤 앱이 비치는 영역이라 앱 화면을 그만큼 더 아래까지 깐다
        val fade = if (view.bgGlassOn) view.frostFadePx().toInt() else 0
        if (tabH <= 0 && fade <= 0) return
        // 탭·띠 자리는 앱 화면을 밀어 올리지 않고, 터치는 탭과 키보드에만 반응한다 (탭 옆 빈 곳은 뒤의 앱으로 전달)
        val rootTop = outInsets.contentTopInsets
        outInsets.contentTopInsets = rootTop + tabH + fade
        outInsets.visibleTopInsets += tabH + fade
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, rootTop + tabH, root.width, rootTop + root.height)
        if (fnTab.visibility == View.VISIBLE) {
            val loc = IntArray(2)
            fnTab.getLocationInWindow(loc)
            outInsets.touchableRegion.union(android.graphics.Rect(loc[0], loc[1], loc[0] + fnTab.width, loc[1] + fnTab.height))
        }
    }

    /** 폴드를 펼치거나 접으면 화면 크기가 바뀐다 → 나눠쓰기 전환 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 시스템 테마(기본)는 휴대폰의 밝은/어두운 모드가 바뀌면 같이 바뀐다
        if (::host.isInitialized && Prefs.themeId(this) == KeyboardThemes.SYSTEM) applyTheme()
        if (::view.isInitialized) {
            updateSplit(newConfig)
            applyGeometry(newConfig)
        }
    }

    /**
     * 설정한 키보드 크기·위치를 적용한다. 폴드를 접었을 때와 펼쳤을 때 각각 따로 저장된 값을 쓴다.
     */
    private fun applyGeometry(config: Configuration) {
        val large = ScreenProfile.isLarge(this)
        val g = Prefs.geometry(this, large, config.orientation == Configuration.ORIENTATION_LANDSCAPE).copy(xDp = 0, raiseDp = 0)       // 위치 이동은 없앴다: 크기와 여백으로만 맞춘다
        val density = resources.displayMetrics.density
        val screenW = when {
            rootWidthPx > 0 -> rootWidthPx
            config.screenWidthDp > 0 -> (config.screenWidthDp * density).toInt()
            else -> resources.displayMetrics.widthPixels
        }
        KeyboardHost.apply(host, screenW, g, density)
        // 가로 모드처럼 화면이 낮으면 키 높이를 자동으로 줄인다 (사용자가 정한 세로 길이에 곱해진다)
        view.heightScale = g.heightScale * ScreenProfile.heightFactor(config.screenHeightDp)
        view.splitGapFraction = g.gapFraction
        view.fnTabHeightScale = g.fnTabHeightPct / 100f            // 전복붙 탭 높이·여백도 화면 상태별(펼침/접힘 × 세로/가로) 설정
        view.fnTabPadDp = g.fnTabPadDp
        view.frame = KeyboardFrame(g.padLeftDp, g.padTopDp, g.padRightDp, g.padBottomDp, g.keyGapDp, g.rowGapDp, g.numHeightScale, g.numRowGapDp, g.hitShifts, g.spaceRowScale)
        refreshFnTab()
        applyWindowBlur()
    }

    
    /** 가장 짧은 변이 600dp 이상(폴드 안쪽 화면, 태블릿)이면 나눠쓰기. 폰 가로 모드는 해당 없음 */
    private fun updateSplit(config: Configuration) {
        val large = ScreenProfile.isLarge(this)
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val gap = Prefs.geometry(this, large, landscape).gapPct
        view.split = ScreenProfile.splitApplies(large, landscape, Prefs.splitOnLargeScreen(this), gap)
        view.splitDuplicate = Prefs.splitDuplicateKeys(this)
    }

    private val emojiListener = object : EmojiPanel.Listener {
        override fun onInsert(text: String) {
            haptics.key()
            endDeletionRun()
            currentInputConnection?.commitText(text, 1)
        }

        override fun onClose() = hideEmojiPanel()
    }

    /**
     * 패널 높이를 키보드 높이와 똑같이 고정한다.
     * (고정하지 않으면 내용만큼 늘어나 화면을 덮고, 아래쪽 닫기 막대가 화면 밖으로 밀려난다)
     */
    private fun fitToKeyboard(p: View) {
        val lp = p.layoutParams
        lp.height = view.height.coerceAtLeast(1)
        p.layoutParams = lp
    }

    private fun showEmojiPanel() {
        flushComposing()
        fitToKeyboard(panel)
        panel.refresh()
        panel.setBottomInset(view.bottomInsetPx)
        view.visibility = View.INVISIBLE   // 높이는 유지해야 패널이 같은 크기로 뜬다
        panel.visibility = View.VISIBLE
        refreshFnTab()
    }

    private fun hideEmojiPanel() {
        panel.visibility = View.GONE
        quickPanel.visibility = View.GONE
        view.visibility = View.VISIBLE
        refreshFnTab()
    }

    private val quickListener = object : QuickPanel.Listener {
        override fun onInsert(text: String) {
            haptics.key()
            endDeletionRun()
            currentInputConnection?.commitText(text, 1)
            hideEmojiPanel()      // 입력하면 키보드로 돌아간다
        }

        override fun onDeleteClip(text: String) {
            history.remove(text)
            quickPanel.setClips(history.items())
        }

        override fun onClearClips() {
            history.clear()
            quickPanel.setClips(history.items())
        }

        override fun onClose() = hideEmojiPanel()

        override fun onTabChanged(tab: Int) = Prefs.setQuickPanelTab(this@KeyboardService, tab)
    }

    /** 엔터 꾹 누르기: 상용문구 | 최근기록 탭 패널 (마지막에 보던 탭으로 열린다) */
    override fun onEnterLongPress() {
        abortVoice()
        flushComposing()
        recordClipboard()                       // 지금 클립보드 내용도 최근기록에 포함
        quickPanel.open(Phrases.load(this), history.items(), Prefs.quickPanelTab(this))
        fitToKeyboard(quickPanel)
        quickPanel.setBottomInset(view.bottomInsetPx)
        view.visibility = View.INVISIBLE
        quickPanel.visibility = View.VISIBLE
        refreshFnTab()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) deletions.clear()
        composer.reset()
        hideEmojiPanel()
        recordClipboard(info.inputType)      // 키보드가 꺼져 있는 동안 복사한 내용도 놓치지 않고 기록
        updateSplit(resources.configuration)   // 설정에서 나눠쓰기를 바꿨을 수 있다
        applyGeometry(resources.configuration)  // 설정에서 크기·위치를 바꿨을 수 있다
        loadTimings()
        shift.reset(); syncShift()
        haptics.refresh()   // 설정 화면에서 바꾼 진동 설정 반영
        // 비밀번호·이메일·URL 입력칸은 그 칸에서만 영문 자판으로 (직접 고른 언어는 바꾸지 않는다).
        // 그 밖의 입력칸은 마지막으로 직접 고른 언어로 시작한다.
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val wantsLatin = cls == InputType.TYPE_CLASS_TEXT && variation in LATIN_VARIATIONS
        val remembered = rememberedLetterLayout()
        val target = when {
            // 숫자·날짜 입력칸은 숫자 키패드, 전화번호 칸은 전화번호 키패드 (ABC 키로 글자 자판 전환 가능)
            cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME -> Layouts.NUM
            cls == InputType.TYPE_CLASS_PHONE -> Layouts.PHONE
            wantsLatin -> Layouts.EN
            else -> remembered
        }
        if (target != layoutId) setLayout(target)
        refreshAutoCaps()      // 영어 자판이고 입력칸이 문장 시작이면 첫 글자는 대문자
    }

    override fun onFinishInput() {
        deletions.clear()
        composer.reset()
        doubleTap.reset()
        voice.cancel()
        super.onFinishInput()
    }

    /**
     * 가로 모드에서 안드로이드가 기본으로 띄우는 "전체화면 입력창"(화면 전체를 덮는 편집창)을 끈다.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** 커서가 앱 쪽에서 움직였으면 조합 상태를 버린다. (옛 알림은 무시: [SelectionUpdatePolicy]) */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (SelectionUpdatePolicy.shouldResetComposition(
                composer.isComposing, newSelStart, newSelEnd, candidatesEnd,
                SystemClock.uptimeMillis() - lastEditTime,
            )
        ) {
            composer.reset()
            currentInputConnection?.finishComposingText()
        }
    }

    override fun onKey(key: Key) {
        lastEditTime = SystemClock.uptimeMillis()
        if (key.type != KeyType.VOICE) abortVoice()
        if (key.type != KeyType.CHAR) doubleTap.reset()
        if (key.type != KeyType.BACKSPACE) endDeletionRun()
        when (key.type) {
            KeyType.CHAR -> onChar(key)
            KeyType.SPACE -> { flushComposing(); commit(" "); refreshAutoCaps() }
            KeyType.BACKSPACE -> { onBackspace(); refreshAutoCaps() }
            KeyType.ENTER -> { onEnter(); refreshAutoCaps() }
            KeyType.SHIFT -> Unit   // 쉬프트는 onShiftPress로 처리
            KeyType.VOICE -> onVoice()
            KeyType.EMOJI -> showEmojiPanel()
            KeyType.LETTERS -> setLayout(rememberedLetterLayout())     // 숫자 키패드 → 글자 자판
            KeyType.SELECT_ALL -> editAction(android.R.id.selectAll)
            KeyType.COPY -> editAction(android.R.id.copy)
            KeyType.PASTE -> editAction(android.R.id.paste)
            KeyType.SPACER -> Unit
        }
    }

    /** 꾹 누르기 팝업에서 고른 기호·상용문구: 조합 중인 글자를 확정하고 그대로 입력 */
    override fun onText(text: String) {
        lastEditTime = SystemClock.uptimeMillis()
        endDeletionRun()
        abortVoice()
        doubleTap.reset()
        val ic = currentInputConnection ?: return
        if (layout.hangul && text.length == 1 && Jamo.isJamo(text[0])) {
            // 꾹 누르기로 고른 쌍자음 등은 한글 조합에 참여
            commitJamo(text[0])
        } else {
            flushComposing()
            ic.commitText(text, 1)
            // 괄호·따옴표 쌍: 상용구처럼 두 글자를 먼저 입력한 뒤 커서를 왼쪽 1칸 이동
            if (text in Layouts.PAIRS) moveCursorLeftAfterPair(ic)
        }
        consumeShift()
    }

    /**
     * 쌍 특수문자(`()`, `[]` 등) 입력 직후 커서를 왼쪽 1칸 옮긴다.
     * 1차로 `getExtractedText` + `setSelection`으로 직접 커서를 이동해 웹뷰·브라우저·일반 에디터에서
     * 확실히 동작하게 하고, 이를 지원하지 않는 입력창(터미널 등)에서만 다음 루프에 `DPAD_LEFT`로 폴백한다.
     */
    internal fun moveCursorLeftAfterPair(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.selectionEnd > 0) {
            val pos = extracted.startOffset.coerceAtLeast(0) + extracted.selectionEnd
            if (pos > 0 && ic.setSelection(pos - 1, pos - 1)) return
        }
        mainHandler.post {
            sendKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0)
            sendKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0)
        }
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
        endDeletionRun()
        flushComposing()
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        // 쉬프트가 켜져 있으면 이동하면서 글자 선택 (쉬프트 + 방향키)
        val select = shift.state != ShiftState.OFF
        repeat(kotlin.math.abs(steps)) { if (select) sendDpadWithShift(code) else sendDownUpKeyEvents(code) }
    }

    /**
     * 쉬프트 + 방향키로 선택 범위 확장. 일반 키보드처럼 "소프트 키보드 입력" 플래그를 달고,
     * 쉬프트 키 누름/뗌 이벤트로 감싸서 보낸다 (플래그나 쉬프트 키 이벤트가 없으면 선택이 안 되는 앱이 많다).
     */
    private fun sendDpadWithShift(code: Int) {
        val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        sendKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, meta)
        sendKey(KeyEvent.ACTION_DOWN, code, meta)
        sendKey(KeyEvent.ACTION_UP, code, meta)
        sendKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0)
    }

    private fun sendKey(action: Int, code: Int, meta: Int) {
        val now = SystemClock.uptimeMillis()
        currentInputConnection?.sendKeyEvent(
            KeyEvent(
                now, now, action, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
            ),
        )
    }

    /** 선택 제스처가 끝나면 한 번용 쉬프트(ON)는 해제. 고정(LOCK)은 유지 */
    override fun onCursorMoveEnd() {
        consumeShift()
        refreshAutoCaps()
    }

    /** 사용자에게 결과를 알린다: 키보드 안 배너 (키보드가 아직 없으면 토스트) */
    private fun say(msg: String) {
        if (::view.isInitialized) view.showMessage(msg) else toast(msg)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** 전체선택·복사·붙여넣기 */
    private fun editAction(id: Int) {
        flushComposing()
        currentInputConnection?.performContextMenuAction(id)
    }

    /** 마지막으로 직접 고른 언어의 글자 자판 (영어 또는 설정한 한글 자판) */
    private fun rememberedLetterLayout() =
        if (Prefs.lastLanguageEnglish(this)) Layouts.EN else Prefs.koreanLayout(this)

    /** 스페이스바 스와이프 → 한/영 전환 */
    override fun onSwipeSpace() {
        abortVoice()
        val english = layout.hangul                       // 한글이면 영어로, 영어면 한글로
        setLayout(if (english) Layouts.EN else Prefs.koreanLayout(this))
        Prefs.setLastLanguageEnglish(this, english)      // 직접 바꾼 언어를 기억 → 다음에 키보드를 띄울 때도 이 언어
    }

    private fun onChar(key: Key) {
        val shifted = shift.state != ShiftState.OFF
        val text = key.outputFor(shifted)
        if (layout.hangul && text.length == 1 && Jamo.isJamo(text[0])) {
            val jamo = text[0]
            if (layout.doubleTap && usesTapConversion(jamo)) {
                // 연타 기준 시간 안에 같은 키를 다시 치면 변환(ㄱ→ㄲ), 지난 뒤면 변환 없이 따로 입력(ㄱ + ㄱ)
                if (!tryDoubleTap(text, SystemClock.uptimeMillis())) commitJamo(jamo)
            } else {
                commitJamo(jamo)
            }
        } else {
            doubleTap.reset()
            flushComposing()
            currentInputConnection?.commitText(text, 1)
        }
        consumeShift()
    }

    /**
     * 단모음 자판: 같은 키를 [DoubleTapDetector.windowMs](설정) 안에 다시 치면 마지막 자모를 변환 (ㄱ→ㄲ, ㅏ→ㅑ).
     * 변환 후 같은 키를 또 치면 새 글자로 취급 (3연타는 변환 연쇄 없음).
     * @return 변환했으면 true
     */
    /** 연타로 변환(ㄱ→ㄲ, ㅏ→ㅑ)하는 키인가. 단모음 개선판을 켜면 그 키들은 밀어서 입력하므로 연타 변환을 쓰지 않는다 */
    internal fun usesTapConversion(jamo: Char) = !(Prefs.singleSwipeDouble(this) && jamo in Jamo.DOUBLE)

    private fun tryDoubleTap(text: String, now: Long): Boolean {
        if (doubleTap.isDouble(text, now)) {
            val out = composer.doubleTap(text[0])
            if (out != null) {
                applyOutput(out)
                doubleTap.reset()
                return true
            }
        }
        doubleTap.record(text, now)
        return false
    }

    /** 자모를 조합 엔진에 넣고 확정/조합 텍스트를 입력창에 반영 */
    private fun commitJamo(jamo: Char) = applyOutput(composer.input(jamo))

    /** 조합 엔진의 결과(확정할 글자 + 조합 중인 글자)를 입력창에 반영 */
    private fun applyOutput(out: HangulComposer.Output) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (out.committed.isNotEmpty()) ic.commitText(out.committed, 1)
        ic.setComposingText(out.composing, 1)
        ic.endBatchEdit()
    }

    private fun textBeforeCursor(): String =
        currentInputConnection?.getTextBeforeCursor(DeletionHistory.WINDOW, 0)?.toString().orEmpty()

    /** 지우기 묶음을 끝내고 기록에 남긴다 (다른 입력이 시작되기 전에 불러야 한다). 기록이 생겼으면 true */
    private fun endDeletionRun(): Boolean = deletions.hasRun && deletions.finish(textBeforeCursor())

    /** 지우기 키를 누를 때마다: 연속으로 지우는 중이면 같은 묶음, 쉬었다가 다시 지우면 새 묶음 */
    private fun noteBackspace() {
        val now = SystemClock.uptimeMillis()
        val startsRun = !deletions.hasRun || now - lastBackspaceTime > DELETE_RUN_GAP_MS
        if (startsRun) {
            endDeletionRun()
            val ic = currentInputConnection
            if (ic != null) deletions.begin(textBeforeCursor() + (ic.getSelectedText(0)?.toString().orEmpty()))
        }
        backspaceStartedRun = startsRun
        lastBackspaceTime = now
    }

    /** 지우기 키를 왼쪽으로 밀었다: 지운 글자를 커서 자리에 되살린다 */
    /** 모아키: 자음을 누른 채 밀어 모음까지 한 번에 입력 (예: ㄱ + ㅏ → 가) */
    override fun onMoaki(key: Key, vowel: Char) {
        lastEditTime = SystemClock.uptimeMillis()
        abortVoice()
        endDeletionRun()
        doubleTap.reset()
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        applyOutput(composer.input(key.output[0]))
        applyOutput(composer.input(vowel))
        ic.endBatchEdit()
        consumeShift()
        refreshAutoCaps()
    }

    override fun onUndoDelete() {
        abortVoice()
        // 이 제스처의 첫 터치가 새로 만든 삭제도 같이 되돌리고, 그 앞에 지웠던 것 하나를 더 되살린다
        val touchDeleted = endDeletionRun()
        val times = if (backspaceStartedRun && touchDeleted) 2 else 1
        backspaceStartedRun = false
        var restored = 0
        repeat(times) { if (restoreLastDeletion()) restored++ }
        if (restored > 0 && ::view.isInitialized) view.showUndoMark()      // 글자 없이 ↶ 표시만
    }

    private fun restoreLastDeletion(): Boolean {
        val chunk = deletions.pop() ?: return false
        val ic = currentInputConnection ?: return false
        flushComposing()
        composer.reset()
        doubleTap.reset()
        if (chunk.removeLen > 0 && ic.getTextBeforeCursor(chunk.removeLen, 0)?.toString() != chunk.expectTail) {
            deletions.clear()          // 그 사이 글이 바뀌었다: 위치가 맞지 않으니 기록을 버린다
            return false
        }
        ic.beginBatchEdit()
        if (chunk.removeLen > 0) ic.deleteSurroundingText(chunk.removeLen, 0)
        ic.commitText(chunk.restore, 1)
        ic.endBatchEdit()
        return true
    }

    private fun onBackspace() {
        noteBackspace()
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
     * 쉬프트 키 누름/뗌 (상태 변화는 [ShiftController] 참고).
     * 한 번 탭: 켜기, 빠르게 두 번: 캡스락(고정), 고정에서 한 번 더: 끄기, 누른 채 다른 키 입력: 그 동안만.
     */
    override fun onShiftPress(down: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (down) shift.press(now) else shift.release()
        syncShift()
    }

    /** 글자를 입력한 뒤: 누르고 있는 쉬프트는 유지, 한 번용(ON) 쉬프트는 해제 */
    private fun consumeShift() {
        shift.consume()
        syncShift()
    }

    private fun syncShift() {
        view.shiftState = shift.state
    }

    /**
     * 영어 자동 대문자: 입력창이 "문장 시작"(맨 처음, 마침표·물음표·느낌표 뒤, 새 줄)이면 쉬프트를 한 번용으로 켠다.
     * 판단은 입력창에 물어본다([android.view.inputmethod.InputConnection.getCursorCapsMode]) —
     * 입력창이 대문자를 요청하지 않으면(비밀번호, 주소 등) 0이 와서 켜지지 않는다.
     * 앱에 묻는 호출이라 글자마다 하지 않고 스페이스·엔터·지우기·입력칸 시작 때만 한다.
     */
    private fun refreshAutoCaps() {
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        applyAutoCaps(if (ic != null && info != null) ic.getCursorCapsMode(info.inputType) else null)
    }

    /**
     * 자동 대문자 적용. [capsMode]는 입력창이 알려준 값(0이면 대문자 아님, null이면 알 수 없음).
     * 설정이 꺼져 있거나 영어 자판이 아니면(한글·숫자 키패드) 자동으로 켠 쉬프트를 끈다.
     */
    internal fun applyAutoCaps(capsMode: Int?) {
        val eligible = Prefs.autoCapitalize(this) && !layout.hangul && !Layouts.isPad(layoutId)
        if (!eligible) {
            shift.autoCapitalize(false)
        } else {
            if (capsMode == null) return
            shift.autoCapitalize(capsMode != 0)
        }
        syncShift()
    }

    private fun setLayout(id: String) {
        flushComposing()
        doubleTap.reset()
        layoutId = id
        view.keyboardLayout = Layouts.byId(id)
        refreshFnTab()
        shift.reset(); syncShift()
        refreshAutoCaps()
    }

    private fun flushComposing() {
        if (!composer.isComposing) return
        currentInputConnection?.commitText(composer.flush(), 1)
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private companion object {
        /** 이 시간 안에 이어서 지우면 한 묶음으로 본다 */
        const val DELETE_RUN_GAP_MS = 1200L


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
