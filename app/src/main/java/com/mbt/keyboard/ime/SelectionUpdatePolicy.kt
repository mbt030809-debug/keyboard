package com.mbt.keyboard.ime

/**
 * "입력창 커서가 앱 쪽에서 움직였는가"를 판단해 한글 조합을 끊을지 정한다 (안드로이드 비의존, 테스트 가능).
 *
 * 문제: 선택 변경 알림은 **비동기**로 늦게 도착한다. 빠르게 치면 "조합 영역이 없던 옛 상태"의 알림이
 * 새 글자를 조합하는 중에 도착하는데, 이걸 "사용자가 커서를 옮겼다"로 오해하면 조합이 끊겨서
 * `ㅇ`+`ㅏ`가 `ㅇ ㅏ`로 갈라진다.
 *
 * 구분 방법: 알림에 조합 영역이 아예 없고(candidatesEnd < 0) 우리가 방금(짧은 시간 안에) 직접 편집했다면
 * 우리 편집에 대한 옛 알림으로 보고 무시한다.
 */
object SelectionUpdatePolicy {

    /** 우리 편집 뒤 이 시간 안에 오는 "조합 영역 없음" 알림은 옛 알림으로 본다 */
    const val STALE_WINDOW_MS = 600L

    fun shouldResetComposition(
        isComposing: Boolean,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesEnd: Int,
        msSinceOwnEdit: Long,
    ): Boolean {
        if (!isComposing) return false
        if (candidatesEnd < 0 && msSinceOwnEdit in 0..STALE_WINDOW_MS) return false   // 옛 알림
        return newSelStart != candidatesEnd || newSelEnd != candidatesEnd
    }
}
