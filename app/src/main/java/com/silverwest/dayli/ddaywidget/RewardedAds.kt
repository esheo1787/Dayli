package com.silverwest.dayli.ddaywidget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.silverwest.dayli.BuildConfig

/**
 * AI 횟수를 받는 보상형 광고.
 * 보상은 SDK의 보상 이벤트(onUserEarnedReward)에서만 한 번 지급. 광고를 중간에 닫으면 지급하지 않음.
 * 광고가 없다고 횟수를 대신 주지 않음 (재시도 안내만).
 * 광고 단위 ID: debug는 Google 테스트 광고, release는 build.gradle의 REWARDED_AD_UNIT_ID (비어 있으면 광고 없음)
 */
object RewardedAds {

    /** 화면에 보여줄 광고 상태 (Compose가 바로 다시 그리도록 상태 값) */
    enum class Status { NOT_CONFIGURED, IDLE, LOADING, READY, NO_FILL, NETWORK_ERROR, FAILED }

    var status by mutableStateOf(if (BuildConfig.REWARDED_AD_UNIT_ID.isBlank()) Status.NOT_CONFIGURED else Status.IDLE)
        private set

    private var ad: RewardedAd? = null
    private var loadedAt = 0L

    /** 받은 광고는 1시간 뒤 만료 (AdMob) → 여유를 두고 55분 지나면 버리고 새로 받음 */
    internal const val EXPIRY_MS = 55 * 60 * 1000L

    internal fun isExpired(loadedAtMs: Long, nowMs: Long): Boolean = nowMs - loadedAtMs >= EXPIRY_MS

    /** 경과 시간 기준 시계 (기기 시간을 바꿔도 영향 없음). 테스트에서 바꿔 끼움 */
    @androidx.annotation.VisibleForTesting
    internal var clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }

    /** 테스트 전용: false면 실제 광고를 불러오지 않음 (상태를 직접 넣어 화면 확인) */
    @androidx.annotation.VisibleForTesting
    internal var loadEnabledForTest = true

    @androidx.annotation.VisibleForTesting
    internal fun setStatusForTest(value: Status) {
        status = value
    }

    val isConfigured: Boolean get() = BuildConfig.REWARDED_AD_UNIT_ID.isNotBlank()

    /** 만료된 광고는 버림 (READY로 남아 있으면 눌러도 안 뜨는 문제 방지) */
    private fun dropIfExpired() {
        if (ad != null && isExpired(loadedAt, clock())) {
            ad = null
            if (status == Status.READY) status = Status.IDLE
        }
    }

    /** 미리 불러 두기 (이미 있거나 불러오는 중이면 무시). 실패 후 다시 부르면 재시도 */
    fun preload(context: Context) {
        dropIfExpired()
        if (!isConfigured || !loadEnabledForTest || ad != null || status == Status.LOADING) return
        status = Status.LOADING
        RewardedAd.load(
            context.applicationContext,
            BuildConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ad = loaded
                    loadedAt = clock()
                    status = Status.READY
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    status = statusOf(error.code)
                }
            }
        )
    }

    /** AdMob 로드 실패 코드 → 안내 구분 (3: 보여줄 광고 없음, 2: 네트워크) */
    internal fun statusOf(errorCode: Int): Status = when (errorCode) {
        AdRequest.ERROR_CODE_NO_FILL, AdRequest.ERROR_CODE_MEDIATION_NO_FILL -> Status.NO_FILL
        AdRequest.ERROR_CODE_NETWORK_ERROR -> Status.NETWORK_ERROR
        else -> Status.FAILED
    }

    /**
     * 광고 보여주기. 준비 안 됐으면 false.
     * @param onRewarded 보상 이벤트 시 정확히 한 번
     * @param onClosed 광고가 닫힘 (보상 여부와 무관). 다음 광고를 미리 불러옴
     */
    fun show(context: Context, onRewarded: () -> Unit, onClosed: () -> Unit): Boolean {
        val activity = context.findActivity() ?: return false
        dropIfExpired()
        val current = ad ?: run { preload(context); return false }
        ad = null  // 한 광고는 한 번만 보여줄 수 있음
        status = Status.IDLE
        var rewarded = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed()
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed()
                preload(activity)
            }
        }
        current.show(activity) {
            if (!rewarded) {
                rewarded = true
                onRewarded()
            }
        }
        return true
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
