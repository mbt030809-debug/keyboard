package com.mbt.keyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickPanelTest {
    private val inserted = ArrayList<String>()
    private val deleted = ArrayList<String>()
    private val tabs = ArrayList<Int>()
    private var cleared = 0
    private var closed = 0
    private lateinit var panel: QuickPanel

    @Before fun setUp() {
        panel = QuickPanel(
            ApplicationProvider.getApplicationContext<Context>(),
            object : QuickPanel.Listener {
                override fun onInsert(text: String) { inserted += text }
                override fun onDeleteClip(text: String) { deleted += text }
                override fun onClearClips() { cleared++ }
                override fun onClose() { closed++ }
                override fun onTabChanged(tab: Int) { tabs += tab }
            },
        )
    }

    private fun texts(v: View): List<TextView> =
        if (v is TextView) listOf(v) else if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    private fun body() = (panel.getChildAt(1) as ScrollView).getChildAt(0) as LinearLayout
    private fun bodyTexts() = texts(body()).map { it.text.toString() }

    private val phrases = listOf("안녕하세요", "감사합니다", "네, 알겠습니다", "잠시만요")
    private val clips = listOf("최근 복사한 글자", "그 전에 복사한 글자")

    @Test fun hasTwoTabsPhrasesAndRecent() {
        assertEquals("상용문구", panel.phrasesTab.text.toString())
        assertEquals("최근기록", panel.clipsTab.text.toString())
    }

    @Test fun opensOnThePhrasesTabAsAThreeColumnGrid() {
        panel.open(phrases, clips, QuickPanel.TAB_PHRASES)
        assertEquals(QuickPanel.TAB_PHRASES, panel.tab)
        assertEquals(2, body().childCount)                                   // 4개 → 3열이라 2줄
        assertEquals(phrases, bodyTexts())
        assertTrue("최근기록 내용은 보이지 않는다", clips.none { it in bodyTexts() })
    }

    @Test fun switchingToTheRecentTabShowsClipsNewestFirstAndRemembersTheTab() {
        panel.open(phrases, clips, QuickPanel.TAB_PHRASES)
        panel.clipsTab.performClick()
        assertEquals(QuickPanel.TAB_CLIPS, panel.tab)
        assertEquals(listOf(QuickPanel.TAB_CLIPS), tabs)                    // 탭 변경이 알려진다 (기억용)
        val shown = bodyTexts()
        assertTrue(shown.indexOf("최근 복사한 글자") < shown.indexOf("그 전에 복사한 글자"))
        assertTrue(phrases.none { it in shown })
        panel.phrasesTab.performClick()
        assertEquals(listOf(QuickPanel.TAB_CLIPS, QuickPanel.TAB_PHRASES), tabs)
    }

    @Test fun canOpenDirectlyOnTheRecentTab() {
        panel.open(phrases, clips, QuickPanel.TAB_CLIPS)
        assertEquals(QuickPanel.TAB_CLIPS, panel.tab)
        assertTrue("최근 복사한 글자" in bodyTexts())
        assertTrue(tabs.isEmpty())                                           // 열 때는 변경 알림을 보내지 않는다
    }

    @Test fun tappingItemsOnEitherTabInsertsThem() {
        panel.open(phrases, clips, QuickPanel.TAB_PHRASES)
        texts(body()).first { it.text == "감사합니다" }.performClick()
        panel.clipsTab.performClick()
        texts(body()).first { it.text == "최근 복사한 글자" }.performClick()
        assertEquals(listOf("감사합니다", "최근 복사한 글자"), inserted)
    }

    @Test fun deleteButtonRemovesOnlyThatRecentItemAndClearAllWorks() {
        panel.open(phrases, clips, QuickPanel.TAB_CLIPS)
        val row = body().getChildAt(1) as LinearLayout                        // 0번은 "모두 지우기"
        texts(row).first { it.text == "✕" }.performClick()
        assertEquals(listOf("최근 복사한 글자"), deleted)
        texts(body()).first { it.text == "모두 지우기" }.performClick()
        assertEquals(1, cleared)
    }

    @Test fun setClipsRefreshesTheRecentListAfterADelete() {
        panel.open(phrases, clips, QuickPanel.TAB_CLIPS)
        panel.setClips(listOf("그 전에 복사한 글자"))
        assertTrue("최근 복사한 글자" !in bodyTexts())
        assertTrue("그 전에 복사한 글자" in bodyTexts())
    }

    @Test fun emptyStatesGuideTheUser() {
        panel.open(emptyList(), emptyList(), QuickPanel.TAB_PHRASES)
        assertTrue(bodyTexts().any { it.contains("저장된 상용구가 없어요") })
        panel.clipsTab.performClick()
        assertTrue(bodyTexts().any { it.contains("복사한 내용이 여기에 쌓여요") })
        assertTrue("모두 지우기" !in bodyTexts())
        assertTrue("줄바꿈이 글자 그대로 보이면 안 된다", bodyTexts().none { it.contains("\\n") })
    }

    @Test fun closeButtonCloses() {
        texts(panel).first { it.text == "✕" && it.parent === panel.getChildAt(0) }.performClick()
        assertEquals(1, closed)
    }
}
