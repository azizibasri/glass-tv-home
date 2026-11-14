package com.gathin.carhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings from {@code config.json} in the app's external files folder
 * ({@code /sdcard/Android/data/com.gathin.carhome/files}), pushed by {@code car/theme.sh}. Every key is
 * optional; a missing or wrongly typed key keeps the default, and a file that doesn't parse is ignored,
 * so a bad file can never break Home. Keys are listed in car/README.md.
 */
final class CarConfig {
    private static final String TAG = "CarHome";

    /** Dock apps in order; empty means "pick maps, music, CarPlay and phone apps by themselves". */
    final List<String> dockApps = new ArrayList<>();
    final List<String> hiddenApps = new ArrayList<>();
    /** More package-name parts the Clean button must never close, on top of {@link Keep#BUILT_IN}. */
    final List<String> keepRunning = new ArrayList<>();
    int columns = 0;
    /** Close background apps by itself when Home shows and this many minutes passed; 0 = only on tap. */
    int autoCleanMinutes = 30;
    boolean showLabels = true;
    boolean wallpaper = true;
    /** Accent for the RAM ring and the page dot, #RRGGBB. */
    int accent = 0xFF0A84FF;
    /** First page: big clock, speedometer and torque. */
    boolean dashboard = true;
    /** The dashboard clock in a flowing, glowing rainbow; false for plain white. */
    boolean clockRainbow = true;
    /** The music widget under the clock. */
    boolean musicWidget = true;
    boolean mph = false;
    /** Top of the speedometer dial. The 2017 Jetta's own cluster ends at 240 km/h. */
    int speedMax = 240;
    /** End of the torque bar, Nm: about the engine's peak (1.4 TSI 250 Nm, 1.5 MPI 145 Nm). */
    int torqueMax = 250;

    /** One value a head unit's CAN app broadcasts: the intent action, the extra holding it, a factor. */
    static final class CanKey {
        final String action, extra;
        final float scale;

        CanKey(String action, String extra, float scale) {
            this.action = action;
            this.extra = extra;
            this.scale = scale;
        }
    }

    CanKey canSpeed, canTorque, canRpm, canCoolant, canRange, canOdometer;
    /** App icons: "color" (as the apps draw them) or "glass" (clear frosted glass, white symbol). */
    String iconStyle = "color";
    /** Rev counter: top of the dial and where the red zone starts. The 2017 Jetta's cluster: 7000 / 6000. */
    int rpmMax = 7000, rpmRed = 6000;

    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static CarConfig load(Context ctx) {
        CarConfig c = new CarConfig();
        File d = dir(ctx);
        File f = d == null ? null : new File(d, "config.json");
        if (f == null || !f.isFile()) return c;
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 64 * 1024)];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            JSONObject o = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));
            strings(o.optJSONArray("dock_apps"), c.dockApps);
            strings(o.optJSONArray("hidden_apps"), c.hiddenApps);
            strings(o.optJSONArray("keep_running"), c.keepRunning);
            int cols = o.optInt("columns", 0);
            if (cols >= 3 && cols <= 8) c.columns = cols;
            int auto = o.optInt("auto_clean_minutes", c.autoCleanMinutes);
            if (auto >= 0 && auto <= 1440) c.autoCleanMinutes = auto;
            if (o.opt("show_labels") instanceof Boolean) c.showLabels = o.getBoolean("show_labels");
            if (o.opt("wallpaper") instanceof Boolean) c.wallpaper = o.getBoolean("wallpaper");
            if (o.opt("dashboard") instanceof Boolean) c.dashboard = o.getBoolean("dashboard");
            if (o.opt("clock_rainbow") instanceof Boolean) c.clockRainbow = o.getBoolean("clock_rainbow");
            if (o.opt("music_widget") instanceof Boolean) c.musicWidget = o.getBoolean("music_widget");
            c.mph = "mph".equalsIgnoreCase(o.optString("speed_unit", "km/h"));
            int sm = o.optInt("speed_max", c.speedMax);
            if (sm >= 60 && sm <= 400) c.speedMax = sm;
            int tm = o.optInt("torque_max", c.torqueMax);
            if (tm >= 50 && tm <= 2000) c.torqueMax = tm;
            c.canSpeed = canKey(o.optJSONObject("can_speed"));
            c.canTorque = canKey(o.optJSONObject("can_torque"));
            c.canRpm = canKey(o.optJSONObject("can_rpm"));
            c.canCoolant = canKey(o.optJSONObject("can_coolant"));
            c.canRange = canKey(o.optJSONObject("can_range"));
            c.canOdometer = canKey(o.optJSONObject("can_odometer"));
            String style = o.optString("icon_style", c.iconStyle);
            if (style.equals("color") || style.equals("glass")) c.iconStyle = style;
            int rm = o.optInt("rpm_max", c.rpmMax);
            if (rm >= 3000 && rm <= 12000) c.rpmMax = rm;
            int rr = o.optInt("rpm_red", c.rpmRed);
            if (rr >= 2000 && rr <= c.rpmMax) c.rpmRed = rr;
            String accent = o.optString("accent", "");
            if (accent.matches("#[0-9a-fA-F]{6}")) c.accent = 0xFF000000 | Integer.parseInt(accent.substring(1), 16);
        } catch (Exception e) {
            Log.w(TAG, "config.json ignored: " + e);
            return new CarConfig();
        }
        return c;
    }

    private static CanKey canKey(JSONObject o) {
        if (o == null) return null;
        String action = o.optString("action", "").trim(), extra = o.optString("extra", "").trim();
        double scale = o.optDouble("scale", 1.0);
        if (action.isEmpty() || extra.isEmpty() || Double.isNaN(scale) || scale == 0) return null;
        return new CanKey(action, extra, (float) scale);
    }

    private static void strings(JSONArray a, List<String> out) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
    }
}
