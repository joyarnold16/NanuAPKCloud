package com.nanu.aitradingbot;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class PaperLedgerTest {
    @Test public void restartRestoresPositionAndRoundTripCosts() throws Exception {
        long now = System.currentTimeMillis(); PaperFixtures.Memory disk = new PaperFixtures.Memory();
        PaperLedger book = new PaperLedger(disk); DexCandidate c = PaperFixtures.good(now);
        assertTrue(book.open(c, new PaperLedger.Limits(), now, "paper").contains("opened"));
        JSONObject before = book.position(); assertTrue(before.getDouble("pnlUsd") < 0);
        book = new PaperLedger(disk); assertEquals(before.toString(), book.position().toString());
        book.requestExit("test"); book.mark(c, new PaperLedger.Limits(), now);
        assertNull(book.position()); assertTrue(book.snapshot().getDouble("cash") < 1000);
        assertEquals(1, book.snapshot().getJSONArray("history").length());
        book.mark(c, new PaperLedger.Limits(), now);
        assertEquals(1, new PaperLedger(disk).snapshot().getJSONArray("history").length());
    }
    @Test public void panicPersistsAndNeverFillsUsingStaleOrWrongPair() throws Exception {
        long now = System.currentTimeMillis(); PaperFixtures.Memory disk = new PaperFixtures.Memory();
        PaperLedger book = new PaperLedger(disk); DexCandidate c = PaperFixtures.good(now);
        book.open(c, new PaperLedger.Limits(), now, "paper"); book.panic(); book = new PaperLedger(disk);
        assertTrue(book.halted()); assertEquals("panic exit", book.position().getString("pendingExit"));
        c.observedAtMs = now - 90_001; book.mark(c, new PaperLedger.Limits(), now); assertNotNull(book.position());
        c.observedAtMs = now; c.pairAddress = "0x3333333333333333333333333333333333333333";
        book.mark(c, new PaperLedger.Limits(), now); assertNotNull(book.position());
        assertThrows(IllegalStateException.class, book::clearPanic);
        c = PaperFixtures.good(now); book.mark(c, new PaperLedger.Limits(), now); assertNull(book.position());
        assertTrue(book.halted()); book.clearPanic(); assertFalse(book.halted());
    }
    @Test public void slippageCapKeepsExitPendingDuringLiquidityShock() throws Exception {
        long now = System.currentTimeMillis(); PaperLedger b = new PaperLedger(new PaperFixtures.Memory());
        DexCandidate c = PaperFixtures.good(now); b.open(c, new PaperLedger.Limits(), now, "paper");
        c.liquidityUsd = 20; b.mark(c, new PaperLedger.Limits(), now);
        assertNotNull(b.position()); assertFalse(b.position().getString("pendingExit").isEmpty());
        c.liquidityUsd = 80000; b.mark(c, new PaperLedger.Limits(), now); assertNull(b.position());
    }
    @Test public void dailyLossIncludesUnrealizedAndLatchesAcrossRestart() throws Exception {
        long now = System.currentTimeMillis(); PaperFixtures.Memory disk = new PaperFixtures.Memory();
        PaperLedger b = new PaperLedger(disk); PaperLedger.Limits l = new PaperLedger.Limits();
        DexCandidate c = PaperFixtures.good(now); b.open(c, l, now, "paper"); c.priceUsd *= 0.1;
        b.mark(c, l, now); assertNull(b.position()); assertTrue(b.snapshot().getBoolean("dailyHalt"));
        b = new PaperLedger(disk); assertTrue(b.halted());
        b.clearPanic(); assertTrue(b.halted());
        assertFalse(b.open(PaperFixtures.good(now), l, now, "paper").contains("opened"));
        long tomorrow = now + 86_400_000L;
        assertTrue(b.open(PaperFixtures.good(tomorrow), l, tomorrow, "paper").contains("opened"));
    }
    @Test public void positionLimitAndCooldownAreEnforced() throws Exception {
        long now = System.currentTimeMillis(); PaperLedger b = new PaperLedger(new PaperFixtures.Memory());
        DexCandidate c = PaperFixtures.good(now); PaperLedger.Limits l = new PaperLedger.Limits();
        b.open(c, l, now, "paper"); assertFalse(b.open(c, l, now, "paper").contains("opened"));
        b.requestExit("test"); b.mark(c, l, now);
        assertFalse(b.open(c, l, now, "paper").contains("opened"));
        assertTrue(b.open(PaperFixtures.good(now + 300_001), l, now + 300_001, "paper").contains("opened"));
    }
    @Test public void lossStreakHaltsUntilExplicitRecovery() throws Exception {
        long now = System.currentTimeMillis(); PaperLedger b = new PaperLedger(new PaperFixtures.Memory());
        PaperLedger.Limits l = new PaperLedger.Limits(); l.maxTrades = 10;
        for (int i = 0; i < 3; i++) {
            DexCandidate c = PaperFixtures.good(now); assertTrue(b.open(c, l, now, "paper").contains("opened"));
            b.requestExit("cost loss"); b.mark(c, l, now); now += 300_001;
        }
        assertTrue(b.halted()); b.clearPanic(); assertFalse(b.halted());
    }
    @Test public void tradeLimitCannotBeResetByClockRollback() throws Exception {
        long now = System.currentTimeMillis(); PaperLedger b = new PaperLedger(new PaperFixtures.Memory());
        PaperLedger.Limits l = new PaperLedger.Limits(); l.maxTrades = 1;
        DexCandidate c = PaperFixtures.good(now); b.open(c, l, now, "paper"); b.requestExit("test"); b.mark(c, l, now);
        assertFalse(b.open(PaperFixtures.good(now + 300_001), l, now + 300_001, "paper").contains("opened"));
        assertFalse(b.open(PaperFixtures.good(now - 86_400_000), l, now - 86_400_000, "paper").contains("opened"));
    }
    @Test public void nanAndZeroLimitsFailClosed() throws Exception {
        PaperLedger b = new PaperLedger(new PaperFixtures.Memory()); PaperLedger.Limits l = new PaperLedger.Limits();
        l.dailyLossUsd = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> b.open(PaperFixtures.good(System.currentTimeMillis()), l, System.currentTimeMillis(), "paper"));
        l.dailyLossUsd = 5; l.maxTrades = 0; assertThrows(IllegalArgumentException.class, l::validate);
    }
    @Test public void corruptedAndFailedStorageCannotTradeOrLoseCommittedPosition() throws Exception {
        PaperFixtures.Memory disk = new PaperFixtures.Memory(); disk.value = "{}";
        PaperLedger corrupt = new PaperLedger(disk); assertFalse(corrupt.healthy());
        disk.value = null; PaperLedger b = new PaperLedger(disk); long now = System.currentTimeMillis();
        b.open(PaperFixtures.good(now), new PaperLedger.Limits(), now, "paper"); String before = disk.value;
        disk.fail = true; assertThrows(Exception.class, b::panic); assertFalse(b.healthy());
        assertEquals(before, disk.value); assertNotNull(new PaperLedger(disk).position());
    }
    @Test public void safetyUnknownOrHoneypotDoesNotFabricatePanicFill() throws Exception {
        long now = System.currentTimeMillis(); PaperLedger b = new PaperLedger(new PaperFixtures.Memory());
        DexCandidate c = PaperFixtures.good(now); b.open(c, new PaperLedger.Limits(), now, "paper"); b.panic();
        c.security.status = "UNKNOWN"; b.mark(c, new PaperLedger.Limits(), now); assertNotNull(b.position());
        c.security.status = "BLOCKED"; c.security.sellBlocked = true;
        b.mark(c, new PaperLedger.Limits(), now); assertNotNull(b.position());
    }
    @Test public void liveModesAlwaysThrowBeforeMutation() throws Exception {
        PaperFixtures.Memory disk = new PaperFixtures.Memory(); PaperLedger b = new PaperLedger(disk); String initial = disk.value;
        for (String mode : new String[]{"live", "mainnet", "test", "PAPER", ""}) {
            assertThrows(SecurityException.class, () -> b.open(PaperFixtures.good(System.currentTimeMillis()), new PaperLedger.Limits(), System.currentTimeMillis(), mode));
        }
        assertEquals(initial, disk.value);
    }
    @Test public void riskSizingReservesEntryExitFeesAndStopLoss() throws Exception {
        long now = System.currentTimeMillis(); PaperLedger b = new PaperLedger(new PaperFixtures.Memory());
        PaperLedger.Limits l = new PaperLedger.Limits(); l.tradeUsd = 100; DexCandidate c = PaperFixtures.good(now);
        b.open(c, l, now, "paper"); JSONObject p = b.position(); assertNotNull(p);
        double risk = p.getDouble("quoteAmount") - PaperExecution.proceeds(p.getDouble("quantity"), p.getDouble("stopPrice"), c.liquidityUsd, 0, "bsc");
        assertTrue(risk <= l.riskPerTradeUsd); assertTrue(p.getDouble("quoteAmount") < 100);
    }
    @Test public void taxesAndPriceImpactIncreaseLoss() {
        double q = PaperExecution.quantity(10, 1, 80000, 0, "bsc");
        assertTrue(q < 10); assertTrue(PaperExecution.quantity(10, 1, 80000, .03, "bsc") < q);
        assertTrue(PaperExecution.proceeds(q, 1, 100, 0, "bsc") < PaperExecution.proceeds(q, 1, 80000, 0, "bsc"));
    }
}
