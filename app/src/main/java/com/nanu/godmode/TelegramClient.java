package com.nanu.godmode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TelegramClient {
    private final AppConfig cfg;
    public TelegramClient(AppConfig cfg) { this.cfg = cfg; }

    public void send(String msg) {
        String token = cfg.telegramToken();
        String chat = cfg.telegramChatId();
        if (token == null || token.trim().isEmpty() || chat == null || chat.trim().isEmpty()) return;
        new Thread(() -> {
            try {
                String url = "https://api.telegram.org/bot" + token.trim() + "/sendMessage?chat_id=" + enc(chat.trim()) + "&text=" + enc("Nanu: " + msg);
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
                while (br.readLine() != null) {}
            } catch (Exception ignored) { }
        }).start();
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
}
