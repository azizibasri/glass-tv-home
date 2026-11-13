package com.gathin.carhome;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

/**
 * Speed and torque for the dashboard, while Home is on screen.
 *
 * Speed: from the car's CAN bus when the head unit's CAN app broadcasts it, else from GPS. Torque: from
 * the CAN bus only. Head units have no standard CAN API: the CAN box behind the radio talks to the maker's
 * own app, and each maker shares the data differently, if at all. So the broadcast to listen to is set in
 * config.json ({@code can_speed}, {@code can_torque}: action, extra, scale), filled in once
 * {@code car/scan.py} has shown what this unit's CAN app sends.
 */
final class CarData {
    private static final String TAG = "CarHome.Data";

    interface Listener {
        /** Speed in the configured unit (NaN when none), its source, and torque in Nm (NaN when none). */
        void onData(float speed, int speedSource, float torqueNm);
    }

    private final Context ctx;
    private final CarConfig cfg;
    private final Listener listener;
    private float canSpeed = Float.NaN, gpsSpeed = Float.NaN, torque = Float.NaN;
    private long canAt, gpsAt, torqueAt;
    private BroadcastReceiver canReceiver;
    private LocationListener gps;

    CarData(Context ctx, CarConfig cfg, Listener listener) {
        this.ctx = ctx;
        this.cfg = cfg;
        this.listener = listener;
    }

    boolean hasGpsPermission() {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    void start() {
        IntentFilter f = new IntentFilter();
        if (cfg.canSpeed != null) f.addAction(cfg.canSpeed.action);
        if (cfg.canTorque != null) f.addAction(cfg.canTorque.action);
        if (f.countActions() > 0 && canReceiver == null) {
            canReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    onCan(i);
                }
            };
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(canReceiver, f, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(canReceiver, f);
        }
        if (gps == null && hasGpsPermission()) {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            gps = new LocationListener() {
                @Override
                public void onLocationChanged(Location l) {
                    if (!l.hasSpeed()) return;
                    gpsSpeed = SpeedMath.fromMetresPerSecond(l.getSpeed(), cfg.mph);
                    gpsAt = SystemClock.elapsedRealtime();
                    publish();
                }

                @Override
                public void onStatusChanged(String p, int s, Bundle b) {
                }

                @Override
                public void onProviderEnabled(String p) {
                }

                @Override
                public void onProviderDisabled(String p) {
                }
            };
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, gps);
            } catch (RuntimeException e) {
                Log.w(TAG, "no GPS: " + e);
                gps = null;
            }
        }
    }

    void stop() {
        if (canReceiver != null) {
            ctx.unregisterReceiver(canReceiver);
            canReceiver = null;
        }
        if (gps != null) {
            ((LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE)).removeUpdates(gps);
            gps = null;
        }
    }

    /** Called about once a second too, so stale values fall back to GPS or to "--". */
    void publish() {
        long now = SystemClock.elapsedRealtime();
        int src = SpeedMath.pick(now, canAt, gpsAt);
        float speed = src == SpeedMath.CAN ? canSpeed : src == SpeedMath.GPS ? gpsSpeed : Float.NaN;
        float t = torqueAt > 0 && now - torqueAt <= SpeedMath.CAN_STALE_MS ? torque : Float.NaN;
        listener.onData(speed, src, t);
    }

    private void onCan(Intent i) {
        Bundle b = i.getExtras();
        if (b == null) return;
        long now = SystemClock.elapsedRealtime();
        if (cfg.canSpeed != null && cfg.canSpeed.action.equals(i.getAction())) {
            float v = SpeedMath.parse(b.get(cfg.canSpeed.extra), cfg.canSpeed.scale);
            if (!Float.isNaN(v) && v >= 0 && v < 400) {
                canSpeed = v;
                canAt = now;
            }
        }
        if (cfg.canTorque != null && cfg.canTorque.action.equals(i.getAction())) {
            float v = SpeedMath.parse(b.get(cfg.canTorque.extra), cfg.canTorque.scale);
            if (!Float.isNaN(v) && v > -500 && v < 2000) {
                torque = v;
                torqueAt = now;
            }
        }
        publish();
    }
}
