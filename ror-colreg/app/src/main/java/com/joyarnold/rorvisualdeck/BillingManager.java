package com.joyarnold.rorvisualdeck;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Wraps Play Billing for a single one-time, non-consumable "premium unlock"
 * product. Unlock state is cached in SharedPreferences so the WebView bridge
 * can answer isPremiumUnlocked() synchronously without waiting on Play.
 */
public final class BillingManager implements PurchasesUpdatedListener {
    public static final String PREMIUM_PRODUCT_ID = "ror_premium_unlock";
    private static final String PREFS_NAME = "ror_billing";
    private static final String PREF_UNLOCKED = "premium_unlocked";

    public interface Listener {
        void onPremiumStateChanged(boolean unlocked);
        void onPriceLoaded(String formattedPrice);
        void onPurchaseError(String message);
    }

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final BillingClient billingClient;
    private ProductDetails premiumProductDetails;

    public BillingManager(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.billingClient = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    public boolean isPremiumUnlocked() {
        // Debug builds (sideloaded test APKs) always unlock: Play Billing purchases
        // only function once the app is distributed through a Play Console track,
        // so there is no real purchase to gate here. Release builds (bundleRelease,
        // what actually ships to Play Store) go through the real check below.
        if (BuildConfig.DEBUG) return true;
        return prefs.getBoolean(PREF_UNLOCKED, false);
    }

    public void start() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                    queryExistingPurchases();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });
    }

    private void queryProductDetails() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();
        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && !productDetailsList.isEmpty()) {
                premiumProductDetails = productDetailsList.get(0);
                ProductDetails.OneTimePurchaseOfferDetails offer =
                        premiumProductDetails.getOneTimePurchaseOfferDetails();
                if (offer != null && listener != null) {
                    String price = offer.getFormattedPrice();
                    activity.runOnUiThread(() -> listener.onPriceLoaded(price));
                }
            }
        });
    }

    private void queryExistingPurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            boolean unlocked = false;
            for (Purchase purchase : purchases) {
                if (purchase.getProducts().contains(PREMIUM_PRODUCT_ID)
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    unlocked = true;
                    if (!purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase);
                    }
                }
            }
            setUnlocked(unlocked);
        });
    }

    public void launchPurchaseFlow() {
        if (premiumProductDetails == null) {
            if (listener != null) {
                listener.onPurchaseError("Store connection not ready yet. Try again in a moment.");
            }
            return;
        }
        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(premiumProductDetails)
                        .build();
        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();
        activity.runOnUiThread(() -> billingClient.launchBillingFlow(activity, flowParams));
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        int code = billingResult.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase.getProducts().contains(PREMIUM_PRODUCT_ID)
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    setUnlocked(true);
                    if (!purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase);
                    }
                }
            }
        } else if (code != BillingClient.BillingResponseCode.USER_CANCELED && listener != null) {
            activity.runOnUiThread(() -> listener.onPurchaseError("Purchase could not be completed."));
        }
    }

    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params, billingResult -> { });
    }

    private void setUnlocked(boolean unlocked) {
        prefs.edit().putBoolean(PREF_UNLOCKED, unlocked).apply();
        if (listener != null) {
            activity.runOnUiThread(() -> listener.onPremiumStateChanged(unlocked));
        }
    }

    public void destroy() {
        if (billingClient.isReady()) {
            billingClient.endConnection();
        }
    }
}
