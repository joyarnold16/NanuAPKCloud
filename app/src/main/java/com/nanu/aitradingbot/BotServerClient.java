package com.nanu.aitradingbot;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS control client for the VPS executor. It never sends Binance credentials from the phone. */
public final class BotServerClient {
    public interface Callback { void complete(JSONObject response, String error); }

    private BotServerClient() {}

    public static String normalizeBaseUrl(String raw) throws Exception {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null || url.getHost().isEmpty()) {
            throw new Exception("Use a complete HTTPS executor URL, for example https://bot.example.com");
        }
        if (url.getQuery() != null || url.getRef() != null || !"".equals(url.getPath())) {
            throw new Exception("Use the executor base URL only, without a path, query, or fragment.");
        }
        return value;
    }

    public static void request(AppStore store, String method, String path, JSONObject body, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String base = normalizeBaseUrl(store.executorUrl);
                if (store.executorControlToken == null || store.executorControlToken.length() < 32) {
                    throw new Exception("Add the executor control token first.");
                }
                connection = (HttpURLConnection) new URL(base + path).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod(method);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + store.executorControlToken);
                if (body != null) {
                    byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setFixedLengthStreamingMode(data.length);
                    try (OutputStream output = connection.getOutputStream()) { output.write(data); }
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String text = read(stream);
                JSONObject result = text.isEmpty() ? new JSONObject() : new JSONObject(text);
                if (code < 200 || code >= 300) throw new Exception(result.optString("error", "Executor returned HTTP " + code));
                callback.complete(result, "");
            } catch (Exception error) {
                callback.complete(null, error.getMessage() == null ? "Executor request failed." : error.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "nanu-executor-control").start();
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }
}
