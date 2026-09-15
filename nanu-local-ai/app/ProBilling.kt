package com.example.llama

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*

/** One application-scoped BillingClient; no Activity retained between calls. */
class ProBilling private constructor(private val context: Context) : PurchasesUpdatedListener {
    data class State(val price: String? = null, val ready: Boolean = false, val message: String = "Connecting to Google Play…", val owned: Boolean = false)
    val state = kotlinx.coroutines.flow.MutableStateFlow(State(owned = ProEntitlement.enabled(context)))
    private val main = Handler(Looper.getMainLooper())
    private var connecting = false
    private var purchaseQueryComplete = false
    private var details: ProductDetails? = null
    private val acknowledging = mutableSetOf<String>()
    private val client = BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    private fun message(value: String) { state.value = state.value.copy(message = value, owned = ProEntitlement.enabled(context)) }
    fun refresh() {
        if (ProEntitlement.publicKey(context).isBlank()) { message("Purchases are not available in this build yet."); return }
        if (client.isReady) { load(); return }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() { connecting = false; state.value = state.value.copy(ready = false); message("Google Play disconnected. Tap Restore purchase to retry.") }
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) load()
                else message("Google Play is unavailable (${result.responseCode}). Install from Play and try again.")
            }
        })
    }
    private fun load() {
        purchaseQueryComplete = false
        state.value = state.value.copy(ready = false)
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            main.post {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchaseQueryComplete = true
                    if (purchases.none { it.products.contains(ProEntitlement.PRODUCT) && it.purchaseState == Purchase.PurchaseState.PURCHASED }) ProEntitlement.clear(context)
                    purchases.forEach(::process)
                    message(if (ProEntitlement.enabled(context)) "Nanu Pro is unlocked." else if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) "Payment pending. Pro unlocks after payment completes." else "Pay once. No monthly subscription.")
                } else message("Could not restore purchases. Check your connection and retry.")
                updateReady()
            }
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(ProEntitlement.PRODUCT).setProductType(BillingClient.ProductType.INAPP).build()
        )).build()) { result, products ->
            main.post {
                details = if (result.responseCode == BillingClient.BillingResponseCode.OK) products.productDetailsList.firstOrNull { it.productId == ProEntitlement.PRODUCT } else null
                val offer = details?.oneTimePurchaseOfferDetailsList?.singleOrNull()
                state.value = state.value.copy(price = offer?.formattedPrice)
                updateReady()
                if (offer == null) message("Pro is not available for purchase in Google Play yet. Free tools remain available.")
            }
        }
    }
    private fun updateReady() { state.value = state.value.copy(ready = purchaseQueryComplete && details?.oneTimePurchaseOfferDetailsList?.size == 1, owned = ProEntitlement.enabled(context)) }
    fun buy(activity: Activity) {
        if (ProEntitlement.enabled(context)) return
        val product = details ?: return
        val offer = product.oneTimePurchaseOfferDetailsList?.singleOrNull() ?: return
        if (!state.value.ready) return
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        offer.offerToken?.takeIf { it.isNotBlank() }?.let(paramsBuilder::setOfferToken)
        val params = paramsBuilder.build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh()
        else if (result.responseCode != BillingClient.BillingResponseCode.OK) message("Purchase could not start (${result.responseCode}). Please retry.")
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        main.post {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::process)
                BillingClient.BillingResponseCode.USER_CANCELED -> message("Purchase cancelled. You can continue with Free.")
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                else -> message("Payment could not complete (${result.responseCode}). Please retry or restore.")
            }
        }
    }
    private fun process(purchase: Purchase) {
        if (!purchase.products.contains(ProEntitlement.PRODUCT)) return
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) { message("Payment pending. Pro unlocks after payment completes."); return }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!ProEntitlement.verify(ProEntitlement.publicKey(context), purchase.originalJson, purchase.signature, context.packageName)) { message("Purchase verification failed. Contact support with your Play receipt."); return }
        try { ProEntitlement.save(context, purchase.originalJson, purchase.signature) }
        catch (e: Exception) { message(e.message ?: "Could not save purchase."); return }
        message("Nanu Pro is unlocked.")
        if (!purchase.isAcknowledged && acknowledging.add(purchase.purchaseToken)) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result ->
                main.post {
                    acknowledging.remove(purchase.purchaseToken)
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) message("Pro unlocked. Keep Play connected to finish payment confirmation; tap Restore to retry.")
                }
            }
        }
    }
    companion object {
        @Volatile private var instance: ProBilling? = null
        fun get(context: Context): ProBilling = instance ?: synchronized(this) { instance ?: ProBilling(context.applicationContext).also { instance = it } }
    }
}
