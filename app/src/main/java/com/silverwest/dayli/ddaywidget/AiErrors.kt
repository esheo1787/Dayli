package com.silverwest.dayli.ddaywidget

import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServiceDisabledException
import java.io.IOException

/**
 * AI 분석 실패 안내. 같은 429라도 원인이 달라 안내가 달라야 함
 * - 서비스 전체 지출 상한·사용 중지 → 이번 달 일시 중지
 * - 짧은 시간 요청 제한 → 잠시 후 다시
 * (개인 월 무료분 소진은 호출 전에 AiQuota로 막으므로 여기 오지 않음)
 */
object AiErrors {

    enum class Kind { SERVICE_PAUSED, RATE_LIMITED, NETWORK, OTHER }

    // 지출 상한·결제 관련 응답에 들어가는 말 (상한 도달 시 서비스가 멈춤)
    private val pausedHints = listOf("billing", "spend cap", "spending cap", "spend limit", "budget")

    fun kindOf(e: Throwable): Kind {
        val message = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            e is ServiceDisabledException -> Kind.SERVICE_PAUSED
            e is QuotaExceededException -> kindOfQuotaMessage(message)
            e is RequestTimeoutException || generateSequence(e) { it.cause }.any { it is IOException } -> Kind.NETWORK
            // SDK가 다른 예외로 감싸도 응답 코드가 메시지에 남아 있으면 요청 제한으로 판단
            "429" in message || "RESOURCE_EXHAUSTED" in message -> kindOfQuotaMessage(message)
            else -> Kind.OTHER
        }
    }

    /** 요청 제한 응답: 결제·지출 상한 문구가 있으면 서비스 중지, 아니면 잠깐의 요청 제한 */
    fun kindOfQuotaMessage(message: String): Kind {
        val lower = message.lowercase()
        return if (pausedHints.any { lower.contains(it) }) Kind.SERVICE_PAUSED else Kind.RATE_LIMITED
    }

    fun userMessage(kind: Kind): String = when (kind) {
        Kind.SERVICE_PAUSED -> "이번 달 AI 자동 입력이 일시 중지됐어요. 직접 입력은 계속 쓸 수 있어요."
        Kind.RATE_LIMITED -> "요청이 많아 잠시 막혔어요. 잠시 후 다시 시도해 주세요."
        Kind.NETWORK -> "인터넷 연결을 확인하고 다시 시도해 주세요."
        Kind.OTHER -> "분석하지 못했어요. 다시 시도해 주세요."
    }
}
