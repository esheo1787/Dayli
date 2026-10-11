package com.silverwest.dayli.ddaywidget

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * 프리미엄 (한 번 결제, Google Play 일회성 상품): 광고 제거 + 폰 캘린더 연동 + 캘린더 위젯.
 * 구매 상태는 Play에서 다시 불러올 수 있음(재설치·기기 변경 시 "구매 복원").
 * 앱 안 저장값은 화면을 바로 그리기 위한 캐시이고, 앱을 켤 때마다 Play 구매 내역으로 맞춤.
 */
object Premium {

    /** Play 콘솔 > 수익 창출 > 인앱 상품에 같은 ID로 만들어야 함 */
    const val PRODUCT_ID = "dayli_premium"

    /** Compose가 바로 다시 그리도록 상태 값 */
    var unlocked by mutableStateOf(false)
        private set

    /** 가격 표시용 (예: "₩4,900"). 불러오기 전엔 null */
    var priceText by mutableStateOf<String?>(null)
        private set

    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    /** 테스트·스크린샷 전용: true면 결제 없이 열림 */
    @androidx.annotation.VisibleForTesting
    internal var forceUnlockedForTest = false

    fun isUnlocked(context: Context): Boolean =
        forceUnlockedForTest || debugForced(context) || unlocked || DdaySettings.isPremiumCached(context)

    /**
     * debug 빌드 전용: 에뮬레이터는 Play 결제를 못 하므로 설정값으로 프리미엄 화면·위젯을 확인
     * (adb로 dday_widget_settings의 debug_force_premium=true). release에서는 항상 false
     */
    private fun debugForced(context: Context): Boolean =
        com.silverwest.dayli.BuildConfig.DEBUG &&
            context.getSharedPreferences("dday_widget_settings", Context.MODE_PRIVATE).getBoolean("debug_force_premium", false)

    /** 앱 시작 시 한 번: 저장값으로 바로 표시하고, Play 구매 내역으로 맞춤 */
    fun init(context: Context) {
        val app = context.applicationContext
        unlocked = DdaySettings.isPremiumCached(app) || debugForced(app)
        if (debugForced(app)) return
        if (client != null) return
        val listener = PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                handlePurchases(app, purchases)
            }
        }
        client = BillingClient.newBuilder(app)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connectAndRefresh(app)
    }

    private fun connectAndRefresh(app: Context) {
        val c = client ?: return
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                refreshPurchases(app)
                loadDetails()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Play 구매 내역으로 잠금 상태를 맞춤 (구매 복원도 이것) */
    fun refreshPurchases(context: Context) {
        val app = context.applicationContext
        if (debugForced(app)) return
        val c = client ?: return
        if (!c.isReady) return  // 연결되면 onBillingSetupFinished에서 다시 조회
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        c.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { isOwnedPremium(it) }
            setUnlocked(app, owned)
            handlePurchases(app, purchases)
        }
    }

    private fun loadDetails() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        c.queryProductDetailsAsync(params) { result, queried ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details = queried.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            priceText = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** 결제 창 열기. 준비 안 됐으면 false */
    fun purchase(activity: Activity): Boolean {
        val c = client ?: return false
        val d = details ?: run { loadDetails(); return false }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
        ).build()
        return c.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    internal fun isOwnedPremium(purchase: Purchase): Boolean =
        PRODUCT_ID in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PURCHASED

    /** 결제 완료(PURCHASED)만 열고, 확인(acknowledge)하지 않으면 3일 뒤 자동 환불되므로 확인 처리 */
    private fun handlePurchases(app: Context, purchases: List<Purchase>) {
        purchases.filter { isOwnedPremium(it) }.forEach { purchase ->
            setUnlocked(app, true)
            if (!purchase.isAcknowledged) acknowledge(purchase.purchaseToken, attempt = 1)
        }
    }

    /**
     * 구매 확인. 3일 안에 확인하지 않으면 Play가 자동 환불하므로 실패하면 잠시 뒤 다시 시도하고,
     * 그래도 실패하면 앱으로 돌아올 때(onResume → refreshPurchases)마다 다시 시도됨
     */
    private fun acknowledge(token: String, attempt: Int) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        client?.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) return@acknowledgePurchase
            Log.w("DDAY_WIDGET", "구매 확인 실패($attempt): ${result.debugMessage}")
            if (attempt < MAX_ACK_ATTEMPTS) {
                android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed({ acknowledge(token, attempt + 1) }, ACK_RETRY_DELAY_MS * attempt)
            }
        }
    }

    private const val MAX_ACK_ATTEMPTS = 3
    private const val ACK_RETRY_DELAY_MS = 5_000L

    private fun setUnlocked(app: Context, value: Boolean) {
        if (unlocked == value && DdaySettings.isPremiumCached(app) == value) return
        unlocked = value
        DdaySettings.setPremiumCached(app, value)
        // 캘린더 위젯·광고 표시가 바로 바뀌도록
        DdayWidgetProvider.refreshAllWidgets(app)
    }
}
