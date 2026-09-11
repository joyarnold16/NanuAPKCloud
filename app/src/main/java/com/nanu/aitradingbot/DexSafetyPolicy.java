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
        if (!Ohlcv.positive(minLiquidityUsd) || !Ohlcv.positive(minVolumeUsd) || minPairAgeHours < 1)
            blocks.add("invalid safety settings");
        if (!Ohlcv.finite(c.liquidityUsd) || !Ohlcv.finite(c.volume24hUsd)
                || !Ohlcv.finite(c.change1h) || !Ohlcv.finite(c.change24h)) blocks.add("invalid market numbers");
        if (!PaperLedger.fresh(c, System.currentTimeMillis())) blocks.add("stale market observation");
        if (c.security != null && "BLOCKED".equals(c.security.status)) blocks.add(c.security.reason);

        if (!validAddress(c.chain, c.tokenAddress) || !validAddress(c.chain, c.pairAddress)) blocks.add("missing token or pair address");
        if (!Ohlcv.positive(c.priceUsd)) blocks.add("no usable USD price");
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
        if (!evidenceReady(c, System.currentTimeMillis()))
            return new Report(Math.min(score, 60), "WATCHING", c.security == null || !"PASS".equals(c.security.status)
                ? (c.security == null ? "Security unknown" : c.security.reason) : "Waiting for 60 valid closed OHLCV candles");
        if (score < 70) return new Report(score, "WATCHING", join(warnings));
        String reason = warnings.isEmpty()
            ? "Liquidity, age, volume and activity passed all local filters."
            : "Passed hard filters; caution: " + join(warnings);
        return new Report(score, "QUALIFIED", reason);
    }

    public static boolean evidenceReady(DexCandidate c, long now) {
        return c != null && c.security != null && "PASS".equals(c.security.status)
            && c.security.checkedAtMs > 0 && c.security.checkedAtMs <= now && now - c.security.checkedAtMs <= 300_000
            && Ohlcv.valid(c.candles, c.candleIntervalMs, now, 60);
    }
    public static boolean canOpenPaperPosition(DexCandidate c, double minMomentumPercent) {
        return canOpenPaperPosition(c, minMomentumPercent, System.currentTimeMillis());
    }
    public static boolean canOpenPaperPosition(DexCandidate c, double minMomentumPercent, long now) {
        if (c == null || !"QUALIFIED".equals(c.decision) || !PaperLedger.fresh(c, now) || !evidenceReady(c, now)) return false;
        Ohlcv.Indicators i = Ohlcv.analyze(c.candles, c.candleIntervalMs, now);
        return Ohlcv.finite(minMomentumPercent) && i.ema12 > i.ema26 && i.macd >= i.signal
            && c.change1h >= Math.max(0.1, minMomentumPercent) && c.change1h <= 25 && c.change24h > -25;
    }
    public static boolean validAddress(String chain, String value) {
        if (value == null) return false;
        if ("bsc".equals(chain)) return value.matches("0x[0-9a-fA-F]{40}");
        if ("solana".equals(chain)) {
            if (!value.matches("[1-9A-HJ-NP-Za-km-z]{32,44}")) return false;
            String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
            java.math.BigInteger number = java.math.BigInteger.ZERO;
            for (int i = 0; i < value.length(); i++) number = number.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(alphabet.indexOf(value.charAt(i))));
            int zeros = 0;
            while (zeros < value.length() && value.charAt(zeros) == '1') zeros++;
            return zeros + (number.bitLength() + 7) / 8 == 32;
        }
        return false;
    }
    public static boolean sameAddress(String chain, String left, String right) {
        return left != null && right != null && ("bsc".equals(chain) ? left.equalsIgnoreCase(right) : left.equals(right));
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
