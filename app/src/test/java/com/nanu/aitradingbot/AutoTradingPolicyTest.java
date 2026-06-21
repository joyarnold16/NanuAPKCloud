package com.nanu.aitradingbot;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    }

    private ScalpingStrategy.Signal signal(ScalpingStrategy.Action action, int confidence) {
        return new ScalpingStrategy.Signal(action, 100.0, 99.0, 98.0, 60.0, confidence, "test");
    }
}
