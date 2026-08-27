package com.joyarnold.rorvisualdeck;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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
    private static final int MAX_PRODUCT_QUERY_ATTEMPTS = 4;

    public interface Listener {
        void onPremiumStateChanged(boolean unlocked);
        void onPriceLoaded(String formattedPrice);
        /**
         * The price could not be fetched. Without this the paywall showed an empty
         * price box forever and gave the buyer nothing to act on.
         */
        void onPriceUnavailable(String reason);
        void onPurchaseError(String message);
    }

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int reconnectDelayMs = 1000;
    private int productQueryAttempts = 0;
    private boolean destroyed = false;
    private final BillingClient billingClient;
    private ProductDetails premiumProductDetails;
    /** Last reason the price could not be loaded, reused if the buyer taps Unlock anyway. */
    private String priceFailureReason;

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
        if (destroyed) return;
        if (billingClient.isReady()) {
            // Already connected. Reconnecting would drop and rebuild a working client;
            // what the caller actually wants (Restore purchase) is a fresh query.
            productQueryAttempts = 0;
            queryProductDetails();
            queryExistingPurchases();
            return;
        }
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                int code = billingResult.getResponseCode();
                if (code == BillingClient.BillingResponseCode.OK) {
                    reconnectDelayMs = 1000;
                    productQueryAttempts = 0;
                    queryProductDetails();
                    queryExistingPurchases();
                    return;
                }
                // Setup failure used to be swallowed entirely: no message, no retry, and a
                // client that stayed dead for the life of the process. The two cases look
                // identical to the buyer (a paywall that never shows a price) but only one
                // is worth retrying, so they are separated here.
                reportPriceUnavailable(describe(code));
                if (isRetryable(code)) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Play drops this connection routinely - the Play Store updating itself
                // is enough. Without reconnecting, the client stays dead for the rest of
                // the process: the paywall never gets a price, purchases cannot launch,
                // and an existing entitlement is never restored. Backs off to a minute so
                // a genuinely unavailable service is not hammered.
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (destroyed) return;
        final int delay = reconnectDelayMs;
        reconnectDelayMs = Math.min(reconnectDelayMs * 2, 60000);
        handler.postDelayed(() -> {
            if (!destroyed && !billingClient.isReady()) {
                start();
            }
        }, delay);
    }

    private void queryProductDetails() {
        productQueryAttempts++;
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();
        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            int code = billingResult.getResponseCode();
            if (code == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                premiumProductDetails = productDetailsList.get(0);
                priceFailureReason = null;
                ProductDetails.OneTimePurchaseOfferDetails offer =
                        premiumProductDetails.getOneTimePurchaseOfferDetails();
                if (offer != null && listener != null) {
                    String price = offer.getFormattedPrice();
                    activity.runOnUiThread(() -> listener.onPriceLoaded(price));
                }
                return;
            }
            // An OK response with an empty list means Play has no such product for this
            // account: the product ID does not match Play Console, the product is not
            // active, or the release has not propagated yet. That is not retryable and
            // used to fail completely silently.
            if (code == BillingClient.BillingResponseCode.OK) {
                reportPriceUnavailable("Premium is not available on your account yet. "
                        + "If the app was just updated, this usually clears within a few hours.");
                return;
            }
            reportPriceUnavailable(describe(code));
            if (isRetryable(code) && productQueryAttempts < MAX_PRODUCT_QUERY_ATTEMPTS) {
                handler.postDelayed(() -> {
                    if (!destroyed && billingClient.isReady()) queryProductDetails();
                }, 2000L * productQueryAttempts);
            }
        });
    }

    private void queryExistingPurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            // Only trust an OK result. A failed query returns an empty list, and
            // treating that as "no purchase" would revoke a paying customer's unlock
            // on any transient network or service error.
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                return;
            }
            boolean unlocked = false;
            boolean pending = false;
            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) continue;
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    unlocked = true;
                    if (!purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase);
                    }
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    pending = true;
                }
            }
            setUnlocked(unlocked);
            if (!unlocked && pending && listener != null) {
                activity.runOnUiThread(() -> listener.onPurchaseError(
                        "Your purchase is still pending payment. It will unlock automatically once Google confirms it."));
            }
        });
    }

    public void launchPurchaseFlow() {
        if (premiumProductDetails == null) {
            if (listener != null) {
                // "Try again in a moment" was wrong for every cause except a genuinely
                // slow connection, and sent buyers chasing a network problem that was
                // really a store configuration or non-Play install.
                String reason = priceFailureReason != null
                        ? priceFailureReason
                        : "Still connecting to Google Play. Try again in a moment.";
                listener.onPurchaseError(reason);
            }
            if (!billingClient.isReady()) {
                start();
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
                if (!purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) continue;
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    setUnlocked(true);
                    if (!purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase);
                    }
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING
                        && listener != null) {
                    // Cash, bank transfer and parental-approval payments land here. Saying
                    // nothing looks to the buyer like the payment silently failed.
                    activity.runOnUiThread(() -> listener.onPurchaseError(
                            "Payment started but is awaiting confirmation. Premium unlocks automatically once Google completes it."));
                }
            }
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            // Play refuses to sell a non-consumable twice, which is exactly what happens
            // after a reinstall on the same account. Showing "purchase could not be
            // completed" to someone who has already paid is the worst version of this;
            // re-querying restores the entitlement they are entitled to.
            queryExistingPurchases();
        } else if (code != BillingClient.BillingResponseCode.USER_CANCELED && listener != null) {
            activity.runOnUiThread(() -> listener.onPurchaseError(describe(code)));
        }
    }

    /** Turns a billing response code into something a buyer can act on. */
    private static String describe(int code) {
        if (code == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
                || code == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED) {
            return "Google Play billing is not available on this device. Premium can only be "
                    + "purchased in the version installed from Google Play, with the Play Store "
                    + "signed in and up to date.";
        }
        if (code == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) {
            return "Premium is not available on your account yet. If the app was just updated, "
                    + "this usually clears within a few hours.";
        }
        if (code == BillingClient.BillingResponseCode.NETWORK_ERROR
                || code == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
            return "Could not reach Google Play. Check your connection and try again.";
        }
        if (code == BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
            return "Store configuration problem — premium cannot be purchased right now.";
        }
        if (code == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
            return "No previous purchase found on this Google account.";
        }
        return "Google Play could not complete this request. Please try again.";
    }

    /** Whether retrying stands any chance, as opposed to needing the store fixed. */
    private static boolean isRetryable(int code) {
        return code == BillingClient.BillingResponseCode.NETWORK_ERROR
                || code == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
                || code == BillingClient.BillingResponseCode.ERROR
                || code < 0; // service disconnected / timed out
    }

    private void reportPriceUnavailable(String reason) {
        priceFailureReason = reason;
        if (listener != null) {
            activity.runOnUiThread(() -> listener.onPriceUnavailable(reason));
        }
    }

    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params, billingResult -> {
            // Google refunds a purchase that is not acknowledged within three days, so a
            // silent failure here costs the sale. Retries once shortly after.
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK && !destroyed) {
                handler.postDelayed(() -> {
                    if (!destroyed && billingClient.isReady()) {
                        billingClient.acknowledgePurchase(params, r -> { });
                    }
                }, 5000);
            }
        });
    }

    private void setUnlocked(boolean unlocked) {
        prefs.edit().putBoolean(PREF_UNLOCKED, unlocked).apply();
        if (listener != null) {
            activity.runOnUiThread(() -> listener.onPremiumStateChanged(unlocked));
        }
    }

    public void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (billingClient.isReady()) {
            billingClient.endConnection();
        }
    }
}
