package com.mbt.keyboard.ime

/**
 * 지운 글자 기록 (되돌리기용). 안드로이드 비의존이라 단위 테스트가 가능하다.
 *
 * 쓰는 법: 지우기를 시작할 때 [begin]에 "커서 앞 글자(+선택된 글자)"를 넘기고, 지우기 묶음이 끝나면
 * [finish]에 그때의 "커서 앞 글자"를 넘긴다. 둘의 차이가 지운 글자로 기록된다.
 * 꾹 눌러 연속으로 지운 것은 하나의 묶음이다. 기록은 최대 [maxChunks]묶음.
 */
class DeletionHistory(private val maxChunks: Int = MAX_CHUNKS) {

    /** [expectTail]: 지금 커서 앞에 남아 있어야 하는 끝부분(조합 중이던 글자 등. 보통 비어 있음), [restore]: 되살릴 글자 */
    class Chunk(val expectTail: String, val restore: String) {
        val removeLen get() = expectTail.length
    }

    private var snapshot: String? = null
    private val stack = ArrayDeque<Chunk>()

    val hasRun get() = snapshot != null
    val size get() = stack.size

    fun begin(textBeforeCursorPlusSelection: String) {
        snapshot = textBeforeCursorPlusSelection
    }

    /** 지우기 묶음을 끝낸다. 기록이 생겼으면 true */
    fun finish(textBeforeCursor: String): Boolean {
        val s = snapshot ?: return false
        snapshot = null
        val chunk = diff(s, textBeforeCursor) ?: return false
        stack.addLast(chunk)
        while (stack.size > maxChunks) stack.removeFirst()
        return true
    }

    /** 가장 최근 묶음을 꺼낸다 */
    fun pop(): Chunk? = stack.removeLastOrNull()

    fun clear() {
        stack.clear()
        snapshot = null
    }

    companion object {
        const val MAX_CHUNKS = 20

        /** 커서 앞 글자를 읽는 최대 길이. 이보다 긴 글 속에서도 되돌릴 수 있게 정렬해서 비교한다 */
        const val WINDOW = 1000

        internal fun diff(before: String, after: String): Chunk? {
            if (before.length < WINDOW) {
                // 앞부분이 같은 만큼은 그대로, 나머지가 바뀐 부분 (조합 중이던 글자가 남아 있으면 그것도 교체 대상)
                var cp = 0
                val n = minOf(before.length, after.length)
                while (cp < n && before[cp] == after[cp]) cp++
                val restore = before.substring(cp)
                if (restore.isEmpty()) return null
                return Chunk(after.substring(cp), restore)
            }
            // 읽은 범위가 잘렸을 때(아주 긴 글): 지운 만큼 창이 밀리므로 끝을 맞춰 비교
            for (k in 1 until before.length) {
                val keep = before.length - k
                if (after.length >= keep && after.takeLast(keep) == before.substring(0, keep)) {
                    return Chunk("", before.substring(keep))
                }
            }
            return null
        }
    }
}
