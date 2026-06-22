package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic pre-trade filters. A pass is a risk reduction, never proof that a token is safe. */
public final class DexSafetyPolicy {
    public static final class Report {
        public final int score;
        public final String decision;
        public final String reason;

        Report(int score, String decision, String reason) {
            this.score = score;
            this.decision = decision;
            this.reason = reason;
        }
    }

    private DexSafetyPolicy() {}

    public static Report evaluate(DexCandidate c, double minLiquidityUsd, double minVolumeUsd, int minPairAgeHours) {
        if (c == null || (!c.isBsc() && !c.isSolana())) return new Report(0, "BLOCKED", "Unsupported chain.");
        List<String> blocks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int score = 100;

        if (blank(c.tokenAddress) || blank(c.pairAddress)) blocks.add("missing token or pair address");
        if (!(c.priceUsd > 0d)) blocks.add("no usable USD price");
        if (c.liquidityUsd < Math.max(1_000d, minLiquidityUsd)) {
            blocks.add("liquidity below $" + fmt(Math.max(1_000d, minLiquidityUsd)));
        }
        if (c.volume24hUsd < Math.max(500d, minVolumeUsd)) {
            blocks.add("24h volume below $" + fmt(Math.max(500d, minVolumeUsd)));
        }
        long ageMs = c.pairCreatedAtMs <= 0L ? 0L : System.currentTimeMillis() - c.pairCreatedAtMs;
        long requiredAgeMs = Math.max(1, minPairAgeHours) * 60L * 60L * 1000L;
        if (ageMs <= 0L || ageMs < requiredAgeMs) blocks.add("pair is newer than the required age");

        int trades = Math.max(0, c.buys24h) + Math.max(0, c.sells24h);
        if (trades < 20) blocks.add("too little buy/sell activity");
        if (trades > 0 && c.sells24h * 100 < c.buys24h * 12) {
            blocks.add("unusually low sell activity");
        }
        if (Math.abs(c.change1h) > 45d) {
            score -= 30;
            warnings.add("extreme one-hour price movement");
        }
        if (Math.abs(c.change24h) > 300d) {
            score -= 20;
            warnings.add("extreme daily price movement");
        }
        if (c.liquidityUsd < minLiquidityUsd * 2d) {
            score -= 10;
            warnings.add("thin liquidity buffer");
        }

        if (!blocks.isEmpty()) return new Report(Math.max(0, score - 45), "BLOCKED", join(blocks));
        if (score < 70) return new Report(score, "WATCHING", join(warnings));
        String reason = warnings.isEmpty()
                ? "Liquidity, age, volume and buy/sell activity passed local filters."
                : "Passed hard filters; caution: " + join(warnings);
        return new Report(score, "QUALIFIED", reason);
    }

    public static boolean canOpenPaperPosition(DexCandidate c, double minMomentumPercent) {
        return c != null && "QUALIFIED".equals(c.decision)
                && c.change1h >= Math.max(0.1d, minMomentumPercent)
                && c.change1h <= 25d && c.change24h > -25d;
    }

    public static boolean validAmount(double value, double lower, double upper) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= lower && value <= upper;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append("; ");
            out.append(value);
        }
        return out.length() == 0 ? "No additional market warning." : out.toString();
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String fmt(double value) { return String.format(Locale.US, "%,.0f", value); }
}
