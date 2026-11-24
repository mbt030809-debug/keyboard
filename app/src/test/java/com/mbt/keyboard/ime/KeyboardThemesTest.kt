package com.mbt.keyboard.ime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardThemesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }
    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = lum(a).let { x -> lum(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + 0.05) / (lo + 0.05)
    }
    private fun hue(c: Int): Float {
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        if (d == 0f) return 0f
        val h = when (mx) { r -> ((g - b) / d) % 6; g -> (b - r) / d + 2; else -> (r - g) / d + 4 } * 60f
        return if (h < 0) h + 360 else h
    }

    @Test fun twentyEightThemesWithUniqueIdsAndNames() {
        assertEquals(32, KeyboardThemes.all.size)
        assertEquals(32, KeyboardThemes.all.map { it.id }.toSet().size)
        assertEquals(32, KeyboardThemes.all.map { it.name }.toSet().size)
        assertEquals(32, KeyboardThemes.all.map { it.theme.background }.toSet().size)
    }

    /** 반투명 [fg]를 불투명 [bg] 위에 올린 색 */
    private fun over(fg: Int, bg: Int): Int {
        val a = ((fg ushr 24) and 0xFF) / 255f
        fun ch(sh: Int) = (((fg shr sh) and 0xFF) * a + ((bg shr sh) and 0xFF) * (1 - a)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Test fun everyThemeIsReadable() {
        KeyboardThemes.all.forEach { n ->
            val t = n.theme
            // 그라데이션이면 시작·가운데·끝 어디서든 읽혀야 한다. 반투명 키는 그 배경 위에 올린 모습으로 본다
            t.backgroundStops.forEach { stop ->
                // 유리 테마는 글자가 판 번짐색이 아니라 키 면 위에 있으므로 키 면만 본다
                (if (t.frost) listOf(over(t.key, stop), over(t.specialKey, stop), over(t.keyPressed, stop))
                else listOf(stop, over(t.key, stop), over(t.specialKey, stop), over(t.keyPressed, stop))).forEach {
                    assertTrue("${n.id} 글자/바탕 ${contrast(t.text, it)}", contrast(t.text, it) >= 4.5)
                }
                if (!t.frost) assertTrue("${n.id} 강조/배경", contrast(t.accent, stop) >= 3.0)    // 유리 테마는 강조색이 엔터 키 면이라 onAccent 대비만 본다
            }
            assertTrue("${n.id} 강조 위 글자", contrast(t.onAccent, t.accent) >= 4.5)
            assertTrue("${n.id} 눌림이 키와 구분", t.keyPressed != t.key)
        }
    }

    @Test fun glassThemesHaveGradientsOrTranslucentKeys() {
        val glass = listOf("sunset", "pinkcloud", "aurora", "mintsoda", "buttercream", "film", "midnightglass", "monoline")
        glass.forEach { assertTrue(it, KeyboardThemes.byId(it) != null) }
        assertTrue(KeyboardThemes.byId("sunset")!!.theme.hasGradient)
        assertEquals(3, KeyboardThemes.byId("sunset")!!.theme.backgroundStops.size)
        assertTrue(((KeyboardThemes.byId("aurora")!!.theme.key ushr 24) and 0xFF) < 0xFF)     // 반투명 키
        assertTrue(!KeyboardThemes.byId("cream")!!.theme.hasGradient)                           // 기존 테마는 단색
        // 말풍선용 불투명 색은 알파가 255
        val t = KeyboardThemes.byId("aurora")!!.theme
        assertEquals(0xFF, (t.solid(t.key) ushr 24) and 0xFF)
    }

    @Test fun toneOnToneKeepsOneHuePerTheme() {
        // 채도가 있는 테마는 배경·키·기능키·눌림이 모두 같은 색상(hue) 계열
        KeyboardThemes.all.filter { it.id !in setOf("charcoal", "cloud", "oat", "neon") && !it.theme.hasGradient && ((it.theme.key ushr 24) and 0xFF) == 0xFF }.forEach { n ->
            val t = n.theme
            val hs = listOf(t.background, t.key, t.specialKey, t.keyPressed).map { hue(it) }
            assertTrue("${n.id} $hs", hs.max() - hs.min() < 12f)
        }
    }

    @Test fun lightThemesAreLightAndDarkThemesAreDark() {
        listOf("cream", "butter", "peach", "rose", "lavender", "periwinkle", "sky", "mint", "sage", "matcha", "oat", "cloud").forEach {
            assertTrue(it, lum(KeyboardThemes.byId(it)!!.theme.background) > 0.6)
        }
        listOf("charcoal", "navy", "ocean", "forest", "mocha", "wine", "plum", "neon").forEach {
            assertTrue(it, lum(KeyboardThemes.byId(it)!!.theme.background) < 0.05)
        }
    }

    @Test fun defaultFollowsTheSystemAndSavedThemeIsUsed() {
        assertEquals("system", Prefs.themeId(ctx))
        assertEquals(KeyboardTheme.system(ctx), KeyboardTheme.current(ctx))
        Prefs.setThemeId(ctx, "navy")
        assertEquals(KeyboardThemes.byId("navy")!!.theme, KeyboardTheme.current(ctx))
        Prefs.setThemeId(ctx, "no-such-theme")
        assertEquals(KeyboardTheme.system(ctx), KeyboardTheme.current(ctx))
        assertNull(KeyboardThemes.resolve("system"))
        assertNotNull(KeyboardThemes.resolve("mint"))
    }

    @Test fun panelsTakeTheChosenThemeColors() {
        Prefs.setThemeId(ctx, "rose")
        val bg = KeyboardThemes.byId("rose")!!.theme.background
        val p = EmojiPanel(ctx, object : EmojiPanel.Listener { override fun onInsert(text: String) {}; override fun onClose() {} })
        assertEquals(bg, (p.background as android.graphics.drawable.ColorDrawable).color)
    }
}
