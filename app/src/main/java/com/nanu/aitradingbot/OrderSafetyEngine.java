package com.nanu.aitradingbot;

import java.util.Locale;

public class OrderSafetyEngine {
    public static class Preview {
        public boolean pass;
        public String symbol;
        public String side;
        public double price;
        public double quoteAmount;
        public double rawQty;
        public double roundedQty;
        public double minNotional;
        public String report;
    }

    public static Preview buildDryRunPreview(AppStore store) {
        store.ensureDailySafetyWindow();
        Preview p = new Preview();
        p.symbol = chooseSymbol(store);
        p.side = "BUY";
        p.price = liveOrFallbackPrice(store, p.symbol);
        p.quoteAmount = Math.max(5.0, store.liveDryRunOrderUsdt);
        p.minNotional = Math.max(5.0, store.minOrderNotionalUsdt);
        p.rawQty = p.quoteAmount / Math.max(0.00000001, p.price);
        p.roundedQty = roundQty(p.symbol, p.rawQty);

        StringBuilder out = new StringBuilder();
        out.append("Nanu Order Safety Engine Tablet Edition\n\n");
        out.append("CONTROLLED LIVE DRY-RUN\n");
        out.append("This preview sends no order. It must pass before a manual protected Spot BUY can run.\n\n");
        out.append("Mode: ").append(store.mode.toUpperCase(Locale.US)).append('\n');
        out.append("Symbol: ").append(p.symbol).append('\n');
        out.append("Side: ").append(p.side).append('\n');
        out.append("Preview amount: ").append(fmt(p.quoteAmount)).append(" USDT\n");
        out.append("Estimated UI price: ").append(priceFmt(p.price)).append(" (final order validates live Binance rules)\n");
        out.append("Raw quantity: ").append(qtyFmt(p.rawQty)).append('\n');
        out.append("Rounded quantity: ").append(qtyFmt(p.roundedQty)).append('\n');
        out.append("Stop-loss: ").append(fmt(store.stopLoss)).append("%\n");
        out.append("Take-profit: ").append(fmt(store.takeProfit)).append("%\n");
        out.append("Slippage limit: ").append(fmt(store.slippageLimitPct)).append("%\n\n");

        boolean ok = true;
        ok &= check(out, "LIVE selected", "live".equals(store.mode));
        ok &= check(out, "Live unlock gate passed", store.liveUnlocked);
        ok &= check(out, "API Doctor private OK", store.apiDoctorOkForCurrentMode());
        ok &= check(out, "Spot trading permission OK", store.apiTradingOkForCurrentMode());
        ok &= check(out, "Spot portfolio synced", store.portfolioSyncOk && System.currentTimeMillis() - store.lastPortfolioSyncMs <= 10 * 60 * 1000L);
        if (!Double.isNaN(store.spotFreeUsdt)) ok &= check(out, "Free USDT covers preview amount", store.spotFreeUsdt >= p.quoteAmount);
        ok &= check(out, "Withdrawals confirmed OFF", store.withdrawalPermissionConfirmedOff);
        ok &= check(out, "Telegram Doctor PASS", store.telegramDoctorOk);
        ok &= check(out, "Profit Guard ON", store.profitGuardEnabled);
        ok &= check(out, "Panic button tested", store.panicButtonTested);
        ok &= check(out, "Order amount >= min notional", p.quoteAmount >= p.minNotional);
        ok &= check(out, "Rounded quantity > 0", p.roundedQty > 0);
        int openDryRunTrades = Math.max(0, store.liveDryRunOpenTrades);
        int maxOpenTrades = Math.max(1, store.maxOpenTrades);
        ok &= check(out, "Open dry-run trades: " + openDryRunTrades + " / " + maxOpenTrades, openDryRunTrades < maxOpenTrades);
        ok &= check(out, "Max live trades/day not exceeded", store.liveTradesToday < Math.max(1, store.maxLiveTradesPerDay));
        long now = System.currentTimeMillis();
        ok &= check(out, "Order cooldown clear", now >= store.orderCooldownUntilMs);
        ok &= check(out, "Dry-run mode is ON", store.liveDryRunEnabled);
        ok &= check(out, "Compliance Guard ON", store.complianceGuardEnabled);
        ok &= check(out, "Binance rate-limit lock clear", !store.binanceRateLimitLock);
        ok &= check(out, "No unresolved Binance protection check", !store.hasPendingProtectionCheck());
        ok &= check(out, "Manual first-order confirmation required", store.firstLiveOrderManualConfirm);
        ok &= check(out, "API key present", store.apiKey != null && !store.apiKey.trim().isEmpty());
        ok &= check(out, "API secret present", store.apiSecret != null && !store.apiSecret.trim().isEmpty());
        ok &= check(out, "BUY amount is within manual order limit", store.microLiveOrderUsdt <= store.manualOrderLimitUsdt);
        ok &= check(out, "Stop loss in safe range", store.stopLoss >= 0.1 && store.stopLoss <= 3.0);
        ok &= check(out, "Take profit in safe range", store.takeProfit >= 0.1 && store.takeProfit <= 5.0);
        ok &= check(out, "Slippage limit <= 0.50%", store.slippageLimitPct > 0 && store.slippageLimitPct <= 0.50);
        ok &= check(out, "Real order requires ARM or Test Order ON", store.liveOrderTestMode || store.liveRealOrderArmed);

        out.append("\nAction decision: ").append(ok ? "DRY-RUN PASS ✅" : "BLOCKED ⚠️").append("\n");
        if (ok) {
            out.append("Nanu is allowed to continue to manual confirmation. Test order mode is recommended before any real Spot BUY.\n");
            store.orderCooldownUntilMs = now + Math.max(10, store.orderCooldownSeconds) * 1000L;
            store.liveDryRunPassCount++;
            store.engine.addJournal("Live dry-run preview PASS: " + p.symbol + " " + fmt(p.quoteAmount) + " USDT.");
        } else {
            out.append("Fix blocked items before manual micro order.\n");
            store.engine.addJournal("Live dry-run preview BLOCKED: safety checklist not complete.");
        }
        out.append("\nSafety note: preview itself never places an order. A manual confirmed BUY requires a separate confirmation screen and requests Binance OCO exit protection after a real fill.");

        p.pass = ok;
        p.report = out.toString();
        store.lastOrderSymbol = p.symbol;
        store.lastOrderPreview = p.report;
        store.lastOrderSafetyPass = ok;
        store.lastOrderPreviewTime = System.currentTimeMillis();
        store.save();
        return p;
    }

