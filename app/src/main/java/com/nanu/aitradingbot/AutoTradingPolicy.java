package com.nanu.aitradingbot;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Map;

/** Pure automatic-entry policy helpers, independent from Android UI code. */
public final class AutoTradingPolicy {
    public static final String[] PAIRS = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT"};

    private AutoTradingPolicy() {}

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
}
