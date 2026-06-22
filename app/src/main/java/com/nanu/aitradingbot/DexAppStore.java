package com.nanu.aitradingbot;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** DEX-only persistent state. Credentials from the retired Binance path are intentionally not read here. */
public final class DexAppStore {
    private static DexAppStore instance;
    private final SharedPreferences prefs;
    private final SecurePrefs secure;

    public String activeChain = "bsc";
    public boolean scannerRunning;
    public boolean paperAuto = true;
    public boolean liveDexArmed;
    public boolean panic;
    public boolean appPinEnabled;
    public String appPinHash = "";
    public String bscAddress = "";
    public String solanaAddress = "";
    public String approvedBscReturnAddress = "";
    public String approvedSolanaReturnAddress = "";
    public String walletCreatedAt = "";
    public double maxTradeUsd = 10d;
    public double maxDailyLossUsd = 5d;
    public int maxTradesPerDay = 2;
    public int tradesToday;
    public double minLiquidityUsd = 25_000d;
    public double minVolumeUsd = 10_000d;
    public int minPairAgeHours = 24;
    public double maxSlippagePercent = 1d;
    public double stopLossPercent = 8d;
    public double takeProfitPercent = 15d;
    public int scanSeconds = 60;
    public String alertSoundUri = "";
    public String lastStatus = "Create a bot wallet, then start DEX discovery in paper mode.";
    public String lastCritical = "";
    public long lastHeartbeatMs;
    public String dailyKey = "";

    public final DexEngine engine;

    public static synchronized DexAppStore get(Context context) {
        if (instance == null) instance = new DexAppStore(context.getApplicationContext());
        return instance;
    }

    private DexAppStore(Context context) {
        prefs = context.getSharedPreferences("nanu_dex_v8", Context.MODE_PRIVATE);
        secure = new SecurePrefs(prefs);
        load();
        engine = new DexEngine(this);
    }

    public void load() {
        activeChain = prefs.getString("activeChain", "bsc");
        if (!"solana".equals(activeChain)) activeChain = "bsc";
        scannerRunning = prefs.getBoolean("scannerRunning", false);
        paperAuto = prefs.getBoolean("paperAuto", true);
        liveDexArmed = false; // Never carry a live arm across an app restart.
        panic = prefs.getBoolean("panic", false);
        appPinEnabled = prefs.getBoolean("appPinEnabled", false);
        appPinHash = prefs.getString("appPinHash", "");
        if (appPinEnabled && (appPinHash == null || appPinHash.isEmpty())) appPinEnabled = false;
        bscAddress = prefs.getString("bscAddress", "");
        solanaAddress = prefs.getString("solanaAddress", "");
        approvedBscReturnAddress = prefs.getString("approvedBscReturnAddress", "");
        approvedSolanaReturnAddress = prefs.getString("approvedSolanaReturnAddress", "");
        walletCreatedAt = prefs.getString("walletCreatedAt", "");
        maxTradeUsd = prefs.getFloat("maxTradeUsd", 10f);
        maxDailyLossUsd = prefs.getFloat("maxDailyLossUsd", 5f);
        maxTradesPerDay = prefs.getInt("maxTradesPerDay", 2);
        tradesToday = prefs.getInt("tradesToday", 0);
        minLiquidityUsd = prefs.getFloat("minLiquidityUsd", 25_000f);
        minVolumeUsd = prefs.getFloat("minVolumeUsd", 10_000f);
        minPairAgeHours = prefs.getInt("minPairAgeHours", 24);
        maxSlippagePercent = prefs.getFloat("maxSlippagePercent", 1f);
        stopLossPercent = prefs.getFloat("stopLossPercent", 8f);
        takeProfitPercent = prefs.getFloat("takeProfitPercent", 15f);
        scanSeconds = prefs.getInt("scanSeconds", 60);
        alertSoundUri = prefs.getString("alertSoundUri", "");
        lastStatus = prefs.getString("lastStatus", lastStatus);
        lastCritical = prefs.getString("lastCritical", "");
        lastHeartbeatMs = prefs.getLong("lastHeartbeatMs", 0L);
        dailyKey = prefs.getString("dailyKey", "");
        ensureDailyWindow();
    }

    public void save() {
        prefs.edit()
                .putString("activeChain", activeChain)
                .putBoolean("scannerRunning", scannerRunning)
                .putBoolean("paperAuto", paperAuto)
                .putBoolean("panic", panic)
                .putBoolean("appPinEnabled", appPinEnabled)
                .putString("appPinHash", appPinHash == null ? "" : appPinHash)
                .putString("bscAddress", bscAddress)
                .putString("solanaAddress", solanaAddress)
                .putString("approvedBscReturnAddress", approvedBscReturnAddress)
                .putString("approvedSolanaReturnAddress", approvedSolanaReturnAddress)
                .putString("walletCreatedAt", walletCreatedAt)
                .putFloat("maxTradeUsd", (float) maxTradeUsd)
                .putFloat("maxDailyLossUsd", (float) maxDailyLossUsd)
                .putInt("maxTradesPerDay", maxTradesPerDay)
                .putInt("tradesToday", tradesToday)
                .putFloat("minLiquidityUsd", (float) minLiquidityUsd)
                .putFloat("minVolumeUsd", (float) minVolumeUsd)
                .putInt("minPairAgeHours", minPairAgeHours)
                .putFloat("maxSlippagePercent", (float) maxSlippagePercent)
                .putFloat("stopLossPercent", (float) stopLossPercent)
                .putFloat("takeProfitPercent", (float) takeProfitPercent)
                .putInt("scanSeconds", scanSeconds)
                .putString("alertSoundUri", alertSoundUri)
                .putString("lastStatus", lastStatus)
                .putString("lastCritical", lastCritical)
                .putLong("lastHeartbeatMs", lastHeartbeatMs)
                .putString("dailyKey", dailyKey)
                .apply();
    }

    public boolean hasWallet() { return !bscAddress.isEmpty() && !solanaAddress.isEmpty() && !getMnemonic().isEmpty(); }
    public String getMnemonic() { return secure.getSecret("dexWalletMnemonic", ""); }
    public void putMnemonic(String mnemonic) { try { secure.putSecret("dexWalletMnemonic", mnemonic); } catch (Exception e) { throw new RuntimeException(e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""), e); } }
    public void clearWallet() { putMnemonic(""); bscAddress = ""; solanaAddress = ""; walletCreatedAt = ""; save(); }
    public String activeAddress() { return "solana".equals(activeChain) ? solanaAddress : bscAddress; }
    public String activeChainLabel() { return "solana".equals(activeChain) ? "Solana" : "BNB Chain"; }
    public String state() { if (panic) return "HALTED"; if (engine.hasPosition()) return "POSITION OPEN"; return scannerRunning ? "SCANNING" : "IDLE"; }
    public void ensureDailyWindow() {
        String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!key.equals(dailyKey)) { dailyKey = key; tradesToday = 0; save(); }
    }
    public boolean canPaperEnter() { ensureDailyWindow(); return !panic && tradesToday < Math.max(1, maxTradesPerDay) && DexSafetyPolicy.validAmount(maxTradeUsd, 1d, 10_000d); }
    public void setCritical(String message) { lastCritical = message == null ? "" : message; lastStatus = lastCritical; scannerRunning = false; panic = true; save(); }
}
