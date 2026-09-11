package com.nanu.aitradingbot;

import org.json.JSONObject;

public final class TradeRecord {
    public String symbol = "", chain = "", exitReason = "", positionId = "", pairAddress = "", tokenAddress = "", executionModel = "legacy";
    public double quantity, entryQuoteUsd, exitQuoteUsd;
    public double entryPrice, exitPrice, pnlUsd, pnlPct;
    public double liquidityUsd, volume24hUsd, change1h, change24h;
    public boolean win;
    public long openedAtMs, closedAtMs, pairAgeMs;
    public int riskScore;

    public String toJson() {
        try {
            return new JSONObject()
                .put("symbol", symbol).put("chain", chain)
                .put("positionId", positionId).put("pairAddress", pairAddress).put("tokenAddress", tokenAddress)
                .put("executionModel", executionModel).put("quantity", quantity).put("entryQuoteUsd", entryQuoteUsd).put("exitQuoteUsd", exitQuoteUsd)
                .put("entryPrice", entryPrice).put("exitPrice", exitPrice)
                .put("pnlUsd", pnlUsd).put("pnlPct", pnlPct)
                .put("win", win).put("exitReason", exitReason)
                .put("openedAtMs", openedAtMs).put("closedAtMs", closedAtMs)
                .put("liquidityUsd", liquidityUsd).put("volume24hUsd", volume24hUsd)
                .put("pairAgeMs", pairAgeMs).put("change1h", change1h)
                .put("change24h", change24h).put("riskScore", riskScore)
                .toString();
        } catch (Exception e) { return "{}"; }
    }

    public static TradeRecord fromJson(String json) {
        TradeRecord r = new TradeRecord();
        try {
            JSONObject o = new JSONObject(json);
            r.positionId = o.optString("positionId", ""); r.pairAddress = o.optString("pairAddress", ""); r.tokenAddress = o.optString("tokenAddress", "");
            r.executionModel = o.optString("executionModel", "legacy"); r.quantity = o.optDouble("quantity", 0);
            r.entryQuoteUsd = o.optDouble("entryQuoteUsd", 0); r.exitQuoteUsd = o.optDouble("exitQuoteUsd", 0);
            r.symbol = o.optString("symbol"); r.chain = o.optString("chain");
            r.entryPrice = o.optDouble("entryPrice"); r.exitPrice = o.optDouble("exitPrice");
            r.pnlUsd = o.optDouble("pnlUsd"); r.pnlPct = o.optDouble("pnlPct");
            r.win = o.optBoolean("win"); r.exitReason = o.optString("exitReason");
            r.openedAtMs = o.optLong("openedAtMs"); r.closedAtMs = o.optLong("closedAtMs");
            r.liquidityUsd = o.optDouble("liquidityUsd"); r.volume24hUsd = o.optDouble("volume24hUsd");
            r.pairAgeMs = o.optLong("pairAgeMs"); r.change1h = o.optDouble("change1h");
            r.change24h = o.optDouble("change24h"); r.riskScore = o.optInt("riskScore");
        } catch (Exception ignored) {}
        return r;
    }
}
