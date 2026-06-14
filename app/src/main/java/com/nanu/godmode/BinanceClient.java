package com.nanu.godmode;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BinanceClient {
    private static final String TAG = "NanuBinance";
    private final AppConfig cfg;

    public BinanceClient(AppConfig cfg) { this.cfg = cfg; }

    public String baseUrl() {
        String mode = cfg.mode();
        if ("testnet".equals(mode)) return "https://testnet.binance.vision";
        if ("demo".equals(mode)) return "https://demo-api.binance.com";
        return "https://api.binance.com";
    }

    public List<Candle> getKlines(String symbol, String interval, int limit) throws Exception {
        String path = "/api/v3/klines?symbol=" + enc(symbol) + "&interval=" + enc(interval) + "&limit=" + limit;
        String body = get(baseUrl() + path, false);
        JSONArray arr = new JSONArray(body);
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray k = arr.getJSONArray(i);
            out.add(new Candle(
                    k.getLong(0),
                    Double.parseDouble(k.getString(1)),
                    Double.parseDouble(k.getString(2)),
                    Double.parseDouble(k.getString(3)),
                    Double.parseDouble(k.getString(4)),
                    Double.parseDouble(k.getString(5))
            ));
        }
        return out;
    }

    public double getPrice(String symbol) throws Exception {
        String body = get(baseUrl() + "/api/v3/ticker/price?symbol=" + enc(symbol), false);
        JSONObject obj = new JSONObject(body);
        return Double.parseDouble(obj.getString("price"));
    }

    public String placeMarketOrder(String symbol, String side, double qty) throws Exception {
        String mode = cfg.mode();
        if ("live".equals(mode) && (!cfg.liveUnlocked() || !cfg.realOrdersEnabled())) {
            throw new IllegalStateException("Live trading is locked. Enable live_unlocked and real_orders_enabled inside Security.");
        }
        if (cfg.apiKey().isEmpty() || cfg.apiSecret().isEmpty()) {
            throw new IllegalStateException("API key/secret missing.");
        }
        Map<String, String> params = new TreeMap<>();
        params.put("symbol", symbol);
        params.put("side", side.toUpperCase(Locale.US));
        params.put("type", "MARKET");
        params.put("quantity", trimQty(qty));
        params.put("recvWindow", "5000");
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String query = query(params);
        String sig = hmacSha256(query, cfg.apiSecret());
        return post(baseUrl() + "/api/v3/order", query + "&signature=" + sig);
    }

    private String get(String urlText, boolean signed) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlText).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(12000);
        con.setRequestMethod("GET");
        if (signed && !cfg.apiKey().isEmpty()) con.setRequestProperty("X-MBX-APIKEY", cfg.apiKey());
        return read(con);
    }

    private String post(String urlText, String query) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlText).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(12000);
        con.setRequestMethod("POST");
        con.setRequestProperty("X-MBX-APIKEY", cfg.apiKey());
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setDoOutput(true);
        try (OutputStream os = con.getOutputStream()) {
            os.write(query.getBytes(StandardCharsets.UTF_8));
        }
        return read(con);
    }

    private String read(HttpURLConnection con) throws Exception {
        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        if (code < 200 || code >= 300) {
            Log.e(TAG, "HTTP " + code + " " + sb);
            throw new RuntimeException("Binance HTTP " + code + ": " + sb);
        }
        return sb.toString();
    }

    private String query(Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String trimQty(double q) {
        String s = String.format(Locale.US, "%.8f", q);
        while (s.contains(".") && s.endsWith("0")) s = s.substring(0, s.length()-1);
        if (s.endsWith(".")) s = s.substring(0, s.length()-1);
        return s;
    }
}
