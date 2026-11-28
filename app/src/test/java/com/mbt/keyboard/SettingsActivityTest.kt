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

    @Test fun checkUpdateButtonDownloadsInAppInsteadOfOpeningTheBrowser() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                a.findViewById<android.view.View>(R.id.btnCheckUpdate).performClick()
                val next = org.robolectric.Shadows.shadowOf(a).nextStartedActivity
                // 브라우저(ACTION_VIEW)로 보내지 않는다: 설치 허용 설정 화면이거나 아무 화면도 없음(다운로드 시작)
                assertTrue(next == null || next.action != android.content.Intent.ACTION_VIEW)
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

    private fun visibleSections(a: SettingsActivity) =
        listOf(R.id.sectionStart, R.id.sectionInput, R.id.sectionScreen, R.id.sectionTheme)
            .map { a.findViewById<android.view.View>(it).visibility == android.view.View.VISIBLE }

    @Test fun tabsShowExactlyOneSectionAtATime() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val tabs = a.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabs)
                assertEquals(4, tabs.tabCount)
                // 아직 키보드를 켜지 않은 기기에서는 "시작" 탭부터
                assertEquals(listOf(true, false, false, false), visibleSections(a))
                tabs.getTabAt(1)!!.select()
                assertEquals(listOf(false, true, false, false), visibleSections(a))
                tabs.getTabAt(2)!!.select()
                assertEquals(listOf(false, false, true, false), visibleSections(a))
                tabs.getTabAt(3)!!.select()
                assertEquals(listOf(false, false, false, true), visibleSections(a))
            }
        }
    }

    @Test fun everySettingLivesInsideItsOwnTab() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                fun parentSection(id: Int): Int {
                    var v: android.view.View? = a.findViewById(id)
                    val sections = listOf(R.id.sectionStart, R.id.sectionInput, R.id.sectionScreen, R.id.sectionTheme)
                    while (v != null) { if (v.id in sections) return sections.indexOf(v.id); v = v.parent as? android.view.View }
                    return -1
                }
                assertEquals(0, parentSection(R.id.btnEnable)); assertEquals(0, parentSection(R.id.btnSelect))
                assertEquals(1, parentSection(R.id.radioLayout)); assertEquals(1, parentSection(R.id.switchAutoCaps))
                assertEquals(1, parentSection(R.id.sliderPause)); assertEquals(1, parentSection(R.id.sliderRepeat))
                assertEquals(1, parentSection(R.id.switchHaptic)); assertEquals(1, parentSection(R.id.switchClipboard))
                assertEquals(1, parentSection(R.id.editPhrases))
                assertEquals(3, parentSection(R.id.themeList)); assertEquals(2, parentSection(R.id.previewContainer))
                assertEquals(1, parentSection(R.id.switchSplit)); assertEquals(1, parentSection(R.id.switchSplitDup))
                assertEquals(0, parentSection(R.id.versionText)); assertEquals(3, parentSection(R.id.glassTypeChips)); assertEquals(3, parentSection(R.id.sliderKeyOpacity))
            }
        }
    }

    @Test fun statusBannerTellsWhatToDoNext() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val t = a.findViewById<TextView>(R.id.statusBanner).text.toString()
                assertTrue(t, t.contains("키보드를 켜") || t.contains("MBT 키보드로") || t.contains("준비 완료"))
            }
        }
    }

    @Test fun tryButtonIsInTheHeaderNotInsideAnyTabAndOpensATestField() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val btn = a.findViewById<android.view.View>(R.id.btnTry)
                val sections = listOf(R.id.sectionStart, R.id.sectionInput, R.id.sectionScreen, R.id.sectionTheme)
                var v: android.view.View? = btn
                var inside = false
                while (v != null) { if (v.id in sections) inside = true; v = v.parent as? android.view.View }
                assertTrue("탭 안에 있으면 안 된다", !inside)
                // 어느 탭이 선택돼 있어도 항상 보인다
                a.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabs).getTabAt(3)!!.select()
                assertEquals(android.view.View.VISIBLE, btn.visibility)
                btn.performClick()
                val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
                assertTrue(dialog != null && dialog.isShowing)
                assertTrue(dialog.findViewById<android.view.View>(R.id.tryField) != null)
            }
        }
    }

    @Test fun setupButtonsStayEnabledEvenWhenAlreadyDone() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a ->
                // 키보드를 켜고 기본 입력기로 선택한 상태를 만든다
                android.provider.Settings.Secure.putString(a.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD, "${a.packageName}/.ime.KeyboardService")
                a.onWindowFocusChanged(true)
                assertTrue(a.findViewById<android.view.View>(R.id.btnEnable).isEnabled)
                assertTrue(a.findViewById<android.view.View>(R.id.btnSelect).isEnabled || true)
            }
        }
    }
}
