package com.mbt.keyboard.settings

import android.content.Context
import com.mbt.keyboard.layout.Layouts

/** 사용자 설정 저장소 */
object Prefs {
    const val FILE = "mbt_keyboard"
    private const val KEY_KOREAN = "korean_layout"
    private const val KEY_HAPTIC_ON = "haptic_enabled"
    private const val KEY_HAPTIC_STRENGTH = "haptic_strength"

    private const val KEY_SPLIT = "split_on_large_screen"
    private const val KEY_LAST_ENGLISH = "last_language_english"
    private const val KEY_AUTO_CAPS = "auto_capitalize"
    private const val KEY_CLIPBOARD_ON = "clipboard_history_enabled"
    private const val KEY_QUICK_TAB = "quick_panel_tab"
    const val KEY_THEME = "keyboard_theme"
    private const val KEY_HAPTIC_STYLE = "haptic_style"
    const val KEY_NUMBER_LEFT = "number_keys_left"

    /** 숫자 키를 맨 위 줄 대신 왼쪽 세로 띠에 둔다 (단모음·두벌식·영어, 기본: 켬) */
    fun numberKeysLeft(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_NUMBER_LEFT, true)

    fun setNumberKeysLeft(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_NUMBER_LEFT, on).apply()
    const val KEY_SWIPE_DOUBLE = "single_swipe_double"

    /**
     * 단모음 개선판: 쌍자음·ㅑㅕㅛㅠㅒㅖ를 연타가 아니라 그 키를 밀어서(상하좌우) 입력 (기본: 끔).
     * 켜면 연타 변환은 쓰지 않는다.
     */
    fun singleSwipeDouble(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SWIPE_DOUBLE, false)

    fun setSingleSwipeDouble(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SWIPE_DOUBLE, on).apply()


    /** 키 누를 때 진동 느낌 id (기본: 예전 방식) */
    fun hapticStyle(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_HAPTIC_STYLE, null) ?: "default"