    private static boolean check(StringBuilder out, String label, boolean pass) {
        out.append(pass ? "✅ " : "❌ ").append(label).append('\n');
        return pass;
    }

    private static String chooseSymbol(AppStore store) {
        if (store.scalperSymbol != null && BinanceClient.isTabletPair(store.scalperSymbol)) return store.scalperSymbol.trim().toUpperCase(Locale.US);
        if (store.watchlist != null && !store.watchlist.isEmpty() && BinanceClient.isTabletPair(store.watchlist.get(0))) return store.watchlist.get(0);
        return "BTCUSDT";
    }

    private static double liveOrFallbackPrice(AppStore store, String symbol) {
        if (store.lastScalperPrice > 0 && !Double.isNaN(store.lastScalperPrice)
                && symbol != null && symbol.equalsIgnoreCase(store.scalperSymbol)) {
            return store.lastScalperPrice;
        }
        return priceFor(symbol);
    }

    private static double priceFor(String s) {
        if (s == null) return 100.0;
        if (s.startsWith("BTC")) return 67842.10;
        if (s.startsWith("ETH")) return 3742.80;
        if (s.startsWith("SOL")) return 164.28;
        if (s.startsWith("BNB")) return 596.51;
        if (s.startsWith("XRP")) return 0.62;
        if (s.startsWith("DOGE")) return 0.13;
        if (s.startsWith("ADA")) return 0.43;
        return 100.00;
    }

    private static double roundQty(String symbol, double qty) {
        double step;
        if (symbol.startsWith("BTC")) step = 0.00001;
        else if (symbol.startsWith("ETH")) step = 0.0001;
        else if (symbol.startsWith("BNB") || symbol.startsWith("SOL")) step = 0.001;
        else step = 0.1;
        return Math.floor(qty / step) * step;
    }

    private static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }
    private static String priceFmt(double v) { return v < 10 ? String.format(Locale.US, "%.5f", v) : String.format(Locale.US, "%.2f", v); }
    private static String qtyFmt(double v) { return String.format(Locale.US, "%.8f", v); }
}
