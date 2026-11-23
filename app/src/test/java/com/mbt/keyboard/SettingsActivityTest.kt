package com.mbt.keyboard

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
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
}
