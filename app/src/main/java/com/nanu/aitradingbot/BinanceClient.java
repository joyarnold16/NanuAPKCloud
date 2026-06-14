package com.nanu.aitradingbot;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BinanceClient {
    public interface Callback { void done(String result); }

    public static String baseUrl(String mode) {
        if ("testnet".equalsIgnoreCase(mode)) return "https://testnet.binance.vision";
        if ("demo".equalsIgnoreCase(mode)) return "https://demo-api.binance.com";
        return "https://api.binance.com";
    }

    public static void testApi(final AppStore store, final Callback cb) {
        new Thread(() -> {
            StringBuilder out = new StringBuilder();
            try {
                String mode = store.mode;
                String base = baseUrl(mode);
                out.append("Mode: ").append(mode.toUpperCase(Locale.US)).append("\n");
                out.append("Endpoint: ").append(base).append("\n\n");
                out.append("Internet / Binance ping: ").append(httpGet(base + "/api/v3/ping", null, 6000).ok ? "OK ✅" : "FAILED ❌").append("\n");
                HttpResult time = httpGet(base + "/api/v3/time", null, 6000);
                out.append("Server time: ").append(time.ok ? "OK ✅" : "FAILED ❌").append("\n");

                if (store.apiKey.trim().isEmpty() || store.apiSecret.trim().isEmpty()) {
                    out.append("\nPrivate API: SKIPPED ⚠️\nAdd API key and secret in Security screen for balance/order permission checks.\n");
                    cb.done(out.toString());
                    return;
                }

                long ts = System.currentTimeMillis();
                String query = "timestamp=" + ts + "&recvWindow=5000";
                String sig = hmacSha256(store.apiSecret, query);
                String url = base + "/api/v3/account?" + query + "&signature=" + URLEncoder.encode(sig, "UTF-8");
                HttpResult account = httpGet(url, store.apiKey, 8000);
                out.append("Signature test: ").append(account.ok ? "VALID ✅" : "FAILED ❌").append("\n");
                out.append("Balance access: ").append(account.ok ? "OK ✅" : "FAILED ❌").append("\n");
                if (!account.ok) {
                    out.append("\nPrivate API error:\n").append(account.body.length() > 500 ? account.body.substring(0, 500) : account.body).append("\n");
                    out.append("\nCheck API permission, testnet/live key mismatch, device clock, and secret key.\n");
                } else {
                    out.append("Trading permission: key accepted. Keep withdrawal permission OFF. ✅\n");
                }
            } catch (Exception e) {
                out.append("API check failed: ").append(e.getClass().getSimpleName()).append(" - ").append(e.getMessage()).append("\n");
            }
            cb.done(out.toString());
        }).start();
    }

    static class HttpResult {
        boolean ok;
        String body;
        int code;
    }

    static HttpResult httpGet(String raw, String apiKey, int timeoutMs) throws Exception {
        URL url = new URL(raw);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(timeoutMs);
        con.setReadTimeout(timeoutMs);
        if (apiKey != null && apiKey.trim().length() > 0) con.setRequestProperty("X-MBX-APIKEY", apiKey.trim());
        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String body = readAll(is);
        HttpResult r = new HttpResult();
        r.code = code;
        r.ok = code >= 200 && code < 300;
        r.body = body == null ? "" : body;
        return r;
    }

    static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    static String hmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
