package com.nanu.aitradingbot;

/** Conservative constant-product approximation, NOT a router quote or transaction simulation. */
public final class PaperExecution {
    public static final double POOL_FEE = 0.003, LATENCY_SLIPPAGE = 0.002;
    public static double gas(String chain) { return "bsc".equals(chain) ? 0.15 : 0.02; }
    public static double impact(double notional, double liquidity) {
        if (!Ohlcv.positive(notional) || !Ohlcv.positive(liquidity)) throw new IllegalArgumentException("Invalid execution inputs");
        return notional / (liquidity / 2 + notional);
    }
    public static double friction(double notional, double liquidity, double tax) {
        if (!Ohlcv.finite(tax) || tax < 0 || tax >= 1) throw new IllegalArgumentException("Unknown/invalid tax");
        return 1 - (1 - POOL_FEE) * (1 - LATENCY_SLIPPAGE) * (1 - impact(notional, liquidity)) * (1 - tax);
    }
    public static double quantity(double budget, double price, double liquidity, double tax, String chain) {
        if (!Ohlcv.positive(price) || budget <= gas(chain)) throw new IllegalArgumentException("Invalid entry");
        double spend = budget - gas(chain);
        return spend * (1 - friction(spend, liquidity, tax)) / price;
    }
    public static double proceeds(double quantity, double price, double liquidity, double tax, String chain) {
        double gross = quantity * price;
        return gross * (1 - friction(gross, liquidity, tax)) - gas(chain);
    }
    public static void requirePaper(String mode) {
        if (!"paper".equals(mode)) throw new SecurityException("Real-money DEX execution is blocked in this build");
    }
}
