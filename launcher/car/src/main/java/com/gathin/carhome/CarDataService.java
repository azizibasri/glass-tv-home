package com.gathin.carhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps the car-data connection (OBD-II adapter, CAN broadcasts, GPS) running while other apps are in
 * front: the reverse camera, navigation, music. Without it the OBD-II link dropped each time Home went to
 * the background and had to reconnect. Android only lets this run with a notification, so it shows a quiet
 * one. Home reads the same data whenever it is on screen; the trip keeps counting meanwhile.
 */
public final class CarDataService extends Service {
    private static final String CHANNEL = "car_data";
    private static final int NOTE_ID = 9;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CarData data;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (data != null) data.publish();
            main.postDelayed(this, 1_000);
        }
    };

    static void start(Context ctx) {
        Intent i = new Intent(ctx, CarDataService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, CarDataService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTE_ID, notification());
        if (data == null) {
            CarConfig cfg = CarConfig.load(this);
            data = CarData.shared(this, cfg);
            if (!data.isStarted()) {
                // The source chosen in the settings panel wins over config.json, as on Home.
                android.content.SharedPreferences p = getSharedPreferences("car", MODE_PRIVATE);
                data.setSource(p.getString("data_source", cfg.dataSource), p.getString("obd_address", cfg.obdAddress));
                data.start();
            }
            main.post(tick);
        }
        // Started again after Android stopped it (low memory): carry on.
        return START_STICKY;
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.car_data_channel),
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open != null) b.setContentIntent(PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE));
        return b.setSmallIcon(R.drawable.ic_car_home)
                .setContentTitle(getString(R.string.car_data_running))
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        if (data != null) data.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
