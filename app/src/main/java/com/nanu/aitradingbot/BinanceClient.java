package com.nanu.aitradingbot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
                out.append("Nanu API Doctor v5.6.3\n\n");
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
