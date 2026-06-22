package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BinanceClient {
    public interface Callback { void done(String result); }
    public interface ScalperCallback { void done(ScalpingStrategy.Signal signal, String report); }
    public interface AutoCallback { void done(AutoResult result); }

    public static final class AutoResult {
        public final boolean ok;
        public final String report;

        AutoResult(boolean ok, String report) {
            this.ok = ok;
            this.report = report == null ? "" : report;
        }
    }
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static volatile boolean scalperRequestInFlight = false;
    private static final String[] SUPPORTED_PAIRS = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT"};

    private static class BinanceHttpException extends Exception {
        final int code;
        final String body;
        BinanceHttpException(int code, String body) {
            super("Binance HTTP " + code);
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    private static class AssetValue implements Comparable<AssetValue> {
        String asset;
        double free;
        double locked;
        double priceUsdt;
        double valueUsdt;
        boolean priced;

        @Override public int compareTo(AssetValue other) {
            return Double.compare(other.valueUsdt, valueUsdt);
        }
    }

    private static class SymbolRules {
        boolean ok = false;
        String status = "UNKNOWN";
        double minNotional = 5.0;
        double maxNotional = Double.NaN;
        double minQty = 0.0;
        double maxQty = Double.NaN;
        double stepSize = 0.0;
        double tickSize = 0.0;
        double marketMinQty = 0.0;
        double marketMaxQty = Double.NaN;
        double marketStepSize = 0.0;
        String report = "Symbol rules not loaded.";
    }

    private static class OcoProtection {
        String report;
        long listId;
        long takeProfitOrderId;
        long stopOrderId;
        double entryQuote;
        double entryPrice;
        double protectedQuantity;
    }

    public static void testApi(AppStore store, Callback cb) {
        exec.execute(() -> {
            StringBuilder out = new StringBuilder();
            String doctorMode = store.mode == null ? "paper" : store.mode;
            try {
                String base = baseUrl(doctorMode);
                out.append("Nanu API Doctor\n\n");
                out.append("Mode: ").append(doctorMode.toUpperCase(Locale.US)).append('\n');
                out.append("Base: ").append(base).append('\n');

                long serverTime = serverTime(base);
                out.append("Server time: ").append(serverTime).append('\n');
                String ip = publicIpSafe();
                if (!ip.isEmpty()) {
                    store.lastPublicIp = ip;
                    store.lastPublicIpCheckedMs = System.currentTimeMillis();
                    out.append("Public IP helper: ").append(ip).append('\n');
                }

                if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) {
                    store.lastApiMode = doctorMode;
                    store.lastApiPrivateOk = false;
                    store.lastApiCanTrade = false;
                    store.lastApiHttpCode = 0;
                    store.lastApiDiagnosis = "Public connection OK. Add API key + secret for private account test.";
                    store.save();
                    out.append("\nPrivate API test skipped. Add API key and secret first.\n");
                    cb.done(out.toString());
                    return;
                }

                String query = "timestamp=" + serverTime + "&recvWindow=5000";
                String sig = hmac(query, store.apiSecret);
                out.append("Signature generation: OK\n");

                HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/account?" + query + "&signature=" + sig).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setRequestProperty("X-MBX-APIKEY", store.apiKey);
                int code = c.getResponseCode();
                store.lastApiHttpCode = code;
                out.append("Account endpoint HTTP: ").append(code).append('\n');

                String body = read(c);
                if (code == 200) {
                    JSONObject account = new JSONObject(body);
                    boolean canTrade = account.optBoolean("canTrade", false);
                    boolean canWithdraw = account.optBoolean("canWithdraw", false);
                    boolean canDeposit = account.optBoolean("canDeposit", false);
                    String portfolioReport = applyAccountSnapshot(store, account, base);
                    store.lastApiMode = doctorMode;
                    store.lastApiPrivateOk = true;
                    store.lastApiCanTrade = canTrade;
                    store.lastApiAccountCanWithdraw = canWithdraw;
                    store.lastApiDiagnosis = canTrade ? "Private API OK and Spot trading permission appears enabled." : "Private API OK but trading is not enabled. Scanner/read-only allowed; live orders blocked.";
                    store.save();
                    out.append("Private account access: OK\n");
                    out.append("Read permission: OK\n");
                    out.append("Spot trading permission: ").append(canTrade ? "OK" : "OFF / READ ONLY").append('\n');
                    out.append("Account deposit ability: ").append(canDeposit ? "ON" : "OFF").append('\n');
                    out.append("Account withdraw ability flag: ").append(canWithdraw ? "ON - keep API-key withdrawals OFF manually" : "OFF").append('\n');
                    out.append('\n').append(portfolioReport);
                } else {
                    store.lastApiMode = doctorMode;
                    store.lastApiPrivateOk = false;
                    store.lastApiCanTrade = false;
                    store.lastApiAccountCanWithdraw = false;
                    store.lastApiDiagnosis = diagnosisFor(code, body, doctorMode);
                    store.portfolioSyncOk = false;
                    store.lastPortfolioSyncStatus = "Portfolio sync failed during API Doctor: HTTP " + code;
                    store.save();
                    out.append("Private account access: FAIL\n\n");
                    out.append(readableError(body)).append("\n\n");
                    out.append(store.lastApiDiagnosis);
                }
            } catch (Exception e) {
                store.lastApiMode = doctorMode;
                store.lastApiPrivateOk = false;
                store.lastApiCanTrade = false;
                store.lastApiDiagnosis = "API Doctor exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                store.portfolioSyncOk = false;
                store.lastPortfolioSyncStatus = store.lastApiDiagnosis;
                store.save();
                out.append("\nAPI Doctor failed: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
                out.append("\n").append(modeAdvice(doctorMode));
            }
            cb.done(out.toString());
        });
    }

    public static void syncSpotPortfolio(AppStore store, Callback cb) {
        exec.execute(() -> {
            try {
                if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) {
                    cb.done("Spot Portfolio Sync\n\nAdd Binance API key and secret first.");
                    return;
                }
                String base = baseUrl(store.mode);
                String report = syncPortfolioInternal(store, base);
                cb.done("Spot Portfolio Sync\n\n" + report);
            } catch (BinanceHttpException e) {
                store.portfolioSyncOk = false;
                store.lastPortfolioSyncStatus = "Portfolio sync failed: HTTP " + e.code;
                store.lastBinanceStatusCode = e.code;
                store.lastBinanceErrorDoctor = explainBinanceCode(e.code, e.body);
                store.save();
                cb.done("Spot Portfolio Sync Failed\n\nHTTP " + e.code + "\n" + readableError(e.body) + "\n\n" + explainBinanceCode(e.code, e.body));
            } catch (Exception e) {
                store.portfolioSyncOk = false;
                store.lastPortfolioSyncStatus = "Portfolio sync failed: " + e.getClass().getSimpleName();
                store.save();
                cb.done("Spot Portfolio Sync Failed\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Reads public closed candles and evaluates a local strategy. This method never signs or sends an order.
     */
    public static void scanScalper(AppStore store, ScalperCallback cb) {
        synchronized (BinanceClient.class) {
            if (scalperRequestInFlight) {
                if (cb != null) cb.done(null, "A live market scan is already running. Wait for it to finish before starting another.");
                return;
            }
            scalperRequestInFlight = true;
        }
        exec.execute(() -> {
            ScalpingStrategy.Signal signal = null;
            String report;
            try {
                String symbol = store.normalizeCoin(store.scalperSymbol);
                if (symbol.isEmpty()) symbol = "BTCUSDT";
                if (!isSupportedPair(symbol)) throw new IllegalArgumentException("Nanu supports BTCUSDT, ETHUSDT, BNBUSDT, and SOLUSDT only.");
                HttpURLConnection c = (HttpURLConnection)new URL("https://api.binance.com/api/v3/klines?symbol=" + enc(symbol) + "&interval=1m&limit=80").openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                int code = c.getResponseCode();
                String body = read(c);
                if (code < 200 || code >= 300) throw new BinanceHttpException(code, body);

                JSONArray rows = new JSONArray(body);
                List<ScalpingStrategy.Candle> candles = new ArrayList<>();
                // The final kline is still forming. The strategy uses only closed candles.
                for (int i = 0; i < Math.max(0, rows.length() - 1); i++) {
                    JSONArray row = rows.optJSONArray(i);
                    if (row == null || row.length() < 7) continue;
                    double high = parseDouble(row.optString(2, "NaN"));
                    double low = parseDouble(row.optString(3, "NaN"));
                    double close = parseDouble(row.optString(4, "NaN"));
                    long closeTime = row.optLong(6, 0L);
                    if (high > 0 && low > 0 && close > 0) candles.add(new ScalpingStrategy.Candle(closeTime, high, low, close));
                }
                signal = ScalpingStrategy.evaluate(candles);
                store.scalperSymbol = symbol;
                store.lastScalperCheckMs = System.currentTimeMillis();
                store.lastScalperPrice = signal.price;
                store.lastScalperSignal = signal.action.name();
                store.lastScalperConfidence = signal.confidence;
                store.lastScalperReport = signal.report(symbol, store.stopLoss, store.takeProfit);
                store.lastScalperError = "";
                store.scalperMarketChecks++;
                store.save();
                report = store.lastScalperReport;
            } catch (Exception e) {
                String detail = e instanceof BinanceHttpException
                        ? "HTTP " + ((BinanceHttpException)e).code + ": " + readableError(((BinanceHttpException)e).body)
                        : e.getClass().getSimpleName() + ": " + e.getMessage();
                store.lastScalperError = "Live market scan failed: " + detail;
                store.lastScalperReport = store.lastScalperError;
                store.save();
                report = store.lastScalperReport;
            } finally {
                synchronized (BinanceClient.class) { scalperRequestInFlight = false; }
            }
            if (cb != null) cb.done(signal, report);
        });
    }

    /** Reads a named approved pair without changing the user's selected dashboard pair. */
    public static void scanScalperSymbol(AppStore store, String requestedSymbol, boolean updateDashboard, ScalperCallback cb) {
        synchronized (BinanceClient.class) {
            if (scalperRequestInFlight) {
                if (cb != null) cb.done(null, "A live market scan is already running.");
                return;
            }
            scalperRequestInFlight = true;
        }
        exec.execute(() -> {
            ScalpingStrategy.Signal signal = null;
            String report;
            try {
                String symbol = store.normalizeCoin(requestedSymbol);
                if (!isSupportedPair(symbol)) throw new IllegalArgumentException("Automatic execution supports BTCUSDT, ETHUSDT, BNBUSDT, and SOLUSDT only.");
                HttpURLConnection c = (HttpURLConnection)new URL("https://api.binance.com/api/v3/klines?symbol=" + enc(symbol) + "&interval=1m&limit=80").openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                int code = c.getResponseCode();
                String body = read(c);
                if (code < 200 || code >= 300) throw new BinanceHttpException(code, body);

                JSONArray rows = new JSONArray(body);
                List<ScalpingStrategy.Candle> candles = new ArrayList<>();
                for (int i = 0; i < Math.max(0, rows.length() - 1); i++) {
                    JSONArray row = rows.optJSONArray(i);
                    if (row == null || row.length() < 7) continue;
                    double high = parseDouble(row.optString(2, "NaN"));
                    double low = parseDouble(row.optString(3, "NaN"));
                    double close = parseDouble(row.optString(4, "NaN"));
                    long closeTime = row.optLong(6, 0L);
                    if (high > 0 && low > 0 && close > 0) candles.add(new ScalpingStrategy.Candle(closeTime, high, low, close));
                }
                signal = ScalpingStrategy.evaluate(candles);
                store.scalperMarketChecks++;
                if (updateDashboard) {
                    store.scalperSymbol = symbol;
                    store.lastScalperCheckMs = System.currentTimeMillis();
                    store.lastScalperPrice = signal.price;
                    store.lastScalperSignal = signal.action.name();
                    store.lastScalperConfidence = signal.confidence;
                    store.lastScalperReport = signal.report(symbol, store.stopLoss, store.takeProfit);
                    store.lastScalperError = "";
                }
                store.save();
                report = signal.report(symbol, store.stopLoss, store.takeProfit);
            } catch (Exception e) {
                String detail = e instanceof BinanceHttpException
                        ? "HTTP " + ((BinanceHttpException)e).code + ": " + readableError(((BinanceHttpException)e).body)
                        : e.getClass().getSimpleName() + ": " + e.getMessage();
                report = "Live market scan failed for " + requestedSymbol + ": " + detail;
                if (updateDashboard) {
                    store.lastScalperError = report;
                    store.lastScalperReport = report;
                    store.save();
                }
            } finally {
                synchronized (BinanceClient.class) { scalperRequestInFlight = false; }
            }
            if (cb != null) cb.done(signal, report);
        });
    }

    public static void placeMarketOrder(AppStore store, String symbol, String side, double amount, Callback cb) {
        exec.execute(() -> {
            StringBuilder out = new StringBuilder();
            boolean realBuySubmitted = false;
            try {
                store.ensureDailySafetyWindow();
                String safeSymbol = (symbol == null || symbol.trim().isEmpty() ? "BTCUSDT" : symbol.trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", ""));
                String safeSide = "SELL".equalsIgnoreCase(side) ? "SELL" : "BUY";
                boolean testOnly = store.liveOrderTestMode;

                out.append("Nanu AI Trading Bot Protected Spot Order\n\n");
                out.append("Mode: ").append(store.mode.toUpperCase(Locale.US)).append('\n');
                out.append("Symbol: ").append(safeSymbol).append('\n');
                out.append("Side: ").append(safeSide).append('\n');
                out.append("Execution channel: ").append(testOnly ? "BINANCE /order/test (NO REAL FILL)" : "BINANCE REAL MARKET ORDER").append("\n\n");

                if (!isSupportedPair(safeSymbol)) { cb.done(out + "BLOCKED: Nanu is limited to BTCUSDT, ETHUSDT, BNBUSDT, and SOLUSDT."); return; }
                if (!"live".equals(store.mode)) { cb.done(out + "BLOCKED: select LIVE mode first."); return; }
                if (!store.liveUnlocked) { cb.done(out + "BLOCKED: LIVE gate is locked."); return; }
                if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) { cb.done(out + "BLOCKED: API key/secret missing."); return; }
                if ("BUY".equals(safeSide)) {
                    if (store.hasPendingProtectionCheck()) { cb.done(out + "BLOCKED: inspect Binance first. A previous " + store.pendingProtectionSymbol + " BUY has an unresolved protection check."); return; }
                    if (!store.apiTradingOkForCurrentMode()) { cb.done(out + "BLOCKED: API Doctor/spot trading permission not passed for LIVE mode."); return; }
                    if (!store.telegramDoctorOk) { cb.done(out + "BLOCKED: Telegram Doctor must pass before a live BUY."); return; }
                    if (!store.profitGuardEnabled) { cb.done(out + "BLOCKED: Profit Guard must be ON before a live BUY."); return; }
                    if (!store.panicButtonTested) { cb.done(out + "BLOCKED: Panic button must be tested before a live BUY."); return; }
                    if (!store.withdrawalPermissionConfirmedOff) { cb.done(out + "BLOCKED: API-key withdrawals must be manually confirmed OFF."); return; }
                    if (!store.complianceGuardEnabled) { cb.done(out + "BLOCKED: Compliance Guard is OFF."); return; }
                    if (store.binanceRateLimitLock) { cb.done(out + "BLOCKED: Binance rate-limit lock is active. Reset only after waiting and checking Binance."); return; }
                    if (store.engine.panic) { cb.done(out + "BLOCKED: Panic state is active."); return; }
                    if (store.liveTradesToday >= Math.max(1, store.maxLiveTradesPerDay)) { cb.done(out + "BLOCKED: max live trades/day reached."); return; }
                    if (System.currentTimeMillis() < store.orderCooldownUntilMs) { cb.done(out + "BLOCKED: order cooldown is still active."); return; }
                    if (!store.portfolioSyncOk || System.currentTimeMillis() - store.lastPortfolioSyncMs > 10 * 60 * 1000L) { cb.done(out + "BLOCKED: sync Binance Spot portfolio within 10 minutes before live/test order."); return; }
                    long previewAgeMs = System.currentTimeMillis() - store.lastOrderPreviewTime;
                    if (!store.lastOrderSafetyPass || !safeSymbol.equals(store.lastOrderSymbol) || previewAgeMs > 10 * 60 * 1000L) {
                        cb.done(out + "BLOCKED: run a fresh Live Dry-Run Preview for this symbol within 10 minutes.");
                        return;
                    }
                    if (!testOnly && !store.liveRealOrderArmed) { cb.done(out + "BLOCKED: real BUY is not armed. Keep Test Order ON or type ARM REAL BUY in app."); return; }
                } else {
                    out.append("Exit path: daily entry cap, entry cooldown, and BUY-only checklist gates are not applied to a manually confirmed SELL.\n");
                }

                String base = baseUrl(store.mode);
                SymbolRules rules = fetchSymbolRules(base, safeSymbol);
                out.append(rules.report).append("\n");
                if (!rules.ok) { cb.done(out + "\nBLOCKED: symbol is not tradable right now."); return; }
                if ("BUY".equals(safeSide) && (!positive(rules.stepSize) || !positive(rules.tickSize))) {
                    cb.done(out + "\nBLOCKED: Binance returned incomplete lot or price filters. Do not place an unprotected buy.");
                    return;
                }

                double safeAmount = Math.max(0.00000001, amount);
                if ("BUY".equals(safeSide)) {
                    if (safeAmount > store.manualOrderLimitUsdt) {
                        cb.done(out + "BLOCKED: requested BUY amount exceeds your manual order limit of " + fmt2(store.manualOrderLimitUsdt) + " USDT.");
                        return;
                    }
                    safeAmount = Math.max(0.01, safeAmount);
                    if (safeAmount < rules.minNotional) {
                        cb.done(out + "BLOCKED: quote amount is below Binance min notional " + fmt2(rules.minNotional) + " USDT.");
                        return;
                    }
                    if (!testOnly) {
                        double protectedMinimum = AutoTradingPolicy.minimumProtectedQuote(rules.minNotional, store.stopLoss);
                        if (safeAmount < protectedMinimum) {
                            cb.done(out + "BLOCKED: a real protected BUY needs at least " + fmt2(protectedMinimum)
                                    + " USDT so its Binance stop order remains valid after fees and price rounding.");
                            return;
                        }
                    }
                    if (!Double.isNaN(rules.maxNotional) && safeAmount > rules.maxNotional) {
                        cb.done(out + "BLOCKED: quote amount is above Binance max notional " + fmt2(rules.maxNotional) + " USDT.");
                        return;
                    }
                    if (!Double.isNaN(store.spotFreeUsdt) && store.spotFreeUsdt < safeAmount) {
                        cb.done(out + "BLOCKED: free USDT is below requested BUY amount. Sync shows " + fmt2(store.spotFreeUsdt) + " USDT free.");
                        return;
                    }
                } else {
                    double step = rules.marketStepSize > 0 ? rules.marketStepSize : rules.stepSize;
                    double minQty = rules.marketMinQty > 0 ? rules.marketMinQty : rules.minQty;
                    double maxQty = !Double.isNaN(rules.marketMaxQty) ? rules.marketMaxQty : rules.maxQty;
                    safeAmount = roundDownToStep(safeAmount, step);
                    if (safeAmount <= 0 || safeAmount < minQty) {
                        cb.done(out + "BLOCKED: sell quantity is below Binance minimum quantity after step rounding.");
                        return;
                    }
                    if (!Double.isNaN(maxQty) && safeAmount > maxQty) {
                        cb.done(out + "BLOCKED: sell quantity is above Binance maximum quantity.");
                        return;
                    }
                }

                double expectedEntryPrice = Double.NaN;
                if ("BUY".equals(safeSide) && !testOnly) expectedEntryPrice = lastPrice(base, safeSymbol);
                long ts = serverTime(base);
                String params;
                if ("BUY".equals(safeSide)) {
                    params = "symbol=" + enc(safeSymbol) + "&side=BUY&type=MARKET&quoteOrderQty=" + enc(String.format(Locale.US, "%.2f", safeAmount)) + "&newOrderRespType=" + (testOnly ? "RESULT" : "FULL") + "&newClientOrderId=" + enc(clientId("nanu-buy", safeSymbol)) + "&timestamp=" + ts + "&recvWindow=5000";
                    out.append("Quote amount: ").append(String.format(Locale.US, "%.2f USDT", safeAmount)).append("\n");
                } else {
                    params = "symbol=" + enc(safeSymbol) + "&side=SELL&type=MARKET&quantity=" + enc(qtyForOrder(safeAmount)) + "&newOrderRespType=RESULT&timestamp=" + ts + "&recvWindow=5000";
                    out.append("Sell quantity: ").append(qtyForOrder(safeAmount)).append("\n");
                }
                String sig = hmac(params, store.apiSecret);
                String endpoint = base + (testOnly ? "/api/v3/order/test" : "/api/v3/order") + "?" + params + "&signature=" + sig;

                if ("BUY".equals(safeSide) && !testOnly) {
                    // Persist before the request so an app/network interruption cannot silently permit another BUY.
                    store.beginProtectionCheck(safeSymbol);
                }

                HttpURLConnection c = (HttpURLConnection)new URL(endpoint).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setRequestProperty("X-MBX-APIKEY", store.apiKey);
                c.setDoOutput(true);
                byte[] empty = new byte[0];
                c.setFixedLengthStreamingMode(empty.length);
                try (OutputStream os = c.getOutputStream()) { os.write(empty); }

                int code = c.getResponseCode();
                String body = read(c);
                store.lastBinanceStatusCode = code;
                out.append("\nBinance HTTP: ").append(code).append('\n');
                if (code >= 200 && code < 300) {
                    if (testOnly) {
                        out.append("RESULT: TEST ORDER PASSED\nNo real order was created. Binance accepted the order format/signature/symbol rules.\n");
                        if ("BUY".equals(safeSide)) store.autoBinanceTestOrderPassed = true;
                        store.engine.addJournal("Binance test order PASS: " + safeSide + " " + safeSymbol);
                    } else {
                        out.append("RESULT: REAL MARKET ORDER ACCEPTED\n");
                        if ("BUY".equals(safeSide)) {
                            store.liveTradesToday++;
                            store.liveRealOrderArmed = false;
                            realBuySubmitted = true;
                            OcoProtection protection = createOcoProtection(store, base, safeSymbol, rules, new JSONObject(body), expectedEntryPrice);
                            out.append(protection.report);
                            store.clearProtectionCheck("Binance OCO creation confirmed");
                            store.engine.addJournal("REAL PROTECTED BUY: " + safeSymbol);
                            store.triggerAlert("Nanu Protected Spot Buy", safeSymbol + " filled with Binance OCO exit protection. Verify order history.", true, "live");
                        } else {
                            store.engine.addJournal("REAL MANUAL SELL: " + safeSymbol);
                            store.triggerAlert("Nanu Real Spot Sell", safeSymbol + " sell submitted. Check Binance order history.", true, "live");
                        }
                    }
                    store.orderCooldownUntilMs = System.currentTimeMillis() + Math.max(10, store.orderCooldownSeconds) * 1000L;
                    store.lastBinanceErrorDoctor = "Last Binance order/test response was OK.";
                    try {
                        out.append("\n").append(syncPortfolioInternal(store, base));
                    } catch (Exception syncError) {
                        out.append("\nPortfolio refresh after order did not complete: ").append(syncError.getMessage()).append('\n');
                    }
                } else {
                    out.append("RESULT: BLOCKED / FAILED\n").append(readableError(body)).append("\n\n");
                    out.append(explainBinanceCode(code, body));
                    store.lockAfterExchangeDanger(code, explainBinanceCode(code, body));
                    store.triggerAlert("Nanu Binance Order Error", "HTTP " + code + " during " + safeSide + " " + safeSymbol + ". Check Error Doctor.", true, "api");
                }
                if (body != null && !body.trim().isEmpty()) out.append("\nRaw response:\n").append(readableError(body)).append('\n');
                store.lastLiveOrderReport = out.toString();
                store.save();
                cb.done(out.toString());
            } catch (Exception e) {
                if (realBuySubmitted) {
                    store.triggerAlert("Nanu Protection Error", "A real " + "buy" + " was submitted but exit protection needs immediate review: " + e.getMessage(), true, "live");
                }
                store.lastLiveOrderReport = "Order exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                store.save();
                cb.done(out + "\nORDER FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /** Submits one automatic real BUY only after the automatic engine has passed its session gates. */
    public static void placeAutomaticMarketBuy(AppStore store, String symbol, ScalpingStrategy.Signal signal, AutoCallback cb) {
        exec.execute(() -> {
            String safeSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
            try {
                store.ensureDailySafetyWindow();
                if (!store.autoRunning || store.autoPanic || store.engine.panic) {
                    cb.done(new AutoResult(false, "Automatic executor is not running."));
                    return;
                }
                if (!isSupportedPair(safeSymbol)) {
                    cb.done(new AutoResult(false, "Automatic execution attempted an unsupported pair."));
                    return;
                }
                String blockers = store.autoStartBlockers();
                if (!blockers.isEmpty()) {
                    cb.done(new AutoResult(false, "Automatic entry is blocked:\n" + blockers));
                    return;
                }
                if (store.hasAutoPosition() || store.hasAutoPendingOrder()) {
                    cb.done(new AutoResult(false, "Automatic entry is blocked until the existing Binance position or pending order is reconciled."));
                    return;
                }

                String base = baseUrl(store.mode);
                SymbolRules rules = fetchSymbolRules(base, safeSymbol);
                if (!rules.ok || !positive(rules.stepSize) || !positive(rules.tickSize)) {
                    cb.done(new AutoResult(false, "Automatic entry blocked: " + rules.report));
                    return;
                }
                double amount = Math.max(0.01, store.autoOrderAmountUsdt);
                double protectedMinimum = AutoTradingPolicy.minimumAutomaticProtectedQuote(rules.minNotional, store.stopLoss);
                if (amount < protectedMinimum || (!Double.isNaN(rules.maxNotional) && amount > rules.maxNotional)) {
                    cb.done(new AutoResult(false, "Automatic entry blocked before Binance BUY: use at least "
                            + fmt2(protectedMinimum) + " USDT so the OCO stop can remain above Binance's minimum after fees."));
                    return;
                }
                if (!Double.isNaN(store.spotFreeUsdt) && store.spotFreeUsdt < amount) {
                    cb.done(new AutoResult(false, "Automatic entry blocked: insufficient free USDT in the last portfolio sync."));
                    return;
                }
                double expectedEntryPrice = lastPrice(base, safeSymbol);

                String clientOrderId = clientId("nanu-auto", safeSymbol);
                store.autoPendingClientOrderId = clientOrderId;
                store.autoPendingSymbol = safeSymbol;
                store.autoPendingStartedMs = System.currentTimeMillis();
                store.autoPendingEntryCounted = false;
                store.beginProtectionCheck(safeSymbol);
                store.save();

                String params = "symbol=" + enc(safeSymbol)
                        + "&side=BUY&type=MARKET"
                        + "&quoteOrderQty=" + enc(String.format(Locale.US, "%.2f", amount))
                        + "&newOrderRespType=FULL"
                        + "&newClientOrderId=" + enc(clientOrderId)
                        + "&timestamp=" + serverTime(base)
                        + "&recvWindow=5000";
                SignedResponse response = signedPost(base, "/api/v3/order", params, store.apiKey, store.apiSecret);
                store.lastBinanceStatusCode = response.code;
                if (response.code < 200 || response.code >= 300) {
                    String message = "Binance rejected automatic " + safeSymbol + " BUY (HTTP " + response.code + "): " + readableError(response.body);
                    store.lockAfterExchangeDanger(response.code, explainBinanceCode(response.code, response.body));
                    store.lastLiveOrderReport = message;
                    store.save();
                    cb.done(new AutoResult(false, message));
                    return;
                }

                OcoProtection protection = createOcoProtection(store, base, safeSymbol, rules, new JSONObject(response.body), expectedEntryPrice);
                recordAutomaticProtection(store, safeSymbol, protection);
                store.liveTradesToday++;
                store.autoPendingEntryCounted = true;
                store.clearAutoPendingOrder();
                store.clearProtectionCheck("Automatic Binance OCO creation confirmed");
                store.orderCooldownUntilMs = System.currentTimeMillis() + Math.max(10, store.orderCooldownSeconds) * 1000L;
                store.lastBinanceErrorDoctor = "Automatic entry and exchange-side OCO protection confirmed.";
                store.lastLiveOrderReport = "AUTOMATIC LIVE ENTRY\n" + protection.report;
                store.engine.addJournal("AUTO PROTECTED BUY: " + safeSymbol + " " + String.format(Locale.US, "%.2f", amount) + " USDT.");
                store.triggerAlert("Nanu Automatic Protected Buy", safeSymbol + " entered automatically at " + signal.confidence + "/100 with Binance OCO target and stop protection.", true, "live");
                try { syncPortfolioInternal(store, base); } catch (Exception ignored) {}
                store.save();
                cb.done(new AutoResult(true, "Automatic protected BUY accepted for " + safeSymbol + ". OCO list " + protection.listId + " is being monitored."));
            } catch (Exception e) {
                String message = "Automatic order exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                store.lastLiveOrderReport = message;
                store.save();
                cb.done(new AutoResult(false, message + " Check Binance order history and Open Orders."));
            }
        });
    }

    /** Recovers a submitted automatic BUY and reconciles its Binance OCO before any new entry. */
    public static void reconcileAutomaticState(AppStore store, AutoCallback cb) {
        exec.execute(() -> {
            try {
                if (!"live".equals(store.mode) || store.apiKey.isEmpty() || store.apiSecret.isEmpty()) {
                    cb.done(new AutoResult(false, "Automatic reconciliation requires LIVE API credentials."));
                    return;
                }
                String base = baseUrl(store.mode);
                if (store.hasAutoPendingOrder() && !store.hasAutoPosition()) {
                    recoverAutomaticPendingOrder(store, base);
                }
                if (!store.hasAutoPosition()) {
                    cb.done(new AutoResult(true, "No automatic exchange position is open."));
                    return;
                }
                String symbol = store.autoActiveSymbol;
                String listParams = "orderListId=" + store.autoOcoOrderListId + "&timestamp=" + serverTime(base) + "&recvWindow=5000";
                SignedResponse listResponse = signedGet(base, "/api/v3/orderList", listParams, store.apiKey, store.apiSecret);
                if (listResponse.code < 200 || listResponse.code >= 300) {
                    cb.done(new AutoResult(false, "Cannot reconcile Binance OCO list (HTTP " + listResponse.code + "): " + readableError(listResponse.body)));
                    return;
                }
                JSONObject orderList = new JSONObject(listResponse.body);
                String listStatus = orderList.optString("listOrderStatus", "").toUpperCase(Locale.US);
                if ("EXECUTING".equals(listStatus)) {
                    cb.done(new AutoResult(true, "Automatic " + symbol + " OCO protection is active on Binance."));
                    return;
                }
                if (!"ALL_DONE".equals(listStatus)) {
                    cb.done(new AutoResult(false, "Unexpected Binance OCO state " + listStatus + " for " + symbol + "."));
                    return;
                }

                long takeProfitId = store.autoTakeProfitOrderId;
                long stopId = store.autoStopOrderId;
                JSONArray orders = orderList.optJSONArray("orders");
                if (orders != null) {
                    for (int i = 0; i < orders.length(); i++) {
                        JSONObject item = orders.optJSONObject(i);
                        if (item == null) continue;
                        long id = item.optLong("orderId", 0L);
                        if (takeProfitId == 0L) takeProfitId = id;
                        else if (stopId == 0L && id != takeProfitId) stopId = id;
                    }
                }
                JSONObject filled = null;
                if (takeProfitId > 0L) {
                    JSONObject order = queryOrder(base, symbol, takeProfitId, store);
                    if ("FILLED".equalsIgnoreCase(order.optString("status"))) filled = order;
                }
                if (filled == null && stopId > 0L) {
                    JSONObject order = queryOrder(base, symbol, stopId, store);
                    if ("FILLED".equalsIgnoreCase(order.optString("status"))) filled = order;
                }
                if (filled == null) {
                    cb.done(new AutoResult(false, "Binance OCO completed without a confirmed filled child order for " + symbol + "."));
                    return;
                }
                double exitQuote = parseDouble(filled.optString("cummulativeQuoteQty", "0"));
                double exitQty = parseDouble(filled.optString("executedQty", "0"));
                if (!positive(exitQuote) || !positive(exitQty)) {
                    cb.done(new AutoResult(false, "Binance OCO exit has no confirmed executed quantity for " + symbol + "."));
                    return;
                }
                double pnl = exitQuote - store.autoEntryQuoteUsdt;
                String exitType = "LIMIT_MAKER".equalsIgnoreCase(filled.optString("type")) ? "TAKE PROFIT" : "STOP LOSS";
                store.autoRealizedPnlUsdt += pnl;
                store.consecutiveLosses = pnl < 0 ? store.consecutiveLosses + 1 : 0;
                store.recordBrainObservation(symbol + " " + exitType, pnl, true);
                store.engine.addJournal("AUTO " + exitType + ": " + symbol + " " + String.format(Locale.US, "%+.2f", pnl) + " USDT.");
                if (store.hasPendingProtectionCheck() && symbol.equals(store.pendingProtectionSymbol)) store.clearProtectionCheck("Automatic OCO exit reconciled");
                store.clearAutoPosition("Binance " + exitType + " confirmed");
                try { syncPortfolioInternal(store, base); } catch (Exception ignored) {}
                store.triggerAlert("Nanu Automatic " + exitType, symbol + " closed through Binance OCO. Estimated realized P&L " + String.format(Locale.US, "%+.2f", pnl) + " USDT.", true, "live");
                cb.done(new AutoResult(true, "Automatic " + symbol + " " + exitType + " confirmed. Estimated P&L " + String.format(Locale.US, "%+.2f", pnl) + " USDT."));
            } catch (Exception e) {
                cb.done(new AutoResult(false, "Automatic reconciliation exception: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        });
    }

    public static void emergencyCloseAutomaticPosition(AppStore store, AutoCallback cb) {
        exec.execute(() -> {
            try {
                if (!store.hasAutoPosition() && !store.hasAutoPendingOrder()) {
                    cb.done(new AutoResult(false, "No tracked automatic position or pending automatic BUY is open."));
                    return;
                }
                String base = baseUrl(store.mode);
                String symbol = store.hasAutoPosition() ? store.autoActiveSymbol : store.autoPendingSymbol;
                if (store.hasAutoPosition()) {
                    String cancelParams = "orderListId=" + store.autoOcoOrderListId + "&timestamp=" + serverTime(base) + "&recvWindow=5000";
                    SignedResponse cancel = signedDelete(base, "/api/v3/orderList", cancelParams, store.apiKey, store.apiSecret);
                    if (cancel.code < 200 || cancel.code >= 300) {
                        cb.done(new AutoResult(false, "Could not cancel this app's Binance OCO list (HTTP " + cancel.code + "): " + readableError(cancel.body)));
                        return;
                    }
                }
                SymbolRules rules = fetchSymbolRules(base, symbol);
                if (!rules.ok) {
                    cb.done(new AutoResult(false, "Could not load current Binance rules for " + symbol + "."));
                    return;
                }
                double quantity = store.hasAutoPosition() ? store.autoProtectedQuantity : pendingAutomaticFilledQuantity(store, base);
                String sell = attemptEmergencySell(store, base, symbol, rules, quantity);
                if (!sell.startsWith("Emergency market sell submitted")) {
                    cb.done(new AutoResult(false, sell));
                    return;
                }
                store.clearAutoPosition("Emergency market sell submitted");
                if (store.hasPendingProtectionCheck() && symbol.equals(store.pendingProtectionSymbol)) store.clearProtectionCheck("Emergency market sell submitted");
                cb.done(new AutoResult(true, sell));
            } catch (Exception e) {
                cb.done(new AutoResult(false, "Emergency close exception: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        });
    }

    public static void verifyTrustedIp(AppStore store, AutoCallback cb) {
        exec.execute(() -> {
            try {
                String expected = store.autoTrustedStaticIp == null ? "" : store.autoTrustedStaticIp.trim();
                String current = publicIpSafe();
                if (!current.isEmpty()) {
                    store.lastPublicIp = current;
                    store.lastPublicIpCheckedMs = System.currentTimeMillis();
                    store.save();
                }
                if (!AutoTradingPolicy.isStaticIp(expected)) {
                    cb.done(new AutoResult(false, "Expected static public IP is not configured."));
                } else if (current.isEmpty()) {
                    cb.done(new AutoResult(false, "Could not determine this device's public IP."));
                } else if (!AutoTradingPolicy.publicIpMatches(expected, current)) {
                    cb.done(new AutoResult(false, "Static-IP mismatch. Expected " + expected + " but this device is using " + current + ". Binance trusted IP must match before automatic execution."));
                } else {
                    cb.done(new AutoResult(true, "Static IP verified: " + current));
                }
            } catch (Exception e) {
                cb.done(new AutoResult(false, "Static-IP check failed: " + e.getMessage()));
            }
        });
    }

    private static void recordAutomaticProtection(AppStore store, String symbol, OcoProtection protection) {
        if (protection == null || protection.listId <= 0L) {
            throw new IllegalStateException("Binance did not return a usable OCO order list id.");
        }
        store.autoActiveSymbol = symbol;
        store.autoOcoOrderListId = protection.listId;
        store.autoTakeProfitOrderId = protection.takeProfitOrderId;
        store.autoStopOrderId = protection.stopOrderId;
        store.autoEntryQuoteUsdt = protection.entryQuote;
        store.autoEntryPrice = protection.entryPrice;
        store.autoProtectedQuantity = protection.protectedQuantity;
        store.autoPositionOpenedMs = System.currentTimeMillis();
    }

    private static void recoverAutomaticPendingOrder(AppStore store, String base) throws Exception {
        String symbol = store.autoPendingSymbol;
        JSONObject buy = pendingAutomaticOrder(store, base);
        if (!"FILLED".equalsIgnoreCase(buy.optString("status"))) {
            throw new IllegalStateException("Pending automatic order status is " + buy.optString("status", "unknown") + ".");
        }
        SymbolRules rules = fetchSymbolRules(base, symbol);
        if (!rules.ok) throw new IllegalStateException("Cannot load active Binance rules for pending " + symbol + " order.");
        OcoProtection protection = createOcoProtection(store, base, symbol, rules, buy, Double.NaN);
        recordAutomaticProtection(store, symbol, protection);
        if (!store.autoPendingEntryCounted) store.liveTradesToday++;
        store.clearAutoPendingOrder();
        if (store.hasPendingProtectionCheck() && symbol.equals(store.pendingProtectionSymbol)) {
            store.clearProtectionCheck("Recovered automatic order and created Binance OCO");
        }
        store.lastLiveOrderReport = "RECOVERED AUTOMATIC ORDER\n" + protection.report;
        store.engine.addJournal("Recovered automatic protected BUY: " + symbol + ".");
        store.save();
    }

    private static double pendingAutomaticFilledQuantity(AppStore store, String base) throws Exception {
        JSONObject buy = pendingAutomaticOrder(store, base);
        if (!"FILLED".equalsIgnoreCase(buy.optString("status"))) {
            throw new IllegalStateException("Pending automatic order status is " + buy.optString("status", "unknown") + ".");
        }
        return netBaseQuantity(buy, store.autoPendingSymbol);
    }

    private static JSONObject pendingAutomaticOrder(AppStore store, String base) throws Exception {
        String params = "symbol=" + enc(store.autoPendingSymbol)
                + "&origClientOrderId=" + enc(store.autoPendingClientOrderId)
                + "&timestamp=" + serverTime(base)
                + "&recvWindow=5000";
        SignedResponse response = signedGet(base, "/api/v3/order", params, store.apiKey, store.apiSecret);
        if (response.code < 200 || response.code >= 300) {
            throw new IOException("Pending automatic order lookup failed (HTTP " + response.code + "): " + readableError(response.body));
        }
        return new JSONObject(response.body);
    }

    private static JSONObject queryOrder(String base, String symbol, long orderId, AppStore store) throws Exception {
        String params = "symbol=" + enc(symbol) + "&orderId=" + orderId + "&timestamp=" + serverTime(base) + "&recvWindow=5000";
        SignedResponse response = signedGet(base, "/api/v3/order", params, store.apiKey, store.apiSecret);
        if (response.code < 200 || response.code >= 300) {
            throw new IOException("Binance order lookup failed (HTTP " + response.code + "): " + readableError(response.body));
        }
        return new JSONObject(response.body);
    }

    public static void getPublicIp(AppStore store, Callback cb) {
        exec.execute(() -> {
            try {
                String ip = publicIpSafe();
                if (!ip.isEmpty()) { store.lastPublicIp = ip; store.lastPublicIpCheckedMs = System.currentTimeMillis(); store.save(); }
                cb.done("Trusted IP Helper\n\nYour current public IP appears to be:\n" + (ip.isEmpty() ? "Unknown" : ip) + "\n\nThis screen only shows the address; it cannot make your IP static. Add the same stable IP to Binance API trusted IP restrictions. Mobile networks often change IP.");
            } catch (Exception e) {
                cb.done("Trusted IP Helper failed: " + e.getMessage());
            }
        });
    }

    private static String syncPortfolioInternal(AppStore store, String base) throws Exception {
        JSONObject account = fetchAccount(base, store.apiKey, store.apiSecret);
        String report = applyAccountSnapshot(store, account, base);
        store.save();
        return report;
    }

    private static JSONObject fetchAccount(String base, String apiKey, String apiSecret) throws Exception {
        long ts = serverTime(base);
        String query = "timestamp=" + ts + "&recvWindow=5000";
        String sig = hmac(query, apiSecret);
        HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/account?" + query + "&signature=" + sig).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("X-MBX-APIKEY", apiKey);
        int code = c.getResponseCode();
        String body = read(c);
        if (code < 200 || code >= 300) throw new BinanceHttpException(code, body);
        return new JSONObject(body);
    }

    private static String applyAccountSnapshot(AppStore store, JSONObject account, String base) throws Exception {
        Map<String, Double> prices = fetchAllTickerPrices(base);
        JSONArray balances = account.optJSONArray("balances");
        List<AssetValue> valued = new ArrayList<>();
        double equity = 0.0;
        double usdtFree = 0.0;
        double usdtLocked = 0.0;
        int nonZeroAssets = 0;
        int unpricedAssets = 0;

        if (balances != null) {
            for (int i = 0; i < balances.length(); i++) {
                JSONObject b = balances.optJSONObject(i);
                if (b == null) continue;
                String asset = b.optString("asset", "").toUpperCase(Locale.US);
                double free = parseDouble(b.optString("free", "0"));
                double locked = parseDouble(b.optString("locked", "0"));
                double total = free + locked;
                if (asset.isEmpty() || total <= 0.0000000001) continue;
                nonZeroAssets++;

                AssetValue av = new AssetValue();
                av.asset = asset;
                av.free = free;
                av.locked = locked;
                av.priceUsdt = priceForAsset(asset, prices);
                av.priced = !Double.isNaN(av.priceUsdt);
                av.valueUsdt = av.priced ? total * av.priceUsdt : 0.0;
                if (!av.priced) unpricedAssets++;
                if ("USDT".equals(asset)) {
                    usdtFree = free;
                    usdtLocked = locked;
                }
                equity += av.valueUsdt;
                valued.add(av);
            }
        }

        Collections.sort(valued);
        store.lastUsdtFree = usdtFree;
        store.lastUsdtLocked = usdtLocked;
        store.lastBtcFree = findFree(valued, "BTC");
        store.spotEquityUsdt = equity;
        store.spotFreeUsdt = usdtFree;
        store.spotLockedUsdt = usdtLocked;
        store.spotAssetCount = nonZeroAssets;
        store.lastPortfolioSyncMs = System.currentTimeMillis();
        store.portfolioSyncOk = true;
        store.topPortfolioAssets = topAssets(valued);
        store.portfolioWarnings = unpricedAssets == 0 ? "" : unpricedAssets + " asset(s) could not be priced to USDT and are shown as zero value.";
        store.lastPortfolioSyncStatus = String.format(Locale.US, "OK: %.2f USDT across %d asset(s)%s", equity, nonZeroAssets, unpricedAssets == 0 ? "" : " with " + unpricedAssets + " unpriced");
        store.lastBalanceSnapshot = String.format(Locale.US, "Spot equity %.2f USDT • Free USDT %.4f • Locked USDT %.4f • Assets %d • Synced now", equity, usdtFree, usdtLocked, nonZeroAssets);
        if ("live".equals(store.mode) && Double.isNaN(store.liveEquityBaselineUsdt)) {
            store.liveEquityBaselineUsdt = equity;
        }
        store.engine.equity = equity;

        StringBuilder out = new StringBuilder();
        out.append("Spot portfolio sync: OK\n");
        out.append("Spot equity: ").append(String.format(Locale.US, "%.2f USDT", equity)).append('\n');
        out.append("USDT free: ").append(String.format(Locale.US, "%.4f", usdtFree)).append(" • locked: ").append(String.format(Locale.US, "%.4f", usdtLocked)).append('\n');
        out.append("Non-zero assets: ").append(nonZeroAssets).append('\n');
        out.append("Top assets: ").append(store.topPortfolioAssets).append('\n');
        if (!store.portfolioWarnings.isEmpty()) out.append("Warning: ").append(store.portfolioWarnings).append('\n');
        return out.toString();
    }

    private static Map<String, Double> fetchAllTickerPrices(String base) {
        Map<String, Double> prices = new HashMap<>();
        try {
            HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/ticker/price").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) return prices;
            JSONArray arr = new JSONArray(read(c));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String symbol = item.optString("symbol", "").toUpperCase(Locale.US);
                double price = parseDouble(item.optString("price", "NaN"));
                if (!symbol.isEmpty() && price > 0) prices.put(symbol, price);
            }
        } catch (Exception ignored) {}
        return prices;
    }

    private static SymbolRules fetchSymbolRules(String base, String symbol) {
        SymbolRules rules = new SymbolRules();
        try {
            HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/exchangeInfo?symbol=" + enc(symbol)).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            int code = c.getResponseCode();
            String body = read(c);
            if (code < 200 || code >= 300) {
                rules.report = "Symbol rules HTTP " + code + ": " + readableError(body);
                return rules;
            }
            JSONObject root = new JSONObject(body);
            JSONArray symbols = root.optJSONArray("symbols");
            if (symbols == null || symbols.length() == 0) {
                rules.report = "Symbol rules: symbol not found.";
                return rules;
            }
            JSONObject s = symbols.getJSONObject(0);
            rules.status = s.optString("status", "UNKNOWN");
            JSONArray filters = s.optJSONArray("filters");
            if (filters != null) {
                for (int i = 0; i < filters.length(); i++) {
                    JSONObject f = filters.optJSONObject(i);
                    if (f == null) continue;
                    String type = f.optString("filterType", "");
                    if ("LOT_SIZE".equals(type)) {
                        rules.minQty = parseDouble(f.optString("minQty", "0"));
                        rules.maxQty = parseDouble(f.optString("maxQty", "NaN"));
                        rules.stepSize = parseDouble(f.optString("stepSize", "0"));
                    } else if ("PRICE_FILTER".equals(type)) {
                        rules.tickSize = parseDouble(f.optString("tickSize", "0"));
                    } else if ("MARKET_LOT_SIZE".equals(type)) {
                        rules.marketMinQty = parseDouble(f.optString("minQty", "0"));
                        rules.marketMaxQty = parseDouble(f.optString("maxQty", "NaN"));
                        rules.marketStepSize = parseDouble(f.optString("stepSize", "0"));
                    } else if ("MIN_NOTIONAL".equals(type)) {
                        rules.minNotional = Math.max(rules.minNotional, parseDouble(f.optString("minNotional", "5")));
                    } else if ("NOTIONAL".equals(type)) {
                        rules.minNotional = Math.max(rules.minNotional, parseDouble(f.optString("minNotional", "5")));
                        rules.maxNotional = parseDouble(f.optString("maxNotional", "NaN"));
                    }
                }
            }
            rules.ok = "TRADING".equalsIgnoreCase(rules.status);
            rules.report = String.format(Locale.US,
                    "Binance symbol rules: %s • min notional %.2f USDT • lot step %s • market step %s",
                    rules.status,
                    rules.minNotional,
                    qtyForReport(rules.stepSize),
                    qtyForReport(rules.marketStepSize));
        } catch (Exception e) {
            rules.report = "Symbol rules failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return rules;
    }

    /**
     * Creates Binance-side sell protection after a real Spot buy. If the exchange rejects
     * the OCO, this method immediately attempts a market sell instead of leaving the fill open.
     */
    private static OcoProtection createOcoProtection(AppStore store, String base, String symbol, SymbolRules rules, JSONObject buy, double expectedEntryPrice) throws Exception {
        String status = buy.optString("status", "").toUpperCase(Locale.US);
        double executedQty = parseDouble(buy.optString("executedQty", "0"));
        double quoteSpent = parseDouble(buy.optString("cummulativeQuoteQty", "0"));
        double netBaseQty = netBaseQuantity(buy, symbol);
        if (!"FILLED".equals(status) || !positive(executedQty) || !positive(quoteSpent)) {
            String emergency = attemptEmergencySell(store, base, symbol, rules, netBaseQty);
            throw new IllegalStateException("Buy status was " + (status.isEmpty() ? "unknown" : status) + "; OCO was not placed. " + emergency);
        }

        // A FULL market-buy response includes fills, allowing base-asset commissions to be removed exactly.
        double protectedQty = roundDownToStep(netBaseQty, rules.stepSize);
        if (!positive(protectedQty) || protectedQty < rules.minQty || (!Double.isNaN(rules.maxQty) && protectedQty > rules.maxQty)) {
            String emergency = attemptEmergencySell(store, base, symbol, rules, protectedQty);
            throw new IllegalStateException("Filled quantity cannot satisfy Binance OCO lot rules. " + emergency);
        }

        double entryPrice = quoteSpent / executedQty;
        if (positive(expectedEntryPrice) && !AutoTradingPolicy.entryWithinSlippage(expectedEntryPrice, entryPrice, store.slippageLimitPct)) {
            String emergency = attemptEmergencySell(store, base, symbol, rules, protectedQty);
            throw new IllegalStateException("Market BUY exceeded the configured " + fmt2(store.slippageLimitPct)
                    + "% slippage limit. " + emergency);
        }
        double current = lastPrice(base, symbol);
        AutoTradingPolicy.OcoLevels levels = AutoTradingPolicy.calculateOcoLevels(
                entryPrice, current, store.takeProfit, store.stopLoss, rules.tickSize);
        if (levels == null || protectedQty * (levels == null ? 0d : levels.stopLimit) < rules.minNotional) {
            String emergency = attemptEmergencySell(store, base, symbol, rules, protectedQty);
            throw new IllegalStateException("OCO could not be safely placed at the current Binance price without weakening the configured stop. " + emergency);
        }
        double takeProfit = levels.takeProfit;
        double stopPrice = levels.stopPrice;
        double stopLimit = levels.stopLimit;

        String params = "symbol=" + enc(symbol)
                + "&side=SELL"
                + "&quantity=" + enc(qtyForOrder(protectedQty))
                + "&aboveType=LIMIT_MAKER"
                + "&abovePrice=" + enc(qtyForOrder(takeProfit))
                + "&aboveClientOrderId=" + enc(clientId("nanu-tp", symbol))
                + "&belowType=STOP_LOSS_LIMIT"
                + "&belowStopPrice=" + enc(qtyForOrder(stopPrice))
                + "&belowPrice=" + enc(qtyForOrder(stopLimit))
                + "&belowTimeInForce=GTC"
                + "&belowClientOrderId=" + enc(clientId("nanu-sl", symbol))
                + "&listClientOrderId=" + enc(clientId("nanu-oco", symbol))
                + "&newOrderRespType=RESULT"
                + "&timestamp=" + serverTime(base)
                + "&recvWindow=5000";
        SignedResponse response = signedPost(base, "/api/v3/orderList/oco", params, store.apiKey, store.apiSecret);
        if (response.code < 200 || response.code >= 300) {
            String emergency = attemptEmergencySell(store, base, symbol, rules, protectedQty);
            throw new IOException("Binance rejected OCO protection (HTTP " + response.code + "): " + readableError(response.body) + ". " + emergency);
        }
        JSONObject orderList = new JSONObject(response.body);
        OcoProtection protection = new OcoProtection();
        protection.listId = orderList.optLong("orderListId", 0L);
        protection.entryQuote = quoteSpent;
        protection.entryPrice = entryPrice;
        protection.protectedQuantity = protectedQty;
        JSONArray reports = orderList.optJSONArray("orderReports");
        if (reports != null) {
            for (int i = 0; i < reports.length(); i++) {
                JSONObject order = reports.optJSONObject(i);
                if (order == null) continue;
                String type = order.optString("type", "");
                if ("LIMIT_MAKER".equals(type)) protection.takeProfitOrderId = order.optLong("orderId", 0L);
                if ("STOP_LOSS_LIMIT".equals(type)) protection.stopOrderId = order.optLong("orderId", 0L);
            }
        }
        protection.report = "PROTECTION: OCO CREATED ON BINANCE\n"
                + "Entry: " + qtyForOrder(entryPrice) + " USDT\n"
                + "Protected quantity: " + qtyForOrder(protectedQty) + " " + baseAsset(symbol) + "\n"
                + "Take profit: " + qtyForOrder(takeProfit) + " USDT\n"
                + "Stop trigger: " + qtyForOrder(stopPrice) + " USDT\n"
                + "Stop limit: " + qtyForOrder(stopLimit) + " USDT\n"
                + "Binance OCO list id: " + protection.listId + "\n"
                + "Verify both exit orders in Binance Open Orders now.\n";
        return protection;
    }

    private static String attemptEmergencySell(AppStore store, String base, String symbol, SymbolRules rules, double quantity) {
        try {
            double step = positive(rules.marketStepSize) ? rules.marketStepSize : rules.stepSize;
            double sellQuantity = roundDownToStep(quantity, step);
            double minQty = positive(rules.marketMinQty) ? rules.marketMinQty : rules.minQty;
            if (!positive(sellQuantity) || sellQuantity < minQty) return "Emergency sell could not be attempted: sell quantity is below Binance minimum.";
            String params = "symbol=" + enc(symbol)
                    + "&side=SELL&type=MARKET"
                    + "&quantity=" + enc(qtyForOrder(sellQuantity))
                    + "&newOrderRespType=RESULT"
                    + "&newClientOrderId=" + enc(clientId("nanu-failsafe", symbol))
                    + "&timestamp=" + serverTime(base)
                    + "&recvWindow=5000";
            SignedResponse response = signedPost(base, "/api/v3/order", params, store.apiKey, store.apiSecret);
            return response.code >= 200 && response.code < 300
                    ? "Emergency market sell submitted. Verify Binance order history immediately."
                    : "CRITICAL: OCO and emergency sell both failed (HTTP " + response.code + "): " + readableError(response.body) + ". Inspect Binance immediately.";
        } catch (Exception e) {
            return "CRITICAL: OCO and emergency sell both failed (" + e.getClass().getSimpleName() + "). Inspect Binance immediately.";
        }
    }

    private static double netBaseQuantity(JSONObject buy, String symbol) {
        double executedQty = parseDouble(buy.optString("executedQty", "0"));
        if (!positive(executedQty)) return 0d;
        double baseCommission = 0d;
        String base = baseAsset(symbol);
        JSONArray fills = buy.optJSONArray("fills");
        if (fills != null) {
            for (int i = 0; i < fills.length(); i++) {
                JSONObject fill = fills.optJSONObject(i);
                if (fill != null && base.equalsIgnoreCase(fill.optString("commissionAsset", ""))) {
                    double commission = parseDouble(fill.optString("commission", "0"));
                    if (positive(commission)) baseCommission += commission;
                }
            }
        } else {
            // A recovery lookup has no fills. Keep a conservative amount so we never sell more than the buy received.
            baseCommission = executedQty * 0.002d;
        }
        return Math.max(0d, executedQty - Math.max(0d, baseCommission));
    }

    private static double lastPrice(String base, String symbol) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/ticker/price?symbol=" + enc(symbol)).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        int code = c.getResponseCode();
        String body = read(c);
        if (code < 200 || code >= 300) throw new BinanceHttpException(code, body);
        double price = parseDouble(new JSONObject(body).optString("price", "0"));
        if (!positive(price)) throw new IllegalStateException("Binance returned no current price for " + symbol + ".");
        return price;
    }

    private static SignedResponse signedPost(String base, String path, String params, String apiKey, String secret) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(base + path + "?" + params + "&signature=" + hmac(params, secret)).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("X-MBX-APIKEY", apiKey);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(0);
        try (OutputStream os = c.getOutputStream()) { os.write(new byte[0]); }
        return new SignedResponse(c.getResponseCode(), read(c));
    }

    private static SignedResponse signedGet(String base, String path, String params, String apiKey, String secret) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(base + path + "?" + params + "&signature=" + hmac(params, secret)).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("X-MBX-APIKEY", apiKey);
        return new SignedResponse(c.getResponseCode(), read(c));
    }

    private static SignedResponse signedDelete(String base, String path, String params, String apiKey, String secret) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(base + path + "?" + params + "&signature=" + hmac(params, secret)).openConnection();
        c.setRequestMethod("DELETE");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("X-MBX-APIKEY", apiKey);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(0);
        try (OutputStream os = c.getOutputStream()) { os.write(new byte[0]); }
        return new SignedResponse(c.getResponseCode(), read(c));
    }

    private static class SignedResponse {
        final int code;
        final String body;
        SignedResponse(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
    }

    private static String clientId(String prefix, String symbol) {
        String raw = prefix + "-" + symbol + "-" + Long.toString(System.currentTimeMillis(), 36);
        return raw.length() <= 36 ? raw : raw.substring(0, 36);
    }

    public static boolean isSupportedPair(String symbol) {
        if (symbol == null) return false;
        for (String pair : SUPPORTED_PAIRS) if (pair.equalsIgnoreCase(symbol.trim())) return true;
        return false;
    }

    private static String baseAsset(String symbol) {
        return symbol != null && symbol.endsWith("USDT") ? symbol.substring(0, symbol.length() - 4) : symbol;
    }

    private static boolean positive(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0d;
    }

    private static double priceForAsset(String asset, Map<String, Double> prices) {
        if (asset == null || asset.isEmpty()) return Double.NaN;
        String a = asset.toUpperCase(Locale.US);
        if (isStableUsd(a)) return 1.0;
        Double direct = prices.get(a + "USDT");
        if (direct != null && direct > 0) return direct;
        Double busd = prices.get(a + "FDUSD");
        if (busd != null && busd > 0) return busd;
        Double btc = prices.get(a + "BTC");
        Double btcUsdt = prices.get("BTCUSDT");
        if (btc != null && btc > 0 && btcUsdt != null && btcUsdt > 0) return btc * btcUsdt;
        Double eth = prices.get(a + "ETH");
        Double ethUsdt = prices.get("ETHUSDT");
        if (eth != null && eth > 0 && ethUsdt != null && ethUsdt > 0) return eth * ethUsdt;
        return Double.NaN;
    }

    private static boolean isStableUsd(String asset) {
        return "USDT".equals(asset) || "USDC".equals(asset) || "FDUSD".equals(asset) || "TUSD".equals(asset) || "BUSD".equals(asset) || "DAI".equals(asset) || "USDP".equals(asset) || "USD1".equals(asset);
    }

    private static String topAssets(List<AssetValue> values) {
        if (values.isEmpty()) return "No non-zero assets";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (AssetValue av : values) {
            if (shown >= 5) break;
            if (sb.length() > 0) sb.append(" • ");
            sb.append(av.asset).append(' ').append(qtyForReport(av.free + av.locked));
            if (av.priced) sb.append(" ≈ ").append(fmt2(av.valueUsdt)).append(" USDT");
            else sb.append(" (unpriced)");
            shown++;
        }
        return sb.toString();
    }

    private static double findFree(List<AssetValue> values, String asset) {
        for (AssetValue av : values) if (asset.equals(av.asset)) return av.free;
        return Double.NaN;
    }

    private static String modeAdvice(String mode) {
        if ("testnet".equals(mode)) return "Use Spot Testnet API key only.";
        if ("live".equals(mode)) return "Use live Binance key only with trusted IP and withdrawals OFF.";
        return "Paper mode does not need API key.";
    }

    private static String diagnosisFor(int code, String body, String mode) {
        String lower = body == null ? "" : body.toLowerCase(Locale.US);
        StringBuilder d = new StringBuilder();
        d.append("Diagnosis: ");
        if (code == 401 || lower.contains("-2015") || lower.contains("invalid api-key")) {
            d.append("API key rejected. ");
            if ("testnet".equals(mode)) d.append("TESTNET needs a Spot Testnet key, not your normal live Binance key.\n");
            else d.append("Check live API key, secret, permissions and trusted IP.\n");
        } else if (code == 403) {
            d.append("Forbidden. Check trusted IP, account restriction, region, or API permissions.\n");
        } else if (code == 400 && lower.contains("timestamp")) {
            d.append("Timestamp/clock problem. Nanu uses Binance server time, retry and check connection.\n");
        } else {
            d.append("Unexpected response. Check internet, Binance status and API key mode.\n");
        }
        d.append(modeAdvice(mode));
        return d.toString();
    }

    public static String explainBinanceCode(int code, String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.US);
        if (code == 200) return "HTTP 200: OK.";
        if (code == 400) return "HTTP 400: Order parameters rejected. Check symbol, minimum notional, quantity step size, quote amount, and timestamp.";
        if (code == 401 || lower.contains("signature") || lower.contains("api-key")) return "HTTP 401/signature: API key, secret, trusted IP, or signature/timestamp issue.";
        if (code == 403) return "HTTP 403: Permission, trusted IP, account, or regional restriction blocked the request.";
        if (code == 418) return "HTTP 418: Binance IP auto-ban/rate-limit protection. Nanu locked live trading. Wait and check Binance before retry.";
        if (code == 429) return "HTTP 429: Too many requests/order attempts. Nanu locked live trading. Wait before retry.";
        return "Unexpected Binance response. Check API Doctor, symbol filters, internet, account permission, and Binance status.";
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }

    private static String readableError(String body) {
        if (body == null || body.trim().isEmpty()) return "No error body returned by Binance.";
        return body.length() > 600 ? body.substring(0, 600) + "..." : body;
    }

    private static String baseUrl(String mode) {
        if ("testnet".equals(mode)) return "https://testnet.binance.vision";
        return "https://api.binance.com";
    }

    private static long serverTime(String base) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(base + "/api/v3/time").openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(10000);
        String body = read(c);
        JSONObject json = new JSONObject(body);
        return json.optLong("serverTime", System.currentTimeMillis());
    }

    private static String publicIpSafe() {
        String[] helpers = {"https://api.ipify.org", "https://ifconfig.me/ip"};
        for (String helper : helpers) {
            try {
                HttpURLConnection c = (HttpURLConnection)new URL(helper).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                String ip = read(c).trim();
                if (AutoTradingPolicy.isStaticIp(ip)) return ip;
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream stream = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value == null ? "NaN" : value); }
        catch (Exception ignored) { return Double.NaN; }
    }

    private static double roundDownToStep(double value, double step) {
        if (step <= 0 || Double.isNaN(step) || Double.isInfinite(step)) return value;
        return Math.floor(value / step) * step;
    }

    private static String fmt2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "n/a";
        return String.format(Locale.US, "%.2f", value);
    }

    private static String qtyForOrder(double value) {
        return String.format(Locale.US, "%.8f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String qtyForReport(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "n/a";
        if (value >= 1000) return String.format(Locale.US, "%.2f", value);
        if (value >= 1) return String.format(Locale.US, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return String.format(Locale.US, "%.8f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
