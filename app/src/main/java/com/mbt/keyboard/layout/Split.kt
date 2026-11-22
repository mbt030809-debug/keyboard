package com.mbt.keyboard.layout

/**
 * 갈라진(나눠쓰기) 자판: 한 줄의 키를 왼손/오른손 두 묶음으로 나눈다.
 * 폴드를 펼쳤을 때처럼 화면이 넓을 때 양손 엄지로 치기 편하도록 가운데를 비운다.
 */
object Split {

    /** 키와, 그 줄에서 차지하는 가로 비중 (스페이스바처럼 가운데를 걸친 키는 반으로 쪼개져 비중이 줄어든다) */
    data class Part(val key: Key, val weight: Float)

    data class Halves(val left: List<Part>, val right: List<Part>)

    /** 한 키의 가로 위치 [left, right] */
    data class Placement(val part: Part, val left: Float, val right: Float)

    private const val EPS = 0.001f

    /**
     * 나눠 쓸 때의 가로 배치. **키 크기는 원래 레이아웃과 같은 비율을 유지**한다 (모음 키만 커지거나 하지 않는다).
     * 한 줄 전체에 같은 배율을 적용해 [gapW]만큼의 틈을 낼 자리를 만들고,
     * 왼쪽 묶음은 왼쪽 끝에, 오른쪽 묶음은 오른쪽 끝에 붙인다. 틈은 두 묶음 사이에 생긴다.
     * 왼쪽·오른쪽 키 개수가 달라도 키 크기를 바꾸지 않으므로, 가운데를 넘어가는 키가 있어도 그대로 둔다.
     *
     * @param startX 줄이 시작하는 x, [usableW] 줄의 전체 가로 길이
     */
    fun place(halves: Halves, startX: Float, usableW: Float, gapW: Float): List<Placement> {
        val total = (halves.left + halves.right).sumOf { it.weight.toDouble() }.toFloat()
        if (total <= 0f) return emptyList()
        val unit = (usableW - gapW) / total
        val out = ArrayList<Placement>()
        var x = startX
        for (p in halves.left) {
            out += Placement(p, x, x + p.weight * unit)
            x += p.weight * unit
        }
        val rightTotal = halves.right.sumOf { it.weight.toDouble() }.toFloat()
        x = startX + usableW - rightTotal * unit
        for (p in halves.right) {
            out += Placement(p, x, x + p.weight * unit)
            x += p.weight * unit
        }
        return out
    }

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
