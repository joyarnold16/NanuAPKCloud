package com.joyarnold.rorvisualdeck;

import android.webkit.JavascriptInterface;

/** Exposed to the bundled web app as window.AndroidBilling. */
public final class WebAppBridge {
    private final BillingManager billingManager;

    public WebAppBridge(BillingManager billingManager) {
        this.billingManager = billingManager;
    }

    @JavascriptInterface
    public boolean isPremiumUnlocked() {
        return billingManager.isPremiumUnlocked();
    }

    @JavascriptInterface
    public void purchasePremium() {
        billingManager.launchPurchaseFlow();
    }

    @JavascriptInterface
    public void restorePurchases() {
        billingManager.start();
    }
}
