package com.joyarnold.rorvisualdeck;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONObject;

public final class MainActivity extends Activity implements BillingManager.Listener {
    private WebView webView;
    private BillingManager billingManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.rgb(7, 17, 31));
        getWindow().setNavigationBarColor(Color.rgb(7, 17, 31));

        webView = new WebView(this);
        setContentView(webView);

        // Android 15+ (targetSdk 35) forces edge-to-edge, which would otherwise draw
        // the WebView's content under the status bar and gesture nav bar. Pad the
        // WebView itself by the system bar insets so its rendered viewport avoids them.
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

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
