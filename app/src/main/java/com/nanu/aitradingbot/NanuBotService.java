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

public class NanuBotService extends Service {
    private static final String CHANNEL_ID = "nanu_bot_service";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppStore store;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (store != null && store.engine.running) {
                store.engine.tick(false);
                startForeground(7, notification("Nanu running in " + store.mode.toUpperCase() + " mode"));
                handler.postDelayed(this, 3000);
            } else {
                stopSelf();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        store = AppStore.get(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (store == null) store = AppStore.get(this);
        startForeground(7, notification("Nanu AI Trading Bot active"));
        handler.removeCallbacks(loop);
        handler.post(loop);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(loop);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Nanu Bot", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps Nanu paper engine alive while running.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification notification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("Nanu AI Trading Bot")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
