package com.nanu.aitradingbot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BinanceClient {
    public interface Callback { void done(String result); }
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();

    public static void testApi(AppStore store, Callback cb) {
        exec.execute(() -> {
            StringBuilder out = new StringBuilder();
            try {
                String doctorMode = store.mode;
                String base = baseUrl(doctorMode);
                String fullBase = base + "/api";
                store.lastApiMode = doctorMode;
                out.append("Nanu API Doctor v6.0\n\n");
                out.append("Mode: ").append(doctorMode.toUpperCase(Locale.US)).append('\n');
                out.append("Endpoint root: ").append(base).append('\n');
                out.append("REST base path: ").append(fullBase).append('\n');
                out.append("Key rule: ").append(keyRule(doctorMode)).append("\n\n");

                String ip = publicIpSafe();
                if (!ip.isEmpty()) {
                    store.lastPublicIp = ip;
                    out.append("Your current public IP: ").append(ip).append('\n');
                    out.append("Trusted IP tip: paste this IP in Binance only if Nanu is running from this phone/network.\n\n");
                }

                long serverTime = serverTime(base);
                out.append("Internet / Binance time: OK ✅\n");
                out.append("Server time: ").append(serverTime).append('\n');

                if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) {
                    store.lastApiMode = doctorMode;
                    store.lastApiPrivateOk = false;
                    store.lastApiCanTrade = false;
                    store.lastApiAccountCanWithdraw = false;
                    store.lastApiHttpCode = 0;
                    store.lastApiDiagnosis = "Public connection OK. Add API key + secret for private account test.";
                    store.save();
                    out.append("\nPrivate API test skipped. Add API key and secret first.\n");
                    out.append("Public market connection is alive.\n");
                    cb.done(out.toString());
                    return;
                }

                String query = "timestamp=" + serverTime + "&recvWindow=5000";
                String sig = hmac(query, store.apiSecret);
                out.append("Signature generation: OK ✅\n");

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
                    boolean canTrade = body.contains("\"canTrade\":true");
                    boolean canWithdraw = body.contains("\"canWithdraw\":true");
                    boolean canDeposit = body.contains("\"canDeposit\":true");
                    store.lastUsdtFree = extractBalance(body, "USDT", "free");
                    store.lastUsdtLocked = extractBalance(body, "USDT", "locked");
                    store.lastBtcFree = extractBalance(body, "BTC", "free");
                    if (!Double.isNaN(store.lastUsdtFree)) {
                        store.lastBalanceSnapshot = String.format(Locale.US, "USDT free %.4f • locked %.4f • BTC free %.8f", store.lastUsdtFree, store.lastUsdtLocked, store.lastBtcFree);
                    }
                    store.lastApiMode = doctorMode;
                    store.lastApiPrivateOk = true;
                    store.lastApiCanTrade = canTrade;
                    store.lastApiAccountCanWithdraw = canWithdraw;
                    store.lastApiDiagnosis = canTrade ? "Private API OK and trading permission appears enabled." : "Private API OK but trading is not enabled. Scanner/read-only allowed; auto trading blocked.";
                    store.save();
                    out.append("Private account access: OK ✅\n");
                    out.append("Read permission: OK ✅\n");
                    if (!Double.isNaN(store.lastUsdtFree)) out.append("USDT free: ").append(String.format(Locale.US, "%.4f", store.lastUsdtFree)).append(" • locked: ").append(String.format(Locale.US, "%.4f", store.lastUsdtLocked)).append('\n');
                    out.append("Spot trading permission: ").append(canTrade ? "OK ✅" : "OFF / READ ONLY ⚠️").append('\n');
                    out.append("Account deposit ability: ").append(canDeposit ? "ON" : "OFF").append('\n');
                    out.append("Account withdraw ability: ").append(canWithdraw ? "ON ⚠️" : "OFF ✅").append("\n");
                    out.append("Important: this is account-level ability from Binance /account, not proof that this API key has withdrawal permission. Nanu never needs withdrawal permission. Manually confirm API-key withdrawals are OFF in Binance before live unlock.\n\n");
                    if (!canTrade) {
                        out.append("Diagnosis: Your key can read account data, but Nanu will block live auto orders because trading permission is OFF.\n");
                        out.append("Fix: In Binance API restrictions, add trusted IP first, then enable Spot & Margin & Stock Trading. Keep Withdrawals OFF.\n");
                    } else {
                        out.append("Diagnosis: API is ready for trading permission checks. Keep Live locked until paper/testnet and risk checks pass.\n");
                        if (canWithdraw) out.append("Warning: account-level withdraw ability is ON. This may be normal for the account, but API-key withdrawal permission must be OFF for Nanu. Confirm it manually in Binance.\n");
                    }
                } else {
                    store.lastApiMode = doctorMode;
                    store.lastApiPrivateOk = false;
                    store.lastApiCanTrade = false;
                    store.lastApiAccountCanWithdraw = false;
                    store.save();
                    String diagnosis = diagnosisFor(code, body, doctorMode);
                    store.lastApiDiagnosis = diagnosis;
                    store.save();
                    out.append("Private API failed ❌\n");
                    out.append(readableError(body)).append("\n\n");
                    out.append("Diagnosis:\n").append(diagnosis).append('\n');
                }
            } catch (Exception e) {
                store.lastApiMode = store.mode;
                store.lastApiPrivateOk = false;
                store.lastApiCanTrade = false;
                store.lastApiAccountCanWithdraw = false;
                store.lastApiDiagnosis = "API Doctor failed: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                store.save();
                out.append("API Doctor Failed ❌\n").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
            }
            cb.done(out.toString());
        });
    }


    public static void placeMarketOrder(AppStore store, String symbol, String side, double amount, Callback cb) {
        exec.execute(() -> {
            StringBuilder out = new StringBuilder();
            try {
                String safeSymbol = (symbol == null || symbol.trim().isEmpty() ? "BTCUSDT" : symbol.trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", ""));
                String safeSide = "SELL".equalsIgnoreCase(side) ? "SELL" : "BUY";
                boolean testOnly = store.liveOrderTestMode;

                out.append("Nanu v6.0 Manual Confirmed Micro Order\n\n");
                out.append("Mode: ").append(store.mode.toUpperCase(Locale.US)).append('\n');
                out.append("Symbol: ").append(safeSymbol).append('\n');
                out.append("Side: ").append(safeSide).append('\n');
                out.append("Execution channel: ").append(testOnly ? "BINANCE /order/test (NO REAL FILL)" : "BINANCE REAL MARKET ORDER").append("\n\n");

                if (!"live".equals(store.mode)) { cb.done(out + "BLOCKED: select LIVE mode first."); return; }
                if (!store.liveUnlocked) { cb.done(out + "BLOCKED: LIVE gate is locked."); return; }
                if (!store.apiTradingOkForCurrentMode()) { cb.done(out + "BLOCKED: API Doctor/spot trading permission not passed for LIVE mode."); return; }
                if (!store.withdrawalPermissionConfirmedOff) { cb.done(out + "BLOCKED: API-key withdrawals must be manually confirmed OFF."); return; }
                if (!store.complianceGuardEnabled) { cb.done(out + "BLOCKED: Compliance Guard is OFF."); return; }
                if (store.binanceRateLimitLock) { cb.done(out + "BLOCKED: Binance rate-limit lock is active. Reset only after waiting and checking Binance."); return; }
                if (store.engine.panic) { cb.done(out + "BLOCKED: Panic state is active."); return; }
                if (store.liveTradesToday >= Math.max(1, store.maxLiveTradesPerDay)) { cb.done(out + "BLOCKED: max live trades/day reached."); return; }
                if (System.currentTimeMillis() < store.orderCooldownUntilMs) { cb.done(out + "BLOCKED: order cooldown is still active."); return; }
                if (!testOnly && !store.liveRealOrderArmed) { cb.done(out + "BLOCKED: real micro order is not armed. Keep Test Order ON or type ARM REAL MICRO in app."); return; }
                if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) { cb.done(out + "BLOCKED: API key/secret missing."); return; }

                double safeAmount = Math.max(0.00000001, amount);
                if ("BUY".equals(safeSide)) {
                    safeAmount = Math.min(Math.max(safeAmount, Math.max(5.0, store.minOrderNotionalUsdt)), Math.max(5.0, store.microLiveOrderUsdt));
                }

                String base = baseUrl(store.mode);
                long ts = serverTime(base);
                String params;
                if ("BUY".equals(safeSide)) {
                    params = "symbol=" + enc(safeSymbol) + "&side=BUY&type=MARKET&quoteOrderQty=" + enc(String.format(Locale.US, "%.2f", safeAmount)) + "&newOrderRespType=RESULT&timestamp=" + ts + "&recvWindow=5000";
                    out.append("Quote amount: ").append(String.format(Locale.US, "%.2f USDT", safeAmount)).append("\n");
                } else {
                    params = "symbol=" + enc(safeSymbol) + "&side=SELL&type=MARKET&quantity=" + enc(String.format(Locale.US, "%.8f", safeAmount)) + "&newOrderRespType=RESULT&timestamp=" + ts + "&recvWindow=5000";
                    out.append("Sell quantity: ").append(String.format(Locale.US, "%.8f", safeAmount)).append("\n");
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
                        out.append("RESULT: TEST ORDER PASSED ✅\nNo real order was created. Binance accepted the order format/signature/symbol rules.\n");
                        store.engine.addJournal("Binance test order PASS: " + safeSide + " " + safeSymbol);
                    } else {
                        out.append("RESULT: REAL MARKET ORDER SENT ✅\nReview Binance order history immediately.\n");
                        store.liveTradesToday++;
                        store.liveRealOrderArmed = false;
                        store.engine.addJournal("REAL MICRO ORDER SENT: " + safeSide + " " + safeSymbol);
                        store.triggerAlert("Nanu Real Micro Order", safeSide + " " + safeSymbol + " submitted. Check Binance order history now.", true, "live");
                    }
                    store.orderCooldownUntilMs = System.currentTimeMillis() + Math.max(10, store.orderCooldownSeconds) * 1000L;
                    store.lastBinanceErrorDoctor = "Last Binance order/test response was OK.";
                } else {
                    out.append("RESULT: BLOCKED / FAILED ❌\n").append(readableError(body)).append("\n\n");
                    out.append(explainBinanceCode(code, body));
                    store.lockAfterExchangeDanger(code, explainBinanceCode(code, body));
                    store.triggerAlert("Nanu Binance Order Error", "HTTP " + code + " during " + safeSide + " " + safeSymbol + ". Check Error Doctor.", true, "api");
                }
                if (body != null && !body.trim().isEmpty()) out.append("\nRaw response:\n").append(readableError(body)).append('\n');
                store.lastLiveOrderReport = out.toString();
                store.save();
                cb.done(store.lastLiveOrderReport);
            } catch (Exception e) {
                store.lastLiveOrderReport = "Nanu live order failed: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                store.lastBinanceErrorDoctor = store.lastLiveOrderReport;
                store.save();
                cb.done(store.lastLiveOrderReport);
            }
        });
    }

    public static void getPublicIp(AppStore store, Callback cb) {
        exec.execute(() -> {
            String ip = publicIpSafe();
            if (!ip.isEmpty()) {
                store.lastPublicIp = ip;
                store.save();
                cb.done("Your current public IP is:\n\n" + ip + "\n\nPaste this into Binance trusted IP field only if Nanu will send API requests from this same phone/network. If your mobile/Airtel IP changes later, Binance will reject the key until you update the trusted IP.");
            } else cb.done("Could not read public IP. Check internet connection and try again.");
        });
    }

    private static String keyRule(String mode) {
        if ("paper".equals(mode)) return "No API key required.";
        if ("demo".equals(mode)) return "Use Demo Trading API key only.";
        if ("testnet".equals(mode)) return "Use Spot Testnet API key only.";
        return "Use real Binance API key with trusted IP and spot trading permission.";
    }

    private static String diagnosisFor(int code, String body, String mode) {
        String lower = body == null ? "" : body.toLowerCase(Locale.US);
        StringBuilder d = new StringBuilder();
        if (code == 401 || lower.contains("-2015") || lower.contains("invalid api-key")) {
            d.append("• Binance rejected private access: invalid key, wrong mode, IP not trusted, or permission missing.\n");
            if ("testnet".equals(mode)) d.append("• TESTNET needs a Spot Testnet key, not your normal live Binance key.\n");
            else if ("demo".equals(mode)) d.append("• DEMO needs a Demo Trading key, not your live key.\n");
            else if ("live".equals(mode)) d.append("• LIVE needs a real Binance key. If trusted IP is enabled, the IP must match the device/server sending requests.\n");
            d.append("• If you selected trusted IPs, paste the public IP shown by Nanu API Doctor into Binance.\n");
            d.append("• For live trading, enable Spot & Margin & Stock Trading only. Keep Withdrawals OFF.");
        } else if (code == 403) {
            d.append("• Binance blocked the request. Check region/network restriction, IP whitelist, and account/API permissions.");
        } else if (code == 418 || code == 429) {
            d.append("• Binance rate limit triggered. Wait and test again later.");
        } else {
            d.append("• Unexpected Binance response. Check key mode, permissions, timestamp, network, and Binance account restrictions.");
        }
        return d.toString();
    }

    private static double extractBalance(String body, String asset, String field) {
        try {
            String token = "\"asset\":\"" + asset + "\"";
            int i = body.indexOf(token);
            if (i < 0) return Double.NaN;
            int end = body.indexOf("}", i);
            if (end < 0) end = Math.min(body.length(), i + 220);
            String chunk = body.substring(i, end);
            String f = "\"" + field + "\":\"";
            int j = chunk.indexOf(f);
            if (j < 0) return Double.NaN;
            int start = j + f.length();
            int stop = chunk.indexOf("\"", start);
            if (stop < 0) return Double.NaN;
            return Double.parseDouble(chunk.substring(start, stop));
        } catch (Exception ignored) { return Double.NaN; }
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
        int i = body.indexOf("serverTime");
        if (i < 0) return System.currentTimeMillis();
        String digits = body.substring(i).replaceAll("[^0-9]", "");
        if (digits.length() > 13) digits = digits.substring(0, 13);
        return Long.parseLong(digits);
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
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
