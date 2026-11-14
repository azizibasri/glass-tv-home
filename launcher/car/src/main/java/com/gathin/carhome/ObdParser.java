package com.gathin.carhome;

import java.util.Locale;

/**
 * Reads the replies of an ELM327-type OBD-II adapter (the kind Carista and most Bluetooth OBD dongles are
 * built on). Plain Java, so it is unit tested. Standard mode 01 values only, the ones every petrol car since
 * 2001 in Europe and Asia must answer; a car that doesn't support one just answers NO DATA.
 */
final class ObdParser {
    /** The requests, in the adapter's own words: mode 01 + PID. */
    static final String SPEED = "010D", RPM = "010C", COOLANT = "0105", FUEL_LEVEL = "012F",
            TORQUE_PERCENT = "0162", TORQUE_REFERENCE = "0163", ODOMETER = "01A6",
            MANIFOLD_PRESSURE = "010B", BAROMETRIC_PRESSURE = "0133";

    /** Sea-level air pressure, kPa: used for boost until the car reports its own. */
    static final float STANDARD_AIR_KPA = 101.3f;

    private ObdParser() {
    }

    /**
     * The data bytes of the reply to {@code request}, or null when there are none (NO DATA, ?, an error, or
     * a reply to something else). Handles echo, spaces, line breaks, headers on several ECUs and the prompt.
     */
    static int[] data(String reply, String request) {
        if (reply == null || request == null || request.length() != 4) return null;
        String want = "4" + request.substring(1).toUpperCase(Locale.ROOT);   // "010D" -> "410D"
        String[] lines = reply.toUpperCase(Locale.ROOT).replace('>', '\n').split("[\\r\\n]+");
        for (String line : lines) {
            String hex = line.replaceAll("[^0-9A-F]", "");
            if (line.contains("SEARCHING") || line.contains("NODATA") || line.contains("NO DATA")) continue;
            int at = hex.indexOf(want);
            // Skip an echo of the request itself ("010D") when echo is on: replies start with 41.
            if (at < 0 || (hex.length() - at - 4) % 2 != 0) continue;
            String bytes = hex.substring(at + 4);
            if (bytes.isEmpty()) continue;
            int[] out = new int[bytes.length() / 2];
            for (int i = 0; i < out.length; i++) out[i] = Integer.parseInt(bytes.substring(2 * i, 2 * i + 2), 16);
            return out;
        }
        return null;
    }

    /** km/h. */
    static float speed(int[] b) {
        return b != null && b.length >= 1 ? b[0] : Float.NaN;
    }

    /** rpm. */
    static float rpm(int[] b) {
        return b != null && b.length >= 2 ? (b[0] * 256 + b[1]) / 4f : Float.NaN;
    }

    /** °C. */
    static float coolant(int[] b) {
        return b != null && b.length >= 1 ? b[0] - 40f : Float.NaN;
    }

    /** Fuel tank level, 0 to 100 %. */
    static float fuelPercent(int[] b) {
        return b != null && b.length >= 1 ? b[0] * 100f / 255f : Float.NaN;
    }

    /** Actual engine torque as % of the reference torque (-125 to 130). */
    static float torquePercent(int[] b) {
        return b != null && b.length >= 1 ? b[0] - 125f : Float.NaN;
    }

    /** The engine's reference torque in Nm. */
    static float torqueReference(int[] b) {
        return b != null && b.length >= 2 ? b[0] * 256 + b[1] : Float.NaN;
    }

    /** km on the odometer (newer cars only). */
    static float odometer(int[] b) {
        return b != null && b.length >= 4 ? ((long) b[0] << 24 | b[1] << 16 | b[2] << 8 | b[3]) / 10f : Float.NaN;
    }

    /** Intake manifold absolute pressure, or barometric pressure: kPa. */
    static float kpa(int[] b) {
        return b != null && b.length >= 1 ? b[0] : Float.NaN;
    }

    /**
     * Turbo boost in bar: manifold pressure over the outside air. Below zero is vacuum (off the throttle);
     * above zero the turbo is pushing.
     */
    static float boostBar(float manifoldKpa, float airKpa) {
        if (Float.isNaN(manifoldKpa)) return Float.NaN;
        return (manifoldKpa - (Float.isNaN(airKpa) ? STANDARD_AIR_KPA : airKpa)) / 100f;
    }

    /** Fuel range from the tank level: litres left, divided by the car's usual use per 100 km. */
    static float range(float fuelPercent, float tankLitres, float litresPer100km) {
        if (Float.isNaN(fuelPercent) || tankLitres <= 0 || litresPer100km <= 0) return Float.NaN;
        return fuelPercent / 100f * tankLitres / litresPer100km * 100f;
    }
}
