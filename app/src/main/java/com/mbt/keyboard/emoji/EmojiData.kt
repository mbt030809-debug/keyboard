package com.mbt.keyboard.emoji

import android.content.Context
import com.mbt.keyboard.R

/** 카테고리 하나. [label]은 알약/탭에 표시되는 글자, [sample]은 대표 이모티콘(또는 이모지) */
data class Category(val name: String, val sample: String, val items: List<String>)

/**
 * 이모티콘(특수문자 얼굴)과 이모지 데이터. `res/raw`의 텍스트 파일에서 읽는다.
 *
 * emoticons.txt:  `## 카테고리명 | 대표 이모티콘` 다음 줄부터 한 줄에 하나씩
 * emoji.txt:      `## 대표이모지 카테고리명` 다음 줄에 공백으로 구분된 이모지
 */
object EmojiData {

    fun emoticons(context: Context): List<Category> =
        parseEmoticons(context.resources.openRawResource(R.raw.emoticons).bufferedReader().readText())

    fun emoji(context: Context): List<Category> =
        parseEmoji(context.resources.openRawResource(R.raw.emoji).bufferedReader().readText())

    fun parseEmoticons(text: String): List<Category> {
        val out = ArrayList<Category>()
        var name: String? = null
        var sample = ""
        var items = ArrayList<String>()
        fun flush() { name?.let { out += Category(it, sample, items) } }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("## ")) {
                flush()
                val header = line.removePrefix("## ")
                val bar = header.indexOf('|')
                name = (if (bar >= 0) header.substring(0, bar) else header).trim()
                sample = if (bar >= 0) header.substring(bar + 1).trim() else ""
                items = ArrayList()
            } else if (line.isNotEmpty() && name != null) {
                items += line
            }
        }
        flush()
        return out
    }

    fun parseEmoji(text: String): List<Category> {
        val out = ArrayList<Category>()
        var name: String? = null
        var icon = ""
        var items = ArrayList<String>()
        fun flush() { name?.let { out += Category(it, icon, items) } }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("## ")) {
                flush()
                val header = line.removePrefix("## ")
                val sp = header.indexOf(' ')
                icon = if (sp > 0) header.substring(0, sp) else header
                name = if (sp > 0) header.substring(sp + 1).trim() else header
                items = ArrayList()
            } else if (line.isNotEmpty() && name != null) {
                items += line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            }
        }
        flush()
        return out
    }
}
