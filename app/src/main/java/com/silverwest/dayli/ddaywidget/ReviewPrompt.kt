package com.silverwest.dayli.ddaywidget

/**
 * 평점 요청(Google Play 인앱 리뷰) 시점 규칙 (저장소와 분리해 단위 테스트 가능)
 * - 만족한 순간(새 항목 저장 직후)에만, 아래 조건을 모두 만족할 때 요청
 * - 실제로 창을 띄울지는 Google이 정함 (할당량). 앱은 띄워졌는지 알 수 없음 → 보상·선별 질문 금지(정책)
 */
object ReviewPrompt {
    const val MIN_DAYS_SINCE_INSTALL = 3
    const val MIN_ITEMS = 3
    const val MIN_DAYS_BETWEEN_ASKS = 90

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun shouldAsk(installedAtMs: Long, nowMs: Long, itemCount: Int, lastAskedMs: Long?): Boolean {
        if (nowMs - installedAtMs < MIN_DAYS_SINCE_INSTALL * DAY_MS) return false
        if (itemCount < MIN_ITEMS) return false
        if (lastAskedMs != null && nowMs - lastAskedMs < MIN_DAYS_BETWEEN_ASKS * DAY_MS) return false
        return true
    }
}
