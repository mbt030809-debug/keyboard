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

    @Test fun tenThemesWithUniqueIdsAndNames() {
        assertEquals(10, KeyboardThemes.all.size)
        assertEquals(10, KeyboardThemes.all.map { it.id }.toSet().size)
        assertEquals(10, KeyboardThemes.all.map { it.name }.toSet().size)
        assertEquals(10, KeyboardThemes.all.map { it.theme.background }.toSet().size)
    }

    @Test fun everyThemeIsReadable() {
        KeyboardThemes.all.forEach { n ->
            val t = n.theme
            listOf(t.background, t.key, t.specialKey, t.keyPressed).forEach {
                assertTrue("${n.id} 글자/바탕 ${contrast(t.text, it)}", contrast(t.text, it) >= 4.5)
            }
            assertTrue("${n.id} 강조/배경", contrast(t.accent, t.background) >= 3.0)
            assertTrue("${n.id} 강조 위 글자", contrast(t.onAccent, t.accent) >= 4.5)
            assertTrue("${n.id} 눌림이 키와 구분", t.keyPressed != t.key)
        }
    }

    @Test fun toneOnToneKeepsOneHuePerTheme() {
        // 채도가 있는 테마는 배경·키·기능키·눌림이 모두 같은 색상(hue) 계열
        KeyboardThemes.all.filter { it.id != "charcoal" }.forEach { n ->
            val t = n.theme
            val hs = listOf(t.background, t.key, t.specialKey, t.keyPressed).map { hue(it) }
            assertTrue("${n.id} $hs", hs.max() - hs.min() < 12f)
        }
    }

    @Test fun lightThemesAreLightAndDarkThemesAreDark() {
        listOf("cream", "lavender", "mint", "sky", "rose", "sage").forEach {
            assertTrue(lum(KeyboardThemes.byId(it)!!.theme.background) > 0.6)
        }
        listOf("charcoal", "navy", "mocha", "forest").forEach {
            assertTrue(lum(KeyboardThemes.byId(it)!!.theme.background) < 0.05)
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
