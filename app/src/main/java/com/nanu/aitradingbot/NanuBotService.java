package com.nanu.aitradingbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class NanuBotService extends Service {
    Handler h = new Handler(Looper.getMainLooper());
    long lastHeartbeatSaveMs = 0L;
    Runnable loop = new Runnable() {
        @Override public void run() {
            AppStore store = AppStore.get(NanuBotService.this);
            store.recordDeviceHeartbeat();
            if (System.currentTimeMillis() - lastHeartbeatSaveMs >= 30_000L) {
                lastHeartbeatSaveMs = System.currentTimeMillis();
                store.save();
            }
            store.engine.tick(false);
            h.postDelayed(this, 2500);
        }
    };
    @Override public void onCreate() { super.onCreate(); createChannel(); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        AppStore store = AppStore.get(this);
        store.recordDeviceHeartbeat();
        store.save();
        startForeground(77, notification());
        h.removeCallbacks(loop);
        h.post(loop);
        return START_STICKY;
    }
    @Override public void onDestroy() {
        h.removeCallbacks(loop);
        AppStore store = AppStore.get(this);
        if (store.engine.running || store.autoRunning) {
            store.recordUnexpectedDeviceStop("Foreground device service stopped unexpectedly. No new automatic entries will be sent; inspect Binance before restarting.");
            store.triggerAlert("Nanu Device Service Stopped", store.deviceLastStopReason, true, "api");
        }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void createChannel() { if (Build.VERSION.SDK_INT >= 26) { NotificationChannel ch = new NotificationChannel("nanu", "Nanu Bot", NotificationManager.IMPORTANCE_LOW); ch.setDescription("Nanu AI Trading Bot engine"); ch.setLightColor(Color.CYAN); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch); } }
    private Notification notification() {
        AppStore store = AppStore.get(this);
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "nanu") : new Notification.Builder(this);
        return b.setContentTitle("Nanu AI Trading Bot")
                .setContentText(store.autoRunning ? "Automatic Spot executor running with Binance OCO protection." : "Market scanner running. No automatic live entries are armed.")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .build();
    }
}
