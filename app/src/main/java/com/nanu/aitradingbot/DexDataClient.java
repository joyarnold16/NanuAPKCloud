package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only public providers. No secrets, signatures, orders or RPC transaction methods. */
public final class DexDataClient {
    public interface Transport { String get(String url) throws Exception; }
    private final Transport transport;
    public DexDataClient() { this(DexDataClient::get); }
    public DexDataClient(Transport transport) { this.transport = transport; }
    private static final String DEX = "https://api.dexscreener.com";

    public List<DexCandidate> discover(double liquidity, double volume, int age) throws Exception {
        JSONArray profiles = new JSONArray(transport.get(DEX + "/token-profiles/latest/v1"));
        List<DexCandidate> out = new ArrayList<>(); java.util.Set<String> seen = new java.util.HashSet<>();
        int fetched = 0, enriched = 0;
        for (int i = 0; i < profiles.length() && fetched < 12; i++) {
            JSONObject p = profiles.getJSONObject(i); String chain = p.optString("chainId"), token = p.optString("tokenAddress");
            if (!DexSafetyPolicy.validAddress(chain, token) || !seen.add(chain + ":" + token)) continue;
            fetched++;
            try {
                JSONArray pairs = new JSONArray(transport.get(DEX + "/token-pairs/v1/" + chain + "/" + token));
                DexCandidate best = null;
                for (int j = 0; j < pairs.length(); j++) {
                    DexCandidate c = parsePair(pairs.getJSONObject(j), System.currentTimeMillis());
                    if (c != null && chain.equals(c.chain) && DexSafetyPolicy.sameAddress(chain, token, c.tokenAddress)
                            && (best == null || c.liquidityUsd > best.liquidityUsd)) best = c;
                }
                if (best == null) continue;
                evaluate(best, liquidity, volume, age);
                if (!"BLOCKED".equals(best.decision) && enriched < 3) { enrich(best); enriched++; evaluate(best, liquidity, volume, age); }
                out.add(best);
            } catch (Exception ignored) { /* A failed token never becomes eligible. */ }
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.riskScore, a.riskScore));
        return out;
    }
    public static void evaluate(DexCandidate c, double liquidity, double volume, int age) {
        DexSafetyPolicy.Report r = DexSafetyPolicy.evaluate(c, liquidity, volume, age);
        c.riskScore = r.score; c.decision = r.decision; c.reason = r.reason;
    }
    public DexCandidate pair(String chain, String pair, String token) throws Exception {
        if (!DexSafetyPolicy.validAddress(chain, pair) || !DexSafetyPolicy.validAddress(chain, token))
            throw new IllegalArgumentException("Invalid pair identity");
        JSONObject response = new JSONObject(transport.get(DEX + "/latest/dex/pairs/" + chain + "/" + pair));
        JSONArray pairs = response.getJSONArray("pairs");
        for (int i = 0; i < pairs.length(); i++) {
            DexCandidate c = parsePair(pairs.getJSONObject(i), System.currentTimeMillis());
            if (c != null && chain.equals(c.chain) && DexSafetyPolicy.sameAddress(chain, pair, c.pairAddress)
                    && DexSafetyPolicy.sameAddress(chain, token, c.tokenAddress)) return c;
        }
        throw new IllegalStateException("Selected pair missing or mismatched");
    }
    public void security(DexCandidate c) {
        try {
            String endpoint = c.isBsc() ? "token_security/56" : "solana/token_security";
            c.security = TokenSecurity.parse(c, new JSONObject(transport.get("https://api.gopluslabs.io/api/v1/" + endpoint
                + "?contract_addresses=" + c.tokenAddress)), System.currentTimeMillis());
        } catch (Exception e) { c.security = new TokenSecurity(); }
    }
    public void enrich(DexCandidate c) {
        security(c);
        try { c.candles = candles(c, c.candleIntervalMs); }
        catch (Exception e) { c.candles = new ArrayList<>(); }
    }
    public List<Ohlcv> candles(DexCandidate c, long interval) throws Exception {
        String frame; int aggregate;
        if (interval == 60_000 || interval == 300_000 || interval == 900_000) { frame = "minute"; aggregate = (int) (interval / 60_000); }
        else if (interval == 3_600_000) { frame = "hour"; aggregate = 1; }
        else throw new IllegalArgumentException("Unsupported candle interval");
        if (!DexSafetyPolicy.validAddress(c.chain, c.pairAddress) || !DexSafetyPolicy.validAddress(c.chain, c.tokenAddress))
            throw new IllegalArgumentException("Invalid candle identity");
        // Explicit token address avoids silently analyzing the opposite side of the pool.
        String url = "https://api.geckoterminal.com/api/v2/networks/" + c.chain + "/pools/" + c.pairAddress
            + "/ohlcv/" + frame + "?aggregate=" + aggregate + "&limit=100&currency=usd&token=" + c.tokenAddress;
        JSONArray rows = new JSONObject(transport.get(url)).getJSONObject("data").getJSONObject("attributes").getJSONArray("ohlcv_list");
        return parseCandles(rows, interval, System.currentTimeMillis());
    }
    public static List<Ohlcv> parseCandles(JSONArray rows, long interval, long now) throws Exception {
        List<Ohlcv> out = new ArrayList<>();
        for (int i = rows.length() - 1; i >= 0; i--) {
            JSONArray row = rows.getJSONArray(i);
            if (row.length() != 6) throw new IllegalArgumentException("Malformed candle");
            long seconds = row.getLong(0);
            if (seconds <= 0 || seconds > Long.MAX_VALUE / 1000) throw new IllegalArgumentException("Invalid candle timestamp");
            long time = seconds * 1000L;
            if (time > now) throw new IllegalArgumentException("Future candle");
            if (time > now - interval) continue; // Exclude the still forming candle.
            out.add(new Ohlcv(time, row.getDouble(1), row.getDouble(2), row.getDouble(3), row.getDouble(4), row.getDouble(5)));
        }
        if (!Ohlcv.valid(out, interval, now, 60)) throw new IllegalArgumentException("Gapped, stale, unordered or short OHLCV history");
        return out;
    }
    public static DexCandidate parsePair(JSONObject pair, long now) throws Exception {
        String chain = pair.optString("chainId");
        JSONObject base = pair.getJSONObject("baseToken");
        if (!DexSafetyPolicy.validAddress(chain, base.optString("address")) || !DexSafetyPolicy.validAddress(chain, pair.optString("pairAddress"))) return null;
        DexCandidate c = new DexCandidate(); c.chain = chain; c.tokenAddress = base.getString("address");
        c.symbol = base.optString("symbol", "TOKEN"); c.name = base.optString("name");
        c.pairAddress = pair.getString("pairAddress"); c.dexId = pair.optString("dexId");
        c.priceUsd = pair.getDouble("priceUsd"); c.liquidityUsd = pair.getJSONObject("liquidity").getDouble("usd");
        c.volume24hUsd = pair.getJSONObject("volume").getDouble("h24");
        c.change1h = pair.getJSONObject("priceChange").optDouble("h1", Double.NaN);
        c.change24h = pair.getJSONObject("priceChange").optDouble("h24", Double.NaN);
        JSONObject tx = pair.getJSONObject("txns").getJSONObject("h24"); c.buys24h = tx.getInt("buys"); c.sells24h = tx.getInt("sells");
        c.pairCreatedAtMs = pair.optLong("pairCreatedAt", 0); c.sourceUrl = pair.optString("url");
        c.observedAtMs = now;
        if (!Ohlcv.positive(c.priceUsd) || !Ohlcv.positive(c.liquidityUsd)) throw new IllegalArgumentException("Invalid quote");
        return c;
    }
    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000); conn.setReadTimeout(8000); conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept", "application/json"); conn.setRequestProperty("User-Agent", "NanuPaper/11");
        try {
            if (conn.getResponseCode() != 200) throw new IllegalStateException("Provider HTTP " + conn.getResponseCode());
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                char[] chunk = new char[4096]; int count;
                while ((count = reader.read(chunk)) != -1) {
                    result.append(chunk, 0, count);
                    if (result.length() > 2_000_000) throw new IllegalStateException("Provider response too large");
                }
            }
            return result.toString();
        } finally { conn.disconnect(); }
    }
}
