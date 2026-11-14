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
 * Car readings for the dashboard, while Home is on screen: speed, rpm, torque, coolant temperature and
 * fuel range.
 *
 * Speed comes from the car's CAN bus when the head unit's CAN app broadcasts it, else from GPS. Everything
 * else (rpm, torque, coolant, fuel range, odometer for the trip) comes from the CAN bus only. Head units have no standard CAN API: the CAN box behind the radio talks
 * to the maker's own app, and each maker shares the data differently, if at all. So the broadcast to listen
 * to for each value is set in config.json ({@code can_speed}, {@code can_rpm}, {@code can_torque},
 * {@code can_coolant}, {@code can_range}, {@code can_odometer}: action, extra, scale), filled in once {@code car/scan.py} has
 * shown what this unit's CAN app sends.
 */
final class CarData {
    private static final String TAG = "CarHome.Data";
    /** Coolant and range change slowly and some boxes send them only every few seconds. */
    private static final long SLOW_STALE_MS = 30_000;

    /** One snapshot. NaN means "no data". */
    static final class Reading {
        float speed = Float.NaN, rpm = Float.NaN, torque = Float.NaN, coolant = Float.NaN, range = Float.NaN;
        /** Total km on the car's odometer, for the trip counter. */
        float odometer = Float.NaN;
        int speedSource = SpeedMath.NONE;
    }

    interface Listener {
        void onData(Reading r);
    }

    /** Latest value from one CAN key, and when it came. */
    private static final class Slot {
        final CarConfig.CanKey key;
        final float min, max;
        float value = Float.NaN;
        long at;

        Slot(CarConfig.CanKey key, float min, float max) {
            this.key = key;
            this.min = min;
            this.max = max;
        }

        float fresh(long now, long staleMs) {
            return at > 0 && now - at <= staleMs ? value : Float.NaN;
        }
    }

    private final Context ctx;
    private final Listener listener;
    private final boolean mph;
    private final Slot speed, rpm, torque, coolant, range, odometer;
    private final Slot[] slots;
    private float gpsSpeed = Float.NaN;
    private long gpsAt;
    private BroadcastReceiver canReceiver;
    private LocationListener gps;

    CarData(Context ctx, CarConfig cfg, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.mph = cfg.mph;
        speed = new Slot(cfg.canSpeed, 0, 400);
        rpm = new Slot(cfg.canRpm, 0, 12_000);
        torque = new Slot(cfg.canTorque, -500, 2_000);
        coolant = new Slot(cfg.canCoolant, -40, 150);
        range = new Slot(cfg.canRange, 0, 3_000);
        odometer = new Slot(cfg.canOdometer, 0, 3_000_000);
        slots = new Slot[]{speed, rpm, torque, coolant, range, odometer};
    }

    boolean hasGpsPermission() {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    void start() {
        IntentFilter f = new IntentFilter();
        for (Slot s : slots) if (s.key != null && !f.hasAction(s.key.action)) f.addAction(s.key.action);
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
                    gpsSpeed = SpeedMath.fromMetresPerSecond(l.getSpeed(), mph);
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
        Reading r = new Reading();
        r.speedSource = SpeedMath.pick(now, speed.at, gpsAt);
        r.speed = r.speedSource == SpeedMath.CAN ? speed.value : r.speedSource == SpeedMath.GPS ? gpsSpeed : Float.NaN;
        r.rpm = rpm.fresh(now, SpeedMath.CAN_STALE_MS);
        r.torque = torque.fresh(now, SpeedMath.CAN_STALE_MS);
        r.coolant = coolant.fresh(now, SLOW_STALE_MS);
        r.range = range.fresh(now, SLOW_STALE_MS);
        r.odometer = odometer.fresh(now, SLOW_STALE_MS);
        listener.onData(r);
    }

    private void onCan(Intent i) {
        Bundle b = i.getExtras();
        if (b == null) return;
        long now = SystemClock.elapsedRealtime();
        for (Slot s : slots) {
            if (s.key == null || !s.key.action.equals(i.getAction()) || !b.containsKey(s.key.extra)) continue;
            float v = SpeedMath.parse(b.get(s.key.extra), s.key.scale);
            if (!Float.isNaN(v) && v >= s.min && v <= s.max) {
                s.value = v;
                s.at = now;
            }
        }
        publish();
    }
}
