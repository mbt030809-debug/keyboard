package com.mbt.keyboard

import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.mbt.keyboard.clipboard.ClipboardHistory
import com.mbt.keyboard.clipboard.PrefsClipboardStore
import com.mbt.keyboard.settings.Prefs
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityTest {
    @Test fun showsTheInstalledBuildVersion() {
        val expected = java.io.File("version.properties").readLines()
            .first { it.startsWith("build=") }.removePrefix("build=").trim()
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val text = a.findViewById<TextView>(R.id.versionText).text.toString()
                assertTrue(text, text.startsWith("버전 0.1.$expected") && text.contains("빌드 $expected"))
            }
        }
    }

    @Test fun autoCapitalizeSwitchIsOnByDefaultAndSavesTheChoice() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val sw = a.findViewById<MaterialSwitch>(R.id.switchAutoCaps)
                assertEquals(true, sw.isChecked)                    // 기본: 켬
                assertEquals(true, Prefs.autoCapitalize(a))
                sw.isChecked = false                                // 끄기
                assertEquals(false, Prefs.autoCapitalize(a))
                sw.isChecked = true                                 // 다시 켜기
                assertEquals(true, Prefs.autoCapitalize(a))
            }
        }
    }

    @Test fun clipboardSwitchIsOnByDefaultAndClearButtonEmptiesTheHistory() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val sw = a.findViewById<MaterialSwitch>(R.id.switchClipboard)
                assertEquals(true, sw.isChecked)
                sw.isChecked = false
                assertEquals(false, Prefs.clipboardEnabled(a))
                sw.isChecked = true
                assertEquals(true, Prefs.clipboardEnabled(a))

                val h = ClipboardHistory(PrefsClipboardStore(a))
                h.add("지워질 기록")
                assertEquals(1, ClipboardHistory(PrefsClipboardStore(a)).items().size)
                a.findViewById<android.view.View>(R.id.btnClearClipboard).performClick()
                assertEquals(0, ClipboardHistory(PrefsClipboardStore(a)).items().size)
            }
        }
    }
}
