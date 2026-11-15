package com.gathin.carhome;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * What the unit's own radio app is showing, for the music widget, from its notification (read by
 * {@link MediaListener}) or a broadcast set up in config.json. Only radio apps are looked at.
 */
final class RadioState {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** A broadcast frequency counts as "the radio is on" for this long after the last one. */
    static final long BROADCAST_FRESH_MS = 20_000;

    private static volatile String pkg;
    private static volatile CharSequence[] texts;
    private static volatile boolean notified;
    private static volatile RadioInfo broadcast;
    private static volatile long broadcastAt;
    private static volatile int broadcastChannel;
    private static Runnable listener;

    private RadioState() {
    }

    static void setListener(Runnable r) {
        listener = r;
    }

    private static void changed() {
        Runnable r = listener;
        if (r != null) MAIN.post(r);
    }

    /** The radio app's notification, with its texts (title, text, sub text...). */
    static void notification(String radioPkg, CharSequence... t) {
        pkg = radioPkg;
        texts = t;
        notified = true;
        changed();
    }

    static void removed(String radioPkg) {
        if (radioPkg != null && radioPkg.equals(pkg)) {
            notified = false;
            changed();
        }
    }

    /** A frequency (and maybe channel) from the configured broadcast. */
    static void broadcast(RadioInfo info, int channel) {
        if (info != null && !info.isEmpty()) broadcast = info;
        if (channel > 0) broadcastChannel = channel;
        broadcastAt = SystemClock.elapsedRealtime();
        changed();
    }

    /** True while the radio app shows its notification, or a broadcast came in lately. */
    static boolean isOn() {
        return notified || (broadcastAt > 0 && SystemClock.elapsedRealtime() - broadcastAt < BROADCAST_FRESH_MS);
    }

    /** The radio app's package, when known. */
    static String pkg() {
        return pkg;
    }

    /** What is known now: the broadcast's numbers first (exact), then the notification's. */
    static RadioInfo info() {
        CharSequence[] t = texts;
        RadioInfo fromText = notified && t != null ? RadioInfo.parse(t) : RadioInfo.parse();
        RadioInfo b = broadcast;
        boolean fresh = broadcastAt > 0 && SystemClock.elapsedRealtime() - broadcastAt < BROADCAST_FRESH_MS;
        if (!fresh) return fromText;
        int ch = broadcastChannel > 0 ? broadcastChannel : fromText.channel;
        return new RadioInfo(b != null ? b.band : fromText.band, b != null ? b.frequency : fromText.frequency, ch,
                fromText.station);
    }
}
