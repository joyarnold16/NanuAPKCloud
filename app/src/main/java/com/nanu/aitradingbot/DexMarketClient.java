package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;

/** Compatibility facade for read-only discovery. */
public final class DexMarketClient {
    public interface Callback { void done(List<DexCandidate> candidates, String status); }
    public static void discover(DexAppStore store, Callback callback) {
        final double liquidity = store.minLiquidityUsd, volume = store.minVolumeUsd;
        final int age = store.minPairAgeHours;
        new Thread(() -> {
            try {
                List<DexCandidate> items = new DexDataClient().discover(liquidity, volume, age);
                callback.done(items, "Screened " + items.size() + " pairs; missing evidence blocks entries.");
            } catch (Exception e) { callback.done(new ArrayList<>(), "Discovery unavailable; no new entries."); }
        }, "nanu-dex-discovery").start();
    }
}
