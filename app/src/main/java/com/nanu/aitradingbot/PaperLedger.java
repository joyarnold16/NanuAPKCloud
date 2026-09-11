package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;

/** Single-writer, transactional paper account shared by Android and the NAS harness. */
public final class PaperLedger {
    public interface Storage {
        String read() throws Exception;
        void write(String snapshot) throws Exception;
    }
    public static final class Limits {
        public double tradeUsd = 10, dailyLossUsd = 5, stopPct = 8, targetPct = 15, slippagePct = 1;
        public double maxExposureUsd = 100, riskPerTradeUsd = 1;
        public int maxTrades = 2, maxConsecutiveLosses = 3;
        public long cooldownMs = 300_000;
        public void validate() {
            if (!range(tradeUsd, 1, 10000) || !range(dailyLossUsd, 0.01, 10000)
                    || !range(stopPct, 0.5, 50) || !range(targetPct, 0.5, 100)
                    || !range(slippagePct, 0.01, 5) || !range(maxExposureUsd, 1, 10000)
                    || !range(riskPerTradeUsd, 0.01, 1000) || maxTrades < 1 || maxTrades > 100
                    || maxConsecutiveLosses < 1 || maxConsecutiveLosses > 10 || cooldownMs < 0)
                throw new IllegalArgumentException("Invalid risk settings");
        }
    }
    private final Storage storage;
    private JSONObject state;
    private boolean failed;
    public PaperLedger(Storage storage) {
        this.storage = storage;
        try {
            String raw = storage.read();
            if (raw == null) {
                state = new JSONObject().put("version", 1).put("day", 0L).put("entries", 0)
                    .put("realized", 0d).put("cash", 1000d).put("losses", 0).put("cooldown", 0L)
                    .put("panic", false).put("dailyHalt", false).put("history", new JSONArray());
                storage.write(state.toString());
            } else { state = new JSONObject(raw); validateState(state); }
        } catch (Exception e) { failed = true; state = new JSONObject(); }
    }
    private static boolean range(double n, double min, double max) { return Ohlcv.finite(n) && n >= min && n <= max; }
    private void validateState(JSONObject s) throws Exception {
        if (s.getInt("version") != 1 || s.getLong("day") < 0 || s.getInt("entries") < 0
                || !Ohlcv.finite(s.getDouble("cash")) || !Ohlcv.finite(s.getDouble("realized"))
                || s.getInt("losses") < 0 || s.getLong("cooldown") < 0) throw new IllegalStateException("Corrupt ledger");
        s.getBoolean("panic"); s.getBoolean("dailyHalt"); s.getJSONArray("history");
        JSONObject p = s.optJSONObject("position");
        if (s.has("position") && p == null) throw new IllegalStateException("Corrupt position");
        if (p != null) {
            for (String key : new String[]{"entryPrice", "markPrice", "quoteAmount", "quantity", "targetPrice", "stopPrice", "liquidity"})
                if (!Ohlcv.positive(p.getDouble(key))) throw new IllegalStateException("Corrupt position " + key);
            if (!DexSafetyPolicy.validAddress(p.getString("chain"), p.getString("tokenAddress"))
                    || !DexSafetyPolicy.validAddress(p.getString("chain"), p.getString("pairAddress"))
                    || !range(p.getDouble("sellTax"), 0, 0.99) || !range(p.getDouble("slippagePct"), 0.01, 5)
                    || !Ohlcv.finite(p.getDouble("pnlUsd")) || p.getLong("openedAtMs") <= 0 || p.getLong("markAtMs") <= 0)
                throw new IllegalStateException("Corrupt position identity");
            p.getString("pendingExit"); p.getString("id");
        }
    }
    private JSONObject copy() throws Exception {
        if (failed) throw new IllegalStateException("Paper storage failed; recovery required");
        return new JSONObject(state.toString());
    }
    private void commit(JSONObject next) throws Exception {
        try { validateState(next); storage.write(next.toString()); state = next; }
        catch (Exception e) { failed = true; throw e; }
    }
    public synchronized boolean healthy() { return !failed; }
    public synchronized boolean halted() { return failed || state.optBoolean("panic") || state.optBoolean("dailyHalt"); }
    public synchronized JSONObject snapshot() { try { return new JSONObject(state.toString()); } catch (Exception e) { throw new IllegalStateException(e); } }
    public synchronized JSONObject position() { return snapshot().optJSONObject("position"); }
    public synchronized void migrate(int entries, double realized, boolean panic, JSONArray history, long now) throws Exception {
        if (state.optBoolean("migrated")) return;
        JSONObject n = copy(); rollDay(n, now);
        n.put("entries", Math.max(entries, n.getInt("entries"))).put("realized", realized)
            .put("panic", panic).put("history", history).put("migrated", true);
        commit(n);
    }
    private static void rollDay(JSONObject s, long now) throws Exception {
        long day = now / 86_400_000L;
        // Clock rollback never resets limits. Overnight unrealized loss remains conservative.
        if (day > s.getLong("day")) s.put("day", day).put("entries", 0).put("realized", 0d).put("dailyHalt", false);
    }
    public synchronized boolean advanceClock(long now) throws Exception {
        JSONObject n = copy();
        if (now / 86_400_000L <= n.getLong("day")) return false;
        rollDay(n, now); commit(n); return true;
    }
    public synchronized void panic() throws Exception {
        JSONObject n = copy(); n.put("panic", true);
        JSONObject p = n.optJSONObject("position");
        if (p != null) p.put("pendingExit", "panic exit");
        commit(n);
    }
    public synchronized void clearPanic() throws Exception {
        if (position() != null) throw new IllegalStateException("Resolve the pending position before clearing Panic");
        JSONObject n = copy(); n.put("panic", false).put("losses", 0); commit(n);
    }
    public synchronized void requestExit(String reason) throws Exception {
        JSONObject n = copy(), p = n.optJSONObject("position");
        if (p != null) { p.put("pendingExit", reason); commit(n); }
    }
    public synchronized String open(DexCandidate c, Limits limits, long now, String mode) throws Exception {
        PaperExecution.requirePaper(mode); limits.validate();
        JSONObject n = copy(); rollDay(n, now);
        if (n.optJSONObject("position") != null) return "Position/exposure limit reached";
        if (n.getBoolean("panic") || n.getBoolean("dailyHalt")) return "Risk halt is latched";
        if (n.getDouble("realized") <= -limits.dailyLossUsd) {
            n.put("dailyHalt", true); commit(n); return "Daily loss limit reached";
        }
        if (n.getInt("entries") >= limits.maxTrades || n.getInt("losses") >= limits.maxConsecutiveLosses
                || now < n.getLong("cooldown")) return "Frequency, loss streak or cooldown limit reached";
        if (!DexSafetyPolicy.canOpenPaperPosition(c, 0.1, now) || !fresh(c, now)) return "Incomplete or stale trade evidence";
        double budget = Math.min(limits.tradeUsd, Math.min(limits.maxExposureUsd, n.getDouble("cash")));
        if (budget < 1) return "Insufficient paper capital";
        double friction = PaperExecution.friction(budget, c.liquidityUsd, c.security.buyTax);
        double sellFriction = PaperExecution.friction(budget, c.liquidityUsd, c.security.sellTax);
        if (Math.max(friction, sellFriction) * 100 > limits.slippagePct) return "Execution friction exceeds slippage cap";
        // Reserve both sides' modeled costs as well as the stop loss. Reduce size until it fits.
        double riskBudget = Math.min(limits.riskPerTradeUsd, limits.dailyLossUsd + Math.min(0, n.getDouble("realized")));
        double qty = 0, stop = c.priceUsd * (1 - limits.stopPct / 100);
        for (int i = 0; i < 100 && budget >= 1; i++) {
            qty = PaperExecution.quantity(budget, c.priceUsd, c.liquidityUsd, c.security.buyTax, c.chain);
            double risk = budget - PaperExecution.proceeds(qty, stop, c.liquidityUsd, c.security.sellTax, c.chain);
            if (risk <= riskBudget) break;
            budget *= 0.9; qty = 0;
        }
        if (budget < 1 || !Ohlcv.positive(qty)) return "Trade cannot fit stop and cost risk budget";
        JSONObject p = new JSONObject().put("id", java.util.UUID.randomUUID().toString())
            .put("chain", c.chain).put("symbol", c.symbol).put("tokenAddress", c.tokenAddress).put("pairAddress", c.pairAddress)
            .put("entryPrice", (budget - PaperExecution.gas(c.chain)) / qty).put("markPrice", c.priceUsd)
            .put("quoteAmount", budget).put("quantity", qty).put("stopPrice", stop)
            .put("targetPrice", c.priceUsd * (1 + limits.targetPct / 100)).put("openedAtMs", now)
            .put("markAtMs", c.observedAtMs).put("liquidity", c.liquidityUsd).put("sellTax", c.security.sellTax)
            .put("slippagePct", limits.slippagePct).put("pendingExit", "")
            .put("pnlUsd", PaperExecution.proceeds(qty, c.priceUsd, c.liquidityUsd, c.security.sellTax, c.chain) - budget);
        n.put("position", p).put("cash", n.getDouble("cash") - budget).put("entries", n.getInt("entries") + 1);
        commit(n); return "Paper position opened with modeled costs";
    }
    static boolean fresh(DexCandidate c, long now) {
        return c != null && Ohlcv.positive(c.priceUsd) && Ohlcv.positive(c.liquidityUsd)
            && c.observedAtMs > 0 && c.observedAtMs <= now && now - c.observedAtMs <= 90_000;
    }
    public synchronized String mark(DexCandidate c, Limits limits, long now) throws Exception {
        limits.validate(); JSONObject n = copy(); rollDay(n, now);
        JSONObject p = n.optJSONObject("position");
        if (p == null) return "No paper position";
        if (!fresh(c, now) || !p.getString("chain").equals(c.chain)
                || !DexSafetyPolicy.sameAddress(c.chain, p.getString("pairAddress"), c.pairAddress)
                || !DexSafetyPolicy.sameAddress(c.chain, p.getString("tokenAddress"), c.tokenAddress)
                || c.observedAtMs < p.getLong("markAtMs")) return "Exit pending: missing, stale or mismatched pair quote";
        p.put("markPrice", c.priceUsd).put("markAtMs", c.observedAtMs);
        double tax = p.getDouble("sellTax");
        if (c.security != null && range(c.security.sellTax, 0, 0.99)) tax = Math.max(tax, c.security.sellTax);
        double gross = p.getDouble("quantity") * c.priceUsd;
        double proceeds = PaperExecution.proceeds(p.getDouble("quantity"), c.priceUsd, c.liquidityUsd, tax, c.chain);
        double pnl = proceeds - p.getDouble("quoteAmount"); p.put("pnlUsd", pnl);
        String reason = p.getString("pendingExit");
        if (n.getDouble("realized") + Math.min(0, pnl) <= -limits.dailyLossUsd) {
            n.put("dailyHalt", true); reason = "daily loss limit";
        }
        if (c.priceUsd <= p.getDouble("stopPrice")) reason = "paper stop hit";
        else if (c.priceUsd >= p.getDouble("targetPrice")) reason = "paper target hit";
        if (c.liquidityUsd < p.getDouble("liquidity") * 0.5) reason = "liquidity shock";
        if (c.security != null && "BLOCKED".equals(c.security.status)) reason = "token safety deteriorated";
        if (n.getBoolean("panic")) reason = "panic exit";
        p.put("pendingExit", reason);
        if (reason.isEmpty()) { commit(n); return "Paper position tracked by pair"; }
        // Panic does not invent a fill or bypass the stored slippage ceiling.
        if (c.security == null || c.security.checkedAtMs <= 0 || c.security.checkedAtMs > now
                || now - c.security.checkedAtMs > 300_000 || !range(c.security.sellTax, 0, 0.99)
                || "UNKNOWN".equals(c.security.status)) {
            commit(n); return "Exit pending: security/tax evidence unavailable";
        }
        if (c.security.sellBlocked) {
            commit(n); return "Exit pending: security provider reports sale restriction";
        }
        if (PaperExecution.friction(gross, c.liquidityUsd, tax) * 100 > Math.min(limits.slippagePct, p.getDouble("slippagePct"))) {
            commit(n); return "Exit pending: slippage exceeds cap";
        }
        TradeRecord record = new TradeRecord();
        record.positionId = p.getString("id"); record.pairAddress = p.getString("pairAddress"); record.tokenAddress = p.getString("tokenAddress");
        record.quantity = p.getDouble("quantity"); record.entryQuoteUsd = p.getDouble("quoteAmount"); record.exitQuoteUsd = proceeds;
        record.executionModel = "constant-product-cost-v1";
        record.chain = c.chain; record.symbol = p.getString("symbol"); record.entryPrice = p.getDouble("entryPrice");
        record.exitPrice = proceeds / p.getDouble("quantity"); record.pnlUsd = pnl;
        record.pnlPct = pnl / p.getDouble("quoteAmount") * 100; record.win = pnl > 0;
        record.openedAtMs = p.getLong("openedAtMs"); record.closedAtMs = now; record.exitReason = reason;
        JSONArray old = n.getJSONArray("history"), history = new JSONArray(); history.put(record.toJson());
        for (int i = 0; i < Math.min(99, old.length()); i++) history.put(old.getString(i));
        n.put("history", history).put("cash", n.getDouble("cash") + proceeds)
            .put("realized", n.getDouble("realized") + pnl).put("losses", pnl < 0 ? n.getInt("losses") + 1 : 0)
            .put("cooldown", now + (pnl < 0 ? limits.cooldownMs : 30_000));
        if (n.getDouble("realized") <= -limits.dailyLossUsd) n.put("dailyHalt", true);
        if (n.getInt("losses") >= limits.maxConsecutiveLosses) n.put("panic", true);
        n.remove("position"); commit(n); return "Paper position closed: " + reason;
    }
}
