package com.nanu.aitradingbot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AppStore {
    private static AppStore instance;
    private final SharedPreferences sp;
    private final Context appContext;
    public final NanuEngine engine;
    public String mode = "paper";
    public boolean liveUnlocked = false;
    public boolean autoCoinMode = true;
    public String apiKey = "";
    public String apiSecret = "";
    public String telegramToken = "";
    public String telegramChatId = "";
    public double riskPerTrade = 1.0;
    public double dailyLossLimit = 3.0;
    public double stopLoss = 0.6;
    public double takeProfit = 1.0;
    public double trailingStop = 0.35;
    public int maxOpenTrades = 3;

    // v5.2 Profit Guard + Alert System
    public boolean phoneNotifications = true;
    public boolean soundAlerts = true;
    public boolean longSoundAlerts = true;
    public boolean profitGuardEnabled = false;
    public double profitTargetUsdt = 50.0;
    public boolean duplicateProfitGuardEnabled = true;
    public int duplicateProfitRepeatCount = 3;
    public double lastRoundedProfit = Double.NaN;
    public int sameProfitRepeats = 0;
    public boolean profitTargetAlreadyHit = false;
    public boolean duplicateProfitAlreadyHit = false;

    public final List<String> watchlist = new ArrayList<>();

    public static synchronized AppStore get(Context c) {
        if (instance == null) instance = new AppStore(c.getApplicationContext());
        return instance;
    }

    private AppStore(Context c) {
        appContext = c.getApplicationContext();
        sp = c.getSharedPreferences("nanu_v52", Context.MODE_PRIVATE);
        engine = new NanuEngine(this);
        load();
    }

    public void load() {
        mode = sp.getString("mode", "paper");
        liveUnlocked = sp.getBoolean("liveUnlocked", false);
        autoCoinMode = sp.getBoolean("autoCoinMode", true);
        apiKey = sp.getString("apiKey", "");
        apiSecret = sp.getString("apiSecret", "");
        telegramToken = sp.getString("telegramToken", "");
        telegramChatId = sp.getString("telegramChatId", "");
        riskPerTrade = Double.longBitsToDouble(sp.getLong("riskPerTrade", Double.doubleToRawLongBits(1.0)));
        dailyLossLimit = Double.longBitsToDouble(sp.getLong("dailyLossLimit", Double.doubleToRawLongBits(3.0)));
        stopLoss = Double.longBitsToDouble(sp.getLong("stopLoss", Double.doubleToRawLongBits(0.6)));
        takeProfit = Double.longBitsToDouble(sp.getLong("takeProfit", Double.doubleToRawLongBits(1.0)));
        trailingStop = Double.longBitsToDouble(sp.getLong("trailingStop", Double.doubleToRawLongBits(0.35)));
        maxOpenTrades = sp.getInt("maxOpenTrades", 3);

        phoneNotifications = sp.getBoolean("phoneNotifications", true);
        soundAlerts = sp.getBoolean("soundAlerts", true);
        longSoundAlerts = sp.getBoolean("longSoundAlerts", true);
        profitGuardEnabled = sp.getBoolean("profitGuardEnabled", false);
        profitTargetUsdt = Double.longBitsToDouble(sp.getLong("profitTargetUsdt", Double.doubleToRawLongBits(50.0)));
        duplicateProfitGuardEnabled = sp.getBoolean("duplicateProfitGuardEnabled", true);
        duplicateProfitRepeatCount = sp.getInt("duplicateProfitRepeatCount", 3);
        lastRoundedProfit = Double.longBitsToDouble(sp.getLong("lastRoundedProfit", Double.doubleToRawLongBits(Double.NaN)));
        sameProfitRepeats = sp.getInt("sameProfitRepeats", 0);
        profitTargetAlreadyHit = sp.getBoolean("profitTargetAlreadyHit", false);
        duplicateProfitAlreadyHit = sp.getBoolean("duplicateProfitAlreadyHit", false);

        watchlist.clear();
        String saved = sp.getString("watchlist", "BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT");
        for (String s : saved.split(",")) if (!s.trim().isEmpty()) watchlist.add(normalizeCoin(s));
    }

    public void save() {
        sp.edit()
                .putString("mode", mode)
                .putBoolean("liveUnlocked", liveUnlocked)
                .putBoolean("autoCoinMode", autoCoinMode)
                .putString("apiKey", apiKey)
                .putString("apiSecret", apiSecret)
                .putString("telegramToken", telegramToken)
                .putString("telegramChatId", telegramChatId)
                .putLong("riskPerTrade", Double.doubleToRawLongBits(riskPerTrade))
                .putLong("dailyLossLimit", Double.doubleToRawLongBits(dailyLossLimit))
                .putLong("stopLoss", Double.doubleToRawLongBits(stopLoss))
                .putLong("takeProfit", Double.doubleToRawLongBits(takeProfit))
                .putLong("trailingStop", Double.doubleToRawLongBits(trailingStop))
                .putInt("maxOpenTrades", maxOpenTrades)
                .putBoolean("phoneNotifications", phoneNotifications)
                .putBoolean("soundAlerts", soundAlerts)
                .putBoolean("longSoundAlerts", longSoundAlerts)
                .putBoolean("profitGuardEnabled", profitGuardEnabled)
                .putLong("profitTargetUsdt", Double.doubleToRawLongBits(profitTargetUsdt))
                .putBoolean("duplicateProfitGuardEnabled", duplicateProfitGuardEnabled)
                .putInt("duplicateProfitRepeatCount", duplicateProfitRepeatCount)
                .putLong("lastRoundedProfit", Double.doubleToRawLongBits(lastRoundedProfit))
                .putInt("sameProfitRepeats", sameProfitRepeats)
                .putBoolean("profitTargetAlreadyHit", profitTargetAlreadyHit)
                .putBoolean("duplicateProfitAlreadyHit", duplicateProfitAlreadyHit)
                .putString("watchlist", join(watchlist))
                .apply();
    }

    public void resetGuardSession() {
        lastRoundedProfit = Double.NaN;
        sameProfitRepeats = 0;
        profitTargetAlreadyHit = false;
        duplicateProfitAlreadyHit = false;
        save();
    }

    public void triggerAlert(String title, String message, boolean critical) {
        AlertCenter.notify(appContext, title, message, critical, soundAlerts, phoneNotifications, longSoundAlerts);
    }

    public void autoStopForGuard(String reason, String message) {
        engine.running = false;
        engine.addJournal("AUTO STOP: " + reason);
        save();
        try { appContext.stopService(new Intent(appContext, NanuBotService.class)); } catch (Exception ignored) {}
        triggerAlert("Nanu Guard Stop", message, true);
    }

    public void addCoin(String raw) {
        String coin = normalizeCoin(raw);
        if (!coin.isEmpty() && !watchlist.contains(coin)) watchlist.add(coin);
        save();
    }

    public void removeCoin(String coin) {
        watchlist.remove(normalizeCoin(coin));
        if (watchlist.isEmpty()) watchlist.addAll(Arrays.asList("BTCUSDT", "ETHUSDT"));
        save();
    }

    public void autoSelectCoins() {
        watchlist.clear();
        watchlist.addAll(Arrays.asList("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT"));
        engine.addJournal("Auto selected BTC, ETH, SOL, BNB: high liquidity and tight spread profile.");
        save();
    }

    public String normalizeCoin(String raw) {
        String coin = raw == null ? "" : raw.trim().toUpperCase(Locale.US).replace("/", "").replace("-", "").replace(" ", "");
        if (coin.length() > 0 && !coin.endsWith("USDT")) coin += "USDT";
        return coin;
    }

    private String join(List<String> xs) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) { if (i > 0) b.append(','); b.append(xs.get(i)); }
        return b.toString();
    }
}
