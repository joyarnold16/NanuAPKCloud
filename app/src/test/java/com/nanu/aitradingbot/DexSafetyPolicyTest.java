package com.nanu.aitradingbot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DexSafetyPolicyTest {
    @Test public void blocksYoungThinOrOneWayPairs() {
        DexCandidate c = good();
        c.liquidityUsd = 900;
        DexSafetyPolicy.Report report = DexSafetyPolicy.evaluate(c, 25_000, 10_000, 24);
        assertEquals("BLOCKED", report.decision);
        assertTrue(report.reason.contains("liquidity"));
    }

    @Test public void qualifiesEstablishedPairWithBalancedActivity() {
        DexCandidate c = good();
        DexSafetyPolicy.Report report = DexSafetyPolicy.evaluate(c, 25_000, 10_000, 24);
        assertEquals("QUALIFIED", report.decision);
        assertTrue(report.score >= 70);
    }

    @Test public void paperEntryNeedsQualifiedControlledMomentum() {
        DexCandidate c = good();
        DexSafetyPolicy.Report report = DexSafetyPolicy.evaluate(c, 25_000, 10_000, 24);
        c.decision = report.decision;
        assertTrue(DexSafetyPolicy.canOpenPaperPosition(c, 1));
        c.change1h = 40;
        assertEquals(false, DexSafetyPolicy.canOpenPaperPosition(c, 1));
    }

    private DexCandidate good() {
        DexCandidate c = new DexCandidate();
        c.chain = "bsc";
        c.tokenAddress = "0x123";
        c.pairAddress = "0x456";
        c.priceUsd = 1;
        c.liquidityUsd = 80_000;
        c.volume24hUsd = 60_000;
        c.pairCreatedAtMs = System.currentTimeMillis() - 48L * 60L * 60L * 1000L;
        c.buys24h = 120;
        c.sells24h = 90;
        c.change1h = 2.5;
        c.change24h = 9;
        return c;
    }
}
