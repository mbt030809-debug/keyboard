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

    /** 가로 모드 키: 세로 키 이름 앞에 land_ 를 붙인다 (예전 "갈라진 정도"의 land_g와 같은 방식) */
    private fun geoKey(large: Boolean, landscape: Boolean, field: String) = geoKey(large, if (landscape) "land_$field" else field)

    /**
     * 키보드 크기·여백·간격·숫자줄·전복붙 설정. [large]가 true면 폴드를 펼친 넓은 화면용, false면 접힘(일반) 화면용이고,
     * [landscape]는 가로 모드다. 이 네 가지(펼침/접힘 × 세로/가로)가 값마다 각각 따로 저장된다.
     * 가로 값이 아직 없으면 같은 화면의 세로 값을 이어받는다. "갈라진 정도"는 접힘 가로에서만 기본 0(나누지 않음)이다.
     */
    fun geometry(context: Context, large: Boolean, landscape: Boolean = false): KeyboardGeometry {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        fun int(field: String, default: Int): Int {
            val portrait = p.getInt(geoKey(large, field), default)
            return if (landscape && p.contains(geoKey(large, true, field))) p.getInt(geoKey(large, true, field), portrait) else portrait
        }
        val portraitGap = p.getInt(geoKey(large, "g"), KeyboardGeometry.GAP_DEFAULT)
        val gap = when {
            !landscape -> portraitGap
            p.contains(geoKey(large, true, "g")) -> p.getInt(geoKey(large, true, "g"), portraitGap)
            large -> portraitGap
            else -> KeyboardGeometry.GAP_NORMAL_LANDSCAPE_DEFAULT
        }
        return KeyboardGeometry(
            int("w", KeyboardGeometry.WIDTH_DEFAULT),
            int("h", KeyboardGeometry.HEIGHT_DEFAULT),
            int("xd", KeyboardGeometry.X_DEFAULT),
            int("r", KeyboardGeometry.RAISE_DEFAULT),
            gap,
            int("pl", KeyboardGeometry.PAD_DEFAULT),
            int("pt", KeyboardGeometry.PAD_DEFAULT),
            int("pr", KeyboardGeometry.PAD_DEFAULT),
            int("pb", KeyboardGeometry.PAD_DEFAULT),
            int("kg", KeyboardGeometry.KEY_GAP_DEFAULT),
            int("rg", KeyboardGeometry.ROW_GAP_DEFAULT),
            int("nh", KeyboardGeometry.NUM_HEIGHT_DEFAULT),
            int("nrg", KeyboardGeometry.NUM_ROW_GAP_DEFAULT),
            int("h0", KeyboardGeometry.HIT_DEFAULT),
            int("h1", KeyboardGeometry.HIT_DEFAULT),
            int("h2", KeyboardGeometry.HIT_DEFAULT),
            int("h3", KeyboardGeometry.HIT_DEFAULT),
            int("ft", KeyboardGeometry.FN_TAB_HEIGHT_DEFAULT),
            int("fp", KeyboardGeometry.FN_TAB_PAD_DEFAULT),
            int("sr", KeyboardGeometry.SPACE_ROW_DEFAULT),
            int("ftx", 0),
            int("fty", 0),
            int("ftw", KeyboardGeometry.FN_TAB_WIDTH_DEFAULT),
        ).clamped()
    }

    fun setGeometry(context: Context, large: Boolean, g: KeyboardGeometry, landscape: Boolean = false) {
        val c = g.clamped()
        fun k(field: String) = geoKey(large, landscape, field)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(k("w"), c.widthPct)
            .putInt(k("h"), c.heightPct)
            .putInt(k("xd"), c.xDp)
            .putInt(k("r"), c.raiseDp)
            .putInt(k("g"), c.gapPct)
            .putInt(k("pl"), c.padLeftDp)
            .putInt(k("pt"), c.padTopDp)
            .putInt(k("pr"), c.padRightDp)
            .putInt(k("pb"), c.padBottomDp)
            .putInt(k("kg"), c.keyGapDp)
            .putInt(k("rg"), c.rowGapDp)
            .putInt(k("nh"), c.numHeightPct)
            .putInt(k("nrg"), c.numRowGapDp)
            .putInt(k("h0"), c.hit0Dp)
            .putInt(k("h1"), c.hit1Dp)
            .putInt(k("h2"), c.hit2Dp)
            .putInt(k("h3"), c.hit3Dp)
            .putInt(k("ft"), c.fnTabHeightPct)
            .putInt(k("fp"), c.fnTabPadDp)
            .putInt(k("sr"), c.spaceRowPct)
            .putInt(k("ftx"), c.fnTabXdp)
            .putInt(k("fty"), c.fnTabYdp)
            .putInt(k("ftw"), c.fnTabWidthPct)
            .apply()
    }

    const val KEY_FN_TAB = "fn_tab"
    const val KEY_FN_TAB_ALPHA = "fn_tab_alpha_pct"
    const val FN_TAB_ALPHA_MIN = 20
    const val FN_TAB_ALPHA_MAX = 90

    /** 숫자 왼쪽 자판에서 전체·복사·붙임을 키보드 위 반투명 탭으로 띄울지 (기본 켬) */
    fun fnTabEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_FN_TAB, true)

    fun setFnTabEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_FN_TAB, on).apply()

    /** 탭의 불투명도 % (기본 50, 낮을수록 뒤가 많이 비침) */
    fun fnTabAlphaPercent(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_FN_TAB_ALPHA, 50).coerceIn(FN_TAB_ALPHA_MIN, FN_TAB_ALPHA_MAX)

    fun setFnTabAlphaPercent(context: Context, pct: Int) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FN_TAB_ALPHA, pct.coerceIn(FN_TAB_ALPHA_MIN, FN_TAB_ALPHA_MAX)).apply()

    const val KEY_KEY_OPACITY = "key_opacity_pct"
    const val KEY_OPACITY_MIN = 20
    const val KEY_OPACITY_MAX = 100
    const val KEY_OPACITY_DEFAULT = 100

    /** 모든 테마 키(전체·복사·붙임 포함)의 불투명도 % (기본 100, 낮을수록 뒤가 비침) */
    fun keyOpacityPercent(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_KEY_OPACITY, KEY_OPACITY_DEFAULT)
            .coerceIn(KEY_OPACITY_MIN, KEY_OPACITY_MAX)

    fun setKeyOpacityPercent(context: Context, pct: Int) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_KEY_OPACITY, pct.coerceIn(KEY_OPACITY_MIN, KEY_OPACITY_MAX)).apply()
    
    const val KEY_BG_OPACITY = "bg_opacity_pct"
    const val BG_OPACITY_MIN = 0
    const val BG_OPACITY_MAX = 100
    const val BG_OPACITY_DEFAULT = 100

    fun bgOpacityPercent(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_BG_OPACITY, BG_OPACITY_DEFAULT)
            .coerceIn(BG_OPACITY_MIN, BG_OPACITY_MAX)

    fun setBgOpacityPercent(context: Context, pct: Int) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_BG_OPACITY, pct.coerceIn(BG_OPACITY_MIN, BG_OPACITY_MAX)).apply()

            
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

    /** 모든 설정과 상용문구를 JSON 문자열로 내보낸다 */
    fun exportJson(context: Context): String {
        val root = org.json.JSONObject()
        val pObj = org.json.JSONObject()
        for ((k, v) in prefs(context).all) {
            when (v) {
                is Boolean, is Int, is Long, is Float, is Double, is String -> pObj.put(k, v)
            }
        }
        root.put("prefs", pObj)
        root.put("phrases", com.mbt.keyboard.phrase.Phrases.loadRaw(context))
        return root.toString(2)
    }

    /** JSON 문자열에서 모든 설정과 상용문구를 불러와 덮어쓴다 */
    fun importJson(context: Context, json: String): Boolean = try {
        val root = org.json.JSONObject(json.trim())
        val pObj = if (root.has("prefs")) root.getJSONObject("prefs") else root
        val ed = prefs(context).edit()
        val keys = pObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            when (val v = pObj.get(k)) {
                is Boolean -> ed.putBoolean(k, v)
                is Long -> ed.putLong(k, v)
                is Int -> if (k == KEY_DOUBLE_TAP_MS || k == KEY_LONG_PRESS_MS) ed.putLong(k, v.toLong()) else ed.putInt(k, v)
                is Number -> if (k == KEY_DOUBLE_TAP_MS || k == KEY_LONG_PRESS_MS) ed.putLong(k, v.toLong()) else ed.putInt(k, v.toInt())
                is String -> ed.putString(k, v)
            }
        }
        ed.apply()
        if (root.has("phrases")) {
            com.mbt.keyboard.phrase.Phrases.saveRaw(context, root.getString("phrases"))
        }
        true
    } catch (_: Exception) {
        false
    }
