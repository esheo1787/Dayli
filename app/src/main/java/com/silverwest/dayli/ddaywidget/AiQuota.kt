package com.silverwest.dayli.ddaywidget

import java.util.Calendar

/**
 * AI 자동 입력 사용 횟수 규칙 (저장소와 분리해 단위 테스트 가능)
 * - 무료: 매달 FREE_PER_MONTH회, 달이 바뀌면 다시 채워짐
 * - 광고: 보상형 광고 1번에 AD_REWARD회. 달이 바뀌어도 사라지지 않음
 * - 분석 요청 한 번 = 1회 (일정 여러 개를 뽑아도 1회). 무료분을 먼저 씀
 *
 * 폰에 저장하는 한도라 재설치하면 무료분이 다시 채워짐 → 비용 방어가 아니라 사용량 조절용.
 * 실제 비용 방어는 Firebase 지출 상한·사용자당 요청 제한(콘솔 설정)
 */
object AiQuota {
    const val FREE_PER_MONTH = 10
    const val AD_REWARD = 3

    data class State(val monthKey: String, val freeUsed: Int, val adCredits: Int)

    /** 어느 몫에서 썼는지 (실패 시 같은 몫으로 돌려줌) */
    enum class Source { FREE, AD }

    fun monthKey(nowMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    /** 달이 바뀌었으면 무료 사용분만 초기화 (광고로 받은 횟수는 유지) */
    fun normalized(state: State, nowKey: String): State =
        if (state.monthKey == nowKey) state else state.copy(monthKey = nowKey, freeUsed = 0)

    fun freeRemaining(state: State): Int = (FREE_PER_MONTH - state.freeUsed).coerceAtLeast(0)

    fun totalRemaining(state: State): Int = freeRemaining(state) + state.adCredits

    /** 1회 사용. 남은 횟수가 없으면 null */
    fun consume(state: State): Pair<State, Source>? = when {
        freeRemaining(state) > 0 -> state.copy(freeUsed = state.freeUsed + 1) to Source.FREE
        state.adCredits > 0 -> state.copy(adCredits = state.adCredits - 1) to Source.AD
        else -> null
    }

    /** 분석 실패 시 돌려줌. 무료분은 그사이 달이 바뀌어 초기화됐으면 음수가 되지 않게 */
    fun refund(state: State, source: Source): State = when (source) {
        Source.FREE -> state.copy(freeUsed = (state.freeUsed - 1).coerceAtLeast(0))
        Source.AD -> state.copy(adCredits = state.adCredits + 1)
    }

    fun addReward(state: State): State = state.copy(adCredits = state.adCredits + AD_REWARD)
}
