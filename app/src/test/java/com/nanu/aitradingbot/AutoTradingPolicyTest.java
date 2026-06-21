package com.nanu.aitradingbot;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AutoTradingPolicyTest {
    @Test
    public void choosesOnlyTheHighestQualifiedApprovedBuy() {
        Map<String, ScalpingStrategy.Signal> signals = new HashMap<>();
        signals.put("BTCUSDT", signal(ScalpingStrategy.Action.BUY, 72));
        signals.put("ETHUSDT", signal(ScalpingStrategy.Action.BUY, 81));
        signals.put("SOLUSDT", signal(ScalpingStrategy.Action.EXIT, 95));

        assertEquals("ETHUSDT", AutoTradingPolicy.chooseBestBuy(signals, 70));
        assertEquals("", AutoTradingPolicy.chooseBestBuy(signals, 85));
    }

    @Test
    public void acceptsOnlyIpLiteralsForStaticIpGate() {
        assertTrue(AutoTradingPolicy.isStaticIp("203.0.113.10"));
        assertTrue(AutoTradingPolicy.isStaticIp("2001:db8::1"));
        assertFalse(AutoTradingPolicy.isStaticIp("example.com"));
        assertFalse(AutoTradingPolicy.isStaticIp("999.1.1.1"));
        assertFalse(AutoTradingPolicy.isStaticIp("2001:::1"));
    }

    @Test
    public void requiresAnExactPublicIpMatchBeforeAutomaticEntry() {
        assertTrue(AutoTradingPolicy.publicIpMatches("203.0.113.10", "203.0.113.10"));
        assertFalse(AutoTradingPolicy.publicIpMatches("203.0.113.10", "203.0.113.11"));
        assertFalse(AutoTradingPolicy.publicIpMatches("203.0.113.10", "not-an-ip"));
    }

    @Test
    public void reportsPanicBeforeActiveOrIdle() {
        assertEquals("ACTIVE", AutoTradingPolicy.runtimeState(true, false, false, false));
        assertEquals("ACTIVE", AutoTradingPolicy.runtimeState(false, true, false, false));
        assertEquals("PANIC", AutoTradingPolicy.runtimeState(true, false, false, true));
        assertEquals("IDLE", AutoTradingPolicy.runtimeState(false, false, false, false));
    }

    @Test
    public void requiresMoreThanTheExchangeMinimumForAPotentiallyProtectedEntry() {
        assertEquals(6.0d, AutoTradingPolicy.minimumProtectedQuote(5.0d, 0.6d), 0.000001d);
        assertTrue(AutoTradingPolicy.minimumProtectedQuote(10.0d, 3.0d) > 10.0d);
    }

    @Test
    public void createsOnlyAValidSellOcoBracket() {
        AutoTradingPolicy.OcoLevels levels = AutoTradingPolicy.calculateOcoLevels(100.0d, 100.2d, 0.6d, 0.6d, 0.01d);

        assertTrue(levels.takeProfit > 100.2d);
        assertTrue(100.2d > levels.stopPrice);
        assertTrue(levels.stopPrice > levels.stopLimit);
        assertNull(AutoTradingPolicy.calculateOcoLevels(100.0d, 99.4d, 0.6d, 0.6d, 0.01d));
    }

    private ScalpingStrategy.Signal signal(ScalpingStrategy.Action action, int confidence) {
        return new ScalpingStrategy.Signal(action, 100.0, 99.0, 98.0, 60.0, confidence, "test");
    }
}
