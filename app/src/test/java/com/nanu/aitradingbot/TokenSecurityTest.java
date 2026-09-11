package com.nanu.aitradingbot;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class TokenSecurityTest {
    private JSONObject holders() throws Exception {
        JSONArray h = new JSONArray();
        for (int i = 0; i < 10; i++) h.put(new JSONObject().put("percent", "0.01"));
        return new JSONObject().put("holders", h);
    }
    private JSONObject bsc(DexCandidate c) throws Exception {
        JSONObject o = holders().put("is_open_source", "1").put("buy_tax", "0").put("sell_tax", "0");
        for (String k : new String[]{"is_proxy", "is_mintable", "can_take_back_ownership", "owner_change_balance", "hidden_owner",
                "selfdestruct", "external_call", "cannot_buy", "cannot_sell_all", "is_honeypot", "slippage_modifiable", "transfer_pausable",
                "is_blacklisted", "is_whitelisted", "is_anti_whale", "anti_whale_modifiable", "trading_cooldown", "personal_slippage_modifiable"}) o.put(k, "0");
        o.put("dex", new JSONArray().put(new JSONObject().put("pair", c.pairAddress)));
        o.put("lp_holders", new JSONArray().put(new JSONObject().put("percent", "0.95")
            .put("address", "0x000000000000000000000000000000000000dead")));
        return o;
    }
    private TokenSecurity parse(DexCandidate c, JSONObject o, long now) throws Exception {
        return TokenSecurity.parse(c, new JSONObject().put("code", 1).put("result", new JSONObject().put(c.tokenAddress, o)), now);
    }
    @Test public void completeBscEvidencePassesButMissingFieldNeverDoes() throws Exception {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now); JSONObject o = bsc(c);
        assertEquals("PASS", parse(c, o, now).status);
        for (String key : new String[]{"is_proxy", "is_mintable", "is_honeypot", "cannot_sell_all", "buy_tax", "sell_tax", "holders", "lp_holders", "dex"}) {
            JSONObject missing = new JSONObject(o.toString()); missing.remove(key);
            assertEquals(key, "UNKNOWN", parse(c, missing, now).status);
        }
    }
    @Test public void dangerousBscPrivilegesAndTaxesBlock() throws Exception {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now);
        for (String key : new String[]{"is_proxy", "is_mintable", "is_blacklisted", "is_honeypot", "transfer_pausable", "slippage_modifiable"}) {
            assertEquals(key, "BLOCKED", parse(c, bsc(c).put(key, "1"), now).status);
        }
        assertTrue(parse(c, bsc(c).put("is_honeypot", "1"), now).sellBlocked);
        assertEquals("BLOCKED", parse(c, bsc(c).put("sell_tax", "0.20"), now).status);
        assertEquals("UNKNOWN", parse(c, bsc(c).put("sell_tax", ""), now).status);
    }
    @Test public void concentrationsAndExpiredLpLocksBlock() throws Exception {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now); JSONObject o = bsc(c);
        o.getJSONArray("holders").getJSONObject(0).put("percent", .6); assertEquals("BLOCKED", parse(c, o, now).status);
        o = bsc(c); o.getJSONArray("lp_holders").getJSONObject(0).put("address", "owner").put("is_locked", "1")
            .put("locked_detail", new JSONArray().put(new JSONObject().put("end_time", now / 1000 - 1)));
        assertEquals("BLOCKED", parse(c, o, now).status);
    }
    private JSONObject solana(DexCandidate c, long now) throws Exception {
        JSONObject o = holders().put("non_transferable", "0").put("default_account_state", "1")
            .put("transfer_fee", new JSONObject()).put("transfer_hook", new JSONArray());
        for (String key : new String[]{"mintable", "freezable", "closable", "metadata_mutable", "transfer_fee_upgradable",
                "default_account_state_upgradable", "balance_mutable_authority", "transfer_hook_upgradable"})
            o.put(key, new JSONObject().put("status", "0"));
        o.put("dex", new JSONArray().put(new JSONObject().put("id", c.pairAddress)));
        o.put("lp_holders", new JSONArray().put(new JSONObject().put("percent", .95).put("is_locked", "1")
            .put("locked_detail", new JSONArray().put(new JSONObject().put("end_time", now / 1000 + 30 * 86400)))));
        return o;
    }
    @Test public void solanaAuthoritiesAndToken2022ExtensionsBlock() throws Exception {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now); c.chain = "solana";
        c.tokenAddress = "So11111111111111111111111111111111111111112"; c.pairAddress = c.tokenAddress;
        assertEquals("PASS", parse(c, solana(c, now), now).status);
        for (String key : new String[]{"mintable", "freezable", "metadata_mutable", "transfer_fee_upgradable"}) {
            JSONObject o = solana(c, now); o.getJSONObject(key).put("status", "1");
            assertEquals("BLOCKED", parse(c, o, now).status);
        }
        assertEquals("BLOCKED", parse(c, solana(c, now).put("transfer_hook", new JSONArray().put("program")), now).status);
        assertEquals("BLOCKED", parse(c, solana(c, now).put("transfer_fee", new JSONObject().put("fee_rate", 200)), now).status);
        JSONObject missing = solana(c, now); missing.remove("freezable"); assertEquals("UNKNOWN", parse(c, missing, now).status);
    }
    @Test public void providerErrorAndWrongTokenRemainUnknown() throws Exception {
        DexCandidate c = PaperFixtures.good(System.currentTimeMillis());
        assertEquals("UNKNOWN", TokenSecurity.parse(c, new JSONObject().put("code", 429), System.currentTimeMillis()).status);
        assertEquals("UNKNOWN", TokenSecurity.parse(c, new JSONObject().put("code", 1).put("result", new JSONObject()), System.currentTimeMillis()).status);
    }
    @Test public void solanaAddressesAreCaseSensitiveAndBscAddressesAreNot() {
        assertFalse(DexSafetyPolicy.sameAddress("solana", "AbC", "abc"));
        assertTrue(DexSafetyPolicy.sameAddress("bsc", "0xABC", "0xabc"));
    }
    @Test public void localLiquidityAloneIsNeverQualified() {
        DexCandidate c = PaperFixtures.good(System.currentTimeMillis()); c.security = new TokenSecurity();
        assertEquals("WATCHING", DexSafetyPolicy.evaluate(c, 25000, 10000, 24).decision);
        assertFalse(DexSafetyPolicy.canOpenPaperPosition(c, 1));
    }
}
