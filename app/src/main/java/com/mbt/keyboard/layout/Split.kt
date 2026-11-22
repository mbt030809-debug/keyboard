package com.mbt.keyboard.layout

/**
 * 갈라진(나눠쓰기) 자판: 한 줄의 키를 왼손/오른손 두 묶음으로 나눈다.
 * 폴드를 펼쳤을 때처럼 화면이 넓을 때 양손 엄지로 치기 편하도록 가운데를 비운다.
 */
object Split {

    /** 키와, 그 줄에서 차지하는 가로 비중 (스페이스바처럼 가운데를 걸친 키는 반으로 쪼개져 비중이 줄어든다) */
    data class Part(val key: Key, val weight: Float)

    data class Halves(val left: List<Part>, val right: List<Part>)

    private const val EPS = 0.001f

    /**
     * @param splitAt 왼쪽 묶음에 넣을 키 개수. null이면 가운데(가로 비중의 절반)를 기준으로 자동 분할.
     *   자동 분할은 중심이 가운데 이하인 키를 왼쪽에 넣고, 스페이스바가 가운데를 걸치면 양쪽에 반씩 나눠 준다.
     */
    fun halves(row: List<Key>, splitAt: Int? = null): Halves {
        if (splitAt != null) {
            return Halves(
                row.take(splitAt).map { Part(it, it.weight) },
                row.drop(splitAt).map { Part(it, it.weight) },
            )
        }
        val total = row.sumOf { it.weight.toDouble() }.toFloat()
        val mid = total / 2f
        val left = ArrayList<Part>()
        val right = ArrayList<Part>()
        var acc = 0f
        for (k in row) {
            val start = acc
            val end = acc + k.weight
            acc = end
            val center = (start + end) / 2f
            when {
                k.type == KeyType.SPACE && start < mid - EPS && end > mid + EPS -> {
                    left += Part(k, mid - start)
                    right += Part(k, end - mid)
                }
                center <= mid + EPS -> left += Part(k, k.weight)
                else -> right += Part(k, k.weight)
            }
        }
        return Halves(left, right)
    }
}
