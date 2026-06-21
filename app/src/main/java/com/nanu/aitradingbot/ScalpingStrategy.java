package com.nanu.aitradingbot;

import java.util.List;
import java.util.Locale;

/**
 * A deterministic, inspectable signal filter. It produces signals only; it never sends orders.
 */
public final class ScalpingStrategy {
    public enum Action { BUY, EXIT, HOLD, WAITING }

    public static final class Candle {
        public final long closeTime;
        public final double high;
        public final double low;
        public final double close;

        public Candle(long closeTime, double high, double low, double close) {
            this.closeTime = closeTime;
            this.high = high;
            this.low = low;
            this.close = close;
        }
    }

    public static final class Signal {
        public final Action action;
        public final double price;
        public final double fastEma;
        public final double slowEma;
        public final double rsi;
        public final int confidence;
        public final String reason;

        Signal(Action action, double price, double fastEma, double slowEma, double rsi, int confidence, String reason) {
            this.action = action;
            this.price = price;
            this.fastEma = fastEma;
            this.slowEma = slowEma;
            this.rsi = rsi;
            this.confidence = confidence;
            this.reason = reason;
        }

        public String report(String symbol, double stopLossPct, double takeProfitPct) {
            return String.format(Locale.US,
                    "Live Spot Signal\n\nPair: %s\nAction: %s\nPrice: %.8f\nEMA 9 / EMA 21: %.8f / %.8f\nRSI 14: %.1f\nConfidence: %d/100\n\nReason: %s\n\nPaper automation may act on this signal. Automatic LIVE execution acts only when its separate static-IP, API Doctor, Telegram Doctor, arm, and foreground-service gates are active.\n\nConfigured reference levels for a BUY: stop %.2f%%, target %.2f%%. A real automatic fill requests Binance OCO exit protection.",
                    symbol, action, price, fastEma, slowEma, rsi, confidence, reason, stopLossPct, takeProfitPct);
        }
    }

    private ScalpingStrategy() {}

    public static Signal evaluate(List<Candle> candles) {
        if (candles == null || candles.size() < 30) {
            return new Signal(Action.WAITING, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0,
                    "Waiting for at least 30 closed one-minute candles.");
        }

        int size = candles.size();
        double price = candles.get(size - 1).close;
        double previousPrice = candles.get(size - 2).close;
        double fast = ema(candles, size, 9);
        double slow = ema(candles, size, 21);
        double previousFast = ema(candles, size - 1, 9);
        double previousSlow = ema(candles, size - 1, 21);
        double rsi = rsi(candles, 14);

        boolean trendUp = fast > slow && price > fast;
        boolean recentCross = previousFast <= previousSlow && fast > slow;
        boolean momentumHealthy = rsi >= 52.0 && rsi <= 68.0;
        boolean candlePositive = price > previousPrice;
        int buyScore = (trendUp ? 35 : 0) + (recentCross ? 25 : 0) + (momentumHealthy ? 25 : 0) + (candlePositive ? 15 : 0);

        if (trendUp && momentumHealthy && candlePositive) {
            return new Signal(Action.BUY, price, fast, slow, rsi, buyScore,
                    recentCross ? "Fresh EMA 9/21 bullish cross with positive candle and controlled RSI." : "EMA trend remains bullish, price holds above EMA 9, and RSI is inside the entry band.");
        }

        boolean trendDown = fast < slow && price < fast;
        if (trendDown || rsi >= 76.0) {
            return new Signal(Action.EXIT, price, fast, slow, rsi, Math.max(55, trendDown ? 75 : 60),
                    trendDown ? "EMA 9 fell below EMA 21 and price is below the fast EMA." : "RSI is extended; protect an existing paper position rather than chase momentum.");
        }

        return new Signal(Action.HOLD, price, fast, slow, rsi, Math.max(20, buyScore),
                "No clean entry. The strategy waits for trend, momentum, and candle confirmation to align.");
    }

    private static double ema(List<Candle> candles, int exclusiveEnd, int period) {
        int start = Math.max(0, exclusiveEnd - Math.max(period * 3, period));
        double value = candles.get(start).close;
        double multiplier = 2.0 / (period + 1.0);
        for (int i = start + 1; i < exclusiveEnd; i++) value = (candles.get(i).close - value) * multiplier + value;
        return value;
    }

    private static double rsi(List<Candle> candles, int period) {
        int start = Math.max(1, candles.size() - period);
        double gains = 0;
        double losses = 0;
        for (int i = start; i < candles.size(); i++) {
            double change = candles.get(i).close - candles.get(i - 1).close;
            if (change >= 0) gains += change;
            else losses -= change;
        }
        if (losses < 0.00000001) return gains < 0.00000001 ? 50.0 : 100.0;
        double rs = gains / losses;
        return 100.0 - 100.0 / (1.0 + rs);
    }
}
