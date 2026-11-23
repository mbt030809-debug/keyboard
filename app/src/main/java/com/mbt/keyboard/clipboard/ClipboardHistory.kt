package com.mbt.keyboard.clipboard

import android.content.Context
import com.mbt.keyboard.settings.Prefs
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 클립보드 기록 (복사한 글자 목록). 최신이 맨 앞, 같은 내용은 하나로 합치고, 최대 [max]개.
 * 안드로이드 비의존(저장소는 [Store]로 주입)이라 단위 테스트가 가능하다.
 */
class ClipboardHistory(
    private val store: Store,
    private val max: Int = MAX_ITEMS,
    private val maxChars: Int = MAX_CHARS,
) {
    interface Store {
        fun load(): String
        fun save(raw: String)
    }

    private var cache: MutableList<String>? = null

    private fun list(): MutableList<String> = cache ?: decode(store.load()).toMutableList().also { cache = it }

    fun items(): List<String> = list().toList()

    /**
     * 기록에 추가한다.
     * @return 실제로 저장했으면 true (비어 있거나 너무 길면 저장하지 않는다)
     */
    fun add(text: String?): Boolean {
        if (text.isNullOrBlank() || text.length > maxChars) return false
        val l = list()
        l.remove(text)                // 같은 내용은 맨 앞으로 끌어올린다
        l.add(0, text)
        while (l.size > max) l.removeAt(l.lastIndex)
        persist()
        return true
    }

    fun remove(text: String) {
        if (list().remove(text)) persist()
    }

    fun clear() {
        list().clear()
        persist()
    }

    private fun persist() = store.save(encode(list()))

    companion object {
        const val MAX_ITEMS = 30
        const val MAX_CHARS = 2000

        /** 항목마다 URL 인코딩해 줄바꿈으로 이어 붙인다 (어떤 글자가 들어 있어도 안전) */
        fun encode(items: List<String>): String = items.joinToString("\n") { URLEncoder.encode(it, "UTF-8") }

        fun decode(raw: String): List<String> =
            raw.lineSequence().filter { it.isNotEmpty() }.map { URLDecoder.decode(it, "UTF-8") }.toList()
    }
}

/** [Prefs]와 같은 저장 파일에 기록을 보관한다 (앱 전용 저장소, 백업 꺼짐) */
class PrefsClipboardStore(
    private val context: Context,
    private val key: String = "clipboard_history",
) : ClipboardHistory.Store {
    override fun load(): String =
        context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).getString(key, "").orEmpty()

    override fun save(raw: String) {
        context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().putString(key, raw).apply()
    }
}
