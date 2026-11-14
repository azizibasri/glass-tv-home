package com.gathin.carhome;

/**
 * The trip counter. Best: the car's odometer from the CAN bus, so the trip is the odometer now minus the
 * odometer at the last reset, and it counts every km even while another app is on screen. Without an
 * odometer it adds up distance from the speed (CAN or GPS) while the dashboard is running. Plain Java, so
 * it is unit tested; the caller saves {@link #baseOdometer} and {@link #accumulated} between runs.
 */
final class TripMeter {
    /** Gaps longer than this (Home was in the background, GPS lost) add nothing rather than guess. */
    static final long MAX_GAP_MS = 5_000;

    /** Odometer at the last reset, or NaN when no odometer has been seen since. */
    float baseOdometer = Float.NaN;
    /** Distance added up from speed since the last reset, used when there is no odometer. */
    float accumulated;
    private float lastOdometer = Float.NaN;
    private long lastSampleAt;

    /** A new odometer reading. A lower reading than the base (a new box, a reset odometer) re-bases. */
    void odometer(float km) {
        if (Float.isNaN(km)) return;
        if (Float.isNaN(baseOdometer) || km < baseOdometer) baseOdometer = km - accumulated;
        lastOdometer = km;
    }

    /** A speed sample (km/h, or mph for miles) at {@code nowMs}; adds the distance since the last one. */
    void speed(float perHour, long nowMs) {
        if (lastSampleAt > 0 && !Float.isNaN(perHour) && perHour >= 2f) {
            long dt = nowMs - lastSampleAt;
            if (dt > 0 && dt <= MAX_GAP_MS) accumulated += perHour * dt / 3_600_000f;
        }
        lastSampleAt = Float.isNaN(perHour) ? 0 : nowMs;
    }

    boolean fromOdometer() {
        return !Float.isNaN(lastOdometer) && !Float.isNaN(baseOdometer);
    }

    /** The trip so far. */
    float distance() {
        return fromOdometer() ? Math.max(0f, lastOdometer - baseOdometer) : accumulated;
    }

    void reset() {
        accumulated = 0f;
        baseOdometer = lastOdometer;
        lastSampleAt = 0;
    }

    /** "123.4" under 1000, "1234" from there. */
    static String format(float d) {
        return d < 1000f ? String.format(java.util.Locale.ROOT, "%.1f", d) : String.valueOf(Math.round(d));
    }
}
