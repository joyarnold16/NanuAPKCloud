package com.nanu.aitradingbot;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Map;

/** Pure automatic-entry policy helpers, independent from Android UI code. */
public final class AutoTradingPolicy {
    public static final String[] PAIRS = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT"};
    public static final double MINIMUM_MANUAL_PROTECTED_QUOTE_USDT = 6.0d;
    public static final double MINIMUM_AUTOMATIC_QUOTE_USDT = 15.0d;
    private static final double BASE_FEE_RESERVE = 0.998d;
    private static final double PROTECTION_NOTIONAL_MARGIN = 1.10d;
    private static final double OCO_CURRENT_PRICE_BUFFER = 0.0015d;
    private static final double OCO_STOP_LIMIT_BUFFER = 0.0015d;

    public static final class OcoLevels {
        public final double takeProfit;
        public final double stopPrice;
        public final double stopLimit;

        OcoLevels(double takeProfit, double stopPrice, double stopLimit) {
            this.takeProfit = takeProfit;
            this.stopPrice = stopPrice;
            this.stopLimit = stopLimit;
        }
    }

    private AutoTradingPolicy() {}

    /**
     * Leaves room for a base-asset commission, a stop below entry, and normal tick rounding.
     * The result is a local preflight floor; live symbol rules are checked again at order time.
     */
    public static double minimumProtectedQuote(double exchangeMinNotional, double stopLossPercent) {
        double minNotional = Math.max(0.01d, exchangeMinNotional);
        double stopFraction = 1d - clamp(stopLossPercent, 0.1d, 3.0d) / 100d;
        double required = minNotional / (BASE_FEE_RESERVE * stopFraction) * PROTECTION_NOTIONAL_MARGIN;
        return Math.max(MINIMUM_MANUAL_PROTECTED_QUOTE_USDT, ceilToCent(required));
    }

    /** Automatic market entries need a wider buffer because the OCO is submitted after the fill. */
    public static double minimumAutomaticProtectedQuote(double exchangeMinNotional, double stopLossPercent) {
        return Math.max(MINIMUM_AUTOMATIC_QUOTE_USDT, minimumProtectedQuote(exchangeMinNotional, stopLossPercent));
    }

    public static boolean entryWithinSlippage(double referencePrice, double entryPrice, double limitPercent) {
        if (!(referencePrice > 0d) || !(entryPrice > 0d) || !(limitPercent > 0d)) return false;
        return entryPrice <= referencePrice * (1d + limitPercent / 100d);
    }

    /**
     * Produces a valid sell OCO bracket. A price that has already reached the intended stop
     * returns null so the caller can use its emergency-close path instead of weakening the stop.
     */
    public static OcoLevels calculateOcoLevels(double entryPrice, double currentPrice,
                                                double takeProfitPercent, double stopLossPercent,
                                                double tickSize) {
        if (!(entryPrice > 0d) || !(currentPrice > 0d) || !(tickSize > 0d)) return null;
        double intendedStop = floorToStep(entryPrice * (1d - clamp(stopLossPercent, 0.1d, 3.0d) / 100d), tickSize);
        if (!(intendedStop > 0d) || currentPrice <= intendedStop) return null;

        double takeProfit = ceilToStep(entryPrice * (1d + clamp(takeProfitPercent, 0.1d, 5.0d) / 100d), tickSize);
        double minimumTakeProfit = ceilToStep(currentPrice * (1d + OCO_CURRENT_PRICE_BUFFER), tickSize);
        takeProfit = Math.max(takeProfit, minimumTakeProfit);

        double stopLimit = floorToStep(intendedStop * (1d - OCO_STOP_LIMIT_BUFFER), tickSize);
        if (!(takeProfit > currentPrice && currentPrice > intendedStop && intendedStop > stopLimit)) return null;
        return new OcoLevels(takeProfit, intendedStop, stopLimit);
    }

    public static String chooseBestBuy(Map<String, ScalpingStrategy.Signal> signals, int minimumConfidence) {
        String selected = "";
        int best = Math.max(60, Math.min(95, minimumConfidence)) - 1;
        if (signals == null) return selected;
        for (String symbol : PAIRS) {
            ScalpingStrategy.Signal signal = signals.get(symbol);
            if (signal == null || signal.action != ScalpingStrategy.Action.BUY || signal.confidence < minimumConfidence) continue;
            if (signal.confidence > best) {
                selected = symbol;
                best = signal.confidence;
            }
        }
        return selected;
    }

    public static boolean isStaticIp(String value) {
        if (value == null) return false;
        String ip = value.trim();
        if (ip.isEmpty() || ip.contains(" ")) return false;
        if (ip.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(ip) instanceof Inet6Address;
            } catch (Exception ignored) {
                return false;
            }
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                if (part.isEmpty() || (part.length() > 1 && part.startsWith("0"))) return false;
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean publicIpMatches(String expected, String current) {
        return isStaticIp(expected)
                && isStaticIp(current)
                && expected.trim().equalsIgnoreCase(current.trim());
    }

    public static String runtimeState(boolean scannerRunning, boolean automaticRunning, boolean scannerPanic, boolean automaticPanic) {
        if (scannerPanic || automaticPanic) return "PANIC";
        return scannerRunning || automaticRunning ? "ACTIVE" : "IDLE";
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double floorToStep(double value, double step) {
        return Math.floor((value + step * 1e-9d) / step) * step;
    }

    private static double ceilToStep(double value, double step) {
        return Math.ceil((value - step * 1e-9d) / step) * step;
    }

    private static double ceilToCent(double value) {
        return Math.ceil(value * 100d - 1e-9d) / 100d;
    }
}
