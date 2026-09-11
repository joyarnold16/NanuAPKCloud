package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Strict GoPlus evidence adapter. Unknown fields never mean safe. */
public final class TokenSecurity {
    public String status = "UNKNOWN", reason = "Security evidence unavailable";
    public double buyTax = Double.NaN, sellTax = Double.NaN;
    public long checkedAtMs;
    public boolean sellBlocked;
    public static TokenSecurity parse(DexCandidate c, JSONObject response, long now) {
        TokenSecurity r = new TokenSecurity();
        List<String> unknown = new ArrayList<>(), blocked = new ArrayList<>();
        try {
            if (response.getInt("code") != 1) throw new IllegalArgumentException("Provider error");
            JSONObject results = response.getJSONObject("result"), o = null;
            java.util.Iterator<String> keys = results.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (DexSafetyPolicy.sameAddress(c.chain, key, c.tokenAddress)) o = results.getJSONObject(key);
            }
            if (o == null) throw new IllegalArgumentException("Token missing from security response");
            if (c.isBsc()) {
                flag(o, "is_open_source", "1", false, unknown, blocked);
                for (String key : new String[]{"is_proxy", "is_mintable", "can_take_back_ownership", "owner_change_balance",
                        "hidden_owner", "selfdestruct", "external_call", "cannot_buy", "cannot_sell_all", "is_honeypot",
                        "slippage_modifiable", "transfer_pausable", "is_blacklisted", "is_whitelisted", "is_anti_whale",
                        "anti_whale_modifiable", "trading_cooldown", "personal_slippage_modifiable"})
                    flag(o, key, "0", false, unknown, blocked);
                r.sellBlocked = "1".equals(o.optString("is_honeypot")) || "1".equals(o.optString("cannot_sell_all"));
                r.buyTax = tax(o, "buy_tax", unknown, blocked); r.sellTax = tax(o, "sell_tax", unknown, blocked);
            } else if (c.isSolana()) {
                for (String key : new String[]{"mintable", "freezable", "closable", "metadata_mutable", "transfer_fee_upgradable",
                        "default_account_state_upgradable", "balance_mutable_authority", "transfer_hook_upgradable"})
                    flag(o, key, "0", true, unknown, blocked);
                flag(o, "non_transferable", "0", false, unknown, blocked);
                flag(o, "default_account_state", "1", false, unknown, blocked);
                r.sellBlocked = "1".equals(o.optString("non_transferable")) || "2".equals(o.optString("default_account_state"));
                // This milestone rejects transfer fee/hook extensions instead of estimating their behavior.
                for (String key : new String[]{"transfer_fee", "transfer_hook"}) {
                    if (!o.has(key)) unknown.add(key);
                    else if (!o.isNull(key) && !"{}".equals(o.opt(key).toString()) && !"[]".equals(o.opt(key).toString())) blocked.add(key);
                }
                r.buyTax = r.sellTax = 0;
            } else throw new IllegalArgumentException("Unsupported chain");
            JSONArray holders = o.optJSONArray("holders");
            if (holders == null || holders.length() == 0) unknown.add("holder concentration");
            else {
                double total = 0;
                for (int i = 0; i < Math.min(10, holders.length()); i++) {
                    double pct = holders.getJSONObject(i).optDouble("percent", Double.NaN);
                    if (!Ohlcv.finite(pct) || pct < 0 || pct > 1) { unknown.add("holder percent"); continue; }
                    total += pct;
                    if (pct > 0.2) blocked.add("single holder over 20%");
                }
                if (total > 0.5) blocked.add("top holders over 50%");
            }
            // Verify the selected pair is covered. A report for another pool is insufficient.
            JSONArray dex = o.optJSONArray("dex"); boolean matched = false;
            if (dex != null) for (int i = 0; i < dex.length(); i++) {
                JSONObject pool = dex.getJSONObject(i);
                String address = pool.optString(c.isBsc() ? "pair" : "id");
                if (DexSafetyPolicy.sameAddress(c.chain, address, c.pairAddress)) matched = true;
            }
            if (!matched) unknown.add("selected pool coverage");
            // LP lock reports are token-level evidence, not proof that a particular pool cannot rug.
            JSONArray lp = o.optJSONArray("lp_holders"); double burned = 0;
            if (lp == null || lp.length() == 0) unknown.add("LP ownership");
            else for (int i = 0; i < lp.length(); i++) {
                JSONObject h = lp.getJSONObject(i);
                double pct = h.optDouble("percent", Double.NaN);
                if (!Ohlcv.finite(pct) || pct < 0 || pct > 1) { unknown.add("LP fraction"); continue; }
                String address = h.optString("address", "");
                boolean burn = c.isBsc() && ("0x000000000000000000000000000000000000dead".equalsIgnoreCase(address)
                    || "0x0000000000000000000000000000000000000000".equals(address));
                boolean locked = false;
                JSONArray locks = h.optJSONArray("locked_detail");
                if ("1".equals(h.optString("is_locked")) && locks != null && locks.length() > 0) {
                    locked = true;
                    for (int j = 0; j < locks.length(); j++)
                        if (locks.getJSONObject(j).optLong("end_time", 0) * 1000L <= now + 7 * 86_400_000L) locked = false;
                }
                if (burn || locked) burned += pct;
            }
            if (lp != null && lp.length() > 0 && burned < 0.9) blocked.add("less than 90% LP locked beyond 7 days/burned");
            r.checkedAtMs = now;
            r.status = !blocked.isEmpty() ? "BLOCKED" : !unknown.isEmpty() ? "UNKNOWN" : "PASS";
            r.reason = !blocked.isEmpty() ? blocked.toString() : !unknown.isEmpty() ? "Missing evidence: " + unknown : "Provider checks passed; not a safety guarantee";
        } catch (Exception e) { r.reason = "Security evidence unavailable or malformed"; }
        return r;
    }
    private static void flag(JSONObject o, String key, String expected, boolean nested, List<String> unknown, List<String> blocked) {
        JSONObject target = nested ? o.optJSONObject(key) : o;
        String value = target == null ? "" : target.optString(nested ? "status" : key, "");
        if (!"0".equals(value) && !"1".equals(value) && !("default_account_state".equals(key) && "2".equals(value))) unknown.add(key);
        else if (!expected.equals(value)) blocked.add(key);
    }
    private static double tax(JSONObject o, String key, List<String> unknown, List<String> blocked) {
        double value = o.optDouble(key, Double.NaN);
        if (!Ohlcv.finite(value) || value < 0 || value > 1) unknown.add(key);
        else if (value > 0.05) blocked.add(key + " over 5%");
        return value;
    }
}
