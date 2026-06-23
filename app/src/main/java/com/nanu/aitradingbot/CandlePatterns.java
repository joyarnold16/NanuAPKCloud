package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;

public final class CandlePatterns {

    public static List<String> detect(DexCandidate c) {
        List<String> p = new ArrayList<>();
        double h1 = c.change1h, h24 = c.change24h;
        double total = c.buys24h + c.sells24h;
        double buyR = total > 0 ? (double) c.buys24h / total : 0.5;
        double volR = c.liquidityUsd > 0 ? c.volume24hUsd / c.liquidityUsd : 0;
        double ageH = c.pairCreatedAtMs > 0 ? (System.currentTimeMillis() - c.pairCreatedAtMs) / 3_600_000.0 : 0;

        // Momentum
        if (h1 > 0 && h24 > 0 && h1 > h24 / 12.0 * 2.5)         p.add("MOMENTUM_ACCEL");
        if (Math.abs(h1) > 3.0 && Math.abs(h24) < 8.0)            p.add(h1 > 0 ? "BREAKOUT_UP" : "BREAKOUT_DN");
        if (h1 > 1.0 && h1 < 15.0 && buyR > 0.65 && volR > 2.0)  p.add("PUMP_SETUP");

        // Reversals
        if (h24 < -15.0 && h1 > 2.0 && buyR > 0.55)              p.add("HAMMER");
        if (h24 > 20.0  && h1 < -2.0 && buyR < 0.45)             p.add("SHOOTING_STAR");
        if (h24 < -10.0 && h1 > 0.5  && h1 < 8.0 && buyR > 0.5) p.add("MORNING_STAR");
        if (h24 > 10.0  && h1 < -0.5 && h1 > -8.0 && buyR < 0.5)p.add("EVENING_STAR");
        if (h1 > 0 && buyR > 0.60 && volR > 5.0)                  p.add("ENGULFING_BULL");
        if (h1 < 0 && buyR < 0.40 && volR > 5.0)                  p.add("ENGULFING_BEAR");

        // Volume
        if (volR > 8.0)                                            p.add("VOL_SURGE");
        if (volR < 0.3 && total < 50)                              p.add("VOL_DRY");
        if (volR > 3.0 && buyR > 0.45 && buyR < 0.55 && total > 500) p.add("WASH_VOL");
        if (Math.abs(h24) > 100.0 && volR > 10.0)                 p.add("CLIMAX");

        // Trend
        if (Math.abs(h24) < 5.0 && buyR > 0.55 && ageH > 12)    p.add("ACCUMULATION");
        if (h24 > 30.0 && Math.abs(h1) < 2.0 && buyR < 0.48)    p.add("DISTRIBUTION");
        if (h24 < -40.0 && h1 > 1.0 && h1 < 8.0)                p.add("DEAD_CAT");
        if (h24 > 15.0 && h1 > -5.0 && h1 < 0 && buyR > 0.45)  p.add("BULL_FLAG");
        if (h24 < -15.0 && h1 > 0 && h1 < 5.0 && buyR < 0.55)  p.add("BEAR_FLAG");

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
