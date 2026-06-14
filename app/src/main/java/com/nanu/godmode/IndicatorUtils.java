package com.nanu.godmode;

import java.util.List;

public class IndicatorUtils {
    public static double ema(List<Double> values, int period) {
        if (values == null || values.isEmpty()) return 0;
        double k = 2.0 / (period + 1.0);
        double ema = values.get(0);
        for (int i = 1; i < values.size(); i++) ema = values.get(i) * k + ema * (1.0 - k);
        return ema;
    }

    public static double rsi(List<Double> closes, int period) {
        if (closes == null || closes.size() <= period) return 50;
        double gains = 0, losses = 0;
        int start = closes.size() - period;
        for (int i = start; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff >= 0) gains += diff; else losses += -diff;
        }
        if (losses == 0) return 100;
        double rs = (gains / period) / (losses / period);
        return 100 - (100 / (1 + rs));
    }

    public static double macdHist(List<Double> closes) {
        double fast = ema(closes, 12);
        double slow = ema(closes, 26);
        double macd = fast - slow;
        // Simple enough for mobile v1: compare MACD against zero as histogram proxy.
        return macd;
    }

    public static double pct(double from, double to) {
        if (from == 0) return 0;
        return ((to - from) / from) * 100.0;
    }
}
