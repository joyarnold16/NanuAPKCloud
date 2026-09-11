package com.nanu.aitradingbot;

import java.util.List;

/** Closed, ascending USD candles. No synthetic candles or gap filling. */
public final class Ohlcv {
    public final long timeMs;
    public final double open, high, low, close, volume;
    public Ohlcv(long timeMs, double open, double high, double low, double close, double volume) {
        this.timeMs = timeMs; this.open = open; this.high = high; this.low = low;
        this.close = close; this.volume = volume;
    }
    public static boolean valid(List<Ohlcv> bars, long interval, long now, int minimum) {
        if (bars == null || bars.size() < minimum || interval <= 0) return false;
        long previous = -1;
        for (Ohlcv b : bars) {
            if (b == null || b.timeMs <= 0 || b.timeMs % interval != 0 || b.timeMs > now - interval
                    || (previous >= 0 && b.timeMs - previous != interval)
                    || !positive(b.open) || !positive(b.high) || !positive(b.low) || !positive(b.close)
                    || !finite(b.volume) || b.volume < 0 || b.high < Math.max(b.open, b.close)
                    || b.low > Math.min(b.open, b.close) || b.low > b.high) return false;
            previous = b.timeMs;
        }
        return now - previous < interval * 3;
    }
    public static boolean finite(double n) { return !Double.isNaN(n) && !Double.isInfinite(n); }
    public static boolean positive(double n) { return finite(n) && n > 0; }

    public static final class Indicators {
        public double ema12, ema26, rsi14, atr14, macd, signal, volumeRatio;
    }
    public static Indicators analyze(List<Ohlcv> bars, long interval, long now) {
        if (!valid(bars, interval, now, 60)) throw new IllegalArgumentException("Missing, stale or invalid OHLCV");
        Indicators r = new Indicators();
        double fast = 0, slow = 0, signal = 0, gain = 0, loss = 0, atr = 0;
        int signalCount = 0;
        for (int i = 0; i < bars.size(); i++) {
            Ohlcv b = bars.get(i);
            if (i < 12) fast += b.close / 12; else fast += (b.close - fast) * 2 / 13;
            if (i < 26) slow += b.close / 26; else slow += (b.close - slow) * 2 / 27;
            if (i >= 25) {
                double m = fast - slow;
                if (signalCount < 9) signal += m / 9; else signal += (m - signal) * 2 / 10;
                signalCount++;
            }
            if (i > 0) {
                double previous = bars.get(i - 1).close, delta = b.close - previous;
                double tr = Math.max(b.high - b.low, Math.max(Math.abs(b.high - previous), Math.abs(b.low - previous)));
                if (i <= 14) { gain += Math.max(delta, 0) / 14; loss += Math.max(-delta, 0) / 14; atr += tr / 14; }
                else { gain = (gain * 13 + Math.max(delta, 0)) / 14; loss = (loss * 13 + Math.max(-delta, 0)) / 14; atr = (atr * 13 + tr) / 14; }
            }
        }
        r.ema12 = fast; r.ema26 = slow; r.macd = fast - slow; r.signal = signal; r.atr14 = atr;
        r.rsi14 = gain == 0 && loss == 0 ? 50 : loss == 0 ? 100 : 100 - 100 / (1 + gain / loss);
        double volume = 0;
        for (int i = bars.size() - 21; i < bars.size() - 1; i++) volume += bars.get(i).volume / 20;
        r.volumeRatio = volume > 0 ? bars.get(bars.size() - 1).volume / volume : 0;
        return r;
    }
}