    fun setHapticStyle(context: Context, id: String) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_HAPTIC_STYLE, id).apply()
    const val KEY_SPLIT_DUP = "split_duplicate_keys"

    /** 영어 갈라진 자판에서 안쪽 가장자리에 반대편 키를 복제해 보일지 (기본: 끔) */
    fun splitDuplicateKeys(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SPLIT_DUP, false)

    fun setSplitDuplicateKeys(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPLIT_DUP, on).apply()

    /** 선택한 키보드 테마 id (기본: 시스템 모드 따라가기) */
    fun themeId(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME, null) ?: "system"

    fun setThemeId(context: Context, id: String) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_THEME, id).apply()

    /** 엔터 꾹 누르기 패널을 마지막에 보던 탭 (0=상용문구, 1=최근기록) */
    fun quickPanelTab(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_QUICK_TAB, 0).coerceIn(0, 1)

    fun setQuickPanelTab(context: Context, tab: Int) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_QUICK_TAB, tab.coerceIn(0, 1)).apply()

    /** 클립보드 기록 저장 (기본: 켬). 끄면 새로 기록하지 않는다 */
    fun clipboardEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_CLIPBOARD_ON, true)

    fun setClipboardEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_CLIPBOARD_ON, on).apply()

    /** 영어 문장 첫 글자 자동 대문자 (기본: 켬) */
    fun autoCapitalize(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CAPS, true)

    fun setAutoCapitalize(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_CAPS, on).apply()

    /** 마지막으로 직접 고른 언어가 영어인가. 키보드를 다시 띄울 때 이 언어로 시작한다 (기본: 한글) */
    fun lastLanguageEnglish(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_LAST_ENGLISH, false)

    fun setLastLanguageEnglish(context: Context, english: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_LAST_ENGLISH, english).apply()

    private fun geoKey(large: Boolean, field: String) = "geo_${if (large) "large" else "normal"}_$field"

    /**
     * 키보드 크기·위치. [large]가 true면 폴드를 펼친 넓은 화면용, false면 접힘(일반) 화면용.
     * "갈라진 정도"만 [landscape](가로 모드)일 때 따로 저장한다. 가로 값이 아직 없으면
     * 펼침 화면은 세로 값을, 접힘 화면은 0(나누지 않음)을 쓴다.
     */
    fun geometry(context: Context, large: Boolean, landscape: Boolean = false): KeyboardGeometry {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val portraitGap = p.getInt(geoKey(large, "g"), KeyboardGeometry.GAP_DEFAULT)
        val gap = when {
            !landscape -> portraitGap
            p.contains(geoKey(large, "land_g")) -> p.getInt(geoKey(large, "land_g"), portraitGap)
            large -> portraitGap
            else -> KeyboardGeometry.GAP_NORMAL_LANDSCAPE_DEFAULT
        }
        return KeyboardGeometry(
            p.getInt(geoKey(large, "w"), KeyboardGeometry.WIDTH_DEFAULT),
            p.getInt(geoKey(large, "h"), KeyboardGeometry.HEIGHT_DEFAULT),
            p.getInt(geoKey(large, "xd"), KeyboardGeometry.X_DEFAULT),
            p.getInt(geoKey(large, "r"), KeyboardGeometry.RAISE_DEFAULT),
            gap,
            p.getInt(geoKey(large, "pl"), KeyboardGeometry.PAD_DEFAULT),
            p.getInt(geoKey(large, "pt"), KeyboardGeometry.PAD_DEFAULT),
            p.getInt(geoKey(large, "pr"), KeyboardGeometry.PAD_DEFAULT),
            p.getInt(geoKey(large, "pb"), KeyboardGeometry.PAD_DEFAULT),
            p.getInt(geoKey(large, "kg"), KeyboardGeometry.KEY_GAP_DEFAULT),
            p.getInt(geoKey(large, "rg"), KeyboardGeometry.ROW_GAP_DEFAULT),
            p.getInt(geoKey(large, "nh"), KeyboardGeometry.NUM_HEIGHT_DEFAULT),
            p.getInt(geoKey(large, "nrg"), KeyboardGeometry.NUM_ROW_GAP_DEFAULT),
            p.getInt(geoKey(large, "h0"), KeyboardGeometry.HIT_DEFAULT),
            p.getInt(geoKey(large, "h1"), KeyboardGeometry.HIT_DEFAULT),
            p.getInt(geoKey(large, "h2"), KeyboardGeometry.HIT_DEFAULT),
            p.getInt(geoKey(large, "h3"), KeyboardGeometry.HIT_DEFAULT),
        ).clamped()
    }

    fun setGeometry(context: Context, large: Boolean, g: KeyboardGeometry, landscape: Boolean = false) {
        val c = g.clamped()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(geoKey(large, "w"), c.widthPct)
            .putInt(geoKey(large, "h"), c.heightPct)
            .putInt(geoKey(large, "xd"), c.xDp)
            .putInt(geoKey(large, "r"), c.raiseDp)
            .putInt(if (landscape) geoKey(large, "land_g") else geoKey(large, "g"), c.gapPct)
            .putInt(geoKey(large, "pl"), c.padLeftDp)
            .putInt(geoKey(large, "pt"), c.padTopDp)
            .putInt(geoKey(large, "pr"), c.padRightDp)
            .putInt(geoKey(large, "pb"), c.padBottomDp)
            .putInt(geoKey(large, "kg"), c.keyGapDp)
            .putInt(geoKey(large, "rg"), c.rowGapDp)
            .putInt(geoKey(large, "nh"), c.numHeightPct)
            .putInt(geoKey(large, "nrg"), c.numRowGapDp)
            .putInt(geoKey(large, "h0"), c.hit0Dp)
            .putInt(geoKey(large, "h1"), c.hit1Dp)
            .putInt(geoKey(large, "h2"), c.hit2Dp)
            .putInt(geoKey(large, "h3"), c.hit3Dp)
            .apply()
    }
    const val KEY_FONT_SCALE = "font_scale_pct"
    const val FONT_SCALE_MIN = 70
    const val FONT_SCALE_MAX = 150

    /** 키 글자 크기 배율 (%, 기본 100) */
    fun fontScalePercent(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_FONT_SCALE, 100).coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)

    fun setFontScalePercent(context: Context, pct: Int) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FONT_SCALE, pct.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)).apply()
    private const val KEY_DOUBLE_TAP_MS = "double_tap_ms"
    const val KEY_REPEAT_CPS = "repeat_cps"

    /** 지우기 키를 꾹 누를 때 초당 지워지는 글자 수 (기본 22) */
    fun repeatCps(context: Context): Int =
        Timing.snapRepeatCps(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_REPEAT_CPS, Timing.REPEAT_CPS_DEFAULT),
        )

    fun setRepeatCps(context: Context, cps: Int) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_REPEAT_CPS, Timing.snapRepeatCps(cps)).apply()
    private const val KEY_LONG_PRESS_MS = "long_press_ms"

    /** 단모음 연타 기준: 이 시간 안에 같은 키를 다시 치면 쌍자음·쌍모음 (기본 0.35초) */
    fun doubleTapMs(context: Context): Long =
        Timing.snapDoubleTap(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_DOUBLE_TAP_MS, Timing.DOUBLE_TAP_DEFAULT_MS),
        )

    fun setDoubleTapMs(context: Context, ms: Long) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DOUBLE_TAP_MS, Timing.snapDoubleTap(ms)).apply()

    /** 꾹 누르기 인식 시간 (기본 0.35초) */
    fun longPressMs(context: Context): Long =
        Timing.snapLongPress(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getLong(KEY_LONG_PRESS_MS, Timing.LONG_PRESS_DEFAULT_MS),
        )

    fun setLongPressMs(context: Context, ms: Long) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LONG_PRESS_MS, Timing.snapLongPress(ms)).apply()

    /** 폴드를 펼친 것처럼 큰 화면에서 자판 나눠쓰기 (기본: 켬) */
    fun splitOnLargeScreen(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SPLIT, true)

    fun setSplitOnLargeScreen(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPLIT, on).apply()

    const val HAPTIC_MIN = 1
    const val HAPTIC_MAX = 10
    const val HAPTIC_DEFAULT = 5

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 키 입력 진동 사용 여부 (기본: 켬) */
    fun hapticEnabled(context: Context) = prefs(context).getBoolean(KEY_HAPTIC_ON, true)

    fun setHapticEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_HAPTIC_ON, on).apply()

    /** 진동 세기 1(약) ~ 10(강) */
    fun hapticStrength(context: Context) =
        prefs(context).getInt(KEY_HAPTIC_STRENGTH, HAPTIC_DEFAULT).coerceIn(HAPTIC_MIN, HAPTIC_MAX)

    fun setHapticStrength(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_HAPTIC_STRENGTH, v.coerceIn(HAPTIC_MIN, HAPTIC_MAX)).apply()

    /** 한/영 전환 시 사용할 한글 자판 (기본: 단모음) */
    fun koreanLayout(context: Context): String {
        val id = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_KOREAN, Layouts.KOS)
        return when (id) { Layouts.KO -> Layouts.KO; Layouts.MOA -> Layouts.MOA; else -> Layouts.KOS }
    }

    fun setKoreanLayout(context: Context, id: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_KOREAN, id).apply()
    }
}
