package com.nanu.aitradingbot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
    public int maxLiveTradesPerDay = 4;
    public int orderCooldownSeconds = 60;
    public int liveTradesToday = 0;
    public String liveSafetyDay = "";
    public String pendingProtectionSymbol = "";
    public long pendingProtectionStartedMs = 0L;
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
    public long lastPublicIpCheckedMs = 0L;
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
    public boolean fullAutoLocked = false;
    public boolean telegramCommandControl = true;
    public boolean liveRealOrderArmed = false;
    public boolean liveOrderTestMode = true;
    public double microLiveOrderUsdt = 5.0;
    public double manualOrderLimitUsdt = 50.0;
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

    // Live-data Spot scalping and controlled automatic execution settings.
    public boolean scalperEnabled = true;
    public boolean scalperPaperAutoTrade = true;
    public String scalperSymbol = "BTCUSDT";
    public int scalperScanSeconds = 60;
    public double scalperTradeAmountUsdt = 5.0;
    public int scalperMarketChecks = 0;
    public long lastScalperCheckMs = 0L;
    public double lastScalperPrice = Double.NaN;
    public String lastScalperSignal = "WAITING";
    public int lastScalperConfidence = 0;
    public String lastScalperReport = "No live market scan yet.";
    public String lastScalperError = "";

    // On-device automatic Spot executor state. The executor is intentionally
    // limited to approved pairs, one protected position, and four daily entries.
    public boolean autoLiveArmed = false;
    public boolean autoRunning = false;
    public boolean autoPanic = false;
    public boolean autoBinanceTestOrderPassed = false;
    public String autoTrustedStaticIp = "";
    public int autoMinConfidence = 68;
    public long autoLastScanMs = 0L;
    public long autoLastHeartbeatMs = 0L;
    public long deviceLastHeartbeatMs = 0L;
    public int deviceUnexpectedStopCount = 0;
    public String deviceLastStopReason = "No unexpected device-service stop recorded.";
    public String autoStatus = "Automatic executor is stopped.";
    public String autoActiveSymbol = "";
    public long autoOcoOrderListId = 0L;
    public long autoTakeProfitOrderId = 0L;
    public long autoStopOrderId = 0L;
    public double autoEntryQuoteUsdt = 0.0;
    public double autoEntryPrice = 0.0;
    public double autoProtectedQuantity = 0.0;
    public long autoPositionOpenedMs = 0L;
    public String autoPendingClientOrderId = "";
    public String autoPendingSymbol = "";
    public long autoPendingStartedMs = 0L;
    public boolean autoPendingEntryCounted = false;
    public double autoRealizedPnlUsdt = 0.0;
    public int autoConsecutiveFailures = 0;

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
        if ("demo".equals(mode)) mode = "paper";
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
        maxLiveTradesPerDay = Math.max(1, Math.min(4, sp.getInt("maxLiveTradesPerDay", 4)));
        orderCooldownSeconds = sp.getInt("orderCooldownSeconds", 60);
        liveTradesToday = sp.getInt("liveTradesToday", 0);
        liveSafetyDay = sp.getString("liveSafetyDay", "");
        pendingProtectionSymbol = sp.getString("pendingProtectionSymbol", "");
        pendingProtectionStartedMs = sp.getLong("pendingProtectionStartedMs", 0L);
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
        lastPublicIpCheckedMs = sp.getLong("lastPublicIpCheckedMs", 0L);
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
        // Legacy display flag. Automatic execution has separate explicit arming and preflight gates.
        fullAutoLocked = false;
        telegramCommandControl = sp.getBoolean("telegramCommandControl", true);
        liveRealOrderArmed = sp.getBoolean("liveRealOrderArmed", false);
        liveOrderTestMode = sp.getBoolean("liveOrderTestMode", true);
        microLiveOrderUsdt = Double.longBitsToDouble(sp.getLong("microLiveOrderUsdt", Double.doubleToRawLongBits(5.0)));
        manualOrderLimitUsdt = Double.longBitsToDouble(sp.getLong("manualOrderLimitUsdt", Double.doubleToRawLongBits(50.0)));
        manualOrderLimitUsdt = Math.max(5.0, Math.min(1000.0, manualOrderLimitUsdt));
        microLiveOrderUsdt = Math.max(5.0, Math.min(manualOrderLimitUsdt, microLiveOrderUsdt));
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
        scalperEnabled = sp.getBoolean("scalperEnabled", true);
        scalperPaperAutoTrade = sp.getBoolean("scalperPaperAutoTrade", true);
        scalperSymbol = normalizeCoin(sp.getString("scalperSymbol", "BTCUSDT"));
        if (!BinanceClient.isSupportedPair(scalperSymbol)) scalperSymbol = "BTCUSDT";
        scalperScanSeconds = Math.max(30, sp.getInt("scalperScanSeconds", 60));
        scalperTradeAmountUsdt = Double.longBitsToDouble(sp.getLong("scalperTradeAmountUsdt", Double.doubleToRawLongBits(5.0)));
        scalperMarketChecks = sp.getInt("scalperMarketChecks", 0);
        lastScalperCheckMs = sp.getLong("lastScalperCheckMs", 0L);
        lastScalperPrice = Double.longBitsToDouble(sp.getLong("lastScalperPrice", Double.doubleToRawLongBits(Double.NaN)));
        lastScalperSignal = sp.getString("lastScalperSignal", "WAITING");
        lastScalperConfidence = sp.getInt("lastScalperConfidence", 0);
        lastScalperReport = sp.getString("lastScalperReport", "No live market scan yet.");
        lastScalperError = sp.getString("lastScalperError", "");
        // A restart must never silently resume real-money automation. Position and
        // pending-order metadata remains available for exchange reconciliation.
        autoLiveArmed = false;
        autoRunning = false;
        autoPanic = sp.getBoolean("autoPanic", false);
        autoBinanceTestOrderPassed = sp.getBoolean("autoBinanceTestOrderPassed", false);
        autoTrustedStaticIp = sp.getString("autoTrustedStaticIp", "").trim();
        autoMinConfidence = Math.max(60, Math.min(95, sp.getInt("autoMinConfidence", 68)));
        autoLastScanMs = sp.getLong("autoLastScanMs", 0L);
        autoLastHeartbeatMs = sp.getLong("autoLastHeartbeatMs", 0L);
        deviceLastHeartbeatMs = sp.getLong("deviceLastHeartbeatMs", 0L);
        deviceUnexpectedStopCount = Math.max(0, sp.getInt("deviceUnexpectedStopCount", 0));
        deviceLastStopReason = sp.getString("deviceLastStopReason", "No unexpected device-service stop recorded.");
        autoStatus = sp.getString("autoStatus", "Automatic executor is stopped.");
        autoActiveSymbol = normalizeCoin(sp.getString("autoActiveSymbol", ""));
        if (!BinanceClient.isSupportedPair(autoActiveSymbol)) autoActiveSymbol = "";
        autoOcoOrderListId = sp.getLong("autoOcoOrderListId", 0L);
        autoTakeProfitOrderId = sp.getLong("autoTakeProfitOrderId", 0L);
        autoStopOrderId = sp.getLong("autoStopOrderId", 0L);
        autoEntryQuoteUsdt = Double.longBitsToDouble(sp.getLong("autoEntryQuoteUsdt", Double.doubleToRawLongBits(0.0)));
        autoEntryPrice = Double.longBitsToDouble(sp.getLong("autoEntryPrice", Double.doubleToRawLongBits(0.0)));
        autoProtectedQuantity = Double.longBitsToDouble(sp.getLong("autoProtectedQuantity", Double.doubleToRawLongBits(0.0)));
        autoPositionOpenedMs = sp.getLong("autoPositionOpenedMs", 0L);
        autoPendingClientOrderId = sp.getString("autoPendingClientOrderId", "");
        autoPendingSymbol = normalizeCoin(sp.getString("autoPendingSymbol", ""));
        if (!BinanceClient.isSupportedPair(autoPendingSymbol)) autoPendingSymbol = "";
        autoPendingStartedMs = sp.getLong("autoPendingStartedMs", 0L);
        autoPendingEntryCounted = sp.getBoolean("autoPendingEntryCounted", false);
        autoRealizedPnlUsdt = Double.longBitsToDouble(sp.getLong("autoRealizedPnlUsdt", Double.doubleToRawLongBits(0.0)));
        autoConsecutiveFailures = Math.max(0, sp.getInt("autoConsecutiveFailures", 0));
        watchlist.clear();
        watchlist.addAll(Arrays.asList("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT"));
        ensureDailySafetyWindow();
    }

    public void save() {
        securePrefs.putSecret("apiKey", apiKey);
        securePrefs.putSecret("apiSecret", apiSecret);
        securePrefs.putSecret("telegramToken", telegramToken);
        securePrefs.putSecret("telegramChatId", telegramChatId);
        // Nanu does not use a remote executor. Clear any old control token during upgrade.
        securePrefs.putSecret("executorControlToken", "");
        sp.edit()
                .putString("mode", mode)
                .putBoolean("liveUnlocked", liveUnlocked)
                .putBoolean("autoCoinMode", autoCoinMode)
                .remove("apiKey")
                .remove("apiSecret")
                .remove("telegramToken")
                .remove("telegramChatId")
                .remove("executorControlToken")
                .remove("executorUrl")
                .remove("executorConnected")
                .remove("executorRunning")
                .remove("executorPanic")
                .remove("executorAutoLiveEnabled")
                .remove("executorMode")
                .remove("executorStatus")
                .remove("executorLastError")
                .remove("executorLastSyncMs")
                .remove("executorLastHeartbeatMs")
                .remove("executorDailyEntries")
                .remove("executorMaxTradesPerDay")
                .remove("executorDailyPnlUsdt")
                .remove("executorTradeQuoteUsdt")
                .remove("executorStopLossPct")
                .remove("executorTakeProfitPct")
                .remove("executorDailyLossPct")
                .remove("executorMinConfidence")
                .remove("executorScanSeconds")
                .remove("executorPairs")
                .remove("executorPositions")
                .remove("executorRecentTrade")
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
                .putString("liveSafetyDay", liveSafetyDay)
                .putString("pendingProtectionSymbol", pendingProtectionSymbol == null ? "" : pendingProtectionSymbol)
                .putLong("pendingProtectionStartedMs", pendingProtectionStartedMs)
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
                .putLong("lastPublicIpCheckedMs", lastPublicIpCheckedMs)
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
                .putLong("manualOrderLimitUsdt", Double.doubleToRawLongBits(manualOrderLimitUsdt))
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
                .putBoolean("scalperEnabled", scalperEnabled)
                .putBoolean("scalperPaperAutoTrade", scalperPaperAutoTrade)
                .putString("scalperSymbol", scalperSymbol)
                .putInt("scalperScanSeconds", scalperScanSeconds)
                .putLong("scalperTradeAmountUsdt", Double.doubleToRawLongBits(scalperTradeAmountUsdt))
                .putInt("scalperMarketChecks", scalperMarketChecks)
                .putLong("lastScalperCheckMs", lastScalperCheckMs)
                .putLong("lastScalperPrice", Double.doubleToRawLongBits(lastScalperPrice))
                .putString("lastScalperSignal", lastScalperSignal)
                .putInt("lastScalperConfidence", lastScalperConfidence)
                .putString("lastScalperReport", lastScalperReport)
                .putString("lastScalperError", lastScalperError)
                .putBoolean("autoLiveArmed", false)
                .putBoolean("autoRunning", false)
                .putBoolean("autoPanic", autoPanic)
                .putBoolean("autoBinanceTestOrderPassed", autoBinanceTestOrderPassed)
                .putString("autoTrustedStaticIp", autoTrustedStaticIp == null ? "" : autoTrustedStaticIp)
                .putInt("autoMinConfidence", autoMinConfidence)
                .putLong("autoLastScanMs", autoLastScanMs)
                .putLong("autoLastHeartbeatMs", autoLastHeartbeatMs)
                .putLong("deviceLastHeartbeatMs", deviceLastHeartbeatMs)
                .putInt("deviceUnexpectedStopCount", deviceUnexpectedStopCount)
                .putString("deviceLastStopReason", deviceLastStopReason == null ? "" : deviceLastStopReason)
                .putString("autoStatus", autoStatus == null ? "" : autoStatus)
                .putString("autoActiveSymbol", autoActiveSymbol == null ? "" : autoActiveSymbol)
                .putLong("autoOcoOrderListId", autoOcoOrderListId)
                .putLong("autoTakeProfitOrderId", autoTakeProfitOrderId)
                .putLong("autoStopOrderId", autoStopOrderId)
                .putLong("autoEntryQuoteUsdt", Double.doubleToRawLongBits(autoEntryQuoteUsdt))
                .putLong("autoEntryPrice", Double.doubleToRawLongBits(autoEntryPrice))
                .putLong("autoProtectedQuantity", Double.doubleToRawLongBits(autoProtectedQuantity))
                .putLong("autoPositionOpenedMs", autoPositionOpenedMs)
                .putString("autoPendingClientOrderId", autoPendingClientOrderId == null ? "" : autoPendingClientOrderId)
                .putString("autoPendingSymbol", autoPendingSymbol == null ? "" : autoPendingSymbol)
                .putLong("autoPendingStartedMs", autoPendingStartedMs)
                .putBoolean("autoPendingEntryCounted", autoPendingEntryCounted)
                .putLong("autoRealizedPnlUsdt", Double.doubleToRawLongBits(autoRealizedPnlUsdt))
                .putInt("autoConsecutiveFailures", autoConsecutiveFailures)
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
        autoLiveArmed = false;
        autoRunning = false;
        autoBinanceTestOrderPassed = false;
        lastApiDiagnosis = reason == null ? "API Doctor must be run again." : reason;
        save();
    }

    public boolean apiDoctorOkForCurrentMode() {
        return mode != null && mode.equals(lastApiMode) && lastApiPrivateOk;
    }

    public boolean apiTradingOkForCurrentMode() {
        return mode != null && mode.equals(lastApiMode) && lastApiPrivateOk && lastApiCanTrade;
    }

    public void ensureDailySafetyWindow() {
        String today = localDay();
        if (today.equals(liveSafetyDay)) return;
        liveSafetyDay = today;
        liveTradesToday = 0;
        dryRunPreviewsToday = 0;
        liveDryRunPassCount = 0;
        orderCooldownUntilMs = 0L;
        liveEquityBaselineUsdt = Double.NaN;
        autoRealizedPnlUsdt = 0.0;
        autoConsecutiveFailures = 0;
        if (engine != null) engine.addJournal("New local safety day: entry count and live P&L baseline reset.");
        save();
    }

    public boolean hasPendingProtectionCheck() {
        return pendingProtectionSymbol != null && !pendingProtectionSymbol.trim().isEmpty();
    }

    public void beginProtectionCheck(String symbol) {
        pendingProtectionSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
        pendingProtectionStartedMs = System.currentTimeMillis();
        save();
    }

    public void clearProtectionCheck(String reason) {
        if (hasPendingProtectionCheck() && engine != null) {
            engine.addJournal("Protection check cleared for " + pendingProtectionSymbol + ": " + (reason == null ? "manual review" : reason));
        }
        pendingProtectionSymbol = "";
        pendingProtectionStartedMs = 0L;
        save();
    }

    private static String localDay() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
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

    public String scalperAgeLabel() {
        if (lastScalperCheckMs <= 0) return "never";
        long seconds = Math.max(0L, System.currentTimeMillis() - lastScalperCheckMs) / 1000L;
        if (seconds < 60) return seconds + "s ago";
        return (seconds / 60L) + "m ago";
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
        autoRunning = false;
        autoLiveArmed = false;
        autoStatus = reason == null ? "Automatic executor stopped by a risk guard." : reason;
        engine.addJournal("AUTO STOP: " + reason);
        save();
        try { appContext.stopService(new Intent(appContext, NanuBotService.class)); } catch (Exception ignored) {}
        triggerAlert("Nanu Guard Stop", message, true, "profitguard");
    }

    public void stopBackgroundEngine() {
        try { appContext.stopService(new Intent(appContext, NanuBotService.class)); } catch (Exception ignored) {}
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

    public void clearRateLimitLock(String reason) {
        liveRealOrderArmed = false;
        autoLiveArmed = false;
        autoRunning = false;
        liveUnlocked = false;
        lastApiMode = "";
        lastApiPrivateOk = false;
        lastApiCanTrade = false;
        autoBinanceTestOrderPassed = false;
        binanceRateLimitLock = false;
        lastBinanceStatusCode = 0;
        lastBinanceErrorDoctor = "Rate-limit lock cleared" + (reason == null || reason.isEmpty() ? "." : (": " + reason));
        engine.addJournal("Rate-limit lock cleared" + (reason == null || reason.isEmpty() ? "." : (": " + reason));
        save();
    }

    public boolean panicLatched() {
        return engine != null && (engine.panic || autoPanic);
    }

    public boolean runtimeActive() {
        return engine != null && "ACTIVE".equals(AutoTradingPolicy.runtimeState(engine.running, autoRunning, engine.panic, autoPanic));
    }

    public String runtimeState() {
        return engine == null ? "IDLE" : AutoTradingPolicy.runtimeState(engine.running, autoRunning, engine.panic, autoPanic);
    }

    public void resetPanicState(String reason) {
        engine.running = false;
        engine.panic = false;
        autoRunning = false;
        autoPanic = false;
        autoLiveArmed = false;
        liveRealOrderArmed = false;
        autoStatus = "Panic state reset. Scanner is idle; Automatic LIVE must be armed again.";
        engine.addJournal("Panic state reset" + (reason == null || reason.isEmpty() ? "." : ": " + reason));
        save();
    }

    public void recordDeviceHeartbeat() {
        deviceLastHeartbeatMs = System.currentTimeMillis();
    }

    public void recordUnexpectedDeviceStop(String reason) {
        deviceUnexpectedStopCount++;
        deviceLastStopReason = reason == null || reason.isEmpty() ? "Foreground device service stopped unexpectedly." : reason;
        autoRunning = false;
        autoLiveArmed = false;
        engine.running = false;
        autoStatus = deviceLastStopReason;
        save();
    }

    public boolean hasAutoPosition() {
        return autoActiveSymbol != null && !autoActiveSymbol.isEmpty() && autoOcoOrderListId > 0L;
    }

    public boolean hasAutoPendingOrder() {
        return autoPendingClientOrderId != null && !autoPendingClientOrderId.isEmpty()
                && autoPendingSymbol != null && !autoPendingSymbol.isEmpty();
    }

    public void clearAutoPendingOrder() {
        autoPendingClientOrderId = "";
        autoPendingSymbol = "";
        autoPendingStartedMs = 0L;
        autoPendingEntryCounted = false;
    }

    public void clearAutoPosition(String reason) {
        if (hasAutoPosition() && engine != null) {
            engine.addJournal("Automatic position cleared for " + autoActiveSymbol + ": " + (reason == null ? "exchange reconciliation" : reason));
        }
        autoActiveSymbol = "";
        autoOcoOrderListId = 0L;
        autoTakeProfitOrderId = 0L;
        autoStopOrderId = 0L;
        autoEntryQuoteUsdt = 0.0;
        autoEntryPrice = 0.0;
        autoProtectedQuantity = 0.0;
        autoPositionOpenedMs = 0L;
        clearAutoPendingOrder();
        save();
    }

    public String autoStartBlockers() {
        StringBuilder out = new StringBuilder();
        boolean recoveringAutomaticOrder = hasAutoPendingOrder()
                && hasPendingProtectionCheck()
                && autoPendingSymbol.equals(pendingProtectionSymbol);
        if (!"live".equals(mode)) out.append("- Select LIVE mode.\n");
        if (!liveUnlocked) out.append("- Unlock the LIVE control gate.\n");
        if (!apiTradingOkForCurrentMode()) out.append("- Run LIVE API Doctor and confirm Spot trading is enabled.\n");
        if (!withdrawalPermissionConfirmedOff) out.append("- Confirm API-key withdrawals are OFF.\n");
        if (!telegramDoctorOk) out.append("- Run Telegram Doctor successfully.\n");
        if (!profitGuardEnabled) out.append("- Enable Profit Guard.\n");
        if (!panicButtonTested) out.append("- Test Panic Stop once in PAPER mode.\n");
        if (!complianceGuardEnabled) out.append("- Enable Compliance Guard.\n");
        if (binanceRateLimitLock) out.append("- Clear Binance rate-limit lock only after checking Binance.\n");
        if (liveOrderTestMode) out.append("- Complete a Binance Test Order, then disable Test Order Mode for auto live execution.\n");
        if (!autoBinanceTestOrderPassed) out.append("- Pass a Binance BUY Test Order with this API key.\n");
        if (autoPanic || engine.panic) out.append("- Reset the Panic state after checking Binance.\n");
        if (!autoLiveArmed) out.append("- Arm Automatic LIVE once for this session.\n");
        if (!AutoTradingPolicy.isStaticIp(autoTrustedStaticIp)) out.append("- Add your expected static public IP.\n");
        if (!portfolioSyncOk || System.currentTimeMillis() - lastPortfolioSyncMs > 5 * 60 * 1000L) out.append("- Sync the Spot portfolio within five minutes.\n");
        if (microLiveOrderUsdt < Math.max(5.0, minOrderNotionalUsdt)) out.append("- Set an automatic amount above the exchange minimum.\n");
        if (microLiveOrderUsdt > manualOrderLimitUsdt) out.append("- Raise the order limit or lower the automatic amount.\n");
        if (!Double.isNaN(spotFreeUsdt) && spotFreeUsdt < microLiveOrderUsdt) out.append("- Free USDT is below the automatic order amount.\n");
        if (liveTradesToday >= Math.max(1, maxLiveTradesPerDay)) out.append("- Daily entry limit has been reached.\n");
        if (hasPendingProtectionCheck() && !recoveringAutomaticOrder) out.append("- Inspect and acknowledge the pending Binance protection check.\n");
        return out.toString();
    }

    public void addCoin(String raw) {
        String coin = normalizeCoin(raw);
        if (BinanceClient.isSupportedPair(coin) && !watchlist.contains(coin)) watchlist.add(coin);
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
