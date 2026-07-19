package com.luv.couple.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

data class PlayPurchaseResult(
    val productId: String,
    val purchaseToken: String,
    val orderId: String?
)

/**
 * Google Play Billing für Coin-Packs (Consumables).
 * Nach Server-Gutschrift muss [consume] aufgerufen werden.
 */
class PlayBilling(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val connectMutex = Mutex()
    private val productCache = ConcurrentHashMap<String, ProductDetails>()
    private var purchaseWaiter: CompletableDeferred<Result<PlayPurchaseResult>>? = null

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    val formattedPrices: Map<String, String>
        get() = productCache.mapValues { (_, details) ->
            details.oneTimePurchaseOfferDetails?.formattedPrice
                ?: details.productId
        }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val waiter = purchaseWaiter
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            val purchase = purchases.firstOrNull {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (purchase != null) {
                val productId = purchase.products.firstOrNull().orEmpty()
                waiter?.complete(
                    Result.success(
                        PlayPurchaseResult(
                            productId = productId,
                            purchaseToken = purchase.purchaseToken,
                            orderId = purchase.orderId
                        )
                    )
                )
                purchaseWaiter = null
                return
            }
        }
        if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            waiter?.complete(Result.failure(PlayBillingException("Kauf abgebrochen")))
            purchaseWaiter = null
            return
        }
        if (waiter != null && result.responseCode != BillingClient.BillingResponseCode.OK) {
            waiter.complete(
                Result.failure(
                    PlayBillingException(result.debugMessage.ifBlank { "Play Billing Fehler (${result.responseCode})" })
                )
            )
            purchaseWaiter = null
        }
    }

    suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (cont.isActive) {
                        cont.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // nächster Aufruf verbindet erneut
                }
            })
        }
    }

    suspend fun queryProducts(productIds: List<String>): Map<String, ProductDetails> =
        withContext(Dispatchers.IO) {
            if (productIds.isEmpty()) return@withContext emptyMap()
            if (!ensureConnected()) return@withContext emptyMap()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    productIds.map { id ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    }
                )
                .build()
            suspendCancellableCoroutine { cont ->
                client.queryProductDetailsAsync(params) { result, detailsList ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        val list = detailsList.orEmpty()
                        list.forEach { productCache[it.productId] = it }
                        if (cont.isActive) {
                            cont.resume(list.associateBy { it.productId })
                        }
                    } else if (cont.isActive) {
                        cont.resume(emptyMap())
                    }
                }
            }
        }

    suspend fun launchPurchase(activity: Activity, productId: String): PlayPurchaseResult {
        if (!ensureConnected()) {
            throw PlayBillingException("Google Play Billing nicht verfügbar.")
        }
        var details = productCache[productId]
        if (details == null) {
            details = queryProducts(listOf(productId))[productId]
        }
        if (details == null) {
            throw PlayBillingException(
                "Produkt nicht in Google Play gefunden. Bitte App aus dem Play Store installieren."
            )
        }
        val offer = details.oneTimePurchaseOfferDetails
            ?: throw PlayBillingException("Kein Preis für dieses Paket.")
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        // offer unused except validating it exists
        check(offer.formattedPrice.isNotBlank())
        val deferred = CompletableDeferred<Result<PlayPurchaseResult>>()
        purchaseWaiter = deferred
        val launch = client.launchBillingFlow(activity, flowParams)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseWaiter = null
            throw PlayBillingException(
                launch.debugMessage.ifBlank { "Kauf konnte nicht gestartet werden (${launch.responseCode})" }
            )
        }
        return deferred.await().getOrThrow()
    }

    suspend fun consume(purchaseToken: String) = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        suspendCancellableCoroutine { cont ->
            client.consumeAsync(params) { _, _ ->
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    /** Offene Käufe nachziehen (z. B. nach Absturz vor Server-Gutschrift). */
    suspend fun queryUnconsumedPurchases(): List<PlayPurchaseResult> = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext emptyList()
        suspendCancellableCoroutine { cont ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (cont.isActive) cont.resume(emptyList())
                    return@queryPurchasesAsync
                }
                val list = purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .mapNotNull { p ->
                        val id = p.products.firstOrNull() ?: return@mapNotNull null
                        PlayPurchaseResult(id, p.purchaseToken, p.orderId)
                    }
                if (cont.isActive) cont.resume(list)
            }
        }
    }

    fun endConnection() {
        runCatching { client.endConnection() }
    }
}

class PlayBillingException(message: String) : Exception(message)
