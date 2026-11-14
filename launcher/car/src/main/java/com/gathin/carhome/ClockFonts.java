package com.gathin.carhome;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;

/**
 * The clock's font choices, modelled on the styles iOS offers for its lock-screen clock: thin, light,
 * regular, heavy, tall condensed and serif, plus Inter (an open font close to Apple's system font, which
 * itself may only be used on Apple devices) and "custom": any font file the owner copies to the Glass Car
 * folder as clock.ttf or clock.otf.
 */
final class ClockFonts {
    static final String[] KEYS = {"thin", "light", "regular", "heavy", "condensed", "serif", "inter", "custom"};
    static final String[] NAMES = {"Thin", "Light", "Regular", "Heavy", "Condensed", "Serif", "Inter", "Custom"};

    private ClockFonts() {
    }

    static boolean isKnown(String key) {
        for (String k : KEYS) if (k.equals(key)) return true;
        return false;
    }

    /** The owner's own font file, or null. */
    static File customFile(Context ctx) {
        File dir = CarConfig.dir(ctx);
        if (dir == null) return null;
        for (String name : new String[]{"clock.ttf", "clock.otf"}) {
            File f = new File(dir, name);
            if (f.isFile()) return f;
        }
        return null;
    }

    static Typeface typeface(Context ctx, String key) {
        switch (key) {
            case "light":
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case "regular":
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case "heavy":
                return Typeface.create("sans-serif-black", Typeface.NORMAL);
            case "condensed":
                return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case "serif":
                return Typeface.create("serif", Typeface.NORMAL);
            case "inter":
                try {
                    return ctx.getResources().getFont(R.font.inter);
                } catch (RuntimeException e) {
                    return Typeface.DEFAULT;
                }
            case "custom":
                File f = customFile(ctx);
                if (f != null) {
                    try {
                        return Typeface.createFromFile(f);
                    } catch (RuntimeException ignored) {
                        // Not a font Android can read: fall through to thin.
                    }
                }
                return Typeface.create("sans-serif-thin", Typeface.NORMAL);
            case "thin":
            default:
                return Typeface.create("sans-serif-thin", Typeface.NORMAL);
        }
    }

    /** Heavier styles look larger at the same size; this keeps them all about the same width. */
    static float widthFactor(String key) {
        switch (key) {
            case "heavy":
                return 0.88f;
            case "condensed":
                return 1.18f;
            case "serif":
                return 0.95f;
            default:
                return 1f;
        }
    }
}
