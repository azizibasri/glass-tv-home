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
 * Car readings for the dashboard, while Home is on screen: speed, turbo boost, torque, coolant temperature and
 * fuel range.
 *
 * Speed comes from the car's CAN bus when the head unit's CAN app broadcasts it, else from GPS. Everything
 * else (turbo boost, torque, coolant, fuel range, odometer for the trip) comes from the CAN bus only. Head units have no standard CAN API: the CAN box behind the radio talks
 * to the maker's own app, and each maker shares the data differently, if at all. So the broadcast to listen
 * to for each value is set in config.json ({@code can_speed}, {@code can_boost}, {@code can_torque},
 * {@code can_coolant}, {@code can_range}, {@code can_odometer}: action, extra, scale), filled in once {@code car/scan.py} has
 * shown what this unit's CAN app sends.
 */
final class CarData {
    private static final String TAG = "CarHome.Data";
    /** Coolant and range change slowly and some boxes send them only every few seconds. */
    private static final long SLOW_STALE_MS = 30_000;

    /** One snapshot. NaN means "no data". */
    static final class Reading {
        /** Turbo boost in bar above the outside air (below zero: vacuum). */
        float boost = Float.NaN;
        float speed = Float.NaN, torque = Float.NaN, coolant = Float.NaN, range = Float.NaN;
        /** Total km on the car's odometer, for the trip counter. */
        float odometer = Float.NaN;
        /** The CAN door value (bits per door, see door_bits), or -1 when there is none. */
        int doors = -1;
        int speedSource = SpeedMath.NONE;
        /** "CAN BUS" or "OBD-II": where the car values come from. */
        String carLabel = "CAN BUS";
        /** In OBD-II mode, the adapter's state (ObdLink.OFF, CONNECTING, CONNECTED...). */
        int obdState = ObdLink.OFF;
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
    private final CarConfig cfg;
    private String mode = "can", obdAddress = "";
    private ObdLink obd;
    private volatile int obdState = ObdLink.OFF;
    private float torquePercent = Float.NaN, torqueReference = Float.NaN;
    private final Slot speed, boost, torque, coolant, range, odometer, doors;
    private float airKpa = Float.NaN;
    private final Slot[] slots;
    private float gpsSpeed = Float.NaN;
    private long gpsAt;
    private BroadcastReceiver canReceiver;
    private LocationListener gps;

    CarData(Context ctx, CarConfig cfg, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.mph = cfg.mph;
        this.cfg = cfg;
        this.mode = cfg.dataSource;
        this.obdAddress = cfg.obdAddress;
        speed = new Slot(cfg.canSpeed, 0, 400);
        boost = new Slot(cfg.canBoost, -1.2f, 4f);
        torque = new Slot(cfg.canTorque, -500, 2_000);
        coolant = new Slot(cfg.canCoolant, -40, 150);
        range = new Slot(cfg.canRange, 0, 3_000);
        odometer = new Slot(cfg.canOdometer, 0, 3_000_000);
        doors = new Slot(cfg.canDoors, 0, 0xFFFF);
        slots = new Slot[]{speed, boost, torque, coolant, range, odometer, doors};
    }

    boolean hasGpsPermission() {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Switches between "can" and "obd"; takes effect at the next start(). */
    void setSource(String mode, String obdAddress) {
        this.mode = mode;
        this.obdAddress = obdAddress == null ? "" : obdAddress;
    }

    boolean usesObd() {
        return "obd".equals(mode);
    }

    @SuppressLint("MissingPermission")
    void start() {
        if (usesObd()) {
            if (obd == null && !obdAddress.isEmpty()) {
                obd = new ObdLink(obdAddress, new ObdLink.Listener() {
                    @Override
                    public void onValue(String pid, int[] data) {
                        onObd(pid, data);
                    }

                    @Override
                    public void onState(int state, String detail) {
                        obdState = state;
                    }
                });
                obd.start();
            } else if (obdAddress.isEmpty()) {
                obdState = ObdLink.NOT_PAIRED;
            }
            startGps();
            return;
        }
        startCan();
        startGps();
    }

    private void startCan() {
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
    }

    @SuppressLint("MissingPermission")
    private void startGps() {
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
        if (obd != null) {
            obd.stop();
            obd = null;
        }
        obdState = ObdLink.OFF;
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
        r.carLabel = usesObd() ? "OBD-II" : "CAN BUS";
        r.obdState = obdState;
        r.speedSource = SpeedMath.pick(now, speed.at, gpsAt);
        r.speed = r.speedSource == SpeedMath.CAN ? speed.value : r.speedSource == SpeedMath.GPS ? gpsSpeed : Float.NaN;
        r.boost = boost.fresh(now, SpeedMath.CAN_STALE_MS);
        // Over OBD-II torque is asked for every few seconds, between the speed and boost requests.
        r.torque = torque.fresh(now, usesObd() ? 12_000 : SpeedMath.CAN_STALE_MS);
        r.coolant = coolant.fresh(now, SLOW_STALE_MS);
        r.range = range.fresh(now, SLOW_STALE_MS);
        r.odometer = odometer.fresh(now, SLOW_STALE_MS);
        // Doors: many boxes send them only when one changes, so the last value stays good. Not over OBD-II.
        r.doors = usesObd() || doors.at == 0 ? -1 : Math.round(doors.value);
        listener.onData(r);
    }

    /** One value from the OBD-II adapter, on its thread. */
    private void onObd(String pid, int[] data) {
        long now = SystemClock.elapsedRealtime();
        float v = Float.NaN;
        Slot slot = null;
        switch (pid) {
            case ObdParser.SPEED:
                v = ObdParser.speed(data) * (mph ? 0.621371f : 1f);
                slot = speed;
                break;
            case ObdParser.MANIFOLD_PRESSURE:
                v = ObdParser.boostBar(ObdParser.kpa(data), airKpa);
                slot = boost;
                break;
            case ObdParser.BAROMETRIC_PRESSURE:
                float air = ObdParser.kpa(data);
                if (air >= 60 && air <= 110) airKpa = air;
                break;
            case ObdParser.COOLANT:
                v = ObdParser.coolant(data);
                slot = coolant;
                break;
            case ObdParser.FUEL_LEVEL:
                v = ObdParser.range(ObdParser.fuelPercent(data), cfg.fuelTankLitres, cfg.fuelLitresPer100km);
                slot = range;
                break;
            case ObdParser.TORQUE_PERCENT:
                torquePercent = ObdParser.torquePercent(data);
                break;
            case ObdParser.TORQUE_REFERENCE:
                torqueReference = ObdParser.torqueReference(data);
                break;
            case ObdParser.ODOMETER:
                v = ObdParser.odometer(data);
                slot = odometer;
                break;
            default:
                break;
        }
        if (!Float.isNaN(torquePercent) && !Float.isNaN(torqueReference)
                && (pid.equals(ObdParser.TORQUE_PERCENT) || pid.equals(ObdParser.TORQUE_REFERENCE))) {
            v = torquePercent / 100f * torqueReference;
            slot = torque;
        }
        if (slot != null && !Float.isNaN(v) && v >= slot.min && v <= slot.max) {
            slot.value = v;
            slot.at = now;
        }
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
