package com.nanu.aitradingbot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelegramClient {
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();

    public interface Callback { void done(String result); }

    public static void sendSilent(AppStore store, String title, String message) {
        if (store == null || store.telegramToken == null || store.telegramChatId == null) return;
        if (store.telegramToken.trim().isEmpty() || store.telegramChatId.trim().isEmpty()) return;
        exec.execute(() -> {
            try { sendRaw(store.telegramToken.trim(), store.telegramChatId.trim(), title + "\n" + message); }
            catch (Exception ignored) {}
        });
    }

    public static void test(AppStore store, Callback cb) {
        exec.execute(() -> {
            try {
                if (store.telegramToken == null || store.telegramToken.trim().isEmpty()) {
                    cb.done("Telegram Doctor\n\nBot token is empty. Paste token from BotFather first.");
                    return;
                }
                if (store.telegramChatId == null || store.telegramChatId.trim().isEmpty()) {
                    cb.done("Telegram Doctor\n\nChat ID is empty. Open your bot in Telegram, press Start, send hi, then paste your chat ID.");
                    return;
                }
                String res = sendRaw(store.telegramToken.trim(), store.telegramChatId.trim(),
                        "Nanu Telegram Doctor ✅\nTest message received. Alerts can be sent from Nanu AI Trading Bot.");
                cb.done("Telegram Doctor\n\n" + res + "\n\nIf you received the message in Telegram, alerts are working. If not, check bot token, chat ID, and press Start inside your bot chat.");
            } catch (Exception e) {
                cb.done("Telegram Doctor Failed ❌\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n\nCommon causes:\n• Wrong bot token = 401 Unauthorized\n• Wrong chat ID = 400 chat not found\n• Bot not started/blocked = 403 Forbidden\n• No internet connection");
            }
        });
    }

    private static String sendRaw(String token, String chatId, String text) throws Exception {
        String endpoint = "https://api.telegram.org/bot" + token + "/sendMessage";
        HttpURLConnection c = (HttpURLConnection)new URL(endpoint).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        String data = "chat_id=" + enc(chatId) + "&text=" + enc(text) + "&disable_web_page_preview=true";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        int code = c.getResponseCode();
        String body = read(c);
        if (code >= 200 && code < 300 && body.contains("\"ok\":true")) {
            return "HTTP " + code + " ✅\nTelegram sendMessage OK.";
        }
        return "HTTP " + code + " ❌\n" + readable(body);
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private static String read(HttpURLConnection c) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static String readable(String body) {
        if (body == null || body.trim().isEmpty()) return "No response body.";
        return body.length() > 700 ? body.substring(0, 700) + "..." : body;
    }
}
