package com.gathin.carhome;

import java.util.Collection;
import java.util.Locale;

/**
 * Which apps the Clean button must never close. A head unit runs the car through ordinary-looking apps:
 * the reverse camera, the steering-wheel buttons, the CAN bus box, the MCU link, the radio, Bluetooth
 * calling and the CarPlay / Android Auto bridge. Closing one can blank the reverse camera while reversing,
 * so anything whose package name looks related is left alone, whatever else it is.
 *
 * Plain Java, no Android imports, so it is unit tested on the JVM.
 */
final class Keep {
    /** Parts of a package name that mean "leave it running". Lower case. */
    static final String[] BUILT_IN = {
            // Phone projection: CarPlay and Android Auto bridges (zLink, AutoKit, CarbitLink, EasyConn...)
            "carplay", "zlink", "autokit", "carlink", "carbit", "easyconn", "androidauto", "projection",
            "com.google.android.projection", "com.google.android.apps.automotive",
            // The car itself: CAN bus, MCU, steering wheel controls, vehicle settings
            "canbus", "canbox", ".can.", "mcu", "swc", "steer", "vehicle", "carsetting", "car.setting",
            "carservice", "car.service", "hardware", "factory", "fyt", "syu", "microntek", "hct", "ts10",
            // Cameras and safety: reverse camera, 360 view, dash cam, tyre pressure
            "camera", "avm", "reverse", "backcar", "rearview", "dvr", "tpms", "adas",
            // Sound and radio
            "radio", "fmradio", "tuner", "dsp", "equalizer", "eq.", "audio", "sound",
            // Bluetooth, calls, keyboard and the system
            "bluetooth", "btphone", "bt.phone", "nfore", "goc", "phone", "telecom", "dialer", "contacts",
            "inputmethod", "keyboard", "systemui", "settings", "launcher", "provider", "permission",
            "gms", "gsf", "vending", "location", "gps", "tts", "voice", "assistant",
    };

    private Keep() {
    }

    /** True when {@code pkg} must not be closed: it matches a built-in or an {@code extra} part. */
    static boolean isProtected(String pkg, Collection<String> extra) {
        if (pkg == null || pkg.isEmpty()) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.equals("android") || p.startsWith("com.android.") && !p.startsWith("com.android.chrome")) return true;
        for (String part : BUILT_IN) if (p.contains(part)) return true;
        if (extra != null) {
            for (String part : extra) {
                if (part != null && !part.isEmpty() && p.contains(part.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
