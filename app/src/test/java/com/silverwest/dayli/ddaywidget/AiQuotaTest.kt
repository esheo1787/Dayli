package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AiQuotaTest {

    private val oct = AiQuota.State("2026-10", freeUsed = 0, adCredits = 0)

    @Test
    fun free_monthlyAllowanceThenNone() {
        var s = oct
        repeat(AiQuota.FREE_PER_MONTH) {
            val (next, source) = AiQuota.consume(s)!!
            assertEquals(AiQuota.Source.FREE, source)
            s = next
        }
        assertEquals(0, AiQuota.totalRemaining(s))
        assertNull(AiQuota.consume(s))
    }

    @Test
    fun freeIsUsedBeforeAdCredits() {
        val s = oct.copy(freeUsed = AiQuota.FREE_PER_MONTH - 1, adCredits = 1)
        val (afterFirst, first) = AiQuota.consume(s)!!
        assertEquals(AiQuota.Source.FREE, first)
        val (afterSecond, second) = AiQuota.consume(afterFirst)!!
        assertEquals(AiQuota.Source.AD, second)
        assertNull(AiQuota.consume(afterSecond))
    }

    @Test
    fun newMonth_resetsFreeOnly_keepsAdCredits() {
        val used = oct.copy(freeUsed = AiQuota.FREE_PER_MONTH, adCredits = 2)
        val nov = AiQuota.normalized(used, "2026-11")
        assertEquals(AiQuota.FREE_PER_MONTH, AiQuota.freeRemaining(nov))
        assertEquals(2, nov.adCredits)
        // 같은 달이면 그대로
        assertEquals(used, AiQuota.normalized(used, "2026-10"))
    }

    @Test
    fun adReward_addsRewardAmount() {
        assertEquals(AiQuota.AD_REWARD, AiQuota.addReward(oct).adCredits)
    }

    @Test
    fun refund_returnsToSameSource() {
        val (afterFree, free) = AiQuota.consume(oct)!!
        assertEquals(oct, AiQuota.refund(afterFree, free))
        val adOnly = oct.copy(freeUsed = AiQuota.FREE_PER_MONTH, adCredits = 1)
        val (afterAd, ad) = AiQuota.consume(adOnly)!!
        assertEquals(adOnly, AiQuota.refund(afterAd, ad))
    }

    @Test
    fun refund_afterMonthChange_neverNegative() {
        // 10월에 쓰고 실패 응답이 11월에 온 경우: 무료분은 이미 초기화 → 음수가 되지 않음
        val nov = AiQuota.normalized(oct.copy(freeUsed = 1), "2026-11")
        assertEquals(0, AiQuota.refund(nov, AiQuota.Source.FREE).freeUsed)
    }

    @Test
    fun monthKey_format() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 5, 12, 0) }
        assertEquals("2026-01", AiQuota.monthKey(cal.timeInMillis))
    }

    @Test
    fun quotaError_spendCapVsRateLimit() {
        assertEquals(AiErrors.Kind.SERVICE_PAUSED, AiErrors.kindOfQuotaMessage("Billing spend cap reached for this project"))
        assertEquals(AiErrors.Kind.RATE_LIMITED, AiErrors.kindOfQuotaMessage("Quota exceeded: generate content requests per minute per user"))
    }

    @Test
    fun adLoadError_noFillVsNetwork() {
        assertEquals(RewardedAds.Status.NO_FILL, RewardedAds.statusOf(3))
        assertEquals(RewardedAds.Status.NETWORK_ERROR, RewardedAds.statusOf(2))
        assertEquals(RewardedAds.Status.FAILED, RewardedAds.statusOf(0))
    }

    @Test
    fun preloadedAd_expiresBeforeOneHour() {
        val loaded = 1_000_000L
        assertEquals(false, RewardedAds.isExpired(loaded, loaded + 54 * 60 * 1000L))
        assertEquals(true, RewardedAds.isExpired(loaded, loaded + RewardedAds.EXPIRY_MS))
        assertEquals(true, RewardedAds.isExpired(loaded, loaded + 60 * 60 * 1000L))
    }
}