const val GLASS_NORMAL = 0
const val GLASS_BLUR = 1
const val GLASS_REED = 2
const val GLASS_3D = 3

const val KEY_GLASS_TYPE = "glass_type"
const val KEY_KEY_GLASS_STRENGTH = "key_glass_strength_pct"
const val KEY_BG_GLASS_STRENGTH = "bg_glass_strength_pct"
    
fun glassType(context: Context): Int =
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_GLASS_TYPE, GLASS_BLUR)
    .coerceIn(GLASS_NORMAL, GLASS_3D)

    fun setGlassType(context: Context, type: Int) =
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
    .putInt(KEY_GLASS_TYPE, type.coerceIn(GLASS_NORMAL, GLASS_3D)).apply()
    
fun keyGlassStrengthPercent(context: Context): Int =
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_KEY_GLASS_STRENGTH, 65)
    .coerceIn(0, 100)
    
fun setKeyGlassStrengthPercent(context: Context, pct: Int) =
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
    .putInt(KEY_KEY_GLASS_STRENGTH, pct.coerceIn(0, 100)).apply()
    
fun bgGlassStrengthPercent(context: Context): Int =
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_BG_GLASS_STRENGTH, 65)
    .coerceIn(0, 100)
    
fun setBgGlassStrengthPercent(context: Context, pct: Int) =
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
    .putInt(KEY_BG_GLASS_STRENGTH, pct.coerceIn(0, 100)).apply()

}
