package com.nanu.aitradingbot;

import org.junit.Test;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OhlcvTest {
    @Test public void indicatorsMatchFlatSeriesAndKnownLinearEma() {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now);
        Ohlcv.Indicators i = Ohlcv.analyze(c.candles, 300000, now);
        assertEquals(1.0935, i.ema12, 1e-10); assertEquals(1.0865, i.ema26, 1e-10);
        assertEquals(.007, i.macd, 1e-10); assertEquals(.007, i.signal, 1e-10);
        assertEquals(100, i.rsi14, 1e-10); assertEquals(.002, i.atr14, 1e-10);
        assertEquals(1, i.volumeRatio, 1e-10);
        List<Ohlcv> flat = new ArrayList<>();
        for (Ohlcv b : c.candles) flat.add(new Ohlcv(b.timeMs, 1, 1, 1, 1, 1));
        i = Ohlcv.analyze(flat, 300000, now); assertEquals(50, i.rsi14, 0); assertEquals(0, i.atr14, 0);
    }
    @Test public void rejectsGapsDuplicateInvalidGeometryAndFutureCandles() {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now);
        c.candles.remove(50); assertFalse(Ohlcv.valid(c.candles, 300000, now, 60));
        c = PaperFixtures.good(now); c.candles.set(50, c.candles.get(49)); assertFalse(Ohlcv.valid(c.candles, 300000, now, 60));
        c = PaperFixtures.good(now); Ohlcv b = c.candles.get(50);
        c.candles.set(50, new Ohlcv(b.timeMs, 1, .5, 1, 1, 1)); assertFalse(Ohlcv.valid(c.candles, 300000, now, 60));
        assertFalse(Ohlcv.valid(PaperFixtures.good(now + 900000).candles, 300000, now, 60));
        assertFalse(Ohlcv.valid(PaperFixtures.good(now - 900000).candles, 300000, now, 60));
    }
    @Test public void providerParserExcludesFormingCandleAndRequiresDescendingOrder() throws Exception {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now); JSONArray rows = new JSONArray();
        rows.put(new JSONArray(new Object[]{now / 300000 * 300, 1, 1, 1, 1, 1}));
        for (int i = c.candles.size() - 1; i >= 0; i--) {
            Ohlcv b = c.candles.get(i); rows.put(new JSONArray(new Object[]{b.timeMs / 1000, b.open, b.high, b.low, b.close, b.volume}));
        }
        assertEquals(100, DexDataClient.parseCandles(rows, 300000, now).size());
        rows.put(rows.getJSONArray(1));
        assertThrows(IllegalArgumentException.class, () -> DexDataClient.parseCandles(rows, 300000, now));
    }
    @Test public void percentChangesCannotInventHammerButRealGeometryCan() {
        long now = System.currentTimeMillis(); DexCandidate c = PaperFixtures.good(now);
        c.candles.clear(); c.change24h = -30; c.change1h = 5; assertTrue(CandlePatterns.detect(c).isEmpty());
        long end = now / 300000 * 300000;
        c.candles.add(new Ohlcv(end - 900000, 12, 12, 10, 11, 100));
        c.candles.add(new Ohlcv(end - 600000, 11, 11, 9, 10, 100));
        c.candles.add(new Ohlcv(end - 300000, 10, 10.6, 8, 10.5, 100));
        assertTrue(CandlePatterns.detect(c).contains("HAMMER"));
    }
}
