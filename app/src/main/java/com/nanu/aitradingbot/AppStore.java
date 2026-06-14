package com.nanu.aitradingbot;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppStore {
    private static AppStore instance;
    public final NanuEngine engine = new NanuEngine();
    public String mode = "paper";
    public String coinMode = "auto";
    public String apiKey = "";
    public String apiSecret = "";
    public String telegramToken = "";
    public String telegramChatId = "";
    public float riskPerTrade = 1.0f;
    public float dailyLossLimit = 3.0f;
    public float stopLoss = 0.7f;
    public float takeProfit = 1.1f;
    public int maxOpenTrades = 3;
    public boolean liveUnlocked = false;
    public final List<String> watchlist = new ArrayList<>();
    private SharedPreferences prefs;

    private AppStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("nanu_ai_trading_bot", Context.MODE_PRIVATE);
        load();
    }

    public static synchronized AppStore get(Context context) {
        if (instance == null) instance = new AppStore(context);
        return instance;
    }

    public void load() {
        mode = prefs.getString("mode", "paper");
        coinMode = prefs.getString("coinMode", "auto");
        apiKey = prefs.getString("apiKey", "");
        apiSecret = prefs.getString("apiSecret", "");
        telegramToken = prefs.getString("telegramToken", "");
        telegramChatId = prefs.getString("telegramChatId", "");
        riskPerTrade = prefs.getFloat("riskPerTrade", 1.0f);
        dailyLossLimit = prefs.getFloat("dailyLossLimit", 3.0f);
        stopLoss = prefs.getFloat("stopLoss", 0.7f);
        takeProfit = prefs.getFloat("takeProfit", 1.1f);
        maxOpenTrades = prefs.getInt("maxOpenTrades", 3);
        liveUnlocked = prefs.getBoolean("liveUnlocked", false);
        watchlist.clear();
        String raw = prefs.getString("watchlist", "BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT");
        for (String s : raw.split(",")) {
            String x = s.trim().toUpperCase();
            if (x.length() > 0 && !watchlist.contains(x)) watchlist.add(x);
        }
        if (watchlist.isEmpty()) watchlist.addAll(Arrays.asList("BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT"));
        engine.setWatchlist(watchlist);
        engine.mode = mode;
    }

    public void save() {
        StringBuilder sb = new StringBuilder();
        for (String s : watchlist) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        prefs.edit()
                .putString("mode", mode)
                .putString("coinMode", coinMode)
                .putString("apiKey", apiKey)
                .putString("apiSecret", apiSecret)
                .putString("telegramToken", telegramToken)
                .putString("telegramChatId", telegramChatId)
                .putFloat("riskPerTrade", riskPerTrade)
                .putFloat("dailyLossLimit", dailyLossLimit)
                .putFloat("stopLoss", stopLoss)
                .putFloat("takeProfit", takeProfit)
                .putInt("maxOpenTrades", maxOpenTrades)
                .putBoolean("liveUnlocked", liveUnlocked)
                .putString("watchlist", sb.toString())
                .apply();
        engine.setWatchlist(watchlist);
        engine.mode = mode;
    }

    public void addCoin(String raw) {
        if (raw == null) return;
        String s = raw.trim().toUpperCase().replace("/", "").replace("-", "");
        if (s.length() == 0) return;
        if (!s.endsWith("USDT")) s = s + "USDT";
        if (!watchlist.contains(s)) watchlist.add(s);
        save();
    }

    public void removeCoin(String s) {
        if (watchlist.size() <= 1) return;
        watchlist.remove(s);
        save();
    }
}
