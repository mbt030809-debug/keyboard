package com.mbt.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTest {

    private fun halves(l: KeyboardLayout, row: Int) = Split.halves(l.rows[row], l.splitAt.getOrNull(row))
    private fun Split.Halves.leftLabels() = left.map { it.key.label }
    private fun Split.Halves.rightLabels() = right.map { it.key.label }

    @Test fun everyKeyLandsOnExactlyOneSideAndWeightIsPreserved() {
        Layouts.all.forEach { l ->
            l.rows.forEachIndexed { r, row ->
                val h = halves(l, r)
                val total = row.sumOf { it.weight.toDouble() }
                val sum = (h.left + h.right).sumOf { it.weight.toDouble() }
                assertEquals("${l.id} row $r", total, sum, 0.001)
                // 스페이스바만 양쪽에 걸쳐 나뉠 수 있다
                val dup = h.left.map { it.key }.filter { k -> h.right.any { it.key === k } }
                assertTrue(dup.all { it.type == KeyType.SPACE })
            }
        }
    }

    @Test fun spaceBarIsSplitIntoTwoHalvesInBottomRow() {
        Layouts.all.forEach { l ->
            val h = halves(l, l.rows.lastIndex)
            assertEquals(1, h.left.count { it.key.type == KeyType.SPACE })
            assertEquals(1, h.right.count { it.key.type == KeyType.SPACE })
            // 쉬프트·이모지는 왼쪽, 엔터는 오른쪽
            assertTrue(h.left.any { it.key.type == KeyType.EMOJI })
            assertTrue(h.right.any { it.key.type == KeyType.ENTER })
        }
    }

    @Test fun englishFollowsHandsOnQwerty() {
        val en = Layouts.english
        assertEquals("qwert", halves(en, 2).leftLabels().joinToString(""))
        assertEquals("yuiop", halves(en, 2).rightLabels().joinToString(""))
        assertEquals("asdfg", halves(en, 3).leftLabels().filter { it.isNotEmpty() }.joinToString(""))
        assertEquals("hjkl", halves(en, 3).rightLabels().filter { it.isNotEmpty() }.joinToString(""))
        // b는 왼손, n은 오른손
        assertEquals(listOf("⇧", "z", "x", "c", "v", "b"), halves(en, 4).leftLabels())
        assertEquals(listOf("n", "m", "⌫"), halves(en, 4).rightLabels())
    }

    @Test fun dubeolsikFollowsHandsOnQwerty() {
        val ko = Layouts.korean
        assertEquals("ㅂㅈㄷㄱㅅ", halves(ko, 2).leftLabels().joinToString(""))
        assertEquals("ㅁㄴㅇㄹㅎ", halves(ko, 3).leftLabels().filter { it.isNotEmpty() }.joinToString(""))
        assertEquals(listOf("⇧", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ"), halves(ko, 4).leftLabels())
    }

    @Test fun singleVowelRowsSplitFourAndFour() {
        val s = Layouts.koreanSingle
        assertEquals("ㅂㅈㄷㄱ", halves(s, 2).leftLabels().joinToString(""))
        assertEquals("ㅅㅗㅐㅔ", halves(s, 2).rightLabels().joinToString(""))
        assertEquals("ㅁㄴㅇㄹ", halves(s, 3).leftLabels().joinToString(""))
        assertEquals("ㅎㅓㅏㅣ", halves(s, 3).rightLabels().joinToString(""))
    }

    @Test fun toolbarAndNumberRowsSplitFiveAndFive() {
        Layouts.all.forEach { l ->
            assertEquals(5, halves(l, 0).left.size)
            assertEquals(5, halves(l, 1).left.size)
        }
    }
}
