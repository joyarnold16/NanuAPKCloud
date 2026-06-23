package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public market discovery only. Wallet secrets are never used here. */
public final class DexMarketClient {
    public interface Callback { void done(List<DexCandidate> candidates, String status); }

    private static final String API = "https://api.dexscreener.com";
    private static final int MAX_ADDRESSES = 30;

    private DexMarketClient() {}

    public static void discover(final DexAppStore store, final Callback callback) {
        new Thread(() -> {
            try {
                List<String> addresses = new ArrayList<>();

                // Endpoint 1: Latest token profiles (promoted tokens)
                collectAddresses(API + "/token-profiles/latest/v1", store.activeChain,
                        addresses, MAX_ADDRESSES);

                // Endpoint 2: Top boosted tokens — catches BNB when profiles are sparse
                if (addresses.size() < 5) {
                    collectAddresses(API + "/token-boosts/top/v1", store.activeChain,
                            addresses, MAX_ADDRESSES);
                }

                // Endpoint 3: Currently active boosts — freshest activity
                if (addresses.size() < 5) {
                    collectAddresses(API + "/token-boosts/active/v1", store.activeChain,
                            addresses, MAX_ADDRESSES);
                }

                if (addresses.isEmpty()) {
                    callback.done(new ArrayList<>(),
                            "No " + store.activeChainLabel() +
                            " tokens on DEX Screener right now. Try again in a few minutes.");
                    return;
                }

                // Fetch pair data for every collected address
                Map<String, DexCandidate> dedupe = new LinkedHashMap<>();
                for (String address : addresses) {
                    try {
                        JSONObject response = new JSONObject(
                                get(API + "/latest/dex/tokens/" + address));
                        JSONArray pairs = response.optJSONArray("pairs");
                        if (pairs == null) continue;
                        for (int i = 0; i < pairs.length(); i++) {
                            DexCandidate c = parse(pairs.optJSONObject(i), store.activeChain);
                            if (c == null || dedupe.containsKey(c.tokenAddress)) continue;
                            DexSafetyPolicy.Report report = DexSafetyPolicy.evaluate(
                                    c, store.minLiquidityUsd,
                                    store.minVolumeUsd, store.minPairAgeHours);
                            c.riskScore  = report.score;
                            c.decision   = report.decision;
                            c.reason     = report.reason;
                            dedupe.put(c.tokenAddress, c);
                        }
                    } catch (Exception ignored) {
                        // One bad address should not abort the whole scan
                    }
                }

                List<DexCandidate> out = new ArrayList<>(dedupe.values());
                String status = out.isEmpty()
                        ? "Scanned " + addresses.size() + " " + store.activeChainLabel()
                          + " tokens — all blocked by safety policy."
                        : "Found " + out.size() + " candidate(s) from "
                          + addresses.size() + " " + store.activeChainLabel() + " tokens screened.";
                callback.done(out, status);

            } catch (Exception e) {
                callback.done(new ArrayList<>(), "Scan error: " + readable(e));
            }
        }, "nanu-dex-discovery").start();
    }

    /** Pulls tokenAddress entries matching chain from a /token-profiles or /token-boosts endpoint. */
    private static void collectAddresses(String url, String chain,
                                         List<String> out, int max) {
        try {
            JSONArray arr = new JSONArray(get(url));
            for (int i = 0; i < arr.length() && out.size() < max; i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                if (!chain.equals(item.optString("chainId", ""))) continue;
                String addr = item.optString("tokenAddress", "");
                if (!addr.isEmpty() && !out.contains(addr)) out.add(addr);
            }
        } catch (Exception ignored) {}
    }

    private static DexCandidate parse(JSONObject pair, String chain) {
        if (pair == null || !chain.equals(pair.optString("chainId", ""))) return null;
        JSONObject base     = pair.optJSONObject("baseToken");
        JSONObject liq      = pair.optJSONObject("liquidity");
        JSONObject vol      = pair.optJSONObject("volume");
        JSONObject changes  = pair.optJSONObject("priceChange");
        JSONObject txns     = pair.optJSONObject("txns");
        JSONObject h24      = txns == null ? null : txns.optJSONObject("h24");
        if (base == null || liq == null) return null;

        DexCandidate c       = new DexCandidate();
        c.chain              = chain;
        c.tokenAddress       = base.optString("address", "");
        c.symbol             = base.optString("symbol", "TOKEN");
        c.name               = base.optString("name", "");
        c.pairAddress        = pair.optString("pairAddress", "");
        c.dexId              = pair.optString("dexId", "");
        c.priceUsd           = number(pair, "priceUsd");
        c.liquidityUsd       = liq.optDouble("usd", 0d);
        c.volume24hUsd       = vol == null ? 0d : vol.optDouble("h24", 0d);
        c.change1h           = changes == null ? 0d : changes.optDouble("h1", 0d);
        c.change24h          = changes == null ? 0d : changes.optDouble("h24", 0d);
        c.buys24h            = h24 == null ? 0 : h24.optInt("buys", 0);
        c.sells24h           = h24 == null ? 0 : h24.optInt("sells", 0);
        c.pairCreatedAtMs    = pair.optLong("pairCreatedAt", 0L);
        c.sourceUrl          = pair.optString("url", "");
        return c;
    }

    private static double number(JSONObject obj, String key) {
        String raw = obj.optString(key, "");
        try { return Double.parseDouble(raw); } catch (Exception e) { return obj.optDouble(key, 0d); }
    }

    private static String get(String rawUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(rawUrl).openConnection();
        conn.setConnectTimeout(14_000);
        conn.setReadTimeout(14_000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NanuDexSafety/10.0");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line);
        } finally { conn.disconnect(); }
        return out.toString();
    }

    private static String readable(Exception e) {
        String v = e.getMessage();
        return v == null || v.trim().isEmpty() ? e.getClass().getSimpleName() : v.trim();
    }
}
