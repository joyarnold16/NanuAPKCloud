package com.nanu.aitradingbot;

import org.json.JSONObject;

public final class TradeRecord {
    public String symbol = "";
    public String chain = "";
    public double entryPrice;
    public double exitPrice;
    public double pnlUsd;
    public double pnlPct;
    public boolean win;
    public String exitReason = "";
    public long openedAtMs;
    public long closedAtMs;
    // Token metrics at entry — used by ML evolution
    public double liquidityUsd;
    public double volume24hUsd;
    public long pairAgeMs;
    public double change1h;
    public double change24h;
    public int riskScore;

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("sy", symbol); o.put("ch", chain);
            o.put("ep", entryPrice); o.put("xp", exitPrice);
            o.put("pu", pnlUsd); o.put("pp", pnlPct);
            o.put("w", win); o.put("xr", exitReason);
            o.put("oa", openedAtMs); o.put("ca", closedAtMs);
            o.put("lq", liquidityUsd); o.put("v2", volume24hUsd);
            o.put("pa", pairAgeMs); o.put("c1", change1h);
            o.put("c2", change24h); o.put("rs", riskScore);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    public static TradeRecord fromJson(String json) {
        TradeRecord r = new TradeRecord();
        try {
            JSONObject o = new JSONObject(json);
            r.symbol = o.optString("sy", ""); r.chain = o.optString("ch", "");
            r.entryPrice = o.optDouble("ep", 0); r.exitPrice = o.optDouble("xp", 0);
            r.pnlUsd = o.optDouble("pu", 0); r.pnlPct = o.optDouble("pp", 0);
            r.win = o.optBoolean("w", false); r.exitReason = o.optString("xr", "");
            r.openedAtMs = o.optLong("oa", 0); r.closedAtMs = o.optLong("ca", 0);
            r.liquidityUsd = o.optDouble("lq", 0); r.volume24hUsd = o.optDouble("v2", 0);
            r.pairAgeMs = o.optLong("pa", 0); r.change1h = o.optDouble("c1", 0);
            r.change24h = o.optDouble("c2", 0); r.riskScore = o.optInt("rs", 0);
        } catch (Exception ignored) {}
        return r;
    }
}
