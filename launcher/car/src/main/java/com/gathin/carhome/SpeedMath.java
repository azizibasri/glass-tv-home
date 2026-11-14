package com.gathin.carhome;

/**
 * Speed arithmetic for the dashboard, in plain Java so it is unit tested: which source to trust, smoothing
 * so the needle doesn't jitter, and where on the dial a speed sits.
 */
final class SpeedMath {
    static final int NONE = 0, CAN = 1, GPS = 2;
    /** CAN frames come many times a second; older than this, the car has stopped sending. */
    static final long CAN_STALE_MS = 3_000;
    /** GPS fixes come about once a second; older than this, the signal is lost (tunnel, car park). */
    static final long GPS_STALE_MS = 5_000;
    /** The dial: 240 degrees, starting at the lower left (150 degrees, clockwise from 3 o'clock). */
    static final float START_DEG = 150f, SWEEP_DEG = 240f;

    private SpeedMath() {
    }

    /** CAN when it is fresh, else GPS when fresh, else none. Times are elapsed-realtime ms, 0 = never. */
    static int pick(long now, long canAt, long gpsAt) {
        if (canAt > 0 && now - canAt <= CAN_STALE_MS) return CAN;
        if (gpsAt > 0 && now - gpsAt <= GPS_STALE_MS) return GPS;
        return NONE;
    }

    /** m/s from GPS to km/h or mph. */
    static float fromMetresPerSecond(float mps, boolean mph) {
        return Math.max(0f, mps * (mph ? 2.236936f : 3.6f));
    }

    /** One smoothing step towards {@code target}; {@code dtMs} since the last step. Snaps when close. */
    static float smooth(float shown, float target, long dtMs) {
        float k = 1f - (float) Math.exp(-Math.max(0, dtMs) / 180.0);
        float next = shown + (target - shown) * k;
        return Math.abs(target - next) < 0.05f ? target : next;
    }

    /** Fraction of the dial, 0 to 1, for {@code speed} on a dial that ends at {@code max}. */
    static float fraction(float speed, float max) {
        if (max <= 0) return 0f;
        return Math.max(0f, Math.min(1f, speed / max));
    }

    /** The whole number shown in the middle; GPS noise under 2 reads as standing still. */
    static int display(float speed) {
        return speed < 2f ? 0 : Math.round(speed);
    }

    /** A number from a CAN broadcast extra (int, float, or text like "62" / "62.5 km/h"), times scale. */
    static float parse(Object value, float scale) {
        if (value instanceof Number) return ((Number) value).floatValue() * scale;
        if (value instanceof CharSequence) {
            String s = value.toString().replaceAll("[^0-9.,-]", "").replace(',', '.');
            try {
                return Float.parseFloat(s) * scale;
            } catch (NumberFormatException e) {
                return Float.NaN;
            }
        }
        return Float.NaN;
    }

    // ---- Coolant and fuel range tiles ----

    static final int COLD = 0, NORMAL = 1, HOT = 2;

    /** Engine coolant, °C: cold under 70 (warming up), hot over 110, normal between (VW runs about 90). */
    static int coolantLevel(float c) {
        return c < 70f ? COLD : c > 110f ? HOT : NORMAL;
    }

    /** Where coolant sits on the tile's bar, which runs from 40 to 130 °C. */
    static float coolantFraction(float c) {
        return Math.max(0f, Math.min(1f, (c - 40f) / 90f));
    }

    /** Fuel range under this many km is shown in orange, and under a third of it in red. */
    static final float RANGE_LOW_KM = 80f;

    /** Range bar: full at 700 km, about a full tank in a 2017 Jetta. */
    static float rangeFraction(float km) {
        return Math.max(0f, Math.min(1f, km / 700f));
    }
}
