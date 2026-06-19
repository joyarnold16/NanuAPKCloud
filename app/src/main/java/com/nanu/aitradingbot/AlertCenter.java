package com.nanu.aitradingbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlertCenter {
    private static final String CHANNEL_ALERTS = "nanu_profit_guard_alerts";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int notifyId = 2200;

    public static void notify(Context context, String title, String message, boolean critical, boolean sound, boolean phoneNotification, boolean longSound) {
        Context c = context.getApplicationContext();
        if (phoneNotification) showNotification(c, title, message, critical);
        if (critical) vibrate(c, longSound);
        if (sound) playSound(c, longSound && critical);
    }

    public static void showNotification(Context c, String title, String message, boolean critical) {
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ALERTS, "Nanu Guard Alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Profit target, duplicate profit guard, panic and API alerts");
            ch.enableLights(true);
            ch.setLightColor(critical ? Color.RED : Color.CYAN);
            ch.enableVibration(true);
            ch.setSound(null, null); // sound is controlled by AlertCenter for long/critical tone
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 100, i, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL_ALERTS) : new Notification.Builder(c);
        b.setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setSmallIcon(critical ? android.R.drawable.ic_dialog_alert : android.R.drawable.ic_menu_compass)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(critical ? Notification.PRIORITY_MAX : Notification.PRIORITY_HIGH);
        try { nm.notify(++notifyId, b.build()); } catch (SecurityException ignored) {}
    }

    public static void playSound(Context c, boolean longSound) {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) return;
            final Ringtone r = RingtoneManager.getRingtone(c, uri);
            if (r == null) return;
            if (Build.VERSION.SDK_INT >= 28) r.setLooping(false);
            r.play();
            MAIN.postDelayed(() -> { try { if (r.isPlaying()) r.stop(); } catch (Exception ignored) {} }, longSound ? 9000 : 2200);
        } catch (Exception ignored) {}
    }

    public static void vibrate(Context c, boolean longVibration) {
        try {
            Vibrator v = (Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            long[] pattern = longVibration ? new long[]{0, 450, 180, 450, 180, 900} : new long[]{0, 250, 100, 250};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1)); else v.vibrate(pattern, -1);
        } catch (Exception ignored) {}
    }
}
