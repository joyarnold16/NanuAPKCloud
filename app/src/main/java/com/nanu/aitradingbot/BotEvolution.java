package com.nanu.aitradingbot;

import java.util.List;
import java.util.Locale;

public final class BotEvolution {
    public static final int MIN_TRADES = 5;

    public static class Result {
        public boolean evolved;
        public String summary = "";
    }

    public static Result evolve(List<TradeRecord> history, DexAppStore store) {
        Result r = new Result();
        if (history == null || history.size() < MIN_TRADES) {
            r.summary = "Not enough trades for evolution."; return r;
        }
        double curRate = winRate(history);
        double origMom = store.minMomentumPercent;
        double origStop = store.stopLossPercent;
        double origTarget = store.takeProfitPercent;
        double bestRate = curRate, bestMom = origMom, bestStop = origStop, bestTarget = origTarget;

        for (double m : new double[]{origMom * 1.2, origMom * 0.8, origMom + 0.5, Math.max(0.1, origMom - 0.25)}) {
            if (m < 0.1 || m > 20) continue;
            double rate = simulate(history, m, bestStop, bestTarget);
            if (rate > bestRate) { bestRate = rate; bestMom = m; }
        }
        for (double[] st : new double[][]{{origStop * 0.8, origTarget * 1.2}, {origStop * 1.2, origTarget * 0.8}, {origStop, origTarget * 1.1}, {origStop * 0.9, origTarget}}) {
            if (st[0] < 0.5 || st[0] > 50 || st[1] < 0.5 || st[1] > 100) continue;
            double rate = simulate(history, bestMom, st[0], st[1]);
            if (rate > bestRate) { bestRate = rate; bestStop = st[0]; bestTarget = st[1]; }
        }

        StringBuilder sb = new StringBuilder("Gen ").append(store.evolutionGeneration + 1).append(": ");
        boolean changed = false;
        if (Math.abs(bestMom - origMom) > 0.01) {
            store.minMomentumPercent = bestMom;
            sb.append(String.format(Locale.US, "mom %.1f\u2192%.1f%% ", origMom, bestMom)); changed = true;
        }
        if (Math.abs(bestStop - origStop) > 0.01) {
            store.stopLossPercent = bestStop;
            sb.append(String.format(Locale.US, "stop %.1f\u2192%.1f%% ", origStop, bestStop)); changed = true;
        }
        if (Math.abs(bestTarget - origTarget) > 0.01) {
            store.takeProfitPercent = bestTarget;
            sb.append(String.format(Locale.US, "target %.1f\u2192%.1f%% ", origTarget, bestTarget)); changed = true;
        }
        if (changed) {
            store.evolutionGeneration++;
            r.evolved = true;
            r.summary = sb.append(String.format(Locale.US, "(%.0f%%\u2192%.0f%%)", curRate * 100, bestRate * 100)).toString().trim();
        } else {
            r.summary = String.format(Locale.US, "Gen %d: no improvement (%.0f%% win rate, %d trades).", store.evolutionGeneration, curRate * 100, history.size());
        }
        return r;
    }

    public static int countWins(List<TradeRecord> records) {
        int w = 0;
        for (TradeRecord r : records) if (r.win) w++;
        return w;
    }

    private static double winRate(List<TradeRecord> records) {
        return records.isEmpty() ? 0 : (double) countWins(records) / records.size();
    }

    private static double simulate(List<TradeRecord> records, double mom, double stop, double target) {
        int wins = 0, total = 0;
        for (TradeRecord r : records) {
            if (r.change1h < mom) continue;
            total++;
            if (r.pnlPct >= target || r.pnlPct > -stop) wins++;
        }
        return total == 0 ? winRate(records) : (double) wins / total;
    }
}
