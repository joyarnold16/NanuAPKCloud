package com.nanu.aitradingbot;

import java.util.Locale;

/** A public DEX pair observation. It never contains a wallet secret or an order instruction. */
public final class DexCandidate {
    public String chain = "";
    public String tokenAddress = "";
    public String symbol = "";
    public String name = "";
    public String pairAddress = "";
    public String dexId = "";
    public double priceUsd;
    public double liquidityUsd;
    public double volume24hUsd;
    public double change1h;
    public double change24h;
    public int buys24h;
    public int sells24h;
    public long pairCreatedAtMs;
    public String sourceUrl = "";

    public int riskScore;
    public String decision = "WATCHING";
    public String reason = "Waiting for a market scan.";

    public boolean isBsc() { return "bsc".equalsIgnoreCase(chain); }
    public boolean isSolana() { return "solana".equalsIgnoreCase(chain); }

    public String label() {
        String safe = symbol == null || symbol.trim().isEmpty() ? "TOKEN" : symbol.trim().toUpperCase(Locale.US);
        return safe + " / " + (isBsc() ? "BNB Chain" : "Solana");
    }

    public String priceLabel() {
        if (!(priceUsd > 0d)) return "Price unavailable";
        if (priceUsd >= 1d) return String.format(Locale.US, "$%.4f", priceUsd);
        return String.format(Locale.US, "$%.8f", priceUsd);
    }

    public String liquidityLabel() {
        return liquidityUsd > 0d ? String.format(Locale.US, "$%,.0f liquidity", liquidityUsd) : "Liquidity unavailable";
    }
}
