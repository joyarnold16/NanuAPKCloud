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
    // v5.6.3 Telegram Control + Safety Dashboard
    public boolean telegramAlertsEnabled = true;
    public boolean telegramQuietMode = false;
    public boolean telegramAlertStartStop = true;
    public boolean telegramAlertProfit = true;
    public boolean telegramAlertPanic = true;
    public boolean telegramAlertApi = true;
    public boolean telegramAlertDryRun = true;
    public boolean telegramAlertLive = true;
    public boolean telegramAlertDaily = true;
    public boolean appPinEnabled = false;
    public String appPin = "";
    public double lastUsdtFree = Double.NaN;
    public double lastUsdtLocked = Double.NaN;
    public double lastBtcFree = Double.NaN;
    public String lastBalanceSnapshot = "Balance not synced yet.";
    public int dryRunPreviewsToday = 0;
    public String lastSafetyReport = "No safety report exported yet.";
    public double riskPerTrade = 1.0;
    public double dailyLossLimit = 3.0;
    public double stopLoss = 0.6;
    public double takeProfit = 1.0;
    public double trailingStop = 0.35;
    public int maxOpenTrades = 3;

    // v5.6.3 Controlled Live Dry-Run + Order Safety Engine
    public boolean liveDryRunEnabled = true;
    public boolean firstLiveOrderManualConfirm = true;
    public double liveDryRunOrderUsdt = 10.0;
    public double minOrderNotionalUsdt = 5.0;
    public double slippageLimitPct = 0.25;
    public int maxLiveTradesPerDay = 3;
    public int orderCooldownSeconds = 60;
    public int liveTradesToday = 0;
    public int liveDryRunOpenTrades = 0;
    public long orderCooldownUntilMs = 0L;
    public int liveDryRunPassCount = 0;
    public boolean lastOrderSafetyPass = false;
    public long lastOrderPreviewTime = 0L;
    public String lastOrderSymbol = "";
    public String lastOrderPreview = "No order preview yet.";

    // v5.2 Profit Guard + Alert System
    public boolean phoneNotifications = true;
    public boolean soundAlerts = true;
    public boolean longSoundAlerts = true;
    public boolean profitGuardEnabled = false;
    public double profitTargetUsdt = 50.0;
    public boolean duplicateProfitGuardEnabled = true;
    public int duplicateProfitRepeatCount = 3;

    // v5.3/v5.4/v5.5 API Doctor + Trusted IP Helper
    public String lastApiMode = "";
    public String lastPublicIp = "";
    public int lastApiHttpCode = 0;
    public boolean lastApiPrivateOk = false;
    public boolean lastApiCanTrade = false;
    public String lastApiDiagnosis = "Not tested yet.";
    public boolean lastApiAccountCanWithdraw = false; // Binance account-level flag, not a direct API-key withdrawal permission proof
    public boolean withdrawalPermissionConfirmedOff = false;
    public boolean telegramDoctorOk = false;
    public boolean panicButtonTested = false;
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
        telegramAlertsEnabled = sp.getBoolean("telegramAlertsEnabled", true);
        telegramQuietMode = sp.getBoolean("telegramQuietMode", false);
        telegramAlertStartStop = sp.getBoolean("telegramAlertStartStop", true);
        telegramAlertProfit = sp.getBoolean("telegramAlertProfit", true);
        telegramAlertPanic = sp.getBoolean("telegramAlertPanic", true);
        telegramAlertApi = sp.getBoolean("telegramAlertApi", true);
        telegramAlertDryRun = sp.getBoolean("telegramAlertDryRun", true);
        telegramAlertLive = sp.getBoolean("telegramAlertLive", true);
        telegramAlertDaily = sp.getBoolean("telegramAlertDaily", true);
        appPinEnabled = sp.getBoolean("appPinEnabled", false);
        appPin = sp.getString("appPin", "");
        lastUsdtFree = Double.longBitsToDouble(sp.getLong("lastUsdtFree", Double.doubleToRawLongBits(Double.NaN)));
        lastUsdtLocked = Double.longBitsToDouble(sp.getLong("lastUsdtLocked", Double.doubleToRawLongBits(Double.NaN)));
        lastBtcFree = Double.longBitsToDouble(sp.getLong("lastBtcFree", Double.doubleToRawLongBits(Double.NaN)));
        lastBalanceSnapshot = sp.getString("lastBalanceSnapshot", "Balance not synced yet.");
        dryRunPreviewsToday = sp.getInt("dryRunPreviewsToday", 0);
        lastSafetyReport = sp.getString("lastSafetyReport", "No safety report exported yet.");
        riskPerTrade = Double.longBitsToDouble(sp.getLong("riskPerTrade", Double.doubleToRawLongBits(1.0)));
        dailyLossLimit = Double.longBitsToDouble(sp.getLong("dailyLossLimit", Double.doubleToRawLongBits(3.0)));
        stopLoss = Double.longBitsToDouble(sp.getLong("stopLoss", Double.doubleToRawLongBits(0.6)));
        takeProfit = Double.longBitsToDouble(sp.getLong("takeProfit", Double.doubleToRawLongBits(1.0)));
        trailingStop = Double.longBitsToDouble(sp.getLong("trailingStop", Double.doubleToRawLongBits(0.35)));
        maxOpenTrades = sp.getInt("maxOpenTrades", 3);

        liveDryRunEnabled = sp.getBoolean("liveDryRunEnabled", true);
        firstLiveOrderManualConfirm = sp.getBoolean("firstLiveOrderManualConfirm", true);
        liveDryRunOrderUsdt = Double.longBitsToDouble(sp.getLong("liveDryRunOrderUsdt", Double.doubleToRawLongBits(10.0)));
        minOrderNotionalUsdt = Double.longBitsToDouble(sp.getLong("minOrderNotionalUsdt", Double.doubleToRawLongBits(5.0)));
        slippageLimitPct = Double.longBitsToDouble(sp.getLong("slippageLimitPct", Double.doubleToRawLongBits(0.25)));
        maxLiveTradesPerDay = sp.getInt("maxLiveTradesPerDay", 3);
        orderCooldownSeconds = sp.getInt("orderCooldownSeconds", 60);
        liveTradesToday = sp.getInt("liveTradesToday", 0);
        liveDryRunOpenTrades = sp.getInt("liveDryRunOpenTrades", 0);
        orderCooldownUntilMs = sp.getLong("orderCooldownUntilMs", 0L);
        liveDryRunPassCount = sp.getInt("liveDryRunPassCount", 0);
        lastOrderSafetyPass = sp.getBoolean("lastOrderSafetyPass", false);
        lastOrderPreviewTime = sp.getLong("lastOrderPreviewTime", 0L);
        lastOrderSymbol = sp.getString("lastOrderSymbol", "");
        lastOrderPreview = sp.getString("lastOrderPreview", "No order preview yet.");

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

        lastApiMode = sp.getString("lastApiMode", "");
        lastPublicIp = sp.getString("lastPublicIp", "");
        lastApiHttpCode = sp.getInt("lastApiHttpCode", 0);
        lastApiPrivateOk = sp.getBoolean("lastApiPrivateOk", false);
        lastApiCanTrade = sp.getBoolean("lastApiCanTrade", false);
        lastApiDiagnosis = sp.getString("lastApiDiagnosis", "Not tested yet.");
        lastApiAccountCanWithdraw = sp.getBoolean("lastApiAccountCanWithdraw", false);
        withdrawalPermissionConfirmedOff = sp.getBoolean("withdrawalPermissionConfirmedOff", false);
        telegramDoctorOk = sp.getBoolean("telegramDoctorOk", false);
        panicButtonTested = sp.getBoolean("panicButtonTested", false);

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
                .putBoolean("telegramAlertsEnabled", telegramAlertsEnabled)
                .putBoolean("telegramQuietMode", telegramQuietMode)
                .putBoolean("telegramAlertStartStop", telegramAlertStartStop)
                .putBoolean("telegramAlertProfit", telegramAlertProfit)
                .putBoolean("telegramAlertPanic", telegramAlertPanic)
                .putBoolean("telegramAlertApi", telegramAlertApi)
                .putBoolean("telegramAlertDryRun", telegramAlertDryRun)
                .putBoolean("telegramAlertLive", telegramAlertLive)
                .putBoolean("telegramAlertDaily", telegramAlertDaily)
                .putBoolean("appPinEnabled", appPinEnabled)
                .putString("appPin", appPin)
                .putLong("lastUsdtFree", Double.doubleToRawLongBits(lastUsdtFree))
                .putLong("lastUsdtLocked", Double.doubleToRawLongBits(lastUsdtLocked))
                .putLong("lastBtcFree", Double.doubleToRawLongBits(lastBtcFree))
                .putString("lastBalanceSnapshot", lastBalanceSnapshot)
                .putInt("dryRunPreviewsToday", dryRunPreviewsToday)
                .putString("lastSafetyReport", lastSafetyReport)
                .putLong("riskPerTrade", Double.doubleToRawLongBits(riskPerTrade))
                .putLong("dailyLossLimit", Double.doubleToRawLongBits(dailyLossLimit))
                .putLong("stopLoss", Double.doubleToRawLongBits(stopLoss))
                .putLong("takeProfit", Double.doubleToRawLongBits(takeProfit))
                .putLong("trailingStop", Double.doubleToRawLongBits(trailingStop))
                .putInt("maxOpenTrades", maxOpenTrades)
                .putBoolean("liveDryRunEnabled", liveDryRunEnabled)
                .putBoolean("firstLiveOrderManualConfirm", firstLiveOrderManualConfirm)
                .putLong("liveDryRunOrderUsdt", Double.doubleToRawLongBits(liveDryRunOrderUsdt))
                .putLong("minOrderNotionalUsdt", Double.doubleToRawLongBits(minOrderNotionalUsdt))
                .putLong("slippageLimitPct", Double.doubleToRawLongBits(slippageLimitPct))
                .putInt("maxLiveTradesPerDay", maxLiveTradesPerDay)
                .putInt("orderCooldownSeconds", orderCooldownSeconds)
                .putInt("liveTradesToday", liveTradesToday)
                .putInt("liveDryRunOpenTrades", liveDryRunOpenTrades)
                .putLong("orderCooldownUntilMs", orderCooldownUntilMs)
                .putInt("liveDryRunPassCount", liveDryRunPassCount)
                .putBoolean("lastOrderSafetyPass", lastOrderSafetyPass)
                .putLong("lastOrderPreviewTime", lastOrderPreviewTime)
                .putString("lastOrderSymbol", lastOrderSymbol)
                .putString("lastOrderPreview", lastOrderPreview)
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
                .putString("lastApiMode", lastApiMode)
                .putString("lastPublicIp", lastPublicIp)
                .putInt("lastApiHttpCode", lastApiHttpCode)
                .putBoolean("lastApiPrivateOk", lastApiPrivateOk)
                .putBoolean("lastApiCanTrade", lastApiCanTrade)
                .putString("lastApiDiagnosis", lastApiDiagnosis)
                .putBoolean("lastApiAccountCanWithdraw", lastApiAccountCanWithdraw)
                .putBoolean("withdrawalPermissionConfirmedOff", withdrawalPermissionConfirmedOff)
                .putBoolean("telegramDoctorOk", telegramDoctorOk)
                .putBoolean("panicButtonTested", panicButtonTested)
                .putString("watchlist", join(watchlist))
                .apply();
    }

    public void resetOrderSafetyState(String reason) {
        liveDryRunOpenTrades = 0;
        orderCooldownUntilMs = 0L;
        lastOrderSafetyPass = false;
        lastOrderSymbol = "";
        lastOrderPreviewTime = 0L;
        lastOrderPreview = "Order safety state reset" + (reason == null || reason.isEmpty() ? "." : (": " + reason));
        if (engine != null) engine.addJournal("Order Safety reset: " + (reason == null || reason.isEmpty() ? "manual reset" : reason));
        save();
    }

    public void resetGuardSession() {
        lastRoundedProfit = Double.NaN;
        sameProfitRepeats = 0;
        profitTargetAlreadyHit = false;
        duplicateProfitAlreadyHit = false;
        save();
    }

    public void clearApiDoctorStatus(String reason) {
        lastApiMode = "";
        lastApiHttpCode = 0;
        lastApiPrivateOk = false;
        lastApiCanTrade = false;
        lastApiAccountCanWithdraw = false;
        withdrawalPermissionConfirmedOff = false;
        liveUnlocked = false;
        lastApiDiagnosis = reason == null ? "API Doctor must be run again." : reason;
        save();
    }

    public boolean apiDoctorOkForCurrentMode() {
        return mode != null && mode.equals(lastApiMode) && lastApiPrivateOk;
    }

    public boolean apiTradingOkForCurrentMode() {
        return mode != null && mode.equals(lastApiMode) && lastApiPrivateOk && lastApiCanTrade;
    }

    public void triggerAlert(String title, String message, boolean critical) {
        triggerAlert(title, message, critical, "general");
    }

    public void triggerAlert(String title, String message, boolean critical, String category) {
        AlertCenter.notify(appContext, title, message, critical, soundAlerts, phoneNotifications, longSoundAlerts);
        TelegramClient.sendSilent(this, title, message, category == null ? "general" : category, critical);
    }

    public String fmtAmount(double value, String suffix) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "Not synced";
        return String.format(Locale.US, "%.6f %s", value, suffix == null ? "" : suffix);
    }

    public void autoStopForGuard(String reason, String message) {
        engine.running = false;
        engine.addJournal("AUTO STOP: " + reason);
        save();
        try { appContext.stopService(new Intent(appContext, NanuBotService.class)); } catch (Exception ignored) {}
        triggerAlert("Nanu Guard Stop", message, true, "profitguard");
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
