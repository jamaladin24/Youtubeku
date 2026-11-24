package com.ytaudio.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * سرویس پیش‌زمینه: اندروید برنامه را در پس‌زمینه نمی‌بندد،
 * و هنگام پخش، پردازنده و وای‌فای با خاموش شدن صفحه نمی‌خوابند.
 */
public class PlaybackService extends Service {

    static final String ACTION_STATE = "com.ytaudio.app.STATE";
    static final String ACTION_TOGGLE = "com.ytaudio.app.TOGGLE";
    static final String ACTION_CLOSE = "com.ytaudio.app.CLOSE";

    static volatile boolean running = false;

    private static final String CHANNEL = "playback";
    private static final int ID = 1;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private String title = "";
    private boolean playing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YtAudio:play");
        wakeLock.setReferenceCounted(false);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "YtAudio:wifi");
            wifiLock.setReferenceCounted(false);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "لێدانی دەنگ", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_TOGGLE.equals(action)) {
            MainActivity.toggleFromNotification();
            return START_NOT_STICKY;
        }
        if (ACTION_CLOSE.equals(action)) {
            running = false;
            MainActivity.pauseFromNotification();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            playing = intent.getBooleanExtra("playing", false);
            String t = intent.getStringExtra("title");
            if (t != null && !t.isEmpty()) title = t;
        }

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID, n);
        }

        // فقط هنگام پخش، دستگاه بیدار نگه داشته می‌شود (برای مصرف کمتر باتری)
        if (playing) {
            if (!wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 60 * 1000L);
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } else {
            releaseLocks();
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;

        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, open, imm);

        PendingIntent piToggle = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(ACTION_TOGGLE), imm);
        PendingIntent piClose = PendingIntent.getService(this, 2,
                new Intent(this, PlaybackService.class).setAction(ACTION_CLOSE), imm);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_note)
                .setContentTitle(playing ? "لێدەدرێت" : "وەستاوە")
                .setContentText(title.isEmpty() ? "تەنها دەنگ" : title)
                .setContentIntent(piOpen)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? "وەستان" : "لێدان", piToggle).build())
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_close, "داخستن", piClose).build());
        if (Build.VERSION.SDK_INT >= 21) {
            b.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1));
        }
        return b.build();
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
