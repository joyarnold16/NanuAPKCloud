package com.nanu.aitradingbot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BinanceClient {
    public interface Callback { void done(String result); }
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();

    public static void testApi(AppStore store, Callback cb) {
        exec.execute(() -> {
            StringBuilder out = new StringBuilder();
            try {
                String base = baseUrl(store.mode);
                out.append("Mode: ").append(store.mode.toUpperCase(Locale.US)).append('\n');
                out.append("Base URL: ").append(base).append('\n').append('\n');
                long serverTime = serverTime(base);
                out.append("Internet / Binance time: OK ✅\n");
                out.append("Server time: ").append(serverTime).append('\n');
                if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) {
                    out.append("\nPrivate API test skipped. Add API key and secret first.\n");
                    out.append("Public market connection is alive.\n");
                    cb.done(out.toString());
                    return;
                }
                String query = "timestamp=" + serverTime + "&recvWindow=5000";
                String sig = hmac(query, store.apiSecret);
                out.append("Signature generation: OK ✅\n");
                HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/account?" + query + "&signature=" + sig).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("X-MBX-APIKEY", store.apiKey);
                int code = c.getResponseCode();
                out.append("Account endpoint HTTP: ").append(code).append('\n');
                if (code == 200) out.append("Balance access: OK ✅\nTrading permission: check Binance key permission screen.\n");
                else out.append("Private API failed. Check key, secret, mode, timestamp and permissions.\n").append(readError(c));
            } catch (Exception e) {
                out.append("API Health Check Failed ❌\n").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
            }
            cb.done(out.toString());
        });
    }

    private static String baseUrl(String mode) {
        if ("testnet".equals(mode)) return "https://testnet.binance.vision";
        if ("demo".equals(mode)) return "https://demo-api.binance.com";
        return "https://api.binance.com";
    }

    private static long serverTime(String base) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/time").openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(10000);
        String body = read(c);
        int i = body.indexOf("serverTime");
        if (i < 0) return System.currentTimeMillis();
        String digits = body.substring(i).replaceAll("[^0-9]", "");
        if (digits.length() > 13) digits = digits.substring(0, 13);
        return Long.parseLong(digits);
    }

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String readError(HttpURLConnection c) {
        try { return read(c); } catch (Exception e) { return ""; }
    }
    private static String read(HttpURLConnection c) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
