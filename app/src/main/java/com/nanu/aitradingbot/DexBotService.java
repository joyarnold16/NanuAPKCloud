package com.nanu.aitradingbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Foreground scanner: stopping the service stops new scans and is reflected in the app state. */
public final class DexBotService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            DexAppStore store = DexAppStore.get(DexBotService.this);
            store.engine.tick(false);
            handler.postDelayed(this, 5_000L);
        }
    };

    @Override public void onCreate() { super.onCreate(); createChannel(); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(808, notification());
        handler.removeCallbacks(loop);
        handler.post(loop);
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(loop);
        DexAppStore store = DexAppStore.get(this);
        if (store.scannerRunning) {
            store.scannerRunning = false;
            store.lastStatus = "HALTED: Android stopped the scanner service. No new actions were sent.";
            store.lastCritical = store.lastStatus;
            store.save();
        }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel("nanu_dex", "Nanu DEX scanner", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows whether the local DEX scanner is running.");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    private Notification notification() {
        Intent intent = new Intent(this, DexActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        DexAppStore store = DexAppStore.get(this);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "nanu_dex") : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Nanu AI Trading Bot")
                .setContentText(store.state() + " - local DEX paper scanner")
                .setContentIntent(pending)
                .build();
    }
}
