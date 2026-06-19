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
    private final SecurePrefs securePrefs;
    private final Context appContext;
    public final NanuEngine engine;
    public String mode = "paper";
    public boolean liveUnlocked = false;
    public boolean autoCoinMode = true;
    public String apiKey = "";
    public String apiSecret = "";
    public String telegramToken = "";
    public String telegramChatId = "";
    // v6.1 Telegram Control + Safety Dashboard
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
    public String appPinHash = "";
    public double lastUsdtFree = Double.NaN;
    public double lastUsdtLocked = Double.NaN;
    public double lastBtcFree = Double.NaN;
    public String lastBalanceSnapshot = "Balance not synced yet.";
    public double spotEquityUsdt = Double.NaN;
    public double spotFreeUsdt = Double.NaN;
    public double spotLockedUsdt = Double.NaN;
    public double liveEquityBaselineUsdt = Double.NaN;
    public int spotAssetCount = 0;
    public long lastPortfolioSyncMs = 0L;
    public boolean portfolioSyncOk = false;
    public String lastPortfolioSyncStatus = "Portfolio not synced yet.";
    public String topPortfolioAssets = "No assets synced yet.";
    public String portfolioWarnings = "";
    public int dryRunPreviewsToday = 0;
    public String lastSafetyReport = "No safety report exported yet.";
    public double riskPerTrade = 1.0;
    public double dailyLossLimit = 3.0;
    public double stopLoss = 0.6;
    public double takeProfit = 1.0;
    public double trailingStop = 0.35;
    public int maxOpenTrades = 3;

    // v6.1 Controlled Live Dry-Run + Order Safety Engine
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

    // v6.1 Controlled Live Scalping System
    public boolean complianceGuardEnabled = true;
    public boolean semiAutoApprovalRequired = true;
    public boolean fullAutoLocked = true;
    public boolean telegramCommandControl = true;
    public boolean liveRealOrderArmed = false;
    public boolean liveOrderTestMode = true;
    public double microLiveOrderUsdt = 5.0;
    public int maxConsecutiveLosses = 2;
    public int consecutiveLosses = 0;
    public int dryRunProofRequired = 50;
    public int fullAutoProofDays = 7;
    public boolean binanceRateLimitLock = false;
    public int lastBinanceStatusCode = 0;
    public String lastLiveOrderReport = "No live order attempted yet.";
    public String lastComplianceReport = "No compliance report generated yet.";
    public String lastBackupText = "No backup exported yet.";
    public String lastBinanceErrorDoctor = "No Binance error yet.";
    public int brainLearningCycles = 0;
    public double brainAdaptiveBias = 0.0;
    public long lastBrainLearnMs = 0L;
    public String lastBrainInsight = "Learning memory is ready.";
    public String brainMemoryLog = "";

    public final List<String> watchlist = new ArrayList<>();

    public static synchronized AppStore get(Context c) {
        if (instance == null) instance = new AppStore(c.getApplicationContext());
        return instance;
    }

    private AppStore(Context c) {
        appContext = c.getApplicationContext();
        sp = c.getSharedPreferences("nanu_v52", Context.MODE_PRIVATE);
        securePrefs = new SecurePrefs(sp);
        engine = new NanuEngine(this);
        load();
    }

    public void load() {
        mode = sp.getString("mode", "paper");
        liveUnlocked = sp.getBoolean("liveUnlocked", false);
        autoCoinMode = sp.getBoolean("autoCoinMode", true);
        apiKey = securePrefs.getSecret("apiKey", "");
        apiSecret = securePrefs.getSecret("apiSecret", "");
        telegramToken = securePrefs.getSecret("telegramToken", "");
        telegramChatId = securePrefs.getSecret("telegramChatId", "");
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
        appPinHash = sp.getString("appPinHash", "");
        String legacyPin = sp.getString("appPin", "");
        if ((appPinHash == null || appPinHash.isEmpty()) && legacyPin != null && !legacyPin.isEmpty()) {
            appPinHash = SecurePrefs.createPinHash(legacyPin);
            sp.edit().putString("appPinHash", appPinHash).remove("appPin").apply();
        }
        if (appPinEnabled && (appPinHash == null || appPinHash.isEmpty())) appPinEnabled = false;
        appPin = "";
        lastUsdtFree = Double.longBitsToDouble(sp.getLong("lastUsdtFree", Double.doubleToRawLongBits(Double.NaN)));
        lastUsdtLocked = Double.longBitsToDouble(sp.getLong("lastUsdtLocked", Double.doubleToRawLongBits(Double.NaN)));
        lastBtcFree = Double.longBitsToDouble(sp.getLong("lastBtcFree", Double.doubleToRawLongBits(Double.NaN)));
        lastBalanceSnapshot = sp.getString("lastBalanceSnapshot", "Balance not synced yet.");
        spotEquityUsdt = Double.longBitsToDouble(sp.getLong("spotEquityUsdt", Double.doubleToRawLongBits(Double.NaN)));
        spotFreeUsdt = Double.longBitsToDouble(sp.getLong("spotFreeUsdt", Double.doubleToRawLongBits(Double.NaN)));
        spotLockedUsdt = Double.longBitsToDouble(sp.getLong("spotLockedUsdt", Double.doubleToRawLongBits(Double.NaN)));
        liveEquityBaselineUsdt = Double.longBitsToDouble(sp.getLong("liveEquityBaselineUsdt", Double.doubleToRawLongBits(Double.NaN)));
        spotAssetCount = sp.getInt("spotAssetCount", 0);
        lastPortfolioSyncMs = sp.getLong("lastPortfolioSyncMs", 0L);
        portfolioSyncOk = sp.getBoolean("portfolioSyncOk", false);
        lastPortfolioSyncStatus = sp.getString("lastPortfolioSyncStatus", "Portfolio not synced yet.");
        topPortfolioAssets = sp.getString("topPortfolioAssets", "No assets synced yet.");
        portfolioWarnings = sp.getString("portfolioWarnings", "");
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

        complianceGuardEnabled = sp.getBoolean("complianceGuardEnabled", true);
        semiAutoApprovalRequired = sp.getBoolean("semiAutoApprovalRequired", true);
        fullAutoLocked = sp.getBoolean("fullAutoLocked", true);
        telegramCommandControl = sp.getBoolean("telegramCommandControl", true);
        liveRealOrderArmed = sp.getBoolean("liveRealOrderArmed", false);
        liveOrderTestMode = sp.getBoolean("liveOrderTestMode", true);
        microLiveOrderUsdt = Double.longBitsToDouble(sp.getLong("microLiveOrderUsdt", Double.doubleToRawLongBits(5.0)));
        maxConsecutiveLosses = sp.getInt("maxConsecutiveLosses", 2);
        consecutiveLosses = sp.getInt("consecutiveLosses", 0);
        dryRunProofRequired = sp.getInt("dryRunProofRequired", 50);
        fullAutoProofDays = sp.getInt("fullAutoProofDays", 7);
        binanceRateLimitLock = sp.getBoolean("binanceRateLimitLock", false);
        lastBinanceStatusCode = sp.getInt("lastBinanceStatusCode", 0);
        lastLiveOrderReport = sp.getString("lastLiveOrderReport", "No live order attempted yet.");
        lastComplianceReport = sp.getString("lastComplianceReport", "No compliance report generated yet.");
        lastBackupText = sp.getString("lastBackupText", "No backup exported yet.");
        lastBinanceErrorDoctor = sp.getString("lastBinanceErrorDoctor", "No Binance error yet.");
        brainLearningCycles = sp.getInt("brainLearningCycles", 0);
        brainAdaptiveBias = Double.longBitsToDouble(sp.getLong("brainAdaptiveBias", Double.doubleToRawLongBits(0.0)));
        lastBrainLearnMs = sp.getLong("lastBrainLearnMs", 0L);
        lastBrainInsight = sp.getString("lastBrainInsight", "Learning memory is ready.");
        brainMemoryLog = sp.getString("brainMemoryLog", "");

        watchlist.clear();
        String saved = sp.getString("watchlist", "BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT");
        for (String s : saved.split(",")) if (!s.trim().isEmpty()) watchlist.add(normalizeCoin(s));
    }

    public void save() {
        securePrefs.putSecret("apiKey", apiKey);
        securePrefs.putSecret("apiSecret", apiSecret);
        securePrefs.putSecret("telegramToken", telegramToken);
        securePrefs.putSecret("telegramChatId", telegramChatId);
        sp.edit()
                .putString("mode", mode)
                .putBoolean("liveUnlocked", liveUnlocked)
                .putBoolean("autoCoinMode", autoCoinMode)
                .remove("apiKey")
                .remove("apiSecret")
                .remove("telegramToken")
                .remove("telegramChatId")
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
                .putString("appPinHash", appPinHash == null ? "" : appPinHash)
                .remove("appPin")
                .putLong("lastUsdtFree", Double.doubleToRawLongBits(lastUsdtFree))
                .putLong("lastUsdtLocked", Double.doubleToRawLongBits(lastUsdtLocked))
                .putLong("lastBtcFree", Double.doubleToRawLongBits(lastBtcFree))
                .putString("lastBalanceSnapshot", lastBalanceSnapshot)
                .putLong("spotEquityUsdt", Double.doubleToRawLongBits(spotEquityUsdt))
                .putLong("spotFreeUsdt", Double.doubleToRawLongBits(spotFreeUsdt))
                .putLong("spotLockedUsdt", Double.doubleToRawLongBits(spotLockedUsdt))
                .putLong("liveEquityBaselineUsdt", Double.doubleToRawLongBits(liveEquityBaselineUsdt))
                .putInt("spotAssetCount", spotAssetCount)
                .putLong("lastPortfolioSyncMs", lastPortfolioSyncMs)
                .putBoolean("portfolioSyncOk", portfolioSyncOk)
                .putString("lastPortfolioSyncStatus", lastPortfolioSyncStatus)
                .putString("topPortfolioAssets", topPortfolioAssets)
                .putString("portfolioWarnings", portfolioWarnings)
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
                .putBoolean("complianceGuardEnabled", complianceGuardEnabled)
                .putBoolean("semiAutoApprovalRequired", semiAutoApprovalRequired)
                .putBoolean("fullAutoLocked", fullAutoLocked)
                .putBoolean("telegramCommandControl", telegramCommandControl)
                .putBoolean("liveRealOrderArmed", liveRealOrderArmed)
                .putBoolean("liveOrderTestMode", liveOrderTestMode)
                .putLong("microLiveOrderUsdt", Double.doubleToRawLongBits(microLiveOrderUsdt))
                .putInt("maxConsecutiveLosses", maxConsecutiveLosses)
                .putInt("consecutiveLosses", consecutiveLosses)
                .putInt("dryRunProofRequired", dryRunProofRequired)
                .putInt("fullAutoProofDays", fullAutoProofDays)
                .putBoolean("binanceRateLimitLock", binanceRateLimitLock)
                .putInt("lastBinanceStatusCode", lastBinanceStatusCode)
                .putString("lastLiveOrderReport", lastLiveOrderReport)
                .putString("lastComplianceReport", lastComplianceReport)
                .putString("lastBackupText", lastBackupText)
                .putString("lastBinanceErrorDoctor", lastBinanceErrorDoctor)
                .putInt("brainLearningCycles", brainLearningCycles)
                .putLong("brainAdaptiveBias", Double.doubleToRawLongBits(brainAdaptiveBias))
                .putLong("lastBrainLearnMs", lastBrainLearnMs)
                .putString("lastBrainInsight", lastBrainInsight)
                .putString("brainMemoryLog", brainMemoryLog)
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

    public void setAppPin(String pin) {
        String safe = pin == null ? "" : pin.trim();
        if (safe.isEmpty()) {
            appPinEnabled = false;
            appPinHash = "";
        } else {
            appPinHash = SecurePrefs.createPinHash(safe);
            appPinEnabled = !appPinHash.isEmpty();
        }
        appPin = "";
        save();
    }

    public boolean verifyAppPin(String pin) {
        if (!appPinEnabled) return true;
        if (appPinHash == null || appPinHash.isEmpty()) return false;
        return SecurePrefs.verifyPin(pin == null ? "" : pin.trim(), appPinHash);
    }

    public String portfolioAgeLabel() {
        if (lastPortfolioSyncMs <= 0) return "never";
        long age = Math.max(0L, System.currentTimeMillis() - lastPortfolioSyncMs);
        long seconds = age / 1000L;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60L;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60L;
        if (hours < 48) return hours + "h ago";
        return (hours / 24L) + "d ago";
    }

    public String portfolioEquityLabel() {
        if (!portfolioSyncOk || Double.isNaN(spotEquityUsdt)) return "Spot equity not synced";
        return String.format(Locale.US, "%.2f USDT", spotEquityUsdt);
    }

    public void recordBrainObservation(String source, double pnl, boolean portfolioBacked) {
        long now = System.currentTimeMillis();
        if (now - lastBrainLearnMs < 60000L) return;
        lastBrainLearnMs = now;
        brainLearningCycles++;
        double signal = Math.max(-1.0, Math.min(1.0, pnl / 100.0));
        brainAdaptiveBias = Math.max(-10.0, Math.min(10.0, brainAdaptiveBias * 0.92 + signal));
        String stance = brainAdaptiveBias > 1.5 ? "favors momentum but keeps micro sizing" : (brainAdaptiveBias < -1.5 ? "defensive bias; reduce frequency and wait for cleaner signals" : "neutral; keep confirmation gates strict");
        lastBrainInsight = "Cycle " + brainLearningCycles + ": " + (portfolioBacked ? "portfolio-backed" : "paper") + " observation from " + source + " -> " + stance + ".";
        String line = String.format(Locale.US, "%d|%s|pnl=%+.2f|bias=%.2f|%s", now, source == null ? "engine" : source, pnl, brainAdaptiveBias, portfolioBacked ? "portfolio" : "paper");
        brainMemoryLog = line + (brainMemoryLog == null || brainMemoryLog.isEmpty() ? "" : "\n" + brainMemoryLog);
        if (brainMemoryLog.length() > 4000) brainMemoryLog = brainMemoryLog.substring(0, 4000);
        save();
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


    public boolean liveSafetyReadyForManualOrder() {
        return "live".equals(mode)
                && liveUnlocked
                && apiTradingOkForCurrentMode()
                && withdrawalPermissionConfirmedOff
                && profitGuardEnabled
                && panicButtonTested
                && !engine.panic
                && complianceGuardEnabled
                && !binanceRateLimitLock
                && liveTradesToday < Math.max(1, maxLiveTradesPerDay)
                && System.currentTimeMillis() >= orderCooldownUntilMs
                && microLiveOrderUsdt >= Math.max(5.0, minOrderNotionalUsdt);
    }

    public void lockAfterExchangeDanger(int statusCode, String reason) {
        lastBinanceStatusCode = statusCode;
        lastBinanceErrorDoctor = reason == null ? "Binance danger response." : reason;
        if (statusCode == 418 || statusCode == 429) {
            binanceRateLimitLock = true;
            liveRealOrderArmed = false;
            liveUnlocked = false;
            engine.running = false;
            engine.addJournal("BINANCE RATE LIMIT LOCK: " + statusCode);
        }
        save();
    }

    public void resetV60SafetyState(String reason) {
        liveRealOrderArmed = false;
        binanceRateLimitLock = false;
        lastBinanceStatusCode = 0;
        lastBinanceErrorDoctor = "Safety state reset" + (reason == null || reason.isEmpty() ? "." : (": " + reason));
        resetOrderSafetyState(reason == null ? "v6.1 safety reset" : reason);
        save();
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
