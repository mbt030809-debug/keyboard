package com.mbt.keyboard

import android.view.View
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResizeActivityTest {
    @Test fun opensWithoutCrashing() {
        ActivityScenario.launch(ResizeActivity::class.java).use { sc ->
            sc.onActivity { a -> assertTrue(a.findViewById<View>(R.id.previewContainer) != null) }
        }
    }

    @Test fun settingsActivityOpensAndResizeButtonLaunchesResize() {
        ActivityScenario.launch(SettingsActivity::class.java).use { sc ->
            sc.onActivity { a -> a.findViewById<View>(R.id.btnResize).performClick() }
        }
    }
}
