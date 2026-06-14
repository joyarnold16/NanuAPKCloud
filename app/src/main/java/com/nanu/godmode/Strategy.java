package com.nanu.godmode;

import java.util.ArrayList;
import java.util.List;

public class Strategy {
    public static class Decision {
        public String action;
        public double confidence;
        public String reason;
        public double lastPrice;
        public double rsi;
        public double emaFast;
        public double emaSlow;
        public double macd;
    }

    public Decision decide(String symbol, List<Candle> candles) {
        Decision d = new Decision();
        d.action = "WAIT";
        d.confidence = 0.0;
        d.reason = "Need more candles.";
        if (candles == null || candles.size() < 35) return d;

        List<Double> closes = new ArrayList<>();
        for (Candle c : candles) closes.add(c.close);
        d.lastPrice = closes.get(closes.size() - 1);
        d.emaFast = IndicatorUtils.ema(closes, 9);
        d.emaSlow = IndicatorUtils.ema(closes, 21);
        d.rsi = IndicatorUtils.rsi(closes, 14);
        d.macd = IndicatorUtils.macdHist(closes);

        double last = d.lastPrice;
        double prev = closes.get(closes.size()-2);
        boolean green = last > prev;
        boolean trendUp = d.emaFast > d.emaSlow;
        boolean trendDown = d.emaFast < d.emaSlow;
        boolean rsiBuyZone = d.rsi > 45 && d.rsi < 68;
        boolean rsiHot = d.rsi >= 72;
        boolean rsiWeak = d.rsi < 38;

        if (trendUp && rsiBuyZone && d.macd > 0 && green) {
            d.action = "BUY";
            d.confidence = Math.min(96, 58 + (d.rsi - 45) + Math.abs(d.macd / Math.max(1, last)) * 10000);
            d.reason = symbol + " scalping BUY: EMA9 above EMA21, RSI healthy, MACD positive.";
        } else if (trendDown || rsiHot || rsiWeak) {
            d.action = "EXIT";
            d.confidence = Math.min(92, 50 + Math.abs(d.rsi - 50));
            d.reason = symbol + " exit/avoid: trend weak or RSI danger zone.";
        } else {
            d.action = "WAIT";
            d.confidence = 35;
            d.reason = symbol + " wait: signal not clean enough for scalping.";
        }
        return d;
    }
}
