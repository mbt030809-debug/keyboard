package com.mbt.keyboard.spell

/**
 * 온디바이스 간단 맞춤법 검사 (인터넷·개인정보 전송 없음).
 *
 * 자주 틀리는 표현 중 **오탐이 거의 없는 것만** 고친다.
 * (되요→돼요, 몇일→며칠, 갈께→갈게, 할수있어→할 수 있어 …)
 * 문맥이 필요한 판단(되/돼 전부, 이/히 전부 등)은 하지 않는다.
 */
object SpellChecker {

    data class Change(val from: String, val to: String)

    data class Result(val text: String, val changes: List<Change>)

    /** 항상 틀린 표현 → 올바른 표현. 순서대로 적용. */
    private val LITERAL = listOf(
        "몇 일" to "며칠", "몇일" to "며칠",
        "어떻해" to "어떡해",
        "됬" to "됐",
        "되요" to "돼요",
        "되서" to "돼서",
        "않되" to "안 돼", "않돼" to "안 돼",
        "않해" to "안 해", "않했" to "안 했",
        "할려고" to "하려고", "될려고" to "되려고",
        "왠일" to "웬일", "웬지" to "왠지",
        "금새" to "금세",
        "어의없" to "어이없",
        "설겆이" to "설거지",
        "오랫만" to "오랜만",
        "희안" to "희한",
        "일부로" to "일부러",
        "역활" to "역할",
        "설레임" to "설렘",
        "깨끗히" to "깨끗이", "곰곰히" to "곰곰이", "일일히" to "일일이", "틈틈히" to "틈틈이",
        "열심이" to "열심히", "꼼꼼이" to "꼼꼼히", "조용이" to "조용히",
        "읍니다" to "습니다",
        "있슴" to "있음", "없슴" to "없음",
        "뵈요" to "봬요",
        "찌게" to "찌개", "육계장" to "육개장",
    )

    /** 영어: 소문자 단어 단위로만 교정 */
    private val ENGLISH = listOf(
        "teh" to "the", "dont" to "don't", "cant" to "can't", "doesnt" to "doesn't", "didnt" to "didn't",
        "recieve" to "receive", "definately" to "definitely", "seperate" to "separate",
        "occured" to "occurred", "alot" to "a lot", "thier" to "their", "untill" to "until",
        "wierd" to "weird", "beleive" to "believe",
    )

    private val KKE = Regex("([가-힣])께(?=요|[^가-힣]|$)")       // 갈께(요) → 갈게(요)
    private val KKEOL = Regex("([가-힣])껄")                       // 갈껄 → 갈걸
    private val KKEO = Regex("([가-힣])꺼")                        // 할꺼야 → 할 거야
    private val SU = Regex("([가-힣])수 ?(있|없)")                // 할수있어 → 할 수 있어
    private val DOUBLE_SPACE = Regex(" {2,}")

    fun check(input: String): Result {
        val changes = ArrayList<Change>()
        var text = input

        for ((wrong, right) in LITERAL) text = replaceLiteral(text, wrong, right, changes)

        // 받침 ㄹ 뒤에서만 적용되는 규칙 (ㄹ 받침: 할, 갈, 줄, 볼, 올 …)
        text = KKE.replace(text) { m -> fixIfRieul(m, changes) { g -> g + "게" } }
        text = KKEOL.replace(text) { m -> fixIfRieul(m, changes) { g -> g + "걸" } }
        text = KKEO.replace(text) { m -> fixIfRieul(m, changes) { g -> "$g 거" } }
        text = SU.replace(text) { m ->
            val syl = m.groupValues[1]
            if (hasRieulFinal(syl[0])) record(m, "$syl 수 ${m.groupValues[2]}", changes)
            else m.value
        }

        for ((wrong, right) in ENGLISH) {
            text = Regex("\\b$wrong\\b").replace(text) { m -> record(m, right, changes) }
        }

        text = DOUBLE_SPACE.replace(text) { m -> record(m, " ", changes) }
        return Result(text, changes)
    }

    private fun replaceLiteral(text: String, wrong: String, right: String, out: MutableList<Change>): String {
        if (!text.contains(wrong)) return text
        // 겹치지 않게 센다 (예: "되요되요")
        val count = Regex(Regex.escape(wrong)).findAll(text).count()
        repeat(count) { out += Change(wrong, right) }
        return text.replace(wrong, right)
    }

    private fun fixIfRieul(
        m: MatchResult,
        changes: MutableList<Change>,
        build: (String) -> String,
    ): String {
        val g = m.groupValues[1]
        return if (hasRieulFinal(g[0])) record(m, build(g), changes) else m.value
    }

    private fun record(m: MatchResult, replacement: String, changes: MutableList<Change>): String {
        changes += Change(m.value, replacement)
        return replacement
    }

    /** 한글 음절의 받침이 ㄹ인지 (종성 인덱스 8) */
    private fun hasRieulFinal(c: Char): Boolean =
        c in '가'..'힣' && (c.code - 0xAC00) % 28 == 8
}
