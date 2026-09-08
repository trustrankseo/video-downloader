package com.faisal.freshdownloader

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class BillingManager(context: Context) : PurchasesUpdatedListener {
    companion object {
        const val PRODUCT_ID = "universal_downloader_premium_monthly"
    }

    val isPremium = mutableStateOf(false)
    val priceText = mutableStateOf("$0.99 / month")
    val statusText = mutableStateOf("Checking subscription…")

    private var productDetails: ProductDetails? = null
    private var connected = false

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun start() {
        if (billingClient.isReady) {
            connected = true
            refreshPurchases()
            queryProduct()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    refreshPurchases()
                    queryProduct()
                } else {
                    statusText.value = "Google Play billing unavailable"
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
                statusText.value = "Google Play disconnected"
            }
        })
    }

    fun close() {
        runCatching { billingClient.endConnection() }
    }

    fun restore() {
        if (!connected) start() else refreshPurchases()
    }

    fun purchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            statusText.value = "Premium plan is not ready yet"
            queryProduct()
            return
        }
        val offer = details.subscriptionOfferDetails
            ?.firstOrNull { it.offerToken.isNotBlank() }
        if (offer == null) {
            statusText.value = "Create an active base plan in Play Console"
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            statusText.value = result.debugMessage.ifBlank { "Unable to open Google Play purchase" }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> statusText.value = "Purchase cancelled"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> statusText.value = result.debugMessage.ifBlank { "Purchase failed" }
        }
    }

    private fun queryProduct() {
        if (!billingClient.isReady) return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                statusText.value = "Premium plan unavailable"
                return@queryProductDetailsAsync
            }
            productDetails = details.firstOrNull()
            val phase = productDetails?.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.lastOrNull()
            if (phase != null) priceText.value = phase.formattedPrice + " / month"
            if (!isPremium.value) statusText.value = "3 free premium trials • then ${priceText.value}"
        }
    }

    private fun refreshPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            } else {
                statusText.value = "Could not verify subscription"
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val active = purchases.firstOrNull { purchase ->
            purchase.products.contains(PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        isPremium.value = active != null
        if (active == null) {
            statusText.value = "Free plan"
            return
        }
        statusText.value = "Premium active"
        if (!active.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(active.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    statusText.value = "Premium active"
                }
            }
        }
    }
}
