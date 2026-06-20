package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
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
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static volatile boolean scalperRequestInFlight = false;

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
        double marketMinQty = 0.0;
        double marketMaxQty = Double.NaN;
        double marketStepSize = 0.0;
        String report = "Symbol rules not loaded.";
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

    public static void placeMarketOrder(AppStore store, String symbol, String side, double amount, Callback cb) {
        exec.execute(() -> {
            StringBuilder out = new StringBuilder();
            try {
                String safeSymbol = (symbol == null || symbol.trim().isEmpty() ? "BTCUSDT" : symbol.trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", ""));
                String safeSide = "SELL".equalsIgnoreCase(side) ? "SELL" : "BUY";
                boolean testOnly = store.liveOrderTestMode;

                out.append("Nanu v6.2 Professional Manual Confirmed Micro Order\n\n");
                out.append("Mode: ").append(store.mode.toUpperCase(Locale.US)).append('\n');
                out.append("Symbol: ").append(safeSymbol).append('\n');
                out.append("Side: ").append(safeSide).append('\n');
                out.append("Execution channel: ").append(testOnly ? "BINANCE /order/test (NO REAL FILL)" : "BINANCE REAL MARKET ORDER").append("\n\n");

                if (!"live".equals(store.mode)) { cb.done(out + "BLOCKED: select LIVE mode first."); return; }
                if (!store.liveUnlocked) { cb.done(out + "BLOCKED: LIVE gate is locked."); return; }
                if (!store.apiTradingOkForCurrentMode()) { cb.done(out + "BLOCKED: API Doctor/spot trading permission not passed for LIVE mode."); return; }
                if (!store.telegramDoctorOk) { cb.done(out + "BLOCKED: Telegram Doctor must pass before live order."); return; }
                if (!store.profitGuardEnabled) { cb.done(out + "BLOCKED: Profit Guard must be ON before live order."); return; }
                if (!store.panicButtonTested) { cb.done(out + "BLOCKED: Panic button must be tested before live order."); return; }
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
                if (!testOnly && !store.liveRealOrderArmed) { cb.done(out + "BLOCKED: real micro order is not armed. Keep Test Order ON or type ARM REAL MICRO in app."); return; }
                if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) { cb.done(out + "BLOCKED: API key/secret missing."); return; }

                String base = baseUrl(store.mode);
                SymbolRules rules = fetchSymbolRules(base, safeSymbol);
                out.append(rules.report).append("\n");
                if (!rules.ok) { cb.done(out + "\nBLOCKED: symbol is not tradable right now."); return; }

                double safeAmount = Math.max(0.00000001, amount);
                if ("BUY".equals(safeSide)) {
                    if (store.microLiveOrderUsdt > 25.0) {
                        cb.done(out + "BLOCKED: micro live order amount is above 25 USDT safety cap.");
                        return;
                    }
                    safeAmount = Math.max(0.01, safeAmount);
                    if (safeAmount < rules.minNotional) {
                        cb.done(out + "BLOCKED: quote amount is below Binance min notional " + fmt2(rules.minNotional) + " USDT.");
                        return;
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

                long ts = serverTime(base);
                String params;
                if ("BUY".equals(safeSide)) {
                    params = "symbol=" + enc(safeSymbol) + "&side=BUY&type=MARKET&quoteOrderQty=" + enc(String.format(Locale.US, "%.2f", safeAmount)) + "&newOrderRespType=RESULT&timestamp=" + ts + "&recvWindow=5000";
                    out.append("Quote amount: ").append(String.format(Locale.US, "%.2f USDT", safeAmount)).append("\n");
                } else {
                    params = "symbol=" + enc(safeSymbol) + "&side=SELL&type=MARKET&quantity=" + enc(qtyForOrder(safeAmount)) + "&newOrderRespType=RESULT&timestamp=" + ts + "&recvWindow=5000";
                    out.append("Sell quantity: ").append(qtyForOrder(safeAmount)).append("\n");
                }
                String sig = hmac(params, store.apiSecret);
                String endpoint = base + (testOnly ? "/api/v3/order/test" : "/api/v3/order") + "?" + params + "&signature=" + sig;

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
                        store.engine.addJournal("Binance test order PASS: " + safeSide + " " + safeSymbol);
                    } else {
                        out.append("RESULT: REAL MARKET ORDER SENT\nReview Binance order history immediately.\n");
                        store.liveTradesToday++;
                        store.liveRealOrderArmed = false;
                        store.engine.addJournal("REAL MICRO ORDER SENT: " + safeSide + " " + safeSymbol);
                        store.triggerAlert("Nanu Real Micro Order", safeSide + " " + safeSymbol + " submitted. Check Binance order history now.", true, "live");
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
                store.lastLiveOrderReport = "Order exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                store.save();
                cb.done(out + "\nORDER FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    public static void getPublicIp(AppStore store, Callback cb) {
        exec.execute(() -> {
            try {
                String ip = publicIpSafe();
                if (!ip.isEmpty()) { store.lastPublicIp = ip; store.save(); }
                cb.done("Trusted IP Helper\n\nYour current public IP appears to be:\n" + (ip.isEmpty() ? "Unknown" : ip) + "\n\nAdd this IP to Binance API trusted IP list if your account requires it. Mobile networks can change IP often.");
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
        if ("demo".equals(mode)) return "Use Demo Trading API key only.";
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
            else if ("demo".equals(mode)) d.append("DEMO needs a Demo Trading key, not your live key.\n");
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
        if ("demo".equals(mode)) return "https://demo-api.binance.com";
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
        try {
            HttpURLConnection c = (HttpURLConnection)new URL("https://api.ipify.org").openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            String ip = read(c).trim();
            if (ip.matches("[0-9a-fA-F:.]+")) return ip;
        } catch (Exception ignored) {}
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
