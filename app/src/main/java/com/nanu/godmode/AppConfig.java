package com.nanu.godmode;

import android.content.Context;
import android.content.SharedPreferences;

public class AppConfig {
    private final SharedPreferences p;

    public AppConfig(Context c) {
        p = c.getSharedPreferences("nanu_config", Context.MODE_PRIVATE);
        seedDefaults();
    }

    private void seedDefaults() {
        if (!p.contains("mode")) {
            SharedPreferences.Editor e = p.edit();
            e.putString("mode", "paper");
            e.putString("symbols", "BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT");
            e.putFloat("trade_usdt", 15.0f);
            e.putFloat("stop_loss_pct", 0.45f);
            e.putFloat("take_profit_pct", 0.75f);
            e.putFloat("trailing_pct", 0.30f);
            e.putFloat("daily_loss_limit", 4.0f);
            e.putInt("max_open_trades", 2);
            e.putInt("interval_seconds", 20);
            e.putInt("max_hold_minutes", 8);
            e.putBoolean("live_unlocked", false);
            e.putBoolean("real_orders_enabled", false);
            e.putString("bot_status", "STOPPED");
            e.putBoolean("panic", false);
            e.putString("last_signal", "Nanu sleeping. Paper mode is safe harbor.");
            e.apply();
        }
    }

    public SharedPreferences prefs() { return p; }
    public String mode() { return p.getString("mode", "paper").trim().toLowerCase(); }
    public String[] symbols() {
        String raw = p.getString("symbols", "BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT");
        String[] parts = raw.split(",");
        for (int i=0;i<parts.length;i++) parts[i] = parts[i].trim().toUpperCase();
        return parts;
    }
    public String apiKey() { return p.getString("api_key", ""); }
    public String apiSecret() { return p.getString("api_secret", ""); }
    public String telegramToken() { return p.getString("telegram_token", ""); }
    public String telegramChatId() { return p.getString("telegram_chat_id", ""); }
    public double tradeUsdt() { return p.getFloat("trade_usdt", 15.0f); }
    public double stopLossPct() { return p.getFloat("stop_loss_pct", 0.45f); }
    public double takeProfitPct() { return p.getFloat("take_profit_pct", 0.75f); }
    public double trailingPct() { return p.getFloat("trailing_pct", 0.30f); }
    public double dailyLossLimit() { return p.getFloat("daily_loss_limit", 4.0f); }
    public int maxOpenTrades() { return p.getInt("max_open_trades", 2); }
    public int intervalSeconds() { return Math.max(8, p.getInt("interval_seconds", 20)); }
    public int maxHoldMinutes() { return Math.max(1, p.getInt("max_hold_minutes", 8)); }
    public boolean liveUnlocked() { return p.getBoolean("live_unlocked", false); }
    public boolean realOrdersEnabled() { return p.getBoolean("real_orders_enabled", false); }

    public void setStatus(String s) { p.edit().putString("bot_status", s).apply(); }
    public String status() { return p.getString("bot_status", "STOPPED"); }
    public void setPanic(boolean v) { p.edit().putBoolean("panic", v).apply(); }
    public boolean panic() { return p.getBoolean("panic", false); }
    public void setLastSignal(String s) { p.edit().putString("last_signal", s).apply(); }
    public String lastSignal() { return p.getString("last_signal", "No signal yet."); }
    public void setLastPrice(String symbol, double price) { p.edit().putFloat("price_"+symbol, (float)price).apply(); }
    public double getLastPrice(String symbol) { return p.getFloat("price_"+symbol, 0f); }
}
