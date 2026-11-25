package com.aura.ai.data

import android.app.Activity
import android.content.Context
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProPlan(
    val productId: String,
    val title: String,
    val price: String,
    val period: String,
    val details: ProductDetails,
    val offerToken: String,
)

sealed interface BillingStatus {
    data object Idle : BillingStatus
    data object Working : BillingStatus
    data class Success(val message: String) : BillingStatus
    data class Failed(val message: String) : BillingStatus
}

/** Google Play Billing for the Aura Pro subscription. Purchases are confirmed by the Aura server. */
class BillingManager(
    context: Context,
    private val repository: AuraRepository,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        val PRODUCT_IDS = listOf("aura_pro_monthly", "aura_pro_yearly")
    }

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _plans = MutableStateFlow<List<ProPlan>>(emptyList())
    val plans: StateFlow<List<ProPlan>> = _plans.asStateFlow()

    private val _status = MutableStateFlow<BillingStatus>(BillingStatus.Idle)
    val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val _available = MutableStateFlow<Boolean?>(null)
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    fun connect() {
        if (client.isReady) {
            scope.launch { loadPlans(); restore(silent = true) }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                _available.value = ok
                if (ok) scope.launch { loadPlans(); restore(silent = true) }
            }

            override fun onBillingServiceDisconnected() {
                _available.value = false
            }
        })
    }

    private suspend fun loadPlans() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            PRODUCT_IDS.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
            },
        ).build()
        val result = client.queryProductDetails(params)
        val list = result.productDetailsList.orEmpty().mapNotNull { pd ->
            val offer = pd.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            ProPlan(
                productId = pd.productId,
                title = if (pd.productId.contains("year")) "Годовая" else "Месячная",
                price = phase.formattedPrice,
                period = if (phase.billingPeriod.contains("Y")) "в год" else "в месяц",
                details = pd,
                offerToken = offer.offerToken,
            )
        }.sortedBy { if (it.productId.contains("year")) 1 else 0 }
        _plans.value = list
    }

    fun buy(activity: Activity, plan: ProPlan) {
        val userId = repository.account.value?.user?.id
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.details)
                        .setOfferToken(plan.offerToken)
                        .build(),
                ),
            )
            .apply { if (userId != null) setObfuscatedAccountId(userId.replace("-", "").take(64)) }
            .build()
        _status.value = BillingStatus.Working
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _status.value = BillingStatus.Failed(describe(result))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { purchases?.forEach { handle(it, silent = false) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> _status.value = BillingStatus.Idle
            else -> _status.value = BillingStatus.Failed(describe(result))
        }
    }

    /** Re-checks active subscriptions with the server (runs on launch and from "Restore"). */
    suspend fun restore(silent: Boolean) {
        if (!client.isReady) {
            if (!silent) _status.value = BillingStatus.Failed("Google Play недоступен на этом устройстве")
            return
        }
        if (!silent) _status.value = BillingStatus.Working
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        val active = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (active.isEmpty()) {
            if (!silent) _status.value = BillingStatus.Failed("Активных подписок не найдено")
            return
        }
        active.forEach { handle(it, silent) }
    }

    private suspend fun handle(purchase: Purchase, silent: Boolean) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _status.value = BillingStatus.Success("Оплата обрабатывается. Pro включится автоматически.")
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return
        try {
            val account = repository.api.verifyGooglePurchase(productId, purchase.purchaseToken)
            repository.applyAccount(account)
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                )
            }
            if (!silent) _status.value = BillingStatus.Success("Aura Pro активирован")
        } catch (e: ApiException) {
            if (!silent) _status.value = BillingStatus.Failed(e.message)
        }
    }

    fun resetStatus() {
        _status.value = BillingStatus.Idle
    }

    private fun describe(result: BillingResult) = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Покупки недоступны: войдите в Google Play"
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Подписка уже оформлена. Нажмите «Восстановить покупки»."
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Google Play временно недоступен"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Подписка пока недоступна в вашей стране"
        else -> "Ошибка оплаты (${result.responseCode}) ${result.debugMessage}".trim()
    }
}
