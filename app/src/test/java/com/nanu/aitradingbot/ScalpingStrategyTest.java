package com.nanu.aitradingbot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ScalpingStrategyTest {
    @Test
    public void waitsUntilThereAreEnoughClosedCandles() {
        List<ScalpingStrategy.Candle> candles = new ArrayList<>();
        for (int i = 0; i < 10; i++) candles.add(candle(i, 100 + i));

        assertEquals(ScalpingStrategy.Action.WAITING, ScalpingStrategy.evaluate(candles).action);
    }

    @Test
    public void producesExitForClearDowntrend() {
        List<ScalpingStrategy.Candle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) candles.add(candle(i, 120.0 - i * 0.35));

        assertEquals(ScalpingStrategy.Action.EXIT, ScalpingStrategy.evaluate(candles).action);
    }

    @Test
    public void producesBuyForTrendMomentumAndControlledRsi() {
        List<ScalpingStrategy.Candle> candles = new ArrayList<>();
        double close = 100.0;
        for (int i = 0; i < 45; i++) {
            close += 0.03;
            candles.add(candle(i, close));
        }
        for (int i = 45; i < 60; i++) {
            close += i % 2 == 1 ? 0.60 : -0.40;
            candles.add(candle(i, close));
        }

        assertEquals(ScalpingStrategy.Action.BUY, ScalpingStrategy.evaluate(candles).action);
    }

    private ScalpingStrategy.Candle candle(int minute, double close) {
        return new ScalpingStrategy.Candle(minute * 60_000L, close + 0.1, close - 0.1, close);
    }
}
