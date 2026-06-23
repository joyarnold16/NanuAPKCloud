package com.nanu.aitradingbot;

import java.util.List;
import java.util.Locale;

public final class BotEvolution {

    public static final int MIN_TRADES = 1;

    private static final class S {
        final String name;
        final double mom, stop, target, minLiq;
        final int minAge;
        S(String n, double m, double s, double t, double l, int a) {
            name=n; mom=m; stop=s; target=t; minLiq=l; minAge=a;
        }
    }

    private static final S[] POOL = {
        new S("BALANCED",     1.0,  5.0, 10.0,  50_000, 2),
        new S("MOMENTUM",     3.5,  3.5,  7.0,  50_000, 1),
        new S("CONSERVATIVE", 0.5,  9.0, 20.0, 200_000, 8),
        new S("VOLUME_SPIKE", 2.0,  5.0, 12.0,  75_000, 1),
        new S("ACCUMULATION", 0.3,  7.0, 22.0, 100_000,12),
        new S("SNIPER",       5.0,  3.0,  6.0,  25_000, 0),
        new S("SAFE_LARGE",   0.5,  8.0, 15.0, 500_000, 8),
        new S("ANTI_DUMP",    1.5,  6.5, 11.0,  75_000, 4),
    };

    public static final class Result {
        public boolean evolved;
        public String summary = "";
    }

    public static Result evolve(List<TradeRecord> history, DexAppStore store) {
        Result r = new Result();
        if (history.isEmpty()) return r;

        S best = null; double bestScore = -1;
        for (S s : POOL) {
            double sc = score(history, s);
            if (sc > bestScore) { bestScore = sc; best = s; }
        }
        if (best == null) return r;

        store.minMomentumPercent = best.mom;
        store.stopLossPercent    = best.stop;
        store.takeProfitPercent  = best.target;
        if (best.minLiq > store.minLiquidityUsd) store.minLiquidityUsd = best.minLiq;
        if (best.minAge > 0) store.minPairAgeHours = best.minAge;
        store.evolutionGeneration++;

        int wins = countWins(history);
        double wr = history.size() > 0 ? wins * 100.0 / history.size() : 0;
        store.evolutionSummary = String.format(Locale.US,
            "%s %.0f%% WR (%d trades) SL=%.1f%% TP=%.1f%%",
            best.name, wr, history.size(), best.stop, best.target);

        r.evolved = true;
        r.summary = store.evolutionSummary;
        return r;
    }

    private static double score(List<TradeRecord> history, S s) {
        int wins = 0, total = 0;
        for (TradeRecord r : history) {
            if (r.liquidityUsd > 0 && r.liquidityUsd < s.minLiq) continue;
            if (r.pairAgeMs > 0 && r.pairAgeMs < s.minAge * 3_600_000L) continue;
            total++;
            boolean win;
            if (r.pnlPct >= s.target * 0.7) {
                win = true;
            } else if ("paper stop hit".equals(r.exitReason)) {
                win = s.stop > 7.0 && r.pnlPct > -s.stop * 0.6;
            } else {
                win = r.win;
            }
            if (win) wins++;
        }
        if (total == 0) return 0;
        double wr = (double) wins / total;
        double rr = Math.min(0.12, (s.target / s.stop - 1.0) * 0.025);
        return wr + rr;
    }

    public static int countWins(List<TradeRecord> records) {
        int w = 0; for (TradeRecord r : records) if (r.win) w++; return w;
    }
}
