package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BotEvolution {
    public static final int MIN_TRADES = 5;

    public static class Result {
        public boolean evolved;
        public String summary = "";
    }

    public static Result evolve(List<TradeRecord> history, DexAppStore store) {
        Result result = new Result();
        if (history.size() < MIN_TRADES) {
            result.summary = "Need " + MIN_TRADES + " paper trades to evolve. Have " + history.size() + ".";
            return result;
        }
        List<String> changes = new ArrayList<>();
        int wins = countWins(history);
        int winPct = wins * 100 / history.size();

        // Evolve minLiquidityUsd
        {
            double cur = store.minLiquidityUsd;
            List<TradeRecord> above = new ArrayList<>(), below = new ArrayList<>();
            for (TradeRecord r : history) { if (r.liquidityUsd >= cur) above.add(r); else below.add(r); }
            if (above.size() >= 2 && below.size() >= 2) {
                double diff = winRate(above) - winRate(below);
                if (Math.abs(diff) >= 0.15) {
                    double next = diff > 0 ? Math.min(500_000, cur * 1.15) : Math.max(5_000, cur * 0.85);
                    if (Math.abs(next - cur) >= cur * 0.02) {
                        store.minLiquidityUsd = next;
                        changes.add(String.format(Locale.US, "liq $%.0f→$%.0f", cur, next));
                    }
                }
            }
        }

        // Evolve minVolumeUsd
        {
            double cur = store.minVolumeUsd;
            List<TradeRecord> above = new ArrayList<>(), below = new ArrayList<>();
            for (TradeRecord r : history) { if (r.volume24hUsd >= cur) above.add(r); else below.add(r); }
            if (above.size() >= 2 && below.size() >= 2) {
                double diff = winRate(above) - winRate(below);
                if (Math.abs(diff) >= 0.15) {
                    double next = diff > 0 ? Math.min(200_000, cur * 1.15) : Math.max(1_000, cur * 0.85);
                    if (Math.abs(next - cur) >= cur * 0.02) {
                        store.minVolumeUsd = next;
                        changes.add(String.format(Locale.US, "vol $%.0f→$%.0f", cur, next));
                    }
                }
            }
        }

        // Evolve minMomentumPercent
        {
            double cur = store.minMomentumPercent;
            List<TradeRecord> above = new ArrayList<>(), below = new ArrayList<>();
            for (TradeRecord r : history) { if (r.change1h >= cur) above.add(r); else below.add(r); }
            if (above.size() >= 2 && below.size() >= 2) {
                double diff = winRate(above) - winRate(below);
                if (Math.abs(diff) >= 0.15) {
                    double next = diff > 0 ? Math.min(15.0, cur * 1.15) : Math.max(0.2, cur * 0.85);
                    if (Math.abs(next - cur) >= cur * 0.02) {
                        store.minMomentumPercent = next;
                        changes.add(String.format(Locale.US, "momentum %.1f%%→%.1f%%", cur, next));
                    }
                }
            }
        }

        // Evolve stop/target from actual P&L outcomes
        {
            List<TradeRecord> wL = new ArrayList<>(), lL = new ArrayList<>();
            for (TradeRecord r : history) { if (r.win) wL.add(r); else lL.add(r); }
            if (wL.size() >= 2 && lL.size() >= 2) {
                double avgWin = 0; for (TradeRecord r : wL) avgWin += r.pnlPct; avgWin /= wL.size();
                double avgLoss = 0; for (TradeRecord r : lL) avgLoss += Math.abs(r.pnlPct); avgLoss /= lL.size();
                if (avgLoss > store.stopLossPercent * 1.2 && store.stopLossPercent > 3) {
                    double ns = Math.max(3, store.stopLossPercent * 0.9);
                    changes.add(String.format(Locale.US, "stop %.1f%%→%.1f%%", store.stopLossPercent, ns));
                    store.stopLossPercent = ns;
                }
                if (avgWin > store.takeProfitPercent * 0.8 && store.takeProfitPercent < 40) {
                    double nt = Math.min(40, store.takeProfitPercent * 1.1);
                    changes.add(String.format(Locale.US, "target %.1f%%→%.1f%%", store.takeProfitPercent, nt));
                    store.takeProfitPercent = nt;
                }
            }
        }

        result.evolved = !changes.isEmpty();
        if (result.evolved) {
            store.evolutionGeneration++;
            result.summary = String.format(Locale.US,
                "Gen %d | %d%% win from %d trades | %s",
                store.evolutionGeneration, winPct, history.size(), join(changes));
        } else {
            result.summary = String.format(Locale.US,
                "Gen %d stable | %d%% win from %d trades | params unchanged",
                store.evolutionGeneration, winPct, history.size());
        }
        return result;
    }

    public static int countWins(List<TradeRecord> records) {
        int c = 0; for (TradeRecord r : records) if (r.win) c++; return c;
    }

    private static double winRate(List<TradeRecord> records) {
        return records.isEmpty() ? 0 : (double) countWins(records) / records.size();
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
        return sb.toString();
    }
}
