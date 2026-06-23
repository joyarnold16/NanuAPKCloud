package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DexSafetyPolicy {
    public static final class Report {
        public final int score;
        public final String decision;
        public final String reason;
        Report(int score, String decision, String reason) {
            this.score = score; this.decision = decision; this.reason = reason;
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
        if (c.liquidityUsd < Math.max(1_000d, minLiquidityUsd))
            blocks.add("liquidity below $" + fmt(Math.max(1_000d, minLiquidityUsd)));
        if (c.volume24hUsd < Math.max(500d, minVolumeUsd))
            blocks.add("24h volume below $" + fmt(Math.max(500d, minVolumeUsd)));

        long ageMs = c.pairCreatedAtMs <= 0L ? 0L : System.currentTimeMillis() - c.pairCreatedAtMs;
        long requiredAgeMs = Math.max(1, minPairAgeHours) * 60L * 60L * 1000L;
        if (ageMs <= 0L || ageMs < requiredAgeMs) blocks.add("pair is newer than required age");

        int trades = Math.max(0, c.buys24h) + Math.max(0, c.sells24h);
        if (trades < 20) blocks.add("too little buy/sell activity");

        // Honeypot signal: almost no sells vs buys
        if (trades > 30 && c.sells24h > 0 && c.buys24h > 0 && (double) c.buys24h / c.sells24h > 15)
            blocks.add("honeypot signal: " + c.sells24h + " sells vs " + c.buys24h + " buys");
        if (trades > 0 && c.sells24h * 100 < c.buys24h * 12)
            blocks.add("unusually low sell activity");

        // Wash trading: volume/liquidity ratio > 20
        if (c.liquidityUsd > 0 && c.volume24hUsd / c.liquidityUsd > 20) {
            score -= 15;
            warnings.add("vol/liq ratio " + String.format(Locale.US, "%.0f", c.volume24hUsd / c.liquidityUsd) + "x (possible wash trading)");
        }

        // Sell pressure: > 70% sells = distribution
        if (trades > 50 && c.sells24h > trades * 0.70) {
            score -= 20;
            warnings.add("high sell pressure (" + (c.sells24h * 100 / trades) + "% sells)");
        }

        if (Math.abs(c.change1h) > 45d) { score -= 30; warnings.add("extreme 1h move"); }
        if (Math.abs(c.change24h) > 300d) { score -= 20; warnings.add("extreme 24h move"); }
        if (c.liquidityUsd < minLiquidityUsd * 2d) { score -= 10; warnings.add("thin liquidity buffer"); }

        if (!blocks.isEmpty()) return new Report(Math.max(0, score - 45), "BLOCKED", join(blocks));
        if (score < 70) return new Report(score, "WATCHING", join(warnings));
        String reason = warnings.isEmpty()
            ? "Liquidity, age, volume and activity passed all local filters."
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
        for (String v : values) { if (out.length() > 0) out.append("; "); out.append(v); }
        return out.length() == 0 ? "No additional warning." : out.toString();
    }
    private static boolean blank(String v) { return v == null || v.trim().isEmpty(); }
    private static String fmt(double v) { return String.format(Locale.US, "%,.0f", v); }
}
