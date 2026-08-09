package com.joyarnold.rorvisualdeck;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONObject;

public final class MainActivity extends Activity implements BillingManager.Listener {
    private WebView webView;
    private BillingManager billingManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(7, 17, 31));
        getWindow().setNavigationBarColor(Color.rgb(7, 17, 31));

        webView = new WebView(this);

        // The window is left in its default "decor fits system windows" mode, so on
        // Android 14 and below - and on Android 15 via windowOptOutEdgeToEdgeEnforcement
        // in values-v35 - the system insets the content and the page never draws under
        // the status bar or gesture nav.
        //
        // The listener below is the fallback for platforms that force edge-to-edge
        // regardless (Android 16+, where the opt-out is gone). When the system has
        // already inset the content, the insets that reach this container are zero and
        // the padding is a no-op, so the two mechanisms cannot double up. The padding is
        // applied to a container rather than to the WebView because WebView ignores its
        // own padding when laying out web content.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 17, 31));
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setBackgroundColor(Color.rgb(7, 17, 31));

        billingManager = new BillingManager(this, this);
        webView.addJavascriptInterface(new WebAppBridge(billingManager), "AndroidBilling");
        billingManager.start();

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onPremiumStateChanged(boolean unlocked) {
        runOnJs("window.onPremiumStateChanged && window.onPremiumStateChanged(" + unlocked + ")");
    }

    @Override
    public void onPriceLoaded(String formattedPrice) {
        runOnJs("window.onPremiumPriceLoaded && window.onPremiumPriceLoaded("
                + JSONObject.quote(formattedPrice) + ")");
    }

    @Override
    public void onPurchaseError(String message) {
        runOnJs("window.onPremiumPurchaseError && window.onPremiumPurchaseError("
                + JSONObject.quote(message) + ")");
    }

    private void runOnJs(String script) {
        if (webView != null) {
            webView.evaluateJavascript(script, null);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (billingManager != null) {
            billingManager.destroy();
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
