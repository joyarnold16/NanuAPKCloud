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

/** Public market discovery only. Wallet secrets are never used in requests made by this client. */
public final class DexMarketClient {
    public interface Callback { void done(List<DexCandidate> candidates, String status); }
    private static final String API = "https://api.dexscreener.com";

    private DexMarketClient() {}

    public static void discover(final DexAppStore store, final Callback callback) {
        new Thread(() -> {
            try {
                JSONArray profiles = new JSONArray(get(API + "/token-profiles/latest/v1"));
                List<String> addresses = new ArrayList<>();
                for (int i = 0; i < profiles.length() && addresses.size() < 8; i++) {
                    JSONObject item = profiles.optJSONObject(i);
                    if (item == null) continue;
                    String chain = item.optString("chainId", "");
                    if (!chain.equals(store.activeChain)) continue;
                    String address = item.optString("tokenAddress", "");
                    if (!address.isEmpty()) addresses.add(address);
                }
                if (addresses.isEmpty()) {
                    callback.done(new ArrayList<>(), "No recent " + store.activeChainLabel() + " profiles returned by DEX Screener.");
                    return;
                }

                Map<String, DexCandidate> dedupe = new LinkedHashMap<>();
                for (String address : addresses) {
                    JSONObject response = new JSONObject(get(API + "/latest/dex/tokens/" + address));
                    JSONArray pairs = response.optJSONArray("pairs");
                    if (pairs == null) continue;
                    for (int i = 0; i < pairs.length(); i++) {
                        DexCandidate candidate = parse(pairs.optJSONObject(i), store.activeChain);
                        if (candidate == null || dedupe.containsKey(candidate.tokenAddress)) continue;
                        DexSafetyPolicy.Report report = DexSafetyPolicy.evaluate(candidate, store.minLiquidityUsd, store.minVolumeUsd, store.minPairAgeHours);
                        candidate.riskScore = report.score;
                        candidate.decision = report.decision;
                        candidate.reason = report.reason;
                        dedupe.put(candidate.tokenAddress, candidate);
                    }
                }
                List<DexCandidate> out = new ArrayList<>(dedupe.values());
                callback.done(out, out.isEmpty() ? "No usable pairs returned. The scanner made no trade decision." : "DEX Screener scan complete. Every candidate still requires local risk checks.");
            } catch (Exception e) {
                callback.done(new ArrayList<>(), "DEX Screener scan failed: " + readable(e));
            }
        }, "nanu-dex-discovery").start();
    }

    private static DexCandidate parse(JSONObject pair, String chain) {
        if (pair == null || !chain.equals(pair.optString("chainId", ""))) return null;
        JSONObject base = pair.optJSONObject("baseToken");
        JSONObject liquidity = pair.optJSONObject("liquidity");
        JSONObject volume = pair.optJSONObject("volume");
        JSONObject changes = pair.optJSONObject("priceChange");
        JSONObject txns = pair.optJSONObject("txns");
        JSONObject h24 = txns == null ? null : txns.optJSONObject("h24");
        if (base == null || liquidity == null) return null;
        DexCandidate c = new DexCandidate();
        c.chain = chain;
        c.tokenAddress = base.optString("address", "");
        c.symbol = base.optString("symbol", "TOKEN");
        c.name = base.optString("name", "");
        c.pairAddress = pair.optString("pairAddress", "");
        c.dexId = pair.optString("dexId", "");
        c.priceUsd = number(pair, "priceUsd");
        c.liquidityUsd = liquidity.optDouble("usd", 0d);
        c.volume24hUsd = volume == null ? 0d : volume.optDouble("h24", 0d);
        c.change1h = changes == null ? 0d : changes.optDouble("h1", 0d);
        c.change24h = changes == null ? 0d : changes.optDouble("h24", 0d);
        c.buys24h = h24 == null ? 0 : h24.optInt("buys", 0);
        c.sells24h = h24 == null ? 0 : h24.optInt("sells", 0);
        c.pairCreatedAtMs = pair.optLong("pairCreatedAt", 0L);
        c.sourceUrl = pair.optString("url", "");
        return c;
    }

    private static double number(JSONObject object, String name) {
        String raw = object.optString(name, "");
        try { return Double.parseDouble(raw); } catch (Exception ignored) { return object.optDouble(name, 0d); }
    }

    private static String get(String rawUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(12_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NanuDexSafety/8.0");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        } finally { connection.disconnect(); }
        return out.toString();
    }

    private static String readable(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value.trim();
    }
}
