package com.mbt.keyboard.emoji

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {
    private val emoticons = EmojiData.parseEmoticons(File("src/main/res/raw/emoticons.txt").readText())
    private val emoji = EmojiData.parseEmoji(File("src/main/res/raw/emoji.txt").readText())

    @Test fun parsesHeaderAndItems() {
        val c = EmojiData.parseEmoticons("## 행복해요 | (*´∪`)\n(*´∪`)\n(´▽`)\n\n## 기타 | (๑•. •๑)\nㅋ").let { it }
        assertEquals(listOf("행복해요", "기타"), c.map { it.name })
        assertEquals("(*´∪`)", c[0].sample)
        assertEquals(listOf("(*´∪`)", "(´▽`)"), c[0].items)
        assertEquals(listOf("ㅋ"), c[1].items)
    }

    @Test fun parsesEmojiLinesSeparatedBySpaces() {
        val c = EmojiData.parseEmoji("## 😀 표정\n😀 😃  😄\n😁")
        assertEquals("😀", c[0].sample)
        assertEquals("표정", c[0].name)
        assertEquals(listOf("😀", "😃", "😄", "😁"), c[0].items)
    }

    @Test fun emoticonCategoriesMatchReferenceList() {
        val names = emoticons.map { it.name }
        listOf(
            "행복해요", "화났어요", "최고에요", "걱정돼요", "생각하고 있어요/곤란해요", "신나요", "공부해요",
            "비정상적이에요", "부탁해요", "윙크해요", "인사해요", "더워요", "하지마세요", "충성!", "무서워요",
            "싸울래요", "운동해요", "미쳤어요", "혼란스러워요", "슬퍼요", "흘라흘라", "사랑에 빠졌어요", "구름",
            "곰돌이", "고양이", "강아지", "돼지", "토끼", "오리", "새", "해양 생물", "눈사람", "나이가 많아요",
            "블러리", "음악 들어요", "잠자요", "뛰어요/급해요", "숨어요", "마법", "먹는 중", "요리해요", "배고파요",
            "더듬이/날개/리본", "무기", "먼작귀", "복학생", "기타",
        ).forEach { assertTrue("missing $it", it in names) }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun everyCategoryHasEnoughNonBlankItems() {
        emoticons.forEach { c ->
            assertTrue("${c.name} sample", c.sample.isNotBlank())
            assertTrue("${c.name} items=${c.items.size}", c.items.size >= 6)
            assertTrue(c.items.none { it.isBlank() })
        }
        emoji.forEach { c -> assertTrue("${c.name}", c.items.size >= 20) }
        assertEquals(7, emoji.size)
    }

    @Test fun parsesShortLabelBeforeTheDoubleColon() {
        val c = EmojiData.parseEmoticons("## 행복 :: 행복해요 | (*´∪`)\n(*´∪`)\n## 숨기 :: 숨어요 | |_•)\n|_•)\n## 기타 | (๑•. •๑)\nㅋ")
        assertEquals(listOf("행복해요", "숨어요", "기타"), c.map { it.name })
        assertEquals(listOf("행복", "숨기", "기타"), c.map { it.label })        // 짧은 이름이 없으면 원래 이름
        assertEquals("|_•)", c[1].sample)                                       // 대표에 '|'가 있어도 안전
    }

    @Test fun everyEmoticonCategoryHasAShortUniqueLabel() {
        assertEquals(48, emoticons.size)
        assertEquals(emoticons.size, emoticons.map { it.label }.toSet().size)
        emoticons.forEach { assertTrue("${it.name} -> ${it.label}", it.label.isNotBlank() && it.label.length <= 5) }
    }

    @Test fun happyAndAngryStartWithTheRequestedEmoticonsInOrder() {
        val happy = emoticons.first { it.label == "행복" }
        assertEquals(listOf("(*´∪`)", "(´▽`)", "(*^▽^*)", "(๑˃ᴗ˂)ﻭ", "ヽ(´▽`)/"), happy.items.take(5))
        val angry = emoticons.first { it.label == "화남" }
        assertEquals(listOf("(`Д´)", "ヽ(`Д´)ﾉ", "(╬ ಠ益ಠ)", "(＃`Д´)"), angry.items.take(4))
    }
}
