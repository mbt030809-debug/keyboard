package com.mbt.keyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mbt.keyboard.emoji.EmojiData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiPanelTest {
    private val inserted = ArrayList<String>()
    private lateinit var panel: EmojiPanel
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val emoticons by lazy { EmojiData.emoticons(ctx) }

    @Before fun setUp() {
        panel = EmojiPanel(ctx, object : EmojiPanel.Listener {
            override fun onInsert(text: String) { inserted += text }
            override fun onClose() {}
        })
    }

    private fun texts(v: View): List<TextView> =
        if (v is TextView) listOf(v) else if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    private val indexBar get() = panel.getChildAt(1) as HorizontalScrollView
    private val body get() = (panel.getChildAt(2) as ScrollView).getChildAt(0) as LinearLayout

    @Test fun emoticonTabListsEveryCategoryAsOneBlockWithItsLabelFirst() {
        assertEquals(emoticons.size, body.childCount)
        emoticons.forEachIndexed { i, c ->
            val t = texts(body.getChildAt(i))
            assertEquals("분류 이름이 줄 맨 앞", c.label, t.first().text.toString())
            assertEquals("이모티콘이 이어서 나열", c.items, t.drop(1).map { it.text.toString() })
        }
    }

    @Test fun oneTapInsertsAnEmoticonDirectlyWithoutOpeningACategoryFirst() {
        val happy = texts(body.getChildAt(0)).first { it.text == "(´▽`)" }
        happy.performClick()
        assertEquals(listOf("(´▽`)"), inserted)                          // 한 번 눌러 바로 입력
        val angry = texts(body.getChildAt(1)).first { it.text == "(╬ ಠ益ಠ)" }
        angry.performClick()
        assertEquals(listOf("(´▽`)", "(╬ ಠ益ಠ)"), inserted)
    }

    @Test fun noBackButtonOrCategoryDrillDownAnymore() {
        assertTrue(texts(panel).none { it.text.startsWith("‹") })
        // 예전 "대표 + 긴 이름" 알약("(*´∪`) 행복해요")은 더 이상 없다
        assertTrue(texts(body).none { it.text == "(*´∪`) 행복해요" })
    }

    @Test fun indexChipsExistForEveryCategoryAndAreClickable() {
        val chips = texts(indexBar).map { it.text.toString() }
        assertEquals(emoticons.map { it.label }, chips)
        texts(indexBar).first { it.text == "화남" }.performClick()      // 스크롤 이동 (예외 없이 동작)
        assertEquals(View.VISIBLE, indexBar.visibility)
    }

    @Test fun emojiTabHidesTheIndexBarAndShowsItsOwnGrid() {
        val tabs = panel.getChildAt(0) as LinearLayout
        (tabs.getChildAt(1) as TextView).performClick()                  // 이모지 탭
        assertEquals(View.GONE, indexBar.visibility)
        assertTrue(texts(body).any { it.text == "😀" })
        (tabs.getChildAt(0) as TextView).performClick()                  // 이모티콘 탭으로 복귀
        assertEquals(View.VISIBLE, indexBar.visibility)
        assertEquals(emoticons.size, body.childCount)
    }
}
