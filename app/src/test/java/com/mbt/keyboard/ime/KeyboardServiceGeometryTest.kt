package com.mbt.keyboard.ime

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.phrase.Phrases
import com.mbt.keyboard.layout.Layouts
import com.mbt.keyboard.settings.KeyboardGeometry
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 실제 키보드(서비스)가 설정한 크기·위치를 적용하고, 켜져 있는 동안 바뀐 설정도 바로 반영하는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardServiceGeometryTest {

    private fun layoutRoot(root: View, widthPx: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        // 틀의 크기가 바뀌었으니 한 번 더 배치
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    @Test fun appliesSavedWidthAndHeightAndFollowsLiveSettingChanges() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 70, heightPct = 120))

        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)

        val host = root.getChildAt(0)
        assertEquals(700, host.layoutParams.width)

        // 키보드가 켜져 있는 동안 설정 앱에서 바꿈 → 새 입력창을 열지 않아도 바로 반영
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 50, heightPct = 100))
        layoutRoot(root, 1000)
        assertEquals(500, host.layoutParams.width)
    }

    @Test fun usesTheRealMeasuredWidthNotAGuess() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 80))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 640)                                  // 분할 화면처럼 화면 폭과 다른 폭
        assertEquals(512, root.getChildAt(0).layoutParams.width)
    }

    @Test fun oldRaiseAndSideOffsetAreIgnoredNowThatPositionIsSetByMarginsOnly() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val density = ctx.resources.displayMetrics.density
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(widthPct = 60, xDp = -16, raiseDp = 30))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val lp = root.getChildAt(0).layoutParams as FrameLayout.LayoutParams
        assertEquals(0, lp.bottomMargin)                       // 띄우기 없음
        assertEquals(200, lp.leftMargin)                       // 가운데 (이동값은 무시)
    }

    @Test fun framePaddingsAndKeyGapReachTheRealKeyboardAndFollowLiveChanges() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(padLeftDp = 12, padTopDp = 1, padRightDp = 20, padBottomDp = 3, keyGapDp = 2))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val keyboard = (root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView
        assertEquals(KeyboardFrame(12, 1, 20, 3, 2, hitShiftDp = listOf(3, 3, 3, 3)), keyboard.frame)   // 인식 영역 기본값: 아래로 3dp

        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(padLeftDp = 0, keyGapDp = 10))
        layoutRoot(root, 1000)
        assertEquals(0, keyboard.frame.leftDp)
        assertEquals(10, keyboard.frame.keyGapDp)
    }

    // ---- 마지막 사용 언어로 시작

    private fun startField(service: KeyboardService, inputType: Int) {
        val info = android.view.inputmethod.EditorInfo().apply { this.inputType = inputType }
        service.onStartInputView(info, false)
    }

    private fun currentLayoutId(root: FrameLayout) =
        ((root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView).keyboardLayout!!.id

    private val TEXT = android.text.InputType.TYPE_CLASS_TEXT
    private val URL = TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
    private val PASSWORD = TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

    @Test fun afterAUrlFieldTheNextNormalFieldGoesBackToKorean() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, TEXT)
        assertEquals("kos", currentLayoutId(root))
        startField(service, URL)                    // 주소창: 그 칸에서만 영어
        assertEquals("en", currentLayoutId(root))
        startField(service, PASSWORD)
        assertEquals("en", currentLayoutId(root))
        startField(service, TEXT)                   // 일반 입력칸: 다시 한글 (예전에는 영어로 남아 있었다)
        assertEquals("kos", currentLayoutId(root))
    }

    @Test fun startsInTheLastLanguageTheUserPickedAndRemembersAcrossRestarts() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, TEXT)
        service.onSwipeSpace()                       // 사용자가 직접 영어로 전환
        assertEquals("en", currentLayoutId(root))
        assertTrue(Prefs.lastLanguageEnglish(ctx))

        startField(service, TEXT)                    // 다른 입력칸에서 키보드를 다시 띄움 → 영어 유지
        assertEquals("en", currentLayoutId(root))

        // 서비스가 새로 만들어져도(프로세스 재시작 등) 마지막 언어로 시작
        val service2 = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root2 = service2.onCreateInputView() as FrameLayout
        startField(service2, TEXT)
        assertEquals("en", currentLayoutId(root2))

        service2.onSwipeSpace()                      // 다시 한글로
        assertEquals(false, Prefs.lastLanguageEnglish(ctx))
        startField(service2, TEXT)
        assertEquals("kos", currentLayoutId(root2))
    }

    @Test fun switchingLanguageInsideAnEmailFieldIsRemembered() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, URL)                     // 자동 영어 (사용자 선택 아님 → 기억하지 않음)
        assertEquals(false, Prefs.lastLanguageEnglish(ctx))
        service.onSwipeSpace()                       // 영어에서 직접 전환 → 한글
        assertEquals("kos", currentLayoutId(root))
        assertEquals(false, Prefs.lastLanguageEnglish(ctx))
    }

    // ---- 가로 모드

    @Test fun fullscreenExtractedInputIsAlwaysOff() {
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        assertEquals(false, service.onEvaluateFullscreenMode())
    }

    @Test @Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
    fun landscapePhoneShrinksKeyHeightAutomatically() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(heightPct = 100))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val keyboard = (root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView
        assertEquals(0.65f, keyboard.heightScale, 0.001f)
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h800dp-port")
    fun portraitKeepsTheUserHeight() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setGeometry(ctx, large = false, KeyboardGeometry(heightPct = 120))
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val keyboard = (root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView
        assertEquals(1.2f, keyboard.heightScale, 0.001f)
    }

    // ---- 숫자·전화번호 키패드 자동 전환

    private val NUMBER = android.text.InputType.TYPE_CLASS_NUMBER
    private val PHONE = android.text.InputType.TYPE_CLASS_PHONE
    private val DATETIME = android.text.InputType.TYPE_CLASS_DATETIME

    @Test fun numberAndPhoneFieldsGetTheirOwnPadAndGoBackToLettersAfterwards() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, TEXT)
        assertEquals("kos", currentLayoutId(root))
        startField(service, NUMBER)
        assertEquals("num", currentLayoutId(root))
        startField(service, PHONE)
        assertEquals("phone", currentLayoutId(root))
        startField(service, DATETIME)
        assertEquals("num", currentLayoutId(root))
        startField(service, TEXT)                    // 일반 입력칸으로 돌아오면 글자 자판
        assertEquals("kos", currentLayoutId(root))
    }

    @Test fun abcKeyOnThePadReturnsToTheRememberedLetterLayout() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, true)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, NUMBER)
        assertEquals("num", currentLayoutId(root))
        service.onKey(Layouts.numberPad.rows.flatten().first { it.type == com.mbt.keyboard.layout.KeyType.LETTERS })
        assertEquals("en", currentLayoutId(root))      // 마지막에 쓴 언어(영어)
    }

    @Test fun padKeepsItsLanguageMemoryUntouched() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, true)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        service.onCreateInputView()
        startField(service, PHONE)
        startField(service, NUMBER)
        assertEquals(true, Prefs.lastLanguageEnglish(ctx))
    }

    // ---- 클립보드 기록

    private fun setClip(ctx: Context, text: String, sensitive: Boolean = false) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("test", text)
        if (sensitive) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(InputKinds.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(clip)
    }

    private fun history(ctx: Context) =
        com.mbt.keyboard.clipboard.ClipboardHistory(com.mbt.keyboard.clipboard.PrefsClipboardStore(ctx)).items()

    private fun freshService(ctx: Context): KeyboardService {
        com.mbt.keyboard.clipboard.ClipboardHistory(com.mbt.keyboard.clipboard.PrefsClipboardStore(ctx)).clear()
        Prefs.setClipboardEnabled(ctx, true)
        return Robolectric.buildService(KeyboardService::class.java).create().get()
    }

    @Test fun recordsWhatWasCopiedNewestFirst() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val service = freshService(ctx)
        setClip(ctx, "첫 번째"); service.recordClipboard()
        setClip(ctx, "두 번째"); service.recordClipboard()
        setClip(ctx, "첫 번째"); service.recordClipboard()          // 같은 내용은 맨 앞으로
        assertEquals(listOf("첫 번째", "두 번째"), history(ctx))
    }

    @Test fun doesNotRecordSensitiveCopies() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val service = freshService(ctx)
        setClip(ctx, "비밀번호관리자에서복사한값", sensitive = true); service.recordClipboard()
        assertTrue(history(ctx).isEmpty())
    }

    @Test fun doesNotRecordWhenTurnedOffInSettings() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val service = freshService(ctx)
        Prefs.setClipboardEnabled(ctx, false)
        setClip(ctx, "기록하면 안 됨"); service.recordClipboard()
        assertTrue(history(ctx).isEmpty())
        Prefs.setClipboardEnabled(ctx, true)
        service.recordClipboard()                                       // 다시 켜면 기록
        assertEquals(listOf("기록하면 안 됨"), history(ctx))
    }

    @Test fun recordsTheCurrentClipWhenAnInputStarts() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        com.mbt.keyboard.clipboard.ClipboardHistory(com.mbt.keyboard.clipboard.PrefsClipboardStore(ctx)).clear()
        Prefs.setClipboardEnabled(ctx, true)
        setClip(ctx, "키보드가 꺼져 있을 때 복사")                      // 서비스가 없는 동안 복사
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        service.onCreateInputView()
        assertTrue(history(ctx).isEmpty())                               // 아직 기록 전
        startField(service, TEXT)                                        // 입력 시작 때 현재 클립보드도 기록
        assertEquals(listOf("키보드가 꺼져 있을 때 복사"), history(ctx))
    }

    @Test fun doesNotRecordInsidePasswordFields() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        com.mbt.keyboard.clipboard.ClipboardHistory(com.mbt.keyboard.clipboard.PrefsClipboardStore(ctx)).clear()
        Prefs.setClipboardEnabled(ctx, true)
        setClip(ctx, "암호처럼 보이는 값")
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        service.onCreateInputView()
        startField(service, PASSWORD)                                    // 비밀번호 칸이 시작될 때는 기록하지 않는다
        service.recordClipboard(PASSWORD)
        assertTrue(history(ctx).isEmpty())
        service.recordClipboard(TEXT)                                    // 같은 내용이라도 일반 칸에서는 기록
        assertEquals(listOf("암호처럼 보이는 값"), history(ctx))
    }

    // ---- 영어 첫 글자 자동 대문자

    private fun shiftOf(root: FrameLayout) =
        ((root.getChildAt(0) as FrameLayout).getChildAt(0) as KeyboardView).shiftState

    private fun englishService(): Pair<KeyboardService, FrameLayout> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, true)
        Prefs.setAutoCapitalize(ctx, true)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, TEXT)
        return service to root
    }

    @Test fun sentenceStartTurnsShiftOnInEnglishAndNotElsewhere() {
        val (service, root) = englishService()
        assertEquals("en", currentLayoutId(root))
        assertEquals(ShiftState.OFF, shiftOf(root))
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)    // 입력창: 문장 시작
        assertEquals(ShiftState.ON, shiftOf(root))
        service.applyAutoCaps(0)                                             // 문장 시작이 아니게 됨
        assertEquals(ShiftState.OFF, shiftOf(root))
    }

    @Test fun unknownCapsModeLeavesTheShiftAlone() {
        val (service, root) = englishService()
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)
        service.applyAutoCaps(null)
        assertEquals(ShiftState.ON, shiftOf(root))
    }

    @Test fun neverCapitalizesAutomaticallyInKoreanOrOnPads() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setLastLanguageEnglish(ctx, false)
        Prefs.setAutoCapitalize(ctx, true)
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        val root = service.onCreateInputView() as FrameLayout
        startField(service, TEXT)
        assertEquals("kos", currentLayoutId(root))
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)
        assertEquals(ShiftState.OFF, shiftOf(root))
        startField(service, NUMBER)
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)
        assertEquals(ShiftState.OFF, shiftOf(root))
    }

    @Test fun turnedOffInSettingsMeansNoAutoCapitalization() {
        val (service, root) = englishService()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setAutoCapitalize(ctx, false)
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)
        assertEquals(ShiftState.OFF, shiftOf(root))
        Prefs.setAutoCapitalize(ctx, true)                                   // 다시 켜면 동작
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)
        assertEquals(ShiftState.ON, shiftOf(root))
    }

    @Test fun turningTheSettingOffClearsAnAutoOnShift() {
        val (service, root) = englishService()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)
        assertEquals(ShiftState.ON, shiftOf(root))
        Prefs.setAutoCapitalize(ctx, false)
        service.applyAutoCaps(android.text.TextUtils.CAP_MODE_SENTENCES)    // 끈 뒤 다음 갱신에서 해제
        assertEquals(ShiftState.OFF, shiftOf(root))
    }

    // ---- 엔터 꾹 누르기 패널 (상용문구 | 최근기록)

    private fun quickPanelOf(root: FrameLayout) = (root.getChildAt(0) as FrameLayout).let { host ->
        (0 until host.childCount).map { host.getChildAt(it) }.filterIsInstance<QuickPanel>().single()
    }

    @Test fun enterLongPressOpensTheQuickPanelWithPhrasesAndRecentClips() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val service = freshService(ctx)
        Prefs.setQuickPanelTab(ctx, 0)
        com.mbt.keyboard.phrase.Phrases.saveRaw(ctx, "첫 상용구\n둘째 상용구")
        com.mbt.keyboard.clipboard.ClipboardHistory(com.mbt.keyboard.clipboard.PrefsClipboardStore(ctx)).add("복사해 둔 글자")
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val panel = quickPanelOf(root)
        assertEquals(View.GONE, panel.visibility)
        service.onEnterLongPress()
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(QuickPanel.TAB_PHRASES, panel.tab)
        panel.clipsTab.performClick()                                         // 최근기록 탭
        val shown = (panel.getChildAt(1) as android.widget.ScrollView).getChildAt(0) as android.widget.LinearLayout
        fun leaves(v: View): List<View> =
            if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { leaves(v.getChildAt(it)) } else listOf(v)
        val texts = leaves(shown).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), "복사해 둔 글자" in texts)
    }

    @Test fun rememberedTabIsUsedNextTime() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val service = freshService(ctx)
        val root = service.onCreateInputView() as FrameLayout
        layoutRoot(root, 1000)
        val panel = quickPanelOf(root)
        Prefs.setQuickPanelTab(ctx, 0)
        service.onEnterLongPress()
        panel.clipsTab.performClick()                                         // 최근기록으로 바꿈 → 기억
        assertEquals(1, Prefs.quickPanelTab(ctx))
        service.onStartInputView(android.view.inputmethod.EditorInfo(), false) // 패널 닫힘
        assertEquals(View.GONE, panel.visibility)
        service.onEnterLongPress()                                            // 다시 열면 최근기록 탭
        assertEquals(QuickPanel.TAB_CLIPS, panel.tab)
    }

    @Test fun enterLongPressIncludesTheCurrentClipboardContent() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        com.mbt.keyboard.clipboard.ClipboardHistory(com.mbt.keyboard.clipboard.PrefsClipboardStore(ctx)).clear()
        Prefs.setClipboardEnabled(ctx, true)
        setClip(ctx, "방금 복사한 글자")                                      // 서비스 시작 전에 복사
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        service.onCreateInputView()
        assertTrue(history(ctx).isEmpty())
        service.onEnterLongPress()                                            // 열 때 현재 클립보드도 기록
        assertEquals(listOf("방금 복사한 글자"), history(ctx))
    }

    @Test fun tapConversionIsOffForConvertibleKeysWhenSwipeDoubleIsOn() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        assertTrue(service.usesTapConversion('ㄱ'))                 // 기본(끔): 연타로 바꾼다
        Prefs.setSingleSwipeDouble(ctx, true)
        listOf('ㄱ', 'ㄷ', 'ㅂ', 'ㅅ', 'ㅈ', 'ㅏ', 'ㅓ', 'ㅗ', 'ㅜ', 'ㅐ', 'ㅔ').forEach { assertTrue("$it", !service.usesTapConversion(it)) }
        assertTrue(service.usesTapConversion('ㅎ'))                 // 변환 대상이 아닌 키는 해당 없음
    }
}
