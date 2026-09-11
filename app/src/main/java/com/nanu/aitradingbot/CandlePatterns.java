package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;

public final class CandlePatterns {

    public static List<String> detect(DexCandidate c) {
        List<String> p = new ArrayList<>();
        if (c == null || !Ohlcv.valid(c.candles, c.candleIntervalMs, System.currentTimeMillis(), 3)) return p;
        List<Ohlcv> bars = c.candles;
        Ohlcv a = bars.get(bars.size() - 3), b = bars.get(bars.size() - 2), d = bars.get(bars.size() - 1);
        double body = Math.abs(d.close - d.open), range = d.high - d.low;
        double upper = d.high - Math.max(d.close, d.open), lower = Math.min(d.close, d.open) - d.low;
        boolean down = a.close > b.close, up = a.close < b.close;
        if (range > 0 && body >= range * 0.05 && body <= range * 0.35) {
            if (down && lower >= 2 * body && upper <= body * 0.5) p.add("HAMMER");
            if (up && upper >= 2 * body && lower <= body * 0.5) p.add("SHOOTING_STAR");
        }
        if (b.close < b.open && d.close > d.open && d.open <= b.close && d.close >= b.open) p.add("ENGULFING_BULL");
        if (b.close > b.open && d.close < d.open && d.open >= b.close && d.close <= b.open) p.add("ENGULFING_BEAR");
        double firstBody = Math.abs(a.close - a.open), middleBody = Math.abs(b.close - b.open);
        if (firstBody > 0 && middleBody < firstBody * 0.3) {
            if (a.close < a.open && d.close > d.open && d.close > (a.open + a.close) / 2
                    && Math.max(b.open, b.close) < a.close) p.add("MORNING_STAR");
            if (a.close > a.open && d.close < d.open && d.close < (a.open + a.close) / 2
                    && Math.min(b.open, b.close) > a.close) p.add("EVENING_STAR");
        }
        if (Ohlcv.valid(bars, c.candleIntervalMs, System.currentTimeMillis(), 60)
                && Ohlcv.analyze(bars, c.candleIntervalMs, System.currentTimeMillis()).volumeRatio >= 2) p.add("VOL_SURGE");

        return p;
    }

    public static int scoreAdj(List<String> p) {
        int s = 0;
        for (String n : p) {
            switch (n) {
                case "PUMP_SETUP":     s += 12; break;
                case "MOMENTUM_ACCEL": s += 10; break;
                case "BREAKOUT_UP":    s +=  8; break;
                case "HAMMER":         s +=  8; break;
                case "MORNING_STAR":   s +=  7; break;
                case "ENGULFING_BULL": s +=  8; break;
                case "ACCUMULATION":   s +=  5; break;
                case "BULL_FLAG":      s +=  6; break;
                case "VOL_SURGE":      s +=  4; break;
                case "SHOOTING_STAR":  s -= 10; break;
                case "ENGULFING_BEAR": s -= 10; break;
                case "DISTRIBUTION":   s -=  8; break;
                case "DEAD_CAT":       s -=  8; break;
                case "EVENING_STAR":   s -=  7; break;
                case "BEAR_FLAG":      s -=  6; break;
                case "BREAKOUT_DN":    s -=  6; break;
                case "WASH_VOL":       s -=  8; break;
                case "CLIMAX":         s -=  5; break;
                case "VOL_DRY":        s -=  3; break;
            }
        }
        return s;
    }

    public static boolean bullish(List<String> p) {
        for (String n : p)
            if ("PUMP_SETUP".equals(n)||"MOMENTUM_ACCEL".equals(n)||"BREAKOUT_UP".equals(n)||
                "ENGULFING_BULL".equals(n)||"HAMMER".equals(n)||"MORNING_STAR".equals(n)) return true;
        return false;
    }

    public static boolean bearish(List<String> p) {
        for (String n : p)
            if ("SHOOTING_STAR".equals(n)||"ENGULFING_BEAR".equals(n)||"DISTRIBUTION".equals(n)||
                "DEAD_CAT".equals(n)||"WASH_VOL".equals(n)||"EVENING_STAR".equals(n)) return true;
        return false;
    }

    public static String summary(List<String> p) {
        if (p.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, p.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(p.get(i).replace("_", " "));
        }
        if (p.size() > 2) sb.append(" +").append(p.size() - 2);
        return sb.toString();
    }
}
