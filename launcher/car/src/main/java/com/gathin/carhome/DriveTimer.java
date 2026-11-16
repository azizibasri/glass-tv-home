package com.gathin.carhome;

/**
 * Driving time: adds up the time the car is moving, from the same speed samples as the trip (CAN, OBD-II
 * or GPS). Short stops (traffic lights, jams) count too, up to {@link #STOP_MAX_MS} each; a longer stop is
 * parking and stops the clock until the car moves again. Without speed data nothing is counted. Plain Java,
 * so it is unit tested; the caller saves {@link #drivenMs} between runs.
 */
final class DriveTimer {
    /** Gaps between samples longer than this (Home was closed, the data lost) add nothing. */
    static final long MAX_GAP_MS = 5_000;
    /** How much of one stop counts as driving. */
    static final long STOP_MAX_MS = 3 * 60_000;
    /** Slower than this is standing still (GPS noise). */
    static final float MOVING = 3f;

    long drivenMs;
    private long lastSampleAt, stoppedFor;
    private boolean everMoved;

    /** A speed sample (km/h or mph; NaN: none) at {@code nowMs}. */
    void speed(float perHour, long nowMs) {
        if (Float.isNaN(perHour)) {
            lastSampleAt = 0;
            return;
        }
        long dt = lastSampleAt > 0 ? nowMs - lastSampleAt : 0;
        lastSampleAt = nowMs;
        if (dt <= 0 || dt > MAX_GAP_MS) {
            if (perHour >= MOVING) {
                everMoved = true;
                stoppedFor = 0;
            }
            return;
        }
        if (perHour >= MOVING) {
            everMoved = true;
            stoppedFor = 0;
            drivenMs += dt;
        } else if (everMoved && stoppedFor < STOP_MAX_MS) {
            long add = Math.min(dt, STOP_MAX_MS - stoppedFor);
            stoppedFor += dt;
            drivenMs += add;
        }
    }

    void reset() {
        drivenMs = 0;
        stoppedFor = 0;
        everMoved = false;
    }

    /** "0:05:09" style: hours, minutes and seconds. */
    static String format(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }
}
