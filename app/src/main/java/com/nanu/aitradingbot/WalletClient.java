package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Reads native-coin balances for the Nanu wallet using public RPC endpoints.
 * Mirrors DexMarketClient: raw HttpURLConnection, background thread, callback.
 * NO wallet secret is ever used here. This class can read, never move funds.
 */
public final class WalletClient {

    public interface Callback {
        void done(Balances balances, String status);
    }

    public static final class Balances {
        public BigDecimal bnb = BigDecimal.ZERO;
        public BigDecimal eth = BigDecimal.ZERO;
        public BigDecimal sol = BigDecimal.ZERO;
        public boolean bnbOk, ethOk, solOk;
    }

    private static final String BNB_RPC = "https://bsc-dataseed.binance.org/";
    private static final String ETH_RPC = "https://eth.llamarpc.com";
    private static final String SOL_RPC = "https://api.mainnet-beta.solana.com";

    private static final BigDecimal WEI = new BigDecimal("1000000000000000000");
    private static final BigDecimal LAMPORTS = new BigDecimal("1000000000");

    private WalletClient() {}

    public static void fetch(final String evmAddress, final String solAddress, final Callback callback) {
        new Thread(() -> {
            Balances b = new Balances();
            StringBuilder problems = new StringBuilder();
            if (evmAddress != null && !evmAddress.isEmpty()) {
                try { b.bnb = evmBalance(BNB_RPC, evmAddress); b.bnbOk = true; }
                catch (Exception e) { problems.append("BNB read failed. "); }
                try { b.eth = evmBalance(ETH_RPC, evmAddress); b.ethOk = true; }
                catch (Exception e) { problems.append("ETH read failed. "); }
            }
            if (solAddress != null && !solAddress.isEmpty()) {
                try { b.sol = solBalance(SOL_RPC, solAddress); b.solOk = true; }
                catch (Exception e) { problems.append("SOL read failed. "); }
            }
            String status = problems.length() == 0 ? "Balances updated."
                    : problems.toString().trim() + " (network or RPC issue)";
            callback.done(b, status);
        }, "nanu-wallet-balances").start();
    }

    private static BigDecimal evmBalance(String rpc, String address) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0"); req.put("id", 1);
        req.put("method", "eth_getBalance");
        req.put("params", new JSONArray().put(address).put("latest"));
        JSONObject resp = new JSONObject(post(rpc, req.toString()));
        String hex = resp.optString("result", "0x0");
        BigInteger wei = new BigInteger(hex.substring(2), 16);
        return new BigDecimal(wei).divide(WEI);
    }

    private static BigDecimal solBalance(String rpc, String address) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0"); req.put("id", 1);
        req.put("method", "getBalance");
        req.put("params", new JSONArray().put(address));
        JSONObject resp = new JSONObject(post(rpc, req.toString()));
        JSONObject result = resp.optJSONObject("result");
        long lamports = result == null ? 0L : result.optLong("value", 0L);
        return new BigDecimal(lamports).divide(LAMPORTS);
    }

    private static String post(String rawUrl, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NanuWallet/8.0");
        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        } finally { connection.disconnect(); }
        return out.toString();
    }
}
